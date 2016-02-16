(ns lupapalvelu.pdf.libreoffice-conversion-client
  (:require [clj-http.client :as http]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.mime :as mime]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.env :as env])
  (:import (org.apache.commons.io FilenameUtils)))


(def- url (str "http://" (env/value :libreoffice :host) ":" (or (env/value :libreoffice :port) 8001)))

(def enabled? (and (env/feature? :libreoffice) (env/value :libreoffice :host)))

(defn- convert-to-pdfa-request [filename content]
  (http/post url
             {:as        :stream
              :multipart [{:name      filename
                           :part-name "file"
                           :mime-type (mime/mime-type (mime/sanitize-filename filename))
                           :encoding  "UTF-8"
                           :content   content
                           }]}))

(defn convert-to-pdfa [filename content]
  (try
    (let [response (convert-to-pdfa-request filename content)]
      {:filename (str (FilenameUtils/removeExtension filename) ".pdf")
       :content  (:body response)})

    (catch Exception e
      (error "convert-to-pdfa-request conversion error: " (.getMessage e))
      {:filename           filename
       :content            content
       :archivabilityError :libre-conversion-error})))
