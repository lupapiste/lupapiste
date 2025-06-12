(ns lupapalvelu.ui.misc
  "Miscellaneous re-frame stuff and corresponding helper functions."
  (:require [cljs.pprint]
            [clojure.set :as set]
            [goog.i18n.NumberFormat.Format]
            [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common]
            [re-frame-fx.dispatch]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [sade.util :as util]
            [schema.core :as s])
  (:import [goog.async Delay]))

(defn set-value
  "`xs` consists of path and value."
  [db xs]
  (assoc-in db (flatten (butlast xs)) (last xs)))

(defn update-value
  "`xs` consists of path and value.
  Note that the update function _must_ be a function (and not e.g. a keyword)"
  [db xs]
  (let [[path fn-and-args] (split-with (complement fn?) xs)]
    (apply update-in db (flatten path) fn-and-args)))

;; -------------------
;; Events
;; -------------------

(rf/reg-event-fx
  :debug/log
  (fn [{db :db} [_ & xs]]
    (js/console.log (cond-> db
                      (seq xs) (get-in xs)))))

(rf/reg-event-fx
  :debug/print-db
  (fn [{db :db} [_ & xs]]
    (cljs.pprint/pprint (cond-> db (seq xs) (get-in xs)))))

(defn print-db [& path]
  (rf/dispatch (vec (concat [:debug/print-db] path))))

(defn scroll-to-element [element]
  (.scrollIntoView element))

(rf/reg-fx
  :scroll/id
  (fn [id]
    (some->> id (.getElementById js/document) scroll-to-element)))

(rf/reg-fx
  :scroll/to
  (fn [y-or-xy]
    (let [[x y] (if (number? y-or-xy)
                  [y-or-xy 0]
                  y-or-xy)]
      (.scrollTo js/window x y))))

(rf/reg-fx
  :click/id
  (fn [id]
    (some->> id (.getElementById js/document) (.click))))

(rf/reg-fx :page/open
  (fn [path]
    (common/open-page path)))

(rf/reg-event-db
  :value/set
  (fn [db [_ & xs]]
    (set-value db xs)))

(rf/reg-event-db
  :value/update
  (fn [db [_ & xs]]
    (update-value db xs)))

(rf/reg-event-db
  :action/store-response
  (fn [db [_ response accessor & path]]
    (set-value db [path (accessor response)])))

(rf/reg-fx ; for applying simple impure functions, e.g. destroying a JS object
  :fn!
  (fn [{:keys [func params]}]
    (apply func params)))

(defn- make-fn  [handler]
  (cond
    (fn? handler)         handler
    (keyword? handler)    #(>evt [handler %])
    (sequential? handler) #(>evt (vec (concat [(first handler) %]
                                              (rest handler))))))

(s/defschema Callback
  (s/cond-pre
    vector? [s/Any]
    fn?     s/Any))

(s/defschema QueryParams
  {:name    s/Keyword
   (s/optional-key :success) Callback
   (s/optional-key :error)   Callback
   (s/optional-key :params)  {s/Keyword s/Any}})

(s/defschema CommandParams
  {:name    s/Keyword
   (s/optional-key :success)   Callback
   (s/optional-key :error)     Callback
   (s/optional-key :fail)      Callback
   (s/optional-key :complete)  Callback
   (s/optional-key :timed-out) Callback
   (s/optional-key :params)    {s/Keyword s/Any}
   (s/optional-key :show-saved-indicator?) s/Bool})

(s/defn action-query
  [{:keys [name success error params]} :- QueryParams]
  (let [fun (if error common/query-with-error-fn common/query)
        err-fn (make-fn error)
        args (cond-> [name (make-fn success)]
               err-fn (conj err-fn))]
    (->> (into [] params)
         (apply concat args)
         (apply fun))))

(s/defn action-command [{:keys [name success error show-saved-indicator? timed-out params fail complete]} :- CommandParams]
  (->> (into [] params)
       (apply concat)
       (apply common/command
              {:command               name
               :show-saved-indicator? show-saved-indicator?
               :success               (make-fn success)
               :error                 (make-fn error)
               :on-timeout            (make-fn timed-out)
               :fail                  (make-fn fail)
               :on-complete           (make-fn complete)})))

(rf/reg-fx :action/query action-query)
(rf/reg-fx :action/command action-command)

(rf/reg-fx
  :post/json
  (fn [{:keys [path success error params]}]
    (common/post-json {:path    path
                       :success (make-fn success)
                       :error   (make-fn error)
                       :params  params})))

(rf/reg-fx
  :post/form
  (fn [{:keys [raw-name success error complete params]}]
    (common/post-form {:raw-name raw-name
                       :success  (make-fn success)
                       :error    (make-fn error)
                       :complete (make-fn complete)
                       :params   params})))

(rf/reg-fx
  :dialog/show
  (fn [{:keys [event callback] :as options}]
    (-> options
        (set/rename-keys {:title-loc :ltitle
                          :text-loc  :ltext})
        (assoc :callback (if callback
                           callback
                           (some->> event (partial >evt))))
        common/show-dialog)))

(rf/reg-event-fx
  :dialog/show
  (fn [_ [_ options]]
    {:dialog/show options}))

(rf/reg-fx
  :dialog/close
  (fn [_] (hub/send "close-dialog")))

(rf/reg-event-fx
  :dialog/close
  (fn [_ _]
    {:dialog/close true}))

(rf/reg-fx :dialog/resize
  (fn []
    ;; Resize _after_ reagent has finished rendering and
    ;; the new dialog contents are in the DOM
    (reagent/after-render
      (fn [] (hub/send "resize-dialog")))))

;; Displays or hides a page overlay with an Ajax loader wheel. The overlay covers
;; all content on the page except the fixed header.
(rf/reg-fx :ajaxwait/show
  (fn [show?]
    (if show?
      (js/pageutil.showAjaxWaitNow)
      (js/pageutil.hideAjaxWait))))

(rf/reg-fx :application/lightload
  (fn [app-id]
    (js/repository.lightLoad app-id)))

(rf/reg-fx :application/load
  (fn [app-id]
    (js/repository.load app-id)))

(rf/reg-fx :application/refresh-auth
  (fn [app-id]
    (.refresh js/lupapisteApp.models.applicationAuthModel app-id)))

;; Does (light) repository load if the application model is out of date.
(rf/reg-event-fx :application/sync
  (fn [_]
    (when-let [app-id (common/application-id)]
      (when-not (= app-id (js/lupapisteApp.models.application.id))
        {:application/lightload app-id}))))

(rf/reg-event-fx :application/open
  (fn [_ [_ application-id & kvs]]
    (let [{:keys [inforequest? suffix]} (apply hash-map kvs)]
      (js/pageutil.openApplicationPage (clj->js {:kind (if inforequest?
                                                         :inforequest
                                                         :application)
                                                 :id   application-id})
                                       (some-> suffix name)))))

(rf/reg-fx :text/copy-to-clipboard
  (fn [text]
    (js/navigator.clipboard.writeText text)))

(defn focus-on-element [selector retry-limit]
  (let [target-elem         (.querySelector js/document selector)
        rerun-after-timeout (fn []
                              (js/setTimeout
                                #(focus-on-element selector (dec retry-limit))
                                5))]
    (cond (and (zero? retry-limit)
               target-elem)
          ;; Last chance: hope that the browser scrolls to somewhere near the focused element.
          (.focus target-elem)
          (and (not (zero? retry-limit))
               (not target-elem))
          (rerun-after-timeout)
          (and (not (zero? retry-limit))
               target-elem)
          (let [offset         (some-> (js/$ target-elem) (.offset))
                nav-height     (or (-> (js/$ "nav:first") (.height)) 0)
                room-for-label (if (= "UL" (.-nodeName target-elem)) 0 30)]
            (if (or (not offset) (= 0 (.-left offset)))
              ;; Element is not yet visible, wait for a short moment.
              ;; Because of the padding, offset left is never zero when
              ;; the element is visible.
              (rerun-after-timeout)
              (do
                (.scrollTo js/window 0 (- (.-top offset) nav-height room-for-label))
                (.focus target-elem)))))))

(rf/reg-fx :focus-on-element
  (fn [[selector retry-limit]]
    (focus-on-element selector retry-limit)))

;; Indicators

(rf/reg-fx :indicator/saved
  (fn [resp]
    ;; Same as `js/util.showSavedIndicator`
    (hub/send :indicator
              {:style   (if (:ok resp) :positive :negative)
               :message (:text resp)})))

(rf/reg-fx
  :indicator/show
  (fn [{:keys [text-loc text html? style sticky?]}]
    (hub/send :indicator
              (util/assoc-when {:style style}
                               :sticky sticky?
                               :message text-loc
                               :rawMessage text
                               :html html?))))

(rf/reg-fx
  :indicator/negative
  (fn [{:keys [text-loc text html?]}]
    (hub/send :indicator
              (util/assoc-when {:style :negative}
                               :sticky true
                               :message text-loc
                               :rawMessage text
                               :html html?))))

(rf/reg-event-fx
  :indicator/positive
  (fn [_ [_ text-loc html?]]
    {:indicator/show {:text-loc text-loc
                      :html?    html?
                      :style    :positive}}))

(rf/reg-event-fx
  :indicator/negative
  (fn [_ [_ text-loc text html?]]
    {:indicator/show {:text-loc text-loc
                      :text     text
                      :html?    html?
                      :style    :negative
                      :sticky?  true}}))

(rf/reg-fx :indicator/icon
  (fn [resp]
    (hub/send :indicator-icon
              {:style   (if (:ok resp) :positive :negative)
               :message (:text resp)})))

;; Poller


(defonce *pollers (atom {}))

(defn- cleanup-poller
  [pollers poller-id]
  (when-let [^js poller (get pollers poller-id)]
    (.dispose poller)))

(rf/reg-fx ::register-poller
  (fn [{:keys [poller-id interval-ms fun]}]
    (let [poller   (Delay. fun interval-ms)
          [old _]  (swap-vals! *pollers assoc poller-id poller)]
      ;; just in case
      (cleanup-poller old poller-id)
      (.start poller))))

(rf/reg-fx ::fire-poller
  (fn [{:keys [poller-id event]}]
    (when-let [poller (get (deref *pollers) poller-id)]
      (.start poller)
      (rf/dispatch event))))

(rf/reg-fx ::unregister-poller
  (fn [{:keys [poller-id]}]
    (let [[old _] (swap-vals! *pollers dissoc poller-id)]
      (cleanup-poller old poller-id))))

(rf/reg-event-fx ::poller-fire
  (fn [_ [_ poller-id event]]
    {::fire-poller {:poller-id poller-id
                    :event     event}}))

(rf/reg-event-fx :poller/add
  (fn [_ [_ poller-id interval-ms event]]
    {::register-poller {:poller-id   poller-id
                        :interval-ms interval-ms
                        :fun         #(>evt [::poller-fire poller-id event])}}))

(rf/reg-event-fx :poller/remove
  (fn [_ [_ poller-id]]
    {::unregister-poller {:poller-id poller-id}}))

;; Browser history

(rf/reg-fx ::navigate-back
  (fn []
    (.back js/window.history)))

;; -------------------
;; Auth
;; -------------------

;; Refreshing auth model in 're-frame side' means toggling the flag that forces the
;; re-evaluation of the auth subscriptions (see below). In addition, the auth model
;; hubscription is added if needed.
(rf/reg-event-db :auth/refresh
  (fn [{:keys [::authorization-hub-id] :as db} _]
    (cond-> (update db ::authorization-flag not)
      (not authorization-hub-id)
      (assoc ::authorization-hub-id (hub/subscribe :auth-model-changed #(>evt [:auth/refresh]))))))

(rf/reg-event-db :auth/category-refresh
  (fn [{:keys [::authorization-category-hub-id] :as db} _]
    (cond-> (update db ::document-category-authorization-flag not)
      (not authorization-category-hub-id)
      (assoc ::authorization-category-hub-id (hub/subscribe :category-auth-model-changed #(>evt [:auth/category-refresh]))))))

;; -------------------
;; Auth update
;; -------------------

(rf/reg-fx ::update-global-auth
  (fn []
    (.refresh js/lupapisteApp.models.globalAuthModel)))

;; -------------------
;; Subs
;; -------------------

(rf/reg-sub
  :value/get
  (fn [db [_ & path]]
    (get-in db (flatten path))))

(rf/reg-sub
  :auth/global?
  :<- [:value/get ::authorization-flag]
  (fn [_ [_ action]]
    (auth/global-auth? action)))

(rf/reg-sub
  :auth/application?
  :<- [:value/get ::authorization-flag]
  (fn [_ [_ & ks]]
    (every? auth/application-auth? (flatten ks))))

(rf/reg-sub
  :auth/attachment?
  :<- [:value/get ::authorization-flag]
  (fn [_ [_ attachment-or-id action]]
    (auth/attachment-auth? attachment-or-id action)))

(rf/reg-sub
  :auth/any-attachment?
  :<- [:value/get ::authorization-flag]
  (fn [_ [_ attachments-or-ids action]]
    (some #(auth/attachment-auth? % action) attachments-or-ids)))

(rf/reg-sub :auth/document?
  :<- [:value/get ::document-category-authorization-flag]
  (fn [_ [_ doc-id action]]
    (-> (.findDocumentById js/lupapisteApp.services.documentDataService doc-id)
        .-auth
        (.ok (name action)))))
