(ns lupapalvelu.foreman
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [lupapalvelu.action :as action :refer [update-application]]
            [lupapalvelu.application :as app]
            [lupapalvelu.assignment :as assi]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.company :as company]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permissions :refer [defcontext]]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.strings :refer [defalias] :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [error]]))

(defalias get-linked-foreman-applications foreman-util/get-linked-foreman-applications)
(defalias get-linked-foreman-applications-by-id foreman-util/get-linked-foreman-applications-by-id)
(defalias select-latest-verdict-status foreman-util/select-latest-verdict-status)
(defalias foreman-app? foreman-util/foreman-app?)

(defn no-foreman
  "Pre-checker that fails if the user's role in the application is foreman."
  [{:keys [user application]}]
  (when (auth/has-auth-role? application (:id user) "foreman")
    (fail :error.foreman)))

(defcontext foreman-app-context [{{user-id :id} :user application :application}]
  ;; Some permissions are added in foreman applications by user application role
  (when (foreman-app? application)
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

(defn- get-foreman-info [foreman-app]
  (let [docs  (:documents foreman-app)
        doc   (or (domain/get-document-by-name docs "tyonjohtaja-v2")
                  (domain/get-document-by-name docs "tyonjohtaja"))
        data  (:data doc)]
    {:email     (get-in data [:yhteystiedot :email :value] "")
     :firstName (get-in data [:henkilotiedot :etunimi :value] "")
     :lastName  (get-in data [:henkilotiedot :sukunimi :value] "")}))

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
      (applicant-field? user applicants :id)    nil ; Applicant selected from dropdown, can terminate anyone
      (applicant-field? user applicants :email) nil ; Fallback if not dropdown selected or company contact
      (nil? (:foreman-app-id data))             nil ; Pre-check must work without data
      (= (:email user) (:foreman-email data))   nil ; Foreman can terminate themselves
      :else                                     (fail :error.unauthorized))))

(defn user-is-authority-for-foreman-app
  "Pre-check for making sure that the user has the appropriate authority for the foreman application.
   Note that checking the actual app's permission is assumed to have been done in the command's permissions."
  [{:keys [user data]}]
  (when-let [foreman-app-id (:foreman-app-id data)]
    (when-not (some->> (mongo/by-id :applications foreman-app-id [:organization])
                       :organization
                       (usr/user-is-authority-in-organization? user))
      (fail :error.unauthorized))))

(defn foreman-state-is
  "Pre-check that fails if the foreman in question is not in an allowed state"
  [allowed-state? {:keys [data]}]
  (when-let [foreman-app-id (:foreman-app-id data)]
    (when-not (some-> (mongo/by-id :applications foreman-app-id [:state :verdicts :pate-verdicts])
                      (foreman-util/select-latest-verdict-status)
                      :status
                      (allowed-state?))
      (fail :error.foreman))))

(defn other-project-document [application timestamp]
  (let [building-doc (domain/get-document-by-name application "uusiRakennus")
        kokonaisala (get-in building-doc [:data :mitat :kokonaisala :value])]
    {:luvanNumero {:value (:id application)
                   :modified timestamp}
     :katuosoite {:value (:address application)
                  :modified timestamp}
     :rakennustoimenpide {:value (get-in application [:primaryOperation :name])
                          :modified timestamp}
     :kokonaisala {:value kokonaisala
                   :modified timestamp}
     :autoupdated {:value true
                   :modified timestamp}}))

(defn- not-finnish-hetu? [foreman-application]
  (let [foreman-doc (domain/get-document-by-name foreman-application "tyonjohtaja-v2")]
    (get-in foreman-doc [:data :henkilotiedot :not-finnish-hetu :value])))

(defn- get-foreman-hetu [foreman-application]
  (let [foreman-doc (domain/get-document-by-name foreman-application "tyonjohtaja-v2")]
    (get-in foreman-doc [:data :henkilotiedot :hetu :value])))

(defn- get-foreman-ulkomainen-hetu [foreman-application]
  (let [foreman-doc (domain/get-document-by-name foreman-application "tyonjohtaja-v2")]
    (get-in foreman-doc [:data :henkilotiedot :ulkomainenHenkilotunnus :value])))

(defn- get-hetu-based-on-hetu-type [foreman-application]
  (if (not-finnish-hetu? foreman-application)
    (get-foreman-ulkomainen-hetu foreman-application)
    (get-foreman-hetu foreman-application)))

(defn- get-foreman-applications [foreman-application]
  (let [foreman-hetu (get-hetu-based-on-hetu-type foreman-application)
        path (if (not-finnish-hetu? foreman-application)
               "data.henkilotiedot.ulkomainenHenkilotunnus.value"
               "data.henkilotiedot.hetu.value")]
    (when-not (ss/blank? foreman-hetu)
      (mongo/select :applications
                    {"primaryOperation.name" "tyonjohtajan-nimeaminen-v2"
                     :state {$in ["acknowledged" "foremanVerdictGiven" "appealed"]}
                     :documents {$elemMatch {"schema-info.name" "tyonjohtaja-v2"
                                             path               foreman-hetu}}}
                    [:created :documents :municipality]))))

(defn get-foreman-document [foreman-application]
  (domain/get-document-by-name foreman-application "tyonjohtaja-v2"))

(defn get-foreman-project-applications
  "Based on the passed foreman application, fetches all project applications that have the same foreman as in
  the passed foreman application (personal id is used as key). Returns all the linked applications as a list"
  [foreman-application]
  (let [foreman-apps    (->> (get-foreman-applications foreman-application)
                             (remove #(= (:id %) (:id foreman-application))))
        foreman-app-ids (map :id foreman-apps)
        links           (mongo/select :app-links {:link {$in foreman-app-ids}})
        linked-app-ids  (remove (set foreman-app-ids) (distinct (mapcat #(:link %) links)))]
    (mongo/select :applications {$and [{:_id {$in linked-app-ids}} {:state {$ne :closed}}]} [:documents :address :primaryOperation])))

(defn- get-linked-app-operations [foreman-app-id link]
  (let [other-id  (first (remove #{foreman-app-id} (:link link)))]
    (get-in link [(keyword other-id) :apptype])))

(defn- unwrap [wrapped-value]
  (let [value (tools/unwrapped wrapped-value)]
    (if (and (or (coll? value) (string? value)) (empty? value))
      "ei tiedossa"
      value)))

(defn- get-history-data-from-app [app-links foreman-app]
  (let [foreman-doc     (domain/get-document-by-name foreman-app "tyonjohtaja-v2")

        municipality    (:municipality foreman-app)
        difficulty      (unwrap (get-in foreman-doc [:data :patevyysvaatimusluokka]))
        foreman-role    (unwrap (get-in foreman-doc [:data :kuntaRoolikoodi]))
        limitedApproval (unwrap (get-in foreman-doc [:data :tyonjohtajanHyvaksynta :tyonjohtajanHyvaksynta]))
        limitedApproval (if limitedApproval
                          "yes"
                          nil)

        relevant-link   (first (filter #(some #{(:id foreman-app)} (:link %)) app-links))
        project-app-id  (first (remove #{(:id foreman-app)} (:link relevant-link)))
        operation       (get-linked-app-operations (:id foreman-app) relevant-link)]

    {:municipality    municipality
     :difficulty      difficulty
     :jobDescription  foreman-role
     :operation       operation
     :limitedApproval limitedApproval
     :linkedAppId     project-app-id
     :foremanAppId    (:id foreman-app)
     :created         (:created foreman-app)}))

(defn get-foreman-history-data [foreman-app]
  (let [foreman-apps       (->> (get-foreman-applications foreman-app)
                                (remove #(= (:id %) (:id foreman-app))))
        links              (mongo/select :app-links {:link {$in (map :id foreman-apps)}})]
    (map (partial get-history-data-from-app links) foreman-apps)))

(defn- reduce-to-highlights [history-group]
  (let [history-group (sort-by :created history-group)
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

(defn get-foreman-reduced-history-data [foreman-app]
  (let [history-data    (get-foreman-history-data foreman-app)
        grouped-history (group-by (juxt :municipality :jobDescription :limitedApproval) history-data)]
    (mapcat (fn [[_ group]] (reduce-to-highlights group)) grouped-history)))


(defn allow-foreman-only-in-foreman-app
  "If user has :foreman role in current application, check that the application is a foreman application."
  [{:keys [application user]}]
  (when (and (auth/has-auth-role? application (:id user) :foreman)
             (not (foreman-app? application)))
    unauthorized))

(defn notice?
  "True if application is foreman application and of type notice (ilmoitus)"
  [application]
  (and
    (foreman-app? application)
    (= "tyonjohtaja-ilmoitus" (:permitSubtype application))))

(defn validate-foreman-submittable [application link-permit]
  (when-not (app/submitted? application)
    (when link-permit
      (when-not (app/submitted? link-permit)
        (fail :error.not-submittable.foreman-link)))))

(defn- validate-notice-or-application [{subtype :permitSubtype :as application}]
  (when (and (foreman-app? application) (ss/blank? subtype))
    (fail :error.foreman.type-not-selected)))

(defn- validate-notice-submittable [application link-permit]
  (when (notice? application)
    (when link-permit
      (when-not (states/post-verdict-states (keyword (:state link-permit)))
        (fail :error.foreman.notice-not-submittable)))))

(defn validate-application
  "Validates foreman applications. Returns nil if application is OK, or fail map."
  [application]
  (when (foreman-app? application)
    (let [link        (some #(when (= (:type %) "lupapistetunnus") %) (:linkPermitData application))
          link-permit (when link
                        (mongo/select-one :applications {:_id (:id link)} {:state 1}))]
      (or
        (validate-notice-or-application application)
        (validate-notice-submittable application link-permit)
        (validate-foreman-submittable application link-permit)))))

(defn new-foreman-application [{:keys [created application] :as command}]
  (-> (app/do-create-application
       (assoc command :data {:operation "tyonjohtajan-nimeaminen-v2"
                             :x (-> application :location first)
                             :y (-> application :location second)
                             :address (:address application)
                             :propertyId (:propertyId application)
                             :municipality (:municipality application)
                             :infoRequest false
                             :messages []}))
      (assoc :opened (if (util/pos? (:opened application)) created nil))))

(defn- cleanup-hakija-doc [doc]
  (let [schema-name (op/get-operation-metadata :tyonjohtajan-nimeaminen-v2 :applicant-doc-schema)]
    (-> doc
      (assoc :id (mongo/create-id))
      (assoc-in [:schema-info :name]  schema-name)
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
  (let [hankkeen-kuvaus      (get-application-description application)
        hankkeen-kuvaus-doc  (first (domain/get-documents-by-subtype (:documents foreman-app) "hankkeen-kuvaus"))
        hankkeen-kuvaus-doc  (if hankkeen-kuvaus
                               (assoc-in hankkeen-kuvaus-doc [:data :kuvaus :value] hankkeen-kuvaus)
                               hankkeen-kuvaus-doc)

        tyonjohtaja-doc      (domain/get-document-by-name foreman-app "tyonjohtaja-v2")
        tyonjohtaja-doc      (if-not (ss/blank? role)
                               (assoc-in tyonjohtaja-doc [:data :kuntaRoolikoodi :value] role)
                               tyonjohtaja-doc)

        hakija-docs          (domain/get-applicant-documents (:documents application))
        hakija-docs          (map cleanup-hakija-doc hakija-docs)]
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
       :role "writer"})))

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
               "yritys"  (yritys-invite % auth)))
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

(defn- invite-to-linked-app! [linked-app foreman-user {user :user created :created :as command}]
  (update-application command
                      {:auth {$not {$elemMatch {:invite.user.username (:email foreman-user)}}}}
                      {$push {:auth (auth/create-invite-auth user foreman-user (:id linked-app) "foreman" created)}
                       $set  {:modified created}}))

(defn send-invite-notifications! [foreman-app foreman-user linked-app command]
  (try
    (when (and foreman-user (not (auth/has-auth? linked-app (:id foreman-user))))
      (invite-to-linked-app! linked-app foreman-user command))
    (copy-app/send-invite-notifications! foreman-app command)
    (catch Exception e
      (error "Error when inviting to foreman application:" e))))

(defn update-foreman-task-on-linked-app! [application {foreman-app-id :id} task-id {created :created}]
  (when-let [task (util/find-by-id task-id (:tasks application))]
    (doc-persistence/persist-model-updates application "tasks" task [[[:asiointitunnus] foreman-app-id]] created)))


(defn- preprocess-foreman-role
  "Foreman role strings can vary in capitalization and style depending on source"
  [role-string]
  (when (string? role-string)
    (let [lower-case (-> role-string str/trim str/lower-case)]
      (case lower-case
        "iv-ty\u00f6njohtaja"  "IV-ty\u00f6njohtaja"
        "kvv-ty\u00f6njohtaja" "KVV-ty\u00f6njohtaja"
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
  {:unmet-role      true
   :required        true
   :role-name       role
   :lupapiste-role  (preprocess-foreman-role role)})

(defn add-required-roles-to-foreman-info
  "Takes a collection of foreman applications processed with foreman-application-info
   and adds items for required foremen from the main application's verdicts.
   If a foreman of a required type already exists in the data, just flags the existing item as required instead of
   adding a new item."
  [application foreman-apps]
  (let [;; Gather the required foreman roles
        get-role-codes  (fn [tasks] (->> tasks
                                         (map #(get-in % [:data :kuntaRoolikoodi :value]))
                                         (filter some?)
                                         (map preprocess-foreman-role)
                                         (into #{})))

        foreman-tasks   (->> (:tasks application)
                             (filter #(= "task-vaadittu-tyonjohtaja" (get-in % [:schema-info :name]))))

        required-roles  (get-role-codes foreman-tasks)
        migrated-roles  (get-role-codes (filter :migrated-foreman-task foreman-tasks))

        ;; Gather already filled roles
        app-id-to-role  (->> foreman-apps
                             (filter #(not= "rejected" (get-in % [:latest-verdict-status :status])))
                             (map #(vec [(:id %)
                                         (->> (:documents %)
                                              first ; Documents already filtered by foreman-application-info
                                              :data
                                              :kuntaRoolikoodi
                                              :value)]))
                             (into {}))
        app-id-to-req   (->> app-id-to-role
                             (map (fn [[id role]] [id (match-required-role role required-roles)]))
                             (into {}))

        ;; Gather roles not filled yet
        unmet-role-reqs (->> app-id-to-req
                             (map second)
                             (into #{})
                             (set/difference required-roles))

        ;; Combine for a list of met and unmet foreman roles
        enriched-apps   (map #(assoc % :required (boolean (get app-id-to-req (:id %)))) foreman-apps)
        unmet-apps      (->> unmet-role-reqs
                             (map unmet-foreman-info-for-role)
                             (map #(assoc % :migrated (contains? migrated-roles (:lupapiste-role %)))))]

    (->> (concat enriched-apps unmet-apps)
         (map #(if (:role-name %) % (assoc % :role-name (get app-id-to-role (:id %) ""))))
         (sort-by (juxt (comp some? :id) (comp str/lower-case str :role-name))))))

;; Notifications
(def foreman-termination {:subject-key    "foreman-termination"
                          :template       "foreman-termination.md"
                          :recipients-fn  (fn [{:keys [application foreman-app]}]
                                            (map enrich-info-with-lang (conj (get-applicant-infos application)
                                                                             (get-foreman-info foreman-app))))
                          :model-fn       (fn [{:keys [foreman-app] :as command} conf recipient]
                                            (-> (select-keys foreman-app [:foreman])
                                                (assoc :foremanRole #(->> (:foremanRole foreman-app)
                                                                          (str "osapuoli.tyonjohtaja.kuntaRoolikoodi.")
                                                                          (i18n/localize %)))
                                                (merge (notifications/create-app-model command conf recipient))))})

(notifications/defemail :foreman-termination foreman-termination)

;; Termination

(defn get-foreman-termination-state
  "Returns true if the given foreman is currently requested for termination."
  [foreman-app]
  (keyword (get-in foreman-app [:foremanTermination :state] :not-requested)))

(defn upsert-foreman-assignments
  [command {foreman-app-id :id :as foreman-app}]
  (let [{:keys [kuntaRoolikoodi
                henkilotiedot]} (-> (domain/get-document-by-name foreman-app
                                                                 "tyonjohtaja-v2")
                                    :data unwrap)
        foreman-name            (ss/join-non-blanks " " [(:etunimi henkilotiedot)
                                                         (:sukunimi henkilotiedot)])
        reason                  (-> command :data :reason ss/trim)]
    (factory/process-foreman-automatic-assignments command
                                                   foreman-app-id
                                                   {:role-code    kuntaRoolikoodi
                                                    :foreman-name foreman-name
                                                    :reason       reason})))

(defn remove-foreman-assignment-target [{:keys [organization application]} foreman-app-id]
    (when (:assignments-enabled @organization)
      (assi/remove-target-from-assignments (:id application) foreman-app-id)))

(defn commit-foreman-termination-changes [{:keys [created]} foreman-app termination-changes]
  (update-application (action/application->command foreman-app)
                      {$set (->> termination-changes
                                 (map (fn [[k v]]
                                        [(util/kw-path :foremanTermination k) v]))
                                 (into {:modified created}))}))

(defn request-foreman-termination
  "Sets the foreman in a state where an authority can confirm the termination of their
  responsibilities. Returns map that can be passed to `commit-foreman-termination-changes`."
  [{:keys [user application created]} foreman-app reason]
  (let [{started :started} (foreman-util/get-foreman-responsibility-timestamps foreman-app application)]
    (when-not (= :not-requested (get-foreman-termination-state foreman-app))
      (error-and-fail! (format "Foreman app '%s' already requested for termination"
                               (:id foreman-app)) :error.unknown))
    (util/strip-nils {:state      "requested"
                      :reason     (ss/trim reason)
                      :requester  (usr/summary user)
                      :request-ts created
                      :started    started})))

(defn confirm-foreman-termination
  "Executes the termination of responsibilities for a foreman. Returns map that can be passed to
  `commit-foreman-termination-changes`."
  [{:keys [user created]} foreman-app check-if-requested?]
  (when-not (or (not check-if-requested?)
                (= :requested (get-foreman-termination-state foreman-app)))
    (error-and-fail! (format "Foreman app '%s' not requested for termination"
                             (:id foreman-app)) :error.unknown))
  {:ended     created
   :confirmer (usr/summary user)
   :state     "confirmed"})

(defn terminate-foreman
  "Terminates the responsiblities of a foreman without the request/confirmation phases. Returns map
  that can be passed to `commit-foreman-termination-changes`."
  [command foreman-app reason]
  (when-not (= :not-requested (get-foreman-termination-state foreman-app))
    (error-and-fail! (format "Foreman app '%s' already requested for termination"
                             (:id foreman-app)) :error.unknown))
  (merge (request-foreman-termination command foreman-app reason)
         (confirm-foreman-termination command foreman-app false)))

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
  (let [vastattavat-data (tools/unwrapped (get-in doc [:data :vastattavatTyotehtavat]))
        vastattavat-loc-keys (reduce (fn [m {:keys [name i18nkey]}]
                                       (assoc m (keyword name) i18nkey))
                                     {}
                                     (get-in schemas/vastattavat-tyotehtavat-tyonjohtaja-v2 [0 :body]))]
    (->> vastattavat-data
         (reduce-kv (fn [result key val]
                      (case (keyword key)
                        :muuMika      result
                        :muuMikaValue (cond-> result
                                        (and (:muuMika vastattavat-data)
                                             (ss/not-blank? val))
                                        (conj val))
                        (cond-> result
                          (true? val)
                          (conj (i18n/localize lang (get vastattavat-loc-keys key))))))
                    []))))
