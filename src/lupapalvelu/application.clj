(ns lupapalvelu.application
  (:use [monger.operators]
        [lupapalvelu.log]
        [lupapalvelu.core :only [defquery defcommand ok fail with-application executed now role]]
        [lupapalvelu.action :only [application-query-for get-application-as]]
        [lupapalvelu.attachment :only [create-attachment attachment-types-for]]
        [lupapalvelu.document.commands :only [create-document]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.tepa :as tepa]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.security :as security]
            [lupapalvelu.util :as util]))

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
  (ok :applications (map with-meta-fields (mongo/select mongo/applications (application-query-for user)))))

(defn find-authorities-in-applications-municipality [app]
  (mongo/select mongo/users {:municipality (:municipality app) :role "authority"} {:firstName 1 :lastName 1}))

(defquery "application" {:authenticated true, :parameters [:id]} [{{id :id} :data user :user}]
  (if-let [app (get-application-as id user)]
    (ok :application (with-meta-fields app)
        :authorities (find-authorities-in-applications-municipality app))
    (fail :error.not-found)))

;; Gets an array of application ids and returns a map for each application that contains the
;; application id and the authorities in that municipality.
(defquery "authorities-in-applications-municipality"
  {:parameters [:id]
   :authenticated true}
  [command]
  (let [id (-> command :data :id)
        app (mongo/select-one mongo/applications {:_id id} {:municipality 1})
        authorities (find-authorities-in-applications-municipality app)]
    (ok :authorityInfo authorities)))

(defcommand "assign-application"
  {:parameters  [:id :assigneeId]
   :roles       [:authority]}
  [{{:keys [assigneeId]} :data user :user :as command}]
  (with-application command
    (fn [application]
      (mongo/update-by-id
        mongo/applications (:id application)
        (if assigneeId 
          {$set {:roles.authority (security/summary (mongo/select-one mongo/users {:_id assigneeId}))}}
          {$unset {:roles.authority ""}})))))

(defcommand "open-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft]}
  [command]
  (with-application command
    (fn [{id :id}]
      (mongo/update-by-id mongo/applications id
        {$set {:modified (:created command)
               :state :open
               :opened (:created command)}}))))

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
        mongo/applications {:_id (:id application) :state :submitted}
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
        mongo/applications {:_id (:id application)}
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
        mongo/applications {:_id (:id application)}
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
        mongo/applications
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
          mongo/applications
          id
          {$set {:comments (:comments inforequest)}})
        (ok :id id)))))

(def default-schemas {:infoRequest []
                      :buildingPermit ["hakija" "paasuunnittelija" "suunnittelija" "maksaja"
                                       "rakennuspaikka" "uusiRakennus" "huoneisto" "lisatiedot"]})

(def default-attachments {:infoRequest []
                          :buildingPermit (map (fn [[type-group type-id]] {:type-group type-group :type-id type-id})
                                               [["paapiirustus" "asemapiirros"]
                                                ["paapiirustus" "pohjapiirros"]
                                                ["paapiirustus" "leikkauspiirros"]
                                                ["paapiirustus" "julkisivupiirros"]
                                                ["rakennuspaikka" "selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista"]
                                                ["muut" "energiataloudellinen_selvitys"]])})

(defcommand "create-application"
  {:parameters [:permitType :x :y :address :propertyId :municipality]
   :roles      [:applicant]}
  [command]
  (let [{:keys [user created data]} command
        id            (mongo/create-id)
        owner         (role user :owner :type :owner)
        permit-type   (keyword (:permitType data))
        operation     (keyword (:operation data))
        info-request  (if (:infoRequest data) true false)]
    (mongo/insert mongo/applications
      {:id id
       :created created
       :modified created
       :state (if info-request :open :draft)
       :municipality (:municipality data)
       :location {:x (:x data) :y (:y data)}
       :address (:address data)
       :propertyId (:propertyId data)
       :title (:address data)
       :roles {:applicant owner}
       :auth [owner]
       :infoRequest info-request
       :permitType permit-type
       :operations (if operation [operation] [])
       :allowedAttachmentTypes (if info-request [[:muut [:muu]]] (attachment-types-for operation))
       :documents (map #(create-document (mongo/create-id) %) (:buildingPermit default-schemas))
       :attachments []
       :comments (if-let [message (:message data)]
                   [{:text message
                     :target  {:type "application"}
                     :created created
                     :user    (security/summary user)}]
                   [])})
    (doseq [attachment-type (default-attachments operation [])]
      (info "Create attachment: [%s]: %s" id attachment-type)
      (create-attachment id attachment-type created))
    (ok :id id)))

(defcommand "add-operation"
  {:parameters [:id :operation]
   :roles      [:applicant :authority]
   :roles-in   [:applicant]
   :states     [:draft :open]}
  [command]
  (with-application command
    (fn [application]
      (debug "Adding operation: id='%s', operation='%s'" (get-in command [:data :id]) (get-in command [:data :operation])))))
