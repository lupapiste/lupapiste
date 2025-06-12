(ns lupapalvelu.bim.checking-plan.api
  "API for uploading, disabling and otherwise configuring BIM integration checking plans
  available for validation and compliance checking.

  BIM integration checking plans are sets of checks (checkset files .CSET) that
  are first uploaded and managed by the Lupapiste admin, made the default for a given operation by
  the authority admin and then possibly customized by the authority on the application itself before
  finally being run by the uploader of the IFC file itself.

  They are managed by Lupapiste admins for the whole app since they list of currently valid checks is
  ultimately mandated by the government."
  (:require [lupapalvelu.action :refer [defcommand defquery defraw]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.bim.checking-plan.core :as bim-cp]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.core :as bim]
            [lupapalvelu.organization :as org]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.object-storage :as object-storage]
            [monger.operators :refer :all]
            [sade.core :refer [now ok fail]]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Lupapiste admin actions

(defquery bim-checking-plans
  {:user-roles #{:admin}}
  [_]
  (ok :bim-checking-plans (into [] (mongo/select :bim-checking-plans))))

(defcommand create-bim-checking-plan
  {:description      "Creates a new disabled checking plan using the already uploaded checkset file"
   :parameters       [object-key]
   :input-validators [{:object-key ssc/NonBlankStr}]
   :user-roles       #{:admin}}
  [{:keys [created]}]
  (if-let [metadata (storage/get-metadata object-storage/bim-bucket object-key)]
    (let [filename            (:filename metadata)
          draft-checking-plan {:id            (mongo/create-id)
                               :enabled       false
                               :title         filename
                               :description   ""
                               :checking-type "model-validation"
                               :model-type    "architectural"
                               :org-settings  {}
                               :file          {:bucket     object-storage/bim-bucket
                                               :object-key object-key
                                               :filename   filename
                                               :created    created}}]
      (mongo/insert :bim-checking-plans draft-checking-plan)
      (ok :bim-checking-plan draft-checking-plan))
    (fail :error.not-found)))

(defcommand update-bim-checking-plan-file
  {:description         "Replaces the given plan's file with the already uploaded checkset file"
   :parameters          [plan-id object-key]
   :input-validators    [{:object-key ssc/NonBlankStr
                          :plan-id    ssc/NonBlankStr}]
   :user-roles          #{:admin}}
  [_]
  (let [old-file (:file (mongo/by-id :bim-checking-plans plan-id [:file]))
        metadata (storage/get-metadata object-storage/bim-bucket object-key)]
    (if (and old-file metadata)
      (let [filename     (:filename metadata)
            updated-file {:bucket     object-storage/bim-bucket
                          :object-key object-key
                          :filename   filename
                          :created    (now)}]
        (mongo/update-by-id :bim-checking-plans plan-id {$set {:file updated-file}})
        (storage/delete-admin-file (:bucket old-file) (:object-key old-file))
        (ok :updated-file updated-file))
      (fail :error.not-found))))

(defcommand update-bim-checking-plan
  {:description      "Updates the given BIM checking plan's non-file fields'"
   :parameters       [plan-id updates]
   :input-validators [{:plan-id ssc/NonBlankStr
                       :updates (-> bim-cp-schema/CheckingPlan
                                    (st/select-keys bim-cp-schema/admin-editable-keys)
                                    st/optional-keys)}]
   :user-roles       #{:admin}}
  [_]
  (if (mongo/any? :bim-checking-plans {:_id plan-id})
    (do (mongo/update-by-id :bim-checking-plans plan-id {$set updates})
        (ok))
    (fail :error.not-found)))

(defcommand delete-bim-checking-plan
  {:description      "Removes the given checking plan entirely. Not allowed if it is in use somewhere"
   :parameters       [plan-id]
   :input-validators [{:plan-id ssc/NonBlankStr}]
   :user-roles       #{:admin}}
  [_]
  (if-let [checking-plan (mongo/by-id :bim-checking-plans plan-id [:file :org-settings])]
    (if (-> checking-plan :org-settings empty?) ;; TODO LPK-7119 also check apps
      (do (mongo/remove-by-id :bim-checking-plans plan-id)
          (some->> checking-plan :file :object-key (storage/delete-admin-file object-storage/bim-bucket))
          (ok))
      (fail :error.bim-checking-plan-in-use))
    (fail :error.not-found)))

(defraw download-bim-checking-plan
  {:description      "Returns the given checking plan as an attachment."
   :parameters       [plan-id]
   :input-validators [{:plan-id ssc/NonBlankStr}]
   :user-roles       #{:admin}}
  [_]
  (if-let [{:keys [bucket filename object-key]} (some-> (mongo/by-id :bim-checking-plans plan-id [:file]) :file)]
    (att/output-attachment
      (assoc (storage/download-admin-file bucket object-key) :filename filename)
      true)
    (fail :error.not-found)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Authority admin actions

(defquery bim-checking-plans-for-org
  {:description "Returns the BIM checking plans configurations that are relevant for the authority admin.
  Also returns the list of operations and the current checking plan configuration for the organization."
   :parameters  [organizationId]
   :permissions [{:required [:organization/admin]}]
   :pre-checks  [bim/validate-bim-organization]}
  [_]
  (let [organization (org/get-organization organizationId)]
    (ok :bim-checking-plans (into [] (bim-cp/organization-plans organization))
        :operations (:selected-operations organization)
        :default-plans (:bim-checking-plans organization))))

(defcommand update-bim-checking-plan-for-operation
  {:description      "Allows the authority admin to change which BIM checking plans are selected by default for operations"
   :parameters       [organizationId operation plan-id enabled]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :operation      ssc/NonBlankStr
                       :plan-id        ssc/NonBlankStr
                       :enabled        sc/Bool}]
   :permissions      [{:required [:organization/admin]}]
   :pre-checks       [bim/validate-bim-organization]}
  [_]
  (if (or (not enabled)
          (-> (mongo/by-id :bim-checking-plans plan-id [:enabled]) :enabled))
    (do (->> {(util/kw-path :bim-checking-plans operation) plan-id}
             (hash-map (if enabled $push $pull))
             (org/update-organization organizationId))
        (ok))
    (fail :error.bim-checking-plan-disabled)))
