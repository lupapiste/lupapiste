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

(enable-console-print!)

(defonce args (atom {}))

(defn- can-edit? []
  (state/auth? :edit-pate-verdict))

(defn- can-edit-verdict? [{published :published}]
  (and (can-edit?)
       (not published)))

(defn open-verdict [arg]
  (common/open-page :pate-verdict
                    @state/application-id
                    (get arg :verdict-id arg)))

(defn replace [arg]
  (service/replace-verdict @state/application-id
                           @state/replacement-verdict
                           (get arg :verdict-id arg))
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
                                                                   (if @state/replacement-verdict replace open-verdict)
                                                                   @state/replacement-verdict)}
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

(defn- confirm-and-replace-verdict [verdict verdict-id]
  (hub/send  "show-dialog"
             {:ltitle          "areyousure"
              :size            "medium"
              :component       "yes-no-dialog"
              :componentParams {:ltext "pate.replace-verdict"
                                :yesFn #(do
                                          (reset! state/verdict-list [verdict])
                                          (reset! state/replacement-verdict verdict-id))}}))

(defn- replace-verdict [verdict verdicts]
  (when-let [replacement-verdict (first (filter #(= (get-in verdict [:replacement :replaces]) (:id %)) verdicts))]
    (common/loc :pate.replacing.verdict (:verdict-section replacement-verdict))))

(rum/defc verdict-list < rum/reactive
  [verdicts app-id replacement-verdict]
  [:div
   (if replacement-verdict
     [:h2 (common/loc :application.tabVerdict.replacement.title)]
     [:h2 (common/loc :application.tabVerdict)])
   (if (empty? verdicts)
     (when-not (state/auth? :new-pate-verdict-draft)
       (common/loc-html :p :application.verdictDesc))
     [:div
     (if replacement-verdict
       [:h3.table-title (common/loc :application.tabVerdict.replacement)])
     [:table.pate-verdicts-table
      [:thead
       [:tr
        [:th (common/loc :pate.verdict-table.verdict)]
        [:th (common/loc :pate.verdict-table.verdict-date)]
        [:th (common/loc :pate.verdict-table.verdict-giver)]
        [:th (common/loc :pate.verdict-table.last-edit)]
        [:th ""]]]
      [:tbody
       (map (fn [{:keys [id published modified verdict-date handler] :as verdict}]
              [:tr {:key id}
               [:td [:a {:on-click #(open-verdict id)}
                     (if published
                       (str "§" (:verdict-section verdict) " " (path/loc :pate-verdict))
                       (path/loc :pate-verdict-draft))
                     (if (some? (get-in verdict [:replacement :replaces]))
                       (replace-verdict verdict verdicts))]]
               [:td (if published
                      (js/util.finnishDate verdict-date))]
               [:td handler]
               [:td (if published
                      (common/loc :pate.published-date (js/util.finnishDate published))
                      (common/loc :pate.last-saved (js/util.finnishDateAndTime modified)))]
               (if replacement-verdict
                 [:td]
                 [:td (if (and (can-edit-verdict? verdict) (not published))
                        [:a
                         {:on-click #(confirm-and-delete-verdict app-id id)}
                         (common/loc :pate.verdict-table.remove-verdict)]
                        [:a
                         {:on-click #(confirm-and-replace-verdict verdict id)}
                         (common/loc :pate.verdict-table.replace-verdict)])])])
            (sort-by :modified > verdicts))]]])
   (when (state/auth? :new-pate-verdict-draft)
     (new-verdict))])

(rum/defc verdicts < rum/reactive
  []
  (when (and (rum/react state/application-id)
             (rum/react state/verdict-list)
             (rum/react state/auth-fn))
    (verdict-list @state/verdict-list @state/application-id @state/replacement-verdict)))

(defn bootstrap-verdicts []
  (when-let [app-id (js/pageutil.hashApplicationId)]
    (reset! state/template-list [])
    (reset! state/verdict-list nil)
    (reset! state/replacement-verdict nil)
    (state/refresh-application-auth-model app-id
                                          #(when (can-edit?)
                                             (service/fetch-verdict-list app-id)
                                             (service/fetch-application-verdict-templates app-id)))))

(defn mount-component []
  (when (common/feature? :pate)
    (rum/mount (verdicts)
               (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId _]
  (when (common/feature? :pate)
    (swap! args assoc
           :dom-id (name domId))
    (bootstrap-verdicts)
    (mount-component)))
