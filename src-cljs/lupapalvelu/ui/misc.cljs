(ns lupapalvelu.ui.misc
  "Miscellaneous re-frame stuff and corresponding helper functions."
  (:require [cljs.pprint]
            [clojure.set :as set]
            [goog.i18n.NumberFormat.Format]
            [lupapalvelu.common.hub :as hub]
            [re-frame-fx.dispatch] ; :dispatch-debounce effect handler
            [lupapalvelu.next.event :refer [>evt]]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]
            [sade.shared-util :as util]))

(defn set-value
  "`xs` consists of path and value."
  [db xs]
  (assoc-in db (flatten (butlast xs)) (last xs)))

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

(rf/reg-event-db
  :value/set
  (fn [db [_ & xs]]
    (set-value db xs)))

(rf/reg-event-db
  :action/store-response
  (fn [db [_ response accessor & path]]
    (set-value db [path (accessor response)])))

(defn- make-fn  [handler]
  (cond
    (fn? handler)         handler
    (keyword? handler)    #(>evt [handler %])
    (sequential? handler) #(>evt (vec (concat [(first handler) %]
                                              (rest handler))))))

(rf/reg-fx
  :action/query
  (fn [{:keys [name success error params]}]
    (let [fun (if error common/query-with-error-fn common/query)
          err-fn (make-fn error)
          args (cond-> [name (make-fn success)]
                 err-fn (conj err-fn))]
      (->> (into [] params)
           (apply concat args)
           (apply fun)))))

(rf/reg-fx
  :action/command
  (fn [{:keys [name success error show-saved-indicator? timed-out params]}]
    (->> (into [] params)
         (apply concat)
         (apply common/command
                {:command               name
                 :show-saved-indicator? show-saved-indicator?
                 :success               (make-fn success)
                 :error                 (make-fn error)
                 :on-timeout            (make-fn timed-out)}))))

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

(rf/reg-fx
  :dialog/close
  (fn [_] (hub/send "close-dialog")))

(rf/reg-event-fx
  :dialog/close
  (fn [_ _]
    {:dialog/close true}))

;; Refreshing auth model in 're-frame side' means toggling the flag that forces the
;; re-evaluation of the auth subscriptions (see below). In addition, the auth model
;; hubscription is added if needed.
(rf/reg-event-db
  :auth/refresh
  (fn [{:keys [::authorization-hub-id] :as db} _]
    (cond-> (update db ::authorization-flag not)
      (not authorization-hub-id)
      (assoc ::authorization-hub-id (hub/subscribe :auth-model-changed #(>evt [:auth/refresh]))))))

;; Does (light) repository load if the application model is out of date.
(rf/reg-event-fx
  :application/sync
  (fn [_]
    (when-let [app-id (common/application-id)]
      (when-not (= app-id (js/lupapisteApp.models.application.id))
        (js/repository.lightLoad app-id)))))

;; Indicators

(rf/reg-fx
  :indicator/show
  (fn [{:keys [text-loc text html? style sticky?]}]
    (hub/send :indicator
              (util/assoc-when {:style style}
                               :sticky sticky?
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
  (fn [_ [_ text-loc html?]]
    {:indicator/show {:text-loc text-loc
                      :html?    html?
                      :style    :negative
                      :sticky?  true}}))

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
