(ns lupapalvelu.ui.application.foremen-view
  (:require [lupapalvelu.application.foreman :as foreman]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.application.foreman-termination :as foreman-termination]
            [lupapalvelu.ui.application.foreman-util :as foreman-util]
            [lupapalvelu.ui.application.invite-foreman :as foreman-invite]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.user :as user]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]
            [sade.strings :as ss]
            [sade.util :as util]))

(def state->color
  {"ok"           "bg-green-600"
   "acknowledged" "bg-green-600"
   "declined"     "bg-red-600"
   "rejected"     "bg-red-600"
   "in-progress"  "bg-blue-900"
   "pending"      "bg-blue-900"
   "new"          "bg-blue-900"
   "declared"     "bg-blue-900"
   "submitted"    "bg-blue-900"
   "approved"     "bg-blue-900"
   "required"     "bg-orange-500"
   "open"         "bg-orange-500"})

(defn state-col [state]
  [:td.relative
   [:div.absolute.top-0.left-0.h-full.w-2
    {:class (get state->color state)}]])

(defn add-foreman-button [{:keys [unmet-role tj-role tj-v3?]}]
  (when (and unmet-role
             ;; show the button only for the old type of foreman tasks
             (not tj-v3?)
             (user/authority?))
    [:button.primary.mr-2
     {:on-click #(>evt [::foreman-invite/open-foreman-dialog tj-role])}
     (loc :application.parties.addForeman)]))

(defn terminate-foreman-button [{:keys [tj-document id doc-state ended]}]
  (when (foreman-util/termination-req-allowed
          {:doc-state          doc-state
           :termination        (:foremanTermination tj-document)
           :command-authorized (or (auth/application-auth? :request-foreman-termination)
                                   (auth/application-auth? :terminate-foreman))
           :foreman-is-current-user (= (:email tj-document) (.email (user/current-user)))
           :is-authority       (user/authority?)
           :is-applicant       (user/applicant?)
           :ended              ended})
    [:button.secondary.mr-2
     {:on-click #(>evt [::data/open-terminate-foreman id tj-document foreman-termination/termination-dialog])}
     (loc :foreman.terminate)]))

(defn cancel-termination-button [{:keys [termination-requested email tj-doc-id id tj-v3?]}]
  (when (and (auth/application-auth? :cancel-foreman-termination-request)
             termination-requested)
    [:button.secondary.mr-2
     {:on-click (fn []
                  (components/confirm-dialog
                    "areyousure"
                    "foreman.termination.cancel.confirm"
                    #(if tj-v3?
                       (>evt [::data/cancel-foreman-termination-request-v3 email tj-doc-id])
                       (>evt [::data/cancel-foreman-termination-request email id]))))}
     (loc :cancel)]))

(defn confirm-termination-button [{:keys [termination-requested tj-doc-id id tj-v3?]}]
  (when (and termination-requested
             (auth/application-auth? :confirm-foreman-termination))
    [:button.primary.mr-2
     {:on-click (fn []
                  (components/confirm-dialog
                    "areyousure"
                    "foreman.terminate.confirm"
                    #(if tj-v3?
                       (>evt [::data/confirm-termination-v3 tj-doc-id])
                       (>evt [::data/confirm-termination id]))))}
     (loc :approve)]))

(defn view-foreman-button [{:keys [tj-doc-id id]}]
  (when tj-doc-id
    ;; For V3 this takes to the same app, for others
    ;; the link might take to another app.
    [:a.right
     {:href (rfe/href :parties
                      {:app-id id}
                      {:focus-doc tj-doc-id})}
     (loc :view)]))

(defn responsibility-col [{:keys [tj-id tj-document doc-state termination-requested termination-request-ts started ended tj-doc-id id tj-v3?]}]
  (let [now                       (js/Date.now)
        started                   (or started (:started tj-document))
        vastuuaika-loc            (when (and (not (contains? #{"required" "open"} doc-state)) (or started ended))
                                    (cond (and ended
                                               (< ended now))
                                          "terminated"
                                          termination-requested
                                          "requested"
                                          (or (not started)
                                              (and started
                                                   (< started now)))
                                          "not-requested"))
        editing-start-date?       (<sub [::data/editing-start-date? tj-id])
        responsibility-start-date (<sub [::data/responsibility-start-date tj-id])]
    [:td (if vastuuaika-loc
           [:div.responsibilities
            [:div.font-bold (loc (str "foreman.termination.status." vastuuaika-loc))]
            [:div (str (js/util.finnishDate started) "-" (js/util.finnishDate ended))]
            (when (and (or (= doc-state "acknowledged")
                           (= doc-state "declared"))
                       (or (and (not tj-v3?)
                                (auth/application-auth? :set-foreman-start-date))
                           (and tj-v3?
                                (auth/application-auth? :set-foreman-start-date-v3))))
              (if editing-start-date?
                [:div
                 [day-edit/day-edit
                  started
                  {:id       "termination-date"
                   :class    "day-edit"
                   :invalid? (< now responsibility-start-date)
                   :callback #(>evt [::data/set-responsibility-start-date tj-id (some-> % (.valueOf))])}]
                 [:button.primary.mr-2
                  {:disabled (not (some-> responsibility-start-date (< now)))
                   :on-click #(if tj-v3?
                                (>evt [::data/set-foreman-start-date-v3 tj-doc-id])
                                (>evt [::data/set-foreman-start-date id]))}
                  (loc :save)]
                 [:button.secondary.mr-2
                  {:on-click #(>evt [::data/toggle-edit-start-date tj-id])}
                  (loc :cancel)]]
                [:button.ghost.no-border
                 {:on-click #(>evt [::data/toggle-edit-start-date tj-id])}
                 [:i.lupicon-pen]]))]
           "-")
     (when (and termination-requested
                termination-request-ts)
       [:div (str (loc :foreman.termination.status.requested) " " (js/util.finnishDate termination-request-ts))])]))

(defn view []
  (r/with-let [_ (>evt [::hub/subscribe "cljs-foreman-task-created"
                        #(>evt [::data/fetch-foreman-applications])
                        ::foreman-task-created-sub])]
    (let [foreman-items (<sub [::data/foreman-applications])
          schema-names  (->> foreman-items
                             (map #(-> % foreman-util/foreman-item->tj-document :schema-info :name keyword))
                             set)
          old-foremen?  (schema-names :tyonjohtaja-v2)]
      [:<>
       [:div
        [:h2 (loc :tyonjohtajat)]
        [components/icon-button {:icon     :lupicon-check
                                 :class    :primary.close
                                 :text-loc :application.parties.addForeman.description
                                 :test-id  :open-invite-foreman
                                 :wait?    false
                                 :on-click #(>evt [::foreman-invite/open-foreman-dialog nil])}]]
       (when old-foremen?
         [:div.help-text.visible.mt-2 (loc :foreman.old-foreman-applications)])
       [:table
        [:thead
         [:tr
          [:th ""]
          [:th (loc :foreman.status)]
          [:th (loc :name)]
          [:th (loc :foreman.tasks)]
          [:th (loc :foreman.condition)]
          [:th (loc :foreman.substitute)]
          [:th (loc :foreman.termination.document.duration)]
          [:th ""]]]
        [:tbody
         (doall
           (for [{:keys [unmet-role id latest-verdict-status required state
                         termination-requested started ended termination-request-ts]
                  :as   item} foreman-items]
             (let [tj-document      (foreman-util/foreman-item->tj-document item)
                   tj-doc-id        (:id tj-document)
                   tj-v3?           (-> tj-document :schema-info :name keyword (= :tyonjohtaja-v3))
                   tj-id            (if tj-v3? tj-doc-id id)
                   doc-state        (or (:state tj-document) ; submitted, declared, acknowledged, approved or rejected
                                        (when (not tj-v3?) state) ; foremanVerdictGiven, acknowledged
                                        (when unmet-role "required")
                                        "open")
                   tj-role          (foreman-util/foreman-item->tj-role item)
                   fullname         (foreman/tj-document->fullname tj-document)
                   email            (foreman/tj-document->email tj-document)
                   permit-condition (if required "yes" "no")
                   substitute       (some-> tj-document :data :sijaistus)
                   substitute-name  (str (some-> substitute :sijaistettavaHloEtunimi :value) " " (some-> substitute :sijaistettavaHloSukunimi :value))
                   processed-state  (if (or unmet-role tj-v3?) doc-state (:status latest-verdict-status))]
               ^{:key (or tj-id (common/unique-id "tj"))}
               [:tr
                [state-col processed-state]
                [:td
                 (loc (str "foreman.task."
                           processed-state
                           (when (and (= (:type tj-document) "application") (= processed-state "acknowledged"))
                             ".application")
                           ".label"))]
                [:td (if (ss/blank? fullname) "-" fullname)]
                [:td
                 [:div (->> tj-role (util/kw-path :osapuoli.tyonjohtaja.kuntaRoolikoodi) loc)]
                 (when (:termination-reason item)
                   [:div.mt-5
                    [:div.font-bold (loc :foreman.termination.reason)]
                    [:div (:termination-reason item)]])]
                [:td (loc permit-condition)]
                [:td (if (ss/blank? substitute-name) "-" substitute-name)]
                [responsibility-col (util/mmap tj-id tj-document doc-state termination-requested termination-request-ts started ended tj-doc-id id tj-v3?)]
                [:td
                 [add-foreman-button (util/mmap unmet-role tj-role tj-v3?)]
                 [terminate-foreman-button (util/mmap tj-document id doc-state ended)]
                 [cancel-termination-button (util/mmap termination-requested email tj-doc-id id tj-v3?)]
                 [confirm-termination-button (util/mmap termination-requested tj-doc-id id tj-v3?)]
                 [view-foreman-button (util/mmap tj-v3? tj-doc-id id)]]])))]]])
    (finally
      (>evt [::hub/unsubscribe ::foreman-task-created-sub]))))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (>evt [::data/clear-foreman-data])
  (>evt [::data/fetch-foreman-applications])
  (ui-dom/mount! mounter dom-id))
