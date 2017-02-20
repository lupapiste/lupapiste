(ns lupapalvelu.ui.inspection-summaries
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :refer [query command]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.ui.rum-util :as rum-util]
            [cljs-time.coerce :as tc]
            [cljs-time.format :as tf]))

(enable-console-print!)

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
   (query "inspection-summaries-for-application"
          (fn [data]
            (swap! component-state assoc
                   :operations (:operations data)
                   :templates  (:templates data)
                   :summaries  (:summaries data)
                   :fileStatuses {})
            (when cb (cb)))
          "id" (-> @component-state :applicationId))))

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
            :id target-id}
   :constructionTime true})

(defn unsubscribe-if-done [target-id]
  (let [statuses (filter #(= target-id (:target-id %))
                         (vals (:fileStatuses @component-state)))]
    (when (every? #(= "done" (:status %)) statuses)
      (doseq [hub-id (distinct (map :subs-id statuses))]
        (.unsubscribe js/hub hub-id))
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

(defn- commit-target-name-edit [applicationId summaryId targetId val]
  (if (not (empty? val))
    (let [cmd-name (if targetId
                     "edit-inspection-summary-target"
                     "add-target-to-inspection-summary")]
      (command cmd-name
               #(refresh)
               "id"         applicationId
               "summaryId"  summaryId
               "targetId"   targetId
               "targetName" val))
    (refresh)))

(rum/defc remove-link [applicationId summaryId targetId index]
  [:a.lupicon-remove.primary
   {:on-click (fn [_]
                (uc/confirm-dialog
                  "inspection-summary.targets.remove-confirm.title"
                  "inspection-summary.targets.remove-confirm.message"
                  (fn [] (command "remove-target-from-inspection-summary"
                                  (fn [_] (.send js/hub "attachmentsService::updateAll") (refresh))
                                  "id"        applicationId
                                  "summaryId" summaryId
                                  "targetId"  targetId))))
    :data-test-id (str "remove-icon-" index)}])

(rum/defc change-status-link [applicationId summaryId targetId finished? index]
  [:a
   {:on-click (fn [_] (command "set-target-status"
                               #(refresh)
                               "id"        applicationId
                               "summaryId" summaryId
                               "targetId"  targetId
                               "status"    (not finished?)))
    :data-test-id (str "change-status-link-" index)}
   (js/loc (if finished?
             "inspection-summary.targets.mark-finished.undo"
             "inspection-summary.targets.mark-finished"))])

(rum/defcs target-row < rum/reactive
                        {:init (fn [state _] (assoc state ::input-id (jsutil/unique-elem-id "inspection-target-row")))}
                        {:did-mount (fn [state]
                                      (upload/bindToElem (js-obj "id" (::input-id state)))
                                      (let [[_ row-target] (-> state :rum/args)]
                                        (if-let [target-id (:id row-target)]
                                          (assoc state :fileupload-subscription-id (upload/subscribe-files-uploaded
                                                                                     (::input-id state)
                                                                                     (partial got-files target-id)))
                                          state)))}
                        {:will-update (fn [state]
                                        (let [[_ row-target] (-> state :rum/args)]
                                          (if (and (:id row-target) (not (:fileupload-subscription-id state)))
                                            (assoc state :fileupload-subscription-id (upload/subscribe-files-uploaded
                                                                                       (::input-id state)
                                                                                       (partial got-files (:id row-target))))
                                            state)))}
  [local-state idx row-target add-enabled? edit-enabled? remove-enabled? status-change-enabled?]
  (let [editing?        (:editing? row-target)
        applicationId   (:applicationId @component-state)
        summaryId       (:id @selected-summary)
        targetId        (:id row-target)
        targetFinished? (:finished row-target)
        remove-attachment-success (fn [resp] (.showSavedIndicator js/util resp) (refresh))]
    [:tr
     {:data-test-id (str "target-" idx)}
     [:td.target-finished
      (when targetFinished?
        [:i.lupicon-circle-check.positive ""])]
     [:td.target-name
      (if (and editing? add-enabled?)
        (uc/autofocus-input-field (:target-name row-target)
                                  (str "edit-target-field-" idx)
                                  (partial commit-target-name-edit applicationId summaryId targetId))
        (:target-name row-target))]
     [:td
      (doall
        (for [attachment (rum/react (rum/cursor-in selected-summary [:targets idx :attachments]))
              :let [latest (:latestVersion attachment)]]
          (vector :div
                  (attc/view-with-download-small-inline latest)
                  (when-not targetFinished?
                    (attc/delete-attachment-link attachment remove-attachment-success)))))
      (when-not targetFinished?
        (attc/upload-link (::input-id local-state)))]
     [:td
      (when (:finished-date row-target)
        (tf/unparse date-formatter (tc/from-long (:finished-date row-target))))]
     [:td (when (:finished-by row-target)
            (js/util.partyFullName (clj->js (:finished-by row-target))))]
     (vector :td.functions
       (if (and (not editing?) status-change-enabled?)
         (change-status-link applicationId summaryId targetId targetFinished? idx))
       (if (and (not editing?) edit-enabled? (not targetFinished?))
         [:a
          {:on-click (fn [_] (swap! selected-summary assoc-in [:targets idx :editing?] true))
           :data-test-id (str "edit-link-" idx)}
          (js/loc "edit")])
       (if (and (not editing?) remove-enabled? (not targetFinished?))
         (remove-link applicationId summaryId targetId idx)))]))

(defn init
  [init-state props]
  (let [[ko-app auth-model] (-> (aget props ":rum/initial-state") :rum/args)
        id-computed (aget ko-app "id")
        id          (id-computed)]
    (swap! component-state assoc :applicationId id)
    (when (.ok auth-model "inspection-summaries-for-application")
      (refresh))
    init-state))

(rum/defc operations-select [operations selection]
  (uc/select #(swap! component-state assoc-in [:view :new :operation] %)
             "operations-select"
             selection
             (cons
               [nil (js/loc "choose")]
               (map (fn [op] (let [op-name        (js/loc (str "operations." (:name op)))
                                   op-description (operation-description-for-select op)]
                               [(:id op) (str op-description " (" op-name ") ")])) operations))))

(rum/defc summaries-select [summaries operations selection]
  (uc/select update-summary-view
             "summaries-select"
             selection
             (cons
               [nil (js/loc "choose")]
               (map (fn [s] (let [operation      (find-by-key :id (-> s :op :id) operations)
                                  op-description (operation-description-for-select operation)]
                              [(:id s) (str (:name s) " - " op-description)])) summaries))))

(rum/defc templates-select [templates selection]
  (uc/select #(swap! component-state assoc-in [:view :new :template] %)
             "templates-select"
             selection
             (cons
               [nil (js/loc "choose")]
               (map (fn [tmpl] [(:id tmpl) (:name tmpl)]) templates))))

(rum/defc create-summary-bubble < rum/reactive
  [visible?]
  (let [visibility (rum/react visible?)]
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
       (operations-select (rum/react (rum/cursor-in component-state [:operations]))
                          (rum/react (rum/cursor-in component-state [:view :new :operation])))]]
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.template")]
      [:div.col-4.no-padding
       [:span.select-arrow.lupicon-chevron-small-down
        {:style {:z-index 10}}]
       (templates-select (rum/react (rum/cursor-in component-state [:templates]))
                         (rum/react (rum/cursor-in component-state [:view :new :template])))]]
     [:div.row.left-buttons
      [:button.positive
       {:on-click (fn [_] (command "create-inspection-summary"
                                   (fn [result]
                                     (refresh #(update-summary-view (:id result)))
                                     (reset! visible? false))
                                   "id"          (-> @component-state :applicationId)
                                   "operationId" (-> @component-state :view :new :operation)
                                   "templateId"  (-> @component-state :view :new :template)))
        :data-test-id "create-summary-button"}
       [:i.lupicon-check]
       [:span (js/loc "button.ok")]]
      [:button.secondary
       {:on-click (fn [_] (reset! visible? false))}
       [:i.lupicon-remove]
       [:span (js/loc "button.cancel")]]]]))

(rum/defc inspection-summaries < rum/reactive
                                 {:init init
                                  :will-unmount (fn [& _] (reset! component-state empty-component-state))}
  [ko-app auth-model]
  (let [{summaryId :id :as summary}    (rum/react selected-summary)
        bubble-visible                 (rum/cursor-in component-state [:view :bubble-visible])
        editing?                       (rum/react (rum-util/derived-atom [selected-summary] #(->> % :targets (some :editing?))))
        target-add-enabled?            (.ok auth-model "add-target-to-inspection-summary")
        target-edit-enabled?           (.ok auth-model "edit-inspection-summary-target")
        target-remove-enabled?         (.ok auth-model "remove-target-from-inspection-summary")
        target-status-change-enabled?  (.ok auth-model "set-target-status")
        table-rows                     (rum/cursor selected-summary :targets)]
    [:div
     [:h1 (js/loc "inspection-summary.tab.title")]
     [:div
      [:label (js/loc "inspection-summary.tab.intro.1")]
      [:br]
      [:label (js/loc "inspection-summary.tab.intro.2")]]
     [:div.form-grid.no-top-border.no-padding
      [:div.row
       [:div.col-1
        [:div.col--vertical
         [:label (js/loc "inspection-summary.select-summary.label")]
         [:span.select-arrow.lupicon-chevron-small-down
          {:style {:z-index 10}}]
         (summaries-select (rum/react (rum/cursor-in component-state [:summaries]))
                           (rum/react (rum/cursor-in component-state [:operations]))
                           summaryId)]]
       (if (.ok auth-model "create-inspection-summary")
         [:div.col-1.summary-button-bar
          [:button.positive
           {:on-click (fn [_] (reset! bubble-visible true))
            :data-test-id "open-create-summary-bubble"}
           [:i.lupicon-circle-plus]
           [:span (js/loc "inspection-summary.new-summary.button")]]])
       (when (and summary target-edit-enabled?
                  (not-any? #(or (:finished %) (not-empty (:attachments %))) @table-rows))
         [:div.col-2.group-buttons.summary-button-bar
          [:button.negative.is-right
           {:on-click (fn [_]
                        (uc/confirm-dialog
                          "inspection-summary.delete-confirm.title"
                          "inspection-summary.delete-confirm.message"
                          (fn [] (command "delete-inspection-summary"
                                          (fn [_]
                                            (swap! component-state assoc-in [:view :selected-summary-id] nil)
                                            (refresh))
                                          "id"        (:applicationId @component-state)
                                          "summaryId" summaryId))))
            :data-test-id "delete-summary"}
           [:i.lupicon-remove]
           [:span (js/loc "inspection-summary.delete.button")]]])]
      (if (.ok auth-model "create-inspection-summary")
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
            [:th (js/loc "inspection-summary.targets.table.date")]
            [:th (js/loc "inspection-summary.targets.table.marked-by")]
            [:th ""]]]
          [:tbody
           (doall
             (for [[idx target] (map-indexed vector (rum/react table-rows))]
               (target-row idx target target-add-enabled? target-edit-enabled? target-remove-enabled? target-status-change-enabled?)))]]])
      (if (and summary (not editing?) target-add-enabled?)
        [:div.row
         [:button.positive
          {:on-click (fn [_] (swap! table-rows conj {:target-name "" :editing? true}))
           :data-test-id "new-target-button"}
          [:i.lupicon-circle-plus]
          [:span (js/loc "inspection-summary.targets.new.button")]]])]]))

(defonce args (atom {}))

(defn mount-component []
  (rum/mount (inspection-summaries (:app @args) (:auth-model @args))
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId componentParams]
  (swap! args assoc :app (aget componentParams "app") :auth-model (aget componentParams "authModel") :dom-id (name domId))
  (mount-component))

(defn reload-hook []
  (mount-component))
