(ns lupapalvelu.application-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.document.schemas :as schemas]))

(fact "creating application without message"
  (apply-remote-minimal)
  (let [resp            (command pena :create-application
                                 :permitType "buildingPermit"
                                 :operation "asuinrakennus"
                                 :propertyId "1"
                                 :x 444444 :y 6666666
                                 :address "foo 42, bar"
                                 :municipality "753")
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
    (:allowedAttachmentTypes application) => (complement empty?)))

(defn- create-app []
  (command pena :create-application
           :permitType "buildingPermit"
           :operation "asuinrakennus"
           :propertyId "1"
           :x 444444 :y 6666666
           :address "foo 42, bar"
           :municipality "753"
           :message "hello"))

(fact "creating application message"
  (let [resp            (create-app)
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)
        hakija          (some (fn [doc] (if (= (-> doc :schema :info :name) "hakija") doc)) (:documents application))]
    (:state application) => "draft"
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"
    (-> hakija :body :henkilo :henkilotiedot) => (contains {:firstName "Pena" :lastName "Panaani" :role "applicant"})))

(fact "Application in Sipoo has two possible authorities: Sonja and Ronja."
  (apply-remote-minimal)
  (let [application-id (:id (create-app))
        authorities  (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))]
    (count authorities) => 2))

(fact "Assign application to an authority"
  (apply-remote-minimal)
  (let [application-id (:id (create-app))
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
    (count roles-after-assignation) => 2))

(fact "Assign application to an authority and then to no-one"
  (apply-remote-minimal)
  (let [application-id (:id (create-app))
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

(comment
  ; Should rewrite this as a couple of unit tests
  (fact "Assert that proper documents are created"
    (against-background (operations/operations :foo) => {:schema "foo" :required ["a" "b"] :attachments []}
                        (operations/operations :bar) => {:schema "bar" :required ["b" "c"] :attachments []}
                        (schemas/schemas "hakija") => {:info {:name "hakija"}, :body []}
                        (schemas/schemas "foo")    => {:info {:name "foo"}, :body []}
                        (schemas/schemas "a")      => {:info {:name "a"}, :body []}
                        (schemas/schemas "b")      => {:info {:name "b"}, :body []}
                        (schemas/schemas "bar")    => {:info {:name "bar"}, :body []}
                        (schemas/schemas "c")      => {:info {:name "c"}, :body []})
    (let [id (:id (command pena :create-application
                           :operation "foo"
                           :permitType "buildingPermit"
                           :propertyId "1"
                           :x 444444 :y 6666666
                           :address "foo 42, bar"
                           :municipality "753"
                           :message "hello"))
          app (:application (query pena :application :id id))
          docs (:documents app)
          find-by-schema? (fn [docs schema-name] (some (fn [doc] (if (= schema-name (-> doc :schema :info :name)) doc)) docs))]
      (count docs) => 4 ; foo, a, b and "hakija".
      (find-by-schema? docs "foo") => truthy
      (find-by-schema? docs "a") => truthy
      (find-by-schema? docs "b") => truthy
      (-> (find-by-schema? docs "foo") :schema :info) => (contains {:op "foo" :removable true}) 
      ; Add operation:
      (command pena :add-operation :id id :operation "bar")
      (let [app (:application (query pena :application :id id))
            docs (:documents app)]
        (count docs) => 6 ; foo, a, b and "hakija" + bar and c
        (find-by-schema? docs "bar") => truthy
        (find-by-schema? docs "c") => truthy
        (-> (find-by-schema? docs "bar") :schema :info) => (contains {:op "bar" :removable true})))))
