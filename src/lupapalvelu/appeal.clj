(ns lupapalvelu.appeal
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema Appeal
  "Schema for a verdict appeal."
  {:id             ssc/ObjectIdStr                          ;;
   :target-verdict ssc/ObjectIdStr                          ;; refers to verdicts.paatokset.id
   :type           (sc/enum
                     "appeal"                               ;; Valitus
                     "rectification"                        ;; Oikasuvaatimus
                     )
   :appellant      sc/Str                                   ;; Name of the person who made the appeal
   :made           ssc/Timestamp                            ;; Date of appeal made - defined manually by authority
   :created        ssc/Timestamp                            ;; Entry creation time
   })

(defn create-appeal [target-verdict-id type appellant made created]
  {:id             (mongo/create-id)
   :target-verdict target-verdict-id
   :type           type
   :appellant      appellant
   :made           made
   :created        created})

(defn new-appeal-mongo-updates
  "Returns $push mongo update map of appeal to :appeals property"
  [target-verdict-id type appellant made now]
  (when-let [appeal (->> (create-appeal target-verdict-id type appellant made now)
                         (sc/check Appeal))]
    {$push {:appeals appeal}}))
