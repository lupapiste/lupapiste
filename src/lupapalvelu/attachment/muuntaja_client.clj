(ns lupapalvelu.attachment.muuntaja-client
  (:require [sade.http :as http]
            [sade.env :as env]
            [taoensso.timbre :as timbre]
            [sade.strings :as str]
            [lupapalvelu.json :as json])
  (:import [java.io File]))

(def unzip-attachments-path "/api/unzip-attachments")
(def alive-path "/api/alive")

(defn alive-status []
  (-> (http/get (str (env/value :muuntaja :url) alive-path) {:throw-exceptions false})
      (select-keys [:status :request-time])))

(def column-keyword-map
  {"tiedosto"          :filename
   "tiedostonimi"      :filename
   "tyyppi"            :localizedType
   "sis\u00e4lt\u00f6" :contents
   "piirrosnumero"     :drawingNumber
   "liittyy"           :operation
   "fil"               :filename
   "filnamn"           :filename
   "typ"               :localizedType
   "inneh\u00e5ll"     :contents
   "ritningsnummer"    :drawingNumber
   "ansluter sig"      :operation})

(defn unzip-attachment-collection [^File zip-file]
  (timbre/info "Sending zip file" (.getName zip-file) "to muuntaja for processing")
  (try
    (let [request-opts                               {:as        :json
                                                      :multipart [{:name      "file"
                                                                   :content   zip-file
                                                                   :mime-type "application/zip"}
                                                                  {:name      "columns"
                                                                   :mime-type "application/json"
                                                                   :encoding  "UTF-8"
                                                                   :content   (json/encode column-keyword-map)}]}
          {{:keys [status attachments error]} :body} (http/post (str (env/value :muuntaja :url) unzip-attachments-path)
                                                                request-opts)]
      (timbre/debug attachments)
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
          (timbre/warn "Muuntaja failed to process the zip file:" error)
          {:error error})))
    (catch Exception ex
      (timbre/error ex)
      {:error "Tuntematon virhe"})))

(defn download-file [file-path]
  {:pre [(not (str/blank? file-path))]}
  (try
    (-> (http/get (str (env/value :muuntaja :url) file-path)
                  {:as :stream})
        :body)
    (catch Exception ex
      (timbre/error ex "File download from muuntaja failed for file" file-path))))
