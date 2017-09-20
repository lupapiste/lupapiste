(ns lupapalvelu.application-schema
  (:require [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]
            [sade.validators :as validators]
            [lupapalvelu.states :as states]
            [lupapalvelu.permit :as permit]))

(defschema ApplicationId
  (sc/constrained sc/Str validators/application-id? "Application id"))


(defschema Operation
  {:id                            ssc/ObjectIdStr
   :name                          sc/Str
   :created                       ssc/Timestamp
   (sc/optional-key :description) sc/Str})

(defschema Application                                      ; WIP, used initially in MATTI state-change JSON
  {:id             ApplicationId
   :operations     [Operation]
   :propertyId     sc/Str
   :municipality   sc/Str
   :location       [sc/Num sc/Num]
   :location-wgs84 [sc/Num sc/Num]
   :address        sc/Str
   :state          (apply sc/enum (map name states/all-states))
   :permitType     (apply sc/enum (map name (keys (permit/permit-types))))
   :applicant      sc/Str
   :infoRequest    sc/Bool})
