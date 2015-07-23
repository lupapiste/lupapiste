(ns lupapalvelu.application-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn error fatal]]
            [clj-time.core :refer [year]]
            [clj-time.local :refer [local-now]]
            [clj-time.format :as tf]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.property :as p]
            [lupapalvelu.action :refer [defraw defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as a]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.document.commands :as commands]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.ktj :as ktj]
            [lupapalvelu.mongo :refer [$each] :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as user]))

;; Notifications

(notifications/defemail :application-state-change
                        {:subject-key    "state-change"
                         :application-fn (fn [{id :id}] (domain/get-application-no-access-checking id))})

;; Validators

(defn- validate-x [{{:keys [x]} :data}]
  (when (and x (not (< 10000 (util/->double x) 800000)))
    (fail :error.illegal-coordinates)))

(defn- validate-y [{{:keys [y]} :data}]
  (when (and y (not (<= 6610000 (util/->double y) 7779999)))
    (fail :error.illegal-coordinates)))

(defn operation-validator [{{operation :operation} :data}]
  (when-not (operations/operations (keyword operation)) (fail :error.unknown-type)))


(defn find-authorities-in-applications-organization [app]
  (mongo/select :users
                {(str "orgAuthz." (:organization app)) "authority", :enabled true}
                user/summary-keys
                (array-map :lastName 1, :firstName 1)))

(defquery application
  {:parameters       [:id]
   :states           action/all-states
   :user-roles       #{:applicant :authority :oirAuthority}
   :user-authz-roles action/all-authz-roles
   :org-authz-roles #{:authority :reader}}
  [{app :application user :user}]
  (if app
    (let [app (assoc app :allowedAttachmentTypes (attachment/get-attachment-types-for-application app))]
      (ok :application (a/post-process-app app user)
          :authorities (if (user/authority? user)
                         (map #(select-keys % [:id :firstName :lastName]) (find-authorities-in-applications-organization app))
                         [])
          :permitSubtypes (permit/permit-subtypes (:permitType app))))
    (fail :error.not-found)))

(defquery application-authorities
  {:user-roles #{:authority}
   :states     (action/all-states-but [:draft :closed :canceled]) ; the same as assign-application
   :parameters [:id]}
  [{application :application}]
  (let [authorities (find-authorities-in-applications-organization application)]
    (ok :authorities (map #(select-keys % [:id :firstName :lastName]) authorities))))

(def ktj-format (tf/formatter "yyyyMMdd"))
(def output-format (tf/formatter "dd.MM.yyyy"))

(defn- autofill-rakennuspaikka [application time]
  (when (and (not (= "Y" (:permitType application))) (not (:infoRequest application)))
    (when-let [rakennuspaikka (domain/get-document-by-type application :location)]
      (when-let [ktj-tiedot (ktj/rekisteritiedot-xml (:propertyId application))]
        (let [updates [[[:kiinteisto :tilanNimi] (or (:nimi ktj-tiedot) "")]
                       [[:kiinteisto :maapintaala] (or (:maapintaala ktj-tiedot) "")]
                       [[:kiinteisto :vesipintaala] (or (:vesipintaala ktj-tiedot) "")]
                       [[:kiinteisto :rekisterointipvm] (or
                                                          (try
                                                            (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                            (catch Exception e (:rekisterointipvm ktj-tiedot)))
                                                          "")]]
              schema (schemas/get-schema (:schema-info rakennuspaikka))
              updates (filter (fn [[update-path _]] (model/find-by-name (:body schema) update-path)) updates)]
          (commands/persist-model-updates
            application
            "documents"
            rakennuspaikka
            updates
            time))))))

(defquery party-document-names
          {:parameters [:id]
           :user-roles #{:applicant :authority}
           :states     action/all-application-states}
          [{application :application}]
          (let [documents (:documents application)
                initialOp (:name (:primaryOperation application))
                original-schema-names (-> initialOp keyword operations/operations :required)
                original-party-documents (a/filter-repeating-party-docs (:schema-version application) original-schema-names)]
            (ok :partyDocumentNames (conj original-party-documents (permit/get-applicant-doc-schema (permit/permit-type application))))))

(defcommand mark-seen
  {:parameters       [:id type]
   :input-validators [(fn [{{type :type} :data}] (when-not (a/collections-to-be-seen type) (fail :error.unknown-type)))]
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           action/all-application-states
   :pre-checks       [a/validate-authority-in-drafts]}
  [{:keys [data user created] :as command}]
  (update-application command {$set (a/mark-collection-seen-update user created type)}))

(defcommand mark-everything-seen
  {:parameters [:id]
   :user-roles #{:authority :oirAuthority}
   :states     (action/all-states-but [:draft])}
  [{:keys [application user created] :as command}]
  (update-application command {$set (a/mark-indicators-seen-updates application user created)}))

;;
;; Assign
;;

(defcommand assign-application
  {:parameters [:id assigneeId]
   :user-roles #{:authority}
   :states     (action/all-states-but [:draft :closed :canceled])}
  [{:keys [user created application] :as command}]
  (let [assignee (util/find-by-id assigneeId (find-authorities-in-applications-organization application))]
    (if (or assignee (ss/blank? assigneeId))
      (update-application command
                          {$set {:modified  created
                                 :authority (if assignee (user/summary assignee) (:authority domain/application-skeleton))}})
      (fail "error.user.not.found"))))

;;
;; Cancel
;;

(defn- remove-app-links [id]
  (mongo/remove-many :app-links {:link {$in [id]}}))

(defcommand cancel-inforequest
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :notified         true
   :on-success       (notify :application-state-change)
   :states           [:info]}
  [{:keys [created] :as command}]
  (update-application command
                      {$set {:modified created
                             :canceled created
                             :state    :canceled}})
  (remove-app-links id)
  (ok))

(defcommand cancel-application
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:applicant}
   :notified         true
   :on-success       (notify :application-state-change)
   :states           [:draft :info :open :submitted]}
  [{:keys [created] :as command}]
  (update-application command
                      {$set {:modified created
                             :canceled created
                             :state    :canceled}})
  (remove-app-links id)
  (ok))

(defcommand cancel-application-authority
  {:parameters       [id text lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :user-roles       #{:authority}
   :notified         true
   :on-success       (notify :application-state-change)
   :states           (action/all-states-but [:canceled :closed :answered])
   :pre-checks       [a/validate-authority-in-drafts]}
  [{:keys [created application] :as command}]
  (update-application command
    (util/deep-merge
      (when (seq text)
        (comment/comment-mongo-update
          (:state application)
          (str
            (i18n/localize lang "application.canceled.text") ". "
            (i18n/localize lang "application.canceled.reason") ": "
            text)
          {:type "application"}
          (-> command :user :role)
          false
          (:user command)
          nil
          created))
      {$set {:modified created
             :canceled created
             :state    :canceled}}))
  (remove-app-links id)
  (ok))


(defcommand request-for-complement
  {:parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:authority}
   :notified         true
   :on-success       (notify :application-state-change)
   :states           [:sent]}
  [{:keys [created] :as command}]
  (update-application command
                      {$set {:modified         created
                             :complementNeeded created
                             :state            :complement-needed}}))


(defn- do-submit [command application created]
  (update-application command
                      {$set {:state     :submitted
                             :modified  created
                             :opened    (or (:opened application) created)
                             :submitted (or (:submitted application) created)}})
  (try
    (mongo/insert :submitted-applications (-> application
                                            meta-fields/enrich-with-link-permit-data
                                            (dissoc :id)
                                            (assoc :_id (:id application))))
    (catch com.mongodb.MongoException$DuplicateKey e
      ; This is ok. Only the first submit is saved.
      )))

(defcommand submit-application
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:applicant :authority}
   :states           [:draft :open]
   :notified         true
   :on-success       (notify :application-state-change)
   :pre-checks       [domain/validate-owner-or-write-access
                      a/validate-authority-in-drafts]}
  [{:keys [application created] :as command}]
  (let [application (meta-fields/enrich-with-link-permit-data application)]
    (or
      (foreman/validate-notice-submittable application)
      (a/validate-link-permits application)
      (do-submit command application created))))

(defcommand refresh-ktj
  {:parameters [:id]
   :user-roles #{:authority}
   :states     (action/all-states-but [:draft])}
  [{:keys [application created]}]
  (autofill-rakennuspaikka application created)
  (ok))

(defcommand save-application-drawings
  {:parameters       [:id drawings]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           [:draft :info :answered :open :submitted :complement-needed]
   :pre-checks       [a/validate-authority-in-drafts]}
  [{:keys [created] :as command}]
  (when (sequential? drawings)
    (update-application command
                        {$set {:modified created
                               :drawings drawings}})))

(defn- make-marker-contents [id lang {:keys [location] :as app}]
  (merge
    {:id        (:id app)
     :title     (:title app)
     :location  {:x (first location) :y (second location)}
     :operation (->> (:primaryOperation app) :name (i18n/localize lang "operations"))
     :authName  (-> app
                    (domain/get-auths-by-role :owner)
                    first
                    (#(str (:firstName %) " " (:lastName %))))
     :comments  (->> (:comments app)
                     (filter #(not (= "system" (:type %))))
                     (map #(identity {:name (str (-> % :user :firstName) " " (-> % :user :lastName))
                                      :type (:type %)
                                      :time (:created %)
                                      :text (:text %)})))}
    (when-not (= id (:id app))
      {:link (str (env/value :host) "/app/" (name lang) "/authority#!/inforequest/" (:id app))})))

(defn- remove-irs-by-id [target-irs irs-to-be-removed]
  (remove (fn [ir] (some #(= (:id ir) (:id %)) irs-to-be-removed)) target-irs))

(defquery inforequest-markers
          {:parameters       [id lang x y]
           :user-roles       #{:authority :oirAuthority}
           :states           action/all-inforequest-states
           :input-validators [(partial action/non-blank-parameters [:id :x :y])]}
          [{:keys [application user]}]
          (let [x (util/->double x)
                y (util/->double y)
                inforequests (mongo/select :applications
                                           (merge
                                             (domain/application-query-for user)
                                             {:infoRequest true})
                                           [:title :auth :location :primaryOperation :secondaryOperations :comments])

                same-location-irs (filter
                                    #(and (== x (-> % :location first)) (== y (-> % :location second)))
                                    inforequests)

                inforequests (remove-irs-by-id inforequests same-location-irs)

                application-op-name (-> application :primaryOperation :name)

                same-op-irs (filter
                              (fn [ir]
                                (some #(= application-op-name (:name %)) (a/get-operations ir)))
                              inforequests)

                others (remove-irs-by-id inforequests same-op-irs)

                same-location-irs (map (partial make-marker-contents id lang) same-location-irs)
                same-op-irs (map (partial make-marker-contents id lang) same-op-irs)
                others (map (partial make-marker-contents id lang) others)]

            (ok :sameLocation same-location-irs :sameOperation same-op-irs :others others)
            ))


(defcommand create-application
  {:parameters       [:operation :x :y :address :propertyId]
   :user-roles       #{:applicant :authority}
   :notified         true                                   ; OIR
   :input-validators [(partial action/non-blank-parameters [:operation :address :propertyId])
                      (partial a/property-id-parameters [:propertyId])
                      operation-validator]}
  [{{:keys [infoRequest]} :data :keys [created] :as command}]
  (let [created-application (a/do-create-application command)]
    (a/insert-application created-application)
    (when (and (boolean infoRequest) (:openInfoRequest created-application))
      (open-inforequest/new-open-inforequest! created-application))
    (try
      (autofill-rakennuspaikka created-application created)
      (catch Exception e (error e "KTJ data was not updated")))
    (ok :id (:id created-application))))

(defn- add-operation-allowed? [_ application]
  (let [op (-> application :primaryOperation :name keyword)
        permit-subtype (keyword (:permitSubtype application))]
    (when-not (and (or (nil? op) (:add-operation-allowed (operations/operations op)))
                   (not= permit-subtype :muutoslupa))
      (fail :error.add-operation-not-allowed))))

(defcommand add-operation
  {:parameters       [id operation]
   :user-roles       #{:applicant :authority}
   :states           [:draft :open :submitted :complement-needed]
   :input-validators [operation-validator]
   :pre-checks       [add-operation-allowed?
                      a/validate-authority-in-drafts]}
  [{:keys [application created] :as command}]
  (let [op (a/make-op operation created)
        new-docs (a/make-documents nil created op application)
        organization (organization/get-organization (:organization application))]
    (update-application command {$push {:secondaryOperations  op
                                        :documents   {$each new-docs}
                                        :attachments {$each (a/make-attachments created op organization (:state application) (:tosFunction application))}}
                                 $set  {:modified created}})))

(defcommand update-op-description
  {:parameters [id op-id desc]
   :user-roles #{:applicant :authority}
   :states     [:draft :open :submitted :complement-needed]
   :pre-checks [a/validate-authority-in-drafts]}
  [{:keys [application] :as command}]
  (if (= (get-in application [:primaryOperation :id]) op-id)
    (update-application command {$set {"primaryOperation.description" desc}})
    (update-application command {"secondaryOperations" {$elemMatch {:id op-id}}} {$set {"secondaryOperations.$.description" desc}})))

(defcommand change-primary-operation
  {:parameters [id secondaryOperationId]
   :user-roles #{:applicant :authority}
   :states [:draft :open :submitted :complement-needed]
   :pre-checks [a/validate-authority-in-drafts]}
  [{:keys [application] :as command}]
  (let [old-primary-op (:primaryOperation application)
        old-secondary-ops (:secondaryOperations application)
        new-primary-op (first (filter #(= secondaryOperationId (:id %)) old-secondary-ops))
        secondary-ops-without-old-primary-op (remove #{new-primary-op} old-secondary-ops)
        new-secondary-ops (if old-primary-op ; production data contains applications with nil in primaryOperation
                            (conj secondary-ops-without-old-primary-op old-primary-op)
                            secondary-ops-without-old-primary-op)]
    (when-not new-primary-op
      (fail! :error.unknown-operation))
    (update-application command {$set {:primaryOperation new-primary-op
                                       :secondaryOperations new-secondary-ops}})))

(defcommand change-permit-sub-type
  {:parameters [id permitSubtype]
   :user-roles #{:applicant :authority}
   :states     [:draft :open :submitted :complement-needed]
   :pre-checks [permit/validate-permit-has-subtypes
                a/validate-authority-in-drafts]}
  [{:keys [application created] :as command}]
  (if-let [validation-errors (permit/is-valid-subtype (keyword permitSubtype) application)]
    validation-errors
    (update-application command
                        {$set {:permitSubtype permitSubtype
                               :modified      created}})))

(defn authority-if-post-verdict-state [{user :user} {state :state}]
  (when-not (or (user/authority? user)
                (contains? action/pre-verdict-states (keyword state)))
    (fail :error.unauthorized)))

(defcommand change-location
  {:parameters       [id x y address propertyId]
   :user-roles       #{:applicant :authority :oirAuthority}
   :states           [:draft :info :answered :open :submitted :complement-needed :verdictGiven :constructionStarted]
   :input-validators [(partial action/non-blank-parameters [:address])
                      (partial a/property-id-parameters [:propertyId])
                      validate-x validate-y]
   :pre-checks       [authority-if-post-verdict-state
                      a/validate-authority-in-drafts]}
  [{:keys [created application] :as command}]
  (if (= (:municipality application) (p/municipality-id-by-property-id propertyId))
    (do
      (update-application command
                          {$set {:location   (a/->location x y)
                                 :address    (ss/trim address)
                                 :propertyId propertyId
                                 :title      (ss/trim address)
                                 :modified   created}})
      (try (autofill-rakennuspaikka (mongo/by-id :applications id) (now))
           (catch Exception e (error e "KTJ data was not updated."))))
    (fail :error.property-in-other-muinicipality)))

;;
;; Link permits
;;

(defquery link-permit-required
          {:description "Dummy command for UI logic: returns falsey if link permit is not required."
           :parameters  [:id]
           :user-roles  #{:applicant :authority}
           :states      [:draft :open :submitted :complement-needed]
           :pre-checks  [(fn [_ application]
                           (when-not (a/validate-link-permits application)
                             (fail :error.link-permit-not-required)))]})

(defquery app-matches-for-link-permits
          {:parameters [id]
           :user-roles #{:applicant :authority}
           :states     (action/all-application-states-but [:sent :closed :canceled])}
          [{{:keys [propertyId] :as application} :application user :user :as command}]
          (let [application (meta-fields/enrich-with-link-permit-data application)
                ;; exclude from results the current application itself, and the applications that have a link-permit relation to it
                ignore-ids (-> application
                               (#(concat (:linkPermitData %) (:appsLinkingToUs %)))
                               (#(map :id %))
                               (conj id))
                results (mongo/select :applications
                                      (merge (domain/application-query-for user) {:_id             {$nin ignore-ids}
                                                                                  :infoRequest     false
                                                                                  :permitType      (:permitType application)
                                                                                  :secondaryOperations.name {$nin ["ya-jatkoaika"]}
                                                                                  :primaryOperation.name {$nin ["ya-jatkoaika"]}})

                                      [:permitType :address :propertyId])
                ;; add the text to show in the dropdown for selections
                enriched-results (map
                                   (fn [r] (assoc r :text (str (:address r) ", " (:id r))))
                                   results)
                ;; sort the results
                same-property-id-fn #(= propertyId (:propertyId %))
                with-same-property-id (vec (filter same-property-id-fn enriched-results))
                without-same-property-id (sort-by :text (vec (remove same-property-id-fn enriched-results)))
                organized-results (flatten (conj with-same-property-id without-same-property-id))
                final-results (map #(select-keys % [:id :text]) organized-results)]
            (ok :app-links final-results)))


(defn- validate-jatkolupa-zero-link-permits [_ application]
  (let [application (meta-fields/enrich-with-link-permit-data application)]
    (when (and (= :ya-jatkoaika (-> application :primaryOperation :name keyword))
               (pos? (-> application :linkPermitData count)))
      (fail :error.jatkolupa-can-only-be-added-one-link-permit))))

(defn- validate-link-permit-id [{:keys [data]} application]
  (let [application (meta-fields/enrich-with-link-permit-data application)
        ignore-ids (-> application
                       (#(concat (:linkPermitData %) (:appsLinkingToUs %)))
                       (#(map :id %))
                       (conj (:id application)))]
    (when (some
            #(= (:id %) (:linkPermitId data))
            (:appsLinkingToUs application))
      (fail :error.link-permit-already-having-us-as-link-permit))))

(defcommand add-link-permit
  {:parameters       ["id" linkPermitId]
   :user-roles       #{:applicant :authority}
   :states           (action/all-application-states-but [:sent :closed :canceled]) ;; Pitaako olla myos 'sent'-tila?
   :pre-checks       [validate-jatkolupa-zero-link-permits
                      validate-link-permit-id
                      a/validate-authority-in-drafts]
   :input-validators [(partial action/non-blank-parameters [:linkPermitId])
                      (fn [{d :data}] (when-not (mongo/valid-key? (:linkPermitId d)) (fail :error.invalid-db-key)))]}
  [{application :application}]
  (a/do-add-link-permit application (ss/trim linkPermitId))
  (ok))

(defcommand remove-link-permit-by-app-id
  {:parameters [id linkPermitId]
   :user-roles #{:applicant :authority}
   :states     [:draft :open :submitted :complement-needed :verdictGiven :constructionStarted]
   :pre-checks [a/validate-authority-in-drafts]} ;; Pitaako olla myos 'sent'-tila?
  [{application :application}]
  (if (mongo/remove :app-links (a/make-mongo-id-for-link-permit id linkPermitId))
    (ok)
    (fail :error.unknown)))


;;
;; Change permit
;;

(defcommand create-change-permit
  {:parameters ["id"]
   :user-roles #{:applicant :authority}
   :states     [:verdictGiven :constructionStarted]
   :pre-checks [(permit/validate-permit-type-is permit/R)]}
  [{:keys [created user application] :as command}]
  (let [muutoslupa-app-id (a/make-application-id (:municipality application))
        muutoslupa-app (merge application
                              {:id            muutoslupa-app-id
                               :created       created
                               :opened        created
                               :modified      created
                               :documents     (into [] (map
                                                         #(assoc % :id (mongo/create-id))
                                                         (:documents application)))
                               :state         (cond
                                                (user/authority? user) :open
                                                :else :draft)
                               :permitSubtype :muutoslupa}
                              (select-keys
                                domain/application-skeleton
                                [:attachments :statements :verdicts :comments :submitted :sent :neighbors
                                 :_statements-seen-by :_comments-seen-by :_verdicts-seen-by]))]
    (a/do-add-link-permit muutoslupa-app (:id application))
    (a/insert-application muutoslupa-app)
    (ok :id muutoslupa-app-id)))


;;
;; Continuation period permit
;;

(defn- get-tyoaika-alkaa-from-ya-app [app]
  (let [mainostus-viitoitus-tapahtuma-doc (:data (domain/get-document-by-name app "mainosten-tai-viitoitusten-sijoittaminen"))
        tapahtuma-name-key (when mainostus-viitoitus-tapahtuma-doc
                             (-> mainostus-viitoitus-tapahtuma-doc :_selected :value keyword))
        tapahtuma-data (when tapahtuma-name-key
                         (mainostus-viitoitus-tapahtuma-doc tapahtuma-name-key))]
    (if (:started app)
      (util/to-local-date (:started app))
      (or
        (-> app (domain/get-document-by-name "tyoaika") :data :tyoaika-alkaa-pvm :value)
        (-> tapahtuma-data :tapahtuma-aika-alkaa-pvm :value)
        (util/to-local-date (:submitted app))))))

(defn- validate-not-jatkolupa-app [_ application]
  (when (= :ya-jatkoaika (-> application :primaryOperation :name keyword))
    (fail :error.cannot-apply-jatkolupa-for-jatkolupa)))

(defcommand create-continuation-period-permit
  {:parameters ["id"]
   :user-roles #{:applicant :authority}
   :states     [:verdictGiven :constructionStarted]
   :pre-checks [(permit/validate-permit-type-is permit/YA) validate-not-jatkolupa-app]}
  [{:keys [created user application] :as command}]

  (let [continuation-app (a/do-create-application
                           (assoc command :data {:operation    "ya-jatkoaika"
                                                 :x            (-> application :location first)
                                                 :y            (-> application :location second)
                                                 :address      (:address application)
                                                 :propertyId   (:propertyId application)
                                                 :municipality (:municipality application)
                                                 :infoRequest  false
                                                 :messages     []}))
        continuation-app (merge continuation-app {:authority (:authority application)})
        ;;
        ;; ************
        ;; Lain mukaan hankeen aloituspvm on hakupvm + 21pv, tai kunnan paatospvm jos se on tata aiempi.
        ;; kts.  http://www.finlex.fi/fi/laki/alkup/2005/20050547 ,  14 a pykala
        ;; ************
        ;;
        tyoaika-alkaa-pvm (get-tyoaika-alkaa-from-ya-app application)
        tyo-aika-for-jatkoaika-doc (-> continuation-app
                                       (domain/get-document-by-name "tyo-aika-for-jatkoaika")
                                       (assoc-in [:data :tyoaika-alkaa-pvm :value] tyoaika-alkaa-pvm))
        docs (concat
               [(domain/get-document-by-name continuation-app "hankkeen-kuvaus-jatkoaika") tyo-aika-for-jatkoaika-doc]
               (map #(-> (domain/get-document-by-name application %) model/without-user-id) ["hakija-ya" "yleiset-alueet-maksaja"]))
        continuation-app (assoc continuation-app :documents docs)]

    (a/do-add-link-permit continuation-app (:id application))
    (a/insert-application continuation-app)
    (ok :id (:id continuation-app))))


(defn- validate-new-applications-enabled [command {:keys [permitType municipality] :as application}]
  (when application
    (let [scope (organization/resolve-organization-scope municipality permitType)]
      (when-not (:new-application-enabled scope)
        (fail :error.new-applications-disabled)))))

(defcommand convert-to-application
  {:parameters [id]
   :user-roles #{:applicant :authority}
   :states     action/all-inforequest-states
   :pre-checks [validate-new-applications-enabled]}
  [{:keys [user created application] :as command}]
  (let [op (:primaryOperation application)
        organization (organization/get-organization (:organization application))]
    (update-application command
                        {$set  {:infoRequest            false
                                :openInfoRequest        false
                                :state                  :open
                                :opened                 created
                                :convertedToApplication created
                                :documents              (a/make-documents user created op application)
                                :modified               created}
                         $push {:attachments {$each (a/make-attachments created op organization (:state application) (:tosFunction application))}}})
    (try (autofill-rakennuspaikka application created)
         (catch Exception e (error e "KTJ data was not updated")))))

(defn- validate-organization-backend-urls [_ {org-id :organization}]
  (when org-id
    (let [org (organization/get-organization org-id)]
      (if-let [conf (:vendor-backend-redirect org)]
        (->> (vals conf)
             (remove ss/blank?)
             (some util/validate-url))
        (fail :error.vendor-urls-not-set)))))

(defn get-vendor-backend-id [verdicts]
  (->> verdicts
       (remove :draft)
       (some :kuntalupatunnus)))

(defn- get-backend-and-lp-urls [org-id]
  (-> (organization/get-organization org-id)
      :vendor-backend-redirect
      (util/select-values [:vendor-backend-url-for-backend-id
                           :vendor-backend-url-for-lp-id])))

(defn- correct-urls-configured [_ {:keys [verdicts organization] :as application}]
  (when application
    (let [vendor-backend-id          (get-vendor-backend-id verdicts)
          [backend-id-url lp-id-url] (get-backend-and-lp-urls organization)
          lp-id-url-missing?         (ss/blank? lp-id-url)
          both-urls-missing?         (and lp-id-url-missing?
                                          (ss/blank? backend-id-url))]
      (if vendor-backend-id
        (when both-urls-missing?
          (fail :error.vendor-urls-not-set))
        (when lp-id-url-missing?
          (fail :error.vendor-urls-not-set))))))

(defraw redirect-to-vendor-backend
  {:parameters [id]
   :user-roles #{:authority}
   :states     action/post-submitted-states
   :pre-checks [validate-organization-backend-urls
                correct-urls-configured]}
  [{{:keys [verdicts organization]} :application}]
  (let [vendor-backend-id          (get-vendor-backend-id verdicts)
        [backend-id-url lp-id-url] (get-backend-and-lp-urls organization)
        url-parts                  (if (and vendor-backend-id
                                            (not (ss/blank? backend-id-url)))
                                     [backend-id-url vendor-backend-id]
                                     [lp-id-url id])
        redirect-url               (apply str url-parts)]
    (info "Redirecting from" id "to" redirect-url)
    {:status 303 :headers {"Location" redirect-url}}))
