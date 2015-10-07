(ns lupapalvelu.permit-test
  (:require [lupapalvelu.permit :refer :all]
            [lupapalvelu.attachment :as attachment]
            [midje.sweet :refer :all]]))


(fact "validators"
  (fact "is-not"
    ((validate-permit-type-is-not YA) irrelevant {:permitType "YA"}) => (contains {:ok false :text "error.invalid-permit-type"})
    ((validate-permit-type-is-not R)  irrelevant {:permitType "YA"}) => nil
    ((validate-permit-type-is-not YA) irrelevant nil)                => (contains {:ok false :text "error.invalid-application-parameter"}))
  (fact "is"
    ((validate-permit-type-is R)  irrelevant {:permitType "YA"}) => (contains {:ok false :text "error.invalid-permit-type"})
    ((validate-permit-type-is YA) irrelevant {:permitType "YA"}) => nil
    ((validate-permit-type-is YA) irrelevant nil)                => (contains {:ok false :text "error.invalid-application-parameter"})))

(fact "permit-type"
  (permit-type {:permitType "R"}) => "R"
  (permit-type {})                => (throws AssertionError))

(fact "permit-subtypes"
  (permit-subtypes "P") => [:poikkeamislupa :suunnittelutarveratkaisu]
  (permit-subtypes P) => [:poikkeamislupa :suunnittelutarveratkaisu]
  (permit-subtypes "R") => []
  (permit-subtypes R) => []
  (permit-subtypes nil) => [])

(facts "get-sftp-directory"
  (fact "R" (get-sftp-directory "R") => "/rakennus")
  (fact ":R" (get-sftp-directory :R) => "/rakennus"))

(fact "All permits have attachments defined"
  (doseq [permit (keys (permit-types))]
    (fact {:midje/description permit}
      (get attachment/attachment-types-by-permit-type (keyword permit)) =not=> empty?)))
