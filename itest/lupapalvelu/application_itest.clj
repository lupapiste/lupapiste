(ns lupapalvelu.application-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.minimal]))

(defn- not-empty? [m] (not (empty? m)))

(fact "creating application without message"
  (fixture/apply-fixture "minimal")
  (let [resp            (command pena :create-application :permitType "buildingPermit" :x 444444 :y 6666666 :address "foo 42, bar" :municipality "753")
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)]
    application => (contains {:id application-id
                              :state "draft"
                              :location {:x 444444 :y 6666666}
                              :permitType "buildingPermit"})
    (count (:comments application)) => 0
    (first (:auth application)) => (contains
                                     {:firstName "Pena"
                                      :lastName "Panaani"
                                      :type "owner"
                                      :role "owner"})
    (:allowedAttahmentTypes application) => not-empty?))

(fact "creating application message"
  (fixture/apply-fixture "minimal")
  (let [resp            (command pena :create-application :permitType "buildingPermit" :x 444444 :y 6666666 :address "foo 42, bar" :municipality "753" :message "hello")
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)
        state           (:state application)
        comments        (:comments application)]
    state => "draft"
    (count comments) => 1
    (-> comments first :text) => "hello"))

(comment
  (fact "Application in Sipoo has two possible authorities: Sonja and Ronja."
  (fixture/apply-fixture "minimal")
  (let [application-id (:id (command pena :create-application :permitType "buildingPermit" :x 444444 :y 6666666 :address "foo 42, bar" :municipality "Sipoo" :message "hello"))
        authorities  (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))]
    (count authorities) => 2)))

(comment
  (fact "Assign application to an authority"
  (fixture/apply-fixture "minimal")
      (let [application-id (:id (command pena :create-application :permitType "buildingPermit" :x 444444 :y 6666666 :address "foo 42, bar" :municipality "Sipoo" :message "hello"))
            ;; add a comment to change state to open
            comment (command pena :add-comment :id application-id :text "hello" :target "application")
            application (:application (query sonja :application :id application-id))
            roles-before-assignation (:roles application)
            authorities (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))
            authority (first authorities)
            resp (command sonja :assign-application :id application-id :assigneeId (:id authority))
            assigned-app (:application (query sonja :application :id application-id))
            roles-after-assignation (:roles assigned-app)]
        (count roles-before-assignation) => 1
        (count roles-after-assignation) => 2)))

(fact "Assign application to an authority and then to no-one"
  (fixture/apply-fixture "minimal")
      (let [application-id (:id (command pena :create-application :permitType "buildingPermit" :x 444444 :y 6666666 :address "foo 42, bar" :municipality "Sipoo" :message "hello"))
            ;; add a comment change set state to open
            comment (command pena :add-comment :id application-id :text "hello" :target "application")
            application (:application (query sonja :application :id application-id))
            roles-before-assignation (:roles application)
            authorities (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))
            authority (first authorities)
            resp (command sonja :assign-application :id application-id :assigneeId (:id authority))
            resp (command sonja :assign-application :id application-id :assigneeId nil)
            assigned-app (:application (query sonja :application :id application-id))
            roles-in-the-end (:roles assigned-app)]
        (count roles-before-assignation) => 1
        (count roles-in-the-end) => 1))
