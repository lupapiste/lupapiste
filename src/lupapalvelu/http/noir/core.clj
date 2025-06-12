(ns lupapalvelu.http.noir.core
  (:require [integrant.core :as ig]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.control-api :as control]
            [lupapalvelu.http.noir.schema :as noir-schema]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.perf-mon :as perf]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.visma-sign-api :as visma]
            [lupapalvelu.web :as web]
            [noir.content.defaults :as defaults]
            [noir.exception :as exception]
            [noir.options :as noir-options]
            [noir.server :as noir-server]
            [noir.server.handler :as noir-handler]
            [noir.session :as session]
            [noir.statuses :as statuses]
            [ring.middleware.session :as middleware.session]
            [rum.core :as rum]
            [sade.security-headers :as headers]
            [sade.util :as util]))

(defn- error-page-translation [lang category type]
  (i18n/localize lang (format "error-page.%s-%s" category type)))

(defn- error-page-half [lang category]
  [:center
   [:h2 (error-page-translation lang category "subtitle")]
   [:p
    (error-page-translation lang category "explanation")
    [:br]
    [:a {:href (str "/login/" (name lang))}
     (i18n/localize lang "error-page.return")]]])

(defn- error-page-layout [category]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:link {:id   "main-css"
            :href "/lp-static/css/main.css"
            :rel  "stylesheet"}]
    [:title (format "%s - %s - Lupapiste"
                    (error-page-translation :fi category "title")
                    (error-page-translation :sv category "title"))]]
   [:body
    [:div {:style {:width      "min(100%, 1200px)"
                   :margin     "auto"
                   :margin-top "100px"}}
     [:div.lupapiste-dark-logo]
     [:div {:style {:margin-top "100px"}}
      [:div {:style {:width "50%"
                     :float "left"}}
       (error-page-half :fi category)]
      [:div {:style {:margin-left "50%"}}
       (error-page-half :sv category)]]]]])

(def ^:private not-found-html (str "<!DOCTYPE html>" (rum/render-static-markup (error-page-layout "not-found"))))
(def ^:private error-page-html (str "<!DOCTYPE html>" (rum/render-static-markup (error-page-layout "error"))))

(defn- install-wrap-exceptions
  [dev-mode?]
  (let [old-handler exception/wrap-exceptions]
    (alter-var-root
      (var exception/wrap-exceptions)
      (constantly
        (fn [handler]
          (fn [request]
            (try
              (handler request)
              (catch Exception e
                (log/error e "Internal noir server error") ; replaced (.printStackTrace e) from noir default wrapper
                (let [content (if dev-mode?
                                (try
                                  (defaults/stack-trace (exception/parse-ex e))
                                  (catch Throwable _
                                    (statuses/get-page 500)))
                                error-page-html)]
                  {:status  500
                   :headers {"Content-Type" "text/html"}
                   :body    content})))))))
    old-handler))

(defn- uninstall-wrap-exceptions
  [old-handler]
  (alter-var-root (var exception/wrap-exceptions) (constantly old-handler)))

(defn- install-wrap-routes
  []
  (let [old-wrappers (deref noir-handler/wrappers)]
    (noir-handler/wrap-route :catch-all (-> {:status  404
                                             :headers {"Content-Type" "text/html"}
                                             :body    not-found-html}
                                            constantly
                                            constantly))
    old-wrappers))

(defn- uninstall-wrap-routes
  [old-wrappers]
  (reset! noir-handler/wrappers old-wrappers))

(defn- install-noir-session
  [components cookie-name]
  ;; Allow session cookie name to be changed
  (reset-meta! #'session/noir-session {})
  (let [old-handler    session/wrap-noir-session
        with-system-mw (components/with-system-components-mw components)]
    (alter-var-root
      (var session/wrap-noir-session)
      (constantly
        (fn [handler & [opts]]
          (-> handler
              ;; inner middleware first, outer last
              (#'session/noir-session)
              (middleware.session/wrap-session (util/assoc-when opts
                                                                :cookie-name cookie-name))
              with-system-mw))))
    old-handler))

(defn- uninstall-noir-session
  [old-handler]
  (alter-var-root (var session/wrap-noir-session) (constantly old-handler)))

(defn- install-middleware
  [dev-mode? components]
  (let [old-mw (deref noir-handler/middleware)
        mw     (->> [;; Middlewares in the execution order, iow. the outermost first
                     perf/perf-mon-middleware
                     (when dev-mode?
                       perf/throttle-middleware)
                     web/bulletin-redirects
                     web/cookie-monster
                     control/lockdown-middleware
                     headers/sanitize-header-values
                     (when dev-mode?
                       visma/wrap-body-for-visma-sign-dev-dummy)
                     autologin/catch-autologin-failure
                     web/session-timeout
                     web/wrap-authentication
                     web/anti-csrf
                     headers/session-id-to-mdc
                     web/remove-_-param
                     web/parse-json-body-middleware
                     i18n/lang-middleware
                     web/tempfile-cleanup]
                    (keep identity)
                    (apply comp))]
    (noir-server/add-middleware mw)
    old-mw))

(defn- uninstall-middleware
  [old-mw]
  (reset! noir-handler/middleware old-mw))

(defmethod ig/assert-key ::handler [_ {:keys [noir-opts]}]
  (noir-schema/valid-noir-opts! (dissoc noir-opts :session-store)))

(defmethod ig/init-key ::handler [k {:keys [cookie-name components dev-mode? noir-opts]}]
  (log/infof "Starting %s..." k)
  ;; The various Noir functions store state in atoms. These atoms are read  when the handler
  ;; is constructed. However, once the handler is constructed, the atoms don't seem to matter anymore.
  ;; To support reloading (and starting concurrent systems), we:
  ;;   1. Call Noir functions to alter atoms and/or alter Noir vars
  ;;   2. Construct the handler, which reads the atoms and applies the modified vars
  ;;   3. Reset the Noir atoms and restore the vars
  ;; NOTE: This isn't thread-safe; if we someday want to build handlers concurrently, we ought
  ;; to use e.g. a mutex to make sure only a single thread at a time is altering the vars. But
  ;; maybe we will replace noir before that happens and all this code can be removed.
  (let [old-exceptions (install-wrap-exceptions dev-mode?)
        old-wrappers   (install-wrap-routes)
        old-session    (install-noir-session components cookie-name)
        old-mw         (install-middleware dev-mode? components)
        ;; NOTE: Noir has a bug that when it creates the handler, wrap-spec-routes -> spec-routes
        ;; part doesn't see the options in the dynamic binding correctly and it uses options
        ;; "during the route creation time" where the middleware hasn't set the dynamic var yet.
        ;; This means the :resource-options doesn't even work. Set the dynamic var even during
        ;; handler creation time, just for resource-options.
        handler        (binding [noir-options/*options* noir-opts]
                         (noir-server/gen-handler noir-opts))]
    (uninstall-middleware old-mw)
    (uninstall-noir-session old-session)
    (uninstall-wrap-routes old-wrappers)
    (uninstall-wrap-exceptions old-exceptions)
    handler))
