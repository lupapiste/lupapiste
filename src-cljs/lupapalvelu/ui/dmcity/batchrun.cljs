(ns lupapalvelu.ui.dmcity.batchrun
  "Admin admin batchrun functionality. Sends batchrun steps to `send-messages-to-dmcity`
  endpoint and shows the reported result."
  (:require [lupapalvelu.next.event :refer [>evt <sub]]
            [lupapalvelu.pate.path :refer [loc]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.dmcity.shared :as dm-sh]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [sade.strings :as ss]
            [sade.util :as util]))

(def app-id-re #"L[PX]-\d{3}-\d{4}-\d{5}")


(defn text->app-ids
  "List of application ids or nil."
  [text]
  (when-let [ids (some-> text ss/upper-case (ss/split #"[^\dA-Z\-]+") seq)]
    (when (every? #(re-matches app-id-re %) ids)
      ids)))

(defn client-ops [db]
  (seq  (for [[k v] (dm-sh/get-data db :batchrun-ops)
              :when v]
          (case k
            :state       v
            :application "application/verdicts"
            :reviews     :reviews))))

;; ---------------------------
;; Events
;; ---------------------------

(rf/reg-event-db
  ::set-channel
  (fn [db [_ channel]]
    (dm-sh/assoc-data db :channel channel)))

(rf/reg-event-db
  ::set-application-ids-text
  (fn [db [_ text]]
    (dm-sh/assoc-data db :application-ids-text text)))

(rf/reg-event-db
  ::set-batchrun-ops
  (fn [db [_ & kvs]]
    (dm-sh/update-data db update :batchrun-ops #(apply assoc % kvs))))

(rf/reg-event-fx
  ::process-pipeline.success
  (fn [{db :db} [_ {:keys [results]}]]
    {:db       (dm-sh/update-data db update :results concat results)
     :dispatch [::process-pipeline]}))

(rf/reg-event-fx
  ::process-pipeline.error
  (fn [{db :db} [_ {:keys [text]} app-id]]
    {:db       (dm-sh/update-data db update :errors
                                  util/conjv {:text-loc text
                                              :id       app-id})
     :dispatch [::process-pipeline]}))

(rf/reg-event-fx
  ::process-pipeline
  (fn [{db :db} _]
    (when-let [[x & xs] (seq (dm-sh/get-data db :pipeline))]
      (cond-> {:db (dm-sh/assoc-data db :pipeline (seq xs))}
        x
        (assoc :action/command
               {:name    :send-messages-to-dmcity
                :params  (util/assoc-when {:organizationId (dm-sh/get-data db :organization-id)
                                           :applicationId  x
                                           :ops            (client-ops db)}
                                          :channel (ss/blank-as-nil (dm-sh/get-data db :channel)))
                :success [::process-pipeline.success]
                :error   [::process-pipeline.error x]})))))

(rf/reg-event-fx
  ::start-batchrun
  (fn [{db :db} _]
    {:db       (->> (dm-sh/get-data db)
                    :application-ids-text
                    text->app-ids
                    (dm-sh/assoc-data db :pipeline))
     :dispatch [::process-pipeline]}))

(rf/reg-event-db
  ::clear-report
  (fn [db]
    (dm-sh/update-data db dissoc :results :errors)))

;; ---------------------------
;; Subs
;; ---------------------------

(rf/reg-sub
  ::application-ids-text
  :-> (dm-sh/access :application-ids-text))

(rf/reg-sub
  ::application-ids
  :<- [::application-ids-text]
  text->app-ids)

(rf/reg-sub
  ::pipeline
  :-> (dm-sh/access :pipeline))

(rf/reg-sub
  ::errors
  :-> (dm-sh/access :errors))

(defn- skip-reason [{:keys [state operation permitType infoRequest]}]
  (cond
    state       (loc state)
    operation   (loc :operations operation)
    permitType  (loc permitType)
    infoRequest (loc :applications.inforequest)))

(rf/reg-sub
  ::report
  (fn [db _]
    (some->> (dm-sh/get-data db :results)
             seq
             (map (fn [{:keys [application-id ts state verdict-id
                               review error skipped]}]
                    (->> (cond
                           state      [(loc state)]
                           verdict-id [verdict-id (loc :pate-verdict)]
                           review     [(:id review) (:name review)]
                           error      [(loc :calculation-error) error]
                           skipped    [(common/loc :dmcity.batchrun.skipped
                                                   (skip-reason skipped))]
                           :else      [(loc :dmcity.batchrun.application)])
                         (concat [application-id (js/util.finnishDateAndTime ts)])
                         (ss/join " - " ))))
             (ss/join "\n"))))

(rf/reg-sub
  ::batchrun-ops
  :-> (dm-sh/access :batchrun-ops))

(rf/reg-sub
  ::channel
  :-> (dm-sh/access :channel))

(rf/reg-sub
  ::can-run?
  :<- [::application-ids]
  :<- [::batchrun-ops]
  (fn [[ids ops]]
    (boolean (and (seq ids)
                  (some identity (vals ops))))))

(rf/reg-sub
  ::show-errors?
  :<- [::errors]
  :-> (comp not empty?))

(rf/reg-sub
  ::show-report?
  :<- [::report]
  :-> ss/not-blank?)

(defn error-notes []
  (into [:div.flex--column.flex--gap1]
        (for [{:keys [id text-loc]} (<sub [::errors])]
          [:div.error-note.flex--column
           [:strong.txt--code id " " text-loc ":"]
           [:span  (loc text-loc)]])))

(defn report []
[:div.flex--column.gap--t2
 [:label.lux {:for "batchrun-report"} (loc :dmcity.batchrun.results)]
 [components/text-edit
  (<sub [::report])
  {:lines    10
   :class    :txt--code
   :disabled true}]])

(defn view []
  (r/with-let [_ (>evt [::set-batchrun-ops nil])]
    (let  [running?     (boolean (seq (<sub [::pipeline])))
           show-report? (<sub [::show-report?])
           text         (<sub [::application-ids-text])
           ops          (<sub [::batchrun-ops])
           channel      (<sub [::channel])
           can-run?     (<sub [::can-run?])
           show-errors? (<sub [::show-errors?])]
      [:div.flex--column.flex--row-gap1
       [:h3 (loc :dmcity.batchrun)]
       [:div.pate-note (loc :dmcity.batchrun.info)]
       [:div.flex.flex--row-gap1.flex--column-gap2.flex--wrap
        [:div.flex--column.w--min-20em
         [:label.lux.required.gap--t1
          {:for "batchrun-app-ids"} (loc :dmcity.batchrun.ids)]
         [components/text-edit
          text
          {:id       "batchrun-app-ids"
           :disabled running?
           :lines    15
           :callback #(>evt [::set-application-ids-text %])}]]
        [:div.flex--column.flex--row-gap1
         [:div.flex--column.gap--t1
          [:label.lux {:for "batchrun-channel"} (loc :dmcity.channel)]
         [components/dropdown channel
          {:id         "batchrun-channel"
           :choose-loc :dmcity.channel.default
           :items      [{:value "production" :text (loc :dmcity.channel.production)}
                        {:value "conversion" :text (loc :dmcity.channel.conversion)}]
           :disabled?  running?
           :callback   #(>evt [::set-channel %])}]]
         [:div.flex--column.gap--t1
          [:label.lux {:for "batchrun-states"} (loc :dmcity.flag.state)]
          [components/dropdown
           (:state ops)
           {:id         "batchrun-states"
            :choose-loc :dmcity.batchrun.no-states
            :items      [{:value "last-state" :text (loc :dmcity.batchrun.last-state)}
                         {:value "all-states" :text (loc :dmcity.batchrun.all-states)}]
            :disabled?  running?
            :callback   #(>evt [::set-batchrun-ops :state (ss/blank-as-nil %)])}]]
         (into [:<>] (for [k [:application :reviews]]
                       [components/toggle
                        (k ops)
                        {:text-loc (case k
                                     :application :dmcity.batchrun.application-verdicts
                                     :reviews     :pate-reviews)
                         :disabled? running?
                         :callback #(>evt [::set-batchrun-ops k %])}]))]]
       [:div.flex--between.flex--gap2
        [components/icon-button {:text-loc :dmcity.batchrun.run
                                 :enabled? can-run?
                                 :wait?    running?
                                 :class    :primary
                                 :icon     :lupicon-hammer
                                 :on-click #(>evt [::start-batchrun])}]
        (when (or show-errors? show-report?)
          [components/icon-button {:class      :primary
                                   :icon       :lupicon-trash
                                   :icon-only? true
                                   :on-click   #(>evt [::clear-report])}])]
       (when show-errors?
         [error-notes])
       (when show-report?
         [report])])))
