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
            [sade.shared-util :as util]))

(defonce args (atom {}))

(defn- can-edit? []
  (state/auth? :edit-pate-verdict))

(defn- can-edit-verdict? [{published :published}]
  (and (can-edit?)
       (not published)))

(defn update-application-id []
  (if-let [app-id (js/lupapisteApp.services.contextService.applicationId)]
    (do (service/fetch-verdict-list app-id)
        (when (common/reset-if-needed! state/application-id app-id)
          (do (reset! state/auth-fn js/lupapisteApp.models.applicationAuthModel.ok)
              (when (can-edit?)
                (service/fetch-application-verdict-templates app-id))
              (when (can-edit?)
                (service/fetch-application-phrases app-id)))))
    (do (reset! state/template-list [])
        (reset! state/phrases [])
        (reset! state/verdict-list nil))))

(defn open-verdict [arg]
  (common/open-page :pate-verdict
                    @state/application-id
                    (get arg :verdict-id arg)))

(rum/defcs new-verdict < rum/reactive
  (rum/local nil ::template)
  [{template* ::template}]
  (let [templates (rum/react state/template-list)]

    (if (empty? templates)
      [:div.pate-note (path/loc :pate.no-verdict-templates)]
      (let [items (map #(set/rename-keys % {:id :value :name :text})
                       templates)
            selected (rum/react template*)]
        (when-not (util/find-by-key :value selected items)
          (common/reset-if-needed! template*
                                   (:value (or (util/find-by-key :default? true items)
                                               (first items)))))
        [:div.pate-grid-6
         [:div.row
          (layout/vertical {:label :pate-verdict-template
                            :align :full}
                           (components/dropdown template*
                                                {:items   items
                                                 :choose? false}))
          (layout/vertical [:button.positive
                            {:on-click #(service/new-verdict-draft @state/application-id
                                                                   @template*
                                                                   open-verdict)}
                            [:i.lupicon-circle-plus]
                            [:span (common/loc :application.verdict.add)]])]]))))


(defn- confirm-and-delete-verdict [app-id verdict-id]
  (hub/send  "show-dialog"
             {:ltitle          "areyousure"
              :size            "medium"
              :component       "yes-no-dialog"
              :componentParams {:ltext "pate.delete-verdict-draft"
                                :yesFn #(service/delete-verdict app-id
                                                                verdict-id)}}))

(rum/defc verdict-list < rum/reactive
  [verdicts app-id]
  [:div
   [:h2 (common/loc "application.tabVerdict")]
   (if (empty? verdicts)
     (when-not (state/auth? :new-pate-verdict-draft)
       (common/loc-html :p :application.verdictDesc))
     [:table.pate-verdicts-table
      [:tbody
       (map (fn [{:keys [id published modified] :as verdict}]
              [:tr {:key id}
               [:td [:a {:on-click #(open-verdict id)}
                     (path/loc (if published :pate-verdict :pate-verdict-draft))]]
               [:td (if published
                      (common/loc :pate.published-date (js/util.finnishDate published))
                      (common/loc :pate.last-saved (js/util.finnishDateAndTime modified)))]
               [:td (when (and (can-edit-verdict? verdict) (not published))
                      [:i.lupicon-remove.primary
                       {:on-click #(confirm-and-delete-verdict app-id id)}])]])
            verdicts)]])
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
             (rum/react state/verdict-list))
    (verdict-list @state/verdict-list @state/application-id)))

(defn mount-component []
  (when (common/feature? :pate)
    (rum/mount (verdicts)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId _]
  (when (common/feature? :pate)
    (swap! args assoc
           :dom-id (name domId))
    (mount-component)))
