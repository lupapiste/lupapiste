(ns lupapalvelu.ui.task.task-view
  "Render task views using `docgen-group`.

  Also see the sibling component `lupapalvelu.ui.task.task-controls`, which
  renders the various controls (approve task, reject task...)."
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.router-api :as router]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.docgen.core :as docgen]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.field.docgen-group :as field-docgen-group]
            [lupapalvelu.ui.dom :as ui-dom]
            [re-frame.core :as rf]))

(defn- set-task
  [{:keys [db]} [_ task-id]]
  (if task-id
    ;; NOTE: we clear any stale data by writing `::data`
    {:db (assoc db ::data {:task-id task-id})}
    {:db (dissoc db ::data)}))

(rf/reg-event-fx ::init
  (fn [{:keys [db] :as cofx} [_ visible?]]
    (if visible?
      (let [{:keys [app-id task-id]} (router/db->path-parameters db)]
        (nutil/merge-every-fn cofx [[set-task task-id]
                                    ;; If we are reloading the browser in the task view, this
                                    ;; refresh might complete before processing the app, which means
                                    ;; the auth model is overwritten. However, the app contains
                                    ;; the fresh auth model so it should be identical.
                                    [auth/refresh-doc-auth-fx {:app-id   app-id
                                                               :doc-id   task-id
                                                               :category :tasks}]
                                    ;; Try to mimic the old behaviour:
                                    ;; The task view will refresh _both_ the category auth models
                                    ;; but also the application auth model, wrt. a specific task.
                                    ;; This is a bit silly, as the application model contains 99%
                                    ;; unrelated commands.
                                    [auth/refresh-task-auth-fx {:app-id  app-id
                                                                :task-id task-id}]
                                    ;; If the user e.g. approves the task, we must update the auth model
                                    [hub/subscribe-fx
                                     "application-model-updated"
                                     (fn []
                                       (rf/dispatch [::auth/refresh-task-auth {:app-id  app-id
                                                                               :task-id task-id}]))
                                     ::hub-subscription]]))
      (let [old-task-id (get-in db [::data :task-id])]
        (nutil/merge-every-fn cofx [[set-task nil]
                                    ;; Cleanup the task-specific application auth model (it's quite large)
                                    ;; when leaving the task view.
                                    [auth/clear-task-auth old-task-id]
                                    [hub/unsubscribe-fx ::hub-subscription]])))))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::task-id :<- [::data] :-> :task-id)

(defn- view
  "Display a single task; no accordions, just a docgen group."
  []
  (when-let [task-id (<sub [::task-id])]
    (let [args {:app-id               (<sub [::app/application-id])
                :disabled?            false
                :doc-id               task-id
                :override-auth-source :auth/task?
                :path                 []
                :render-fn            docgen/render-children
                :schema               (<sub [::docs/schema task-id])}]
      (when (and (:app-id args)
                 (:doc-id args))
        [field-docgen-group/render args]))))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id {:dom-id dom-id}))
