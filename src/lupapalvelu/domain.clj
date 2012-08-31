(ns lupapalvelu.domain)

(defn get-party-role-in-application
  "Gets the party role in application. In case there are many roles for the party, the first one is returned."
  [party application]
  (if-let [partyId (:id party)]
    (:role (first (filter #(= (:partyId %) partyId) (:roles application))))))