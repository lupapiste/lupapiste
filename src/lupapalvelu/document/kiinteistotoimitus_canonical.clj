(ns lupapalvelu.document.kiinteistotoimitus-canonical
  (:require [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.document.tools :as tools ]
            [lupapalvelu.permit :as permit]
            [sade.util :as util]
            [sade.strings :as str]))

(defn- operation-name
  "Operation name to schema element name.
  Special cases are handled explcitly, others are converted:
  'this-is-operation' -> :ThisIsOperation"
  [name]
  (when name
    (let [specials {"lohkominen-tonttijako" :Lohkominen
                    "lohkominen-ohjeellinen" :Lohkominen}
          name->xml (fn [n]
                      (let [parts (str/split n #"-")]
                        (->> parts (map str/capitalize) str/join keyword)))]
      (or (get specials name) (name->xml name)))))

(defmulti kiinteistonmuodostus-details (fn [name _] name))

(defmethod kiinteistonmuodostus-details :default
  [_ doc])

(defmethod kiinteistonmuodostus-details "lohkominen-tonttijako"
  [_ doc]
  {:lohkomisenTyyppi "Tonttijaon mukainen tontti"})

(defmethod kiinteistonmuodostus-details "lohkominen-ohjeellinen"
  [_ doc]
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
  [{:name :KiinteistonMaaritys
    :details {:selvitettavaAsia (:rajankayntiTyyppi data)
              :kuvaus (:kuvaus data)}}])

(defn rt-details
  "All the rasitetoimitus operations are combined into one Rasitetoimitus,
  with kayttooikeustieto sequence."
  [docs from-property]
  (let [rt-docs (canonical-common/schema-info-filter docs :name #{"rasitetoimitus"})
        to-properties (map #(-> % :data :kiinteisto :kiinteistoTunnus)
                           (canonical-common/schema-info-filter docs :name #{"secondary-kiinteistot"}))]
    {:name :Rasitetoimitus
     :details {:kayttooikeustieto
               (flatten (for [doc rt-docs
                              :let [group (-> doc :data :rasitetoimitus)
                                    date  (-> group :paattymispvm util/to-xml-date-from-string)]]
                          (map (fn [p] {:KayttoOikeus
                                        (merge {:kayttooikeuslaji (:kayttooikeuslaji group)
                                                :kayttaja p
                                                :antaja from-property}
                                               (when date
                                                 {:paattymispvm date})
                                               {:valiaikainenKytkin (not (str/blank? date))})})
                               to-properties)))}}))


(defn kiinteistotoimitus-canonical [application lang]
  (let [app (tools/unwrapped application)
        docs  (canonical-common/documents-without-blanks app)
        property-id (:propertyId application)
        op-docs (canonical-common/schema-info-filter docs :name #{"kiinteistonmuodostus" "rajankaynti"})
        op-details (concat (map operation-details op-docs) [(rt-details docs property-id)])
        parties (canonical-common/process-parties docs lang)
        [{{property :kiinteisto} :data}] (canonical-common/schema-info-filter docs :name "kiinteisto")
        toimitus-fn (fn [{:keys [:name :details]}]
                      (merge {:toimituksenTiedottieto
                              {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
                              :toimitushakemustieto
                              {:Toimitushakemus
                               {:osapuolitieto parties
                                :sijaintitieto (canonical-common/get-sijaintitieto application)
                                :kiinteistotieto {:Kiinteisto {:kiinteistotunnus property-id}}
                                :maaraAlatieto  (when-let [mat (canonical-common/maaraalatunnus app property)]
                                                  {:MaaraAla {:maaraAlatunnus mat}})
                                :tilatieto (canonical-common/application-state app)}}
                              :toimituksenTila "Hakemus"}
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
