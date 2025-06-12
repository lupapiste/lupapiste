(ns lupapalvelu.permit-test
  (:require [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.i18n :refer [has-term?]]
            [lupapalvelu.permit :refer :all]
            [midje.sweet :refer :all]))

(fact "validators"
  (fact "is-not"
    ((validate-permit-type-is-not YA) {:application {:permitType "YA"}}) => (contains {:ok false :text "error.invalid-permit-type"})
    ((validate-permit-type-is-not R)  {:application {:permitType "YA"}}) => nil
    ((validate-permit-type-is-not YA) nil)                               => (contains {:ok false :text "error.invalid-application-parameter"}))
  (fact "is"
    ((validate-permit-type-is R)  {:application {:permitType "YA"}}) => (contains {:ok false :text "error.invalid-permit-type"})
    ((validate-permit-type-is YA) {:application {:permitType "YA"}}) => nil
    ((validate-permit-type-is YA) nil)                               => (contains {:ok false :text "error.invalid-application-parameter"})))

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

(facts "Every permit type has AddOperations localization"
       (doseq [p (keys (permit-types))
               :let [s (str "help." p ".AddOperations")]]
         (fact {:midje/description s} (has-term? "fi" s) => true)))

(fact "All permits have attachments defined"
  (doseq [permit (keys (permit-types))]
    (fact {:midje/description permit}
      (get att-type/attachment-types-by-permit-type (keyword permit)) =not=> empty?)))
