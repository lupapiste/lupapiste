(ns lupapalvelu.pate.pate-bulletin-itest
  (:require [midje.sweet :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))

(def verdict-date 1542547500000)
(def julkipano-date 1542633900000)

(facts "Pate bulletin"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id
             :description "This is description from approve application tab") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                          :id app-id
                                          :template-id "ba7aff3e5266a1d9c1581666")
        {verdict-draft :verdict
         references :references} (query sonja :pate-verdict
                                        :id app-id
                                        :verdict-id verdict-id)]

    (fact "Bulletin description in draft is from template"
      (get-in verdict-draft [:data :bulletin-op-description])
        => "Pate bulletin description")

    (facts "Fill verdict data and publish"
      (fill-verdict sonja app-id verdict-id
                    :verdict-code "hyvaksytty"
                    :verdict-text "Verdict given"
                    :verdict-date verdict-date
                    :julkipano julkipano-date
                    :bulletin-op-description "Changed pate bulletin description")
      (fact "Add plans"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:plans]
                 :value (->> references :plans (map :id))) => no-errors?)
      (add-verdict-condition sonja app-id verdict-id "Condition 1")
      (add-verdict-condition sonja app-id verdict-id "Condition 2")
      (add-verdict-condition sonja app-id verdict-id "Condition 3"))

    (fact "Publish PATE verdict"
      (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

  (facts "Bulletin is created"
    (let [bulletin (:bulletin (query pena :bulletin :bulletinId (str app-id "_" verdict-id)))]

      (fact "Bulletin description is from pate verdict"
        (:bulletinOpDescription bulletin) => "Changed pate bulletin description")

      (fact "Appeal period is correct"
        (:appealPeriodStartsAt bulletin) => julkipano-date
        (:appealPeriodEndsAt bulletin) => (-> julkipano-date c/from-long (t/plus (t/days 14)) c/to-long))

      (fact "Bulletin have data from verdict"
        (let [lupamaaraykset (-> bulletin :verdicts first :paatokset first :lupamaaraykset)]
          (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava työnjohtaja"
          (:vaaditutKatselmukset lupamaaraykset) => [{:tarkastuksenTaiKatselmuksenNimi "Katselmus"}
                                                     {:tarkastuksenTaiKatselmuksenNimi "Loppukatselmus"}
                                                     {:tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}]
          (:maaraykset lupamaaraykset) => [{:sisalto "Condition 1"}
                                           {:sisalto "Condition 2"}
                                           {:sisalto "Condition 3"}]
          (:vaaditutErityissuunnitelmat lupamaaraykset) => ["ErityisSuunnitelmat" "Suunnitelmat"]))))))

(facts "Pate legacy verdict bulletin"

  (fact "Turn Pate off"
    (toggle-pate "753-R" false) => true?)

  (let [{legacy-app-id :id} (create-and-submit-application pena
                                                    :operation :pientalo
                                                    :propertyId sipoo-property-id)]

    (facts "Approve application"
      (command sonja :update-app-bulletin-op-description :id legacy-app-id
               :description "This is description from approve application tab") => ok?
      (command sonja :approve-application :id legacy-app-id :lang "fi") => ok?)

    (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft :id legacy-app-id)
          edit                 (partial edit-verdict sonja legacy-app-id verdict-id)]

      (facts "Fill legacy verdict"
        (fill-verdict sonja legacy-app-id verdict-id
                      :kuntalupatunnus "123-456"
                      :verdict-code "1"
                      :verdict-text "Verdict given"
                      :anto (timestamp "20.11.2018")
                      :lainvoimainen (timestamp "20.11.20118")
                      :julkipano (timestamp "20.11.2018")
                      :bulletin-op-description "This is pate legacy bulletin description")

        (add-legacy-review sonja legacy-app-id verdict-id "First review" :paikan-merkitseminen)

        (fact "Add condition"
          (let [condition-id (-> (edit :add-condition true) :changes flatten second)]
            (edit [:conditions condition-id :name] "Strict condition")
            => (contains {:filled true})))

        (fact "Add foreman"
          (let [foreman-id (-> (edit :add-foreman true) :changes flatten second)]
            (edit [:foremen foreman-id :role] "Some random foreman")
            => (contains {:filled true})))

        (fact "Publish legacy verdict"
          (command sonja :publish-legacy-verdict :id legacy-app-id :verdict-id verdict-id) => ok?))

      (facts "Bulletin is created"
        (let [bulletin (:bulletin (query pena :bulletin :bulletinId (str legacy-app-id "_" verdict-id)))]

          (fact "Bulletin description is from pate verdict"
            (:bulletinOpDescription bulletin) => "This is pate legacy bulletin description")

          (fact "Appeal period is correct"
            (:appealPeriodStartsAt bulletin) => (timestamp "20.11.2018")
            (:appealPeriodEndsAt bulletin) => (-> (timestamp "20.11.2018") c/from-long (t/plus (t/days 14)) c/to-long))

          (fact "Bulletin have data from verdict"
            (let [lupamaaraykset (-> bulletin :verdicts first :paatokset first :lupamaaraykset)]
              (:vaaditutTyonjohtajat lupamaaraykset) => "Some random foreman"
              (:vaaditutKatselmukset lupamaaraykset) => [{:tarkastuksenTaiKatselmuksenNimi "First review"}]
              (:maaraykset lupamaaraykset) => [{:sisalto "Strict condition"}])))))))