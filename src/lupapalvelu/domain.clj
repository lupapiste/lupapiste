(ns lupapalvelu.domain)

(defn role-in-application [user-id {roles :roles}]
  (some (fn [[role {id :userId}]]
          (if (= id user-id) role))
        roles))
