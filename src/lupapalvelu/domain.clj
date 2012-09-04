(ns lupapalvelu.domain)

(defn role-in-application [user-id application]
  (and user-id (first (map key (filter #(= (:userId (val %)) user-id) (:roles application))))))