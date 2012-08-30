(ns lupapalvelu.domain)

(defn get-party-role-in-application [party application]
  "Gets the party role in application. In case there are many roles for the party, the first one is returned."
  (if-let [partyId (:id party)]
    (:role (first (filter #(= (:partyId %) partyId) (:roles application))))))
