(ns lupapalvelu.rest.verdict
  "Implements a verdict submission API for dmCity.

  Similar to `lupapalvelu.rest.review`:
   - User uploads a JSON file and any necessary attachments
   - The JSON describes the verdict (and the attachments)
   - Uses schema to validate the inputs and tries to provide
     relevant error messages as data to the caller

  Note that we do NOT allow submitting a verdict if the target
  application already has verdicts. If the verdict is updated in
  dmCity, the authority should remove the old verdict from Lupapiste
  and re-try the submission."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.automatic-assignment.factory :as factory]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.invoices.core :as invoices]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.pate.verdict-common :as verdict-common]
            [lupapalvelu.pate.verdict-date :as verdict-date]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.rest.schemas :as rest-schemas]
            [lupapalvelu.rest.util :as rest-util]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.verdict :as verdict]
            [monger.operators :refer [$set]]
            [ring.swagger.json-schema :as rjs]
            [ring.util.http-status :as http-status]
            [sade.core :as sade]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.coerce :as stc]
            [schema-tools.core :as st]
            [schema.core :as sc])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

;;; Request coercion and validation

(sc/defschema Filename
  (sc/named ssc/NonBlankStr "Filename"))

;; Noir has a tendency to coerce filenames to keywords, unless the contain the file type suffix
;; (e.g. "foo" => :foo but "foo.json" => "foo.json")
(sc/defschema MultipartFilename
  (sc/named (sc/cond-pre Filename sc/Keyword) "Multipart Filename"))

(sc/defschema FileUpload
  (sc/named
    {:filename     Filename
     :content-type ssc/NonBlankStr
     :size         sc/Int
     :tempfile     (sc/pred #(instance? File %))}
    "Uploaded file"))

(sc/defschema Request
  {:application-id   rest-schemas/ApplicationId
   :verdict          (sc/cond-pre ssc/NonBlankStr
                                  FileUpload)
   MultipartFilename FileUpload})

(def ^:private check-request (sc/checker Request))

(def validate-http-request
  "Perform superficial validation of the HTTP request"
  (rest-util/check-data-step {:src-key   :request
                              :checker   check-request
                              :err-title "Invalid HTTP parameters"}))

(defn- convert-verdict-json
  "Parse the verdict (either a string or a file) as JSON"
  [{{:keys [verdict]} :request :as ctx}]
  (try
    (assoc ctx ::verdict-json
           (cond
             (string? verdict)   (json/decode verdict true)
             (:tempfile verdict) (-> verdict
                                     :tempfile
                                     io/reader
                                     (json/decode-stream true))))
    (catch Exception _
      (assoc ctx
             :error {:title  "Failed to parse verdict file as JSON"
                     :errors [(-> verdict
                                  (select-keys [:filename :content-type :size])
                                  (assoc :error "not-valid")
                                  (rest-util/make-error :verdict))]}))))

(def ^:private api-verdict-code->verdict-code
  "Translate the API verdict code enumeration to LP enums.
   See e.g. `lupapalvelu.pate.schema-helper/verdict-code-map`"
  {"accepted"      :hyvaksytty
   "conditional"   :ehdollinen
   "denied"        :evatty
   "granted"       :myonnetty
   "granted-start" :myonnetty-aloitusoikeudella
   "statement"     :annettu-lausunto
   "tabled"        :lautakunta-poydalle
   "verdict-given" :paatos-annettu})

(sc/defschema VerdictCode (->> api-verdict-code->verdict-code keys (apply sc/enum)))

(def ^:private api-attachment-type->attachment-type
  "Translate the API attachment type enumeration to LP enums"
  {"verdict"         :paatos
   "verdict-extract" :paatosote})

(sc/defschema AttachmentType (->> api-attachment-type->attachment-type keys (apply sc/enum)))

(sc/defschema VerdictData
  {:verdict-code  VerdictCode
   :section       sc/Int
   :decided-by    ssc/NonBlankStr
   :decision-date ssc/Timestamp
   :apply-date    ssc/Timestamp
   :attachments   {Filename {:type        AttachmentType
                             :created-at  ssc/Timestamp
                             :description ssc/NonBlankStr}}})

(def ^:private coercions
  (some-fn stc/json-coercion-matcher
           {ssc/Timestamp (comp date/timestamp
                                (fn [s] (date/zoned-parse s :iso-date)))
            ;; Convert the filenames (back) to strings
            Filename      name}))

(def ^:private coercer (stc/coercer VerdictData coercions))

(def coerce-verdict-data
  "Coerce the JSONish verdict data into proper clojure data"
  (rest-util/coerce-data-step {:src-key   ::verdict-json
                               :dst-key   ::verdict-data
                               :coercer   coercer
                               :err-title "Failed to validate verdict data"
                               :err-path  [:verdict]}))

(defn- check-attachments
  "Check that all the attachment mentioned wrt. the verdict are uploaded"
  [{{:keys [attachments]} ::verdict-data
    :keys                 [request]
    :as                   ctx}]
  (let [verdict-filenames  (->> attachments keys set)
        uploaded-filenames (->> request keys (filter string?) set)]
    (if-not (set/subset? verdict-filenames uploaded-filenames)
      (assoc ctx :error {:title  "Failed to validate verdict data"
                         :errors [{:error    "missing-attachments"
                                   :expected verdict-filenames
                                   :actual   uploaded-filenames}]})
      ctx)))

;;; Application

(def find-application-from-mongo
  (rest-util/find-application-from-mongo-step
    {:application-id-key  [:request :application-id]
     :user-key            :user
     :timestamp-key       :request-timestamp
     :dst-application-key ::application
     :dst-org-key         ::organization
     :dst-command-key     ::command}))

(def ^:private check-application-authorization
  (rest-util/check-application-authorization-step
    {:application-id-key   [:request :application-id]
     :command-key          ::command
     :required-permissions #{:verdict/publish}}))

(defn- check-application-state
  "Check that the application (and organization et al.) are in a suitable state"
  [{{:keys [state]} ::application
    ::keys          [application organization]
    :as             ctx}]
  (let [make-error (fn [err] (rest-util/make-error err :application-id))]
    (if-some [errors (seq (cond-> []
                            ;; Check integration partner
                            (not (dm-cfg/target-enabled? organization
                                                         (permit/permit-type application)))
                            (conj (make-error {:error "dmcity-not-configured"}))
                            ;; Application state allows giving a verdict?
                            (not (util/includes-as-kw? states/give-verdict-states state))
                            (conj (make-error {:error "application-incorrect-state"
                                               :value state}))
                            ;; A verdict has already been given?
                            (seq (verdict-common/all-published-verdicts application))
                            (conj (make-error {:error "application-has-verdict"}))))]
      (assoc ctx :error {:title  "Application error"
                         :errors (vec errors)})
      ctx)))

(sc/defn ctx->verdict :- verdict/Verdict
  {:always-validate true
   :private         true}
  [{:keys  [request-timestamp]
    ::keys [verdict-data]}]
  ;; Unfortunately dmCity does not provide us with any unique identifiers for the verdict
  {:id              (mongo/create-id)
   :kuntalupatunnus "-"
   :source          "dmcity"
   :timestamp       request-timestamp
   :paatokset       [{:id          (mongo/create-id)
                      :paivamaarat {:anto          (:decision-date verdict-data)
                                    :lainvoimainen (:apply-date verdict-data)}
                      :poytakirjat [{:verdict-code    (->> verdict-data
                                                           :verdict-code
                                                           (get api-verdict-code->verdict-code)
                                                           name)
                                     :paatospvm       (:decision-date verdict-data)
                                     :paatoksentekija (:decided-by verdict-data)
                                     :pykala          (str (:section verdict-data))}]}]})

(defn- merge-updates
  [ctx updates]
  (update ctx ::updates util/deep-merge updates))

(defn- with-verdict
  [ctx]
  (let [verdict (ctx->verdict ctx)]
    (-> ctx
        (assoc ::verdict verdict)
        (merge-updates {$set {:verdicts [verdict]}}))))

(sc/defn ->attachment-options :- attachment/AttachmentOptions
  {:always-validate true
   :private         true}
  [{::keys [application verdict]}
   {:keys [created-at description] :as attachment}]
  (let [att-type (-> attachment :type api-attachment-type->attachment-type)]
    {:created         created-at
     :attachment-id   (str (random-uuid))
     :attachment-type (att-type/resolve-type (permit/permit-type application) att-type)
     :target          {:id   (:id verdict)
                       :type :verdict}
     :contents        description
     :modified        created-at
     :locked          true
     :read-only       true}))

(defn- with-attachment-options
  [{{:keys [attachments]} ::verdict-data
    :as                   ctx}]
  (assoc ctx ::attachment-options (reduce-kv
                                    (fn [m filename attachment]
                                      (assoc m filename (->attachment-options ctx attachment)))
                                    {}
                                    attachments)))

(sc/defn ->file-options :- attachment/FileOptions
  {:always-validate true
   :private         true}
  [{:keys [filename size content-type tempfile]} :- FileUpload]
  {:filename     filename
   :size         size
   :content      tempfile
   :content-type content-type})

(defn- with-attachment-file-options
  [{:keys [request]
    :as   ctx}]
  (let [attachments (->> request
                         (filter (comp string? key))
                         (into {}))]
    (assoc ctx ::attachment-file-options (reduce-kv
                                           (fn [m filename attachment]
                                             (assoc m filename (->file-options attachment)))
                                           {}
                                           attachments))))

(defn- save-and-upload-attachments!
  [{::keys [command attachment-options attachment-file-options]
    :as    ctx}]
  {:pre [(map? (:system command))]}
  (log/infof "Uploading %s attachments." (count attachment-options))
  (->> attachment-options
       keys
       (map (juxt (constantly command)
                  attachment-options
                  attachment-file-options))
       (run! #(apply attachment/upload-and-attach! %)))
  ctx)

(defn- with-application-state-updates
  [{::keys [application command] :as ctx}]
  (merge-updates ctx (app-state/state-transition-update
                       (sm/verdict-given-state application)
                       (:created command)
                       application
                       (:user command))))

(def ^:private commit-queued-updates!
  (rest-util/commit-queued-updates-step!
    {:command-key ::command
     :updates-key ::updates}))

(defn- update-verdict-dates!
  "Update the `:verdictDate` field.
   NOTE: deadlines are not set, as we don't produce `:aloitettava` or `:voimassa` dates"
  [{::keys [command] :as ctx}]
  (try
    (verdict-date/update-verdict-date command nil)
    (catch Exception e
      (log/error e "Failed to update verdict dates")))
  ctx)

(defn- create-new-invoice!
  [{::keys [command] :as ctx}]
  (try
    (invoices/new-verdict-invoice command nil)
    (catch Exception e
      (log/error e "Failed to create invoice")))
  ctx)

(defn- send-notifications!
  [{::keys [command] :as ctx}]
  (try
    (notifications/notify! :application-state-change command)
    (catch Exception e
      (log/error e "Failed to process notifications")))
  ctx)

(def ^:private run-attachment-processor! (factory/attachment-assignment-processor identity))

(defn- run-assignment-triggers!
  "Try to run the attachment processor against all the attachments we created"
  [{:keys  [user request-timestamp]
    ::keys [application organization attachment-options]
    :as    ctx}]
  (let [base-response {:user             user
                       :organization     organization
                       :application      application
                       :assignment-group "attachments"
                       :timestamp        request-timestamp}]
    (try
      (->> attachment-options
           vals
           (map (fn [{{:keys [type-group type-id]} :attachment-type
                      :keys                        [attachment-id]}]
                  (assoc base-response :targets [{:id           attachment-id
                                                  :trigger-type {:type-group (name type-group)
                                                                 :type-id    (name type-id)}}])))
           (run! run-attachment-processor!))
      (catch Exception e
        (log/error e "Failed to run attachment processor"))))
  ctx)

(def ^:private insert-process (rest-util/do-all rest-util/check-authentication-step
                                                rest-util/check-authorization-step
                                                validate-http-request
                                                convert-verdict-json
                                                coerce-verdict-data
                                                check-attachments
                                                find-application-from-mongo
                                                check-application-authorization
                                                check-application-state
                                                with-verdict
                                                with-attachment-options
                                                with-attachment-file-options
                                                with-application-state-updates
                                                ;; Persist changes to mongo
                                                save-and-upload-attachments!
                                                commit-queued-updates!
                                                ;; The following steps log errors but
                                                ;; continue processing
                                                update-verdict-dates!
                                                create-new-invoice!
                                                send-notifications!
                                                run-assignment-triggers!))

(defn- on-error
  [{:keys [title errors status]
    :or   {errors []
           status http-status/bad-request}}]
  (log/errorf "Failed to process verdict from dmCity: %d:'%s' %s"
              status title errors)
  (sade/fail title :status status :errors errors))

(defn- on-success
  [{::keys [verdict]}]
  (log/info "Successfully processed verdict from dmCity.")
  (sade/ok
    :verdict-id (:id verdict)
    :status http-status/created))

(defn insert
  "Inserts a verdict into an application"
  [{{:keys [application-id]} :request
    {user-id :id}            :user
    :as                      ctx}]
  {:pre [(map? (:system ctx))]}
  (log/with-logging-context
    ;; Note that we haven't yet validated the application id
    {:applicationId application-id
     :userId        user-id}
    (log/info "Processing verdict from dmCity..")
    (let [ctx (insert-process ctx)]
      (if-some [error (:error ctx)]
        (on-error error)
        (on-success ctx)))))

;;; Response schemas for swagger

(sc/defschema Response
  (st/merge
    rest-schemas/ApiResponse
    {:status sc/Num}))

(sc/defschema SuccessResponse
  {:ok         (sc/eq true)
   :status     (sc/eq http-status/created)
   :verdict-id (rjs/describe ssc/NonBlankStr "Lupapiste ID for the verdict")})

(sc/defschema SimpleErrorResponse
  (st/merge
    Response
    {:ok (sc/eq false)}))

(sc/defschema ErrorMap
  {:error                      (rjs/field ssc/NonBlankStr {:description "Description of the error"
                                                           :example     "Application error"})
   :field                      (rjs/field [ssc/NonBlankStr] {:description "Path to the problematic field"
                                                             :example     ["application-id"]})
   (sc/optional-key :value)    (rjs/field ssc/NonBlankStr {:description "The problematic value"
                                                           :example     "LP-123-2023-12345"})
   (sc/optional-key :expected) (rjs/field [ssc/NonBlankStr] {:description "Expected values"})
   (sc/optional-key :actual)   (rjs/field [ssc/NonBlankStr] {:description "Actual values"})})

(sc/defschema ErrorResponse
  (st/merge
    SimpleErrorResponse
    {:errors [ErrorMap]}))

(def return-code-map
  {http-status/created               {:schema SuccessResponse}
   http-status/bad-request           {:schema ErrorResponse}
   http-status/forbidden             {:schema SimpleErrorResponse}
   http-status/unauthorized          {:schema SimpleErrorResponse}
   http-status/internal-server-error {:schema SimpleErrorResponse}})

(def verdict-form-data
  (rjs/field ssc/NonBlankStr {:description "Verdict data as JSON"}))
