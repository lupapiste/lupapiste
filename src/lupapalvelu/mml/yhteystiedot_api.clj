(ns lupapalvelu.mml.yhteystiedot-api
  (:require [lupapalvelu.mml.yhteystiedot :as yht]
            [sade.core :refer [ok now]]
            [sade.validators :as v]
            [lupapalvelu.action :refer [defquery disallow-impersonation] :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.permit :as permit]))

(defn- owners-of [property-id]
  (->> (yht/get-owners property-id)
       (map (fn [owner] (assoc owner :propertyId property-id)))))

(defquery owners
  {:parameters [propertyIds]
   :input-validators [(partial action/vector-parameter-of :propertyIds v/kiinteistotunnus?)]
   :pre-checks [disallow-impersonation]
   :user-roles #{:applicant :authority}}
  [_]
  (ok :owners (flatten (map owners-of propertyIds)))) ; pmap?

(defquery application-property-owners
  {:parameters      [:id]
   :states          states/all-states
   :user-roles      #{:authority}
   :org-authz-roles #{:authority :approver}
   :pre-checks      [permit/is-not-archiving-project]}
  [{{property-id :propertyId docs :documents} :application}]
  (let [extra-properties   (domain/get-documents-by-name docs "secondary-kiinteistot")
        extra-property-ids (map (comp :value :kiinteistoTunnus :kiinteisto :data) extra-properties)
        all-property-ids   (set (cons property-id extra-property-ids))]
    (ok :owners (flatten (map owners-of all-property-ids))))) ; pmap?
