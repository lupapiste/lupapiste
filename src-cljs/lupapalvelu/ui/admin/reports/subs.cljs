(ns lupapalvelu.ui.admin.reports.subs
  (:require [re-frame.core :as rf]
            [sade.strings :as ss]))

(def user-reports
  [{:label "Yritystili"
    :arg   :company}
   {:label "Ammattilainen"
    :arg   :professional}
   {:label "Suoramarkkinointilupa"
    :arg   :allow}])

(def user-options [{:text "Kyllä" :value :yes}
                   {:text "Ei" :value :no}
                   {:text "Sekä että" :value :both}])

(def query-unsubscribed [:action/query {:name    :company-unsubscribed-emails
                                        :success ::company-unsubscribed-emails.success}])


(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ visible?]]
    (if visible?
      {:db (assoc db ::data {:visible? true
                             :dates    {:now (js/Date.)}})
       :fx [query-unsubscribed]}
      {:db (dissoc db ::data)})))

(rf/reg-event-fx ::company-unsubscribed-emails.success
  (fn [{:keys [db]} [_ {:keys [emails]}]]
    {:db (assoc-in db [::data :emails] emails)}))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::user-selections
  :<- [::data]
  :-> :user-selections)

(rf/reg-sub ::user-selections-with-defaults
  :<- [::user-selections]
  (fn [selections]
    (merge (->> user-reports
                (map (fn [{:keys [arg]}]
                       [arg :yes]))
                (into {}))
           selections)))

(rf/reg-sub ::user-selection-value
  :<- [::user-selections-with-defaults]
  (fn [selections [_ arg]]
    (get selections arg)))

(rf/reg-event-fx ::change-user-selection
  (fn [{:keys [db]} [_ {:keys [arg value]}]]
    {:db (assoc-in db [::data :user-selections arg] value)}))

(rf/reg-sub ::user-link
  :<- [::user-selections-with-defaults]
  (fn [selections]
    (str "/api/raw/user-report?"
         (->> selections
              (map (fn [[k v]]
                     (str (name k) "=" (name v))))
              (ss/join "&")))))

(rf/reg-sub ::emails
  :<- [::data]
  :-> :emails)

(rf/reg-sub ::emails-value
  :<- [::emails]
  (fn [emails]
    (ss/join "\n" emails)))

(rf/reg-event-fx ::change-unsubscribed-emails
  (fn [{:keys [db]} [_ {:keys [emails]}]]
    {:db (assoc-in db [::data :emails] (ss/split-lines emails))}))

(rf/reg-event-fx ::submit-unsubscribed-emails
  (fn [{:keys [db]} _]
    (let [emails (get-in db [::data :emails])]
      {:fx [[:action/command {:name    :upsert-company-unsubscribed
                              :params  {:emails (ss/join " " emails)}
                              :success ::upsert-company-unsubscribed.success}]]})))

(rf/reg-event-fx ::upsert-company-unsubscribed.success
  (fn [_ [_ resp]]
    {:fx [query-unsubscribed
          [:indicator/saved resp]]}))

(rf/reg-sub ::dates
  :<- [::data]
  :-> :dates)

(rf/reg-sub ::now
  :<- [::dates]
  :-> :now)

(rf/reg-sub ::start-of-month
  :<- [::dates]
  (fn [{:keys [start-of-month now]}]
    (or start-of-month
        (-> (js/moment now)
            (.startOf "month")
            (.toDate)))))

(defn- date->ts-fn
  [end?]
  (fn [date]
    (cond-> (js/moment date)
      end?       (.endOf "day")
      (not end?) (.startOf "day")
      true       (.valueOf))))

(rf/reg-sub ::last-month-start-ts
  :<- [::now]
  (fn [now]
    (-> (js/moment now)
        (.subtract 1 "months")
        (.startOf "month")
        (.startOf "day")
        (.valueOf))))

(rf/reg-sub ::last-month-end-ts
  :<- [::now]
  (fn [now]
    (-> (js/moment now)
        (.subtract 1 "months")
        (.endOf "month")
        (.endOf "day")
        (.valueOf))))

;; start date + end date per report

(rf/reg-sub ::start-date-raw
  :<- [::dates]
  (fn [dates [_ id]]
    (get-in dates [id :start-date])))

(rf/reg-sub ::start-date
  (fn [[_ id]]
    [(rf/subscribe [::start-date-raw id])
     (rf/subscribe [::start-of-month])])
  (fn [[start-date start-of-month]]
    (or start-date
        start-of-month)))

(rf/reg-sub ::end-date-raw
  :<- [::dates]
  (fn [dates [_ id]]
    (get-in dates [id :end-date])))

(rf/reg-sub ::end-date
  (fn [[_ id]]
    [(rf/subscribe [::end-date-raw id])
     (rf/subscribe [::now])])
  (fn [[end-date now]]
    (or end-date now)))

(rf/reg-event-fx ::change-start-date
  (fn [{:keys [db]} [_ {:keys [id start-date]}]]
    {:db (assoc-in db [::data :dates id :start-date] start-date)}))

(rf/reg-event-fx ::change-end-date
  (fn [{:keys [db]} [_ {:keys [id end-date]}]]
    {:db (assoc-in db [::data :dates id :end-date] end-date)}))

(rf/reg-sub ::start-date-ts
  (fn [[_ id]]
    (rf/subscribe [::start-date id]))
  :-> (date->ts-fn false))

(rf/reg-sub ::end-date-ts
  (fn [[_ id]]
    (rf/subscribe [::end-date id]))
  :-> (date->ts-fn true))
