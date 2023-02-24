(ns lupapalvelu.fixture.minimal-test
  (:require [midje.sweet :refer :all]
            [schema.core :as sc]
            [lupapalvelu.fixture.minimal :as fm]
            [lupapalvelu.user :as usr]
            [lupapalvelu.organization :as org]
            [lupapalvelu.company :as company]))

(facts "users in minimal fixture must match user schema"
  (doseq [u fm/users]
    (fact {:midje/description (str "id: " (:id u))}
      (sc/check usr/User u) => nil)))

(facts "organizations in minimal fixture must match organization schema"
  (doseq [o fm/organizations]
    (fact {:midje/description (str "id: " (:id o))}
      (sc/check org/Organization o) => nil)))

(facts "companies in minimal fixture must match company schema"
  (doseq [co fm/companies]
    (fact {:midje/description (str "id: " (:id co))}
      (sc/check company/Company co) => nil)))

