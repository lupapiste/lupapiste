(ns lupapalvelu.ui.statistics.entries
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.statistics.data.entries :as data]
            [lupapalvelu.ui.statistics.log :as stats-log]
            [sade.strings :as ss]))

(defn can-edit? []
  (<sub [:auth/application? :update-processing-entry]))

(defn request? [entry-type]
  (= entry-type :request))

(defn can-add-entry? [entry-type]
  (<sub [:auth/application? (if (request? entry-type)
                              :new-processing-request
                              :new-processing-delay)]))

(defn can-add? []
  (and (can-edit?) (not (<sub [::data/adding?]))))

(defn can-answer? [entry-id]
  (<sub [::data/entry-auth? entry-id :answer-processing-entry]))

(defn can-close? [entry-id]
  (<sub [::data/entry-auth? entry-id :close-processing-entry]))

(defn can-toggle? [entry-id]
  (<sub [::data/entry-auth? entry-id :toggle-entry-seen]))

(defn show-deleted-toggle []
  (when (<sub [::data/deleted-entries?])
    (let [show-deleted? (<sub [::data/show-deleted?])]
      [components/toggle show-deleted?
       {:text-loc :handler-roles.show-all
        :callback #(>evt [::data/toggle-show-deleted %])}])))

(defn label [text-loc {:keys [required? view? id]}]
  [:label.lux {:for   id
               :class (common/css-flags :required required?
                                        :txt--bold view?)}
   (common/loc text-loc)])

(defn- with-id [entry-id k options]
  (assoc options :id (str entry-id "-" (name k))))

(defn label+date
  ([text-loc ts {id :id :as options}]
   (let [options (cond-> options
                   (not id) (assoc :id (str "stats-entries-" (name text-loc))))]
     [:div.flex--column.flex--align-start
      [label text-loc options]
      (if (:view? options)
        [:div (date/finnish-date ts)]
        [day-edit/day-edit ts (assoc options :format :epoch)])]))
  ([entry-id text-loc k options]
   (let [options (with-id entry-id k options)
         ts      (<sub [::data/entry entry-id k])]
     [label+date text-loc ts options])))

(defn label+textarea
  ([text-loc text {id :id :as options}]
   (let [options (cond-> options
                   (not id) (assoc :id (str "stats-entries-" (name text-loc))))]
     [:div.flex--column
      [label text-loc options]
      (if (:view? options)
        [:div.ws--pre-wrap {:id (:id options)} text]
        [components/textarea-edit text options])]))
  ([entry-id text-loc k options]
   (let [options (with-id entry-id k options)
         text    (<sub [::data/entry entry-id k])]
     [label+textarea text-loc text options])))

(defn state-info-note [entry-id state-key]
  (when-let [txt (<sub [::data/authorstamp entry-id state-key])]
    [:div.flex.flex--gap05.ws--nowrap
     [:div.txt--bold
      (str (common/loc (case state-key
                         :created  :common.created
                         :answered :statistics.entries.state.answered
                         :closed   :statistics.entries.state.closed))
           ":")]
     [:div txt]]))

(defn created-note [entry-id]
  (let [{:keys [type explanation]} (<sub [::data/entry entry-id])
        d?                         (= :delay type)
        created                    [label+textarea entry-id
                                    (if d?
                                      :statistics.entries.delay.created
                                      :statistics.entries.required-changes)
                                    :created-text {:view? true}]]
    [:div.pate-note
     [:div.flex--column.flex--row-gap1
      [label+date entry-id
       (if d?
         :statistics.entries.delay.start
         :supplement-request.due-date)
       :delay-start {:view? true}]
      (if explanation
        [:div.flex--wrap.flex--column-gap4
         created
         [label+textarea entry-id
          :statistics.entries.explanation
          :explanation {:view? true}]]
        created)]]))

(defn answer-note [entry-id]
  (let [{:keys [answered-text]} (<sub [::data/entry entry-id])]
    (when (<sub [::data/answered? entry-id])
      [:div.pate-note.flex--column
       [state-info-note entry-id :answered]
       [:div.ws--pre-wrap answered-text]])))

(defn cancel-button [entry-id]
  (let [waiting? (<sub [::data/waiting? entry-id])]
    [:button.tertiary {:disabled waiting?
                       :on-click #(>evt [::data/cancel entry-id])}
    (common/loc :cancel)]))

(defn request-editor [entry-id]
  (let [{:keys [delay-start created-text
                closed-ts]} (<sub [::data/editing entry-id])
        required-filled?    (fn []
                              (and delay-start
                                   (ss/blank-as-nil created-text)))
        waiting?            (<sub [::data/waiting? entry-id])]
    [:div.flex--column.flex--gap2.pad--2.bd--black
     [:h4 (common/loc :supplement-request)]
     [label+date entry-id
      :supplement-request.due-date
      :delay-start
      {:required? true
       :disabled? waiting?
       :callback  #(>evt [::data/edit entry-id :delay-start %])}]
     [label+textarea entry-id
      :statistics.entries.required-changes
      :created-text
      {:required? true
       :disabled  waiting?
       :callback  #(>evt [::data/edit entry-id :created-text %])}]
     [answer-note entry-id]
     [label+date entry-id
      :statistics.entries.closed-ts
      :closed-ts
      {:disabled? waiting?
       :callback  #(>evt [::data/edit entry-id :closed-ts %])}]
     (when closed-ts
       [label+textarea entry-id
        :statistics.entries.closed-text
        :closed-text
        {:disabled waiting?
         :callback #(>evt [::data/edit entry-id :closed-text %])}])
     [:div.flex--right.flex--gap2
      [:button.primary {:disabled (or (not (required-filled?))
                                      waiting?)
                        :on-click #(>evt [::data/save-editing entry-id])}
       (common/loc :save)]
      [cancel-button entry-id]]]))

(defn delay-editor [entry-id]
  (let [{:keys [delay-start created-text
                closed-ts]} (<sub [::data/editing
                                   entry-id])
        bad-dates?          (fn []
                              (and delay-start
                                   (some-> closed-ts (< delay-start))))
        required-filled?    (fn []
                              (and delay-start
                                   (ss/blank-as-nil created-text)
                                   (not (bad-dates?))))
        waiting?            (<sub [::data/waiting? entry-id])]
    [:div.flex--column.flex--gap2.pad--2.bd--black
     [:h4 (common/loc :statistics.entries.delay)]
     [:div.flex.flex--gap8
      [label+date entry-id
       :statistics.entries.delay.start
       :delay-start
       {:required? true
        :disabled? waiting?
        :callback  #(>evt [::data/edit entry-id :delay-start %])}]
      [label+date entry-id
       :statistics.entries.delay.end
       :closed-ts
       {:disabled? waiting?
        :invalid? (bad-dates?)
        :callback  #(>evt [::data/edit entry-id :closed-ts %])}]]
     [label+textarea entry-id
      :statistics.entries.delay.created
      :created-text
      {:required? true
       :disabled  waiting?
       :callback  #(>evt [::data/edit entry-id :created-text %])}]
     [label+textarea entry-id
      :statistics.entries.explanation
      :explanation
      {:disabled waiting?
       :callback #(>evt [::data/edit entry-id :explanation %])}]
     [answer-note entry-id]
     (when closed-ts
       [label+textarea entry-id
        :statistics.entries.closed-text
        :closed-text
        {:disabled waiting?
         :callback #(>evt [::data/edit entry-id :closed-text %])}])
     [:div.flex--right.flex--gap2
      [:button.primary {:disabled (or (not (required-filled?))
                                      waiting?)
                        :on-click #(>evt [::data/save-editing
                                          entry-id])}
       (common/loc :save)]
      [cancel-button entry-id]]]))

(defn close-delay-editor [entry-id]
  (let [delay-start (<sub [::data/entry entry-id :delay-start])
        {:keys [closed-ts]} (<sub [::data/closing entry-id])
        required-filled?    (fn []
                              (some-> closed-ts (>= delay-start)))
        waiting?            (<sub [::data/waiting? entry-id])]
    [:div.flex--column.flex--gap2.pad--2.bd--black
     [:h4 (common/loc :statistics.entries.delay)]
     [created-note entry-id]
     [answer-note entry-id]
     [label+date entry-id
      :statistics.entries.delay.end
      :closed-ts
      {:disabled? waiting?
       :required? true
       :callback  #(>evt [::data/close entry-id :closed-ts %])}]
     [label+textarea entry-id
      :statistics.entries.closed-text
      :closed-text
      {:disabled waiting?
       :callback #(>evt [::data/close entry-id :closed-text %])}]
     [:div.flex--right.flex--gap2
      [:button.primary {:disabled (or (not (required-filled?))
                                      waiting?)
                        :on-click #(>evt [::data/save-closing entry-id])}
       (common/loc :save)]
      [cancel-button entry-id]]]))

(defn entry-editor [entry-id]
  (if (= :request (<sub [::data/entry entry-id :type]))
    [request-editor entry-id]
    [delay-editor entry-id]))

(defn new-entry-editor []
  (let [entry-id            data/new-id
        {:keys [type delay-start created-text explanation
                closed-ts]} (<sub [::data/editing entry-id])
        request?            (= type :request)
        bad-dates?          (fn []
                              (and (not request?)
                                   delay-start
                                   (some-> closed-ts (< delay-start))))
        required-filled?    (fn []
                              (and delay-start
                                   (ss/blank-as-nil created-text)
                                   (not (bad-dates?))))
        waiting?            (<sub [::data/waiting? entry-id])]
    [:div.flex--column.flex--gap2.pad--2.bd--black
     [:h3 (common/loc (if request?
                        :statistics.entries.new-request
                        :statistics.entries.new-delay))]
     (if request?
       [label+date :supplement-request.due-date delay-start
        {:required? true
         :disabled? waiting?
         :callback  #(>evt [::data/edit entry-id :delay-start %])}]
       [:div.flex.flex--gap8
        [label+date :statistics.entries.delay.start delay-start
         {:required? true
          :disabled? waiting?
          :callback  #(>evt [::data/edit entry-id :delay-start %])}]
        [label+date :statistics.entries.delay.end  closed-ts
         {:disabled? waiting?
          :invalid?  (bad-dates?)
          :callback  #(>evt [::data/edit entry-id :closed-ts %])}]])
     [label+textarea (if request?
                       :statistics.entries.required-changes
                       :statistics.entries.delay.created)
      created-text
      {:required? true
       :disabled  waiting?
       :callback  #(>evt [::data/edit entry-id :created-text %])}]
     (when-not request?
       [label+textarea :statistics.entries.explanation
        explanation
        {:disabled waiting?
         :callback #(>evt [::data/edit entry-id :explanation %])}])
     [:div.flex--right.flex--gap2
      [:button.primary {:disabled (or (not (required-filled?))
                                      waiting?)
                        :on-click #(>evt [::data/save-editing entry-id])}
       (common/loc :save)]
      [cancel-button entry-id]]]))

(defn answer-editor [entry-id]
  (let [text     (<sub [::data/answering entry-id])
        waiting? (<sub [::data/waiting? entry-id])]
    [:div.flex--column.flex--gap2.pad--2.bd--black
     [:h4 (common/loc :supplement-request)]
     [created-note entry-id]
     [label+textarea entry-id
      :statistics.entries.answered-text
      :answered-text
      {:required? true
       :disabled  waiting?
       :callback  #(>evt [::data/answer entry-id %])}]
     [:div.flex--right.flex--gap2
      [:button.primary {:disabled (or (ss/blank? text) waiting?)
                        :on-click #(>evt [::data/save-answering entry-id])}
       (common/loc :save)]
      [cancel-button entry-id]]]))

(defn close-request-editor [entry-id]
  (let [{:keys [closed-ts]} (<sub [::data/closing entry-id])
        answered-ts         (<sub [::data/entry entry-id :answered-ts])
        waiting?            (<sub [::data/waiting? entry-id])]
    [:div.flex--column.flex--gap2.pad--2.bd--black
     [:h4 (common/loc :supplement-request)]
     [created-note entry-id]
     (when answered-ts
       [answer-note entry-id])
     [label+date entry-id
      :statistics.entries.closed-ts
      :closed-ts
      {:disabled? waiting?
       :required? true
       :callback  #(>evt [::data/close entry-id :closed-ts %])}]
     [label+textarea entry-id
      :statistics.entries.closed-text
      :closed-text
      {:disabled waiting?
       :callback #(>evt [::data/close entry-id :closed-text %])}]
     [:div.flex--right.flex--gap2
      [:button.primary {:disabled (or (not closed-ts) waiting?)
                        :on-click #(>evt [::data/save-closing  entry-id])}
       (common/loc :save)]
      [cancel-button entry-id]]]))

(defn close-editor [entry-id]
  (if (= :request (<sub [::data/entry entry-id :type]))
    [close-request-editor entry-id]
    [close-delay-editor entry-id]))

(defn toggle-seen-button [entry-id]
  (let [seen-by? (<sub [::data/entry entry-id :seen-by?])
        m        {:icon-only? true
                  :class      :tertiary
                  :on-click   #(>evt [::data/toggle-entry-seen entry-id])}]
    [components/icon-button (merge m
                                   (if seen-by?
                                     {:text-loc   :statistics.mark.unread
                                      :title-loc  :statistics.mark.unread
                                      :icon-only? true
                                      :icon       :lupicon-bell}
                                     {:text-loc   :statistics.mark.read
                                      :title-loc  :statistics.mark.read
                                      :icon-only? true
                                      :icon       :lupicon-bell-off}))]))

(defn toggle-delete-button [entry-id]
  (let [deleted? (<sub [::data/entry entry-id :deleted?])
        text-loc (if deleted? :pate-restore :remove)
        icon     (if deleted? :lupicon-undo :lupicon-remove)]
    [components/icon-button {:text-loc   text-loc
                             :title-loc  text-loc
                             :icon-only? true
                             :class      :tertiary
                             :icon       icon
                             :on-click   #(>evt [::data/mark-deleted entry-id (not deleted?)])}]))

(defn actions-cell [entry-id]
  [:td.va--top {:style {:padding-top 0}}
   [:div.flex--right.flex--gap1
    (when (can-close? entry-id)
      [components/icon-button {:text-loc   :close
                               :title-loc  :close
                               :icon-only? true
                               :class      :tertiary
                               :icon       :lupicon-check
                               :on-click   #(>evt [::data/start-closing entry-id])}])
    (when (can-edit?)
      [components/icon-button {:text-loc   :edit
                               :title-loc  :edit
                               :icon-only? true
                               :class      :tertiary
                               :icon       :lupicon-pen
                               :on-click   #(>evt [::data/start-editing entry-id])}])
    (when (can-edit?)
      [toggle-delete-button entry-id])
    (when (can-toggle? entry-id)
      [toggle-seen-button entry-id])
    (when (can-answer? entry-id)
      [:button.primary.gap--t1
       {:on-click #(>evt [::data/start-answering entry-id])}
       (common/loc (if (<sub [::data/answered? entry-id])
                     :statistics.entries.edit-answer
                     :statistics.entries.answer))])]])

(defn created-comment [entry-id]
  (let [{:keys [type explanation]} (<sub [::data/entry entry-id])]
    [:div.flex--column.flex--row-gap05
     [state-info-note entry-id :created]
     [label+textarea entry-id
      (if (= :delay type)
        :statistics.entries.delay.created
        :statistics.entries.required-changes)
      :created-text {:view? true}]
     (when explanation
       [label+textarea entry-id
        :statistics.entries.explanation
        :explanation {:view? true}])]))

(defn closed-note [entry-id]
  (when (<sub [::data/closed? entry-id])
    [:div.flex--column.flex--row-gap05
     [state-info-note entry-id :closed]
     (when-let [closed-text (<sub [::data/entry entry-id :closed-text])]
       [:div.ws--pre-wrap closed-text])]))

(defn comments-cell [entry-id]
  [:td.pad--1.va--top.w--40.ws--pre-wrap
   [:div.flex--column.flex--gap05
    [created-comment entry-id]
    [answer-note entry-id]
    [closed-note entry-id]]])

(defn state-cell [entry-id]
  (let [state (<sub [::data/entry-state entry-id])]
    [:td.pad--1.va--top.ws--nowrap
     (path/loc :statistics.entries.state state)]))

(defn date-cell [field-key entry-id]
  [:td.pad--1.va--top
   (date/finnish-date (<sub [::data/entry entry-id field-key]))])

(defn modified-cell [entry-id]
  (let [{:keys [seen-by? modified]} (<sub [::data/entry entry-id])]
    [:td.pad--1.va--top
     [:span (when-not seen-by?
              {:class [:lux-new :txt--bold]})
      (date/finnish-date modified)]]))

(defn delay-start-cell [entry-id]
  [:td.pad--1.va--top
   (when (<sub [::data/late? entry-id])
     {:class [:txt--bold :fg--negative]})
   (date/finnish-date (<sub [::data/entry entry-id :delay-start]))])

(def closed-cell (partial date-cell :closed-ts))

(defn column-th
  [k]
  (let [[text-loc style] (case k
                           :modified    [:attachment.editDate {:min-width "8em"}]
                           :state       [:dmcity.status]
                           :comments    [:conversation.title]
                           :delay-start [:statistics.entries.delay.start]
                           :duedate     [:supplement-request.due-date]
                           :delay-end   [:statistics.entries.delay.end]
                           :closed      [:statistics.entries.closed-ts]
                           :actions     [:admin.actions {:text-align :center}])]
    [:th.pad--1 (some->> style (hash-map :style)) (common/loc text-loc)]))

(defn cell [k]
  (case k
    :modified               modified-cell
    :state                  state-cell
    :comments               comments-cell
    (:delay-start :duedate) delay-start-cell
    (:delay-end :closed)    closed-cell
    :actions                actions-cell))

(defn entry-row [columns entry-id]
  (let [colspan {:col-span (count columns)}]
    (cond
      (<sub [::data/editing? entry-id])
      [:tr
       [:td colspan [entry-editor entry-id]]]
      (<sub [::data/answering entry-id])
      [:tr
       [:td colspan [answer-editor entry-id]]]
      (<sub [::data/closing entry-id])
      [:tr
       [:td colspan [close-editor entry-id]]]
      :else
      (into [:tr]
            (for [k columns]
              [(cell k) entry-id])))))

(defn entry-table
  [text-loc ids columns]
  (when ids
    (let [columns (cond-> columns
                    (or (can-edit?) (<sub [::data/auths-for-entries? ids]))
                    (conj :actions))]
      [:div.gap--t4
       [:h3 (common/loc text-loc)]
       [:table
        [:thead.bg--gray.bd--black
         (into [:tr]
               (for [k columns]
                 [column-th k]))]
        (into [:tbody]
              (mapv (fn [entry-id]
                      [entry-row columns entry-id])
                    ids))]])))

(defn log-section []
  (when (<sub [:auth/application? :processing-logdata])
    [:div.flex--column.gap--t4
     [:h3 (common/loc :statistics.processing.log)]
     [stats-log/log]
     [:div.flex.gap--t2 [stats-log/pdf-button]]]))

(defn new-entry-button [entry-type]
  (when (can-add-entry? entry-type)
    [components/icon-button {:text-loc  (if (request? entry-type)
                                         :statistics.entries.new-request
                                         :statistics.entries.new-delay)
                             :icon      :lupicon-circle-plus
                             :class     :primary
                             :disabled? (<sub [::data/waiting? data/new-id])
                             :on-click  #(>evt [::data/start-editing data/new-id {:type entry-type}])}]))

(defn view []
  (when (<sub [::data/visible?])
    [:div.flex--column.flex--row-gap1
     [:h1 (common/loc :statistics.entries.title)]
     (when (can-add?)
       [:div.flex--wrap.flex--gap2.flex--between
        [:div.flex--wrap.flex--gap2
         [new-entry-button :request]
         [new-entry-button :delay]]
        [show-deleted-toggle]])
     (when (<sub [::data/adding?])
       [new-entry-editor])
     [entry-table :statistics.entries.requests (<sub [::data/request-ids])
      [:modified :state :comments :duedate]]
     [entry-table :statistics.processing.delays (<sub [::data/delay-ids])
      [:modified :state :comments :delay-start :delay-end]]
     [log-section]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
