(ns lupapalvelu.ui.pate.verdict-templates
  (:require [clojure.set :as set]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.settings-schemas :as settings-schemas]
            [lupapalvelu.pate.verdict-template-schemas :as template-schemas]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.settings :as settings]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn updater
  ([{:keys [state info path] :as options} value]
   (service/save-draft-value (path/value [:id] info)
                             path
                             value
                             (service/update-changes-and-errors state/current-template
                                                                options)))
  ([{:keys [path state] :as options}]
   (updater options (path/value path state))))

(defn open-settings
  [& [template-id]]
  (reset! state/current-view {:view ::settings
                              :back template-id}))

(defn- loc-text [loc-key & args]
  (apply common/loc
         (cons (if (util/=as-kw (rum/react state/current-category)
                                :contract)
                 (get {:pate.verdict-templates      :pate.contract-templates
                       :pate-verdict-template       :pate.contract.template
                       :pate.add-verdict-template   :pate.add-contract-template
                       :pate.template-not-published :pate.contract.template.not-published
                       :pate.edit-verdict-template  :pate.contract.template.edit}
                      (keyword loc-key) loc-key)
                 loc-key)
               args)))

(rum/defc verdict-template-name < rum/reactive
  [{info* :info}]
  (letfn [(handler-fn [value]
            (reset! (path/state [:name] info*) value)
            (service/set-template-name @(path/state [:id] info*)
                                       value
                                       (common/response->state info* :modified)))]
    (components/pen-input {:value     (rum/react (path/state [:name] info*))
                           :callback  handler-fn
                           :disabled? (not (state/auth? :set-verdict-template-name))
                           :test-id   :template-name})))


(rum/defc verdict-template-publish < rum/reactive
  [{info* :info}]
  (let [published (path/react [:published] info*)]
    [:div [:span.row-text.row-text--margin
           (common/add-test-id {} :template-state)
           (if published
             (loc-text :pate.last-published
                       (js/util.finnishDateAndTime published))
             (loc-text :pate.template-not-published))]
     (components/icon-button
      {:disabled? (or (not (state/auth? :publish-verdict-template))
                      (> published
                         (max (path/react [:modified] info*)
                              (path/react [:info :modified] state/settings-info)))
                      (false? (path/react :filled? info*))
                      (false? (path/react [:info :filled?] state/settings-info)))
       :on-click  #(service/publish-template (path/value [:id] info*)
                                             (common/response->state info* :published))
       :class     :primary
       :icon      :lupicon-megaphone
       :text-loc  :pate.publish
       :test-id   :publish-template})]))


(defn reset-template [{draft :draft :as template}]
  (reset! state/current-template
          (when template
            {:state draft
             :info  (set/rename-keys (dissoc template :draft)
                                     {:filled :filled?})
             :_meta {:updated       updater
                     :open-settings (partial open-settings (:id template))
                     :enabled?      (state/auth? :save-verdict-template-draft-value)
                     :editing?      true}}))
  (reset! state/current-view {:view (if template ::template ::list)}))

(defn open-template [template-id]
  ((if (state/auth? :update-and-open-verdict-template)
     service/fetch-updated-template
     service/fetch-template) template-id reset-template))

(defn update-settings-dependencies [{:keys [modified draft]}]
  (when-not (= (path/value [:info :modified] state/current-template)
               modified)
    (swap! state/current-template
           (fn [template]
             (-> template
                 (update :state #(merge % (select-keys draft
                                                       [:plans :reviews])))
                 (assoc-in [:info :modified] modified))))))

(defn with-back-button [component]
  [:div
   (components/icon-button {:class    :ghost
                            :on-click #(if-let [template-id (path/value :back state/current-view)]
                                         (do (when (state/auth? :update-and-open-verdict-template)
                                               (service/fetch-updated-template template-id
                                                                               update-settings-dependencies))
                                             (reset! state/current-view {:view ::template}))
                                         (reset-template nil))
                            :icon     :lupicon-chevron-left
                            :text-loc :back
                            :test-id  :back})
   component])

(defn verdict-template
  [{:keys [schema state info] :as options}]
  [:div.verdict-template
   [:div.pate-grid-2
    [:div.row.row--tight
     [:div.col-1
      [:span.row-text.header
       (loc-text :pate.edit-verdict-template)
       (verdict-template-name options)]]
     [:div.col-1.col--right
      (verdict-template-publish options)]]
    [:div.row.row--tight
     [:div.col-1
      (pate-components/required-fields-note (assoc options :test-id :required-template))
      (pate-components/required-fields-note {:info (rum/cursor-in state/settings-info [:info])}
                                            (components/text-and-link {:text-loc :pate.settings-required-fields
                                                                       :click    #(open-settings (path/value :id info))
                                                                       :test-id  :required-settings}))]
     [:div.col-1.col--right
      (pate-components/last-saved options)]]]
   (sections/sections options :verdict-template)])

(defn new-template [options]
  (reset-template options))

(defn toggle-delete [id deleted test-id]
  (components/icon-button
   {:class    [:primary :outline]
    :on-click #(service/toggle-delete-template id (not deleted) identity)
    :text-loc (if deleted :pate-restore :remove)
    :icon (if deleted :lupicon-undo :lupicon-remove)
    :test-id [(if deleted :restore :delete) test-id]}))

(defn set-category [category]
  (reset! state/current-category category)
  (settings/fetch-settings category))

(rum/defc category-select < rum/reactive
  []
  [:div.pate-grid-6
   [:div.row
    [:div.col-2.col--full
     [:select.dropdown
      (common/add-test-id {:value     (rum/react state/current-category)
                           :on-change #(set-category (.. % -target -value))}
                          :category-select)
      (->> (rum/react state/categories)
           (map (fn [cid]
                  {:value cid
                   :text  (common/loc (str "pate-" cid))}))
           (sort-by :text)
           (map (fn [{:keys [value text]}]
                  [:option {:key value :value value} text])))]]
    [:div.col-4.col--right
     (components/icon-button
      {:icon     :lupicon-gear
       :class    :ghost
       :on-click #(open-settings)
       :text-loc :auth-admin.organization.properties
       :test-id  :open-settings})]]])

(rum/defcs verdict-template-list < rum/reactive
  (rum/local false ::show-deleted)
  [{show-deleted ::show-deleted}]
  (let [templates (filter #(= (rum/react state/current-category)
                              (:category %))
                          (rum/react state/template-list))]
    [:div.pate-template-list
     [:h2 (loc-text :pate.verdict-templates)]
     (category-select)
     (when (some :deleted templates)
       (components/toggle show-deleted
                          {:test-id  :show-deleted-templates
                           :text-loc :handler-roles.show-all
                           :prefix   :checkbox}))
     (let [filtered (if @show-deleted
                      templates
                      (remove :deleted templates))]
       (when (seq filtered)
         [:table.pate-templates-table
          [:thead
           [:tr
            [:th (loc-text :pate-verdict-template)]
            [:th (common/loc "pate-verdict-template.published")]
            [:th]]]
          [:tbody
           (map-indexed (fn [i {:keys [id name published deleted]}]
                          (let [tid (str "template-" i)]
                            [:tr {:key id}
                             [:td (if deleted
                                    name
                                    [:a (common/add-test-id {:on-click #(open-template id)}
                                                            :link tid)
                                     name])]
                             [:td (common/add-test-id {} :published tid)
                              (js/util.finnishDate published)]
                             [:td
                              [:div.pate-buttons
                               (when-not deleted
                                 (components/icon-button
                                  {:class    [:primary :outline]
                                   :on-click #(open-template id)
                                   :text-loc :edit
                                   :icon     :lupicon-pen
                                   :test-id [:open tid]}))
                               (components/icon-button
                                {:class    [:primary :outline]
                                 :on-click #(service/copy-template id reset-template)
                                 :text-loc :pate.copy
                                 :icon     :lupicon-copy
                                 :test-id  [:copy tid]})
                               (toggle-delete id deleted tid)]]]))
                        filtered)]]))
     (components/icon-button {:icon     :lupicon-circle-plus
                              :class    :positive
                              :text     (loc-text :pate.add-verdict-template)
                              :on-click #(service/new-template @state/current-category
                                                               new-template)
                              :enabled? (state/auth? :new-verdict-template)
                              :test-id  :add-template})]))

(rum/defc verdict-templates < rum/reactive
  []
  (when (and (rum/react state/categories)
             (rum/react state/phrases))
    [:div
     (case (:view (rum/react state/current-view))
       ::template
       (with-back-button
         (verdict-template
          (merge
           {:schema     (dissoc (template-schemas/verdict-template-schema @state/current-category)
                                :dictionary)
            :dictionary (:dictionary (template-schemas/verdict-template-schema @state/current-category))
            :references state/references}
           (state/select-keys state/current-template [:state :info :_meta]))))

       ::list
       [:div (verdict-template-list) (phrases/phrases-table)]

       ::settings
       (when-let [full-schema (settings-schemas/settings-schema @state/current-category)]
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

(defn ^:export start [domId params]
  (when (common/feature? :pate)
    (swap! args assoc
           :dom-id (name domId))
    (reset! state/org-id (js/ko.unwrap (common/oget params "orgId")))
    (reset! state/auth-fn lupapisteApp.models.globalAuthModel.ok)
    (service/fetch-template-list)
    (service/fetch-categories (fn [categories]
                                (set-category (first categories))))
    (service/fetch-organization-phrases)
    (reset-template nil)
    (mount-component)))
