(ns lupapalvelu.singlepage-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.singlepage :refer [compose-resource]]
            [sade.env :as env]))

(facts "compose-resource js without Exceptions"
  (against-background
   (env/feature? :no-minification) => false
   (env/feature? :dummy-ident) => true
   (env/feature? :suomifi-ident) => false)

  (fact "common"
    (compose-resource :js :common) => truthy)

  (fact "welcome"
    (compose-resource :js :welcome) => truthy)

  (fact "applicant"
    (compose-resource :js :applicant) => truthy)

  (fact "authority"
    (compose-resource :js :authority) => truthy)

  (fact "upload"
    (compose-resource :js :upload) => truthy)

  (fact "authority-admin"
    (compose-resource :js :authority-admin) => truthy)

  (fact "oskari"
    (compose-resource :js :oskari) => truthy)

  (fact "oir"
    (compose-resource :js :oir) => truthy)

  (fact "wordpress"
    (compose-resource :js :wordpress) => truthy)

  (fact "neighbor"
    (compose-resource :js :neighbor) => truthy)

  (fact "hashbang"
    (compose-resource :js :hashbang) => truthy))
