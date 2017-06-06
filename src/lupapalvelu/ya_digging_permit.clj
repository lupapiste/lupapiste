(ns lupapalvelu.ya-digging-permit
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.operations :as op]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.ya :as ya]))

(defn digging-permit-can-be-created? [application]
  (and (ya/sijoittaminen? application)
       (app/verdict-given? application)))

(defn- digging-permit-operation? [operation-name]
  (let [{:keys [permit-type subtypes]} (op/get-operation-metadata operation-name)]
    (and (= permit/YA permit-type)
         (boolean (some #(= :tyolupa %) subtypes)))))

(defn organization-digging-operations
  "Return an operation tree containing only the digging operations
  selected by the given organization"
  [organization]
  (op/selected-operations-for-organizations [organization]
                                            digging-permit-operation?))

(defn new-digging-permit [{:keys [address propertyId propertyIdSource] [x y] :location :as sijoitus-application}
                          user created digging-operation]
  {:pre [(digging-permit-can-be-created? sijoitus-application)]}
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
