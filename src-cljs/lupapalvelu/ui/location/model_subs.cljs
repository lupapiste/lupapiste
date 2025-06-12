(ns lupapalvelu.ui.location.model-subs
  (:require [lupapalvelu.common.job :as job]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.location.bim-viewer.viewer-subs :as bim-viewer-subs]
            [lupapalvelu.ui.location.locations-operations :as locations-operations]
            [lupapalvelu.ui.location.model-utils :as model-utils]
            [lupapalvelu.ui.util :as jsutil]
            [lupapalvelu.next.router-api :as router]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn get-file-version
  "If an id is provided, returns the version with that :file-version-id. Otherwise, returns
  the latest version."
  ([versions id]
   (if id
     (util/find-by-key :file-version-id id versions)
     (apply util/max-key :uploaded-at versions)))
  ([versions]
   (get-file-version versions nil)))

(rf/reg-event-fx ::fetch-linked-file-metadata.success
  (fn [{:keys [db]} [_ {:keys [linked-file-metadata linked-file-children-metadata linked-file-running-jobs
                               application-data bim-compliance-check-enabled rakennusluokka-enabled]}]]
    (let [selected-version      (get-file-version (:versions linked-file-metadata)
                                                  (get-in db [::model :selected-version :id]))
          selected-version-id   (:file-version-id selected-version)
          bim-inspection-report (->> linked-file-children-metadata
                                     (util/find-first #(-> % :target-entity :target-type (= "bim-inspection-report"))))
          bim-additional-files  (->> linked-file-children-metadata
                                     (filter #(-> % :target-entity :target-type (= "bim-additional-file")))
                                     seq)]
      {:ajaxwait/show false
       :db            (-> db
                          (assoc-in [::model :model-data] linked-file-metadata) ; note: this is used by bim viewer
                          (assoc-in [::model :application-data] application-data)
                          (assoc-in [::model :selected-version :id] selected-version-id)
                          (assoc-in [::model :bim-inspection-report] bim-inspection-report)
                          (assoc-in [::model :bim-additional-files] bim-additional-files)
                          (assoc ::bim-compliance-check-enabled bim-compliance-check-enabled
                                 ::rakennusluokka-enabled rakennusluokka-enabled)
                          util/strip-nils)
       :fx            (concat [[:dispatch [::bim-viewer-subs/load-xkt-model
                                           selected-version-id
                                           (:target-file-path selected-version)]]]
                              (when (seq linked-file-running-jobs)
                                (mapv (fn [job]
                                        [:dispatch [::poll-check-model-job {:job job}]])
                                      linked-file-running-jobs)))})))

(rf/reg-event-db ::fetch-linked-file-bim-results.success
  (fn [db [_ {:keys [data]}]]
    (if (seq data)
      (assoc-in db [::model :selected-version :check-results] (util/map-values jsutil/decode-transit data))
      (util/dissoc-in db [::model :selected-version :check-results]))))

(rf/reg-event-fx ::init-component
  (fn [{:keys [db]} [_ visible? {:keys [ajaxwait force-latest-version] :or {ajaxwait true}}]]
    (let [{:keys [app-id file-id]} (get-in db [:route/match :parameters :path])
          selected-version-id (when-not force-latest-version (get-in db [::model :selected-version :id]))]
      (if visible?
        {:fx [(when ajaxwait [:ajaxwait/show true])
              ;; Refresh applicationAuthModel so that auth checks work correctly when navigating directly
              [:application/refresh-auth app-id]
              [:action/query {:name    :fetch-linked-file-metadata
                              :params  {:id      app-id
                                        :file-id file-id}
                              :success [::fetch-linked-file-metadata.success]}]
              [:action/query {:name    :fetch-linked-file-bim-results
                              :params  (util/assoc-when {:id      app-id
                                                         :file-id file-id}
                                                        :file-version-id selected-version-id)
                              :success [::fetch-linked-file-bim-results.success]}]]
         :db (if force-latest-version
               (util/dissoc-in db [::model :selected-version :id])
               db)}
        {:db (dissoc db
                     ::model
                     ::locations-operations/pending-model-check
                     ::pending-job-ids
                     ::bim-compliance-check-enabled
                     ::rakennusluokka-enabled)
         :fx (if (seq (::pending-job-ids db))
               (mapv (fn [job-id]
                       [:dispatch [::job/stop-polling job-id]])
                     (::pending-job-ids db))
               [])}))))

(defn model-checking-error-dialog [errors]
  [:div
   (into [:div]
         (for [[type text] errors]
           [:div.pb-4
            [:div.strong (path/loc :model-details-page.check-type (bim-cp-schema/sub-task-name->check-type type))]
            [:div (common/loc text)]]))
   [:div.button-group.ok
    [:button.positive.btn-dialog
     {:on-click #(>evt [:dialog/close])}
     (common/loc :close)]]])

(rf/reg-event-fx ::job-update
  (fn [{:keys [db]} [_ job updates]]
    (let [some-job-done?        (some #(some-> updates % :status (= "done"))
                                      [:validation-check :compliance-check :xkt-generation])
          model-checking-errors (->> updates
                                     (filter (fn [[k v]]
                                               (and (#{:validation-check :compliance-check} k)
                                                    (= (:status v) "error"))))
                                     (mapv (fn [[k v]] [k (:status-message v)])))]
      {:db (when (not= (:status job) "running")
             (update db ::pending-job-ids #(-> % (disj (:id job)))))
       :fx [(when some-job-done?
              [:dispatch [::init-component true {:ajaxwait             false
                                                 :force-latest-version true}]])
            (when (seq model-checking-errors)
              [:dialog/show {:title            (common/loc :error.bim-checking-plan.dialog.title)
                             :type             :react
                             :dialog-component [model-checking-error-dialog model-checking-errors]}])]})))

(rf/reg-event-fx ::start-check-model.error
  (fn [_ [_ {:keys [text]} _]]
    {:fx [[:dialog/show {:ltitle :error.dialog.title
                         :ltext  text}]]}))

(rf/reg-event-fx ::poll-check-model-job
  (fn [{db :db} [_ {:keys [job]}]]
    {:db (update db ::pending-job-ids #(-> % (conj (:id job)) set))
     :fx [[:dispatch [::job/start-polling (:id job) (fn [job updates]
                                                      (>evt [::job-update job updates]))]]]}))

(rf/reg-event-fx ::start-check-model
  (fn [{db :db} [_ check-type operation-id file-id model-type {:keys [target-file-path bucket]}]]
    (let [application-id (router/path-parameter db :app-id)]
      {:action/command {:name    :check-model
                        :params  {:id               application-id
                                  :model-type       model-type
                                  :check-type       check-type
                                  :operation-id     operation-id
                                  :file-id          file-id
                                  :bucket           bucket
                                  :target-file-path target-file-path}
                        :success [::poll-check-model-job]
                        :error   [::start-check-model.error]}})))

(rf/reg-event-fx ::open-model-version
  (fn [{:keys [db]} [_ file-version-id]]
    {:fx [[:action/query {:name    :fetch-linked-file-bim-results
                          :params  {:id              (router/path-parameter db :app-id)
                                    :file-id         (router/path-parameter db :file-id)
                                    :file-version-id file-version-id}
                          :success [::fetch-linked-file-bim-results.success]}]
          [:dispatch [::bim-viewer-subs/load-xkt-model
                      file-version-id
                      (:target-file-path (get-file-version (get-in db [::model :model-data :versions]) file-version-id))]]]
     :db (assoc-in db [::model :selected-version :id] file-version-id)}))

(rf/reg-event-fx ::model-version-upload.success
  (fn [_ _]
    {:fx [[:dispatch [::init-component true {:ajaxwait             false
                                             :force-latest-version true}]]]}))

(rf/reg-event-fx ::back-to-structures
  (fn [_ [_ app-id]]
    {:page/open [:application app-id :structures]}))

(rf/reg-event-fx ::click-file-input
  (fn [_ [_ id]]
    {:click/id id}))

(rf/reg-sub ::model :-> ::model)

(rf/reg-sub ::bim-compliance-check-enabled :-> ::bim-compliance-check-enabled)

(rf/reg-sub ::app-id
  :<- [:route/path-parameters]
  :-> :app-id)

(rf/reg-sub ::application-data
  :<- [::model]
  :-> :application-data)

(rf/reg-sub ::ryhti-data-2026
   :<- [::application-data]
   :-> :ryhti-data-2026)

(rf/reg-sub ::application-address
  :<- [::application-data]
  :-> :address)

(rf/reg-sub ::operation-id
  :<- [::model-data]
  :-> (comp :operation-id :target-entity))

(rf/reg-sub ::operation-name
  :<- [::application-data]
  :<- [::operation-id]
  (fn [[application-data operation-id] _]
    (->> application-data
         ((juxt (comp vector :primaryOperation) :secondaryOperations))
         (apply concat)
         (util/find-by-key :id operation-id)
         :name)))

(rf/reg-sub ::model-data
  :<- [::model]
  :-> :model-data)

(rf/reg-sub ::file-id
  :<- [:route/path-parameters]
  :-> :file-id)

(rf/reg-sub ::model-metadata
  :<- [::model-data]
  :-> :metadata)

(rf/reg-sub ::model-type
  :<- [::model-metadata]
  :-> :model-type)

(rf/reg-sub ::valid-model-phase? ; computed and assoc:ed in backend
  :<- [::model-metadata]
  :-> :valid-model-phase?)

(rf/reg-sub ::rakennusluokka
  :<- [::model-data]
  :-> (comp :rakennusluokka :target-entity))

(rf/reg-sub ::rakennusluokka-enabled :-> ::rakennusluokka-enabled)

(rf/reg-sub ::versions
  :<- [::model-data]
  :-> :versions)

(rf/reg-sub ::selected-version-id
  :<- [::model]
  :-> (comp :id :selected-version))

(rf/reg-sub ::selected-version
  :<- [::versions]
  :<- [::selected-version-id]
  (fn [[versions selected-version-id] _]
    (get-file-version versions selected-version-id)))

(def version-number (comp (partial ss/join ".") (juxt :major :minor) :version))

(rf/reg-sub ::selected-version-number
  :<- [::selected-version]
  :-> version-number)

(defn version-edit-info [version]
  (str
    (-> version :uploaded-at model-utils/format-fi-datetime)
    ", "
    (->> version :uploaded-by ((juxt :firstName :lastName)) (ss/join " "))))

(rf/reg-sub ::selected-version-edit-info
  :<- [::selected-version]
  :-> version-edit-info)

(rf/reg-sub ::selected-version-info
  :<- [::selected-version-number]
  :<- [::selected-version-edit-info]
  (fn [[version-number edit-info] _]
    (str version-number " " "(" edit-info ")")))

(rf/reg-sub ::selected-version-checked-ts
  :<- [::selected-version]
  (fn [version [_ check-type]]
    (some-> (bim-cp-schema/linked-file-version-check-result check-type version)
            :timestamp
            model-utils/format-fi-datetime)))

(rf/reg-sub ::selected-version-file-name
  :<- [::selected-version]
  :-> (comp :name :metadata))

(rf/reg-sub ::selected-version-file-size
  :<- [::selected-version]
  :-> (comp :size :metadata))

(rf/reg-sub ::selected-version-file-id
  :<- [::selected-version]
  :-> :file-version-id)


(rf/reg-sub ::selected-version-target-path
  :<- [::selected-version]
  :-> :target-file-path)

(rf/reg-sub ::selected-version-ifc-version
  :<- [::selected-version]
  :-> (comp ss/blank-as-nil :schema :ifc-metadata :bim))

(rf/reg-sub ::selected-version-ifc-timestamp
  :<- [::selected-version]
  (fn [selected-version _]
    (some-> selected-version
            :bim
            :ifc-metadata
            :createdAt
            model-utils/format-fi-datetime)))

(rf/reg-sub ::bim-inspection-report
  :<- [::model]
  :-> :bim-inspection-report)

(rf/reg-sub ::bim-additional-files
  :<- [::model]
  :-> (comp (partial sort-by (comp :uploaded-at get-file-version :versions))
            :bim-additional-files))

(rf/reg-sub ::pending-job-ids :-> ::pending-job-ids)

(rf/reg-sub ::check-pending?
  (fn [[_ pending-job-ids check-type]]
    (->> pending-job-ids
         (mapv #(rf/subscribe [::job/in-progress? % check-type]))))
  (fn [checks-in-progress _]
    (boolean (some identity checks-in-progress))))

(rf/reg-event-fx ::delete-additional-file
  (fn [{:keys [db]} [_ file-id confirmed?]]
    (if confirmed?
      (let [application-id (router/path-parameter db :app-id)]
        {:action/command {:name    :delete-linked-file
                          :params  {:id      application-id
                                    :file-id file-id}
                          :success [::model-version-upload.success]}})
      {:dialog/show {:ltitle   :areyousure
                     :ltext    :model-details-page.remove-file-confirm
                     :type     :yes-no
                     :callback #(>evt [::delete-additional-file file-id true])}})))

(rf/reg-event-fx ::save-additional-file-metadata
  (fn [{:keys [db]} [_ file-version-id field-key value]]
      (let [application-id (router/path-parameter db :app-id)]
        {:action/command {:name :update-linked-file-version-metadata
                          :params  {:id              application-id
                                    :file-version-id file-version-id
                                    :value           value
                                    :field-key       field-key}
                          :success [::model-version-upload.success]}})))
