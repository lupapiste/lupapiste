(ns lupapalvelu.attachment.onkalo-client
  (:require [sade.http :as http]
            [taoensso.timbre :as timbre]
            [ring.util.codec :as codec]
            [sade.env :as env]))

(def arkisto-host (env/value :arkisto :host))
(def app-id (env/value :arkisto :app-id))
(def app-key (env/value :arkisto :app-key))

(defn get-file
  "Gets file or preview image of the file from Onkalo by organization id and file id.
   Returns an InputStream or nil if the request fails."
  ([org id]
    (get-file org id false))
  ([org id preview?]
   (try
     (let [url (str arkisto-host "/documents/" (codec/url-encode id) (when preview? "/preview"))
           options {:basic-auth [app-id app-key]
                    :query-params {:organization org}
                    :as :stream}
           {:keys [headers body]} (http/get url options)]
       {:content #(identity body)  ;; Return a function to be compatible with mongo api
        :contentType (get headers "content-type")})
     (catch Throwable t
       (timbre/error t "Could not download document" id "from onkalo.")))))
