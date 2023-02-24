(ns lupapalvelu.ui.filebank.upload
  (:require [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.filebank.keyword-container :refer [keyword-container]]
            [sade.shared-util :as util]))

(defn- poll-file-binding-job!
  "Listens to the binding job and updates the view when it's done"
  [update-fn job]
  (when job
    (update-fn job)
    (when (not= "done" (:status job))
      (query :bind-filebank-job
             #(poll-file-binding-job! update-fn (:job %))
             :jobId (:id job)))))

(defn link-files
  "Starts jobs for binding the files to the filebank from their temp storage and
   begins polling for their completion."
  [update-fn filebank-id files]
  (command :bind-filebank-files
           #(poll-file-binding-job! update-fn (:job %))
           :id filebank-id
           :filedatas files))

(defn upload-zone
  "Upload zone for files"
  [file-input file-input-id]
  [:div.drop-zone-highlight
   [:drop-zone.hidden
    [:div.drop-zone-placeholder
     [:i.lupicon-upload ]
     [:div (loc "dropzone.placeholder")]]]
   file-input
   [:div.upload-zone
    [:i.lupicon-upload]
    [:span.b (loc "uploadzone.text-one.part-one")]
    " "
    [:span (loc "uploadzone.text-one.part-two")]
    " "
    [:label {:data-test-id "choose-file-button"
             :on-click #(.click (.getElementById js/document file-input-id))}
     (loc "uploadzone.label")]
    " "
    [:span (loc "uploadzone.text-two")]]])

(defn enrich-with-file-info
  "Merges the job info with the file info related to that job"
  [job files]
  (->> files
       (util/find-first #(= (:file-id %) (:fileId job)))
       (#(or % {}))
       (merge job)))

(defn upload-preview-row
  "A single row for upload-preview"
  [save-fn remove-fn test-id {:keys [filename keywords]}]
  [:tr
   [:td filename]
   [:td (keyword-container keywords {:callback save-fn :test-id test-id})]
   [:td [:i.lupicon-remove.primary {:on-click remove-fn}]]])
