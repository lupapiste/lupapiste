(ns lupapalvelu.next.session
  (:require [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(rf/reg-event-db ::store-org-names
  [(rf/path :session :org-names)]
  (fn [_ [_ names]]
    names))

(rf/reg-event-db ::store-usage-purposes
  [(rf/path :session :usage-purposes)]
  (fn [_ [_ purposes]]
    purposes))

(defn user [db]
  (get-in db [:session :user]))

(defn user-id [db]
  (:id (user db)))

(defn usage-purpose-from-url
  "Returns the usage purpose map parsed from the current URL path, or nil.
  Read once at session initialisation and stored in app-db; exposed here for
  callers that need the raw value before the session is available.
  Mirrors the JS util.usagePurposeFromUrl() function.
  NOTE: If JS-side util.usagePurposeFromUrl is ever changed, update this too."
  []
  (when-let [match (re-find #"/app/[a-z]{2}/([^/]*)/?([^/]*)" (.. js/window -location -pathname))]
    (let [purpose-type (get match 1)
          org-id       (not-empty (get match 2))]
      (cond-> {:type purpose-type}
        org-id (assoc :orgId org-id)))))

(rf/reg-event-db ::invalidate
  (fn [db [_]]
    (dissoc db :session)))

(rf/reg-event-fx ::initialize
  (fn [{:keys [db]} [_ session-data]]
    {:db (-> db
             (assoc :session session-data)
             (assoc-in [:session :page-usage-purpose] (usage-purpose-from-url)))
     :fx [[:action/query {:name    :organization-names-by-user
                          :success #(>evt [::store-org-names (:names %)])}]
          [:action/query {:name    :usage-purposes
                          :success #(>evt [::store-usage-purposes (:usagePurposes %)])}]]}))

(rf/reg-sub ::session :-> :session)

(rf/reg-sub ::user
  :<- [::session]
  :-> :user)

(rf/reg-sub ::display-name
  :<- [::user]
  (fn [{:keys [firstName lastName username]} _]
    (let [full-name (ss/join-non-blanks " " [firstName lastName])]
      (or full-name username ""))))

(rf/reg-sub ::email
  :<- [::user]
  :-> :email)

(rf/reg-sub ::role
  :<- [::user]
  :-> :role)

(rf/reg-sub ::org-authz
  :<- [::user]
  :-> :orgAuthz)

(rf/reg-sub ::org-names
  :<- [::session]
  :-> :org-names)

(rf/reg-sub ::language
  :<- [::user]
  :-> :language)

(rf/reg-sub ::usage-purposes
  :<- [::session]
  :-> :usage-purposes)

;; Usage purpose stored at session init — read once from URL, never re-derived from location.
(rf/reg-sub ::page-usage-purpose
  :<- [::session]
  :-> :page-usage-purpose)

(defn- current-org-name [org-names org-authz lang page-purpose]
  (let [{org-id :orgId} page-purpose
        ;; When no active org context, fall back to the user's sole org (if they have exactly one)
        sole-org-id     (when (and (nil? org-id) (= 1 (count org-authz)))
                          (name (ffirst org-authz)))]
    (or (when (and org-names org-id)
          (get-in org-names [(keyword org-id) (keyword lang)]))
        (when (and org-names sole-org-id)
          (get-in org-names [(keyword sole-org-id) (keyword lang)])))))

(rf/reg-sub ::current-org-name
  :<- [::org-names]
  :<- [::org-authz]
  :<- [::language]
  :<- [::page-usage-purpose]
  (fn [[org-names org-authz lang page-purpose] _]
    (current-org-name org-names org-authz lang page-purpose)))

;; True when the current URL context is an authority-admin view.
(rf/reg-sub ::page-authority-admin?
  :<- [::page-usage-purpose]
  (fn [purpose _] (= "authority-admin" (:type purpose))))

;; Usage purposes available to switch to, excluding the currently active one.
(rf/reg-sub ::alt-usage-purposes
  :<- [::usage-purposes]
  :<- [::page-usage-purpose]
  (fn [[all-purposes current] _]
    (when (seq all-purposes)
      (remove #(and (= (:type %) (:type current))
                    (= (:orgId %) (:orgId current)))
              all-purposes))))

(defn user-success
  "Success handler from calling 'ajax.query(\"user\")' "
  [router-callback js-result]
  (let [data (js->clj js-result :keywordize-keys true)]
    (if (:user data)
      (do
        (>evt [::initialize data])
        (js/hub.send "login" js-result)
        (router-callback))
      (do
        (js/console.error "User query did not return user, response" js-result)
        (>evt [::invalidate])
        (js/hub.send "logout" js-result)))))

(defn error-response [js-result]
  (>evt [::invalidate])
  (js/hub.send "logout" js-result))
