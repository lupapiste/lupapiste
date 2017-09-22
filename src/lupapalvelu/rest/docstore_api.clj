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
            [clojure.string :as str]
            [lupapalvelu.permit :as permit]))

(sc/defschema OrganizationDocstoreInfo
  (assoc org/DocStoreInfo
         :id org/OrgId
         :name (i18n/localization-schema sc/Str)
         :municipalities [{:id         sc/Str
                           :name       (i18n/localization-schema sc/Str)}]))

(sc/defschema OrganizationResponse
  (assoc ApiResponse :data OrganizationDocstoreInfo))

(sc/defschema OrganizationsResponse
  (assoc ApiResponse :data [OrganizationDocstoreInfo]))

(defn- municipality-name [municipality-code]
  (i18n/supported-langs-map #(i18n/localize % (str "municipality." municipality-code))))

(defn- municipality-info [{:keys [municipality permitType]}]
  {:id         municipality
   :name       (municipality-name municipality)})

(defn- make-docstore-info [organization]
  (let [{:keys [id docstore-info name scope]} organization]
    (assoc docstore-info
           :id             id
           :name           name
           :municipalities (->> scope
                                (map municipality-info)
                                (distinct)))))

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

