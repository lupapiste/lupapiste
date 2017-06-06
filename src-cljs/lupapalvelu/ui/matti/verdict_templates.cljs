(ns lupapalvelu.ui.matti.verdict-templates
  (:require [clojure.string :as s]
            [lupapalvelu.matti.shared :as matti]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [rum.core :as rum]))


(defonce current-template (atom nil))

#_(rum/defcs add-section < (rum/local "" ::selected)
  [local-state {:keys [sections state] :as options}]
  (let [removed (filter #(path/meta? (assoc options
                                            :path [%]
                                            :state state)
                                     :removed?)
                        (map :id sections))
        selected* (::selected local-state)]
    (when-not (contains? (set removed) @selected*)
     (common/reset-if-needed! selected* (first removed)))
    (when-not (empty? removed)
      [:span
       [:select.dropdown
        {:value @selected*
         :on-change (common/event->state selected*)}
        (map (fn [n]
               [:option {:value n :key n} (common/loc n)])
             removed)]
       [:button.primary
        {:on-click #(path/flip-meta {:path [@selected*]
                                     :state state}
                                    :removed?)}
        [:i.lupicon-circle-plus]
        [:span (common/loc "add")]]])))

(defn updater [template-id {:keys [state path]}]
  (service/save-draft-value template-id
                            path
                            @(path/state path state)
                            (fn [{modified :modified}]
                              (path/set-top-meta state :modified modified))))

(rum/defcs verdict-name < (rum/local "" ::name)
                          (rum/local false ::editing?)
  [{name ::name editing? ::editing?} {state :state}]
  (if @editing?
    (letfn [(success-fn [{modified :modified}]
              (swap! state #(assoc % :modified modified)))
            (save-fn []
              (reset! editing? false)
              (swap! state #(assoc % :name @name))
              (service/set-template-name @(path/state [:id] state)
                                         @name
                                         success-fn))]
      [:span
       [:input.grid-style-input.row-text
        {:type      "text"
         :value     @name
         :on-change (common/event->state name)
         :on-key-up #(when-not (s/blank? @name)
                       (case (.-keyCode %)
                         13 (save-fn)               ;; Save on Enter
                         27 (reset! editing? false) ;; Cancel on Esc
                         :default))}]
       [:button.primary
        {:disabled (s/blank? @name)
         :on-click save-fn}
        [:i.lupicon-save]]])
    (do (common/reset-if-needed! name @(path/state [:name] state))
        [:span @name
         [:button.ghost.no-border
          {:on-click #(swap! editing? not)}
          [:i.lupicon-pen]]])))

(defn verdict-template
  [{:keys [sections state] :as options}]
  [:div
   [:button.ghost
    {:on-click #(reset! current-template nil)}
    [:i.lupicon-chevron-left]
    [:span (common/loc "back")]]
   [:div.matti-grid-2
    [:div.row
     [:div.col-1
      [:span.row-text
       (common/loc "matti.edit-verdict-template")
       (verdict-name options)]]
     [:div.col-1.col--right
      [:div [:span.row-text
             (if-let [published @(path/state [:published] state)]
               (common/loc :matti.last-published
                           (js/util.finnishDateTime published))
               (common/loc :matti.template-not-published))]
       [:button.ghost (common/loc :matti.publish)]]]]]
   (for [sec sections]
     [:div {:key (:id sec)}
      (sections/section (assoc sec
                               :path (path/extend (:id sec))
                               :state state))])
   #_(add-section options)])

(defn reset-template [template-id template-name {data :draft}]
  (reset! current-template
          (assoc data
                 :name template-name
                 :id template-id
                 :_meta
                 {:updated   (partial updater template-id)
                  :can-edit? true?})))

(defn new-template [{:keys [id name] :as options}]
  (reset-template id name options))

(defn template-list
  [templates]
  [:div
   [:h4 (common/loc "matti.verdict-templates")]
   [:table
    [:thead
     [:tr
      [:th (common/loc "matti-verdict-template")]
      [:th (common/loc "matti-verdict-template.published")]
      [:th]]]
    [:tbody
     (for [{:keys [id name published]} (rum/react templates)]
       [:tr
        [:td name]
        [:td (js/util.finnishDate published)]
        [:td
         [:div.matti-buttons
          [:button.ghost
           {:on-click #(service/fetch-template-draft id
                                                     (partial reset-template id name))}
           (common/loc "edit")]
          [:button.ghost (common/loc "matti.copy")]
          [:button.ghost (common/loc "remove")]]]])]]])

(rum/defc verdict-templates < rum/reactive
  [_]
  (when-not (empty? (rum/react service/schemas))
    [:div
     (if (rum/react current-template)
       (verdict-template (assoc  matti/default-verdict-template
                                 :state current-template))
       [:div
        (when (not-empty (rum/react service/template-list))
          (template-list service/template-list))
        [:button.positive
         {:on-click #(service/new-template new-template)}
         [:i.lupicon-circle-plus]
         [:span (common/loc "add")]]])
     [:p (str (rum/react current-template))]
     [:p (str (rum/react service/template-list))]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (verdict-templates (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc
         :auth-model (aget componentParams "authModel")
         :dom-id (name domId))
  (service/fetch-schemas)
  (service/fetch-template-list)
  (mount-component))
