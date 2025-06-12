(ns lupapalvelu.appeal-verdict
  (:require [lupapalvelu.appeal-schema :as appeal-schema]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :refer [defschema] :as sc]))

(defschema AppealVerdict
  "Schema for an appeal verdict. Appeal verdict is given for
   an appeal(s) that is that is made from a verdict or
   appeal verdict given earlier."
  {:id                         ssc/ObjectIdStr
   :target-verdict             ssc/ObjectIdStr   ;; refers to verdicts.paatokset.id
   :giver                      sc/Str            ;; Name for board or court which gave the verdict
   :datestamp                  ssc/Timestamp     ;; Date of appeal made - defined manually by authority
   (sc/optional-key :text)     sc/Str            ;; Optional description
   })

(defschema FrontendAppealVerdict
  "Schema of AppealVerdict presentation for frontend"
  (assoc AppealVerdict
         :editable sc/Bool
         :type (sc/eq "appealVerdict")
         :files [appeal-schema/FrontendAppealFile]))

(defn create-appeal-verdict [target-verdict-id giver datestamp text]
  (util/strip-nils
    {:id                  (mongo/create-id)
     :target-verdict      target-verdict-id
     :giver               giver
     :datestamp           datestamp
     :text                text}))

(def appeal-verdict-checker (sc/checker AppealVerdict))

(defn appeal-verdict-data-for-upsert
  "'Dispatcher' function for appeal verdict data.
   If appealVerdictId is given as last parameter, returns validated update data without id.
   If appealVerdictId is not given, returns validated appeal verdict data with generated id"
  [target-verdict-id giver datestamp text & [appealVerdictId]]
  (if appealVerdictId
    (let [update-data (dissoc (create-appeal-verdict target-verdict-id giver datestamp text) :id)]
      (when-not (appeal-verdict-checker (assoc update-data :id appealVerdictId))
        (dissoc update-data :target-verdict)))
    (let [new-data (create-appeal-verdict target-verdict-id giver datestamp text)]
      (when-not (appeal-verdict-checker new-data)
        new-data))))
