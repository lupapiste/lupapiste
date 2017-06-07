(ns lupapalvelu.ya-digging-permit
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.ya :as ya]
            [sade.core :refer [fail]]))

;;; Helper functions

(defn digging-permit-can-be-created? [application]
  (and (ya/sijoittaminen? application)
       (app/verdict-given? application)))

(defn digging-permit-operation? [operation-name]
  (let [{:keys [permit-type subtypes]} (op/get-operation-metadata operation-name)]
    (and (= permit/YA permit-type)
         (boolean (some #(= :tyolupa %) subtypes)))))

(defn organization-digging-operations
  "Return an operation tree containing only the digging operations
  selected by the given organization"
  [organization]
  (op/selected-operations-for-organizations [organization]
                                            digging-permit-operation?))

;;; Validators

(defn validate-digging-permit-source [{:keys [application]}]
  (when (and application
             (not (digging-permit-can-be-created? application)))
    (fail :error.invalid-digging-permit-source
          {:id (:id application)
           :state (:state application)
           :primaryOperationName (-> application :primaryOperation :name)})))

(defn validate-digging-permit-operation [{{:keys [operation]} :data}]
  (when (and operation
             (not (digging-permit-operation? operation)))
    (fail :error.not-digging-permit-operation
          {:operation operation})))

;;; Creating digging permits

(defn new-digging-permit [{:keys [address propertyId propertyIdSource] [x y] :location :as sijoitus-application}
                          user created digging-operation]
  {:pre [(digging-permit-can-be-created? sijoitus-application)
         (digging-permit-operation? digging-operation)]}
  (app/do-create-application {:data {:address          address
                                     :operation        digging-operation
                                     :propertyId       propertyId
                                     :propertyIdSource propertyIdSource
                                     :x                x
                                     :y                y
                                     :infoRequest      false
                                     :messages         []}
                              :user user
                              :created created}))
