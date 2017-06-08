(ns lupapalvelu.ya-digging-permit
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.copy-application :as copy-app]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.domain :as domain]
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

(defn- copy-document [doc-name sijoitus-app digging-app organization]
  (-> (domain/get-document-by-name sijoitus-app doc-name)
      (copy-app/preprocess-document digging-app organization nil)))

(defn new-digging-permit [{:keys [address propertyId propertyIdSource] [x y] :location :as sijoitus-application}
                          user created digging-operation organization]
  {:pre [(digging-permit-can-be-created? sijoitus-application)
         (digging-permit-operation? digging-operation)]}
  (let [digging-app (app/do-create-application {:data {:address          address
                                                       :operation        digging-operation
                                                       :propertyId       propertyId
                                                       :propertyIdSource propertyIdSource
                                                       :x                x
                                                       :y                y
                                                       :infoRequest      false
                                                       :messages         []}
                                                :user user
                                                :created created})]
    (update digging-app :documents
            (partial map (fn [document]
                           (if (contains? #{"yleiset-alueet-maksaja" "hakija-ya"}
                                          (doc-tools/doc-name document))
                             (copy-document (doc-tools/doc-name document)
                                            sijoitus-application
                                            digging-app
                                            organization)
                               document))))))
