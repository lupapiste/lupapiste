(ns lupapalvelu.foreman
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [lupapalvelu.action :as action :refer [update-application]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application.foreman :as foreman]
            [lupapalvelu.assignment :as assi]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.company :as company]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.foreman-pdf :as foreman-pdf]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log :refer [error info warn]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.files :as files]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc :refer [defschema]])
  (:import [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn no-foreman
  "Pre-checker that fails if the user's role in the application is foreman."
  [{:keys [user application]}]
  (when (auth/has-auth-role? application (:id user) "foreman")
    (fail :error.foreman)))

(defcontext foreman-app-context [{{user-id :id} :user application :application}]
  ;; Some permissions are added in foreman applications by user application role
  (when (foreman-util/foreman-app? application)
    {:context-scope :foreman-app
     :context-roles (->> (auth/get-auths application user-id)
                         (map :role))}))

(defn ensure-foreman-not-linked
  "Pre-check"
  [{{foreman-app-id :foremanAppId task-id :taskId} :data {:keys [tasks]} :application}]
  (when (and (not (ss/blank? foreman-app-id))
             (->> (filter (comp #{:task-vaadittu-tyonjohtaja} keyword :name :schema-info) tasks)
                  (remove (comp #{task-id} :id))
                  (map (comp :value :asiointitunnus :data))
                  (some #{foreman-app-id})))
    (fail :error.foreman-already-linked)))

(defn- get-applicant-infos [application]
  (some->> application :documents domain/get-applicant-documents (map domain/get-applicant-info-from-doc)))

(defn- get-foreman-doc-info [foreman-doc]
  (let [data (:data foreman-doc)]
    {:email     (get-in data [:yhteystiedot :email :value] "")
     :firstName (get-in data [:henkilotiedot :etunimi :value] "")
     :lastName  (get-in data [:henkilotiedot :sukunimi :value] "")}))

(defn- get-foreman-info [foreman-app]
  (let [docs (:documents foreman-app)
        doc  (or (domain/get-document-by-name docs "tyonjohtaja-v2")
                 (domain/get-document-by-name docs "tyonjohtaja"))]
    (get-foreman-doc-info doc)))

(defn- enrich-info-with-lang [{:keys [email] :as person-info}]
  (assoc person-info
         :lang (if-let [user (mongo/select-one :users {:email email} [:language])]
                 (:language user)
                 "fi")))

(defn- applicant-field?
  "Checks if the user matches with any applicant on the given field"
  [user applicant-infos field]
  (some #(= (get user field) (get % field)) applicant-infos))

(defn user-can-request-foreman-termination
  "Pre-check for limiting the users to those who can request termination.
   Note that checking that the user is related to the application is assumed to have been checked
   by the command's permissions and are not checked here separately."
  [{:keys [user application data]}]
  (let [applicants (get-applicant-infos application)]
    (cond
      (applicant-field? user applicants :id) nil ; Applicant selected from dropdown, can terminate anyone
      (applicant-field? user applicants :email) nil ; Fallback if not dropdown selected or company contact
      (nil? (:foreman-app-id data)) nil ; Pre-check must work without data
      (= (:email user) (:foreman-email data)) nil ; Foreman can terminate themselves
      :else (fail :error.unauthorized))))

(defn user-is-authority-for-foreman-app
  "Pre-check for making sure that the user has the appropriate authority for the foreman application.
   Note that checking the actual app's permission is assumed to have been done in the command's permissions."
  [{:keys [user data]}]
  (when-let [foreman-app-id (:foreman-app-id data)]
    (when-not (some->> (mongo/by-id :applications foreman-app-id [:organization])
                       :organization
                       (usr/user-is-authority-in-organization? user))
      (fail :error.unauthorized))))

(defn foreman-document-schema?
  "Check if the document is a foreman document"
  [foreman-doc]
  (when foreman-doc
    (-> foreman-doc :schema-info :foreman-document)))

(defn foreman-doc-id-is-foreman-document
  "Pre-check for making sure that the given document id refers to a foreman document"
  [{:keys [application data]}]
  (when-let [doc-id (:foreman-doc-id data)]
    (when-not (some->> doc-id
                       (domain/get-document-by-id application)
                       foreman-document-schema?)
      (fail :error.document-is-not-a-foreman-document))))

(defn foreman-doc-is-v3? [foreman-doc]
  (:tj-document foreman-doc))

(defn foreman-doc-id-is-foreman-v3-document
  "Pre-check for making sure that the given document id refers to a foreman v3 document found on the application documents."
  [{:keys [application data]}]
  (when-let [doc-id (:foreman-doc-id data)]
    (when-not (some->> doc-id
                       (domain/get-document-by-id application)
                       foreman-doc-is-v3?)
      (fail :error.unauthorized))))

(defn termination-date-after-start
  "Pre-check that checks whether foreman application's started and ended dates are temporally correct.
   If 'get-started-from-app?' is true :started is taken from the application, :ended from the request.
   If false the opposite applies.

   Works for both tyonjohtaja-v2 applications and tyonjohtaja-v3 documents."
  [get-started-from-app? {:keys [application data]}]
  (when-let [foreman-app-or-doc (if (:foreman-app-id data)
                                  (mongo/by-id :applications (:foreman-app-id data))
                                  (when (:foreman-doc-id data)
                                    (domain/get-document-by-id application (:foreman-doc-id data))))]
    (let [app-dates (some-> foreman-app-or-doc
                            (foreman-util/get-foreman-responsibility-timestamps application))
          started   (if get-started-from-app? (:started app-dates) (:started data))
          ended     (if get-started-from-app? (:ended data) (:ended app-dates))]
      (when (and started ended (> started ended))
        (fail :error.invalid-date)))))

(defn termination-requested?
  "Returns `nil` or a fail map"
  [{:keys [data]}]
  (when-let [foreman-app-id (:foreman-app-id data)]
    (let [foreman-app (mongo/by-id :applications foreman-app-id)]
      (when (not= (some-> foreman-app :foremanTermination :state) "requested")
        (fail :error.foreman-termination-not-requested)))))

(defn termination-requested-v3?
  "Returns `nil` or a fail map"
  [{:keys [application data]}]
  (when (some->> data
                 :foreman-doc-id
                 (domain/get-document-by-id application)
                 :foremanTermination
                 :state
                 (not= "requested"))
    (fail :error.foreman-termination-not-requested)))
(defn foreman-state? [foreman-app allowed-state?]
  (some-> foreman-app
          (foreman-util/select-latest-verdict-status)
          :status
          allowed-state?))

(def foreman-doc-states #{"declared"     ; Työnjohtajailmoitus jätetty
                          "submitted"    ; Työnjohtajahakemus jätetty
                          "approved"     ; Työnjohtaja siirretty käsittelyyn
                          "acknowledged" ; Työnjohtaja hyväksytty
                          "rejected"     ; Työnjohtaja hylätty
                          })

(defschema ForemanDocState (apply sc/enum foreman-doc-states))

(defn foreman-v3-state? [doc allowed-states]
  (contains? allowed-states (:state doc)))

(defn foreman-responsibility-started-or-rejected?
  "Returns truthy if the foremans responsibility has started or formean has been rejected.
   For notifications this means that it has some state.
   For applications this means that it has been given a verdict (state = acknowledged)
   or the application has been rejected).

   For v3 documents the state is stored directly on the document. For older foreman apps it is
   derived from the verdict status.

   When foreman-v2 apps are fetched via `get-linked-foreman-applications`
   (e.g. in `get-all-initiated-foreman-documents`) it applies `foreman-application-info` to the
   apps, and the verdict fields (:verdicts, :pate-verdicts) are stripped and the status is
   pre-computed as :latest-verdict-status. We use that when available and fall back to
   re-deriving it via `foreman-state?` for callers that pass a full application document."
  [foreman-doc app]
  (if (foreman-doc-is-v3? foreman-doc)
    (foreman-v3-state? foreman-doc (if (= "application" (:type foreman-doc))
                                     (disj foreman-doc-states "submitted" "approved")
                                     foreman-doc-states))
    (or (some-> (:latest-verdict-status app) :status #{"ok" "rejected"})
        (foreman-state? app #{"ok" "rejected"}))))

(defn get-all-initiated-foreman-documents
  "Gets all initiated foremen documents related to the application.
   Initiated meaning their responsibility has started or the foremen has been rejected."
  [application]
  (let [linked-foreman-apps (foreman-util/get-linked-foreman-applications application)]
    (into []
          (for [app   (conj linked-foreman-apps application)
                doc   (:documents app)
                :let  [doc-with-schema-info (schemas/with-current-schema-info doc)]
                :when (and (foreman-document-schema? doc-with-schema-info)
                           (foreman-responsibility-started-or-rejected? doc-with-schema-info app))]
            doc-with-schema-info))))

(defn foreman-state-is
  "Pre-check that fails if the foreman in question is not in an allowed state"
  [allowed-state? {:keys [data]}]
  (when-let [foreman-app-id (:foreman-app-id data)]
    (let [foreman-app (mongo/by-id :applications foreman-app-id [:state :verdicts :pate-verdicts])]
      (when-not (foreman-state? foreman-app allowed-state?)
        (fail :error.foreman.wrong-state)))))

(sc/defn foreman-v3-state-is
  "Returns a pre-check that fails if the foreman in question is not in an allowed state"
  {:always-validate true}
  [allowed-states :- #{ForemanDocState}]
  (fn [{:keys [application data]}]
    (when-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
      (when-not (foreman-v3-state? foreman-doc allowed-states)
        (fail :error.foreman.wrong-state)))))

(defn foreman-v3-state-missing
  "Pre-check that fails if the foreman's state is not nil"
  [{:keys [application data]}]
  (when-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
    (when (:state foreman-doc)
      (fail :error.foreman.wrong-state))))

(defn foreman-v3-consent-given
  "Pre-check that fails if the foreman's consent is missing on the form"
  [{:keys [application data]}]
  (when-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
    (when-not (foreman/tj-document->foreman-consent foreman-doc)
      (fail :error.foreman.consent-missing))))

(defn foreman-v3-document-is-valid?
  "If the document is a foreman-v3 document, check that the document is valid
  (the required fields are filled.)"
  [{:keys [data application]}]
  (when-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
    (let [errors (model/validate application foreman-doc)]
      (when-not (empty? errors)
        (fail :error.missing-parameters :reason :error.missing-parameters)))))

(defn foreman-document-is-initiated
  "Pre-check that verifies that a foreman document has been initiated.

  This works for both foreman-v3 and older foreman apps"
  [{application :application {foreman-doc-id :foreman-doc-id} :data}]
  (if-let [foreman-doc (domain/get-document-by-id application foreman-doc-id)]
    (when-not (foreman-responsibility-started-or-rejected? foreman-doc application)
      (fail :error.foreman.wrong-state))
    (fail :error.foreman-document-not-found)))

(defn application-has-initiated-foreman
  "Pre-check that verifies the application has at least one foreman document that has been initiated.
   Non-foreman documents in the application are ignored."
  [{:keys [application]}]
  (when (empty? (get-all-initiated-foreman-documents application))
    (fail :error.no-initiated-foremen)))

(defn other-project-document [application timestamp]
  (let [building-doc (domain/get-document-by-name application "uusiRakennus")
        kokonaisala  (get-in building-doc [:data :mitat :kokonaisala :value])]
    {:luvanNumero        {:value    (:id application)
                          :modified timestamp}
     :katuosoite         {:value    (:address application)
                          :modified timestamp}
     :rakennustoimenpide {:value    (get-in application [:primaryOperation :name])
                          :modified timestamp}
     :kokonaisala        {:value    kokonaisala
                          :modified timestamp}
     :autoupdated        {:value    true
                          :modified timestamp}}))

(defn- not-finnish-hetu? [foreman-doc]
  (get-in foreman-doc [:data :henkilotiedot :not-finnish-hetu :value]))

(defn- get-foreman-hetu [foreman-doc]
  (get-in foreman-doc [:data :henkilotiedot :hetu :value]))

(defn- get-foreman-ulkomainen-hetu [foreman-doc]
  (get-in foreman-doc [:data :henkilotiedot :ulkomainenHenkilotunnus :value]))

(defn get-hetu-based-on-hetu-type [foreman-document]
  (if (not-finnish-hetu? foreman-document)
    (get-foreman-ulkomainen-hetu foreman-document)
    (get-foreman-hetu foreman-document)))

(defn get-foreman-email [foreman-doc]
  (ss/canonize-email (get-in foreman-doc [:data :yhteystiedot :email :value])))

(defn- get-foreman-applications
  "Returns applications that have the corresponding foreman as in the given foreman document"
  [foreman-document]
  (let [foreman-hetu (get-hetu-based-on-hetu-type foreman-document)
        path         (if (not-finnish-hetu? foreman-document)
                       "data.henkilotiedot.ulkomainenHenkilotunnus.value"
                       "data.henkilotiedot.hetu.value")]
    (when-not (ss/blank? foreman-hetu)
      (mongo/select :applications
                    {$or [{"primaryOperation.name"      "tyonjohtajan-nimeaminen-v2"
                           :state                       {$in ["acknowledged" "foremanVerdictGiven" "appealed"]}
                           "documents.schema-info.name" "tyonjohtaja-v2"
                           (str "documents." path)      foreman-hetu}
                          {"documents" {$elemMatch {"tj-document"      true
                                                    "schema-info.name" "tyonjohtaja-v3"
                                                    path               foreman-hetu
                                                    ;; TODO should there be "approved" also?
                                                    "state"            {$in ["acknowledged" "declared"]}}}}]}
                    [:created :documents :municipality :primaryOperation]))))

(defn get-foreman-document [foreman-application]
  (domain/get-document-by-name foreman-application "tyonjohtaja-v2"))

(defn get-foreman-documents-v3 [application]
  (domain/get-documents-by-name application "tyonjohtaja-v3"))

(defn get-foreman-project-applications
  "Based on the given application (can be a regular application or a passed foreman application),
  fetches all project applications that have the same foreman as in the passed foreman application
  (personal id is used as key). Returns all the linked applications as a list."
  [foreman-document app-id]
  (let [foreman-apps    (->> (get-foreman-applications foreman-document)
                             (remove #(= (:id %) app-id)))
        foreman-app-ids (map :id foreman-apps)
        links           (mongo/select :app-links {:link {$in foreman-app-ids}})
        linked-app-ids  (remove (set foreman-app-ids) (distinct (mapcat #(:link %) links)))]
    (mongo/select :applications {$and [{:_id {$in linked-app-ids}} {:state {$ne :closed}}]} [:documents :address :primaryOperation])))

(defn- get-linked-app-operations [foreman-app-id link]
  (let [other-id (first (remove #{foreman-app-id} (:link link)))]
    (get-in link [(keyword other-id) :apptype])))

(defn- unwrap [wrapped-value]
  (let [value (tools/unwrapped wrapped-value)]
    (if (and (or (coll? value) (string? value)) (empty? value))
      "ei tiedossa"
      value)))

(defn- basic-history-data [foreman-app foreman-doc]
  (let [municipality    (:municipality foreman-app)
        difficulty      (unwrap (get-in foreman-doc [:data :patevyysvaatimusluokka]))
        foreman-role    (unwrap (get-in foreman-doc [:data :kuntaRoolikoodi]))
        limitedApproval (unwrap (get-in foreman-doc [:data :tyonjohtajanHyvaksynta :tyonjohtajanHyvaksynta]))
        limitedApproval (if limitedApproval
                          "yes"
                          nil)]
    {:municipality    municipality
     :difficulty      difficulty
     :jobDescription  foreman-role
     :limitedApproval limitedApproval}))

(defn- get-history-data-from-app-for-tyonjohtaja-v3
  "Return all matching foreman docs from given application"
  [foreman-hetu hetu-path foreman-app]
  (let [hetu-path (util/split-kw-path hetu-path)
        operation (-> foreman-app :primaryOperation :name)]
    (for [foreman-doc (->> (domain/get-documents-by-name foreman-app "tyonjohtaja-v3")
                           (filter (fn [doc]
                                     (= foreman-hetu (get-in doc hetu-path)))))]
      (merge (basic-history-data foreman-app foreman-doc)
             {:foremanDocId    (:id foreman-doc)
              :operation       operation
              :linkedAppId     (:id foreman-app)
              :foremanAppId    (:id foreman-app)
              :created         (:created foreman-app)}))))

(defn- get-history-data-from-app [foreman-hetu path app-links foreman-app]
  ;; If tyonjohtaja-v2 doc is found the application is a foreman application
  ;; otherwise it's a project application with the foreman document in it
  ;; NOTE: Application can only have single tyonjohtaja-v2 document.
  (if-let [foreman-doc (domain/get-document-by-name foreman-app "tyonjohtaja-v2")]
    (let [relevant-link  (first (filter #(some #{(:id foreman-app)} (:link %)) app-links))
          project-app-id (first (remove #{(:id foreman-app)} (:link relevant-link)))
          operation      (get-linked-app-operations (:id foreman-app) relevant-link)]
      [(merge (basic-history-data foreman-app foreman-doc)
              {:foremanDocId (:id foreman-doc)
               :operation    operation
               :linkedAppId  project-app-id
               :foremanAppId (:id foreman-app)
               :created      (:created foreman-app)})])
    (get-history-data-from-app-for-tyonjohtaja-v3 foreman-hetu path foreman-app)))

(defn get-foreman-history-data [foreman-document app-id]
  (let [foreman-hetu (get-hetu-based-on-hetu-type foreman-document)
        path         (if (not-finnish-hetu? foreman-document)
                       "data.henkilotiedot.ulkomainenHenkilotunnus.value"
                       "data.henkilotiedot.hetu.value")
        foreman-apps (->> (get-foreman-applications foreman-document)
                          (util/remove-by-id app-id))
        links        (mongo/select :app-links {:link {$in (map :id foreman-apps)}})]
    (->> foreman-apps
         (mapcat (partial get-history-data-from-app foreman-hetu path links))
         (into []))))

(defn- reduce-to-highlights [history-group]
  (let [history-group     (sort-by :created history-group)
        difficulty-values (vec (map :name (:body schemas/patevyysvaatimusluokka)))]
    (reduce (fn [highlights group]
              (if (pos? (util/compare-difficulty :difficulty difficulty-values (first highlights) group))
                (cons group highlights)
                highlights))
            nil
            history-group)))

(defn reduce-foreman-history-data [history-data]
  (let [grouped-history (group-by (juxt :municipality :jobDescription :limitedApproval) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))

(defn allow-foreman-only-in-foreman-app
  "If user has :foreman role in current application, check that the application is a foreman application."
  [{:keys [application user]}]
  (when (and (auth/has-auth-role? application (:id user) :foreman)
             (not (foreman-util/foreman-app? application)))
    unauthorized))

(defn notice?
  "True if application is foreman application and of type notice (ilmoitus)"
  [application]
  (and
    (foreman-util/foreman-app? application)
    (= "tyonjohtaja-ilmoitus" (:permitSubtype application))))

(defn validate-foreman-submittable [application link-permit]
  (when-not (app/submitted? application)
    (when link-permit
      (when-not (app/submitted? link-permit)
        (fail :error.not-submittable.foreman-link)))))

(defn- validate-notice-or-application [{subtype :permitSubtype :as application}]
  (when (and (foreman-util/foreman-app? application) (ss/blank? subtype))
    (fail :error.foreman.type-not-selected)))

(defn- validate-notice-submittable [application link-permit]
  (when (notice? application)
    (when link-permit
      (when-not (states/post-verdict-states (keyword (:state link-permit)))
        (fail :error.foreman.notice-not-submittable)))))

(defn validate-application
  "Validates foreman applications. Returns nil if application is OK, or fail map."
  [application]
  (when (foreman-util/foreman-app? application)
    (let [link        (some #(when (= (:type %) "lupapistetunnus") %) (:linkPermitData application))
          link-permit (when link
                        (mongo/select-one :applications {:_id (:id link)} {:state 1}))]
      (or
        (validate-notice-or-application application)
        (validate-notice-submittable application link-permit)
        (validate-foreman-submittable application link-permit)))))

(defn new-foreman-application [{:keys [created application] :as command}]
  (-> (app/do-create-application
        (assoc command :data {:operation    "tyonjohtajan-nimeaminen-v2"
                              :x            (-> application :location first)
                              :y            (-> application :location second)
                              :address      (:address application)
                              :propertyId   (:propertyId application)
                              :municipality (:municipality application)
                              :infoRequest  false
                              :messages     []}))
      (assoc :opened (if (util/pos? (:opened application)) created nil))))

(defn- cleanup-hakija-doc [doc]
  (let [schema-name (op/get-operation-metadata :tyonjohtajan-nimeaminen-v2 :applicant-doc-schema)]
    (-> doc
        (assoc :id (mongo/create-id))
        (assoc-in [:schema-info :name] schema-name)
        (assoc-in [:data :henkilo :userId] {:value nil}))))

;; imported previous permit applications do not have a hankkeen-kuvaus -document
;; and have the description in the first aiemman-luvan-toimenpide -document
(defn get-application-description [application]
  (let
    [description
     (-> (domain/get-documents-by-subtype (:documents application) "hankkeen-kuvaus")
         first
         (get-in [:data :kuvaus :value]))]
    (if (and description (not (= description "")))
      description
      (or
        (-> (domain/get-document-by-name (:documents application) "aiemman-luvan-toimenpide")
            (get-in [:data :kuvaus :value]))
        ""))))

(defn update-foreman-docs [foreman-app application role]
  (let [hankkeen-kuvaus     (get-application-description application)
        hankkeen-kuvaus-doc (first (domain/get-documents-by-subtype (:documents foreman-app) "hankkeen-kuvaus"))
        hankkeen-kuvaus-doc (if hankkeen-kuvaus
                              (assoc-in hankkeen-kuvaus-doc [:data :kuvaus :value] hankkeen-kuvaus)
                              hankkeen-kuvaus-doc)

        tyonjohtaja-doc     (domain/get-document-by-name foreman-app "tyonjohtaja-v2")
        tyonjohtaja-doc     (if-not (ss/blank? role)
                              (assoc-in tyonjohtaja-doc [:data :kuntaRoolikoodi :value] role)
                              tyonjohtaja-doc)

        hakija-docs         (domain/get-applicant-documents (:documents application))
        hakija-docs         (map cleanup-hakija-doc hakija-docs)]
    (->> (:documents foreman-app)
         (remove (comp #{:tyonjohtaja-v2} keyword :name :schema-info))
         (remove (comp #{:hankkeen-kuvaus :hakija} keyword :subtype :schema-info))
         (concat hakija-docs [hankkeen-kuvaus-doc tyonjohtaja-doc])
         (remove nil?)
         (assoc foreman-app :documents))))

(defn- applicant-user-auth [applicant path auth]
  (when-let [applicant-user (some-> applicant
                                    (get-in path)
                                    ss/canonize-email
                                    usr/get-user-by-email)]
    ;; Create invite for applicant if authed
    (when (some (partial usr/same-user? applicant-user) auth)
      {:email (:email applicant-user)
       :role  "writer"})))

(defn- henkilo-invite [applicant auth]
  {:pre [(= "henkilo" (get-in applicant [:data :_selected]))
         (sequential? auth)]}
  (applicant-user-auth applicant [:data :henkilo :yhteystiedot :email] auth))

(defn- yritys-invite [applicant auth]
  {:pre [(= "yritys" (get-in applicant [:data :_selected]))
         (sequential? auth)]}
  (if-let [company-id (get-in applicant [:data :yritys :companyId])]
    ;; invite the Company if it's authed
    (some
      #(when (= company-id (or (:id %) (get-in % [:invite :user :id])))
         {:company-id company-id})
      auth)
    (applicant-user-auth applicant [:data :yritys :yhteyshenkilo :yhteystiedot :email] auth)))

(defn applicant-invites [documents auth]
  (->> (domain/get-applicant-documents documents)
       (tools/unwrapped)
       (map #(case (-> % :data :_selected)
               "henkilo" (henkilo-invite % auth)
               "yritys" (yritys-invite % auth)))
       (remove nil?)))

(defn- create-company-auth [inviter company-id]
  (when-let [company (company/find-company-by-id company-id)]
    ;; company-auth can be nil if company is locked
    (when-let [company-auth (company/company->auth company :writer)]
      (assoc company-auth
             :inviter (usr/summary inviter)))))

(defn- invite->auth [inv app-id inviter timestamp]
  (if (:company-id inv)
    (create-company-auth inviter (:company-id inv))
    (let [invited (usr/get-or-create-user-by-email (:email inv) inviter)]
      (auth/create-invite-auth inviter invited app-id (:role inv) timestamp))))

(defn copy-auths-from-linked-app [foreman-app linked-app user created]
  (->> (:auth linked-app)
       (applicant-invites (:documents foreman-app))
       (remove #(= (:email %) (:email user)))
       (mapv #(invite->auth % (:id foreman-app) user created))
       (remove nil?)
       (update foreman-app :auth concat)))

(defn add-foreman-invite-auth [foreman-app foreman-user user created]
  (if (and foreman-user (not (auth/has-auth? foreman-app (:id foreman-user))))
    (->> (auth/create-invite-auth user foreman-user (:id foreman-app) "foreman" created)
         (update foreman-app :auth conj))
    foreman-app))

(defn- authorize-to-app!
  "Authorize the invited foreman to the application."
  [linked-app foreman-user {user :user created :created :as command}]
  (update-application command
                      {:auth {$not {$elemMatch {:invite.user.username (:email foreman-user)}}}}
                      {$push {:auth (auth/create-invite-auth user foreman-user (:id linked-app) "foreman" created)}
                       $set  {:modified created}}))

(defn authorize-and-send-invite-notifications!
  "Authorize the foreman and send notifications to the parties when a new foreman app is created"
  [foreman-app foreman-user linked-app command]
  (try
    (when (and foreman-user (not (auth/has-auth? linked-app (:id foreman-user))))
      (authorize-to-app! linked-app foreman-user command))
    (copy-app/send-invite-notifications! foreman-app command)
    (catch Exception e
      (error e "Error when inviting to foreman application:" (.getMessage e)))))

(defn send-invite-notification!
  "Send a notification to the invited foreman"
  [foreman-user command]
  (notifications/notify! :invite-foreman (assoc command :recipients [foreman-user])))

(defn authorize-and-send-invite-notification!
  "Authorize the foreman and send the invite notification"
  [foreman-user application command]
  (try
    (authorize-to-app! application foreman-user command)
    (send-invite-notification! foreman-user command)
    (catch Exception e
      (error e "Error when inviting to foreman application:" (.getMessage e)))))

(defn update-foreman-task-on-linked-app! [application {foreman-app-id :id} task-id {created :created}]
  (when-let [task (util/find-by-id task-id (:tasks application))]
    (doc-persistence/persist-model-updates application "tasks" task [[[:asiointitunnus] foreman-app-id]] created)))

(defn preprocess-foreman-role
  "Foreman role strings can vary in capitalization and style depending on source"
  [role-string]
  (when (string? role-string)
    (let [lower-case (-> role-string ss/trim ss/lower-case)]
      (case lower-case
        "iv-työnjohtaja"  "IV-työnjohtaja"
        "kvv-työnjohtaja" "KVV-työnjohtaja"
        lower-case))))

(defn- match-required-role
  "Returns the 'required' version of the role given or nil if not required"
  [role-string required-roles]
  (->> required-roles
       (filter #(= (preprocess-foreman-role %) (preprocess-foreman-role role-string)))
       first))

(defn- unmet-foreman-info-for-role
  "Generates a map containing data for the foreman table for roles not met by any foreman application"
  [role]
  {:unmet-role     true
   :required       true
   :role-name      role
   :lupapiste-role (preprocess-foreman-role role)})

(defn add-required-roles-to-foreman-info
  "Takes a collection of foreman applications processed with foreman-application-info
   and adds items for required foremen from the main application's verdicts.
   If a foreman of a required type already exists in the data, just flags the existing item as required instead of
   adding a new item.
   Also processes the new tyonjohtaja-v3 documents found on the main application."
  [application foreman-apps]
  (let [;; Gather the required foreman roles
        get-role-codes     (fn [tasks] (->> tasks
                                            (map #(get-in % [:data :kuntaRoolikoodi :value]))
                                            (filter some?)
                                            (map preprocess-foreman-role)
                                            (into #{})))

        foreman-tasks      (->> (:tasks application)
                                (filter #(= "task-vaadittu-tyonjohtaja" (get-in % [:schema-info :name]))))

        required-roles     (get-role-codes foreman-tasks)
        migrated-roles     (get-role-codes (filter :migrated-foreman-task foreman-tasks))

        new-foreman-docs   (get-foreman-documents-v3 application)

        ;; Gather already filled roles
        app-id-to-role     (->> foreman-apps
                                (filter #(not= "rejected" (get-in % [:latest-verdict-status :status])))
                                (map #(vec [(:id %)
                                            (->> (:documents %)
                                                 first ; Documents already filtered by foreman-application-info
                                                 :data
                                                 :kuntaRoolikoodi
                                                 :value)]))
                                (into {}))

        v3-item-to-role-fn (fn [item]
                             (-> item
                                 :documents
                                 first
                                 :data
                                 :kuntaRoolikoodi
                                 :value))

        app-id-to-req      (->> app-id-to-role
                                (map (fn [[id role]] [id (match-required-role role required-roles)]))
                                (into {}))

        ;; Gather roles not filled yet
        unmet-role-reqs    (->> app-id-to-req
                                (map second)
                                (into #{})
                                (set/difference required-roles))

        ;; Combine for a list of met and unmet foreman roles
        enriched-apps      (map #(assoc % :required (boolean (get app-id-to-req (:id %)))) foreman-apps)

        foreman-v3-items   (->> new-foreman-docs
                                (map (partial foreman-util/foreman-application-info-for-v3 application)))

        foreman-v3-roles   (->> foreman-v3-items
                                (map v3-item-to-role-fn)
                                (into #{}))

        enriched-v3-items  (->> foreman-v3-items
                                (map #(assoc % :required (boolean (match-required-role (v3-item-to-role-fn %) required-roles)))))

        unmet-apps         (->> unmet-role-reqs
                                (remove foreman-v3-roles)
                                (map unmet-foreman-info-for-role)
                                (map #(assoc % :migrated (contains? migrated-roles (:lupapiste-role %)))))]

    (->> (concat enriched-apps unmet-apps enriched-v3-items)
         (map #(if (:role-name %) % (assoc % :role-name (get app-id-to-role (:id %) ""))))
         (sort-by (juxt (comp some? :id) (comp ss/lower-case str :role-name))))))

;; Notifications
(def ^:private foreman-app-keys [:foreman :foremanRole])

(def foreman-termination {:subject-key   "foreman-termination"
                          :template      "foreman-termination.md"
                          :recipients-fn (fn [{:keys [application foreman-app foreman-doc]}]
                                           (map enrich-info-with-lang (conj (get-applicant-infos application)
                                                                            (if foreman-app
                                                                              (get-foreman-info foreman-app)
                                                                              (get-foreman-doc-info foreman-doc)))))
                          :model-fn      (fn [{:keys [foreman-app foreman-doc] :as command} conf recipient]
                                           (let [foreman      (if foreman-app
                                                                (:foreman foreman-app)
                                                                (foreman/tj-document->fullname foreman-doc))
                                                 foreman-role (if foreman-app
                                                                (:foremanRole foreman-app)
                                                                (foreman/foreman-document->role foreman-doc))]
                                             (-> {:foreman foreman}
                                                 (assoc :foremanRole #(->> foreman-role
                                                                           (str "osapuoli.tyonjohtaja.kuntaRoolikoodi.")
                                                                           (i18n/localize %)))
                                                 (merge (notifications/create-app-model command conf recipient)))))})

(notifications/defemail :foreman-termination foreman-termination)

(defn send-termination-email!
  [{:keys                                   [application system]
    {:keys [foreman-app-id foreman-doc-id]} :data} _status]
  (let [foreman-app (when foreman-app-id (mongo/by-id :applications foreman-app-id foreman-app-keys))
        foreman-doc (when foreman-doc-id (domain/get-document-by-id application foreman-doc-id))]
    (if (or foreman-doc foreman-app)
      (notifications/notify! :foreman-termination (util/assoc-when
                                                    {:application application
                                                     :system      system}
                                                    :foreman-app foreman-app
                                                    :foreman-doc foreman-doc))
      (warn "foreman app or doc missing:" foreman-app-id foreman-doc-id))))

;; Termination

(defn get-foreman-termination-state
  "Returns true if the given foreman is currently requested for termination."
  [foreman-app]
  (keyword (get-in foreman-app [:foremanTermination :state] :not-requested)))

;; TODO LPK-7297 refactor foreman assignments for v3
(defn upsert-foreman-assignments
  [command {foreman-app-id :id :as foreman-app}]
  (let [{:keys [kuntaRoolikoodi
                henkilotiedot]} (-> (domain/get-document-by-name foreman-app
                                                                 "tyonjohtaja-v2")
                                    :data unwrap)
        foreman-name (ss/join-non-blanks " " [(:etunimi henkilotiedot)
                                              (:sukunimi henkilotiedot)])
        reason       (-> command :data :reason ss/trim)]
    (factory/process-foreman-automatic-assignments command
                                                   foreman-app-id
                                                   {:role-code    kuntaRoolikoodi
                                                    :foreman-name foreman-name
                                                    :reason       reason})))

;; TODO LPK-7297 refactor foreman assignments for v3
(defn remove-foreman-assignment-target [{:keys [organization application]} foreman-app-id]
  (when (:assignments-enabled @organization)
    (assi/remove-target-from-assignments (:id application) foreman-app-id)))

(defn commit-foreman-termination-changes [{:keys [created]} foreman-app termination-changes]
  (update-application (action/application->command foreman-app)
                      {$set (->> termination-changes
                                 (map (fn [[k v]]
                                        [(util/kw-path :foremanTermination k) v]))
                                 (into {:modified created}))}))

(defn commit-foreman-v3-termination-changes [{:keys [created user]} application document-id termination-changes]
  (let [foreman-doc        (domain/get-document-by-id application document-id)
        ;; When termination is confirmed and document is in "declared" state, transition to "acknowledged" state
        state-update?      (and (= "confirmed" (:state termination-changes))
                                (= "declared" (:state foreman-doc)))
        termination-update (->> termination-changes
                                (map (fn [[k v]]
                                       [(util/kw-path :documents.$.foremanTermination k) v]))
                                (into {:modified created}))
        mongo-updates      (cond-> {$set termination-update}
                             state-update? (-> (update $set assoc :documents.$.state "acknowledged")
                                               (assoc $push {:documents.$.changeLog {:field :state
                                                                                     :value "acknowledged"
                                                                                     :user  (usr/summary user)
                                                                                     :ts    created}})))]
    (update-application (action/application->command application)
                        {:documents {$elemMatch {:id document-id}}}
                        mongo-updates)))

(defn request-foreman-termination
  "Sets the foreman in a state where an authority can confirm the termination of their
  responsibilities. Returns map that can be passed to `commit-foreman-termination-changes`."
  [{:keys [user application created]} foreman-app-or-doc reason]
  (let [{started :started} (foreman-util/get-foreman-responsibility-timestamps foreman-app-or-doc application)]
    (when-not (= :not-requested (get-foreman-termination-state foreman-app-or-doc))
      (error-and-fail! (format "Foreman app '%s' already requested for termination"
                               (:id foreman-app-or-doc)) :error.unknown))
    (util/strip-nils {:state      "requested"
                      :reason     (ss/trim reason)
                      :requester  (usr/summary user)
                      :request-ts created
                      :started    started})))

(defn confirm-foreman-termination
  "Executes the termination of responsibilities for a foreman. Returns map that can be passed to
  `commit-foreman-termination-changes`."
  ([{:keys [user]} foreman-app-or-doc check-if-requested? ended]
   (when-not (or (not check-if-requested?)
                 (= :requested (get-foreman-termination-state foreman-app-or-doc)))
     (error-and-fail! (format "Foreman app '%s' not requested for termination"
                              (:id foreman-app-or-doc)) :error.unknown))
   {:ended     ended
    :confirmer (usr/summary user)
    :state     "confirmed"})
  ([{:keys [created] :as command} foreman-app check-if-requested]
   (confirm-foreman-termination command foreman-app check-if-requested created)))

(defn terminate-foreman
  "Terminates the responsibilities of a foreman without the request/confirmation phases. Returns map
  that can be passed to `commit-foreman-termination-changes`."
  [command foreman-app-or-doc reason ended]
  (when-not (= :not-requested (get-foreman-termination-state foreman-app-or-doc))
    (error-and-fail! (format "Foreman app '%s' already requested for termination"
                             (:id foreman-app-or-doc)) :error.unknown))
  (merge (request-foreman-termination command foreman-app-or-doc reason)
         (confirm-foreman-termination command foreman-app-or-doc false ended)))

(defn filter-termination-reason
  "Only authorities and the party requesting termination should see the termination reason unless it is requested"
  [user foreman-apps]
  (->> foreman-apps
       (map (fn [{:keys [termination-reason termination-requested termination-requester organization] :as foreman-app}]
              (cond-> foreman-app
                (and (some? termination-reason)
                     (not termination-requested)
                     (not= (:id user) termination-requester)
                     (not (usr/user-is-authority-in-organization? user organization)))
                (dissoc :termination-reason))))))

(defn vastattavat-tyotehtavat [doc lang]
  (let [filtered-doc         (foreman-util/filter-foreman-doc-responsibilities doc)
        vastattavat-data     (tools/unwrapped (get-in filtered-doc [:data :vastattavatTyotehtavat]))
        vastattavat-loc-keys (reduce (fn [m {:keys [name i18nkey]}]
                                       (assoc m (keyword name) i18nkey))
                                     {}
                                     (get-in schemas/vastattavat-tyotehtavat-tyonjohtaja-v2 [0 :body]))]
    (->> vastattavat-data
         (reduce-kv (fn [result key val]
                      (case (keyword key)
                        :muuMika result
                        :muuMikaValue (cond-> result
                                        (and (:muuMika vastattavat-data)
                                             (ss/not-blank? val))
                                        (conj val))
                        (cond-> result
                          (true? val)
                          (conj (i18n/localize lang (get vastattavat-loc-keys key))))))
                    []))))

(defn- get-termination-document-filename
  [lang application]
  (i18n/localize-and-fill lang :foreman.termination.document.filename (:id application)))

(defn- attach-termination-document!
  [{:keys [lang user created system]} application app-or-doc-id ^ByteArrayInputStream pdf-file-stream]
  {:pre [(map? system)]}
  (files/with-temp-file temp-file
    (io/copy pdf-file-stream temp-file)
    (let [attachment-opts {:attachment-type   {:type-group :paatoksenteko
                                               :type-id    :paatos}
                           :source            {:type          "foreman-termination-verdict"
                                               :app-or-doc-id app-or-doc-id}
                           :required          false
                           :read-only         true
                           :locked            true
                           :created           created
                           :state             :ok
                           :set-app-modified? false}
          file-opts       {:filename (get-termination-document-filename lang application)
                           :size     (.length temp-file)
                           :content  temp-file}
          {:keys [id]}    (attachment/upload-and-attach! {:application application
                                                          :user        user
                                                          :system      system}
                                                      attachment-opts
                                                      file-opts)]
      (info "Created foreman termination attachment:" id))))

(defn create-and-attach-termination-document!
  "Creates a PDF attachment describing the fact that a foreman was terminated
  and attaches it to the corresponding foreman application."
  [{:keys [lang organization created application system] :as command} foreman-app-or-doc]
  {:pre [(map? system)]}
  (try
    (info "Creating foreman termination document")
    (let [{:keys [ok pdf-file-stream]} (foreman-pdf/generate-termination-document
                                         {:foreman-app-or-doc foreman-app-or-doc
                                          :lang               lang
                                          :now                created
                                          :organization       (force organization)
                                          :system             system})]
      (if (not ok)
        (error "Failed to create termination document")
        (attach-termination-document! command (if (:tj-document foreman-app-or-doc) application foreman-app-or-doc) (:id foreman-app-or-doc) pdf-file-stream)))
    (catch Exception ex
      (error ex "Failed to create & attach termination document"))))

(defn new-foreman-document
  "Returns a new foreman document (tyonjohtaja-v3) with default values and optionally sets the foreman role"
  [foremanRole {:keys [created] :as command}]
  (let [schema         (schemas/get-schema 1 "tyonjohtaja-v3")
        schema-flags   (tools/resolve-schema-flags command)
        default-values (tools/create-document-data schema (partial tools/default-values schema-flags))
        document       {:id          (mongo/create-id)
                        :created     created
                        :schema-info (select-keys (:info schema) model/db-schema-info-keys)
                        :data        default-values
                        :type        "notification"
                        ;; Setting :tj-document to true carries the same information as having
                        ;; :schema-info.name equal to "tyonjohtaja-v3". A lot of code assume
                        ;; these two conditions are either both true or both false. Key difference
                        ;; is that there is a mongo index for the field :tj-document.
                        :tj-document true}]
    (if-not (ss/blank? foremanRole)
      (assoc-in document [:data :kuntaRoolikoodi :value] foremanRole)
      document)))

(defschema NewForemanEmailInvite
  (st/merge
    {:id           ssc/NonBlankStr
     :foremanRole  ssc/NonBlankStr
     :foremanEmail ssc/NonBlankStr}
    (st/optional-keys
      ;; NOTE: This `taskId` parameter seems to be only used when `new-foreman-invite` is disabled.
      {:taskId ssc/NonBlankStr})))

(defschema NewForemanUserInvite
  (st/merge
    {:id          ssc/NonBlankStr
     :foremanRole ssc/NonBlankStr
     ;; user id
     :foremanUser ssc/NonBlankStr}
    (st/optional-keys
      ;; NOTE: This `taskId` parameter seems to be only used when `new-foreman-invite` is disabled.
      {:taskId ssc/NonBlankStr})))

(defn- common-user-checks
  [user application]
  (cond
    ;; Can't invite if the company denies invitations
    (some-> user
            (get-in [:company :id])
            (->> (company/company-denies-invitations? application)))
    (fail :invite.company-denies-invitation)

    ;; Can't assign as foreman if the invite has not been accepted (yet)
    (not (domain/no-pending-invites? application (:id user)))
    (fail :invite.already-has-auth)))

(defn validate-foreman-user-params
  [{{:keys [foremanUser]} :data
    application           :application}]
  (when foremanUser
    (let [user (usr/get-user-by-id foremanUser)]
      (cond
        ;; Can't invite a specific user if the user does not exist
        (nil? user)
        (fail :invite.user-not-found)

        ;; Can't assign as foreman if the user is not already a party on the app
        (not (auth/has-some-auth-role? application (:id user) #{:writer :foreman}))
        (do (log/infof "Cannot accept %s, insufficient roles." (:id user))
            (fail :invite.user-not-found))

        :else
        (common-user-checks user application)))))

(defn validate-foreman-email-params
  [{{:keys [foremanEmail]} :data
    application            :application
    :as                    command}]
  (when foremanEmail
    (or
      ;; Check the email first before we do anything with it
      (action/email-validator :foremanEmail command)
      ;; Check if happen to have a matching existing user and if so -> validate
      (when-let [user (usr/get-user-by-email (ss/canonize-email foremanEmail))]
        (cond
          ;; User tried to invite using email but the target user is already authorized?
          (auth/has-auth? application (:id user))
          (fail :invite.already-has-auth)

          :else
          (common-user-checks user application))))))

(defn user-authorized-as-foreman? [user-email foreman-doc]
  (boolean (some->> user-email
                    (= (get-foreman-email foreman-doc)))))

(defn is-foreman-user?
  "Pre-check that fails if the selected foreman is not the current user"
  [{:keys [user application data]}]
  (when-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
    (when-not (user-authorized-as-foreman? (:email user) foreman-doc)
      (fail :error.no-permission-to-modify-the-user))))

(defn is-setting-enabled?
  "Pre-check that fails if the selected foreman submit setting is not set"
  [setting {:keys [application data organization]}]
  (when-let [foreman-doc (domain/get-document-by-id application (:foreman-doc-id data))]
    (let [foreman-submit-settings  (-> organization
                                       force
                                       :foreman-submit-settings
                                       foreman/with-submit-settings-defaults)]
      (when-not (-> foreman-doc :type keyword foreman-submit-settings setting)
        (fail :error.setting-not-active-for-foreman)))))
