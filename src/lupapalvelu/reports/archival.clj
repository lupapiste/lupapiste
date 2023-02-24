(ns lupapalvelu.reports.archival
  "Organization specific archival report
  i.e. what has been archived and digitized through Lupapiste and by whom.
  Does not include documents imported straight into Onkalo,
  for that see `lupapalvelu.reports.onkalo`"
  (:require [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.property :as prop]
            [sade.util :as util])
  (:import [java.sql Timestamp]
           [org.dhatim.fastexcel Workbook BorderSide BorderStyle]))


(defn- applications [{:keys [organization-id start-ts end-ts]}]
  (mongo/select :applications {:organization organization-id
                               :created      {$gte start-ts
                                              $lte end-ts}
                               :state        {$in states/post-verdict-states}}))

(defn- ts-state-fn [lang history]
  (let [history (->> (filter :state history)
                     (sort-by :ts) ; Just in case
                     (map #(update % :state (partial i18n/localize lang))))]
    (fn [ts]
      (when ts
        (loop [[x & xs] history
               state    nil]
          (if (or (nil? x) (> (:ts x) ts))
            state
            (recur xs (:state x))))))))

(defn add-application-options
  [{:keys [lang organization-id] :as options} application]
  (let [{:keys [tosFunction archived primaryOperation state
                permitType creator history]} application]
    (merge options
           {:ts->state           (ts-state-fn lang history)
            :tos-function        (:name (tos/tos-function-with-name tosFunction organization-id))
            :primary-operation   (i18n/localize lang :operations (:name primaryOperation))
            :archiving-completed (:completed archived)
            :application-id      (:id application)
            :backend-id          (vif/published-kuntalupatunnus application)
            :property-id         (some-> application :propertyId prop/to-human-readable-property-id)
            :application-state   (i18n/localize lang state)}
           (when (util/=as-kw permitType :ARK)
             {:digitizer (usr/full-name creator)
              :archiver  (some-> (util/find-by-key :state "archived" history)
                                 :user
                                 usr/full-name)}))))

(defn add-attachment-options
  "Each row represents an attachment."
  [{:keys [ts->state lang] :as options}
   {:keys [latestVersion contents metadata modified type]}]
  (merge options
         {:contents        contents
          :filename        (:filename latestVersion)
          :attachment-type (i18n/localize lang :attachmentType (:type-group type) (:type-id type))
          :archived?       (i18n/localize lang (if (= (:tila metadata) "arkistoitu") :yes :no))
          :modified        modified
          :modified-state  (ts->state modified)}))

(def columns [{:id    :application-id
               :title :application.id}
              {:id    :backend-id
               :title :verdict.id}
              {:id    :property-id
               :title :pate.property-id}
              {:id    :application-state
               :title :company.report.excel.header.state}
              {:id    :primary-operation
               :title :operations.primary}
              {:id    :tos-function
               :title :tos.function}
              {:id    :archiving-completed
               :title :archival-report.application.archived
               :type  :timestamp}
              {:id    :digitizer
               :title :digitizer.report.excel.header.digitizer}
              {:id    :archiver
               :title :archival-report.archiver}
              {:id    :filename
               :title :attachment.file}
              {:id    :contents
               :title :attachment.th-content}
              {:id    :attachment-type
               :title :attachment.chooseType}
              {:id    :archived?
               :title :digitizer.report.excel.header.archived}
              {:id    :modified
               :title :attachment.th-edited
               :type  :timestamp}
              {:id    :modified-state
               :title :archival-report.modified-state}])

(defn archival-report-data [{:keys [lang organization-id] :as options}]
  (->> (applications options)
       (mapcat (fn [application]
                 (let [options (add-application-options {:lang            lang
                                                         :organization-id organization-id}
                                                        application)]
                   (->> (:attachments application)
                        (filter :latestVersion)
                        (map (comp #(select-keys % (map :id columns))
                                   (partial add-attachment-options options)))))))))

(defn- parse-command [{:keys [data lang]}]
  (let [opts (->> (select-keys data [:organizationId :startTs :endTs])
                  (util/map-keys ->kebab-case-keyword))]
    (-> opts
        (update :start-ts util/->long)
        (update :end-ts util/->long)
        (assoc :lang (if (some-> lang i18n/supported-lang?)
                       (keyword lang)
                       :fi)))))

(defn report-filename [command]
  (let [{:keys [lang organization-id]} (parse-command command)]
    (i18n/localize-and-fill lang :archival-report.filename
                           organization-id
                           (date/finnish-date (now) :zero-pad))))

(defn write-header-row [worksheet lang]
  (doseq [i (range (count columns))]
    (->> (nth columns i)
         :title
         (i18n/localize lang)
         (.value worksheet 0 i)))
  (-> (.range worksheet 0 0 0 (count columns))
      (.style)
        (.bold)
        (.borderStyle BorderSide/BOTTOM BorderStyle/THIN)
        (.set)))

(defn write-date-cell [worksheet r c timestamp]
  (->> (Timestamp. timestamp)
       .toLocalDateTime
       .toLocalDate
       (.value worksheet r c ))
  (-> (.style worksheet r c)
      (.format "D.M.yyyy")
      .set))

(defn write-row [worksheet r row-data]
  (doall (map-indexed (fn [c {:keys [id type]}]
                        (when-let [v (row-data id)]
                          (case type
                            :timestamp (write-date-cell worksheet r c v)
                            (.value worksheet r c v))))
                      columns)))

(defn write-report [command output-stream]
  (let [{:keys [organization-id lang]
         :as   options} (parse-command command)
        org-name      (get-in (mongo/by-id :organizations organization-id [:name])
                               [:name lang] organization-id)
        wb            (Workbook. output-stream "Lupapiste" "1.0")
        ws            (.newWorksheet wb org-name)]
    (write-header-row ws lang)
    (loop [[x & xs] (archival-report-data options)
           i        1]
      (when (zero? (rem i 5000))
        (.flush ws))
      (if x
        (do (write-row ws i x)
            (recur xs (inc i)))
        (.finish wb)))))
