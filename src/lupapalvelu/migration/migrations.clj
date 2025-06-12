(ns lupapalvelu.migration.migrations
  (:require [monger.operators :refer :all]
            [sade.core :refer [now]]))

(set! *warn-on-reflection* true)

;; Utility functions

(defn push-note
  "See also `lupapalvelu.conversion.util/with-sheriff-note`."
  ([text timestamp]
   {$push {:_sheriff-notes {:note    text
                            :created timestamp}}})
  ([text]
   (push-note text (now))))


;; ****** NOTE! ******
;;  1) When you are writing a new migration that goes through subcollections
;;     in the collections "Applications" and "Submitted-applications"
;;     do not manually write like this
;;       (doseq [collection [:applications :submitted-applications] ...)
;;     but use the "update-applications-array" function existing in this namespace.
;;
;;  2) Check if migration needs to be done for "bulletins" (julkipano/kuulutus) also. See update-bulletin-versions.
;;
;; *******************
