(ns lupapalvelu.document.kiinteistotoimitus-canonical
  (:require [clojure.walk :as walk]
            [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [sade.date :as date]
            [sade.strings :as ss]))

(defn operation-name
  "Operation name to schema element name.
  Special cases are handled explcitly, others are converted:
  'this-is-operation' -> :ThisIsOperation"
  [name]
  (when name
    (let [specials {"lohkominen-tonttijako" :Lohkominen
                    "lohkominen-ohjeellinen" :Lohkominen
                    "kiinteistolajin-muutos" :KiinteistolajinMuutos
                    "kiinteiston-tunnusmuutos" :KiinteistolajinMuutos}
          name->xml (fn [n]
                      (let [parts (ss/split n #"-")]
                        (->> parts (map ss/capitalize) ss/join keyword)))]
      (or (get specials name) (name->xml name)))))

(defmulti kiinteistonmuodostus-details (fn [name _] name))

(defmethod kiinteistonmuodostus-details :default
  [_ _])

(defmethod kiinteistonmuodostus-details "lohkominen-tonttijako"
  [_ _]
  {:lohkomisenTyyppi "Tonttijaon mukainen tontti"})

(defmethod kiinteistonmuodostus-details "lohkominen-ohjeellinen"
  [_ _]
  {:lohkomisenTyyppi "Ohjeellisen tonttijaon mukainen rakennuspaikka"})

(defmulti operation-details (fn [doc]
                              (-> doc :schema-info :name keyword)))

(defmethod operation-details :kiinteistonmuodostus
  [doc]
  (let [group (-> doc :data :kiinteistonmuodostus)
        name  (:kiinteistonmuodostusTyyppi group)]
    {:name (operation-name name)
     :details (merge (kiinteistonmuodostus-details name doc)
                     {:kuvaus (:kuvaus group)})}))


(defmethod operation-details :rajankaynti
  [{data :data}]
  {:name :KiinteistonMaaritys
   :details {:selvitettavaAsia (:rajankayntiTyyppi data)
             :kuvaus (:kuvaus data)}})

(defn property-details
  "List of maps with either :property-id or :mat (kiinteisto- and maaraalatunnus)"
  [docs & [app]]
  (for [{{property :kiinteisto} :data} docs]
    (if-let [mat (canonical-common/maaraalatunnus property app)]
      {:mat mat}
      (if app
        {:property-id (:propertyId app)}
        {:property-id (:kiinteistoTunnus property)}))))

(defn kiinteisto-vs-maaraala
  ":kiinteistotieto and :maaraAlatieto sequences for given properties.
  Properties are property-details results."
  [properties]
  (letfn [(safe-conj [coll pred x]
            (if pred
              (let [coll (or coll [])]
                (conj coll x))
              coll))]
    (reduce (fn [acc p]
              (let [{:keys [kiinteistotieto maaraAlatieto]} acc
                    {:keys [property-id mat]} p
                    kt (safe-conj kiinteistotieto
                                  property-id
                                  {:Kiinteisto {:kiinteistotunnus property-id}})
                    m (safe-conj maaraAlatieto
                                 mat
                                 {:MaaraAla {:maaraAlatunnus mat}})]
                (merge acc {:kiinteistotieto kt :maaraAlatieto m})))
            {}
            properties)))

(defn rt-details
  "All the rasitetoimitus operations are combined into one Rasitetoimitus,
  with kayttooikeustieto sequence."
  [docs from-property-id to-properties]
  (let [rt-docs (canonical-common/schema-info-filter docs :name #{"rasitetoimitus"})
        to-prop-ids (walk/walk :property-id #(remove nil? %) to-properties)]
    (when (not-empty rt-docs)
     {:name :Rasitetoimitus
      :details {:kayttooikeustieto
                (flatten (for [doc rt-docs
                               :let [group (-> doc :data :rasitetoimitus)
                                     date  (-> group :paattymispvm date/xml-date)]]
                           (map (fn [p] {:KayttoOikeus
                                         (merge {:kayttooikeuslaji (:kayttooikeuslaji group)
                                                 :kayttaja p
                                                 :antaja from-property-id}
                                                (when date
                                                  {:paattymispvm date})
                                                {:valiaikainenKytkin (not (ss/blank? date))})})
                                to-prop-ids)))}})))

(defn kiinteistotoimitus-canonical [application lang]
  (let [app (tools/unwrapped application)
        docs  (canonical-common/documents-without-blanks app)
        property-id (:propertyId application)
        main-property (property-details (canonical-common/schema-info-filter docs :name "kiinteisto") app)
        main-property-canon (kiinteisto-vs-maaraala main-property)
        secondaries (property-details (canonical-common/schema-info-filter docs :name "secondary-kiinteistot"))
        all-properties (concat main-property secondaries)
        all-properties-canon (kiinteisto-vs-maaraala all-properties)
        op-docs (canonical-common/schema-info-filter docs :name #{"kiinteistonmuodostus" "rajankaynti"})
        rt-dets (rt-details docs property-id secondaries)
        op-details (map operation-details op-docs)
        op-details (if rt-dets
                     (conj op-details rt-dets)
                     op-details)
        parties (canonical-common/process-parties docs lang)
        toimitus-fn (fn [{:keys [:name :details]}]
                      (merge {:toimituksenTiedottieto
                              {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
                              :toimitushakemustieto
                              {:Toimitushakemus
                               (merge all-properties-canon
                                      {:osapuolitieto parties
                                       :sijaintitieto (canonical-common/get-sijaintitieto application)
                                       :tilatieto (canonical-common/simple-application-state app)
                                       :hakemustunnustieto {:Hakemustunnus {:tunnus (:id application)
                                                                            :sovellus "Lupapiste"}}})}
                              :toimituksenTila "Hakemus"
                              :kayttotapaus "Lupapiste kiinteist\u00f6toimitus"} ; LPK-3295 kayttotapaus from schema update
                             main-property-canon
                             details))]
    {:Kiinteistotoimitus
     {:featureMembers
      (reduce (fn [acc details]
                (let [op-name  (:name details)
                      ops      (get acc op-name [])
                      toimitus (toimitus-fn details)]
                  (assoc acc op-name (conj ops toimitus))))
              {}
              op-details)}}))

(defmethod canonical-common/application->canonical :KT [application lang]
  (kiinteistotoimitus-canonical application lang))
