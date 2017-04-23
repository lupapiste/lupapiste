(ns lupapalvelu.copy-application-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.test-util :refer [walk-dissoc-keys]]
            [lupapalvelu.application :as app]
            [sade.property :as prop]))

(apply-remote-minimal)

(defn copy-application [apikey app-id & {:keys [x y address auth-invites propertyId]}]
  (command sonja :copy-application
           :x (or x 444445.0) :y (or y 6666665.0)
           :address (or address "Testitie 1")
           :auth-invites (or auth-invites [])
           :propertyId (or propertyId "75312312341234")
           :source-application-id app-id))

(facts "copying application"
  (let [pena-user (find-user-from-minimal-by-apikey pena)
        sonja-user (find-user-from-minimal-by-apikey sonja)
        solita-company (company-from-minimal-by-id "solita")
        solita-company-admin (find-user-from-minimal-by-apikey kaino)

        ; Pena creates, submits and invites company
        {app-id :id} (create-and-submit-application pena)
        _ (command pena :company-invite :id app-id :company-id (:_id solita-company)) => ok?

        app (query-application sonja app-id)
        x 444445.0 y 6666665.0
        property-id "75312312341234"
        _ (sent-emails) ; reset sent emails
        {copy-app-id :id} (copy-application sonja app-id
                                   :x x :y y
                                   :address "Testitie 1"
                                   :auth-invites [pena-id (:_id solita-company)]
                                   :propertyId property-id) => ok?
        copy-app (query-application sonja copy-app-id)]

    (fact "primaryOperation is copied, but id is new"
      (dissoc (:primaryOperation copy-app) :id)
      => (dissoc (:primaryOperation app) :id)
      (:id (:primaryOperation copy-app))
      =not=> (:id (:primaryOperation app)))

    (fact "documents are copied, apart from ids"
      (walk-dissoc-keys (:documents copy-app) :id :created :allowedActions)
      => (just (walk-dissoc-keys (:documents app) :id :created  :allowedActions) :in-any-order))

    (fact "the copied app has the provided location and property id"
      (:location copy-app) => {:x x :y y}
      (:propertyId copy-app) => property-id
      (:municipality copy-app) => (prop/municipality-id-by-property-id property-id))

    (fact "Sonja is new owner, Pena (previous owner) is invited as writer"
      (count (:auth copy-app)) => 3
      (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "owner"]
      (-> copy-app :auth (second) ((juxt :id (comp :role :invite)))) => [pena-id "writer"]
      (-> copy-app :auth (get 2) ((juxt :name :type))) => [(:name solita-company) "company"]
      (let [emails (sent-emails)]
        (count emails) => 2

        (-> emails first :body :html) => (contains "Sinut halutaan valtuuttaa kirjoitusoikeudella")
        (-> emails first :to) => (contains "Pena Panaani")

        (-> emails second :body :html) => (contains "Sonja Sibbo haluaa valtuuttaa yrityksenne")
        (-> emails second :to) => (contains (:email solita-company-admin))))

    (fact "Only auths with ids in auth-invites are copied from old app"
          (let [{copy-app-id :id} (copy-application sonja app-id
                                           :auth-invites []) => ok?
                copy-app (query-application sonja copy-app-id)]
            (-> copy-app :auth (first) ((juxt :id :role))) => [(:id sonja-user) "owner"]
            (-> copy-app :auth count) => 1))
    (fact "Copying fails if some of provided auth-invites don't exist in source application"
          (copy-application sonja app-id :auth-invites ["nonexistent" pena-id])
          => (partial expected-failure? "error.nonexistent-auths"))))
