(ns lupapalvelu.pdf.pdfa-conversion-client
  (:require [clj-http.client :as http]
            [lupapalvelu.mime :as mime]
            [sade.env :as env]))


(defn get-url []
  (env/value :libreoffice :url))

(def enabled? (not (clojure.string/blank? (env/value :libreoffice :url))))

(defn convert-to-pdfa [filename content]
  (http/post (get-url)
             {:debug     true
              :as :stream
              :multipart [{:name filename
                           :part-name "file"
                           :mime-type (mime/mime-type (mime/sanitize-filename filename))
                           :encoding  "UTF-8"
                           :content   content
                           }]}))

