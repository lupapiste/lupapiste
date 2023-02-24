(ns lupapalvelu.pate.verdict-date
  "Updates application verdictDate and deadlines fields. Separate namespace due to
  various dependencies."
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [monger.operators :refer :all]
            [sade.util :as util]))

(defn make-update [k v]
  (if (nil? v)
    {$unset {k true}}
    {$set {k v}}))

(defn verdictDate-update
  "Mongo update (`$set`/`$unset`) for `verdictDate`. The date is taken from the _latest_ published
 verdict. Replaced verdicts are ignored."
  [application]
  (make-update :verdictDate (vif/latest-published-verdict-date application)))

(defn deadlines-update
  "Mongo update (`$set`/`$unset`) for `deadlines` (`aloitettava`, `voimassa`).  The date is taken from
  the _earliest_ published verdict. Replaced verdicts are ignored."
  [application]
  (make-update :deadlines
               (some-> (vif/earliest-published-verdict {:application application})
                       vc/verdict-dates
                       (select-keys [:aloitettava :voimassa])
                       util/strip-nils
                       not-empty)))

(defn update-verdict-date
  "Updates application `verdictDate` and `deadlines`. Can be called as a command post function."
  ([application-id]
   (when-let [app (mongo/by-id :applications
                               application-id
                               [:verdicts :pate-verdicts])]
     (mongo/update-by-id :applications
                         application-id
                         (util/deep-merge (verdictDate-update app)
                                          (deadlines-update app)))))
  ([command _]
   (update-verdict-date (some-> command :application :id))))
