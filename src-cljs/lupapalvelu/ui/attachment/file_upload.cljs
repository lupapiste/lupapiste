(ns lupapalvelu.ui.attachment.file-upload
  (:require [lupapalvelu.ui.hub :as hub]))

(defn bindToElem [options]
  (.bindFileInput js/lupapisteApp.services.fileUploadService options))

(defn subscribe-bind-attachments-status [opts callback]
  (hub/subscribe (assoc opts :eventType "attachmentsService::bind-attachments-status") callback))

(defn bind-attachments [files]
  (.bindAttachments js/lupapisteApp.services.attachmentsService files))

(defn service-hubscribe
  "Input-id is the file input id.

  Callback is a function or map. If map, the possible keys
  are :added, :bad, :pending, :finished, :success, :failed
  or :progress and values are corresponding callback functtions. Just
  a a function is a shorthand for a map with :success. Returns a
  corresponding map with subscription ids."
  [input-id callback]
  (reduce-kv (fn [acc k fun]
               (let [[event status] (case k
                                      :added    [:fileAdded]
                                      :bad      [:badFile]
                                      :pending  [:filesUploading :pending]
                                      :finished [:filesUploading :finished]
                                      :success  [:filesUploaded :success]
                                      :failed   [:filesUploaded :failed]
                                      :progress [:filesUploadingProgress])]
                 (assoc acc k (hub/subscribe
                               (merge {:eventType (str "fileuploadService::"
                                                       (name event))
                                       :input input-id}
                                      (when status
                                        {:status (name status)}))
                               fun))))
             {}
             (if (map? callback)
               callback
               {:success callback})))

(defn- message-filedata [message]
  (if-let [file (.-file message)]
    {:last-modified (.-lastModified file)
     :filename      (.-name file)
     :size          (.-size file)
     :type          (.-type file)
     :file-id       (.-fileId file)}
    (do
      (->> (.-files message)
          (map (fn [file]
                 {:filename (.-filename file)
                  :file-id  (.-fileId file)}))))))


(defn file-monitors
  "Returns callback function map that updates files* atom. The supported
  callbacks are :added, :bad, :success, :failed and :progress.

  Note: This does not work correctly if any files have identical names."
  [files*]
  (letfn [(file-state [msg state]
            (let [changed (->> [(message-filedata msg)]
                               flatten
                               (map (fn [{k :filename :as file}]
                                      [k file]))
                               (into {}))]
              (swap! files* (fn [files]
                              (map (fn [{:keys [filename] :as file}]
                                     (if-let [c-file (get changed filename)]
                                       (merge (assoc file
                                                     :state state)
                                              (select-keys c-file [:file-id]))
                                       file))
                                   files)))))]
    {:added    (fn [msg]
                 (swap! files* #(conj (vec %) (message-filedata msg))))
     :bad      #(file-state % :bad)
     :success  #(file-state % :success)
     :failed   #(file-state % :failed)
     :progress #(file-state % :progress)}))
