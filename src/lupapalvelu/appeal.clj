(ns lupapalvelu.appeal
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema Appeal
  "Schema for a verdict appeal."
  {:id                         ssc/ObjectIdStr  ;; 
   :target                     {:type   (sc/enum :verdict :appeal-verdict)
                                :id     ssc/ObjectIdStr}
   (sc/optional-key :decision) ssc/ObjectIdStr  ;; Decision for appeal made by court or commitee
   :appellant                  sc/Str           ;; Name of the person who made the appeal
   :made                       ssc/Timestamp})  ;; Date of appeal made - defined manually by authority

(defn create-appeal-for-verdict [paatos-id appellant made]
  {:id        (mongo/create-id)
   :target    {:type :verdit
               :id    paatos-id}
   :appellant appellant
   :made      made})

(defn create-appeal-for-appeal-verdict [appeal-verdict-id appellant made]
  {:id        (mongo/create-id)
   :target    {:type :appeal-verdit
               :id   appeal-verdict-id}
   :appellant appellant
   :made      made})

(defn appeal-decision-updates [target-verdict-id decisive-verdict-id]
  {:query   {:appeals.target.id target-verdict-id}
   :updates {$set {:appeals.$.decision decisive-verdict-id}}})

(defn new-appeal-for-verdict-mongo-updates [paatos-id appellant made now]
  (when-let [appeal (->> (create-appeal-for-verdict paatos-id appellant made now)
                         (sc/check Appeal))]
    {$push {:appeals appeal}}))
