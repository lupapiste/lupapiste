(ns lupapalvelu.ui.auth-admin.invoicing.backend-id
  "Invoicing backend-id configuration."
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.components :as components]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

(rf/reg-event-db
  ::invoicing-config-response
  (fn [db [_ {:keys [invoicing-config backend-id-config] :as res}]]
    (assoc db
           ::backend-id-enabled? (:backend-id? invoicing-config)
           ::configuration backend-id-config)))

(rf/reg-event-fx
  ::fetch-configuration
  (fn [{db :db} _]
    {:action/query {:name    :invoicing-config
                    :params  {:organizationId (::organization-id db)}
                    :success [::invoicing-config-response]}}))

(rf/reg-event-fx
  ::update-config
  (fn [{db :db} [_ op]]
    {:action/command {:name                  :configure-invoicing-backend-id
                      :show-saved-indicator? true
                      :params                {:organizationId (::organization-id db)
                                              :op             op}
                      :success               [:action/store-response :config ::configuration]}}))


(defn- can-edit? []
  (<sub [:auth/global? :configure-invoicing-backend-id]))

(defn numbers-editor []
  (let [id (common/unique-id "numbers")
        n  (<sub [:value/get ::configuration :numbers])]
    [:div
     [:label.lux {:for id} (loc :invoicing.backend-id.numbers)]
     [components/dropdown (or n 1) {:id       id
                                    :choose?  false
                                    :callback #(>evt [::update-config {:set {:numbers (js/parseInt %)}}])
                                    :enabled? (can-edit?)
                                    :items    (map #(hash-map :value % :text %) (range 1 11))}]]))

(defn code-editor [id]
  (let [reserved-codes      (->> (<sub [:value/get ::configuration :codes])
                                 (remove #(= id (:id %)))
                                 (map :code)
                                 set)
        {:keys [code text]} (<sub [:value/get ::editing id])
        bad-code?           (contains? reserved-codes (ss/trim code))]
    [:tr
     [:td [components/text-edit code {:callback  #(>evt [:value/set ::editing id :code %])
                                      :required? code
                                      :invalid?  bad-code?
                                      :aria-labelledby "invoicing-backend-id-code"}]]
     [:td [components/text-edit text {:callback  #(>evt [:value/set ::editing id :text %])
                                      :required? text
                                      :class     :w--100
                                      :aria-labelledby "invoicing-backend-id-text"}]]
     [:td
      [:div.flex.flex--gap2
       [components/icon-button {:text-loc  :save
                                :class     :primary
                                :disabled? (or bad-code? (ss/blank? code) (ss/blank? text))
                                :on-click  (fn []
                                             (>evt [::update-config
                                                    {:upsert (util/assoc-when {:code code
                                                                               :text text}
                                                                              :id (when-not (= id :new)
                                                                                    id))}])
                                             (>evt [:value/set ::editing id nil]))}]
       [components/icon-button {:text-loc :cancel
                                :class    :secondary
                                :on-click #(>evt [:value/set ::editing id nil])}]]]]))

(defn code-table []
  (let [codes     (seq (<sub [:value/get ::configuration :codes]))
        new-code? (<sub [:value/get ::editing :new])]
    (when (or codes new-code?)
      [:table.pate-templates-table.w--max-70em
       [:thead
        [:tr
         [:th.w--min-8em {:id "invoicing-backend-id-code"} (loc :invoicing.backend-id.codes.code)]
         [:th.w--100 {:id "invoicing-backend-id-text"} (loc :invoicing.backend-id.codes.text)]
         [:th (loc :auth-admin.actions)]]]
       [:tbody
        (doall
          (for [{:keys [code text id] :as item} codes]
            (if (<sub [:value/get ::editing id])
              ^{:key id} [code-editor id]
              [:tr {:key id}
               [:td code]
               [:td text]
               [:td
                (when (can-edit?)
                  [:div.flex.flex--gap2
                   [components/icon-button {:text-loc :edit
                                            :icon     :lupicon-pen
                                            :class    :primary
                                            :on-click #(>evt [:value/set ::editing id item])}]
                       [components/icon-button
                        {:text-loc :remove
                         :icon     :lupicon-remove
                         :class    :secondary
                         :on-click (fn []
                                     (common/show-dialog
                                       {:text     (loc :invoicing.backend-id.codes.delete-confirmation
                                                       text code)
                                        :type     :yes-no
                                        :callback #(>evt [::update-config {:delete {:id id}}])}))}]])]])))
        (when new-code?
          [code-editor :new])]])))

(defn configuration []
  (r/with-let [_ (>evt [:auth/refresh])
               _ (>evt [::fetch-configuration])]
    (when (<sub [:value/get ::backend-id-enabled?])
      [:div.gap--v6.flex--column.flex--gap2.flex--align-start
       [:h2 (loc :invoicing.backend-id.title)]
       [numbers-editor]
       [code-table]
       [components/icon-button {:text-loc :invoicing.backend-id.codes.new
                                :icon     :lupicon-circle-plus
                                :class    :primary.flex--g0
                                :enabled? (and (can-edit?)
                                               (not (<sub [:value/get ::editing :new])))
                                :on-click #(>evt [:value/set ::editing :new {}])}]])))

(defn mount-component [dom-id]
  (rd/render [configuration]
             (.getElementById js/document dom-id)))

(defn ^:export start [dom-id params]
  (>evt [:value/set ::organization-id (common/->cljs (common/oget params "orgId"))])
  (mount-component dom-id ))
