(ns lupapalvelu.application
  (:use [monger.operators]
        [lupapalvelu.log]
        [lupapalvelu.core :only [defquery defcommand ok fail with-application executed now role]]
        [lupapalvelu.action :only [application-query-for get-application-as]]
        [lupapalvelu.attachment :only [create-attachment attachment-types-for]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.tepa :as tepa]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]))

(defquery "applications" {:authenticated true} [{user :user}]
  (ok :applications (mongo/select mongo/applications (application-query-for user))))

(defquery "application" {:authenticated true, :parameters [:id]} [{{id :id} :data user :user}]
  (if-let [app (get-application-as id user)]
    (ok :application app)
    (fail :error.not-found)))

(defcommand "open-application"
  {:parameters [:id]
   :roles      [:applicant]
   :states     [:draft]}
  [command]
  (with-application command
    (fn [{id :id}]
      (mongo/update-by-id mongo/applications id
        {$set {:modified (:created command)
               :state :open}}))))

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

(defn create-document [schema-name]
  (let [schema (get schemas/schemas schema-name)]
    (if (nil? schema) (throw (Exception. (str "Unknown schema: [" schema-name "]"))))
    {:id (mongo/create-id)
     :created (now)
     :schema schema
     :body {}}))

; TODO: Figure out where these should come from?
(def default-schemas {:infoRequest []
                      :buildingPermit 
                      ["hakija" "paasuunnittelija" "suunnittelija" "maksaja" "rakennuspaikka" "uusiRakennus" "huoneisto" "lisatiedot"]})

(def default-attachments {:infoRequest []
                          :buildingPermit (map (fn [[type-group type-id]] {:type-group type-group :type-id type-id})
                              [["paapiirustus" "asemapiirros"]
                               ["paapiirustus" "pohjapiirros"]
                               ["paapiirustus" "leikkauspiirros"]
                               ["paapiirustus" "julkisivupiirros"]
                               ["rakennuspaikka" "selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista"]
                               ["muut" "energiataloudellinen_selvitys"]])})

(defcommand "create-application"
  {:parameters [:permitType :x :y :address :municipality]
   :roles      [:applicant]}
  [command]
  (let [{:keys [user created data]} command
        id         (mongo/create-id)
        owner      (role user :owner :type :owner)
        permitType (keyword (:permitType data))
        documents  (map create-document (permitType default-schemas))
        message    (:message data)]
    (mongo/insert mongo/applications
      {:id id
       :created created
       :modified created
       :state :draft
       :municipality {}
       :location {:x (:x data)
                  :y (:y data)}
       :address (:address data)
       :title (:address data)
       :authority (:municipality data)
       :roles {:applicant owner}
       :auth [owner]
       :documents documents
       :permitType permitType 
       :allowedAttahmentTypes (attachment-types-for permitType)
       :attachments []})
    (if message
      (executed (assoc (lupapalvelu.core/command "add-comment" {:id id
                                                                :text message
                                                                :target {:type "application"}})
                       :user user)))
    (doseq [attachment-type (default-attachments permitType)]
      (info "Create attachment: [%s]: %s" id attachment-type)
      (create-attachment id attachment-type created))
    (ok :id id)))

