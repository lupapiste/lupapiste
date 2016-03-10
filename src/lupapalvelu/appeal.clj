(ns lupapalvelu.appeal
  (:require [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema Appeal
  "Schema for a verdict appeal."
  {:id            ssc/ObjectIdStr       ;; 
   :paatos-id     ssc/ObjectIdStr       ;; Refers to verdicts.paatokset.id
   :appellant     {:firstName  sc/Str   ;; Person who made the appeal
                   :lastName   sc/Str}  ;;
   :made          ssc/Timestamp         ;; Date of appeal made - defined manually by authority
   :created       ssc/Timestamp})
