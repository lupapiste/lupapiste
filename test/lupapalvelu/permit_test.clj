(ns lupapalvelu.permit-test
  (:use [lupapalvelu.permit]
        [midje.sweet]))

(fact "validators"
  (fact "is-not"
    ((validate-permit-type-is-not YA) irrelevant {:permitType "YA"}) => (contains {:ok false})
    ((validate-permit-type-is-not R)  irrelevant {:permitType "YA"}) => nil)
  (fact "is"
    ((validate-permit-type-is R) irrelevant {:permitType "YA"}) => (contains {:ok false})
    ((validate-permit-type-is YA)  irrelevant {:permitType "YA"}) => nil))

(fact "permit-type"
  (permit-type {:permitType "R"}) => "R"
  (permit-type {})                => (throws AssertionError))
