(ns lupapalvelu.xls-muuntaja-client
  (:require [sade.http :as http]
            [sade.env :as env]
            [taoensso.timbre :as timbre]))

(def xls-2-csv-path "/api/xls2csv")

(defn xls-2-csv [file-request-map]
  (timbre/info "Sending premises excel file" (:filename file-request-map) "to muuntaja for processing")
  (try
    (let [request-opts                {:multipart [{:name "file" :content (:tempfile file-request-map)}]
                                       :throw-exceptions false}
          {:keys [status body error]} (http/post (str (env/value :muuntaja :url) xls-2-csv-path) request-opts)]
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
