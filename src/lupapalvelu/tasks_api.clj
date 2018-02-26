(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]))

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

;; API
(def valid-source-types #{"verdict" "task"})

(def valid-states states/all-application-states-but-draft-or-terminal)

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
  (when-not (some #(let [{:keys [name type]} (:info %)] (and (= name schemaName ) (= type :task))) (tasks/task-schemas application))
    (fail! :illegal-schema))
  (let [meta {:created created :assignee user}
        source (or (valid-source (:source data)) {:type :authority :id (:id user)})
        rakennus-data (when (contains? #{"task-katselmus" "task-katselmus-backend"} schemaName)
                        (util/strip-empty-maps {:rakennus (tasks/rakennus-data-from-buildings {} (:buildings application))}))
        task (tasks/new-task schemaName
                             taskName
                             (if-not (ss/blank? taskSubtype)
                               (merge rakennus-data {:katselmuksenLaji taskSubtype})
                               rakennus-data)
                             meta
                             source)
        validation-results (tasks/task-doc-validation schemaName task)
        error (when (seq validation-results)
                (-> (first validation-results)
                    :result
                    last))]
    (if-not error
      (do
        (update-application command
                            {$push {:tasks task}
                             $set {:modified created}})
        (ok :taskId (:id task)))
      (fail (str "error." error)))))

(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states     valid-states
   :pre-checks [(task-state-assertion (tasks/all-states-but :sent :faulty_review_task))
                tasks/deny-removal-of-vaadittu-lupaehtona]}
  [{:keys [application created] :as command}]
  (attachment/delete-attachments! application (map :id (tasks/task-attachments application taskId)))
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))

(defcommand approve-task
  {:description "Authority can approve task, moves to ok"
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states
   :pre-checks [(task-state-assertion (tasks/all-states-but :sent :ok))
                validate-task-is-not-review]}
  [{:keys [application user lang] :as command}]
  (tasks/generate-task-pdfa application (util/find-by-id taskId (:tasks application)) user lang)
  (tasks/set-state command taskId :ok))

(defcommand reject-task
  {:description "Authority can reject task, requires user action."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states
   :pre-checks  [(task-state-assertion (tasks/all-states-but :sent))
                 validate-task-is-not-review]}
  [{:keys [application] :as command}]
  (tasks/set-state command taskId :requires_user_action))


(defn- validate-review-kind [{{task-id :taskId} :data} {tasks :tasks}]
  (when (ss/blank? (get-in (util/find-by-id task-id tasks) [:data :katselmuksenLaji :value]))
    (fail! :error.missing-parameters)))

(defn- validate-required-review-fields! [{{task-id :taskId :as data} :data} {tasks :tasks :as application}]
  (when (= (permit/permit-type application) permit/R)
    (let [review (get-in (util/find-by-id task-id tasks) [:data :katselmus])]
      (when (some #(ss/blank? (get-in review [% :value])) [:pitoPvm :pitaja :tila])
        (fail! :error.missing-parameters)))))

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
  [{application :application}]
  (ok :schemas (->> application
                 (tasks/task-schemas)
                 (sort-by (comp :order :info))
                 (map schema-with-type-options))))

(defn- root-task [application {source :source :as task}]
  (let [{:keys [id type]} source]
    (if (= "task" type)
      (recur application (util/find-by-id id (:tasks application)))
      task)))

(defn no-sent-reviews-yet? [tasks]
  (->> (filter #(tasks/task-is-review? %) tasks)
       (filter #(= :sent (keyword (:state %))))
       (count)
       (zero?)))

(defquery review-can-be-marked-done
  {:description "Review can be marked done by authorities"
   :parameters  [:id :taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles  #{:authority}
   :states      valid-states
   :pre-checks  [validate-task-is-review
                 (permit/validate-permit-type-is permit/R permit/YA)
                 (task-state-assertion (tasks/all-states-but :sent :faulty_review_task))]}
  [_])

(defcommand review-done
  {:description "Marks review done, generates PDF/A and sends data to backend"
   :parameters  [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks  [validate-task-is-review
                 (permit/validate-permit-type-is permit/R permit/YA)  ; KRYSP mapping currently implemented only for R & YA
                 (task-state-assertion (tasks/all-states-but :sent))]
   :user-roles  #{:authority}
   :states      valid-states}
  [{application :application user :user created :created organization :organization :as command}]
  (validate-required-review-fields! command application)
  (validate-review-kind command application)
  (let [task (util/find-by-id taskId (:tasks application))
        tila (get-in task [:data :katselmus :tila :value])

        task-pdf-version (tasks/generate-task-pdfa application task user lang)
        application (-> (domain/get-application-no-access-checking id)
                        (app/post-process-app-for-krysp @organization))
        all-attachments (:attachments application)
        command (assoc command :application application)

        sent-file-ids (mapping-to-krysp/save-review-as-krysp command task lang)
        sent-to-krysp? (not (nil? sent-file-ids))
        review-attachments (if-not sent-file-ids
                             (->> (:attachments application)
                                  (filter #(= {:type "task" :id taskId} (:target %)) )
                                  (map (comp :fileId :latestVersion))
                                  (remove ss/blank?))
                             sent-file-ids)
        set-statement (util/deep-merge
                        (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)
                        (attachment/create-read-only-update-statements
                          all-attachments
                          (if task-pdf-version (conj review-attachments (:fileId task-pdf-version)) review-attachments)))]

    (tasks/set-state command taskId :sent (when (seq set-statement) {$set set-statement}))

    (case tila
      ; Create new, similar task
      "osittainen"
      (let [schema-name (get-in task [:schema-info :name])
            meta {:created created :assignee (:assignee task)}
            source {:type :task :id taskId}
            laji (get-in task [:data :katselmuksenLaji :value])
            rakennus-data (when (tasks/task-is-review? task)
                            (util/strip-empty-maps {:rakennus (tasks/rakennus-data-from-buildings {} (:buildings application))}))
            data (merge rakennus-data {:katselmuksenLaji laji})
            new-task (tasks/new-task schema-name (:taskname task) data meta source)]
        (infof "sent %s review task %s to KRYSP (%s) - creating new review task (katselmukenLaji: %s)"
               tila taskId sent-to-krysp? laji)
        (update-application command {$push {:tasks new-task}}))

      ; Mark the state of the original task to final
      "lopullinen"
      (let [root-task (root-task application task)
            task-updates [[[:katselmus :tila] "lopullinen"]]
            schema  (schemas/get-schema (:schema-info task))
            updates (filter (partial doc-persistence/update-key-in-schema? (:body schema)) task-updates)]
        (when (and (not= (:id root-task) taskId)
                   (get-in root-task [:data :vaadittuLupaehtona :value]))
          (infof "sent task %s to KRYSP (%s)  - updated lupaehto task as final (%s)" taskId sent-to-krysp? tila)
          (doc-persistence/persist-model-updates application "tasks" root-task updates created)))

      :nop)

    (when (and (env/feature? :pate-json)
               (org/pate-org? (:id @organization))
               (no-sent-reviews-yet? (:tasks application))
               (= :constructionStarted (sm/next-state application)))
      (update-application command (app-state/state-transition-update :constructionStarted created application user)))

    (ok :integrationAvailable sent-to-krysp?)))

(defcommand mark-review-faulty
  {:description      "Sets review's state to faulty_review_task, cleares
  its attachments but stores the file ids and filenames. The latter is
  done just in case for future reference. Notes parameter updates the
  katselmus/huomautukset/kuvaus field in the schema."
   :parameters       [id taskId notes]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :pre-checks       [validate-task-is-review
                      (permit/validate-permit-type-is permit/R permit/YA)  ; KRYSP mapping currently implemented only for R & YA
                      (task-state-assertion #{:sent})]
   :user-roles       #{:authority}
   :states           valid-states}
  [command]
  (tasks/task->faulty command taskId notes))

(defcommand resend-review-to-backing-system
  {:description "Resend review data to backend"
   :parameters  [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks  [validate-task-is-review
                 (permit/validate-permit-type-is permit/R permit/YA)  ; KRYSP mapping currently implemented only for R & YA
                 (task-state-assertion #{:sent})
                 (task-source-type-assertion #{:verdict :authority :task})]
   :user-roles  #{:authority}
   :states      valid-states}
  [{application :application user :user organization :organization created :created :as command}]
  (let [command        (assoc command :application (app/post-process-app-for-krysp application @organization))
        sent-file-ids  (mapping-to-krysp/save-review-as-krysp command (util/find-by-id taskId (:tasks application)) lang)
        sent-to-krysp? (not (nil? sent-file-ids))]
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
                (permit/validate-permit-type-is permit/R)]})

(defquery is-other-review
  {:description "Pseudo query that fails if the task is not Muu katselmus"
   :parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states valid-states
   :pre-checks [(validate-review-type-in #{"muu katselmus"})
                (permit/validate-permit-type-is permit/R)]})

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
                          (fail :error.not-faulty)))]})
