(ns lupapalvelu.pate.pate-p-verdict-itest
  (:require [clojure.data.xml :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :as core]
            [sade.date :as date]
            [sade.util :as util]
            [sade.xml :as xml]))

(def p-verdict-date (core/now))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "PATE P verdicts"

    (fact "Enable pate sftp"
      (command admin :update-organization
               :permitType "P"
               :municipality "753"
               :pateEnabled true
               :pateSftp true) => ok?)

    (facts "Create, submit and approve application"
      (let [{app-id :id} (create-and-open-application pena
                                                      :operation "poikkeamis"
                                                      :propertyId sipoo-property-id)]
        (command sonja :submit-application :id app-id) => ok?
        (command sonja :approve-application :id app-id :lang "fi") => ok?

        (facts "Create, edit and publish P verdict"
          (let [template-id              (->> pate-fixture/verdict-templates-r
                                              (util/find-by-key :category "p")
                                              :id)
                {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)
                edit-verdict             (fn [path value]
                                           (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                                                    :path path :value value))]
            ;; Required fields
            (fact "Set automatic calculation of other dates"
              (edit-verdict [:automatic-verdict-dates] true) => no-errors?)
            (fact "Verdict date"
              (edit-verdict [:verdict-date] p-verdict-date) => no-errors?)
            (fact "Verdict code"
              (edit-verdict [:verdict-code] "hyvaksytty") => no-errors?)
            (fact "Verdict giver"
              (edit-verdict [:giver] "päättäjä") => no-errors?)
            (fact "Verdict section"
              (edit-verdict [:verdict-section] "42") => no-errors?)
            (fact "Verdict flag"
              (edit-verdict [:verdict-flag] true) => no-errors?)
            ;; Optional fields
            (fact "Rationale"
              (edit-verdict [:rationale] "Perusteluteksti") => no-errors?)
            (fact "Neighbors"
              (edit-verdict [:neighbors] "Naapuriteksti") => no-errors?)
            (fact "Publish verdict"
              (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

            (verdict-pdf-queue-test sonja {:app-id            app-id
                                           :verdict-id        verdict-id
                                           :state             "verdictGiven"
                                           :skip-attachments? true})
            (check-verdict-date sonja app-id p-verdict-date)

            (facts "Xml is created and is valid"
              (let [application          (query-application sonja app-id)
                    filename-starts-with (:id application)
                    xml                  (get-valid-krysp-xml application filename-starts-with)]

                (fact "Query returns right application"
                  (:id application) => app-id)

                (fact "XML has right application id"
                  (xml/get-text xml [:LupaTunnus :tunnus]) => (:id application))

                (fact "Kayttotapaus is 'Uusi päätös'"
                  (xml/get-text xml [:kayttotapaus]) => "Uusi päätös")

                (facts "Paatostieto is correct"
                  (let [paatostieto (xml/select xml [:paatostieto])
                        paatos      (xml/select1 paatostieto [:Paatos])
                        poytakirja  (xml/select paatos [:poytakirja])]
                    (fact "Paatostieto contains only one Paatos"
                      (count paatostieto) => 1)

                    (facts "poytakirja is correct"
                      (fact "paatoskoodi" (xml/get-text poytakirja [:paatoskoodi]) => "hyväksytty")
                      (fact "paatoskoodi" (xml/get-text poytakirja [:paatoksentekija]) => "päättäjä")
                      (fact "paatospvm"   (xml/get-text poytakirja [:paatospvm]) => (date/xml-date p-verdict-date)))

                    (fact "verdict avain-arvo field(s) are correct"
                      (->> (xml/select xml [:AvainArvoPari])
                           (map (fn [{[avain arvo] :content}]
                                  [(-> avain :content first)
                                   (-> arvo :content first)])))
                      => (just [["paatosId" verdict-id]
                                ["paatoksenPerustelut" "Perusteluteksti"]
                                ["paatoksenKasittelija" "Sonja Sibbo"]
                                ["naapurienKuuleminen" "Naapuriteksti"]
                                ["paatoksenRatkaisija" "päättäjä"]]))))))

            (facts "P verdict is published with corrected data"
              (let [application        (query-application sonja app-id)
                    p-verdict          (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))
                    verdict-attachment (first (filter #(= verdict-id (-> % :source :id)) (:attachments application)))]
                (fact "Category" (:category p-verdict) => "p")
                (fact "verdict has the correct attachment id"
                  (get-in p-verdict [:published :attachment-id]) => (:id verdict-attachment))
                (fact "verdict attachment has sent timestamp"
                  (:sent verdict-attachment) => pos?)
                (fact "Verdict code" (get-in p-verdict [:data :verdict-code]) => "hyvaksytty")
                (fact "Verdict date" (get-in p-verdict [:data :verdict-date]) => p-verdict-date)
                (fact "Handler" (get-in p-verdict [:data :handler]) => "Sonja Sibbo")))))))))
