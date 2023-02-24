(ns lupapalvelu.bulletin-report-api
  "API for generating PDF and Excel bulletin reports."
  (:require [lupapalvelu.action :refer [defraw defquery] :as action]
            [lupapalvelu.bulletin-report.core :as report]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict :refer [try-again-page]]
            [lupapalvelu.states :as states]
            [sade.core :refer [fail ok]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(def permissions [{:context  {:application {:state      states/post-verdict-states
                                            :permitType #{:R :P}}}
                   :required [:application/read]}])

(defn bulletins [& kvs]
  (some->> (apply hash-map kvs)
           (mongo/select :application-bulletins)
           seq))

(defn bulletins-enabled
  "Pre-check that fails if the bulletins are not enabled for the permit type in the
  organization."
  [{:keys [organization application]}]
  (when-let [permit-type (:permitType application)]
    (when-not (some->> (force organization)
                       :scope
                       (util/find-by-key :permitType permit-type)
                       :bulletins
                       :enabled)
      (fail :error.bulletins-not-enabled-for-scope))))

(defquery verdict-bulletins
  {:description      "List of bulletins for application verdicts. Includes both active and
  finished bulletins."
   :permissions      permissions
   :parameters       [id]
   :input-validators [{:id        ssc/ApplicationId
                       sc/Keyword sc/Any}]
   :pre-checks       [bulletins-enabled]}
  [{lang :lang}]
  (->> (bulletins :versions.application-id id)
       (map (fn [bulletin]
              (-> (report/bulletin-info bulletin lang)
                  (select-keys [:id :section :start-date :end-date])
                  (update :start-date date/timestamp)
                  (update :end-date date/timestamp))))
       (ok :bulletins)))

(defraw bulletin-report-pdf
  {:description      "PDF report for the given bulletin."
   :permissions      permissions
   :parameters       [id bulletinId lang]
   :input-validators [{:id         ssc/ApplicationId
                       :bulletinId ssc/NonBlankStr
                       :lang       i18n/Lang
                       sc/Keyword  sc/Any}]
   :pre-checks       [bulletins-enabled]}
  [{:keys [created] :as command}]
  (let [{:keys [pdf-file-stream
                text]} (some-> (bulletins :versions.application-id id
                                          :_id bulletinId)
                               first
                               (report/pdf-report {:lang lang}))]
    (cond
      pdf-file-stream
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "filename=\"%s %s %s.pdf\""
                                               (i18n/localize lang :bulletin-report.report)
                                               id
                                               (date/finnish-date created :zero-pad))}
       :body    pdf-file-stream}

      text
      (try-again-page command {:raw    :bulletin-report-pdf
                               :status 503 ;; Service Unavailable
                               :error  text})

      :else
      (fail :error.bulletin.not-found))))
