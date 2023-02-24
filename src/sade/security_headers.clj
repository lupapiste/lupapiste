(ns sade.security-headers
  (:require [lupapalvelu.logging :refer [with-logging-context]]
            [lupapalvelu.web :as web]
            [sade.session :refer [merge-to-session]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env])
  (:import java.util.UUID))

(def content-security-policy-base
  (str "default-src 'self' https://*.lupapiste.fi https://widget-telwin.getjenny.com" (when (env/dev-mode?) (str " ws://*:*" " http://localhost:8080")) " https://storage.googleapis.com" "; "
       "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://widget-telwin.getjenny.com;"
       "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
       "img-src 'self' data: https://*.lupapiste.fi https://lupapiste.fi;"
       "font-src 'self' data: https://fonts.gstatic.com;"
       "report-uri /api/csp-report;"))

(def csp-with-frame-ancestors
  "Used by login pages, as they cannot include the form-action directive which would prevent login redirects"
  (str content-security-policy-base
       "frame-ancestors 'self';"))

(def default-csp
  (str csp-with-frame-ancestors
       "form-action 'self' "
       (env/value :onnistuu :post-to) ";" ))

(def csp-for-julkipano
  "Used by julkipano to allow iframe embedding in public displays"
  (str content-security-policy-base
       "form-action 'self';"))

(def cors-headers
  (when (env/dev-mode?)
    {"Access-Control-Allow-Origin" "*"}))

(defn add-security-headers
  "Ring middleware.
   Sets Content-Security-Policy headers."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (when response
        (-> response
            ;; Content security policy - http://www.w3.org/TR/CSP/
            (update :headers (fn [hdrs]
                               (if-not (get hdrs "Content-Security-Policy")
                                 (assoc hdrs "Content-Security-Policy" (if (web/bulletin-request? request)
                                                                         csp-for-julkipano
                                                                         default-csp))
                                 ;; Do not override if handler wants to set a separate CSP
                                 hdrs)))
            (update :headers merge cors-headers))))))

(defn- copy-id [handler request]
  (let [session-id (or (get-in request [:session :id]) (str (UUID/randomUUID)))]
    (with-logging-context {:session-id session-id}
      (when-let [response (handler (assoc-in request [:session :id] session-id))]
        (if-not (get-in request [:session :id])
          (merge-to-session request response {:id session-id})
          response)))))

(defn session-id-to-mdc
  "Ring middleware. Sets 'session-id' mdc-key with ring session id."
  [handler]
  (fn [request] (copy-id handler request)))

(defn sanitize-header-values
  "Ring middleware. Removes non-printable characters from request and response headers."
  [handler]
  (fn [request]
    (let [sanitized-request (update request :headers util/convert-values ss/strip-non-printables)
          response (handler sanitized-request)]
      (if (:headers response)
        (update response :headers util/convert-values ss/strip-non-printables)
        response))))
