(ns lupapalvelu.backing-system.dynasty.api.case
  "A Dynasty `Case` represents an issue submitted to a municipal administration.

   From Lupapiste point of view, a `Case` is the root under which all other entities
   (documents, attachments, contacts..) are stored."
  (:require [lupapalvelu.backing-system.dynasty.api.client :as client]
            [lupapalvelu.backing-system.dynasty.api.common :as common]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema CaseId
  "> Asian tunnus, tietokannan sisällä asian yksilöivä tunniste, esim. 120
   The primary identifier for a Case entity in the API"
  sc/Int)

(sc/defschema CaseTitle
  "> Asian otsikko
   For example: `\"Ympäristölupahakemus\"`. "
  (ssc/max-length-string 760))

(sc/defschema CaseBase
  "> Aineiston nimi
   For example: `\"Yleinen aineisto\"`"
  ssc/NonBlankStr)

(sc/defschema CaseBaseSelector
  "> Aineiston valinta. Tällä voidaan yliajaa julkisen rajapinnan organisaatiokonfiguraation oletusaineisto.
   > Aineistoon vietäessä tulee käyttäjälle olla luontioikeus kyseisen aineiston julkisuusluokkaan."
  (st/optional-keys
    {:id   ssc/NonBlankStr
     :name CaseBase}))

(sc/defschema CaseState
  "> Asian tila, asian tilan päivittämistä rajoittaa elinkaaren hallinta ja numerointi riippuu ympäristöstä"
  (sc/enum "Opened"
           "Pending"
           "Closed"
           "Cancelled" ;; aseta jos hakemuksen peruuttaa
           "Invalidated"
           "Archived"))

(sc/defschema LegacyCaseNumber
  "> Asianumero eli diaarinumero
   For example: `\"123/02.02.02/2021\"`
   This is a legacy format, which ought to be deprecated. However, it is still in use in the API."
  ssc/NonBlankStr)

(sc/defschema CaseNumber
  "The current proper case number format (replaces `LegacyCaseNumber`)
   For example: `\"123/2021\".`"
  ssc/NonBlankStr)

(sc/defschema Date
  "Note that the schema defines this as `iso-datetime` but the documentation uses just the date"
  (ssc/date-string :iso-datetime))

(sc/defschema OrganisationSelector
  "> Voidaan hakea organisaatio joko id:llä tai nimellä.
   > Jos organisaatiota ei löydy, tulee siitä virheilmoitus."
  (st/optional-keys
    {:id   ssc/NonBlankStr
     :name ssc/NonBlankStr}))

(sc/defschema CaseCreateModel
  "Note that the API incorrectly claims that case-date is optional"
  (st/merge
    {:title     CaseTitle
     :tos-class common/TosClass
     :case-date Date}
    (st/optional-keys
      {:schema-id          common/SchemaID
       :state              CaseState
       :case-base          CaseBaseSelector
       :organisation       OrganisationSelector
       :personal-data      common/PersonalData
       :pending-start-date Date
       :owner              common/Owner
       :meta-json          common/MetaJson})))

(sc/defschema CaseModel
  (st/merge
    CaseCreateModel
    {:id                   CaseId
     :case-number          LegacyCaseNumber
     :publicity-class-name ssc/NonBlankStr
     :is-public            sc/Bool}))

(sc/defschema CaseQueryRequest
  "Note that the API incorrectly claims that both `state` and `case-number` are optional,
   but actually omitting them results in an internal server error."
  (st/merge
    {:title     CaseTitle
     :case-base CaseBase
     :category  common/TosClass
     :state     CaseState}
    (st/optional-keys
      {:case-number CaseNumber})))

(sc/defschema CaseQueryResponse
  {:count sc/Int
   :ids   #{CaseId}})

(sc/defn find-by-query :- CaseQueryResponse
  "> Hae asioita hakuehdoilla"
  [client :- client/Client
   server-config :- client/ServerConfig
   opts :- CaseQueryRequest]
  (let [opts (merge {:case-number ""} opts)]
    (-> (client/post! client "/cases_by_query" server-config {:form-params opts})
        (update :ids set))))

(sc/defn find-by-id :- (sc/maybe CaseModel)
  "> Hae asian perustietoja"
  [client :- client/Client
   server-config :- client/ServerConfig
   case-id :- CaseId]
  (client/get! client (str "/cases/" case-id) server-config))

(sc/defn create :- CaseModel
  "> Luo uusi asia"
  [client :- client/Client
   server-config :- client/ServerConfig
   case :- CaseCreateModel]
  (client/post! client "/cases" server-config {:form-params case}))

(sc/defschema Phase
  "The state of a `Phase` wrt. a particular `Case`.
   The name of the `Phase` depends on the municipality, e.g. `\"Vireilletulo\"`"
  {:id      ssc/UUIDStr
   :name    ssc/NonBlankStr
   :is-done sc/Bool})

(sc/defn phases :- [Phase]
  "> Hae asian käsittelyvaiheet
   At the moment this is the only way to monitor the progress of a `Case`."
  [client :- client/Client
   server-config :- client/ServerConfig
   case-id :- CaseId]
  (client/get! client (str "/cases/" case-id "/phases") server-config))

(sc/defn add-contact :- common/ContactModel
  "> Lisää asialle uusi asianomainen"
  [client :- client/Client
   server-config :- client/ServerConfig
   id :- CaseId
   contact :- common/ContactCreateModel]
  (client/post! client (str "/cases/" id "/contacts") server-config {:form-params contact}))

(sc/defn contacts-by-id :- [common/ContactModel]
  "> Hae asian asianomaiset"
  [client :- client/Client
   server-config :- client/ServerConfig
   id :- CaseId]
  (client/get! client (str "/cases/" id "/contacts") server-config))
