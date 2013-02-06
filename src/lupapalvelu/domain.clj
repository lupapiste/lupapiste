(ns lupapalvelu.domain)

(defn role-in-application [user-id {roles :roles}]
  (some (fn [[role {id :id}]]
          (if (= id user-id) role))
        roles))

(defn get-document-by-id
  "returns first document from application with the document-id"
  [{documents :documents} document-id]
  (first (filter #(= document-id (:id %)) documents)))

(defn get-document-by-name
  "returns first document from application by name"
  [{documents :documents} name]
  (first (filter #(= name (get-in % [:schema :info :name])) documents)))

(defn invited? [{invites :invites} email]
  (or (some #(= email (-> % :user :username)) invites) false))
