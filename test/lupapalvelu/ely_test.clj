(ns lupapalvelu.ely-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [midje.sweet :refer :all]
            [schema.core :as sc]
            [sade.core :refer :all]
            [lupapalvelu.action :as action]
            [lupapalvelu.mock.organization :refer :all]
            [lupapalvelu.generators.application :as app-gen]
            [lupapalvelu.generators.organization :as org-gen]
            [lupapalvelu.generators.user :as user-gen]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [lupapalvelu.statement-api]                     ; for (action/build-action "ely-statement-types" ...)
            [lupapalvelu.user :as usr]))

(def org-map-gen
  (gen/hash-map :id org-gen/org-id-gen))

(def ely-app-data-gen
  (gen/let [orgs (gen/vector-distinct-by :id org-map-gen {:min-elements 1})
            user (user-gen/user-with-org-auth-gen (gen/elements (map (comp keyword :id) orgs)))
            application (app-gen/application-gen user :org-id-gen (gen/elements (map (comp keyword :id) orgs)))]
    {:orgs        orgs
     :application application
     :user        user}))

(def ely-query-prop
  (prop/for-all [{:keys [orgs application user]} ely-app-data-gen]
    (with-mocked-orgs orgs
      (let [action-skeleton {:user        user
                             :application application
                             :data        {:id (:id application)}}
            ely-action (action/build-action "ely-statement-types" action-skeleton)
            res (action/validate ely-action)]
        (cond
          (usr/oir-authority? user) (= :error.unauthorized (keyword (:text res)))
          (or (not (usr/authority? user))
              (empty? (:orgAuthz user))) (fail? res)
          (contains? #{:canceled :draft} (keyword (:state application))) (= (:text res) "error.command-illegal-state")
          (and (usr/authority? user)
               (usr/user-is-authority-in-organization? user (name (:organization application)))) (ok? res)
          :else (fail? res))))))

(sc/with-fn-validation
  (fact :qc "ely-statement-types-query"
    ; writing test as midje + quick-check seems to work better in IDEA compared to defspec (crashes when tests shrink)
    (tc/quick-check 150 ely-query-prop :max-size 30) => passing-quick-check))
