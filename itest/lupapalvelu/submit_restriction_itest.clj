(ns lupapalvelu.fixture.submit-restriction-itest
  (:require [midje.sweet :refer :all]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.submit-restriction :refer [default-app-id]]))

(apply-remote-fixture "submit-restriction")

(facts "submit restriction"
  (facts "enable submit restriction in approve-invite"
    (fact "pena invites Esimerkki company"
      (command pena :company-invite :id default-app-id :company-id "esimerkki") => ok?)

    (fact "application is submittable by pena"
      (query pena :application-submittable :id default-app-id) => ok?)

    (fact "application is submittable by authority"
      (query sonja :application-submittable :id default-app-id) => ok?)

    (fact "erkki applies submit restriction to others by approving invite with flag"
      (command erkki :approve-invite :id default-app-id :invite-type :company :apply-submit-restriction true) => ok?)

    (fact "application is not submittable for pena"
      (query pena :application-submittable :id default-app-id) => {:ok false
                                                                   :text "error.cannot-submit-application"
                                                                   :errors [{:ok false
                                                                             :restriction "application/submit"
                                                                             :text "error.permissions-restricted-by-another-user"
                                                                             :user {:id "esimerkki"
                                                                                    :name "Esimerkki Oy"
                                                                                    :type "company"
                                                                                    :username "7208863-8"}}]})

    (fact "erkki sees submit restriction is enabled for other auths"
      (query erkki :submit-restriction-enabled-for-other-auths :id default-app-id) => ok?)

    (fact "submit restriction enabled for others fails for pena"
      (query pena :submit-restriction-enabled-for-other-auths :id default-app-id) => (partial expected-failure? :error.auth-restriction-not-enabled))

    (fact "submit restriction enabled for others fails for sonja"
      (query pena :submit-restriction-enabled-for-other-auths :id default-app-id) => (partial expected-failure? :error.auth-restriction-not-enabled))

    (fact "application is still submittable by authority"
      (query sonja :application-submittable :id default-app-id) => ok?)

    (fact "application is also still submittable by restrictor company user"
      (query erkki :application-submittable :id default-app-id) => ok?)

    (fact "pena cannot submit application"
      (command pena :submit-application :id default-app-id) => unauthorized?))

  (facts "Esimerkki is removed from auths"

    #_(fact "pena cannot remove Esimerkki from authorized users"
        (command pena :remove-auth :id default-app-id :username "7208863-8") => unauthorized?)

    (fact "authority removes Esimerkki"
      (command sonja :remove-auth :id default-app-id :username "7208863-8") => ok?)

    (fact "application is again submittable for pena"
      (query pena :application-submittable :id default-app-id) => ok?))

  (facts "enable submit restriction by toggling it after approving invite"

    (fact "pena invites Esimerkki company again"
      (command pena :company-invite :id default-app-id :company-id "esimerkki") => ok?)

    (fact "erkki is not authorized to restrict submissions since invite is not accepted"
      (query erkki :authorized-to-apply-submit-restriction-to-other-auths :id default-app-id) => (partial expected-failure? :error.company-has-not-accepted-invite))

    (fact "erkki is authorized to restrict submissions outside application context"
      (query erkki :authorized-to-apply-submit-restriction-to-other-auths) => ok?)

    (fact "submit restriction enabled for others fails for erkki since invitation is not accepted"
      (query erkki :submit-restriction-enabled-for-other-auths :id default-app-id) => unauthorized?)

    (fact "erkki approves invite without submit restriction"
      (command erkki :approve-invite :id default-app-id :invite-type :company) => ok?)

    (fact "submit restriction enabled for others fails for erkki"
      (query erkki :submit-restriction-enabled-for-other-auths :id default-app-id) => (partial expected-failure? :error.auth-restriction-not-enabled))

    (fact "erkki is now authorized to restrict submissions inside application context"
      (query erkki :authorized-to-apply-submit-restriction-to-other-auths :id default-app-id) => ok?)

    (fact "application is still submittable for pena"
      (query pena :application-submittable :id default-app-id) => ok?)

    (fact "pena is not allowed to toggle submit restriction"
      (command pena :toggle-submit-restriction-for-other-auths :id default-app-id :apply-submit-restriction true) => (partial expected-failure? :error.not-allowed-to-apply-submit-restriction))

    (fact "authority is not allowed to toggle submit restriction"
      (command sonja :toggle-submit-restriction-for-other-auths :id default-app-id :apply-submit-restriction true) => (partial expected-failure? :error.not-allowed-to-apply-submit-restriction))

    (fact "erkki applies submit restriction"
      (command erkki :toggle-submit-restriction-for-other-auths :id default-app-id :apply-submit-restriction true) => ok?)

    (fact "application is not submittable for pena anymore"
      (query pena :application-submittable :id default-app-id) => {:ok false
                                                                   :text "error.cannot-submit-application"
                                                                   :errors [{:ok false
                                                                             :restriction "application/submit"
                                                                             :text "error.permissions-restricted-by-another-user"
                                                                             :user {:id "esimerkki"
                                                                                    :name "Esimerkki Oy"
                                                                                    :type "company"
                                                                                    :username "7208863-8"}}]}))

  (facts "disable submit restriction"
    (fact "erkki disables submit restriction"
      (command erkki :toggle-submit-restriction-for-other-auths :id default-app-id :apply-submit-restriction false) => ok?)

    (fact "application is submittable for pena again"
      (query pena :application-submittable :id default-app-id) => ok?))

  (facts "invite non-submit-restrictor company"
    (fact "pena invites Solita"
      (command pena :company-invite :id default-app-id :company-id "solita") => ok?)

    (fact "solita cannot apply submit restriction to others by approving invite with flag"
      (command kaino :approve-invite :id default-app-id :invite-type :company :apply-submit-restriction true) => (partial expected-failure? :error.not-allowed-to-apply-submit-restriction))

    (fact "solita approves invite without flag"
      (command kaino :approve-invite :id default-app-id :invite-type :company) => ok?)

    (fact "kaino is not authorized to restrict submissions inside application context"
      (query kaino :authorized-to-apply-submit-restriction-to-other-auths :id default-app-id) => (partial expected-failure? :error.not-allowed-to-apply-submit-restriction))

    (fact "... or outside application context"
      (query kaino :authorized-to-apply-submit-restriction-to-other-auths) => (partial expected-failure? :error.not-allowed-to-apply-submit-restriction))

    (fact "kaino is not allowed to toggle submit restriction"
      (command kaino :toggle-submit-restriction-for-other-auths :id default-app-id :apply-submit-restriction true) => (partial expected-failure? :error.not-allowed-to-apply-submit-restriction))))
