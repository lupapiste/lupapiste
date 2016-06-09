(ns lupapalvelu.attachment-type
  (:refer-clojure :exclude [contains?])
  (:require [lupapiste-commons.attachment-types :as attachment-types]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :refer [fn->>] :as util]
            [sade.strings :as ss]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema AttachmentType
  {:type-id     sc/Keyword
   :type-group  sc/Keyword
   :metadata   {(sc/optional-key :permitType)     sc/Keyword
                :operation-specific               sc/Bool
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
   {:type-id :muu              :type-group :muut}])

(defn- for-operations [attachment-type]
  (cond-> #{}
    (contains? foreman-application-types attachment-type) (conj :tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen)))

(def- operation-specific-types
  [{:type-id :pohjapiirros       :type-group :paapiirustus}
   {:type-id :leikkauspiirros    :type-group :paapiirustus}
   {:type-id :julkisivupiirros   :type-group :paapiirustus}
   {:type-id :yhdistelmapiirros  :type-group :paapiirustus}
   {:type-id :erityissuunnitelma :type-group :rakentamisen_aikaiset}
   {:type-id :energiatodistus    :type-group :muut}
   {:type-id :korjausrakentamisen_energiaselvitys :type-group :muut}
   {:type-id :rakennuksen_tietomalli_BIM :type-group :muut}
   {:type-id :pohjapiirustus     :type-group :paapiirustus}
   {:type-id :leikkauspiirustus  :type-group :paapiirustus}
   {:type-id :julkisivupiirustus :type-group :paapiirustus}
   {:type-id :muu_paapiirustus   :type-group :paapiirustus}
   {:type-id :energiatodistus    :type-group :energiatodistus}
   {:type-id :rakennuksen_tietomalli_BIM :type-group :tietomallit}])

(defn attachment-type
  ([{type-group :type-group type-id :type-id :as attachment-type}]
   (->> (update attachment-type :metadata util/assoc-when
                :operation-specific (contains? operation-specific-types attachment-type)
                :for-operations     (not-empty (for-operations attachment-type)))
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
