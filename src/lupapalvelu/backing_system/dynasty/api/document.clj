(ns lupapalvelu.backing-system.dynasty.api.document
  (:require [lupapalvelu.backing-system.dynasty.api.case :as case]
            [lupapalvelu.backing-system.dynasty.api.client :as client]
            [lupapalvelu.backing-system.dynasty.api.common :as common]
            [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc])
  (:import [java.io File]))

(sc/defschema DocumentId
  "> Asiakirjan tunnus"
  sc/Int)

(sc/defschema DocumentType
  "> Asiakirjan tyyppi"
  (sc/enum "DOCUMENT" "MEETING_ITEM" "CONTRACT" "ACTIVITY" "OFFICIAL_DECISION"))

(sc/defschema DocumentState
  "> Asiakirjan tila, asiakirjan tilan päivitys mahdollisuus riippuu elinkaaresta"
  (sc/enum "NoFiles" "Draft" "Accepted" "Ready" "Invalidated" "Unknown"))

(sc/defschema DocumentWorkflow
  {:name     ssc/NonBlankStr
   :for-type DocumentType})

(sc/defschema DocumentTitle
  "> Asiakirjan otsikko"
  (ssc/max-length-string 2000))

(sc/defschema CaseSelector
  "> Asian valitsin
   Allows us to link a `Document` to a `Case`"
  (st/optional-keys
    {:id          case/CaseId
     :case-number case/LegacyCaseNumber}))

(sc/defschema PathSelector
  "> Yksitasoisissa käsittelyvaiheissa annetaan tähän kokoelmaan yksi valitsin.
   > Kaksitasoisissa käsittelyvaiheissa voi olla tarpeen käyttää kahta valitsinta.
   > Ensimmäinen kokoelman valitsin on ylimmän tason valintaa varten."
  (st/optional-keys
    {:id    sc/Int
     :title ssc/NonBlankStr}))

(sc/defschema PhaseSmartAttachOptions
  "> Vaihtoehtoisesti käytettävä tos-rakennetta tutkiva valitsin, joka luo
   > puuttuvat avoimet käsittelyvaiheet asian rakenteeseen tarvittaessa."
  (st/optional-keys
    {:path-selector [PathSelector]}))

(sc/defschema PhaseSelector
  "> Käsittelyvaiheen valitsin. Jos null, liitetään asiakirja asialle käsittelyvaiheiden
   > ulkopuolelle ilman TOS-kytköstä."
  (st/optional-keys
    {:id                   sc/Int
     :smart-attach-options PhaseSmartAttachOptions}))

(sc/defschema TosTitle
  "> Asiakirjatyypin tarkenne. Tämän täytyy löytyä asian TOS:n käsittelyvaiheen alta. Pakollinen jos PhaseSelector annettu.

  For example `\"Hakemus liitteineen (myös yhteislupa)\"`."
  ssc/NonBlankStr)

(sc/defschema CaseAttachmentOptions
  "> Näillä tiedoilla liitetään luotava asiakirja heti jollekin asialle.
   Allows us to link a `Document` to a particular phase within a `Case` (e.g. \"vireilletulo\" phase)"
  (st/merge
    {:case-selector CaseSelector}
    (st/optional-keys
      {:phase-selector PhaseSelector
       :tos-title      TosTitle})))

(sc/defschema PublicityClassName
  "> Julkisuusluokan nimi. Arvo tulee TOS:sta. TOS-tiedottomille annettava kuitenkin erikseen.
   > Oletusarvo: 'Julkinen'"
  (ssc/max-length-string 4000))

(sc/defschema AdProperties
  [ssc/NonBlankStr])

(sc/defschema DocumentCreateModel
  (st/merge
    {:title DocumentTitle}
    (st/optional-keys
      {:state                   DocumentState
       :schema-id               common/SchemaID
       :owner                   common/Owner
       :personal-data           common/PersonalData
       :is-public               sc/Bool
       :publicity-class-name    PublicityClassName
       :case-attachment-options CaseAttachmentOptions
       :meta-json               common/MetaJson
       :ad                      {ssc/NonBlankStr ssc/NonBlankStr}
       ;; Some D10 instances need to know the arrival type of attachments
       :arrival-type            ssc/NonBlankStr})))

(sc/defschema DocumentModel
  (st/merge
    DocumentCreateModel
    {:id   DocumentId
     :guid common/Guid
     :type DocumentType}
    (st/optional-keys
      {:ad-hidden   AdProperties
       :ad-readonly AdProperties})))

(sc/defschema FileEntry
  (st/merge
    {:content (sc/pred #(instance? File %) 'is-file?)
     ;; Name of the file, "foo.pdf" -> Sets the "content-disposition" header
     :name    ssc/NonBlankStr}
    (st/optional-keys
      {:mime-type ssc/NonBlankStr
       :encoding  ssc/NonBlankStr})))

(sc/defn create :- DocumentModel
  "> Luo uusi asiakirja"
  [client :- client/Client
   server-config :- client/ServerConfig
   document :- DocumentCreateModel
   file-entry :- FileEntry]
  (client/post! client "/documents" server-config {:multipart [{:name    "MetaData"
                                                                :content document}
                                                               (merge {:part-name "File"}
                                                                      file-entry)]}))

(sc/defn find-by-id :- (sc/maybe DocumentModel)
  "> Hae asiakirjan tiedot"
  [client :- client/Client
   server-config :- client/ServerConfig
   id :- DocumentId]
  (client/get! client (str "/documents/" id) server-config))

(sc/defn add-contact :- common/ContactModel
  "> Lisää asiakirjalle yhteystieto"
  [client :- client/Client
   server-config :- client/ServerConfig
   id :- DocumentId
   contact :- common/ContactCreateModel]
  (client/post! client (str "/documents/" id "/contacts") server-config {:form-params contact}))

(sc/defn contacts-by-id :- [common/ContactModel]
  "> Hae asiakirjan yhteystiedot"
  [client :- client/Client
   server-config :- client/ServerConfig
   id :- DocumentId]
  (client/get! client (str "/documents/" id "/contacts") server-config))

(sc/defn workflows :- [DocumentWorkflow]
  "> Hae asiakirjojen työnkulkumallit"
  [client :- client/Client
   server-config :- client/ServerConfig]
  (client/get! client "/documents/workflow_templates" server-config))

(sc/defn set-status
  "> Päivitä asiakirjan tilaa
   The state of the document affects its visibility within D10"
  [client :- client/Client
   server-config :- client/ServerConfig
   id :- DocumentId
   state :- DocumentState]
  (client/post! client (format "/documents/%s/status/%s" id state) server-config))
