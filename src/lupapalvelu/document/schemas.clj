(ns lupapalvelu.document.schemas
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [iso-country-codes.countries :as countries]
            [lupapalvelu.building-types :as building-types]
            [lupapalvelu.document.flags :as flags]
            [lupapalvelu.document.schema-validation :as schema-validation]
            [lupapalvelu.document.tools :as t :refer [body schema-body-without-element-by-name wrapped]]
            [lupapalvelu.low-carbon-assessment.config :as low-carbon]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [lupapalvelu.states :as states]
            [lupapalvelu.user-enums :as user-enums]
            [lupapiste-commons.usage-types :as usages]
            [sade.shared-schemas :as shared]
            [sade.util :as util]))

(defn update-in-body
  "Updates k in given body path with update function f. Body is vector of maps, path is vector of strings."
  [body path k f]
  (mapv
    (fn [body-part]
      (if (= (name (first path)) (:name body-part))
        (if (seq (rest path))
          (update body-part :body update-in-body (rest path) k f)
          (update body-part k f))
        body-part))
    body))

(defn accordion-field-emitters
  "Adds :accordionUpdate emitters to paths"
  [body paths]
  (if (seq paths)
    (reduce
      (fn [body field-path]
        (update-in-body body field-path :emit #(conj % :accordionUpdate)))
      body
      paths)
    body))

;;
;; Register schemas
;;

(defonce ^:private registered-schemas (atom {}))

(defn get-all-schemas [] @registered-schemas)
(defn get-schemas [version] (get @registered-schemas version))

(defn get-hakija-schema-names [schema-version]
  (let [schemas (get-schemas schema-version)]
    (assert schemas)
    (->> schemas
         (map #(when (= :hakija (-> % val :info :subtype keyword))
                 (-> % val :info :name)))
         (filter identity)
         set)))

(def info-keys #{:name :type :subtype :version
                 :i18name :i18nprefix
                 :demolition-notice?
                 :approvable :removable-by :last-removable-by :post-verdict-editable
                 :disableable
                 :user-authz-roles
                 :group-help :section-help
                 :after-update
                 :repeating :no-repeat-button :order
                 :redraw-on-approval
                 :post-verdict-party
                 :addable-in-states
                 :editable-in-states
                 :editable-in-doc-states
                 :accordion-fields
                 :blacklist
                 :building?
                 :copy-action
                 :only-one-active
                 :ref-update-permitted?
                 :refresh-auth-model-on-update-doc
                 :use-designer-approval
                 :copy-to-climate-report :climate-report-schema :generates-climate-report
                 :foreman-document
                 :maalampokaivo?})

(def building-id-doc-fields
  "Ordered based on the value most wanted for verdict building-id"
  [:valtakunnallinenNumero
   :pysyva-rakennustunnus
   :manuaalinen_rakennusnro])

(def select-one-of-key "_selected")

(defn defschema [version data]
  (let [schema-name       (name (get-in data [:info :name]))
        validation-result (schema-validation/validate-doc-schema data)]
    (assert (every? info-keys (keys (:info data))))
    (assert (nil? validation-result) (format "Document schema validation failed. Doc: %s, Error: %s" schema-name validation-result))
    (let [schema (-> data
                     (assoc-in [:info :name] schema-name)
                     (assoc-in [:info :version] version)
                     (update :body accordion-field-emitters
                             (->> data
                                  :info
                                  :accordion-fields
                                  (reduce (fn [acc {:keys [type paths] :as field}]
                                            (cond-> acc
                                              (= type :selected)  (conj [select-one-of-key])
                                              paths               (concat paths)
                                              (sequential? field) (concat [field])))
                                          []))))
          schema (flags/with-flag-paths schema)]
      (swap! registered-schemas
             assoc-in
             [version schema-name]
             schema))))

(defn defschemas [version schemas]
  (doseq [schema schemas]
    (defschema version schema)))

(defn get-schema
  "Returns document schema map that contais :info and :body,
   see 'lupapalvelu.document.schema-validation/Doc'"
  ([{:keys [version name] :or {version 1}}] (get-schema version name))
  ([schema-version schema-name]
   {:pre [schema-version schema-name]}
   (get-in @registered-schemas [(long schema-version) (name schema-name)])))

(defn get-subschema [schema subschema-name]
  (util/find-by-key :name (name subschema-name) (:body schema)))

(defn get-in-schemas [schema-name path]
  (reduce get-subschema (get-schema {:name schema-name}) path))

(defn find-identifier-field-from [schema-name]
  (util/find-by-key :identifier true (:body (get-schema {:name schema-name}))))

(defn resolve-identifier [document]
  (when-let [{identifier-field :name} (find-identifier-field-from (-> document :schema-info :name))]
    (get-in document [:data (keyword identifier-field) :value])))

(defn get-latest-schema-version []
  (->> @registered-schemas keys (sort >) first))

(defn with-current-schema-info
  [{{op :op} :schema-info :as document}]
  (let [info (-> document :schema-info get-schema :info)
        ;; Operation documents are removable by default
        info (if (map? op)
               (merge {:removable-by :all} info)
               info)
        ;; Don't propagate `:schema-include`/`:schema-exclude` flag paths
        info (dissoc info :flags)]
    (update document :schema-info merge info)))

(defn select-one-of-schema? [{schema-name :name}]
  (= select-one-of-key (name schema-name)))

;;
;; helpers
;;

(defn repeatable
  "Created repeatable element."
  [name childs]
  [{:name      name
    :type      :group
    :repeating true
    :body      (body childs)}])

(defn approvable-top-level-groups [v]
  (mapv #(if (= (:type %) :group) (assoc % :approvable true) %) v))

(defn resolve-accordion-field-values
  "Returns collection of document values from paths defined by
  schema's :accordion-fields"
  [document]
  (when-let [fields (get-in document [:schema-info :accordion-fields])]
    (let [doc-data (:data document)]
      (->> fields
           (map (fn [{:keys [type paths] :as field}]
                  (cond
                    (= type :selected) (let [selected (get-in doc-data
                                                              [(keyword select-one-of-key)
                                                               :value])]
                                         (filter #(= selected (first %)) paths))
                    paths              paths
                    :else              field)))
           (apply concat)
           (map (fn [path] (get-in doc-data (conj (mapv keyword path) :value))))
           (remove util/empty-or-nil?)))))

(defn ^:ryhti ryhti-id
  "Adds one or more ryhti-ids to the 'ryhti' metadata group.
  The id's need to be added to the `:name` enum of the RyhtiIdentifier schema in `schema_validation.clj`
  See also `lupapalvelu.ryhti.util/ryhti-id` for the corresponding fn for non-docgen usage"
  [& id-keys]
  {:name             "ryhti"
   :type             :group
   :exclude-from-pdf true
   :whitelist        {:roles [:none] :otherwise :hidden}
   :body             (mapv (fn [id-key] {:name (name id-key) :type :ryhti-identifier}) id-keys)})

(defn ^:ryhti mapping->options
  "Maps ryhti codes to list of options that can be used with a select input element.

  NOTICE!!
  The old KuntaGML codes that have been used in our DB are usually in a 'foo bar baz' format,
  while the ryhti codes, since the mappings use keywords, are in a 'foo-bar-baz' format"
  [mapping & {:keys [i18nkey]}]
  (if (map? mapping)
    (mapv (fn [[k _v]]
            (cond-> {:name (name k)}
              i18nkey (assoc :i18nkey (name (util/kw-path i18nkey k)))))
          mapping)
    ;; Used with grouped mappings, mappings with optgroups
    (mapv (fn [v]
            (cond-> v
              true    (update :name name)
              i18nkey (assoc :i18nkey (name (util/kw-path i18nkey (:name v))))))
          mapping)))

;; style helpers

(def top-container-margin :mt-8)
(def top-container-css [top-container-margin])

;;
;; schema sniplets
;;

(def country-list (map :alpha-3 countries/countries))
(def country {:name    "maa"
              :type    :select
              :default "FIN"
              :i18nkey "country.country"
              :sortBy  :displayname
              :body    (mapv (fn [n] {:name n :i18nkey (str "country." n)}) country-list)})

(def turvakielto "turvakieltoKytkin")

(def ei-tiedossa {:name "ei tiedossa" :i18nkey "ei-tiedossa"})

(def suoramarkkinointilupa {:name "suoramarkkinointilupa" :type :checkbox :layout :full-width :i18nkey "osapuoli.suoramarkkinointilupa"})
(def vain-sahkoinen-asiointi {:name "vainsahkoinenAsiointiKytkin" :type :checkbox :layout :full-width :i18nkey "osapuoli.vainsahkoinenAsiointiKytkin" :default true})

(def kytkimet {:name "kytkimet" :type :group :i18nkey "empty" :body [suoramarkkinointilupa]})
(def kytkimet-with-vain-sahkoinen-asiointi (update-in kytkimet [:body] conj vain-sahkoinen-asiointi))

(def national-building-id "valtakunnallinenNumero")

(def kuvaus {:name "kuvaus" :type :text :max-len 4000 :required true :layout :full-width})

(def rahoitus {:name "rahoitus" :type :fundingSelector})

(def henkilo-valitsin [{:name "userId" :type :personSelector :blacklist [:neighbor]}])

(def yritys-valitsin [{:name "companyId" :type :companySelector :blacklist [:neighbor]}])

(def tunnus {:name "tunnus" :type :string :required true :hidden true :max-len 6 :identifier true})

(def rakennuksen-valitsin [{:name "buildingId" :type :buildingSelector :required true :i18nkey "rakennusnro" :other-key "manuaalinen_rakennusnro"}
                           {:name "rakennusnro" :type :string :subtype :rakennusnumero :hidden true}
                           {:name "manuaalinen_rakennusnro" :type :string :subtype :rakennusnumero :i18nkey "manuaalinen_rakennusnro" :labelclass "really-long" :hidden true}
                           {:name national-building-id :type :string :subtype :rakennustunnus :hidden true}
                           {:name "kunnanSisainenPysyvaRakennusnumero" :type :string :hidden true}])

(def ^:ryhti ryhti-rakennuksen-valitsin
  [{:name      "buildingId"
    :type      :buildingSelector
    :required  true
    :i18nkey   "rakennuskohde.rakennusnro"
    :other-key "manuaalinen_rakennusnro"}
   {:name       "manuaalinen_rakennusnro"
    :type       :string
    :subtype    :rakennusnumero
    :i18nkey    "manuaalinen_rakennusnro"
    :labelclass "really-long"
    :hidden     true}
   {:name    "rakennusnro"
    :type    :string
    :subtype :rakennusnumero
    :hidden  true}])

(def postinumero {:name "postinumero" :type :string :subtype :zipcode :size :s :required true :dummy-test :postal-code :i18nkey "osoite.postinumero"})

(def ^:ryhti foreign-postinumero (assoc postinumero
                                        :name      "foreign-postinumero"
                                        :i18nkey   "osoite.postinumero"
                                        :hide-when {:path "maa" :values #{"FIN"}}))

(def postitoimipaikannimi {:name "postitoimipaikannimi" :type :string :subtype :vrk-address :size :m :required true :i18nkey "osoite.postitoimipaikannimi"})

(def ^:ryhti readonly-postitoimipaikannimi (-> postitoimipaikannimi
                                               (assoc :readonly true)
                                               (dissoc :required)))

(def ^:ryhti foreign-postitoimipaikannimi (assoc postitoimipaikannimi
                                                 :name      "foreign-postitoimipaikannimi"
                                                 :i18nkey   "osoite.postitoimipaikannimi"
                                                 :hide-when {:path "maa" :values #{"FIN"}}))

(def simple-osoite [{:name         "osoite"
                     :type         :group
                     :validator    :address
                     :address-type :contact
                     :blacklist    [turvakielto]
                     :body         [(ryhti-id :addressKey)
                                    {:name "katu" :type :string :subtype :vrk-address :required true :i18nkey "osoite.katu" :blacklist [:neighbor]}
                                    postinumero
                                    postitoimipaikannimi
                                    country]}])

(def simple-osoite-maksaja [{:name      "osoite"
                             :i18nkey   "osoite-maksaja"
                             :type      :group
                             :validator :address
                             :blacklist [turvakielto]
                             :body      [{:name "katu" :type :string :subtype :vrk-address :size :l :required true :i18nkey "osoite.katu"}
                                         postinumero
                                         {:name "postitoimipaikannimi" :type :string :subtype :vrk-address :size :m :required true :i18nkey "osoite.postitoimipaikannimi"}
                                         country]}])

(def rakennuksen-osoite [{:name      "osoite"
                          :type      :group
                          :validator :address
                          :body      [{:name "kunta" :type :string :i18nkey "osoite.kunta"}
                                      {:name "lahiosoite" :type :string :i18nkey "osoite.katu"}
                                      {:name "osoitenumero" :type :string}
                                      {:name "osoitenumero2" :type :string}
                                      {:name "jakokirjain" :type :string :subtype :letter :case :lower :max-len 1 :size :s :hidden true :readonly true}
                                      {:name "jakokirjain2" :type :string :size :s :hidden true :readonly true}
                                      {:name "porras" :type :string :subtype :letter :case :upper :max-len 1 :size :s :hidden true :readonly true}
                                      {:name "huoneisto" :type :string :size :s :hidden true :readonly true}
                                      (dissoc postinumero :required)
                                      {:name "postitoimipaikannimi" :type :string :size :m :i18nkey "osoite.postitoimipaikannimi"}
                                      country]}])

(def ^:ryhti ryhti-yhteysosoite
  "Ryhti 'ContactAddress' entity
  Used in the `Operator` entity"
  {:name         "yhteysosoite"
   :type         :group
   :validator    :address
   :i18nkey      "yhteysosoite"
   :address-type :contact
   :blacklist    [turvakielto]
   :body         (body (ryhti-id :contactAddressKey)
                       country
                       {:name "lahiosoite" :type :string :i18nkey "osoite.katu"}
                       (assoc postinumero :show-when {:path "maa" :values #{"FIN"}})
                       (assoc readonly-postitoimipaikannimi :show-when {:path "maa" :values #{"FIN"}})
                       foreign-postinumero
                       foreign-postitoimipaikannimi
                       {:name "c-o" :i18nkey "osoite.co" :type :string}
                       {:name "osoite.lisamaare" :type :string} ;TODO: loc-string (fin/swe)
                       )})

(def ^:ryhti ryhti-rakennuskohteen-osoite
  "Ryhti 'Address' entity
  The address of a 'building' or 'site'. The address is presumed to be a finnish address
  Used in the `BuildingSite`, `Building`, `Structure` and `AreaToBeBuiltForSpecificActivities` entities"
  {:name      "osoite"
   :type      :group
   :validator :building-address
   :i18nkey   "osoite"
   :body      (body (ryhti-id :addressKey)
                    {:name        "katu"
                     :type        :string
                     :subtype     :ryhti-address-name
                     :i18nkey     "osoite.katu"
                     :ifc-mapping :ifc-osoite-katu}
                    {:name "osoitenumero" :type :string :i18nkey "osoite.osoitenumero"}
                    {:name "osoitenumero2" :type :string :i18nkey "osoite.osoitenumero2"}
                    {:name "jakokirjain" :type :string :subtype :letter :case :lower :max-len 1 :size :s :hidden true :readonly true}
                    {:name "jakokirjain2" :type :string :size :s :hidden true :readonly true}
                    {:name "porras" :type :string :subtype :letter :case :upper :max-len 1 :size :s :hidden true :readonly true}
                    {:name "huoneisto" :type :string :size :s :hidden true :readonly true}
                    (assoc postinumero :ifc-mapping :ifc-osoite-postinumero)
                    (assoc readonly-postitoimipaikannimi :ifc-mapping :ifc-osoite-postitoimipaikka))})

(def yhteystiedot [{:name      "yhteystiedot"
                    :type      :group
                    :i18nkey   "yhteystiedot._group_label"
                    :blacklist [:neighbor turvakielto]
                    :body      [{:name "puhelin" :type :string :subtype :tel :required true :i18nkey "puhelin"}
                                ;; NOTE: Suunnittelija consent is dependant on this field, so the path and name of this field should not change.
                                ;; - Cljs component directly follows the value of this field
                                ;; - Backend reads the designer-email from this field (get-designer-email)
                                {:name "email" :type :string :subtype :email :required true :i18nkey "email" :emit [:emailChanged]}]}])

(def henkilotiedot-minimal {:name "henkilotiedot"
                            :type :group
                            :body [{:name "etunimi" :type :string :subtype :vrk-name :required true :i18nkey "etunimi"}
                                   {:name "sukunimi" :type :string :subtype :vrk-name :required true :i18nkey "sukunimi"}
                                   {:name turvakielto :type :checkbox :blacklist [turvakielto] :i18nkey "henkilo.turvakieltoKytkin"}]})

(def henkilotiedot
  {:name "henkilotiedot"
   :type :group
   :body [{:name "etunimi" :type :string :subtype :vrk-name :required true :i18nkey "etunimi"}
          {:name "sukunimi" :type :string :subtype :vrk-name :required true :i18nkey "sukunimi"}
          {:name           "not-finnish-hetu"
           :type           :checkbox
           :i18nkey        "not-finnish-hetu"
           :emit           [:hetuChanged]
           :blacklist      [:neighbor turvakielto]
           :schema-include :yht-219}
          {:name           "ulkomainenHenkilotunnus"
           :type           :string
           :i18nkey        "ulkomainenHetu"
           :show-when      {:path   "not-finnish-hetu"
                            :values #{true}}
           :required       true
           :emit           [:hetuChanged]
           :blacklist      [:neighbor turvakielto]
           :schema-include :yht-219}
          {:name      "hetu"
           :type      :hetu
           :max-len   11
           :required  true
           :blacklist [:neighbor turvakielto]
           :emit      [:hetuChanged]
           :transform :upper-case
           :i18nkey   "hetu"
           :show-when {:path           "not-finnish-hetu"
                       :values         #{false}
                       :schema-include :yht-219}}
          {:name turvakielto :type :checkbox :blacklist [turvakielto] :i18nkey "henkilo.turvakieltoKytkin"}]})

(def henkilo
  (body
    henkilo-valitsin
    [henkilotiedot]
    simple-osoite
    yhteystiedot
    kytkimet-with-vain-sahkoinen-asiointi))

(def henkilo-maksaja (body
                       henkilo-valitsin
                       [henkilotiedot]
                       simple-osoite-maksaja
                       yhteystiedot
                       kytkimet))

(def henkilo-with-required-hetu (body
                                  henkilo-valitsin
                                  [(assoc henkilotiedot
                                          :body
                                          (mapv (fn [ht] (if (= (:name ht) "hetu") (merge ht {:required true}) ht))
                                                (:body henkilotiedot)))]
                                  simple-osoite
                                  yhteystiedot
                                  kytkimet))

(def yhteyshenkilo-without-kytkimet
  {:name "yhteyshenkilo"
   :type :group
   :body (body
           [henkilotiedot-minimal]
           yhteystiedot)})

(def yhteyshenkilo-suoramarkkinointi
  (update-in yhteyshenkilo-without-kytkimet [:body] concat [kytkimet]))

(def yhteyshenkilo
  (update-in yhteyshenkilo-without-kytkimet [:body] concat [kytkimet-with-vain-sahkoinen-asiointi]))

(def yritys-minimal [{:name "yritysnimi" :type :string :required true :size :l}
                     {:name "liikeJaYhteisoTunnus" :type :string :subtype :y-tunnus :required true :i18nkey "y-tunnus"}])

(def yritys (body
              yritys-valitsin
              yritys-minimal
              simple-osoite
              yhteyshenkilo))

(def yritys-maksaja (body
                      yritys-valitsin
                      yritys-minimal
                      simple-osoite-maksaja
                      yhteyshenkilo-suoramarkkinointi))

(def yritys-without-kytkimet
  (body
    yritys-valitsin
    yritys-minimal
    simple-osoite
    yhteyshenkilo-without-kytkimet))

(def e-invoice-operators (mapv #(hash-map :name %) shared/e-invoice-operators))

(def verkkolaskutustieto [{:name "verkkolaskuTunnus" :type :string}
                          {:name "ovtTunnus" :type :string :subtype :ovt :min-len 12 :max-len 17}
                          {:name    "valittajaTunnus"
                           :type    :select
                           :sortBy  :displayname
                           :i18nkey "osapuoli.yritys.verkkolaskutustieto.valittajaTunnus"
                           :size    :l
                           :body    e-invoice-operators}])

(def yritys-with-verkkolaskutustieto (body
                                       yritys-maksaja
                                       {:name "verkkolaskutustieto"
                                        :type :group
                                        :body (body
                                                verkkolaskutustieto)}))

(defn- henkilo-yritys-select-group
  [& {:keys [default henkilo-body yritys-body exclude-companies? aria-label]
      :or   {default "henkilo" henkilo-body henkilo yritys-body yritys exclude-companies? true}}]
  (body
    (util/assoc-when
      {:name    select-one-of-key
       :type    :radioGroup
       :body    [{:name "henkilo" :classPrefix :person-vs-company :icon :lupicon-user}
                 {:name "yritys" :classPrefix :person-vs-company :icon :lupicon-building}]
       :default default}
      :aria-label aria-label)
    {:name "henkilo" :type :group :body (cond-> henkilo-body
                                          exclude-companies? (update-in-body
                                                               ["userId"]
                                                               :excludeCompanies (constantly true)))}
    {:name "yritys" :type :group :body yritys-body}))

(def party (henkilo-yritys-select-group))

(def applicant (body
                 (ryhti-id :engagingPartyKey)
                 (henkilo-yritys-select-group)))

(def party-vireillepanija
  (body
    {:name      "vireilepanijan-rooli"
     :type      :select
     :size      :l
     :body      [{:name "haitankarsija"}
                 {:name "valvontaviranomainen"}
                 {:name "yhdistys"}]
     :other-key "muu-vireillepanijan-rooli"
     :required  true}
    {:name "muu-vireillepanijan-rooli"
     :type :string
     :size :l}
    (henkilo-yritys-select-group)))

(def ya-party (body
                (ryhti-id :engagingPartyKey)
                (henkilo-yritys-select-group {:default "yritys"})))

(def ya-party-tyomaasta-vastaava (henkilo-yritys-select-group :default "yritys"
                                                              :exclude-companies? false))
(def building-parties (henkilo-yritys-select-group
                        :aria-label :building.owner
                        :henkilo-body henkilo-with-required-hetu
                        :yritys-body yritys-without-kytkimet))

(def koulutusvalinta {:name "koulutusvalinta" :type :select :sortBy :displayname :i18nkey "koulutus" :other-key "koulutus" :required true
                      :body (wrapped user-enums/koulutusvalinta :name)})

(def fise-kelpoisuus-lajit (wrapped user-enums/fise-kelpoisuus-lajit :name))

(defn- make-patevyysluokka
  [tj?]
  ;; Make sure order is sane, as docgen will not sort this (:sortBy nil)
  {:name "patevyysluokka" :type :select :sortBy nil :required true
   :body (->> [{:name "AA"}
               {:name "A+" :schema-include :yht-2111}
               {:name "A"}
               ;; Note that unlike designers, `yht:TyonjohtajaPatevyysluokkaType` does
               ;; include `B+` option in 2.1.11
               (when tj?
                 {:name "B+" :schema-include :yht-2111})
               {:name "B"}
               {:name "C"}
               ;; Should be hidden in yht-2111
               ei-tiedossa]
              (keep identity)
              (into []))})

(def tj-patevyysluokka (make-patevyysluokka true))

(def patevyysluokka (make-patevyysluokka false))

(def patevyydentoteaja-siirtymasaannos "siirtymasaannos")

(def patevyystodistus
  [{:name           "patevyydentoteaja"
    :i18nkey        "osapuoli.patevyys.patevyydentoteaja._group_label"
    :type           :select
    :sortBy         nil
    ;; Required on KuntaGML (if todistustieto used) but not
    ;; required on the application yet:
    ;; https://cloudpermit.atlassian.net/browse/LPK-7707
    :required       false
    :body           [{:name    "kiwa"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.kiwa"}
                     {:name    "seti"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.seti"}
                     {:name    "fise"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.fise"}
                     {:name    "safa"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.safa"}
                     {:name    "eurofins"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.eurofins"}
                     {:name    "ril"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.ril"}
                     {:name    "sbif"
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.sbif"}
                     {:name    patevyydentoteaja-siirtymasaannos
                      :i18nkey "osapuoli.patevyys.patevyydentoteaja.siirtymasaannos"}]
    :schema-include :rakval-226}
   {:name           "patevyysvoimassapvm"
    :i18nkey        "osapuoli.patevyys.patevyysvoimassapvm"
    :type           :date
    ;; https://cloudpermit.atlassian.net/browse/LPK-7707
    :required       false
    :schema-include :rakval-226}
   {:name           "patevyystodistusurl"
    :i18nkey        "osapuoli.patevyys.patevyystodistusurl"
    :type           :url
    :required       false
    :schema-include :rakval-226
    :placeholder    "osapuoli.patevyys.patevyystodistusurl.placeholder"}
   {:name                   "patevyystodistusliite"
    :i18nkey                "osapuoli.patevyys.patevyystodistusliite"
    :type                   :partyAttachment
    :required               false
    :schema-include         :rakval-226
    :attachment-type-id     "patevyystodistus"
    :attachment-target-type "suunnittelija-patevyystodistus"}])

(def patevyys (body
                koulutusvalinta
                {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
                {:name "valmistumisvuosi" :type :string :subtype :recent-year :range 100 :required true}
                {:name "fise" :type :string :required false
                 ;; These are mostly replaced by the patevyystodistus, but
                 ;; 2.2.6 orgs might already have applications using these fise fields.
                 ;; These work for 2.2.6 and we don't know if the target systems
                 ;; use the patevyys properties yet, so lets keep fise fields visible for now.
                 ;; :schema-exclude :rakval-226
                 }
                {:name "fiseKelpoisuus" :type :select :sortBy :displayname :i18nkey "fisekelpoisuus" :size :l :required false :body fise-kelpoisuus-lajit
                 ;; :schema-exclude :rakval-226
                 }
                patevyysluokka
                patevyystodistus
                ;; "Lisätietoa"
                {:name "patevyys" :type :string :required false}
                ;; "Kokemus vuosina"
                {:name "kokemus" :type :string :subtype :number :min-len 1 :max-len 2 :size :s :required false}))

(def designer-basic (body
                      (schema-body-without-element-by-name henkilotiedot turvakielto)
                      {:name "yritys" :type :group
                       :body (util/postwalk-map #(dissoc % :required) yritys-minimal)}
                      simple-osoite
                      yhteystiedot))

(def suunnittelutehtavan-vaativuusluokka (assoc patevyysluokka :name "suunnittelutehtavanVaativuusluokka"))

(def suunnittelija-suostumus (body
                               {:name     "designerConsent" :type           :consent
                                :readonly true              :schema-include :consent}
                               {:name                   "designerConsentAttachment"
                                :i18nkey                "osapuoli.suunnittelija.give-consent.liite"
                                :type                   :partyAttachment
                                :required               false
                                :schema-include         :consent
                                :attachment-type-id     "suostumus"
                                :attachment-target-type "suunnittelija-suostumus"}))

(def ^:ryhti suunnittelija-vastuu
  {:name           "vastuu"
   :type           :group
   :schema-include :ryhti-data-2026
   :body           (body
                     {:name           "vastuunAlkamisPvm"
                      :i18nkey        "osapuoli.vastuu.vastuunAlkamisPvm"
                      :type           :date
                      ;; Not required for now because required ryhti fields are
                      ;; not handled properly in non-ryhti applications at the moment
                      :required       false
                      :schema-include :ryhti-data-2026}
                     {:name           "vastuunPaattymisPvm"
                      :i18nkey        "osapuoli.vastuu.vastuunPaattymisPvm"
                      :type           :date
                      :required       false
                      :schema-include :ryhti-data-2026}
                     {:name           "rakennuskohteet"
                      :type           :rakennuskohdeSelector
                      :required       false
                      :i18nkey        "osapuoli.suunnittelija.rakennuskohteet"
                      :schema-include :ryhti-data-2026})})

(def ^:ryhti tyonjohtajan-rakennuskohteet
  {:name           "rakennuskohteet"
   :type           :rakennuskohdeSelector
   :required       false
   :i18nkey        "osapuoli.tyonjohtaja.rakennuskohteet"
   :schema-include :ryhti-data-2026})

(def paasuunnittelija (body
                        (ryhti-id :plannerKey)
                        suunnittelutehtavan-vaativuusluokka
                        henkilo-valitsin
                        designer-basic
                        {:name      "patevyys"
                         :type      :group
                         :body      patevyys
                         :validator :patevyys}
                        suunnittelija-suostumus
                        suunnittelija-vastuu))

;; These map to `yht:SuunnittelijaRoolikoodiType`
(def kuntaroolikoodi [{:name      "kuntaRoolikoodi"
                       :i18nkey   "osapuoli.suunnittelija.kuntaRoolikoodi._group_label"
                       :type      :select :sortBy :displayname :required true
                       :other-key "muuSuunnittelijaRooli"
                       :body      [{:name "pääsuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.pääsuunnittelija"}

                                   {:name "LVI-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.LVI-suunnittelija"}
                                   {:name "IV-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.IV-suunnittelija"}
                                   {:name "KVV-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.KVV-suunnittelija"}
                                   {:name "Vaikeiden töiden suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.Vaikeiden töiden suunnittelija"}

                                   ;; KRYSP yht 2.1.6
                                   {:name "rakennussuunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.rakennussuunnittelija"}
                                   {:name "kantavien rakenteiden suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.kantavien rakenteiden suunnittelija"}
                                   {:name "ilmanvaihdon suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.ilmanvaihdon suunnittelija"}
                                   {:name "kiinteistön vesi- ja viemäröintilaitteiston suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.vesiviemarisuunnittelija"}
                                   {:name "rakennusfysikaalinen suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.rakennusfysikaalinen suunnittelija"}
                                   {:name "kosteusvaurion korjaustyön suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.kosteusvaurion korjaustyön suunnittelija"}

                                   ;; Removed in yht 2.1.11
                                   ;; TODO: Add logic to forbid these in new applications (>2025).
                                   {:name "GEO-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.GEO-suunnittelija"}
                                   {:name "RAK-rakennesuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.RAK-rakennesuunnittelija"}
                                   {:name "ARK-rakennussuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.ARK-rakennussuunnittelija"}
                                   {:name "pohjarakenteiden suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.pohjarakenteiden suunnittelija"}

                                   ;; New in yht 2.1.11
                                   {:name "rakennesuunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.rakennesuunnittelija" :schema-include :yht-2111}
                                   {:name "pohjarakennesuunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.pohjarakennesuunnittelija" :schema-include :yht-2111}
                                   {:name "energiatodistuksen laatija" :i18nkey "osapuoli.kuntaRoolikoodi.energiatodistuksen laatija" :schema-include :yht-2111}
                                   {:name "vastaava rakennesuunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.vastaava rakennesuunnittelija" :schema-include :yht-2111}
                                   {:name "kosteudenhallintakoordinaattori" :i18nkey "osapuoli.kuntaRoolikoodi.kosteudenhallintakoordinaattori" :schema-include :yht-2111}
                                   {:name "palotekninen suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.palotekninen suunnittelija" :schema-include :yht-2111}
                                   {:name "akustiikkasuunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.akustiikkasuunnittelija" :schema-include :yht-2111}
                                   {:name "maisemarakentamisen suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.maisemarakentamisen suunnittelija" :schema-include :yht-2111}

                                   ;; Should be hidden in yht-2111
                                   ei-tiedossa]}])

(def suunnittelija (body
                     (ryhti-id :plannerKey)
                     kuntaroolikoodi
                     {:name "muuSuunnittelijaRooli" :type :string}
                     suunnittelutehtavan-vaativuusluokka
                     henkilo-valitsin
                     designer-basic
                     {:name      "patevyys"
                      :type      :group
                      :body      patevyys
                      :validator :patevyys}
                     suunnittelija-suostumus
                     suunnittelija-vastuu))

(def vastattavat-tyotehtavat-tyonjohtaja [{:name      "vastattavatTyotehtavat"
                                           :i18nkey   "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"
                                           :type      :group
                                           :layout    :vertical
                                           :validator :some-checked
                                           :body      [{:name "rakennuksenRakentaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenRakentaminen" :type :checkbox}
                                                       {:name "rakennuksenMuutosJaKorjaustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenMuutosJaKorjaustyo" :type :checkbox}
                                                       {:name "rakennuksenPurkaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenPurkaminen" :type :checkbox}
                                                       {:name "maanrakennustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.maanrakennustyo" :type :checkbox}
                                                       {:name "rakennelmaTaiLaitos" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennelmaTaiLaitos" :type :checkbox}
                                                       {:name "elementtienAsennus" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.elementtienAsennus" :type :checkbox}
                                                       {:name "terasRakenteet_tiilirakenteet" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.terasRakenteet_tiilirakenteet" :type :checkbox}
                                                       {:name "kiinteistonVesiJaViemarilaitteistonRakentaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.kiinteistonVesiJaViemarilaitteistonRakentaminen" :type :checkbox}
                                                       {:name "kiinteistonilmanvaihtolaitteistonRakentaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.kiinteistonilmanvaihtolaitteistonRakentaminen" :type :checkbox}
                                                       {:name "muuMika" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMika" :type :checkbox}
                                                       {:name      "muuMikaValue" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMikaValue" :type :string
                                                        :required  true
                                                        :show-when {:path "muuMika" :values #{true}}}]}])

;; `yht:TyonjohtajaRooliKoodiType`
(def kuntaroolikoodi-tyonjohtaja [{:name     "kuntaRoolikoodi"
                                   :i18nkey  "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label"
                                   :type     :select
                                   :sortBy   :displayname
                                   :required true
                                   :body     [{:name "KVV-työnjohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.KVV-työnjohtaja"}
                                              {:name "IV-työnjohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.IV-työnjohtaja"}
                                              {:name "erityisalojen työnjohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.erityisalojen työnjohtaja"}
                                              {:name "vastaava työnjohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.vastaava työnjohtaja"}
                                              {:name "työnjohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.työnjohtaja"}
                                              ei-tiedossa]}])

(def patevyysvaatimusluokka (assoc tj-patevyysluokka :name "patevyysvaatimusluokka"))

(def patevyys-tyonjohtaja [koulutusvalinta
                           {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
                           patevyysvaatimusluokka           ; Actually vaadittuPatevyysluokka in KRYSP
                           {:name "valmistumisvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size :s :required true}
                           {:name "kokemusvuodet" :type :string :subtype :number :min-len 1 :max-len 2 :size :s :required true}
                           {:name "valvottavienKohteidenMaara" :i18nkey "tyonjohtaja.patevyys.valvottavienKohteidenMaara" :type :string :subtype :number :size :s :required true}
                           {:name "tyonjohtajaHakemusKytkin" :i18nkey "tyonjohtaja.patevyys.tyonjohtajaHakemusKytkin._group_label" :required true :type :select :sortBy :displayname
                            :body [{:name "nimeaminen" :i18nkey "tyonjohtaja.patevyys.tyonjohtajaHakemusKytkin.nimeaminen"}
                                   {:name "hakemus" :i18nkey "tyonjohtaja.patevyys.tyonjohtajaHakemusKytkin.hakemus"}]}])

(def patevyys-tyonjohtaja-v2 [koulutusvalinta
                              {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
                              {:name "valmistumisvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size :s :required true}
                              {:name "kokemusvuodet" :type :string :subtype :number :min-len 1 :max-len 2 :size :s :required true}
                              {:name "valvottavienKohteidenMaara" :i18nkey "tyonjohtaja.patevyys.valvottavienKohteidenMaara" :type :string :subtype :number :size :s :required true}])

(def patevyys-tyonjohtaja-v3 (body
                               koulutusvalinta
                               {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
                               {:name "valmistumisvuosi" :type :string :subtype :number :min-len 4 :max-len 4 :size :s :required true}
                               patevyystodistus
                               {:name "kokemusvuodet" :type :string :subtype :number :min-len 1 :max-len 2 :size :s :required true}
                               {:name "valvottavienKohteidenMaara" :i18nkey "tyonjohtaja.patevyys.valvottavienKohteidenMaara" :type :string :subtype :number :size :s :required true}))

(def sijaisuus-tyonjohtaja [{:name "sijaistus" :i18nkey "tyonjohtaja.sijaistus._group_label"
                             :type :group
                             :body [{:name "sijaistettavaHloEtunimi" :i18nkey "tyonjohtaja.sijaistus.sijaistettavaHloEtunimi" :type :string}
                                    {:name "sijaistettavaHloSukunimi" :i18nkey "tyonjohtaja.sijaistus.sijaistettavaHloSukunimi" :type :string}
                                    {:name "alkamisPvm" :i18nkey "tyonjohtaja.sijaistus.alkamisPvm" :type :date}
                                    {:name "paattymisPvm" :i18nkey "tyonjohtaja.sijaistus.paattymisPvm" :type :date}]}])

(def tyonjohtaja (body
                   kuntaroolikoodi-tyonjohtaja
                   vastattavat-tyotehtavat-tyonjohtaja
                   henkilo-valitsin
                   designer-basic
                   {:name "patevyys-tyonjohtaja" :type :group :body patevyys-tyonjohtaja}
                   sijaisuus-tyonjohtaja))

(def kuntaroolikoodi-tyonjohtaja-v2 [{:name     "kuntaRoolikoodi"
                                      :i18nkey  "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label"
                                      :type     :select
                                      :emit     [:filterByCode]
                                      :sortBy   :displayname
                                      :required true
                                      :body     [{:name "vastaava työnjohtaja" :code :vtj :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.vastaava työnjohtaja"}
                                                 {:name "KVV-työnjohtaja" :code :kvv :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.KVV-työnjohtaja"}
                                                 {:name "IV-työnjohtaja" :code :ivt :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.IV-työnjohtaja"}
                                                 {:name "erityisalojen työnjohtaja" :code :vrt :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.erityisalojen työnjohtaja"}
                                                 ;; Use :vtj code here as well, since työnjohtaja role can be very generic.
                                                 {:name "työnjohtaja" :code :vtj :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.työnjohtaja"}]}])

(def vastattavat-tyotehtavat-tyonjohtaja-v2 [{:name      "vastattavatTyotehtavat"
                                              :i18nkey   "osapuoli.tyonjohtaja.vastattavatTyotehtavat._group_label"
                                              :type      :group
                                              :validator :some-checked
                                              :subtype   :foreman-tasks ;; Used in pdf-export together with :codes.
                                              :listen    [:filterByCode]
                                              :css       [:checkbox-group]
                                              :layout    :vertical
                                              :body      [{:name "ivLaitoksenAsennustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.ivLaitoksenAsennustyo" :codes [:ivt] :type :checkbox}
                                                          {:name "ivLaitoksenKorjausJaMuutostyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.ivLaitoksenKorjausJaMuutostyo" :codes [:ivt] :type :checkbox}
                                                          {:name "sisapuolinenKvvTyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.sisapuolinenKvvTyo" :codes [:kvv] :type :checkbox}
                                                          {:name "ulkopuolinenKvvTyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.ulkopuolinenKvvTyo" :codes [:kvv] :type :checkbox}
                                                          {:name "rakennuksenMuutosJaKorjaustyo" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenMuutosJaKorjaustyo" :codes [:vtj] :type :checkbox}
                                                          {:name "uudisrakennustyoMaanrakennustoineen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.uudisrakennustyoMaanrakennustoineen" :codes [:vtj] :type :checkbox}
                                                          {:name "uudisrakennustyoIlmanMaanrakennustoita" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.uudisrakennustyoIlmanMaanrakennustoita" :codes [:vtj] :type :checkbox}
                                                          {:name "linjasaneeraus" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.linjasaneeraus" :codes [:vtj] :type :checkbox}
                                                          {:name "maanrakennustyot" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.maanrakennustyot" :codes [:vtj] :type :checkbox}
                                                          {:name "rakennuksenPurkaminen" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.rakennuksenPurkaminen" :codes [:vtj] :type :checkbox}
                                                          {:name "muuMika" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMika" :codes [:vtj :kvv :ivt :vrt] :type :checkbox}
                                                          {:name      "muuMikaValue" :i18nkey "osapuoli.tyonjohtaja.vastattavatTyotehtavat.muuMikaValue" :type :string
                                                           :required  true
                                                           :show-when {:path "muuMika" :values #{true}}}]}])

(def tyonjohtaja-hanketieto {:name "tyonjohtajaHanketieto" :type :group
                             :body [{:name "taysiaikainenOsaaikainen" :type :radioGroup :body [{:name "taysiaikainen"} {:name "osaaikainen"}] :default "taysiaikainen"}
                                    {:name "hankeKesto" :type :string :size :s :unit :kuukautta :subtype :number :min 0 :max 9999999}
                                    {:name "kaytettavaAika" :type :string :size :s :unit :tuntiaviikko :subtype :number :min 0 :max 168} ; 7*24 = 168h :)
                                    {:name "kayntienMaara" :type :string :size :s :unit :kpl :subtype :number :min 0 :max 9999999}]})

(def hanke-row [{:name "luvanNumero" :type :string :size :m :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.luvanNumero"}
                {:name "katuosoite" :type :string :size :m :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.katuosoite"}
                {:name "rakennustoimenpide" :type :string :size :l :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.rakennustoimenpide" :locPrefix "operations"}
                {:name "kokonaisala" :type :string :subtype :decimal :size :s :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.kokonaisala"}
                {:name "vaihe" :type :select :size :t :label false :uicomponent :docgen-select :i18nkey "muutHankkeet.vaihe" :valueAllowUnset false
                 :body [{:name "R" :i18nkey "muutHankkeet.R"}
                        {:name "A" :i18nkey "muutHankkeet.A"}
                        {:name "K" :i18nkey "muutHankkeet.K"}]}
                {:name "3kk" :type :string :subtype :number :size :s :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.3kk"}
                {:name "6kk" :type :string :subtype :number :size :s :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.6kk"}
                {:name "9kk" :type :string :subtype :number :size :s :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.9kk"}
                {:name "12kk" :type :string :subtype :number :size :s :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.12kk"}
                {:name "autoupdated" :type :checkbox :hidden true :i18nkey "muutHankkeet.autoupdated" :uicomponent :docgen-input :inputType :checkbox :whitelist {:roles     [:none]
                                                                                                                                                                  :otherwise :disabled}}])

(def muut-rakennushankkeet-table {:name             "muutHankkeet"
                                  :type             :group
                                  :uicomponent      :hanke-table
                                  :exclude-from-pdf true
                                  :repeating        true
                                  :approvable       true
                                  :copybutton       false
                                  :listen           [:hetuChanged]
                                  :whitelist        {:roles [:authority] :otherwise :hidden}
                                  :body             hanke-row})

(def tayta-omat-tiedot-button {:name "fillMyInfo" :type :fillMyInfoButton :whitelist {:roles [:applicant] :otherwise :disabled}})

(def tyonjohtajan-historia {:name "foremanHistory" :type :foremanHistory})

(def tyonjohtajan-hyvaksynta [{:name      "tyonjohtajanHyvaksynta"
                               :type      :group
                               :whitelist {:roles     [:authority]
                                           :otherwise :hidden}
                               :body      [tyonjohtajan-historia
                                           {:name "tyonjohtajanHyvaksynta" :type :checkbox :layout :full-width :i18nkey "tyonjohtaja.historia.hyvaksynta"}]}])

(def tyonjohtajan-suostumus {:name "foremanConsent" :type :consent :readonly true})

(def tyonjohtaja-toiminnot {:name "foremanActions" :type :foremanActions})

(def tyonjohtaja-v2 (body
                      tayta-omat-tiedot-button
                      designer-basic
                      kuntaroolikoodi-tyonjohtaja-v2
                      patevyysvaatimusluokka                ; Actually vaadittuPatevyysluokka in KRYSP
                      vastattavat-tyotehtavat-tyonjohtaja-v2
                      tyonjohtaja-hanketieto
                      sijaisuus-tyonjohtaja
                      {:name "patevyys-tyonjohtaja" :type :group :body patevyys-tyonjohtaja-v2}
                      muut-rakennushankkeet-table
                      tyonjohtajan-hyvaksynta))

(def tyonjohtaja-v3 (body
                      (ryhti-id :foremanKey)
                      tayta-omat-tiedot-button
                      henkilo-valitsin
                      designer-basic
                      kuntaroolikoodi-tyonjohtaja-v2
                      patevyysvaatimusluokka                ; Actually vaadittuPatevyysluokka in KRYSP
                      vastattavat-tyotehtavat-tyonjohtaja-v2
                      tyonjohtaja-hanketieto
                      sijaisuus-tyonjohtaja
                      {:name      "patevyys-tyonjohtaja"
                       :type      :group
                       :validator :patevyys
                       :body      patevyys-tyonjohtaja-v3}
                      muut-rakennushankkeet-table
                      tyonjohtajan-hyvaksynta
                      tyonjohtajan-rakennuskohteet
                      tyonjohtajan-suostumus
                      tyonjohtaja-toiminnot))

(def maksaja (body
               (henkilo-yritys-select-group
                 :yritys-body yritys-with-verkkolaskutustieto
                 :henkilo-body henkilo-maksaja)
               {:name "laskuviite" :type :string :max-len 500 :layout :full-width}))

(def ya-maksaja (body
                  (henkilo-yritys-select-group :default "yritys"
                                               :yritys-body yritys-with-verkkolaskutustieto
                                               :henkilo-body henkilo-maksaja)
                  {:name "laskuviite" :type :string :max-len 500 :layout :full-width}))

(def muutostapa {:name "muutostapa" :type  :select :sortBy  :displayname
                 :size :s           :label false   :i18nkey "huoneistot.muutostapa"
                 :body [{:name "poisto"}
                        {:name "lisäys" :i18nkey "huoneistot.muutostapa.lisays"}
                        {:name "muutos"}]})

(def muutostapa-hidden (assoc muutostapa
                              :default "lisäys"
                              :hidden true))

(def huoneisto-row [{:name     "huoneistoTyyppi" :type    :select :sortBy :displayname :size :s
                     ;; Note the spelling, this is `huoneistonTyyppi` in the XML schema
                     :label    false             :i18nkey "huoneistot.huoneistoTyyppi"
                     :required true
                     :body     [{:name "asuinhuoneisto"}
                                {:name "toimitila"}
                                ;; Should be hidden in rakval-226
                                ei-tiedossa]}
                    {:name "porras" :type :string :subtype :letter :case :upper :max-len 1 :size :t :label false :i18nkey "huoneistot.porras" :transform :upper-case}
                    {:name "huoneistonumero" :type :string :subtype :number :min 0 :min-len 1 :max-len 3 :size :s :required true :label false :i18nkey "huoneistot.huoneistonumero"}
                    {:name "jakokirjain" :type :string :subtype :letter :case :lower :max-len 1 :size :t :label false :i18nkey "huoneistot.jakokirjain" :transform :lower-case}
                    {:name "huoneluku" :type :string :subtype :number :min 1 :max 99 :required true :size :t :label false :i18nkey "huoneistot.huoneluku"}
                    {:name "keittionTyyppi" :type :select :sortBy :displayname :required true :size :s :label false :i18nkey "huoneistot.keittionTyyppi"
                     :body [{:name "keittio"}
                            {:name "keittokomero"}
                            {:name "keittotila"}
                            {:name "tupakeittio"}
                            ;; Should be hidden in rakval-226
                            ei-tiedossa]}
                    {:name "huoneistoala" :type :string :subtype :decimal :size :s :min 1 :max 9999999 :required true :label false :i18nkey "huoneistot.huoneistoala"}
                    {:name "WCKytkin" :type :checkbox :label false :i18nkey "huoneistot.WCKytkin"}
                    {:name "ammeTaiSuihkuKytkin" :type :checkbox :label false :i18nkey "huoneistot.ammeTaiSuihkuKytkin"}
                    {:name "saunaKytkin" :type :checkbox :label false :i18nkey "huoneistot.saunaKytkin"}
                    {:name "parvekeTaiTerassiKytkin" :type :checkbox :label false :i18nkey "huoneistot.parvekeTaiTerassiKytkin"}
                    {:name "lamminvesiKytkin" :type :checkbox :label false :i18nkey "huoneistot.lamminvesiKytkin"}
                    {:name "pysyvaHuoneistotunnus" :type :string :size :s :label false :i18nkey "huoneistot.pysyvaHuoneistotunnus" :readonly true}])

(def huoneistotTable {:name                     "huoneistot"
                      :i18nkey                  "huoneistot"
                      :type                     :table
                      :uicomponent              :docgenHuoneistot
                      :validator                :huoneistot
                      :group-help               "huoneistot.groupHelpText"
                      :repeating                true
                      :repeating-allow-empty    true
                      :approvable               true
                      :copybutton               true
                      :allow-highlight-changes? true
                      :allow-premise-upload?    false
                      :body                     (conj huoneisto-row muutostapa)})

(def huoneistotTable-new-building (assoc huoneistotTable
                                         :allow-highlight-changes? false
                                         :allow-premise-upload? true
                                         :body (conj huoneisto-row muutostapa-hidden)))

(def rakennuksen-kayttotarkoitus
  "Usage type definitions have moved to lupapiste-commons.usage-types"
  (->> usages/rakennuksen-kayttotarkoitus
       (mapv (fn [item]
               (if (= "ei tiedossa" (:name item))
                 ;; Should be hidden in rakval-226
                 item
                 item)))))

(def kayttotarkoitus {:name     "kayttotarkoitus"
                      :type     :select
                      :sortBy   :displayname
                      :size     :l
                      :i18nkey  "kayttotarkoitus"
                      :required true
                      :body     rakennuksen-kayttotarkoitus})

(def rakennusluokka {:name           "rakennusluokka"
                     :type           :select
                     :sortBy         :displayname
                     :size           :l
                     :i18nkey        "rakennusluokka"
                     :required       true
                     :schema-include :rakennusluokka
                     :body           building-types/rakennuksen-rakennusluokka})

(def rakennusluokka-vahahiilisyys
  {:name           "rakennusluokka-vahahiilisyys"
   :i18nkey        "rakennusluokka-vahahiilisyys"
   :schema-include :building-product-catalog
   :type           :group
   :css            top-container-css
   :body           [(ryhti-id :buildingPurposeForLowCarbonAssessmentKey)
                    {:name           "rakennusluokka-vahahiilisyys"
                     :type           :select
                     :sortBy         :displayname
                     :size           :l
                     :i18nkey        "rakennusluokka-vahahiilisyys"
                     :schema-include :building-product-catalog
                     :emit           [:lowCarbonAssessmentCriteriaChanged]
                     :body           (mapping->options ryhti-mappings/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa
                                                       :i18nkey :suomi.fi.rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa)}]})

;; 2.2.6 introduces a new concept: `valiaikainenRakennus`, which replaces
;; the existing `tilapainenRakennus` in practice. However, the latter is still defined
;; in the 2.2.6 schema.

(def tilapainenRakennusKytkin {:name           "tilapainenRakennusKytkin"
                               :type           :checkbox
                               :i18nkey        "tilapainenRakennusKytkin"
                               :schema-include :rakval-223
                               :schema-exclude :rakval-226})

(def tilapainenRakennusvoimassaPvm
  {:i18nkey        "rakennuksenTilapaisuusvoimassaPvm"
   :name           "tilapainenRakennusvoimassaPvm"
   :type           :date
   :show-when      {:path "tilapainenRakennusKytkin" :values #{true}}
   :schema-include :rakval-224
   :schema-exclude :rakval-226})

(def valiaikainenRakennusKytkin {:name           "valiaikainenRakennusKytkin"
                                 :type           :checkbox
                                 :i18nkey        "valiaikainenRakennusKytkin"
                                 :schema-include :rakval-226})

(def valiaikainenRakennusvoimassaPvm
  {:i18nkey        "rakennuksenValiaikaisuusvoimassaPvm"
   :name           "valiaikainenRakennusvoimassaPvm"
   :type           :date
   :show-when      {:path "valiaikainenRakennusKytkin" :values #{true}}
   :schema-include :rakval-226})

(def esteetonKytkin {:name           "esteetonKytkin"
                     :type           :checkbox
                     :i18nkey        "esteetonKytkin"
                     :schema-include :rakval-226})

;; TODO: Could use mapping->options and mappings/etvra-tyyppi
;; NOTE: Is the order here important?
(def etvra-tyyppi
  "etvra = Erityista toimintaa varten rakennettava alue"
  [{:name "aurinkopaneelikentta"}
   {:name "kaatopaikka"}
   {:name "puisto"}
   {:name "pysakointialue"}
   {:name "tapahtuma-alue"}
   {:name "tori"}
   {:name "urheilualue"}
   {:name "varastointialue"}
   {:name "muu-alue"}])

(def rakennelman-kayttotarkoitukset (-> ryhti-mappings/rakennelman-kayttotarkoitus
                                        keys
                                        sort
                                        vec))

(def rakennelman-kayttotarkoitus {:name    "kayttotarkoitus"
                                  :type    :select
                                  :size    :l
                                  :i18nkey "rakennelman-kayttotarkoitus"
                                  :body    (mapv #(hash-map :i18nkey (str "rakennelman-kayttotarkoitus." %) :name %) rakennelman-kayttotarkoitukset)})

(def erityista-toimintaa-varten-rakennettava-alue
  [{:name     "tyyppi"
    :type     :select
    :size     :l
    :body     etvra-tyyppi
    :required true}
   kuvaus
   {:name     "pintaAla"
    :type     :string
    :subtype  :number
    :required true
    :size     :s
    :unit     :m2
    :min      -9999999
    :max      9999999
    :layout   :full-width}])

(def rakentajaTyyppi {:name "rakentajaTyyppi" :type :select :sortBy :displayname :required true
                      :body [{:name "liiketaloudellinen"}
                             {:name "muu"}
                             ;; Should be hidden in rakval-226
                             ei-tiedossa]})

(def kaytto {:name "kaytto"
             :type :group
             :body [rakentajaTyyppi
                    kayttotarkoitus
                    rakennusluokka
                    tilapainenRakennusKytkin
                    tilapainenRakennusvoimassaPvm
                    valiaikainenRakennusKytkin
                    valiaikainenRakennusvoimassaPvm
                    esteetonKytkin]})

(def kaytto-minimal {:name "kaytto"
                     :type :group
                     :body [(assoc kayttotarkoitus :required false)
                            (assoc rakennusluokka :required false)]})

(def default-rakennusoikeusValinta "kerrosala")

(defn rakennusoikeus-fields
  "KuntaGML 2.2.6 requires that the user chooses how the rakennusoikeus is measured/reported.
   `kerrosala` is the typical case so present it as the default"
  [{:keys [laajentaminen]}]
  [;; Always show the selector, but select the choices depending on the KuntaGML version
   {:name     "rakennusoikeusValinta"
    :type     :select
    :css      [:dropdown :w--20em]
    :i18nkey  "ulkokuorentiedot.rakennusoikeusValinta"
    :sortBy   :displayname
    :required true
    :default  default-rakennusoikeusValinta
    :body     [{:name default-rakennusoikeusValinta}
               {:name "tilavuus" :schema-include :rakval-226}
               {:name "harjakorkeus" :schema-include :rakval-226}]}
   {:name      "rakennusoikeudellinenKerrosala"
    :type      :string
    :i18nkey   "ulkokuorentiedot.rakennusoikeudellinenKerrosala"
    :size      :s
    :unit      :m2
    :subtype   :number
    :min       (if laajentaminen -9999999 1)
    :max       9999999
    :ifc-mapping :ifc-agg-rakennusoikeudellinenkerrosala
    :show-when {:path   "rakennusoikeusValinta"
                :values #{default-rakennusoikeusValinta}}}
   {:name           "rakennusoikeusKuutiometreina"
    :type           :string
    :i18nkey        "ulkokuorentiedot.rakennusoikeusKuutiometreina"
    :size           :s
    :subtype        :number
    :unit           :m3
    :schema-include :rakval-226
    :show-when      {:path   "rakennusoikeusValinta"
                     :values #{"tilavuus" "harjakorkeus"}}}
   {:name           "harjanKorkeusrakennusalalla"
    :type           :string
    :i18nkey        "ulkokuorentiedot.harjanKorkeusrakennusalalla"
    :size           :s
    :unit           :m
    :subtype        :decimal
    :schema-include :rakval-226
    :show-when      {:path   "rakennusoikeusValinta"
                     :values #{"harjakorkeus"}}}])

(defn mitat-body [& {:as opts}]
  (into [{:name "tilavuus" :type :string :i18nkey "ulkokuorentiedot.tilavuus"
          :size :s         :unit :m3     :subtype :number :min 0 :max 9999999
          :ifc-mapping :ifc-ulkokuori-tilavuus}
         {:name "kerrosala" :type :string :i18nkey "ulkokuorentiedot.kerrosala"
          :size :s          :unit :m2     :subtype :number :min 0 :max 9999999
          :ifc-mapping :ifc-agg-kerrosala}
         {:name "kokonaisala" :type :string :i18nkey "ulkokuorentiedot.kokonaisala"
          :size :s            :unit :m2     :subtype :number :min 0 :max 9999999
          :ifc-mapping :ifc-agg-kokonaisala}
         {:name "kerrosluku" :type    :string :i18nkey "ulkokuorentiedot.kerrosluku"
          :size :s           :subtype :number :min     0 :max 50
          :ifc-mapping :ifc-ulkokuori-kerrosluku}
         {:name           "korkeus" :type :string :i18nkey "ulkokuorentiedot.korkeus"
          :size           :s        :unit :m      :subtype :number :min 0 :max 9999999
          :schema-include :ryhti-data-2026
          :ifc-mapping    :ifc-ulkokuori-korkeus}
         {:name           "suhde-maan-pintaan" :type :select
          :i18nkey        "ulkokuorentiedot.suhde-maan-pintaan"
          :schema-include :ryhti-data-2026
          :ifc-mapping    :ifc-ulkokuori-suhde-maan-pintaan
          :body           (mapping->options ryhti-mappings/suhde-pintaan
                                            :i18nkey :suomi.fi.suhde-maan-pintaan)}
         {:name    "kellarinpinta-ala"                  :type :string
          :i18nkey "ulkokuorentiedot.kellarinpinta-ala" :size :s :unit :m2
          :subtype :number                              :min  1  :max  9999999 :schema-exclude :ryhti-data-2026}]
        (rakennusoikeus-fields opts)))

(defn make-mitat [& {:as opts}]
  (cond-> {:name "mitat" :type :group :body (mitat-body opts)}
    (:muuttaminen opts) (-> (update :body conj {:name    "muutosala" :type :string :size :s :unit :m2
                                                :subtype :number     :min  0       :max  9999999})
                            (merge {:group-help "mitat-muutos.help"
                                    :whitelist  {:roles [:authority] :otherwise :disabled}}))))

(def mitat (make-mitat))

(def mitat-muutos (make-mitat :muuttaminen true))

(defn rakenne [& {:keys [optional]}]
  {:name "rakenne"
   :type :group
   :body [{:name "rakentamistapa" :type :select :sortBy :displayname :required (not optional) :i18nkey "rakentamistapa"
           :body [{:name "elementti"}
                  {:name "paikalla"}
                  ;; Should be hidden in rakval-226
                  ei-tiedossa]}
          {:name "kantavaRakennusaine" :type :select :sortBy :displayname :required (not optional) :other-key "muuRakennusaine" :i18nkey "kantavaRakennusaine"
           :body [{:name "betoni"}
                  {:name "tiili"}
                  {:name "teräs"}
                  {:name "puu"}
                  ei-tiedossa]}
          {:name "muuRakennusaine" :type :string :i18nkey "kantavaRakennusaine.muuRakennusaine"}
          {:name "julkisivu" :type :select :sortBy :displayname :other-key "muuMateriaali" :i18nkey "julkisivu"
           :body [{:name "betoni"}
                  {:name "tiili"}
                  {:name "metallilevy"}
                  {:name "kivi"}
                  {:name "puu"}
                  {:name "lasi"}
                  ei-tiedossa]}
          {:name "muuMateriaali" :type :string :i18nkey "julkisivu.muuMateriaali"}]})

(def lammonlahde-kuntagml-options
  [{:name "kauko tai aluelämpö"}
   {:name "kevyt polttoöljy"}
   {:name "raskas polttoöljy"}
   {:name "sähkö"}
   {:name "kaasu"}
   {:name "kiviihiili koksi tms"}
   {:name "turve"}
   {:name "maalämpö"}
   {:name "puu"}
   ei-tiedossa])

(def lammitystapa-kuntagml-options
  [{:name "vesikeskus"}
   {:name "ilmakeskus"}
   {:name "suora sähkö"}
   {:name "uuni"}
   {:name "ei lämmitystä"}
   ;; Should be hidden in rakval-226
   ei-tiedossa])

(def lammitys {:name "lammitys"
               :type :group
               :body [{:name    "lammitystapa"
                       :type    :select
                       :sortBy  :displayname
                       :i18nkey "lammitystapa"
                       :body    lammitystapa-kuntagml-options}
                      {:name      "lammonlahde"
                       :type      :select
                       :sortBy    :displayname
                       :other-key "muu-lammonlahde"
                       :i18nkey   "lammonlahde"
                       :body      lammonlahde-kuntagml-options}
                      {:name "muu-lammonlahde" :type :string :i18nkey "lammonlahde.muu-lammonlahde"}]})

(def verkostoliittymat {:name "verkostoliittymat" :type :group :layout :vertical :i18nkey "verkostoliittymat"
                        :body [{:name "viemariKytkin" :type :checkbox :i18nkey "viemariKytkin"}
                               {:name "vesijohtoKytkin" :type :checkbox :i18nkey "vesijohtoKytkin"}
                               {:name "sahkoKytkin" :type :checkbox :i18nkey "sahkoKytkin"}
                               {:name "maakaasuKytkin" :type :checkbox :i18nkey "maakaasuKytkin"}
                               {:name "kaapeliKytkin" :type :checkbox :i18nkey "kaapeliKytkin"}]})

(def default-varusteet {:name "varusteet" :type :group :layout :vertical :i18nkey "varusteet"
                        :body [{:name "sahkoKytkin" :type :checkbox :i18nkey "sahkoKytkin"}
                               {:name "kaasuKytkin" :type :checkbox :i18nkey "kaasuKytkin"}
                               {:name "viemariKytkin" :type :checkbox :i18nkey "viemariKytkin"}
                               {:name "vesijohtoKytkin" :type :checkbox :i18nkey "vesijohtoKytkin"}
                               {:name "hissiKytkin" :type :checkbox :i18nkey "hissiKytkin"}
                               {:name "koneellinenilmastointiKytkin" :type :checkbox :i18nkey "koneellinenilmastointiKytkin"}
                               {:name "lamminvesiKytkin" :type :checkbox :i18nkey "lamminvesiKytkin"}
                               {:name "aurinkopaneeliKytkin" :type :checkbox :i18nkey "aurinkopaneeliKytkin"}
                               {:name "saunoja" :type :string :subtype :number :min 0 :max 99 :size :s :unit :kpl :i18nkey "saunoja"} ; max value is resricted by vrk validators
                               {:name "vaestonsuoja" :type :string :subtype :number :min 0 :size :s :unit :hengelle :i18nkey "vaestonsuoja"}
                               {:name "kokoontumistilanHenkilomaara" :type :string :subtype :number :min 0 :size :s :i18nkey "kokoontumistilanHenkilomaaraMuutos"}
                               {:name "liitettyJatevesijarjestelmaanKytkin" :type :checkbox :i18nkey "liitettyJatevesijarjestelmaanKytkin"}]})

(def alter-varusteet (partial t/alter-schema default-varusteet))

(def varusteet (alter-varusteet))

(def varusteet-uusi (alter-varusteet ["kokoontumistilanHenkilomaara" :i18nkey "kokoontumistilanHenkilomaara"]))

(def energialuokka-kuntagml-options
  [{:name "A"}
   {:name "B"}
   {:name "C"}
   {:name "D"}
   {:name "E"}
   {:name "F"}
   {:name "G"}])

(def luokitus {:name "luokitus"
               :type :group
               :body [{:name    "energialuokka"
                       :type    :select
                       :sortBy  :displayname
                       :i18nkey "energialuokka"
                       :body    energialuokka-kuntagml-options}
                      {:name "energiatehokkuusluku" :type :string :size :s :subtype :number}
                      {:name "energiatehokkuusluvunYksikko" :type :select, :sortBy :displayname, :default "kWh/m2"
                       :body [{:name "kWh/m2"}
                              {:name "kWh/brm2/vuosi"}]}
                      {:name "paloluokka" :type :select :sortBy :displayname :i18nkey "paloluokka"
                       :body (->> [{:name "palonkestävä"}
                                   {:name "paloapidättävä"}
                                   {:name "paloahidastava"}
                                   {:name "lähinnä paloakestävä"}
                                   {:name "lähinnä paloapidättävä"}
                                   {:name "lähinnä paloahidastava"}
                                   {:name "P0"}
                                   {:name "P1"}
                                   {:name "P2"}
                                   {:name "P3"}
                                   {:name "P1/P2"}
                                   {:name "P1/P3"}
                                   {:name "P2/P3"}
                                   {:name "P1/P2/P3"}]
                                  (remove nil?)
                                  vec)}]})

(defn puhtaan-siirtyman-lupa-old [show?]
  [{:name             "show-clean-transition"
    :type             :checkbox
    :css              [:hidden]
    :exclude-from-pdf true
    :i18nkey          "puhtaan-siirtyman-lupa.show-clean-transition"
    :default          show?}
   {:name      "puhtaan-siirtyman-lupa"
    :type      :group
    :show-when {:path "show-clean-transition" :values #{true}}
    :css       [:group-border :hide-header :!mt-2]
    :body      [{:name      "puhtaan-siirtyman-lupa-enabled"
                 :i18nkey   "puhtaan-siirtyman-lupa.enabled"
                 :type      :checkbox
                 :inputType :checkbox-wrapper
                 :css       [:hide-blockbox-wrapper-border :!mb-0 :p-2 :!min-h-min :long]
                 :default   false}
                {:name       "tyyppi"
                 :i18nkey    "puhtaan-siirtyman-lupa.tyyppi"
                 :type       :radioGroup
                 :vertical   true
                 :show-label true
                 :show-when  {:path "puhtaan-siirtyman-lupa-enabled" :values #{true}}
                 :body       [{:name    "energiatuotantolaitos"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.energiatuotantolaitos"}
                              {:name    "teollisuuden-hanke"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.teollisuuden-hanke"}
                              {:name    "vedyn-valmistus"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.vedyn-valmistus"}
                              {:name    "hiilidioksidin-talteenotto"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.hiilidioksidin-talteenotto"}
                              {:name    "akkutehdas"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.akkutehdas"}
                              {:name    "nettonollateknologiatuotteet"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.nettonollateknologiatuotteet"}
                              {:name    "uusiutuvat-lahteet"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.uusiutuvat-lahteet"}
                              {:name    "palvelinkeskus"
                               :i18nkey "puhtaan-siirtyman-lupa.tyyppi.palvelinkeskus"}]}]}])

(def puhtaan-siirtyman-lupa
  [{:name "puhtaan-siirtyman-lupa"
    :type :group
    :css  [:group-border :hide-header :!mt-2]
    :body [{:name       "tyyppi"
            :i18nkey    "puhtaan-siirtyman-lupa.tyyppi"
            :default    "energiatuotantolaitos"
            :type       :radioGroup
            :vertical   true
            :show-label true
            :body       [{:name    "energiatuotantolaitos"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.energiatuotantolaitos"}
                         {:name    "teollisuuden-hanke"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.teollisuuden-hanke"}
                         {:name    "vedyn-valmistus"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.vedyn-valmistus"}
                         {:name    "hiilidioksidin-talteenotto"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.hiilidioksidin-talteenotto"}
                         {:name    "akkutehdas"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.akkutehdas"}
                         {:name    "nettonollateknologiatuotteet"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.nettonollateknologiatuotteet"}
                         {:name    "uusiutuvat-lahteet"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.uusiutuvat-lahteet"}
                         {:name    "palvelinkeskus"
                          :i18nkey "puhtaan-siirtyman-lupa.tyyppi.palvelinkeskus"}]}]}])

(defn with-puhtaan-siirtyman-lupa [body & {:keys [show?]}]
  (into (puhtaan-siirtyman-lupa-old show?) body))

(def rakennustunnus {:name      national-building-id :type :string :subtype :rakennustunnus :hidden true :readonly false
                     :whitelist {:permitType [:ARK] :roles [:authority] :otherwise :disabled}})

(def rakennuksen-tiedot-ilman-huoneistoa [kaytto
                                          rakennusluokka-vahahiilisyys
                                          mitat
                                          (rakenne)
                                          lammitys
                                          verkostoliittymat
                                          varusteet
                                          luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-uusi [kaytto
                                               rakennusluokka-vahahiilisyys
                                               mitat
                                               (rakenne)
                                               lammitys
                                               verkostoliittymat
                                               varusteet-uusi
                                               luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-muutos [kaytto
                                                 mitat-muutos
                                                 (rakenne)
                                                 lammitys
                                                 verkostoliittymat
                                                 varusteet
                                                 luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja [kaytto
                                                                  (rakenne)
                                                                  mitat])

(def rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja-muutos [kaytto
                                                                         (rakenne)
                                                                         mitat-muutos])

(def rakennuksen-tiedot-sijoittamislupa [kaytto
                                         mitat
                                         (rakenne {:optional true})
                                         lammitys
                                         verkostoliittymat
                                         varusteet-uusi
                                         luokitus])

(def rakennuksen-tiedot (conj rakennuksen-tiedot-ilman-huoneistoa huoneistotTable))

(def rakennuksen-tiedot-uusi-rakennus (conj rakennuksen-tiedot-ilman-huoneistoa-uusi huoneistotTable-new-building))

(def rakennuksen-tiedot-muutos (conj rakennuksen-tiedot-ilman-huoneistoa-muutos huoneistotTable))

(def alle-yli-radiogroup
  {:name "alleYli" :type :radioGroup :body [{:name "alle"} {:name "yli"}] :default "alle" :required true})

(defn etaisyys-row [name min-default]
  {:name name
   :type :group
   :body [{:name "minimietaisyys" :type :string :size :s :unit :m :readonly true :default min-default :required true}
          alle-yli-radiogroup
          {:name "huomautukset" :type :string :size :l}]})

(def maalampokaivon-etaisyydet {:name       "kaivo-etaisyydet"
                                :i18nkey    "maalampokaivo.kaivo-etaisyydet"
                                :type       :group
                                :group-help "maalampokaivo.kaivo-etaisyydet.groupHelpText"
                                :approvable true
                                :body       (body
                                              (etaisyys-row "lampokaivo" "15")
                                              (etaisyys-row "porakaivo" "40")
                                              (etaisyys-row "rengaskaivo" "20")
                                              (etaisyys-row "rakennus" "3")
                                              (etaisyys-row "tontin-raja" "7.5")
                                              (etaisyys-row "omat-vv-johdot" "3") ; vesi- ja viemarijohdot
                                              (etaisyys-row "muut-vv-johdot" "5") ; vesi- ja viemarijohdot
                                              (etaisyys-row "omat-lampojohdot" "3")
                                              (etaisyys-row "muut-lampojohdot" "5")
                                              (etaisyys-row "wc-jatevedet-purkupaikka" "30")
                                              (etaisyys-row "harmaat-jatevedet-purkupaikka" "20"))})

(def maalampokaivo-rakennelma (body
                                kuvaus
                                {:name           "kaivojen-lukumaara"
                                 :type           :string
                                 :subtype        :number
                                 :size           :s
                                 :min            1
                                 :max            99
                                 :i18nkey        "maalampokaivo.kaivojen-lukumaara"
                                 :schema-include :maalampokaivo-count}
                                maalampokaivon-etaisyydet))

(def tilapainenRakennelmaKytkin
  {:name    "tilapainenRakennelmaKytkin"
   :type    :checkbox
   :i18nkey "tilapainenRakennelmaKytkin"})

(def tilapainenRakennelmavoimassaPvm
  {:name      "tilapainenRakennelmavoimassaPvm"
   :type      :date
   :i18nkey   "rakennelmanTilapaisuusvoimassaPvm"
   :show-when {:path "tilapainenRakennelmaKytkin" :values #{true}}})

(def rakennelma (body {:name "kokonaisala" :type :string :size :s :unit :m2 :subtype :number}
                      rakennelman-kayttotarkoitus
                      kuvaus
                      tilapainenRakennelmaKytkin
                      tilapainenRakennelmavoimassaPvm))

(def rakennelma-kokoontumistilalla
  (conj (vec rakennelma)
        {:name "kokoontumistilanHenkilomaara" :type    :string :subtype :number :min 0
         :size :s                             :i18nkey "kokoontumistilanHenkilomaara"}))

(def maisematyo (body kuvaus))

(def omistajalajit
  [{:name "omistajalaji" :type :select :sortBy :displayname :other-key "muu-omistajalaji" :required true
    :size :l :i18nkey "omistajalaji._group_label" :ifc-mapping :ifc-omistajat-omistajalaji
    :body [{:name "yksityinen maatalousyrittäjä" :i18nkey "omistajalaji.yksityinen-maatalousyrittaja"}
           {:name "muu yksityinen henkilö tai perikunta" :i18nkey "omistajalaji.muu-yksityinen"}
           {:name "asunto-oy tai asunto-osuuskunta" :i18nkey "omistajalaji.asunto-oy-tms"}
           {:name "kiinteistö oy" :i18nkey "omistajalaji.kiinteisto-oy"}
           {:name "yksityinen yritys (osake-, avoin- tai kommandiittiyhtiö, osuuskunta)" :i18nkey "omistajalaji.yksityinen-yritys"}
           {:name "valtio- tai kuntaenemmistöinen yritys" :i18nkey "omistajalaji.valtio-tai-kunta-yritys"}
           {:name "kunnan liikelaitos" :i18nkey "omistajalaji.kunnan-liikelaitos"}
           {:name "valtion liikelaitos" :i18nkey "omistajalaji.valtion-liikelaitos"}
           {:name "pankki tai vakuutuslaitos" :i18nkey "omistajalaji.pankki-tai-vakuutuslaitos"}
           {:name "kunta tai kuntainliitto" :i18nkey "omistajalaji.kunta-tai-kuntaliitto"}
           {:name "valtio" :i18nkey "omistajalaji.valtio"}
           {:name "sosiaaliturvarahasto" :i18nkey "omistajalaji.sosiaaliturvarahasto"}
           {:name "uskonnollinen yhteisö, säätiö, puolue tai yhdistys" :i18nkey "omistajalaji.yhteiso-saatio-puolue-yhdistys"}
           ei-tiedossa]}
   {:name "muu-omistajalaji" :type :string :i18nkey "muu-omistajalaji"}])

(def rakennuksen-omistajat [{:name                            "rakennuksenOmistajat"
                             :type                            :group
                             :repeating                       true
                             :approvable                      true
                             ;; For cljs docgen, force disabled even when in editing state for post-verdict-editable document.
                             :post-verdict-editable-disabled? true
                             :body                            (body building-parties
                                                                    omistajalajit)}])

(def muumuutostyo "muut muutostyöt")
(def perustusten-korjaus "perustusten ja kantavien rakenteiden muutos- ja korjaustyöt")
(def kayttotarkotuksen-muutos "rakennukse pääasiallinen käyttötarkoitusmuutos")

(def muutostyonlaji [{:name        "perusparannuskytkin"
                      :type        :checkbox
                      :classPrefix :bottombox}
                     {:name        "rakennustietojaEimuutetaKytkin" :type :checkbox
                      :classPrefix :bottombox
                      :whitelist   {:roles [:authority] :otherwise :hidden}}
                     {:name   "muutostyolaji" :type     :select :size    :l
                      :sortBy :displayname    :required true    :i18nkey "muutostyolaji"
                      :body
                      [{:name perustusten-korjaus}
                       {:name kayttotarkotuksen-muutos}
                       {:name muumuutostyo}]}])

(def olemassaoleva-rakennus (body
                              rakennuksen-valitsin
                              rakennuksen-omistajat
                              rakennuksen-osoite
                              rakennuksen-tiedot))

(def olemassaoleva-rakennus-ilman-rakennustietoja (body
                                                    rakennuksen-valitsin
                                                    rakennuksen-omistajat
                                                    rakennuksen-osoite))

(def olemassaoleva-rakennus-muutos (body
                                     rakennuksen-valitsin
                                     rakennuksen-omistajat
                                     rakennuksen-osoite
                                     rakennuksen-tiedot-muutos))

(def olemassaoleva-rakennus-ei-huoneistoja (body
                                             rakennuksen-valitsin
                                             rakennuksen-omistajat
                                             rakennuksen-osoite
                                             rakennuksen-tiedot-ilman-huoneistoa))

(def olemassaoleva-rakennus-ei-huoneistoja-muutos (body
                                                    rakennuksen-valitsin
                                                    rakennuksen-omistajat
                                                    rakennuksen-osoite
                                                    rakennuksen-tiedot-ilman-huoneistoa-muutos))

(def olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja
  (body rakennuksen-valitsin
        rakennuksen-omistajat
        rakennuksen-osoite
        rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja))

(def olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja-muutos
  (body rakennuksen-valitsin
        rakennuksen-omistajat
        rakennuksen-osoite
        rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja-muutos))

(def rakennuksen-muuttaminen-ei-huoneistoja (body
                                              muutostyonlaji
                                              olemassaoleva-rakennus-ei-huoneistoja))

(def rakennuksen-muuttaminen-ei-huoneistoja-muutos (body
                                                     muutostyonlaji
                                                     olemassaoleva-rakennus-ei-huoneistoja-muutos))

(def rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja (body
                                                                    muutostyonlaji
                                                                    olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja))

(def rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja-muutos (body
                                                                           muutostyonlaji
                                                                           olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja-muutos))

(def rakennuksen-muuttaminen (body
                               muutostyonlaji
                               olemassaoleva-rakennus))

(def rakennuksen-muuttaminen-muutos (body
                                      muutostyonlaji
                                      olemassaoleva-rakennus-muutos))

(def laajentaminen
  [{:name "laajennuksen-tiedot"
    :type :group
    :body [{:name "perusparannuskytkin"
            :type :checkbox}
           {:name "mitat"
            :type :group
            :body (into [{:name    "tilavuus"
                          :type    :string
                          :size    :s
                          :unit    :m3
                          :subtype :number
                          :min     -9999999
                          :max     9999999}
                         {:name    "kerrosala"
                          :type    :string
                          :size    :s
                          :unit    :m2
                          :subtype :number
                          :min     -9999999
                          :max     9999999}
                         {:name    "kokonaisala"
                          :type    :string
                          :size    :s
                          :unit    :m2
                          :subtype :number
                          :min     -9999999
                          :max     9999999}
                         {:name                  "huoneistoala"
                          :type                  :group
                          :repeating             true
                          :repeating-allow-empty true
                          :body                  [{:name    "pintaAla"
                                                   :type    :string
                                                   :size    :s
                                                   :unit    :m2
                                                   :subtype :number
                                                   :min     -9999999
                                                   :max     9999999}
                                                  {:name   "kayttotarkoitusKoodi"
                                                   :type   :select
                                                   :sortBy :displayname
                                                   :body   [{:name "asuntotilaa(ei vapaa-ajan asunnoista)"}
                                                            {:name "myymälä, majoitus- ja ravitsemustilaa"}
                                                            {:name "hoitotilaa"}
                                                            {:name "toimisto- ja hallintotilaa"}
                                                            {:name "kokoontumistilaa"}
                                                            {:name "opetustilaa"}
                                                            {:name "tuotantotilaa(teollisuus)"}
                                                            {:name "varastotilaa"}
                                                            {:name "muuta huoneistoalaan kuuluvaa tilaa"}
                                                            ;; Should be hidden in yht-2111
                                                            ei-tiedossa]}]}]
                        (rakennusoikeus-fields {:laajentaminen true}))}]}])

(def rakennuksen-laajentaminen-ilman-rakennustietoja (body
                                                       olemassaoleva-rakennus-ilman-rakennustietoja
                                                       laajentaminen))

(def rakennuksen-laajentaminen (body
                                 rakennuksen-laajentaminen-ilman-rakennustietoja
                                 rakennuksen-tiedot))

(def rakennuksen-laajentaminen-ei-huoneistoja (body
                                                rakennuksen-laajentaminen-ilman-rakennustietoja
                                                rakennuksen-tiedot-ilman-huoneistoa))

(def poistumanAjankohta {:name "poistumanAjankohta" :type :date})

(def poistuma  (body {:name "poistumanSyy" :type :select :sortBy :displayname
                      :size :l
                      :body [{:name "purettu uudisrakentamisen vuoksi"}
                             {:name "purettu muusta syystä"}
                             {:name "tuhoutunut"}
                             {:name "ränsistymisen vuoksi hylätty"}
                             {:name "poistaminen"}]}
                     poistumanAjankohta))

(def purku (body
             poistuma
             olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja))

(defn remove-required [form]
  (walk/postwalk #(util/pcond-> %
                                map? (dissoc :required)) form))

(defn add-show-when [xs show-when-map]
  (mapv (fn [{:keys [hidden] :as m}]
          (cond-> m
            (not hidden) (assoc :show-when show-when-map)))
        xs))

(def purkuilmoitus
  "The structure must be compatible with `purku`, since there is a use case, where
  purkamisilmoitus -> purkaminen."
  (let [show-when {:path "buildingDemolition" :values #{true}}]
    (-> (body poistuma
              {:name        "buildingDemolition" :type :checkbox :default true
               :classPrefix :checkbox            :css  [:full-width]
               :emit        [:buildingDemolition]}
              {:name      "kohde" :type :text :layout :full-width
               :hide-when show-when}
              (add-show-when rakennuksen-valitsin show-when)
              (add-show-when rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja
                             show-when)
              rakennuksen-omistajat
              rakennuksen-osoite)
        remove-required
        vec)))

(def rakennuspaikka [(ryhti-id :buildingSiteKey)
                     {:name        "kiinteisto"
                      :type        :group
                      :uicomponent :propertyGroup
                      :body        [{:name      "maaraalaTunnus" :type :maaraalaTunnus :uicomponent :maaraala-tunnus :size :s
                                     :transform :zero-pad-4}
                                    ;; {:name "luvanNumero" :type :string :size :m :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.luvanNumero"}
                                    {:name "tilanNimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                    {:name "rekisterointipvm" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                    {:name "maapintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                    {:name "vesipintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                    {:name "rantaKytkin" :type :checkbox :uicomponent :docgen-input :inputType :checkbox}]}
                     {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                      :ifc-mapping :ifc-rakennuspaikka-hallintaperuste
                      :body [{:name "oma"}
                             {:name "vuokra"}
                             {:name "muu oikeus" :schema-include :yht-219}
                             ;; Should be hidden in yht-2111
                             (assoc ei-tiedossa :schema-exclude :ryhti-data-2026)]}
                     {:name "kaavanaste" :type :select :sortBy :displayname :hidden true
                      :body [{:name "asema"}
                             {:name "ranta"}
                             {:name "rakennus"}
                             {:name "yleis"}
                             {:name "ei kaavaa"}
                             ;; Should be hidden in yht-2111
                             ei-tiedossa]}
                     {:name "kaavatilanne" :type :select :sortBy :displayname
                      :ifc-mapping :ifc-rakennuspaikka-kaavatilanne
                      :body [{:name "maakuntakaava"}
                             {:name "oikeusvaikutteinen yleiskaava"}
                             {:name "oikeusvaikutukseton yleiskaava"}
                             {:name "asemakaava"}
                             {:name "ranta-asemakaava"}
                             {:name "ei kaavaa"}]}
                     {:name           "huleveden-kasittely"
                      :type           :select
                      :i18nkey        "huleveden-kasittely"
                      :required       true
                      :schema-include :ryhti-data-2026
                      ;; TODO: ryhti-mappings/hulevedenkasittely, use mapping->options
                      :body           (mapv #(assoc % :i18nkey (name (util/kw-path :huleveden-kasittely (:name %))))
                                            [{:name "imeyttaminen"}
                                             {:name "viivyttaminen ennen pois johtamista"}
                                             {:name "pois johtaminen ilman viivyttamista"}
                                             {:name "jatevesiviemari"}])}
                     {:name       "hankkeestaIlmoitettu" :type :group
                      :group-help "hankkeestaIlmoitettu.groupHelpText"
                      :body       [{:name "hankkeestaIlmoitettuPvm" :type :date :i18nkey "date"}]}])

(def rakennuspaikka-kuntagml
  "Only used with converted applications. The information is extracted from a KuntaGML message."
  [{:name        "kiinteisto"
    :type        :group
    :uicomponent :docgenGroup
    :body        [{:name        "kiinteistotunnus" :type      :string :readonly true
                   :uicomponent :docgen-input      :inputType :string}
                  {:name        "maaraalaTunnus" :type      :string :readonly true
                   :uicomponent :docgen-input    :inputType :string
                   :hidden      :by-value}
                  {:name        "tilanNimi"   :type      :string :readonly true
                   :uicomponent :docgen-input :inputType :string :hidden   :by-value}
                  {:name        "rekisterointipvm" :type      :string :readonly true
                   :uicomponent :docgen-input      :inputType :string :hidden   :by-value}
                  {:name        "kerrosala"   :type      :string :readonly true
                   :uicomponent :docgen-input :inputType :string :hidden   :by-value}
                  {:name   "maapintaala" :type        :string       :readonly  true
                   :unit   :hehtaaria    :uicomponent :docgen-input :inputType :string
                   :hidden :by-value}
                  {:name   "vesipintaala" :type        :string       :readonly  true
                   :unit   :hehtaaria     :uicomponent :docgen-input :inputType :string
                   :hidden :by-value}
                  {:name   "rakennusoikeusYhteensa" :type        :string       :readonly  true
                   :unit   :m2                      :uicomponent :docgen-input :inputType :string
                   :hidden :by-value}
                  {:name        "kylanimi"    :type      :string :readonly true
                   :uicomponent :docgen-input :inputType :string :hidden   :by-value}
                  {:name        "rantaKytkin" :type      :checkbox :readonly true
                   :uicomponent :docgen-input :inputType :checkbox :hidden   :by-value}]}
   {:name "osoite"
    :type :group
    :body [{:name        "kunta"       :type      :string :readonly true
            :uicomponent :docgen-input :inputType :string}
           {:name        "postinumero" :type      :string :readonly true
            :uicomponent :docgen-input :inputType :string :hidden   :by-value}
           {:name        "osoitenimi"  :type      :string :readonly true
            :uicomponent :docgen-input :inputType :string :hidden   :by-value}
           {:name        "osoitenumero" :type      :string :readonly true
            :uicomponent :docgen-input  :inputType :string :hidden   :by-value}
           {:name        "kaupunginosanumero" :type      :string :readonly true
            :uicomponent :docgen-input        :inputType :string :hidden   :by-value}
           {:name        "postitoimipaikannimi" :type      :string :readonly true
            :uicomponent :docgen-input          :inputType :string :hidden   :by-value}]}
   {:name "hallintaperuste" :type :select :sortBy :displayname :required true
    :body [{:name "oma"}
           {:name "vuokra"}
           ;; Should be hidden in yht-2111
           ei-tiedossa]}
   {:name "kaavatilanne" :type :select :sortBy :displayname
    :body [{:name "maakuntakaava"}
           {:name "oikeusvaikutteinen yleiskaava"}
           {:name "oikeusvaikutukseton yleiskaava"}
           {:name "asemakaava"}
           {:name "ranta-asemakaava"}
           {:name "ei kaavaa"}]}])

(defn- property-list-field [element-name & [opts]]
  (merge {:name     element-name
          :required true
          :type     :string
          :i18nkey  (str "kiinteistoLista." element-name)}
         opts))

(defn property-list-owners [element-name & [opts]]
  (let [show-when            (fn [values]
                               {:show-when {:path   "henkilolaji"
                                            :values values}})
        show-when-person     (show-when #{"luonnollinen" "kuolinpesa"})
        show-when-company    (show-when #{"juridinen"})
        property-owner-field (fn [field-name & args]
                               (-> (apply property-list-field field-name args)
                                   (assoc :i18nkey (format "kiinteistoLista.%s.%s" element-name field-name))))]
    (merge {:name                  element-name
            :type                  :table
            :repeating             true
            ;; Fixes Cljs docgen: would enforce one child always without this
            :repeating-allow-empty true
            :i18nkey               (str "kiinteistoLista." element-name)
            :body                  [(property-owner-field "nimi" show-when-company)
                                    (property-owner-field "yritystunnus" (assoc show-when-company :required false))
                                    (property-owner-field "etunimi" show-when-person)
                                    (property-owner-field "sukunimi" show-when-person)
                                    (property-owner-field "osoite" show-when-person)
                                    (property-owner-field "postinumero" show-when-person)
                                    (property-owner-field "postitoimipaikannimi" show-when-person)
                                    (property-owner-field "henkilolaji"
                                                          {:type :select
                                                           :body [{:name "luonnollinen"}
                                                                  {:name "kuolinpesa"}
                                                                  {:name "juridinen"}]})]}
           opts)))

(defn property-list [element-name]
  {:name                  element-name
   :type                  :table
   :uicomponent           :docgen-property-list
   :validator             :property-list
   :repeating             true
   ;; Fixes Cljs docgen: would enforce one child always without this
   :repeating-allow-empty true
   :approvable            true
   :body                  [(property-list-field "kiinteistotunnus" {:subtype   :kiinteistotunnus
                                                                    :transform :property-id})
                           (property-list-field "pintaAla" {:subtype  :decimal
                                                            :unit     :hehtaaria
                                                            :required false})
                           (property-list-field "tilanNimi" {:required false})
                           (property-list-field "rekisterointiPvm" {:type     :date
                                                                    :required false})
                           (property-list-owners "omistajat")]})

(def rasite-tai-yhteisjarjestely [{:name    "tyyppi"
                                   :type    :radioGroup
                                   :default "rakennusrasite"
                                   :body    [{:name "rakennusrasite"}
                                             {:name "yhteisjarjestely"}]}
                                  kuvaus
                                  {:name      "rakennusrasite"
                                   :type      :group
                                   :show-when {:path "tyyppi" :values #{"rakennusrasite"}}
                                   :body      [(property-list "oikeutetutTontit")
                                               (property-list "rasitetutTontit")]}
                                  {:name      "yhteisjarjestely"
                                   :type      :group
                                   :show-when {:path "tyyppi" :values #{"yhteisjarjestely"}}
                                   :body      [(property-list "kohdeTontit")]}])

(def lisakohde-rakennuspaikka [{:name        "kiinteisto"
                                :type        :group
                                :uicomponent :propertyGroup
                                :body        [{:name "maaraalaTunnus" :type :maaraalaTunnus :uicomponent :maaraala-tunnus :size :s}
                                              ;; Please change yhteystiedot-api/application-property-owners if kiinteistoTunnus path changes
                                              {:name "kiinteistoTunnus" :type :string :hidden true}
                                              ;; {:name "luvanNumero" :type :string :size :m :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.luvanNumero"}
                                              {:name "tilanNimi" :type :string :uicomponent :docgen-input :inputType :string}
                                              {:name "rekisterointipvm" :type :date}
                                              {:name "maapintaala" :type :string :unit :hehtaaria :uicomponent :docgen-input :inputType :string :subtype :decimal}
                                              {:name "vesipintaala" :type :string :unit :hehtaaria :uicomponent :docgen-input :inputType :string :subtype :decimal}
                                              {:name "rantaKytkin" :type :checkbox :uicomponent :docgen-input :inputType :checkbox}]}
                               {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                                :body [{:name "oma"}
                                       {:name "vuokra"}
                                       ;; Should be hidden in yht-2111
                                       ei-tiedossa]}
                               {:name "kaavanaste" :type :select :sortBy :displayname :hidden true
                                :body [{:name "asema"}
                                       {:name "ranta"}
                                       {:name "rakennus"}
                                       {:name "yleis"}
                                       {:name "ei kaavaa"}
                                       ;; Should be hidden in yht-2111
                                       ei-tiedossa]}
                               {:name "kaavatilanne" :type :select :sortBy :displayname
                                :body [{:name "maakuntakaava"}
                                       {:name "oikeusvaikutteinen yleiskaava"}
                                       {:name "oikeusvaikutukseton yleiskaava"}
                                       {:name "asemakaava"}
                                       {:name "ranta-asemakaava"}
                                       {:name "ei kaavaa"}]}])

(def rajankaynti-tyyppi {:name     "rajankayntiTyyppi"
                         :type     :select
                         :layout   :full-width
                         :required true
                         :body     [{:name "Rajan paikkaa ja rajamerkkiä koskeva epäselvyys (rajankäynti)"}
                                    {:name "Epäselvyys siitä, mihin rekisteriyksikköön jokin alue kuuluu"}
                                    {:name "Rasiteoikeutta ja rasitteen sijaintia koskeva epäselvyys"}
                                    {:name "Kiinteistön osuus yhteiseen alueeseen tai yhteiseen erityiseen etuuteen ja osuudensuuruus sekä kiinteistölle kuuluva erityinen etuus"}
                                    {:name "Yhteisen alueen tai yhteisen erityisen etuuden osakaskiinteistöt ja niille kuuluvien osuuksien suuruudet"}
                                    {:name "Epäselvän, kadonneen tai turmeltuneen toimitusasiakirjan tai kartan sisältö"}
                                    {:name "Ristiriitaisista toimitusasiakirjoista tai kartoista johtuva epäselvyys"}]})

(def uusi-tai-muutos
  "Used in maankayton-muutos."
  {:name     "uusiKytkin"
   :type     :radioGroup
   :required true
   :default  "uusi"
   :body     [{:name "uusi"},
              {:name "muutos"}]})

(def maastotyot
  {:name      "maastotyotToimituksenYhteydessa"
   :type      :checkbox
   :inputType :checkbox-wrapper
   :i18nkey   "kiinteistonmuodostus.maastotyotToimituksenYhteydessa"})

(def kiinteisto-row
  [{:name "tunnus"
    :type :string
    :css  [:grid-style-input--wide]}
   {:name        "pinta-ala"
    :type        :string
    :subtype     :decimal
    :uicomponent :docgen-input
    :inputType   :string
    :css         [:grid-style-input--wide]
    :min         0 :max 999999999
    :size        :s}])

(def erityista-toimintaa-varten-rakennettava-alue-v2
  (body erityista-toimintaa-varten-rakennettava-alue
        [{:name             "muita-kiinteistoja"
          :type             :table
          :uicomponent      :docgenTable
          :exclude-from-pdf true
          :css              [:form-table]
          :repeating        true
          :body             (t/body (map (fn [{n :name :as field}]
                                           (update field :i18nkey
                                                   (fn [k]
                                                     ;; NOTE: JS version ignores this and uses
                                                     ;; sijoittamislupa-v2.erityista-toimintaa-varten-rakennettava-alue.muita-kiinteistoja.tunnus
                                                     ;; or erityista-toimintaa-varten-rakennettava-alue.muita-kiinteistoja.tunnus
                                                     ;; Cljs version uses kiinteistoja.tunnus
                                                     ;; Once JS version is removed, those old loc keys could be removed
                                                     (or k (str "kiinteistoja." n)))))
                                         kiinteisto-row))}]))

(defn sijoittamislupa [& [puhdas-siirtyma]]
  [{:name     "hankkeen-tyyppi"
    :type     :select
    :required true
    :css      [:pt-4 :!mb-0]
    :body     [{:name "erityista-toimintaa-varten-rakennettava-alue"}
               {:name "rakennelma"}
               {:name "uusiRakennus"}]}
   {:name      "uusiRakennus"
    :type      :group
    :show-when {:path "hankkeen-tyyppi" :values #{"uusiRakennus"}}
    :body      (into rakennuksen-tiedot-sijoittamislupa
                     puhdas-siirtyma)}
   {:name      "rakennelma"
    :type      :group
    :show-when {:path "hankkeen-tyyppi" :values #{"rakennelma"}}
    :body      (into rakennelma
                     puhdas-siirtyma)}
   {:name      "erityista-toimintaa-varten-rakennettava-alue"
    :type      :group
    :show-when {:path "hankkeen-tyyppi" :values #{"erityista-toimintaa-varten-rakennettava-alue"}}
    :body      (into erityista-toimintaa-varten-rakennettava-alue-v2
                     puhdas-siirtyma)}])

;; Kiinteistotoimitukset

(def kt-kiinteistonmuodostus {:name       "kiinteistonmuodostus"
                              :type       :group
                              :approvable true
                              :body       [{:name     "kiinteistonmuodostusTyyppi"
                                            :type     :select
                                            :layout   :full-width
                                            :required true
                                            :body     [{:name "lohkominen-tonttijako"}
                                                       {:name "lohkominen-ohjeellinen"}
                                                       {:name "kiinteistojen-yhdistaminen"}
                                                       {:name "kiinteistolajin-muutos"}
                                                       {:name "kiinteiston-tunnusmuutos"}
                                                       {:name "halkominen"}
                                                       {:name "tilan-lohkominen" :schema-include :kt-108-lp}
                                                       {:name "tilusvaihto"}
                                                       {:name "tontinosan-lunastus" :schema-include :kt-108-lp}
                                                       {:name "yht-alueen-osuuksien-siirto"}
                                                       {:name "yleisen-alueen-lohkominen"}]}
                                           kuvaus]})

(def kt-rasitetoimitus {:name       "rasitetoimitus"
                        :type       :group
                        :group-help "help.rasitetoimitus"
                        :approvable true
                        :body       [{:name     "kayttooikeuslaji"
                                      :type     :select
                                      :layout   :full-width
                                      :required true
                                      :body     [{:name "Ajoneuvojen pitäminen"}
                                                 {:name "Ajoneuvojen pitäminen, venevalkama ja laituri"}
                                                 {:name "Autojen pitäminen"}
                                                 {:name "Autojen pitäminen, venevalkama ja -laituri"}
                                                 {:name "Erityinen oikeus, johto tai vastaava"}
                                                 {:name "Erityisesti suojeltavan lajin esiintymispaikka"}
                                                 {:name "Hiekan ottaminen"}
                                                 {:name "Huoltorasite"}
                                                 {:name "Jäteveden johtaminen ja käsittely"}
                                                 {:name "Jätteiden kokoamispaikka"}
                                                 {:name "Johto"}
                                                 {:name "Käyttörasite"}
                                                 {:name "Kaasujohto"}
                                                 {:name "Kaivoksen apualue"}
                                                 {:name "Kaivosalue"}
                                                 {:name "Kaivoslupa-alue"}
                                                 {:name "Kaivoslupa-alueen apualue"}
                                                 {:name "Kalastuksen kielto lohi- ja siikapitoisessa vesistössä"}
                                                 {:name "Kalastuksen kielto padon alapuolella"}
                                                 {:name "Kalastusta varten tarvittava alue"}
                                                 {:name "Kalaväylä"}
                                                 {:name "Kiinteä muinaisjäännös"}
                                                 {:name "Kiinteistöjen yhteinen lämpökeskus"}
                                                 {:name "Kiven ottaminen"}
                                                 {:name "Kulkuyhteys asemakaava-alueella"}
                                                 {:name "Kullanhuuhdonta-alue"}
                                                 {:name "Lämpöjohto"}
                                                 {:name "Laiterasite"}
                                                 {:name "Laituri"}
                                                 {:name "Lastauspaikka"}
                                                 {:name "Lentokentän lähestymisalue"}
                                                 {:name "Lopetetun kaivoksen vaikutusalue"}
                                                 {:name "Lunastuslain mukainen erityinen oikeus"}
                                                 {:name "Luonnonsuojelualue (Ahvenanmaa)"}
                                                 {:name "Luonnonsuojelualue"}
                                                 {:name "Maa-aineksen ottaminen"}
                                                 {:name "Maakaasujohto"}
                                                 {:name "Maantielain mukainen tieoikeus"}
                                                 {:name "Maantien liitännäisalue"}
                                                 {:name "Maantien näkemäalue"}
                                                 {:name "Maantien suoja-alue"}
                                                 {:name "Malminetsintäalue"}
                                                 {:name "Moottorikelkkailureitti"}
                                                 {:name "Oikeus vesivoimaan"}
                                                 {:name "Ojitusrasite"}
                                                 {:name "Padotusalue"}
                                                 {:name "Perustusrasite"}
                                                 {:name "Puhelinjohto"}
                                                 {:name "Puutavaran varastointi"}
                                                 {:name "Radanpitäjällä oikeus laskuojaan"}
                                                 {:name "Radanpitäjällä oikeus tiehen"}
                                                 {:name "Rakennerasite"}
                                                 {:name "Ratalain mukainen rautatieoikeus"}
                                                 {:name "Rautatien liitännäisalue"}
                                                 {:name "Rautatien näkemäalue"}
                                                 {:name "Rautatien suoja-alue"}
                                                 {:name "Sähköjohto"}
                                                 {:name "Sadevesiviemäri"}
                                                 {:name "Saven ottaminen"}
                                                 {:name "Seinärasite"}
                                                 {:name "Sietorasite"}
                                                 {:name "Sopimus luontoarvokaupasta"}
                                                 {:name "Sopimus määräaikaisesta rauhoittamisesta"}
                                                 {:name "Sopimus ympäristötuesta"}
                                                 {:name "Soran ottaminen"}
                                                 {:name "Suojeltu luontotyyppi"}
                                                 {:name "Talousveden johtaminen"}
                                                 {:name "Tienpitäjällä oikeus laskuojaan (laki yleisistä teistä)"}
                                                 {:name "Tienpitäjällä oikeus laskuojaan (maantielaki)"}
                                                 {:name "Tienpitoaineen kuljettaminen"}
                                                 {:name "Tienpitoaineen ottaminen"}
                                                 {:name "Tieoikeus"}
                                                 {:name "Turpeen ottaminen"}
                                                 {:name "Tutka-aseman ympäristö"}
                                                 {:name "Uimapaikka"}
                                                 {:name "Ulko- ja sisäsaariston välinen raja"}
                                                 {:name "Ulkoilureitin levähdyspaikka"}
                                                 {:name "Ulkoilureitti"}
                                                 {:name "Uoma"}
                                                 {:name "Väestösuojelua varten tarvittava rakennelma"}
                                                 {:name "Vähäisten laitteiden sijoittaminen (Maankäyttö- ja rakennuslaki 163 §)"}
                                                 {:name "Valtausalue (kaivoslaki 503/1965)"}
                                                 {:name "Valtion retkeilyalueen lisäalue"}
                                                 {:name "Veden johtaminen maan kuivattamista varten"}
                                                 {:name "Vedenottamo"}
                                                 {:name "Vedenottamon suoja-alue"}
                                                 {:name "Venelaituri"}
                                                 {:name "Venevalkama (kiinteistönmuodostamislaki)"}
                                                 {:name "Venevalkama (yksityistielaki)"}
                                                 {:name "Venevalkama ja -laituri"}
                                                 {:name "Venevalkama ja ajoneuvojen pitäminen"}
                                                 {:name "Venevalkama ja autojen pitäminen"}
                                                 {:name "Vesijohto"}
                                                 {:name "Vesilain mukainen käyttöoikeus"}
                                                 {:name "Vesilain mukainen rakennus, laite tai vastaava"}
                                                 {:name "Viemärijohto"}
                                                 {:name "Voiman- ja tiedonsiirtolinja"}
                                                 {:name "Voimansiirtolinja"}
                                                 {:name "Ydinjätteiden loppusijoituspaikka"}
                                                 {:name "Yhdyskuntateknisten laitteiden sijoittaminen (Maankäyttö- ja rakennuslaki 161 §)"}
                                                 {:name "Yhteisjärjestely (Maankäyttö- ja rakennuslaki 164 §)"}
                                                 {:name "Yhteiskäyttöalue (Maankäyttö- ja rakennuslaki 75 ja 91 §)"}
                                                 {:name "Yhteispiha"}
                                                 {:name "Yhteisrasite"}
                                                 {:name "Yksityinen hauta"}
                                                 {:name "Talousveden ottaminen"}]}
                                     {:name "paattymispvm"
                                      :type :date}]})

;; Previous permits ancd their change permits (aka "paper permits")

(def kuntagml-toimenpide
  "KuntaGML Toimenpide element details for previous permits (and their change permits)."
  {:name        "kuntagml-toimenpide"
   :type        :group
   :i18nkey     "kuntagml-toimenpide"
   :uicomponent :docgenGroup
   :template    "form-grid-docgen-group-template"
   :body        [{:name        "toimenpide"
                  :type        :select
                  :sortBy      :displayname
                  :required    true
                  :i18nkey     "kuntagml-toimenpide.toimenpide"
                  :uicomponent :docgen-select
                  :css         [:dropdown]
                  :body        [{:name "uusi"}
                                {:name "laajennus"}
                                {:name "uudelleenrakentaminen"}
                                {:name "purkaminen"}
                                {:name "muuMuutosTyo"}
                                {:name "kaupunkikuvaToimenpide"}]}
                 {:name      "perusparannuskytkin"
                  :type      :checkbox
                  :inputType :checkbox-wrapper
                  :i18nkey   "kuntagml-toimenpide.perusparannuskytkin"
                  :css       [:docgen-checkbox-wrapper]
                  :show-when {:path   "toimenpide"
                              :values #{"laajennus" "uudelleenrakentaminen" "muuMuutosTyo"}}}
                 {:name      "rakennustietojaEimuutetaKytkin"
                  :type      :checkbox
                  :inputType :checkbox-wrapper
                  :i18nkey   "kuntagml-toimenpide.rakennustietojaEimuutetaKytkin"
                  :css       [:docgen-checkbox-wrapper]
                  :whitelist {:roles [:authority] :otherwise :hidden}
                  :show-when {:path   "toimenpide"
                              :values #{"muuMuutosTyo"}}}
                 {:name        "muutostyolaji"
                  :type        :select
                  :uicomponent :docgen-select
                  :css         [:dropdown]
                  :sortBy      :displayname
                  :required    true
                  :show-when   {:path   "toimenpide"
                                :values #{"muuMuutosTyo" "uudelleenrakentaminen"}}
                  :i18nkey     "muutostyolaji"
                  :body
                  [{:name perustusten-korjaus}
                   ;; The legacy typo can be fixed here since there is no old data.
                   {:name "rakennuksen pääasiallinen käyttötarkoitusmuutos"}
                   {:name muumuutostyo}]}]
   :rows        [{:css [:row--no-bottom-margin]
                  :row ["toimenpide" "muutostyolaji::2" "perusparannuskytkin"]}
                 {:css [:row--tight]
                  :row ["rakennustietojaEimuutetaKytkin::2"]}]})

;;
;; Accordion paths
;;

(def hakija-accordion-paths
  "Data from paths are visible in accordion header"
  [{:type   :selected
    :paths  [["henkilo" "henkilotiedot" "etunimi"]
             ["henkilo" "henkilotiedot" "sukunimi"]
             ["yritys" "yritysnimi"]]
    :format "- %s %s"}])

(def designer-accordion-paths
  "Data from paths are visible in accordion header"
  [{:type   :text
    :paths  [["henkilotiedot" "etunimi"]
             ["henkilotiedot" "sukunimi"]]
    :format "- %s %s"}
   {:type   :text
    :paths  [["kuntaRoolikoodi"]]
    :format "- %s"}
   {:type   :text
    :paths  [["suunnittelutehtavanVaativuusluokka"]]
    :format "(%s)"}
   {:type   :text
    :paths  [["muuSuunnittelijaRooli"]]
    :format "- %s"}])

(def hakijan-asiamies-accordion-paths hakija-accordion-paths)

(def foreman-accordion-paths
  "Data from paths are visible in accordion header"
  [{:type   :text
    :paths  [["kuntaRoolikoodi"]
             ["henkilotiedot" "etunimi"]
             ["henkilotiedot" "sukunimi"]]
    :format "- %s %s %s"}])

(def buildingid-accordion-paths
  [{:type   :text
    :paths  [[national-building-id]]
    :format " - %s"}])

(def ^:ryhti ryhti-buildingid-accordion-paths
  [{:type   :text
    :paths  [["kaytto" "nimi"]
             ["pysyva-rakennustunnus"]]
    :format " - %s - %s"}])

(def jatkoaika-pvm
  [{:name "jatkoaika-paattyy" :type :date}
   {:name "rakennustyo-aloitettu" :type :date}])

;; Ryhti

(defn ^:ryhti table-checkbox
  "This adds a checkbox that is centered in a table cell, it needs to be inside of a docgenTable to work"
  [name & {:keys [i18nkey]}]
  (cond-> {:name          name
           :type          :checkbox
           :inputType     :checkbox-wrapper
           :container-css [:flex :justify-center :items-center]
           :css           [:wrapper--no-border :!mb-0 :!min-h-min]}
    i18nkey (assoc :i18nkey i18nkey)))

(defn ^:ryhti ryhti-kayttotarkoitus
  "These values are a subset of the ryhti
  'Building', 'Structure' and 'AreaToBeBuiltForSpecificActivity' entities"
  [& {:keys [muutoksen-laji]}]
  {:name        "kaytto"
   :type        :group
   :uicomponent :docgenGroup
   :i18nkey     "rakennuskohde.kaytto"
   :template    "form-grid-docgen-group-template"
   :css         top-container-css
   :rows        (->> [["nimi"]
                      {:font-bold.mt-8 "rakennuskohde.kaytto"}
                      ["kohteen-laji::1"
                       ;; One of these three is shown depending on `kohteen-laji`:
                       "rakennusluokka::2" "rakennelman-kayttotarkoitus::1" "erityista-toimintaa-tyyppi::1"]
                      ["rakentaja-tyyppi::2"]
                      ["valiaikainenRakennusKytkin::2"]
                      (when-not (= :new muutoksen-laji)
                        ["perusparannusPaiva"])]
                     (keep identity)
                     (into []))
   :body        (body {:name        "nimi"
                       :type        :string
                       :inputType   :string
                       :ifc-mapping :ifc-kaytto-nimi}
                      {:name     "kohteen-laji"
                       :type     :select
                       :required true
                       :emit     [:lowCarbonAssessmentCriteriaChanged]
                       :body     [{:name    "etvra"
                                   :i18nkey "application.area-to-be-built-for-specific-activity"}
                                  {:name    "rakennelma"
                                   :i18nkey "application.structure"}
                                  {:name    "rakennus"
                                   :i18nkey "application.building"}]}
                      {:name        "rakentaja-tyyppi"
                       :type        :checkbox
                       :inputType   :checkbox-wrapper
                       :ifc-mapping :ifc-kaytto-rakentaja-tyyppi}
                      ;; TODO: LPK-7850 Rakennusluokka select could be improved once the new cljs docgen is implemented
                      {:name        "rakennusluokka"
                       :type        :select
                       :show-when   {:path "kohteen-laji" :values #{"rakennus"}}
                       ;; NOTE: LPK-8167 old schema field had the required flag - see def rakennusluokka
                       :required    true
                       :ifc-mapping :ifc-kaytto-rakennusluokka
                       :body        building-types/rakennuksen-rakennusluokka-schema-body}
                      {:name      "rakennelman-kayttotarkoitus"
                       :type      :select
                       :show-when {:path "kohteen-laji" :values #{"rakennelma"}}
                       ;; NOTE: LPK-8167 old schema field had the required flag - see def kayttotarkoitus
                       :required  true
                       :body      (mapv #(hash-map :i18nkey (str "rakennelman-kayttotarkoitus." %) :name %) rakennelman-kayttotarkoitukset)}
                      {:name      "erityista-toimintaa-tyyppi"
                       :type      :select
                       :show-when {:path "kohteen-laji" :values #{"etvra"}}
                       ;; NOTE: LPK-8167 old schema field had the required flag - see def erityista-toimintaa-varten-rakennettava-alue
                       :required  true
                       :body      (mapv #(assoc % :i18nkey (str "erityista-toimintaa-varten-rakennettava-alue.tyyppi." (:name %))) etvra-tyyppi)}
                      {:name        "valiaikainenRakennusKytkin"
                       :type        :checkbox
                       :inputType   :checkbox-wrapper
                       :ifc-mapping :ifc-kaytto-valiaikainen}
                      {:name      "perusparannusPaiva"
                       :type      :date
                       :show-when {:path "kohteen-laji" :values #{"rakennus"}}
                       :hidden    (= :new muutoksen-laji)})})

(defn ^:ryhti ryhti-sijaintiyksikko
  "The individual required fields are only required if the group itself is"
  [element-name & {:keys [required]}]
  {:name           element-name
   :type           :group
   :i18nkey        (str "sijaintiyksikko." (name element-name) "._group_label")
   :schema-include :ryhti-data-2026
   :css            top-container-css
   :body           [(ryhti-id :administrativeLocationUnitKey)
                    {:name       "kiinteistotunnus"
                     :required   (boolean required)
                     :type       :string
                     :subtype    :kiinteistotunnus
                     :transform  :property-id
                     :auto-fill? true
                     :i18nkey    "rakennuspaikka.kiinteisto.kiinteistotunnus"}]})

(def ^:ryhti ryhti-sijainti
  "This maps to the 'location' field of the ryhti
  'Building', 'Structure' and 'AreaToBeBuiltForSpecificActivity' entities"
  {:name           "sijaintitiedot"
   :ryhti-address? true
   :type           :group
   :uicomponent    :docgenGroup
   :template       "form-grid-docgen-group-template"
   :i18nkey        "rakennuskohde.sijaintitiedot._group_label"
   :schema-include :ryhti-data-2026
   :css            top-container-css
   :rows           [{:font-bold "rakennuskohde.sijaintitiedot._group_label"}
                    ["osoite/katu" "osoite/osoitenumero" "osoite/osoitenumero2"]
                    ["osoite/postinumero" "osoite/postitoimipaikannimi" "sijainti/hallinnollinen-sijainti-yksikko/kiinteistotunnus"]]
   :body           (body
                     ryhti-rakennuskohteen-osoite
                     {:name    "sijainti"
                      :type    :group
                      :i18nkey "sijaintitiedot.sijainti"
                      :body    [(ryhti-id :buildingObjectLocationDataKey)
                                (ryhti-sijaintiyksikko "hallinnollinen-sijainti-yksikko" :required true)]})})

(def ^:ryhti ryhti-mitat
  "These values are a subset of the ryhti
  'Building', 'Structure' and 'AreaToBeBuiltForSpecificActivity' entities"
  {:name                "mitat"
   :ryhti-measurements? true
   :type                :group
   :i18nkey             "rakennuskohde.mitat"
   :uicomponent         :docgenGroup
   :show-when           {:path "/kaytto/kohteen-laji" :values #{"etvra"}}
   :css                 top-container-css
   :body                [{:name     "pintaAla"
                          :type     :string
                          :size     :s
                          :unit     :m2
                          :subtype  :number
                          ;; Internally set reasonable limits (not spec-defined)
                          :min      1
                          :max      9999999
                          ;; NOTE: LPK-8167 old schema field had the required flag - see def erityista-toimintaa-varten-rakennettava-alue
                          :required true}
                         ;; Kerrosluku is deprecated & hidden
                         {:name   "kerrosluku"
                          :type   :string
                          :hidden true}]})

(def ^:ryhti energiatiedot
  "Ryhti 'EnergyData' entity"
  {:name      "energiatiedot"
   :type      :group
   :template  "form-grid-docgen-group-template"
   :i18nkey   "energiatiedot"
   :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus" "rakennelma"}}
   :css       top-container-css
   :rows      [{:font-bold "energiatiedot"}
               ["lammonlahde::4"]
               ["lammitystapa::4"]
               ["jaahdytysenergianlahde::4"]
               ["jaahdytystapa::4"]
               ["sahkoenergianlahde::4"]
               ["energialuokka" "energialuokan-alaindeksi"]
               ["lammitetty-nettoala::2"]
               {:font-bold "energiatiedot.laskennallinen-ostoenergian-kulutus"}
               ["laskennallinen-ostoenergian-kulutus/ostoenergian-laji"
                "laskennallinen-ostoenergian-kulutus/energiamaara-vuodessa"]]
   :body      (body (ryhti-id :energyDataKey)
                    {:name                  "lammonlahde"
                     :type                  :table
                     ;; TODO: pdf-export is now fixed to support using parent i18nkey as prefix?
                     ;; overriding the key here shouldn't be necessary.
                     :i18nkey               "energiatiedot.lammonlahde"
                     :repeating             true
                     :repeating-allow-empty true
                     :repeating-init-empty  true
                     :copybutton            false
                     :validator             :is-primary
                     :ifc-mapping           :ifc-tbl-lammonlahde
                     :body                  [(ryhti-id :heatingEnergySourceKey)
                                             (table-checkbox "is-primary" :i18nkey "is-primary")
                                             {:name    "lammonlahdetyyppi"
                                              :type    :select
                                              :css     [:dropdown]
                                              :i18nkey "lammonlahde"
                                              :body    (conj lammonlahde-kuntagml-options {:name "muu"})}
                                             {:name      "muu-lammonlahde"
                                              :type      :string
                                              ;; Since this is in a table we can not use :other-key, we do it manually instead
                                              :show-when {:path   "lammonlahdetyyppi"
                                                          :values #{"muu"}}
                                              :i18nkey   "lammonlahde.muu-lammonlahde"}]}
                    {:name                  "lammitystapa"
                     :i18nkey               "energiatiedot.lammitystapa"
                     :type                  :table
                     :repeating             true
                     :repeating-allow-empty true
                     :repeating-init-empty  true
                     :copybutton            false
                     :validator             :is-primary
                     :ifc-mapping           :ifc-tbl-lammitystapa
                     :css                   top-container-css
                     :body                  (body (ryhti-id :heatingMethodKey)
                                                  (table-checkbox "is-primary" :i18nkey "is-primary")
                                                  {:name    "lammitystapatyyppi"
                                                   :type    :select
                                                   :css     [:dropdown]
                                                   :i18nkey "lammitystapa"
                                                   :body    lammitystapa-kuntagml-options})}
                    {:name                  "jaahdytysenergianlahde"
                     :type                  :table
                     :repeating             true
                     :repeating-allow-empty true
                     :repeating-init-empty  true
                     :copybutton            false
                     :validator             :is-primary
                     :i18nkey               "energiatiedot.jaahdytysenergianlahde"
                     :css                   top-container-css
                     :body                  (body (ryhti-id :coolingEnergySourceKey)
                                                  (table-checkbox "is-primary" :i18nkey "is-primary")
                                                  {:name    "jaahdytysenergianlahdetyyppi"
                                                   :i18nkey "energiatiedot.jaahdytysenergianlahdetyyppi"
                                                   :type    :select
                                                   :css     [:dropdown :w--25em]
                                                   :body    (mapping->options ryhti-mappings/jaahdytysenergianlahde :i18nkey :suomi.fi.jaahdytysenergianlahde)})}
                    {:name                  "jaahdytystapa"
                     :type                  :table
                     :repeating             true
                     :repeating-allow-empty true
                     :repeating-init-empty  true
                     :copybutton            false
                     :validator             :is-primary
                     :i18nkey               "energiatiedot.jaahdytystapa"
                     :css                   top-container-css
                     :body                  (body (ryhti-id :coolingMethodKey)
                                                  (table-checkbox "is-primary" :i18nkey "is-primary")
                                                  {:name        "jaahdytystapatyyppi"
                                                   :i18nkey     "energiatiedot.jaahdytystapatyyppi"
                                                   :type        :select
                                                   :css         [:dropdown :w--25em]
                                                   :ifc-mapping :ifc-jaahdytystapa-tyyppi
                                                   :body        (mapping->options ryhti-mappings/jaahdytystapa :i18nkey :suomi.fi.jaahdytystapa)})}
                    {:name                  "sahkoenergianlahde"
                     :i18nkey               "energiatiedot.sahkoenergianlahde"
                     :type                  :table
                     :repeating             true
                     :repeating-allow-empty true
                     :repeating-init-empty  true
                     :copybutton            false
                     :validator             :is-primary
                     :css                   top-container-css
                     :body                  (body (ryhti-id :electricalEnergySourceKey)
                                                  (table-checkbox "is-primary" :i18nkey "is-primary")
                                                  {:name    "sahkoenergianlahdetyyppi"
                                                   :i18nkey "energiatiedot.sahkoenergianlahdetyyppi"
                                                   :type    :select
                                                   :css     [:dropdown :w--25em]
                                                   :body    (mapping->options ryhti-mappings/sahkoenergianlahde :i18nkey :suomi.fi.sahkoenergianlahde)}
                                                  {:name      "muu-sahkoenergianlahdetyyppi"
                                                   :type      :string
                                                   ;; Since this is in a table we can not use :other-key, we do it manually instead
                                                   :show-when {:path   "sahkoenergianlahdetyyppi"
                                                               :values #{"muu"}}
                                                   :i18nkey   "energiatiedot.sahkoenergianlahdetyyppi.muu-sahkoenergianlahdetyyppi"})}
                    {:name        "energialuokka"
                     :type        :select
                     :css         [:dropdown]
                     :i18nkey     "energialuokka"
                     :ifc-mapping :ifc-energia-luokka
                     :body        energialuokka-kuntagml-options}
                    {:name        "energialuokan-alaindeksi"
                     :type        :select
                     :css         [:dropdown :w--25em]
                     :i18nkey     "energiatiedot.energiatehokkuusluokka-alaindeksi"
                     :ifc-mapping :ifc-energia-luokka-alaindeksi
                     :body        (mapping->options ryhti-mappings/energialuokka-alaindeksi :i18nkey :suomi.fi.energialuokka-alaindeksi)}
                    {:name      "lammitetty-nettoala"
                     :type      :string
                     :size      :s
                     :unit      :m2
                     :subtype   :number
                     :min       0
                     :required  true
                     :ifc-mapping :ifc-agg-lammitetty-nettoala
                     ;; Show when any value is selected for rakennusluokka-vahahiilisyys
                     :show-when {:path   "/rakennusluokka-vahahiilisyys/rakennusluokka-vahahiilisyys"
                                 :values (->> ryhti-mappings/rakennuksenkayttotarkoitusvahahiilisyydenarvioinnissa
                                              (map (fn [[k _v]] (name k)))
                                              set)}}
                    {:name "laskennallinen-ostoenergian-kulutus"
                     :type :group
                     :body (body (ryhti-id :calculatoryDeliveredEnergyUsageKey)
                                 {:name    "ostoenergian-laji"
                                  :type    :select
                                  :i18nkey "energiatiedot.laskennallinen-ostoenergian-kulutus.ostoenergian-laji"
                                  :css     [:dropdown :w--25em]
                                  :body    (mapping->options ryhti-mappings/ostoenergian-laji :i18nkey :suomi.fi.ostoenergian-laji)}
                                 {:name    "energiamaara-vuodessa"
                                  :type    :string
                                  :i18nkey "energiatiedot.laskennallinen-ostoenergian-kulutus.energiamaara-vuodessa"
                                  :size    :s
                                  :unit    :kwh
                                  :subtype :decimal})})})

(def ^:ryhti ryhti-rakennuksen-omistajat
  ;; Unfortunately we can't use i18nkey here, as the plain `:group`
  ;; component doesn't cascade the key to the children.
  {:name                            "rakennuksenOmistajat"
   :type                            :group
   :repeating                       true
   :approvable                      true
   :post-verdict-editable-disabled? true
   :schema-include                  :ryhti-data-2026
   :css                             top-container-css
   :body                            (body (ryhti-id :buildingObjectOwnerKey)
                                          building-parties
                                          omistajalajit)})

(def ^:ryhti kayttotiedot
  "Ryhti 'UsageData' entity"
  {:name      "kayttotiedot"
   :type      :group
   :template  "basic-docgen-group-template"
   :i18nkey   "kayttotiedot"
   :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
   :css       top-container-css
   :body      (body {:name        "kayttoika"
                     :type        :string
                     :size        :s
                     :unit        :y
                     :subtype     :number
                     :min         0
                     :ifc-mapping :ifc-kayttotiedot-kayttoika}
                    {:name        "kayttajamaara"
                     :type        :string
                     :size        :s
                     :unit        :hengelle
                     :subtype     :number
                     :min         0
                     :ifc-mapping :ifc-kayttotiedot-kayttajamaara}
                    {:name                  "kayttotarkoitus"
                     :type                  :table
                     :repeating             true
                     :repeating-allow-empty true
                     :repeating-init-empty  true
                     :validator             :is-primary
                     :css                   top-container-css
                     :body                  [(ryhti-id :purposeKey)
                                             (table-checkbox "is-primary" :i18nkey "is-primary")
                                             {:name     "tyyppi"
                                              :i18nkey  "kayttotiedot.kayttotarkoitus.tyyppi"
                                              :type     :select
                                              :css      [:dropdown :w--35em]
                                              :required true
                                              ;; TODO This still uses the old kayttotarkoitus codes
                                              :body     (mapv (fn [k]
                                                                (assoc k :i18nkey (str "kayttotarkoitus." (:name k))))
                                                              rakennuksen-kayttotarkoitus)}]})})

(defn ^:ryhti ulkokuorentiedot
  "Ryhti 'ExteriorData' entity"
  [opts]
  (merge (make-mitat opts)
         {:name      "ulkokuorentiedot"
          :type      :group
          :template  "form-grid-docgen-group-template"
          :i18nkey   "ulkokuorentiedot"
          :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus" "rakennelma"}}
          :css       top-container-css
          :rows      [{:font-bold "ulkokuorentiedot"}
                      ["tilavuus" "kerrosala" "kokonaisala" "kerrosluku"]
                      ["korkeus" "suhde-maan-pintaan" "kellarinpinta-ala"]
                      {:font-bold "ulkokuorentiedot.rakennusoikeus"}
                      ["rakennusoikeusValinta" "rakennusoikeudellinenKerrosala"
                       "rakennusoikeusKuutiometreina" "harjanKorkeusrakennusalalla"]]}))

(def ^:ryhti tekniset-tiedot
  "Ryhti 'BuildingServicesEngineeringData' and `Equipment` entities"
  {:name      "tekniset-tiedot"
   :type      :group
   :template  "form-grid-docgen-group-template"
   :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus" "rakennelma"}}
   :css       top-container-css
   :rows      [{:font-bold "rakennuskohteen-osa.tekniset-tiedot"}
               ["jatevesienkasittely" "talousvesityyppi"]
               ["verkostoliittymat::4"]
               ["ilmanvaihtotavan-laji::4"]
               ["varuste::4"]]
   :body      (body
                ;; This ryhti-id does not (yet?) exist in ryhti, it is added so that the parent gets created in the db
                ;; Otherwise the frontend will not allow the nested tables to create new children,
                ;; see `lupapalvelu.ui.docgen.feature.documents/add-group-child`
                (ryhti-id :buildingServicesEngineeringDataKey)
                {:name        "jatevesienkasittely"
                 :i18nkey     "tekniset-tiedot.jatevesienkasittely"
                 :type        :select
                 :css         [:dropdown]
                 :ifc-mapping :ifc-tekniset-jatevesi
                 :body        (mapping->options ryhti-mappings/jatevesienkasittely :i18nkey :suomi.fi.jatevesienkasittely)}
                {:name        "talousvesityyppi"
                 :i18nkey     "tekniset-tiedot.talousvesityyppi"
                 :type        :select
                 :css         [:dropdown]
                 :ifc-mapping :ifc-tekniset-talousvesi
                 :body        (mapping->options ryhti-mappings/talousvesityyppi :i18nkey :suomi.fi.talousvesityyppi)}
                {:name                  "verkostoliittymat"
                 :type                  :table
                 :repeating             true
                 :repeating-allow-empty true
                 :repeating-init-empty  true
                 :copybutton            false
                 :ifc-mapping           :ifc-tbl-verkostoliittymat
                 :i18nkey               "tekniset-tiedot.verkostoliittymat"
                 :group-help            "tekniset-tiedot.verkostoliittymat.help"
                 :css                   top-container-css
                 :body                  [(ryhti-id :networkConnectionKey)
                                         (table-checkbox "liitetty" {:i18nkey "tekniset-tiedot.verkostoliittymat.liitetty"})
                                         {:name    "verkostoliittymatyyppi"
                                          :type    :select
                                          :css     [:dropdown]
                                          :i18nkey "tekniset-tiedot.verkostoliittymat.verkostoliittymatyyppi"
                                          :body    (mapping->options ryhti-mappings/verkostoliittymat :i18nkey :suomi.fi.verkostoliittymatyyppi)}]}
                {:name                  "ilmanvaihtotavan-laji"
                 :type                  :table
                 :i18nkey               "tekniset-tiedot.ilmanvaihtotavan-laji"
                 :repeating             true
                 :repeating-allow-empty true
                 :repeating-init-empty  true
                 :copybutton            false
                 :validator             :is-primary
                 :css                   top-container-css
                 :body                  [(ryhti-id :ventilationMethodKey)
                                         (table-checkbox "is-primary" {:i18nkey "is-primary"})
                                         {:name        "ilmanvaihtotyyppi"
                                          :i18nkey     "tekniset-tiedot.ilmanvaihtotavan-laji.ilmanvaihtotyyppi"
                                          :type        :select
                                          :css         [:dropdown :w--25em]
                                          :ifc-mapping :ifc-ilmanvaihto-tyyppi
                                          :body        (mapping->options ryhti-mappings/ilmanvaihtotavan-laji :i18nkey :suomi.fi.ilmanvaihtotavan-laji)}]}
                {:name                  "varuste"
                 :i18nkey               "tekniset-tiedot.varuste"
                 :type                  :table
                 :repeating             true
                 :repeating-allow-empty true
                 :repeating-init-empty  true
                 :copybutton            false
                 :ifc-mapping           :ifc-tbl-varuste
                 :css                   top-container-css
                 :body                  (body (ryhti-id :equipmentKey)
                                              {:name    "varustetyyppi"
                                               :i18nkey "tekniset-tiedot.varuste.varustetyyppi"
                                               :type    :select
                                               :css     [:dropdown]
                                               :body    (mapping->options ryhti-mappings/rakennusvaruste :i18nkey :suomi.fi.rakennusvaruste)}
                                              {:name     "maara"
                                               :type     :string
                                               :required true
                                               :default  "1"
                                               :i18nkey  "tekniset-tiedot.varuste.maara"
                                               :subtype  :number
                                               :min      0
                                               :size     :s})})})

(def ryhti-paloluokat
  "The ordering is important."
  ["P0" "P1" "P2" "P3" "P1/P2" "P1/P3" "P2/P3" "P1/P2/P3"])

(def ^:ryhti materiaalitiedot
  "Ryhti 'MaterialData' entity"
  {:name      "materiaalitiedot"
   :i18nkey   "materiaalitiedot"
   :type      :group
   :template  "form-grid-docgen-group-template"
   :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus" "rakennelma"}}
   :css       top-container-css
   :rows      [{:font-bold "materiaalitiedot"}
               ["laajarunkoinen-rakennus"]
               ["rakentamistapa" "paloluokka"]
               ["kantavaRakennusaine::4"]
               ["julkisivu::4"]]
   :body      (body
                {:name      "laajarunkoinen-rakennus"
                 :type      :checkbox
                 :inputType :checkbox-wrapper
                 :default   false
                 :i18nkey   "materiaalitiedot.laajarunkoinen-rakennus"}
                {:name        "rakentamistapa"
                 :type        :select
                 :sortBy      :displayname
                 :required    true
                 :i18nkey     "rakentamistapa"
                 :css         [:dropdown]
                 :ifc-mapping :ifc-materiaalit-rakentamistapa
                 :body        (mapping->options ryhti-mappings/rakentamistapa :i18nkey :suomi.fi.rakentamistapa)}

                ;; This implementation of Paloluokka is a compromise between Ryhti and KuntaGML.
                ;; In Ryhti it should be possible to pick any combination of P0/P1/P2/P3, but this is not supported by KuntaGML.
                ;; And in KuntaGML there are options like "palonkestävä", "paloapidättävä", "paloahidastava" etc. which have been omitted.
                {:name        "paloluokka"
                 :i18nkey     "paloluokka"
                 :type        :select
                 :css         [:dropdown]
                 :ifc-mapping :ifc-materiaalit-paloluokka
                 :body        (mapv (partial hash-map :name) ryhti-paloluokat)}

                {:name                  "kantavaRakennusaine"
                 :type                  :table
                 :i18nkey               "materiaalitiedot.kantavaRakennusaine"
                 :repeating             true
                 :repeating-allow-empty true
                 :repeating-init-empty  true
                 :copybutton            false
                 :validator             :is-primary
                 :ifc-mapping           :ifc-tbl-kantavarakennusaine
                 :css                   top-container-css
                 :body                  [(ryhti-id :buildingMaterialOfLoadBearingStructuresKey)
                                         (table-checkbox "is-primary" {:i18nkey "is-primary"})
                                         {:name     "rakennusaine"
                                          :type     :select
                                          :i18nkey  "materiaalitiedot.kantavaRakennusaine.rakennusaine"
                                          :css      [:dropdown]
                                          :required true
                                          :body     (mapping->options ryhti-mappings/kantava-rakennusaine :i18nkey :suomi.fi.kantava-rakennusaine)}
                                         {:name      "muuRakennusaine"
                                          :type      :string
                                          ;; Since this is in a table we can not use :other-key, we do it manually instead
                                          :show-when {:path   "rakennusaine"
                                                      :values #{"other"}}
                                          :i18nkey   "suomi.fi.kantava-rakennusaine.other"}]}
                {:name                  "julkisivu"
                 :type                  :table
                 :i18nkey               "materiaalitiedot.julkisivu"
                 :repeating             true
                 :repeating-allow-empty true
                 :repeating-init-empty  true
                 :copybutton            false
                 :validator             :is-primary
                 :ifc-mapping           :ifc-tbl-julkisivu
                 :css                   top-container-css
                 :body                  [(ryhti-id :facadeMaterialKey)
                                         (table-checkbox "is-primary" {:i18nkey "is-primary"})
                                         {:name     "julkisivumateriaali"
                                          :type     :select
                                          :i18nkey  "materiaalitiedot.julkisivu.julkisivumateriaali"
                                          :css      [:dropdown]
                                          :required true
                                          :body     (mapping->options ryhti-mappings/julkisivu :i18nkey :suomi.fi.julkisivu)}
                                         {:name      "muuMateriaali"
                                          :type      :string
                                          ;; Since this is in a table we can not use :other-key, we do it manually instead
                                          :show-when {:path   "julkisivumateriaali"
                                                      :values #{"other"}}
                                          :i18nkey   "suomi.fi.julkisivu.other"}]})})

(def ^:ryhti sisatilat
  "Ryhti 'InteriorData' entity"
  {:name      "sisatilat"
   :type      :group
   :template  "form-grid-docgen-group-template"
   :i18nkey   "sisatilat"
   :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
   :css       top-container-css
   :rows      [{:font-bold "sisatilat"}
               ["huoneistoala" "ullakkoala" "kellariala"]]
   :body      (body {:name    "huoneistoala"
                     :type    :string
                     :i18nkey "sisatilat.huoneistoala"
                     :size    :s
                     :unit    :m2
                     :subtype :number
                     :min     0
                     :max     9999999
                     :ifc-mapping :ifc-agg-huoneistoala}
                    {:name    "ullakkoala"
                     :type    :string
                     :i18nkey "sisatilat.ullakkoala"
                     :size    :s
                     :unit    :m2
                     :subtype :number
                     :min     0
                     :max     9999999
                     :ifc-mapping :ifc-agg-ullakkoala}
                    {:name    "kellariala"
                     :type    :string
                     :i18nkey "sisatilat.kellariala"
                     :size    :s
                     :unit    :m2
                     :subtype :number
                     :min     0
                     :max     9999999
                     :ifc-mapping :ifc-agg-kellariala})})

(def ^:ryhti hissit
  "Ryhti 'Elevator' entity"
  (let [container-css [:flex :flex-col :mb-1]]
    {:name                  "hissit"
     :i18nkey               "hissit"
     :type                  :table
     :flatten-for-pdf       true
     :columnCss             {"hissin-tiedot" [:align-baseline :border-0 :border-r :border-solid :border-ui-gray-221]
                             "kapasiteetti"  [:align-baseline :border-0 :border-r :border-solid :border-ui-gray-221]
                             "mitat"         [:align-baseline :border-0 :border-r :border-solid :border-ui-gray-221]}
     :repeating             true
     :show-when             {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
     :repeating-allow-empty true
     :repeating-init-empty  true
     :css                   top-container-css
     :body                  (body (ryhti-id :elevatorKey)
                                  {:name     "hissin-tiedot"
                                   :type     :group
                                   :template "simple-docgen-group-template"
                                   :i18nkey  "hissit.hissin-tiedot"
                                   :body     [{:name          "nimi"
                                               :i18nkey       "hissit.nimi"
                                               :container-css container-css
                                               :type          :string
                                               :ifc-mapping   :ifc-hissi-nimi}
                                              {:name          "hissin-laji"
                                               :type          :select
                                               :i18nkey       "hissit.hissin-laji"
                                               :container-css container-css
                                               :css           [:dropdown]
                                               :required      true
                                               :ifc-mapping   :ifc-hissi-laji
                                               :body          (mapping->options ryhti-mappings/hissin-laji :i18nkey :suomi.fi.hissin-laji)}
                                              {:name          "elinkaaren-vaihe"
                                               :type          :select
                                               :i18nkey       "hissit.elinkaaren-vaihe"
                                               :container-css container-css
                                               :css           [:dropdown]
                                               :required      true
                                               :body          (mapping->options ryhti-mappings/elinkaaren-vaihe :i18nkey :suomi.fi.elinkaaren-vaihe)}
                                              {:name          "palomieshissi"
                                               :type          :select
                                               :i18nkey       "hissit.palomieshissi"
                                               :container-css container-css
                                               :css           [:dropdown]
                                               :ifc-mapping   :ifc-hissi-palomieshissi
                                               :body          [{:name    "yes"
                                                                :i18nkey "yes"}
                                                               {:name    "no"
                                                                :i18nkey "no"}]}]}
                                  {:name     "kapasiteetti"
                                   :type     :group
                                   :i18nkey  "hissit.kapasiteetti"
                                   :template "simple-docgen-group-template"
                                   :body     [{:name          "henkilomaara"
                                               :type          :string
                                               :subtype       :number
                                               :i18nkey       "hissit.henkilomaara"
                                               :container-css container-css
                                               :min           0
                                               :required      true
                                               :ifc-mapping   :ifc-hissi-henkilomaara}
                                              {:name          "kantavuus"
                                               :type          :string
                                               :subtype       :number
                                               :i18nkey       "hissit.kantavuus"
                                               :container-css container-css
                                               :min           0
                                               :required      true
                                               :ifc-mapping   :ifc-hissi-kantavuus}]}
                                  {:name     "mitat"
                                   :type     :group
                                   :i18nkey  "hissit.mitat"
                                   :template "simple-docgen-group-template"
                                   :body     [{:name          "oviaukon-korkeus"
                                               :type          :string
                                               :subtype       :number
                                               :i18nkey       "hissit.oviaukon-korkeus"
                                               :container-css container-css
                                               :min           0
                                               :required      true}
                                              {:name          "oviaukon-leveys"
                                               :type          :string
                                               :subtype       :number
                                               :i18nkey       "hissit.oviaukon-leveys"
                                               :container-css container-css
                                               :min           0
                                               :required      true
                                               :ifc-mapping   :ifc-hissi-oviaukon-leveys}
                                              {:name          "sisaleveys"
                                               :type          :string
                                               :subtype       :number
                                               :i18nkey       "hissit.sisaleveys"
                                               :container-css container-css
                                               :min           0
                                               :required      true
                                               :ifc-mapping   :ifc-hissi-sisaleveys}
                                              {:name          "sisapituus"
                                               :type          :string
                                               :subtype       :number
                                               :i18nkey       "hissit.sisapituus"
                                               :container-css container-css
                                               :min           0
                                               :required      true
                                               :ifc-mapping   :ifc-hissi-sisapituus}]})}))

(def ^:ryhti ryhti-autopaikat
  ;; Does NOT correspond to any Ryhti entity
  {:name        "autopaikat"
   :type        :group
   :uicomponent :docgenGroup
   :template    "form-grid-docgen-group-template"
   :i18nkey     "autopaikat"
   :css         top-container-css
   :rows        [{:font-bold "autopaikat"}
                 ["kiinteiston" "kiinteiston-ulkopuoliset" "rakennetut"]]
   :body        (body {:name    "kiinteiston"
                       :type    :string
                       :subtype :number
                       :unit    :kpl
                       :size    :s
                       :min     0}
                      {:name    "kiinteiston-ulkopuoliset"
                       :type    :string
                       :subtype :number
                       :unit    :kpl
                       :size    :s
                       :min     0}
                      {:name    "rakennetut"
                       :type    :string
                       :subtype :number
                       :unit    :kpl
                       :size    :s
                       :min     0})})

(def ^:ryhti ryhti-latauspiste
  "Ryhti `ChargingPoint` entity"
  {:name        "latauspiste"
   :type        :group
   :uicomponent :docgenGroup
   :template    "form-grid-docgen-group-template"
   :i18nkey     "latauspiste"
   :css         top-container-css
   :rows        [{:font-bold "latauspiste"}
                 ["esikaapelointi" "putkitus" "muutJohtoreitit" "latauspisteidenMaara"]
                 {:font-bold "latauspiste.polkupyora"}
                 ["polkupyorapaikkojenMaara"]]
   :body        (body (ryhti-id :chargingPointKey)
                      {:name        "esikaapelointi"
                       :inputType   :checkbox-wrapper
                       :type        :checkbox
                       :ifc-mapping :ifc-latauspiste-esikaapelointi}
                      {:name        "putkitus"
                       :inputType   :checkbox-wrapper
                       :type        :checkbox
                       :ifc-mapping :ifc-latauspiste-putkitus}
                      {:name        "muutJohtoreitit"
                       :inputType   :checkbox-wrapper
                       :type        :checkbox
                       :ifc-mapping :ifc-latauspiste-muut-johtoreitit}
                      {:name        "latauspisteidenMaara"
                       :type        :string
                       :subtype     :number
                       :unit        :kpl
                       :size        :s
                       :min         0
                       :ifc-mapping :ifc-latauspiste-maara}
                      {:name        "polkupyorapaikkojenMaara"
                       :type        :string
                       :subtype     :number
                       :unit        :kpl
                       :size        :s
                       :min         0
                       :ifc-mapping :ifc-latauspiste-polkupyorat})})
(defn ^:ryhti ryhti-sisaverkko
  "Ryhti `InternalNetwork` entity"
  [& {:keys [muutoksen-laji]}]
  {:name      "sisaverkko"
   :type      :group
   :template  "basic-docgen-group-template"
   :i18nkey   "sisaverkko"
   :css       top-container-css
   :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
   :body      (body (ryhti-id :internalNetworkKey)
                    {:name        "olemassaOlevaValokuituverkko"
                     :inputType   :checkbox-wrapper
                     :type        :checkbox
                     :hidden      (= :new muutoksen-laji)
                     :ifc-mapping :ifc-sisaverkko-valokuitu}
                    {:name        "yleiskaapeloinninParikaapelointi"
                     :inputType   :checkbox-wrapper
                     :type        :checkbox
                     :ifc-mapping :ifc-sisaverkko-parikaapelointi}
                    {:name        "yleiskaapeloinninOptinenKaapelointi"
                     :inputType   :checkbox-wrapper
                     :type        :checkbox
                     :ifc-mapping :ifc-sisaverkko-optinen}
                    {:name        "antenniverkko"
                     :inputType   :checkbox-wrapper
                     :type        :checkbox
                     :ifc-mapping :ifc-sisaverkko-antenni})})
(def ^:ryhti sisaankaynti
  "Ryhti 'Entrance' entity"
  (let [container-css [:flex :flex-col :mb-1]
        column-css    [:border-0 :border-r :border-solid :border-ui-gray-221]]
    {:name                  "sisaankaynti"
     :i18nkey               "sisaankaynti"
     :type                  :table
     :columnCss             {"is-primary"    column-css
                             "nimi"          column-css
                             "tila"          column-css
                             "esteeton"      column-css
                             "sijaintipiste" column-css}
     :repeating             true
     :repeating-allow-empty true
     :repeating-init-empty  true
     :validator             :is-primary
     :show-when             {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
     :body                  (body (ryhti-id :entranceKey)
                                  (table-checkbox "is-primary" :i18nkey "is-primary")
                                  {:name          "nimi"
                                   :type          :string
                                   :i18nkey       "sisaankaynti.nimi"
                                   :container-css container-css
                                   :ifc-mapping   :ifc-sisaankaynti-nimi}
                                  {:name     "tila"
                                   :type     :group
                                   :template "simple-docgen-group-template"
                                   :i18nkey  "sisaankaynti.tila"
                                   :body     [{:name          "laji"
                                               :type          :select
                                               :i18nkey       "sisaankaynti.laji"
                                               :container-css container-css
                                               :css           [:dropdown :w--25em]
                                               :required      true
                                               :ifc-mapping   :ifc-sisaankaynti-tila-laji
                                               :body          (mapping->options ryhti-mappings/sisaankaynnin-tyyppi :i18nkey :suomi.fi.sisaankaynnin-tyyppi)}
                                              {:name          "elinkaaren-vaihe"
                                               :type          :select
                                               :i18nkey       "sisaankaynti.elinkaaren-vaihe"
                                               :container-css container-css
                                               :css           [:dropdown :w--25em]
                                               :required      true
                                               :body          (mapping->options ryhti-mappings/elinkaaren-vaihe :i18nkey :suomi.fi.elinkaaren-vaihe)}]}
                                  (assoc (table-checkbox "esteeton" :i18nkey "sisaankaynti.esteeton")
                                         :ifc-mapping :ifc-sisaankaynti-esteeton)
                                  (assoc (table-checkbox "hyokkausreitti" :i18nkey "sisaankaynti.hyokkaysreitti")
                                         :ifc-mapping :ifc-sisaankaynti-hyokkausreitti)
                                  {:name     "sijaintipiste" ;; TODO map picker LPK-8020
                                   :type     :group
                                   :template "simple-docgen-group-template"
                                   :i18nkey  "sijaintitiedot.sijainti.sijaintipiste"
                                   :body     [{:name          "pohjoiskoordinaatti"
                                               :i18nkey       "sijaintitiedot.sijainti.sijaintipiste.pohjoiskoordinaatti"
                                               :container-css container-css
                                               :type          :string}
                                              {:name          "itakoordinaatti"
                                               :i18nkey       "sijaintitiedot.sijainti.sijaintipiste.itakoordinaatti"
                                               :container-css container-css
                                               :type          :string}]})}))

(def ^:ryhti vaestonsuoja
  "Ryhti 'CivilDefenceShelter' entity"
  {:name                  "vaestonsuoja"
   :i18nkey               "vaestonsuoja"
   :type                  :table
   :repeating             true
   :show-when             {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
   :repeating-allow-empty true
   :repeating-init-empty  true
   :css                   top-container-css
   :body                  (body (ryhti-id :civilDefenceShelterKey)
                                {:name        "nimi"
                                 :type        :string
                                 :i18nkey     "vaestonsuoja.nimi"
                                 :ifc-mapping :ifc-vss-nimi}
                                {:name        "luokka"
                                 :type        :select
                                 :i18nkey     "vaestonsuoja.luokka"
                                 :ifc-mapping :ifc-vss-luokka
                                 :body        (mapping->options ryhti-mappings/vaestonsuoja-luokka-grouped :i18nkey :suomi.fi.vaestonsuoja-luokka)}
                                {:name        "kapasiteetti"
                                 :type        :string
                                 :subtype     :number
                                 :i18nkey     "vaestonsuoja.kapasiteetti"
                                 :css         ["w-4/5"]
                                 :min         0
                                 :ifc-mapping :ifc-vss-kapasiteetti}
                                {:name        "suoja-ala"
                                 :type        :string
                                 :subtype     :number
                                 :i18nkey     "vaestonsuoja.suoja-ala"
                                 :css         ["w-4/5"]
                                 :min         0
                                 :ifc-mapping :ifc-vss-ala}
                                (assoc (table-checkbox "yhteinen" :i18nkey "vaestonsuoja.yhteinen")
                                       :ifc-mapping :ifc-vss-yhteinen))})

(defn ^:ryhti huoneiston-varuste [{:keys [name i18nkey ifc-mapping]}]
  (cond-> {:name    name
           :i18nkey i18nkey
           :type    :string
           :subtype :number
           :label   false
           :size    :s
           :min     0}
    ifc-mapping (assoc :ifc-mapping ifc-mapping)))

(defn ^:ryhti huoneiston-varuste-tunniste
  [{:keys [name]}]
  {:name             (str "varuste-" name)
   :type             :group
   :exclude-from-pdf true
   :whitelist        {:roles     [:none]
                      :otherwise :hidden}
   :body             (body (ryhti-id :apartmentEquipmentKey))})

(def ^:ryhti huoneisto-row-ryhti
  "One row in the ryhti apartment table.
  The 'huoneiston-varuste' is automatically generated from the ryhti-mappings here, but the matching
  columns need to be manually added in the cljs side `lupapalvelu.ui.docgen.field.huoneistot.columns`"
  (vec (concat
         [(ryhti-id :apartmentKey)
          {:name        "huoneistoTyyppi"
           :type        :select
           :sortBy      :displayname
           :size        :s
           :label       false
           :i18nkey     "huoneistot.huoneistoTyyppi"
           :css         [:dropdown]
           :required    true
           :ifc-mapping :ifc-huoneisto-tyyppi
           :body        (mapping->options ryhti-mappings/huoneistotyyppi :i18nkey :suomi.fi.huoneistotyyppi)}
          {:name        "porras"
           :type        :string
           :subtype     :letter
           :case        :upper
           :max-len     1
           :size        :t
           :label       false
           :i18nkey     "huoneistot.porras"
           :ifc-mapping :ifc-huoneisto-porras
           :transform   :upper-case}
          {:name        "huoneistonumero"
           :type        :string
           :subtype     :number
           :min         0
           :min-len     1
           :max-len     3
           :size        :s
           :required    true
           :label       false
           :i18nkey     "huoneistot.huoneistonumero"
           :ifc-mapping :ifc-huoneisto-numero}
          {:name        "jakokirjain"
           :type        :string
           :subtype     :letter
           :case        :lower
           :max-len     1
           :size        :t
           :label       false
           :i18nkey     "huoneistot.jakokirjain"
           :ifc-mapping :ifc-huoneisto-jakokirjain
           :transform   :lower-case}
          {:name        "sijaintikerros"
           :type        :string
           :subtype     :number
           :i18nkey     "huoneistot.sijaintikerros"
           :ifc-mapping :ifc-huoneisto-sijaintikerros}
          {:name        "sijaintikerroksen-tarkenne"
           :type        :select
           :i18nkey     "huoneistot.sijaintikerroksen-tarkenne"
           :css         [:dropdown]
           :ifc-mapping :ifc-huoneisto-sijaintikerros-tarkenne
           :body        (mapping->options ryhti-mappings/sijaintikerroksen-tarkenne :i18nkey :suomi.fi.sijaintikerroksen-tarkenne)}
          {:name        "huoneluku"
           :type        :string
           :subtype     :number
           :min         1
           :max         99
           :required    true
           :size        :t
           :label       false
           :i18nkey     "huoneistot.huoneluku"
           :ifc-mapping :ifc-huoneisto-huoneluku}
          {:name        "keittionTyyppi"
           :type        :select
           :sortBy      :displayname
           :size        :s
           :label       false
           :i18nkey     "huoneistot.keittionTyyppi"
           :ifc-mapping :ifc-huoneisto-keittio
           :body        (mapping->options ryhti-mappings/keittiotyyppi :i18nkey :suomi.fi.keittiotyyppi)}
          {:name        "huoneistoala"
           :type        :string
           :subtype     :decimal
           :size        :s
           :min         1
           :max         9999999
           :required    true
           :label       false
           :i18nkey     "huoneistot.huoneistoala"
           :ifc-mapping :ifc-huoneisto-ala}]
         (let [varusteet     (mapping->options ryhti-mappings/huoneiston-varuste :i18nkey :suomi.fi.huoneiston-varuste)
               ifc-varusteet {:wc          :ifc-huoneisto-wc
                              :suihku      :ifc-huoneisto-suihku
                              :sauna       :ifc-huoneisto-sauna
                              :parveke     :ifc-huoneisto-parveke
                              :lammin-vesi :ifc-huoneisto-lammin-vesi}]
           (->> (concat (map #(huoneiston-varuste
                                (if-let [k (get ifc-varusteet (keyword (:name %)))]
                                  (assoc % :ifc-mapping k)
                                  %))
                             varusteet)
                        (map huoneiston-varuste-tunniste varusteet))
                (into [])))
         [{:name     "pysyvaHuoneistotunnus"
           :type     :string
           :size     :s
           :label    false
           :i18nkey  "huoneistot.pysyvaHuoneistotunnus"
           :readonly true}])))

(def ^:ryhti huoneiston-muutoksen-laji
  {:name        "muutostapa"
   :type        :select
   :sortBy      :displayname
   :size        :s
   :label       false
   :ifc-mapping :ifc-huoneisto-muutostapa
   :i18nkey "huoneistot.muutostapa"
   :css     [:dropdown]
   :body    (mapping->options ryhti-mappings/huoneiston-muutoksen-laji :i18nkey :suomi.fi.huoneiston-muutoksen-laji)})

(def ^:ryhti huoneiston-muutoksen-laji-hidden
  (assoc huoneiston-muutoksen-laji
         :default "lisäys"
         :hidden true))

(def ^:ryhti kokoontumistilat
  "Ryhti 'AssemblyFacility' entity"
  {:name                  "kokoontumistilat"
   :type                  :table
   :i18nkey               "kokoontumistilat"
   :repeating             true
   :repeating-allow-empty true
   :css                   top-container-css
   :body                  [(ryhti-id :assemblyFacilityKey)
                           {:name    "nimi"
                            :type    :string
                            :size    :s
                            :i18nkey "kokoontumistilat.nimi"}
                           {:name     "henkilomaara"
                            :type     :string
                            :subtype  :number
                            :required true
                            :min      0
                            :max      9999999
                            :size     :s
                            :i18nkey  "kokoontumistilat.henkilomaara"}]})

(defn ^:ryhti ryhti-huoneisto
  "Ryhti 'Apartment' entity"
  [& {muutoksen-laji :muutoksen-laji}]
  {:pre [(#{:new :change} muutoksen-laji)]}
  {:name                     "huoneistot"
   :i18nkey                  "huoneistot"
   :type                     :table
   :uicomponent              :docgenHuoneistot
   :validator                :huoneistot
   :group-help               "huoneistot.groupHelpText"
   :repeating                true
   :repeating-allow-empty    true
   :repeating-init-empty     true
   :approvable               true
   :copybutton               true
   :allow-highlight-changes? (= :change muutoksen-laji)
   :allow-premise-upload?    (= :new muutoksen-laji)
   :show-when                {:path "/kaytto/kohteen-laji" :values #{"rakennus"}}
   :css                      top-container-css
   :body                     (conj huoneisto-row-ryhti (if (= muutoksen-laji :new)
                                                         huoneiston-muutoksen-laji-hidden
                                                         huoneiston-muutoksen-laji))})

(defn ^:ryhti ryhti-rakennuskohteen-osa
  "Ryhti 'BuildingSection' and 'StructureSection' entities"
  [opts]
  {:name        "rakennuskohteen-osa"
   :type        :group
   :uicomponent :docgenGroup
   :template    "basic-docgen-group-template"
   :i18nkey     "rakennuskohteen-osa"
   :css         top-container-css
   :body        [(ryhti-id :buildingSectionKey :structureSectionKey)
                 (ulkokuorentiedot opts)
                 materiaalitiedot
                 kayttotiedot
                 sisaankaynti
                 sisatilat
                 (ryhti-huoneisto opts)
                 kokoontumistilat
                 energiatiedot
                 tekniset-tiedot
                 hissit
                 vaestonsuoja
                 (ryhti-sisaverkko opts)]})

(defn ^:ryhti ryhti-pysyva-tunnus [tunnus]
  {:name    (name tunnus)
   :type    :string
   :i18nkey (str "rakennuskohde." (name tunnus))
   :hidden  true})

(defn ^:ryhti ryhti-rakennuskohde
  "Ryhti 'Building', 'Structure' and 'AreaToBeBuiltForSpecificActivity' entities"
  [opts]
  [(ryhti-id :buildingKey :structureKey :areaToBeBuiltForSpecificActivitiesKey)
   (ryhti-kayttotarkoitus opts)
   (assoc kuvaus
          :i18nkey   "rakennuskohde.kuvaus"
          :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennelma" "etvra"}})
   (assoc rakennusluokka-vahahiilisyys :show-when {:path "/kaytto/kohteen-laji" :values #{"rakennus"}})
   (assoc (ryhti-pysyva-tunnus :pysyva-rakennustunnus) :ifc-mapping :ifc-pysyva-rakennustunnus)
   (ryhti-pysyva-tunnus :pysyva-rakennelmatunnus)
   ryhti-mitat
   ryhti-sijainti
   (ryhti-rakennuskohteen-osa opts)
   ryhti-autopaikat
   ryhti-latauspiste
   ryhti-rakennuksen-omistajat])

(defn create-sijoittamislupa-schema [{:keys [info-name i18name body-approvable-top-level-groups]}]
  {:info {:name                  info-name
          :i18name               (or i18name info-name)
          :approvable            true
          :building?             true
          :post-verdict-editable true
          :accordion-fields      buildingid-accordion-paths}
   :body (body tunnus
               (approvable-top-level-groups body-approvable-top-level-groups)
               rakennustunnus)})

(def purkuilmoitus-ryhti
  "The structure is compatible with rakennuskohteenPurkaminen schema
  except that it adds a buildingDemolition checkbox."
  (let [is-demolition {:path "buildingDemolition" :values #{true}}]
    (-> (body poistuma
              {:name        "buildingDemolition" :type :checkbox :default true
               :classPrefix :checkbox            :css  [:full-width]
               :emit        [:buildingDemolition]}
              {:name      "kohde" :type :text :layout :full-width
               :hide-when is-demolition}
              (add-show-when ryhti-rakennuksen-valitsin is-demolition)
              (add-show-when (ryhti-rakennuskohde {:muutoksen-laji :change})
                             is-demolition)
              rakennuksen-osoite)
        remove-required
        vec)))

;;
;; schemas
;;

(defschemas
  1
  [{:info {:name       "hankkeen-kuvaus-minimum"
           :subtype    :hankkeen-kuvaus
           :approvable true
           :order      1}
    :body [kuvaus]}

   {:info {:name         "hankkeen-kuvaus"
           :subtype      :hankkeen-kuvaus
           :approvable   true
           :order        1
           :after-update 'lupapalvelu.application-meta-fields/update-project-description-index}
    :body [(ryhti-id :callForDeviationKey)
           kuvaus
           {:name "poikkeamat" :type :text :max-len 99999 :layout :full-width}
           rahoitus]}

   {:info {:name       "jatkoaika-hankkeen-kuvaus"
           :subtype    :hankkeen-kuvaus
           :approvable true
           :order      1}
    :body (body
            (ryhti-id :callForDeviationKey)
            kuvaus
            {:name           "poikkeamat" :type    :text
             :max-len        99999        :layout  :full-width
             :schema-include :conversion  :i18nkey "hankkeen-kuvaus.poikkeamat"}
            jatkoaika-pvm)}

   {:info {:name             "aiemman-luvan-toimenpide"
           :i18name          "uusiRakennus"
           :approvable       true
           :building?        true
           :accordion-fields buildingid-accordion-paths}
    :body (body (ryhti-id :callForDeviationKey)
                kuvaus
                {:name "poikkeamat" :type :text :max-len 99999 :layout :full-width}
                kuntagml-toimenpide
                tunnus
                rakennuksen-omistajat
                (approvable-top-level-groups rakennuksen-tiedot)
                rakennustunnus)}

   {:info {:name             "archiving-project"
           :i18name          "uusiRakennus"
           :approvable       false
           :building?        true
           :accordion-fields buildingid-accordion-paths}
    :body (body (assoc kuvaus :required false)
                (assoc tunnus :required false)
                kaytto-minimal
                rakennustunnus)}

   {:info {:name                  "uusiRakennus"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (body tunnus
                rakennuksen-omistajat
                (approvable-top-level-groups (with-puhtaan-siirtyman-lupa rakennuksen-tiedot-uusi-rakennus {:show? false}))
                rakennustunnus)}

   {:info {:name                  "uusi-rakennus-ei-huoneistoa"
           :i18name               "uusiRakennus"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (body tunnus
                rakennuksen-omistajat
                (approvable-top-level-groups (with-puhtaan-siirtyman-lupa rakennuksen-tiedot-ilman-huoneistoa-uusi {:show? false}))
                rakennustunnus)}

   {:info {:name                  "rakennuksen-muuttaminen-ei-huoneistoja"
           :i18name               "rakennuksen-muuttaminen"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-ei-huoneistoja-muutos)}

   {:info {:name                  "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
           :i18name               "rakennuksen-muuttaminen"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja-muutos)}

   {:info {:name                  "rakennuksen-muuttaminen"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-muutos)}

   {:info {:name "rakennustietojen-korjaus" :approvable true :building? true :accordion-fields buildingid-accordion-paths}
    :body (approvable-top-level-groups olemassaoleva-rakennus)}

   {:info {:name "rakennuksen-laajentaminen" :approvable true :building? true :accordion-fields buildingid-accordion-paths :post-verdict-editable true}
    :body (approvable-top-level-groups rakennuksen-laajentaminen)}

   {:info {:name "rakennuksen-laajentaminen-ei-huoneistoja" :i18name "rakennuksen-laajentaminen" :approvable true :building? true :accordion-fields buildingid-accordion-paths :post-verdict-editable true}
    :body (approvable-top-level-groups rakennuksen-laajentaminen-ei-huoneistoja)}

   {:info {:name "purkaminen" :i18name "purku" :approvable true :building? true :accordion-fields buildingid-accordion-paths}
    :body (approvable-top-level-groups purku)}

   {:info {:name             "purkamisilmoitus" :i18name   "purkuilmoitus"
           :demolition-notice? true
           :approvable       true               :building? true
           :accordion-fields buildingid-accordion-paths}
    :body (approvable-top-level-groups purkuilmoitus)}

   {:info {:name                  "kaupunkikuvatoimenpide"
           :approvable            true
           :building?             true
           :accordion-fields      buildingid-accordion-paths
           :post-verdict-editable true}
    :body (body tunnus
                (approvable-top-level-groups (with-puhtaan-siirtyman-lupa rakennelma {:show? false}))
                rakennustunnus)}

   {:info {:name                  "kaupunkikuvatoimenpide-kokoontumistilalla"
           :approvable            true
           :building?             true
           :i18name               "kaupunkikuvatoimenpide"
           :accordion-fields      buildingid-accordion-paths
           :post-verdict-editable true}
    :body (body tunnus
                (approvable-top-level-groups (with-puhtaan-siirtyman-lupa rakennelma-kokoontumistilalla {:show? false}))

                rakennustunnus)}

   {:info {:name "kaupunkikuvatoimenpide-ei-tunnusta" :i18name "kaupunkikuvatoimenpide" :approvable true :post-verdict-editable true}
    :body (approvable-top-level-groups rakennelma)}

   {:info {:name "kaupunkikuvatoimenpide-ei-tunnusta-v2" :i18name "kaupunkikuvatoimenpide" :approvable true :post-verdict-editable true}
    :body (approvable-top-level-groups (with-puhtaan-siirtyman-lupa rakennelma {:show? false}))}

   {:info {:name "maalampokaivo" :approvable true :i18name "maalampokaivo" :maalampokaivo? true}
    :body (approvable-top-level-groups maalampokaivo-rakennelma)}

   {:info {:name "maalampokaivo-v2" :approvable true :i18name "maalampokaivo" :maalampokaivo? true}
    :body (approvable-top-level-groups (with-puhtaan-siirtyman-lupa maalampokaivo-rakennelma {:show? false}))}

   {:info {:name "maisematyo" :approvable true}
    :body (approvable-top-level-groups maisematyo)}
   {:info {:name "rajankaynti" :approvable true}
    :body (approvable-top-level-groups (body maastotyot rajankaynti-tyyppi kuvaus))}

   {:info {:name "maankayton-muutos" :approvable true}
    :body (approvable-top-level-groups (body uusi-tai-muutos kuvaus))}

   {:info {:name "rasitetoimitus" :approvable true}
    :body [kt-rasitetoimitus]}

   {:info {:name "kiinteistonmuodostus" :approvable true}
    :body [maastotyot
           kt-kiinteistonmuodostus]}

   {:info {:name              "hakija"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      nil
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body applicant}

   {:info {:name              "hakijan-yhteyshenkilo"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      nil
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body henkilo}

   {:info {:name              "hakija-r"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        "hakija.group.help"
           :section-help      "party.section.help"
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body applicant}

   {:info {:name              "hakija-tj"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :user-authz-roles  #{:writer}
           :group-help        "hakija.group.help"
           :section-help      "party.section.help"
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body (util/postwalk-map #(dissoc % :required) applicant)}

   {:info {:name              "hakija-kt"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      nil
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body applicant}

   {:info {:name              "hakija-ya"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      "hakija-ya.section.help"
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body (schema-body-without-element-by-name ya-party turvakielto)}

   {:info {:name              "hakija-ark"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        false
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      nil
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body (henkilo-yritys-select-group :henkilo-body [henkilotiedot-minimal]
                                       :yritys-body [{:name "yritysnimi" :type :string :required true :size :l}])}

   {:info {:name              "ilmoittaja"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      nil
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body applicant}

   {:info {:name              "vireillepanija"
           :i18name           "osapuoli"
           :order             3
           :removable-by      :all
           :repeating         true
           :last-removable-by :none
           :approvable        true
           :type              :party
           :subtype           :hakija
           :group-help        nil
           :section-help      nil
           :after-update      'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields  hakija-accordion-paths}
    :body party-vireillepanija}

   {:info {:name             "hakijan-asiamies"
           :i18name          "osapuoli"
           :order            3
           :removable-by     :all
           :repeating        true
           :approvable       true
           :type             :party
           :subtype          :hakijan-asiamies
           :group-help       nil
           :section-help     nil
           :after-update     'lupapalvelu.application-meta-fields/applicant-index-update
           :accordion-fields hakijan-asiamies-accordion-paths}
    :body party}

   {:info {:name                             "paasuunnittelija"
           :i18name                          "osapuoli"
           :repeating                        true
           :order                            4
           :removable-by                     :authority
           :approvable                       true
           :redraw-on-approval               true
           :disableable                      true
           :only-one-active                  true
           :accordion-fields                 designer-accordion-paths
           :type                             :party
           :subtype                          :suunnittelija
           :addable-in-states                (set/union states/create-doc-states
                                                        states/post-verdict-but-terminal)
           :editable-in-states               (set/union states/update-doc-states states/post-verdict-but-terminal)
           :section-help                     "schemas.paasuunnittelija.section.help"
           :after-update                     'lupapalvelu.application-meta-fields/designers-index-update

           :refresh-auth-model-on-update-doc true
           ;; Use "Approve" instead of "OK" button to mark document approved
           ;; and add a line to the pdf export with user & timestamp
           :use-designer-approval            true
           :blacklist                        [:neighbor]}
    :body paasuunnittelija}

   {:info {:name                             "suunnittelija"
           :i18name                          "osapuoli"
           :repeating                        true
           :order                            5
           :removable-by                     :all
           :approvable                       true
           :disableable                      true
           :redraw-on-approval               true
           :post-verdict-party               true
           :accordion-fields                 designer-accordion-paths
           :type                             :party
           :subtype                          :suunnittelija
           :addable-in-states                (set/union states/create-doc-states
                                                        states/post-verdict-but-terminal)
           :editable-in-states               (set/union states/update-doc-states states/post-verdict-but-terminal)
           :after-update                     'lupapalvelu.application-meta-fields/designers-index-update

           :refresh-auth-model-on-update-doc true
           :use-designer-approval            true
           :blacklist                        [:neighbor]}

    :body suunnittelija}

   {:info {:name             "tyonjohtaja"
           :i18name          "osapuoli"
           :order            2
           :removable-by     :all
           :repeating        true
           :approvable       true
           :type             :party
           :after-update     'lupapalvelu.application-meta-fields/foreman-index-update
           :blacklist        [:neighbor]
           :foreman-document true}
    :body tyonjohtaja}

   {:info {:name             "tyonjohtaja-v2"
           :i18name          "osapuoli"
           :order            2
           :removable-by     :none
           :repeating        false
           :approvable       true
           :type             :party
           :user-authz-roles roles/writer-roles-with-foreman
           :after-update     'lupapalvelu.application-meta-fields/foreman-index-update
           :accordion-fields foreman-accordion-paths
           :blacklist        [:neighbor]
           :foreman-document true}
    :body tyonjohtaja-v2}

   {:info {:name                             "tyonjohtaja-v3"
           :i18name                          "osapuoli"
           :order                            2
           :removable-by                     :authority
           :repeating                        false
           :approvable                       true
           :redraw-on-approval               true
           :disableable                      true
           :type                             :party
           :user-authz-roles                 roles/writer-roles-with-foreman
           :after-update                     'lupapalvelu.application-meta-fields/foreman-index-update
           :accordion-fields                 foreman-accordion-paths
           :editable-in-doc-states           #{nil :submitted :declared :acknowledged}
           :refresh-auth-model-on-update-doc true
           :blacklist                        [:neighbor]
           :foreman-document                 true}
    :body tyonjohtaja-v3}

   {:info {:name              "maksaja"
           :i18name           "osapuoli"
           :repeating         false
           :order             6
           :removable-by      :all
           :last-removable-by :authority
           :approvable        true
           :subtype           :maksaja
           :section-help      "schemas.maksaja.section.help"
           :accordion-fields  hakija-accordion-paths
           :type              :party
           :blacklist         [:neighbor]}
    :body maksaja}

   {:info {:name                     "rakennuspaikka"
           :approvable               true
           :order                    2
           :type                     :location
           :generates-climate-report true
           :climate-report-schema    "rakennuspaikan-ilmastoselvitys"
           :copy-action              :clear}
    :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin")}

   {:info {:name        "rakennuspaikka-ilman-ilmoitusta"
           :approvable  true
           :i18name     "rakennuspaikka"
           :order       2
           :type        :location
           :copy-action :clear}
    :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin" "hankkeestaIlmoitettu")}

   {:info {:name        "toiminnan-sijainti"
           :approvable  true
           :i18name     "rakennuspaikka"
           :order       2
           :type        :location
           :copy-action :clear}
    :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin" "hankkeestaIlmoitettu")}

   {:info {:name        "kiinteisto"
           :approvable  true
           :order       2
           :type        :location
           :copy-action :clear}
    :body (schema-body-without-element-by-name rakennuspaikka "rantaKytkin" "hallintaperuste" "kaavanaste" "kaavatilanne" "hankkeestaIlmoitettu")}

   {:info {:name             "secondary-kiinteistot"
           :i18name          "kiinteisto"
           :approvable       true
           :order            3
           :repeating        true
           :no-repeat-button true
           :removable-by     :all
           :type             :location
           :copy-action      :clear}
    :body (schema-body-without-element-by-name lisakohde-rakennuspaikka "rantaKytkin" "hallintaperuste" "kaavanaste" "kaavatilanne" "hankkeestaIlmoitettu")}

   {:info {:name "aloitusoikeus" :removable-by :none :approvable true}
    :body (body kuvaus)}

   {:info {:name  "lisatiedot"
           :order 100}
    :body [{:name   "suoramarkkinointikielto"               ;THIS IS DEPRECATED!
            :type   :checkbox
            :layout :full-width}]}

   {:info {:name         "paatoksen-toimitus-rakval"
           :removable-by :none
           :approvable   true
           :order        300
           :blacklist    [:neighbor]
           :copy-action  :clear}
    :body (body
            [(update-in henkilotiedot-minimal [:body] (partial remove #(= turvakielto (:name %))))]
            simple-osoite
            [{:name "yritys" :type :group
              :body [{:name "yritysnimi" :type :string}]}]
            tayta-omat-tiedot-button)}

   {:info {:name  "rakennuspaikka-kuntagml"
           :order 2
           :type  :location}
    :body rakennuspaikka-kuntagml}

   {:info {:name       "rasite-tai-yhteisjarjestely"
           :approvable true
           :order      3}
    :body rasite-tai-yhteisjarjestely}

   {:info {:name "ymparistoluvan-selventaminen"}
    :body [kuvaus]}

   {:info {:name  "talousjatevesien-kasittelysta-poikkeaminen"
           :order 1}
    :body [kuvaus]}

   {:info {:name       "erityista-toimintaa-varten-rakennettava-alue"
           :approvable true}
    :body (into (puhtaan-siirtyman-lupa-old false)
                erityista-toimintaa-varten-rakennettava-alue-v2)}

   (create-sijoittamislupa-schema {:info-name                        "sijoittamislupa"
                                   :body-approvable-top-level-groups rakennuksen-tiedot-sijoittamislupa})

   (create-sijoittamislupa-schema {:info-name                        "sijoittamislupa-v2"
                                   :body-approvable-top-level-groups (sijoittamislupa (puhtaan-siirtyman-lupa-old true))})

   (create-sijoittamislupa-schema {:info-name                        "sijoittamislupa-v3"
                                   :i18name                          "sijoittamislupa-v2"
                                   :body-approvable-top-level-groups (sijoittamislupa)})

   (create-sijoittamislupa-schema {:info-name                        "puhtaan-siirtyman-sijoittamislupa"
                                   :i18name                          "sijoittamislupa-v2"
                                   :body-approvable-top-level-groups (sijoittamislupa puhtaan-siirtyman-lupa)})

   ;; New Ryhti Data Model Schemas

   {:info {:name                     "uusiRakennuskohde"
           :approvable               true
           :building?                true
           :post-verdict-editable    true
           :generates-climate-report true
           :climate-report-schema    "rakennuskohteen-osan-ilmastoselvitys"
           :copy-to-climate-report   [low-carbon/climate-report-lammitetty-nettoala-path]
           :accordion-fields         ryhti-buildingid-accordion-paths}
    :body (body (assoc tunnus :i18nkey "document.operation-identifiers.identifier")
                (ryhti-rakennuskohde {:muutoksen-laji :new}))}

   {:info {:name                  "rakennuskohteenPurkaminen"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      ryhti-buildingid-accordion-paths}
    :body (body ryhti-rakennuksen-valitsin
                poistumanAjankohta
                (ryhti-rakennuskohde {:muutoksen-laji :change}))}

   {:info {:name                  "rakennuskohteenPurkaminen-purkamisilmoitus"
           :i18name               "purkuilmoitus"
           :demolition-notice?    true
           :building?             true
           :approvable            true
           :post-verdict-editable true
           :accordion-fields      ryhti-buildingid-accordion-paths}
    :body purkuilmoitus-ryhti}

   {:info {:name                  "rakennuskohteenLaajentaminen"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      ryhti-buildingid-accordion-paths}
    :body (body ryhti-rakennuksen-valitsin
                laajentaminen
                (ryhti-rakennuskohde {:laajentaminen true :muutoksen-laji :change}))}

   {:info {:name                  "rakennuskohteenMuuttaminen"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      ryhti-buildingid-accordion-paths}
    :body (body ryhti-rakennuksen-valitsin
                muutostyonlaji
                (ryhti-rakennuskohde {:muutoksen-laji :change
                                      :muuttaminen    true}))}

   {:info {:name                  "rakennuskohteenTietojenKorjaus"
           :approvable            true
           :building?             true
           :post-verdict-editable true
           :accordion-fields      ryhti-buildingid-accordion-paths}
    :body (body ryhti-rakennuksen-valitsin
                (ryhti-rakennuskohde {:muutoksen-laji :change}))}])
