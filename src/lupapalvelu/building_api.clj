(ns lupapalvelu.building-api
  (:require [lupapalvelu.action :refer [defquery defcommand defraw] :as action]
            [lupapalvelu.building-attributes :as attr]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.organization :as org]
            [sade.core :refer [ok fail fail! unauthorized]]
            [sade.strings :as ss]))

(defn- file-size-positive [{{files :files} :data}]
  (when-not (some-> files first :size pos?)
    (fail :error.select-file)))

(defn- validate-mime-type [{{files :files} :data}]
  (when-not (-> files (first) :filename (mime/allowed-file?))
    (fail :error.file-upload.illegal-file-type)))

(defn org-does-not-have-existing-buildings
  "Pre-checker that fails if buildings have already been added to the org"
  [{{:keys [organizationId]} :data}]
  (when (attr/org-has-existing-buildings? organizationId)
    (fail :error.building.import-not-allowed-when-existing-buildings)))

(defn org-has-permit-type-R
  "Pre-checker that fails if org does not have R permit type"
  [{{:keys [organizationId]} :data}]
  (when-not (ss/blank? organizationId)
    (let [org (org/get-organization organizationId)]
      (when-not (org/has-permit-type? org :R)
        (fail :error.unsupported-permit-type)))))

(defquery buildings
  {:description "Fetches organization's buildings"
   :parameters [organizationId]
   :pre-checks [org-has-permit-type-R]
   :permissions [{:required [:organization/admin]}]}
  [_]
  (let [buildings (attr/fetch-buildings organizationId)]
    (ok :data buildings)))

(defcommand update-building
  {:description "Updates organization's building data"
   :parameters  [organizationId building-update]
   :pre-checks [org-has-permit-type-R]
   :permissions [{:required [:organization/admin]}]
   :input-validators [attr/validate-building-attribute-update]}
  [_]
  (let [{:keys [error error-data building-id updated-building] :as result} (attr/upsert-building-attribute! organizationId building-update)]
    (if error
      (-> (fail error) (merge {:error-data error-data}))
      (ok :data {:building-id building-id
                 :building updated-building}))))

(defcommand remove-building
  {:description "Removes a building from organization's building attribute data"
   :parameters  [organizationId building-id]
   :pre-checks [org-has-permit-type-R]
   :permissions [{:required [:organization/admin]}]
   :input-validators [attr/validate-building-remove-request]}
  [_]
  (let [{:keys [error building-id] :as result} (attr/mark-building-removed! building-id)]
    (if error
      (fail error)
      (ok :data {:building-id building-id}))))

(defcommand update-buildings-in-archive
  {:description "Updates building attributes in archive"
   :parameters  [organizationId building-ids]
   :pre-checks [org-has-permit-type-R]
   :permissions [{:required [:organization/admin]}]
   :input-validators [attr/validate-update-buildings-in-archive-request]}
  [_]
  (let [{:keys [result-by-building error] :as result} (attr/update-in-archive! organizationId building-ids)]
    (if error
      (fail error {:data result-by-building})
      (ok :data result-by-building))))

(defraw upload-building-data
  {:description "Handles excel-file (xlsx) that sets the initial data for organization buildings as a batch"
   :parameters [organizationId files]
   :pre-checks [org-has-permit-type-R
                org-does-not-have-existing-buildings]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      validate-mime-type
                      file-size-positive
                      file-upload/file-size-legal]
   :permissions [{:required [:organization/admin]}]}
  [command]
  (attr/upload-building-data organizationId files command))
