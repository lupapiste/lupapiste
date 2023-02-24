(ns lupapalvelu.rest.review
  (:require [clojure.java.io :as io]
            [clojure.set :as cljset]
            [clojure.walk :refer [keywordize-keys]]
            [json-schema.core :as json-schema]
            [lupapalvelu.action :as action]
            [lupapalvelu.api-common :refer :all]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.bind :as attachment.bind]
            [lupapalvelu.document.persistence :as persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.json :as json]
            [lupapalvelu.rest.schemas :refer :all]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [noir.response :as resp]
            [sade.core :refer [ok fail fail? def-]]
            [sade.date :as date]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [infof warnf]])
  (:import [clojure.lang ExceptionInfo]))

(def- muutunnus-sovellus-matti    "MATTI")
(def- ya-review-schema            (slurp (io/resource "ya-review-schema.json")))

;;; Pipeline helpers

;; Updating the review is a multi-step process with lots of potential errors on
;; which we have to stop the process. Here each step is modeled as a function
;; `context -> context`, i.e. a function that takes a context map (the state of
;; the process), does something to move to process forward, and returns an
;; updated context map. If a step fails, it adds the `:error` key to the
;; context, possibly with some other keys that contain the details of the
;; failure.

;; The following helpers are used to compose the steps into a single pipeline,
;; similar to how one might compose them with Clojure's threading macros (->,
;; ->>, etc.), but with error checking.

(defn- do-all [& steps]
  "Composes the given steps (context -> context) into a single step , short-circuiting on error."
  (fn [context]
    (reduce (fn [context step]
              (let [result (step context)]
                (if (contains? result :error)
                  ;; Step failed, short-circuiting.
                  (reduced result)
                  ;; Step succeeded, move on to the next one.
                  result)))
            context steps)))

(defn- do-one [& steps]
  "Composes the given steps (context -> context), short-circuiting on success."
  (fn [context]
    (reduce (fn [context step]
              ;; Clear the error set by the previous step.
              (let [result (step (dissoc context :error))]
                (if (contains? result :error)
                  ;; Step failed, try the next one.
                  result
                  ;; Step succeeded, short-circuiting.
                  (reduced result))))
            context steps)))

;;; Access control

(defn- check-authentication [{:keys [user] :as context}]
  (if-not user
    (assoc context :error :unauthenticated)
    context))

(defn- check-authorization [{:keys [user] :as context}]
  (if-not (usr/rest-user? user)
    (assoc context :error :unauthorized)
    context))

;;; Reading the request

;; In the following function names "read x" means "make sense of x", i.e.
;; validate and convert from the REST API's representation to an internal
;; representation.

(defn- read-application-id [{:keys [request], :as context}]
  (let [application-id (:application-id request)]
    (-> context
        (assoc :application-id application-id)
        (cond->
          (sc/check ApplicationId application-id)
          (assoc :error :invalid-application-id)))))

(defn- canonical-uuid
  "Converts the given UUID string into a canonical representation.
  Returns nil if the string is not a valid UUID."
  [s]
  (let [sl (ss/lower-case s)]
    (when (nil? (sc/check sssc/UUIDStr sl))
      sl)))

(defn- read-review-id [{:keys [request], :as context}]
  (let [review-id (:review-id request)]
    (if-let [canonical-review-id (canonical-uuid review-id)]
      (assoc context
             :review-id canonical-review-id)
      (assoc context
             :review-id review-id
             :error :invalid-review-id))))

(def- katselmuksen-laji-mapping
  "Mapping converting katselmuksen-laji from rest form to lupapiste form."
  {"ALOITUSKATSELMUS"   "Aloituskatselmus"
   "LOPPUKATSELMUS"     "Loppukatselmus"
   "MUU_VALVONTAKAYNTI" "Muu valvontak\u00e4ynti"})

(def- attachment-type-mapping
  ;; See lupapiste-commons.attachment-types/YleistenAlueidenLuvat-v2
  {"KATSELMUKSEN_LIITE" {:type-group "muut"
                         :type-id "muu"}
   "KATSELMUKSEN_POYTAKIRJA" {:type-group "katselmukset_ja_tarkastukset"
                              :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}
   "MUU" {:type-group "muut"
          :type-id "muu"}
   "VALOKUVA" {:type-group "yleiset-alueet"
               :type-id "valokuva"}})

;; The data received in the request is converted to internal representations
;; that are easier to work with in the later steps of the pipeline.

(defn- make-attachment [liitetiedosto-from-request]
  (let [{:keys [request_part tyyppi kuvaus]} liitetiedosto-from-request]
    {:request_part request_part
     :metadata {:type (attachment-type-mapping tyyppi)
                :description kuvaus}}))

(defn- make-review [katselmus-from-request]
  "Creates a review from the review document received in the request."
  (-> katselmus-from-request

      (cljset/rename-keys {:katselmuksen_laji :type})
      (update :type katselmuksen-laji-mapping)

      (cljset/rename-keys {:liitetiedostot :attachments})
      (update :attachments #(map make-attachment %))))

(defn- read-review [{:keys [request], :as context}]
  (let [katselmus-json (:katselmus request)]
    (try
      (json-schema/validate ya-review-schema katselmus-json)
      (assoc context :review (make-review (json/decode katselmus-json true)))
      (catch ExceptionInfo e
        (assoc context
               :katselmus-json katselmus-json
               :error :review-validation-failed
               :review-validation-errors (:errors (ex-data e)))))))

(defn- validate-attachments [review attachments]
  (let [attachment-request-parts-in-review-metadata (some->> review :attachments (mapv :request_part) set)
        received-attachment-request-parts (->> attachments keys (map name) set)
        request-parts-in-metadata-not-received-in-request (cljset/difference
                                                            attachment-request-parts-in-review-metadata
                                                            received-attachment-request-parts)
        received-attachment-request-parts-not-in-metadata (cljset/difference
                                                            received-attachment-request-parts
                                                            attachment-request-parts-in-review-metadata)]
    (not-empty (cljset/union
                 request-parts-in-metadata-not-received-in-request
                 received-attachment-request-parts-not-in-metadata))))

(defn- read-attachments [{:keys [review request] :as context}]
  (let [attachments (-> request
                        (dissoc :katselmus :application-id :review-id)
                        keywordize-keys)]
    (if-let [errors (validate-attachments review attachments)]
      (assoc context
             :error :invalid-attachment-parts
             :review-attachment-validation-errors errors)
      (assoc context
             :attachments attachments))))

(defn- combine-attachments-with-review [{:keys [attachments] :as context}]
  (-> context
      (update-in [:review :attachments]
                 (fn [review-attachments]
                   (for [a review-attachments]
                     (let [rp-id (keyword (:request_part a))
                           rp (get attachments rp-id)]
                       (-> a
                           (assoc :file (:tempfile rp))
                           (assoc-in [:metadata :filename] (:filename rp))
                           (dissoc :request_part))))))
      (dissoc :attachments)))

(def- read-request (do-all read-application-id
                           read-review-id
                           read-review
                           read-attachments
                           combine-attachments-with-review
                           ;; Make sure that the downstream steps use the
                           ;; validated and canonicalized values produced by
                           ;; read-request and not the raw values from the
                           ;; request.
                           #(dissoc % :request)))

;;; Logging

(defn- save-review-data-message! [user status data timestamp]
  (let [message-id (imessages/create-id)
        application-id (:application-id data)]
    (imessages/save
     (cond-> {:id message-id
              :direction "in"
              :messageType "upsert-review-data"
              :format "json"
              :created timestamp
              :partner "matti"
              :status  status
              :action "upsert-review-data"
              :data data}

       ;; imessages/save requires the application ID to be valid, so only add it
       ;; if it passes validation.
       (nil? (sc/check ApplicationId application-id))
       (assoc :application {:id application-id})

       (some? user)
       (assoc :initiator (select-keys user [:id :username]))))
    message-id))

(defn- log-integration-message-start! [{:keys [user timestamp request] :as context}]
  (let [modified-request (-> request
                             (dissoc :user)
                             keywordize-keys
                             (->> (util/postwalk-map #(dissoc % :tempfile))))
        message-id (save-review-data-message! user
                                              "processing"
                                              modified-request
                                              timestamp)]
    (assoc context :integration-message-id message-id)))

(defn- log-integration-message-end! [{:keys [integration-message-id] :as context}]
  (imessages/set-message-status integration-message-id "processed")
  context)

;;; Application lookup

(defn- in-post-verdict-state? [application]
  (contains? states/ya-post-verdict-states (keyword (:state application))))

(defn- find-and-check-application [{:keys [application-id user timestamp] :as context}]
  (let [application (domain/get-application-as application-id user)]
    (cond
      (nil? application)                         (assoc context :error :no-such-application)
      (not (in-post-verdict-state? application)) (assoc context :error :application-in-wrong-state
                                                        :application-state (keyword (:state application)))
      :else
      (assoc context
             :application application
             ;; Some application update functions we need to call require a command object,
             ;; which we don't have since our entrypoint is a REST API and not a Lupapiste
             ;; command created with `defcommand`. Luckily we can create one from the
             ;; application with `application->command`.
             :command (-> (action/application->command application user) (assoc :created timestamp))))))

;;; Task lookup

(defn- find-ya-tasks [{:keys [application], :as context}]
  (assoc context :ya-tasks
         (->> (-> application :tasks) (filter #(= "task-katselmus-ya" (-> % :schema-info :name))))))

(defn find-task-with-same-muutunnus [{:keys [ya-tasks review-id] :as context}]
  (if-let [task (->> ya-tasks
                     (some #(when (= review-id (-> % :data :muuTunnus :value)) %)))]
    (assoc context
           :task task
           :task-origin :existing-review)
    (assoc context :error :no-task-with-same-muutunnus)))

(defn find-task-with-same-katselmuksen-laji [{:keys [ya-tasks review] :as context}]
  (if-let [task (->> ya-tasks
                     (some #(and (= (:type review) (-> % :data :katselmuksenLaji :value))
                                 (ss/blank? (-> % :data :muuTunnus :value))
                                 %)))]
    (assoc context
           :task task
           :task-origin :existing-empty-template)
    (assoc context :error :no-task-with-same-katselmuksen-laji)))

(defn- create-new-task [{:keys [command user timestamp review] :as context}]
  (let [task-data {:katselmuksenLaji        (:type review)
                   :vaadittuLupaehtona      false}
        meta {:created  timestamp
              :assignee user
              :state    :ok}
        source {:type "background"}  ;; similar source type is in use in R
        task (tasks/new-task "task-katselmus-ya"
                             (:type review)
                             task-data
                             meta
                             source)
        validation-results  (tasks/task-doc-validation "task-katselmus-ya" task)]

    ;; This assert should never fail in production, it is only for revealing development-time bugs. The "review-data" here has already come through the json-schema.
    (assert (empty? validation-results) (str "Newly created review task is not valid. Validation results: " validation-results))
    (action/update-application command
                               {$push {:tasks task}
                                $set {:modified timestamp}})
    (assoc context
           :task task
           :task-origin :newly-created)))

(def- find-or-create-task (do-all find-ya-tasks
                                  (do-one find-task-with-same-muutunnus
                                          find-task-with-same-katselmuksen-laji
                                          create-new-task)))

;;; Tasks state checks

(defn check-task-type-matches-review [{:keys [task review], :as context}]
  (let [review-type-in-lupapiste (-> task :data :katselmuksenLaji :value)]
    (if (not= review-type-in-lupapiste
              (:type review))
      (assoc context :error :muutunnus-task-type-mismatch
             :differences {:received-type       (:type review)
                           :review-in-lupapiste review-type-in-lupapiste})
      context)))

;;; Task updates

(defn- delete-old-attachments [{:keys [application user task review-minutes-attachment] :as context}]
  (attachment/delete-attachments!
    application
    (for [a (:attachments application)
          :when (and (= (get-in a [:latestVersion :user :id])
                        (:id user))
                     (= (get-in a [:target :id])
                        (:id task))
                     (not= (:id a)
                           (:id review-minutes-attachment)))]
      (:id a)))
  context)

(defn- add-new-attachments [{:keys [command review task user] :as context}]
  (doseq [a (:attachments review)]
    (let [{:keys [fileId]} (file-upload/save-file
                             {:filename (:filename (:metadata a))
                              :content (:file a)}
                             :uploader-user-id (:id user))]
      (attachment.bind/make-bind-job command :attachment
                                     [{:fileId fileId
                                       :contents (:description (:metadata a))
                                       :target {:type :task
                                                :id (:id task)}
                                       :type (:type (:metadata a))}])))
  context)

(def- update-attachments (do-all delete-old-attachments
                                 add-new-attachments))

(defn- ->mongo-date-format [date]
  (date/finnish-date date :zero-pad))

(defn- update-task [{:keys [application timestamp review-id review task task-origin] :as context}]
  (let [updates [[[:katselmus :pitaja]                      (:pitaja review)]
                 [[:katselmus :pitoPvm]                     (->mongo-date-format (:pitopaiva review))]
                 [[:katselmus :lasnaolijat]                 (or (:lasnaolijat review) "")]
                 [[:katselmus :poikkeamat]                  (or (:poikkeamat review) "")]
                 [[:katselmus :huomautukset :kuvaus]        (or (:huomautettavaa review) "")]
                 [[:katselmus :huomautukset :toteaja]       (:toteaja review)]
                 [[:katselmus :huomautukset :toteamisHetki] (->mongo-date-format (:toteamishetki review))]
                 [[:katselmus :huomautukset :maaraAika]     (->mongo-date-format (:maaraaika review))]
                 [[:muuTunnus]         review-id]
                 [[:muuTunnusSovellus] muutunnus-sovellus-matti]]]
    ;; NOTE: Using persistence/persist-model-updates instead of the "update-task" command,
    ;; because the command included read-only checking for the document fields, and e.g. the fields :muuTunnus and :muuTunnusSovellus
    ;; are marked as read-only in the document schema.
    (persistence/persist-model-updates application :tasks task updates timestamp)
    (condp = task-origin
      :newly-created
      (infof "Review created. Task id for it: %s." (:id task))

      :existing-empty-template
      (infof "Review with id %s updated (empty existing review template filled)." (:id task))

      :existing-review
      (infof "Review with id %s updated (overwritten)." (:id task))))
  context)

(defn- mark-review-as-completed [{:keys [command task] :as context}]
  (tasks/set-state command (:id task) :sent)
  context)

(defn- generate-review-minutes [{:keys [application task user] :as context}]
  (let [task (util/find-by-id (:id task) (:tasks application))]
    (assoc context :review-minutes-attachment (tasks/generate-task-pdfa application task user "fi"))))

(defn upsert
  "Matti sends reviews with UUIDs which it has created (the review-id parameter).\n
   The review tasks are saved into Lupapiste and their MuuTunnus field is filled with the received Matti UUID,\n
   and muuTunnusSovellus field with text \"Matti\".\n
   No tasks are touched if validation fails.
   Business logic:\n
   1) If a review task with MuuTunnus matching the Matti's UUID is FOUND on the specified application, then it is updated.\n
   2) When a review task with MuuTunnus matching the review-id parameter is NOT FOUND:\n
       a) If application's required reviews (vaaditut katselmukset) has a free \"empty\" review of the received type (aloitus, loppu, valvonta), then it is updated.\n
       b) If not, then an all-new review is created."
  [context]
  {:pre [(every? #(contains? context %) [:request :user :timestamp])]}
  (let [result ((do-all log-integration-message-start!
                        check-authentication
                        check-authorization
                        read-request
                        find-and-check-application
                        find-or-create-task
                        check-task-type-matches-review
                        update-task
                        ;; Re-fetch the application so `generate-review-minutes`
                        ;; gets a version with our updates to `:tasks`.
                        find-and-check-application
                        generate-review-minutes
                        ;; `update-attachments` starts a background job that
                        ;; interferes with `generate-review-minutes,` so we run
                        ;; it only after `generate-review-minutes` has finished.
                        update-attachments
                        mark-review-as-completed
                        log-integration-message-end!)
                context)
        logged-failure (fn [status-code message]
                         (warnf "request %s - %s" (:integration-message-id result) message)
                         (resp/status status-code (fail message)))]
    (case (:error result)
      :unauthenticated
      (resp/status 401 (fail "Unauthenticated"))

      :unauthorized
      (resp/status 403 (fail (str "Unauthorized, user: " (:user result))))

      :invalid-application-id
      (logged-failure 400 (str "Invalid application-id: " (:application-id result)))

      :invalid-review-id
      (logged-failure 400 (str "Invalid review-id: " (:review-id result)))

      :review-validation-failed
      (logged-failure 400 (str "Review schema validation failed: "
                               (:review-validation-errors result)))

      :invalid-attachment-parts
      (logged-failure 400 (str "Invalid attachments metadata in request: " (:review-attachment-validation-errors result)))

      :no-such-application
      (logged-failure 404 (str "No such application: " (:application-id result)))

      :application-in-wrong-state
      (logged-failure 404 (str "Application in wrong state: " (:application-state result)))

      :muutunnus-task-type-mismatch
      (logged-failure 400 (str "Review type not matching the one in Lupapiste: " (:differences result)))

      (case (:task-origin result)
        :newly-created
        (resp/status 201 (ok :text "Review created"))

        :existing-empty-template
        (resp/status 201 (ok :text "Review updated (empty template filled)"))

        :existing-review
        (resp/status 204 (ok :text "Review updated (overwritten)"))))))
