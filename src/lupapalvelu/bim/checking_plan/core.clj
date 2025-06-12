(ns lupapalvelu.bim.checking-plan.core
  "Core functionality for BIM checking plans, especially related to the organization's configuration"
  (:require [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(defn organization-plans
  "Returns the plans that are relevant for the given organization (= either enabled or configured for an operation)"
  [organization]
  (mongo/select :bim-checking-plans
                (merge {$or [{:enabled true}
                             {:_id {$in (->> organization
                                             :bim-checking-plans
                                             vals
                                             (reduce into))}}]}
                       (when-not (org/check-organization-flag organization :bim-compliance-check-enabled)
                         {:checking-type "model-validation"}))
                [:enabled :title :description :checking-type :model-type]))

(sc/defn ^:always-validate default-application-checking-plans
  "Returns the default checking plans for the given application"
  [app-id        :- ssc/NonBlankStr
   operation-id  :- ssc/NonBlankStr
   checking-type :- bim-cp-schema/CheckingType
   model-type    :- bim-schema/ModelType]
  (let [application      (mongo/by-id :applications app-id [:primaryOperation :secondaryOperations :organization])
        organization     (mongo/by-id :organizations (:organization application) [:bim-checking-plans])
        operation-kw     (-> application (app-utils/operation-by-id operation-id) :name keyword)
        default-plan-ids (some-> organization :bim-checking-plans operation-kw)]
    (when (seq default-plan-ids)
      (mongo/select :bim-checking-plans
                    {:_id           {$in default-plan-ids}
                     :enabled       true
                     :checking-type checking-type
                     :model-type    model-type}
                    [:file.bucket :file.object-key]))))
