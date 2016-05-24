(ns lupapalvelu.main
  (:require [lupapalvelu.logging]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]])
  (:gen-class))

(def services {"server"                     'lupapalvelu.server/-main
               "migration"                  'lupapalvelu.migration.migration/-main
               "update-poi"                 'lupapalvelu.mml.update-poi/-main
               "smoketest"                  'lupapalvelu.smoketest.lupamonster/-main
               "reminders"                  'lupapalvelu.batchrun/send-reminder-emails
               "check-verdicts"             'lupapalvelu.batchrun/check-for-verdicts
               "check-ah-verdicts"          'lupapalvelu.batchrun/check-for-asianhallinta-verdicts
               "check-reviews"              'lupapalvelu.batchrun/check-for-reviews
               "check-review-for-id"              'lupapalvelu.batchrun/check-review-for-id
               "fix-prev-permit-addresses"  'lupapalvelu.prev-permit/fix-prev-permit-addresses
               "fix-prev-permit-applicants" 'lupapalvelu.prev-permit/fix-prev-permit-applicants
               "cleanup-uploaded-files"     'lupapalvelu.file-upload/cleanup-uploaded-files})

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
