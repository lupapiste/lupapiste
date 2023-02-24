(ns lupapalvelu.ui.auth-admin.automatic-assignments.state
  "Automatic assignment filters state management."
  (:require [clojure.string :refer [split-lines]]
            [lupapalvelu.automatic-assignment.schemas :as schemas]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]
            [schema.core :as sc]))

(defonce state* (atom {}))
;; AutomaticAssignments JS object that is given as parameter to the component.
(defonce automatic* (atom nil))

(defn state-cursor [field]
  (rum/cursor-in state* [(keyword field)]))

(def current-filter* (state-cursor :current-filter))
(def processed-current-filter* (state-cursor :processed-current-filter))
(def filters* (state-cursor :filters))
(def refresh* (state-cursor :refresh))

(defn dsptch [pathy & _] (flatten [pathy]))

(defmulti init-field dsptch)

(defmethod init-field :default
  [_ value]
  value)

(defmethod init-field [:criteria :reviews]
  [_ value]
  (some->> value (ss/join "\n")))

(defmethod init-field [:email :emails]
  [_ value]
  (some->> value (ss/join ", ")))

(defmulti process-field dsptch)

(defmethod process-field :default
  [_ value]
  value)

(defmethod process-field [:rank]
  [_ value]
  (js/parseInt value))

(defmethod process-field [:criteria :reviews]
  [_ value]
  (->> (split-lines value)
       (remove ss/blank?)
       (map ss/trim)))

(defmethod process-field [:email :emails]
  [_ value]
  (->> (ss/split (ss/lower-case value) #"[ ,;]+")
       (remove ss/blank?)
       (map ss/trim)))

(defmulti valid-field? dsptch)

(defmethod valid-field? :default
  [& _]
  true)

(defmethod valid-field? [:rank]
  [_ value]
  (or (integer? value) (util/int-string? value)))

(defmethod valid-field? [:email :emails]
  [path value]
  (every? js/util.isValidEmailAddress (process-field path value)))


(defn field-paths
  "Every path for individual fields in `m`.
  > (field-paths {:foo 10 :bar 4 :baz {:hii 8 :hoo {:dum []}}})
  ([:foo] [:bar] [:baz :hii] [:baz :hoo :dum])"
  ([m path]
   (reduce-kv (fn [acc k v]
             (if (map? v)
               (concat acc (field-paths v (conj path k)))
               (conj acc (conj path k))))
           []
           m))
  ([m]
   (field-paths m [])))

(defn traverse-fields
  "Returns list of path, value vectors. Each value is return value of `fun`. `fun` is a
  function that receives path and old value (value of the path in `m`) as params and
  returns new value."
  ([m fun]
   (map (fn [path]
          [path (fun path (get-in m path))])
        (field-paths m))))

(defn construct-fields
  "Traverses `m` fields and creates new map from the `fun` results."
  [m fun]
  (reduce (fn [acc [path value]]
            (assoc-in acc path value))
          {}
          (traverse-fields m fun)))

(defn best-value [k-or-path]
  (let [path (flatten [k-or-path])
        processed (some-> processed-current-filter* rum/react (get-in path))
        current   (some-> current-filter* rum/react (get-in path))]
    (if (or (nil? processed) (= :error processed))
      current
      processed)))

(defn refresh-filters []
  (swap! refresh* inc)
  (reset! filters* (when-let [automatic @automatic*]
                     (js->clj ((common/oget automatic "automaticAssignmentFilters"))
                              :keywordize-keys true))))

(defn filters []
  (and (rum/react refresh*) ;; Triggers refresh even if filters are unchanged.
       (rum/react filters*)))

(defn org-id []
  (when-let [automatic @automatic*]
    ((common/oget automatic "organizationId"))))

(defn reset-current-filter [fltr]
  (reset! current-filter* fltr)
  (reset! processed-current-filter* (some-> fltr (construct-fields init-field))))

(defn init-state [params]
  (reset! automatic* (common/oget params "automaticAssignments"))
  (reset! state/auth-fn js/lupapisteApp.models.globalAuthModel.ok)
  (reset-current-filter nil)
  ((common/oget @automatic* "setRefreshCallback") refresh-filters)
  (refresh-filters))

(defn edit-field [path value]
  (swap! processed-current-filter* assoc-in (flatten [path]) value))

(defn listicle [k]
  (case k
    :foreman-roles (for [role schemas/foreman-roles]
                     {:value role
                      :text  (common/loc (util/kw-path :automatic.foreman role))})
    (when-let [automatic @automatic*]
      (not-empty (js->clj (case k
                            :operations ((common/oget automatic "operations"))
                            :areas ((common/oget automatic "areas"))
                            :attachment-types ((common/oget automatic "attachmentTypes"))
                            :notice-forms ((common/oget automatic "noticeForms"))
                            :handler-role-id ((common/oget automatic "handlerRoles"))
                            :user-id ((common/oget automatic "authorities")))
                          :keywordize-keys true)))))

(defn new-filter []
  (reset-current-filter {:rank 0}))

(defn edit-filter [fltr]
  (reset-current-filter fltr))

(defn delete-filter-command [filter-id]
  (common/command {:command :delete-automatic-assignment-filter
                   :success (fn []
                              ((common/oget @automatic* "deleteAutomaticAssignmentFilter") (clj->js filter-id)))}
                  :organizationId (org-id)
                  :filter-id filter-id))

(defn delete-filter [fltr]
  (common/show-dialog {:ltitle   :triggers.confirm.remove.title
                       :size     :small
                       :type     :yes-no
                       :text     (common/loc :automatic.confirm.delete (:name fltr))
                       :callback #(delete-filter-command (:id fltr))}))

(defn prune-map [m]
  (not-empty (reduce-kv (fn [acc k v]
                          (let [pruned (cond-> v
                                         (set? v) seq
                                         (map? v) prune-map)]
                            (cond-> acc
                              (util/fullish? pruned) (assoc k pruned))))
                        {}
                        m)))

(defn validate-and-process-filter
  "Returns either valid and processed (aka schema valid) filter or nil."
  []
  (when-let [m (-> (rum/react processed-current-filter*)
                   prune-map
                   (select-keys [:id :name :rank :criteria :target :email]))]
    (when (every? second (traverse-fields m valid-field?))
      (let [m (construct-fields m process-field)]
        ;; Just in case
        (when-not (sc/check (:filter schemas/UpsertParams) m)
          m)))))

(defn upsert-filter [fltr]
  (common/command {:command               :upsert-automatic-assignment-filter
                   :show-saved-indicator? true
                   :success               (fn [{fltr :filter}]
                                            (reset-current-filter nil)
                                            ((common/oget @automatic* "upsertAutomaticAssignmentFilter") (clj->js fltr)))}
                  :organizationId (org-id)
                  :filter fltr))
