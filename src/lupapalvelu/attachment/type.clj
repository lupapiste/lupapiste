(ns lupapalvelu.attachment.type
  (:refer-clojure :exclude [contains?])
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [lupapiste-commons.attachment-types :as attachment-types]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.strings :as str]
            [sade.util :refer [fn-> fn->>] :as util]
            [schema.core :refer [defschema] :as sc]
            [swiss.arrows :refer :all]))

(defschema AttachmentType
  {:type-id                    sc/Keyword
   :type-group                 sc/Keyword
   (sc/optional-key :metadata) {(sc/optional-key :permitType)     sc/Keyword
                                (sc/optional-key :grouping)       sc/Keyword
                                (sc/optional-key :multioperation) sc/Bool
                                (sc/optional-key :contents)       [sc/Keyword]
                                (sc/optional-key :for-operations) #{(apply sc/enum (keys op/operations))}}})

(def osapuolet attachment-types/osapuolet-v2)

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
  ([{type-group :type-group type-id :type-id}]
   (let [attachment-type {:type-group (keyword type-group) :type-id (keyword type-id)}
         grouping (some-> (util/find-first (fn-> val (contains? attachment-type)) default-grouping) key)]
     (->> (update attachment-type :metadata util/assoc-when
                  :grouping       (if (= :multioperation grouping) :operation grouping)
                  :multioperation (= :multioperation grouping)
                  :contents       (not-empty (content-mapping (select-keys attachment-type [:type-id :type-group])))
                  :for-operations (not-empty (for-operations attachment-type)))
          (util/strip-nils)
          (sc/validate AttachmentType))))
  ([type-group type-id]
   (attachment-type {:type-group (keyword type-group) :type-id (keyword type-id)}))
  ([permit-type type-group type-id]
   (attachment-type {:type-id (keyword type-id) :type-group (keyword type-group) :metadata {:permitType (keyword permit-type)}})))

(defn operation-specific? [type]
  (let [{:keys [grouping multioperation]} (or (:metadata type) (:metadata (attachment-type type)))]
    (and (= :operation grouping) (not multioperation))))

(defn multioperation? [type]
  (:multioperation (or (:metadata type) (:metadata (attachment-type type)))))

(defn- ->attachment-type-array [[permit-type attachment-types]]
  (->> (partition 2 attachment-types)
       (mapcat #(map (partial attachment-type permit-type (first %)) (second %)))))

(def attachment-types-by-permit-type
  (->> {:R    attachment-types/Rakennusluvat-v2
        :P    attachment-types/Rakennusluvat-v2
        :YA   attachment-types/YleistenAlueidenLuvat-v2
        :A    attachment-types/Allu
        :YI   attachment-types/Ymparistoilmoitukset
        :YL   attachment-types/Ymparistolupa
        :YM   attachment-types/MuutYmparistoluvat
        :VVVL attachment-types/VesihuoltoVapautushakemukset
        :MAL  attachment-types/Maa-ainesluvat
        :MM   attachment-types/Kiinteistotoimitus
        :KT   attachment-types/Kiinteistotoimitus
        :ARK  attachment-types/Rakennusluvat-v2}
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
  [{:keys [primaryOperation secondaryOperations] :as application}]
  {:pre [application]}
  (->> (cons primaryOperation secondaryOperations)
       (map :name)
       (mapcat get-attachment-types-for-operation)
       (distinct)))

(defn ->grouped-array [attachment-types]
  (->> (group-by :type-group attachment-types)
       (map (juxt key (fn->> val (map :type-id))))))

(defn get-all-attachment-types-for-permit-type [permit-type]
  (attachment-types-by-permit-type permit-type))

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

(defn tag-by-type [{:keys [type]}]
  (get type-grouping
       (-> (select-keys type [:type-group :type-id])
           (util/convert-values keyword))))

(defn resolve-type
  "Resolves :type-group and :type-id from given permit-type and
  type-id. Arguments can be either keywords or strings."
  [permit-type type-id]
  (-<>> (attachment-types-by-permit-type (keyword permit-type))
        (util/find-by-key :type-id (keyword type-id))
        (select-keys <> [:type-group :type-id])))

(def localisation-to-attachment-type-map
  (memoize
    (fn [permit-type]
      (reduce
        (fn [acc {:keys [type-group type-id] :as attachment-type}]
          (let [loc-key (ss/join "." ["attachmentType" (name type-group) (name type-id)])]
            (reduce
              #(assoc %1 (str/lower-case (i18n/localize %2 loc-key)) attachment-type)
              acc
              [:fi :sv])))
        {}
        (get attachment-types-by-permit-type (keyword permit-type))))))

(defn localisation->attachment-type [permit-type localisation]
  (get (localisation-to-attachment-type-map permit-type) (str/lower-case localisation)))

;; Utils for printing out the attachment types and contents for zip import index sheet

(defn print-localized-attachment-types [permit-type lang]
  (->> ((keyword permit-type) attachment-types-by-permit-type)
       (map (fn [{:keys [type-group type-id]}]
              (i18n/localize (keyword lang) (ss/join "." ["attachmentType" (name type-group) (name type-id)]))))
       sort
       (map println)
       dorun))

(defn print-localized-attachment-types-grouped [permit-type lang]
  (->> ((keyword permit-type) attachment-types-by-permit-type)
       (partition-by :type-group)
       (map (fn [group]
              (->> (str "attachmentType." (name (:type-group (first group))) "._group_label")
                   (i18n/localize (keyword lang))
                   str/upper-case
                   println)
              (doseq [{:keys [type-group type-id]} group]
                (-> (i18n/localize (keyword lang) (ss/join "." ["attachmentType" (name type-group) (name type-id)]))
                    (str  ";" (name type-group) "." (name type-id))
                    println))))
       dorun))

;; cleaner output for list above
(defn print-localized-attachment-types-grouped-v2 [permit-type lang]
  (->> ((keyword permit-type) attachment-types-by-permit-type)
       (partition-by :type-group)
       (map (fn [group]
              (->> (str "attachmentType." (name (:type-group (first group))) "._group_label")
                   (i18n/localize (keyword lang))
                   println)
              (doseq [{:keys [type-group type-id]} group]
                (->>  (i18n/localize (keyword lang) (ss/join "." ["attachmentType" (name type-group) (name type-id)]))
                    (str \tab)
                    println))))
       dorun))

(defn print-localized-attachment-contents [lang]
  (->> (map second content-mapping)
       flatten
       dedupe
       (map #(i18n/localize (keyword lang) (str "attachments.contents." (name %))))
       sort
       (map println)
       dorun))
