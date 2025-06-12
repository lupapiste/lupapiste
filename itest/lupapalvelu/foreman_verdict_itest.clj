(ns lupapalvelu.foreman-verdict-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set]]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.env :as env]))

(def template-id "ba7aff3e5266a1d9c1581666")

(def verdict-date (now))
(def julkipano-date (now))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-mock-laundry)
  (env/with-feature-value :new-foreman-invite true
    (with-local-actions
      (facts "Pate new foreman verdict"
        (let [app-id (create-app-id pena
                                    :propertyId sipoo-property-id
                                    :operation "kerrostalo-rivitalo")
              _      (mongo/update-by-id :applications app-id {$set {:submitted (date/timestamp "30.12.2024")}})]

          (fact "Submit application"
            (command pena :submit-application :id app-id) => ok?)

          (let [{:keys [verdict-id]} (command sonja :new-pate-verdict-draft
                                              :id app-id
                                              :template-id template-id)]

            (command sipoo :set-foreman-submit-setting :organizationId "753-R" :path {:notification {:give-verdict true}} :enabled true) => ok?

            (facts "Fill verdict data and publish"
              (fill-verdict sonja app-id verdict-id
                            :verdict-code "hyvaksytty"
                            :verdict-text "Verdict given"
                            :verdict-date verdict-date
                            :giver-first-name "Pena"
                            :giver-last-name "Päättäjä"
                            :julkipano julkipano-date
                            :bulletin-op-description "Changed pate bulletin description"))

            (fact "Publish PATE verdict"
              (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

            (let [app                 (query-application sonja app-id)
                  doc                 (first (domain/get-documents-by-name app :tyonjohtaja-v3))
                  foreman-app-id      (:id app)
                  foreman-doc-id      (:id doc)
                  foreman-template-id "5b0689f8cb66507187fbc18f"]

              (fact "TJ doc state is not updated"
                (:state doc) => nil)

              (fact "Can not create a verdict if state is nil"
                (command sonja :new-pate-foreman-verdict-draft
                         :id app-id
                         :template-id foreman-template-id
                         :foreman-doc-id (:id doc)) => {:ok false :text "error.foreman.wrong-state"})

              (fact "Fill in foreman document and declare it")
              (command pena :update-doc
                       :id foreman-app-id
                       :doc foreman-doc-id
                       :updates [["yhteystiedot.email"                              "pena@example.com"]
                                 ["yhteystiedot.puhelin"                            "123456789"]
                                 ["henkilotiedot.sukunimi"                          "Panaani"]
                                 ["henkilotiedot.etunimi"                           "Pena"]
                                 ["osoite.katu"                                     "Panaanitie 42"]
                                 ["osoite.postinumero"                              "00400"]
                                 ["osoite.postitoimipaikannimi"                     "Helsinki"]
                                 ["patevyysvaatimusluokka"                          "A"]
                                 ["henkilotiedot.hetu"                              "010171-957M"]
                                 ["vastattavatTyotehtavat.sisapuolinenKvvTyo"       true]
                                 ["patevyys-tyonjohtaja.valmistumisvuosi"           "1942"]
                                 ["patevyys-tyonjohtaja.valvottavienKohteidenMaara" "10"]
                                 ["patevyys-tyonjohtaja.kokemusvuodet"              "10"]
                                 ["patevyys-tyonjohtaja.koulutusvalinta"            "insinööri"]]) => ok?

              (command pena :foreman-consent :id foreman-app-id :foreman-doc-id foreman-doc-id :consent true :with-lang "fi") => ok?
              (command sonja :declare-foreman :id foreman-app-id :foreman-doc-id foreman-doc-id :with-lang "fi") => ok?

              (let [foreman-verdict (command sonja :new-pate-foreman-verdict-draft
                                             :id app-id
                                             :template-id foreman-template-id
                                             :foreman-doc-id (:id doc))
                    verdict-id      (:verdict-id foreman-verdict)]
                (fact "Can create a verdict after document is filled in"
                  foreman-verdict => ok?)
                (fact "Set automatic calculation of other dates"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:automatic-verdict-dates] :value true) => no-errors?)
                (fact "Verdict date"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:verdict-date] :value (now)) => no-errors?)
                (fact "Verdict code"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:verdict-code] :value "hyvaksytty") => no-errors?)
                (fact "Verdict giver"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:giver] :value "päättäjä") => no-errors?)
                (fact "Start day for responsibilities"
                  (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                           :path [:responsibilities-start-date] :value (now)) => no-errors?)
                (fact "Publish foreman-v3 verdict"
                  (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

                (let [app (query-application sonja app-id)
                      doc (first (domain/get-documents-by-name app :tyonjohtaja-v3))]

                  (fact "TJ doc state is updated once the verdict is published"
                    (:state doc) => "acknowledged"))))))))))
