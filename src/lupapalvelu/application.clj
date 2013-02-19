(ns lupapalvelu.application
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.core :only [defquery defcommand ok fail with-application executed now role]]
        [clojure.string :only [blank?]])
  (:require [clojure.string :as s]
            [lupapalvelu.mongo :as mongo]
            [monger.query :as query]
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
            [lupapalvelu.operations :as operations]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]))

;;
;;
;; Common helpers:
;;

(defn get-applicant-name [app]
  (if (:infoRequest app)
    (let [{first-name :firstName last-name :lastName} (first (domain/get-auths-by-role app :owner))]
      (str first-name \space last-name))
    (when-let [body (:body (domain/get-document-by-name app "hakija"))]
      (if (= (:_selected body) "yritys")
        (get-in body [:yritys :yritysnimi])
        (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
          (str first-name \space last-name))))))

;; Meta-fields:
;;
;; Fetch some fields drom the depths of documents and put them to top level
;; so that yhey are easy to find in UI.

(def meta-fields [{:field :applicant :fn get-applicant-name}])

(defn with-meta-fields [app]
  (reduce (fn [app {field :field f :fn}] (assoc app field (f app))) app meta-fields))

;;
;; Query application:
;;

(defquery "applications" {:authenticated true} [{user :user}]
  (ok :applications (map with-meta-fields (mongo/select :applications (domain/application-query-for user)))))

(defn find-authorities-in-applications-municipality [app]
  (mongo/select :users {:municipality (:municipality app) :role "authority"} {:firstName 1 :lastName 1}))

(defquery "application" {:authenticated true, :parameters [:id]} [{{id :id} :data user :user}]
  (if-let [app (domain/get-application-as id user)]
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
          {$set {:authority (security/summary (mongo/select-one :users {:_id assigneeId}))}}
          {$unset {:authority ""}})))))

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
   :states     [:draft :open :submitted]}
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
      (rl-mapping/get-application-as-krysp application)
      (mongo/update
        :applications {:_id (:id application) :state :submitted}
        {$set {:state :sent}}))))

(defcommand "submit-application"
  {:parameters [:id]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        :applications {:_id (:id application)}
          {$set {:state :submitted
                 :submitted (:created command) }}))))

(defcommand "save-application-shape"
  {:parameters [:id :shape]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [command]
  (let [shape (:shape (:data command))]
  (with-application command
    (fn [application]
      (mongo/update
        :applications {:_id (:id application)}
          {$set {:shapes [shape]}})))))

(defcommand "mark-inforequest-answered"
  {:parameters [:id]
   :roles      [:authority]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (mongo/update
        :applications {:_id (:id application)}
          {$set {:state :answered
                 :modified (:created command)}}))))

(defn- make-attachments [created op]
  (for [[type-group type-ids] (partition 2 (:attachments (operations/operations op)))
        type-id type-ids]
    {:id (mongo/create-id)
     :type {:type-group type-group :type-id type-id}
     :state :requires_user_action
     :modified created
     :versions []}))

(defn- schema-data-to-body [schema-data]
  (reduce (fn [body [data-path value]] (update-in body data-path (constantly value))) {} schema-data))

(defn- make-documents [user created existing-documents op]
  (let [op-info               (operations/operations op)
        make                  (fn [schema-name] {:id (mongo/create-id) :schema (schemas/schemas schema-name) :created created
                                                 :body (schema-data-to-body (:schema-data op-info))})
        existing-schema-names (set (map (comp :name :info :schema) existing-documents))
        required-schema-names (remove existing-schema-names (:required op-info))
        required-docs         (map make required-schema-names)
        op-schema-name        (:schema op-info)
        op-doc                (update-in (make op-schema-name) [:schema :info] merge {:op op :removable true})
        new-docs              (cons op-doc required-docs)
        hakija                (make "hakija")]
    (if user
      (cons #_hakija (assoc-in hakija [:body :henkilo] (domain/user2henkilo user)) new-docs)
      new-docs)))

(defn- ->double [v]
  (let [v (str v)]
    (if (s/blank? v) 0.0 (Double/parseDouble v))))

(defcommand "create-application"
  {:parameters [:operation :permitType :x :y :address :propertyId :municipality]
   :roles      [:applicant :authority]}
  [command]
  (let [{:keys [user created data]} command
        user-role     (keyword (:role user))]
    (if (or (= :applicant user-role)
            (and (:municipality user) (= (:municipality data) (:municipality user))))
      (let [
            user-summary  (security/summary user)
            id            (mongo/create-id)
            owner         (role user :owner :type :owner)
            op            (keyword (:operation data))
            info-request? (if (:infoRequest data) true false)
            make-comment  (partial assoc {:target {:type "application"} :created created :user user-summary} :text)]
        (mongo/insert :applications
                      {:id            id
                       :created       created
                       :opened        (when (= :authority user-role) created)
                       :modified      created
                       :infoRequest   info-request?
                       :state         (if (or info-request? (= :authority user-role)) :open :draft)
                       :municipality  (:municipality data)
                       :location      {:x (->double (:x data)) :y (->double (:y data))}
                       :address       (:address data)
                       :propertyId    (:propertyId data)
                       :title         (:address data)
                       :auth          [owner]
                       :operations    [{:operation op :created created}]
                       :documents     (if info-request? [] (make-documents user created nil op))
                       :attachments   (if info-request? [] (make-attachments created op))
                       :allowedAttachmentTypes (if info-request?
                                                 [[:muut [:muu]]]
                                                 (partition 2 attachment/attachment-types))
                       :comments      (map make-comment (:messages data))
                       :permitType    (keyword (:permitType data))})
        (ok :id id))
      (fail :error.unauthorized))))

(defcommand "add-operation"
  {:parameters [:id :operation]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (let [id         (get-in command [:data :id])
            created    (:created command)
            documents  (:documents application)
            op         (keyword (get-in command [:data :operation]))
            new-docs   (make-documents nil created documents op)]
        (mongo/update-by-id :applications id {$push {:operations {:operation op :created created}}
                                              $pushAll {:documents new-docs}
                                              $set {:modified created}})
        (ok)))))

(defcommand "convert-to-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [inforequest]
      (let [id       (get-in command [:data :id])
            created  (:created command)
            op       (-> inforequest :operations first :operation keyword)]
        (mongo/update-by-id :applications id {$set {:infoRequest false
                                                    :state :open
                                                    :allowedAttachmentTypes (partition 2 attachment/attachment-types)
                                                    :documents (make-documents (-> command :user security/summary) created nil op)
                                                    :modified created}
                                              $pushAll {:attachments (make-attachments created op)}})
        (ok)))))

(defquery "get-users-in-application"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [query]
  (with-application query
    (fn [{:keys [auth]}]
      (ok :users auth))))

;;
;; krysp enrichment
;;

(defcommand "merge-details-from-krysp"
  {:parameters [:id :buildingId]
   :roles      [:applicant :authority]}
  [{{:keys [id buildingId]} :data :as command}]
  (with-application command
    (fn [{:keys [municipality propertyId] :as application}]
      (if-let [legacy (municipality/get-legacy municipality)]
        (let [doc-name     "rakennuksen-muuttaminen"
              document     (domain/get-document-by-name application doc-name)
              old-body     (:body document)
              kryspxml     (krysp/building-xml legacy propertyId)
              new-body     (or (krysp/->rakennuksen-muuttaminen kryspxml buildingId) {})]
          (mongo/update
            :applications
            {:_id (:id application)
             :documents {$elemMatch {:schema.info.name doc-name}}}
            {$set {:documents.$.body new-body
                   :modified (:created command)}})
          (ok))
        (fail :no-legacy-available)))))

(defcommand "get-building-info-from-legacy"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [{{:keys [id]} :data :as command}]
  (with-application command
    (fn [{:keys [municipality propertyId] :as application}]
      (if-let [legacy   (municipality/get-legacy municipality)]
        (let [kryspxml  (krysp/building-xml legacy propertyId)
              buildings (krysp/->buildings kryspxml)]
          (ok :data buildings))
        (fail :no-legacy-available)))))

;;
;; Service point for jQuery dataTables:
;;

(def col-sources [(fn [app] (if (:infoRequest app) "inforequest" "application"))
                  :address
                  :title
                  get-applicant-name
                  :submitted
                  :modified
                  :state
                  :authority])

(def col-map (zipmap col-sources (map str (range))))

(defn add-field [application data [app-field data-field]]
  (assoc data data-field (if (keyword? app-field) (get application app-field) (app-field application))))

(defn make-row [application]
  (let [base {"id" (:_id application)
              "kind" (if (:infoRequest application) "inforequest" "application")}]
    (reduce (partial add-field application) base col-map)))

(defn make-query [query params]
  (let [search (params :sSearch)
        kind (params :kind)]
    (merge
      query
      (condp = kind
        "applications" {:infoRequest false}
        "inforequests" {:infoRequest true}
        nil)
      (when-not (blank? search)
        {:title {$regex search $options "i"}}))))

(defn applications-for-user [user params]
  (let [user-query  (domain/application-query-for user)
        user-total  (mongo/count :applications user-query)
        query       (make-query user-query params)
        query-total (mongo/count :applications query)
        skip        (params :iDisplayStart)
        limit       (params :iDisplayLength)
        apps        (query/with-collection "applications"
                      (query/find query)
                      (query/skip skip)
                      (query/limit limit))
        rows        (map (comp make-row with-meta-fields) apps)
        echo        (str (Integer/parseInt (str (params :sEcho))))] ; Prevent XSS

    {:aaData                rows
     :iTotalRecords         user-total
     :iTotalDisplayRecords  query-total
     :sEcho                 echo}))

(defcommand "applications-for-datatables"
  {:parameters [:params]}
  [{user :user {params :params} :data}]
  (ok :data (applications-for-user user params)))


