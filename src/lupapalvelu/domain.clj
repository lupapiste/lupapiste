(ns lupapalvelu.domain)

(defn role-in-application [user-id application]
  (some (fn [[role {id :userId}]] (if (= id user-id) role))
        (:roles application)))
