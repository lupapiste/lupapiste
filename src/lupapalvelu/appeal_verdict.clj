(ns lupapalvelu.appeal-verdict
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]
            [sade.util :as util]))

(defschema AppealVerdict
  "Schema for an appeal verdict. Appeal verdict is given for
   an appeal(s) that is that is made from a verdict or 
   appeal verdict given earlier."
  {:id                         ssc/ObjectIdStr
   :target-verdict             ssc/ObjectIdStr   ;; refers to verdicts.paatokset.id
   :giver                      sc/Str            ;; Name for board or court which gave the verdict
   :made                       ssc/Timestamp     ;; Date of appeal made - defined manually by authority
   (sc/optional-key :text)     sc/Str            ;; Optional description
   })

(defn create-appeal-verdict [target-verdict-id giver made text]
  (util/strip-nils
    {:id                  (mongo/create-id)
     :target-verdict      target-verdict-id
     :giver               giver
     :made                made
     :text                text}))

(defn new-appeal-verdict-mongo-updates
  "Returns $push mongo update map of appeal verdict to :appealVerdicts property"
  [target-verdict-id giver made text]
  (let [appeal-verdict (create-appeal-verdict target-verdict-id giver made text)]
    (when-not (sc/check AppealVerdict appeal-verdict)
      {$push {:appealVerdicts appeal-verdict}})))

