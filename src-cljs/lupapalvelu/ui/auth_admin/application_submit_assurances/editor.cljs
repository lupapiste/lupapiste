(ns lupapalvelu.ui.auth-admin.application-submit-assurances.editor
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.reselect :as reselect]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(def new-assurance-id "new-assurance")

(defn- set-visible [{:keys [db]} _]
  {:db (assoc-in db [::data :visible?] true)})

(defn- register-auth-model-listener [_ _]
  {:fx [[:dispatch [:auth/refresh]]]})

(rf/reg-event-fx ::toggle-view
  [(rf/inject-cofx ::org-subs/url-org-id)]
  (fn [{:keys [db] :as cofx} [_ visible?]]
    (let [org-id (::org-subs/url-org-id cofx)]
      (if visible?
        (nutil/merge-every-fn cofx [[set-visible]
                                    [register-auth-model-listener]
                                    [org-subs/set-org-id org-id]
                                    (when org-id
                                      [org-subs/fetch-org {:org-id org-id}])])
        {:db (dissoc db ::data)}))))

(rf/reg-sub ::edit-allowed?
  :<- [:auth/global? :save-application-submit-assurance-template]
  :-> identity)

(rf/reg-event-db ::start-edit
  (fn [db [_ assurance-template]]
    (assoc-in db [::data :edit (:id assurance-template)]
              (select-keys assurance-template
                           [:id :text :required :all-operations :operations]))))

(rf/reg-event-db ::add-new-assurance
  (fn [db [_]]
    (assoc-in db [::data :edit new-assurance-id] {:all-operations true
                                                  :required       true})))

(rf/reg-event-db ::cancel-edit
  (fn [db [_ id]]
    (util/dissoc-in db [::data :edit id])))

(rf/reg-sub ::edited-data
  (fn [db [_ id]]
    (get-in db [::data :edit id])))

(rf/reg-sub ::save-disabled?
  (fn [db [_ id]]
    (->>
      (get-in db [::data :edit id :text])
      ((apply juxt (common/supported-langs)))
      (some ss/blank?))))

(rf/reg-event-fx ::save-assurance-template
  (fn [{db :db} [_ id]]
    (when-let [assurance (-> (get-in db [::data :edit id])
                             (update :text (partial util/map-values ss/trim)))]
      {:action/command {:name                  (if (= id new-assurance-id)
                                                 :create-application-submit-assurance-template
                                                 :save-application-submit-assurance-template)
                        :show-saved-indicator? true
                        :params                {:organizationId (org-subs/db->org-id db)
                                                :assurance      assurance}
                        :success               [::create-or-modify-success id]}})))

(rf/reg-event-fx ::remove-assurance-template
  (fn [{db :db} [_ id confirmed?]]
    (if confirmed?
      {:action/command {:name                  :remove-application-submit-assurance-template
                        :show-saved-indicator? true
                        :params                {:organizationId (org-subs/db->org-id db)
                                                :assuranceId    id}
                        :success               [::create-or-modify-success id]}}
      {:dialog/show {:ltitle   :areyousure
                     :text     (common/loc :auth-admin.assurances-editor.remove-confirm)
                     :type     :yes-no
                     :callback #(>evt [::remove-assurance-template id true])}})))

(defn- finish-edit [id {:keys [db]}]
  (when id
    {:db (util/dissoc-in db [::data :edit id])}))

(rf/reg-event-fx ::create-or-modify-success
  (fn [{:keys [db] :as cofx} [_ _response assurance-id]]
    (let [org-id (org-subs/db->org-id db)]
      (nutil/merge-every-fn cofx [[(partial finish-edit assurance-id)]
                                  [org-subs/fetch-org {:org-id org-id :force? true}]]))))

(rf/reg-sub ::editing?
  (fn [db [_ id]]
    (boolean (get-in db [::data :edit id]))))

(defn loc-op [op]
  (common/loc (util/kw-path :operations op)))

(rf/reg-sub ::org-operations
  :<- [::org-subs/selected-operations]
  (fn [operations _]
    (->> operations
         vals
         (apply concat)
         (map (fn [op] {:text (loc-op op) :value op})))))

(defn db->assurance-ids [db]
  (->> (org-subs/db->org db)
       :application-submit-assurance-templates
       (mapv :id)))

(rf/reg-event-fx ::move
  (fn [{db :db} [_ id direction]]
    {:action/command {:name    :store-application-submit-assurance-order
                      :params  {:organizationId (org-subs/db->org-id db)
                                :assuranceIds   (util/move-item direction (partial = id) (db->assurance-ids db))}
                      :success [::create-or-modify-success]}}))

(rf/reg-sub ::assurance-position
  :<- [::org-subs/application-submit-assurance-templates]
  (fn [assurances [_ id]]
    (let [index (util/find-index (partial = id) (map :id assurances))]
      {:first? (zero? index)
       :last?  (= index (-> assurances count dec))})))

(defn move-buttons [id]
  (when (<sub [::edit-allowed?])
    (when-not (= id new-assurance-id)
      (let [{:keys [first? last?]} (<sub [::assurance-position id])]
        [:span.flex.gap-1
         [components/icon-button {:class     [:secondary :no-twp]
                                  :icon      :lupicon-arrow-up
                                  :disabled? first?
                                  :on-click  #(>evt [::move id :up])}]
         [components/icon-button {:class     [:secondary :no-twp]
                                  :icon      :lupicon-arrow-down
                                  :disabled? last?
                                  :on-click  #(>evt [::move id :down])}]]))))

(defn button-row [{:keys [id] :as assurance-template} editing?]
  [:div.flex.gap-4
   [move-buttons id]
   (if-not editing?
     [:<>
      (when (<sub [::edit-allowed?])
        [components/icon-button {:class    [:secondary :no-twp :bg-white]
                                 :text-loc :remove
                                 :icon     :lupicon-remove
                                 :on-click #(>evt [::remove-assurance-template id])}])
      [components/icon-button {:class    [:primary :no-twp]
                               :text-loc (if (<sub [::edit-allowed?]) :edit :price-catalogue.open)
                               :icon     :lupicon-pen
                               :on-click #(>evt [::start-edit assurance-template])}]]
     [:<>
      [components/icon-button {:class    [:secondary :no-twp :bg-white]
                               :text-loc (if (<sub [::edit-allowed?]) :cancel :close)
                               :on-click #(>evt [::cancel-edit id])}]
      (when (<sub [::edit-allowed?])
        [components/icon-button {:class     [:primary :no-twp]
                                 :text-loc  :save
                                 :disabled? (<sub [::save-disabled? id])
                                 :on-click  #(>evt [::save-assurance-template id])}])])])

(defn template-row [{:keys [id] :as assurance-template}]
  (let [editing?       (<sub [::editing? id])
        {:keys [text operations all-operations required]} (if editing?
                                                            (<sub [::edited-data id])
                                                            assurance-template)
        org-operations (<sub [::org-operations])
        disabled?      (not (<sub [::edit-allowed?]))]
    [:div.border.border-ui-gray-221
     [:div.bg-ui-gray-221.p-2.px-4.flex.justify-between.items-center
      [:div.font-bold (get text (keyword (common/get-current-language)))]
      [button-row assurance-template editing?]]
     (when editing?
       [:div.p-4.bg-white
        (into [:div]
              (for [lang (common/supported-langs)]
                [:label.block.pb-4.required
                 [:div.pb-1
                  (common/loc (util/kw-path :lang lang))
                  [:span.text-ui-red " *"]]
                 [:div
                  [:input
                   {:class     (concat [:w-full :p-2]
                                       (if (= "" (-> text lang))
                                         [:border "border-fail"]
                                         [:border-b :border-ui-black]))
                    :type      "text"
                    :value     (-> text lang)
                    :disabled  disabled?
                    :on-change #(>evt [:value/set ::data :edit id :text lang (util/tval %)])}]]]))
        [components/toggle
         required
         {:text-loc    :auth-admin.assurances-editor.required
          :callback    #(>evt [:value/set ::data :edit id :required %])
          :controlled? true
          :disabled?   disabled?
          :prefix      "checkbox"
          :class       "wrapper--no-border"}]
        [components/toggle
         all-operations
         {:text-loc    :auth-admin.assurances-editor.all-operations
          :callback    #(>evt [:value/set ::data :edit id :all-operations %])
          :controlled? true
          :disabled?   disabled?
          :prefix      "checkbox"
          :class       "wrapper--no-border"}]
        (when-not all-operations
          [:div.mt-4
           [:label (common/loc :auth-admin.assurances-editor.choose-operations)
            [:div.pt-2
             (reselect/reselect operations {:items       org-operations
                                            :multi?      true
                                            :disabled?   disabled?
                                            :callback    #(>evt [:value/set ::data :edit id :operations %])
                                            :placeholder (common/loc "choose")})]]])])]))

(defn assurance-editor
  "A configuration view for application submit assurances"
  []
  (let [assurance-templates (<sub [::org-subs/application-submit-assurance-templates])]
    [:<>
     [:h2.mb-4 (common/loc "auth-admin.assurances-editor.title")]
     [:div.twp
      (into [:ul]
            (for [assurance-template assurance-templates]
              [:li.mb-4
               [template-row assurance-template]]))

      (let [adding-new? (<sub [::editing? new-assurance-id])]
        (if adding-new?
          [template-row {:id new-assurance-id}]
          (when (<sub [::edit-allowed?])
            [:div
             [components/icon-button {:class    [:secondary :no-twp]
                                      :text-loc :auth-admin.assurances-editor.add-new
                                      :icon     :lupicon-plus
                                      :on-click #(>evt [::add-new-assurance])}]])))]]))

(defonce mounter (ui-dom/init :reagent (fn [] assurance-editor)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
