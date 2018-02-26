(ns lupapalvelu.rest.docstore-api
  (:require [clojure.set :refer [rename-keys]]
            [noir.response :as resp]
            [schema.core :as sc]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rest.rest-api :refer [defendpoint-for]]
            [lupapalvelu.rest.schemas :refer [ApiResponse]]
            [lupapalvelu.user :as usr]
            [sade.core :refer [fail]]
            [sade.validators :refer [matches?]]
            [clojure.string :as str]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.building-reader :as building-reader]))

;; Schema definitions

(sc/defschema OrganizationMunicipalityInfo
  {:id sc/Str
   :name (i18n/lenient-localization-schema sc/Str)
   :permitType org/PermitType})

(sc/defschema OrganizationDocstoreInfo
  (-> org/DocStoreInfo
      (dissoc :allowedTerminalAttachmentTypes)
      (assoc :id org/OrgId
             :name (i18n/lenient-localization-schema sc/Str)
             :municipalities [OrganizationMunicipalityInfo])))

(sc/defschema OrganizationResponse
  (assoc ApiResponse :data OrganizationDocstoreInfo))

(sc/defschema OrganizationsResponse
  (assoc ApiResponse :data [OrganizationDocstoreInfo]))

(sc/defschema OrganizationDocterminalAllowedAttachmentTypesInfo
  {:id org/OrgId
   :municipalities [OrganizationMunicipalityInfo]
   :allowedTerminalAttachmentTypes [org/DocTerminalAttachmentType]})

(sc/defschema AllowedAttachmentTypesResponse
  (assoc ApiResponse :data [OrganizationDocterminalAllowedAttachmentTypesInfo]))


;; Docstore info

(defn- municipality-name [municipality-code]
  (i18n/supported-langs-map #(i18n/localize % (str "municipality." municipality-code))))

(defn municipality-info [{:keys [municipality permitType]}]
  {:id         municipality
   :name       (municipality-name municipality)
   :permitType permitType})

(defn- make-docstore-info [{:keys [id docstore-info name scope]}]
  (-> docstore-info
      (dissoc :allowedTerminalAttachmentTypes)
      (assoc :id             id
             :name           name
             :municipalities (->> scope
                                  (map municipality-info)
                                  distinct))))

(defn get-docstore-infos
  ([]
   (get-docstore-infos {}))
  ([query]
   (->> (org/get-organizations query {:docstore-info 1
                                      :name          1
                                      :scope         1})
        (map make-docstore-info)
        (remove (partial sc/check OrganizationDocstoreInfo)))))

(sc/defschema OrganizationStatusFilter
  (sc/enum "all" "active" "inactive"))

(def status-query
  {"all"      {}
   "active"   {:docstore-info.docStoreInUse true}
   "inactive" {:docstore-info.docStoreInUse false}})

(defendpoint-for usr/docstore-user? "/rest/docstore/organization" false
    {:summary     ""
     :description ""
     :parameters  [:id org/OrgId]
     :returns     OrganizationResponse}
  (if-let [docstore-info (first (get-docstore-infos {:_id id}))]
    {:ok true :data docstore-info}
    (resp/status 404 (resp/json (fail :error.missing-organization)))))

(defendpoint-for usr/docstore-user? "/rest/docstore/organizations" false
  {:summary             ""
   :description         ""
   :parameters          []
   :optional-parameters [:status OrganizationStatusFilter
                         :permit-type (apply sc/enum (keys (permit/permit-types)))]
   :returns             OrganizationsResponse}
  (let [query (cond-> (get status-query status {})
                      (not (str/blank? permit-type)) (assoc :scope.permitType permit-type))]
    {:ok true :data (get-docstore-infos query)}))


;; Allowed attachment types for docterminal

(defn- make-attachment-type-info [{:keys [id docstore-info scope]}]
  (-> docstore-info
      (select-keys [:allowedTerminalAttachmentTypes])
      (assoc :id             id
             :municipalities (->> scope
                                  (map municipality-info)
                                  (distinct)))))

(defn get-attachment-type-infos []
  (->> (org/get-organizations {:docstore-info.docTerminalInUse true}
                              [:docstore-info :scope])
       (map make-attachment-type-info)))

(defendpoint-for usr/docstore-user? "/rest/docstore/allowed-attachment-types" false
  {:summary     ""
   :description ""
   :parameters  []
   :returns     AllowedAttachmentTypesResponse}
  {:ok true :data (get-attachment-type-infos)})


(sc/defschema BuildingsResponse
  (assoc ApiResponse :data [{:nationalId sc/Str :usage (sc/maybe sc/Str)}]))

(defendpoint-for usr/docstore-user? "/rest/docstore/property-building-ids" false
  {:summary "Given address or property id, return a list of buildings in the given property."
   :description ""
   :parameters [:organization org/OrgId
                :propertyId   (sc/pred (partial matches? #"\d+"))]
   :returns BuildingsResponse}
  (if-let [{url :url credentials :credentials} (org/get-building-wfs {:organization organization
                                                                      :permitType "R"})]
    {:ok true :data (->> (building-reader/building-info-list url credentials propertyId)
                         (map #(select-keys % [:usage :nationalId]))
                         (remove (comp nil? :nationalId))
                         (map #(merge {:usage nil} %))
                         (sort-by :nationalId)
                         vec)}
    {:ok true :data []}))
