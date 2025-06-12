(ns sade.security-headers
  (:require [lupapalvelu.logging :refer [with-logging-context]]
            [sade.session :refer [merge-to-session]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

(def csp-default-src-urls
  (cond-> ["https://*.lupapiste.fi"
           "https://storage.googleapis.com"
           "https://ld-webchat.leaddesk.com/"
           "wss://webchatconnector-ce2.leaddesk.com/"]
    (env/dev-mode?) (conj "ws://*:*"
                          "http://localhost:8080")))

(def content-security-policy-base
  (str "default-src 'self' " (ss/join " " csp-default-src-urls) ";"
       "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://ld-webchat.leaddesk.com/;"
       "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;"
       "img-src 'self' data: https://*.lupapiste.fi https://lupapiste.fi;"
       "font-src 'self' data: https://fonts.gstatic.com;"))

(def csp-with-frame-ancestors
  "Used by login pages, as they cannot include the form-action directive which would prevent login redirects"
  (str content-security-policy-base
       "frame-ancestors 'self';"))

(def default-csp
  (str csp-with-frame-ancestors
       "form-action 'self' "
       (env/value :visma-sign :base-url) ";" ))

(def csp-for-julkipano
  "Used by julkipano to allow iframe embedding in public displays"
  (str content-security-policy-base
       "form-action 'self';"))

(def cors-headers
  (when (env/dev-mode?)
    {"Access-Control-Allow-Origin" "*"
     ;; Content-Type needs to allowed for webhook.site to be able to redirect
     ;; requests to local environment
     "Access-Control-Allow-Headers" "Content-Type"}))

(defn bulletin-request?
  "Note that only request to `local-bulletins` are considered bulletin requests."
  [request]
  (boolean (or (some->> (get-in request [:headers "referer"])
                        (re-matches #"https://(?:julkipano|kuulutukset)(-\w+)*.lupapiste.fi/app/(?:fi|sv)/local-bulletins.*"))
               (re-matches #"^/app/(?:fi|sv)/local-bulletins.*"
                           (:uri request)))))

(defn add-security-headers
  "Ring middleware.
   Sets Content-Security-Policy and other security headers."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (when response
        (-> response
            (assoc-in [:headers "Referrer-Policy"] "no-referrer-when-downgrade")
            (assoc-in [:headers "X-Content-Type-Options"] "nosniff")
            ;; Content security policy - http://www.w3.org/TR/CSP/
            (update :headers (fn [hdrs]
                               (if-not (get hdrs "Content-Security-Policy")
                                 (assoc hdrs "Content-Security-Policy" (if (bulletin-request? request)
                                                                         csp-for-julkipano
                                                                         default-csp))
                                 ;; Do not override if handler wants to set a separate CSP
                                 hdrs)))
            (update :headers merge cors-headers))))))

(defn- copy-id [handler request]
  (let [session-id (or (get-in request [:session :id]) (str (random-uuid)))]
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
