(ns lupapalvelu.http.reitit.core
  (:require [integrant.core :as ig]
            [lupapalvelu.asiointi.api :as asiointi-api]
            [lupapalvelu.http.reitit.schema :as reitit-schema]
            [lupapalvelu.logging :as log]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.web :as web]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as reitit-coercion-malli]
            [reitit.ring :as reitit-ring]
            [reitit.ring.coercion :as reitit-ring-coercion]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.spec :as rrs]
            [ring.middleware.cookies :as ring-cookies]
            [ring.middleware.session :as middleware.session]
            [sade.env :as env]))

(defn- routes
  []
  ["" {}
   (concat
     (when (env/feature? :reitit-api)
       web/api-routes)
     (when (env/feature? :asiointi-api)
       asiointi-api/routes))])

(defn- create-session-wrapper
  "Return a session middleware that uses the in-memory session store."
  [session-store cookie-name]
  (fn [handler]
    (middleware.session/wrap-session handler {:store       session-store
                                              :cookie-name cookie-name})))

(defn make-router
  [{:keys [default-handler components session-store cookie-name]}]
  (reitit-ring/ring-handler
    (reitit-ring/router
      (routes)
      {:validate rrs/validate
       :data     {:muuntaja   m/instance
                  :coercion   (reitit-coercion-malli/create (-> reitit-coercion-malli/default-options
                                                                ;; Don't silently discard unknown keys,
                                                                ;; instead raise an error
                                                                (assoc :strip-extra-keys false)))
                  :middleware [(components/with-system-components-mw components)
                               (create-session-wrapper session-store cookie-name)
                               parameters/parameters-middleware
                               muuntaja/format-negotiate-middleware
                               muuntaja/format-request-middleware
                               muuntaja/format-response-middleware
                               reitit-ring-coercion/coerce-exceptions-middleware
                               reitit-ring-coercion/coerce-request-middleware
                               reitit-ring-coercion/coerce-response-middleware
                               multipart/multipart-middleware
                               ring-cookies/wrap-cookies]}})
    default-handler))

(defn- gen-reitit-handler
  [{:keys [refresh?] :as options}]
  (if refresh?
    ;; Build a fresh router for each request:
    ;; + Fast feedback when developing in the REPL
    ;; - Malformed routing tree not detected until a request occurs
    (fn [req]
      (let [router (make-router options)]
        (router req)))
    (make-router options)))

(defmethod ig/assert-key ::routes [_ options]
  (reitit-schema/valid-routes-opts! options))

(defmethod ig/init-key ::routes [k options]
  (log/infof "Starting %s..." k)
  (gen-reitit-handler options))
