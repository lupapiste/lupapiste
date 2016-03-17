(ns lupapalvelu.appeal-verdict
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema AppealVerdict
  "Schema for an appeal verdict. Appeal verdict is given for
   an appeal(s) that is that is made from a verdict or 
   appeal verdict given earlier."
  {:id                         ssc/ObjectIdStr  ;; 
   :target-verdict             ssc/ObjectIdStr  ;; refers to verdicts.paatokset.id
   :giver                      sc/Str           ;; Name for board or court which gave the verdict
   :made                       ssc/Timestamp    ;; Date of appeal made - defined manually by authority
   :created                    ssc/Timestamp    ;; Entry creation time
   })

(defn create-appeal-verdict [target-verdict-id giver made created]
  {:id             (mongo/create-id)
   :target-verdict target-verdict-id
   :giver          giver
   :made           made
   :created        created})

(defn new-appeal-verdict-mongo-updates
  "Returns $push mongo update map of appeal verdict to :appealVerdicts property"
  [target-verdict-id giver made now]
  (when-let [appeal-verdict (create-appeal-verdict target-verdict-id giver made now)]
    {$push {:appealVerdicts appeal-verdict}}))

