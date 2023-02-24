(ns lupapalvelu.ui.auth-admin.automatic-assignments.view
  "Container for automatic assignment filters configuration."
  (:require [clojure.set :as set]
            [lupapalvelu.automatic-assignment.schemas :as schemas]
            [lupapalvelu.ui.auth-admin.automatic-assignments.state :as state]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.state :as pate-state]
            [rum.core :as rum]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))



(def can-edit? (partial pate-state/auth? :upsert-automatic-assignment-filter))

(defn warning-class [path]
  (common/css-flags :warning (not (state/valid-field? path (state/best-value path)))))

(rum/defc filter-autocomplete-row < rum/reactive
  [property-key disabled?]
  (when-let [items (state/listicle property-key)]
    (let [path [:criteria property-key]
          title (common/loc (util/kw-path :automatic property-key :title))]
      (if-let [property-value (state/best-value path)]
        (let [selected-ids (set property-value)
              selected     (filter  #(contains? selected-ids (:value %))
                                    items)]
          (list [:div.row {:key (util/kw-path path :-selected)}
                 [:div.col-7
                  [:h4 title]
                  (for [{:keys [value text]} (sort-by :text selected)]
                    [:span.selected {:key value}
                     text
                     [:button.ghost.btn-small.no-border
                      {:disabled disabled?
                       :on-click #(state/edit-field path (set/difference selected-ids #{value}))}
                      [:i.lupicon-remove]]])]]
                [:div.row {:key (util/kw-path path :-autocomplete)}
                 [:div.col-4
                  (components/autocomplete nil
                                           {:items    items
                                            :callback #(state/edit-field path
                                                                         (cons % selected-ids))
                                            :disabled? disabled?
                                            :test-id  (common/test-id :automatic property-key)})]]))
        [:div.row
         (components/text-and-link {:text  (common/loc :automatic.activate title)
                                    :test-id (str "activate-" (name property-key) "-filter")
                                    :click #(state/edit-field path [])})]))))

(rum/defc filter-selection-row < rum/reactive
  [property-key disabled?]
  (when-let [items (state/listicle property-key)]
    (let [path  [:criteria property-key]
          title (common/loc (util/kw-path :automatic property-key :title))]
      [:div.row
       (if-let [property-value (state/best-value path)]
         [:div.col-4
          [:label.pate-label title]
          (components/autocomplete property-value {:items     items
                                                   :clear?    true
                                                   :disabled? disabled?
                                                   :test-id   (common/test-id :automatic property-key)
                                                   :callback  #(state/edit-field path %)})]
         (components/text-and-link {:text (common/loc :automatic.activate title)
                                    :test-id (str "activate-" (name property-key) "-filter")
                                    :click #(state/edit-field path "")}))])))

(rum/defc filter-toggles-row < rum/reactive
  [property-key initial-value disabled?]
  (when-let [items (state/listicle property-key)]
    (let [path [:criteria property-key]
          title (common/loc (util/kw-path :automatic property-key :title))]
      [:div.row
       (if-let [property-value (state/best-value path)]
         [:div.col-8
          [:h4 title]
          (components/toggle-group property-value
                                   {:items      items
                                    :disabled? disabled?
                                    :callback   #(state/edit-field path %)})]
         (components/text-and-link {:text  (common/loc :automatic.activate title)
                                    :test-id (str "activate-" (name property-key) "-filter")
                                    :click #(state/edit-field path initial-value)}))])))

(rum/defc filter-reviews-row < rum/reactive
  [initial-value disabled?]
  (let [path  [:criteria :reviews]
        title (common/loc (util/kw-path :automatic.reviews.title))]
    [:div.row
     (if-let [property-value (state/best-value path)]
       [:div.col-8
        [:h4.help-adjacent-title title]
        (components/help-toggle {:text-loc :automatic.reviews.help
                                 :html?    true})
        [:div.pate-grid-3
         [:div.row
          [:div.col-1.col--full
           (components/textarea-edit property-value
                                        {:disabled disabled?
                                         :rows 5
                                         :test-id "automatic-reviews"
                                         :callback #(state/edit-field path %)})]]]]
       (components/text-and-link {:text    (common/loc :automatic.activate title)
                                  :test-id "activate-reviews-filter"
                                  :click   #(state/edit-field path initial-value)}))]))

(rum/defc filter-email-row < rum/reactive
  [disabled?]
  [:div.row.row--extra-tight
   (let [path [:email :emails]]
     (if-let [emails-value (state/best-value path)]
      [:div.col-8
       [:div.pate-grid-2
        [:div.row
         [:div.col-1.col--full
          [:label.pate-label (common/loc (util/kw-path :automatic.email.emails))]
          (components/text-edit emails-value
                                   {:disabled disabled?
                                    :class    (warning-class [:email :emails])
                                    :callback #(state/edit-field path %)})]]
        [:div.row
         [:div.col-1.col--full
          [:label.pate-label (common/loc (util/kw-path :automatic.email.message))]
          (components/textarea-edit (state/best-value [:email :message])
                                       {:disabled disabled?
                                        :rows     6
                                        :callback #(state/edit-field [:email :message] %)})]]]]
      (components/text-and-link {:text  (common/loc :automatic.activate (common/loc :automatic.email))
                                 :test-id "activate-email-filter"
                                 :click #(state/edit-field path "")})))])

(rum/defc filter-target-row < rum/reactive
  [property-key disabled?]
  (when-let [items (state/listicle property-key)]
    [:div.row
     (let [path [:target property-key]
           property-value (state/best-value path)]
       [:div.col-4
        [:label.pate-label (common/loc (util/kw-path :automatic property-key))]
        (components/autocomplete property-value {:items     items
                                                 :clear?    true
                                                 :disabled? disabled?
                                                 :test-id   (str "autocomplete-" (name property-key))
                                                 :callback  #(state/edit-field path %)})])]))


(rum/defcs filter-editor <
  rum/reactive
  (rum/local false ::disabled?)
  [{disabled?* ::disabled?} filter]
  [:div.automatic-filter-editor
   [:div.pate-grid-8
    [:div.row
     ;; Name and rank
     [:div.col-2.col--full.required
      [:label.pate-label.required {:for "filter-edit-input"} (common/loc :triggers.header.title)]
      (components/text-edit (:name filter)
                               {:id        "filter-edit-input"
                                :required? true
                                :disabled  @disabled?*
                                :callback  #(state/edit-field :name %)})]
     [:div.col-1]
     [:div.col-1.col--full.required
      [:label.pate-label.required {:for "rank-edit-input"} (common/loc :automatic.rank)]
      (components/text-edit (:rank filter)
                               {:id        "rank-edit-input"
                                :type      "number"
                                :required? true
                                :disabled  @disabled?*
                                :callback  #(state/edit-field :rank %)
                                :class     (warning-class :rank)})]]

    [:div.row
     [:div.col-8 [:h3 (common/loc :automatic.limitations)]]]
    (filter-autocomplete-row :areas @disabled?*)
    (filter-autocomplete-row :operations @disabled?*)
    (filter-selection-row :handler-role-id @disabled?*)
    [:div.row
     [:div.col-8 [:h3 (common/loc :automatic.events)]]]
    (filter-autocomplete-row :attachment-types @disabled?*)
    (filter-toggles-row :notice-forms [] @disabled?*)
    (filter-toggles-row :foreman-roles schemas/foreman-roles @disabled?*)
    (filter-reviews-row "" @disabled?*)
    [:div.row
     [:div.col-8 [:h3 (common/loc :automatic.targets)]]]
    (filter-target-row :handler-role-id @disabled?*)
    (filter-target-row :user-id @disabled?*)
    [:div.row
     [:div.col-8 [:h3 (common/loc :automatic.email)]]]
    (filter-email-row @disabled?*)
    [:div.row
     [:div.col-7
      (let [fltr (state/validate-and-process-filter)]
        (components/icon-button {:text-loc :save
                                 :icon     :lupicon-save
                                 :wait?    disabled?*
                                 :enabled? (boolean fltr)
                                 :test-id  "save-filter"
                                 :on-click (fn []
                                             (reset! disabled?* true)
                                             (state/upsert-filter fltr))
                                 :class    :positive}))
      [:span common/nbsp]
      [:button.btn-link
       {:disabled @disabled?*
        :on-click #(state/reset-current-filter nil)}
       (common/loc :cancel)]]]]])

(defn filter-details
  [{:keys [rank criteria target email]}]
  (letfn [(find-texts [prop-key values]
            (when-let [values (cond
                                (coll? values) (set values)
                                (string? values) (set [values]))]
              (some->> (state/listicle prop-key)
                       (filter #(contains? values (:value %)))
                       seq
                       (map :text))))
          (row [loc-key value]
            (when-not (ss/blank? value)
              [:div.tabby__row
               {:data-test-id (str loc-key)}
               [:div.tabby__cell.tabby--top [:label.pate-label (common/loc loc-key)]]
               [:div.tabby__cell.tabby--100.tabby--top
                {:data-test-id "row-value"}
                value]]))
          (lst-row [prop-key]
            (when-let [value (some->> (find-texts prop-key (prop-key criteria))
                                      sort
                                      (ss/join ", "))]
              (row (util/kw-path :automatic prop-key :title) value)))]
    [:div.tabby
     (row :automatic.rank rank)
     (lst-row :areas)
     (lst-row :operations)
     (lst-row :handler-role-id)
     (lst-row :attachment-types)
     (lst-row :notice-forms)
     (lst-row :foreman-roles)
     (row :automatic.reviews.title (ss/join ", " (:reviews criteria)))
     (row :automatic.targets (ss/join (js/sprintf " %s " (common/loc :or))
                                      (concat (find-texts :handler-role-id (:handler-role-id target))
                                              (find-texts :user-id (:user-id target)))))
     (row :automatic.email (when-let [emails (some->> email :emails seq (ss/join ", "))]
                             [:div
                              [:div emails]
                              (when-let [msg (ss/blank-as-nil (:message email))]
                                [:div.message msg])]))]))

(rum/defcs filter-table < rum/reactive
  (rum/local {} ::open)
  [{open* ::open}]
  [:div.automatic-filter-list
   (when-let [filters (some->> (state/filters)
                               (sort-by (comp ss/lower-case :name))
                               seq)]
     [:table.table-even-odd
      [:tbody
       (for [[i {:keys [id name] :as fltr}] (map-indexed vector filters)]
         (let [cls     (common/css-flags :even-row (even? i) :odd-row (odd? i))
               open?   (get @open* id)
               view-fn #(swap! open* update id not)]
           (rum/fragment
             [:tr {:key   (str "link" i)
                   :class (common/css cls (common/css-flags :open open?))}
              [:td.filter-item
               [:div.tabby
                [:div.tabby__row
                 [:div.tabby__cell.tabby--100.filter-name
                  [:a {:on-click view-fn} name]]
                 [:div.tabby__cell.filter-actions
                  [:button.ghost.no-border {:on-click view-fn}
                   [:i.lupicon-eye]]
                  [:button.ghost.no-border
                   {:on-click #(state/edit-filter fltr)
                    :data-test-id (str "edit-filter-" i)}
                   [:i.lupicon-pen]]
                  [:button.ghost.no-border
                   {:on-click     #(state/delete-filter fltr)
                    :data-test-id (str "delete-filter-" i)}
                   [:i.lupicon-remove]]]]]]]
             (when open?
               [:tr.details {:key   (str "details" i)
                             :class cls}
                [:td (filter-details fltr)]]))))]])
   (components/icon-button {:text-loc :triggers.add
                            :enabled? (can-edit?)
                            :class    :positive
                            :icon     :lupicon-circle-plus
                            :test-id  :automatic-add
                            :on-click state/new-filter})])

(rum/defc view < rum/reactive
  []
  [:div#automatic-assignments
   [:h2.help-adjacent-title (common/loc :triggers.title)]
   (components/help-toggle {:text-loc :automatic.help
                            :html?    true})
   (if-let [filter (rum/react state/current-filter*)]
     (filter-editor filter)
     (filter-table))])


(defonce args (atom {}))

(defn mount-component []
  (rum/mount (view) (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params]
  (swap! args assoc
         :dom-id (name domId))
  (state/init-state params)
  (mount-component))
