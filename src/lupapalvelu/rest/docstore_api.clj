(ns lupapalvelu.rest.docstore-api
  (:require [noir.response :as resp]
            [schema.core :as sc]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rest.rest-api :refer [defendpoint-for]]
            [lupapalvelu.rest.schemas :refer [ApiResponse]]
            [lupapalvelu.user :as usr]
            [sade.core :refer [fail]]))

(sc/defschema OrganizationDocstoreInfo
  (assoc org/DocStoreInfo
         :id org/OrgId))

(sc/defschema OrganizationResponse
  (assoc ApiResponse :data OrganizationDocstoreInfo))

(sc/defschema OrganizationsResponse
  (assoc ApiResponse :data [OrganizationDocstoreInfo]))

(defn get-docstore-infos
  ([]
   (get-docstore-infos {}))
  ([query]
   (->> (org/get-organizations query {:docstore-info 1})
        (map #(let [{:keys [id docstore-info]} %]
                (assoc docstore-info :id id)))
        (remove (partial sc/check OrganizationDocstoreInfo)))))

(defendpoint-for usr/docstore-user? "/rest/docstore/organizations" false
    {:summary     ""
     :description ""
     :parameters  []
     :returns     OrganizationsResponse}
  {:ok true :data (get-docstore-infos)})


(defendpoint-for usr/docstore-user? "/rest/docstore/organization" false
    {:summary     ""
     :description ""
     :parameters  [:id org/OrgId]
     :returns     OrganizationResponse}
  (if-let [docstore-info (first (get-docstore-infos {:_id id}))]
    {:ok true :data docstore-info}
    (fail :error.missing-organization)))
