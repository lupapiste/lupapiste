(ns lupapalvelu.ui.search.filters
  (:require [clojure.set :as set]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.search.core :as search]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(defn- view []
  (<sub [::search/view]))

(defn- field [field]
  (<sub [::search/field (view) field]))

(defn- field-setter [field]
  #(>evt [::search/set-field field %]))

(defn search-text
  ([label-loc]
   (let [id   (common/unique-id "search-text")
         text (<sub [::search/search-text])]
     [:div.gap--v1.w--49.w--l-100
      [:label
       {:for id}
       (loc label-loc)]
      [components/delayed-search-bar
       text
       {:id          id
        :placeholder (loc :application.filter.search.placeholder)
        :callback    #(>evt [::search/set-search-text %])}]]))
  ([]
   [search-text :application.filter.search]))

(rf/reg-sub ::selected-filters
  (fn [[_ v]]
    (rf/subscribe [::search/view-filters v]))
  (fn [filters]
    (some->> filters
             seq
             (map #(hash-map :value (:id %)
                             :text (:title %)))
             (into []))))

(defn selected-filter
  ([cls]
   (let [v (view)]
     (when-let [filters (<sub [::selected-filters v])]
       (let [id (common/unique-id "selected-filter")]
         [:div.gap--v1
          {:class (common/css cls)}
          [:label
           {:for id}
           (loc :application.filter.search.select.saved.filter)]
          [reselect/reselect
           (<sub [::search/current-filter-id v])
           {:callback   #(>evt [::search/select-filter v % true])
            :choose-loc :application.filter.search.placeholder.saved-filter
            :clear?     true
            :id         id
            :items      filters}]]))))
  ([] [selected-filter :w--49.w--l-100]))

(defn handler-role []
  (let [id (common/unique-id "handler-role")]
    [:div.gap--v1.
     [:label {:for id} (loc :view)]
     [:div.dsp--flex.flex--gap1.flex--wrap-s
      {:id id}
      [components/toggle-group
       #{(or (field :handler-role) :any)}
       {:items    [{:text-loc :applications.filter.handler-role-any
                    :value    :any}
                   {:text-loc :applications.filter.handler-role-general
                    :value    :general}
                   {:text-loc :applications.filter.handler-role-not-general
                    :value    :other}]
        :callback (field-setter :handler-role)
        :radio?   true
        :prefix   :plain-bold-narrow-tag
        :class    :gap--b.ws--nowrap}]]]))

(defn areas []
  (let [id (common/unique-id "areas")]
    (when-let [areas (<sub [::search/areas])]
      [:div.:div.gap--v1.w--49.w--l-100.flex--column
       [:label {:for id} (loc :applications.filter.areas)]
       [reselect/reselect
        (field :areas)
        {:id         id
         :items      areas
         :multi?     true
         :callback   (field-setter :areas)
         :choose-loc :application.filter.search.placeholder.areas-filter}]])))

(defn chevron-button
  ([text-loc flag extra-cls]
   (let [v    (view)
         expanded? (<sub [::search/flag? v flag])]
     [components/icon-button
      {:text-loc      text-loc
       :class         [:plain extra-cls]
       :icon          (if expanded? :lupicon-chevron-up :lupicon-chevron-down)
       :aria-expanded expanded?
       :on-click      #(>evt [::search/set-flag v flag (not expanded?)])}]))
  ([text-loc flag]
   [chevron-button text-loc flag nil]))

(rf/reg-sub ::selected-handler
  :<- [::search/view]
  (fn [view _]
    (<sub [::search/field view :handlers])))

(rf/reg-sub ::no-authority-selected?
  :<- [::selected-handler]
  (fn [selected]
    (contains? (set selected) :no-authority)))

(rf/reg-sub ::handler-items
  :<- [::search/handlers]
  :<- [::no-authority-selected?]
  (fn [[handlers no-authority?]]
    (->> (if no-authority? [] handlers)
         (cons {:value :no-authority
                :text  (loc :triggers.no.handler)})
         (into []))))

(defn handlers
  "Note that we allow the user to either select individual handler(s)
  or 'no handler' but not both at the same time."
  []
  (let [id                     (common/unique-id "handlers")
        selected               (<sub [::selected-handler])
        no-authority-selected? (fn [items] (contains? (set items) :no-authority))]
    [:div.gap--v1.w--49.w--l-100.flex--column
     [:label {:for id} (loc :applications.filter.handler)]
     [reselect/reselect
      selected
      {:id         id
       :items      (<sub [::handler-items])
       :multi?     true
       :callback   (fn [items]
                      (>evt [::search/set-field :handlers
                             (if (no-authority-selected? items)
                               [:no-authority]
                               items)]))
       :choose-loc (when (empty? selected)
                     :all)}]]))

(defn organization-tags []
  (let [id (common/unique-id "organization-tags")]
    (when-let [tags (<sub [::search/organization-tags])]
      [:div.gap--v1.w--49.w--l-100.flex--column
       [:label {:for id} (loc :applications.filter.tags)]
       [reselect/reselect
        (field :organization-tags)
        {:id         id
         :items      tags
         :multi?     true
         :callback   (field-setter :organization-tags)
         :choose-loc :application.filter.search.placeholder.tags-filter}]])))

(rf/reg-sub ::company-tag-items
  :<- [::search/company-tags]
  (fn [tags]
    (->> tags
         (map #(set/rename-keys % {:id :value :label :text}))
         (sort-by :text)
         (into [])
         not-empty)))

(defn company-tags []
  (let [id (common/unique-id "company-tags")]
    (when-let [tags (<sub [::company-tag-items])]
      [:div.gap--v1.w--49.w--l-100.flex--column
       [:label {:for id} (loc :applications.filter.tags)]
       [reselect/reselect
        (field :company-tags)
        {:id         id
         :items      tags
         :multi?     true
         :callback   (field-setter :company-tags)
         :choose-loc :application.filter.search.placeholder.tags-filter}]])))

(defn operations []
  (let [id (common/unique-id "operations")]
    [:div.gap--v1.w--49.w--l-100.flex--column
     [:label {:for id} (loc :applications.filter.operations)]
     [reselect/reselect
      (field :operations)
      {:id         id
       :items      (<sub [::search/operations])
       :multi?     true
       :callback   (field-setter :operations)
       :choose-loc :application.filter.search.placeholder.operations-filter}]]))

(defn event-range []
  (when (search/event-range-supported?)
    (let [[id-dd id-start
           id-end]   (repeatedly 3 #(common/unique-id "event-range"))
          event      (field [:event-range :event])
          start-date (field [:event-range :start-ts])
          end-date   (field [:event-range :end-ts])
          bad?       (<sub [::search/bad-event-range? (view)])]
      [:div.gap--t1.gap--b2.w--49.w--l-100.dsp--flex.flex--wrap-xl.flex--column-gap4.flex--row-gap1
       [:div.flex--column.flex--g1
        [:label {:for id-dd} (loc :applications.filter.event)]
        [components/dropdown
         event
         {:id         id-dd
          :class      :w--100
          :choose-loc :a11y.filter.no-event-range
          :items      search/event-range-events
          :callback   (field-setter [:event-range :event])}]]
       (when-not (ss/blank? event)
         [:div.dsp--flex.flex--gap2.flex--wrap-xs
          [:div.flex--column.flex--between
           [:label {:for id-start} (loc :a11y.earliest)]
           (day-edit/day-edit start-date
                              {:id       id-start
                               :class    :w--8em
                               :invalid? bad?
                               :callback (field-setter [:event-range :start-ts])})]
          [:div.flex--column.flex--between
           [:label {:for id-end} (loc :a11y.latest)]
           (day-edit/day-edit end-date
                              {:id       id-end
                               :class    :w--8em
                               :invalid? bad?
                               :callback (field-setter [:event-range :end-ts])})]])])))

(defn organizations []
  (let [id (common/unique-id "organizations")]
    [:div.gap--v1.w--49.w--l-100.flex--column
     [:label {:for id} (loc :applications.filter.organizations)]
     [reselect/reselect
      (field :organizations)
      {:id         id
       :items      (<sub [::search/organizations])
       :multi?     true
       :callback   (field-setter :organizations)
       :choose-loc :application.filter.search.placeholder.organizations-filter}]]))

(defn foreman-role []
  (let [id (common/unique-id "foreman-role")]
    [:div.gap--v1.w--49.w--l-100.flex--column
     [:label {:for id} (loc :applications.filter.foremanRole)]
     [reselect/reselect
      (field :foreman-roles)
      {:id         id
       :items      (<sub [::search/foreman-roles])
       :multi?     true
       :callback   (field-setter :foreman-roles)
       :choose-loc :application.filter.search.placeholder.foreman-role-filter}]]))

(defn states []
  (let [id (common/unique-id "states")]
    [:div.gap--v1.w--49.w--l-100.flex--column
     [:label {:for id} (loc :a11y.filter.by-state)]
     [reselect/reselect
      (field :states)
      {:id         id
       :items      (search/state-items (view))
       :multi?     true
       :callback   (field-setter :states)
       :choose-loc :a11y.filter.by-state.placeholder}]]))

(defn include-converted []
  [:div.gap--v1.w--49.w--l-100.flex--self-center
   [components/toggle
    (field :include-converted)
    {:text-loc :a11y.filter.include-converted
     :callback (field-setter :include-converted)}]])

(defn include-paper-applications []
  [:div.gap--v1.w--49.w--l-100.flex--self-center
   [components/toggle
    (if (false? (field :include-paper-applications)) false true)
    {:text-loc :a11y.filter.include-paper-applications
     :callback (field-setter :include-paper-applications)}]])

(defn save-filter []
  (let [id        (common/unique-id "states")
        err-id    (str id "-error")
        view      (view)
        title     (<sub [::search/save-title view])
        save-fn   #(>evt [::search/save-filter view])
        reserved? (<sub [::search/title-reserved? view])]
    [:div.gap--v1.w--49.w--l-100
     [:label {:for id} (loc :applications.search.name-and-save-filter)]

     [:div.dsp--flex.flex--wrap-l.flex--align-end.flex--column-gap1
      [components/text-edit
       title
       {:id                id
        :class             :flex--g1
        :invalid?          reserved?
        :aria-errormessage (when reserved? err-id)
        :enter-callback    #(when-not (ss/blank? title)
                              (save-fn))
        :callback          #(>evt [::search/set-save-title view %])}]
      [components/icon-button
       {:text-loc  :applications.search.save-filter
        :icon      :lupicon-save
        :class     :primary.gap--t1
        :disabled? (or (ss/blank? title) reserved?)
        :on-click  save-fn}]]
     (when reserved?
       [:div.error-note
        {:id   err-id
         :role :alert}
        (loc :error.filter-title-collision)])]))

(defn saved-filters []
  (let [v          (view)
        default-id (<sub [::search/default-filter-id v])]
    (when-let [[x & _
                :as filters] (some->> (<sub [::search/view-filters v])
                                      (sort-by :title)
                                      not-empty)]

      [:div.gap--2.flex--around
       [:div.dsp--inline-block
        [:div.txt--bold.pad--1.gap--l2 (loc :applications.search.saved-filters)]
        (for [{:keys [id title]} filters
              :let               [default? (= default-id id)]]
          ^{:key id} [:div.flex--wrap.gap--h2.pad--1.flex--gap2.bd--b-gray
                      {:class (common/css-flags :bd--t-gray (= id (:id x)))}
                      [:div.w--30em.w--max-30em.pad--t1
                       [:a {:tab-index 0
                            :on-click  #(>evt [::search/select-filter v id true])} title]]
                      [:div.flex--row-gap2.flex--column-gap4.flex--wrap
                       [components/toggle
                        default?
                        {:id         (str "default-filter-" id)
                         :text-loc   :applications.search.set-as-default-filter
                         :aria-label (str (loc :applications.search.set-as-default-filter)
                                          ": " title)
                         :callback   #(>evt [::search/set-default-filter-id v
                                             (when % id)])}]
                       [components/icon-button {:text-loc :remove
                                                :icon     :lupicon-remove
                                                :on-click #(>evt [::search/confirm-delete-filter v id])
                                                :class    :primary}]]])]])))

(defn clear-filter []
  [components/icon-button {:text-loc :applications.search.clear-filters
                           :icon     :lupicon-remove
                           :class    :plain-secondary
                           :on-click #(>evt [::search/clear-filter true])}])

(defn save-filter-row []
  (let [view (view)]
    [:div.flex--between.flex--wrap-l.flex--between.flex--row-gap1
     [save-filter]
     [:div.dsp--flex.flex--gap2.flex--align-end.gap--b1.flex--wrap
      {:class (common/css-flags :pad--b3 (<sub [::search/title-reserved? view]))}
      [clear-filter]
      (when (<sub [::search/view-filters view])
        [chevron-button
         :applications.search.saved-filters
         :show-saved-filters?])]]))

(defn recipient []
  (let [id       (common/unique-id "recipients")
        reps     (<sub [::search/recipients])
        selected (field :recipient)]
    [:div.gap--v1.w--49.w--l-100.flex--column
     [:label {:for id} (loc :applications.filter.recipient)]
     [reselect/reselect
      selected
      {:id         id
       :clear?     true
       :items      reps
       :callback   #(>evt [::search/set-field :recipient %])
       :choose-loc :applications.search.recipient.my-own}]]))

(defn created-range []
  (let [[id-start id-end] (repeatedly 2 #(common/unique-id "created-range"))
        start-date        (field [:created-range :start-ts])
        end-date          (field [:created-range :end-ts])
        bad?              (<sub [::search/bad-created-range? (view)])]
    [:div.gap--t1.gap--b2.w--49.w--l-100.dsp--flex.
     [:div.dsp--flex.flex--gap2.flex--wrap-xs
      [:div.flex--column.flex--between
       [:label {:for id-start} (loc :a11y.created.earliest)]
       (day-edit/day-edit start-date
                          {:id       id-start
                           :class    :w--8em
                           :invalid? bad?
                           :callback (field-setter [:created-range :start-ts])})]
      [:div.flex--column.flex--between
       [:label {:for id-end} (loc :a11y.created.latest)]
       (day-edit/day-edit end-date
                          {:id       id-end
                           :class    :w--8em
                           :invalid? bad?
                           :callback (field-setter [:created-range :end-ts])})]]]))

(defn authority-applications
  []
  (let [view (view)]
    [:div
     [:div.flex--between.flex--wrap
      [search-text]
      [selected-filter]]
     [:div.flex--between.flex--align-start.flex--wrap.flex--between.flex--gap1
      [handler-role]
      [chevron-button
       :applications.filter.advancedFilters
       :show-filters? :flex--self-end.gap--b1]]
     (when (<sub [::search/flag? view :show-filters?])
       [:<>
        [:div.flex--between.flex--wrap
         [areas]
         [handlers]
         [organization-tags]
         [operations]
         [states]
         [event-range]
         [organizations]
         [include-converted]
         [include-paper-applications]]
        [save-filter-row]
        (when (<sub [::search/flag? view :show-saved-filters?])
          [saved-filters])])]))

(defn foreman-applications
  []
  (let [view (view)]
    [:div
     [:div.flex--between.flex--wrap
      [search-text]
      [selected-filter]]
     [:div.flex--between.flex--align-start.flex--wrap.flex--between.flex--gap1
      [handler-role]
      [chevron-button
       :applications.filter.advancedFilters
       :show-filters? :flex--self-end.gap--b1]]
     (when (<sub [::search/flag? view :show-filters?])
       [:<>
        [:div.flex--between.flex--wrap
         [areas]
         [handlers]
         [organization-tags]
         [states]
         [organizations]
         [include-converted]
         [include-paper-applications]]
        [save-filter-row]
        (when (<sub [::search/flag? view :show-saved-filters?])
          [saved-filters])])]))

(defn new-foreman-applications
  []
  (let [view (view)]
    [:div
     [:div.flex--between.flex--wrap
      [search-text]
      [selected-filter]]
     [:div.flex--between.flex--align-start.flex--wrap.flex--between.flex--gap1
      [handler-role]
      [chevron-button
       :applications.filter.advancedFilters
       :show-filters? :flex--self-end.gap--b1]]
     (when (<sub [::search/flag? view :show-filters?])
       [:<>
        [:div.flex--between.flex--wrap
         [areas]
         [handlers]
         [organization-tags]
         [organizations]
         [foreman-role]]
        [save-filter-row]
        (when (<sub [::search/flag? view :show-saved-filters?])
          [saved-filters])])]))

(defn company-applications
  []
  (let [view (view)
        filters? (<sub [::search/view-filters view])]
    [:div
     [:div.flex--between.flex--wrap
      [search-text]
      [:div.dsp--flex.flex--wrap-s..w--49.w--l-100.flex--gap2
       [selected-filter :flex--g1]
       [chevron-button
       :applications.filter.advancedFilters
        :show-filters?
        [:flex--self-end (if filters? :gap--b2 :gap--b1)]]]]
     (when (<sub [::search/flag? view :show-filters?])
       [:<>
        [:div.flex--between.flex--wrap
         [company-tags]
         [operations]
         [states]]
        [save-filter-row]
        (when (<sub [::search/flag? view :show-saved-filters?])
          [saved-filters])])]))

(defn assignments
  []
  (let [view (view)]
    [:div
     [:div.flex--between.flex--wrap
      [search-text :application.filter.assignment.search]
      [chevron-button
       :applications.filter.advancedFilters
       :show-filters? :flex--self-end.gap--b1]]
     (when (<sub [::search/flag? view :show-filters?])
       [:<>
        [:div.flex--between.flex--wrap
         [recipient]
         [areas]
         [operations]
         [created-range]
         [organizations]
         [:div.dsp--flex.w--49.w--l-100.flex--align-end.flex--right.gap--b2
          [clear-filter]]]])]))
