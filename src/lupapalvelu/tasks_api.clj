(ns lupapalvelu.tasks-api
  (:require [clojure.set :as set]
            [lupapalvelu.action
             :refer [defcommand defquery non-blank-parameters parameters-matching-schema update-application]
             :as action]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.assignment :refer [assignments-enabled-for-application]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.automatic-assignment.core :as automatic]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system
             :as mapping-to-krysp]
            [lupapalvelu.document.low-carbon-assessment-api :as low-carbon-assessment]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :refer [infof]]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.task-util :as task-util]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))

;; Helpers

(defn- task-source-type-assertion [types]
  (fn [{{task-id :taskId} :data app :application}]
    (if-let [task (util/find-by-id task-id (:tasks app))]
      (when-not ((set types) (keyword (-> task :source :type)))
        (fail :error.invalid-task-type))
      (fail :error.task-not-found))))

(defn- task-state-assertion [states]
  (fn [{{task-id :taskId} :data app :application}]
    (if-let [task (util/find-by-id task-id (:tasks app))]
      (when-not ((set states) (keyword (:state task)))
        (fail :error.command-illegal-state))
      (fail :error.task-not-found))))

(defn- validate-task-is-not-review [{{task-id :taskId} :data {tasks :tasks} :application}]
  (when (tasks/task-is-review? (util/find-by-id task-id tasks))
    (fail :error.invalid-task-type)))

(defn- validate-task-is-review [{{task-id :taskId} :data {tasks :tasks} :application}]
  (when-not (tasks/task-is-review? (util/find-by-id task-id tasks))
    (fail :error.invalid-task-type)))

(defn- review-type-in? [types task]
  (->> (get-in task [:data :katselmuksenLaji :value])
       (ss/lower-case)
       (contains? types)))

(defn- validate-review-type-in [types]
  {:pre [(set? types) (every? string? types) (every? ss/in-lower-case? types)]}
  (fn [{{task-id :taskId} :data {tasks :tasks} :application}]
    (when-not (review-type-in? types (util/find-by-id task-id tasks))
      (fail :error.invalid-task-type))))

(defn- check-review-request
  "Pre-checker that fails if the review has already been requested."
  [should-exist?]
  (fn [{:keys [application data]}]
    (when-let [task-id (:taskId data)]
      (let [exists? (boolean (some->> application
                                      :tasks
                                      (util/find-by-id task-id)
                                      :request))]
        (when-not (= should-exist? exists?)
          (fail :error.bad-review-request-state))))))


;; API
(def valid-source-types #{"verdict" "task"})

(def valid-states task-util/valid-application-states)

(defn- valid-source [{:keys [id type]}]
  (when (and (string? id) (valid-source-types type))
    {:id id, :type type}))

(defcommand create-task
  {:parameters [id taskName schemaName]
   :optional-parameters [taskSubtype]
   :input-validators [(partial non-blank-parameters [:id :taskName :schemaName])]
   :user-roles #{:authority}
   :states     valid-states}
  [{:keys [created application user data] :as command}]
  (when-not (some #(let [{:keys [name type]} (:info %)]
                     (and (= name schemaName)
                          (= type :task)))
                  (tasks/task-schemas command application))
    (fail! :illegal-schema))
  (let [meta {:created created :assignee user}
        source (or (valid-source (:source data)) {:type :authority :id (:id user)})
        rakennus-data       (when (contains? #{"task-katselmus" "task-katselmus-backend"} schemaName)
                              (util/strip-empty-maps
                               {:rakennus (tasks/rakennus-data-from-buildings application {} (:buildings application))}))
        subtype-field       (case schemaName
                              "task-vaadittu-tyonjohtaja" :kuntaRoolikoodi
                              :katselmuksenLaji) ;; Multiple "task-katselmus-.*" cases
        task                (tasks/new-task schemaName
                                            taskName
                                            (cond-> rakennus-data
                                              (ss/not-blank? taskSubtype) (merge {subtype-field taskSubtype})
                                              (= schemaName "task-lupamaarays") (assoc :maarays taskName))
                                            meta
                                            source)
        validation-results  (tasks/task-doc-validation schemaName task)
        error               (when (seq validation-results)
                              (-> (first validation-results) :result last))]
    (if-not error
      (do
        (update-application command
                            {$push {:tasks task}
                             $set {:modified created}})
        (ok :taskId (:id task)))
      (fail (str "error." error)))))

(defcommand delete-task
  {:description "Deletes a task (requirement) and related assignments but not the related attachments"
   :parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states     valid-states
   :pre-checks [(task-state-assertion (tasks/all-states-but :sent :faulty_review_task))]}
  [{:keys [application created] :as command}]
  (attachment/remove-attachments-targets! application (map :id (tasks/task-attachments application taskId)))
  (factory/delete-review-assignments command id taskId)
  (or (update-application command
                          {$pull {:tasks {:id taskId}}
                           $set  {:modified  created}})
      (ok)))

(defcommand approve-task
  {:description "Authority can approve task, moves to ok"
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states
   :pre-checks [(task-state-assertion (tasks/all-states-but :sent :ok))
                validate-task-is-not-review]}
  [{:keys [application user lang] :as command}]
  (tasks/generate-task-pdfa command (util/find-by-id taskId (:tasks application)))
  (or (tasks/set-state command taskId :ok)
      (ok)))

(defcommand reject-task
  {:description "Authority can reject task, requires user action."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states
   :pre-checks  [(task-state-assertion (tasks/all-states-but :sent))
                 validate-task-is-not-review]}
  [command]
  (or (tasks/set-state command taskId :requires_user_action)
      (ok)))

(defn- mandatory-field! [task field-path]
  (when (ss/blank? (get-in task (concat [:data] field-path [:value])))
    (fail! :error.missing-parameters :field (last field-path))))

(defn validate-required-review-fields
  "This pre-check is not executed when an auth model is being built (thus the `:lang`
  guard). The reason is the co-operation between UI, `review-can-be-marked-done`
  pseudo-query, `update-task` and `review-done` commands."
  [{:keys [application data organization]
    :as   command}]
  (when (:lang data)
    (when-let [task (some-> data :taskId (util/find-by-id (:tasks application)))]
      (let [permit-type     (:permitType application)
            required-fields (cond-> [[:katselmuksenLaji] [:katselmus :pitoPvm]]
                              (= permit-type permit/R) (conj [:katselmus :tila])
                              (= permit-type permit/YA) (conj [:katselmus :huomautukset :kuvaus]))]
        (doseq [field-path required-fields]
          (mandatory-field! task field-path)))

      (let [enriched-task (task-util/enrich-default-task-reviewer command task)
            org           (force organization)
            pitaja-value  (some-> enriched-task :data :katselmus :pitaja :value)]
        (if (:ryhti-data-2026 application)
          (when-not (and (some-> enriched-task :data :katselmus :pitajaDetails :etunimi :value util/fullish?)
                         (some-> enriched-task :data :katselmus :pitajaDetails :sukunimi :value util/fullish?))
            (fail! :error.reviewer-missing))
          (when-not (util/fullish? pitaja-value)
            (fail! :error.reviewer-missing)))
        (when (:review-officers-list-enabled org)
          (when-not (util/find-by-key :code
                                      (:code pitaja-value)
                                      (:reviewOfficers org))
            (fail! :error.invalid-reviewer-code)))))))

(defn- schema-with-type-options
  "Genereate 'subtype' options for readonly elements with sequential body"
  [{info :info body :body}]
  {:schemaName (:name info)
   :schemaSubtype (:subtype info)
   :types      (some (fn [{:keys [name readonly body]}]
                       (when (and readonly (seq body))
                         (for [option body
                               :let [option-id (:name option)]]
                           {:id   option-id
                            :text (i18n/localize i18n/*lang* (:name info) name option-id)})))
                     body)})

(defquery task-types-for-application
  {:description      "Returns a list of allowed schema maps for current application and user. Map has :schemaName, and :types is list of subtypes for that schema"
   :parameters       [:id :lang]
   :user-roles       #{:authority}
   :input-validators [(partial non-blank-parameters [:lang])]
   :states           states/all-states}
  [{application :application :as command}]
  (ok :schemas (->> (tasks/task-schemas command application)
                    (sort-by (comp :order :info))
                    (mapv schema-with-type-options))))

(defn no-sent-reviews-yet? [tasks]
  (->> (filter #(tasks/task-is-review? %) tasks)
       (filter #(= :sent (keyword (:state %))))
       (count)
       (zero?)))

(defn- climate-reports-valid-for-final-review
  [{{task-id :taskId} :data {tasks :tasks} :application :as command}]
  (let [{{:keys [katselmuksenLaji katselmus]} :data} (util/find-by-id task-id tasks)]
    ;; Only check climate reports if this is the final final review
    (when (and (some-> katselmuksenLaji :value (= "loppukatselmus"))
               (some-> katselmus :tila :value (= "lopullinen")))
      (low-carbon-assessment/climate-reports-valid command))))

(defquery review-can-be-marked-done
  {:description      "Review can be marked done by authorities. When allowed, the review done
   button is shown in the UI."
   :parameters       [:id :taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles       #{:authority}
   :states           valid-states
   :pre-checks       [validate-task-is-review
                      (permit/validate-permit-type-is permit/R permit/YA)
                      (task-state-assertion (tasks/all-states-but :sent :faulty_review_task))]}
  [_]
  (ok))

(defcommand review-done
  {:description      "Marks review done, generates PDF/A and sends data to backend. If review
  is partial (osittainen), a new similar draft task is created.
  The review has a dynamic field, 'pitaja', to which the current user is assigned if it has not been set already"
   :parameters       [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks       [validate-task-is-review
                      (permit/validate-permit-type-is permit/R permit/YA)  ; KRYSP mapping currently implemented only for R & YA
                      (task-state-assertion (tasks/all-states-but :sent :faulty_review_task))
                      validate-required-review-fields]
   :user-roles       #{:authority}
   :states           valid-states}
  [{application :application user :user created :created organization :organization :as command}]

  ;; Error checking is done here, not in pre-checks, because update-task does not return an updated auth-model
  ;; the way update-doc does. This means the auth-model can become outdated when the user makes changes.
  ;; Similar logic exists on the frontend to enable/disable the review-done button, but we duplicate the validation
  ;; here as well to ensure server-side enforcement (not just relying on UI button state).
  (if-let [error (climate-reports-valid-for-final-review command)]
    error
    (let [_                  (tasks/update-task-reviewer! command taskId) ; Must be done first
          task               (->> (domain/get-application-no-access-checking id [:tasks])
                                  :tasks
                                  (util/find-by-id taskId))
          tila               (get-in task [:data :katselmus :tila :value])
          task-pdf-version   (tasks/generate-task-pdfa command task)
          application        (-> (domain/get-application-no-access-checking id)
                                 (mapping-to-krysp/post-process-app-for-krysp organization))
          command            (assoc command :application application)
          all-attachments    (:attachments application)
          sent-file-ids      (seq (mapping-to-krysp/save-review-as-krysp command task lang))
          sent-to-krysp?     (boolean sent-file-ids)
          review-attachments (if-not sent-file-ids
                               (->> (:attachments application)
                                    (filter #(= {:type "task" :id taskId} (:target %)))
                                    (map (comp :fileId :latestVersion))
                                    (remove ss/blank?))
                               sent-file-ids)
          set-statement      (util/deep-merge
                               (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)
                               (attachment/create-read-only-update-statements
                                 all-attachments
                                 (if task-pdf-version (conj review-attachments (:fileId task-pdf-version)) review-attachments)))]

      (tasks/set-state command taskId :sent (when (seq set-statement) {$set set-statement}))

      ;; Create a new task if the completed task was partially completed
      (when (= tila "osittainen")
        (let [schema-name   (get-in task [:schema-info :name])
              meta          {:created created :assignee (:assignee task)}
              source        {:type :task :id taskId}
              laji          (get-in task [:data :katselmuksenLaji :value])
              required?     (get-in task [:data :vaadittuLupaehtona :value])
              ;; Use the building data from the completed task so that
              ;; the new task shows which part of the building has
              ;; already been reviewed.
              rakennus-data (when (tasks/task-is-review? task)
                              (if (env/feature? :partial-building-review)
                                (let [buildings (-> task
                                                    (get-in [:data :rakennus])
                                                    (tools/unwrapped))]
                                  (if buildings
                                    {:rakennus buildings}
                                    (util/strip-empty-maps {:rakennus (tasks/rakennus-data-from-buildings {} (:buildings application))})))
                                ;; If feature flag is not enabled, we use the old behaviour which rebuilds
                                ;; the buildings info instead of attempting to copy it from the completed task.
                                (util/strip-empty-maps {:rakennus (tasks/rakennus-data-from-buildings {} (:buildings application))})))
              data          (merge rakennus-data {:katselmuksenLaji   laji
                                                  :vaadittuLupaehtona required?})
              new-task      (tasks/new-task schema-name (:taskname task) data meta source)]
          (infof "sent %s review task %s to KRYSP (%s) - creating new review task (katselmuksenLaji: %s)"
                 tila taskId sent-to-krysp? laji)
          (update-application command {$push {:tasks new-task}})))

      ;; Update application state to constructionStarted if applicable
      (when (and (not= "YA" (:permitType application))
                 (org/pate-scope? application)
                 (-> @organization :automatic-construction-started false? not) ; Default (nil) is true
                 (no-sent-reviews-yet? (:tasks application))
                 (= :constructionStarted (sm/next-state application)))
        (update-application command (app-state/state-transition-update :constructionStarted created application user)))

      ;; No need to review the task since it is complete
      (factory/delete-review-assignments command id taskId)

      (ok :integrationAvailable sent-to-krysp?))))

(defcommand mark-review-faulty
  {:description         "Sets review's state to faulty_review_task, cleares
  its attachments but stores the file ids and filenames. The latter is
  done just in case for future reference. Notes parameter updates the
  katselmus/huomautukset/kuvaus field in the schema."
   :parameters          [id taskId]
   :optional-parameters [notes]
   :input-validators    [(partial non-blank-parameters [:id :taskId])]
   :pre-checks          [validate-task-is-review
                         (permit/validate-permit-type-is permit/R permit/YA)  ; KRYSP mapping currently implemented only for R & YA
                         (task-state-assertion #{:sent})]
   :user-roles          #{:authority}
   :states              valid-states}
  [command]
  (or (tasks/task->faulty command taskId notes)
      (ok)))

(defcommand resend-review-to-backing-system
  {:description      "Resend review data to backend"
   :parameters       [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks       [validate-task-is-review
                      (permit/validate-permit-type-is permit/R permit/YA)  ; KRYSP mapping currently implemented only for R & YA
                      (task-state-assertion #{:sent})
                      (task-source-type-assertion #{:verdict :authority :task})
                      (fn [{:keys [application]}]
                        (let [state (-> application :state keyword)]
                          (when-not (or (:facta-imported application)
                                        (contains? valid-states state))
                            (fail :error.command-illegal-state :state state))))]
   :permissions      [{:required [:application/edit :task/send]
                       :context  {:application {:state states/all-application-states}}}]}
  [{:keys [application organization] :as command}]
  (let [command        (assoc command :application (mapping-to-krysp/post-process-app-for-krysp application
                                                                                                organization))
        task           (util/find-by-id taskId (:tasks application))
        sent-file-ids  (seq (mapping-to-krysp/save-review-as-krysp command task lang))
        sent-to-krysp? (boolean sent-file-ids)]
    (infof "resending review task %s to KRYSP (successful: %s)" taskId sent-to-krysp?)
    (ok :integrationAvailable sent-to-krysp?)))

(defquery is-end-review
  {:description "Pseudo query that fails if the task is neither
  Loppukatselmus nor Osittainen loppukatselmus."
   :parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states valid-states
   :pre-checks [(validate-review-type-in #{"loppukatselmus" "osittainen loppukatselmus"})
                (permit/validate-permit-type-is permit/R)]}
  [_]
  (ok))

(defquery is-other-review
  {:description "Pseudo query that fails if the task is not Muu katselmus"
   :parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states valid-states
   :pre-checks [(validate-review-type-in #{"muu katselmus"})
                (permit/validate-permit-type-is permit/R)]}
  [_]
  (ok))

(defquery is-faulty-review
  {:description      "Pseudo query that succeeds only if the current task
  has been marked faulty."
   :parameters       [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles       #{:authority}
   :states           valid-states
   :pre-checks       [(fn [{{task-id :taskId} :data {tasks :tasks} :application}]
                        (when-not (some-> (util/find-by-id task-id tasks)
                                          :state
                                          (util/=as-kw :faulty_review_task))
                          (fail :error.not-faulty)))]}
  [_]
  (ok))

(defn matches-automatic-assignment-filters
  "Pre-check that fails if the task does not match any filters."
  [{:keys [application data] :as command}]
  (when-let [task-id (:taskId data)]
    (when-not (some->> (factory/find-open-review application task-id)
                       :taskname
                       (automatic/resolve-filters command :review-name))
      (fail :error.no-matching-filters))))

(defn good-request-operation-ids
  "Pre-check that fails if any of the given `operation-ids` does not exist in the
  application."
  [{:keys [application data]}]
  (when-let [op-ids (some-> data :request :operation-ids set not-empty)]
    (let [good-ids (->> application
                        app-utils/get-operations
                        (map :id)
                        set)]
      (when-not (empty? (set/difference op-ids good-ids))
        (fail :error.invalid-operation-ids)))))

(defschema OperationBuilding
  {:opId                          ssc/NonBlankStr
   :opName                        ssc/NonBlankStr
   (sc/optional-key :buildingId)  sc/Str
   (sc/optional-key :description) sc/Str
   (sc/optional-key :nationalId)  sc/Str})

(defschema ReviewRequest
  {(sc/optional-key :operation-ids) [ssc/NonBlankStr]
   :message                         ssc/NonBlankStr
   :contact                         {:name  ssc/NonBlankStr
                                     :email ssc/Email
                                     :phone ssc/Tel}})

(defcommand request-review
  {:description      "Creates review request and triggers automatic assignments."
   :parameters       [id taskId request]
   :input-validators [(partial non-blank-parameters [:id :taskId])
                      (partial parameters-matching-schema [:request]
                               ReviewRequest)]
   :pre-checks       [validate-task-is-review
                      (task-state-assertion
                        (tasks/all-states-but :sent :faulty_review_task))
                      (check-review-request false)
                      good-request-operation-ids
                      assignments-enabled-for-application
                      matches-automatic-assignment-filters]
   :categories       #{:reviews}
   :user-roles       #{:applicant}
   :states           valid-states}
  [{:keys [created user] :as command}]
  (let [request (-> request
                    util/strip-blanks
                    ss/trimwalk)]
    (factory/process-review-automatic-assignments command taskId request)
    (or (update-application command
                            {:tasks {$elemMatch {:id taskId}}}
                            {$set {:tasks.$.request {:details request
                                                     :created created
                                                     :user    (usr/summary user)}}})
        (ok))))

(defcommand cancel-review-request
  {:description      "Cancels an open review request. Removes request information and task
  assignments."
   :parameters       [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :pre-checks       [validate-task-is-review
                      (task-state-assertion
                        (tasks/all-states-but :sent :faulty_review_task))
                      (check-review-request true)
                      assignments-enabled-for-application]
   :categories       #{:reviews}
   :user-roles       #{:applicant}
   :states           valid-states}
  [{:keys [created user] :as command}]
  (factory/delete-review-assignments id taskId)
  (or (update-application command
                          {:tasks {$elemMatch {:id taskId}}}
                          {$unset {:tasks.$.request true}})
      (ok)))

(defquery application-operation-buildings
  {:description      "Convenience query that returns basic information on the application
  buildings that are linked to operations."
   :parameters       [id]
   :input-validators [(partial non-blank-parameters [:id])]
   :permissions      [{:required [:application/edit]}]
   :states           valid-states}
  [{:keys [application]}]
  (ok :buildings (app-utils/application-operation-buildings application)))

(defmethod action/allowed-actions-for-category :reviews
  [{:keys [application] :as command}]
  (if-let [task-id (get-in command [:data :taskId])]
    {task-id (action/allowed-category-actions-for-command :reviews command)}
    (some-> application
            :tasks
            seq
            (action/allowed-actions-for-collection :reviews
                                                   (fn [application task]
                                                     {:id     (:id application)
                                                      :taskId (:id task)})
                                                   command))))
