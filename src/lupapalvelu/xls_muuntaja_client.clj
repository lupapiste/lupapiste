(ns lupapalvelu.xls-muuntaja-client
  (:require [sade.http :as http]
            [sade.env :as env]
            [taoensso.timbre :as timbre]))

(def xls-2-csv-path "/api/xls2csv")

(defn xls-2-csv [xls]
  (timbre/info "Sending premises excel file" (.getName xls) "to muuntaja for processing")
  (try
    (let [request-opts                             {:multipart [{:name "file" :content xls}]}
          {:keys [status body error] :as response} (http/post (str (env/value :muuntaja :url) xls-2-csv-path) request-opts)]
      (if (= status 200)
        (do
          (timbre/info "Successfully converted Excel file to csv")
          {:data body})
        (do
          (timbre/warn "Muuntaja failed to process the Excel file: " error)
          nil)))
    (catch Exception ex
      (timbre/error ex)
      nil)))