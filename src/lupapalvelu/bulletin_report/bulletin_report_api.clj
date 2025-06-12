(ns lupapalvelu.bulletin-report.bulletin-report-api
  "API for generating PDF and Excel bulletin reports."
  (:require [lupapalvelu.action :refer [defquery defraw]]
            [lupapalvelu.application-bulletin-utils :as bulletin-utils]
            [lupapalvelu.bulletin-report.core :as report]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pageutil :refer [try-again-page]]
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
       (mapv #(bulletin-utils/bulletin-list-item % lang))
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
  [{:keys [created system] :as command}]
  (let [{:keys [pdf-file-stream
                text
                filename-prefix]} (some-> (bulletins :versions.application-id id
                                                     :_id bulletinId)
                                          first
                                          (report/pdf-report system {:lang lang}))]
    (cond
      pdf-file-stream
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "filename=\"%s %s %s.pdf\""
                                               filename-prefix
                                               id
                                               (date/finnish-date created :zero-pad))}
       :body    pdf-file-stream}

      text
      (try-again-page command {:raw    :bulletin-report-pdf
                               :status 503 ;; Service Unavailable
                               :error  text})

      :else
      (fail :error.bulletin.not-found))))
