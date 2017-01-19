(ns lupapalvelu.attachment.muuntaja-client
  (:require [sade.http :as http]
            [sade.env :as env]
            [taoensso.timbre :as timbre]
            [sade.strings :as str])
  (:import [java.io File]))

(def unzip-attachments-path "/api/unzip-attachments")

(defn unzip-attachment-collection [^File zip-file]
  (timbre/info "Sending zip file" (.getName zip-file) "to muuntaja for processing")
  (try
    (let [{{:keys [status attachments error]} :body} (http/post (str (env/value :muuntaja :url) unzip-attachments-path)
                                                                {:as        :json
                                                                 :multipart [{:name      "file"
                                                                              :content   zip-file
                                                                              :mime-type "application/zip"}]})]
      (cond
        (and (= status "ok") (seq attachments))
        (do
          (timbre/info "Successfully extracted" (count attachments) "attachments from zip file")
          {:attachments attachments})

        (= status "ok")
        (do
          (timbre/info "No attachments found inside the zip file"))

        :else
        (do
          (timbre/warn "Muuntaja reported an error in the zip file:" error)
          {:error error})))
    (catch Exception ex
      (timbre/error ex)
      {:error "Tuntematon virhe"})))

(defn download-file [file-path]
  {:pre [(not (str/blank? file-path))]}
  (try
    (-> (http/get (str (env/value :muuntaja :url) file-path))
        :body)
    (catch Exception ex
      (timbre/error ex "File download from muuntaja failed for file" file-path))))
