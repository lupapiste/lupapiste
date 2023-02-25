(ns lupapalvelu.document.schemas
  (:require [clojure.set :as set]
            [iso-country-codes.countries :as countries]
            [lupapalvelu.building-types :as building-types]
            [lupapalvelu.document.schema-validation :as schema-validation]
            [lupapalvelu.document.tools :as t :refer :all]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user-enums :as user-enums]
            [lupapiste-commons.usage-types :as usages]
            [sade.util :as util]))

(defn update-in-body
  "Updates k in given body path with update function f. Body is vector of maps, path is vector of strings."
  [body path k f]
  (map
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
                 :accordion-fields
                 :blacklist
                 :copy-action})

(def select-one-of-key "_selected")

(defn defschema [version data]
  (let [schema-name (name (get-in data [:info :name]))
        validation-result (schema-validation/validate-doc-schema data)]
    (assert (every? info-keys (keys (:info data))))
    (assert (nil? validation-result) (format "Document schema validation failed. Doc: %s, Error: %s" schema-name validation-result))
    (swap! registered-schemas
           assoc-in
           [version schema-name]
           (-> data
               (assoc-in [:info :name] schema-name)
               (assoc-in [:info :version] version)
               (update :body accordion-field-emitters
                       (->> data
                            :info
                            :accordion-fields
                            (reduce (fn [acc {:keys [type paths] :as field}]
                                      (cond-> acc
                                              (= type :selected) (conj [select-one-of-key])
                                              paths (concat paths)
                                              (sequential? field) (concat [field])))
                                    [])))))))

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
  (if-let [{identifier-field :name} (find-identifier-field-from (-> document :schema-info :name))]
    (get-in document [:data (keyword identifier-field) :value])))

(defn get-latest-schema-version []
  (->> @registered-schemas keys (sort >) first))

(defn with-current-schema-info [{{op :op} :schema-info :as document}]
  (->> document :schema-info get-schema :info
       (merge (when (map? op) {:removable-by :all}))        ; Operation documents are removable by default
       (update document :schema-info merge)))

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
  (map #(if (= (:type %) :group) (assoc % :approvable true) %) v))

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
                    paths paths
                    :else field)))
           (apply concat)
           (map (fn [path] (get-in doc-data (conj (mapv keyword path) :value))))
           (remove util/empty-or-nil?)))))

;;
;; schema sniplets
;;

(def country-list (map :alpha-3 countries/countries))
(def country {:name    "maa"
              :type    :select
              :default "FIN"
              :i18nkey "country.country"
              :sortBy  :displayname
              :body    (map (fn [n] {:name n :i18nkey (str "country." n)}) country-list)})

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

(def postinumero {:name "postinumero" :type :string :subtype :zipcode :size :s :required true :dummy-test :postal-code :i18nkey "osoite.postinumero"})

(def simple-osoite [{:name         "osoite"
                     :type         :group
                     :validator    :address
                     :address-type :contact
                     :blacklist    [turvakielto]
                     :body         [{:name "katu" :type :string :subtype :vrk-address :required true :i18nkey "osoite.katu"}
                                    postinumero
                                    {:name "postitoimipaikannimi" :type :string :subtype :vrk-address :size :m :required true :i18nkey "osoite.postitoimipaikannimi"}
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

(def yhteystiedot [{:name      "yhteystiedot"
                    :type      :group
                    :i18nkey   "yhteystiedot._group_label"
                    :blacklist [:neighbor turvakielto]
                    :body      [{:name "puhelin" :type :string :subtype :tel :required true :i18nkey "puhelin"}
                                {:name "email" :type :string :subtype :email :required true :i18nkey "email"}]}])

(def henkilotiedot-minimal {:name "henkilotiedot"
                            :type :group
                            :body [{:name "etunimi" :type :string :subtype :vrk-name :required true :i18nkey "etunimi"}
                                   {:name "sukunimi" :type :string :subtype :vrk-name :required true :i18nkey "sukunimi"}
                                   {:name turvakielto :type :checkbox :blacklist [turvakielto] :i18nkey "henkilo.turvakieltoKytkin"}]})

(def henkilotiedot {:name "henkilotiedot"
                    :type :group
                    :body [{:name "etunimi" :type :string :subtype :vrk-name :required true :i18nkey "etunimi"}
                           {:name "sukunimi" :type :string :subtype :vrk-name :required true :i18nkey "sukunimi"}
                           {:name           "not-finnish-hetu" :type :checkbox :i18nkey "not-finnish-hetu" :emit [:hetuChanged]
                            :schema-include :yht-219}
                           {:name           "ulkomainenHenkilotunnus" :type :string :i18nkey "ulkomainenHetu"
                            :show-when      {:path "not-finnish-hetu" :values #{true}}
                            :required       true :emit [:hetuChanged]
                            :schema-include :yht-219}
                           {:name      "hetu" :type :hetu :max-len 11 :required true :blacklist [:neighbor turvakielto]
                            :emit      [:hetuChanged] :transform :upper-case :i18nkey "hetu"
                            :show-when {:path           "not-finnish-hetu"
                                        :values         #{false}
                                        :schema-include :yht-219}}
                           {:name turvakielto :type :checkbox :blacklist [turvakielto] :i18nkey "henkilo.turvakieltoKytkin"}]})

(def henkilo (body
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
                                     (map (fn [ht] (if (= (:name ht) "hetu") (merge ht {:required true}) ht))
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

(def e-invoice-operators [{:name "BAWCFI22"}                ; Basware Oyj
                          {:name "003714377140"}            ; Ropo Capital Oy
                          {:name "003708599126"}            ; Open Text Oy
                          {:name "HELSFIHH"}                ; Aktia S\u00e4\u00e4st\u00f6pankki Oyj
                          {:name "POPFFI22"}                ; Paikallisosuuspankit
                          {:name "HANDFIHH"}                ; Handelsbanken
                          {:name "003721291126"}            ; Maventa
                          {:name "003723327487"}            ; Apix Messaging Oy
                          {:name "003717203971"}            ; Notebeat Oy
                          {:name "003723609900"}            ; (tai PAGERO) Pagero
                          {:name "003701150617"}            ; Str\u00e5lfors Oy
                          {:name "FIYAPSOL"}                ; YAP Solutions Oy
                          {:name "00885060259470028"}       ; Tradeshift
                          {:name "TAPIFI22"}                ; S-Pankki Oy (vanha, ent L\u00e4hiTapiola)
                          {:name "INEXCHANGE"}              ; InExchange Factorum AB
                          {:name "DNBAFIHX"}                ; DNB Bank ASA
                          {:name "ITELFIHH"}                ; S\u00e4\u00e4st\u00f6pankit
                          {:name "E204503"}                 ; OpusCapita Solutions Oy
                          {:name "00885790000000418"}       ; HighJump AS
                          {:name "NDEAFIHH"}                ; Nordea
                          {:name "OKOYFIHH"}                ; OP-Pohjola-ryhm\u00e4
                          {:name "003701011385"}            ; Tieto Oyj
                          {:name "DABAFIHH"}                ; Danske Bank Oyj
                          {:name "003703575029"}            ; CGI / TeliaSonera Finland Oyj
                          {:name "AABAFI22"}                ; \u00c5landsbanken Abp
                          {:name "SBANFIHH"}                ; S-Pankki Oy (uusi)
                          ])

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
    {:name     "muu-vireillepanijan-rooli"
     :type     :string
     :size     :l}
    (henkilo-yritys-select-group)))

(def ya-party (henkilo-yritys-select-group :default "yritys"))
(def ya-party-tyomaasta-vastaava (henkilo-yritys-select-group :default "yritys"
                                                              :exclude-companies? false))
(def building-parties (henkilo-yritys-select-group
                        :aria-label :building.owner
                        :henkilo-body henkilo-with-required-hetu
                        :yritys-body yritys-without-kytkimet))


(def koulutusvalinta {:name "koulutusvalinta" :type :select :sortBy :displayname :i18nkey "koulutus" :other-key "koulutus" :required true
                      :body (wrapped user-enums/koulutusvalinta :name)})

(def fise-kelpoisuus-lajit (wrapped user-enums/fise-kelpoisuus-lajit :name))

(def patevyysluokka {:name "patevyysluokka" :type :select :sortBy nil :required true
                     :body [{:name "AA"}
                            {:name "A"}
                            {:name "B"}
                            {:name "C"}
                            ei-tiedossa]})

(def patevyys [koulutusvalinta
               {:name "koulutus" :type :string :required false :i18nkey "muukoulutus"}
               {:name "valmistumisvuosi" :type :string :subtype :recent-year :range 100 :required true}
               {:name "fise" :type :string :required false}
               {:name "fiseKelpoisuus" :type :select :sortBy :displayname :i18nkey "fisekelpoisuus" :size :l :required false :body fise-kelpoisuus-lajit}
               patevyysluokka
               {:name "kokemus" :type :string :subtype :number :min-len 1 :max-len 2 :size :s :required false}
               {:name "patevyys" :type :string :required false}])

(def designer-basic (body
                      (schema-body-without-element-by-name henkilotiedot turvakielto)
                      {:name "yritys" :type :group
                       :body (util/postwalk-map #(dissoc % :required) yritys-minimal)}
                      simple-osoite
                      yhteystiedot))

(def suunnittelutehtavan-vaativuusluokka (assoc patevyysluokka :name "suunnittelutehtavanVaativuusluokka"))

(def paasuunnittelija (body
                        suunnittelutehtavan-vaativuusluokka
                        henkilo-valitsin
                        designer-basic
                        {:name "patevyys" :type :group :body patevyys}))

(def kuntaroolikoodi [{:name      "kuntaRoolikoodi"
                       :i18nkey   "osapuoli.suunnittelija.kuntaRoolikoodi._group_label"
                       :type      :select :sortBy :displayname :required true
                       :other-key "muuSuunnittelijaRooli"
                       :body      [{:name "p\u00e4\u00e4suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.p\u00e4\u00e4suunnittelija"}
                                   {:name "GEO-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.GEO-suunnittelija"}
                                   {:name "LVI-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.LVI-suunnittelija"}
                                   {:name "IV-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.IV-suunnittelija"}
                                   {:name "KVV-suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.KVV-suunnittelija"}
                                   {:name "RAK-rakennesuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.RAK-rakennesuunnittelija"}
                                   {:name "ARK-rakennussuunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.ARK-rakennussuunnittelija"}
                                   {:name "Vaikeiden t\u00F6iden suunnittelija" :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.Vaikeiden t\u00f6iden suunnittelija"}

                                   ; KRYSP yht 2.1.6
                                   {:name "rakennussuunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.rakennussuunnittelija"}
                                   {:name "kantavien rakenteiden suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.kantavien rakenteiden suunnittelija"}
                                   {:name "pohjarakenteiden suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.pohjarakenteiden suunnittelija"}
                                   {:name "ilmanvaihdon suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.ilmanvaihdon suunnittelija"}
                                   {:name "kiinteist\u00f6n vesi- ja viem\u00e4r\u00f6intilaitteiston suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.vesiviemarisuunnittelija"}
                                   {:name "rakennusfysikaalinen suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.rakennusfysikaalinen suunnittelija"}
                                   {:name "kosteusvaurion korjausty\u00f6n suunnittelija" :i18nkey "osapuoli.kuntaRoolikoodi.kosteusvaurion korjausty\u00f6n suunnittelija"}

                                   ei-tiedossa
                                   ]}])

(def suunnittelija (body
                     kuntaroolikoodi
                     {:name "muuSuunnittelijaRooli" :type :string}
                     suunnittelutehtavan-vaativuusluokka
                     henkilo-valitsin
                     designer-basic
                     {:name "patevyys" :type :group :body patevyys}))

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

(def kuntaroolikoodi-tyonjohtaja [{:name     "kuntaRoolikoodi"
                                   :i18nkey  "osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label"
                                   :type     :select
                                   :sortBy   :displayname
                                   :required true
                                   :body     [{:name "KVV-ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.KVV-ty\u00f6njohtaja"}
                                              {:name "IV-ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.IV-ty\u00f6njohtaja"}
                                              {:name "erityisalojen ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.erityisalojen ty\u00f6njohtaja"}
                                              {:name "vastaava ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.vastaava ty\u00f6njohtaja"}
                                              {:name "ty\u00F6njohtaja" :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.ty\u00f6njohtaja"}
                                              ei-tiedossa]}])

(def patevyysvaatimusluokka (assoc patevyysluokka :name "patevyysvaatimusluokka"))

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
                                      :body     [{:name "vastaava ty\u00F6njohtaja" :code :vtj :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.vastaava ty\u00f6njohtaja"}
                                                 {:name "KVV-ty\u00F6njohtaja" :code :kvv :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.KVV-ty\u00f6njohtaja"}
                                                 {:name "IV-ty\u00F6njohtaja" :code :ivt :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.IV-ty\u00f6njohtaja"}
                                                 {:name "erityisalojen ty\u00F6njohtaja" :code :vrt :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.erityisalojen ty\u00f6njohtaja"}
                                                 ;; Use :vtj code here as well, since työnjohtaja role can be very generic.
                                                 {:name "ty\u00F6njohtaja" :code :vtj :i18nkey "osapuoli.tyonjohtaja.kuntaRoolikoodi.ty\u00f6njohtaja"}]}])

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

(def muutostapa {:name "muutostapa" :type :select :sortBy :displayname
                 :size :s :label false :i18nkey "huoneistot.muutostapa"
                 :body [{:name "poisto"}
                        {:name "lisäys" :i18nkey "huoneistot.muutostapa.lisays"}
                        {:name "muutos"}]})

(def muutostapa-hidden (assoc muutostapa
                         :default "lis\u00e4ys"
                         :hidden true))



(def huoneisto-row [{:name     "huoneistoTyyppi" :type :select :sortBy :displayname :size :s
                     :label    false :i18nkey "huoneistot.huoneistoTyyppi"
                     :required true
                     :body     [{:name "asuinhuoneisto"}
                                {:name "toimitila"}
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
                            ei-tiedossa]}
                    {:name "huoneistoala" :type :string :subtype :decimal :size :s :min 1 :max 9999999 :required true :label false :i18nkey "huoneistot.huoneistoala"}
                    {:name "WCKytkin" :type :checkbox :label false :i18nkey "huoneistot.WCKytkin"}
                    {:name "ammeTaiSuihkuKytkin" :type :checkbox :label false :i18nkey "huoneistot.ammeTaiSuihkuKytkin"}
                    {:name "saunaKytkin" :type :checkbox :label false :i18nkey "huoneistot.saunaKytkin"}
                    {:name "parvekeTaiTerassiKytkin" :type :checkbox :label false :i18nkey "huoneistot.parvekeTaiTerassiKytkin"}
                    {:name "lamminvesiKytkin" :type :checkbox :label false :i18nkey "huoneistot.lamminvesiKytkin"}
                    {:name "pysyvaHuoneistotunnus" :type :string :size :s :label false :i18nkey "huoneistot.pysyvaHuoneistotunnus" :readonly true}])

(def huoneistotTable {:name                  "huoneistot"
                      :i18nkey               "huoneistot"
                      :type                  :table
                      :uicomponent           :docgenHuoneistot
                      :validator             :huoneistot
                      :group-help            "huoneistot.groupHelpText"
                      :repeating             true
                      :repeating-allow-empty true
                      :approvable            true
                      :copybutton            true
                      :body                  (conj huoneisto-row muutostapa)})

(def huoneistotTable-new-building (assoc huoneistotTable :body (conj huoneisto-row muutostapa-hidden)))

;; Usage type definitions have moved to lupapiste-commons.usage-types

(def kayttotarkoitus {:name "kayttotarkoitus" :type :select :sortBy :displayname
                      :size :l :i18nkey "kayttotarkoitus" :required true
                      :body usages/rakennuksen-kayttotarkoitus})

(def rakennusluokka {:name           "rakennusluokka" :type :select :sortBy :displayname
                     :size           :l :i18nkey "rakennusluokka" :required true
                     :schema-include :rakennusluokka
                     :body           building-types/rakennuksen-rakennusluokka})

(def tilapainenRakennusKytkin {:name           "tilapainenRakennusKytkin"
                               :type           :checkbox
                               :i18nkey        "tilapainenRakennusKytkin"
                               :schema-include :rakval-223})

(def tilapainenRakennusvoimassaPvm
  {:i18nkey        "rakennuksenTilapaisuusvoimassaPvm"
   :name           "tilapainenRakennusvoimassaPvm"
   :type           :date
   :show-when      {:path "tilapainenRakennusKytkin" :values #{true}}
   :schema-include :rakval-224})

(def kaytto {:name "kaytto"
             :type :group
             :body [{:name "rakentajaTyyppi" :type :select :sortBy :displayname :required true
                     :body [{:name "liiketaloudellinen"}
                            {:name "muu"}
                            ei-tiedossa]}
                    kayttotarkoitus
                    rakennusluokka
                    tilapainenRakennusKytkin
                    tilapainenRakennusvoimassaPvm]})

(def kaytto-minimal {:name "kaytto"
                     :type :group
                     :body [(assoc kayttotarkoitus :required false)
                            (assoc rakennusluokka :required false)]})

(def mitat {:name "mitat"
            :type :group
            :body [{:name "tilavuus" :type :string :size :s :unit :m3 :subtype :number :min 0 :max 9999999}
                   {:name "kerrosala" :type :string :size :s :unit :m2 :subtype :number :min 0 :max 9999999}
                   {:name "rakennusoikeudellinenKerrosala" :type :string :size :s :unit :m2 :subtype :number :min 1 :max 9999999}
                   {:name "kokonaisala" :type :string :size :s :unit :m2 :subtype :number :min 0 :max 9999999}
                   {:name "kerrosluku" :type :string :size :s :subtype :number :min 0 :max 50}
                   {:name "kellarinpinta-ala" :type :string :size :s :unit :m2 :subtype :number :min 1 :max 9999999}]})

(def mitat-muutos (merge (assoc mitat :body
                                      (conj (:body mitat)
                                            {:name "muutosala" :type :string :size :s :unit :m2 :subtype :number :min 0 :max 9999999}))
                         {:group-help "mitat-muutos.help"
                          :whitelist  {:roles [:authority] :otherwise :disabled}}))

(def rakenne {:name "rakenne"
              :type :group
              :body [{:name "rakentamistapa" :type :select :sortBy :displayname :required true :i18nkey "rakentamistapa"
                      :body [{:name "elementti"}
                             {:name "paikalla"}
                             ei-tiedossa]}
                     {:name "kantavaRakennusaine" :type :select :sortBy :displayname :required true :other-key "muuRakennusaine" :i18nkey "kantavaRakennusaine"
                      :body [{:name "betoni"}
                             {:name "tiili"}
                             {:name "ter\u00e4s"}
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

(def lammitys {:name "lammitys"
               :type :group
               :body [{:name "lammitystapa" :type :select :sortBy :displayname :i18nkey "lammitystapa"
                       :body [{:name "vesikeskus"}
                              {:name "ilmakeskus"}
                              {:name "suora s\u00e4hk\u00f6"}
                              {:name "uuni"}
                              {:name "ei l\u00e4mmityst\u00e4"}
                              ei-tiedossa]}
                      {:name "lammonlahde" :type :select :sortBy :displayname :other-key "muu-lammonlahde" :i18nkey "lammonlahde"
                       :body [{:name "kauko tai aluel\u00e4mp\u00f6"}
                              {:name "kevyt poltto\u00f6ljy"}
                              {:name "raskas poltto\u00f6ljy"}
                              {:name "s\u00e4hk\u00f6"}
                              {:name "kaasu"}
                              {:name "kiviihiili koksi tms"}
                              {:name "turve"}
                              {:name "maal\u00e4mp\u00f6"}
                              {:name "puu"}
                              ei-tiedossa]}
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

(def luokitus {:name "luokitus"
               :type :group
               :body [{:name "energialuokka" :type :select :sortBy :displayname :i18nkey "energialuokka"
                       :body [{:name "A"}
                              {:name "B"}
                              {:name "C"}
                              {:name "D"}
                              {:name "E"}
                              {:name "F"}
                              {:name "G"}]}
                      {:name "energiatehokkuusluku" :type :string :size :s :subtype :number}
                      {:name "energiatehokkuusluvunYksikko" :type :select, :sortBy :displayname, :default "kWh/m2"
                       :body [{:name "kWh/m2"}
                              {:name "kWh/brm2/vuosi"}]}
                      {:name "paloluokka" :type :select :sortBy :displayname :i18nkey "paloluokka"
                       :body (->> [{:name "palonkest\u00e4v\u00e4"}
                                   {:name "paloapid\u00e4tt\u00e4v\u00e4"}
                                   {:name "paloahidastava"}
                                   {:name "l\u00e4hinn\u00e4 paloakest\u00e4v\u00e4"}
                                   {:name "l\u00e4hinn\u00e4 paloapid\u00e4tt\u00e4v\u00e4"}
                                   {:name "l\u00e4hinn\u00e4 paloahidastava"}
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

(def rakennustunnus {:name      national-building-id :type :string :subtype :rakennustunnus :hidden true :readonly false
                     :whitelist {:permitType [:ARK] :roles [:authority] :otherwise :disabled}})

(def rakennuksen-tiedot-ilman-huoneistoa [kaytto
                                          mitat
                                          rakenne
                                          lammitys
                                          verkostoliittymat
                                          varusteet
                                          luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-uusi [kaytto
                                               mitat
                                               rakenne
                                               lammitys
                                               verkostoliittymat
                                               varusteet-uusi
                                               luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-muutos [kaytto
                                                 mitat-muutos
                                                 rakenne
                                                 lammitys
                                                 verkostoliittymat
                                                 varusteet
                                                 luokitus])

(def rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja [kaytto
                                                                  rakenne
                                                                  mitat])

(def rakennuksen-tiedot-ilman-huoneistoa-ilman-ominaisuustietoja-muutos [kaytto
                                                                         rakenne
                                                                         mitat-muutos])

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
                                :i18nkey    "kaivo-etaisyydet"
                                :type       :group
                                :group-help "kaivo-etaisyydet.groupHelpText"
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
                                maalampokaivon-etaisyydet))

(def rakennelman-kayttotarkoitukset ["Aallonmurtaja" "Aita" "Antenni" "Asuntovaunu" "Aurinkopaneeli" "Autosuoja" "Autotalli" "Ei tiedossa" "Hyppyrim\u00e4ki" "Ikkuna" "Infotaulu (jalankulkuopastaulu)" "Jakokaappi" "Jalasm\u00f6kki" "J\u00e4tekatos tai -aitaus" "J\u00e4tevesij\u00e4rjestelm\u00e4" "Kasvihuone" "Katos/pergola" "Katsomo" "Katumainostaulu" "Kelluva rakennelma" "Kierr\u00e4tyspiste" "Kioski" "Kolmiopilari" "Laituri" "Lastauslaituri" "Liikuteltava grillikioski" "Lipputankoryhm\u00e4" "Maakellari" "Maal\u00e4mp\u00f6pumppuj\u00e4rjestelm\u00e4" "Mainoslaite" "Markiisi" "Masto" "Muu k\u00e4ytt\u00f6" "Muu rakennelma" "Muu toimenpide" "Muu vesirajalaite" "Muuntamo" "Muuri" "N\u00e4k\u00f6torni" "Odotuskatos" "Opaste" "Ovi" "Parvekelasitus" "Pihaj\u00e4rjestely" "Piippu" "Portti" "Puhelinkioski" "Pylv\u00e4sbanderolli" "Pylv\u00e4staulu" "Pys\u00e4kkikatos" "Pys\u00e4k\u00f6intialue" "Py\u00f6re\u00e4 mainospilari" "Rantamuuri" "Savupiippu" "Siirtopuutarham\u00f6kki" "Suurtaulu, sis\u00e4lt\u00e4 valaistu" "Suurtaulu, ulkoa valaistu" "Taideteos" "Taksikatos" "Tuulivoimala" "Ulkomainoslaite" "Ulkotarjoilualue" "Vaja" "Valaisinpylv\u00e4s" "Varasto" "Varastointialue" "Varastointis\u00e4ili\u00f6" "Viestint\u00e4torni" "Yleis\u00f6teltta" "Yleis\u00f6-WC"])

(def rakennelman-kayttotarkoitus {:name    "kayttotarkoitus"
                                  :type    :select
                                  :i18nkey "rakennelman-kayttotarkoitus"
                                  :body    (mapv #(hash-map :i18nkey (str "rakennelman-kayttotarkoitus." %) :name %) rakennelman-kayttotarkoitukset)})

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
        {:name "kokoontumistilanHenkilomaara" :type :string :subtype :number :min 0
         :size :s :i18nkey "kokoontumistilanHenkilomaara"}))

(def maisematyo (body kuvaus))

(def rakennuksen-omistajat [{:name       "rakennuksenOmistajat"
                             :type       :group
                             :repeating  true
                             :approvable true
                             :body       (body building-parties
                                               [{:name "omistajalaji" :type :select :sortBy :displayname :other-key "muu-omistajalaji" :required true :size :l :i18nkey "omistajalaji._group_label"
                                                 :body [{:name "yksityinen maatalousyritt\u00e4j\u00e4" :i18nkey "omistajalaji.yksityinen-maatalousyrittaja"}
                                                        {:name "muu yksityinen henkil\u00f6 tai perikunta" :i18nkey "omistajalaji.muu-yksityinen"}
                                                        {:name "asunto-oy tai asunto-osuuskunta" :i18nkey "omistajalaji.asunto-oy-tms"}
                                                        {:name "kiinteist\u00f6 oy" :i18nkey "omistajalaji.kiinteisto-oy"}
                                                        {:name "yksityinen yritys (osake-, avoin- tai kommandiittiyhti\u00f6, osuuskunta)" :i18nkey "omistajalaji.yksityinen-yritys"}
                                                        {:name "valtio- tai kuntaenemmist\u00f6inen yritys" :i18nkey "omistajalaji.valtio-tai-kunta-yritys"}
                                                        {:name "kunnan liikelaitos" :i18nkey "omistajalaji.kunnan-liikelaitos"}
                                                        {:name "valtion liikelaitos" :i18nkey "omistajalaji.valtion-liikelaitos"}
                                                        {:name "pankki tai vakuutuslaitos" :i18nkey "omistajalaji.pankki-tai-vakuutuslaitos"}
                                                        {:name "kunta tai kuntainliitto" :i18nkey "omistajalaji.kunta-tai-kuntaliitto"}
                                                        {:name "valtio" :i18nkey "omistajalaji.valtio"}
                                                        {:name "sosiaaliturvarahasto" :i18nkey "omistajalaji.sosiaaliturvarahasto"}
                                                        {:name "uskonnollinen yhteis\u00f6, s\u00e4\u00e4ti\u00f6, puolue tai yhdistys" :i18nkey "omistajalaji.yhteiso-saatio-puolue-yhdistys"}
                                                        ei-tiedossa]}
                                                {:name "muu-omistajalaji" :type :string :i18nkey "muu-omistajalaji"}])}])

(def muumuutostyo "muut muutosty\u00f6t")
(def perustusten-korjaus "perustusten ja kantavien rakenteiden muutos- ja korjausty\u00f6t")
(def kayttotarkotuksen-muutos "rakennukse p\u00e4\u00e4asiallinen k\u00e4ytt\u00f6tarkoitusmuutos")

(def muutostyonlaji [{:name        "perusparannuskytkin"
                      :type        :checkbox
                      :classPrefix :bottombox}
                     {:name      "rakennustietojaEimuutetaKytkin" :type :checkbox
                      :whitelist {:roles [:authority] :otherwise :hidden}}
                     {:name "muutostyolaji" :type :select :sortBy :displayname :required true :i18nkey "muutostyolaji"
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


(def laajentaminen [{:name "laajennuksen-tiedot"
                     :type :group
                     :body [{:name "perusparannuskytkin"
                             :type :checkbox}
                            {:name "mitat"
                             :type :group
                             :body [{:name "tilavuus" :type :string :size :s :unit :m3 :subtype :number :min -9999999 :max 9999999}
                                    {:name "kerrosala" :type :string :size :s :unit :m2 :subtype :number :min -9999999 :max 9999999}
                                    {:name "rakennusoikeudellinenKerrosala" :type :string :size :s :unit :m2 :subtype :number :min -9999999 :max 9999999}
                                    {:name "kokonaisala" :type :string :size :s :unit :m2 :subtype :number :min -9999999 :max 9999999}
                                    {:name "huoneistoala" :type :group :repeating true
                                     :body [{:name "pintaAla" :type :string :size :s :unit :m2 :subtype :number :min -9999999 :max 9999999}
                                            {:name "kayttotarkoitusKoodi" :type :select :sortBy :displayname
                                             :body [{:name "asuntotilaa(ei vapaa-ajan asunnoista)"}
                                                    {:name "myym\u00e4l\u00e4, majoitus- ja ravitsemustilaa"}
                                                    {:name "hoitotilaa"}
                                                    {:name "toimisto- ja hallintotilaa"}
                                                    {:name "kokoontumistilaa"}
                                                    {:name "opetustilaa"}
                                                    {:name "tuotantotilaa(teollisuus)"}
                                                    {:name "varastotilaa"}
                                                    {:name "muuta huoneistoalaan kuuluvaa tilaa"}
                                                    ei-tiedossa]}]}]}]}])

(def rakennuksen-laajentaminen-ilman-rakennustietoja (body
                                                       olemassaoleva-rakennus-ilman-rakennustietoja
                                                       laajentaminen))

(def rakennuksen-laajentaminen (body
                                 rakennuksen-laajentaminen-ilman-rakennustietoja
                                 rakennuksen-tiedot))

(def rakennuksen-laajentaminen-ei-huoneistoja (body
                                                rakennuksen-laajentaminen-ilman-rakennustietoja
                                                rakennuksen-tiedot-ilman-huoneistoa))

(def purku (body
             {:name "poistumanSyy" :type :select :sortBy :displayname
              :body [{:name "purettu uudisrakentamisen vuoksi"}
                     {:name "purettu muusta syyst\u00e4"}
                     {:name "tuhoutunut"}
                     {:name "r\u00e4nsistymisen vuoksi hyl\u00e4tty"}
                     {:name "poistaminen"}]}
             {:name "poistumanAjankohta" :type :date}
             olemassaoleva-rakennus-ei-huoneistoja-ei-ominaisuus-tietoja))

(def rakennuspaikka [{:name        "kiinteisto"
                      :type        :group
                      :uicomponent :propertyGroup
                      :body        [{:name      "maaraalaTunnus" :type :maaraalaTunnus :uicomponent :maaraala-tunnus :size :s
                                     :transform :zero-pad-4}
                                    ;{:name "luvanNumero" :type :string :size :m :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.luvanNumero"}
                                    {:name "tilanNimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                    {:name "rekisterointipvm" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                    {:name "maapintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                    {:name "vesipintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                    {:name "rantaKytkin" :type :checkbox :uicomponent :docgen-input :inputType :checkbox}]}
                     {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                      :body [{:name "oma"}
                             {:name "vuokra"}
                             ei-tiedossa]}
                     {:name "kaavanaste" :type :select :sortBy :displayname :hidden true
                      :body [{:name "asema"}
                             {:name "ranta"}
                             {:name "rakennus"}
                             {:name "yleis"}
                             {:name "ei kaavaa"}
                             ei-tiedossa]}
                     {:name "kaavatilanne" :type :select :sortBy :displayname
                      :body [{:name "maakuntakaava"}
                             {:name "oikeusvaikutteinen yleiskaava"}
                             {:name "oikeusvaikutukseton yleiskaava"}
                             {:name "asemakaava"}
                             {:name "ranta-asemakaava"}
                             {:name "ei kaavaa"}]}
                     {:name       "hankkeestaIlmoitettu" :type :group
                      :group-help "hankkeestaIlmoitettu.groupHelpText"
                      :body       [{:name "hankkeestaIlmoitettuPvm" :type :date :i18nkey "date"}]}])

(def rakennuspaikka-kuntagml [{:name        "kiinteisto"
                               :type        :group
                               :uicomponent :propertyGroup
                               :body        [{:name "maaraalaTunnus" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                             {:name "tilanNimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                             {:name "rekisterointipvm" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                             {:name "kerrosala" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                             {:name "maapintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                             {:name "vesipintaala" :type :string :readonly true :unit :hehtaaria :uicomponent :docgen-input :inputType :string}
                                             {:name "rakennusoikeusYhteensa" :type :string :readonly true :unit :m2 :uicomponent :docgen-input :inputType :string}
                                             {:name "kylanimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                             {:name "kiinteistotunnus" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                             {:name "rantaKytkin" :type :checkbox :uicomponent :docgen-input :inputType :checkbox}]}
                              {:name "osoite"
                               :type :group
                               :body [{:name "kunta" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                      {:name "postinumero" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                      {:name "osoitenimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                      {:name "osoitenumero" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                      {:name "kaupunginosanumero" :type :string :readonly true :uicomponent :docgen-input :inputType :string}
                                      {:name "postitoimipaikannimi" :type :string :readonly true :uicomponent :docgen-input :inputType :string}]}
                              {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                               :body [{:name "oma"}
                                      {:name "vuokra"}
                                      ei-tiedossa]}
                              {:name "kaavatilanne" :type :select :sortBy :displayname
                               :body [{:name "maakuntakaava"}
                                      {:name "oikeusvaikutteinen yleiskaava"}
                                      {:name "oikeusvaikutukseton yleiskaava"}
                                      {:name "asemakaava"}
                                      {:name "ranta-asemakaava"}
                                      {:name "ei kaavaa"}]}])

(defn property-list-field [element-name & [opts]]
  (merge {:name     element-name
          :required true
          :type     :string
          :i18nkey  (str "kiinteistoLista." element-name)}
         opts))

(defn property-list-owners [element-name & [opts]]
  (let [show-when (fn [values]
                    {:show-when {:path   "henkilolaji"
                                 :values values}})
        show-when-person (show-when #{"luonnollinen" "kuolinpesa"})
        show-when-company (show-when #{"juridinen"})
        property-owner-field (fn [field-name & args]
                               (-> (apply property-list-field field-name args)
                                   (assoc :i18nkey (format "kiinteistoLista.%s.%s" element-name field-name))))]
    (merge {:name      element-name
            :type      :table
            :repeating true
            :i18nkey   (str "kiinteistoLista." element-name)
            :body      [(property-owner-field "nimi" show-when-company)
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
  {:name        element-name
   :type        :table
   :uicomponent :docgen-property-list
   :validator   :property-list
   :repeating   true
   :approvable  true
   :body        [(property-list-field "kiinteistotunnus")
                 (property-list-field "pintaAla" {:subtype :number
                                                  :unit    :hehtaaria})
                 (property-list-field "tilanNimi")
                 (property-list-field "rekisterointiPvm" {:type :date})
                 (property-list-owners "omistajat")]})

(def rasite-tai-yhteisjarjestely [{:name "tyyppi"
                                   :type :radioGroup
                                   :body [{:name "rakennusrasite"}
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
                                              ; Please change yhteystiedot-api/application-property-owners if kiinteistoTunnus path changes
                                              {:name "kiinteistoTunnus" :type :string :hidden true}
                                              ;{:name "luvanNumero" :type :string :size :m :label false :uicomponent :docgen-input :inputType :string :i18nkey "muutHankkeet.luvanNumero"}
                                              {:name "tilanNimi" :type :string :uicomponent :docgen-input :inputType :string}
                                              {:name "rekisterointipvm" :type :date}
                                              {:name "maapintaala" :type :string :unit :hehtaaria :uicomponent :docgen-input :inputType :string :subtype :number}
                                              {:name "vesipintaala" :type :string :unit :hehtaaria :uicomponent :docgen-input :inputType :string :subtype :number}
                                              {:name "rantaKytkin" :type :checkbox :uicomponent :docgen-input :inputType :checkbox}]}
                               {:name "hallintaperuste" :type :select :sortBy :displayname :required true
                                :body [{:name "oma"}
                                       {:name "vuokra"}
                                       ei-tiedossa]}
                               {:name "kaavanaste" :type :select :sortBy :displayname :hidden true
                                :body [{:name "asema"}
                                       {:name "ranta"}
                                       {:name "rakennus"}
                                       {:name "yleis"}
                                       {:name "ei kaavaa"}
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
                         :body     [{:name "Rajan paikkaa ja rajamerkki\u00e4 koskeva ep\u00e4selvyys (rajank\u00e4ynti)"}
                                    {:name "Ep\u00e4selvyys siit\u00e4, mihin rekisteriyksikk\u00f6\u00f6n jokin alue kuuluu"}
                                    {:name "Rasiteoikeutta ja rasitteen sijaintia koskeva ep\u00e4selvyys"}
                                    {:name "Kiinteist\u00f6n osuus yhteiseen alueeseen tai yhteiseen erityiseen etuuteen ja osuudensuuruus sek\u00e4 kiinteist\u00f6lle kuuluva erityinen etuus"}
                                    {:name "Yhteisen alueen tai yhteisen erityisen etuuden osakaskiinteist\u00f6t ja niille kuuluvien osuuksien suuruudet"}
                                    {:name "Ep\u00e4selv\u00e4n, kadonneen tai turmeltuneen toimitusasiakirjan tai kartan sis\u00e4lt\u00f6"}
                                    {:name "Ristiriitaisista toimitusasiakirjoista tai kartoista johtuva ep\u00e4selvyys"}]})

(def uusi-tai-muutos
  "Used in maankayton-muutos."
  {:name     "uusiKytkin"
   :type     :radioGroup
   :required true
   :default  "uusi"
   :body     [{:name "uusi"},
              {:name "muutos"}]})

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
                                                       {:name "tilusvaihto"}
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
                                      :body     [{:name "Ajoneuvojen pit\u00e4minen"}
                                                 {:name "Ajoneuvojen pit\u00e4minen, venevalkama ja laituri"}
                                                 {:name "Autojen pit\u00e4minen"}
                                                 {:name "Autojen pit\u00e4minen, venevalkama ja -laituri"}
                                                 {:name "Erityinen oikeus, johto tai vastaava"}
                                                 {:name "Erityisesti suojeltavan lajin esiintymispaikka"}
                                                 {:name "Hiekan ottaminen"}
                                                 {:name "Huoltorasite"}
                                                 {:name "J\u00e4teveden johtaminen ja k\u00e4sittely"}
                                                 {:name "J\u00e4tteiden kokoamispaikka"}
                                                 {:name "Johto"}
                                                 {:name "K\u00e4ytt\u00f6rasite"}
                                                 {:name "Kaasujohto"}
                                                 {:name "Kaivoksen apualue"}
                                                 {:name "Kaivosalue"}
                                                 {:name "Kaivoslupa-alue"}
                                                 {:name "Kaivoslupa-alueen apualue"}
                                                 {:name "Kalastuksen kielto lohi- ja siikapitoisessa vesist\u00f6ss\u00e4"}
                                                 {:name "Kalastuksen kielto padon alapuolella"}
                                                 {:name "Kalastusta varten tarvittava alue"}
                                                 {:name "Kalav\u00e4yl\u00e4"}
                                                 {:name "Kiinte\u00e4 muinaisj\u00e4\u00e4nn\u00f6s"}
                                                 {:name "Kiinteist\u00f6jen yhteinen l\u00e4mp\u00f6keskus"}
                                                 {:name "Kiven ottaminen"}
                                                 {:name "Kulkuyhteys asemakaava-alueella"}
                                                 {:name "Kullanhuuhdonta-alue"}
                                                 {:name "L\u00e4mp\u00f6johto"}
                                                 {:name "Laiterasite"}
                                                 {:name "Laituri"}
                                                 {:name "Lastauspaikka"}
                                                 {:name "Lentokent\u00e4n l\u00e4hestymisalue"}
                                                 {:name "Lopetetun kaivoksen vaikutusalue"}
                                                 {:name "Lunastuslain mukainen erityinen oikeus"}
                                                 {:name "Luonnonsuojelualue (Ahvenanmaa)"}
                                                 {:name "Luonnonsuojelualue"}
                                                 {:name "Maa-aineksen ottaminen"}
                                                 {:name "Maakaasujohto"}
                                                 {:name "Maantielain mukainen tieoikeus"}
                                                 {:name "Maantien liit\u00e4nn\u00e4isalue"}
                                                 {:name "Maantien n\u00e4kem\u00e4alue"}
                                                 {:name "Maantien suoja-alue"}
                                                 {:name "Malminetsint\u00e4alue"}
                                                 {:name "Moottorikelkkailureitti"}
                                                 {:name "Oikeus vesivoimaan"}
                                                 {:name "Ojitusrasite"}
                                                 {:name "Padotusalue"}
                                                 {:name "Perustusrasite"}
                                                 {:name "Puhelinjohto"}
                                                 {:name "Puutavaran varastointi"}
                                                 {:name "Puutavaran varastointi"}
                                                 {:name "Radanpit\u00e4j\u00e4ll\u00e4 oikeus laskuojaan"}
                                                 {:name "Radanpit\u00e4j\u00e4ll\u00e4 oikeus tiehen"}
                                                 {:name "Rakennerasite"}
                                                 {:name "Ratalain mukainen rautatieoikeus"}
                                                 {:name "Rautatien liit\u00e4nn\u00e4isalue"}
                                                 {:name "Rautatien n\u00e4kem\u00e4alue"}
                                                 {:name "Rautatien suoja-alue"}
                                                 {:name "S\u00e4hk\u00f6johto"}
                                                 {:name "Sadevesiviem\u00e4ri"}
                                                 {:name "Saven ottaminen"}
                                                 {:name "Sein\u00e4rasite"}
                                                 {:name "Sietorasite"}
                                                 {:name "Sopimus luontoarvokaupasta"}
                                                 {:name "Sopimus m\u00e4\u00e4r\u00e4aikaisesta rauhoittamisesta"}
                                                 {:name "Sopimus ymp\u00e4rist\u00f6tuesta"}
                                                 {:name "Soran ottaminen"}
                                                 {:name "Suojeltu luontotyyppi"}
                                                 {:name "Talousveden johtaminen"}
                                                 {:name "Tienpit\u00e4j\u00e4ll\u00e4 oikeus laskuojaan (laki yleisist\u00e4 teist\u00e4)"}
                                                 {:name "Tienpit\u00e4j\u00e4ll\u00e4 oikeus laskuojaan (maantielaki)"}
                                                 {:name "Tienpitoaineen kuljettaminen"}
                                                 {:name "Tienpitoaineen ottaminen"}
                                                 {:name "Tieoikeus"}
                                                 {:name "Turpeen ottaminen"}
                                                 {:name "Tutka-aseman ymp\u00e4rist\u00f6"}
                                                 {:name "Uimapaikka"}
                                                 {:name "Ulko- ja sis\u00e4saariston v\u00e4linen raja"}
                                                 {:name "Ulkoilureitin lev\u00e4hdyspaikka"}
                                                 {:name "Ulkoilureitti"}
                                                 {:name "Uoma"}
                                                 {:name "V\u00e4est\u00f6suojelua varten tarvittava rakennelma"}
                                                 {:name "V\u00e4h\u00e4isten laitteiden sijoittaminen (Maank\u00e4ytt\u00f6- ja rakennuslaki 163 \u00a7)"}
                                                 {:name "Valtausalue (kaivoslaki 503/1965)"}
                                                 {:name "Valtion retkeilyalueen lis\u00e4alue"}
                                                 {:name "Veden johtaminen maan kuivattamista varten"}
                                                 {:name "Vedenottamo"}
                                                 {:name "Vedenottamon suoja-alue"}
                                                 {:name "Venelaituri"}
                                                 {:name "Venevalkama (kiinteist\u00f6nmuodostamislaki)"}
                                                 {:name "Venevalkama (yksityistielaki)"}
                                                 {:name "Venevalkama ja -laituri"}
                                                 {:name "Venevalkama ja ajoneuvojen pit\u00e4minen"}
                                                 {:name "Venevalkama ja autojen pit\u00e4minen"}
                                                 {:name "Vesijohto"}
                                                 {:name "Vesilain mukainen k\u00e4ytt\u00f6oikeus"}
                                                 {:name "Vesilain mukainen rakennus, laite tai vastaava"}
                                                 {:name "Viem\u00e4rijohto"}
                                                 {:name "Voiman- ja tiedonsiirtolinja"}
                                                 {:name "Voimansiirtolinja"}
                                                 {:name "Ydinj\u00e4tteiden loppusijoituspaikka"}
                                                 {:name "Yhdyskuntateknisten laitteiden sijoittaminen (Maank\u00e4ytt\u00f6- ja rakennuslaki 161 \u00a7)"}
                                                 {:name "Yhteisj\u00e4rjestely (Maank\u00e4ytt\u00f6- ja rakennuslaki 164 \u00a7)"}
                                                 {:name "Yhteisk\u00e4ytt\u00f6alue (Maank\u00e4ytt\u00f6- ja rakennuslaki 75 ja 91 \u00a7)"}
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

(def jatkoaika-pvm
  [{:name "jatkoaika-paattyy" :type :date}
   {:name "rakennustyo-aloitettu" :type :date}])


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
    :body [kuvaus
           {:name "poikkeamat" :type :text :max-len 99999 :layout :full-width}
           rahoitus]}

   {:info {:name       "jatkoaika-hankkeen-kuvaus"
           :subtype    :hankkeen-kuvaus
           :approvable true
           :order      1}
    :body (body
            kuvaus
            jatkoaika-pvm)}

   {:info {:name             "aiemman-luvan-toimenpide"
           :i18name          "uusiRakennus"
           :approvable       true
           :accordion-fields buildingid-accordion-paths}
    :body (body kuvaus
                {:name "poikkeamat" :type :text :max-len 99999 :layout :full-width}
                kuntagml-toimenpide
                tunnus
                rakennuksen-omistajat
                (approvable-top-level-groups rakennuksen-tiedot)
                rakennustunnus)}

   {:info {:name             "archiving-project"
           :i18name          "uusiRakennus"
           :approvable       false
           :accordion-fields buildingid-accordion-paths}
    :body (body (assoc kuvaus :required false)
                (assoc tunnus :required false)
                kaytto-minimal
                rakennustunnus)}

   {:info {:name                  "uusiRakennus"
           :approvable            true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (body tunnus
                rakennuksen-omistajat
                (approvable-top-level-groups rakennuksen-tiedot-uusi-rakennus)
                rakennustunnus)}

   {:info {:name                  "uusi-rakennus-ei-huoneistoa"
           :i18name               "uusiRakennus"
           :approvable            true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (body tunnus
                rakennuksen-omistajat
                (approvable-top-level-groups rakennuksen-tiedot-ilman-huoneistoa-uusi)
                rakennustunnus)}

   {:info {:name                  "rakennuksen-muuttaminen-ei-huoneistoja"
           :i18name               "rakennuksen-muuttaminen"
           :approvable            true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-ei-huoneistoja-muutos)}

   {:info {:name                  "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
           :i18name               "rakennuksen-muuttaminen"
           :approvable            true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuus-tietoja-muutos)}

   {:info {:name                  "rakennuksen-muuttaminen"
           :approvable            true
           :post-verdict-editable true
           :accordion-fields      buildingid-accordion-paths}
    :body (approvable-top-level-groups rakennuksen-muuttaminen-muutos)}

   {:info {:name "rakennustietojen-korjaus" :approvable true :accordion-fields buildingid-accordion-paths}
    :body (approvable-top-level-groups olemassaoleva-rakennus)}

   {:info {:name "rakennuksen-laajentaminen" :approvable true :accordion-fields buildingid-accordion-paths :post-verdict-editable true}
    :body (approvable-top-level-groups rakennuksen-laajentaminen)}

   {:info {:name "rakennuksen-laajentaminen-ei-huoneistoja" :i18name "rakennuksen-laajentaminen" :approvable true :accordion-fields buildingid-accordion-paths :post-verdict-editable true}
    :body (approvable-top-level-groups rakennuksen-laajentaminen-ei-huoneistoja)}

   {:info {:name "purkaminen" :i18name "purku" :approvable true :accordion-fields buildingid-accordion-paths}
    :body (approvable-top-level-groups purku)}

   {:info {:name                  "kaupunkikuvatoimenpide"
           :approvable            true
           :accordion-fields      buildingid-accordion-paths
           :post-verdict-editable true}
    :body (body tunnus
                (approvable-top-level-groups rakennelma)
                rakennustunnus)}

   {:info {:name                  "kaupunkikuvatoimenpide-kokoontumistilalla"
           :approvable            true
           :i18name               "kaupunkikuvatoimenpide"
           :accordion-fields      buildingid-accordion-paths
           :post-verdict-editable true}
    :body (body tunnus
                (approvable-top-level-groups rakennelma-kokoontumistilalla)
                rakennustunnus)}

   {:info {:name "kaupunkikuvatoimenpide-ei-tunnusta" :i18name "kaupunkikuvatoimenpide" :approvable true :post-verdict-editable true}
    :body (approvable-top-level-groups rakennelma)}

   {:info {:name "maalampokaivo" :approvable true :i18name "maalampokaivo"}
    :body (approvable-top-level-groups maalampokaivo-rakennelma)}

   {:info {:name "maisematyo" :approvable true}
    :body (approvable-top-level-groups maisematyo)}
   {:info {:name "rajankaynti" :approvable true}
    :body (approvable-top-level-groups (body rajankaynti-tyyppi kuvaus))}

   {:info {:name "maankayton-muutos" :approvable true}
    :body (approvable-top-level-groups (body uusi-tai-muutos kuvaus))}

   {:info {:name "rasitetoimitus" :approvable true}
    :body [kt-rasitetoimitus]}

   {:info {:name "kiinteistonmuodostus" :approvable true}
    :body [kt-kiinteistonmuodostus]}

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
           :accordion-fields  hakija-accordion-paths
           }
    :body party}

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
           :accordion-fields  hakija-accordion-paths
           }
    :body party}

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
           :accordion-fields  hakija-accordion-paths
           }
    :body (util/postwalk-map #(dissoc % :required) party)}

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
           :accordion-fields  hakija-accordion-paths
           }
    :body party}

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
           :accordion-fields  hakija-accordion-paths
           }
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
           :accordion-fields  hakija-accordion-paths
           }
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
           :accordion-fields  hakija-accordion-paths
           }
    :body party}

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
           :accordion-fields  hakija-accordion-paths
           }
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
           :accordion-fields hakijan-asiamies-accordion-paths
           }
    :body party}

   {:info {:name             "paasuunnittelija"
           :i18name          "osapuoli"
           :order            4
           :removable-by     :authority
           :approvable       true
           :accordion-fields designer-accordion-paths
           :type             :party
           :subtype          :suunnittelija
           :section-help     "schemas.paasuunnittelija.section.help"
           :after-update     'lupapalvelu.application-meta-fields/designers-index-update
           }
    :body paasuunnittelija}

   {:info {:name               "suunnittelija"
           :i18name            "osapuoli"
           :repeating          true
           :order              5
           :removable-by       :all
           :approvable         true
           :disableable        true
           :redraw-on-approval true
           :post-verdict-party true
           :accordion-fields   designer-accordion-paths
           :type               :party
           :subtype            :suunnittelija
           :addable-in-states  (set/union states/create-doc-states
                                          states/post-verdict-but-terminal)
           :editable-in-states (set/union states/update-doc-states states/post-verdict-but-terminal)
           :after-update       'lupapalvelu.application-meta-fields/designers-index-update
           }

    :body suunnittelija}

   {:info {:name         "tyonjohtaja"
           :i18name      "osapuoli"
           :order        2
           :removable-by :all
           :repeating    true
           :approvable   true
           :type         :party
           :after-update 'lupapalvelu.application-meta-fields/foreman-index-update}
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
           :accordion-fields foreman-accordion-paths}
    :body tyonjohtaja-v2}

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

   {:info {:name        "rakennuspaikka"
           :approvable  true
           :order       2
           :type        :location
           :copy-action :clear}
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
           :order 2}
    :body rakennuspaikka-kuntagml}

   {:info {:name  "rasite-tai-yhteisjarjestely"
           :order 3}
    :body rasite-tai-yhteisjarjestely}

   {:info {:name "ymparistoluvan-selventaminen"}
    :body [kuvaus]}

   {:info {:name  "talousjatevesien-kasittelysta-poikkeaminen"
           :order 1}
    :body [kuvaus]}
   ])
