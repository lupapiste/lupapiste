(ns lupapalvelu.application
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.core]
        [clojure.string :only [blank?]]
        [clj-time.core :only [year]]
        [clj-time.local :only [local-now]]
        [lupapalvelu.i18n :only [with-lang loc]])
  (:require [clojure.string :as s]
            [clj-time.format :as timeformat]
            [lupapalvelu.mongo :as mongo]
            [monger.query :as query]
            [sade.env :as env]
            [lupapalvelu.tepa :as tepa]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.xml.krysp.reader :as krysp]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.security :as security]
            [lupapalvelu.municipality :as municipality]
            [sade.util :as util]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]))

;;
;; Common helpers:
;;

(defn get-applicant-name [app]
  (if (:infoRequest app)
    (let [{first-name :firstName last-name :lastName} (first (domain/get-auths-by-role app :owner))]
      (str first-name \space last-name))
    (when-let [body (:data (domain/get-document-by-name app "hakija"))]
      (if (= (get-in body [:_selected :value]) "yritys")
        (get-in body [:yritys :yritysnimi :value])
        (let [{first-name :etunimi last-name :sukunimi} (get-in body [:henkilo :henkilotiedot])]
          (str (:value first-name) \space (:value last-name)))))))

(defn get-application-operation [app]
  (first (:operations app)))

(defn update-application
  "get current application from command (or fail) and run changes into it."
  [command changes]
  (with-application command
    (fn [{:keys [id]}]
      (mongo/update
        :applications
        {:_id id}
        changes))))

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

(defquery "applications" {:authenticated true :verified true} [{user :user}]
  (ok :applications (map with-meta-fields (mongo/select :applications (domain/application-query-for user)))))

(defn find-authorities-in-applications-municipality [app]
  (mongo/select :users {:municipality (:municipality app) :role "authority"} {:firstName 1 :lastName 1}))

(defquery "application"
  {:authenticated true
   :parameters [:id]}
  [{{id :id} :data user :user}]
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

(defn filter-repeating-party-docs [names]
  (filter (fn [name] (and (= :party (get-in schemas/schemas [name :info :type])) (= true (get-in schemas/schemas [name :info :repeating])))) names))

(defquery "party-document-names"
  {:parameters [:id]
   :authenticated true}
  [command]
  (with-application command
    (fn [application]
      (let [documents (:documents application)
            initialOp (:name (first (:operations application)))
            original-schema-names (:required ((keyword initialOp) operations/operations))
            original-party-documents (filter-repeating-party-docs original-schema-names)]
        (ok :partyDocumentNames (conj original-party-documents "hakija"))))))

;;
;; Invites
;;

(defquery "invites"
  {:authenticated true
   :verified true}
  [{{:keys [id]} :user}]
  (let [filter     {:auth {$elemMatch {:invite.user.id id}}}
        projection (assoc filter :_id 0)
        data       (mongo/select :applications filter projection)
        invites    (map :invite (mapcat :auth data))]
    (ok :invites invites)))

(defcommand "invite"
  {:parameters [:id :email :title :text :documentName]
   :roles      [:applicant]
   :verified   true}
  [{created :created
    user    :user
    {:keys [id email title text documentName documentId]} :data {:keys [host]} :web :as command}]
  (with-application command
    (fn [{application-id :id :as application}]
      (if (domain/invited? application email)
        (fail :already-invited)
        (let [invited (security/get-or-create-user-by-email email)
              invite  {:title        title
                       :application  application-id
                       :text         text
                       :documentName documentName
                       :documentId   documentId
                       :created      created
                       :email        email
                       :user         (security/summary invited)
                       :inviter      (security/summary user)}
              writer  (role invited :writer)
              auth    (assoc writer :invite invite)]
          (if (domain/has-auth? application (:id invited))
            (fail :already-has-auth)
            (do
              (mongo/update
                :applications
                {:_id application-id
                 :auth {$not {$elemMatch {:invite.user.username email}}}}
                {$push {:auth auth}})
              (notifications/send-invite! email text application user host))))))))

(defcommand "approve-invite"
  {:parameters [:id]
   :roles      [:applicant]
   :verified   true}
  [{user :user :as command}]
  (with-application command
    (fn [{application-id :id :as application}]
      (when-let [my-invite (domain/invite application (:email user))]
        (executed "set-user-to-document"
          (-> command
            (assoc-in [:data :documentId] (:documentId my-invite))
            (assoc-in [:data :userId]     (:id user))))
        (mongo/update :applications
          {:_id application-id :auth {$elemMatch {:invite.user.id (:id user)}}}
          {$set  {:auth.$ (role user :writer)}})))))

(defcommand "remove-invite"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [id email]} :data :as command}]
  (with-application command
    (fn [{application-id :id}]
      (with-user email
        (fn [_]
          (mongo/update-by-id :applications application-id
            {$pull {:auth {$and [{:username email}
                                 {:type {$ne :owner}}]}}}))))))

;; TODO: we need a) custom validator to tell weathet this is ok and/or b) return effected rows (0 if owner)
(defcommand "remove-auth"
  {:parameters [:id :email]
   :roles      [:applicant]}
  [{{:keys [email]} :data :as command}]
  (update-application command
    {$pull {:auth {$and [{:username email}
                         {:type {$ne :owner}}]}}}))

(defcommand "add-comment"
  {:parameters [:id :text :target]
   :roles      [:applicant :authority]}
  [{{:keys [text target]} :data {:keys [host]} :web :keys [user created] :as command}]
  (with-application command
    (fn [{:keys [id state] :as application}]
      (update-application command
        {$set  {:modified created}
         $push {:comments {:text    text
                           :target  target
                           :created created
                           :user    (security/summary user)}}})

      (condp = (keyword state)

        ;; LUPA-XYZ (was: open-application)
        :draft  (when (not (s/blank? text))
                  (update-application command
                    {$set {:modified created
                           :state    :open
                           :opened   created}}))

        ;; LUPA-371
        :info (when (security/authority? user)
                (update-application command
                  {$set {:state    :answered
                         :modified created}}))

        ;; LUPA-371 (was: mark-inforequest-answered)
        :answered (when (security/applicant? user)
                    (update-application command
                      {$set {:state :info
                             :modified created}}))

        nil)

      ;; TODO: details should come from updated state!
      (notifications/send-notifications-on-new-comment! application user text host))))

(defcommand "set-user-to-document"
  {:parameters [:id :documentId :userId :path]
   :authenticated true}
  [{{:keys [documentId userId path]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (let [document     (domain/get-document-by-id application documentId)
            schema-name  (get-in document [:schema :info :name])
            schema       (get schemas/schemas schema-name)
            subject      (security/get-non-private-userinfo userId)
            henkilo      (domain/user2henkilo subject)
            full-path    (str "documents.$.data" (when-not (s/blank? path) (str "." path)))]
        (if (nil? document)
          (fail :error.document-not-found)
          (do
            (infof "merging user %s with best effort into document %s into path %s" subject name full-path)
            (mongo/update
              :applications
              {:_id (:id application)
               :documents {$elemMatch {:id documentId}}}
              {$set {full-path henkilo
                     :modified (:created command)}})))))))


;;
;; Assign
;;

(defcommand "assign-to-me"
  {:parameters [:id]
   :roles      [:authority]}
  [{user :user :as command}]
  (update-application command
    {$set {:authority (security/summary user)}}))

(defcommand "assign-application"
  {:parameters  [:id :assigneeId]
   :roles       [:authority]}
  [{{:keys [assigneeId]} :data user :user :as command}]
  (update-application command
    (if assigneeId
      {$set   {:authority (security/summary (mongo/select-one :users {:_id assigneeId}))}}
      {$unset {:authority ""}})))

;;
;;
;;

(defcommand "cancel-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft :info :open :submitted]}
  [{{id :id} :data {:keys [host]} :web created :created :as command}]
  (update-application command
    {$set {:modified  created
           :state     :canceled}})
  (notifications/send-notifications-on-application-state-change! id host))

(defcommand "request-for-complement"
  {:parameters [:id]
   :roles      [:authority]
   :states     [:sent]}
  [{{id :id} :data {host :host} :web created :created :as command}]
  (update-application command
    {$set {:modified  created
           :state :complement-needed}})
  (notifications/send-notifications-on-application-state-change! id host))

(defcommand "approve-application"
  {:parameters [:id :lang]
   :roles      [:authority]
   :states     [:submitted]}
  [{{:keys [host]} :web :as command}]
  (with-application command
    (fn [application]
      (let [new-state :submitted
            application-id (:id application)
            submitted-application (mongo/by-id :submitted-applications (:id application))
            municipality (mongo/by-id :municipalities (:municipality application))]
        (if (nil? (:authority application))
          (executed "assign-to-me" command))
        (try (rl-mapping/get-application-as-krysp application (-> command :data :lang) submitted-application municipality)
          (mongo/update
            :applications {:_id (:id application) :state new-state}
            {$set {:state :sent}})
          (notifications/send-notifications-on-application-state-change! application-id host)
          (catch org.xml.sax.SAXParseException e
            (.printStackTrace e)
            (fail (.getMessage e))))))))

(defcommand "submit-application"
  {:parameters [:id]
   :roles      [:applicant :authority]
   :states     [:draft :info :open :complement-needed]
   :validators [(fn [command application]
                  (when-not (domain/is-owner-or-writer? application (-> command :user :id))
                    (fail :error.unauthorized)))]}
  [{{:keys [host]} :web :as command}]
  (with-application command
    (fn [application]
      (let [new-state :submitted
            application-id (:id application)]
        (mongo/update
          :applications
          {:_id application-id}
          {$set {:state new-state
                 :submitted (:created command) }})
        (try
          (mongo/insert
            :submitted-applications
            (assoc (dissoc application :id) :_id application-id))
          (catch com.mongodb.MongoException$DuplicateKey e
            ; This is ok. Only the first submit is saved.
            ))
        (notifications/send-notifications-on-application-state-change! application-id host)))))

(defcommand "save-application-shape"
  {:parameters [:id :shape]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{{:keys [shape]} :data :as command}]
  (update-application command
    {$set {:shapes [shape]}}))

(defn- make-attachments [created op municipality-id & {:keys [target]}]
  (let [municipality (mongo/select-one :municipalities {:_id municipality-id} {:operations-attachments 1})]
    (for [[type-group type-id] (get-in municipality [:operations-attachments (keyword (:name op))])]
      {:id (mongo/create-id)
       :type {:type-group type-group :type-id type-id}
       :state :requires_user_action
       :target target
       :modified created
       :versions []
       :op op})))

(defn- schema-data-to-body [schema-data]
  (reduce
    (fn [body [data-path value]]
      (let [path (if (= :value (last data-path)) data-path (conj (vec data-path) :value))]
        (update-in body path (constantly value))))
    {} schema-data))

(defn- make-documents [user created existing-documents op]
  (let [op-info               (operations/operations (keyword (:name op)))
        make                  (fn [schema-name] {:id (mongo/create-id)
                                                 :schema (schemas/schemas schema-name)
                                                 :created created
                                                 :data (if (= schema-name (:schema op-info))
                                                         (schema-data-to-body (:schema-data op-info))
                                                         {})})
        existing-schema-names (set (map (comp :name :info :schema) existing-documents))
        required-schema-names (remove existing-schema-names (:required op-info))
        required-docs         (map make required-schema-names)
        op-schema-name        (:schema op-info)
        op-doc                (update-in (make op-schema-name) [:schema :info] merge {:op op :removable true})
        new-docs              (cons op-doc required-docs)
        hakija                (make "hakija")]
    (if user
      (cons #_hakija (assoc-in hakija [:data :henkilo] (domain/user2henkilo user)) new-docs)
      new-docs)))

(defn- ->double [v]
  (let [v (str v)]
    (if (s/blank? v) 0.0 (Double/parseDouble v))))

(defn- permit-type-from-operation [operation]
  ;; TODO operation to permit type mapping???
  "buildingPermit")

(defn- make-application-id [municipality]
  (let [year           (str (year (local-now)))
        sequence-name  (str "applications-" municipality "-" year)
        counter        (format "%05d" (mongo/get-next-sequence-value sequence-name))]
    (str "LP-" municipality "-" year "-" counter)))

(defn- make-op [op-name created]
  {:id (mongo/create-id)
   :name (keyword op-name)
   :created created})

;; TODO: separate methods for inforequests & applications for clarity.
(defcommand "create-application"
  {:parameters [:operation :x :y :address :propertyId :municipality]
   :roles      [:applicant :authority]
   :verified   true}
  [{{:keys [operation x y address propertyId municipality infoRequest messages]} :data :keys [user created] :as command}]
  (if (or (security/applicant? user) (and (:municipality user) (= municipality (:municipality user))))
    (let [user-summary  (security/summary user)
          id            (make-application-id municipality)
          owner         (role user :owner :type :owner)
          op            (make-op operation created)
          info-request? (if infoRequest true false)
          state         (if info-request? :info (if (security/authority? user) :open :draft))
          make-comment  (partial assoc {:target {:type "application"} :created created :user user-summary} :text)
          application   {:id            id
                         :created       created
                         :opened        (when (#{:open :info} state) created)
                         :modified      created
                         :infoRequest   info-request?
                         :operations    [op]
                         :state         state
                         :municipality  municipality
                         :location      {:x (->double x) :y (->double y)}
                         :address       address
                         :propertyId    propertyId
                         :title         address
                         :auth          [owner]
                         :documents     (if info-request? [] (make-documents user created nil op))
                         :attachments   (if info-request? [] (make-attachments created op municipality))
                         :allowedAttachmentTypes (if info-request?
                                                   [[:muut [:muu]]]
                                                   (partition 2 attachment/attachment-types))
                         :comments      (map make-comment messages)
                         :permitType    (permit-type-from-operation op)}
          app-with-ver  (domain/set-software-version application)]
      (mongo/insert :applications app-with-ver)
      (ok :id id))
    (fail :error.unauthorized)))

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
            op-id      (mongo/create-id)
            op         (make-op (get-in command [:data :operation]) created)
            new-docs   (make-documents nil created documents op)]
        (mongo/update-by-id :applications id {$push {:operations op}
                                              $pushAll {:documents new-docs
                                                        :attachments (make-attachments created op (:municipality application))}
                                              $set {:modified created}})
        (ok)))))

(defcommand "convert-to-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft :info :answered]}
  [command]
  (with-application command
    (fn [inforequest]
      (let [id       (get-in command [:data :id])
            created  (:created command)
            op       (first (:operations inforequest))]
        (mongo/update-by-id :applications id {$set {:infoRequest false
                                                    :state :open
                                                    :allowedAttachmentTypes (partition 2 attachment/attachment-types)
                                                    :documents (make-documents (-> command :user security/summary) created nil op)
                                                    :modified created}
                                              $pushAll {:attachments (make-attachments created op (:municipality inforequest))}})))))

;;
;; Verdicts
;;

(defcommand "give-verdict"
  {:parameters [:id :verdictId :status :name :given :official]
   :states     [:submitted #_:verdictGiven]
   :roles      [:authority]}
  [{{:keys [id verdictId status name given official]} :data {:keys [host]} :web created :created}]
  (mongo/update
    :applications
    {:_id id}
    {$set {:modified created
           :state    :verdictGiven}
     $push {:verdict  {:id verdictId
                       :name name
                       :given given
                       :status status
                       :official official}}}))

;;
;; krysp enrichment
;;

(defn add-value-metadata [m meta-data]
  (reduce (fn [r [k v]] (assoc r k (if (map? v) (add-value-metadata v meta-data) (assoc meta-data :value v)))) {} m))

(defcommand "merge-details-from-krysp"
  {:parameters [:id :documentId :buildingId]
   :roles      [:applicant :authority]}
  [{{:keys [id documentId buildingId]} :data :as command}]
  (with-application command
    (fn [{:keys [municipality propertyId] :as application}]
      (if-let [legacy (municipality/get-legacy municipality)]
        (let [doc-name     "rakennuksen-muuttaminen"
              document     (domain/get-document-by-id (:documents application) documentId)
              old-body     (:data document)
              kryspxml     (krysp/building-xml legacy propertyId)
              new-body     (or (krysp/->rakennuksen-muuttaminen kryspxml buildingId) {})
              with-value-metadata (add-value-metadata new-body {:source :krysp})]
          (mongo/update
            :applications
            {:_id (:id application)
             :documents {$elemMatch {:id documentId}}}
            {$set {:documents.$.data with-value-metadata
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
                  get-application-operation
                  get-applicant-name
                  :submitted
                  :modified
                  :state
                  :authority])

(def order-by (assoc col-sources 0 :infoRequest, 2 nil, 3 nil))

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
        {:address {$regex search $options "i"}}))))

(defn make-sort [params]
  (let [col (get order-by (:iSortCol_0 params))
        dir (if (= "asc" (:sSortDir_0 params)) 1 -1)]
    (if col {col dir} {})))

(defn applications-for-user [user params]
  (let [user-query  (domain/application-query-for user)
        user-total  (mongo/count :applications user-query)
        query       (make-query user-query params)
        query-total (mongo/count :applications query)
        skip        (params :iDisplayStart)
        limit       (params :iDisplayLength)
        apps        (query/with-collection "applications"
                      (query/find query)
                      (query/sort (make-sort params))
                      (query/skip skip)
                      (query/limit limit))
        rows        (map (comp make-row with-meta-fields) apps)
        echo        (str (Integer/parseInt (str (params :sEcho))))] ; Prevent XSS

    {:aaData                rows
     :iTotalRecords         user-total
     :iTotalDisplayRecords  query-total
     :sEcho                 echo}))

(defcommand "applications-for-datatables"
  {:parameters [:params] :verified true}
  [{user :user {:keys [params]} :data}]
  (ok :data (applications-for-user user params)))

;;
;; Query that returns number of applications or info-requests user has:
;;

(defquery "applications-count"
  {:parameters [:kind]
   :authenticated true
   :verified true}
  [{user :user {kind :kind} :data}]
  (let [base-query (domain/application-query-for user)
        query (condp = kind
                "inforequests" (assoc base-query :infoRequest true)
                "applications" (assoc base-query :infoRequest false)
                "both"         base-query)]
    (ok :data (mongo/count :applications query))))
