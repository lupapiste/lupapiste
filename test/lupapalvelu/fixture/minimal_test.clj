(ns lupapalvelu.fixture.minimal-test
  (:require [midje.sweet :refer :all]
            [midje.emission.plugins.util :as emit]
            [schema.core :as sc]
            [lupapalvelu.fixture.minimal :as fm]
            [lupapalvelu.user :as usr]
            [lupapalvelu.organization :as org]
            [lupapalvelu.company :as company]))

(fact "users in minimal fixture must match user schema"
  (doseq [u fm/users]
    (emit/emit-one-line (str "Checking user id: " (:id u)))
    (sc/check usr/User u) => nil))

(fact "organizations in minimal fixture must match organization schema"
  (doseq [o fm/organizations]
    (emit/emit-one-line (str "Checking organization id: " (:id o)))
    (sc/check org/Organization o) => nil))

(fact "companies in minimal fixture must match company schema"
  (doseq [co fm/companies]
    (emit/emit-one-line (str "Checking company id: " (:id co)))
    (sc/check company/Company co) => nil))
