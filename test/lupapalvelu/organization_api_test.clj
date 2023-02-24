(ns lupapalvelu.organization-api-test
  (:require
    [lupapalvelu.organization-api :refer :all]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [sade.util :as util]))

(testable-privates lupapalvelu.organization-api
                   validate-handler-role-in-organization validate-handler-role-not-general
                   check-bulletins-enabled automatic-construction-started-supported
                   organization-attachments)

(facts validate-handler-role-in-organization
  (fact "role id found"
    (validate-handler-role-in-organization
      {:data               {:organizationId ..org-id.. :roleId ..role-id..}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil)

  (fact "role id not found in organization"
    (:ok (validate-handler-role-in-organization
           {:data               {:organizationId ..org-id.. :roleId ..role-id..}
            :user               ..user..
            :user-organizations [{:id            ..org-id..
                                  :handler-roles [{:id   ..another-role-id..
                                                   :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}))
    => false)

  (fact "no mathing authAdmin organization"
    (validate-handler-role-in-organization
      {:data               {:organizationId ..org-id.. :roleId nil}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil)

  (fact "no role id in data"
    (validate-handler-role-in-organization
      {:data               {:organizationId ..org-id.. :roleId nil}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil))

(facts validate-handler-role-not-general
  (fact "non general role id found"
    (validate-handler-role-not-general
      {:data               {:organizationId ..org-id.. :roleId ..role-id..}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil)

  (fact "general role id found"
    (:ok (validate-handler-role-not-general
           {:data               {:organizationId ..org-id.. :roleId ..role-id..}
            :user               ..user..
            :user-organizations [{:id            ..org-id..
                                  :handler-roles [{:id      ..role-id..
                                                   :name    {:fi "kasittelija" :sv "handlaggare" :en "handler"}
                                                   :general true}]}]})) => false)

  (fact "role id not found in organization"
    (validate-handler-role-not-general
      {:data               {:organizationId ..org-id.. :roleId ..role-id..}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..another-role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil)

  (fact "no matching authAdmin organization"
    (validate-handler-role-not-general
      {:data               {:organizationId ..org-id.. :roleId nil}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil)

  (fact "no role id in data"
    (validate-handler-role-not-general
      {:data               {:organizationId ..org-id.. :roleId nil}
       :user               ..user..
       :user-organizations [{:id            ..org-id..
                             :handler-roles [{:id   ..role-id..
                                              :name {:fi "kasittelija" :sv "handlaggare" :en "handler"}}]}]}) => nil))

(defn make-org [id scopes]
  {:id id :scope (map (fn [[mu pt bulletins?]]
                        {:municipality mu
                         :permitType   pt
                         :bulletins    {:enabled bulletins?}})
                      scopes)})

(facts "check-bulletins-enabled"
  (let [org1  (make-org "org1" [["1" "R" true]
                                ["1" "P" false]])
        org2  (make-org "org2" [["2" "R" false]
                                ["2" "P" true]])
        org3  (make-org "org3" [["3" "R" true]])
        org4  (make-org "org4" [["4" "YL" false]
                                ["4" "VVVL" false]])
        error {:ok false :text "error.bulletins-not-enabled-for-scope"}]
    (fact "No data"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]}) => nil)
    (fact "Permit type and municipality match: enabled"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "1"
                                                     :permitType   "R"}})
      => nil
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "2"
                                                     :permitType   "P"}})
      => nil)
    (fact "Permit type and municipality match: not enabled"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "1"
                                                     :permitType   "P"}})
      => error
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "2"
                                                     :permitType   "R"}})
      => error)
    (fact "Organization id matches: enabled"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:organizationId "org1"}})
      => nil
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:organizationId "org3"}})
      => nil)
    (fact "Organization id matches: not enabled"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:organizationId "org4"}})
      => error)
    (fact "Permit type and municipality do not match"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "1"
                                                     :permitType   "P"}})
      => error
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "2"
                                                     :permitType   "BAD"}})
      => error
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality "123"
                                                     :permitType   "R"}})
      => error)
    (fact "Organization id does not match"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:organizationId "org4"}})
      => error
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:organizationId "org123"}})
      => error)
    (fact "Parameters mismatch"
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality   "1"
                                                     :permitType     "R"
                                                     :organizationId "org2"}})
      => error
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality   "1"
                                                     :permitType     "P"
                                                     :organizationId "org2"}})
      => error
      (check-bulletins-enabled {:user-organizations [org1 org2 org3]
                                :data               {:municipality   "1"
                                                     :permitType     "KT"
                                                     :organizationId "org1"}})
      => error)))

(defn make-scope [permit-type pate]
  (merge {:permitType permit-type}
         (when (boolean? pate)
           {:pate {:enabled pate}})))

(defn make-command [scopes]
  {:user-organizations [{:id    "ORG"
                         :scope (map (partial apply make-scope) scopes)}]
   :data               {:organizationId "ORG"}})

(def not-supported  {:ok   false
                     :text "error.no-pate-construction-started-scope"})

(facts "automatic-construction-started-supported"
  (automatic-construction-started-supported (make-command [["R" true] ["P" false]])) => nil
  (automatic-construction-started-supported (make-command [["R" false] ["P" true]])) => nil
  (automatic-construction-started-supported (make-command [])) => not-supported
  (automatic-construction-started-supported (make-command [["YA" true] ["P" false]]))
  => not-supported
  (automatic-construction-started-supported (make-command [["YA" true] ["P" true]])) => nil
  (automatic-construction-started-supported (make-command [["YA" true] ["A" true]]))
  => not-supported)

(defn att-type? [permit-type op type-group type-id]
  (fn [m]
    (some->> (get-in m [permit-type op])
             (util/find-first #(= (first %) type-group))
             second
             (some #{type-id}))))
