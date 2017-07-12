(ns lupapalvelu.ely-test
  (:require [clojure.test :refer [is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [lupapalvelu.action :as action]
            [lupapalvelu.mock.organization :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.generators.application :as app-gen]
            [lupapalvelu.generators.organization :as org-gen]
            [lupapalvelu.generators.user :as user-gen]
            [lupapalvelu.user :as usr]))


(def ely-app-data-gen
  (gen/let [org-ids (gen/set org-gen/org-id-gen {:num-elements 10
                                                 :max-tries 20})
            orgs  (gen/vector (gen/hash-map :id (gen/elements org-ids)) 10)
            user (user-gen/user-with-org-auth-gen orgs)
            random-org-id  (gen/elements org-ids)
            application (app-gen/application-gen user)]
    {:orgs        orgs
     :application (assoc application :organization random-org-id)
     :user        user}))


(defspec ely-statement-types
  3
  (prop/for-all [data ely-app-data-gen]
    (let [{:keys [orgs application user]} data
          action-skeleton {:user user
                           :application application
                           :data {:id (:id application)}}
          ely-action (action/build-action "ely-statement-types" action-skeleton)]
      (when (usr/authority? user) (println "user is authority" (:orgAuthz user)))
      (with-mocked-orgs orgs
        (let [res (action/validate ely-action)]
          (cond
            (empty? (:orgAuthz user)) (is (fail? res))
            (contains? #{:canceled :draft :open} (keyword (:state application))) (is (= (:text res) "error.command-illegal-state"))
            (and (usr/authority? user)
                 (usr/user-is-authority-in-organization? user (name (:organization application)))) (is (ok? res))
            :else (is (fail? res))))))))
