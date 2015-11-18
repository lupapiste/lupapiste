(ns lupapalvelu.file-upload-api
  (:require [noir.core :refer [defpage]]
            [noir.response :as resp]
            [lupapalvelu.mongo :as mongo]))

(defn- save-file [file]
  (let [file-id (mongo/create-id)]
    {:id file-id
     :filename (:filename file)
     :size (:size file)}))

(defpage [:post "/upload/file"] {files :files}
  (prn "asdasd" files)
  (let [file-info {:files (map save-file files)}]
    (->> (assoc file-info :ok true)
      (resp/json)
      (resp/content-type "text/plain")
      (resp/status 200))))
