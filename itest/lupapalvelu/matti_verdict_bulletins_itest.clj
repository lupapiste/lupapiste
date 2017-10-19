(ns lupapalvelu.matti-verdict-bulletins-itest
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.matti-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "verdict bulletins for matti-style verdicts"
  (let [{id :id} (init-verdict-template sipoo "r") =not=> nil?]
    (fact "publish verdict template"
      (set-template-draft-values id :bulletinOpDescription "Kerrostalolle lupa")
      (publish-verdict-template sipoo id) => ok?)
    (fact "create a verdict from the template"
      (let [{app-id :id} (create-and-submit-application sonja :operation "kerrostalo-rivitalo"
                                                        :propertyId sipoo-property-id
                                                        :x 406898.625 :y 6684125.375
                                                        :address "Hitantine 108")
            {verdict :verdict} (command sonja :new-matti-verdict-draft :id app-id :template-id id) => ok?]
        (-> verdict :data) => (contains {:bulletinOpDescription "Kerrostalolle lupa"})
        (fact "bulletin appears in search"
          (command sonja :upsert-matti-verdict-bulletin :id app-id :verdict-id (:id verdict)) => ok?
          (let [{data :data ok :ok} (datatables pena :application-bulletins :page 1 :searchText ""
                                                     :municipality nil :state nil :sort nil)]
               ok => true
               (count data) => 1))
        (fact "create a second verdict"
          (let [{verdict2 :verdict} (command sonja :new-matti-verdict-draft :id app-id :template-id id)
                _ (command sonja :edit-matti-verdict :id app-id :verdict-id (:id verdict2)
                                                     :path [:bulletinOpDescription] :value "Toimenpidelupa kaivolle kans")]
            (fact "upsert second bulletin"
              (command sonja :upsert-matti-verdict-bulletin :id app-id :verdict-id (:id verdict2)) => ok?
              (let [{data :data ok :ok} (datatables pena :application-bulletins :page 1 :searchText ""
                                                         :municipality nil :state nil :sort nil)]
                ok => true
                (fact "both verdicts appear in bulletins search as separate items"
                  (count data) => 2
                  (map :id data) => [(str app-id "_" (:id verdict)) (str app-id "_" (:id verdict2))])))))))))