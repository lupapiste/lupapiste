(ns lupapalvelu.ui.matti.verdict-templates
  (:require [lupapalvelu.matti.shared :as matti]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [rum.core :as rum]))


(defonce current-template (atom nil))

(defn response->state [state kw]
  (fn [response]
    (swap! state #(assoc % kw (kw response)))))

(defn updater [{:keys [state path]}]
  (service/save-draft-value (path/value [:id] state)
                            path
                            (path/value path state)
                            (response->state state :modified)))


(defn verdict-name [{state :state}]
  "Called from pen-input component."
  (letfn [(handler-fn [value]
            (reset! (path/state [:name] state) value)
            (service/set-template-name @(path/state [:id] state)
                                       value
                                       (response->state state :modified)))]
    (components/pen-input {:value      @(path/state [:name] state)
                           :handler-fn handler-fn})))


(defn verdict-template
  [{:keys [sections state] :as options}]
  [:div.verdict-template
   [:button.ghost
    {:on-click #(reset! current-template nil)}
    [:i.lupicon-chevron-left]
    [:span (common/loc "back")]]
   [:div.matti-grid-2
    [:div.row.row--tight
     [:div.col-1
      [:span.row-text.header
       (common/loc "matti.edit-verdict-template")
       (verdict-name options)]]
     [:div.col-1.col--right
      [:div [:span.row-text.row-text--margin
             (if-let [published (path/value [:published] state)]
               (common/loc :matti.last-published
                           (js/util.finnishDateAndTime published))
               (common/loc :matti.template-not-published))]
       [:button.ghost
        {:on-click #(service/publish-template @(path/state [:id] state)
                                              (response->state state :published))}
        (common/loc :matti.publish)]]]]
    [:div.row.row--tight
     [:div.col-2.col--right
      [:span.saved-info (common/loc :matti.last-saved
                                    (js/util.finnishDateAndTime (path/value [:modified] state)))]]]]
   (for [sec sections]
     (sections/section (assoc sec
                              :path (path/extend (:id sec))
                              :state state)))])

(defn reset-template [{:keys [id name modified published draft]}]
  (reset! current-template
          (assoc draft
                 :name name
                 :id id
                 :modified modified
                 :published published
                 :_meta
                 {:updated   updater
                  :can-edit? true?
                  :editing? true})))

(defn new-template [options]
  (reset-template options))

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
           {:on-click #(service/fetch-template id reset-template)}
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
         [:span (common/loc "add")]]])]))

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
