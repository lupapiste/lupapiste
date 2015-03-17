(ns sade.security-headers
  (:require [lupapalvelu.logging :refer [with-logging-context]]
            [sade.session :refer [merge-to-session]])
  (:import java.util.UUID))

(defn add-security-headers
  "Ring middleware.
   Sets X-XSS-Protection and X-Frame-Options headers.
   Ported from Debian Unstable apache2.2-common /etc/apache2/conf.d/security,
   see also http://en.wikipedia.org/wiki/List_of_HTTP_header_fields#Common_non-standard_response_headers"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (when response
        (-> response
          ; Some browsers have a built-in XSS filter that will detect some cross site
          ; scripting attacks. By default, these browsers modify the suspicious part of
          ; the page and display the result. This behavior can create various problems
          ; including new security issues. This header will tell the XSS filter to
          ; completely block access to the page instead.
          (assoc-in [:headers "X-XSS-Protection"] "1; mode=block")

          ; Prevents other sites from embedding pages from this
          ; site as frames. This defends against clickjacking attacks.
          (assoc-in [:headers "X-Frame-Options"] "sameorigin"))))))

(defn- copy-id [handler request]
  (let [session-id (get-in request [:session :id] (str (UUID/randomUUID)))]
    (with-logging-context {:session-id session-id}
      (when-let [response (handler (assoc-in request [:session :id] session-id))]
        (if-not (get-in request [:session :id])
          (merge-to-session request response {:id session-id})
          response)))))

(defn session-id-to-mdc
  "Ring middleware. Sets 'session-id' mdc-key with ring session id."
  [handler]
  (fn [request] (copy-id handler request)))
