(ns lupapalvelu.generators.application
  (:require [clojure.test.check.generators :as gen]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.generators.organization :as org-gen]
            [lupapalvelu.generators.permit :as permit-gen]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]))

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
  [user & {:keys [permit-type-gen org-id-gen]
           :or {permit-type-gen permit-gen/permit-type-gen
                org-id-gen      org-gen/org-id-gen}}]
  (gen/let [permit-type permit-type-gen
            application-auths (application-auths-gen user)
            id (ssg/generator ssc/ObjectIdStr)
            org-id org-id-gen
            state (gen/elements states/all-application-states)]
    (merge domain/application-skeleton
           {:id           id
            :state        state
            :permitType   permit-type
            :organization org-id
            :auths        application-auths})))
