(ns lupapalvelu.assurances-api
  (:require [lupapalvelu.action :refer [defcommand defquery]]
            [lupapalvelu.assurances :as assurances]
            [lupapalvelu.assurance-schemas :as assurance-schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(defquery application-submit-assurance-templates
  {:description      "Returns such assurance templates from organization that are relevant to the application"
   :parameters       [id]
   :states           #{:draft :open}
   :input-validators [{:id ssc/NonBlankStr}]
   :permissions      [{:required [:application/submit]}]}
  [{:keys [organization application]}]
  (ok :assurances (assurances/get-application-submit-assurance-templates organization application)))

(defcommand create-application-submit-assurance-template
  {:description      "Receives data for a new assurance template and pushes it to the end of organization's assurances
                      template array."
   :parameters       [organizationId assurance]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :assurance      (dissoc assurance-schemas/ApplicationSubmitAssuranceTemplate :id :modified)}]
   :pre-checks       [org/organization-exists]}
  [{:keys [created]}]
  (mongo/update-by-id :organizations organizationId
    {$push {:application-submit-assurance-templates (assoc assurance
                                                           :id (mongo/create-id)
                                                           :modified created)}})
  (ok))

(defcommand save-application-submit-assurance-template
  {:description      "Stores data to organization's existing assurance template."
   :parameters       [organizationId assurance]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :assurance      (dissoc assurance-schemas/ApplicationSubmitAssuranceTemplate :modified)}]
   :pre-checks       [org/organization-exists]}
  [{:keys                                                          [created]
    {{:keys [text operations required all-operations]} :assurance} :data}]
  (let [result (mongo/update-by-query
                 :organizations
                 {:_id                                    organizationId
                  :application-submit-assurance-templates {$elemMatch {:id (:id assurance)}}}
                 {$set   (util/assoc-when
                           {:application-submit-assurance-templates.$.modified created
                            :application-submit-assurance-templates.$.text     text}
                           :application-submit-assurance-templates.$.operations (seq operations)
                           :application-submit-assurance-templates.$.required required
                           :application-submit-assurance-templates.$.all-operations all-operations)
                  $unset (util/assoc-when
                           {}
                           :application-submit-assurance-templates.$.required (not required)
                           :application-submit-assurance-templates.$.operations (not (seq operations))
                           :application-submit-assurance-templates.$.all-operations (not all-operations))})]
    (if (zero? result)
      (fail :error.assurance-not-found)
      (ok))))

(defcommand remove-application-submit-assurance-template
  {:description      "Removes an assurance template from organization"
   :parameters       [organizationId assuranceId]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :assuranceId    ssc/ObjectIdStr}]
   :pre-checks       [org/organization-exists]}
  [{:keys [created]}]
  (let [result (mongo/update-by-query :organizations
                                      {:_id                                    organizationId
                                       :application-submit-assurance-templates {$elemMatch {:id assuranceId}}}
                                      {$pull {:application-submit-assurance-templates {:id assuranceId}}})]
    (if (zero? result)
      (fail :error.assurance-not-found)
      (ok))))

(defcommand store-application-submit-assurance-order
  {:description      "Receives an ordered list of template IDs and arranges templates in organization's data
                      accordingly."
   :parameters       [organizationId assuranceIds]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :assuranceIds   [ssc/ObjectIdStr]}]
   :pre-checks       [org/organization-exists]}
  [_]
  (let [original-templates (:application-submit-assurance-templates
                             (mongo/by-id :organizations organizationId
                                          [:application-submit-assurance-templates]))
        assurance-data     (into {} (map (juxt :id identity) original-templates))
        updated-assurances (mapv (fn [id] (assurance-data id)) assuranceIds)]
    (if (= (set assuranceIds)
           (->> original-templates (map :id) set))
      (do
        (org/update-organization organizationId {$set {:application-submit-assurance-templates updated-assurances}})
        (ok))
      (fail :error.invalid-assurance-id-list))))

(defquery get-assurances-for-application
  {:description      "Get the submitted assurances for some application by application id"
   :parameters       [id]
   :input-validators [{:id ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]}
  [{:keys [application]}]
  (ok {:data (:application-submit-assurances application)}))
