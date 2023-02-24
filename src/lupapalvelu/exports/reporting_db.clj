(ns lupapalvelu.exports.reporting-db
  "This namespace provides a representation of Lupapiste applications
  for the purpose of exporting the data to a reporting database. The
  data is based on the canonical representation (see
  eg. `application-to-canonical`, `katselmus-canonical`), which in
  turn is based on KuntaGML. The supported permit types are in
  `permit-types-for-reporting-db`."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.poikkeamis-canonical :as poik]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.drawing :as draw]
            [lupapalvelu.exports.reporting-db-schema :refer [DateString ApplicationReport]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-canonical :refer [verdict-canonical]]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.statement-schemas :as statement-schemas]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.coerce :as scc]
            [schema.core :as sc]
            [taoensso.timbre :refer [error info]]))

;;
;; Helpers
;;
(def strip (comp util/strip-empty-maps
                 util/strip-nils))

(def not-available (constantly nil))

(defn- sequentialize [xs]
  (if xs
    (util/sequentialize xs)
    []))

(defn- str->num
  "Change the given `keys-to-update` in `a-map` into numerical values,
  if possible. Otherwise set them to `nil`"
  [a-map keys-to-update]
  (util/update-values a-map
                      keys-to-update
                      #(util/->int % nil)))

(defn- context-canonical [context]
  @(:canonical context))

(defn get-katselmustieto [application]
  (ds/from-context (cons :context (common/review-path application))))


(def rakennusvalvonta-asia
  (ds/from-context [context-canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]))

(def r-osapuolet
  (ds/from-context [rakennusvalvonta-asia :osapuolettieto :Osapuolet]))

;;
;; The application report
;;

(def reporting-app-skeleton
  {:id (ds/access :id) ;; Application id
   :araFunding (ds/access :araFunding) ;; Does the project receive ARA funding?
   :state (ds/access :state) ;; The internal Lupapiste representation of state, ie. not a 1-to-1 mapping with KuntaGML
   :permitType (ds/access :permitType)
   :permitSubtype (ds/access :permitSubtype)
   :location-etrs-tm35fin (ds/access :location)
   :location-wgs84 (ds/access :location-wgs84)
   :address (ds/access :address)
   :propertyId (ds/access :propertyId)
   :organization (ds/access :organization)
   :municipality (ds/access :municipality)
   :stateChangeTs (ds/access :stateChangeTs) ;; The timestamp of the latest state change, also see `:state`
   :createdTs (ds/access :created)
   :submittedTs (ds/access :submitted)
   :modifiedTs (ds/access :modified)
   :projectDescription (ds/access :projectDescription)
   :newDimensions (ds/access :new-dimensions)
   :tosFunction (ds/access :tosFunction)
   :tosFunctionName (ds/access :tosFunctionName)
   :features (ds/access :features)

   :reviews (ds/array-from :reviews
                           {:id             (ds/access :review-id)
                            :type           (ds/access :review-type) ;; KatselmuksenlajiSelite: Liittyy toimenpiteeseen
                            :date           (ds/access :review-date) ;; Pitopvm
                            :reviewer       (ds/access :review-reviewer)
                            :verottajanTvLl (ds/access :review-verottajanTvLl)
                            :lasnaolijat    (ds/access :review-lasnaolijat)
                            :poikkeamat     (ds/access :review-poikkeamat)
                            :huomautukset   (ds/access :review-huomautukset)
                            :rakennukset    (ds/access :review-rakennukset)})

   :statements (ds/access :statements)
   :parties (ds/access :parties)
   :planners (ds/access :planners)
   :foremen (ds/access :foremen)
   :handlers (ds/access :handlers)

   ;; Päätös
   :verdicts (ds/array-from :verdicts
                            (ds/access :context))

   :operations (ds/array-from
                :operations
                {:id (ds/access :operation-id) ;; Lupapiste operation id
                 :operation (ds/access :operation)
                 :primary (ds/access :operation-primary?) ;; Is this the primary operation in Lupapiste
                 :kuvaus (ds/access :operation-description)
                 :nimi (ds/access :operation-name-fi) ;; The KuntaGML `kuvaus`, not the internal `:name`
                 :rakennus (ds/access :operation-building)
                 :rakennelma (ds/access :operation-structure)})

   :links (ds/access :links)
   :poikkeamat (ds/access :poikkeamat)
   :reservedArea (ds/access :varattava-pinta-ala)
   :placementPermit (ds/access :sijoitusLuvanTunniste)
   :workDates {:startDate (ds/access :alkuPvm)
               :endDate   (ds/access :loppuPvm)}})

;;
;; Accessors
;;

(defn- verdict-via-canonical
  "Builds a representation of the given verdict for reporting db API
  using mostly the canonical representation."
  [lang application verdict]
  (-> (:Paatos (verdict-canonical lang verdict application))
      (dissoc :paatosdokumentinPvm)
      (assoc
        :id (:id verdict)
        :kuntalupatunnus (vc/verdict-municipality-permit-id verdict))
      (update-in [:lupamaaraykset :maaraystieto]
                 (partial mapv (comp :sisalto :Maarays)))
      (update-in [:lupamaaraykset :vaadittuErityissuunnitelmatieto]
                 (partial mapv :VaadittuErityissuunnitelma))
      (update-in [:lupamaaraykset :vaadittuTyonjohtajatieto]
                 (partial mapv (comp :tyonjohtajaRooliKoodi :VaadittuTyonjohtaja)))
      (update-in [:lupamaaraykset :vaaditutKatselmukset]
                 (partial mapv :Katselmus))))

(defn ->statement [raw-statement]
  (-> {:id       (:id raw-statement)
       :lausunto (:text raw-statement)
       :puolto   (:status raw-statement)
       :antoTs   (:given raw-statement)
       :antaja   {:nimi (-> raw-statement :person :name)
                  :kuvaus (-> raw-statement :person :text)
                  :email (-> raw-statement :person :email)}}
      strip))

(defn ->statements [statements]
  (->> statements
       (filter #(statement-schemas/post-given-states (keyword (:state %))))
       (mapv ->statement)))

(defn- operation-name-fi [operation]
  (i18n/localize "fi" (str "operations." (:name operation))))

(defn- dissoc-superfluous-company-address [party]
  (util/safe-update-in party [:yritys] dissoc :postiosoite))

(defn- has-national-id-of [building-information]
  (let [national-id (-> building-information :tiedot :rakennustunnus :valtakunnallinenNumero)]
    (fn [building-from-buildings-array]
      (= (:nationalId building-from-buildings-array)
         national-id))))

(defn- building-coordinates
  "Gets the coordinates of `building-information`'s building from the
  application's `buildings` array, if available. The buildings are
  matched by the national id (VTJ-PRT)"
  [building-information context]
  (when-let [building (util/find-first (has-national-id-of building-information)
                                       (-> context :application :buildings))]
    {:location-etrs-tm35fin (:location building)
     :location-wgs84 (:location-wgs84 building)}))

(defn- add-building-coordinates [building-information context]
  (merge building-information
         (building-coordinates building-information context)))

(defn muu->value
  "In KuntaGML, there are several 'other' freetext choices, which bypass enumeration.
  If muu-key has non-blank value, we put that into the actual key, as reporting doesn't really care
  about KuntaGML enumeration."
  [context path muu-key value-key]
  {:pre [(map? context) (vector? path) (keyword? muu-key) (keyword? value-key)]}
  (let [muu-path    (conj (vec path) muu-key)
        actual-path (conj (vec path) value-key)]
    (if-let [muu-value (ss/trim (get-in context muu-path))]
      (if-not (ss/blank? muu-value)
        (-> context
            (assoc-in actual-path muu-value)
            (util/dissoc-in muu-path))
        context)
      context)))

(defn- operation-building [context]
  ;; We know that :rakennustieto is not sequential, i.e. there's only one building per operation
  (some-> ((ds/from-context [:context :rakennustieto :Rakennus]) context)
          (update :omistajatieto (comp (partial mapv #(-> %
                                                          (util/dissoc-in [:henkilo :henkilotunnus])
                                                          (util/dissoc-in [:henkilo :ulkomainenHenkilotunnus])))
                                       (partial mapv :Omistaja)
                                       sequentialize))
          (dissoc :alkuHetki :sijaintitieto :yksilointitieto)
          (set/rename-keys {:rakennuksenTiedot :tiedot :omistajatieto :omistajat})
          (util/safe-update-in [:omistajat] (partial mapv dissoc-superfluous-company-address))

          ;; Change various numerical values into actual numbers
          (util/safe-update-in [:tiedot :asuinhuoneistot :huoneisto]
                               (partial mapv #(str->num % [:huoneistoala :huoneluku])))
          (util/safe-update-in [:tiedot :varusteet] #(str->num % [:saunoja]))
          (str->num [:kokoontumistilanHenkilomaara])


          (muu->value [:tiedot :kantavaRakennusaine] :muuRakennusaine :rakennusaine)
          (muu->value [:tiedot :julkisivu] :muuMateriaali :julkisivumateriaali)
          (muu->value [:tiedot :lammonlahde] :muu :polttoaine)

          (update :tiedot #(str->num % [:energiatehokkuusluku :kellaripinta-ala :kerrosala
                                        :kerrosluku :kokonaisala :rakennusoikeudellinenKerrosala
                                        :tilavuus :kellarinpinta-ala]))
          (add-building-coordinates context)
          strip))

(defn- operation-structure [context]
  (some-> ((ds/from-context [:context :rakennelmatieto :Rakennelma]) context)
          (str->num [:kokonaisala :kokoontumistilanHenkilomaara])
          (dissoc :alkuHetki :loppuHetki :metatieto :sijaintitieto :yksilointitieto)
          strip))

(defn- operation-id [canonical-operation]
  (or (get-in canonical-operation [:rakennustieto :Rakennus :yksilointitieto])
      (get-in canonical-operation [:rakennelmatieto :Rakennelma :yksilointitieto])))

(defn- merge-data-from-canonical-operation [operation canonical-operations]
  (merge operation
         (util/find-first #(= (:id operation)
                              (operation-id %))
                          canonical-operations)))

(defn- merge-municipality-building-id
  "Municipality building id is not part of the canonical model; fetch it separately here"
  [operation context]
  (if-let [kunnallinen (some->> context
                                :application
                                :documents
                                (util/find-first #(= (-> operation :id)
                                                     (-> % :schema-info :op :id)))
                                :data
                                :kunnanSisainenPysyvaRakennusnumero
                                :value)]
    (assoc-in operation
              [:rakennustieto :Rakennus :rakennuksenTiedot :rakennustunnus :kunnanSisainenPysyvaRakennusnumero]
              kunnallinen)
    operation))

(defn- operations [context]
  (let [operations (conj (-> context :application :secondaryOperations)
                         (-> context :application :primaryOperation (assoc :primary? true)))
        canonical-operations ((ds/from-context [rakennusvalvonta-asia :toimenpidetieto
                                                sequentialize
                                                (partial mapv :Toimenpide)])
                              context)]
    (strip (mapv #(-> %
                      (merge-data-from-canonical-operation canonical-operations)
                      (merge-municipality-building-id context))
                 operations))))

(defn- ->planner [planner]
  (-> planner
      :Suunnittelija
      (str->num [:kokemusvuodet :valmistumisvuosi])
      dissoc-superfluous-company-address
      (util/dissoc-in [:henkilo :henkilotunnus])
      (util/dissoc-in [:henkilo :ulkomainenHenkilotunnus])))

(defn- ->foreman [foreman]
  (-> foreman
      :Tyonjohtaja
      (update :sijaistustieto get :Sijaistus)
      (update :vastattavaTyotieto (comp (partial map (comp :vastattavaTyo
                                                           :VastattavaTyo))
                                        sequentialize))
      (set/rename-keys {:sijaistustieto     :sijaistus
                        :vastattavaTyotieto :vastattavatTyot})
      (dissoc :vastattavatTyotehtavat)
      dissoc-superfluous-company-address
      (util/dissoc-in [:henkilo :henkilotunnus])
      (util/dissoc-in [:henkilo :ulkomainenHenkilotunnus])
      (str->num [:kokemusvuodet :valmistumisvuosi :valvottavienKohteidenMaara])))

(defn- get-link
  "Get the data for the app that is linked to app with `app-id`"
  [{:keys [link] :as link-data} app-id]
  (let [linked-id (->> link (remove #(util/=as-kw % app-id)) first)]
    {:id (name linked-id)
     :permitType (:apptype (get link-data (keyword linked-id)))}))

(defn- app-links [context]
  (let [app-id (-> context :application :id keyword)]
    (->> (:app-links context)
         (filter #(get % app-id))
         (map #(get-link % app-id)))))

(defn- ->party [party]
  (-> party
      :Osapuoli
      dissoc-superfluous-company-address
      (util/dissoc-in [:henkilo :henkilotunnus])
      (util/dissoc-in [:henkilo :ulkomainenHenkilotunnus])))

(defn- ->ya-party [party]
  (letfn [(ya-party [party]
            (util/assoc-when
              {}
              :kuntaRooliKoodi (:rooliKoodi party)
              :henkilo (get-in party [:henkilotieto :Henkilo])
              :yritys (-> party
                          (get-in [:yritystieto :Yritys])
                          (dissoc :postiosoite)
                          (util/dissoc-in [:postiosoitetieto :Postiosoite]))
              :suoramarkkinointikieltoKytkin (:suoramarkkinointikieltoKytkin party)))

          (vastuuhenkilo [party]
            (let [vastuuhenk (:Vastuuhenkilo party)]
              (-> (ya-party (:Osapuoli party))
                  (util/assoc-when :kuntaRooliKoodi (:rooliKoodi vastuuhenk))
                  (assoc :henkilo (-> vastuuhenk
                                      (dissoc :rooliKoodi :sukunimi :etunimi :osoitetieto)
                                      (assoc :nimi (select-keys vastuuhenk [:etunimi :sukunimi])
                                             :osoite (get-in vastuuhenk [:osoitetieto :osoite]))
                                      (set/rename-keys {:puhelinnumero :puhelin}))))))]
    (if (:Vastuuhenkilo party)
      (vastuuhenkilo party)
      (ya-party (:Osapuoli party)))))

(defn- ->review-building [building]
  (-> building
      :KatselmuksenRakennus
      (update :kayttoonottoKytkin boolean)
      (update :jarjestysnumero str)
      (dissoc :muuTunnustieto)
      (util/assoc-when :toimenpideId (->> (get-in building [:KatselmuksenRakennus :muuTunnustieto])
                                          (util/find-first #(= (get-in % [:MuuTunnus :sovellus])
                                                               "toimenpideId"))
                                          (#(get-in % [:MuuTunnus :tunnus]))))))

;; Copy paste from lupapalvelu.pate.pdf starts
(defn- update-sum-field [result target field sum-fn]
  (update result field (fn [v]
                         (sum-fn (or v 0)
                                 (util/->double (get target field 0))))))

(defn- update-sum-map [result target fields sum-fn]
  (reduce (fn [acc field]
            (update-sum-field acc target field sum-fn))
          result
          fields))

(defn new-dimensions
  "NOTE that this is mostly copy-paste from lupapalvelu.pate.pdf/dimensions.
  Map of :kerrosala, :rakennusoikeudellinenKerrosala, :kokonaisala
  and :tilavuus keys. The values are a _sum_ of the corresponding
  fields in the supported document schemas. Values are strings and
  doubles are shown with one decimal. Nil if none of the application
  documents is supported. For purkaminen, the sums are nonpositive."
  [documents]
  ;; schema name - data path
  (let [supported {:uusiRakennus                             {:key :mitat :sum-fn +}
                   :uusi-rakennus-ei-huoneistoa              {:key :mitat :sum-fn +}
                   :rakennuksen-laajentaminen                {:key :laajennuksen-tiedot.mitat
                                                              :sum-fn +}
                   :rakennuksen-laajentaminen-ei-huoneistoja {:key :laajennuksen-tiedot.mitat
                                                              :sum-fn +}
                   :purkaminen                               {:key :mitat
                                                              :sum-fn -}}
        schemas   (-> supported keys set)
        fields    [:kerrosala :rakennusoikeudellinenKerrosala
                   :kokonaisala :tilavuus]]
    (some->> documents
             (filter (util/fn->> :schema-info :name keyword (contains? schemas)))
             (reduce (fn [acc {:keys [data schema-info]}]
                       (let [{:keys [key sum-fn]} (->> schema-info :name keyword
                                                       (get supported))]
                         (update-sum-map acc
                                         (tools/unwrapped (get-in data (util/split-kw-path key)))
                                         fields
                                         sum-fn)))
                     {}))))
;; Copy paste from lupapalvelu.pate.pdf ends

(defn common-accessors [application lang]
  {:id (ds/from-context [:application :id])
   :address (ds/from-context [:application :address])
   :propertyId (ds/from-context [:application :propertyId])
   :organization (ds/from-context [:application :organization])
   :municipality (ds/from-context [:application :municipality])
   :araFunding (ds/from-context [:application #(domain/get-document-by-name % "hankkeen-kuvaus")
                                 :data tools/unwrapped :rahoitus]
                                false)
   :context (ds/from-context [:context])
   :created (ds/from-context [:application :created])
   :submitted (ds/from-context [:application :submitted])
   :location (ds/from-context [:application :location])
   :location-wgs84 (ds/from-context [:application :location-wgs84])
   :features (ds/from-context
               [:application :drawings (partial filter :geometry-wgs84) (partial keep draw/drawing->feature) vec]
               [])
   :modified (ds/from-context [:application :modified])
   :new-dimensions (ds/from-context [:application :documents new-dimensions])
   :tosFunction (ds/from-context [:application :tosFunction])
   :tosFunctionName (ds/from-context [:application (fn [{:keys [history tosFunction]}]
                                                     (->> history
                                                          (keep :tosFunction)
                                                          (util/find-by-key :code tosFunction)
                                                          :name))])

   :operations operations
   :operation (ds/from-context [:context :name])
   :operation-id (ds/from-context [:context :id])
   :operation-description (ds/from-context [:context :description])
   :operation-name-fi (ds/from-context [:context operation-name-fi])
   :operation-building operation-building
   :operation-primary? (ds/from-context [:context :primary?] false)
   :operation-structure operation-structure
   :parties (constantly [])
   :planners (constantly [])
   :foremen (constantly [])
   :handlers (ds/from-context [:application :handlers
                               #(mapv (fn [{:keys [name firstName lastName]}]
                                        {:nimi  {:etunimi firstName :sukunimi lastName}
                                         :rooli (or (:fi name) "  ")})
                                      %)
                               strip])

   :state (ds/from-context [:application :state])
   :stateChangeTs (ds/from-context [:application :history (partial filterv :state) last :ts])
   :statements (ds/from-context [:application :statements ->statements]
                                [])
   :permitType (ds/from-context [:application :permitType])
   :permitSubtype (ds/from-context [:application :permitSubtype])
   :varattava-pinta-ala not-available
   :sijoitusLuvanTunniste not-available
   :alkuPvm not-available
   :loppuPvm not-available
   :reviews not-available
   :verdicts (fn [ctx]
               ((ds/from-context [:application vc/all-published-verdicts
                                  (partial map (partial verdict-via-canonical (:lang ctx) application))
                                  (partial mapv strip)]
                                 [])
                ctx))
   :links app-links})

(defmulti type-specific-app-accessors {:argslists '([application lang])}
  (fn [application _] (keyword (:permitType application))))

(defmethod type-specific-app-accessors :R [application lang]
  {:projectDescription    (ds/from-context [context-canonical (partial common/description application)])
   :poikkeamat            (ds/from-context [context-canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto
                                            :RakennusvalvontaAsia :asianTiedot :Asiantiedot
                                            :vahainenPoikkeaminen])

   :parties (ds/from-context [r-osapuolet :osapuolitieto
                              sequentialize (partial mapv ->party)
                              strip]
                             [])
   :planners (ds/from-context [r-osapuolet :suunnittelijatieto
                               sequentialize (partial mapv ->planner)
                               strip]
                              [])
   :foremen (ds/from-context [r-osapuolet :tyonjohtajatieto
                              sequentialize (partial mapv ->foreman)
                              strip]
                             [])

   :reviews               (ds/from-context
                            [:application :tasks (util/fn->>
                                                   (map #(assoc (common/review->canonical application % {:lang lang}) :id (:id %)))
                                                   (remove #(ss/blank? (get-in % (conj (common/review-path application) :pitoPvm))))
                                                   (vec))]
                            [])
   :review-id             (ds/from-context [:context :id])
   :review-date           (ds/from-context [(get-katselmustieto application) :pitoPvm])
   :review-type           (ds/from-context [(get-katselmustieto application) :katselmuksenLaji])
   :review-reviewer       (ds/from-context [(get-katselmustieto application) :pitaja])
   :review-verottajanTvLl (ds/from-context [(get-katselmustieto application) :verottajanTvLlKytkin] false)
   :review-lasnaolijat    (ds/from-context [(get-katselmustieto application) :lasnaolijat])
   :review-poikkeamat     (ds/from-context [(get-katselmustieto application) :poikkeamat])
   :review-huomautukset   (ds/from-context [(get-katselmustieto application) :huomautukset :huomautus])
   :review-rakennukset    (ds/from-context [(get-katselmustieto application) :katselmuksenRakennustieto
                                            sequentialize (partial mapv ->review-building)
                                            strip]
                                           [])})

(defmethod type-specific-app-accessors :P [application lang]
  (let [conf    (poik/get-poikkeamis-conf (:permitSubtype application))
        parties (ds/from-context (concat [context-canonical] (:asia-path conf) [:osapuolettieto :Osapuolet]))]
    {:projectDescription (ds/from-context [context-canonical (partial common/description application)])
     :poikkeamat         (ds/from-context [context-canonical (partial poik/poikkeamat application)])

     :parties            (ds/from-context [parties :osapuolitieto
                                           sequentialize (partial mapv ->party)
                                           strip]
                                          [])
     :planners           (ds/from-context [parties :suunnittelijatieto
                                           sequentialize (partial mapv ->planner)
                                           strip]
                                          [])
     :foremen            (ds/from-context [parties :tyonjohtajatieto
                                           sequentialize (partial mapv ->foreman)
                                           strip]
                                          [])}))

(defmethod type-specific-app-accessors :YA [{:keys [primaryOperation] :as application} lang]
  (let [ya-element-type (get common/ya-operation-type-to-schema-name-key (keyword (:name primaryOperation)))
        ya-context-path [context-canonical :YleisetAlueet :yleinenAlueAsiatieto ya-element-type]
        no-pitopvm?     (fn [canonical]
                          (ss/blank? (get-in canonical (conj (common/review-path application) :pitoPvm))))]
    {:projectDescription    (ds/from-context [context-canonical (partial common/description application)])
     :varattava-pinta-ala   (ds/from-context (conj ya-context-path :pintaala))
     :sijoitusLuvanTunniste (ds/from-context (conj ya-context-path :sijoituslupaviitetieto :Sijoituslupaviite :tunniste))
     :alkuPvm               (ds/from-context (conj ya-context-path :alkuPvm))
     :loppuPvm              (ds/from-context (conj ya-context-path :loppuPvm))
     :parties               (ds/from-context (conj ya-context-path :osapuolitieto
                                                   sequentialize (partial mapv ->ya-party)
                                                   strip)
                                             [])

     :reviews               (ds/from-context
                              [:application :tasks (util/fn->>
                                                     (map #(assoc (common/review->canonical application % {:lang lang}) :id (:id %)))
                                                     (remove no-pitopvm?)
                                                     (vec))]
                              [])
     :review-id             (ds/from-context [:context :id])
     :review-date           (ds/from-context [(get-katselmustieto application) :pitoPvm])
     :review-type           (ds/from-context [(get-katselmustieto application) :katselmuksenLaji])
     :review-reviewer       (ds/from-context [(get-katselmustieto application) :pitaja])
     :review-lasnaolijat    (ds/from-context [(get-katselmustieto application) :lasnaolijat])
     :review-poikkeamat     (ds/from-context [(get-katselmustieto application) :poikkeamat])
     :review-huomautukset   (ds/from-context [(get-katselmustieto application) :huomautustieto :Huomautus])
     ;; keys from skeleton that don't apply to YA
     ;; another option would be to 'define skeleton' per permit type
     ;; but at least this way we can see some errors from test and catch possible typos etc :)
     :poikkeamat            not-available
     :review-rakennukset    not-available
     :review-verottajanTvLl not-available
     }))

(defn- skeleton
  "Define the shape of the reporting result by selecting `fields` from
  `app-skeleton`. if `fields` is empty or `nil`, the entire
  `app-skeleton` is returned."
  [app-skeleton fields]
  (cond-> app-skeleton
    (seq fields) (select-keys (conj fields :id))))

(defn- datefixer  [schema]
  (when (= schema DateString)
    #(date/iso-date % :local)))

(defn- fix-dates
  "Cononical stores dates as `date/xml-date` results that contain timezone offset
  information. If the `fields` is given only those fields are selected from the schema."
  [data fields schema]
  ((scc/coercer (assoc (cond-> schema
                         (seq fields) (select-keys fields))
                       ;; Unknown fields are ignored
                       sc/Keyword sc/Any)
                datefixer) data))

(defn ->reporting-result [application app-links lang & [fields]]
  {:pre [(some? application)]}
  (try
    (let [result (-> (skeleton reporting-app-skeleton fields)
                     (ds/build-with-skeleton
                       {:application application
                        :canonical   (delay
                                       (when-not (:infoRequest application)
                                         (common/application->canonical application lang)))
                        :app-links   app-links
                        :lang        lang}
                       (merge
                         (common-accessors application lang)
                         (type-specific-app-accessors application lang)))
                     (fix-dates fields ApplicationReport))]
      (if-let [validation-error (-> (skeleton ApplicationReport fields) (sc/check result))]
        (error "Validation error for application reporting export:" (str validation-error))
        result))
    (catch Exception e
      (error e "Caught exception while reporting application"))))

(def permit-types-for-reporting-db ["R" "P" "YA"])

(defn applications-to-report
  "Fetch applications of relevant permit types that are modified
  between `start-ts` and `end-ts`"
  [start-ts end-ts]
  (let [query {$and [{$or [{:modified {$gte start-ts
                                       $lte end-ts}}
                           {:reportingDbOverrideTs {$gte start-ts
                                                    $lte end-ts}}]}
                     {:permitType {$in permit-types-for-reporting-db}
                      :infoRequest false
                      :primaryOperation.id {$exists true}}]}]
    (map :id (mongo/select :applications query {:_id 1}))))

(defn- links-for-app
  [app-id]
  (mongo/select :app-links {:link app-id}))

(defn- ->fields [fields-param-string]
  (mapv keyword
        (ss/split fields-param-string #",")))

(defn- links-needed? [fields]
  (or (empty? fields)
      (some #(= :links %) fields)))

(defn report-application! [id fields]
  (logging/with-logging-context {:applicationId id}
    (let [fields (->fields fields)]
      (when-let [application (domain/get-application-no-access-checking {:_id id :infoRequest false})]
        (->reporting-result
          application
          (when (links-needed? fields)
            (links-for-app id))
          "fi"
          fields)))))

(defn applications
  "Returns a reporting database representation of applications whose
  `modified` timestamps are between `start-ts` and `end-ts`, inclusive."
  [start-ts end-ts]
  {:pre [(number? start-ts) (number? end-ts)]}
  (let [app-ids (applications-to-report start-ts end-ts)]
    (->> app-ids
         (map #(report-application! % nil))
         (remove nil?))))


;;
;; Forcing reporting db force
;;
(defn reporting-db-timestamp-update [timestamp]
  {$set {:reportingDbOverrideTs timestamp}})

(defn force-reporting-db-update-by-query!
  "Updates a special timestamp in applications matching `query`,
  forcing the next reporting database batchrun to fetch the app"
  [query timestamp]
  (mongo/update-by-query :applications
                         query
                         (reporting-db-timestamp-update timestamp)))

;; For running from command line
(defn force-reporting-db-update [query-string]
  (mount/start)
  (let [ts (now)
        n-updated (force-reporting-db-update-by-query! (edn/read-string query-string) ts)]
    (info "force-reporting-db-update updated" n-updated "applications to timestamp: " ts))
  (mount/stop))
