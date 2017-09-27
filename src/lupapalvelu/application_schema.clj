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
  (sc/named sc/Str 'Foreman))

(defschema OpenInfoRequest
  "Only 273 applications with openInfoRequest = null.
TODO: document"
  (sc/maybe sc/Bool))

(defschema InfoRequest
  "TODO: document"
  (sc/named sc/Bool 'InfoRequest))

(defschema Municipality
  "Municipality is a three digit string, e.g. \"092\""
  (sc/constrained sc/Str (fn [a-str] (boolean (re-matches #"\d\d\d" a-str)))))

(defschema PrimaryOperation
  "TODO: need schema and documentation for this. Name of operation is an enum"
  sc/Any)

(defschema SecondaryOperation
  "TODO: need schema and documentation for this. Name of operation is an enum"
  sc/Any)

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
  (apply sc/enum states))

(def urgencies #{"normal", "urgent", "pending"})

(defschema Urgency
  (apply sc/enum urgencies))

(defschema Title
  "Title of an application, usually an address."
  (sc/named ssc/NonBlankStr 'Title))

(defschema Address
  "TODO: document. What is the difference between this and address?"
  (sc/named ssc/NonBlankStr 'Address))

(defschema Started
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'Started))

(defschema Opened
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'Opened))

(defschema Closed
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'Closed))

(defschema Created
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'Created))

(defschema Submitted
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'Submitted))

(defschema ReminderSent
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'ReminderSent))

(defschema ConvertedToApplication
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'ConvertedToApplication))

(defschema Sent
  "TODO: document. When is this null? Can it be changed once set? If yes,
when/why."
  (sc/named
    (sc/maybe ssc/Timestamp)
    'Sent))

(defschema PropertyId
  ssc/Kiinteistotunnus)

(defschema Verdict
  "TODO: document and give schema"
  sc/Any)

(defschema Verdicts
  "TODO: document and give schema"
  [Verdict])

(defschema Authority
  "TODO: document. Username and role either both exist or neither does."
  {:firstName sc/Str
   :lastName sc/Str
   :id (sc/maybe ssc/ObjectIdStr)
   (sc/optional-key :role) (sc/eq "authority")
   (sc/optional-key :username) ssc/Username})

(defschema AuthorityNotice
  "TODO: document this. Only 2052 applications have this as null."
  (sc/maybe sc/Str))

(defschema Tasks
  "TODO: document and give schema."
  [sc/Any])

(defschema ClosedBy
  "TODO: document and give schema"
  {sc/Any sc/Any})

(defschema Applicant
  "TODO: 7 applications where applicant is empty string.
Seems to contain name of applicant, which can be a person's name, a company's
name or an asunto-osakeyhtiös name."
  (sc/named sc/Str 'Applicant))

(defschema ApplicantIndex
  "TODO: document and give schema"
  [sc/Any])

(defschema VerdictsSeenBy
  "TODO: document and give schema. Some kind of map {id id}?
There are 170 applications without the field '_verdicts-seen-by'"
  {sc/Any sc/Any})

(defschema CommentsSeenBy
  "TODO: document and give schema. Some kind of map {id id}?
There are 88 applications without the field '_comments-seen-by'"
  {sc/Any sc/Any})

(defschema Location
  "TODO: document."
  [double double])

(defschema Attachment
  "TODO: document and give schema.
Is this always a map?"
  sc/Any)

(defschema Attachments
  "TODO: document."
  [Attachment])

(defschema Statement
  "TODO: document and give schema.
Is this always a map?"
  sc/Any)

(defschema Statements
  "TODO: document."
  [Statement])

(defschema Building
  "TODO: document and give schema."
  sc/Any)

(defschema Buildings
  "TODO: document"
  [Building])

(defschema StartedBy
  "TODO: "
  {:firstName sc/Str
   :lastName sc/Str
   :id sc/Str})

(defschema BaseNeighborStatus
  {:created ssc/Timestamp
   :state sc/Str})

(defschema NeighborStatusOpen
  (merge BaseNeighborStatus
         {:state "open"}))

(defschema NeighborStatusEmailSent
  (merge BaseNeighborStatus
         {:state "email-sent"
          :email ssc/Email
          :token sc/Str ;;TODO: is there a better schema for tokens?
          :user (sc/maybe {sc/Any sc/Any}) ;;TODO: use a User schema here
          ;; when is this null?
          }))

(defschema NeighborStatusReminderSent
  (merge BaseNeighborStatus
         {:state "reminder-sent"
          :token sc/Str
          ;;TODO: token schema? Also, this should be the same token as in the
          ;;      relevant EmailSent status one
          }))

(defschema NeighborStatusResponseGiven
  (merge BaseNeighborStatus
         {:state "response-given"
          :user (sc/maybe {sc/Any sc/Any}) ;;TODO: use a User schema here
          ;; Need documentation on when this can be null
          :message ssc/NonBlankStr
          :vetuma {sc/Any sc/Any} ;; TODO: need a Vetuma schema here
          }))

(defn state= [val]
  (fn [a-map]
    (= val (:state a-map))))

(defschema NeighborStatus
  "TODO: "
  (sc/conditional (state= "open")
                  NeighborStatusOpen
                  (state= "email-sent")
                  NeighborStatusEmailSent
                  (state= "reminder-sent")
                  NeighborStatusReminderSent
                  (state= "response-given")
                  NeighborStatusResponseGiven))

(def neighbor-owner-types
  ["juridinen"
   "luonnollinen"
   "tuntematon"
   "kuolinpesa"
   "valtio"])

(defschema NeighborOwnerType
  (apply sc/enum neighbor-owner-types))

(defschema NeighborOwner
  "TODO: Document"
  {:type (sc/maybe NeighborOwnerType) ;;TODO: when is this null?
   ;; 1337 applications where at least one neighbor's owner's type is null
   :name ssc/NonBlankStr
   :businessID (sc/maybe ssc/FinnishY)
   ;; businessID can be null even when type is "juridinen"
   ;; 1745 applications with a neighbor whose owner has type "juridinen" and
   ;; businessID null
   :nameOfDeceased sc/Any ;;TODO: schema needed
   :address {sc/Any sc/Any} ;;TODO: need schema for address maps
   :email (sc/maybe ssc/Email)
   })

(defschema Neighbor
  "TODO: document"
  {:id ssc/ObjectIdStr
   :propertyId ssc/ObjectIdStr
   :owner NeighborOwner
   :status [NeighborStatus]})

(defschema Neighbors
  "TODO: Document and give schema."
  [Neighbor])

(def auth-roles
  ["owner" "reader" "statementGiver" "guest" "writer" "foreman" "guestAuthority"])

(defschema AuthRole
  (apply sc/enum auth-roles))

(def auth-types ["owner" "company"])

(defschema AuthType
  (apply sc/enum auth-types))

(def auth-inviter-roles
  "TODO: this is probably available somewhere in the codebase already?"
  ["applicant" "authority" "rest-api"])

(defschema AuthInviterRole
  (apply sc/enum auth-inviter-roles))

(defschema AuthInviterMap
  "TODO: this schema is likely a duplicate"
  {:role AuthInviterRole
   :lastName ssc/NonBlankStr ;;verify existence, nonBlankness and nonNullness
   :firstName ssc/NonBlankStr ;;verify existence, nonNullness and nonBlankness
   :username ssc/Username ;; verify existence and nonNullness
   :id ssc/ObjectIdStr ;; verify existence, and nonNullness
   })

(defschema AuthInviterId
  "Auth inviter can sometimes just be an id string"
  (sc/named
    ssc/ObjectIdStr
    'AuthInviterById))

(defschema AuthInviter
  (sc/cond-pre
    AuthInviterMap
    AuthInviterId))

(defschema AuthInviteInviter
  "TODO: similar to other schemas describing some person"
  {:id ssc/ObjectIdStr
   :username ssc/Username
   :firstName sc/Str ;; NonBlank? Allways exist?
   :lastName sc/Str ;; NonBlank? Allways exist?
   :role (sc/enum ["authority" "applicant" "rest-api"])
   })

(defschema AuthInvite
  "TODO: document"
  {:text ssc/NonBlankStr
   :user {sc/Any sc/Any} ;;TODO: give schema
   :inviter {sc/Any sc/Any} ;;TODO: give schema
   :created ssc/Timestamp
   :email ssc/Email
   :path sc/Str ;; what is this? Can be empty
   :role (sc/enum ["writer" "foreman" "reader"])
   })

(defschema BaseAuthElement
  "firstName and lastName can be empty, even for type owner"
  {:username ssc/Username
   :firstName sc/Str
   :lastName sc/Str
   :role AuthRole
   (sc/optional-key :type) AuthType
   :id sc/Str ;; 22 applications where an auth has empty string as id
   (sc/optional-key :inviter) AuthInviter
   ;; if :type is "owner", then :inviter never exists
   ;; this should be encoded in the schema
   (sc/optional-key :invite) AuthInvite
   (sc/optional-key :unsubscribed) sc/Bool ;; document
   (sc/optional-key :statementId) ssc/ObjectIdStr ;; document
   (sc/optional-key :inviteAccepted) ssc/Timestamp
   ;; inviteAccepted can exists even when there is no inviter or invite
   })

(defschema CompanyAuthName
  "Company's name"
  (sc/named ssc/NonBlankStr 'CompanyName))

(defschema CompanyAuthElement
  "A Company always has a name, y-tunnus and its type is company. The name is always
exactly the same as firstName."
  (merge BaseAuthElement
         {:name CompanyAuthName
          :type (sc/eq "company")
          :y ssc/FinnishY}))

(defschema AuthElement
  "TODO: document"
  sc/Any ;;TODO: turn into conditional
  )

(defschema Auth
  "TODO: "
  [AuthElement])
