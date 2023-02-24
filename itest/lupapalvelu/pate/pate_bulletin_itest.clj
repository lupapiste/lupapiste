(ns lupapalvelu.pate.pate-bulletin-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))
(def template-id "ba7aff3e5266a1d9c1581666")


(def verdict-date (now))
(def julkipano-date (now))
(def julkipano-end-date (-> julkipano-date (date/plus :days 15) date/timestamp))
(def anto-date          (-> julkipano-date (date/plus :day) date/timestamp))
(def lainvoimainen-date (-> julkipano-date (date/plus :days 16) date/timestamp))
(def muutoksenhaku-date (-> julkipano-date (date/plus :days 30) date/timestamp))

(defn toggle-bulletins [enabled?]
  (fact {:midje/description (str "Bulletins enabled: " enabled?)}
      (command admin :update-organization
               :permitType "R"
               :municipality "753"
               :bulletinsEnabled enabled?) => ok?))

(facts "Pate bulletin"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id
             :description "This is description from approve application tab") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (fact "No application bulletins yet"
    (query sonja :verdict-bulletins :id app-id)
    => fail?)

  (let [{verdict-id :verdict-id}    (command sonja :new-pate-verdict-draft
                                             :id app-id
                                             :template-id template-id)
        bulletin-id                 (str app-id "_" verdict-id)
        {verdict-draft :verdict
         references    :references} (query sonja :pate-verdict
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
    (let [bulletin (:bulletin (query pena :bulletin :bulletinId bulletin-id))]

      (fact "Bulletin description is from pate verdict"
        (:bulletinOpDescription bulletin) => "Changed pate bulletin description")

      (fact "Markup? is true"
        (:markup? bulletin) => true)

      (fact "Appeal period is correct"
        (:appealPeriodStartsAt bulletin) => julkipano-date
        (:appealPeriodEndsAt bulletin) => julkipano-end-date)

      (fact "Bulletin have data from verdict"
        (let [lupamaaraykset (-> bulletin :verdicts first :paatokset first :lupamaaraykset)]
          (:vaaditutTyonjohtajat lupamaaraykset) => "Vastaava ty\u00f6njohtaja"
          (:vaaditutKatselmukset lupamaaraykset) => [{:tarkastuksenTaiKatselmuksenNimi "Katselmus"}
                                                     {:tarkastuksenTaiKatselmuksenNimi "Loppukatselmus"}
                                                     {:tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}]
          (:maaraykset lupamaaraykset) => [{:sisalto "Condition 1"}
                                           {:sisalto "Condition 2"}
                                           {:sisalto "Condition 3"}]
          (:vaaditutErityissuunnitelmat lupamaaraykset) => ["Suunnitelmat" "ErityisSuunnitelmat"]))))

  (fact "Bulletin is listed for the application"
    (:bulletins (query sonja :verdict-bulletins :id app-id))
    => [{:end-date   (-> (date/zoned-date-time julkipano-end-date)
                         date/end-of-day
                         date/timestamp)
         :id         bulletin-id
         :section    "1"
         :start-date (-> (date/zoned-date-time julkipano-date)
                         date/with-time
                         date/timestamp)}])

  (facts "Bulletin information pdf"
    (pdf-response?
      (raw sonja :bulletin-report-pdf :id app-id
           :bulletinId bulletin-id
           :lang "fi")
      {"Content-Disposition" (format "filename=\"Julkipanon tiedot %s %s.pdf\""
                                     app-id (date/finnish-date (now) :zero-pad))})))

  (facts "Muutoksenhaku field in verdict"
    (fact "Update and publish the verdict template"
      (command sipoo :save-verdict-template-draft-value
               :organizationId "753-R"
               :template-id template-id
               :path [:verdict-dates]
               :value ["julkipano" "muutoksenhaku"]) => ok?
      (command sipoo :publish-verdict-template
               :organizationId "753-R"
               :template-id template-id) => ok?)
    (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                            :id app-id
                                            :template-id template-id)]

     (facts "Fill verdict data including muutoksenhaku and publish"
       (fill-verdict sonja app-id verdict-id
                     :verdict-code "hyvaksytty"
                     :verdict-text "Verdict given"
                     :verdict-date verdict-date
                     :julkipano julkipano-date
                     :muutoksenhaku muutoksenhaku-date
                     :bulletin-op-description "Changed pate bulletin description"))

     (fact "Publish PATE verdict"
       (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

     (facts "Bulletin is created and the appeal period ends at muutoksenhaku date"
       (:bulletin (query pena :bulletin :bulletinId (str app-id "_" verdict-id)))
       => (contains {:appealPeriodStartsAt julkipano-date
                     :appealPeriodEndsAt   muutoksenhaku-date}))

     (facts "Create replacement verdict"
       (let [{new-verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                   :id app-id
                                                   :template-id template-id
                                                   :replacement-id verdict-id)]
         (fill-verdict sonja app-id new-verdict-id
                       :verdict-code "hyvaksytty"
                       :verdict-text "Verdict given"
                       :verdict-date verdict-date
                       :julkipano julkipano-date
                       :muutoksenhaku muutoksenhaku-date)
         (command sonja :publish-pate-verdict :id app-id :verdict-id new-verdict-id)
         => no-errors?
         (fact "Old bulletin removed from bulletins"
               (query pena :bulletin :bulletinId (str app-id "_" verdict-id))
               => (err :error.bulletin.not-found)))))))



(facts "Pate verdict without bulletin"
  (let [app-id (create-app-id mikko
                              :propertyId sipoo-property-id
                              :operation  :kerrostalo-rivitalo)]
    (toggle-bulletins false)
    (facts "Submit and approve application"
      (command mikko :submit-application :id app-id) => ok?
      (command sonja :update-app-bulletin-op-description :id app-id
               :description "Cannot be set") => fail?
      (command sonja :approve-application :id app-id :lang "fi") => ok?)
    (facts "Give Pate verdict"
      (let [{:keys [verdict-id]} (command sonja :new-pate-verdict-draft
                                          :id app-id
                                          :template-id template-id)]
        (fill-verdict sonja app-id verdict-id
                      :verdict-code "hyvaksytty"
                      :verdict-text "Verdict given"
                      :verdict-date verdict-date
                      :julkipano julkipano-date
                      :muutoksenhaku muutoksenhaku-date)
        (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id)
        => no-errors?
        (fact "No bulletin created"
          (query mikko :bulletin :bulletinId (str app-id "_" verdict-id))
          => (err :error.bulletin.not-found))))))

(toggle-bulletins true)

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
                      :anto anto-date
                      :lainvoimainen lainvoimainen-date
                      :julkipano julkipano-date
                      :bulletin-op-description "This is pate legacy bulletin description")

        (add-legacy-review sonja legacy-app-id verdict-id "First review" :paikan-merkitseminen)

        (fact "Add condition"
          (let [condition-id (-> (edit :add-condition true) :changes flatten second)]
            (edit [:conditions condition-id :name] "Strict condition")
            => (contains {:filled true})))

        (fact "Add foreman"
          (let [foreman-id (-> (edit :add-foreman true) :changes flatten second)]
            (edit [:foremen foreman-id :role] "KVV-ty\u00f6njohtaja")
            => (contains {:filled true})))

        (fact "Publish legacy verdict"
          (command sonja :publish-legacy-verdict :id legacy-app-id :verdict-id verdict-id) => ok?))

      (facts "Bulletin is created"
        (let [bulletin (:bulletin (query pena :bulletin :bulletinId (str legacy-app-id "_" verdict-id)))]

          (fact "Bulletin description is from pate verdict"
            (:bulletinOpDescription bulletin) => "This is pate legacy bulletin description")

          (fact "Appeal period is correct"
            (:appealPeriodStartsAt bulletin) => julkipano-date
            (:appealPeriodEndsAt bulletin) => (-> anto-date (date/plus :days 14) date/timestamp))

          (fact "Bulletin have data from verdict"
            (let [lupamaaraykset (-> bulletin :verdicts first :paatokset first :lupamaaraykset)]
              (:vaaditutTyonjohtajat lupamaaraykset) => "KVV-ty\u00f6njohtaja"
              (:vaaditutKatselmukset lupamaaraykset) => [{:tarkastuksenTaiKatselmuksenNimi "First review"}]
              (:maaraykset lupamaaraykset) => [{:sisalto "Strict condition"}])))))))

(facts "Legacy verdict without bulletin"
  (let [app-id (create-app-id mikko
                              :propertyId sipoo-property-id
                              :operation  :pientalo)]
    (toggle-bulletins false)
    (facts "Submit and approve application"
      (command mikko :submit-application :id app-id) => ok?
      (command sonja :update-app-bulletin-op-description :id app-id
               :description "Cannot be set") => fail?
      (command sonja :approve-application :id app-id :lang "fi") => ok?)
    (facts "Give legacy verdict"
      (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft
                                          :id app-id
                                          :template-id template-id)]
        (fill-verdict sonja app-id verdict-id
                      :kuntalupatunnus "123-456"
                      :verdict-code "1"
                      :verdict-text "Verdict given"
                      :anto anto-date
                      :lainvoimainen lainvoimainen-date
                      :julkipano julkipano-date)
        (command sonja :publish-legacy-verdict :id app-id :verdict-id verdict-id)
        => no-errors?
        (fact "No bulletin created"
          (query mikko :bulletin :bulletinId (str app-id "_" verdict-id))
          => (err :error.bulletin.not-found))))))
