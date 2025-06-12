(ns lupapalvelu.document.approval
  "Documents can be approved/rejected as a whole are by groups. When a document is
  approved/rejected, all the document groups are implicitly affected, as well. Similarly,
  if the user edits any document field after a document approval/rejection, it changes the
  status of the encompassing group (and the document).

  In mongo, the approval information is stored under the `:meta` key. The information as
  itself is typically not useful and it must be processed with `resolve-approvals`, so
  the implicit status changes are included.

  The frontend-friendly approval resolution maps are created by `applicationApprovals` and
  `documentApprovals`."
  (:require [clojure.set :refer [rename-keys]]
            [clojure.walk :refer [keywordize-keys]]
            [lupapalvelu.action :refer [update-application]]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.tools :as tools]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn get-approval
  "Returns document's approval data. Nil if not found."
  [document]
  (not-empty (get-in document [:meta :_approved])))

(defn ->approved
  "Approval meta data model. To be used within with-timestamp."
  [status user]
  {:value status
   :user (select-keys user [:id :firstName :lastName])
   :timestamp (model/current-timestamp)})

(defn approvable?
  "True if the target element schema has `:approvable` set to true."
  ([document] (approvable? document nil nil))
  ([document path] (approvable? document nil path))
  ([document schema path]
   (boolean (let [schema (or schema (model/get-document-schema document))]
              (if (seq path)
                (let [schema-body (:body schema)
                      str-path    (map #(if (keyword? %) (name %) %) path)
                      element     (keywordize-keys (model/find-by-name schema-body str-path))]
                  (:approvable element))
                (get-in schema [:info :approvable]))))))

(defn validate-approvability
  "Pre-checker document/group approval/rejection."
  [{{:keys [doc path collection]} :data application :application}]
  (when (and doc collection)
    (let [path-v (if (ss/blank? path) [] (ss/split path #"\."))
          document (tools/by-id application collection doc)]
      (if document
        (when-not (approvable? document path-v)
          (fail :error.document-not-approvable))
       (fail :error.document-not-found)))))

(defn ->approval-mongo-model
  "Creates a mongo update map of approval data.
   To be used within model/with-timestamp. Does not overwrite the rejection note."
  [path approval]
  (let [mongo-path (if (ss/blank? path) "documents.$.meta._approved" (str "documents.$.meta." path "._approved"))
        approval-pairs (map (fn [[k v]]
                              [(format "%s.%s" mongo-path (name k)) v])
                            approval)]
    {$set (into {:modified (model/current-timestamp)} approval-pairs)}))

(declare documentApprovals)

(defn- update-approval
  "Upserts document approval data and returns up-to-date `documentApprovals` result that can
  be passed to the frontend."
  [{{:keys [doc path]} :data
    :keys              [created application]
    :as                command}
   approval-data]
  (model/with-timestamp created
    (update-application
      command
      {:documents {$elemMatch {:id doc}}}
      (->approval-mongo-model path approval-data))
    (-> (util/find-by-id doc (:documents application))
        (update-in (if (ss/blank? path)
                     [:meta :_approved]
                     (util/split-kw-path (util/kw-path :meta path :_approved)))
                   merge approval-data)
        documentApprovals)))

(defn approve
  "Approve/reject document or group. Returns `documentApprovals` result."
  ([command status]
   (approve command status nil))
  ([{:keys [user created] :as command} status extra]
   (update-approval command (model/with-timestamp created (merge (->approved status user)
                                                                 extra)))))

(defn set-rejection-note
  "Set the rejection note text for a document or group. Returns `documentApprovals` result."
  [command note]
  (update-approval command {:note note}))


(defn- map-paths
  "Utility for distilling and transforming map structures (forms where all the branches are
  maps). `m` is the target structure, `leaf-pred` returns true if the currently visited
  node is a leaf and `value-fn` returns the transformed leaf. `value-fn` is only called
  for nodes accepted by `leaf-pred`. Both `leaf-pred` and `value-fn` take three arguments:
  `path` is a key-path for the current branch, not including the key for the current
  node. `k` is node key and `v` is the node value. The result is a list of key-path -
  value pairs.

  Simple example: lengths and paths of `:word` fields.

  > (map-paths {:one {:word \"one\"}
              :two {:three {:nope \"nope\"}
                    :four {:foo \"bar\"
                           :word \"four\"}}
              :five \"?\"}
             #(:word %3)
             #(count (:word %3)))
  ([(:one) 3] [(:two :four) 4])"
  ([m leaf-pred value-fn path]
   (some->> (not-empty m)
            (mapcat (fn [[k v]]
                      (cond
                        (leaf-pred path k v)
                        [[(concat path [k]) (value-fn path k v)]]

                        (map? v)
                        (map-paths v leaf-pred value-fn (concat path [k])))))
            seq))
  ([m leaf-pred value-fn]
   (map-paths m leaf-pred value-fn [])))

(defn- base-groups
  "Initializes group approvals by creating empty placeholders with zero timestamps. Only
  those groups that have data are included (otherwise repeating groups would be
  infinite)."
  [document]
  (let [schema (model/get-document-schema document)
        ks     (->> (map-paths (:data document)
                               (fn [path k _]
                                 (approvable? document schema (concat path [k])))
                               (fn [path k v]
                                 (let [path                     (concat path [k])
                                       {:keys [type repeating]} (->> (map name path)
                                                                     (model/find-by-name (:body schema)))]
                                   (when (and (util/=as-kw type :group) repeating)
                                     (keys v)))))
                    (mapcat (fn [[path v]]
                              (if v
                                (map #(concat path [%]) v)
                                [path]))))]
    (not-empty (zipmap ks (cycle [{:timestamp 0}])))))

(defn- prepare-document-approvals
  "Packs document approvals into a format that is easier to analyze in the next
  steps (`resolve--changed` and `resolve--override`)."
  [document]
  (some->> (map-paths (:meta document)
                      (fn [_ k _] (= :_approved k))
                      (fn [_ _ v]
                        (update (select-keys v [:timestamp :value :note :user])
                                :user select-keys [:firstName :lastName])))
           seq
           (map (fn [[path v]]
                  [(vec (butlast path)) v]))
           (into {})
           (merge (base-groups document))
           (sort-by (comp vec key))
           reverse))

(defn- prefixed?
  "True if `path` begins with `prefix`."
  [prefix path]
  (= (take (count prefix) path) prefix))

(defn- latest-modification
  "The biggest timestamp in `ts-paths` (path - timestamp pairs), where path starts with
  `path-prefix`."
  [path-prefix ts-paths]
  (some->> ts-paths
           (keep (fn [[path ts]]
                   (when (prefixed? path-prefix path)
                     ts)))
           seq
           (apply max)))

(defn- resolve--changed
  "Whether the fields have been edited since approval. "
  [document]
  (loop [[ax & axs] (prepare-document-approvals document)
         data-ts    (map-paths (:data document)
                               (fn [_ _ v] (:modified v))
                               (fn [_ _ v] (:modified v)))
         result     []]
    (if-not ax
      (not-empty result)
      (let [[path {:keys [timestamp value]
                   :as   axm}] ax
            timestamp          (or timestamp 0)
            ts                 (latest-modification path data-ts)
            changed            (when (and ts (> ts timestamp))
                                 ts)]
        (recur axs
               (cond->> (remove #(prefixed? path (first %)) data-ts)
                 ;; if the approved group is not changed, its timestamp can be omitted
                 ;; from the future rounds. This supports the use case, where an approved
                 ;; group implicitly "reapproves" the document.
                 (or changed (not= value "approved"))
                 (cons [path (or changed timestamp)]))
               (cons [path (util/assoc-when axm
                                            (if (zero? timestamp) :timestamp :changed)
                                            changed)]
                     result))))))

(defn- resolve--override
  "Approval overrides."
  [approvals]
  (loop [[ax & axs] approvals
         result     approvals]
    (if ax
      (->> result
           (map (fn [[path {:keys [timestamp changed override] :as m}]]
                  (let [[prefix {a-override :override
                                 a-ts       :timestamp
                                 value      :value}] ax]
                    [path (cond-> m
                            (and (not override)
                                 (not a-override)
                                 value
                                 (prefixed? prefix path)
                                 (< (or changed timestamp 0) (or a-ts 0)))
                            (assoc :override value))])))
           (recur axs))
      (into {} result))))

(defn- merge-updates
  "We not consider values nor schemas and just merge the `updates` blindly."
  [document updates modified]
  (update document :data
          (fn [data]
            (reduce (fn [acc [k v]]
                      (assoc-in acc
                                (util/split-kw-path k)
                                {:value v :modified modified}))
                    data
                    updates))))

(defn resolve-approvals
  "Resolve the current document (and group) approvals. The optional `updates` (list of
  string-path - value pairs) are included in the document (with `modified` timestamp) if
  given."
  ([document]
   (some-> document resolve--changed resolve--override))
  ([document updates modified]
   (resolve-approvals (merge-updates document updates modified)))
  ([document updates]
   (resolve-approvals document updates (now))))

(defn approved?
  "True only when the document is explicitly approved AND the approval status is still
  up-to-date."
  [document]
  (boolean (when-let [{:keys [value changed]} (some-> (resolve-approvals document)
                                                      (get []))]
             (and (= value "approved") (not changed)))))

(defn approval-value [document]
  (some-> (resolve-approvals document)
          (get [])
          :value))

(defn rejected?
  "True when the document is explicitly rejected. Unlike `approved?` it does not matter
  whether the approval status is strictly up-to-date. In other words, the document is
  deemed rejected until it is explicitly approved."
  [document]
  (boolean (some-> (approval-value document)
                   (= "rejected"))))

(defn locked?
  "Locked document cannot be edited but can be approved or rejected."
  [document]
  (boolean (some-> (approval-value document)
                   (= "locked"))))

(defn- approvals->js
    "JavaScript-friendly `approvals` map (or nil). Keys are kw-paths (`[]` -> `_root`)."
  [approvals]
  (some->> (rename-keys approvals {[] :_root})
           not-empty
           (util/map-keys util/kw-path)))

(def documentApprovals (comp approvals->js resolve-approvals))

(defn applicationApprovals
  "Approval map for every applicable `application` document. For the frontend's (JavaScript)
  benefit, the keys for the document maps are kw-paths (`[]` -> `_root`). If `document-id`
  is given, the map consists only of that document."
  ([application document-id]
   (into {}
         (for [doc   (cond->> (:documents application)
                       document-id (filter #(= document-id (:id %))))
               :let  [m (documentApprovals doc)]
               :when m]
           [(:id doc) m])))
  ([application] (applicationApprovals application nil)))
