(ns lupapalvelu.ui.matti.settings
  (:require [clojure.string :as s]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.components :as matti-components]
            [lupapalvelu.ui.matti.layout :as layout]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn settings-updater [{:keys [path state info]}]
  (service/save-settings-value (path/value [:category] info)
                               path
                               (path/value path state)
                               (common/response->state state :modified)))

(defn fetch-settings [category]
  (service/settings category
                    (fn [{settings :settings}]
                      (reset! state/settings (:draft settings))
                      (reset! state/settings-info
                              {:info  {:modified (:modified settings)
                                       :category category}
                               :_meta {:updated  settings-updater
                                       :editing? true
                                       :enabled? (state/auth? :save-verdict-template-settings-value)}})))
  (service/generics :review
                    category
                    (fn [{reviews :reviews}]
                      (reset! state/reviews reviews)))
  (service/generics :plan
                    category
                    (fn [{plans :plans}]
                      (reset! state/plans plans))))

(defn- generic-state [generic-type]
  (case generic-type
    :review state/reviews
    :plan   state/plans))

(defn upsert-generic
  "Updates generic's (review or plan) state according to the
  response. After adding or updating a review."
  [generic-type]
  (let [state* (generic-state generic-type)]
   (fn [response]
     (let [generic (generic-type response)]
       (when (= (:category generic) @state/current-category)
         (if-let [index (first (keep-indexed #(when (= (:id %2) (:id generic))
                                                %1)
                                             @state*))]
           (swap! state* #(assoc % index generic))
           (swap! state* #(conj % generic))))))))

(defn name-edit [initial callback]
  (components/text-edit initial {:callback  callback
                                 :required? true}))

(rum/defcs type-edit < (components/initial-value-mixin ::selection)
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


(defn update-details [generic-type gen-id details-key initial value]
  (let [value (s/trim value)]
    (when-not (or (= initial value) (s/blank? value))
      (service/update-generic generic-type
                              gen-id
                              (upsert-generic generic-type)
                              details-key
                              value))))

(defn name-cell [generic-type {:keys [id name deleted]} key]
  (let [value (key name)]
    (if deleted
      [:span value]
      (name-edit value (partial update-details generic-type id key value)))))

(defn type-cell [{:keys [id type deleted]}]
  (if deleted
    [:span (common/loc (str "matti.review-type." (name type)))]
    (type-edit type (partial update-details :review id :type type))))

(rum/defcs generic-editor < rum/reactive
  (rum/local false ::show-deleted)
  [{show-deleted ::show-deleted} generic-type]
  (let [items    (rum/react (generic-state generic-type))
        check-id (js/sprintf "show-deleted-%ss" (name generic-type))]
    [:div.matti-settings-editor
     (when (some :deleted items)
       [:div.checkbox-wrapper
        [:input {:type  "checkbox"
                 :id    check-id
                 :value @show-deleted}]
        [:label.checkbox-label
         {:for      check-id
          :on-click #(swap! show-deleted not)}
         (common/loc :handler-roles.show-all)]])
     (let [filtered (if @show-deleted
                      items
                      (remove :deleted items))]
       (when (seq filtered)
         [:table.matti-editor-table
          [:thead
           [:tr
            [:th (common/loc "lang.fi")]
            [:th (common/loc "lang.sv")]
            [:th (common/loc "lang.en")]
            (when (= generic-type :review)
              [:th (common/loc "verdict.katselmuksenLaji")])
            [:th]]]
          [:tbody
           (for [{:keys [id deleted] :as item} filtered]
             [:tr {:key id}
              [:td (name-cell generic-type item :fi)]
              [:td (name-cell generic-type item :sv)]
              [:td (name-cell generic-type item :en)]
              (when (= generic-type :review)
                [:td (type-cell item)])
              [:td [:button.primary.outline
                    {:on-click #(service/update-generic generic-type
                                                        id
                                                        (upsert-generic generic-type)
                                                        :deleted
                                                        (not deleted))}
                    (common/loc (if deleted :matti-restore :remove))]]])]]))
     [:button.positive
      {:on-click #(service/new-generic generic-type
                                       @state/current-category
                                       (upsert-generic generic-type))}
      [:i.lupicon-circle-plus]
      [:span (common/loc "add")]]]))

;; -------------------------
;; Settings sections
;; -------------------------

(defn settings-section-header [{:keys [path schema state] :as options} edit?]
  [:div.matti-grid-6.section-header
   [:div.row.row--tight
    [:div.col-4
     [:span.matti-label
      (when edit? {:class :row-text})
      (path/loc options)]]
    (when (and edit? (path/enabled? options))
      [:div.col-2.col--right
       [:button.ghost
        {:on-click #(path/flip-meta options :editor?)}
        (common/loc (if (path/react-meta options :editor?)
                      :close
                      :edit))]])]])

(rum/defc settings-section-body < rum/reactive
  [{:keys [schema] :as options}]
  [:div.section-body
   (if (path/react-meta options :editor?)
     (case (-> schema :id keyword)
       :reviews (generic-editor :review)
       :plans   (generic-editor :plan))
     (layout/matti-grid (path/schema-options options
                                             (:grid schema))))])

(defmethod sections/section-header :settings
  [{schema :schema :as options} _]
  (settings-section-header options (util/includes-as-kw? [:reviews :plans]
                                                         (:id schema))))

(defmethod sections/section-body :settings
  [options _]
  (settings-section-body options))

(rum/defc verdict-template-settings < rum/reactive
  [{:keys [schema] :as options}]
  [:div.matti-settings
   [:div.matti-grid-2
    [:div.row.row--tight
     [:div.col-1
      [:h2.matti-settings-title (common/loc :matti-settings
                                            (common/loc (:title schema)))]]
     [:div.col-1.col--right
      (matti-components/last-saved options)]]]
   (sections/sections options :settings)])
