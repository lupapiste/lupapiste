(ns lupapalvelu.attachment.type
  (:refer-clojure :exclude [contains?])
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [lupapiste-commons.attachment-types :as attachment-types]
            [lupapalvelu.attachment.type-settings-schemas :as att-schemas]
            [lupapalvelu.operations :as operations]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :refer [fn-> fn->>] :as util]
            [schema.coerce :as coerce]
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

(defn equals?
  "Compares AttachmentType-like maps.

   - The type maps must have type-id and type-group keys having string or keyword values.
   - Types are equal when keywordized type-id and type-group tuples have equal values. Metadata map is ignored

   See tests for usage examples."
  [& types]
  (boolean (and (:type-id (first types))
                (:type-group (first types))
                (->> (map (juxt (comp keyword :type-id) (comp keyword :type-group)) types)
                     (apply =)))))

(defn contains? [attachment-type-coll attachment-type]
  (boolean (some (partial equals? attachment-type) attachment-type-coll)))

(def osapuolet attachment-types/osapuolet-v2)

(defn contains? [attachment-type-coll attachment-type]
  (boolean (some (partial equals? attachment-type) attachment-type-coll)))

(def- foreman-application-types
  [{:type-id :tutkintotodistus :type-group :osapuolet}
   {:type-id :patevyystodistus :type-group :osapuolet}
   {:type-id :cv :type-group :osapuolet}
   {:type-id :valtakirja :type-group :hakija}
   {:type-id :muu :type-group :muut}
   {:type-id :paatos :type-group :paatoksenteko}
   {:type-id :paatosote :type-group :paatoksenteko}])

(defn- for-operations [attachment-type]
  (when
    (contains? foreman-application-types attachment-type) #{:tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen}))

(def- default-grouping (util/read-edn-resource "attachments/default-grouping.edn"))

(def- type-grouping ;; Force array-map to ensure fixed order of the type groups
  (array-map {:type-id :aitapiirustus :type-group :paapiirustus} :paapiirustus
             {:type-id :asemapiirros :type-group :paapiirustus} :paapiirustus
             {:type-id :pohjapiirustus :type-group :paapiirustus} :paapiirustus
             {:type-id :leikkauspiirustus :type-group :paapiirustus} :paapiirustus
             {:type-id :julkisivupiirustus :type-group :paapiirustus} :paapiirustus
             {:type-id :muu_paapiirustus :type-group :paapiirustus} :paapiirustus

             {:type-id :iv_suunnitelma :type-group :erityissuunnitelmat} :iv_suunnitelma

             {:type-id :kvv_suunnitelma :type-group :erityissuunnitelmat} :kvv_suunnitelma
             {:type-id :lammityslaitesuunnitelma :type-group :erityissuunnitelmat} :kvv_suunnitelma

             {:type-id :rakennesuunnitelma :type-group :erityissuunnitelmat} :rakennesuunnitelma
             {:type-id :kalliorakentamistekninen_suunnitelma :type-group :erityissuunnitelmat} :rakennesuunnitelma
             {:type-id :pohjarakennesuunnitelma :type-group :erityissuunnitelmat} :rakennesuunnitelma
             {:type-id :pohjaveden_hallintasuunnitelma :type-group :erityissuunnitelmat} :rakennesuunnitelma))

(def content-mapping (util/read-edn-resource "attachments/contents.edn"))

(def other-type-group :other)
(def type-groups (-> (vals type-grouping) distinct (concat [other-type-group])))

(defn attachment-type
  ([{type-group :type-group type-id :type-id}]
   (let [attachment-type {:type-group (keyword type-group) :type-id (keyword type-id)}
         grouping (some-> (util/find-first (fn-> val (contains? attachment-type)) default-grouping) key)]
     (->> (update attachment-type :metadata util/assoc-when
                  :grouping (if (= :multioperation grouping) :operation grouping)
                  :multioperation (= :multioperation grouping)
                  :contents (not-empty (content-mapping (select-keys attachment-type [:type-id :type-group])))
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

(defn plain-kv-pair-vector->tuple-vector
  "Converts lupapiste commons attachment format [group-id-1 [attachment-id ...] group-id-N [attachment-id ...]]
  into tuple format [[group-id-1 [attachment-id ...]] [group-id-N [attachment-id ...]]]"
  [data]
  (mapv vec (partition 2 data)))

(defn plain-kv-pair-vector->attachment-type-array [type-vector]
  (->> (partition 2 type-vector)
       (mapcat (fn [[type-group type-ids]]
                 (map (partial attachment-type type-group) type-ids)))))

(defn plain-kv-pair-vector->map [type-vector]
  (apply hash-map type-vector))

(defn attachment-map->tuple-vector
  "Convert map representation of attachments into list of vector tuples, where first item is group-id
  and second item is vector of attachment type-ids."
  [data]
  (mapv (fn [[group-id attachment-ids]] [group-id attachment-ids]) data))

(defn attachment-map->attachment-type-array [type-map]
  (->> type-map
       (mapcat (fn [[type-group type-ids]]
                 (map (partial attachment-type type-group) type-ids)))))

(defn attachment-tuple-vector->map
  "Convert collection of tuple vectors [group-id [attachment-id ...]] into map where group-id is key
   and list of attachment-id is value if vector has duplicate group-id, their values are merged."
  [data]
  (letfn [(ensure-keyword-collection [data]
            (cond
              (string? data) [(keyword data)]
              (keyword? data) [data]
              :else (map keyword data)))]
    (reduce (fn [acc [group-id attachments-ids]]
              (update acc (keyword group-id) (comp distinct concat) (ensure-keyword-collection attachments-ids)))
            {}
            data)))

(sc/defn attachment-type-array->map :- att-schemas/AttachmentTypeMap
  [types :- [AttachmentType]]
  (letfn [(->type-id-list [[_ types]] (mapv :type-id types))]
    (->> types
         (group-by :type-group)
         (map (juxt key ->type-id-list))
         (into {}))))

(def attachment-types-by-permit-type
  "Attachment types associated to permit-types"
  (->> {:R    attachment-types/Rakennusluvat-v2
        :P    attachment-types/Rakennusluvat-v2
        :YA   attachment-types/YleistenAlueidenLuvat-v2
        :A    attachment-types/Allu
        :YI   attachment-types/Ymparisto-types-v2
        :YL   attachment-types/Ymparisto-types-v2
        :YM   attachment-types/Ymparisto-types-v2
        :VVVL attachment-types/Ymparisto-types-v2
        :MAL  attachment-types/Ymparisto-types-v2
        :MM   attachment-types/Kiinteistotoimitus
        :KT   attachment-types/Kiinteistotoimitus
        :ARK  attachment-types/Rakennusluvat-v2}
       (map (juxt key ->attachment-type-array))
       (into {})))

(def attachment-types-by-permit-type-as-attachment-map
  (->> attachment-types-by-permit-type
       (map (juxt key (comp attachment-type-array->map val)))
       (into {})))

(def attachment-types-as-type-array
  (->> attachment-types-by-permit-type
       (mapcat val)
       distinct))

(def all-attachment-type-ids
  (->> (partition 2 attachment-types/all-attachment-types)
       (mapcat second)
       (set)))

(def ^:private all-types-as-attachment-type-array
  (->> attachment-types/all-attachment-types
       (partition 2)
       (mapcat (fn [[group-id list-of-attachment-types]]
                 (map (partial attachment-type group-id) list-of-attachment-types)))))

(def all-attachment-type-groups
  (->> all-types-as-attachment-type-array
       (map :type-group)
       (set)))


(defn attachment-type-for-appeal [appeal-type]
  (case (keyword appeal-type)
    :appealVerdict {:type-group :paatoksenteko
                    :type-id    :paatos}
    :appeal {:type-group :muutoksenhaku
             :type-id    :valitus}
    :rectification {:type-group :muutoksenhaku
                    :type-id    :oikaisuvaatimus}))

(defn collect-applicable-settings-nodes-for-operation
  [{:keys [defaults]
    :as   organization-attachment-settings}
   operation-name]
  (let [{:keys [permit-type]
         :as   operation-node} (get-in organization-attachment-settings [:operation-nodes (keyword operation-name)])
        permit-type-node (get-in organization-attachment-settings [:permit-type-nodes (keyword permit-type)])
        defaults-allowed-types (get-in defaults [:allowed-attachments permit-type])
        default-node-for-op {:allowed-attachments {:mode :set :types defaults-allowed-types}}]
    (into [] (keep identity [default-node-for-op permit-type-node operation-node]))))

(defn apply-attachment-note-setup
  [accumulated-state {:keys [mode] :as inheritable-attachment-type-data}]
  (case mode
    :inherit accumulated-state
    :set (select-keys inheritable-attachment-type-data [:types])
    (throw (ex-info "unsupported attachment inheritance mode" mode))))

(defn- get-organizations-attachment-types-for-operation-from-config
  [organization-attachment-settings operation-name]
  (letfn [(node-accumulator [acc {:keys [allowed-attachments]}]
            (update acc :allowed-attachments #(apply-attachment-note-setup % allowed-attachments)))
          (->attachment-type-array [accumulated-settings]
            (-> accumulated-settings
                (get-in [:allowed-attachments :types])
                attachment-map->attachment-type-array))]
    (->> (collect-applicable-settings-nodes-for-operation organization-attachment-settings operation-name)
         (reduce node-accumulator {:allowed-attachments {}})
         ->attachment-type-array)))

(defn get-organizations-attachment-types-for-operations-with-hardcoded-override
  [operation-name]
  (not-empty
    (filter
      (fn [att-type]
        (let [hardcoded-for-operation-set (get-in att-type [:metadata :for-operations] #{})
              operation (keyword operation-name)]
          (operation hardcoded-for-operation-set)))
      attachment-types-as-type-array)))

(defn get-organizations-attachment-types-for-operation
  "Returns allowed attachments for an operation `operation-name` from attachment settings `organization-attachment-settings`.

   Optional flags:
   :operation-baseline-only? true: Get hardcoded permit-type or for-operation metadata based default for the operation."
  [organization-attachment-settings
   operation-name
   & {:keys [operation-baseline-only?]}]
  (let [harcoded-types-for-operation (get-organizations-attachment-types-for-operations-with-hardcoded-override operation-name)
        permit-type (get-in organization-attachment-settings [:operation-nodes (keyword operation-name) :permit-type])
        hardcoded-type-for-op-permit-type (when permit-type (->> attachment-types-by-permit-type
                                                                 permit-type
                                                                 (map #(select-keys % [:type-id :type-group]))))]
    (cond (seq harcoded-types-for-operation) harcoded-types-for-operation
          operation-baseline-only? (or hardcoded-type-for-op-permit-type [])
          :else (get-organizations-attachment-types-for-operation-from-config organization-attachment-settings operation-name))))

(defn- attachment-type->localized-text
  [{:keys [type-group type-id]} lang]
  (i18n/localize lang :attachmentType type-group type-id))

(defn- ymp-attachment-type-sort-fn
  "Attachments for environmental permits are first sorted by specific group order
  defined in lupapiste-commons. Types under each group are then sorted alphabetically."
  [lang]
  (let [group-ordinal   #(-> % :type-group attachment-types/ymparisto-type-group->ordinal)
        attachment-name #(-> % (attachment-type->localized-text lang) ss/lower-case)]
    (juxt group-ordinal attachment-name)))

(defn sort-attachment-types
  [attachment-types permit-type lang]
  (cond->> attachment-types
    (#{:YI :YL :YM :VVVL :MAL} (keyword permit-type))
    (sort-by (ymp-attachment-type-sort-fn lang))))

(sc/defn ^:always-validate get-attachment-types-for-application :- [AttachmentType]
  "Get all types related to primary or secondary operations is of application."
  [organization-attachment-settings :- att-schemas/ExpandedOperationsAttachmentSettings
   {:keys [primaryOperation secondaryOperations] :as application}]
  {:pre [application]}
  (->> (cons primaryOperation secondaryOperations)
           (map :name)
           (mapcat (partial get-organizations-attachment-types-for-operation organization-attachment-settings))
           distinct))

(sc/defn ^:always-validate get-all-allowed-attachment-types :- [AttachmentType]
  "Get all types related organization."
  [organization-attachment-settings :- att-schemas/ExpandedOperationsAttachmentSettings]
  (->> (keys (organization-attachment-settings :operation-nodes))
       (mapcat (partial get-organizations-attachment-types-for-operation organization-attachment-settings))
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

(sc/defn ^:always-validate allowed-attachment-type-for-application?
  "Check if attachment type is in allowed in organization settings to the operation."
  [operations-attachment-settings :- att-schemas/ExpandedOperationsAttachmentSettings
   attachment-type
   application]
  (let [allowed-types (get-attachment-types-for-application operations-attachment-settings application)]
    (contains? allowed-types attachment-type)))

(defn tag-by-type [{:keys [type]}]
  (get type-grouping
       (-> (select-keys type [:type-group :type-id])
           (util/convert-values keyword))))

(defn resolve-type
  "Resolves :type-group and :type-id from given permit-type and type-id.
  Arguments can be either keywords or strings."
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
              #(assoc %1 (ss/lower-case (i18n/localize %2 loc-key)) attachment-type)
              acc
              [:fi :sv])))
        {}
        (get attachment-types-by-permit-type (keyword permit-type))))))

(defn localisation->attachment-type
  "Try to map attachemt type on based of localized string"
  [permit-type localisation]
  (get (localisation-to-attachment-type-map permit-type) (ss/lower-case localisation)))

(defn- default-operations-node [permit-type]
  {:permit-type         permit-type
   :allowed-attachments {:mode  :inherit
                         :types {}}
   :default-attachments {}})

(def- default-permit-type-node
  {:allowed-attachments {:mode  :inherit
                         :types {}}})

(defn- expand-operations [settings
                          operations-per-permit-type
                          default-attachments
                          tos-functions
                          default-attachments-mandatory]
  (->> operations-per-permit-type
       (mapcat (fn [[permit-type operations]]
                 (map
                   (fn [operation-name]
                     (let [op-key (keyword operation-name)]
                       [operation-name
                        (util/strip-nils
                          (merge
                            (default-operations-node permit-type)
                            {:default-attachments            (attachment-tuple-vector->map (get default-attachments
                                                                                                op-key []))
                             :tos-function                   (get tos-functions op-key)
                             :default-attachments-mandatory? (some (partial = (name operation-name))
                                                                   default-attachments-mandatory)
                             :deprecated?                    (operations/get-operation-metadata operation-name
                                                                                                :hidden)}
                           (get-in settings [:operation-nodes (keyword operation-name)])))]))
                   operations)))
       (into {})))

(defn- expand-permit-types [settings permit-types]
  (->> permit-types
       (map (fn [permit-type]
              (hash-map permit-type
                        (merge
                          default-permit-type-node
                          (get-in settings [:permit-type-nodes permit-type])))))
       (into {})))

(defn organization->organization-attachment-settings
  "Returns a map with keys :defaults :permit-type-nodes and :operations-nodes.

   :operation-nodes contains all operation organization have access. If there are no organization specific settings
    for an operation, defaults are used.
   :permit-type-nodes contains settings for all permit types organization has access.
    If there are no organization specific settings for an operation, defaults are used"
  [{scope                     :scope
    settings                  :operations-attachment-settings
    ;; NOTE
    ;; It would be better if default attachments and all attachments are under the same
    ;; consistent data structure. This is not the case at the moment, so we combine them here.
    default-attachments       :operations-attachments
    permanent-archive-enabled :permanent-archive-enabled
    digitizer-tools-enabled   :digitizer-tools-enabled
    tos-functions             :operations-tos-functions
    default-attachments-mandatory             :default-attachments-mandatory
    :or {settings {} permanent-archive-enabled false digitizer-tools-enabled false}}
   & {:keys [types-layout]
      :or   {types-layout :map}}]
  (let [permit-types (->> scope
                          (map (comp keyword :permitType))
                          (concat (when (and permanent-archive-enabled digitizer-tools-enabled) [:ARK]))
                          distinct)
        allowed-attachments-defaults (select-keys attachment-types-by-permit-type-as-attachment-map permit-types)

        ;; NOTE: all operations are returned here. If we return only those operations organization
        ;; have selected there will be problems when there are old applications for an operation that
        ;; is no longer used (see e.g. set-attachment-type command in lupapiste.attachment-api).
        ;; Possible operation filtering should be done elsewhere!
        operations-per-permit-type-map (select-keys (operations/resolve-operation-names-by-permit-type true)
                                                    permit-types)

        expanded-operations (expand-operations settings
                                               operations-per-permit-type-map
                                               default-attachments
                                               tos-functions
                                               default-attachments-mandatory)
        expanded-permit-types (expand-permit-types settings permit-types)

        coerce-schema (coerce/coercer att-schemas/ExpandedOperationsAttachmentSettings coerce/json-coercion-matcher)
        response {:defaults          {:allowed-attachments allowed-attachments-defaults}
                  :permit-type-nodes expanded-permit-types
                  :operation-nodes   expanded-operations}]

    (coerce-schema response)))

;;
;; UTILS
;;

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
                   ss/upper-case
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
