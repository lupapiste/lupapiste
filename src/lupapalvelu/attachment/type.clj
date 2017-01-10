(ns lupapalvelu.attachment.type
  (:refer-clojure :exclude [contains?])
  (:require [lupapiste-commons.attachment-types :as attachment-types]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :refer [fn-> fn->>] :as util]
            [sade.strings :as ss]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema AttachmentType
  {:type-id                    sc/Keyword
   :type-group                 sc/Keyword
   (sc/optional-key :metadata) {(sc/optional-key :permitType)     sc/Keyword
                                (sc/optional-key :grouping)       sc/Keyword
                                (sc/optional-key :contents)       [sc/Keyword]
                                (sc/optional-key :for-operations) #{(apply sc/enum (keys op/operations))}}})

(def osapuolet attachment-types/osapuolet-v2)

(def- attachment-types-by-permit-type-unevaluated
  {:R    'attachment-types/Rakennusluvat-v2
   :P    'attachment-types/Rakennusluvat-v2
   :YA   'attachment-types/YleistenAlueidenLuvat-v2
   :YI   'attachment-types/Ymparistoilmoitukset
   :YL   'attachment-types/Ymparistolupa
   :YM   'attachment-types/MuutYmparistoluvat
   :VVVL 'attachment-types/Ymparistoilmoitukset
   :MAL  'attachment-types/Maa-ainesluvat
   :MM   'attachment-types/Kiinteistotoimitus
   :KT   'attachment-types/Kiinteistotoimitus})

(defn equals? [& types]
  (boolean (and (:type-id (first types))
                (:type-group (first types))
                (->> (map (juxt (comp keyword :type-id) (comp keyword :type-group)) types)
                     (apply =)))))

(defn contains? [attachment-type-coll attachment-type]
  (boolean (some (partial equals? attachment-type) attachment-type-coll)))

(def- foreman-application-types
  [{:type-id :tutkintotodistus :type-group :osapuolet}
   {:type-id :patevyystodistus :type-group :osapuolet}
   {:type-id :cv               :type-group :osapuolet}
   {:type-id :valtakirja       :type-group :hakija}
   {:type-id :muu              :type-group :muut}
   {:type-id :paatos           :type-group :paatoksenteko}
   {:type-id :paatosote        :type-group :paatoksenteko}])

(defn- for-operations [attachment-type]
  (cond-> #{}
    (contains? foreman-application-types attachment-type) (conj :tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen)))

(def- default-grouping (util/read-edn-resource "attachments/default-grouping.edn"))

(def- type-grouping ; Force array-map to ensure fixed order of the type groups
  (array-map {:type-id :aitapiirustus            :type-group :paapiirustus}        :paapiirustus
             {:type-id :asemapiirros             :type-group :paapiirustus}        :paapiirustus
             {:type-id :pohjapiirustus           :type-group :paapiirustus}        :paapiirustus
             {:type-id :leikkauspiirustus        :type-group :paapiirustus}        :paapiirustus
             {:type-id :julkisivupiirustus       :type-group :paapiirustus}        :paapiirustus
             {:type-id :muu_paapiirustus         :type-group :paapiirustus}        :paapiirustus

             {:type-id :iv_suunnitelma :type-group :erityissuunnitelmat} :iv_suunnitelma

             {:type-id :kvv_suunnitelma          :type-group :erityissuunnitelmat} :kvv_suunnitelma
             {:type-id :lammityslaitesuunnitelma :type-group :erityissuunnitelmat} :kvv_suunnitelma

             {:type-id :rakennesuunnitelma                   :type-group :erityissuunnitelmat} :rakennesuunnitelma
             {:type-id :kalliorakentamistekninen_suunnitelma :type-group :erityissuunnitelmat} :rakennesuunnitelma
             {:type-id :pohjarakennesuunnitelma              :type-group :erityissuunnitelmat} :rakennesuunnitelma
             {:type-id :pohjaveden_hallintasuunnitelma       :type-group :erityissuunnitelmat} :rakennesuunnitelma))

(def content-mapping (util/read-edn-resource "attachments/contents.edn"))

(def other-type-group :other)
(def type-groups (-> (vals type-grouping) distinct (concat [other-type-group])))

(defn attachment-type
  ([{type-group :type-group type-id :type-id :as attachment-type}]
   (->> (update attachment-type :metadata util/assoc-when
                :grouping       (some-> (util/find-first (fn-> val (contains? attachment-type)) default-grouping) key)
                :contents       (not-empty (content-mapping (select-keys attachment-type [:type-id :type-group])))
                :for-operations (not-empty (for-operations attachment-type)))
        (util/strip-nils)
        (sc/validate AttachmentType)))
  ([type-group type-id]
   (attachment-type {:type-group (keyword type-group) :type-id (keyword type-id)}))
  ([permit-type type-group type-id]
   (attachment-type {:type-id (keyword type-id) :type-group (keyword type-group) :metadata {:permitType (keyword permit-type)}})))

(defn- ->attachment-type-array [[permit-type attachment-types]]
  (->> (partition 2 attachment-types)
       (mapcat #(map (partial attachment-type permit-type (first %)) (second %)))))

(def attachment-types-by-permit-type
  (->> (eval attachment-types-by-permit-type-unevaluated)
       (map (juxt key ->attachment-type-array))
       (into {})))

(def all-attachment-type-ids
  (->> (mapcat val attachment-types-by-permit-type)
       (map :type-id)
       (set)))

(def all-attachment-type-groups
  (->> (mapcat val attachment-types-by-permit-type)
       (map :type-group)
       (set)))

(defn attachment-type-for-appeal [appeal-type]
  (case (keyword appeal-type)
    :appealVerdict  {:type-group :paatoksenteko
                     :type-id    :paatos}
    :appeal         {:type-group :muutoksenhaku
                     :type-id    :valitus}
    :rectification  {:type-group :muutoksenhaku
                     :type-id    :oikaisuvaatimus}))

(defn- attachment-types-by-operation [operation]
  (let [operation (keyword operation)
        types     (-> operation op/permit-type-of-operation keyword attachment-types-by-permit-type)]
    (or (not-empty (filter #(some-> (get-in % [:metadata :for-operations]) operation) types))
        types)))

(def get-attachment-types-for-operation (memoize attachment-types-by-operation))

(defn get-attachment-types-for-application
  [{:keys [permitType primaryOperation secondaryOperations] :as application}]
  {:pre [application]}
  (->> (cons primaryOperation secondaryOperations)
       (map :name)
       (mapcat get-attachment-types-for-operation)
       (distinct)))

(defn ->grouped-array [attachment-types]
  (->> (group-by :type-group attachment-types)
       (map (juxt key (fn->> val (map :type-id))))))

(defn parse-attachment-type [attachment-type]
  (if-let [match (re-find #"(.+)\.(.+)" (or attachment-type ""))]
    (let [[type-group type-id] (->> match (drop 1) (map keyword))]
      {:type-group type-group :type-id type-id})))

(defn allowed-attachment-types-contain? [allowed-types {:keys [type-group type-id]}]
  (let [type-group (keyword type-group)
        type-id (keyword type-id)]
    (if-let [types (some (fn [[group group-types]] (when (= (keyword group) type-group) group-types)) allowed-types)]
      (some #(= (keyword %) type-id) types))))

(defn allowed-attachment-type-for-application? [attachment-type application]
  {:pre [(map? attachment-type)]}
  (let [allowed-types (or (not-empty (get-attachment-types-for-application application))
                          (-> application :permitType keyword attachment-types-by-permit-type))]
    (contains? allowed-types attachment-type)))

(defn tag-by-type [{type :type :as attachment}]
  (get type-grouping
       (-> (select-keys type [:type-group :type-id])
           (util/convert-values keyword))))

;;
;; Helpers for reporting purposes
;;

(defn localised-attachments-by-permit-type [permit-type]
  (let [localize-attachment-section
        (fn [lang [title attachment-names]]
          [(i18n/localize lang (ss/join "." ["attachmentType" (name title) "_group_label"]))
           (reduce
            (fn [result attachment-name]
              (let [lockey                    (ss/join "." ["attachmentType" (name title) (name attachment-name)])
                    localized-attachment-name (i18n/localize lang lockey)]
                (conj
                 result
                 (ss/join \tab [(name attachment-name) localized-attachment-name]))))
             []
             attachment-names)])]
    (reduce
     (fn [accu lang]
       (assoc accu (keyword lang)
          (->> (get attachment-types-by-permit-type (keyword permit-type))
               (partition 2)
               (map (partial localize-attachment-section lang))
               vec)))
     {}
     ["fi" "sv"])))

(defn print-attachment-types-by-permit-type []
  (let [permit-types-with-names (into {}
                                      (for [[k v] attachment-types-by-permit-type-unevaluated]
                                        [k (name v)]))]
    (doseq [[permit-type permit-type-name] permit-types-with-names]
      (println permit-type-name)
      (doseq [[group-name types] (:fi (localised-attachments-by-permit-type permit-type))]
        (println "\t" group-name)
        (doseq [type types]
          (println "\t\t" type))))))
