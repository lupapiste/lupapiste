(ns lupapalvelu.backing-system.dynasty.message
  "Transform a Lupapiste application into a set of Dynasty compatible messages"
  (:require [lupapalvelu.attachment.metadata :as attachment-metadata]
            [lupapalvelu.backing-system.dynasty.api.case :as case-api]
            [lupapalvelu.backing-system.dynasty.api.common :as common-api]
            [lupapalvelu.backing-system.dynasty.api.document :as document-api]
            [lupapalvelu.backing-system.dynasty.municipal-context :as ctx]
            [lupapalvelu.backing-system.dynasty.util :as d-util]
            [lupapalvelu.i18n :as i18n]
            [sade.date :as date]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema BaseMessage
  {::message-type (sc/pred qualified-keyword? "message-type")})

(defn- has-type?
  [expected-type]
  (fn [{::keys [message-type]}]
    (= expected-type message-type)))

;;; Case

(defn- format-timestamp
  [ts]
  (-> ts
      date/zoned-date-time
      (date/iso-datetime :local)))

(sc/defschema CaseMessage
  (st/merge BaseMessage
            {::payload case-api/CaseCreateModel}))

(sc/defn application->case :- (sc/maybe CaseMessage)
  "Create a case from the application"
  {:private true}
  [{::ctx/keys                        [case-fn now integration]
    {:keys [created] :as application} ::ctx/application} :- ctx/MunicipalContext]
  (when-not (d-util/get-case-id application)
    {::message-type ::case
     ::payload      (-> {:case-date          (format-timestamp now)
                         :pending-start-date (format-timestamp created)
                         :personal-data      "Sisältää henkilötietoja"}
                        (util/deep-merge (case-fn application integration))
                        util/strip-blanks)}))

;;; Contacts

(sc/defschema ContactMessage
  (st/merge BaseMessage
            {::lp-document-id ssc/ObjectIdStr
             ::payload        common-api/ContactCreateModel}))

(defmulti applicant->contact (fn [_ctx doc] (-> doc :data :_selected)))

(sc/defmethod applicant->contact "yritys" :- ContactMessage
  [{::ctx/keys [contact-fn integration]} :- ctx/MunicipalContext
   {{{:keys                                               [yritysnimi liikeJaYhteisoTunnus]
      {{:keys [etunimi sukunimi]} :henkilotiedot
       {:keys [puhelin email]}    :yhteystiedot}          :yhteyshenkilo
      {:keys [katu postinumero postitoimipaikannimi maa]} :osoite} :yritys} :data
    :as                                                                     applicant}]
  {::message-type   ::contact
   ::lp-document-id (:id applicant)
   ::payload        (-> {:type         "Organisation"
                         :org          {:title    yritysnimi
                                        :trade-no liikeJaYhteisoTunnus}
                         :person       {:first-name etunimi
                                        :last-name  sukunimi}
                         :contact-info {:phone          puhelin
                                        :email          email
                                        :postal-address katu
                                        :postal-code    postinumero
                                        :city           postitoimipaikannimi
                                        :country        maa}
                         :channel      "NO_CHANNEL"}
                        (util/deep-merge (contact-fn applicant integration))
                        util/strip-blanks)})

(sc/defmethod applicant->contact :default :- ContactMessage
  [{::ctx/keys [contact-fn integration]} :- ctx/MunicipalContext
   {{{{:keys [etunimi sukunimi]}                          :henkilotiedot
      {:keys [puhelin email]}                             :yhteystiedot
      {:keys [katu postinumero postitoimipaikannimi maa]} :osoite} :henkilo} :data
    :as                                                                      applicant}]
  {::message-type   ::contact
   ::lp-document-id (:id applicant)
   ::payload        (-> {:type         "Person"
                         :person       {:first-name etunimi
                                        :last-name  sukunimi}
                         :contact-info {:phone          puhelin
                                        :email          email
                                        :postal-address katu
                                        :postal-code    postinumero
                                        :city           postitoimipaikannimi
                                        :country        maa}
                         :channel      "NO_CHANNEL"}
                        (util/deep-merge (contact-fn applicant integration))
                        util/strip-blanks)})

(sc/defn application->contacts :- [ContactMessage]
  "Find all applicants and add them as case contacts"
  {:private true}
  [{::ctx/keys [application] :as ctx}]
  (->> application
       d-util/application->applicant-documents
       (filter (comp nil? d-util/get-applicant-contact-id))
       (map (partial applicant->contact ctx))))

;;; Document

(sc/defschema DocumentMessage
  (st/merge BaseMessage
            {::payload    document-api/DocumentCreateModel
             ::file-entry (st/dissoc document-api/FileEntry :content)}))

(sc/defn application->document :- (sc/maybe DocumentMessage)
  "Create a document from the application itself"
  {:private true}
  [{::ctx/keys                   [document-fn integration]
    {:keys [id] :as application} ::ctx/application} :- ctx/MunicipalContext]
  (when-not (d-util/get-document-id application)
    ;; TODO: read public flag from TOJ?
    (let [public? true]
      {::message-type ::document
       ::payload      (-> {:state                   "Draft"
                           :personal-data           "Sisältää henkilötietoja"
                           :is-public               public?
                           :publicity-class-name    (if public? "Julkinen" "Salassa pidettävä")
                           :case-attachment-options {;; We know the case-id once the case has been created
                                                     :case-selector {}}}
                          (util/deep-merge (document-fn application integration))
                          util/strip-blanks)
       ::file-entry   {:mime-type "application/pdf"
                       :name      (format "%s.pdf" id)}})))

;;; Attachments

;; Later versions of Dynasty will/may have better support for plain attachments ("tiedostoliite")
;; but at least for now we will model them as documents ("asiakirja"). This also allows attaching
;; various metadata to the attachments.

(sc/defschema AttachmentMessage
  (st/merge BaseMessage
            {::attachment-id ssc/ObjectIdStr
             ::file-id       ssc/FileId
             ::payload       document-api/DocumentCreateModel}))

(sc/defn lp-attachment->attachment :- (sc/maybe AttachmentMessage)
  [{::ctx/keys [attachment-fn
                application
                integration]} :- ctx/MunicipalContext
   {{:keys [type-group type-id]} :type
    {:keys [fileId]}             :latestVersion
    :keys                        [id]
    :as                          attachment}]
  (when fileId
    (let [public? (attachment-metadata/public-attachment? attachment)
          title   (i18n/loc (format "attachmentType.%s.%s" type-group type-id))]
      {::message-type  ::attachment
       ::attachment-id id
       ::file-id       fileId
       ::payload       (-> {:title                   title
                            :state                   "Draft"
                            :personal-data           "Sisältää henkilötietoja"
                            :publicity-class-name    (if public? "Julkinen" "Salassa pidettävä")
                            :case-attachment-options {:case-selector {}}
                            :meta-json               {:public-title title}}
                           (util/deep-merge (attachment-fn application attachment integration))
                           util/strip-blanks)})))

(defn- application->attachments
  "Attach all lupapiste attachments as attachments to the document"
  [{::ctx/keys [application] :as ctx}]
  (->> application
       :attachments
       (filter (comp nil? d-util/get-attachment-document-id))
       ;; TODO: do we want to sent all of the attachments or some subset?
       (keep (partial lp-attachment->attachment ctx))))

;; API

(sc/defschema Message
  (sc/conditional
    (has-type? ::case) CaseMessage
    (has-type? ::contact) ContactMessage
    (has-type? ::document) DocumentMessage
    (has-type? ::attachment) AttachmentMessage))

(sc/defn create-case-messages :- [Message]
  "Build a seq of messages for creating a new case (and related entities) from a Lupapiste application.

   If the application has already been (partially) submitted to Dynasty, only the unsubmitted
   data will be processed (e.g. messages for new attachments)."
  {:always-validate true}
  [ctx :- ctx/MunicipalContext]
  (->> (concat
         [(application->case ctx)]
         (application->contacts ctx)
         [(application->document ctx)]
         (application->attachments ctx))
       (remove nil?)))
