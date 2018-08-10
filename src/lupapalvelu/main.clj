(ns lupapalvelu.main
  (:require [lupapalvelu.logging]
            [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]])
  (:gen-class))

(def services {"server"                     'lupapalvelu.server/-main
               "migration"                  'lupapalvelu.migration.migration/-main
               "update-poi"                 'lupapalvelu.mml.update-poi/-main
               "smoketest"                  'lupapalvelu.smoketest.lupamonster/-main
               "reminders"                  'lupapalvelu.batchrun/send-reminder-emails
               "check-verdicts"             'lupapalvelu.batchrun/check-for-verdicts
               "check-verdict-attachments"  'lupapalvelu.batchrun/check-for-verdict-attachments
               "check-ah-verdicts"          'lupapalvelu.batchrun/check-for-asianhallinta-messages
               "check-reviews"              'lupapalvelu.batchrun/check-for-reviews
               "check-reviews-for-orgs"     'lupapalvelu.batchrun/check-reviews-for-orgs
               "check-reviews-for-ids"      'lupapalvelu.batchrun/check-reviews-for-ids
               "overwrite-reviews-for-orgs" 'lupapalvelu.batchrun/overwrite-reviews-for-orgs
               "extend-previous-permit"     'lupapalvelu.batchrun/extend-previous-permit
               "fix-prev-permit-addresses"  'lupapalvelu.prev-permit/fix-prev-permit-addresses
               "fix-prev-permit-applicants" 'lupapalvelu.prev-permit/fix-prev-permit-applicants
               "pdfa-convert-review-pdfs"   'lupapalvelu.batchrun/pdfa-convert-review-pdfs
               "pdf-to-pdfa-conversion"     'lupapalvelu.batchrun/pdf-to-pdfa-conversion
               "cleanup-uploaded-files"     'lupapalvelu.storage.file-storage/delete-old-unlinked-files
               "unarchive"                  'lupapalvelu.batchrun/unarchive
               "fix-helsinki-pdfa"          'lupapalvelu.batchrun/fix-bad-archival-conversions-in-091-R})

(defn launch! [service args]
  (debugf "Loading namespace '%s'...\n" (namespace service))
  (require (symbol (namespace service)))
  (debugf "Invoking service '%s'...\n" (str service))
  (apply (resolve service) args))

(defn show-help []
  (println "usage: [-h] [-l] [<service> <args...>]")
  (println "  -h ........ Show this help")
  (println "  -l ........ List known services")
  (println "  service ... Name of service to start")
  (println "  args ...... Possible arguments for service"))

(defn list-services []
  (println "available services:")
  (printf "\tservice:                    symbol:\n")
  (printf "\t-----------------------------------\n")
  (doseq [[n s] services]
    (printf "\t%-27s %s\n" n (str s)))
  (flush))

(defn rtfm []
  (println "What? I don't even...\n")
  (show-help))

(defn -main [& [f & args]]
  (cond
    (= f "-l")    (list-services)
    (= f "-h")    (show-help)
    (services f)  (launch! (services f) args)
    :else         (rtfm)))
