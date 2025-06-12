(ns lupapalvelu.ryhti.transform
  "Various mechanisms for transforming old data to Ryhti."
  (:require [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.db :as db]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.foreman-application-util :as foreman-app-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.ryhti.util :as ryhti-util]
            [monger.operators :refer :all]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(def ^:private schema-ryhti-schema-map
  "Map from schema name to Ryhti schema name as defined in `ops/operations`. The underlying
  assumption is that a schema always maps to the same Ryhti schema."
  (->> (vals ops/operations)
       (filter :ryhti-schema)
       (map (juxt :schema :ryhti-schema))
       (into {})))

(def ^:private ryhti-schema-set
  "Every `:ryhti-schema` from operations."
  (-> schema-ryhti-schema-map vals set))

(defn ryhti-schema
  "Returns a corresponding Ryhti document schema for legacy `document`. Nil if there is no
  Ryhti counterpart or the document already is Ryhti document."
  [document]
  (some->>  document :schema-info :name
            (get schema-ryhti-schema-map)
            (hash-map :name)
            schemas/get-schema))

(defn ryhti-doc?
  "True if `doc` is a Ryhti-specific document."
  [doc]
  (contains? ryhti-schema-set
             (some-> doc :schema-info :name)))

(defn transform-needed? [{:keys [documents permitType infoRequest
                                 permitSubtype facta-imported
                                 primaryOperation ryhti-data-2026]
                          :as   application}]
  (boolean (and (not infoRequest)
                (not facta-imported)
                (#{"R" "P"} permitType)
                (util/not=as-kw :muutoslupa permitSubtype)
                (util/not=as-kw :raktyo-aloit-loppuunsaat (:name primaryOperation))
                (not (app-utils/paper-permit? application))
                (not (foreman-app-util/foreman-app? application))
                (or (not ryhti-data-2026)
                    (some ryhti-schema documents)))))

(defn- -handle-ryhti-ids [wrap? m & ks]
  (cond->> (apply ryhti-util/ryhti-id (flatten ks))
    wrap? tools/wrapped
    true (merge m)))

(def ^:private wrap-ryhti-ids (partial -handle-ryhti-ids true))
(def ^:private add-ryhti-ids  (partial -handle-ryhti-ids false))

(defn- branch?
  [m]
  (not-any? #{:value :modified} (keys m)))

(defn- m-or-nil
  "Returns item from `data` that matches keyword path `kwp`. Branch nodes are returned as
  such, but leaf nodes are returned only if the value is fullish (see
  `util/fullish?`) or the map has source value. Otherwise nil."
  [data kwp]
  (let [{v :value :as m} (get-in data (util/split-kw-path kwp))]
    (when  (or (branch? m)
               (contains? m :sourceValue)
               (util/fullish? v))
      m)))

(defn- from-or-nil
  "Convenience function for accessing item form context `:from-doc` data."
  [{:keys [from-doc]} kwp]
  (m-or-nil (:data from-doc) kwp))

(defn- data-set
  "Assocs value `v` (if it is fullish) to `ctx` to-doc data. The path within data is
  defined by keyword path `kwp`."
  ([ctx kw-prefix kwp v]
   (cond-> ctx
     (util/fullish? v) (assoc-in (concat [:to-doc :data]
                                         (util/split-kw-path kw-prefix)
                                         (util/split-kw-path kwp)) v)))
  ([ctx kwp v]
   (data-set ctx nil kwp v)))

(defn- data-set-ryhti-ids
  "Like `data-set` but adds (wrapped) Ryhti ids into keyword path `kwp`. If the path already
  has  `:ryhti` key it is not overriden."
  [ctx kwp & ks]
  (update-in ctx
             (concat [:to-doc :data]
                     (util/split-kw-path kwp)
                     [:ryhti])
             (fn [ryhti]
               (or ryhti (:ryhti (apply wrap-ryhti-ids nil ks))))))

(defn- data-copy
  "Copy item from context from-doc data to to-doc data. Only branches, fullish or
  source-valued items are copied."
  ([ctx from-kwp kw-prefix to-kwp]
   (data-set ctx kw-prefix to-kwp (from-or-nil ctx from-kwp)))
  ([ctx from-kwp to-kwp]
   (data-copy ctx from-kwp nil to-kwp))
  ([ctx kwp]
   (data-copy ctx kwp kwp)))

(defn- revalue
  "Changes value and source-value of `m` if needed. Nil, if `m` is not map."
  [{v :value src :sourceValue :as m} new-value]
  (cond
    (not (map? m))  nil
    (= v new-value) m
    :else           (cond-> (assoc m :value new-value)
                      (and (contains? m :sourceValue)
                           (= src v)) (assoc :sourceValue new-value))))

(defn- ctx-wrap [{:keys [from-doc]} value]
  {:value value :modified (:created from-doc)})

(defn apartment->ryhti
  "Takes legacy apartment (huoneisto) and returns Ryhti version."
  [apt]
  (let [apt-type (some->> apt :huoneistoTyyppi :value
                          #{"asuinhuoneisto" "toimitila"})
        kitchen  (some->> apt :keittionTyyppi :value
                          #{"keittio" "keittokomero" "keittotila" "tupakeittio"})
        utils    (into {} (for [[old-k new-k] {:WCKytkin                :wc
                                               :ammeTaiSuihkuKytkin     :suihku
                                               :saunaKytkin             :sauna
                                               :parvekeTaiTerassiKytkin :parveke
                                               :lamminvesiKytkin        :lammin-vesi}
                                :let          [{v :value :as m} (m-or-nil apt old-k)]
                                :when         m]
                            {new-k (revalue m (if v "1" ""))}))]
    (-> (select-keys apt (cond-> [:porras :huoneistonumero :jakokirjain :huoneluku
                                  :huoneistoala :pysyvaHuoneistotunnus :muutostapa]
                           apt-type (conj :huoneistoTyyppi)
                           kitchen  (conj :keittionTyyppi)))
        (wrap-ryhti-ids :apartmentKey)
        (merge utils))))

(defn- target-type
  "Value for `:kaytto.kohteen-laji`. Either `rakennus`, `rakennelma`, `etvra` or nil. First
  resolved by operation and then fallback to document schema name matching."
  [ctx]
  (let [{schema-name :name op :op} (-> ctx :from-doc :schema-info)
        op-info                    (some-> op :name ops/get-operation-metadata)]
    (cond
      (:building op-info)                                            "rakennus"
      (:structure op-info)                                           "rakennelma"
      (= schema-name "erityista-toimintaa-varten-rakennettava-alue") "etvra")))

(defn- set-primary [ctx m]
  (assoc m :is-primary (ctx-wrap ctx true)))

(defmulti ^:private -fill (fn [_ctx k] k))

(defmethod -fill :default
  [ctx k]
  ;; Transform fill key not defined. Fallback to copy.
  (data-copy ctx k))

(defmethod -fill :ryhti
  ;; Ryhti ids for the document. Only added if missing.
  [ctx _]
  (data-set-ryhti-ids ctx nil :buildingKey :structureKey
                      :areaToBeBuiltForSpecificActivitiesKey))

(defmethod -fill :kaytto
  [ctx k]
  (let [builder (when-let [m (from-or-nil ctx :kaytto.rakentajaTyyppi)]
                  (revalue m (= "liiketaloudellinen" (:value m))))]
    (-> ctx
        (data-set :kaytto.kohteen-laji (some->> (target-type ctx) (ctx-wrap ctx)))
        (data-set :kaytto.rakentaja-tyyppi builder)
        (data-copy :kaytto.rakennusluokka)
        (data-copy :tyyppi k :erityista-toimintaa-tyyppi)
        (data-copy :kaytto.valiaikainenRakennusKytkin)
        ;; The same flag is used for structures
        (data-copy :tilapainenRakennelmaKytkin k :valiaikainenRakennusKytkin)
        (data-copy :kayttotarkoitus k :rakennelman-kayttotarkoitus))))

(defmethod -fill :pysyva-rakennustunnus
  [ctx k]
  (data-copy ctx :valtakunnallinenNumero k))

(defmethod -fill :mitat
  [ctx k]
  (-> ctx
      ;; Available if `from-doc` is etvra.
      (data-copy :pintaAla k :pintaAla)))

(defmethod -fill :sijaintitiedot
  [ctx k]
  (-> ctx
      (data-set-ryhti-ids :sijaintitiedot.osoite :addressKey)
      (data-copy :osoite.lahiosoite k :osoite.katu)
      (data-copy :osoite.postinumero k :osoite.postinumero)
      (data-copy :osoite.postitoimipaikannimi k :osoite.postitoimipaikannimi)
      (data-copy :osoite.osoitenumero k :osoite.osoitenumero)
      (data-copy :osoite.osoitenumero2 k :osoite.osoitenumero2)
      (data-copy :osoite.jakokirjain k :osoite.jakokirjain)
      (data-copy :osoite.jakokirjain2 k :osoite.jakokirjain2)
      (data-copy :osoite.porras k :osoite.porras)
      (data-copy :osoite.huoneisto k :osoite.huoneisto)
      ;; We assume that the site is in the application property
      (data-set-ryhti-ids :sijaintitiedot.sijainti :buildingObjectLocationDataKey)
      (data-set-ryhti-ids :sijaintitiedot.sijainti.hallinnollinen-sijainti-yksikko
                          :administrativeLocationUnitKey)
      (data-set k :sijainti.hallinnollinen-sijainti-yksikko.kiinteistotunnus
                (some->> ctx :command :application
                         :propertyId (ctx-wrap ctx)))))

(defmethod -fill :rakennuskohteen-osa.huoneistot
  [ctx _]
  ;; We overwrite the existing apartments
  (let [apts (some->> (from-or-nil ctx :huoneistot)
                      seq
                      (map (fn [[k apt]]
                             [k (apartment->ryhti apt)]))
                      (into {}))]
    (data-set ctx :rakennuskohteen-osa.huoneistot apts)))

(defmethod -fill :rakennuskohteen-osa.energiatiedot
  [ctx k]
  ;; We overwrite the existing lammitystapa and lammanlahde.
  (let [heating-type   (some-<>> (from-or-nil ctx :lammitys.lammitystapa)
                                 (hash-map :lammitystapatyyppi)
                                 (set-primary ctx)
                                 (wrap-ryhti-ids <> :heatingMethodKey)
                                 (hash-map :0))
        source         (from-or-nil ctx :lammitys.lammonlahde)
        other          (from-or-nil ctx :lammitys.muu-lammonlahde)
        heating-source (some-<>> (util/assoc-when nil
                                                  :lammonlahdetyyppi source
                                                  :muu-lammonlahde other)
                                 (set-primary ctx)
                                 (wrap-ryhti-ids <> :heatingEnergySourceKey)
                                 (hash-map :0))]
    (-> ctx
        (data-set-ryhti-ids k :energyDataKey)
        (data-copy :luokitus.energialuokka k :energialuokka)
        (data-set k :lammitystapa heating-type)
        (data-set k :lammonlahde heating-source))))

(defmethod -fill :rakennuskohteen-osa.tekniset-tiedot
  [ctx k]
  (let [sewer            (or (from-or-nil ctx :verkostoliittymat.viemariKytkin)
                             (from-or-nil ctx :varusteet.liitettyJatevesijarjestelmaanKytkin))
        water            (from-or-nil ctx :verkostoliittymat.vesijohtoKytkin)
        electricity      (from-or-nil ctx :verkostoliittymat.sahkoKytkin)
        gas              (from-or-nil ctx :verkostoliittymat.maakaasuKytkin)
        cable            (from-or-nil ctx :verkostoliittymat.kaapeliKytkin)
        sewer-kind       (when (:value sewer) (ctx-wrap ctx "verkostoliittyma"))
        water-kind       (when (:value water) (ctx-wrap ctx "verkostoliittymat")) ;; Not a typo
        connections      (some->> {:jatevesi             sewer       :talousvesi water
                                   :sahko                electricity :kaasu      gas
                                   :tietoliikennekaapeli cable}
                                  (util/filter-map-by-val :value)
                                  not-empty
                                  (map-indexed (fn [i [k m]]
                                                 [(util/make-kw i)
                                                  (wrap-ryhti-ids {:liitetty               m
                                                                   :verkostoliittymatyyppi (revalue m (name k))}
                                                                  :networkConnectionKey)]))
                                  (into {}))
        util-electricity (from-or-nil ctx :varusteet.sahkoKytkin)
        util-gas         (from-or-nil ctx :varusteet.kaasuKytkin)
        util-sewer       (from-or-nil ctx :varusteet.viemariKytkin)
        util-water       (from-or-nil ctx :varusteet.vesijohtoKytkin)
        util-lift        (from-or-nil ctx :varusteet.hissiKytkin)
        util-ac          (from-or-nil ctx :varusteet.koneellinenilmastointiKytkin)
        util-warm        (from-or-nil ctx :varusteet.lamminvesiKytkin)
        util-solar       (from-or-nil ctx :varusteet.aurinkopaneeliKytkin)
        ac-kind          (when (:value util-ac)
                           {:0 (wrap-ryhti-ids (set-primary ctx {:ilmanvaihtotyyppi (ctx-wrap ctx "koneellinen-tulo-ja-poistoilmanvaihto")})
                                               :ventilationMethodKey)})
        saunas           (from-or-nil ctx :varusteet.saunoja)
        utils            (->> {:aurinkopaneeli  util-solar :kaasu                   util-gas
                               :sauna           saunas     :vesijohto               util-water
                               :lammin-vesi     util-warm  :hissi                   util-lift
                               :jatevesiviemari util-sewer :koneellinen-ilmastointi util-ac
                               :sahko           util-electricity}
                              (util/filter-map-by-val :value)
                              not-empty
                              (map-indexed (fn [i [k m]]
                                             [(util/make-kw i)
                                              (wrap-ryhti-ids {:varustetyyppi (revalue m (name k))
                                                               :maara         (cond
                                                                                (true? (:value m))  (revalue m "1")
                                                                                (false? (:value m)) (revalue m "")
                                                                                :else               m)}
                                                              :equipmentKey)]))
                              (into {}))]
    (-> ctx
        (data-set-ryhti-ids k :buildingServicesEngineeringDataKey)
        ;; We assume that the corresponding network connection implies the top-level
        ;; handling as well.
        (data-set k :jatevesienkasittely sewer-kind)
        (data-set k :talousvesityyppi water-kind)
        (data-set k :verkostoliittymat connections)
        (data-set k :ilmanvaihtotavan-laji ac-kind)
        (data-set k :varuste utils))))

(defmethod -fill :rakennuskohteen-osa.materiaalitiedot
  [ctx k]
  (let [method (when-let [{v :value :as m} (from-or-nil ctx :rakenne.rakentamistapa)]
                 (case v
                   "elementti"      m
                   "paikalla" (revalue m "paikalla-tehty")
                   nil))
        fire     (when-let [m (from-or-nil ctx :luokitus.paloluokka)]
                   (when (util/includes-as-kw? schemas/ryhti-paloluokat (:value m))
                     m))
        material (when-let [m (from-or-nil ctx :rakenne.kantavaRakennusaine)]
                   (-<> {:rakennusaine (cond-> m
                                         (= "muuRakennusaine" (:value m)) (revalue "other"))}
                        (util/assoc-when :muuRakennusaine (from-or-nil ctx :rakenne.muuRakennusaine))
                        (wrap-ryhti-ids :buildingMaterialOfLoadBearingStructuresKey)
                        (set-primary ctx <>)
                        (hash-map :0 <>)))
        facade   (when-let [m (from-or-nil ctx :rakenne.julkisivu)]
                   (-<> {:julkisivumateriaali (cond-> m
                                                (= "muuMateriaal" (:value m)) (revalue "other"))}
                        (util/assoc-when :muuMateriaali (from-or-nil ctx :rakenne.muuMateriaali))
                        (wrap-ryhti-ids :facadeMaterialKey)
                        (set-primary ctx <>)
                        (hash-map :0 <>)))]
    (-> ctx
        (data-set k :rakentamistapa method)
        (data-set k :paloluokka fire)
        (data-set k :kantavaRakennusaine material)
        (data-set k :julkisivu facade))))

(defmethod -fill :rakennuskohteen-osa.kayttotiedot
  [ctx k]
  (let [function (when-let [m (from-or-nil ctx :kaytto.kayttotarkoitus)]
                   {:0 (wrap-ryhti-ids (set-primary ctx {:tyyppi m}) :purposeKey)})]
    (-> ctx
        (data-set k :kayttotarkoitus function)
        (data-copy :varusteet.kokoontumistilanHenkilomaara k :kayttajamaara))))

(defmethod -fill :rakennuskohteen-osa.sisaankaynti
  [ctx k]
  (let [entrance (when-let [m (from-or-nil ctx :kaytto.esteetonKytkin)]
                   (when (:value m)
                     {:0 (set-primary ctx (wrap-ryhti-ids {:esteeton m}
                                                          :entranceKey))}))]
    (data-set ctx k entrance)))

(defmethod -fill :rakennuskohteen-osa.vaestonsuoja
  [ctx k]
  (let [shelter (when-let [m (from-or-nil ctx :varusteet.vaestonsuoja)]
                  {:0 (wrap-ryhti-ids {:kapasiteetti m}
                                      :civilDefenceShelterKey)})]
    (data-set ctx k shelter)))

(defmethod -fill :rakennuskohteen-osa.ulkokuorentiedot
  [ctx k]
  (-> ctx
      (data-copy :mitat k)
      ;; Structure kokonaisala
      (data-copy :kokonaisala k :kokonaisala)))

(defmethod -fill :rakennuskohteen-osa.kokoontumistilat
  [ctx k]
  (let [kokoontumistila (when-let [m (from-or-nil ctx :varusteet.kokoontumistilanHenkilomaara)]
                          {:0 (wrap-ryhti-ids {:henkilomaara m}
                                              :assemblyFacilityKey)})]
    (data-set ctx k kokoontumistila)))

(defmethod -fill :rakennuskohteen-osa
  [ctx k]
  (-> ctx
      (data-set-ryhti-ids k :buildingSectionKey :structureSectionKey)
      (data-copy :mitat.kellarinpinta-ala k :sisatilat.kellariala)
      (-fill :rakennuskohteen-osa.kokoontumistilat)
      (-fill :rakennuskohteen-osa.ulkokuorentiedot)
      (-fill :rakennuskohteen-osa.tekniset-tiedot)
      (-fill :rakennuskohteen-osa.huoneistot)
      (-fill :rakennuskohteen-osa.energiatiedot)
      (-fill :rakennuskohteen-osa.materiaalitiedot)
      (-fill :rakennuskohteen-osa.kayttotiedot)
      (-fill :rakennuskohteen-osa.sisaankaynti)
      (-fill :rakennuskohteen-osa.vaestonsuoja)))

(defmethod -fill :rakennuksenOmistajat
  [ctx k]
  ;; The only difference between legacy and Ryhti owners is the ryhti-ids.
  (let [owners (some->> (from-or-nil ctx :rakennuksenOmistajat)
                        (util/map-values #(wrap-ryhti-ids % :buildingObjectOwnerKey)))]
    (data-set ctx k owners)))

(defn- fill-ryhti-doc-data
  "Fills `ryhti-doc` with legacy `source-doc` data."
  [command source-doc ryhti-doc]
  {:pre [(ryhti-doc? ryhti-doc)]}
  (let [ctx {:command  command
             :from-doc source-doc
             :to-doc   ryhti-doc}]
    (->> (model/get-document-schema ryhti-doc)
         :body
         (mapv (comp keyword :name))
         (reduce (fn [acc schema-key]
                   (-fill acc schema-key))
                 ctx)
         :to-doc)))

(defn- validation-errors
  "Validates given document and returns errors or nil. Lower level issues (warnings, tips)
  are ignored."
  [application document]
  (->> (model/validate application document)
       (filter (util/fn-> :result first (= :err)))
       seq))

(defn- make-ryhti-doc
  "Transforms `from-doc` into a corresponding Ryhti document. Returns either the Ryhti
  document or nil, if the transform is not needed/supported/successful."
  [{:keys [application] :as command} from-doc]
  (when-let [schema (ryhti-schema from-doc)]
    (log/with-logging-context {:applicationId (:id application)
                               :documentId    (:id from-doc)}
      (try
        (let [
              schema-flags (tools/resolve-schema-flags command)
              ryhti-doc    (model/new-document schema schema-flags
                                               (:created from-doc))
              ;; Operations have been enriched with `:ryhti` earlier.
              op           (some->> from-doc :schema-info :op :id
                                    (app-utils/operation-by-id application))
              doc-meta     (:meta from-doc)
              to-doc       (cond-> {:id          (:id from-doc)
                                    :created     (:created from-doc)
                                    :schema-info (:schema-info ryhti-doc)
                                    :data        (:data ryhti-doc)}
                             op       (assoc-in [:schema-info :op] op)
                             doc-meta (assoc :meta doc-meta))
              old-err-n    (count (validation-errors application from-doc))
              result       (fill-ryhti-doc-data command from-doc to-doc)
              errors       (validation-errors application result)]
          (if (> (count errors) old-err-n)
            (log/error "Ryhti transform failed due to validation:" errors)
            result))
        (catch Exception e
          (log/error e "Ryhti transform failed:" (ex-message e) (ex-data e)))))))

(defn- array-ryhti-updates [prefix xs]
  (some->> xs
           (keep-indexed (fn [i {:keys [ryhti]}]
                           (when ryhti
                             [(util/kw-path prefix i :ryhti) ryhti])))
           seq
           (into {})))

(defn- commit-to-mongo!
  "Similar to `action/update-application` but more straight-forward, without `:data`
  requirement."
  ([{:keys [application]} query changes]
   {:pre [(:id application)]}
   (mongo/update-by-query :applications
                          (assoc query :_id (:id application))
                          changes))
  ([command changes]
   (commit-to-mongo! command {} changes)))

(defn- non-document-ryhti-updates! [{:keys [application] :as command}]
  (let[root-fields (assoc (ryhti-util/ryhti-id :permitApplicationKey
                                               :constructionProjectKey)
                          :ryhti-data-2026 true)
       ryhti-ks    [:buildingObjectIssueKey :constructionActionKey]

       primary-op    (-> application :primaryOperation (add-ryhti-ids ryhti-ks))
       secondary-ops (some->> application :secondaryOperations seq
                              (mapv #(add-ryhti-ids % ryhti-ks)))
       attachments   (some->> application :attachments
                              (mapv #(add-ryhti-ids % :attachmentDocumentKey)))
       updates       (->> (assoc root-fields :primaryOperation.ryhti (:ryhti primary-op))
                          (merge (array-ryhti-updates :secondaryOperations secondary-ops)
                                 (array-ryhti-updates :attachments attachments))
                          (hash-map $set))]
    (commit-to-mongo! command updates)
    (update command :application merge
            root-fields {:primaryOperation primary-op}
            (when secondary-ops
              {:secondaryOperations secondary-ops})
            (when attachments
              (util/mmap attachments)))))

(defn- document-ryhti-id-keys [document]
  (some->> document model/get-document-schema
           :body
           (util/find-by-key :name "ryhti")
           :body
           (util/filter-by-key :type :ryhti-identifier)
           seq
           (mapv (comp keyword :name))))

(defn- add-missing-document-ryhti-ids
  "Adds top level Ryhti ids to document data and schema-info operation, if needed. Does not
  override existing `:ryhti` key. Returns either updated document or nil if the update is
  not needed."
  [{:keys [application]} document]
  (when-not (-> document :data :ryhti)
    (let [op-ryhti (some->> document :schema-info :op :id
                            (app-utils/operation-by-id application)
                            :ryhti)
          rks      (document-ryhti-id-keys document)]
      (when (or op-ryhti rks)
        (cond-> document
          rks      (update :data merge (wrap-ryhti-ids nil rks))
          op-ryhti (assoc-in [:schema-info :op :ryhti] op-ryhti))))))

(defn- process-document [command document]
  (let [doc-query {:documents {$elemMatch {:id (:id document)}}}
        new-doc     (make-ryhti-doc command document)
        updated-doc (when-not new-doc
                      (add-missing-document-ryhti-ids command document))]
    (cond
      new-doc     (commit-to-mongo! command doc-query
                                    (db/with-sheriff-note
                                      "LPK-8103 Ryhti transform" (:created command)
                                      {$set {:documents.$ new-doc}}
                                      ;; Do not store extra `:schema-info` fields.
                                      :document (update document :schema-info model/->schema-info)))
      updated-doc
      (commit-to-mongo! command doc-query
                        {$set (util/assoc-when
                                nil
                                :documents.$.data.ryhti (-> updated-doc :data :ryhti)
                                :documents.$.schema-info.op.ryhti (some-> updated-doc
                                                                          :schema-info
                                                                          :op :ryhti))}))
    (or new-doc updated-doc document)))

(defn command->ryhti!
  "Transforms application into corresponding Ryhti application, if needed. The application
  is persisted into mongo. If a document's schema is updated to Ryhti schema, the somewhat
  original document is stored into sheriff notes. Returns updated or untouched command."
  [{:keys [organization application created] :as command}]
  (if (and (some-> (force organization) :ryhti-data-2026-start-ts (< created))
           (transform-needed? application))
    (let [command (non-document-ryhti-updates! command)]
      (update-in command [:application :documents]
                 (util/fn->> (mapv (partial process-document command)))))
    command))

(defn building->ryhti
  "Takes a `building` data in KuntaGML format and returns the corresponding Ryhti
  data. `document` is the target Ryhti document."
  [application document building]
  (some->> (fill-ryhti-doc-data {:application application}
                                ;; For target-type.
                                {:schema-info (:schema-info document)
                                 :data        (tools/wrapped building)}
                                (dissoc document :data))
           :data
           tools/unwrapped))

(comment
  (defn test-doc
    "Simple REPL utility for testing document transformation."
    [app-id doc-id]
    (let [{org-id :organization
           :as    a} (mongo/by-id :applications app-id)
          d          (->> a :documents (util/find-by-id doc-id))
          rd         (make-ryhti-doc {:application  a
                                      :created      12345678
                                      :organization (delay (mongo/by-id :organizations org-id))}
                                     d)]
      (filter (util/fn-> :result first (= :err))
              (model/validate a rd)))))
