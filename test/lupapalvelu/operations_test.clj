(ns lupapalvelu.operations-test
  (:use [lupapalvelu.operations]
        [midje.sweet]))

(fact "validate-is-not-public-area?"
  ((validate-permit-type-is-not :YA) irrelevant {:permitType "YA"}) => (contains {:ok false})
  ((validate-permit-type-is-not :R)  irrelevant {:permitType "YA"}) => nil
  (validate-permit-type-is-not-ya    irrelevant {:permitType "RA"}) => nil
  (validate-permit-type-is-not-ya    irrelevant {:permitType "YA"}) => (contains {:ok false})
  (validate-permit-type-is-not-ya    irrelevant {:permitType :YA})  => (contains {:ok false}))
