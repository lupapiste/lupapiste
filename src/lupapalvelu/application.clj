(ns lupapalvelu.application
  (:use [monger.operators]
        [lupapalvelu.log]
        [lupapalvelu.core :only [defquery defcommand ok fail with-application executed now role]]
        [lupapalvelu.action :only [application-query-for get-application-as]])
  (:require [clojure.string :as s]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.env :as env]
            [lupapalvelu.tepa :as tepa]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.security :as security]
            [lupapalvelu.municipality :as municipality]
            [lupapalvelu.util :as util]
            [lupapalvelu.operations :as operations]))

;;
;; Meta-fields:
;;
;; Fetch some fields drom the depths of documents and put them to top level
;; so that yhey are easy to find in UI.

(def meta-fields [{:field :applicant
                   :schema "hakija"
                   :f (fn [doc]
                        (let [data (get-in doc [:body :henkilo :henkilotiedot])]
                          {:firstName (:etunimi data)
                           :lastName (:sukunimi data)}))}])

(defn search-doc [app schema]
  (some (fn [doc] (if (= schema (-> doc :schema :info :name)) doc)) (:documents app)))

(defn with-meta-fields [app]
  (reduce (fn [app {:keys [field schema f]}]
            (if-let [doc (search-doc app schema)]
              (assoc app field (f doc))
              app))
          app
          meta-fields))

;;
;; Query application:
;;

(defquery "applications" {:authenticated true} [{user :user}]
  (ok :applications (map with-meta-fields (mongo/select :applications (application-query-for user)))))

(defn find-authorities-in-applications-municipality [app]
  (mongo/select :users {:municipality (:municipality app) :role "authority"} {:firstName 1 :lastName 1}))

(defquery "application" {:authenticated true, :parameters [:id]} [{{id :id} :data user :user}]
  (if-let [app (get-application-as id user)]
    (ok :application (with-meta-fields app) :authorities (find-authorities-in-applications-municipality app))
    (fail :error.not-found)))

;; Gets an array of application ids and returns a map for each application that contains the
;; application id and the authorities in that municipality.
(defquery "authorities-in-applications-municipality"
  {:parameters [:id]
   :authenticated true}
  [command]
  (let [id (-> command :data :id)
        app (mongo/select-one :applications {:_id id} {:municipality 1})
        authorities (find-authorities-in-applications-municipality app)]
    (ok :authorityInfo authorities)))

(defcommand "assign-application"
  {:parameters  [:id :assigneeId]
   :roles       [:authority]}
  [{{:keys [assigneeId]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (mongo/update-by-id
        :applications (:id application)
        (if assigneeId
          {$set {:roles.authority (security/summary (mongo/select-one :users {:_id assigneeId}))}}
          {$unset {:roles.authority ""}})))))

(defcommand "open-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft]}
  [command]
  (with-application command
    (fn [{id :id}]
      (mongo/update-by-id :applications id
        {$set {:modified (:created command)
               :state :open
               :opened (:created command)}}))))

(defcommand "cancel-application"
  {:parameters [:id]
   :roles      [:applicant]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (mongo/update-by-id :applications (-> command :data :id)
                      {$set {:modified (:created command)
                             :state :canceled}})
  (ok))

(defcommand "approve-application"
  {:parameters [:id]
   :roles      [:authority]
   :authority  true
   :states     [:submitted]}
  [command]
  (with-application command
    (fn [application]
      (if (nil? (-> application :roles :authority))
        (executed "assign-to-me" command))
      (mongo/update
        :applications {:_id (:id application) :state :submitted}
        {$set {:state :sent}}))))

(defcommand "submit-application"
  {:parameters [:id]
   :roles      [:applicant]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        :applications {:_id (:id application)}
          {$set {:state :submitted
                 :submitted (:created command) }}))))

(defcommand "mark-inforequest-answered"
  {:parameters [:id]
   :roles      [:applicant :authority]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        :applications {:_id (:id application)}
          {$set {:state :answered
                 :modified (:created command)}}))))

(defcommand "convert-to-application"
  {:parameters [:id]
   :roles      [:applicant]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [inforequest]
      ; Mark source info-request as answered:
      (mongo/update
        :applications
        {:_id (:id inforequest)}
        {$set {:state :answered
               :modified (:created command)}})
      ; Create application with comments from info-request:
      (let [result (executed
                     (lupapalvelu.core/command
                       "create-application"
                       (:user command)
                       (assoc
                         (util/sub-map inforequest [:x :y :municipality :address])
                         :permitType "buildingPermit")))
            id (:id result)]
        (mongo/update-by-id
          :applications
          id
          {$set {:comments (:comments inforequest)}})
        (ok :id id)))))

(defn- make-attachments [created op]
  (for [[type-group type-ids] (partition 2 (:attachments (operations/operations op)))
        type-id type-ids]
    {:id (mongo/create-id)
     :type {:type-group type-group :type-id type-id}
     :state :requires_user_action
     :modified created
     :versions []}))

(defn- make-documents [user created existing-documents op]
  (let [make (fn [schema-name] {:id (mongo/create-id) :schema (schemas/schemas schema-name) :created created :body {}})
        op-info               (operations/operations op)
        existing-schema-names (set (map (comp :name :info :schema) existing-documents))
        required-schema-names (filter (complement existing-schema-names) (:required op-info))
        required-docs         (map make required-schema-names)
        op-schema-name        (:schema op-info)
        op-doc                (update-in (make op-schema-name) [:schema :info] merge {:op op :removable true})
        new-docs              (cons op-doc required-docs)]
    (if user
      (cons (update-in (make "hakija") [:body :henkilo :henkilotiedot] merge (security/summary user)) new-docs)
      new-docs)))

(defcommand "create-application"
  {:parameters [:operation :permitType :x :y :address :propertyId :municipality]
   :roles      [:applicant]}
  [command]
  (let [{:keys [user created data]} command
        id            (mongo/create-id)
        owner         (role user :owner :type :owner)
        op            (keyword (:operation data))
        info-request? (if (:infoRequest data) true false)]
    (mongo/insert :applications
      {:id            id
       :created       created
       :modified      created
       :infoRequest   info-request?
       :state         (if info-request? :open :draft)
       :municipality  (:municipality data)
       :location      {:x (:x data) :y (:y data)}
       :address       (:address data)
       :propertyId    (:propertyId data)
       :title         (:address data)
       :roles         {:applicant owner}
       :auth          [owner]
       :documents     (make-documents user created nil op)
       :attachments   (make-attachments created op)
       :allowedAttachmentTypes (if info-request?
                                 [[:muut [:muu]]]
                                 (partition 2 attachment/attachment-types))
       :comments      (if-let [message (:message data)]
                        [{:text message
                          :target  {:type "application"}
                          :created created
                          :user    (security/summary user)}]
                        [])
       :permitType     (keyword (:permitType data))})
    (ok :id id)))

(defcommand "add-operation"
  {:parameters [:id :operation]
   :roles      [:applicant :authority]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (let [id         (get-in command [:data :id])
            created    (:created command)
            documents  (:documents application)
            op         (keyword (get-in command [:data :operation]))
            new-docs   (make-documents nil created documents op)]
        (mongo/update-by-id :applications id {$pushAll {:documents new-docs}})))))

;;
;; krysp enrichment
;;

(defquery "merge-details-from-krysp"
  {:parameters [:id]
   :roles-in   [:applicant :authority]}
  [{{:keys [id]} :data :as command}]
  (with-application command
    (fn [{:keys [municipality] :as application}]
      (if-let [legacy (municipality/get-legacy municipality)]
        (let [doc-name     "huoneisto"
              document     (domain/get-document-by-name application doc-name)
              old-body     (:body document)
              kryspxml     (krysp/building-info legacy "24500301050006")
              new-body     (krysp/->building kryspxml)
              merged       (merge old-body new-body)]
          (mongo/update
            :applications
            {:_id (:id application)
             :documents {$elemMatch {:schema.info.name doc-name}}}
            {$set {:documents.$.body merged
                   :modified (:created command)}})
          (ok :old old-body :new new-body :merged merged))
        (fail :no_legacy_available)))))

(defquery "get-building-info-from-legacy"
  {:parameters [:propertyId]
   ;;:authenticated true
   }
  [{{:keys [propertyId]} :data}]
  (let [municipality  (municipality/municipality-by-propertyId propertyId)]
    (if-let [legacy   (municipality/get-legacy municipality)]
      (let [kryspxml  (krysp/building-xml legacy propertyId)
            buildings (krysp/get-buildings kryspxml)]
        (ok :data buildings))
      (fail :no_legacy_available))))
