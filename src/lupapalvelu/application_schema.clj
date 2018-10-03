(ns lupapalvelu.application-schema
  (:require [schema.core :refer [defschema] :as sc]
            [sade.schemas :as ssc]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]))


(defschema Operation
  {:id                            ssc/ObjectIdStr
   :name                          sc/Str
   :created                       ssc/Timestamp
   (sc/optional-key :description) (sc/maybe sc/Str)})

(defschema Application                                      ; WIP, used initially in state-change JSON
  {:id                               ssc/ApplicationId
   :primaryOperation                 Operation
   :secondaryOperations              [Operation]
   :propertyId                       sc/Str
   :municipality                     sc/Str
   :location                         [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
   :location-wgs84                   [(sc/one sc/Num "X") (sc/one sc/Num "Y")]
   :address                          sc/Str
   :state                            (apply sc/enum (map name states/all-states))
   :permitType                       (apply sc/enum (map name (keys (permit/permit-types))))
   :permitSubtype                    (sc/maybe (->> (concat
                                                      (->> (permit/permit-types) vals (map :subtypes) flatten distinct)
                                                      (->> (vals op/operations) (map :subtypes) flatten distinct))
                                                    (distinct)
                                                    (apply sc/enum)))
   :applicant                        (sc/maybe sc/Str)
   :infoRequest                      sc/Bool
   (sc/optional-key :3dMapActivated) (sc/maybe sc/Num)
   })

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
  {:firstName                  sc/Str
   :lastName                   sc/Str
   :id                         (sc/maybe ssc/ObjectIdStr)
   (sc/optional-key :role)     (sc/eq "authority")
   (sc/optional-key :username) ssc/Username})

(defschema AuthorityNotice
  "TODO: document this. Only 2052 applications have this as null."
  (sc/maybe sc/Str))

(def task-states
  ["requires_user_action"
   "sent"
   "ok"
   "requires_authority_action"
   "faulty_review_task"])

(def task-schema-info-names
  "TODO: get these from somewhere else?"
  ["task-katselmus"
   "task-katselmus-backend"
   "task-lupamaarays"
   "task-vaadittu-tyonjohtaja"
   "task-katselmus-ya"])

(def task-schema-info-subtypes
  ["foreman" "review" "review-backend"])

(defschema TaskSchemaInfo
  "TODO: document.
What is the point of the fields with only one possible values?"
  {:type                               (sc/eq "task")
   :name                               (apply sc/enum task-schema-info-names)
   :order                              long
   :version                            (sc/eq 1)
   (sc/optional-key :subtype)          (apply sc/enum task-schema-info-subtypes) ;;why is this optional?
   (sc/optional-key :i18name)          (sc/eq "task-katselmus")
   (sc/optional-key :section-help)     (sc/eq "authority-fills")
   (sc/optional-key :i18nprefix)       (sc/eq "task-katselmus.katselmuksenLaji")
   (sc/optional-key :user-authz-roles) (sc/eq [])           ;; what is the point of this?
   })

(defschema BackgroundTaskSource
  {:type (sc/eq "background")})

(def normal-task-source-types
  ["verdict" "authority" "task"])

(defschema NormalTaskSource
  {:type (apply sc/enum normal-task-source-types)
   :id   ssc/ObjectIdStr})

(defschema TaskSource
  (sc/conditional (fn [x] (= "background"
                             (:type x)))
                  BackgroundTaskSource
                  (fn [x] true)
                  NormalTaskSource))

(defschema TaskAssignee
  "TODO: check if the keys either all appear or none? Can there be other fields?"
  {(sc/optional-key :id)        ssc/ObjectIdStr
   (sc/optional-key :firstName) ssc/NonBlankStr
   (sc/optional-key :lastName)  ssc/NonBlankStr})

(def task-data-katselmuksenLaji-values
  #{""
    "aloituskokous"
    "loppukatselmus"
    "muu katselmus"
    "osittainen loppukatselmus"
    "rakennekatselmus"
    "rakennuksen paikan merkitseminen"
    "rakennuksen paikan tarkastaminen"
    "pohjakatselmus"
    "ei tiedossa"
    "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus"
    "muu tarkastus"
    "Loppukatselmus"
    "Aloituskatselmus"
    "Muu valvontakäynti"})

(def task-data-katselmuksenLaji-numeric-values
  #{"0"
    "2"
    "6"
    "8"
    "9"
    "10"
    "12"
    "13"
    "14"
    "17"
    "18"
    "19"
    "20"
    "21"
    "22"
    "23"
    "24"
    "26"
    "27"
    "32"
    "37"
    "38"
    "39"
    "42"
    "43"
    "44"
    "47"
    "52"
    "53"
    "54"
    "55"
    "104"
    "105"
    "106"
    "107"
    "110"
    "114"
    "125"
    "134"
    "400"
    "401"})

(defschema TaskDataKatselmuksenLaji
  "NOTE: 318 applications have task data where katselmuksenLaji.value is a number string.
Many of these have null modified timestamps.
572 applications have task data where katselmuksenLaji.value is null.
Only 5 of the cases with value '' have a non null modified timestamp.
24 applications have task data where katselmuksenLaji.value is empty string ''.
All 24 of those with value '' have non null modified timestamp."
  {:value                      (sc/maybe
                                 (apply sc/enum
                                        (clojure.set/union
                                          task-data-katselmuksenLaji-values
                                          task-data-katselmuksenLaji-numeric-values)))
   ;; TODO:remove nulls, "" and numeric values?
   (sc/optional-key :modified) (sc/maybe ssc/Timestamp)
   ;;why both optional and nullable?
   })

(defschema TaskDataVaadittuLupaehtona
  {:value                      sc/Bool
   (sc/optional-key :modified) (sc/maybe ssc/Timestamp)     ;;why both optional and nullable?
   })

(defschema NatString
  (sc/pred (fn [s] (re-matches #"^0|[1-9]\d*$" s)) 'NatStringRegex))

(defschema TaskDataRakennusRakennus
  {:jarjestysnumero                    {:value NatString}
   :kiinttun                           {:value ssc/ObjectIdStr} ;; is this always ObjectIdStr?
   :rakennusnro                        {:value (sc/pred (partial re-matches #"^\d{3}$") 'TaskDataRakennusNro)}
   :valtakunnallinenNumero             {:value (sc/maybe NatString)}
   :kunnanSisainenPysyvaRakennusnumero {:value NatString}
   })

(def task-data-rakennus-tila-tila-values
  ["lopullinen" "osittainen" ""])

(defschema TaskDataRakennusTila
  {:tila                             {:value (sc/maybe
                                               (apply sc/enum task-data-rakennus-tila-tila-values))
                                      ;;allows nulls and empty string, are "lopullinen" and "osittainen" the only other possible values?
                                      }
   (sc/optional-key :kayttoonottava) {:value sc/Bool}})

(defschema TaskDataRakennus
  {NatString
   {:rakennus TaskDataRakennusRakennus
    :tila     TaskDataRakennusTila}})

(defschema TaskDataType1
  "TODO: this needs a better name"
  {:katselmuksenLaji            TaskDataKatselmuksenLaji
   :vaadittuLupaehtona          TaskDataVaadittuLupaehtona
   :rakennus                    TaskDataRakennus
   (sc/optional-key :katselmus) sc/Any                      ;;FIXME
   (sc/optional-key :muuTunnus) sc/Any                      ;; FIXME
   })

(defschema TaskDataType2
  "TODO: write subschemas, document and name"
  {:maarays                                       sc/Any
   (sc/optional-key :kuvaus)                      sc/Any
   (sc/optional-key :vaaditutErityissuunnitelmat) sc/Any})

(defschema TaskDataType3
  "TODO: write subschemas, document and name"
  {:asiointitunnus               sc/Any
   (sc/optional-key :osapuolena) sc/Any})

(defschema TaskData
  " A quick glance indicates that this migh be a disjoint unoin of schemas with
fields [:katselmuksenLaji, :vaadittuLupaehto, :rakennus, (optional :katselmus), (optional :muuTunnus)],
[:maarays, (optional-key :kuvaus), (optional-key :vaaditutErityissuunnitelmat)], and
[(optional :osapuolena), :asiointitunnus].

TODO: These need further checking, might not be sufficient."
  (sc/conditional (fn [x] (contains? x :rakennus))
                  TaskDataType1
                  (fn [x] (contains? x :maaraus))
                  TaskDataType2
                  (fn [x] (contains? x :asiointitunnus))
                  TaskDataType3))

(defschema Task
  "TODO: document"
  {:id          ssc/ObjectIdStr
   :taskname    (sc/maybe sc/Str)                           ;;only 2 tasks have taskname null, only 1 with taskname ""
   :state       (apply sc/enum task-states)
   :created     (sc/maybe ssc/Timestamp)
   :closed      (sc/eq nil)                                 ;; always null, why ???
   :duedate     (sc/eq nil)                                 ;;always null, why ???
   :schema-info TaskSchemaInfo
   :data        TaskData
   :source      TaskSource
   :assignee    TaskAssignee
   }
  )

(defschema Tasks
  "TODO: document and give schema."
  [Task])

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
   :lastName  sc/Str
   :id        sc/Str})

(defschema BaseNeighborStatus
  {:created ssc/Timestamp
   :state   sc/Str})

(defschema NeighborStatusOpen
  (merge BaseNeighborStatus
         {:state "open"}))

(defschema NeighborStatusEmailSent
  (merge BaseNeighborStatus
         {:state "email-sent"
          :email ssc/Email
          :token sc/Str                                     ;;TODO: is there a better schema for tokens?
          :user  (sc/maybe {sc/Any sc/Any})                 ;;TODO: use a User schema here
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
         {:state   "response-given"
          :user    (sc/maybe {sc/Any sc/Any})               ;;TODO: use a User schema here
          ;; Need documentation on when this can be null
          :message ssc/NonBlankStr
          :vetuma  {sc/Any sc/Any}                          ;; TODO: need a Vetuma schema here
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
  {:type           (sc/maybe NeighborOwnerType)             ;;TODO: when is this null?
   ;; 1337 applications where at least one neighbor's owner's type is null
   :name           ssc/NonBlankStr
   :businessID     (sc/maybe ssc/FinnishY)
   ;; businessID can be null even when type is "juridinen"
   ;; 1745 applications with a neighbor whose owner has type "juridinen" and
   ;; businessID null
   :nameOfDeceased sc/Any                                   ;;TODO: schema needed
   :address        {sc/Any sc/Any}                          ;;TODO: need schema for address maps
   :email          (sc/maybe ssc/Email)
   })

(defschema Neighbor
  "TODO: document"
  {:id         ssc/ObjectIdStr
   :propertyId ssc/ObjectIdStr
   :owner      NeighborOwner
   :status     [NeighborStatus]})

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

(defschema AuthInviterMap
  "TODO: this schema is likely a duplicate"
  {:role      (apply sc/enum auth-inviter-roles)
   :lastName  ssc/NonBlankStr                               ;;verify existence, nonBlankness and nonNullness
   :firstName ssc/NonBlankStr                               ;;verify existence, nonNullness and nonBlankness
   :username  ssc/Username                                  ;; verify existence and nonNullness
   :id        ssc/ObjectIdStr                               ;; verify existence, and nonNullness
   })

(defschema AuthInviterId
  "Auth inviter can sometimes just be an id string"
  (sc/named
    ssc/ObjectIdStr
    'AuthInviterById))

(defschema AuthInviter
  "TODO: verify that inviter is always either an id string or a map.
TODO: what does the id refer to?
AuthInviter is represented by an id string or or an AuthInviterMap"
  (sc/cond-pre
    AuthInviterMap
    AuthInviterId))

(def auth-invite-inviter-roles
  "TODO: get these from somewhere instead of hard coding them"
  ["authority" "applicant" "rest-api"])

(defschema AuthInviteInviter
  "TODO: similar to other schemas describing people"
  {:id        ssc/ObjectIdStr
   :username  ssc/Username
   :firstName sc/Str                                        ;; NonBlank? Allways exist?
   :lastName  sc/Str                                        ;; NonBlank? Allways exist?
   :role      (apply sc/enum auth-invite-inviter-roles)
   })

(defschema AuthInvite
  "TODO: document"
  {:text    ssc/NonBlankStr
   :user    {sc/Any sc/Any}                                 ;;TODO: give schema
   :inviter {sc/Any sc/Any}                                 ;;TODO: give schema
   :created ssc/Timestamp
   :email   ssc/Email
   :path    sc/Str                                          ;; what is this? Can be empty
   :role    (sc/enum ["writer" "foreman" "reader"])
   })

(defschema BaseAuthElement
  "firstName and lastName can be empty, even for type owner
TODO: are there other possible fields?"
  {:username                         ssc/Username
   :firstName                        sc/Str
   :lastName                         sc/Str
   :role                             AuthRole
   :id                               sc/Str                 ;; 22 applications where an auth has empty string as id
   (sc/optional-key :type)           AuthType
   (sc/optional-key :inviter)        AuthInviter
   (sc/optional-key :invite)         AuthInvite
   ;; if :type is "owner", then :inviter and :invite never exist
   ;; this should be encoded in the schema
   ;;
   ;; it is possible for both inviter and invite to exist on the same AuthElement
   ;; TODO: is the inviter of the invite always equal to the inviter?
   (sc/optional-key :unsubscribed)   sc/Bool                ;; document
   (sc/optional-key :statementId)    ssc/ObjectIdStr        ;; document
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
         {:name CompanyAuthName                             ;;TODO: add the constraint that this equals :firstName?
          :type (sc/eq "company")
          :y    ssc/FinnishY}))

(defschema OwnerAuthElement
  "Owner never has an inviter or an invite.
TODO: add better documentation."
  (dissoc BaseAuthElement
          (sc/optional-key :inviter)
          (sc/optional-key :invite)))

(defschema AuthElement
  "TODO: document"
  (sc/conditional (fn [x] (= "company"
                             (:type x)))
                  CompanyAuthElement
                  (fn [x] (= "owner"
                             (:type x)))
                  OwnerAuthElement
                  (fn [x] true)
                  BaseAuthElement))

(defschema Auth
  [AuthElement])

(defschema GeometryString
  "TODO: document this, and it should have a better schema that checks the contents.
Should these be parsed and represented as something other than strings?"
  (sc/named
    sc/Str
    'GeometryString))

(def geometry-wgs84-types
  ["Polygon" "LineString" "Point" "Multilinestring"])

(defschema GeometryWgs84Coordinate
  [double double])

(defschema GeometryWgs84
  "TODO: document"
  {:type        (apply sc/enum geometry-wgs84-types)
   :coordinates [GeometryWgs84Coordinate]})

(defschema Drawing
  "TODO: document.
There are 357 applications where 'drawings' does not exist."
  {:id             sc/Int
   :geometry       GeometryString
   :geometry-wgs84 GeometryWgs84
   :category       (sc/eq "123")                            ;;TODO: what is this and why is it always "123"
   :desc           sc/Str
   :name           sc/Str
   :area           sc/Str                                   ;;this seems to be numeric, perhaps integer
   :height         sc/Str                                   ;;this seems to be a decimal number string
   })

(defschema Drawings
  [Drawing])
