(ns lupapalvelu.ui.matti.verdicts
  (:require [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.matti.layout :as layout]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.phrases :as phrases]
            [lupapalvelu.ui.matti.sections :as sections]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
            [clojure.set :as set]
            [rum.core :as rum]
            [sade.shared_util :as util]))

(defonce args (atom {}))

(defn reset-verdict [{:keys [data settings] :as verdict}]
  (reset! state/current-verdict data)
  (reset! state/references settings)
  (reset! state/current-view (if verdict ::verdict ::list)))

(defn update-application-id []
  (let [app-id (lupapisteApp.services.contextService.applicationId)]
    (when (common/reset-if-needed! state/application-id app-id)
      (if app-id
        (do (service/fetch-application-verdict-templates app-id)
            (service/fetch-application-phrases app-id))
        (do (reset! state/template-list [])
            (reset! state/phrases []))))))

(defn with-back-button [component]
  [:div
   [:button.secondary
    {:on-click #(reset-verdict nil)}
    [:i.lupicon-chevron-left]
    [:span (common/loc "back")]]
   component])

(rum/defc verdict-section-header < rum/reactive
  [options]
  [:div.matti-grid-1.section-header
   [:div.row.row--tight
    [:div.col-1.col--right
     [:div.verdict-buttons
      [:button.primary.outline
       {:on-click #(path/flip-meta options :editing?)}
       (common/loc (if (path/react-meta? options :editing?)
                     :close
                     :edit))]]]]])

(defn verdict
  [{:keys [sections state settings] :as options}]
  [:div.matti-verdict
   [:div.matti-grid-2
    [:div.row.row--tight
     [:div.col-2.col--right
      (layout/last-saved options)]]]
   (for [sec sections]
     (sections/section (assoc sec
                              :path (path/extend (:id sec))
                              :state state)
                       verdict-section-header))])


(rum/defcs new-verdict < rum/reactive
  (rum/local nil ::template)
  (rum/local :small ::complexity)
  [{template* ::template
    complexity* ::complexity}]
  (let [items (map #(set/rename-keys % {:id :value :name :text})
                   (rum/react state/template-list))]
    (when-not (rum/react template*)
      (common/reset-if-needed! template*
                               (:value (or (util/find-by-key :default? true items)
                                           (first items)))))
    [:div.matti-grid-6
     [:div.row
      (layout/vertical {:label :matti-verdict-template
                        :align :full}
                       (components/dropdown template*
                            {:items items}))
      (layout/vertical [:button.positive
                        {:on-click #(service/new-verdict-draft @state/application-id
                                                               @template*
                                                               reset-verdict)}
                        [:i.lupicon-circle-plus]
                        [:span (common/loc :application.verdict.add)]])]]))

(rum/defc verdict-list < rum/reactive
  []
  [:div
   [:h2 (common/loc "application.tabVerdict")]
   [:p "Application id:" (rum/react state/application-id)]
   [:p "Permit type:" (lupapisteApp.models.application.permitType)]
   (new-verdict)])

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
             (rum/react state/phrases))
    [:div
     (case (rum/react state/current-view)
       ::list (verdict-list)
       ::verdict (with-back-button (verdict (assoc (get shared/verdict-schemas
                                                        (shared/permit-type->category (lupapisteApp.models.application.permitType)))
                                                   :state state/current-verdict))))
     (components/debug-atom state/current-verdict "state/current-verdict")
     (components/debug-atom state/references "state/references")]))

(defn mount-component []
  (when (common/feature? :matti)
    (rum/mount (verdicts)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId _]
  (when (common/feature? :matti)
    (swap! args assoc
           :dom-id (name domId))
    (service/fetch-schemas)
    (reset-verdict nil)
    (mount-component)))
