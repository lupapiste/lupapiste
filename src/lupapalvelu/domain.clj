(ns lupapalvelu.domain)

(defn get-user-role-in-application
  "Gets the user role in application. In case there are many roles for the user, the first one is returned."
  [user application]
  (if-let [userId (:id user)]
    (:role (first (filter #(= (:userId %) userId) (:roles application))))))