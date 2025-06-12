(ns lupapalvelu.ui.task.task-controls
  "Render the controls (approve task etc.) on the task view"
  (:require ["react" :as react]
            [goog.object :as googo]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.feature.flags :as flags]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.task.task-view :as task-view]
            [re-frame.core :as rf]))

(rf/reg-sub ::validation-errors?
  (fn [[_ doc-id]]
    (rf/subscribe [::validation/doc-validation doc-id]))
  (fn [validation-errors [_ doc-id]] ;; TODO this should probably be handled in the backend LPK-8178
    (->> validation-errors
         (some (fn [[path errors]]
                 (and (->> errors
                           (map :error)
                           (some #{"illegal-value:required" "warn"}))
                      (let [schema (<sub [::field-schemas/field-schema doc-id path])]
                        (<sub [::flags/include-schema? schema])))))
         boolean)))

(rf/reg-sub ::review-done-ok?
  (fn [[_ doc-id override-auth-source]]
    [(rf/subscribe [::auth/auth? doc-id :review-done override-auth-source])
     (rf/subscribe [::validation-errors? doc-id])])
  (fn [[auth-ok? errors?]]
    (and auth-ok? (not errors?))))

(rf/reg-event-fx ::processing
  (fn [{:keys [db]} [_ task-id processing?]]
    {:db (assoc-in db [::processing task-id] processing?)}))

(rf/reg-sub ::processing :-> ::processing)

(rf/reg-sub ::processing?
  :<- [::processing]
  (fn [processing [_ task-id]]
    (boolean (get processing task-id))))

(rf/reg-sub ::final-final-review?
  (fn final-review-inputfn [[_ task-id]]
    [(rf/subscribe [::value/current-field-value task-id [:katselmus :tila]])
     (rf/subscribe [::value/current-field-value task-id [:katselmuksenLaji]])])
  (fn [[review-status review-type]]
    (and (= review-type "loppukatselmus")
         (= review-status "lopullinen"))))

(defn subscribe!
  [observable task-id on-change]
  (react/useEffect
    (fn []
      (let [^js sub (.subscribe observable (fn [value]
                                             (when task-id
                                               (on-change value))))]
        (when task-id
          (on-change (.peek observable)))
        (fn []
          (.dispose sub))))
    #js [observable task-id]))

(defn- view*
  [{:keys [processing-obs
           on-return
           on-review-done
           on-resend-review
           on-approve
           on-reject
           on-mark-faulty
           on-delete]}]
  (let [task-id (<sub [::task-view/task-id])]
    ;; The various operations can take several seconds during which the UI should be disabled.
    ;; The `taskPageController` specifies a KO observable called `processing`, which indicates
    ;; when a command is being executed.
    (subscribe! processing-obs
                task-id
                (fn [processing?]
                  (>evt [::processing task-id processing?])))
    (when task-id
      (let [override-auth-source       :auth/task?
            processing?                (<sub [::processing? task-id])
            climate-reports-not-valid? (and (<sub [::final-final-review? task-id])
                                            (not (<sub [::auth/auth? task-id :climate-reports-valid])))]
        [:div
         [:div.clear-fix.inner-margins
          ;; Return to application
          [components/icon-button {:class    :secondary
                                   :text-loc :application.return
                                   :icon     :lupicon-chevron-start
                                   :on-click on-return}]
          ;; Mark review as done
          (when (<sub [::auth/auth? task-id :review-can-be-marked-done override-auth-source])
            [components/icon-button {:class     :positive
                                     :text-loc  :task.review-done
                                     :icon      :lupicon-eye
                                     :disabled? (or processing?
                                                    climate-reports-not-valid?)
                                     :enabled?  (<sub [::review-done-ok? task-id override-auth-source])
                                     :on-click  on-review-done}])
          ;; Resend review to backing system
          (when (<sub [::auth/auth? task-id :resend-review-to-backing-system override-auth-source])
            [components/icon-button {:class     :positive
                                     :text-loc  :task.resend-review
                                     :icon      :lupicon-circle-arrow-right
                                     :disabled? processing?
                                     :on-click  on-resend-review}])
          ;; Approve review
          (when (<sub [::auth/auth? task-id :approve-task override-auth-source])
            [components/icon-button {:class     :positive
                                     :text-loc  :task.approve
                                     :disabled? processing?
                                     :on-click  on-approve}])
          ;; Reject review
          (when (<sub [::auth/auth? task-id :reject-task override-auth-source])
            [components/icon-button {:class     :negative
                                     :text-loc  :task.reject
                                     :disabled? processing?
                                     :on-click  on-reject}])
          ;; Mark review as faulty
          (when (<sub [::auth/auth? task-id :mark-review-faulty override-auth-source])
            ;; Supplying the `notes` isn't strictly necessary, as the application (and
            ;; thus the `documentDataService`) is refreshed when the task state changes.
            ;; However, this becomes more relevant later when the old docgen is removed.
            (let [notes (<sub [::value/current-field-value task-id [:katselmus :huomautukset :kuvaus]])]
              [components/icon-button {:class     :negative
                                       :text-loc  :task.mark-faulty
                                       :icon      :lupicon-remove
                                       :disabled? processing?
                                       :on-click  (fn [_]
                                                    (on-mark-faulty notes))}]))
          ;; Delete whole task
          (when (<sub [::auth/auth? task-id :delete-task override-auth-source])
            [components/icon-button {:class     :secondary.right
                                     :text-loc  :remove
                                     :disabled? processing?
                                     :on-click  on-delete}])]
         ;; If this is the final final-review (lopullinen loppukatselmus) and there exists some climate-reports,
         ;; then all climate-reports must be valid.
         (when climate-reports-not-valid?
           [:div.text-ui-red.mt-2 [common/loc :task.missing-climate-report-data]])]))))

(defn- view [args]
  [:f> view* args])

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id params]
  (ui-dom/mount! mounter dom-id {:dom-id           dom-id
                                 ;; The `processing` observable
                                 :processing-obs   (googo/get params "processingObs")
                                 ;; Callbacks to various functions in `task.js`
                                 :on-return        (googo/get params "onReturn")
                                 :on-review-done   (googo/get params "onReviewDone")
                                 :on-resend-review (googo/get params "onResendReview")
                                 :on-approve       (googo/get params "onApprove")
                                 :on-reject        (googo/get params "onReject")
                                 :on-mark-faulty   (googo/get params "onMarkFaulty")
                                 :on-delete        (googo/get params "onDelete")}))
