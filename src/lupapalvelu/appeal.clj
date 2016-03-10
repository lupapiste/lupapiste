(ns lupapalvelu.appeal
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema Appeal
  "Schema for a verdict appeal."
  {:id            ssc/ObjectIdStr       ;; 
   :paatos-id     ssc/ObjectIdStr       ;; Refers to verdicts.paatokset.id
   :appellant     {:firstName  sc/Str   ;; Person who made the appeal
                   :lastName   sc/Str}  ;;
   :made          ssc/Timestamp         ;; Date of appeal made - defined manually by authority
   :created       ssc/Timestamp})

(defn create-appeal [paatos-id appellant made now]
  {:id        (mongo/create-id)
   :paatos-id paatos-id
   :appellant appellant
   :made      made
   :created   now})

(defn new-appeal-mongo-updates [paatos-id appellant made now]
  (when-let [appeal (->> (create-appeal paatos-id appellant made now)
                         (sc/check Appeal))]
    {$push {:appeals appeal}}))
