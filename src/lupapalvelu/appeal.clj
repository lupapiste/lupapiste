(ns lupapalvelu.appeal
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema Appeal
  "Schema for a verdict appeal."
  {:id                         ssc/ObjectIdStr  ;; 
   :target-verdict             ssc/ObjectIdStr  ;; refers to verdicts.paatokset.id
   :appellant                  sc/Str           ;; Name of the person who made the appeal
   :made                       ssc/Timestamp})  ;; Date of appeal made - defined manually by authority

(defn create-appeal [target-verdict-id appellant made]
  {:id             (mongo/create-id)
   :target-verdict target-verdict-id
   :appellant      appellant
   :made           made})

(defn new-appeal-mongo-updates [target-verdict-id appellant made now]
  (when-let [appeal (->> (create-appeal target-verdict-id appellant made now)
                         (sc/check Appeal))]
    {$push {:appeals appeal}}))
