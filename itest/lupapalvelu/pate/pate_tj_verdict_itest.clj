(ns lupapalvelu.pate.pate-tj-verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :as core]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))

(def tj-verdict-date (core/now))

(facts "PATE TJ verdicts"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (facts "Create, submit and approve foreman application"
    (let [{foreman-app-id :id} (command sonja :create-foreman-application :id app-id
                                        :taskId ""
                                        :foremanRole "KVV-ty\u00f6njohtaja"
                                        :foremanEmail "johtaja@mail.com")]
    (command sonja :change-permit-sub-type :id foreman-app-id :permitSubtype "tyonjohtaja-hakemus") => ok?
    (command sonja :submit-application :id foreman-app-id) => ok?
    (command sonja :approve-application :id foreman-app-id :lang "fi") => ok?

    (facts "Create, edit and publish TJ verdict"
      (let [template-id               (->> (:templates pate-fixture/verdic-templates-setting)
                                           (filter #(= "tj" (:category %)))
                                           (first)
                                           :id)
            {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft :id foreman-app-id :template-id template-id)]

        (fact "Set automatic calculation of other dates"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:automatic-verdict-dates] :value true) => no-errors?)
        (fact "Verdict date"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:verdict-date] :value tj-verdict-date) => no-errors?)
        (fact "Verdict code"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:verdict-code] :value "hyvaksytty") => no-errors?)
        (fact "Publish verdict"
          (command sonja :publish-pate-verdict :id foreman-app-id :verdict-id verdict-id) => no-errors?)

        (fact "Applicant cant create verdict"
          (command pena :new-pate-verdict-draft :id foreman-app-id :template-id template-id) => unauthorized?)

      (facts "TJ verdict is published with corrected data"
        (let [application (query-application sonja foreman-app-id)
              tj-verdict  (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))]
          (fact "Category" (:category tj-verdict) => "tj")
          (fact "Verdict code" (get-in tj-verdict [:data :verdict-code]) => "hyvaksytty"
          (fact "Verdict text" (get-in tj-verdict [:data :verdict-text]) => "Paatos annettu\n"
          (fact "Verdict date" (get-in tj-verdict [:data :verdict-date]) => tj-verdict-date
          (fact "Appeal" (get-in tj-verdict [:data :appeal]) => "Ohje muutoksen hakuun.\n"
          (fact "Handler" (get-in tj-verdict [:data :handler]) => "Sonja Sibbo"))))))))))))

