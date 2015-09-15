(ns lupapalvelu.document.kiinteistotoimitus-canonical
  (:require [clojure.string :as str]
            [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.document.tools :as tools ]
            [lupapalvelu.permit :as permit]
            [sade.util :as util]))

(defn operation-name
  "Operation name to schema element name.
  Special cases are handled explcitly, others are converted:
  'this-is-operation' -> :ThisIsOperation"
  [name]
  (let [specials {"lohkominen-tonttijako" :Lohkominen
                  "lohkominen-ohjeellinen" :Lohkominen}
        name->xml (fn [n]
                    (let [parts (str/split n #"-")]
                      (->> parts (map str/capitalize) str/join keyword)))]
    (or (get specials name) (name->xml name))))

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
  (let [groups (-> doc :data :kiinteistonmuodostus vals)]
    (for [g groups
          :let [name (:kiinteistonmuodostusTyyppi g)]]
      {:name (operation-name name)
       :details (merge (kiinteistonmuodostus-details name doc)
                       {:kuvaus (:kuvaus g)})})))

(defmethod operation-details :rasitetoimitus
  [doc]
  (let [groups (-> doc :data :rasitetoimitus vals)]
    (for [g groups
          :let [date (-> g :paattymispvm util/to-xml-date-from-string)]]
      {:name :Rasitetoimitus
       :details {:kayttooikeustieto
                 {:KayttoOikeus
                  (merge (select-keys g [:kayttooikeuslaji :kayttaja :antaja])
                         (when date
                           {:paattymispvm date})
                         {:valiaikainenKytkin (str/blank? date)})}}})))

(defmethod operation-details :rajankaynti
  [{data :data}]
  [{:name :KiinteistonMaaritys
    :details {:selvitettavaAsia (:rajankayntiTyyppi data)
              :kuvaus (:kuvaus data)}}])

(defn kiinteistotoimitus-canonical [application lang]
  (let [app (tools/unwrapped application)
        docs  (canonical-common/documents-without-blanks app)
        op-docs (canonical-common/schema-info-filter docs :name #{"kiinteistonmuodostus" "rasitetoimitus" "rajankaynti"})
        parties (canonical-common/process-parties docs lang)
        [{{property :kiinteisto} :data}] (canonical-common/schema-info-filter docs :name "kiinteisto")
        toimitus-fn (fn [{:keys [:name :details]}]
                      (merge {:toimituksenTiedottieto
                              {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
                              :toimitushakemustieto
                              {:Toimitushakemus
                               {:osapuolitieto parties
                                :sijaintitieto (canonical-common/get-sijaintitieto application)
                                :kiinteistotieto {:Kiinteisto {:kiinteistotunnus (:propertyId application)}}
                                :maaraAlatieto  {:maaraAla {:maaraAlaTunnus (:maaraalaTunnus property)}}
                                :tilatieto (canonical-common/application-state app)}}
                              :toimituksenTila "Hakemus"}
                             details))]
    {:Kiinteistotoimitus
     {:featureMembers
      (reduce (fn [acc doc]
                (let [doc-ops (reduce (fn [all-details op-details]
                                        (let [op-name  (:name op-details)
                                              ops      (get all-details op-name (get acc op-name []))
                                              toimitus (toimitus-fn op-details)]
                                          (assoc all-details op-name (conj ops toimitus))
                                          ;;(conj all-details toimitus)
                                          ))
                                      {}
                                      (operation-details doc))]
                  (merge acc doc-ops)
                  ;;(concat acc doc-ops)
                  ))
              {}
              op-docs)}}))
