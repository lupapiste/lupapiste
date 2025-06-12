(ns lupapalvelu.notices.core
  "Notices are applications that follow (rather limited) notice state machine. Thus, many
  typical use cases are not available to them. In the operations metadata, they are
  marked with `notice` flag."
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.application-replace-operation :as replace-op]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.document.schemas :as doc-schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.schemas :as notices-schemas]
            [lupapalvelu.notices.util :as notices-util]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.quill :as quill]
            [lupapalvelu.shared-quill :as squill]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [fail! now]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn- missing-documents [{:keys [application organization created]} new-operation-name]
  (let [organization (force organization)
        required     (set (replace-op/get-document-schema-names-for-operation organization
                                                                              {:name new-operation-name}))]
    (some->> (:documents application)
             (map (util/fn-> :schema-info :name))
             (into #{})
             (set/difference required)
             seq
             (mapv #(app/make-document {:primary-operation-name new-operation-name
                                        :schema-flags           (tools/resolve-schema-flags (util/mmap application organization))
                                        :created                created
                                        :schema                 (doc-schemas/get-schema (:schema-version application) %)})))))

(defn- missing-attachments
  [application organization created]
  (let [{:keys [state primaryOperation tosFunction
                attachments]} application
        att-types             (some->> attachments seq (map :type) (into #{}))]
    (seq (app/make-attachments created primaryOperation
                               (force organization) state tosFunction
                               {:existing-attachments-types att-types
                                :ryhti-data-2026            (:ryhti-data-2026 application)}))))

(defn- purkamisilmoitus->purkaminen-updates
  "Returns a vector of `:query`, `:updates` maps."
  [{:keys [application created organization] :as command}]
  {:pre [(notices-util/building-demolition? application)]}
  (let [{op     :primaryOperation
         app-id :id} application
        op-id        (:id op)
        {doc-id :id} (util/find-first (util/fn-> :schema-info
                                                 :op :id (= op-id))
                                      (:documents application))
        new-docs     (missing-documents command "purkaminen")
        new-atts     (-> application
                         (assoc-in [:primaryOperation :name] "purkaminen")
                         (missing-attachments organization created))
        purkamisilmoitus-schema-name (operations/get-operation-schema (:name op) application)
        purkaminen-schema-name (operations/get-operation-schema "purkaminen" application)]
    (cond-> [{:query   {:_id app-id
                        :documents {$elemMatch {:id               doc-id
                                                ;; Just in case
                                                :schema-info.name purkamisilmoitus-schema-name}}}
              :updates (cond-> {$set   {:operation-name                  :purkaminen
                                        :primaryOperation.name           :purkaminen
                                        :documents.$.schema-info.name    purkaminen-schema-name
                                        :documents.$.schema-info.op.name :purkaminen
                                        :convertedToApplication          created
                                        :modified                        created
                                        ;; LPK-7430: Reset state and submitted timestamp to clear up "käsittelyaika"
                                        :submitted                       nil
                                        :state                           "open"
                                        ;; LPK-7430: Reset tosFunction ("Menettely")
                                        :tosFunction                     (app/tos-function (force organization) "purkaminen")}
                                $unset {:documents.$.data.buildingDemolition true
                                        :documents.$.data.kohde              true}}
                         new-atts (assoc-in [$push :attachments] {$each new-atts}))}]
      new-docs (conj {:query   {:_id app-id}
                      :updates {$push {:documents {$each new-docs}}}}))))

(defn purkamisilmoitus->purkaminen
  "Transforms the `command` application in mongo form purkamisilmoitus to purkaminen."
  [command]
  (doseq [{:keys [query updates]} (purkamisilmoitus->purkaminen-updates command)]
    (mongo/update-by-query :applications query updates)))

(defn- parse-resolution
  "Parse resolution from command data. While the intermediate resolution is validated
  against corresponding schema (published or draft), the text field is deflated in the end
  result (and thus mongo-ready)."
  ([{:keys [user created data]} published?]
   (let [{:keys [state text]} data
         user                 (usr/summary user)
         ts-key               (if published? :published :modified)]
     (-> {:state state
          :text  (some-> text squill/trim-operations)
          ts-key created
          :user  user}
         util/strip-nils
         notices-schemas/valid-resolution!
         (util/update-in-when [:text] quill/deflate))))
  ([command] (parse-resolution command false)))

(defn- state-change-updates
  "State and history updates."
  ([user ts state]
   {$set  {:state state}
    $push {:history (cond-> {:state state
                             :ts    ts
                             :user  (usr/summary user)}
                      (usr/batchrun-user? user)
                      (assoc :info-loc :notice.automatic-ready.text))}})
  ([{:keys [user created]} state]
   (state-change-updates user created state)))

(defn- publish-resolution-updates
  [command]
  (let [resolution (parse-resolution command true)]
    (assoc-in (state-change-updates command :ready)
              [$set notices-util/resolution-key] resolution)))

(defn- demolition-target-filled?
  "True if the demolition target IS NOT filled properly. Either the
  building must have been selected or non-building target notes filled. Should not be used
  in a pre-checker since auth model is not updated on every document change."
  [{:keys [application]}]
  (let [{building? :buildingDemolition
         :as       data}   (some-> application notices-util/purkamisilmoitus-doc
                                   :data tools/unwrapped)
        [bid other? note?] (mapv #(ss/blank-as-nil (get data %))
                                 [:buildingId :manuaalinen_rakennusnro :kohde])]
    (boolean (cond
                (and building? (= bid "other")) other?
                building?                       bid
                :else                           note?))))

(defn- demolition-notice-building [demolition-notice-doc]
  (let [operation-name (get-in demolition-notice-doc [:schema :op :name])]
    {:show-building true
     :operation operation-name
     :description (:kuvaus demolition-notice-doc)
     :building-id (:buildingId demolition-notice-doc)
     :tag ""
     :order "0"}))

(defn- notice-resolution->pseudo-verdict [application user lang]
  (let [notice-resolution (get application notices-util/resolution-key)
        doc (notices-util/purkamisilmoitus-doc application)
        operation (get-in doc [:schema :operation])
        operation-id (keyword (:id operation))
        buildings {operation-id (demolition-notice-building doc)}
        resolution-giver-name (usr/full-name user)
        verdict-text (get-in notice-resolution [:text :i])
        verdict-code (case (:state notice-resolution)
                       "rejected" "hylätty"
                       "approved" "hyväksytty")]
    {:category :r
     :schema-version 1
     :created (:published notice-resolution)
     :state {:_value "published" :_user (:username user) :_modified (:published notice-resolution)}
     :modified (:published notice-resolution)
     :id (mongo/create-id)
     :archive {:verdict-date (:published notice-resolution) :verdict-giver resolution-giver-name}
     :data {:address (:address application)
            :selected-attachments []
            :verdict-date (:published notice-resolution)
            :buildings buildings
            :verdict-text verdict-text
            :verdict-code verdict-code
            :giver resolution-giver-name
            :attachments []
            :operation (get-in doc [:data :kuvaus :value])
            :deviations ""
            :language lang
            :neighbor-states []
            :handler resolution-giver-name
            :statements []}}))

(defn publish-notice-as-kuntagml-verdict
  "Creates a pseudo verdict that is used for generating verdict KuntaGML
   message. Assumes that the state of the notice is already updated at
   this point."
  [{:keys [application organization user] :as command} lang]
  (when (org/krysp-write-integration? @organization (:permitType application))
    (let [handlers (:handlers application)
          submitted-application (mongo/by-id :submitted-applications (:id application))
          sent-application (-> (mongo/by-id :applications (:id application))
                               (assoc :handlers handlers)
                               (mapping-to-krysp/post-process-app-for-krysp organization))
          approve-command (assoc command :application sent-application)
          ;; Send a message about the new application to the backing system.
          [_integration-available _sent-file-ids] (bs/approve-application! approve-command submitted-application lang)
          verdict-given-command (assoc command :application sent-application)
          notice-verdict (notice-resolution->pseudo-verdict sent-application user lang)]
      ;; Send verdict message to the backing system.
      (mapping-to-krysp/verdict-as-kuntagml verdict-given-command notice-verdict nil))))

(defn publish-resolution
  "Stores published resolution into mongo. The attachment is generated elsewhere."
  [{:keys [application] :as command} lang]
  (if (demolition-target-filled? command)
    (let [update-result (action/update-application command (publish-resolution-updates command))]
      (when (notices-util/purkamisilmoitus-doc application)
        (publish-notice-as-kuntagml-verdict command lang))
      update-result)
    (fail! :error.notice.demolition-target)))

(defn resolution
  "Current notice resolution for the command application. Supported flags:

  `:refresh` If given, the application is reread from mongo before resolution resolution.

  `:frontend` If given, the resolution user field only contains first and last name."
  [command-or-application & flags]
  (let [application                (util/pcond-> command-or-application
                                     :application :application)
        {:keys [refresh frontend]} (some-> (seq flags) (zipmap (repeat true)))]
    (when-let [m (notices-util/resolution-key (if refresh
                                                (mongo/by-id :applications (:id application) [notices-util/resolution-key])
                                                application))]
      (cond-> m
        true     (update :text quill/inflate)
        ;; No need to share email and id with the frontend
        frontend (update :user select-keys [:firstName :lastName])))))

(def ^:private ^:dynamic *sleep-in-ms* 5000)

(defn poll-resolution [application-id]
  (letfn [(ready? [] (mongo/any? :applications
                                 {:_id                         application-id
                                  (notices-util/resolution-kw-path :job-id) {$exists false}}))]
    (loop [n 10]
      ;; Maximum total polling time is 10 * 5s, which is still under frontend
      ;; timeout (minute).
      (Thread/sleep ^long *sleep-in-ms*)
      (cond
        (ready?) {:resolution (resolution {:id application-id} :refresh :frontend)}
        (zero? n) {:waiting true}
        :else (recur (dec n))))))

(defn save-resolution
  "Saves parsed draft resolution to mongo."
  [{:keys [application] :as command}]
  (let [resolution (parse-resolution command)]
    ;; Do not update application modified timestamp when saving draft resolution
    (mongo/update-by-id :applications (:id application)
                        {$set {notices-util/resolution-key resolution}})))

(defn delete-resolution
  "Deletes the current notice resolution. If the resolution is published, then the
  attachment is deleted too and the application state is rewound to `:submitted`."
  [{:keys [application] :as command}]
  (when-let [{:keys [published attachment-id]} (notices-util/resolution-key application)]
    (mongo/update-by-id :applications (:id application)
                        (cond-> {$unset {notices-util/resolution-key true}}
                          published (util/deep-merge (state-change-updates command :submitted))))
    (when attachment-id
      (att/delete-attachments! application [attachment-id]))))

(defn update-resolution-job-id
  "Adds `job-id` to resolution and returns the updated (frontend) resolution. Throws if the
  resolution is not published, already has `job-id` or `attachment-id`."
  [{:keys [application] :as command} job-id]
  {:pre [(ssc/valid-object-id-str! job-id)]}
  (let [app-id (:id application)]
    (if (zero? (mongo/update-by-query :applications
                                      {:_id                                app-id
                                       (notices-util/resolution-kw-path :published)     {$exists true}
                                       (notices-util/resolution-kw-path :attachment-id) {$exists false}
                                       (notices-util/resolution-kw-path :job-id)        {$exists false}}
                                      {$set {(notices-util/resolution-kw-path :job-id) job-id}}))
      (throw (ex-info (format "Adding job-id %s to %s resolution failed." job-id app-id)
                      {:job-id job-id :application-id app-id}))
      (resolution command :refresh :frontend))))

(defn update-resolution-attachment-id
  "Adds or clears `attachment-id` to resolution if it does not already have one. Throws
  otherwise.  Nil attachment-id is supported and it means that the resolution will not
  have an attachment. Returns updated resolution."
  [{:keys [application] :as command} attachment-id]
  (let [app-id (:id application)
        old    (resolution command :refresh)
        new    (util/strip-nils (-> old
                                    (dissoc :job-id)
                                    (assoc :attachment-id attachment-id)))
        err    (notices-schemas/check-resolution new)
        nope!  #(throw (ex-info (format "Adding attachment-id %s to %s resolution failed: %s"
                                        attachment-id app-id %)
                                {:attachment-id  attachment-id
                                 :application-id app-id}))]
    (cond
      (:attachment-id old) (nope! "Attachment already added.")
      err                  (nope! err)
      attachment-id        (mongo/update-by-id :applications
                                               app-id
                                               {$unset {(notices-util/resolution-kw-path :job-id) true}
                                                $set   {(notices-util/resolution-kw-path :attachment-id) attachment-id}})
      :else                (mongo/update-by-id :applications
                                               app-id
                                               {$unset {(notices-util/resolution-kw-path :job-id) true}}))
    new))
