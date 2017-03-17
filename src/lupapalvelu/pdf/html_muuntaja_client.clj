(ns lupapalvelu.pdf.html-muuntaja-client
  (:require [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [sade.core :refer [now ok fail]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss])
    (:import [java.io InputStream]))

(def html2pdf-path "/api/html2pdf")

(defn convert-html-to-pdf [target-name template-name html-content header-content footer-content]
  (timbre/info "Sending '" template-name "' template for " target-name " to muuntaja for processing")
  (try
    (let [request-opts {:as           :stream
                        :content-type :json
                        :encoding     "UTF-8"
                        :body         (json/encode {:html html-content
                                                    :footer footer-content
                                                    :header header-content})}
          resp         (http/post (str (env/value :muuntaja :url) html2pdf-path)
                                  request-opts)]

      (cond
        (and (= (:status resp) 200) (:body resp))
        (do
          (timbre/info "Successfully converted '" template-name "' template for " target-name)
          (ok :pdf-file-stream (:body resp)))
        :else

        (do
          (timbre/warn "Muuntaja reported an error when trying to convert '" template-name"' template for " target-name":" (:error resp))
          (fail (:error resp)))))

    (catch Exception ex
      (timbre/error ex)
      (fail :unknown))))
