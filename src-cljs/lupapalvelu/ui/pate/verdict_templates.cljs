(ns lupapalvelu.ui.pate.verdict-templates
  (:require [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.settings :as settings]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]))


(defn updater [{:keys [state info path]}]
  (service/save-draft-value (path/value [:id] info)
                            path
                            (path/value path state)
                            (common/response->state info :modified)))


(rum/defc verdict-template-name < rum/reactive
  [{info* :info}]
  (letfn [(handler-fn [value]
            (reset! (path/state [:name] info*) value)
            (service/set-template-name @(path/state [:id] info*)
                                       value
                                       (common/response->state info* :modified)))]
    (components/pen-input {:value      (rum/react (path/state [:name] info*))
                           :handler-fn handler-fn
                           :disabled? (not (state/auth? :set-verdict-template-name))})))


(rum/defc verdict-template-publish < rum/reactive
  [{info* :info}]
  (let [published (path/react [:published] info*)]
    [:div [:span.row-text.row-text--margin
           (if published
             (common/loc :pate.last-published
                         (js/util.finnishDateAndTime published))
             (common/loc :pate.template-not-published))]
     [:button.ghost
      {:disabled (or (not (state/auth? :publish-verdict-template))
                     (> published
                        (path/react [:modified] info*)))
       :on-click #(service/publish-template (path/value [:id] info*)
                                            (common/response->state info* :published))}
      (common/loc :pate.publish)]]))


(defn reset-template [{draft :draft :as template}]
  (reset! state/current-template
          (when template
            {:state draft
             :info (dissoc template :draft)
             :_meta {:updated   updater
                     :enabled?  (state/auth? :save-verdict-template-draft-value)
                     :editing?  true}}))
  (reset! state/current-view (if template ::template ::list)))

(defn with-back-button [component]
  [:div
   [:button.ghost
    {:on-click #(reset-template nil)}
    [:i.lupicon-chevron-left]
    [:span (common/loc "back")]]
   component])

(defn verdict-template
  [{:keys [schema state] :as options}]
  [:div.verdict-template
   [:div.pate-grid-2
    [:div.row.row--tight
     [:div.col-1
      [:span.row-text.header
       (common/loc "pate.edit-verdict-template")
       (verdict-template-name options)]]
     [:div.col-1.col--right
      (verdict-template-publish options)]]
    [:div.row.row--tight
     [:div.col-2.col--right
      (pate-components/last-saved options)]]]
   (sections/sections options :verdict-template)])

(defn new-template [options]
  (reset-template options))

(defn toggle-delete [id deleted]
  [:button.primary.outline
   {:on-click #(service/toggle-delete-template id (not deleted) identity)}
   (common/loc (if deleted :pate-restore :remove))])

(defn set-category [category]
  (reset! state/current-category category)
  (settings/fetch-settings category ))


(rum/defc category-select < rum/reactive
  []
  [:div.pate-grid-6
   [:div.row
    [:div.col-2.col--full
     [:select.dropdown
      {:value (rum/react state/current-category)
       :on-change #(set-category (.. % -target -value))}
      (->> (rum/react state/categories)
           (map (fn [cid]
                  {:value cid
                   :text (common/loc (str "pate-" cid))}))
           (sort-by :text)
           (map (fn [{:keys [value text]}]
                  [:option {:key value :value value} text])))]]
    [:div.col-4.col--right
     [:button.ghost
      {:on-click #(reset! state/current-view ::settings)}
      [:span (common/loc :auth-admin.organization.properties)]]]]])

(rum/defcs verdict-template-list < rum/reactive
  (rum/local false ::show-deleted)
  [{show-deleted ::show-deleted}]
  (let [templates (filter #(= (rum/react state/current-category)
                              (:category %))
                          (rum/react state/template-list))]
    [:div.pate-template-list
     [:h2 (common/loc "pate.verdict-templates")]
     (category-select)
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
         [:table.pate-templates-table
          [:thead
           [:tr
            [:th (common/loc "pate-verdict-template")]
            [:th (common/loc "pate-verdict-template.published")]
            [:th]]]
          [:tbody
           (for [{:keys [id name published deleted]} filtered]
             [:tr {:key id}
              [:td (if deleted
                     name
                     [:a {:on-click #(service/fetch-template id reset-template)}
                      name])]
              [:td (js/util.finnishDate published)]
              [:td
               [:div.pate-buttons
                (when-not deleted
                  [:button.primary.outline
                   {:on-click #(service/fetch-template id reset-template)}
                   (common/loc "edit")])
                [:button.primary.outline
                 {:on-click #(service/copy-template id reset-template)}
                 (common/loc "pate.copy")]
                (toggle-delete id deleted)]]])]]))
     [:button.positive
      {:on-click #(service/new-template @state/current-category new-template)}
      [:i.lupicon-circle-plus]
      [:span (common/loc :pate.add-verdict-template)]]]))

(rum/defc verdict-templates < rum/reactive
  []
  (when (and (rum/react state/schemas)
             (rum/react state/categories)
             (rum/react state/phrases))
    [:div
     (case (rum/react state/current-view)
       ::template
       (with-back-button
         (verdict-template
          (merge
           {:schema     (dissoc shared/default-verdict-template
                                :dictionary)
            :dictionary (:dictionary shared/default-verdict-template)
            :references state/references}
           (state/select-keys state/current-template [:state :info :_meta]))))

       ::list
       [:div (verdict-template-list) (phrases/phrases-table)]

       ::settings
       (when-let [full-schema ((keyword @state/current-category) shared/settings-schemas)]
         (with-back-button
           (settings/verdict-template-settings
            (merge
             {:schema     (dissoc full-schema :dictionary)
              :dictionary (:dictionary full-schema)
              :references state/references
              :state state/settings}
             (state/select-keys state/settings-info [:info :_meta]))))))]))

(defonce args (atom {}))

(defn mount-component []
  (when (common/feature? :pate)
    (rum/mount (verdict-templates)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId]
  (when (common/feature? :pate)
    (swap! args assoc
           :dom-id (name domId))
    (reset! state/auth-fn lupapisteApp.models.globalAuthModel.ok)
    (service/fetch-schemas)
    (service/fetch-template-list)
    (service/fetch-categories (fn [categories]
                                (set-category (first categories))))
    (service/fetch-organization-phrases)
    (reset-template nil)
    (mount-component)))
