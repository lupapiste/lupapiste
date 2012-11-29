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

(defn create-document [schema-name]
  (let [schema (get schemas/schemas schema-name)]
    (if (nil? schema) (throw (Exception. (str "Unknown schema: [" schema-name "]"))))
    {:id (mongo/create-id)
     :created (now)
     :schema schema
     :body {}}))

; TODO: Figure out where these should come from?
(def default-schemas ["hakija" "paasuunnittelija" "suunnittelija" "maksaja" "rakennuspaikka" "uusiRakennus" "huoneisto" "lisatiedot"])
(def default-attachments (map (fn [[type-group type-id]] {:type-group type-group :type-id type-id})
                              [["hakija" "valtakirja"]
                               ["hakija" "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]
                               ["rakennuspaikka" "tonttikartta_tarvittaessa"]
                               ["muut" "piha_tai_istutussuunnitelma"]
                               ["muut" "selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista"]
                               ["muut" "liikkumis_ja_esteettomyysselvitys"]
                               ["muut" "selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo"]]))

(defcommand "create-application"
  {:parameters [:x :y :street :zip :city]
   :roles      [:applicant]}
  [command]
  (let [{:keys [user created data]} command
        id        (mongo/create-id)
        owner     (role user :owner :type :owner)
        documents (map create-document default-schemas)]
    (mongo/insert mongo/applications
      {:id id
       :created created
       :modified created
       :state :draft
       :municipality {}
       :location {:x (:x data)
                  :y (:y data)}
       :address {:street (:street data)
                 :zip (:zip data)
                 :city (:city data)}
       :title (:street data)
       :authority (:city data)
       :roles {:applicant owner}
       :auth [owner]
       :documents documents
       :permitType :buildingPermit
       :allowedAttahmentTypes (attachment-types-for :buildingPermit)
       :attachments []})
    (future ; TODO: Should use agent with error handling:
      (if-let [municipality (:result (executed "municipality-by-location" command))]
        (mongo/update-by-id mongo/applications id {$set {:municipality municipality}})))
    (doseq [attachment-type default-attachments]
      (info "Create attachment: [%s]: %s" id attachment-type)
      (create-attachment id attachment-type created))
    (ok :id id)))

