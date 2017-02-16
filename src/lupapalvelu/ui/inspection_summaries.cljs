(ns lupapalvelu.ui.inspection-summaries
  (:require [rum.core :as rum]
            [clojure.string :as string]
            [lupapalvelu.ui.attachment.components :as attc]
            [lupapalvelu.ui.attachment.file-upload :as upload]
            [lupapalvelu.ui.common :refer [query command]]
            [lupapalvelu.ui.components :as uc]
            [lupapalvelu.ui.util :as jsutil]))

(enable-console-print!)

(defn find-by-key
  "Return item from sequence col of maps where element k (keyword) matches value v."
  [k v col]
  (some (fn [m] (when (= v (get m k)) m)) col))

(defn save-indicator [visible-atom]
  [:span.form-indicator.form-input-saved
   {:style {:display (when-not (rum/react visible-atom) "none")}}
   [:span.icon]])

(def empty-state {:applicationId ""
                  :operations []
                  :summaries []
                  :templates []
                  :view {:bubble-visible false
                         :new {:operation nil
                               :template nil}
                         :summary {:id nil
                                   :targets []}}
                  :fileStatuses {}})

(def state         (atom empty-state))
(def table-rows    (rum/cursor-in state [:view :summary :targets]))

(defn- refresh
  ([] (refresh nil))
  ([cb]
   (query "inspection-summaries-for-application"
          (fn [data]
            (swap! state assoc
                   :operations (:operations data)
                   :templates  (:templates data)
                   :summaries  (:summaries data)
                   :fileStatuses {})
            (when cb (cb)))
          "id" (-> @state :applicationId))))

(defn- operation-description-for-select [op]
  (string/join " - " (remove empty? [(:description op) (:op-identifier op)])))

(defn- update-summary-view [id]
  (->> (:summaries @state)
       (find-by-key :id id)
       (swap! state assoc-in [:view :summary])))

(defn to-bindable-file [target-id file]
  #_:target #_{:type "inspection-item"
              :id target-id}
  {:type {:type-group "katselmukset_ja_tarkastukset"
          :type-id    "tarkastusasiakirja"}
   :fileId (aget file "fileId")
   :constructionTime true})


(defn unsubscribe-if-done [target-id]
  (let [statuses (filter #(= target-id (:target-id %))
                         (vals (:fileStatuses @state)))]
    (when (every? #(= "done" (:status %)) statuses)
      (doseq [hub-id (distinct (map :subs-id statuses))]
        (.unsubscribe js/hub hub-id))
      (refresh))))

(defn bind-attachment-callback [target-id event]
  (let [clj-event (js->clj event :keywordize-keys true)]
    (swap! state update-in [:fileStatuses (keyword (:fileId clj-event))] assoc :status (:status clj-event))
    (unsubscribe-if-done target-id)))

(defn got-files [target-id hub-event]
  (let [files          (.-files hub-event)
        fileIds        (map #(.-fileId %) files)
        bindable-files (map (partial to-bindable-file target-id) files)
        subs-id        (.subscribe js/hub
                                   #js {:eventType "attachmentsService::bind-attachments-status"
                                        :status "done"
                                        :jobStatus "done"}
                                   (partial bind-attachment-callback target-id))
        create-filestatuses-fn (fn [statuses fid]
                                 (assoc statuses (keyword fid)
                                                 {:subs-id subs-id
                                                  :target-id target-id}))]
    (swap! state update :fileStatuses #(reduce % create-filestatuses-fn fileIds))
    (.bindAttachments upload/attachment-service (clj->js bindable-files))))


(rum/defcs summary-row < {:init (fn [state _]
                                  (-> state
                                      (assoc ::input-id (jsutil/unique-elem-id "inspection-row"))
                                      (assoc ::row-target (-> state :rum/args first)))
                                  ; or (-> state :rum/args first :id)
                                  )}
                         {:did-mount (fn [state]
                                       (upload/bindToElem (js-obj "id" (::input-id state)))
                                       (upload/subscribe-files-uploaded
                                         (::input-id state)
                                         (partial got-files (-> state ::row-target :id)))
                                       state)}
  [state row-data]
  [:tr
   [:td ""]
   [:td
    {:data-test-id (str "target-name-" (:target-name row-data))}
    (:target-name row-data)]
   [:td (attc/upload-link (::input-id state))]
   [:td ""]
   [:td ""]
   [:td ""]])

(defn init
  [init-state props]
  (let [[ko-app auth-model] (-> (aget props ":rum/initial-state") :rum/args)
        id-computed (aget ko-app "id")
        id          (id-computed)]
    (swap! state assoc :applicationId id)
    (when (.ok auth-model "inspection-summaries-for-application")
      (refresh nil))
    init-state))

(rum/defc operations-select [operations selection]
  (uc/select #(swap! state assoc-in [:view :new :operation] %)
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
  (uc/select #(swap! state assoc-in [:view :new :template] %)
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
       (operations-select (rum/react (rum/cursor-in state [:operations]))
                          (rum/react (rum/cursor-in state [:view :new :operation])))]]
     [:div.row
      [:label (js/loc "inspection-summary.new-summary.template")]
      [:div.col-4.no-padding
       [:span.select-arrow.lupicon-chevron-small-down
        {:style {:z-index 10}}]
       (templates-select (rum/react (rum/cursor-in state [:templates]))
                         (rum/react (rum/cursor-in state [:view :new :template])))]]
     [:div.row.left-buttons
      [:button.positive
       {:on-click (fn [_] (command "create-inspection-summary"
                                   (fn [result]
                                     (refresh #(update-summary-view (:id result)))
                                     (reset! visible? false))
                                   "id"          (-> @state :applicationId)
                                   "operationId" (-> @state :view :new :operation)
                                   "templateId"  (-> @state :view :new :template)))}
       [:i.lupicon-check]
       [:span (js/loc "button.ok")]]
      [:button.secondary
       {:on-click (fn [_] (reset! visible? false))}
       [:i.lupicon-remove]
       [:span (js/loc "button.cancel")]]]]))

(rum/defc inspection-summaries < rum/reactive
                                 {:init init
                                  :will-unmount (fn [& _] (reset! state empty-state))}
  [ko-app auth-model]
  (let [summary-in-view (rum/react (rum/cursor-in state [:view :summary]))
        bubble-visible (rum/cursor-in state [:view :bubble-visible])]
    [:div
     [:h1 (js/loc "inspection-summary.tab.title")]
     [:div
      [:label (js/loc "inspection-summary.tab.intro.1")]
      [:br]
      [:label (js/loc "inspection-summary.tab.intro.2")]
      [:br]
      [:label (js/loc "inspection-summary.tab.intro.3")]]
     [:div.form-grid.no-top-border.no-padding
      [:div.row
       [:div.col-1
        [:div.col--vertical
         [:label (js/loc "inspection-summary.select-summary.label")]
         [:span.select-arrow.lupicon-chevron-small-down
          {:style {:z-index 10}}]
         (summaries-select (rum/react (rum/cursor-in state [:summaries]))
                           (rum/react (rum/cursor-in state [:operations]))
                           (:id summary-in-view))]]
       (if (.ok auth-model "create-inspection-summary")
         [:div.col-1.create-new-summary-button
          [:button.positive
           {:on-click (fn [_] (reset! bubble-visible true))}
           [:i.lupicon-circle-plus]
           [:span (js/loc "inspection-summary.new-summary.button")]]])]
      (if (.ok auth-model "create-inspection-summary")
        [:div.row.create-summary-bubble (create-summary-bubble bubble-visible)])
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
           (for [row (rum/react table-rows)]
             (summary-row row)))]]]
      [:div.row
       [:button.positive
        {:on-click (fn [_] (swap! table-rows conj {:target-name "Faas"}))}
        [:i.lupicon-circle-plus]
        [:span (js/loc "inspection-summary.targets.new.button")]]]]]))

(defn ^:export start [domId componentParams]
  (rum/mount (inspection-summaries (aget componentParams "app") (aget componentParams "authModel"))
             (.getElementById js/document (name domId))))
