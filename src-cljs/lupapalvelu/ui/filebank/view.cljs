(ns lupapalvelu.ui.filebank.view
  (:require [clojure.set :refer [subset?]]
            [clojure.string :as ss]
            [goog.object :as googo]
            [lupapalvelu.ui.attachment.components :refer [upload-wrapper]]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [loc query command]]
            [lupapalvelu.ui.components :refer [icon-button search-bar]]
            [lupapalvelu.ui.filebank.filter-box :refer [filter-box]]
            [lupapalvelu.ui.filebank.keyword-container :refer [keyword-container]]
            [lupapalvelu.ui.filebank.upload :as up]
            [rum.core :as rum]
            [sade.shared-util :refer [find-first]]))

;; The component args are stored here; see the end of the file
(defonce args (atom {}))
(def file-input-id "filebank-file-input")
(defn- application-id! [] (:application-id @args))

(defn- get-files!
  "Get files from database and update the file list"
  []
  (query "get-filebank-files"
         #(swap! args assoc :files (:files %))
         :id (application-id!)))

(defn- display-upload-job!
  "Adds the uploaded files to a preview window where the user can add keywords to them.
   Note that unlike attachments, the file is already in the filebank at this point and refreshing the page
   hides the preview."
  [job]
  (swap! args assoc-in [:upload-jobs (:id job)] job)
  (get-files!))

(defn- download-all-files!
  "Downloads all the files in a ZIP.
   The window is redirected to a GET request for raw actions.
   Does not factor into filtering; the same as the attachment view"
  []
  (->> (map :file-id (:files @args))
       (interpose ",")
       (apply str "?id=" (application-id!) "&fileIds=")
       (str "/api/raw/download-filebank-files")
       (.assign (.-location js/window))))

(defn- find-first-job-id-with-file-id
  "A clarifying function for handling the convoluted structure of a job"
  [file-id jobs]
  (->> jobs
       (find-first #(some #{file-id} (map :fileId (vals (:value %)))))
       :id))

(defn- remove-file!
  "Removes file from the upload job list (if exists) as well as file storage and the database document"
  [filebank-id file-id]
  (->> (vals (:upload-jobs @args))
       (find-first-job-id-with-file-id file-id)
       (swap! args update :upload-jobs dissoc))
  (command "delete-filebank-file"
           get-files!
           :id filebank-id
           :fileId file-id))

(defn- save-keywords!
  "Updates keywords for filebank file"
  [filebank-id file-id keywords]
  (command "update-filebank-keywords"
           get-files!
           :id filebank-id
           :fileId file-id
           :keywords (filterv (comp not ss/blank?) keywords)))

(rum/defc file-row
  "Creates row that contains info about the file"
  [filebank-id test-id-prefix i {:keys [file-id filename size modified user keywords]}]
  [:tr
   [:td [:a {:href          (str "/api/raw/download-filebank-file"
                                 "?id=" filebank-id
                                 "&fileId=" file-id)
             :data-test-id  (str test-id-prefix i "-download")}
         filename
         [:i.lupicon-download.btn-small]]]
   [:td (int (/ size 1024)) " kB"]
   [:td (.finnishDateAndTime js/util modified "DD.M.YYYY HH:mm")]
   [:td (user :firstName) " " (user :lastName)]
   [:td (keyword-container keywords {:test-id   (str test-id-prefix i "-keywords")
                                     :callback  #(save-keywords! filebank-id file-id %)})]
   [:td (icon-button {:test-id  (str test-id-prefix i "-remove-file")
                      :on-click #(remove-file! filebank-id file-id)
                      :icon     :lupicon-remove
                      :class    "secondary"})]])

(rum/defc file-list
  "List that contains files that are in the filebank"
  [filebank-id files filter-string filter-by-keywords]
  [:div
   [:table#files
    [:thead
     [:tr
      [:th (loc "attachment.file")]
      [:th (loc "attachment.file.size")]
      [:th (loc "applications.updated")]
      [:th (loc "user")]
      [:th (loc "application.tags")]
      [:th (loc "remove")]]]
    (into [:tbody]
          (for [[i file] (->> files
                              (filter #(ss/includes? (ss/lower-case (:filename %)) (ss/lower-case filter-string)))
                              (filter #(subset? filter-by-keywords (into #{} (:keywords %))))
                              (map-indexed vector))
                :let [test-id-prefix "filebank-row-"]]
            (file-row filebank-id test-id-prefix i file)))]])

(defn- filter-by-filename
  "Causes the filelist to be filtered by filename"
  [filter-string]
  (swap! args assoc :filter-by-filename filter-string))

(defn- callback-for-filterbox-filter-by-keywords
  "Causes the filelist to be filtered by keywords"
  [chosen-filter-keywords]
  (swap! args assoc :filter-by-keywords chosen-filter-keywords))

(defn- file-list-header
  "The filter-box, search-bar, etc"
  [files]
  [:div.form-grid.form-grid--no-border
   [:div.row
    [:div.col-2.attachment-operations
     (search-bar {:test-id      "search-files-bar"
                  :placeholder  (loc "filebank.search.placeholder")
                  :callback     filter-by-filename})]
    [:div.col-1.attachment-operations
     (icon-button {:test-id   "download-all-files"
                   :icon      :lupicon-download
                   :text-loc  "download-all-short"
                   :on-click  download-all-files!
                   :class     "ghost"})]]
   (filter-box (->> files (map :keywords) flatten distinct sort)
               {:callback #(callback-for-filterbox-filter-by-keywords %)
                :test-id "filter-box"})])

(rum/defc filebank-view < rum/reactive
  "Filebank view for authorities"
  []
  (let [{:keys [application-id files filter-by-filename filter-by-keywords upload-jobs]} (rum/react args)]
    [:div
     [:h2 (loc "application.tabFilebank")]
     (upload-wrapper {:callback   #(up/link-files display-upload-job! (application-id!) (js->clj (:files %)))
                      :dropzone   ".drop-zone-highlight"
                      :multiple?  true
                      :input-id   file-input-id
                      :test-id    "filebank-upload"
                      :component  (fn [{:keys [input]}] (up/upload-zone input file-input-id))})
     (if (empty? upload-jobs) ;; Show either list of files or preview
       (if (empty? files)
         [:div [:span (loc :application.attachmentsEmpty)]]
         [:div
          (file-list-header files)
          (file-list application-id files filter-by-filename filter-by-keywords)])
       [:div
        [:table
         (->> (vals upload-jobs)
              (map (comp vals :value))
              (flatten)
              (map #(up/enrich-with-file-info % files))
              (zipmap (range))
              (mapv (fn [[i {:keys [file-id] :as file}]]
                      (up/upload-preview-row #(save-keywords! application-id file-id %)
                                             #(remove-file! application-id file-id)
                                             (str "filebank-upload-" i)
                                             file)))
              (into [:tbody]))]
        (icon-button {:icon      :lupicon-check
                      :test-id   "finish-upload"
                      :text-loc  "done"
                      :class     :positive
                      :on-click  #(swap! args assoc :upload-jobs {})})])]))

(defn mount-component []
  "Rum component:method: called when the component is added to the page"
  (rum/mount (filebank-view)
             (.getElementById js/document (:dom-id @args))))

(defn ^:export start [domId params] ;params = component parameters from template
  "Rum component method: called when the component starts up"
  (let [auth-model      (googo/get params "authModel")
        application-id  ((googo/get params "applicationId"))
        user-id         (googo/get params "userId")]
    (swap! args assoc
           :auth-model          auth-model
           :dom-id              (name domId)
           :application-id      application-id
           :user-id             (user-id)
           :filter-by-filename  ""
           :filter-by-keywords  #{}
           :upload-jobs         {})
    (when (auth/ok? auth-model :filebank-enabled)
      (get-files!)
      (mount-component))))
