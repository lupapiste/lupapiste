(ns lupapalvelu.main
  (:require [lupapalvelu.logging :as log :refer [debugf]]
            [sade.env :as env])
  (:gen-class))

(def services
  {"server"                            'lupapalvelu.server/-main
   "mongo-watcher"                     'lupapalvelu.mongo-watcher.core/-main
   "migration"                         'lupapalvelu.migration.main/-main
   "update-poi"                        'lupapalvelu.mml.update-poi/-main
   "smoketest"                         'lupapalvelu.smoketest.lupamonster/-main
   "reminders"                         'lupapalvelu.batchrun/send-reminder-emails
   "fetch-allu-contracts"              'lupapalvelu.batchrun/fetch-allu-contracts
   "fetch-missing-allu-decisions"      'lupapalvelu.batchrun/fetch-missing-allu-decisions
   "fetch-allu-semiconstants"          'lupapalvelu.batchrun/fetch-allu-semiconstants
   "allu-resubmit"                     'lupapalvelu.batchrun/resubmit-allu-applications
   "check-verdicts"                    'lupapalvelu.batchrun/check-for-verdicts
   "check-foreman-verdicts"            'lupapalvelu.batchrun/check-for-foreman-verdicts
   "check-verdict-attachments"         'lupapalvelu.batchrun/check-for-verdict-attachments
   "check-verdict-attachments-for-ids" 'lupapalvelu.batchrun/check-for-verdict-attachments-for-ids
   "check-ah-verdicts"                 'lupapalvelu.batchrun/check-for-asianhallinta-messages
   "check-reviews"                     'lupapalvelu.batchrun/check-for-reviews
   "check-reviews-for-orgs"            'lupapalvelu.batchrun/check-reviews-for-orgs
   "check-reviews-for-ids"             'lupapalvelu.batchrun/check-reviews-for-ids
   "overwrite-reviews-for-orgs"        'lupapalvelu.batchrun/overwrite-reviews-for-orgs
   "fix-prev-permit-addresses"         'lupapalvelu.prev-permit/fix-prev-permit-addresses
   "pdfa-convert-review-pdfs"          'lupapalvelu.batchrun/pdfa-convert-review-pdfs
   "pdf-to-pdfa-conversion"            'lupapalvelu.batchrun/pdf-to-pdfa-conversion
   "cleanup-uploaded-files"            'lupapalvelu.storage.file-storage/delete-old-unlinked-files
   "unarchive"                         'lupapalvelu.batchrun/unarchive
   "fix-helsinki-pdfa"                 'lupapalvelu.batchrun/fix-bad-archival-conversions-in-091-R
   "fix-file-links"                    'lupapalvelu.batchrun/fix-file-links
   "archive-digitized-projects"        'lupapalvelu.batchrun/archive-digitized-projects-in-org
   "conversion"                        'lupapalvelu.batchrun/conversion-router
   "other"                             'lupapalvelu.batchrun/other-router
   "force-reporting-db-update"         'lupapalvelu.exports.reporting-db/force-reporting-db-update
   "rescue-orphaned-files"             'lupapalvelu.batchrun/rescue-orphaned-files
   "vantaa-statements"                 'lupapalvelu.batchrun/vantaa-statements-from-excel
   "publish-scheduled-verdicts"        'lupapalvelu.batchrun/scheduled-verdict-publishing
   "kuntagml-export"                   'lupapalvelu.batchrun/kuntagml-export-run
   "attachments-export"                'lupapalvelu.batchrun/attachments-export-batchrun
   "review-cleanup"                    'lupapalvelu.batchrun/review-cleanup
   "fix-verdict-dates"                 'lupapalvelu.batchrun/fix-verdict-dates
   "generate-neighbor-attachments"     'lupapalvelu.batchrun/generate-missing-neighbor-attachments
   "fix-missing-statements"            'lupapalvelu.batchrun/fix-missing-statements
   "update-ad-login-certs"             'lupapalvelu.batchrun/update-ad-login-certs
   "pate-backend-ids"                  'lupapalvelu.batchrun/pate-backend-ids
   "push-building-data-to-onkalo"      'lupapalvelu.batchrun/push-building-data-to-onkalo
   "merge-organizations"               'lupapalvelu.batchrun/merge-org
   "rami-campaign"                     'lupapalvelu.batchrun/rami-campaign
   "process-notices"                   'lupapalvelu.batchrun/process-notices
   "process-statistics"                'lupapalvelu.batchrun/process-statistics})

(defn launch! [service args]
  (debugf "Resolved Lupapiste environment name: %s" env/target-env)
  (debugf "Loading namespace '%s'...\n" (namespace service))
  (require (symbol (namespace service)))
  (log/infof "Invoking service '%s': '%s'...\n" (str service) args)
  (apply (resolve service) args)
  (debugf "Service done!"))

(defn show-help []
  (log/info "usage: [-h] [-l] [<service> <args...>]")
  (log/info "  -h ........ Show this help")
  (log/info "  -l ........ List known services")
  (log/info "  service ... Name of service to start")
  (log/info "  args ...... Possible arguments for service"))

(defn list-services []
  (log/info "available services:")
  (log/info "\tservice:                       symbol:")
  (log/info "\t--------------------------------------")
  (doseq [[n s] (->> services (sort-by second))]
    (log/infof "\t%-30s %s" n (str s)))
  (flush))

(defn rtfm []
  (log/warn "What? I don't even...\n")
  (show-help))

(defn -main [& [f & args]]
  (cond
    (= f "-l") (list-services)
    (= f "-h") (show-help)
    (services f) (launch! (services f) args)
    :else (rtfm)))
