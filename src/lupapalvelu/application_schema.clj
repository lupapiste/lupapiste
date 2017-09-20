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
   (sc/optional-key :description) (sc/maybe sc/Str)})

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

(def permitSubtypes
  ["tyonjohtaja-ilmoitus"
   "tyonjohtaja-hakemus"
   "tyolupa"
   "suunnittelutarveratkaisu"
   "sijoitussopimus"
   "sijoituslupa"
   "poikkeamislupa"
   "muutoslupa"
   "kayttolupa"
   ""
   nil])

(defschema PermitSubtype
  (apply sc/enum permitSubtypes))

(defschema Foreman
  "Full name as a string, e.g. \"Korhonen Marko Harri Ilmari\". The foreman is a
non empty string only on applications where permitSubtype is one of
\"tyonjohtaja-ilmoitus\", \"tyonjohtaja-hakemus\", \"muutoslupa\", \"\" or null"
  sc/Str)

(defschema OpenInfoRequest
  "Only 273 applications with openInfoRequest = null"
  (sc/maybe sc/Bool))

(defschema Municipality
  "Municipality is a three digit string, e.g. \"092\""
  (sc/constrained sc/Str (fn [a-str] (boolean (re-matches #"\d\d\d" a-str)))))

(defschema PrimaryOperation
  ""
  sc/Any)

(defschema InfoRequest
  sc/Bool)

(defschema Organization
  "Organization id is usually of the format nnn-X where nnn is the municipality
id, and X is a suffix depending on the specific municipalitys organization.
For example, 092-R is Vantaa's rakennusvalvonta. There are exceptions that don't
use the municipality number, e.g. \"keskiuusimaa-YMP\""
  (sc/constrained sc/Str (comp not empty?) "Organization id"))

(def permit-types
  #{"ARK" "KT" "MAL" "MM" "P" "R" "VVVL" "YA" "YI" "YL" "YM"})

(defschema PermitType
  (apply sc/enum permit-types))

(def states
  #{"acknowledged"
    "agreementPrepared"
    "agreementSigned"
    "answered"
    "appealed"
    "archived"
    "canceled"
    "closed"
    "complementNeeded"
    "constructionStarted"
    "draft"
    "extinct"
    "finished"
    "foremanVerdictGiven"
    "inUse"
    "info"
    "onHold"
    "open"
    "sent"
    "submitted"
    "underReview"
    "verdictGiven"})

(defschema State
  (apply sc/enum State))

(def urgencies #{"normal", "urgent", "pending"})

(defschema Urgency
  (apply sc/enum urgencies))

(defschema Title
  "Title of an application, usually an address."
  (sc/constrained sc/Str (comp not empty) "Application Title"))
>>>>>>> Add schemas for some fields of Application
