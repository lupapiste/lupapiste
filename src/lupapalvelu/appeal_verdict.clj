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
   :made                       ssc/Timestamp})  ;; Date of appeal verdict given - defined manually by authority

(defn create-appeal-verdict [target-verdict-id giver made]
  {:id             (mongo/create-id)
   :target-verdict target-verdict-id
   :giver          giver
   :made           made})

(defn new-appeal-verdict-mongo-updates
  [target-verdict-id giver made]
  (when-let [appeal-verdict (create-appeal-verdict target-verdict-id giver made)]
    {$push {:appealVerdicts appeal-verdict}}))

