(ns lupapalvelu.application-schema
  (:require [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]
            [lupapalvelu.states :as states]
            [lupapalvelu.permit :as permit]))

(defschema Operation
  {:id                            ssc/ObjectIdStr
   :name                          sc/Str
   :created                       ssc/Timestamp
   (sc/optional-key :description) (sc/maybe sc/Str)})

(defschema Application                                      ; WIP, used initially in state-change JSON
  {:id             ssc/ApplicationId
   :operations     [Operation]
   :propertyId     sc/Str
   :municipality   sc/Str
   :location       [sc/Num sc/Num]
   :location-wgs84 [sc/Num sc/Num]
   :address        sc/Str
   :state          (apply sc/enum (map name states/all-states))
   :permitType     (apply sc/enum (map name (keys (permit/permit-types))))
   :permitSubtype  (sc/maybe sc/Str)
   ;; or (sc/maybe (->> (concat
   ;;                     (->> (permit/permit-types) vals (map :subtypes) flatten distinct)
   ;;                     (->> (vals op/operations) (map :subtypes) flatten distinct))
   ;;                    (distinct)
   ;;                    (apply sc/enum)))
   ;; but requiring lupapalvelu.operation results in dependency cycle...
   :applicant      (sc/maybe sc/Str)
   :infoRequest    sc/Bool})
