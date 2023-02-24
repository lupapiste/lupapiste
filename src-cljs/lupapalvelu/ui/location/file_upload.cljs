(ns lupapalvelu.ui.location.file-upload
  "Upload file to GCP"
  (:require [ajax.core :refer [PUT]]
            [goog.crypt.Md5]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.ui.common :refer [loc] :as common]
            [lupapalvelu.ui.location.md5-util :as md5-util]
            [promesa.core :as promesa]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.shared-strings :as ss]
            [sade.shared-util :as util]))

;; Utility functions

(defn generate-component-id
  [operation-id & {:keys [file-id]}]
  (when (ss/blank? operation-id) (throw (js/Error. "operation-id vas not be nil")))
  (str "file-upload_operation-id-" operation-id (when file-id (str "_file-id-" file-id))))

(defn adjust-update-state
  "When there is no more pending files set upload state :ready"
  [db component-id]
  (if (seq (get-in db [::upload component-id :pending-files]))
    db
    ;; the last pending file is removed
    (assoc-in db [::upload component-id :state] :ready)))

(defn ->file-element-id [component-id] (str component-id "-file-input"))

(defn get-pending-file
  [db component-id file-id]
  (get-in db [::upload component-id :pending-files file-id]))

(defn get-upload-state
  [db component-id]
  (get-in db [::upload component-id]))

(defn open-filepicker [element e]
  (when e (.preventDefault e))
  (-> (.getElementById js/document element)
      (.click)))

(defn get-file-input-of [component-id]
  (let [file-element-id (->file-element-id component-id)
        file-element (.getElementById js/document file-element-id)]
    file-element))

(defn read-files
  "Iterates though all files in an input file element, and generates md5 hash for each file.
   Dispatch :pending-file-prepared sub-event after md5 diggest is calculated for a file."
  [component-id file-element]
  (letfn [(->pending-file [file md5]
            {:md5  md5
             :file file
             :name (.-name file)
             :size (.-size file)})

          (dispatch-fn [pending-file]
            (>evt [::upload-event component-id :pending-file-prepared :pending-file pending-file]))

          (read-file-and-dispatch [file on-progress]
            (promesa/let [md5-hash (md5-util/calculater-md5-digest file on-progress)]
                         (-> (->pending-file file md5-hash) dispatch-fn)))]
    (doseq [file (array-seq (.-files file-element))
            :let [total-size (.-size file)
                  on-progress-fn (fn [end-position]
                                   (>evt [::upload-event component-id :md5-progress-updated
                                          :md5-progress {:total total-size :current (inc end-position)}]))]]
      (read-file-and-dispatch file on-progress-fn))))

(defn upload-event-hander
  "::update-state handles the overall update process.

  It has following operations (ops) / sub-event

  - :init will set upload component state and callback functions to db.
     Init is fired when the component is mounted.
  - :reset will reset upload specific field (:metadata, :pending-files, :state) but not remaining initalized data.
     Note: when file-id is set, component uploads a version to an existing file; hence it is not upload specific data.
  - :files-selected set upload component state into :upload-when-possible state when autoupload is true,
     otherwise :files-selected
     File selected is fired when the user has selected file(s) for upload.
     After this it will set content-type and calc md5 hash for each file (::read-files event)
  - :md5-progress is triggered when files are selected once per each 10MB chunk. (E.g. if file is 100MB,
     then it is triggered 10 times).
  - :pending-file-prepared is fired once per successfully prepared file (file has content-type and md5 hash).
     After this event a file should have valid upload url.
  - :upload-initialized-for is fired ones per successfully prepared file (file has content-type and md5 diggest).
     After this it should automatically upload file to GCP (::upload-file event)
  - :upload-started when upload has actually started (i.e. file is no longer in any of the pre-upload states).
  - :pending-files-removed will remove files from pending file map. If the removed pending file was the last one
     it will automatically set upload state ready.
     :pending-files-removed if fired in following situation:
      - Upload is succeeded (i.e. file is in GCP)
      - Upload is failed
      - Initialize resumable upload is failed
      - Read-files is failed

    The happy case flow is:
    1) component mounted -> :init
    2) user select file -> :files-selected (default) or :upload-when-possible when autoupload
       - :md5 hash is computed, once done init resumable upload (:init-resumable-upload)
       - once uploade is initialized for a pinding file (::init-resumable-upload.success)
         it ready for upload
    3) [optional] user updates metadata for the file and then trigger upload (:upload-triggered)
       - UI and logic for this is outside file upload component
       - NOTE: this step is skipped when :auto-upload is true
       - NOTE 2: It's possible that the user reselect files at this point.
    4) For each file (once in state :upload-when-possible):
      -> upload
      -> notify component parent (using on-uploaded-fn callback)
      -> remove pending file
    5) Once all files are processed -> :reset"
  [{db :db} [_ component-id op &
             {:keys [on-uploaded-file-fn
                     file-id
                     pending-file-id
                     pending-file
                     auto-upload
                     uploaded-bytes
                     md5-progress]}]]
  (let [pending-file-id (or pending-file-id (:md5 pending-file))
        {:keys [state auto-upload pending-files file-id]}
        (merge
          (get-in db [::upload component-id])
          (when (some? file-id) {:file-id file-id})
          (when (some? auto-upload) {:auto-upload auto-upload}))

        updated-database (case op

                           :init
                           (assoc-in db [::upload component-id] {:on-uploaded-file-fn on-uploaded-file-fn
                                                                 :file-element-id     (->file-element-id component-id)
                                                                 :auto-upload         (boolean auto-upload)
                                                                 :pending-files       {}
                                                                 :file-id             file-id
                                                                 :metadata            {}
                                                                 :uploading-data?     false
                                                                 :state               :ready})
                           :reset
                           (update-in db [::upload component-id] #(merge % {:state           :ready
                                                                            :metadata        {}
                                                                            :pending-files   {}
                                                                            :md5-progress    nil
                                                                            :uploading-data? false}))

                           :files-selected
                           (-> db
                               (assoc-in [::upload component-id :pending-files] {})
                               (assoc-in [::upload component-id :state]
                                         (if auto-upload :upload-when-possible :files-selected)))

                           :md5-progress-updated
                           (let [{:keys [total current]} md5-progress
                                 in-progress? (and total current (> total current))
                                 percentage (common/->percentage current total)]
                             (assoc-in db [::upload component-id :md5-progress]
                                       (merge md5-progress {:in-progress? in-progress? :percentage percentage})))

                           :upload-triggered
                           (assoc-in db [::upload component-id :state] :upload-when-possible)

                           :upload-started
                           (assoc-in db [::upload component-id :uploading-data?] true)

                           :pending-file-prepared
                           (assoc-in db [::upload component-id :pending-files pending-file-id] pending-file)

                           :upload-initialized-for
                           (assoc-in db [::upload component-id :pending-files pending-file-id] pending-file)

                           :upload-progress
                           (assoc-in db [::upload component-id :pending-files pending-file-id :uploaded-bytes] uploaded-bytes)

                           :pending-file-removed
                           (-> (update-in db [::upload component-id :pending-files] dissoc pending-file-id)
                               (adjust-update-state component-id))

                           (throw (js/Error. (str "invalid ::upload-state operation: " op " in " component-id))))
        triggered-effects (cond-> {:fx []}
                                  (= op :files-selected)
                                  (update :fx concat [[:dispatch [::read-files component-id]]])

                                  (= op :pending-file-prepared)
                                  (update :fx concat [[:dispatch [::init-resumable-upload-for-file component-id pending-file-id]]])

                                  (and (= op :upload-initialized-for) (= state :upload-when-possible))
                                  (update :fx
                                          concat
                                          [[:dispatch [::upload-event component-id :upload-started]]
                                           [:dispatch [::upload-file component-id pending-file-id]]])

                                  (= op :upload-triggered)
                                  ;; TODO: check if file has upload url, when/if you add support for multifile upload
                                  ;; this is not problem is there is only one file
                                  (update :fx
                                          concat
                                          [[:dispatch [::upload-event component-id :upload-started]]]
                                          (for [[file-id _] pending-files]
                                            [:dispatch [::upload-file component-id file-id]])))]
    (merge {:db updated-database}
           triggered-effects)))

;; Events

(rf/reg-event-db
  ::assoc-in-upload-metadata
  (fn [db [_ component-id property value]]
    (assoc-in db [::upload component-id :metadata property] (ss/blank-as-nil value))))

(rf/reg-sub
  ::get-upload-metadata
  (fn [db [_ component-id]]
    (get-in db [::upload component-id])))

(rf/reg-event-fx
  ::upload-event
  (fn [coef event] (upload-event-hander coef event)))

(rf/reg-event-fx
  ::upload-file
  (fn [{db :db} [_ component-id pending-file-id]]
    (let [{:keys [upload-url file-id file content-type]} (get-pending-file db component-id pending-file-id)]
      (PUT upload-url {:headers          {"Content-Type" content-type}
                       :body             file
                       :handler          #(>evt [::on-upload-file.success component-id pending-file-id])
                       :progress-handler #(>evt [::upload-event component-id :upload-progress
                                                 :pending-file-id pending-file-id
                                                 :uploaded-bytes (.-loaded %)])
                       :error-handler    (fn [{:keys [response status]}]
                                           (>evt [::on-upload-file.error component-id pending-file-id status response]))})
      {})))

(rf/reg-fx
  ::upload-file
  (fn [_ upload-url request]
    (PUT upload-url request)))

(rf/reg-event-fx
  ::on-upload-file.success
  (fn [{db :db} [_ component-id pending-file-id]]
    (let [{:keys [on-uploaded-file-fn]} (get-upload-state db component-id)
          {:keys [metadata]} (get-in db [::upload component-id])
          pending-file (get-pending-file db component-id pending-file-id)]
      (on-uploaded-file-fn
        (select-keys pending-file [:file-id :file-version-id :md5 :file :pending-file-id :content-type :name :size])
        metadata
        component-id)
      {:dispatch [::upload-event component-id :pending-file-removed :pending-file-id pending-file-id]})))

(rf/reg-event-fx
  ::on-upload-file.error
  (fn [{db :db} [_ component-id pending-file-id status response]]
    (let [{:keys [on-upload-failed-fn]} (get-upload-state db component-id)
          pending-file (get-pending-file db component-id pending-file-id)]
      (when on-upload-failed-fn (on-upload-failed-fn pending-file))
      {:dialog/show {:title-loc :file-upload.loading-error
                     :text-loc  :file-upload.loading-error-text}
       :dispatch    [::upload-event component-id :pending-file-removed :pending-file-id pending-file-id]})))

(rf/reg-event-fx
  ::init-resumable-upload-for-file
  (fn [{db :db} [_ component-id pending-file-id]]
    (let [application-id (:lupapalvelu.ui.location.locations-operations/current-application-id db)
          file-id (get-in db [::upload component-id :file-id])
          {:keys [md5 name]} (get-pending-file db component-id pending-file-id)]
      {:action/command {:name       :init-resumable-upload-for-application
                        :params     {:id         application-id
                                     :filename   name
                                     :md5-digest md5
                                     :file-id    file-id}
                        :success    [::init-resumable-upload-for-file.success component-id pending-file-id]
                        :error      [::init-resumable-upload-for-file.failed component-id pending-file-id]
                        :on-timeout [::init-resumable-uplaoad-for-file.failed component-id pending-file-id]}})))

(rf/reg-event-fx
  ::init-resumable-upload-for-file.success
  (fn [{db :db} [_ result component-id pending-file-id]]
    (let [prepared-file (get-pending-file db component-id pending-file-id)
          ready-to-upload (merge prepared-file result)]
      {:dispatch [::upload-event component-id :upload-initialized-for :pending-file ready-to-upload]})))

(rf/reg-event-fx
  ::init-resumable-upload-for-file.failed
  (fn [{db :db} [_ _result component-id file-id]]
    {:dispatch [::upload-event component-id :pending-file-removed file-id]}))

(rf/reg-event-fx
  ::open-file-picker
  (fn [_ [_ component-id e]]
    (open-filepicker (->file-element-id component-id) e)
    {}))

(rf/reg-event-fx
  ::read-files
  (fn [{db :db} [_ component-id]]
    (let [file-element-id (->file-element-id component-id)
          file-element (.getElementById js/document file-element-id)]
      (read-files component-id file-element))
    {}))

;; Subscriptions

(rf/reg-sub
  ::upload-in-progress?
  (fn [db [_ component-id]]
    (not= :ready (get-in db [::upload component-id :state]))))

(rf/reg-sub
  ::pending-files-for
  (fn [db [_ component-id]]
    (-> (get-in db [::upload component-id :pending-files])
        (vals))))

(rf/reg-sub
  ::uploading-data?
  (fn [db [_ component-id]]
    (get-in db [::upload component-id :uploading-data?])))

(rf/reg-sub
  ::processing-md5?
  (fn [db [_ component-id]]
    (get-in db [::upload component-id :md5-progress :in-progress?])))

(rf/reg-sub
  ::is-ready?
  (fn [db [_ component-id]]
    (= :ready (get-in db [::upload component-id :state]))))

;; UI

(defn file-picker
  "Creates a hidden upload input for a file picker. File picker can be associated to an upload button or drop area.

  Props:
  `id`             component id
  `file-id`        id of a related file. When given, new version is added to and existing file, instead of creating
                   a new file. If not given, new file is created and the uploaded file will be the first version of it.
  `on-select-fn`   called when the user selects a file, called with three arguments:
                      file           for doing whatever it is you want to do
                      component id   for updating the pending status of the file picker
                      file hash      for identifying the file
                    note that you will have to mark the file processing as complete after this is done
  `accept-types`    a seq of string file types formats, e.g. [\".pdf\"]
  `class`           CSS classes given to the button container

  TODO: add support for:
  `multiple`        true if multiple files can be chosen"
  [{:keys [id on-uploaded-file-fn class accept-types file-id auto-upload]}]
  (r/with-let [_ (>evt [::upload-event id
                        :init
                        :on-uploaded-file-fn on-uploaded-file-fn
                        :file-id file-id
                        :auto-upload auto-upload])]
              [:div.upload-button {:class class}
               [:input (util/assoc-when {:type      "file"
                                         :id        (->file-element-id id)
                                         :style     {:display "none"}
                                         :on-change #(>evt [::upload-event id :files-selected])
                                         :accept    (ss/join "," accept-types)})]]))

(defn progress-bar [total current]
  (let [percent (common/->percentage current total)]
    (when percent
      [:div {:style {:background-color "lightgray" :height "10px" :width "100%"}}
       [:div {:style {:width (str percent "%") :background-color "green" :height "100%"}}]])))

(defn status-in-bytes [total current]
  (let [{:keys [size-in-kt uploaded-in-kt :sizes-calculated?]}
        (when (and total current (not= 0 total))
          {:sizes-calculated? true
           :size-in-kt        (Math/round (/ total 1024))
           :uploaded-in-kt    (Math/round (/ current 1024))})]
    (when sizes-calculated?
      [:div.upload-ration-in-kt
       (str (common/format-number uploaded-in-kt)
            " / "
            (common/format-number size-in-kt)
            " "
            (loc :file-upload.unit.kt))])))

(defn file-preparation-progress-indicator [component-id]
  (let [{:keys [md5-progress]} (<sub [::get-upload-metadata component-id])
        {:keys [in-progress? current total percentage]} md5-progress]
    (when in-progress?
      [:div.upload-progress-indicator
       [:div.upload-phase (loc :file-upload.preparing) " " percentage " %"]
       [progress-bar total current]
       [status-in-bytes total current]])))

(defn file-loading-indicator [component-id]
  (let [pending-files (<sub [::pending-files-for component-id])
        uploading-data? (<sub [::uploading-data? component-id])
        processing-md5? (<sub [::processing-md5? component-id])
        has-pending-upload? (seq pending-files)
        {:keys [size uploaded-bytes]} (first pending-files)
        uploaded-percent (common/->percentage uploaded-bytes size)]
    (cond
      processing-md5?
      [file-preparation-progress-indicator component-id]

      uploading-data?
      [:div.upload-progress-indicator
       [:div.upload-phase (str (loc :file-upload.loading) " " uploaded-percent " %")]
       [progress-bar size uploaded-bytes]
       [status-in-bytes size uploaded-bytes]]

      has-pending-upload?
      [:div.upload-progress-indicator
       [:div.upload-phase (loc :file-upload.preparing)]])))
