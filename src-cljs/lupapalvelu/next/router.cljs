(ns
  ;; Reload always to also trigger React remount on lazy-loaded ns changes
  ^:dev/always
  lupapalvelu.next.router
  (:require [goog.object :as g]
            [lupapalvelu.next.ajax :as ajax]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.next.legacy-dom :as legacy]
            [lupapalvelu.next.modern :as modern]
            [lupapalvelu.next.router-api :as router-api]
            [lupapalvelu.next.session :as sess]
            [lupapalvelu.next.util :as nutil]
            [lupapalvelu.ui.common :refer [feature?]]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.media-query :as media-query]
            [lupapalvelu.ui.misc]
            [re-frame.core :as re-frame]
            [re-frame.db :as rf-db]
            [reitit.coercion.schema :as rsc]
            [reitit.frontend :as rf]
            [reitit.frontend.controllers :as rfc]
            [reitit.frontend.easy :as rfe]
            [reitit.spec :as rs]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.validators :as v]
            [schema.core :as s]
            [shadow.lazy :as shadow-lazy]))

(re-frame/reg-event-db
  ::init-router
  (fn [db [_ router]]
    (assoc db ::router router)))

(def ^:dynamic *init-fns* nil)

(defn run-controllers
  [db new-match]
  (let [old-controllers (-> db :route/match :controllers)
        db              (assoc db :route/match new-match)]
    ;; Reitit ignores the return value of controllers. We use a dynamic atom to collect
    ;; the results (a seq of functions and args to run) from within the controllers.
    (binding [*init-fns* (atom [])]
      (let [new-controllers (rfc/apply-controllers old-controllers new-match)
            db              (assoc-in db [:route/match :controllers] new-controllers)]
        ;; Invoke the returned functions (if any) and produce the final effect map
        (nutil/merge-every-fn {:db db} (deref *init-fns*))))))

(re-frame/reg-event-fx
  :route/on-navigate
  (fn [{:keys [db]} [_ new-match router]]
    (if new-match
      (run-controllers db new-match)
      (let [start-page (keyword js/lupapisteApp.startPage)]
        (if (rf/match-by-name (::router db) start-page)
          (if (= :hashbang start-page)
            ;; hashbang page is special delivery from backend - it is returned for unauthorized accesses
            ;; in order to provide login or registration paths for the user
            ;; hashbang.js contains logic that generates links with redirect-after-login query params
            ;; by doing nothing here we support that legacy logic to work, as it relies on reading window.location.hash
            nil #_(js/console.log "It's our friend hashbang.js from backend! Do nothing...")
            (do
              (js/console.warn "No Reitit route match for path" @(:last-fragment router) ", redirecting to start page" router)
              {:route/replace start-page}))
          (js/console.error "Missing route for startPage:" start-page "- no operation"))))))

(defn- push-reitit-fx [value]
  (let [{:keys [route params query]} (if (keyword? value)
                                       {:route value}
                                       value)]
    (rfe/push-state route params query)))

(defn- replace-reitit-fx [value]
  (let [{:keys [route params query]} (if (keyword? value)
                                       {:route value}
                                       value)]
    (rfe/replace-state route params query)))

(re-frame/reg-fx :route/push push-reitit-fx)
(re-frame/reg-fx :route/replace replace-reitit-fx)
(re-frame/reg-event-fx :route/push (fn [_ [_ arg]] {:route/push arg}))
(re-frame/reg-event-fx :route/replace (fn [_ [_ arg]] {:route/replace arg}))

;;; Legacy controller

;; Navigation between multiple <section>s

(defn match->page-event [match]
  (let [path               (:path match)
        ;; rest strips exclamation mark ! from the path parts
        [page & page-path] (rest (ss/split path #"/"))]
    {:pageId         page
     :pagePath       page-path
     :currentHash    path
     :scroll-to-top? (get-in match [:data :parameters :scroll-to-top?] true)
     :toggle-event   (get-in match [:data :toggle-event])}))

(defn apply-legacy-page-change [{:keys [pageId scroll-to-top? toggle-event] :as page-event} visible?]
  (let [elem (js/document.getElementById pageId)]
    (if elem
      (if visible?
        (.add (.-classList elem) "visible")
        (.remove (.-classList elem) "visible"))
      ;; No element found, perhaps we are in hashbang view
      ;; because we retain the URL and hashbang/index.html provides
      ;; the user possibility to login with redirection to the requested but
      ;; unauthorized page
      (when-not (= js/lupapisteApp.startPage "hashbang")
        (throw (js/Error pageId))))
    (if visible?
      (js/hub.send "page-load" (clj->js page-event))
      (js/hub.send "page-unload" (clj->js page-event)))
    (when (and elem toggle-event)
      (if (keyword? toggle-event)
        (re-frame/dispatch [toggle-event visible?])
        (run! #(re-frame/dispatch [% visible?]) toggle-event)))
    (when scroll-to-top?
      (js/window.scrollTo 0 0))))

(defn toggle-legacy [visible? match]
  (let [page-event  (match->page-event match)
        toggle-page (fn []
                      (try
                        (apply-legacy-page-change page-event visible?)
                        (catch js/Error e
                          (let [start-page js/lupapisteApp.startPage]
                            (js/console.warn "Bad page-id:" (.-message e)
                                             "- Redirecting to start page"
                                             start-page)
                            (>evt [:route/replace (keyword start-page)])))))
        user        (sess/user (deref rf-db/app-db))]
    (cond
      (or js/lupapisteApp.allowAnonymous
          user)
      (toggle-page)

      (not js/lupapisteApp.allowAnonymous)
      (do
        (js/pageutil.showAjaxWaitNow)
        (ajax/query "user"
                    {:success-fn  (partial sess/user-success toggle-page)
                     :error-fn    sess/error-response
                     :complete-fn js/pageutil.hideAjaxWait}))

      :else
      (do
        (js/console.error "Unauthorized Reitit route, redirecting to login")
        (>evt [:route/replace :login])))))

(def bulletins-controller
  {:start    #(js/hub.send "page-load" (clj->js (match->page-event %)))
   :stop     #(js/hub.send "page-unload" (clj->js (match->page-event %)))
   :identity identity})

(def modern-pages
  "List page IDs that are statically defined to router.
  To avoid route conflicts with dynamically parsed pages from DOM."
  #{"application" "attachment" "bulletins" "company" "company-reports"
    "task" "neighbors" "neighbor" "statement" "printing-order"
    "verdict" "verdict-attachments-select" "send-attachments"
    "biller" "stamping" "structure" "structures"
    ;; admin pages
    "organization" "users" "edit-authority" "companies" "ad-login-settings"
    "import-export" "reports" "single-sign-on-keys" "elasticsearch"
    "asiointi-api"
    ;; auth admin
    "auth-admin-inspection-summary" "operations" "application-settings"
    "auth-admin-imports" "auth-admin-import-config"})

(def legacy-paths
  {"inforequest" "/:app-id"})

(def default-legacy-controller
  {:start    (partial toggle-legacy true)
   :stop     (fn [match]
               (js/hub.send "scrollService::push"
                            (clj->js {:hash     (str "#" (:path match))
                                      :followed true
                                      :override true}))
               (toggle-legacy false match))
   :identity identity})

;;; Root controller

;; Navigation within a single <section> + <cljs-*> component

(defn- verify-authenticated
  "Make sure user is authenticated before running `init-fn` for the route"
  [init-fn+args {:keys [db] :as cofx} []]
  (if-not (sess/user db)
    {:fx [[:ajaxwait/show true]
          [:action/query {:name    :user
                          :success (fn [result]
                                     (sess/user-success
                                       #(re-frame/dispatch [::authenticated.success init-fn+args])
                                       result))
                          :error   (fn [result]
                                     (sess/error-response result)
                                     (re-frame/dispatch [::authenticated.done]))}]]}
    (nutil/merge-every-fn cofx [init-fn+args])))

(defn- clear-ajax-wait
  [_ _]
  {:fx [[:ajaxwait/show false]]})

(re-frame/reg-event-fx ::authenticated.success
  (fn [cofx [_ init-fn+args]]
    (nutil/merge-every-fn cofx [[clear-ajax-wait]
                                init-fn+args])))

(re-frame/reg-event-fx ::authenticated.done
  clear-ajax-wait)

(def root-controller
  {:identity identity
   :start    (fn [{{::keys [section anonymous?] :as data} :data}]
               (when-not (and section (some? anonymous?))
                 (throw (ex-info "Missing parameters" (select-keys data [::section
                                                                         ::anonymous?]))))
               ;; Section documents where the view is mounted
               (when-not (js/document.getElementById section)
                 (throw (ex-info "No element found" {:section section})))
               (when (nil? *init-fns*)
                 (throw (ex-info "*init-fns* not bound" {})))
               (let [init-fn (router-api/route-init-fn (:name data))]
                 ;; Indirectly return the init function(s) to the navigation event handler
                 (if-not anonymous?
                   ;; Check user is authenticated before activating view
                   (swap! *init-fns* conj [(partial verify-authenticated [init-fn true])])
                   ;; Activate view immediately
                   (swap! *init-fns* conj [init-fn true]))))
   :stop     (fn [{:keys [data]}]
               (when (nil? *init-fns*)
                 (throw (ex-info "*init-fns* not bound" {})))
               (swap! *init-fns* conj [(router-api/route-init-fn (:name data)) false]))})

;;; Routes

(defn legacy-routes []
  (for [page-id (legacy/page-sections)
        :when   (not (contains? modern-pages page-id))]
    [(str "!/" page-id (get legacy-paths page-id))
     ["" {:controllers [default-legacy-controller]
          :name        (keyword page-id)
          :page        page-id}]
     ["/*" {:controllers [{:identity identity
                           :start    #(js/console.warn "Undefined Reitit sub-path for legacy page" (:path %1))}
                          default-legacy-controller]}]]))

(def admin-routes
  [["!/organization/:org-id"
    {:controllers  [default-legacy-controller]
     :page         "admin:organization"
     :parameters   {:path {:org-id s/Str}}
     :toggle-event :lupapalvelu.ui.admin.organization-settings.common/init}]
   ["!/users"
    {:controllers [default-legacy-controller]
     :name        :users
     :page        "admin:users"}]
   ["!/edit-authority/:user-id"
    {:controllers [default-legacy-controller]
     :page        "admin:edit-authority"
     :parameters  {:path {:user-id s/Str}}}]
   ["!/companies"
    {:controllers  [default-legacy-controller]
     :name         :companies
     :page         "admin:companies"
     :toggle-event :company.subscribers/set-visible}]
   ["!/ad-login-settings"
    {:controllers  [default-legacy-controller]
     :name         :ad-login-settings
     :page         "admin:ad-login-settings"
     :toggle-event :ad-login-settings/init}]
   ["!/asiointi-api"
    {:controllers  [default-legacy-controller]
     :name         :asiointi-api
     :page         "admin:asiointi-api"
     :toggle-event :lupapalvelu.ui.admin.asiointi-api.settings/init}]
   ["!/import-export"
    {:controllers  [default-legacy-controller]
     :name         :import-export
     :toggle-event :lupapalvelu.ui.admin.import-export.subs/init}]
   ["!/reports"
    {:controllers  [default-legacy-controller]
     :name         :reports
     :toggle-event :lupapalvelu.ui.admin.reports.subs/init}]
   ["!/single-sign-on-keys"
    {:controllers  [default-legacy-controller]
     :name         :single-sign-on-keys
     :toggle-event :lupapalvelu.ui.admin.sso-keys.subs/init}]
   ["!/elasticsearch"
    {:controllers  [default-legacy-controller]
     :name         :elasticsearch
     :toggle-event :lupapalvelu.ui.admin.elasticsearch.core/init}]])

(def ^:private auth-admin-routes
  [["!/auth-admin-inspection-summary"
    {:controllers  [default-legacy-controller]
     :page         "auth-admin:inspection-summary"
     :parameters   {}
     :toggle-event :lupapalvelu.ui.auth-admin.inspection-summary.subs/toggle-view}]
   ["!/operations"
    {:controllers  [default-legacy-controller]
     :page         "auth-admin:operations"
     :parameters   {}
     :toggle-event :lupapalvelu.ui.auth-admin.selected-operations.view/toggle-view}]
   ["!/auth-admin-imports"
    {:controllers  [default-legacy-controller]
     :page         "auth-admin:imports"
     :parameters   {}
     :toggle-event :lupapalvelu.ui.auth-admin.imports.data.view/toggle-view}]
   ["!/auth-admin-import-config/:cfg-id"
    {:controllers  [default-legacy-controller]
     :page         "auth-admin:import-config"
     :parameters   {:path {:cfg-id sssc/ObjectIdStr}}
     :toggle-event :lupapalvelu.ui.auth-admin.imports.data.config/toggle-view}]
   ["!/application-settings"
    {:controllers  [default-legacy-controller]
     :page         "auth-admin:application-submit-assurances"
     :parameters   {}
     :toggle-event :lupapalvelu.ui.auth-admin.application-submit-assurances.editor/toggle-view}]])

(def application-controller
  "Controller for routes for a single application.
  Routes are expected to have a `:app-id` path parameter.

  Due to legacy reasons, the application view consists of disjointed
  URLs (`#!/application/:app-id/*`, `#!/task/:app-id/*`, `#!/verdict/:app-id/*`..).
  However, these all still semantically belong to the same app. The good news
  is that `reitit` allows us to apply the same controller to multiple routes
  so that it does _not_ call the stop/start methods unnecessarily between the routes."
  {:start      (fn [_] (>evt [:lupapalvelu.ui.docgen.state/init true]))
   :stop       (fn [_] (>evt [:lupapalvelu.ui.docgen.state/init false]))
   ;; The default legacy controller uses `:identity`:
   ;; - every route is considered unique based on the match
   ;; - even the parent controller is executed when switching between sibling
   ;;   routes!
   ;; Instead, use `:parameters` here to specify that this controller should
   ;; only start/stop if the `:app-id` changes.
   :parameters {:path [:app-id]}})

(def feature-controller
  "Check that all features required by the view are given.

  If not, redirect user to the app start page."
  {:start    (fn [required-features]
               (when (seq required-features)
                 (let [ok? (every? feature? required-features)]
                   (when-not ok?
                     (>evt [:route/replace (keyword js/lupapisteApp.startPage)])))))
   :identity (fn [match]
               (:required-features (:data match)))})

(def routes
  (rf/router
    (concat
      [["/" {:controllers [{:start #(>evt [:route/replace (keyword js/lupapisteApp.startPage)])}]}]]
      (legacy-routes)
      admin-routes
      auth-admin-routes
      [["!/dashboard"
        #_{:clj-kondo/ignore [:unresolved-namespace]}
        {:page              "dashboard"
         :required-features #{:dashboard-view}
         :loadable          (shadow-lazy/loadable lupapalvelu.ui.dashboard.view/view)}
        [""
         {:name :dashboard}]
        ["/search"
         {:name :dashboard-search}]]]
      [["!/application/:app-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "application"
         :parameters  {:path           {:app-id v/application-id-pattern}
                       :scroll-to-top? false}}
        [""] ; aka '/info'
        ["/info"
         {:name         :info
          :parameters   {:query focus/focus-query-params}
          :toggle-event :lupapalvelu.ui.application.info/toggle-view}]
        ["/parties"
         {:name         :parties
          :parameters   {:query focus/focus-query-params}
          :toggle-event :lupapalvelu.ui.application.parties/toggle-view}]
        ["/structures"
         {:toggle-event :lupapalvelu.ui.location.locations-operations/toggle-view}]
        ["/process"
         [""]
         ["/createReply/:issue-type/:id"
          {:controllers [{:start #(>evt [:lupapalvelu.ui.process.replies/create-new-reply])}]
           :parameters  {:path {:issue-type s/Str
                                :id         s/Str}}}]
         ["/createReply"]]
        ["/states"]
        ["/attachments"]
        ["/requiredFieldSummary" {:toggle-event :lupapalvelu.ui.assurances.data/toggle-view}]
        ["/statement"]
        ["/verdict"]
        ["/ymp-bulletin"]
        ["/bulletin"]
        ["/tasks"]
        ["/inspectionSummaries"]
        ["/buildingProductCatalog"]
        ["/caseFile"]
        ["/archival"]
        ["/filebank"]
        ["/applicationSummary" {:toggle-event :lupapalvelu.ui.assurances.data/toggle-view}]
        ["/invoice"]
        ["/conversion"
         {:toggle-event :lupapalvelu.ui.conversion.data/toggle-view}]
        ["/imports"
         {:toggle-event :lupapalvelu.ui.imports.data.view/toggle-view}]
        ["/entries"
         {:toggle-event :lupapalvelu.ui.statistics.data.entries/toggle-view}]
        ["/notices"
         {:toggle-event :lupapalvelu.ui.notices.data/toggle-view}]
        ["/conversation" ; emails about new comment contain this suffix, which should pop up conversation panel
         {:controllers [{:start #(>evt [::legacy/open-side-panel "conversation"])}]
          :page        "application-conversation"
          :name        :conversation-panel}]
        ["/notice" ; pops up notice panel
         {:controllers [{:start #(>evt [::legacy/open-side-panel "notice"])}]
          :page        "application-notices"
          :name        :notice-panel}]]
       ["!/attachment/:app-id/:att-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "attachment"
         :name        :attachment
         :parameters  {:path {:att-id s/Str ; could be ObjectIdStr or composite of LP_userid_objectid
                              :app-id v/application-id-pattern}}}]
       ["!/stamping/:app-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/structure/:app-id/:file-id"
        {:controllers  [default-legacy-controller
                        application-controller]
         :page         "structure"
         :name         :structure
         :toggle-event [:lupapalvelu.ui.location.model-subs/init-component
                        :lupapalvelu.ui.location.model-solibri-result-subs/init-component]
         :parameters   {:path {:file-id sssc/FileId
                               :app-id  v/application-id-pattern}}}]
       ["!/structures/:app-id/:ifc-version-ids-csv"
        {:controllers  [default-legacy-controller
                        application-controller]
         :name         :structures
         :toggle-event [:lupapalvelu.ui.location.bim-viewer.viewer-subs/toggle-fullscreen]
         :parameters   {:path {:ifc-version-ids-csv s/Str ; CSV value, decoded on page
                               :app-id              v/application-id-pattern}}}]
       ["!/verdict/:app-id/:verdict-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "verdict"
         :name        :verdict
         ;; we could handle verdict page purely in CLJS, just render pate-verdict component here
         :parameters  {:path {:verdict-id sssc/ObjectIdStr
                              :app-id     v/application-id-pattern}}}]
       ["!/verdict/:app-id/:foreman-id/:verdict-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "verdict"
         :name        :foreman-verdict
         ;; we could handle verdict page purely in CLJS, just render pate-verdict component here
         :parameters  {:path {:verdict-id sssc/ObjectIdStr
                              :foreman-id sssc/ObjectIdStr
                              :app-id     v/application-id-pattern}}}]
       ["!/neighbors/:app-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "neighbors"
         :name        :neighbors
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/neighbor"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "neighbor"
         :name        :neighbor}
        [""]
        ["/:app-id/:neighbor-id/:token"
         [""
          {:name       :neighbor-show
           :parameters {:path {:app-id      v/application-id-pattern
                               :neighbor-id s/Str
                               :token       s/Str}}}]
         ["/:idp-status"
          {:name       :neighbor-show-status
           :parameters {:path {:app-id      v/application-id-pattern
                               :neighbor-id s/Str
                               :token       s/Str
                               :idp-status  (s/enum "cancel" "error" "y" "vtj")}}}]]]
       ["!/statement/:app-id/:statement-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "statement"
         :parameters  {:path {:statement-id sssc/ObjectIdStr
                              :app-id       v/application-id-pattern}}}
        ["/statement"]
        ["/reply-request"]
        ["/reply"]]
       ["!/task/:app-id/:task-id"
        {:controllers  [default-legacy-controller
                        application-controller]
         :toggle-event :lupapalvelu.ui.task.task-view/init
         :page         "task"
         :name         :task
         :parameters   {:path {:task-id sssc/ObjectIdStr
                               :app-id  v/application-id-pattern}}}]
       ["!/printing-order/:app-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "printing-order"
         :name        :printing-order
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/verdict-attachments-select/:app-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "verdict-attachments-select"
         :name        :verdict-attachments-select
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/send-attachments/:app-id"
        {:controllers [default-legacy-controller
                       application-controller]
         :page        "send-attachments"
         :name        :send-attachments
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/company/:company-id"
        {:controllers [default-legacy-controller]
         :page        "company"
         :parameters  {:path {:company-id s/Str}}}
        [""]
        ["/info"]
        ["/users"]
        ["/tags"]]
       ["!/company-reports/:company-id"
        {:controllers [default-legacy-controller]
         :page        "company"
         :parameters  {:path {:company-id s/Str}}}
        [""]]
       ["!/bulletins" {:controllers [bulletins-controller] :name :bulletins}]
       ["!/bulletin/:app-id"
        {:controllers [bulletins-controller]
         :parameters  {:path {:app-id s/Str}}}
        [""]
        ["/instructions"]
        ["/verdicts"]]
       ["!/ymp-bulletin/:app-id"
        {:controllers [bulletins-controller
                       application-controller]
         :parameters  {:path {:app-id s/Str}}}
        [""]
        ["/info"]
        ["/attachments"]
        ["/instructions"]
        ["/verdicts"]]
       ["!/biller"
        {:controllers [default-legacy-controller]
         :page        "biller"
         :name        :biller}]
       [""
        {::section    "suomifi-login-root"
         ::anonymous? true}
        ["!/suomifi-login-success"
         {:name        :suomifi-success
          :controllers [root-controller]
          :parameters  {:query {(s/optional-key :reset-pw) s/Bool}}}]
        ["!/suomifi-login-error"
         {:name        :suomifi-error
          :controllers [root-controller]}]
        ["!/suomifi-login-registration"
         {:name        :suomifi-registration
          :controllers [root-controller]}]
        ["!/suomifi-login-change-password/:user-id"
         {:name        :suomifi-change-password
          :controllers [root-controller]
          :parameters  {:path {:user-id sssc/ObjectIdStr}}}]
        ["!/suomifi-login-change-email/:user-id"
         {:name        :suomifi-change-email
          :controllers [root-controller]
          :parameters  {:path {:user-id sssc/ObjectIdStr}}}]]])
    {:data     {:controllers [{:identity identity
                               ;; copy from app.js: "Reset title. Pages can override title
                               ;; when they handle page-load event."  One beautiful day
                               ;; this could be: #(>evt [::dom/set-title
                               ;; js/lupapisteApp.defaultTitle]), but as rf events are
                               ;; async, it seems that legacy models who catch
                               ;; hub.onPageLoad events are faster in setting the title in
                               ;; place.
                               ;; -> this resulted in defaultTitle being set here AFTER
                               ;;    those page-load events which obviously is not what we
                               ;;    want, thus for now we set title syncrhonously here
                               :start    #(g/set js/document "title" js/lupapisteApp.defaultTitle)}
                              feature-controller]
                :coercion    rsc/coercion}
     :validate rs/validate}))

(defn init []
  (js/console.log "Starting Reitit frontend router")
  (modern/init-new-root-view!)
  (>evt [::init-router routes])
  (media-query/init)
  (rfe/start!
    routes
    #(>evt [:route/on-navigate %1 %2])
    {:use-fragment true}))

;; legacy router was also initialized to hashchange events after DOM was loaded
;; https://github.com/cloudpermit/lupapiste/blob/7b2201c3e3b158bf117c5db77ef16fde000d0210/resources/private/common/app.js#L322-L330
;; shadow-cljs has :init-fn functionality, but it is triggered right when the JS is
;; loaded..  In Lupapiste architecture case we need to defer the initialization until DOM
;; is ready so knockout models are bind etc
(.ready (js/$ js/document) init)
