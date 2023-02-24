(ns lupapalvelu.task-util-test
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.task-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(def sonja-user (find-user-from-minimal-by-apikey sonja))

(testable-privates lupapalvelu.task-util
                   supported-application? authorized? good-reviewer?)

(facts task-is-review?
  (task-is-review? nil) => falsey
  (task-is-review? {}) => falsey
  (task-is-review? {:schema-info {:name "task-vaadittu-tyonjohtaja"}}) => falsey
  (task-is-review? {:schema-info {:name "task-katselmus"}}) => truthy
  (task-is-review? {:schema-info {:name "task-katselmus-ya"}}) => truthy)

(facts supported-application?
  (supported-application? nil) => falsey
  (supported-application? {}) => falsey
  (supported-application? {:state      "verdictGiven"
                           :permitType "R"
                           :tasks      [{:schema-info {:name "task-katselmus"}}]})
  => truthy
  (supported-application? {:state      "agreementPrepared"
                           :permitType "YA"
                           :tasks      [{:schema-info {:name "task-lupamaarays"}}
                                        {:schema-info {:name "task-katselmus-ya"}}]})
  => truthy
  (supported-application? {:state      "canceled"
                           :permitType "R"
                           :tasks      [{:schema-info {:name "task-katselmus"}}]})
  => falsey
  (supported-application? {:state "verdictGiven"
                           :permitType "P"
                           :tasks [{:schema-info {:name "task-katselmus"}}]})
  => falsey
  (supported-application? {:state "verdictGiven"
                           :permitType "R"
                           :tasks [{:schema-info {:name "task-lupamaarays"}}]})
  => falsey
  (supported-application? {:state "verdictGiven"
                           :permitType "R"
                           :tasks []})
  => falsey
  (supported-application? {:state "verdictGiven"
                           :permitType "R"})
  => falsey)

(facts authorized?
  (authorized? {:user sonja-user :application {:organization "753-R"}}) => truthy
  (authorized? {:user sonja-user :application {:organization "123-R"}}) => falsey)

(defn make-officer [code name]
  {:code code :name name})

(defn make-reviewer
  ([code name]
   (assoc-in {} [:data :katselmus :pitaja :value] (if code
                                                    {:code code :name name}
                                                    name)))
  ([name]
   (make-reviewer nil name)))

(facts good-reviewer?
  (good-reviewer? nil nil) => falsey
  (good-reviewer? {} {}) => falsey
  (good-reviewer? {} (make-reviewer "   ")) => falsey
  (good-reviewer? {} (make-reviewer "")) => falsey
  (good-reviewer? {} (make-reviewer nil)) => falsey
  (good-reviewer? {} (make-reviewer "F" "Foo")) => falsey
  (good-reviewer? {:review-officers-list-enabled true
                   :reviewOfficers               [(make-officer "B" "Bar")]}
                  (make-reviewer "hello")) => falsey
  (good-reviewer? {:review-officers-list-enabled true
                   :reviewOfficers               [(make-officer "B" "Bar")]}
                  (make-reviewer "F" "Foo"))
  => falsey
  (good-reviewer? {:review-officers-list-enabled true
                   :reviewOfficers               [(make-officer "B" "Bar")]}
                  (make-reviewer "Foobar"))
  => falsey
  (good-reviewer? {:review-officers-list-enabled true
                   :reviewOfficers               [(make-officer "B" "Bar")]}
                  (make-reviewer ""))
  => falsey
  (good-reviewer? nil (make-reviewer "Hello")) => truthy
  (good-reviewer? {:review-officers-list-enabled false
                   :reviewOfficers               [(make-officer "B" "Bar")]}
                  (make-reviewer "Foobar"))
  => truthy
  (good-reviewer? {:review-officers-list-enabled true
                   :reviewOfficers               [(make-officer "F" "Foo")
                                                  (make-officer "B" "Bar")]}
                  (make-reviewer "B" "Bar"))
  => truthy
  (good-reviewer? {:review-officers-list-enabled true
                   :reviewOfficers               [(make-officer "F" "Foo")
                                                  (make-officer "B" "Bar")]}
                  (make-reviewer "F" "Only id matters"))
  => truthy)

(defn make-organization [enabled? & officer-defs]
  (-> {:id                           "753-R"
       :review-officers-list-enabled enabled?
       :reviewOfficers               (->> officer-defs
                                          (map #(apply make-officer %))
                                          seq)}
      util/strip-nils
      delay))

(let [task        {:schema-info {:name "task-katselmus"}
                   :state       "requires_user_action"}
      task-string (merge task (make-reviewer "Ines Inspector"))
      task-map    (merge task (make-reviewer "II" "Ines Inspector"))
      application {:permitType   "R"
                   :state        "constructionStarted"
                   :organization "753-R"
                   :tasks        [task-string task-map]}
      command     {:organization (make-organization false)
                   :application  application
                   :user         sonja-user}]
  (facts default-reviewer-value
    (fact "No reviewer, no officer list"
      (default-reviewer-value command task) => {:value "Sonja Sibbo"})
    (fact "Good reviewer, no officer list"
      (default-reviewer-value command task-string) => nil)
    (fact "No reviewer, officer list, Sonja listed"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["S1" "SONJA SIBBO"]
                                                        ["R1" "RONJA SIBBO"]))
                              task)
      => {:value (make-officer "S1" "SONJA SIBBO")}
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["S2" "Sibbo Sonja"]
                                                        ["R2" "Sibbo Ronja"]))
                              task)
      => {:value (make-officer "S2" "Sibbo Sonja")})

    (fact "No reviewer, officer list, close enough"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["L2" "Laskuttaja Laura"]
                                                        ["R2" "Sibbo Ronja"]))
                              task)
      => {:value (make-officer "R2" "Sibbo Ronja")}
      (default-reviewer-value (assoc command :organization
                                     (make-organization true ["R3" "Sibbo Ranja"]))
                              task)
      => {:value (make-officer "R3" "Sibbo Ranja")}
      (default-reviewer-value (assoc command :organization
                                     (make-organization true ["R4" "Raija Sibbo"]))
                              task)
      => {:value (make-officer "R4" "Raija Sibbo")})

    (fact "No reviewer, officer list, no match"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true ["R5" "Raita Sibbo"]))
                              task)
      => nil)

    (fact "String reviewer, officer list, Sonja listed"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["S2" "Sibbo Sonja"]))
                              task-string)
      => {:value (make-officer "S2" "Sibbo Sonja")})

    (fact "String reviewer, officer list, Sonja not listed"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true #_["L2" "Laskuttaja Laura"]))
                              task-string)
      => {:value nil})

    (fact "Map reviewer, officer list, listed"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["II" "INSPECTOR INES"]
                                                        ["S2" "Sibbo Sonja"]))
                              task-map)
      => nil)

    (fact "Map reviewer, officer list, not listed, Sonja listed"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["S2" "Sibbo Sonja"]))
                              task-map)
      => {:value (make-officer "S2" "Sibbo Sonja")})

    (fact "Map reviewer, officer list, not listed, Sonja not listed"
      (default-reviewer-value (assoc command :organization
                                     (make-organization true
                                                        ["L1" "Laura Laskuttaja"]))
                              task-map)
      => {:value nil})

    (fact "Map reviewer, no officer list"
      (default-reviewer-value (assoc command :organization
                                     (make-organization false))
                              task-map)
      => {:value "Sonja Sibbo"}))

  (facts enrich-default-task-reviewer
    (fact "No reviewer, no officer list"
      (enrich-default-task-reviewer command task)
      => (merge task (make-reviewer "Sonja Sibbo")))
    (fact "Good reviewer, no officer list"
      (enrich-default-task-reviewer command task-string) => task-string)
    (fact "Map reviewer, officer list, not listed, Sonja listed"
      (enrich-default-task-reviewer (assoc command :organization
                                           (make-organization true ["S2" "Sibbo Sonja"]))
                                    task-map)
      => (merge task-map (make-reviewer "S2" "Sibbo Sonja")))
    (fact "Map reviewer, officer list, not listed, Sonja not listed"
      (enrich-default-task-reviewer (assoc command :organization
                                           (make-organization true ["L1" "Laura Laskuttaja"]))
                              task-map)
      => (assoc-in task-map [:data :katselmus :pitaja :value] nil)))

  (facts with-review-officers
    (fact "Officers enabled, list not empty"
      (with-review-officers
        (assoc command :organization (make-organization true ["L1" "Laura Laskuttaja"]))
        application)
      => (assoc application :reviewOfficers
                {:enabled true :officers [(make-officer "L1" "Laura Laskuttaja")]}))
    (fact "Officers disabled, list not empty"
      (with-review-officers
        (assoc command :organization (make-organization false ["L1" "Laura Laskuttaja"]))
        application)
      => (assoc application :reviewOfficers {}))
    (fact "Officers enabled, list empty"
      (with-review-officers
        (assoc command :organization (make-organization true))
        application)
      => (assoc application :reviewOfficers {:enabled true})
      (with-review-officers
        (assoc command :organization (delay {:id                           "753-R"
                                             :review-officers-list-enabled true
                                             :reviewOfficers               []}))
        application)
      => (assoc application :reviewOfficers {:enabled true}))
    (facts "Application not supported"
      (with-review-officers
        (assoc command :organization (make-organization true ["L1" "Laura Laskuttaja"]))
        (assoc application :state "draft"))
      => (assoc application :state "draft" :reviewOfficers {})
      (with-review-officers
        (assoc command :organization (make-organization true ["L1" "Laura Laskuttaja"]))
        (assoc application :permitType "VVVL"))
      => (assoc application :permitType "VVVL" :reviewOfficers {}))
    (facts "User not authed"
      (with-review-officers
        (assoc command
               :organization (make-organization true ["L1" "Laura Laskuttaja"])
               :user (find-user-from-minimal-by-apikey pena))
        application)
      => (assoc application :reviewOfficers {}))))
