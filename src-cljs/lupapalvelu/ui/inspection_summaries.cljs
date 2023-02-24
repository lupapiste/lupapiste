(ns lupapalvelu.ui.inspection-summaries
  (:require [cljs-time.coerce :as tc]
            [cljs-time.format :as tf]
            [clojure.string :as string]
            [goog.object :as googo]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.ui.pate.attachments :refer [attachments-refresh-mixin]]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [query command] :as common]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.rum-util :as rum-util]
            [rum.core :as rum]
            [sade.shared-util :as util]))

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

(def empty-component-state {:operations   []
                            :summaries    []
                            :templates    []
                            :fileStatuses {}
                            :new-target?  false
                            :view         {:bubble-visible      false
                                           :new                 {:operation nil
                                                                 :template  nil}
                                           :selected-summary-id nil}})


(def component-state  (atom empty-component-state))
(def selected-id      (rum/cursor-in component-state [:view :selected-summary-id]))
(def selected-summary (rum-util/derived-atom
                        [component-state selected-id]
                        (fn [state id]
                          (when id
                            (->> (:summaries state)
                                 (find-by-key :id id))))))

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

(defn update-selected-summary [update-fn]
  (letfn [(update-if-selected [summary]
            (if (= (:id summary) @selected-id)
              (update-fn summary)
              summary))]
    (swap! component-state update :summaries (partial mapv update-if-selected))))

(defn- set-summary-id [id]
  (swap! component-state assoc-in [:view :selected-summary-id] id))

(defn to-bindable-file [target-id file]
  {:type {:type-group "katselmukset_ja_tarkastukset"
          :type-id    "tarkastusasiakirja"}
   :fileId (common/oget file "fileId")
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
  (let [files          (:files hub-event)
        fileIds        (map #(common/oget % "fileId") files)
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
  (when (not (empty? val))
    (let [cmd-name (if target-id
                     :edit-inspection-summary-target
                     :add-target-to-inspection-summary)]
      (command cmd-name
               (if target-id
                 #(update-selected-summary (fn [summary]
                                             (letfn [(update-name [target]
                                                       (if (= (:id target) target-id)
                                                         (assoc target :target-name val)
                                                         target))]
                                               (update summary :targets (partial mapv update-name)))))
                 #(refresh))
               :id         (:applicationId @component-state)
               :summaryId  (:id @selected-summary)
               :targetId   target-id
               :targetName val))))

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

(defn- remove-link [target-id index]
  (uc/icon-button
    {:text-loc   :remove
     :icon-only? true
     :icon       :lupicon-remove
     :class      :tertiary
     :on-click   (fn [_]
                   (uc/confirm-dialog
                     "inspection-summary.targets.remove-confirm.title"
                     "inspection-summary.targets.remove-confirm.message"
                     (fn [] (command :remove-target-from-inspection-summary
                                     (fn [_] (hub/send "attachmentsService::updateAll") (refresh))
                                     :id        (:applicationId @component-state)
                                     :summaryId (:id @selected-summary)
                                     :targetId  target-id))))
     :test-id    (str "remove-icon-" index)}))

(rum/defc new-inspection-summary-button < rum/reactive
  [bubble-visible]
  (uc/icon-button {:class     :primary
                   :disabled? (rum/react bubble-visible)
                   :on-click  #(reset! bubble-visible true)
                   :test-id   "open-create-summary-bubble"
                   :icon      :lupicon-circle-plus
                   :text-loc  :inspection-summary.new-summary.button}))

(defn delete-inspection-summary-button []
  (uc/icon-button {:class    :negative
                   :on-click #(uc/confirm-dialog
                                "inspection-summary.delete-confirm.title"
                                "inspection-summary.delete-confirm.message"
                                delete-inspection-summary)
                   :test-id  "delete-summary"
                   :icon :lupicon-remove
                   :text-loc :inspection-summary.delete.button}))

(defn add-target-button [on-click disabled?]
  (uc/icon-button {:class     :primary
                   :on-click  on-click
                   :disabled? disabled?
                   :test-id   "new-target-button"
                   :icon      :lupicon-circle-plus
                   :text-loc  :inspection-summary.targets.new.button}))

(rum/defc toggle-inspection-summary-locking-button [locked?]
  (letfn [(toggle [] (toggle-summary-locking (not locked?)))]
    (uc/icon-button {:class    (if locked? :secondary :primary)
                     :on-click toggle
                     :icon     (if locked? :lupicon-lock-open-fully :lupicon-lock)
                     :text-loc (if locked?
                                 :inspection-summary.unlock.button
                                 :inspection-summary.lock.button)})))

(rum/defc change-status-link [target-id finished? index]
  (uc/click-link {:click    (fn [_] (command :set-target-status
                                             refresh
                                             :id        (:applicationId @component-state)
                                             :summaryId (:id @selected-summary)
                                             :targetId  target-id
                                             :isFinished (not finished?)))
                  :test-id  (str "change-status-link-" index)
                  :text-loc (util/kw-path :inspection-summary.targets
                                          (if finished?
                                            :set-target-status-unmarked
                                            :set-target-status-marked))}))

(defn commit-inspection-date [target-id value]
    (command :set-inspection-date
             refresh
             :id (:applicationId @component-state)
             :summaryId (:id @selected-summary)
             :targetId target-id
             :date (tc/to-long value))
    (refresh))

(rum/defc remove-inspection-date [target-id index]
  (uc/icon-button {:class      :tertiary.gap--l05
                   :text-loc   :remove
                   :icon       :lupicon-remove
                   :icon-only? true
                   :test-id    (str "remove-inspection-date-" index)
                   :on-click   (fn [_]
                                 (commit-inspection-date target-id nil))}))

(rum/defc delete-attachment-link < (attachments-refresh-mixin)
  [attachment success-fn]
  (when (auth/attachment-auth? attachment :delete-attachment)
    (attc/delete-attachment-link attachment success-fn)))

(rum/defc target-row < rum/reactive
  {:key-fn #(or (:id %2) "new-target")}
  [idx row-target]
  (let [editing?                  (:editing? row-target)
        editingInspectionDate?    (:editingInspectionDate? row-target)
        summary-id                (:id @selected-summary)
        target-id                 (:id row-target)
        application-auth-model    (:auth-model @args)
        auth-model                (rum/react (rum-util/derived-atom [component-state]
                                                                    (partial auth/get-auth-model :inspection-summaries summary-id)))
        attachments               (rum/cursor-in selected-summary [:targets idx :attachments])
        locked?                   (:locked @selected-summary)
        targetFinished?           (:finished row-target)
        remove-attachment-success (fn [att resp]
                                    (js/util.showSavedIndicator (clj->js resp))
                                    (swap! attachments #(filterv (fn [{:keys [id]}] (not= id (:id att))) %)))]
    [:tr
     {:data-test-id (str "target-" idx)}
     [:td.target-finished
      (when targetFinished?
        [:i.lupicon-circle-check.positive {:title (common/loc :done)}])]
     [:td.target-name
      (if (and (auth/ok? auth-model :add-target-to-inspection-summary) (not targetFinished?))
        (uc/pen-input {:value     (:target-name row-target)
                       :editing?  editing?
                       :callback  (partial commit-target-name-edit target-id)
                       :test-id   (str "target-name-" idx)
                       :disabled? (not (auth/ok? auth-model :edit-inspection-summary-target))})
        (:target-name row-target))]
     [:td
      (for [attachment (rum/react attachments)]
        [:div {:data-test-id (str "target-row-attachment")
               :key          (str "target-row-attachment-" (:id attachment))}
         (attc/view-with-download-small-inline attachment)
         (when-not (or locked? targetFinished?)
           (delete-attachment-link attachment (partial remove-attachment-success attachment)))])
      (when (and (auth/ok? application-auth-model :upload-file-authenticated) target-id
                 (not locked?) (not targetFinished?))
        [:div (attc/upload-button (partial got-files (:id row-target)))])]
     [:td
      [:div.inspection-date-row
       (if (and editingInspectionDate? (auth/ok? auth-model :set-inspection-date))
         (let [date      (tc/to-date (tc/from-long (:inspection-date row-target)))
               commit-fn (partial commit-inspection-date target-id)]
           [:div.dsp--inline-block (uc/day-edit date {:callback commit-fn})])
         (when (:inspection-date row-target)
           (common/format-timestamp (:inspection-date row-target))))
       (when (and target-id (not editingInspectionDate?) (auth/ok? auth-model :set-inspection-date) (not targetFinished?))
         (let [click-fn #(swap! selected-summary assoc-in
                                [:targets idx :editingInspectionDate?] true)
               tid      (str "choose-inspection-date-" idx)]
           (if (:inspection-date row-target)
             (uc/icon-button {:class      :tertiary
                              :icon       :lupicon-pen
                              :on-click   click-fn
                              :icon-only? true
                              :text-loc   :edit
                              :test-id    tid})
             (uc/click-link {:text-loc :choose
                             :click    click-fn
                             :test-id  tid}))))
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
  (let [[app-id application-auth-model] (:rum/args init-state)]
    (swap! component-state assoc :applicationId app-id :auth-models {:application application-auth-model})
    (when (auth/ok? application-auth-model :inspection-summaries-for-application)
      (refresh #(set-summary-id (-> @component-state :summaries first :id))))
    init-state))

(rum/defc operations-select [operations selection]
  (uc/select (or selection "")
             (cons
               ["" (js/loc "choose")]
               (map (fn [op] (let [op-name        (js/loc (str "operations." (:name op)))
                                   op-description (operation-description-for-select op)]
                               [(:id op) (str op-description " (" op-name ") ")])) operations))
             {:callback #(swap! component-state assoc-in [:view :new :operation] %)
              :id       "inspection-summary-operation-select"
              :class    "w--100"
              :test-id  "operations-select"}))

(rum/defc templates-select [templates selection]
  (uc/select (or selection "")
             (cons
               ["" (js/loc "choose")]
               (map (fn [tmpl] [(:id tmpl) (:name tmpl)]) templates))
             {:callback #(swap! component-state assoc-in [:view :new :template] %)
              :class    "w--100"
              :id       "inspection-summary-template-select"
              :test-id  "templates-select"}))

(defn- create-inspection-summary [cb]
  (command :create-inspection-summary
           (fn [result]
             (refresh #(set-summary-id (:id result)))
             (cb result))
           :id          (-> @component-state :applicationId)
           :operationId (-> @component-state :view :new :operation)
           :templateId  (-> @component-state :view :new :template)))

(rum/defc create-summary-bubble < rum/reactive
  [visible?]
  (let [selected-op       (rum/react (rum/cursor-in component-state [:view :new :operation]))
        selected-template (rum/react (rum/cursor-in component-state [:view :new :template]))]
    (when (rum/react visible?)
      [:div.container-bubble.half-width
       [:div.flex--column.flex--gap2.pad--2
        [:div
         [:div (common/loc :inspection-summary.new-summary.intro.1)]
         [:div (common/loc :inspection-summary.new-summary.intro.2)]]
        [:div
         [:label.lux {:for "inspection-summary-operation-select"}
          (common/loc :inspection-summary.new-summary.operation)]
         (operations-select (rum/react (rum/cursor-in component-state [:operations])) selected-op)]
        [:div
         [:label.lux {:for "inspection-summary-template-select"}
          (common/loc :inspection-summary.new-summary.template)]
         (templates-select (rum/react (rum/cursor-in component-state [:templates])) selected-template)]
        [:div.dsp--flex.flex--gap2
         (uc/icon-button {:class     :primary
                          :on-click  (partial create-inspection-summary #(reset! visible? false))
                          :test-id   "create-summary-button"
                          :disabled? (or (empty? selected-op) (empty? selected-template))
                         :icon      :lupicon-check
                          :text-loc  :button.ok})
         (uc/icon-button {:class    :secondary
                          :on-click #(reset! visible? false)
                          :test-id  "cancel-summary-button"
                          :icon     :lupicon-remove
                          :text-loc :button.cancel})]]])))

(rum/defc inspection-summaries < rum/reactive
  {:init         init
   :will-unmount (fn [& _] (swap! component-state merge empty-component-state))}
  [app-id application-auth-model]
  (let [{summary-id :id :as summary} (rum/react selected-summary)
        auth-model                   (rum/react (rum-util/derived-atom [component-state] (partial auth/get-auth-model :inspection-summaries summary-id)))
        bubble-visible               (rum/cursor-in component-state [:view :bubble-visible])
        new-target?                  (rum/cursor-in component-state [:new-target?])
        table-rows                   (rum/cursor selected-summary :targets)
        editing?                     (rum/react (rum-util/derived-atom [table-rows] #(some :editing? %)))
        operations                   (rum/react (rum/cursor-in component-state [:operations]))]
    [:div
     [:h1 (js/loc "inspection-summary.tab.title")]
     [:div
      [:p (string/join " " [(js/loc "inspection-summary.tab.intro.1")
                            (js/loc "inspection-summary.tab.intro.2")
                            (js/loc "inspection-summary.tab.intro.3")])]]
     [:div.flex--column.flex--gap2.pad--h2.flex--align-start
      [:div.flex--between.flex--align-end.flex--gap2.w--100
       [:div
        [:label.lux.dsp--block {:for "inspection-select-summary"}
         (common/loc :inspection-summary.select-summary.label)]
        (uc/select (or summary-id "")
                   (cons
                     ["" (js/loc "choose")]
                     (->> (rum/react (rum/cursor-in component-state [:summaries]))
                          (map (fn [s]
                                 (let [operation      (find-by-key :id (-> s :op :id) operations)
                                       op-description (operation-description-for-select operation)]
                                   [(:id s) (str (:name s) " - " op-description)])))))
                   {:callback set-summary-id
                    :class    "w--20em"
                    :id       "inspection-select-summary"
                    :test-id  "summaries-select"})]

       (when (auth/ok? auth-model :delete-inspection-summary)
         (delete-inspection-summary-button))]

      (when (auth/ok? application-auth-model :create-inspection-summary)
        [:div.flex--column.flex--align-start
         (new-inspection-summary-button bubble-visible)
         (create-summary-bubble bubble-visible)])

      (when summary
        [:table
         {:id "targets-table"}
         [:thead
          [:tr
           [:th (common/loc :inspection-summary.targets.table.state)]
           [:th (common/loc :inspection-summary.targets.table.target-name)]
           [:th (common/loc :inspection-summary.targets.table.attachment)]
           [:th (common/loc :inspection-summary.targets.table.inspection-date)]
           [:th (common/loc :inspection-summary.targets.table.finished-date)]
           [:th (common/loc :inspection-summary.targets.table.marked-by)]
           [:th (common/loc :admin.actions)]]]
         [:tbody
          (rum/fragment
            (for [[idx target] (map-indexed vector (rum/react table-rows))]
              (target-row idx target))
            (when (rum/react new-target?)
              [:tr
               [:td]
               [:td (uc/pen-input {:value     ""
                                   :editing?  true
                                   :callback  (fn [v]
                                                (reset! new-target? false)
                                                (commit-target-name-edit nil v))
                                   :on-cancel #(reset! new-target? false)})]
               [:td {:col-span 5}]]))]])

      (when (auth/ok? auth-model :add-target-to-inspection-summary)
        (add-target-button #(reset! new-target? true) (rum/react new-target?)))

      (when (and (auth/ok? auth-model :toggle-inspection-summary-locking) (not (:locked summary)))
        [:p.gap--0
         (common/loc :inspection-summary.locking.info)
         " "
         (common/loc :inspection-summary.locking-archive.info)])

      (when (auth/ok? auth-model :toggle-inspection-summary-locking)
        (toggle-inspection-summary-locking-button (:locked summary)))]]))

(defn mount-component []
  (some-> (:app @args)
          (googo/get "id")
          (.call)
          (inspection-summaries (:auth-model @args))
          (rum/mount (.getElementById js/document (:dom-id @args)))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (googo/get componentParams "app") :auth-model (common/oget componentParams "authModel") :dom-id (name domId))
  (mount-component))
