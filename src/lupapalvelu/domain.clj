(ns lupapalvelu.domain)

(defn get-user-role-in-application [user application]
  (if-let [user-id (:id user)]
    (filter #(= (:userId (val %)) user-id) (:roles application))))