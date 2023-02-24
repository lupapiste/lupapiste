(ns lupapalvelu.ui.attachment.filters
  "Each filtering use case (attachments view, transfer view, ...) is modelled as filter
  sets. Each set has an unique id (key) and contains both filtering
  functionality (prefilter, selected/available filters) and layout guidance (groups)."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.attachment.approval :as approval]
            [lupapalvelu.ui.attachment.grouping :as att-grouping]
            [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.attachment.state-tags :as state-tags]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(defn operation-loc [operation]
  (js/lupapisteApp.services.accordionService.getOperationLocalization (:id operation)))

(def filter-categories ["general" "building-site" "parties"
                        "reports" "technical-reports"])
(def filter-types att-grouping/type-group-order)

(def phase-filters [{:text-loc  "attachment.filter.phase.pre-verdict"
                     :value     :pre-verdict
                     :filter-fn #(->> % :applicationState (contains? att-grouping/post-verdict-states) not)}
                    {:text-loc  "attachment.filter.phase.post-verdict"
                     :value     :post-verdict
                     :filter-fn #(->> % :applicationState (contains? att-grouping/post-verdict-states))}])

(def status-filters [{:text-loc  :attachment.state.not-needed
                      :value     :attachment-not-needed
                      :filter-fn :notNeeded}
                     {:text-loc  :a11y.attachment.required
                      :value     :attachment-required
                      :filter-fn :required}])

(defn- id-set [xs]
  (set (map :id xs)))

(defn- assignment-filters []
  (let [assis   (<sub [::shared/all-attachment-assignments])
        autos   (filter :automatic? assis)
        user-id (.id js/lupapisteApp.models.currentUser)
        own-ids (->> assis
                     (filter (fn [{:keys [recipient]}]
                               (= (:id recipient) user-id)))
                     (mapcat (comp id-set :targets))
                     set)]
    (concat [{:text-loc  :attachment.filter.assignments
              :value     :attachment-has-assignments
              :filter-fn #(<sub [::shared/attachment-assignments %])}
             {:text-loc  :applications.search.recipient.my-own
              :value     :own-assignments
              :filter-fn #(-> % :id own-ids)}]
            (for [{:keys [description id targets]} autos]
              {:text      description
               :value     id
               :filter-fn #(contains? (id-set targets) (:id %))}))))

(defn- make-group-filters
  "Generic filters that filter according to the attachment's category / type / tags."
  [title paths]
  {:type    :group
   :title   title
   :filters (map (fn [path]
                   {:text      (att-grouping/group-loc path)
                    :value     path
                    :filter-fn (partial att-grouping/whole-group-path-in-tags? [path])})
                 paths)})

(defn- available-filters
  []
  {:phase      {:type      :group
                :threshold 2
                :title     :attachment.filter.phase
                :filters   phase-filters}
   :category   (make-group-filters :attachment.filter.category filter-categories)
   :type       (make-group-filters :attachment.filter.type filter-types)
   :status     {:type    :tri-state
                :title   :attachment.filter.status.title
                :filters status-filters}
   :state      {:type    :tri-state
                :title   :a11y.attachment.state.filter
                :filters state-tags/state-filters}
   :assignment {:type    :group
                :title   :attachment.filter.assignments.title
                :filters (assignment-filters)}})

(defn- find-filter-fns [filter-def values]
  (when-let [values (some-> values set not-empty)]
    (->> (:filters filter-def)
         (filter #(contains? values (:value %)))
         (map :filter-fn)
         seq)))

(defn- make-filter-fn-pred [filter-def values]
  (when-let [fns (find-filter-fns filter-def values)]
    (apply some-fn fns)))

(defmulti filter-predicate
  "Methods return predicates that signal whether a given attachment matches the filter or
  not. Nil predicates are ignored during the filtering."
  (fn [filter-key & _]
    (get-in (available-filters) [filter-key :type] filter-key)))

(defmethod filter-predicate :group
  [filter-key values]
  (some-> (get (available-filters) filter-key)
          (make-filter-fn-pred values)))

(defmethod filter-predicate :tri-state
  [filter-key values]
  (let [fn-map (->> (get-in (available-filters) [filter-key :filters])
                    (map (juxt :value :filter-fn))
                    (into {}))
        fns    (for [[k v] values
                     :when (some? v)]
                 (cond-> (get fn-map k)
                   (false? v) complement))]
    (some->> (seq fns) (apply every-pred))))

(defmethod filter-predicate :search-text
  [_ search-text]
  (when-let [search-text (some-> search-text ss/blank-as-nil ss/trim)]
    #(->> % :contents str (re-find (common/fuzzy-re search-text)))))

(defmethod filter-predicate :operations
  [_ operations]
  (when-let [operations (some-> operations set not-empty)]
    #(some->> (:op %) (map :id) (some operations))))

(defn- filter-attachments [applied-filters attachments]
  (if-let [pred-fns (->> applied-filters
                         (keep #(apply filter-predicate %))
                         seq)]
    (filter (apply every-pred pred-fns) attachments)
    attachments))

(rf/reg-sub ::operations #(::operations %))

(defn- get-operations []
  (->> (js->clj (js/lupapisteApp.models.application.allOperations)
                :keywordize-keys true)
       (map (fn [{:keys [id name description]}]
              {:id          (id)
               :name        (name)
               :description (description)}))))

(defn- clear-filters
  ([db fltr-key]
   (if fltr-key
     (update-in db
                [::filter-sets fltr-key]
                select-keys [:prefilter :groups])
     (assoc db ::filter-sets {})))
  ([db]
   (clear-filters db nil)))

(defmulti attachment-matches? (fn [pre-filter-key _] pre-filter-key))

(defmethod attachment-matches? :default
  [k _]
  (throw (ex-info (str "Unsupported filter: " k) {:k k})))

(defmethod attachment-matches? nil
  [& _]
  true)

(defmethod attachment-matches? :has-file
  [_ a]
  (boolean (:latestVersion a)))

(defmethod attachment-matches? :not-needed
  [_ a]
  (boolean (:notNeeded a)))

(def non-sendable-attachment-types
  "The disallowed types are taken from
`lupapiste-commons.attachment-types/types-not-transmitted-to-backing-system`."
  (->> {:muut          #{:paatos :paatosote :sopimus}
        :paatoksenteko #{:paatoksen_liite}}
       (mapcat (fn [[k xs]]
                 (map (partial util/kw-path k) xs)))
       set))

(defmethod attachment-matches? :sendable-file
  [_ {:keys [type latestVersion]}]
  (and latestVersion
       (not (contains? non-sendable-attachment-types
                       (util/kw-path (:type-group type) (:type-id type))))))

;; Attachment requires user action: it is either empty or
;; rejected (Tarkennettavaa)
(defmethod attachment-matches? :incomplete
  [_ {:keys [latestVersion notNeeded] :as a}]
  (or (and (not notNeeded) (not latestVersion))
      (approval/rejected? a)))

(defmethod attachment-matches? :updated
  [_ a]
  (and (:latestVersion a)
       (att-grouping/requires-authority-action? a)))

(defmethod attachment-matches? :ok
  [_ {:keys [latestVersion] :as a}]
  (and latestVersion
       (approval/approved? a)))

(defn prefilter-attachments [pre-filter-key attachments]
  (cond->> attachments
    pre-filter-key
    (filter #(attachment-matches? pre-filter-key %))))

(defn group-attachments
  "Each group-key is a filter for `attachment-matches?`."
  [group-keys attachments]
  (letfn [(a-group [a]
            (some #(when (attachment-matches? % a) %)
                  group-keys))]
    (group-by a-group attachments)))

;; -------------------------------
;; Events
;; -------------------------------

(rf/reg-event-db
  ::refresh-operations
  (fn [db]
    (assoc-in db [::operations] (get-operations))))

(rf/reg-event-db
  ::configure-filters
  (fn [db [_ fltr-key & xs]]
    (update-in db [::filter-sets fltr-key] merge (apply hash-map xs))))

(defn- make-id-groups [db fltr-key]
  (let [{:keys [prefilter filters
                groups]} (get-in db [::filter-sets fltr-key])]
    (->> (prefilter-attachments prefilter (shared/attachments db))
         (filter-attachments filters)
         (group-attachments groups)
         (util/map-values (partial map :id)))))

(rf/reg-event-db
  ::filter-and-group
  (fn [db [_ fltr-key]]
    (update-in db [::filter-sets fltr-key]
               #(assoc %
                       :id-groups (make-id-groups db fltr-key)
                       :refresh? false))))

(rf/reg-event-fx
  ::set-filters
  (fn [{db :db} [_ fltr-key group-key & xs]]
    {:db       (assoc-in db
                         (concat [::filter-sets fltr-key :filters group-key]
                                 (butlast xs))
                         (last xs))
     :dispatch [::filter-and-group fltr-key]}))

(rf/reg-event-fx
  ::check-filters
  (fn [{db :db} [_ fltr-key]]
    (let [attachments? (not-empty (prefilter-attachments (get-in db [::filter-sets fltr-key :prefilter])
                                                         (shared/attachments db)))]
      (cond
        (and attachments?
             (nil? (get-in db [::filter-sets fltr-key :filters])))
        {:db       (assoc-in db [::filter-sets fltr-key :filters] {})
         :dispatch [::filter-and-group fltr-key]}

        attachments?
        (let [new-id-groups (make-id-groups db fltr-key)
              new-id-n      (->> (vals new-id-groups) (map count) (apply +))
              old-id-groups (get-in db [::filter-sets fltr-key :id-groups])
              old-id-n      (->> (vals old-id-groups) (map count) (apply +))]
          (if (= new-id-n old-id-n)
            {:db (assoc-in db
                           [::filter-sets fltr-key :refresh?]
                           (not= new-id-groups old-id-groups))}
            {:dispatch [::filter-and-group fltr-key]}))

        :else
        {:db (assoc-in db [::filter-sets fltr-key :refresh?] false)}))))

(rf/reg-event-fx
  ::clear-filters
  (fn [{db :db} [_ fltr-key]]
    {:db       (clear-filters db fltr-key)
     :dispatch [::filter-and-group fltr-key]}))

(rf/reg-event-fx
  ::check-application
  (fn [{{:keys [::application-id] :as db} :db} [_ fltr-key]]
    (let [app-id (common/application-id)]
      (when-not (= app-id application-id)
        {:db       (assoc (clear-filters db fltr-key) ::application-id app-id)
         :dispatch [::check-filters fltr-key]}))))

(rf/reg-event-fx
  ::select-automatic-assignment
  (fn [{db :db} [_ fltr-key assi-id]]
    (when (some->> (shared/all-attachment-assignments db)
                   (filter :automatic?)
                   (util/find-by-id assi-id))
      {:db        (clear-filters db fltr-key)
       :dispatch  [::set-filters fltr-key :assignment #{assi-id}]
       :scroll/id (str (name fltr-key) "-filter-settings")})))

;; -------------------------------
;; Subs
;; -------------------------------

(rf/reg-sub
  ::filter-sets
  (fn [db [_ fltr-key]]
    (get-in db (cond-> [::filter-sets]
                 fltr-key (conj fltr-key)))))

(rf/reg-sub
  ;; Prefiltered means only filtered by the prefilter.
  ::prefiltered
  :<- [::shared/attachments]
  (fn [atts [_ fltr-key]]
    (prefilter-attachments (:prefilter (<sub [::filter-sets fltr-key])) atts)))

(rf/reg-sub
  ::applied-filters
  (fn [db [_ fltr-key]]
    (get-in db [::filter-sets fltr-key :filters])))

(rf/reg-sub
  ::get-filters
  (fn [[_ fltr-key]]
    (rf/subscribe [::applied-filters fltr-key]))
  (fn [filters [_ _ & xs]]
    (get-in filters xs)))

(rf/reg-sub
  ::filtered-groups
  (fn [db [_ fltr-key]]
    (util/filter-map-by-val seq (get-in db [::filter-sets fltr-key :id-groups]))))

(rf/reg-sub
  ::refresh?
  (fn [db [_ fltr-key]]
    (get-in db [::filter-sets fltr-key :refresh?])))

(rf/reg-sub
  ::applied-filter-count
  (fn [[_ fltr-key]]
    (rf/subscribe [::applied-filters fltr-key]))
  (fn [filter-map _]
    (->> (vals filter-map)
         (filter (fn [v]
                   (cond->> v
                     (map? v) (util/filter-map-by-val some?)
                     true util/fullish?)))
         count)))

;; -------------------------------
;; Components
;; -------------------------------

(defn- filter-items [attachments group-key]
  (->> (get (available-filters) group-key)
       :filters
       (filter (fn [{:keys [filter-fn]}]
                 (some filter-fn attachments)))))

(defn filter-group-component
  ([fltr-key group-key title-loc threshold]
   (let [current-filters (<sub [::get-filters fltr-key group-key])
         attachments     (<sub [::shared/attachments])
         items (filter-items attachments group-key)]
     (when (>= (count items) threshold)
       [:div.gap--t2
        [:label.txt--bold.dsp--block (loc title-loc)]
        [components/toggle-group current-filters
         {:items    items
          :prefix   :filter-tag
          :class    :gap--t1.gap--r1.txt--bold
          :callback #(>evt [::set-filters fltr-key group-key %])}]]))))

(defn filter-tri-state-component
  "Unlike other filter 'rows' the filters are resolved as AND. Each filter is a tri-state
  button."
  [fltr-key group-key title-loc]
  (let [selected    (<sub [::get-filters fltr-key group-key])
        attachments (<sub [::shared/attachments])
        all-terms   (filter-items attachments group-key)]
    (when (seq all-terms)
      [:div.gap--t2
       [:label.txt--bold.dsp--block (loc title-loc)]
       (into [:div.gap--t1.gap--r1.flex.flex--gap1]
             (for [{:keys [text-loc value]} all-terms]
               [components/tri-state-button
                (get selected value)
                {:text-loc text-loc
                 :callback #(>evt [::set-filters fltr-key group-key value %])}]))])))

(defn content-filter-component [fltr-key]
  [:div.gap--t1
   [:label.txt--bold
    {:for "att--filter-content"}
    (loc "attachment.filter.content")]
   [:div.col
    [components/delayed-search-bar (<sub [::get-filters fltr-key :search-text])
     {:placeholder (loc :attachment.filter.content.placeholder)
      :id          "att--filter-content"
      :callback    (fn [text]
                     (>evt [::set-filters fltr-key :search-text text]))}]]])

(defn operation-filter-component [fltr-key]
  (let [operations      (<sub [::operations])
        applied-filters (<sub [::get-filters fltr-key :operations])]
    (when (not-empty operations)
      [:div.gap--t2
       [:label.txt--bold
        {:for "att--filter-operation"}
        (loc "attachment.filter.operation")]
       [components/autocomplete-tags
        applied-filters
        {:id       "att--filter-operation"
         :items    (map (fn [operation]
                          {:text  (operation-loc operation)
                           :value (:id operation)})
                        operations)
         :callback (fn [op-ids]
                     (>evt [::set-filters fltr-key :operations op-ids]))}]])))

(defn filter-settings
  "`fltr-key` is an unique keyword for these filter settings."
  [fltr-key & filter-keys]
  (r/with-let [*expanded?  (r/atom false)
               _           (>evt [::hub/subscribe "automaticAssignment::selected"
                                  (fn [event]
                                    (reset! *expanded? false)
                                    (>evt [::select-automatic-assignment
                                           fltr-key
                                           (:assignmentId event)]))]
                                 ::automatic-hubscription)
               _           (>evt [::check-application fltr-key])
               filter-keys (some-> (flatten filter-keys) seq set)
               fltr?       #(or (nil? filter-keys) (filter-keys %))]
    (let [filter-count       (<sub [::applied-filter-count fltr-key])
          expanded?          @*expanded?
          filter-button-text (loc :attachment.filter.title.open)]
      [:div {:id (str (name fltr-key) "-filter-settings")}
       [:div.dsp--flex.flex--between
        [components/icon-button
         {:icon          (if expanded?
                           :lupicon-chevron-up
                           :lupicon-chevron-down)
          :on-click      #(swap! *expanded? not)
          :aria-expanded expanded?
          :class         :primary
          :text          (ss/join-non-blanks
                           " "
                           [filter-button-text
                            (case filter-count
                              0 nil
                              1 (loc :attachment.filter.active.single)
                              (loc :attachment.filter.active.multiple
                                   filter-count))])}]
        (when (pos? filter-count)
          [:button.tertiary
           {:on-click #(>evt [::clear-filters fltr-key])}
           (loc :attachment.filter.reset)])]
       (when @*expanded?
         (let [available (available-filters)]
           (into [:div.bg--violet.pad--1
                  {:role       "form"
                   :aria-label filter-button-text}
                 (when (fltr? :contents)
                   [content-filter-component fltr-key])
                 (when (fltr? :operation)
                   [operation-filter-component fltr-key])]
                 (for [[k {:keys [type title threshold]}] (filter (comp fltr? first) available)]
                   (case type
                     :group     [filter-group-component fltr-key k title (or threshold 1)]
                     :tri-state [filter-tri-state-component fltr-key k title])))))])
    (finally
      (>evt [::hub/unsubscribe ::automatic-hubscription]))))

(defn refresh-button [fltr-key]
  (when (<sub [::refresh? fltr-key])
    [:button.tertiary {:on-click #(>evt [::filter-and-group fltr-key])}
     (loc :attachment.filter.refresh)]))
