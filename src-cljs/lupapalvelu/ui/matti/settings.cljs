(ns lupapalvelu.ui.matti.settings
  (:require [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.layout :as layout]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
            [clojure.string :as s]
            [rum.core :as rum]))

(defn settings-updater [{:keys [path state]}]
  (service/save-settings-value (path/value [:category] state)
                               path
                               (path/value path state)
                               (common/response->state state :modified)))

(defn fetch-settings [category]
  (service/settings category
                    (fn [{settings :settings}]
                      (reset! state/settings
                              (assoc (:draft settings)
                                     :modified (:modified settings)
                                     :category category
                                     :_meta {:updated settings-updater}))))
  (service/reviews category
                   (fn [{reviews :reviews}]
                     (reset! state/reviews reviews))))

(defn upsert-review
  "Updates reviews state according to the response. After adding or
  updating a review."
  [{review :review}]
  (when (= (:category review) @state/current-category)
    (if-let [index (first (keep-indexed #(when (= (:id %2) (:id review))
                                           %1)
                                        @state/reviews))]
      (swap! state/reviews #(assoc % index review))
      (swap! state/reviews #(conj % review)))))

(defn css-flags [& flags]
  (->> (apply hash-map flags)
       (filter (fn [[k v]] v))
       keys
       (map name)))

;;
(defn initial-value-mixin
  "Assocs to component's local state local-key with atom that is
  initialized to the first component argument."
  [local-key]
  {:will-mount (fn [state]
                 (assoc state local-key (-> state
                                            :rum/args
                                            first
                                            atom)))})

(rum/defcs name-edit < (initial-value-mixin ::text)
  rum/reactive
  [local-state _ callback]
  (let [text* (::text local-state)]
    [:input.grid-style-input
     {:value     @text*
      :type      "text"
      :class     (css-flags :required (-> text* rum/react s/trim s/blank?))
      :on-change (common/event->state text*)
      :on-blur   #(callback (.. % -target -value))}]))

(rum/defcs type-edit < (initial-value-mixin ::selection)
  rum/reactive
  [local-state _ callback]
  (let [selection* (::selection local-state)]
    [:select.dropdown
     {:value     (rum/react selection*)
      :on-change (fn [event]
                   (let [value (.. event -target -value)]
                     (callback value)
                     (reset! selection* value)))}
     (->> shared/review-type-map
          keys
          (map (fn [k]
                 {:text (common/loc (str "matti.review-type." (name k)))
                  :value k}))
          (sort-by :text)
          (map (fn [{:keys [value text]}]
                 [:option {:key value :value value} text])))]))


(defn update-details [review-id details-key initial value]
  (let [value (s/trim value)]
    (when-not (or (= initial value) (s/blank? value))
      (service/update-review review-id
                             upsert-review
                             details-key
                             value))))

(defn name-cell [{:keys [id name deleted]} key]
  (let [value (key name)]
    (if deleted
      [:span value]
      (name-edit value (partial update-details id key value)))))

(defn type-cell [{:keys [id type deleted]}]
  (if deleted
    [:span (common/loc (str "matti.review-type." (name type)))]
    (type-edit type (partial update-details id :type type))))


(rum/defcs reviews-editor < rum/reactive
  (rum/local false ::show-deleted)
  [{show-deleted ::show-deleted}]
  (let [reviews (rum/react state/reviews)]
    [:div.matti-settings-editor
     (when (some :deleted reviews)
       [:div.checkbox-wrapper
        [:input {:type "checkbox"
                 :id "show-deleted-reviews"
                 :value @show-deleted}]
        [:label.checkbox-label
         {:for "show-deleted-reviews"
          :on-click #(swap! show-deleted not)}
         (common/loc :handler-roles.show-all)]])
     (let [filtered (if @show-deleted
                      reviews
                      (remove :deleted reviews))]
       (when (seq filtered)
         [:table.matti-editor-table
          [:thead
           [:tr
            [:th (common/loc "lang.fi")]
            [:th (common/loc "lang.sv")]
            [:th (common/loc "lang.en")]
            [:th (common/loc "verdict.katselmuksenLaji")]
            [:th]]]
          [:tbody
           (for [{:keys [id type deleted] :as review} filtered]
             [:tr {:key id}
              [:td (name-cell review :fi)]
              [:td (name-cell review :sv)]
              [:td (name-cell review :en)]
              [:td (type-cell review)]
              [:td [:button.primary.outline
                    {:on-click #(service/update-review id
                                                       upsert-review
                                                       :deleted
                                                       (not deleted))}
                    (common/loc (if deleted :matti-restore :remove))]]])]]))
     [:button.positive
      {:on-click #(service/new-review @state/current-category upsert-review)}
      [:i.lupicon-circle-plus]
      [:span (common/loc "add")]]])
  )

(defn settings-section-header [{:keys [path schema state] :as options} edit?]
  [:div.matti-grid-6.section-header
   [:div.row.row--tight
    [:div.col-4
     [:span.matti-label
      (when edit? {:class :row-text})
      (common/loc (str "matti-settings."
                       (-> path first name)))]]
    (when edit?
      [:div.col-2.col--right
       [:button.ghost
        {:on-click #(path/flip-meta options :editor?)}
        (common/loc (if (path/meta? options :editor?)
                      :close
                      :edit))]])]])

(rum/defc settings-section < rum/reactive
  {:key-fn path/key-fn}
  [{:keys [state path id css] :as options}]
  [:div.matti-section
   {:class (path/css options)}
   (settings-section-header options (contains? #{:reviews :plans}
                                               (keyword id)))
   [:div.section-body
    (if (path/react-meta? options :editor?)
      (case (keyword id)
        :reviews (reviews-editor))
      (layout/matti-grid (shared/child-schema options
                                              :grid
                                              options)))]])

(rum/defc verdict-template-settings < rum/reactive
  [{:keys [title sections] :as options}]
  [:div.matti-settings
   [:div.matti-grid-2
    [:div.row.row--tight
     [:div.col-1
      [:h2.matti-settings-title (common/loc :matti-settings
                                            (common/loc title))]]
     [:div.col-1.col--right
      (layout/last-saved (assoc options :state state/settings))]]]
   (for [{id :id :as sec} sections]
     (settings-section (assoc sec
                              :path [id]
                              :state state/settings)))])
