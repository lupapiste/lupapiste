(ns lupapalvelu.document.document
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized! now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :refer [update-application] :as action]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.wfs :as wfs]
            [clj-time.format :as tf]))

;;
;; Validators
;;

(defn state-history-entries [history]
  (filter :state history)) ; only history elements that regard state change

(defn state-valid-by-schema? [schema schema-states-key default-states state]
  (-> (get-in schema [:info (keyword schema-states-key)])
      (or default-states)
      (contains? (keyword state))))

(defn created-after-verdict? [document application]
  (if (contains? states/post-verdict-states (keyword (:state application)))
    (let [verdict-state        (sm/verdict-given-state application)
          verdict-history-item (->> (state-history-entries (:history application))
                                    (filter #(= (:state %) (name verdict-state)))
                                    (sort-by :ts)
                                    last)]
      (when-not verdict-history-item
        (error "Application in post-verdict, but doesnt have verdictGiven state in history"))
      (> (:created document) (:ts verdict-history-item)))
    false))

(defn approved? [document]
  (= "approved" (get-in document [:meta :_approved :value])))

(defn user-can-be-set? [user-id application]
  (and (auth/has-auth? application user-id) (domain/no-pending-invites? application user-id)))

(defn create-doc-validator [{{documents :documents permit-type :permitType} :application}]
  ;; Hide the "Lisaa osapuoli" button when application contains "party" type documents and more can not be added.
  (when (and
          (not (permit/multiple-parties-allowed? permit-type))
          (some (comp (partial = "party") :type :schema-info) documents))
    (fail :error.create-doc-not-allowed)))

(defn user-can-be-set-validator [{{user-id :userId} :data application :application}]
  (when-not (or (ss/blank? user-id) (user-can-be-set? user-id application)
                (auth/has-auth-via-company? application user-id))
    (fail :error.application-does-not-have-given-auth)))

(defn- deny-remove-of-non-removable-doc [{{:keys [removable-by]} :schema-info} application user]
  (let [user-app-role (auth/application-role application user)]
    (not (#{:all user-app-role} removable-by))))

(defn- deny-remove-of-primary-operation [document application]
  (= (get-in document [:schema-info :op :id]) (get-in application [:primaryOperation :id])))

(defn- deny-remove-of-last-document [{{:keys [last-removable-by schema-name]} :schema-info} {documents :documents :as app} user]
  (let [user-app-role (auth/application-role application user)
        doc-count     (count (domain/get-documents-by-name documents schema-name))]
    (and last-removable-by (not (#{:all user-app-role}) last-removable-by) (<= doc-count 1))))

(defn- deny-remove-for-non-authority-user [user {schema-info :schema-info}]
  (and (not (usr/authority? user))
       schema-info
       (get-in (schemas/get-schema schema-info) [:info :removable-only-by-authority])))

(defn- deny-remove-of-non-post-verdict-document [document {state :state :as application}]
  (and (contains? states/post-verdict-states (keyword state)) (not (created-after-verdict? document application))))

(defn- deny-remove-of-approved-post-verdict-document [document application]
  (and (created-after-verdict? document application) (approved? document)))

(defn remove-doc-validator [{data :data user :user application :application}]
  (if-let [document (when application (domain/get-document-by-id application (:docId data)))]
    (cond
      (deny-remove-of-non-removable-doc document)             (fail :error.not-allowed-to-remove-document)
      (deny-remove-for-non-authority-user user document)      (fail :error.action-allowed-only-for-authority)
      (deny-remove-of-last-document document application user) (fail :error.removal-of-last-document-denied)
      (deny-remove-of-primary-operation document application) (fail :error.removal-of-primary-document-denied)
      (deny-remove-of-non-post-verdict-document document application) (fail :error.document.post-verdict-deletion)
      (deny-remove-of-approved-post-verdict-document document application) (fail :error.document.post-verdict-deletion))))


(defn validate-post-verdict-not-approved
  "In post verdict states, validates that given document is approved.
   Approval 'locks' documents in post-verdict state."
  [key]
  (fn [{:keys [application data]}]
    (when-let [document (when (and application (contains? states/post-verdict-states (keyword (:state application))))
                          (domain/get-document-by-id application (get data key)))]
      (when (approved? document)
        (fail :error.document.approved)))))

(defn validate-created-after-verdict
  "In post-verdict state, validates that document is post-verdict-party and it's not created-after-verdict.
   This is special case for post-verdict-parties. Also waste schemas can be edited in post-verdict states, though
   they have been created before verdict. Thus we are only interested in 'post-verdict-party' documents here."
  [key]
  (fn [{:keys [application data]}]
    (when-let [document (when (and application (contains? states/post-verdict-states (keyword (:state application))))
                          (domain/get-document-by-id application (get data key)))]
      (when (and (get-in document [:schema-info :post-verdict-party]) (not (created-after-verdict? document application)))
        (fail :error.document.pre-verdict-document)))))

(defn doc-disabled-validator
  "Deny action if document is marked as disabled"
  [key]
  (fn [{:keys [application data]}]
    (when-let [doc (and (get data key) (domain/get-document-by-id application (get data key)))]
      (when (:disabled doc)
        (fail :error.document.disabled)))))

(defn validate-disableable-schema
  "Checks if document can be disabled from document's schema"
  [key]
  (fn [{:keys [application data]}]
    (when-let [doc (and (get data key) (domain/get-document-by-id application (get data key)))]
      (when-not (get-in doc [:schema-info :disableable])
        (fail :error.document.not-disableable)))))

(defn validate-document-is-pre-verdict-or-approved
  "Pre-check for document disabling. If document is added after verdict, it needs to be approved."
  [{:keys [application data]}]
  (when-let [document (when application (domain/get-document-by-id application (:docId data)))]
    (when-not (or (not (created-after-verdict? document application)) (approved? document))
      (fail :error.document-not-approved))))


;;
;; KTJ-info updation
;;

(def ktj-format (tf/formatter "yyyyMMdd"))
(def output-format (tf/formatter "dd.MM.yyyy"))

(defn fetch-and-persist-ktj-tiedot [application document property-id time]
  (when-let [ktj-tiedot (wfs/rekisteritiedot-xml property-id)]
    (let [doc-updates [[[:kiinteisto :tilanNimi] (or (:nimi ktj-tiedot) "")]
                       [[:kiinteisto :maapintaala] (or (:maapintaala ktj-tiedot) "")]
                       [[:kiinteisto :vesipintaala] (or (:vesipintaala ktj-tiedot) "")]
                       [[:kiinteisto :rekisterointipvm] (or
                                                          (try
                                                            (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                            (catch Exception e (:rekisterointipvm ktj-tiedot)))
                                                          "")]]
          schema (schemas/get-schema (:schema-info document))
          updates (filter (partial doc-persistence/update-key-in-schema? (:body schema)) doc-updates)]
      (doc-persistence/persist-model-updates application "documents" document updates time))))


;;
;; Document approvals
;;

(defn- validate-approvability [{{:keys [doc path collection]} :data application :application}]
  (let [path-v (if (ss/blank? path) [] (ss/split path #"\."))
        document (doc-persistence/by-id application collection doc)]
    (if document
      (when-not (model/approvable? document path-v)
        (fail :error.document-not-approvable))
      (fail :error.document-not-found))))

(defn- ->approval-mongo-model
  "Creates a mongo update map of approval data.
   To be used within model/with-timestamp. Does not overwrite the rejection note."
  [path approval]
  (let [mongo-path (if (ss/blank? path) "documents.$.meta._approved" (str "documents.$.meta." path "._approved"))
        approval-pairs (map (fn [[k v]]
                              [(format "%s.%s" mongo-path (name k)) v])
                            approval)]
    {$set (into {:modified (model/current-timestamp)} approval-pairs)}))

(defn- update-approval [{{:keys [id doc path collection]} :data user :user created :created :as command} approval-data]
  (or
   (validate-approvability command)
   (model/with-timestamp created
     (update-application
      command
      {collection {$elemMatch {:id doc}}}
      (->approval-mongo-model path approval-data))
     approval-data)))

(defn approve [{:keys [user created] :as command} status]
  (update-approval command (model/with-timestamp created (model/->approved status user))))

(defn set-rejection-note [command note]
  (update-approval command {:note note}))


;;
;; Assignments
;;

(defn- document-assignment-info
  "Return document info as assignment target"
  [operations {{name :name doc-op :op} :schema-info id :id :as doc}]
  (let [accordion-datas (schemas/resolve-accordion-field-values doc)
        op-description  (:description (util/find-by-id (:id doc-op) operations))]
    (util/assoc-when-pred {:id id :type-key (ss/join "." [name "_group_label"])} ss/not-blank?
                          :description (or op-description (ss/join " " accordion-datas)))))

(defn- describe-parties-assignment-targets [application]
  (->> (domain/get-documents-by-type application :party)
       (remove :disabled)
       (sort-by tools/document-ordering-fn)
       (map (partial document-assignment-info nil))))

(defn- describe-non-party-document-assignment-targets [{:keys [documents primaryOperation secondaryOperations] :as application}]
  (let [party-doc-ids (set (map :id (domain/get-documents-by-type application :party)))
        operations (cons primaryOperation secondaryOperations)]
    (->> (remove (comp party-doc-ids :id) documents)
         (remove :disabled)
         (sort-by tools/document-ordering-fn)
         (map (partial document-assignment-info operations)))))

(assignment/register-assignment-target! :parties describe-parties-assignment-targets)

(assignment/register-assignment-target! :documents describe-non-party-document-assignment-targets)

(defn check-removable-against-schema [{{removable :removable :as schema} :schema-info doc-id :id}]
  (let [schema-removable (-> (schemas/get-schema schema) :info :removable)]
    (when-not (or (nil? removable) (= removable schema-removable)) {:id doc-id :name (:name schema) :removable-s schema-removable :removable-d removable})))

(defn check-application-removables [{documents :documents id :id}]
  (some->> (map check-removable-against-schema documents)
           (remove nil?)
           not-empty
           (hash-map :app-id id :docs)))

(->> (lupapalvelu.mongo/with-db "lupapiste-smoke"
       (lupapalvelu.mongo/select :applications {:_id #"LP-...-2017"} [:documents]))
     (map check-application-removables)
     (remove nil?))

(mapcat :docs
        '({:app-id "LP-245-2015-00292", :docs ("5625eab928e06f7b1bb97e35")} {:app-id "LP-491-2015-00506", :docs ("556313e7e4b06ad18eb29784")} {:app-id "LP-505-2015-00001", :docs ("55cb2063e4b0115300055a5d")} {:app-id "LP-543-2015-00032", :docs ("55c1959be4b0c0d656ae9db7")} {:app-id "LP-543-2015-00020", :docs ("5587fafde4b0d2a425eada83")} {:app-id "LP-543-2015-00043", :docs ("55d5be02e4b025c8f3ed1b26")} {:app-id "LP-543-2015-00044", :docs ("55d6c317e4b025c8f3ed2c1c")} {:app-id "LP-753-2015-00094", :docs ("553a2c38e4b0d614ebff3c1a")} {:app-id "LP-753-2015-00233", :docs ("55ed2280e4b073172ddedb28")} {:app-id "LP-753-2015-00247", :docs ("560138a028e06f195b435c88")} {:app-id "LP-753-2015-00261", :docs ("560cf19b28e06f0cda003ebb")} {:app-id "LP-753-2015-00288", :docs ("5624d5c728e06f7b1bb9614c")} {:app-id "LP-753-2015-00347", :docs ("567a7a5b28e06f129b7db0a8")} {:app-id "LP-858-2015-00199", :docs ("5579282ae4b0995d5089ae12")}))

(lupapalvelu.mongo/with-db "lupapiste-smoke" (lupapalvelu.mongo/select :applications
                                                                       {:documents.id {$in (mapcat :docs)}}))
'({:app-id "LP-245-2015-00292", :docs ("5625eab928e06f7b1bb97e35")} {:app-id "LP-491-2015-00506", :docs ("556313e7e4b06ad18eb29784")} {:app-id "LP-505-2015-00001", :docs ("55cb2063e4b0115300055a5d")} {:app-id "LP-543-2015-00032", :docs ("55c1959be4b0c0d656ae9db7")} {:app-id "LP-543-2015-00020", :docs ("5587fafde4b0d2a425eada83")} {:app-id "LP-543-2015-00043", :docs ("55d5be02e4b025c8f3ed1b26")} {:app-id "LP-543-2015-00044", :docs ("55d6c317e4b025c8f3ed2c1c")} {:app-id "LP-753-2015-00094", :docs ("553a2c38e4b0d614ebff3c1a")} {:app-id "LP-753-2015-00233", :docs ("55ed2280e4b073172ddedb28")} {:app-id "LP-753-2015-00247", :docs ("560138a028e06f195b435c88")} {:app-id "LP-753-2015-00261", :docs ("560cf19b28e06f0cda003ebb")} {:app-id "LP-753-2015-00288", :docs ("5624d5c728e06f7b1bb9614c")} {:app-id "LP-753-2015-00347", :docs ("567a7a5b28e06f129b7db0a8")} {:app-id "LP-858-2015-00199", :docs ("5579282ae4b0995d5089ae12")})
{:documents.schema-info.name 1}

(lupapalvelu.mongo/with-db "lupapiste-smoke" (lupapalvelu.mongo/select :applications
                                                                       {:documents.id {$in (->>
                                                                                            '({:app-id "LP-245-2017-00083", :docs ({:id "5717354828e06f48bd7fc9c9", :name "hakija-ya"})} {:app-id "LP-245-2017-00135", :docs ({:id "56caa3e528e06f08ea69f0ac", :name "hakija-ya"})} {:app-id "LP-245-2017-00136", :docs ({:id "56caa3e528e06f08ea69f0ac", :name "hakija-ya"})} {:app-id "LP-186-2017-00184", :docs ({:id "5644aed928e06f20cc96b087", :name "hakija-ya"})} {:app-id "LP-245-2017-00224", :docs ({:id "56d53d14edf02d49bc202703", :name "hakija-ya"})} {:app-id "LP-186-2017-00282", :docs ({:id "56c6b74128e06f4eb92d2892", :name "hakija-ya"})} {:app-id "LP-092-2017-01817", :docs ({:id "5702294eedf02d36eb7060ac", :name "hakija-ya"})} {:app-id "LP-245-2017-00412", :docs ({:id "5704b93a28e06f1d2b263805", :name "hakija-ya"})} {:app-id "LP-245-2017-00430", :docs ({:id "56ab50a828e06f2f33634213", :name "hakija-ya"})} {:app-id "LP-092-2017-02083", :docs ({:id "571cc31aedf02d57fe886407", :name "hakija-ya"})} {:app-id "LP-092-2017-02141", :docs ({:id "570cb51828e06f0ec716f701", :name "hakija-ya"})} {:app-id "LP-734-2017-00736", :docs ({:id "56f8c6b228e06f73aba53835", :name "hakija-ya"})} {:app-id "LP-092-2017-03876", :docs ({:id "56f22cf028e06f73aba4be2c", :name "hakija-ya"})})
                                                                                            (mapcat :docs) (map :id))}}
                                                                       {:primaryOperation.name 1}))

(:removable (:info (schemas/get-schema {:name "maksaja" :version 1})))
