(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mml.yhteystiedot :as yht]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [sade.core :refer [ok]]
            [sade.validators :as v]))

(defn- owners-of [system property-id]
  (->> (yht/get-owners system property-id)
       (mapv (fn [owner] (assoc owner :propertyId property-id)))))

(defquery owners
  {:parameters           [propertyIds]
   :input-validators     [(partial action/vector-parameter-of :propertyIds v/kiinteistotunnus?)]
   :allow-impersonation? false
   :user-roles           #{:applicant :authority}
   :pre-checks           [yht/yhteystiedot-configured]}
  [{:keys [system]}]
  (ok :owners (into [] (flatten (map (partial owners-of system) propertyIds))))) ; pmap?

(defquery application-property-owners
  {:parameters      [:id]
   :states          states/all-states
   :user-roles      #{:authority}
   :org-authz-roles #{:authority :approver}
   :pre-checks      [permit/is-not-archiving-project
                     yht/yhteystiedot-configured]}
  [{{property-id :propertyId docs :documents} :application system :system}]
  (let [extra-properties   (domain/get-documents-by-name docs "secondary-kiinteistot")
        extra-property-ids (map (comp :value :kiinteistoTunnus :kiinteisto :data) extra-properties)
        all-property-ids   (set (cons property-id extra-property-ids))]
    (ok :owners (->> all-property-ids
                     (map (partial owners-of system))  ; pmap?
                     flatten ; mapcat?
                     (into [])))))
