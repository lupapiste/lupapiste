(ns lupapalvelu.ui.admin.reports.core
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.reports.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as ui]
            [lupapalvelu.ui.components.organization-report-form :as org-form]
            [lupapalvelu.ui.dom :as ui-dom]
            [sade.util :as util]))

(defn- user-row
  [{:keys [arg label]}]
  [:div.col-1
   [:label label]
   [ui/select
    (<sub [::subs/user-selection-value arg])
    (->> subs/user-options
         (mapv (juxt :value :text)))
    {:callback (fn [value]
                 (let [value (keyword value)]
                   (>evt [::subs/change-user-selection (util/mmap arg value)])))}]])

(defn- user-report-download-button
  []
  [:div.col-1
   [:br]
   [ui/link-button
    {:text "Lataa raportti"
     :url  (<sub [::subs/user-link])
     :icon :lupicon-download}]])

(defn- users-no-email
  []
  [:div.row
   [:div.col-1
    [:br]
    [:label "Henkilöt, joille ei lähetetä sähköpostia"]
    [ui/textarea-edit
     (<sub [::subs/emails-value])
     {:callback (fn [value]
                  (>evt [::subs/change-unsubscribed-emails {:emails value}]))
      :on-blur? true}]
    [ui/icon-button
     {:class    :primary
      :text-loc :save
      :on-click (fn []
                  (>evt [::subs/submit-unsubscribed-emails]))}]]])

(defn- users
  []
  [:<>
   [:h3 "Käyttäjät"]
   [:div.form-grid
    {:id "admin-user-report"}
    (->> (concat (map user-row subs/user-reports)
                 [[user-report-download-button]
                  [users-no-email]])
         (into [:div.row]))]])

(def report-data
  [{:id             :applications-reports
    :action         :applications-period-report
    :title-loc      :authorityAdmin.applicationsReportTimeperiod.title
    :button-loc     :authorityAdmin.applicationsReportTimeperiod.buttonText
    :start-date-loc :authorityAdmin.applicationsReportTimeperiod.startTs
    :end-date-loc   :authorityAdmin.applicationsReportTimeperiod.endTs}
   {:id             :billings-reports
    :title-loc      :authorityAdmin.storeBillingReports.titleAll
    :action         :store-billing-report-all-municipalities
    :caption-loc    :authorityAdmin.storeBillingReports.captionAll
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :authorityAdmin.storeBillingReports.timePeriod.startTs
    :end-date-loc   :authorityAdmin.storeBillingReports.timePeriod.endTs
    :shortcut       {:action     :store-billing-report-all-municipalities
                     :button-loc :authorityAdmin.storeBillingReports.lastMonthButtonText}}
   {:id             :store-downloads-reports
    :title-loc      :authorityAdmin.storeDownloadsReports.titleAll
    :action         :store-downloads-report-all-municipalities
    :caption-loc    :authorityAdmin.storeDownloadsReports.captionAll
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :authorityAdmin.storeDownloadsReports.timePeriod.startTs
    :end-date-loc   :authorityAdmin.storeDownloadsReports.timePeriod.endTs
    :shortcut       {:action     :store-downloads-report-all-municipalities
                     :button-loc :authorityAdmin.storeBillingReports.lastMonthButtonText}}
   {:id             :digitizing-reports
    :title-loc      :authorityAdmin.digitizingReports.titleAll
    :action         :digitizer-applications-report-all-municipalities
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :authorityAdmin.digitizingReports.timePeriod.startTs
    :end-date-loc   :authorityAdmin.digitizingReports.timePeriod.endTs
    :shortcut       {:action     :digitizer-applications-report-all-municipalities
                     :button-loc :authorityAdmin.storeBillingReports.lastMonthButtonText}}
   {:id             :rami-reports
    :title-loc      :oauth.report.title
    :action         :rami-logins-report
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :oauth.report.start
    :end-date-loc   :oauth.report.end}
   {:id             :verdicts-reports
    :title-loc      :admin-report.verdicts.title
    :action         :verdicts-contracts-report
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :oauth.report.start
    :end-date-loc   :oauth.report.end}
   {:id             :operation-areas-reports
    :title-loc      :admin-report.operation-areas
    :action         :operation-areas-report
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :oauth.report.start
    :end-date-loc   :oauth.report.end}
   {:id             :archivable-applications-report
    :title-loc      :admin-report.archivable-applications
    :action         :archivable-applications-report
    :button-loc     :authorityAdmin.storeBillingReports.buttonText
    :start-date-loc :admin-report.archivable-applications.start
    :end-date-loc   :admin-report.archivable-applications.end}])

(defn- make-report
  [{:keys [id action title-loc button-loc caption-loc start-date-loc end-date-loc shortcut]}]
  [:section
   [:h3 (common/loc title-loc)]
   [:div.form-grid
    [org-form/report-form
     (merge {:action      action
             :button-loc  button-loc
             :caption-loc caption-loc}
            (when (or start-date-loc end-date-loc)
              {:start-date-loc       start-date-loc
               :end-date-loc         end-date-loc
               :start-ts             (<sub [::subs/start-date-ts id])
               :end-ts               (<sub [::subs/end-date-ts id])
               :start-date           (<sub [::subs/start-date id])
               :end-date             (<sub [::subs/end-date id])
               :on-start-date-change (fn [date] (>evt [::subs/change-start-date {:id id :start-date date}]))
               :on-end-date-change   (fn [date] (>evt [::subs/change-end-date {:id id :end-date date}]))}))]
    ;; Some reports offer a "fetch report for the last month" shortcut
    (when-let [{:keys [action button-loc]} shortcut]
      [org-form/report-form
       {:action     action
        :button-loc button-loc
        :start-ts   (<sub [::subs/last-month-start-ts])
        :end-ts     (<sub [::subs/last-month-end-ts])}])]])

(defn- reports
  []
  (->> (map make-report report-data)
       (into [:<>])))

(defn- view
  []
  (when (<sub [::subs/visible?])
    [:<>
     [:h2 "Raportit"]
     [:p [:b "Huom:"] " Raporttien generointi voi kestää jokusen tovin."]
     [users]
     [reports]]))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id _]
  (ui-dom/mount! mounter dom-id))
