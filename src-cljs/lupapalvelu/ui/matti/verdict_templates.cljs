(ns lupapalvelu.ui.matti.verdict-templates
  (:require [lupapalvelu.matti.shared :as matti]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [rum.core :as rum]))


(defonce current-template (atom {}))

(defn response->state [state kw]
  (fn [response]
    (swap! state #(assoc % kw (kw response)))))

(defn updater [{:keys [state path]}]
  (service/save-draft-value (path/value [:id] state)
                            path
                            (path/value path state)
                            (response->state state :modified)))


(rum/defc verdict-template-name < rum/reactive
  [{state :state}]
  (letfn [(handler-fn [value]
            (reset! (path/state [:name] state) value)
            (service/set-template-name @(path/state [:id] state)
                                       value
                                       (response->state state :modified)))]
    (components/pen-input {:value      (rum/react (path/state [:name] state))
                           :handler-fn handler-fn})))


(rum/defc verdict-template-publish < rum/reactive
  [{state :state}]
  [:div [:span.row-text.row-text--margin
         (if-let [published (path/react [:published] state)]
           (common/loc :matti.last-published
                       (js/util.finnishDateAndTime published))
           (common/loc :matti.template-not-published))]
   [:button.ghost
    {:on-click #(service/publish-template (path/value [:id] state)
                                          (response->state state :published))}
        (common/loc :matti.publish)]])

(rum/defc verdict-template-saved < rum/reactive
  [{state :state}]
  [:span.saved-info
   (common/loc :matti.last-saved
               (js/util.finnishDateAndTime (path/react [:modified] state)))])

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
       (verdict-template-name options)]]
     [:div.col-1.col--right
      (verdict-template-publish options)]]
    [:div.row.row--tight
     [:div.col-2.col--right
      (verdict-template-saved options)]]]
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

(defn toggle-delete [id deleted]
  [:button.primary.outline
   {:on-click #(service/toggle-delete-template id (not deleted) identity)}
   (common/loc (if deleted "matti-restore-template" "remove"))])

(rum/defcs verdict-template-list < rum/reactive
  (rum/local false ::show-deleted)
  [{show-deleted ::show-deleted}]
  (let [templates (rum/react service/template-list)]
    [:div.matti-template-list
     [:h2 (common/loc "matti.verdict-templates")]
     (when (some :deleted templates)
       [:div.checkbox-wrapper
        [:input {:type "checkbox"
                 :id "show-deleted"
                 :value @show-deleted}]
        [:label.checkbox-label
         {:for "show-deleted"
          :on-click #(swap! show-deleted not)}
         (common/loc :handler-roles.show-all)]])
     (let [filtered (if @show-deleted
                      templates
                      (remove :deleted templates))]
       (when (seq filtered)
         [:table.matti-templates-table
          [:thead
           [:tr
            [:th (common/loc "matti-verdict-template")]
            [:th (common/loc "matti-verdict-template.published")]
            [:th]]]
          [:tbody
           (for [{:keys [id name published deleted]} filtered]
             [:tr {:key id}
              [:td name]
              [:td (js/util.finnishDate published)]
              [:td
               [:div.matti-buttons
                (when-not deleted
                  [:button.primary.outline
                   {:on-click #(service/fetch-template id reset-template)}
                   (common/loc "edit")])
                [:button.primary.outline
                 {:on-click #(service/copy-template id reset-template)}
                 (common/loc "matti.copy")]
                (toggle-delete id deleted)]]])]]))
     [:button.positive
      {:on-click #(service/new-template new-template)}
      [:i.lupicon-circle-plus]
      [:span (common/loc "add")]]]))

(rum/defc verdict-templates < rum/reactive
  [_]
  (when-not (empty? (rum/react service/schemas))
    [:div
     (if (rum/react (rum/cursor-in current-template [:_meta :editing?]))
       (verdict-template (assoc  matti/default-verdict-template
                                 :state current-template))
       (verdict-template-list))]))

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
