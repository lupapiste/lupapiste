(ns lupapalvelu.appeal
  (:require [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]
            [sade.util :as util]))

(def appeal-types
  "appeal = Valitus, rectification = Oikaisuvaatimus"
  ["appeal" "rectification"])

(defschema Appeal
  "Schema for a verdict appeal."
  {:id                         ssc/ObjectIdStr
   :target-verdict             ssc/ObjectIdStr         ;; refers to verdicts.paatokset.id
   :type                       (apply sc/enum appeal-types)
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
  (let [appeal (create-appeal target-verdict-id type appellant made text)]
    (when-not (sc/check Appeal appeal)
      {$push {:appeals appeal}})))

(defn input-validator
  "Input validator for appeal commands. Validates command parameter against Appeal schema."
  [{{:keys [targetId type appellant made text]} :data}]
  (when (sc/check (dissoc Appeal :id)
                  (util/strip-nils {:target-verdict targetId
                                    :type type
                                    :appellant appellant
                                    :made made
                                    :text text}))
    (fail :error.invalid-appeal)))
