(ns lupapalvelu.permit-test
  (:require [lupapalvelu.permit :refer :all]
            [midje.sweet :refer :all]))

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

(fact "validate-permit-has-subtypes"
  (validate-permit-has-subtypes nil {:permitType P}) => nil
  (validate-permit-has-subtypes nil {:permitType R}) => {:ok false, :text "error.permit-has-no-subtypes"}
  (validate-permit-has-subtypes nil nil) => {:ok false, :text "error.permit-has-no-subtypes"})

(fact "permit-subtypes"
  (permit-subtypes "P") => [:poikkeamislupa :suunnittelutarveratkaisu]
  (permit-subtypes P) => [:poikkeamislupa :suunnittelutarveratkaisu]
  (permit-subtypes "R") => []
  (permit-subtypes R) => []
  (permit-subtypes nil) => [])

(fact "is-valid-subtype"
  (is-valid-subtype  :poikkeamislupa {:permitType "P"}) => nil
  (is-valid-subtype  :suunnittelutarveratkaisu {:permitType "P"}) => nil
  (is-valid-subtype  :olematon {:permitType "P"}) => {:ok false, :text "error.permit-has-no-such-subtype"}
  (is-valid-subtype  :poikkeamislupa {:permitType "R"}) => {:ok false, :text "error.permit-has-no-such-subtype"})

(facts "get-sftp-directory"
  (fact "R" (get-sftp-directory "R") => "/rakennus")
  (fact ":R" (get-sftp-directory :R) => "/rakennus"))
