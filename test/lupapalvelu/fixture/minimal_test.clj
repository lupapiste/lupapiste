(ns lupapalvelu.fixture.minimal-test
  (:require [midje.sweet :refer :all]
            [midje.emission.plugins.util :as emit]
            [schema.core :as sc]
            [lupapalvelu.fixture.minimal :as fm]
            [lupapalvelu.user :as usr]))

(fact "users in minimal fixture must match user schema"
  (doseq [u fm/users]
    (emit/emit-one-line (str "Checking user id: " (:id u)))
    (sc/check usr/User u) => nil))
