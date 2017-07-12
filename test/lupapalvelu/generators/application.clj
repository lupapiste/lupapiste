(ns lupapalvelu.generators.application
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.generators.permit :as permit-gen]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.user :as usr]
            [sade.schema-generators :as ssg]))

(def application-role-gen (gen/elements roles/all-authz-roles))

(defn single-auth-gen [& {:keys [user-id-gen application-role-gen]
                          :or   {user-id-gen          (ssg/generator usr/Id)
                                 application-role-gen application-role-gen}}]
  (gen/let [user-id user-id-gen
            role application-role-gen]
           {:role role
            :id user-id}))

(defn application-auths-gen [user]
  (gen/let [auths (gen/vector (single-auth-gen))
            give-user-auths? gen/boolean
            users-auths (single-auth-gen :user-id-gen (gen/return (:id user)))]
           (if give-user-auths?
             (conj auths users-auths)
             auths)))

(defn application-gen
  [orgs user & {:keys [permit-type-gen] :or {permit-type-gen permit-gen/permit-type-gen}}]
  (let [org-ids (map (comp keyword :id) orgs)]
    (gen/let [permit-type permit-type-gen
              org-id (gen/elements org-ids)
              application-auths (application-auths-gen user)]
      (merge lupapalvelu.domain/application-skeleton
             {:permitType permit-type
              :organization org-id
              :auths application-auths}))))
