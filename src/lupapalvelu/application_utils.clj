(ns lupapalvelu.application-utils
  (:require [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.foreman-application-util :as foreman-app-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]))


(defn get-operations [application]
  (remove nil? (conj (seq (:secondaryOperations application)) (:primaryOperation application))))

(defn get-sorted-operation-documents [{docs :documents primary-op :primaryOperation secondary-ops :secondaryOperations}]
  (let [operations (cons primary-op (sort-by :created secondary-ops))
        operation-ids (set (map :id operations))]
    (->> docs
         (filter (comp operation-ids :id :op :schema-info))
         (sort-by (util/fn-> :schema-info :op :id (util/position-by-id operations))))))

(defn operation-description
  "obtain the name of application's primary operation, taking into
  account that some legacy applications may not have a primary
  operation"
  [application lang]
  (let [primary-operation (-> application :primaryOperation :name)]
    (if primary-operation
      (i18n/localize lang "operations" primary-operation)
      "")))

(defn with-application-kind [{:keys [permitSubtype infoRequest permitType] :as app}]
  (assoc app :kind (cond
                     infoRequest "applications.inforequest"
                     (not (ss/blank? permitSubtype))
                                 (str "permitSubtype." permitSubtype)
                     (= permitType permit/ARK) "digitizedPermit"
                     :else       "applications.application")))

(defn enrich-applications-with-organization-name [applications]
  (let [application-org-ids    (map (comp :organization first) (partition-by :organization applications))
        organization-name-map  (zipmap application-org-ids
                                       (map #(org/with-organization % org/get-organization-name)
                                            application-org-ids))]
    (map (fn [app] (assoc app :organizationName (get organization-name-map (:organization app)))) applications)))

(defn with-organization-name [{:keys [organization] :as app}]
  (assoc app :organizationName (org/with-organization organization org/get-organization-name)))

(defn location->object [application]
  (let [[x y] (:location application)]
    (assoc application :location {:x x :y y})))

(defn make-area-query
  ([areas user]
   (make-area-query areas user nil))
  ([areas user key-prefix]
   {:pre [(sequential? areas)]}
   (let [orgs (usr/organization-ids-by-roles user #{:authority :commenter :reader})
         orgs-with-areas (mongo/select :organizations {:_id {$in orgs} :areas-wgs84.features.id {$in areas}} [:areas-wgs84])
         features (flatten (map (comp :features :areas-wgs84) orgs-with-areas))
         selected-areas (set areas)
         filtered-features (filter (comp selected-areas :id) features)
         target-kw (if (keyword? key-prefix)
                     (keyword (str (name key-prefix) ".location-wgs84"))
                     :location-wgs84)]
     (when (seq filtered-features)
       {$or (map (fn [feature] {target-kw {$geoWithin {"$geometry" (:geometry feature)}}}) filtered-features)}))))

; Masking
(defn person-id-masker-for-user [user application]
  (cond
    (auth/application-handler? application user) identity
    (auth/application-authority? application user)  (comp model/mask-person-id-ending
                                                          model/mask-non-finnish-person-id)
    :else (comp model/mask-person-id-birthday
                model/mask-person-id-ending
                model/mask-non-finnish-person-id)))

(defn with-masked-person-ids [application user]
  (let [mask-person-ids (person-id-masker-for-user user application)]
    (update-in application [:documents] (partial map mask-person-ids))))

; Parties
(defn- get-party-category
  "Returns the category that the given party belongs to.
   See get-application-email-parties for a list of the categories."
  [document]
  (let [schema-info (:schema-info document)
        name        (:name schema-info)
        subtype     (:subtype schema-info)
        role        (get-in document [:data :kuntaRoolikoodi :value])]
    (cond
      (= "hakija-tj" name)                  nil ;Foreman "applicants" aren't what we're looking for.
      (= "hakija" subtype)                  "hakija"
      (= "p\u00e4\u00e4suunnittelija" role) "paasuunnittelija" ;Lesser lead designer
      (= "paasuunnittelija" name)           "paasuunnittelija" ;Proper lead designer
      (= "suunnittelija" subtype)           "erityissuunnittelijat"
      (= "vastaava ty\u00f6njohtaja" role)  "vastaava tyonjohtaja"
      (= "tyonjohtaja-v2" name)             "muut tyonjohtajat"
      :else                                 nil)))

(defn- get-party-detail
  "Returns the given field's value for the party document from all possible hiding places."
  [document field-path]
  (or (get-in document (concat [:data] field-path [:value]))
      (get-in document (concat [:data :henkilo] field-path [:value]))
      (get-in document (concat [:data :yritys :yhteyshenkilo] field-path [:value]))))

(defn get-application-email-parties
  "Collects the parties of the document into a list with categories.
   Used for authority admin authored emails, whose recipients are listed at category level.
   The categories are (with alternative translations):
   - \"hakija\"                 The applicant of the actual application (not foremen applications)
   - \"pääsuunnittelija\"       The lead designer (planner)
   - \"erityissuunnittelijat\"  The other designers (planners)
   - \"vastaava tyonjohtaja\"   The senior supervisor (foreman)
   - \"muut tyonjohtajat\"      Other supervisors (foremen)"
  [application]
  (->> (foreman-app-util/get-linked-foreman-applications application)
       (map :documents)
       (apply concat (:documents application))
       (filter #(= "party" (get-in % [:schema-info :type])))            ; Only parties are considered
       (map #(hash-map :firstName   (get-party-detail % [:henkilotiedot :etunimi])
                       :lastName    (get-party-detail % [:henkilotiedot :sukunimi])
                       :email       (get-party-detail % [:yhteystiedot :email])
                       :category    (get-party-category %)))
       (filterv #(and (some? (:category %)) (some? (:email %))))))

;; https://eevertti.vrk.fi/documents/2634109/3072453/VTJ-yll%C3%A4pito+Virhekoodit+Rajapinta/e7904362-6c43-43e6-8f1c-a80b24313ac9?version=1.0
;; gives some hint for valid ID, but is still pretty confusing...

(defn vrk-lupatunnus                                        ; LPK-3207
  "Mimics other system's interpretation of VRKLupatunnus (KuntaGML).
  Number part is fixed at 4 digits, and can't be '0000'.
  When sequence hits 10000, it would generate illegal value '0000'. To bypass this we will take first 4 in this special case.
  For the next value 10001 we would be back in line returning '0001'.
  It seems values do not need to be unique accross time."
  [{:keys [municipality created submitted id]}]
  (when (and (not-any? ss/blank? [municipality id]) (or submitted created))
    (let [orig-suffix (ss/suffix id "-")
          vrk-suffix (->> orig-suffix
                          (take-last 4)
                          (apply str))
          final-suffix (if (= "0000" vrk-suffix)            ; handle special case
                         (->> orig-suffix
                              (take 4)
                              (apply str))
                         vrk-suffix)]
      (assert (not= "0000" final-suffix) "VRKLupatunnus number can't be '0000'")
      (format "%s000%ty-%s" municipality (or submitted created) final-suffix))))

(defschema OperationBuilding
  {:opId                          ssc/NonBlankStr
   :opName                        ssc/NonBlankStr
   (sc/optional-key :buildingId)  sc/Str
   (sc/optional-key :description) sc/Str
   (sc/optional-key :nationalId)  sc/Str})

(sc/defn ^:always-validate application-operation-buildings :- [OperationBuilding]
  [{:keys [buildings] :as application}]
  (let [operations (get-operations application)]
    (for [{:keys [operationId description]
           :as   build} buildings
          :let          [op (util/find-by-id operationId operations)]
          :when         op]
      (->> (select-keys build [:buildingId :nationalId])
           (merge {;; Operation description overrides the building description.
                   :description (or (some-> op :description ss/blank-as-nil)
                                    description)
                   :opName      (:name op)
                   :opId        operationId})
           util/strip-blanks
           ss/trimwalk))))
