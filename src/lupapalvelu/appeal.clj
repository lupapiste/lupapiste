(ns lupapalvelu.appeal
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]
            [sade.util :as util]))

(defschema Appeal
  "Schema for a verdict appeal."
  {:id                         ssc/ObjectIdStr
   :target-verdict             ssc/ObjectIdStr         ;; refers to verdicts.paatokset.id
   :type                       (sc/enum
                                 "appeal"              ;; Valitus
                                 "rectification"       ;; Oikasuvaatimus
                                 )
   :appellant                  sc/Str                  ;; Name of the person who made the appeal
   :made                       ssc/Timestamp           ;; Date of appeal made - defined manually by authority
   (sc/optional-key :text)     sc/Str                  ;; Optional description
   })

(defn create-appeal [target-verdict-id type appellant made text]
  (util/strip-nils
    {:id                  (mongo/create-id)
     :target-verdict      target-verdict-id
     :type                type
     :appellant           appellant
     :made                made
     :text                text}))

(defn new-appeal-mongo-updates
  "Returns $push mongo update map of appeal to :appeals property"
  [target-verdict-id type appellant made text]
  (when-let [appeal (->> (create-appeal target-verdict-id type appellant made text)
                         (sc/check Appeal))]
    {$push {:appeals appeal}}))
