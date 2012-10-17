(ns lupapalvelu.domain)

(defn role-in-application [user-id {roles :roles}]
  (some (fn [[role {id :id}]]
          (if (= id user-id) role))
        roles))
