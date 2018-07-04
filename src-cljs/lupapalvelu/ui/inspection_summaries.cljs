(ns lupapalvelu.ui.inspection-summaries
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.components.datepicker :as date]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.hub :as hub]
            [lupapalvelu.ui.rum-util :as rum-util]
            [cljs-time.coerce :as tc]
            [cljs-time.format :as tf]))

(enable-console-print!)

(defonce args (atom {}))

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(def date-formatter (tf/formatter "d.M.yyyy"))

(defn save-indicator [visible-atom]
  [:span.form-indicator.form-input-saved
   {:style {:display (when-not (rum/react visible-atom) "none")}}
   [:span.icon]])

(def empty-component-state {:applicationId ""
                            :operations []
                            :summaries []
                            :templates []
                            :fileStatuses {}
                            :view {:bubble-visible false
                                   :new {:operation nil
                                         :template nil}
                                   :selected-summary-id nil}})


(def component-state  (atom empty-component-state))
(def selected-summary (rum-util/derived-atom
                        [component-state]
                        (fn [state]
                          (when-let [selected-id (get-in state [:view :selected-summary-id])]
                            (->> (:summaries state)
                                 (find-by-key :id selected-id))))))

(defn- refresh
  ([] (refresh nil))
  ([cb]
   (auth/refresh-auth-models-for-category component-state :inspection-summaries)
   (query :inspection-summaries-for-application
          (fn [data]
            (swap! component-state assoc
                   :operations (:operations data)
                   :templates  (:templates data)
                   :summaries  (:summaries data)
                   :fileStatuses {})
            (when cb (cb data)))
          :id (-> @component-state :applicationId))))

(defn- operation-description-for-select [op]
  (string/join " - " (remove empty? [(:description op) (:op-identifier op)])))

(defn- update-summary-view [id]
  (swap! component-state assoc-in [:view :selected-summary-id] id))

(defn to-bindable-file [target-id file]
  {:type {:type-group "katselmukset_ja_tarkastukset"
          :type-id    "tarkastusasiakirja"}
   :fileId (aget file "fileId")
   :group {:groupType "operation"
           :operations [(select-keys (:op @selected-summary) [:id :name])]}
   :target {:type "inspection-summary-item"
            :id target-id}})

(defn unsubscribe-if-done [target-id]
  (let [statuses (filter #(= target-id (:target-id %))
                         (vals (:fileStatuses @component-state)))]
    (when (every? #(= "done" (:status %)) statuses)
      (doseq [hub-id (distinct (map :subs-id statuses))]
        (hub/unsubscribe hub-id))
      (refresh))))

(defn bind-attachment-callback [target-id event]
  (let [clj-event (js->clj event :keywordize-keys true)]
    (swap! component-state update-in [:fileStatuses (keyword (:fileId clj-event))] assoc :status (:status clj-event))
    (unsubscribe-if-done target-id)))

(defn got-files [target-id hub-event]
  (let [files          (.-files hub-event)
        fileIds        (map #(aget % "fileId") files)
        bindable-files (map (partial to-bindable-file target-id) files)
        subs-id        (upload/subscribe-bind-attachments-status {:status "done" :jobStatus "done"}
                                                                 (partial bind-attachment-callback target-id))
        create-filestatuses-fn (fn [statuses fid]
                                 (assoc statuses (keyword fid)
                                                 {:subs-id subs-id
                                                  :target-id target-id}))]
    (swap! component-state update :fileStatuses #(reduce create-filestatuses-fn % fileIds))
    (upload/bind-attachments (clj->js bindable-files))))

(defn- commit-target-name-edit [target-id val]
  (if (not (empty? val))
    (let [cmd-name (if target-id
                     :edit-inspection-summary-target
                     :add-target-to-inspection-summary)]
      (command cmd-name
               refresh
               :id         (:applicationId @component-state)
               :summaryId  (:id @selected-summary)
               :targetId   target-id
               :targetName val))
    (refresh)))

(defn- toggle-summary-locking [locked?]
 (command :toggle-inspection-summary-locking
          (fn [{job :job}]
            (if locked?
              (hub/send "attachmentsService::bindJobInitialized" job)
              (hub/send "attachmentsService::updateAll"))
            (refresh))
          :id         (:applicationId @component-state)
          :lang       (common/get-current-language)
          :summaryId  (:id @selected-summary)
          :isLocked   locked?))

(defn- delete-inspection-summary []
  (command :delete-inspection-summary
           (fn [_]
             (swap! component-state assoc-in [:view :selected-summary-id] nil)
             (refresh))
           :id        (:applicationId @component-state)
           :summaryId (:id @selected-summary)))

(rum/defc remove-link [target-id index]
  [:a.lupicon-remove.primary.inspection-summary-link
   {:on-click (fn [_]
                (uc/confirm-dialog
                  "inspection-summary.targets.remove-confirm.title"
                  "inspection-summary.targets.remove-confirm.message"
                  (fn [] (command :remove-target-from-inspection-summary
                                  (fn [_] (hub/send "attachmentsService::updateAll") (refresh))
                                  :id        (:applicationId @component-state)
                                  :summaryId (:id @selected-summary)
                                  :targetId  target-id))))
    :data-test-id (str "remove-icon-" index)}])

(rum/defc new-inspection-summary-button [bubble-visible]
  [:button.positive
   {:on-click (fn [_] (reset! bubble-visible true))
    :data-test-id "open-create-summary-bubble"}
   [:i.lupicon-circle-plus]
   [:span (js/loc "inspection-summary.new-summary.button")]])

(rum/defc delete-inspection-summary-button []
  [:button.negative.is-right
   {:on-click (fn [_]
                (uc/confirm-dialog
                 "inspection-summary.delete-confirm.title"
                 "inspection-summary.delete-confirm.message"
                 delete-inspection-summary))
    :data-test-id "delete-summary"}
   [:i.lupicon-remove]
   [:span (js/loc "inspection-summary.delete.button")]])

(rum/defc add-target-button [disabled?]
  (let [table-rows (rum/cursor selected-summary :targets)]
    [:button.positive
     {:on-click (fn [_] (swap! table-rows conj {:target-name "" :editing? true}))
      :disabled disabled?
      :data-test-id "new-target-button"}
     [:i.lupicon-circle-plus]
     [:span (js/loc "inspection-summary.targets.new.button")]]))

(rum/defc toggle-inspection-summary-locking-button [locked?]
  (letfn [(toggle [] (toggle-summary-locking (not locked?)))]
    (if locked?
      [:button.secondary {:on-click toggle}
       [:i.lupicon-lock-open-fully]
       [:span (js/loc "inspection-summary.unlock.button")]]
      [:button.positive {:on-click toggle}
       [:i.lupicon-lock]
       [:span (js/loc "inspection-summary.lock.button")]])))

(rum/defc change-status-link [target-id finished? index]
  [:a
   {:on-click (fn [_] (command :set-target-status
                               refresh
                               :id        (:applicationId @component-state)
                               :summaryId (:id @selected-summary)
                               :targetId  target-id
                               :status    (not finished?)))
    :data-test-id (str "change-status-link-" index)}
   (js/loc (if finished?
             "inspection-summary.targets.mark-finished.undo"
             "inspection-summary.targets.mark-finished"))])

(defn commit-inspection-date [target-id value]
    (command :set-inspection-date
             refresh
             :id (:applicationId @component-state)
             :summaryId (:id @selected-summary)
             :targetId target-id
             :date (tc/to-long value))
    (refresh))

(rum/defc remove-inspection-date [target-id index]
  [:a.lupicon-remove.primary.inspection-summary-link
   {:on-click (fn [_]
                (commit-inspection-date target-id nil))
    :data-test-id (str "remove-inspection-date-" index)}])

(rum/defc target-row < rum/reactive
  [idx row-target]
  (let [editing? (:editing? row-target)
        editingInspectionDate? (:editingInspectionDate? row-target)
        application-id (:applicationId @component-state)
        summary-id (:id @selected-summary)
        target-id (:id row-target)
        application-auth-model (:auth-model @args)
        auth-model (rum/react (rum-util/derived-atom [component-state] (partial auth/get-auth-model :inspection-summaries summary-id)))
        locked? (:locked @selected-summary)
        targetFinished? (:finished row-target)
        remove-attachment-success (fn [resp] (.showSavedIndicator js/util resp) (refresh))]
    [:tr
     {:data-test-id (str "target-" idx)}
     [:td.target-finished
      (when targetFinished?
        [:i.lupicon-circle-check.positive ""])]
     [:td.target-name
      (if (and editing? (auth/ok? auth-model :add-target-to-inspection-summary))
        (uc/autofocus-input-field (:target-name row-target)
                                  (str "edit-target-field-" idx)
                                  (partial commit-target-name-edit target-id))
        (:target-name row-target))
      (when (and (not editing?) (auth/ok? auth-model :edit-inspection-summary-target) (not targetFinished?))
        [:a.inspection-summary-link
         {:on-click (fn [_] (swap! selected-summary assoc-in [:targets idx :editing?] true))
          :data-test-id (str "edit-link-" idx)}
         [:i.lupicon-pen.inspection-summary-edit]])]
     [:td
      (for [attachment (rum/react (rum/cursor-in selected-summary [:targets idx :attachments]))]
        [:div {:data-test-id (str "target-row-attachment")
               :key          (str "target-row-attachment-" (:id attachment))}
         (attc/view-with-download-small-inline attachment)
         (when-not (or locked? targetFinished?)
           (attc/delete-attachment-link attachment remove-attachment-success))])
      (when (and (auth/ok? application-auth-model :upload-file-authenticated) target-id (not locked?) (not targetFinished?))
        [:div (attc/upload-link (partial got-files (:id row-target)))])]
     [:td
      [:div.inspection-date-row
       (if (and editingInspectionDate? (auth/ok? auth-model :set-inspection-date))
         (let [date (tc/to-date (tc/from-long (:inspection-date row-target)))
               commit-fn (partial commit-inspection-date target-id)]
           (date/basic-datepicker date commit-fn idx))
         (when (:inspection-date row-target)
           (common/format-timestamp (:inspection-date row-target))))
       (when (and target-id (not editingInspectionDate?) (auth/ok? auth-model :set-inspection-date) (not targetFinished?))
         [:a.inspection-summary-link
          {:on-click     (fn [_] (swap! selected-summary assoc-in [:targets idx :editingInspectionDate?] true))
           :data-test-id (str "choose-inspection-date-" idx)}
          (if (:inspection-date row-target)
            [:i.lupicon-pen.inspection-summary-edit]
            (js/loc "choose"))])
       (when (and editingInspectionDate? (auth/ok? auth-model :set-inspection-date) (not targetFinished?))
         (remove-inspection-date target-id idx))]]
     [:td
      (when (:finished-date row-target)
        (tf/unparse date-formatter (tc/from-long (:finished-date row-target))))]
     [:td (when (:finished-by row-target)
            (js/util.partyFullName (clj->js (:finished-by row-target))))]
     [:td.functions
      (when (and (not editing?) (not editingInspectionDate?) (auth/ok? auth-model :set-target-status))
        (change-status-link target-id targetFinished? idx))
      (when (and (auth/ok? auth-model :remove-target-from-inspection-summary) (not editing?) (not editingInspectionDate?) (not targetFinished?))
        (remove-link target-id idx))]]))

(defn init
  [init-state props]
  (let [[ko-app application-auth-model] (-> (aget props ":rum/initial-state") :rum/args)
        app-id  (aget ko-app "_js" "id")]
    (swap! component-state assoc :applicationId app-id :auth-models {:application application-auth-model})
    (when (auth/ok? application-auth-model :inspection-summaries-for-application)
      (refresh (fn [_]
                 (update-summary-view (-> @component-state :summaries first :id)))))
    init-state))

(rum/defc operations-select [operations selection]
  (uc/select #(swap! component-state assoc-in [:view :new :operation] %)
             "operations-select"
             selection
             (cons
               ["" (js/loc "choose")]
               (map (fn [op] (let [op-name        (js/loc (str "operations." (:name op)))
                                   op-description (operation-description-for-select op)]
                               [(:id op) (str op-description " (" op-name ") ")])) operations))))

(rum/defc summaries-select [summaries operations selection]
  (uc/select update-summary-view
             "summaries-select"
             selection
             (cons
               ["" (js/loc "choose")]
               (map (fn [s] (let [operation      (find-by-key :id (-> s :op :id) operations)
                                  op-description (operation-description-for-select operation)]
                              [(:id s) (str (:name s) " - " op-description)])) summaries))))

(rum/defc templates-select [templates selection]
  (uc/select #(swap! component-state assoc-in [:view :new :template] %)
             "templates-select"
             selection
             (cons
               ["" (js/loc "choose")]
               (map (fn [tmpl] [(:id tmpl) (:name tmpl)]) templates))))

(defn- create-inspection-summary [cb]
  (command :create-inspection-summary
           (fn [result]
             (refresh #(update-summary-view (:id result)))
             (cb result))
           :id          (-> @component-state :applicationId)
           :operationId (-> @component-state :view :new :operation)
           :templateId  (-> @component-state :view :new :template)))

(rum/defc create-summary-bubble < rum/reactive
  [visible?]
  (let [visibility (rum/react visible?)
        selected-op (rum/react (rum/cursor-in component-state [:view :new :operation]))
        selected-template (rum/react (rum/cursor-in component-state [:view :new :template]))]
    [:div.container-bubble.half-width.arrow-2nd-col
     {:style {:display (when-not visibility "none")}}
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.intro.1")]
      [:br]
      [:label (js/loc "inspection-summary.new-summary.intro.2")]]
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.operation")]
      [:div.col-4.no-padding
       [:span.select-arrow.lupicon-chevron-small-down
        {:style {:z-index 10}}]
       (operations-select (rum/react (rum/cursor-in component-state [:operations])) selected-op)]]
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.template")]
      [:div.col-4.no-padding
       [:span.select-arrow.lupicon-chevron-small-down
        {:style {:z-index 10}}]
       (templates-select (rum/react (rum/cursor-in component-state [:templates])) selected-template)]]
     [:div.row.left-buttons
      [:button.positive
       {:on-click (partial create-inspection-summary (fn [_] (reset! visible? false)))
        :data-test-id "create-summary-button"
        :disabled (or (empty? selected-op) (empty? selected-template))}
       [:i.lupicon-check]
       [:span (js/loc "button.ok")]]
      [:button.secondary
       {:on-click (fn [_] (reset! visible? false))}
       [:i.lupicon-remove]
       [:span (js/loc "button.cancel")]]]]))

(rum/defc inspection-summaries < rum/reactive
                                 {:init init
                                  :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [ko-app application-auth-model]
  (let [{summary-id :id :as summary}   (rum/react selected-summary)
        auth-model                     (rum/react (rum-util/derived-atom [component-state] (partial auth/get-auth-model :inspection-summaries summary-id)))
        bubble-visible                 (rum/cursor-in component-state [:view :bubble-visible])
        editing?                       (rum/react (rum-util/derived-atom [selected-summary] #(->> % :targets (some :editing?))))
        table-rows                     (rum/cursor selected-summary :targets)]
    [:div
     [:h1 (js/loc "inspection-summary.tab.title")]
     [:div
      [:p (string/join " " [(js/loc "inspection-summary.tab.intro.1")
                            (js/loc "inspection-summary.tab.intro.2")
                            (js/loc "inspection-summary.tab.intro.3")])]]
     [:div.form-grid.no-top-border.no-padding

      [:div.row
       [:div.col-1
        [:div.col--vertical
         [:label (js/loc "inspection-summary.select-summary.label")]
         [:span.select-arrow.lupicon-chevron-small-down
          {:style {:z-index 10}}]
         (summaries-select (rum/react (rum/cursor-in component-state [:summaries]))
                           (rum/react (rum/cursor-in component-state [:operations]))
                           summary-id)]]
       (when (auth/ok? application-auth-model :create-inspection-summary)
         [:div.col-1.summary-button-bar
          (new-inspection-summary-button bubble-visible)])
       (when (auth/ok? auth-model :delete-inspection-summary)
         [:div.col-2.group-buttons.summary-button-bar
          (delete-inspection-summary-button)])]

      (when (auth/ok? application-auth-model :create-inspection-summary)
        [:div.row.create-summary-bubble (create-summary-bubble bubble-visible)])

      (when summary
        [:div.row
         [:table
          {:id "targets-table"}
          [:thead
           [:tr
            [:th (js/loc "inspection-summary.targets.table.state")]
            [:th (js/loc "inspection-summary.targets.table.target-name")]
            [:th (js/loc "inspection-summary.targets.table.attachment")]
            [:th (js/loc "inspection-summary.targets.table.inspection-date")]
            [:th (js/loc "inspection-summary.targets.table.finished-date")]
            [:th (js/loc "inspection-summary.targets.table.marked-by")]
            [:th ""]]]
          [:tbody
           (for [[idx target] (map-indexed vector (rum/react table-rows))]
             (rum/with-key (target-row idx target) (str "target-row-" idx)))]]])

      (when (auth/ok? auth-model :add-target-to-inspection-summary)
        [:div.row
         (add-target-button editing?)])

      (when (and (auth/ok? auth-model :toggle-inspection-summary-locking) (not (:locked summary)))
        [:div.row
         [:p (string/join " " [(js/loc "inspection-summary.locking.info")
                               (js/loc "inspection-summary.locking-archive.info")])]])

      (when (auth/ok? auth-model :toggle-inspection-summary-locking)
        [:div.row
         (toggle-inspection-summary-locking-button (:locked summary))])]]))

(defn mount-component []
  (rum/mount (inspection-summaries (:app @args) (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (aget componentParams "app") :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))
