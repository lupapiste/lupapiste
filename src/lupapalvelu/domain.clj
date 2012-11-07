(ns lupapalvelu.domain)

(defn role-in-application [user-id {roles :roles}]
  (some (fn [[role {id :id}]]
          (if (= id user-id) role))
        roles))

(defn get-document
  "returns first document from application with the document-id"
  [{documents :documents} document-id]
  (first (filter #(= document-id (:id %)) documents)))
