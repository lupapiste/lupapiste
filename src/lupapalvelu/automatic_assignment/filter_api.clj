(ns lupapalvelu.automatic-assignment.filter-api
  "Configuration and resolution for automatic assignments."
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.automatic-assignment.core :as automatic]
            [lupapalvelu.automatic-assignment.schemas :refer [UpsertParams]]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
            [sade.util :as util]))

(defcommand delete-automatic-assignment-filter
  {:description      "Deletes an existing assignment filter."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId filter-id]
   :input-validators [(partial action/non-blank-parameters [:organizationId :filter-id])]
   :pre-checks       [automatic/filter-exists]}
  [{:keys [created] :as command}]
  (ok (automatic/commit command (automatic/delete-filter filter-id))))

(defcommand upsert-automatic-assignment-filter
  {:description      "Create a new filter or replace an existing one with the given (fully formed) filter.
Returns the upserted filter."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId :filter]
   :input-validators [UpsertParams]
   :pre-checks       [automatic/upsert-filter-exists]}
  [command]
  (ok :filter (automatic/upsert-filter command)))

(defquery automatic-assignment-authorities
  {:description      "List of organization authorities that are suitable
  for automatic assignment target users. Each item has firstName, lastName and id
  property."
   :permissions      [{:required [:organization/admin]}]
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]}
  [_]
  (ok :authorities (mongo/select :users
                                 {(util/kw-path :orgAuthz
                                                organizationId) "authority"
                                  :role                         "authority"
                                  :enabled                      true}
                                 [:firstName :lastName])))
