(ns lupapalvelu.organization-deactivation-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.util :as util]))

(apply-remote-minimal)

(defn set-hankkeen-kuvaus [apikey {:keys [id documents permitType]} description]
  (let [schema (if (= permitType "R")
                 "hankkeen-kuvaus"
                 "yleiset-alueet-hankkeen-kuvaus-kaivulupa")
        path   (if (= permitType "R")
                 "kuvaus"
                 "kayttotarkoitus")]
    (command apikey :update-doc
            :id id
            :doc (:id (util/find-first (util/fn-> :schema-info :name (= schema)) documents))
            :updates [[path description]])))

(defn set-authority-notice [apikey {app-id :id} notice]
  (command apikey :add-authority-notice
           :id app-id
           :authorityNotice notice))

(defn err [error]
  (partial expected-failure? (name error)))

(let [{app-id :id
       :as app}    (create-and-open-application pena
                                                :propertyId sipoo-property-id
                                                :operation :pientalo)
      {ya-app-id :id
       :as ya-app} (create-and-open-application pena
                                                :propertyId sipoo-property-id
                                                :operation :ya-katulupa-vesi-ja-viemarityot)]
  (fact "Applicant can edit applications"
    (set-hankkeen-kuvaus pena app "Hello") => ok?
    (set-hankkeen-kuvaus pena ya-app "world") => ok?)

  (fact "Authority can set notices"
    (set-authority-notice sonja app "looks") => ok?
    (set-authority-notice sonja ya-app "good") => ok?)

  (fact "Sipoo-R is active"
    (query pena :municipality-active :municipality "753")
    => (contains {:applications (contains "R")
                  :infoRequests (contains "R")}))

  (facts "Deactivate organization"
    (fact "Organization must exist"
      (command admin :toggle-deactivation
               :organizationId "313-D"
               :deactivated true)
      => (err :error.no-such-organization))
    (fact "Deactivated must be boolean"
      (command admin :toggle-deactivation
               :organizationId "753-R"
               :deactivated "hello") => fail?
      (command admin :toggle-deactivation
               :organizationId "753-R"
               :deactivated nil) => fail?)
    (fact "Deactivate 753-R"
      (command admin :toggle-deactivation
               :organizationId "753-R"
               :deactivated true) => ok?)
    (fact "Sipoo-R is no longer active"
      (query pena :municipality-active :municipality "753")
      => (contains {:applications ["YA" "A"]
                    :infoRequests ["YA" "A"]}))
    (fact "R application cannot be edited"
      (set-hankkeen-kuvaus pena app "no can do") => fail?
      (set-authority-notice sonja app "nope") => fail?)
    (fact "YA application can be edited"
      (set-hankkeen-kuvaus pena ya-app "yes can do") => ok?
      (set-authority-notice sonja ya-app "yup") => ok?)
    (fact "P application cannot be created"
      (create-app pena
                  :propertyId sipoo-property-id
                  :operation :poikkeamis) => fail?)
    (fact "R inforequest cannot be created"
      (create-app pena
                  :propertyId sipoo-property-id
                  :operation :kerrostalo-rivitalo
                  :infoRequest true) => fail?))
  (fact "Activate organization"
    (command admin :toggle-deactivation
             :organizationId "753-R"
             :deactivated false) => ok?
    (fact "R application can again be edited"
      (set-hankkeen-kuvaus pena app "works again") => ok?
      (set-authority-notice sonja app "great") => ok?)
    (fact "Application creation is still disabled"
      (create-app pena
                  :propertyId sipoo-property-id
                  :operation :poikkeamis) => fail?)))
