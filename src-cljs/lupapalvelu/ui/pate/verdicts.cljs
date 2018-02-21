(ns lupapalvelu.ui.pate.verdicts
  (:require [clojure.set :as set]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.pate.layout :as layout]
            [lupapalvelu.ui.pate.components :as pate-components]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.sections :as sections]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared_util :as util]))

(enable-console-print!)

(defonce args (atom {}))

(defn- can-edit? []
  (state/auth? :edit-pate-verdict))

(defn- can-view? []
  (state/auth? :pate-verdicts))

(defn- can-publish? []
  (state/auth? :publish-pate-verdict))

(defn updater
  ([{:keys [state path] :as options} value]
   (service/edit-verdict @state/application-id
                         (path/value [:info :id] state/current-verdict)
                         path
                         value
                         (service/update-changes-and-errors state/current-verdict
                                                            options)))
  ([{:keys [state path] :as options}]
   (updater options (path/value path state))))

(defn- can-edit-verdict? [{published :published}]
  (and (can-edit?)
       (not published)))

(defn reset-verdict [{:keys [verdict references filled]}]
  (println " RESET ::: " verdict)
  (reset! state/current-verdict
          (when verdict
            {:state (:data verdict)
             :info  (assoc (dissoc verdict :data)
                           :filled? filled)
             :_meta {:updated             updater
                     :highlight-required? (-> verdict :published not)
                     :enabled?            (can-edit-verdict? verdict)
                     :published?          (:published verdict)
                     :upload.filedata     (fn [_ filedata & kvs]
                                            (apply assoc filedata
                                                   :target {:type :verdict
                                                            :id   (:id verdict)}
                                                   kvs))
                     :upload.include?     (fn [_ {target :target :as att}]
                                            (= (:id target) (:id verdict)))}}))
  (reset! state/references references)
  (reset! state/current-view (if verdict ::verdict ::list)))

(defn update-application-id []
  (let [app-id (js/lupapisteApp.services.contextService.applicationId)]
    (when (common/reset-if-needed! state/application-id app-id)
      (if app-id
        (do (reset! state/auth-fn js/lupapisteApp.models.applicationAuthModel.ok)
            (when (can-edit?)
              (service/fetch-application-verdict-templates app-id))
            (when (can-edit?)
              (service/fetch-application-phrases app-id))
            (service/fetch-verdict-list app-id))
        (do (reset! state/template-list [])
            (reset! state/phrases [])
            (reset! state/verdict-list nil))))))

(defn with-back-button [component]
  [:div
   (lupapalvelu.ui.attachment.components/dropzone)
   [:div.operation-button-row
    [:button.secondary
     {:on-click #(reset-verdict nil)}
     [:i.lupicon-chevron-left]
     [:span (common/loc "back")]]]
   component])

(rum/defc verdict-section-header < rum/reactive
  [{:keys [schema] :as options}]
  [:div.pate-grid-1.section-header
   {:class (path/css options)}
   (when (and (not (-> schema :buttons? false?))
              (path/enabled? options))
     [:div.row.row--tight
      [:div.col-1.col--right
       [:div.verdict-buttons
        [:button.primary.outline
         {:on-click #(path/flip-meta options :editing?)}
         (common/loc (if (path/react-meta options :editing?)
                       :close
                       :edit))]]]])])

(defmethod sections/section-header :verdict
  [options _]
  (verdict-section-header options))

(rum/defc toggle-all < rum/reactive
  [{:keys [schema _meta] :as options}]
  (when (path/enabled? options)
    (let [all-sections  (map :id (:sections schema))
          meta-map (rum/react _meta)
          open-sections (filter #(get meta-map (util/kw-path % :editing?))
                                all-sections)]
      [:a.pate-left-space
       {:on-click #(swap! _meta (fn [m]
                                  (->> (or (not-empty open-sections)
                                           all-sections)
                                       (map (fn [id]
                                              [(util/kw-path id :editing?)
                                               (empty? open-sections)]))
                                       (into {})
                                       (merge m))))}
       (common/loc (if (seq open-sections)
                     :pate.close-all
                     :pate.open-all))])))

(rum/defcs verdict < rum/reactive
  (rum/local false ::wait?)
  [{wait?* ::wait?} {:keys [schema state info _meta] :as options}]
  (let [_ (println "Verdict!??? " schema)
        _ (println (path/react :filled? info))
        published (path/react :published info)
        yes-fn    (fn []
                    (reset! wait?* true)
                    (reset! (rum/cursor-in _meta [:enabled?]) false)
                    (service/publish-and-reopen-verdict  @state/application-id
                                                         (path/value :id info)
                                                         reset-verdict))]
    [:div.pate-verdict
     [:div.pate-grid-2
      (when (and (or (path/enabled? options)
                     (rum/react wait?*))
                 (not published))
        [:div.row
         [:div.col-2.col--right
          (components/icon-button {:text-loc :verdict.submit
                                   :class    (common/css :primary :pate-left-space)
                                   :icon     :lupicon-circle-section-sign
                                   :wait?    wait?*
                                   :enabled? (and (path/react :filled? info)
                                                  (can-publish?))
                                   :on-click (fn []
                                               (hub/send "show-dialog"
                                                         {:ltitle          "areyousure"
                                                          :size            "medium"
                                                          :component       "yes-no-dialog"
                                                          :componentParams {:ltext "verdict.confirmpublish"
                                                                            :yesFn yes-fn}}))})
          (components/link-button {:url      (js/sprintf "/api/raw/preview-pate-verdict?id=%s&verdict-id=%s"
                                                         @state/application-id
                                                         (path/value :id info))
                                   :enabled? (and (path/enabled? options)
                                                  (path/react :filled? info))
                                   :disabled published
                                   :text-loc :pdf.preview})]])
      (if published
        [:div.row
         [:div.col-2.col--right
          [:span.verdict-published
           (common/loc :pate.verdict-published
                       (js/util.finnishDate published))]]]
        [:div.row.row--tight
         [:div.col-1
          (pate-components/required-fields-note options)]
         [:div.col-1.col--right
          (toggle-all options)
          (pate-components/last-saved options)]])]
     (sections/sections options :verdict)]))

(rum/defcs new-verdict < rum/reactive
  (rum/local nil ::template)
  [{template* ::template}]
  (let [templates (rum/react state/template-list)]

    (if (empty? templates)
      [:div.pate-note (path/loc :pate.no-verdict-templates)]
      (let [items (map #(set/rename-keys % {:id :value :name :text})
                       templates)]
        (when-not (rum/react template*)
          (common/reset-if-needed! template*
                                   (:value (or (util/find-by-key :default? true items)
                                               (first items)))))
        [:div.pate-grid-6
         [:div.row
          (layout/vertical {:label :pate-verdict-template
                            :align :full}
                           (components/dropdown template*
                                                {:items items}))
          (layout/vertical [:button.positive
                            {:on-click #(service/new-verdict-draft @state/application-id
                                                                   @template*
                                                                   reset-verdict)}
                            [:i.lupicon-circle-plus]
                            [:span (common/loc :application.verdict.add)]])]]))))


(defn- confirm-and-delete-verdict [app-id verdict-id]
  (hub/send  "show-dialog"
             {:ltitle          "areyousure"
              :size            "medium"
              :component       "yes-no-dialog"
              :componentParams {:ltext "pate.delete-verdict-draft"
                                :yesFn #(service/delete-verdict app-id
                                                                verdict-id
                                                                reset-verdict)}}))

(rum/defc verdict-list < rum/reactive
  [verdicts app-id]
  [:div
   [:h2 (common/loc "application.tabVerdict")]
   [:table.pate-verdicts-table
    [:tbody
     (map (fn [{:keys [id published modified] :as verdict}]
           [:tr {:key id}
            [:td [:a {:on-click #(service/open-verdict app-id id reset-verdict)}
                  (path/loc (if published :pate-verdict :pate-verdict-draft))]]
            [:td (if published
                   (common/loc :pate.published-date (js/util.finnishDate published))
                   (common/loc :pate.last-saved (js/util.finnishDateAndTime modified)))]
            [:td (when (and (can-edit-verdict? verdict) (not published))
                   [:i.lupicon-remove.primary
                    {:on-click #(confirm-and-delete-verdict app-id id)}])]])
          verdicts)]]
   (when (state/auth? :new-pate-verdict-draft)
     (new-verdict))])

(rum/defc verdicts < rum/reactive
  {:will-mount   (fn [state]
                   (update-application-id)
                   (assoc state
                          ::hub-id (hub/subscribe :contextService::enter
                                                  update-application-id)))
   :will-unmount (fn [state]
                   (hub/unsubscribe (::hub-id state))
                   (dissoc state ::hub-id))}
  []
  (when (and (rum/react state/application-id)
             (rum/react state/schemas)
             (rum/react state/verdict-list))
    [:div
     (case (rum/react state/current-view)
       ::list (verdict-list @state/verdict-list @state/application-id)
       ::verdict (let [{dictionary :dictionary :as schema} (get shared/verdict-schemas
                                                                (shared/permit-type->category (js/lupapisteApp.models.application.permitType)))]
                   (with-back-button (verdict (assoc (state/select-keys state/current-verdict
                                                                        [:state :info :_meta])
                                                     :schema (dissoc schema :dictionary)
                                                     :dictionary dictionary
                                                     :references state/references)))))]))

(defn mount-component []
  (when (common/feature? :pate)
    (rum/mount (verdicts)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId _]
  (when (common/feature? :pate)
    (swap! args assoc
           :dom-id (name domId))

    (service/fetch-schemas)
    (reset-verdict nil)
    (mount-component)))
