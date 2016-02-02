(ns lupapalvelu.pdf.libreoffice-conversion-client
  (:require [clj-http.client :as http]
            [lupapalvelu.mime :as mime]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.env :as env]))


(def- url (str "http://" (env/value :libreoffice :host) ":" (or (env/value :libreoffice :port) 8001)))

(def enabled? (env/feature? :libreoffice))

(defn convert-to-pdfa [filename content]
  (http/post url
             {:debug     true
              :as :stream
              :multipart [{:name filename
                           :part-name "file"
                           :mime-type (mime/mime-type (mime/sanitize-filename filename))
                           :encoding  "UTF-8"
                           :content   content
                           }]}))

