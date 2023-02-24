(ns lupapalvelu.application-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defraw defquery defcommand
                                        update-application notify] :as action]
            [lupapalvelu.allu :as allu]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.application-replace-operation :as replace-operation]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.archive.archiving-util :as archiving-util]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.company :as company]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.digitizer :as digitizer]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.rakennuslupa-canonical :refer [fix-legacy-apartments]]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.drawing :as draw]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.premises :as prem]
            [lupapalvelu.property :as prop]
            [lupapalvelu.restrictions :as restrictions]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.shapefile :as shp]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.suti :as suti]
            [lupapalvelu.user :as usr]
            [lupapalvelu.ya :as ya]
            [monger.operators :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :refer [info warnf warn]])
  (:import (java.net SocketTimeoutException)))

(defn- return-to-draft-model [{{:keys [text]} :data :as command} conf recipient]
  (assoc (notifications/create-app-model command conf recipient)
    :text text))

(notifications/defemail :application-return-to-draft
  {:subject-key "return-to-draft"
   :model-fn return-to-draft-model})

;; Validators

(defn operation-validator [{{operation :operation} :data}]
  (when-not (op/operations (keyword operation)) (fail :error.unknown-type)))

(defquery application
  {:parameters       [:id]
   :permissions      [{:required [:application/read]}]}
  [{:keys [application] :as command}]
  (if application
    (ok :application (app/post-process-app command)
        :permitSubtypes (app/resolve-valid-subtypes application)
        :schemaFlags (tools/resolve-schema-flags command))
    (fail :error.application-not-found)))

(defquery application-authorities
  {:permissions [{:required [:application/show-authorities]}]
   :states     (states/all-states-but :draft)
   :parameters [:id]}
  [{app :application}]
  (ok :authorities (app/application-org-authz-users app #{"authority" "digitizer"})))

(defquery application-commenters
  {:permissions [{:required [:application/show-commenters]}]
   :states     (states/all-states-but :draft)
   :parameters [:id]}
  [{app :application}]
  (ok :authorities (app/application-org-authz-users app #{"authority" "commenter"})))

(defquery enable-accordions
  {:description "Pseudo-query for checking if accordions should be open or closed"
   :permissions [{:description "Always when permit type is YA or ARK (archiving project)"
                  :context  {:application {:permitType #{:YA :ARK}}}
                  :required [:application/read]}

                 {:required [:application/read :application/accordions-open]}]}
  [_])

(defquery party-document-names
  {:description "Which party documents can be added to the application in its current state?"
   :parameters [:id]
   :contexts    [foreman/foreman-app-context]
   :permissions [{:required [:application/edit]}]}
  [{:keys [application]}]
  (ok :partyDocumentNames (sort (doc/addable-party-schema-names application))))

(defcommand mark-seen
  {:parameters       [id type]
   :input-validators [(fn [{{type :type} :data}] (when-not (app/collections-to-be-seen type) (fail :error.unknown-type)))]
   :permissions      [{:context {:application {:state #{:draft}}}
                       :required [:application/edit-draft]}

                      {:required [:application/read]}]}
  [{:keys [user created] :as command}]
  (update-application command {$set (app/mark-collection-seen-update user created type)}))

(defcommand mark-everything-seen
  {:parameters [:id]
   :permissions [{:required [:application/read :application/mark-everything-seen]}]
   :states     (states/all-states-but [:draft :archived])}
  [command]
  (update-application command {$set (app/mark-indicators-seen-updates command)}))

;;
;; Assign
;;

(defn- validate-handler-role [{{role-id :roleId} :data org :organization}]
  (when (and role-id (not (util/find-by-id role-id (:handler-roles @org))))
    (fail :error.unknown-handler)))

(defn- validate-handler-role-not-in-use
  "Pre-check for setting application handler. Validates that handler role is not already set on application."
  [{{role-id :roleId handler-id :handlerId} :data {application-handlers :handlers} :application}]
  (when (and role-id (->> (remove (comp #{handler-id} :id) application-handlers)
                          (util/find-by-key :roleId role-id)))
    (fail :error.duplicate-handler-role)))

(defn- validate-handler-id-in-application [{{handler-id :handlerId} :data {application-handlers :handlers} :application}]
  (when (and handler-id (not (util/find-by-id handler-id application-handlers)))
    (fail :error.unknown-handler)))

(defn- validate-handler-in-organization [{{user-id :userId} :data {application-org :organization} :application}]
  (when (and user-id (not (usr/find-user {:id user-id (util/kw-path :orgAuthz application-org) "authority" :enabled true})))
    (fail :error.unknown-handler)))

(defn- do-upsert-application-handler
  "Updates or creates an application handler. Automatic assignments corresponding to the role are
  updated too if needed. Returns the handler."
  [{:keys [user created]
    {handlers :handlers app-id :id} :application
    :as                             command}
   {:keys [handler-id handler-user handler-role-id]}]
  (let [old-role-id (some-> handler-id
                            (util/find-by-id handlers)
                            :roleId)
        handler     (usr/create-handler handler-id handler-role-id handler-user)]
    (update-application command (app/handler-upsert-updates handler handlers created user))
    (when (and old-role-id (not= old-role-id handler-role-id))
      (assignment/change-assignment-recipient app-id old-role-id nil))
    (assignment/change-assignment-recipient app-id handler-role-id handler)
    handler))

(defn- find-authority!
  "Returns user if `user-id` is an authority in `org-id`, throws otherwise."
  [org-id user-id]
  (let [org-authz-path (util/kw-path :orgAuthz org-id)
        user           (usr/find-user {:id            user-id
                                       org-authz-path "authority"
                                       :role          "authority"})]
    (or user (fail! :error.user-not-found))))

(defcommand upsert-application-handler
  {:parameters          [id userId roleId]
   :optional-parameters [handlerId]
   :pre-checks          [validate-handler-role
                         validate-handler-role-not-in-use
                         validate-handler-id-in-application
                         validate-handler-in-organization]
   :input-validators    [(partial action/non-blank-parameters [:id :userId :roleId])]
   :states              (states/all-states-but :draft :canceled)
   :permissions         [{:required [:application/edit-handlers]}]}
  [{:keys [application] :as command}]
  (let [handler-arg    {:handler-id      handlerId
                        :handler-role-id roleId
                        :handler-user (find-authority! (:organization application) userId)}
        handler        (do-upsert-application-handler command handler-arg)]
    (ok :id (:id handler))))

(defcommand remove-application-handler
  {:parameters       [id handlerId]
   :pre-checks       [validate-handler-id-in-application]
   :input-validators [(partial action/non-blank-parameters [:id :handlerId])]
   :permissions      [{:required [:application/edit-handlers]}]
   :states           (states/all-states-but :draft :canceled)}
  [{:keys [created user application] :as command}]
  (let [role-id (:roleId (util/find-by-id handlerId (:handlers application)))
        result  (update-application command
                                    {$set  {:modified created}
                                     $pull {:handlers {:id handlerId}}
                                     $push {:history (app/handler-history-entry {:id handlerId :removed true} created user)}})]
    (assignment/change-assignment-recipient id role-id nil)
    result))


(defcommand change-handler-applications
  {:description      "Changes the handler for `roleId` to `newUserId` for every application in `organizationId`
  that is in any of the `states` and the current corresponding handler is `oldUserId`. Returns the number of
  updated applications."
   :parameters       [organizationId roleId oldUserId newUserId states]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :roleId         ssc/NonBlankStr
                       :oldUserId      ssc/NonBlankStr
                       :newUserId      ssc/NonBlankStr
                       :states         [(apply sc/enum (map name states/all-states))]}
                      (fn [{{:keys [oldUserId newUserId]} :data}]
                        (when (= oldUserId newUserId)
                          (fail :error.same-user)))]
   :user-roles       #{:admin}}
  [{:keys [created]}]
  (let [organization (or (org/get-organization organizationId)
                         (fail! :error.organization-not-found))
        _            (when-not (util/find-by-id roleId (:handler-roles organization))
                       (fail! :error.handler-role-not-found))
        new-user     (find-authority! organizationId newUserId)
        apps         (mongo/select :applications
                                   {:organization organizationId
                                    :handlers     {$elemMatch {:userId oldUserId
                                                               :roleId roleId}}
                                    :state        {$in states}})
        user         (usr/batchrun-user [organizationId])
        cmd-fn       (fn [application]
                       (assoc (action/application->command application user)
                              :created created))]
    (doseq [{:keys [handlers]
             :as   application} apps
            :let                [handler-id (:id (util/find-by-keys {:userId oldUserId
                                                                     :roleId roleId}
                                                                    handlers))]]
      (do-upsert-application-handler (cmd-fn application)
                                     {:handler-id      handler-id
                                      :handler-role-id roleId
                                      :handler-user    new-user}))
    (ok :count (count apps))))

;;
;; Cancel
;;


(defcommand cancel-inforequest
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :contexts         [open-inforequest/inforequest-context]
   :permissions      [{:required [:application/cancel]}]
   :notified         true
   :on-success       (notify :application-state-change)
   :pre-checks       [(partial sm/validate-state-transition :canceled)]}
  [command]
  (app/cancel-inforequest command))

(defcommand cancel-application
  {:parameters       [id text lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :contexts         [foreman/foreman-app-context]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft
                                  :application/cancel-in-restricted-states]}

                      {:context  {:application {:state #{:info :open :submitted}}}
                       :required [:application/cancel-in-restricted-states]}

                      {:context  {:application {:state states/all-but-draft}}
                       :required [:application/cancel]}]
   :notified         true
   :on-success       [(notify :application-state-change)
                      (fn [command _] (bs/cancel-application! command))]
   :states           states/all-application-or-archiving-project-states
   :pre-checks       [(partial sm/validate-state-transition :canceled)
                      (fn [{:keys [application]}]
                        (when-let [{:keys [state permitType operation-name]} application]
                          (when (some->> state keyword states/post-verdict-states)
                            (when (and (= permitType "R")
                                       (not= operation-name "aiemmalla-luvalla-hakeminen"))
                              (fail :error.command-illegal-state)))))]}
  [command]
  (app/cancel-application command))

(defcommand undo-cancellation
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :contexts         [app/canceled-app-context]
   :permissions      [{:required [:application/undo-cancelation]}]
   :pre-checks       [bs/validate-action-support
                      (fn last-history-item-is-canceled [{:keys [application]}]
                        (when application
                          (when-not (= (:state (app-state/last-history-item application)) "canceled")
                           (fail :error.latest-state-not-canceled))))
                      (fn has-previous-state [{:keys [application]}]
                        (when application
                          (when-not (states/all-states (app-state/get-previous-app-state application))
                           (fail :error.illegal-state))))]
   :on-success       (notify :undo-cancellation)
   :states           #{:canceled}}
  [command]
  (app/undo-cancellation command))

(defcommand request-for-complement
  {:parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:application/request-for-complement]}]
   :notified         true
   :on-success       (notify :application-state-change)
   :pre-checks       [bs/validate-action-support
                      (partial sm/validate-state-transition :complementNeeded)]}
  [{:keys [created user application] :as command}]
  (update-application command (util/deep-merge (app-state/state-transition-update :complementNeeded created application user))))

(defcommand cleanup-krysp
  {:description      "Removes application KRYSP messages. The cleanup
  criteria depends on the message contents."
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:application/access-backend]}]
   :states           #{:complementNeeded}}
  [command]
  (sftp/cleanup-directory command))

(notifications/defemail :neighbor-hearing-requested
  {:pred-fn       (fn [command] (get-in command [:application :options :municipalityHearsNeighbors]))
   :recipients-fn (fn [{application :application org :organization}]
                    (let [organization (or (and org @org) (org/get-organization (:organization application)))
                          emails (get-in organization [:notifications :neighbor-order-emails])]
                      (map (fn [e] {:email e, :role "authority"}) emails)))
   :tab-fn (constantly "statement")})

(notifications/defemail :organization-on-submit
  {:recipients-fn (fn [{application :application org :organization}]
                    (let [organization (or (and org @org) (org/get-organization (:organization application)))
                          emails (get-in organization [:notifications :submit-notification-emails])]
                      (map (fn [e] {:email e, :role "authority"}) emails)))
   :model-fn (fn [{app :application :as command} conf recipient]
               (assoc (notifications/create-app-model command conf recipient)
                 :applicants (if-let [applicants (not-empty (:_applicantIndex app))]
                               (reduce #(str %1 ", " %2) applicants)
                               #(i18n/localize % "not-known"))))})

(notifications/defemail :organization-housing-office
  {:pred-fn       (fn [command] (let [application (:application command)
                                      document-data (:data (domain/get-document-by-name application "hankkeen-kuvaus"))]
                                  (and (some? (:rahoitus document-data))
                                       (true? (get-in document-data [:rahoitus :value])))))
   :recipients-fn (fn [{application :application org :organization}]
                    (let [organization (or (and org @org) (org/get-organization (:organization application)))
                          emails (get-in organization [:notifications :funding-notification-emails])]
                      (map (fn [e] {:email e, :role "authority"}) emails)))})

(defn submit-validation-errors [{:keys [application] :as command}]
  (remove nil? [(foreman/validate-application application)
                (app/validate-link-permits application)
                (app/validate-fully-formed command)
                (ya/validate-digging-permit application)
                (when-not (company/cannot-submit command)
                  (fail :company.user.cannot.submit))
                (suti/suti-submit-validation command)
                (restrictions/check-auth-restriction command :application/submit)]))

(defquery application-submittable
  {:description "Query for frontend, to display possible errors regarding application submit"
   :parameters [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :contexts         [foreman/foreman-app-context]
   :permissions      [{:required [:application/edit]}]
   :states           #{:draft :open}}
  [command]
  (let [command (update-in command [:application] meta-fields/enrich-with-link-permit-data)]
    (if-some [errors (seq (submit-validation-errors command))]
      (fail :error.cannot-submit-application :errors errors)
      (ok))))

(defcommand submit-application
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :states           #{:draft :open}
   :contexts         [foreman/foreman-app-context]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :application/submit]}

                      {:required [:application/submit]}]
   :notified         true
   :on-success       [(notify :application-state-change)
                      (notify :neighbor-hearing-requested)
                      (notify :organization-on-submit)
                      (notify :organization-housing-office)]
   :pre-checks       [permit/is-not-archiving-project
                      (partial sm/validate-state-transition :submitted)]}
  [{:keys [application] :as command}]
  (let [command (assoc command :application (meta-fields/enrich-with-link-permit-data application))]
    (if-some [errors (seq (submit-validation-errors command))]
      (fail :error.cannot-submit-application :errors errors)
      (do (app/submit command)
          (bs/submit-application! command)
          (ok)))))

(defcommand refresh-ktj
  {:parameters [:id]
   :permissions [{:required [:application/access-backend]}]
   :states     (states/all-application-states-but (conj states/terminal-states :draft))}
  [{:keys [application created]}]
  (app/autofill-rakennuspaikka application created)
  (ok))

(defcommand save-application-drawings
  {:parameters       [:id drawings]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :application/edit-drawings]}
                      {:context {:application {:state #{ :info :answered :open :submitted :complementNeeded}}}
                       :required [:application/edit-drawings]}
                      {:context {:application {:state states/all-but-draft-or-terminal}}
                       :required [:application/edit-drawings :document/approve]}]
   :on-success       bs/update-callback}
  [{:keys [application created] :as command}]
  (when (sequential? drawings)
    (let [{:keys [allu-drawings new-drawings]} (allu/merge-drawings application drawings)
          drawings-with-geojson (map #(assoc % :geometry-wgs84 (draw/wgs84-geometry %)) new-drawings)]
      (if (every? :geometry-wgs84 drawings-with-geojson)
        (update-application command
                            {$set {:modified created
                                   :drawings (concat allu-drawings drawings-with-geojson)}})
        ; We don't accept invalid GeoJSON, as then the data would not be archived nor searchable by document search
        (fail :error.invalid-drawing)))))

(defraw upload-application-shapefile
  {:description      "Converts uploaded shapefile into application drawings"
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :application/edit-drawings]}
                      {:context {:application {:state #{ :info :answered :open :submitted :complementNeeded}}}
                       :required [:application/edit-drawings]}
                      {:context {:application {:state states/all-but-draft-or-terminal}}
                       :required [:application/edit-drawings :document/approve]}]
   :pre-checks       [action/disallow-impersonation]
   :on-success       bs/update-callback}
  [{{[{:keys [tempfile]}] :files} :data lang :lang :as command}]
  (shp/parse-and-respond {:file-info  (shp/command->fileinfo command)
                          :tempfile   tempfile
                          :lang       lang
                          :respond-fn (fn [areas]
                                        (update-application command
                                                            {$push {:drawings {$each (shp/areas->drawings areas)}}})
                                        (ok))}))


(defn- make-marker-contents [id lang {:keys [location] :as app}]
  (merge
    {:id        (:id app)
     :title     (:title app)
     :location  {:x (first location) :y (second location)}
     :operation (app-utils/operation-description app lang)
     :authName  (usr/full-name (:creator app))
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
           :states           states/all-inforequest-states
           :permissions      [{:required [:application/show-inforequest-markers]}]
           :input-validators [(partial action/non-blank-parameters [:id :x :y])]}
          [{:keys [application user]}]
          (let [x (util/->double x)
                y (util/->double y)
                inforequests (mongo/select :applications
                                           (merge
                                             (domain/application-query-for user)
                                             {:infoRequest true})
                                           [:title :auth :creator :location :primaryOperation :secondaryOperations :comments])

                same-location-irs (filter
                                    #(and (== x (-> % :location first)) (== y (-> % :location second)))
                                    inforequests)

                inforequests (remove-irs-by-id inforequests same-location-irs)

                application-op-name (-> application :primaryOperation :name)

                same-op-irs (filter
                              (fn [ir]
                                (some #(= application-op-name (:name %)) (app-utils/get-operations ir)))
                              inforequests)

                others (remove-irs-by-id inforequests same-op-irs)

                same-location-irs (map (partial make-marker-contents id lang) same-location-irs)
                same-op-irs (map (partial make-marker-contents id lang) same-op-irs)
                others (map (partial make-marker-contents id lang) others)]

            (ok :sameLocation same-location-irs :sameOperation same-op-irs :others others)
            ))

(notifications/defemail :inforequest-invite
  {:recipients-fn (fn [{application :application org :organization}]
                    (let [organization (or (and org @org) (org/get-organization (:organization application)))
                          emails (get-in organization [:notifications :inforequest-notification-emails])]
                      (map (fn [e] {:email e, :role "authority"}) emails)))})


(defcommand create-application
  {:parameters       [:operation :x :y :address :propertyId]
   :permissions      [{:required [:application/create]}]
   :notified         true                                   ; info requests (also oir)
   :input-validators [(partial action/non-blank-parameters [:operation :address :propertyId])
                      (partial action/property-id-parameters [:propertyId])
                      coord/validate-x coord/validate-y
                      operation-validator]}
  [{{:keys [infoRequest]} :data :keys [created] :as command}]
  (let [created-application (app/do-create-application command)]
    (logging/with-logging-context {:applicationId (:id created-application)}
      (app/insert-application created-application)
      (when (boolean infoRequest)
        ; Notify organization about new inforequest
        (if (:openInfoRequest created-application)
          (open-inforequest/new-open-inforequest! created-application)
          (notifications/notify! :inforequest-invite {:application created-application})))
      (try
        (try+
         (app/autofill-rakennuspaikka created-application created)
         (catch [:sade.core/type :sade.core/fail] {:keys [cause text] :as exp}
           (warnf "Could not get KTJ data for the new application, cause: %s, text: %s. From %s:%s"
                  cause
                  text
                  (:sade.core/file exp)
                  (:sade.core/line exp)))
         (catch SocketTimeoutException _
           (warn "Socket timeout from KTJ when creating application")))
        (catch Exception e
          (warn "Exception when creating application: " (.getMessage e))))
      (ok :id (:id created-application)))))

(defn- add-operation-allowed? [{application :application}]
  (let [op (-> application :primaryOperation :name keyword)
        permit-subtype (keyword (:permitSubtype application))]
    (when-not (and (or (nil? op) (:add-operation-allowed (op/operations op)))
                   (not= permit-subtype :muutoslupa))
      (fail :error.add-operation-not-allowed))))

(defn multiple-operations-supported? [{organization :organization}]
  (when-not (and organization (-> @organization :multiple-operations-supported))
    (fail :info.multiple-opertions-not-supported)))

(defcommand add-operation
  {:parameters       [id operation]
   :states           states/pre-sent-application-states
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :application/edit-operation]}

                      {:required [:application/edit-operation]}]
   :input-validators [operation-validator]
   :pre-checks       [add-operation-allowed?
                      multiple-operations-supported?]}
  [command]
  (app/add-operation command id operation))

(defcommand update-op-description
  {:parameters       [id op-id desc]
   :categories       #{:documents} ; edited from document header
   :input-validators [(partial action/non-blank-parameters [:id :op-id])
                      (partial action/string-parameters [:desc])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :application/edit-operation]}
                      {:context  {:application {:state states/pre-sent-application-states}}
                       :required [:application/edit-operation]}
                      {:context  {:application {:state states/all-application-or-archiving-project-states}}
                       :required [:application/edit-operation :document/edit-identifiers]}]}
  [{:keys [application] :as command}]
  (if (= (get-in application [:primaryOperation :id]) op-id)
    (update-application command {$set {"primaryOperation.description" desc}})
    (update-application command {"secondaryOperations" {$elemMatch {:id op-id}}} {$set {"secondaryOperations.$.description" desc}})))

(defn- there-is-primary-operation? [{application :application}]
  (when-not (:primaryOperation application)
    (fail :error.unknown-operation)))

(defn- secondary-operation-exists? [{parameters :data app :application}]
  (when-let [op-id (:secondaryOperationId parameters)]
    (when-not (replace-operation/get-operation-by-key app :id op-id)
      (fail :error.unknown-operation))))

(defcommand change-primary-operation
  {:parameters [id secondaryOperationId]
   :categories #{:documents} ; edited from document header
   :input-validators [(partial action/non-blank-parameters [:id :secondaryOperationId])]
   :states states/pre-sent-application-states
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft :application/edit-operation]}

                 {:required [:application/edit-operation]}]
   :pre-checks [secondary-operation-exists?]}
  [command]
  (app/change-primary-operation command secondaryOperationId)
  (ok))

(defn- replace-operation-allowed-pre-check [{application :application}]
  (when (or (foreman/foreman-app? application)
            (app/designer-app? application)
            (app/jatkoaika-application? application))
    (fail :error.replace-operation-not-allowed)))

(defn- operation-exists? [{parameters :data app :application}]
  (when-let [op-id (:opId parameters)]
    (when-not (replace-operation/get-operation-by-key app :id op-id)
      (fail :error.operations-not-found))))

(defcommand replace-operation
  {:parameters       [id opId operation]
   :states           states/pre-sent-application-states
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :application/edit-operation]}

                      {:required [:application/edit-operation]}]
   :input-validators [operation-validator
                      (partial action/non-blank-parameters [:id :opId :operation])]
   :pre-checks       [(partial permit/valid-permit-types {:R [] :KT :all})
                      replace-operation-allowed-pre-check
                      operation-exists?]}
  [command]
  (replace-operation/replace-operation command opId operation)
  (ok))

(defcommand change-permit-sub-type
  {:parameters       [id permitSubtype]
   :states           states/pre-sent-application-states
   :input-validators [(partial action/non-blank-parameters [:id :permitSubtype])]
   :contexts         [foreman/foreman-app-context]
   :permissions      [{:description "draft non-YA application"
                       :context  {:application {:state #{:draft} :permitType (comp not #{:YA} keyword)}}
                       :required [:application/edit-draft :application/edit-permit-subtype]}

                      {:description "non-YA application"
                       :context  {:application {:permitType (comp not #{:YA} keyword)}}
                       :required [:application/edit-permit-subtype]}

                      {:description "non-draft YA application"
                       :context  {:application {:state states/all-but-draft :permitType #{:YA}}}
                       :required [:application/edit-permit-subtype-in-ya]}]
   :pre-checks       [app/validate-has-subtypes
                      app/pre-check-permit-subtype]}
  [{:keys [created] :as command}]
  (update-application command {$set {:permitSubtype permitSubtype, :modified created}})
  (ok))

(defcommand change-location
  {:parameters          [id x y address propertyId]
   :optional-parameters [refreshBuildings]
   :states              (-> (conj states/terminal-states :sent)
                            (set/difference #{:acknowledged})
                            states/all-states-but)
   :input-validators    [(partial action/non-blank-parameters [:id :address])
                         (partial action/property-id-parameters [:propertyId])
                         coord/validate-x coord/validate-y]
   :permissions         [{:context  {:application {:state #{:draft}}}
                          :required [:application/edit-draft :application/change-location-in-pre-verdict-states]}

                         {:context  {:application {:state states/pre-verdict-states}}
                          :required [:application/change-location-in-pre-verdict-states]}

                         {:required [:application/change-location-in-post-verdict-states]}]}
  [{:keys [created application] :as command}]
  (if (= (:municipality application) (prop/municipality-by-property-id propertyId))
    (do
      (when (and (permit/archiving-project? application) (true? refreshBuildings))
        (digitizer/fetch-buildings command propertyId))
      (update-application command
                          {$set   {:location       (app/->location x y)
                                   :location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 (app/->location x y))
                                   :address        (ss/trim address)
                                   :propertyId     propertyId
                                   :title          (ss/trim address)
                                   :modified       created}
                           $unset {:propertyIdSource true}})
      (app/autofill-rakennuspaikka (mongo/by-id :applications id) created))
    (fail :error.property-in-other-municipality)))

(defcommand change-address
  {:description      "Change the application address. A subset of `change-location` functionality."
   :parameters       [id address]
   :states           (-> (conj states/terminal-states :sent)
                         (set/difference #{:acknowledged})
                         states/all-states-but)
   :input-validators [(partial action/non-blank-parameters [:id :address])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft
                                  :application/change-location-in-pre-verdict-states]}
                      {:context  {:application {:state states/pre-verdict-states}}
                       :required [:application/change-location-in-pre-verdict-states]}
                      {:required [:application/change-location-in-post-verdict-states]}]}
  [{:keys [created application] :as command}]
  (let [address (ss/trim address)]
    (update-application command
                        {$set {:address  address
                               :title    address
                               :modified created}})))

(defcommand change-application-state
  {:description      "Changes application state. The transitions happen
  between post-verdict (excluding verdict given)states. In addition,
  the transition from appealed to a verdict given state is supported."
   :parameters       [id state]
   :input-validators [(partial action/non-blank-parameters [:state])]
   :states           (conj states/post-verdict-states :underReview)
   :pre-checks       [permit/valid-permit-types-for-state-change
                      app/valid-new-state]
   :permissions      [{:required [:application/change-state]}]
   :notified         true
   :on-success       (notify :application-state-change)}
  [{:keys [user created application organization] :as command}]
  (let [organization       (force organization)
        archiving-project? (util/=as-kw (:permitType application) :ARK)
        krysp?             (org/krysp-write-integration? organization (permit/permit-type application))
        warranty?          (and (permit/is-ya-permit (permit/permit-type application))
                                (util/=as-kw state :closed)
                                (not krysp?)
                                (nil? (:warrantyStart application))
                                (nil? (:warrantyEnd application)))]
    (when (attachment/comments-saved-as-attachment? application state)
      (attachment/save-comments-as-attachment command {:state state}))
    (let [transition-update (app-state/state-transition-update (keyword state) created application user)]
      (update-application command (util/deep-merge
                                    transition-update
                                    (when warranty?
                                      {$set (app/warranty-period created)}))))
    (when-not archiving-project?
      (archiving-util/mark-application-archived-if-done application created user))
    (when (= state "extinct")
      (archiving-util/set-extinct-metadata application created user))))

(defcommand return-to-draft
  {:description      "Returns the application to draft state."
   :parameters       [id text lang]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :permissions      [{:required [:application/change-state]}]
   :states           #{:submitted}
   :pre-checks       [(partial sm/validate-state-transition :draft)]
   :on-success       [(notify :application-return-to-draft)
                      (fn [command _] (bs/return-to-draft! command))
                      (fn [{:keys [application]} _]
                        (assignment/remove-application-assignments (:id application)))]}
  [{{:keys [role] :as user}         :user
    {:keys [state] :as application} :application
    created                         :created
    organization                    :organization
    :as                             command}]
  (->> (util/deep-merge
        (app-state/state-transition-update :draft created application user)
        (when (seq text)
          (comment/comment-mongo-update state text {:type "application"} role false user nil created))
        {$set (merge {:submitted nil}
                     (when (:remove-handlers-from-reverted-draft @organization)
                       {:handlers  []}))})
       (update-application command)))

(defn timestamp-or-nil
  "Pre-check that fails if `param-key` parameter is not positive timestamp or nil."
  [param-key]
  (fn [{data :data}]
    (let [ts (get data param-key)]
      (when-not (or (nil? ts) (pos-int? ts))
        (fail :error.invalid-value)))))

(defcommand change-warranty-start-date
  {:description         "Changes warranty start date"
   :parameters          [id startDate]
   :input-validators    [(partial action/non-blank-parameters [:id])
                         (timestamp-or-nil :startDate)]
   :permissions         [{:required [:application/edit-warranty-dates]}]
   :states              states/post-verdict-states}
   [command]
  (update-application command
                      {$set {:warrantyStart startDate}})
  (ok))

(defcommand change-warranty-end-date
  {:description      "Changes warranty end date"
   :parameters       [id endDate]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (timestamp-or-nil :endDate)]
   :permissions      [{:required [:application/edit-warranty-dates]}]
   :states           states/post-verdict-states}
  [command]
  (update-application command {$set {:warrantyEnd endDate}})
  (ok))

(defquery change-application-state-targets
  {:description "List of possible target states for
  change-application-state transitions."
   :permissions [{:required [:application/change-state]}]
   :pre-checks  [permit/valid-permit-types-for-state-change]
   :states      (conj states/post-verdict-states :underReview)}
  [{application :application}]
  (ok :states (app/change-application-state-targets application)))

;;
;; Link permits
;;

(defquery link-permit-required
          {:description "Dummy command for UI logic: returns falsey if link permit is not required."
           :parameters  [:id]
           :contexts    [foreman/foreman-app-context]
           :permissions [{:required [:application/edit]}]
           :states      states/pre-sent-application-states
           :pre-checks  [(fn [{application :application}]
                           (when-not (app/validate-link-permits application)
                             (fail :error.link-permit-not-required)))]})

(defquery app-matches-for-link-permits
  {:parameters [id]
   :description "Retuns a list of application IDs that can be linked to current application."
   :contexts    [foreman/foreman-app-context]
   :permissions [{:required [:application/edit]}]
   :states     (states/all-application-states-but (conj states/terminal-states :sent))}
  [{{:keys [propertyId] :as application} :application user :user}]
  (let [application (meta-fields/enrich-with-link-permit-data application)
        ;; exclude from results the current application itself, and the applications that have a link-permit relation to it
        ignore-ids (-> application
                       (#(concat (:linkPermitData %) (:appsLinkingToUs %)))
                       (#(map :id %))
                       (conj id))
        results (mongo/select :applications
                              ; FIXME optimize, this bastard is pretty heavy.. maybe add municipality?
                              (merge (domain/application-query-for user) {:_id             {$nin ignore-ids}
                                                                          :infoRequest     false
                                                                          ; Backend systems support only the same kind of link permits.
                                                                          ; We COULD filter the other kinds from XML messages in the future...
                                                                          :permitType      (:permitType application)
                                                                          :secondaryOperations.name {$nin ["ya-jatkoaika"]}
                                                                          :primaryOperation.name {$nin ["ya-jatkoaika"]}})

                              [:permitType :address :propertyId :primaryOperation.name])
        ;; add the text to show in the dropdown for selections
        enriched-results (map (fn [r] (assoc r :primaryOperation (get-in r [:primaryOperation :name])))
                              results)
        ;; sort the results
        same-property-id-fn #(= propertyId (:propertyId %))
        with-same-property-id (filterv same-property-id-fn enriched-results)
        without-same-property-id (sort-by :text (vec (remove same-property-id-fn enriched-results)))
        organized-results (flatten (conj with-same-property-id without-same-property-id))
        final-results (map #(select-keys % [:id :address :propertyId :primaryOperation]) organized-results)]
    (ok :app-links final-results)))

(defn- validate-linking [{app :application :as command}]
  (when app
    (let [link-permit-id (ss/trim (get-in command [:data :linkPermitId]))
        {:keys [appsLinkingToUs linkPermitData]} (meta-fields/enrich-with-link-permit-data app)
        max-outgoing-link-permits (op/get-primary-operation-metadata app :max-outgoing-link-permits)
        links    (concat appsLinkingToUs linkPermitData)
        illegal-apps (conj links app)]
    (cond
      (and link-permit-id (util/find-by-id link-permit-id illegal-apps))
      (fail :error.link-permit-already-having-us-as-link-permit)

      (and max-outgoing-link-permits (= max-outgoing-link-permits (count linkPermitData)))
      (fail :error.max-outgoing-link-permits)))))

(defcommand add-link-permit
  {:parameters       ["id" linkPermitId]
   :contexts         [foreman/foreman-app-context]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft]}

                      {:required [:application/edit]}]
   :states           (states/all-application-states-but (conj states/terminal-states :sent)) ;; Pitaako olla myos 'sent'-tila?
   :pre-checks       [permit/is-not-archiving-project
                      validate-linking]
   :input-validators [(partial action/non-blank-parameters [:linkPermitId])
                      (fn [{data :data}] (when (= (:id data) (ss/trim (:linkPermitId data))) (fail :error.link-permit-self-reference)))
                      (action/valid-db-key :linkPermitId)]}
  [{application :application}]
  (app/do-add-link-permit application (ss/trim linkPermitId))
  (ok))

(defcommand remove-link-permit-by-app-id
  {:parameters [id linkPermitId]
   :input-validators [(partial action/non-blank-parameters [:id :linkPermitId])]
   :permissions      [{:context  {:application (every-pred (comp #{:draft} keyword :state) app/extra-link-permits?)}
                       :required [:application/edit-draft :application/remove-extra-link-permit]}

                      {:context  {:application app/extra-link-permits?}
                       :required [:application/remove-extra-link-permit]}

                      {:required [:application/remove-link-permit]}]
   :states     (states/all-application-states-but (conj states/terminal-states :sent))}
  [_]
  (if (mongo/remove :app-links (app/make-mongo-id-for-link-permit id linkPermitId))
    (ok)
    (fail :error.unknown)))

(defquery all-operations-in
  {:description "Return all operation names in operation tree for given paths."
   :optional-parameters [path]
   :contexts            [foreman/foreman-app-context]
   :permissions         [{:required [:application/edit]}]
   :input-validators    [(partial action/string-parameters [:path])]}
  [_]
  (ok :operations (op/operations-in (ss/split (not-empty path) #"\."))))

;;
;; Change permit
;;

(defn set-new-document-ids [op-id-mapping documents]
  (into [] (map
            (fn [doc]
              (let [doc (-> doc
                            (assoc :id (mongo/create-id))
                            (util/dissoc-in [:meta :_approved]))]
                (if (-> doc :schema-info :op)
                  (update-in doc [:schema-info :op :id] op-id-mapping)
                  doc)))
            documents)))

(defcommand create-change-permit
  {:parameters ["id"]
   :states     #{:verdictGiven :constructionStarted :appealed :inUse :onHold}
   :permissions [{:required [:application/create-change-permit]}]
   :pre-checks [(permit/validate-permit-type-is permit/R)
                (app/reject-primary-operations #{:raktyo-aloit-loppuunsaat})]}
  [{:keys [created user application] :as command}]
  (let [muutoslupa-app-id (app/make-application-id (:municipality application))
        primary-op (:primaryOperation application)
        secondary-ops (:secondaryOperations application)
        op-id-mapping (into {} (map
                                 #(vector (:id %) (mongo/create-id))
                                 (conj secondary-ops primary-op)))
        state (if (auth/application-authority? application user) :open :draft)
        muutoslupa-app (merge domain/application-skeleton
                              (select-keys application
                                           [:propertyId :location
                                            :location-wgs84
                                            :schema-version
                                            :address :title
                                            :foreman :foremanRole
                                            :applicant :_applicantIndex
                                            :municipality :organization
                                            :drawings
                                            :metadata])
                              {:auth (remove #(util/=as-kw (:role %) :statementGiver)
                                             (:auth application))}
                              {:id            muutoslupa-app-id
                               :permitType    permit/R
                               :permitSubtype :muutoslupa
                               :created       created
                               :opened        (when (= state :open) created)
                               :modified      created
                               :documents     (->> (fix-legacy-apartments application)
                                                   :documents
                                                   (set-new-document-ids op-id-mapping)
                                                   (prem/set-premises-source-values-for-documents (:id application)))
                               :state         state

                               :history [(app-state/history-entry state created user)]
                               :infoRequest false
                               :openInfoRequest false
                               :convertedToApplication nil

                               :primaryOperation (assoc primary-op :id (op-id-mapping (:id primary-op)))
                               :secondaryOperations (mapv #(assoc % :id (op-id-mapping (:id %))) secondary-ops)})]
    (app/do-add-link-permit muutoslupa-app (:id application))
    (app/insert-application muutoslupa-app)
    (ok :id muutoslupa-app-id)))

(defquery change-permit-premises-note
  {:description "Pseudo query for signaling when an additional note is shown with the premises table"
   :permissions [{:context  {:application {:state         #{:draft}
                                           :permitType    #{:R}
                                           :permitSubtype #{:muutoslupa}}}
                  :required [:application/edit-draft]}
                 {:context  {:application {:state         states/pre-sent-application-states
                                           :permitType    #{:R}
                                           :permitSubtype #{:muutoslupa}}}
                  :required [:application/edit]}]
   :pre-checks  [(fn [{:keys [application]}]
                   (when application
                     (when-not (some->> application :documents (some (comp :huoneistot :data)))
                       (fail :error.no-premises-table))))]}
  [_])

;;
;; Continuation period permit
;;

(defn- get-tyoaika-alkaa-from-ya-app [app]
  (let [mainostus-viitoitus-tapahtuma-doc (:data (domain/get-document-by-name app "mainosten-tai-viitoitusten-sijoittaminen"))
        tapahtuma-name-key                (when mainostus-viitoitus-tapahtuma-doc
                                            (-> mainostus-viitoitus-tapahtuma-doc :_selected :value keyword))
        tapahtuma-data                    (when tapahtuma-name-key
                                            (mainostus-viitoitus-tapahtuma-doc tapahtuma-name-key))
        fi-date                           #(date/finnish-date % :zero-pad)]
    (if (:started app)
      (fi-date (:started app))
      (or
        (-> app (domain/get-document-by-name "tyoaika") :data :tyoaika-alkaa-ms :value fi-date)
        (-> tapahtuma-data :tapahtuma-aika-alkaa-pvm :value)
        (fi-date (:submitted app))))))

(defn- validate-not-jatkolupa-app [{:keys [application]}]
  (when (app/jatkoaika-application? application)
    (fail :error.cannot-apply-jatkolupa-for-jatkolupa)))

(defn- jatkolupa-selected-for-organization [{:keys [organization application]}]
  (when-not (some->> (:permitType application)
                     (get app/jatkolupa-operations)
                     (op/selected-operation-for-organization? @organization))
    (fail :error.jatkolupa-not-selected-for-organization)))

(defn- validate-not-operation [operation-name {:keys [application]}]
  (when (-> application :primaryOperation :name (= operation-name))
    (fail :error.invalid-operation)))

(defn- operation-selected-for-organization [operation-name {:keys [organization]}]
  (when-not (op/selected-operation-for-organization? @organization operation-name)
    (fail :error.invalid-operation)))

;;
;; ************
;; Lain mukaan hankeen aloituspvm on hakupvm + 21pv, tai kunnan paatospvm jos se on tata aiempi.
;; kts.  http://www.finlex.fi/fi/laki/alkup/2005/20050547 ,  14 a pykala
;; ************
;;
(defn- assoc-ya-continuation-app-docs [continuation-app application]
  (cond-> continuation-app
    (-> application :permitType (= permit/YA))
    (assoc :documents
           (concat
             ;; Documents from the original permit
             (->>  ["hakija-ya" "yleiset-alueet-maksaja"]
                   (map #(-> (domain/get-document-by-name application %)
                             (model/without-user-id))))
             ;; Documents from the continuation permit
             (->> ["hankkeen-kuvaus-jatkoaika" "tyo-aika-for-jatkoaika"]
                  (map #(cond-> (domain/get-document-by-name continuation-app %)
                          (= % "tyo-aika-for-jatkoaika")
                          (assoc-in [:data :tyoaika-alkaa-pvm :value]
                                    (get-tyoaika-alkaa-from-ya-app application)))))))))

(defn- prune-handlers
  "Returns application handlers that are current:

  - The user is an active authority in the organization
  - The handler role exists in the organization"
  [{:keys [organization application]}]
  (if-let [handlers (some-> application :handlers seq)]
    (let [organization (force organization)
          role-id-set  (->> organization
                            :handler-roles
                            (remove :disabled)
                            (map :id)
                            set)
          user-id-set  (->> (mongo/select :users
                                          {:_id                              {$in (mapv :userId handlers)}
                                           :enabled                          true
                                           (util/kw-path :orgAuthz
                                                         (:id organization)) :authority
                                           :role                             :authority}
                                          [:id])
                            (map :id)
                            set)]
      (filter (fn [{:keys [roleId userId]}]
                (and (contains? role-id-set roleId)
                     (contains? user-id-set userId)))
              handlers))
    []))

(defcommand create-continuation-period-permit
  {:parameters ["id"]
   :permissions [{:required [:application/create-continuation-period-permit]}]
   :states     #{:verdictGiven :constructionStarted :inUse}
   :pre-checks [validate-not-jatkolupa-app
                (partial permit/valid-permit-types {:R :all :YA :all :MAL :all})
                jatkolupa-selected-for-organization]}
  [{:keys [application] :as command}]
  (let [permit-type      (:permitType application)
        continuation-app (-> (->> {:operation    (name (get app/jatkolupa-operations permit-type))
                                   :x            (-> application :location first)
                                   :y            (-> application :location second)
                                   :infoRequest  false
                                   :messages     []}
                                  (merge (select-keys application [:address :propertyId :municipality]))
                                  (assoc command :data))
                             (app/do-create-application)
                             (assoc :handlers (prune-handlers command))
                             (assoc-ya-continuation-app-docs application))]
    (app/do-add-link-permit continuation-app (:id application))
    (app/insert-application continuation-app)
    (ok :id (:id continuation-app))))

(defcommand create-encumbrance-permit
  {:description "Creates a linked permit with 'rasite-tai-yhteisjarjestely' as its primary operation"
   :parameters  [id]
   :permissions [{:required [:application/create-encumbrance-permit]}]
   :states      states/all-application-states-but-draft-or-terminal
   :pre-checks  [(partial validate-not-operation "rasite-tai-yhteisjarjestely")
                 (partial permit/valid-permit-types {:R :all})
                 (partial operation-selected-for-organization "rasite-tai-yhteisjarjestely")]}
  [{:keys [application] :as command}]
  (let [encumbrance-app (-> command
                            (assoc :data {:operation    "rasite-tai-yhteisjarjestely"
                                          :x            (-> application :location first)
                                          :y            (-> application :location second)
                                          :address      (-> application :address)
                                          :propertyId   (-> application :propertyId)
                                          :municipality (-> application :municipality)
                                          :messages     []})
                            (app/do-create-application)
                            (assoc :handlers (prune-handlers command)))]
    (app/do-add-link-permit encumbrance-app (:id application))
    (app/insert-application encumbrance-app)
    (ok :id (:id encumbrance-app))))

(defn- validate-new-applications-enabled [{{:keys [permitType municipality] :as application} :application}]
  (when application
    (let [scope (org/resolve-organization-scope municipality permitType)]
      (when-not (:new-application-enabled scope)
        (fail :error.new-applications-disabled)))))

(defcommand convert-to-application
  {:parameters [id]
   :permissions [{:required [:application/convert-to-application]}]
   :states     states/all-inforequest-states
   :pre-checks [validate-new-applications-enabled]}
  [{user :user created :created {state :state op :primaryOperation tos-fn :tosFunction :as app} :application org :organization :as command}]
  (update-application command
                      (util/deep-merge
                       (app-state/state-transition-update :open created (assoc app :infoRequest false) user)
                       {$set  (merge {:infoRequest            false
                                      :openInfoRequest        false
                                      :convertedToApplication created
                                      :documents              (app/make-documents user created @org op app)
                                      :modified               created}
                                     (when (:remove-handlers-from-converted-application @org)
                                       {:handlers []}))
                        $push {:attachments {$each (app/make-attachments created op @org state tos-fn)}}}))
  (try (app/autofill-rakennuspaikka app created)
       (catch Exception _ (warn "KTJ data was not updated to inforequest when converted to application"))))

(defn- validate-organization-backend-urls [{organization :organization}]
  (when-let [org (and organization @organization)]
    (if-let [conf (:vendor-backend-redirect org)]
      (->> (vals conf)
           (remove ss/blank?)
           (some action/validate-url))
      (fail :error.vendor-urls-not-set))))

(defn get-vendor-backend-id [verdicts]
  (->> verdicts
       (remove :draft)
       (some :kuntalupatunnus)))

(defn- get-backend-and-lp-urls [org]
  (-> (:vendor-backend-redirect org)
      (util/select-values [:vendor-backend-url-for-backend-id
                           :vendor-backend-url-for-lp-id])))

(defn- correct-urls-configured [{{:keys [verdicts] :as application} :application organization :organization}]
  (when application
    (let [vendor-backend-id          (get-vendor-backend-id verdicts)
          [backend-id-url lp-id-url] (get-backend-and-lp-urls @organization)
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
   :permissions [{:required [:application/access-backend]}]
   :states     states/post-sent-states
   :pre-checks [validate-organization-backend-urls
                correct-urls-configured]}
  [{{:keys [verdicts]} :application organization :organization}]
  (let [vendor-backend-id          (get-vendor-backend-id verdicts)
        [backend-id-url lp-id-url] (get-backend-and-lp-urls @organization)
        url-parts                  (if (and vendor-backend-id
                                            (not (ss/blank? backend-id-url)))
                                     [backend-id-url vendor-backend-id]
                                     [lp-id-url id])
        redirect-url               (ss/join url-parts)]
    (info "Redirecting from" id "to" redirect-url)
    {:status 303 :headers {"Location" redirect-url}}))

(defquery application-handlers
  {:parameters       [id]
   :permissions      [{:required [:application/read]}]
   :states           states/all-states}
  [{:keys [application lang]}]
  (ok :handlers (map (fn [{role-name :name :as handler}]
                       (-> handler
                           (assoc :roleName ((keyword lang) role-name))
                           (dissoc :name)))
                     (:handlers application))))

(defquery application-organization-handler-roles
  {:description "Every handler defined in the organization, including
  the disabled ones."
   :parameters  [id]
   :permissions [{:required [:application/show-authorities]}]
   :states      states/all-states}
  [{:keys [organization]}]
  (ok :handlerRoles (:handler-roles @organization)))

(defquery application-organization-archive-enabled
  {:description "Permanent archive flag check as pseudo query. Depends
  on the (delayed) organization parameter and thus implicitly from the
  application id parameter as well."
   :parameters [:id]
   :permissions [{:required [:application/read]}]
   :states states/all-states
   :pre-checks  [(fn [{organization :organization}]
                   (when-not (some-> organization deref :permanent-archive-enabled)
                     (fail :error.archive-not-enabled)))]}
  [_])

(defquery ya-application
  {:parameters [id]
   :states states/all-states
   :permissions [{:required [:application/read]}]
   :pre-checks [(permit/validate-permit-type-is permit/YA)]}
  [_])

;
;Create property formation app based on 'source app'
;

(defn applicant-schemas-have-equal-bodies [primary-operation {application :application}]
  (let [{:keys [name version]}   (:schema-info (domain/get-applicant-document (:documents application)))
        schema-body-based-on-app (:body (schema/get-schema version name))
        schema-body-based-on-po  (->> (op/get-operation-metadata primary-operation)
                                      (:applicant-doc-schema)
                                      (schema/get-schema version)
                                      (:body))]
    (when-not (= schema-body-based-on-app schema-body-based-on-po)
      (fail! :error.documents-do-not-have-matching-schema-bodies-for-applicants))))

(defn primary-operation-is-valid [primary-operation {application :application}]
  (when-not (= primary-operation (->> application :primaryOperation :name))
    (fail! :error.primary-operation-is-not-tonttijako)))

(defquery property-formation-app-invite-candidates
  {:description "Possible parties to invite from the source application to the new application"
   :parameters [id]
   :permissions [{:required [:application/create-property-formation-app]}]
   :input-validators [(partial action/non-blank-parameters [:id])]}
  [{{:keys [id]} :data user :user}]
  (let [candidates (copy-app/copy-application-invite-candidates user id)]
    (if (fail? candidates)
      candidates
      (ok :candidates candidates))))

(defquery property-info
  {:parameters  [id]
   :permissions [{:required [:application/create-property-formation-app]}]}
  [{:keys [application]}]
  (ok :propertyInfo (select-keys application [:propertyId :address :municipality])))

(defn preprocess-doc [new-docs created doc]
  (let [get-schema-name #(get-in % [:schema-info :subtype])
        new-schema-info (:schema-info (util/find-first #(= (get-schema-name doc)
                                                           (get-schema-name %))
                                                       new-docs))]
    (if (not-empty new-schema-info)
      (-> doc
          (assoc :schema-info new-schema-info :created created :id (mongo/create-id))
          model/without-user-id)
      (fail! :error.documents-do-not-match))))

(defcommand create-property-formation-app
  "Creates a new property formation (kiinteistönmuodostus) app based on data from 'source app'.
   Note that party docs are copied from source app to new app.
   At the moment command is only available for plot allocation (tonttijako) apps."
  {:parameters [id invites]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/vector-parameters [:invites])]
   :permissions [{:required [:application/create-property-formation-app]}]
   :pre-checks [(permit/validate-permit-type-is permit/MM)
                (partial primary-operation-is-valid "tonttijako")
                (partial applicant-schemas-have-equal-bodies :kiinteistonmuodostus)]
   :states (states/all-application-states-but [:canceled])}
  [{:keys [application user created organization] :as command}]
  (if-let [check-failed (copy-app/check-valid-auth-invites application invites user)]
    check-failed
    (let [new-app  (app/do-create-application
                     (assoc command :data {:operation    "kiinteistonmuodostus"
                                           :x            (-> application :location first)
                                           :y            (-> application :location second)
                                           :address      (:address application)
                                           :propertyId   (:propertyId application)
                                           :municipality (:municipality application)
                                           :infoRequest  false
                                           :messages     []}))
          new-app-docs (:documents new-app)
          party-docs-from-source-app (->> (domain/get-documents-by-type (:documents application) :party)
                                          (map #(preprocess-doc new-app-docs created %)))
          doc-names-from-source-app (set (map #(get-in % [:schema-info :subtype]) party-docs-from-source-app))
          merged-docs (concat (remove #(doc-names-from-source-app (get-in % [:schema-info :subtype])) new-app-docs)
                              party-docs-from-source-app)
          new-app (-> (merge new-app {:auth (filter #((set invites) (:id %)) (:auth application))
                                      :documents merged-docs})
                      (util/merge-in copy-app/new-auth-map user))]
      (app/do-add-link-permit new-app (:id application))
      (app/insert-application new-app)
      (app/try-autofill-rakennuspaikka new-app created)
      (copy-app/send-invite-notifications! new-app command)
      (ok :id (:id new-app)))))
