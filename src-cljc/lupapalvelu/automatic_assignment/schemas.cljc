(ns lupapalvelu.automatic-assignment.schemas
  "Schemas are in a separate namespace in order to avoid cyclic dependencies."
  (:require [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.pate.schema-helper :as helper]
            [sade.shared-schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc :refer [defschema]]))

(def notice-form-types ["construction" ; Aloittamisilmoitus
                        "terrain"      ; Maastoonmerkintä
                        "location"     ; Sijaintikatselmus
                        ])

(defschema NoticeFormType
  "This definition is here to avoid a) cyclic dependency and b) duplicate definition."
  (apply sc/enum notice-form-types))

(def UNKNOWN-FOREMAN-ROLE "ei tiedossa")

(def foreman-roles (conj (mapv (comp ss/lower-case helper/foreman-roles) helper/foreman-codes)
                         UNKNOWN-FOREMAN-ROLE))

(def bim-model-phases (mapv name bim-schema/model-phases))

(defschema ForemanRole
  (apply sc/enum foreman-roles))

(defschema BimModelPhase
  (apply sc/enum bim-model-phases))

(defschema Criteria
  "Narrowing matching mechanism for assignment resolution."
  {(sc/optional-key :areas)            [sc/Str]          ; e.g., sipoo_keskusta
   (sc/optional-key :operations)       [sc/Str]          ; e.g., kerrostalo-rivitalo
   (sc/optional-key :attachment-types) [sc/Str]          ; e.g., paapiirustus.aitapiirustus
   (sc/optional-key :notice-forms)     [NoticeFormType]
   (sc/optional-key :foreman-roles)    [ForemanRole]
   (sc/optional-key :reviews)          [ssc/NonBlankStr] ; List of matchers (e.g., *loppu*)
   (sc/optional-key :bim-model-phases) [BimModelPhase]
   (sc/optional-key :handler-role-id)  ssc/ObjectIdStr})

(defschema Target
  "Target resolves the assignee either by implicitly via handler role or explicitly."
  {(sc/optional-key :handler-role-id) ssc/ObjectIdStr ; Roles are defined in organization
   (sc/optional-key :user-id)         ssc/NonBlankStr ; User must be an authority in the organization
   })

(defschema Email
  "Email notification that is to be sent in addition to creating the assignments."
  {:emails                    [ssc/EmailSpaced]
   (sc/optional-key :message) ssc/NonBlankStr})

(defschema Filter
  "Automatic assignment filter consists of narrowing criteria and the assignment
  target. When multiple filters match, the ones with highest rank are selected. Thus, a
  trigger event can result in multiple assignments."
  {:id                         ssc/ObjectIdStr
   :name                       ssc/NonBlankStr
   :rank                       sc/Int
   :modified                   ssc/Timestamp
   (sc/optional-key :criteria) Criteria
   (sc/optional-key :target)   Target
   (sc/optional-key :email)    Email})

(def valid-filter! (sc/validator Filter))

(defschema UpsertParams
  "Input validator schema for upsert command."
  {:organizationId ssc/NonBlankStr
   :filter         {(sc/optional-key :id)       ssc/ObjectIdStr
                    :name                       ssc/NonBlankStr
                    :rank                       sc/Int
                    (sc/optional-key :criteria) Criteria
                    (sc/optional-key :target)   Target
                    (sc/optional-key :email)    Email}})

(defschema ResolverOptions
  "In addition to the 'base data' like application and organization, the options include
  the actual triggering event information. In practise, the events are mutually exclusive,
  but the resolver supports multiple events as well."
  {:application                        sc/Any ; Regular application map
   :organization                       sc/Any ; Regular organization map
   (sc/optional-key :notice-form-type) NoticeFormType
   (sc/optional-key :foreman-role )    ForemanRole
   (sc/optional-key :bim-model-phase)  BimModelPhase
   (sc/optional-key :review-name)      ssc/NonBlankStr
   (sc/optional-key :attachment-type)  (sc/cond-pre sc/Str {:type-group sc/Str
                                                            :type-id    sc/Str})})
