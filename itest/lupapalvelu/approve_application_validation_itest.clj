(ns lupapalvelu.approve-application-validation-itest
  (:require [lupapalvelu.fixture.approve-application-validation :refer [app-id
                                                                        purkaminen-doc-id
                                                                        hakija-doc-id]]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-fixture "approve-application-validation")

(fact "Sonja sets the bulletin operation description"
  (command sonja :update-app-bulletin-op-description :id app-id
           :description "Nulla ornare tempor porta. Phasellus sed tempus urna.")
  => ok?)

(fact "Application can be approved successfully"
  (command sonja :approve-application :id app-id
           :lang "fi") => ok?)

(fact "Complement needed"
  (command sonja :request-for-complement :id app-id) => ok?)

(fact "Wrong date for poistumanAjankohta"
  (command erkki :update-doc
           :id app-id
           :doc purkaminen-doc-id
           :updates [["poistumanAjankohta" "2019"]]) => fail?)

(fact "Bad Y-tunnus for applicant"
  (command erkki :update-doc
           :id app-id
           :doc hakija-doc-id
           :updates [["yritys.liikeJaYhteisoTunnus" "bad"]])
  => ok?)

(fact "Invalid Y-tunnus is found in KuntaGML validation"
  (command sonja :approve-application :id app-id
           :lang "fi")
  => {:ok      false
      :text    "error.integration.create-message"
      :details "cvc-pattern-valid: Value 'bad' is not facet-valid with respect to pattern '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][-][0-9]' for type '#AnonType_liikeJaYhteisotunnusYritysType'."})

(fact "Fix the Y-tunnus and the application can be approved again"
  (command erkki :update-doc
           :id app-id
           :doc hakija-doc-id
           :updates [["yritys.liikeJaYhteisoTunnus" "7208863-8"]])
  => ok?
  (command sonja :approve-application :id app-id
           :lang "fi") => ok?)
