(ns lupapalvelu.common.upload
  (:require [lupapalvelu.common.hub :as hub]))

(defn destroy-file-upload-subscriptions [input subscriptions]
  (hub/send "fileuploadService::destroy" {:input input})
  (doseq [id subscriptions]
    (hub/unsubscribe id))
  true)

(defn bindToElem
  "Call through for JS fileuploadService/bindFileInput"
  ([options]
   (.bindFileInput js/lupapisteApp.services.fileUploadService options))
  ([options plugin-opts]
   (.bindFileInput js/lupapisteApp.services.fileUploadService options plugin-opts)))

(defn service-hubscribe
  "Input-id is the file input id.

  Callback is a function or map. If map, the possible keys
  are :added, :bad, :pending, :finished, :success, :failed
  or :progress and values are corresponding callback functtions. Just
  a a function is a shorthand for a map with :success. Returns a
  corresponding map with subscription ids."
  [input-id callback]
  (reduce-kv (fn [acc k fun]
               (let [[event status-filter]
                     (case k
                       :added    [:fileAdded]
                       :bad      [:badFile]
                       ;; NOTE: These events have different state/status property
                       :pending  [:filesUploading {:state "pending"}]
                       :finished [:filesUploading {:state "finished"}]
                       :success  [:filesUploaded {:status "success"}]
                       :failed   [:filesUploaded {:status "failed"}]
                       :progress [:filesUploadingProgress])]
                 (assoc acc k (hub/subscribe
                                (merge {:eventType (str "fileuploadService::"
                                                        (name event))
                                        :input input-id}
                                       status-filter)
                                fun))))
             {}
             (if (map? callback)
               callback
               {:success callback})))
