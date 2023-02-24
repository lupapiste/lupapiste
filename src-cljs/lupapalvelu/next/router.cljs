(ns lupapalvelu.next.router
  (:require [clojure.string :as str]
            [goog.object :as g]
            [lupapalvelu.next.ajax :as ajax]
            [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.next.legacy-dom :as legacy]
            [lupapalvelu.next.session :as sess]
            [re-frame.core :as re-frame]
            [reitit.coercion.schema :as rsc]
            [reitit.frontend :as rf]
            [reitit.frontend.controllers :as rfc]
            [reitit.frontend.easy :as rfe]
            [reitit.spec :as rs]
            [sade.shared-schemas :as sssc]
            [sade.validators :as v]
            [schema.core :as s]))

(re-frame/reg-event-db
  ::init-router
  (fn [db [_ router]]
    (assoc db ::router router)))


(re-frame/reg-event-fx
  :route/on-navigate
  (fn [{:keys [db]} [_ new-match router]]
    (if new-match
      {:db (update db :route/match #(assoc new-match :controllers (rfc/apply-controllers (:controllers %) new-match)))}
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


(re-frame/reg-sub
  :route/match
  (fn [db]
    (get db :route/match)))


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

(defn match->page-event [match]
  (let [path               (:path match)
        ;; rest strips exclamation mark ! from the path parts
        [page & page-path] (rest (str/split path #"/"))]
    {:pageId         page
     :pagePath       page-path
     :currentHash    path
     :scroll-to-top? (get-in match [:data :parameters :scroll-to-top?] true)}))

(defn apply-legacy-page-change [{:keys [pageId scroll-to-top?] :as page-event} visible?]
  (let [elem (js/document.getElementById pageId)]
    (if elem
      (if visible?
        (.add (.-classList elem) "visible")
        (.remove (.-classList elem) "visible"))
      (do
        ;; No element found, perhaps we are in hashbang view
        ;; because we retain the URL and hashbang/index.html provides
        ;; the user possibility to login with redirection to the requested but
        ;; unauthorized page
        (when-not (= js/lupapisteApp.startPage "hashbang")
          (throw (js/Error pageId)))))
    (if visible?
      (js/hub.send "page-load" (clj->js page-event))
      (js/hub.send "page-unload" (clj->js page-event)))
    (when scroll-to-top?
      (js/window.scrollTo 0 0))))


(defn toggle-legacy [visible? match]
  (let [page-event (match->page-event match)
        toggle-page (fn []
                      (try
                        (apply-legacy-page-change page-event visible?)
                        (catch js/Error e
                          (let [start-page js/lupapisteApp.startPage]
                            (js/console.warn "Bad page-id:" (.-message e)
                                             "- Redirecting to start page"
                                             start-page)
                            (>evt [:route/replace (keyword start-page)])))))]
    (cond
      (or js/lupapisteApp.allowAnonymous
          (<sub [::sess/user]))
      (toggle-page)

      (not js/lupapisteApp.allowAnonymous)
      (do
        (js/pageutil.showAjaxWaitNow)
        (ajax/query "user"
                    {:success-fn (partial sess/user-success toggle-page)
                     :error-fn   sess/error-response
                     :complete-fn js/pageutil.hideAjaxWait}))

      :else
      (do
        (js/console.error "Unauthorized Reitit route, redirecting to login")
        (>evt [:route/replace :login])))))

(def bulletins-controller
  {:start #(js/hub.send "page-load" (clj->js (match->page-event %)))
   :stop  #(js/hub.send "page-unload" (clj->js (match->page-event %)))
   :identity identity})

(def modern-pages
  "List page IDs that are statically defined to router.
  To avoid route conflicts with dynamically parsed pages from DOM."
  #{"application" "attachment" "bulletins" "company" "company-reports"
    "task" "neighbors" "neighbor" "statement" "printing-order"
    "verdict" "verdict-attachments-select" "send-attachments"
    ;; admin pages
    "organization" "users" "edit-authority"})

(def legacy-paths
  {"inforequest" "/:app-id"})

(def default-legacy-controller
  {:start    (partial toggle-legacy true)
   :stop     (fn [match]
               (js/hub.send "scrollService::push"
                            (clj->js {:hash (str "#" (:path match))
                                      :followed true
                                      :override true}))
               (toggle-legacy false match))
   :identity identity})


(defn legacy-routes []
  (for [page-id (legacy/page-sections)
        :when (not (contains? modern-pages page-id))]
    [(str "!/" page-id (get legacy-paths page-id))
     ["" {:controllers [default-legacy-controller]
          :name        (keyword page-id)
          :page        page-id}]
     ["/*" {:controllers [{:identity identity
                           :start    #(js/console.warn "Undefined Reitit sub-path for legacy page" (:path %1))}
                          default-legacy-controller]}]]))

(def admin-routes
  [["!/organization/:org-id"
    {:controllers [default-legacy-controller]
     :page        "admin:organization"
     :parameters  {:path {:org-id s/Str}}}]
   ["!/users"
    {:controllers [default-legacy-controller]
     :name        :users
     :page        "admin:users"}]
   ["!/edit-authority/:user-id"
    {:controllers [default-legacy-controller]
     :page        "admin:edit-authority"
     :parameters  {:path {:user-id s/Str}}}]])

(def routes
  (rf/router
    (concat
      [["/" {:controllers [{:start #(>evt [:route/replace (keyword js/lupapisteApp.startPage)])}]}]]
      (legacy-routes)
      admin-routes
      [["!/application/:app-id"
        {:controllers [default-legacy-controller]
         :page        "application"
         :parameters  {:path           {:app-id v/application-id-pattern}
                       :scroll-to-top? false}}
        [""] ; aka '/info'
        ["/info"]
        ["/parties"]
        ["/structures"]
        ["/attachments"]
        ["/requiredFieldSummary"]
        ["/statement"]
        ["/verdict"]
        ["/ymp-bulletin"]
        ["/bulletin"]
        ["/tasks"]
        ["/inspectionSummaries"]
        ["/caseFile"]
        ["/archival"]
        ["/filebank"]
        ["/applicationSummary"]
        ["/invoice"]
        ["/conversation" ; emails about new comment contain this suffix, which should pop up conversation panel
         {:controllers [{:start #(>evt [::legacy/open-side-panel "conversation"])}]
          :page        "application-conversation"
          :name        :conversation-panel}]
        ["/notice" ; pops up notice panel
         {:controllers [{:start #(>evt [::legacy/open-side-panel "notice"])}]
          :page        "application-notices"
          :name        :notice-panel}]]
       ["!/attachment/:app-id/:att-id"
        {:controllers [default-legacy-controller]
         :page        "attachment"
         :name        :attachment
         :parameters  {:path {:att-id s/Str ; could be ObjectIdStr or composite of LP_userid_objectid
                              :app-id v/application-id-pattern}}}]
       ["!/verdict/:app-id/:verdict-id"
        {:controllers [default-legacy-controller]
         :page        "verdict"
         :name        :verdict
         ;; we could handle verdict page purely in CLJS, just render pate-verdict component here
         :parameters  {:path {:verdict-id sssc/ObjectIdStr
                              :app-id     v/application-id-pattern}}}]
       ["!/neighbors/:app-id"
        {:controllers [default-legacy-controller]
         :page        "neighbors"
         :name        :neighbors
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/neighbor"
        {:controllers [default-legacy-controller]
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
        {:controllers [default-legacy-controller]
         :page        "statement"
         :parameters  {:path {:statement-id sssc/ObjectIdStr
                              :app-id       v/application-id-pattern}}}
        ["/statement"]
        ["/reply-request"]
        ["/reply"]]
       ["!/task/:app-id/:task-id"
        {:controllers [default-legacy-controller]
         :page        "task"
         :name        :task
         :parameters  {:path {:task-id sssc/ObjectIdStr
                              :app-id  v/application-id-pattern}}}]
       ["!/printing-order/:app-id"
        {:controllers [default-legacy-controller]
         :page        "printing-order"
         :name        :printing-order
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/verdict-attachments-select/:app-id"
        {:controllers [default-legacy-controller]
         :page        "verdict-attachments-select"
         :name        :verdict-attachments-select
         :parameters  {:path {:app-id v/application-id-pattern}}}]
       ["!/send-attachments/:app-id"
        {:controllers [default-legacy-controller]
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
        {:controllers [bulletins-controller]
         :parameters  {:path {:app-id s/Str}}}
        [""]
        ["/info"]
        ["/attachments"]
        ["/instructions"]
        ["/verdicts"]]])
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
                               :start    #(g/set js/document "title" js/lupapisteApp.defaultTitle)}]
                :coercion    rsc/coercion}
     :validate rs/validate}))

(defn init []
  (js/console.log "Starting Reitit frontend router")
  (>evt [::init-router routes])
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
