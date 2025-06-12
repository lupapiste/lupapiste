(ns lupapalvelu.pate.pate-bulletin-itest
  (:require [lupapalvelu.application-bulletins-itest-util :as bulletins-itest-util]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.pate.date :as pate-date]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(def template-id
  "From Pate verdict fixture."
  "ba7aff3e5266a1d9c1581666")

(def verdict-date (now))
(def julkipano-date (now))
(def julkipano-end-date (-> julkipano-date (date/plus :days 15) date/timestamp))
(def anto-date (-> julkipano-date (date/plus :day) date/timestamp))
(def lainvoimainen-date (-> julkipano-date (date/plus :days 16) date/timestamp))
(def muutoksenhaku-date (-> julkipano-date (date/plus :days 30) date/timestamp))

(defn =ignoring-holidays
  "Compare dates but take into account that dates might move due to holidays, weekends"
  [target-date actual-date]
  (= (-> target-date
         pate-date/forward-to-work-day
         date/timestamp)
     actual-date))

(defn toggle-bulletins [enabled?]
  (fact {:midje/description (str "Bulletins enabled: " enabled?)}
    (command admin :update-organization
             :permitType "R"
             :municipality "753"
             :bulletinsEnabled enabled?) => ok?))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Pate bulletin"
    (let [app-id (create-app-id pena
                                :propertyId sipoo-property-id
                                :operation :kerrostalo-rivitalo)]
      (facts "Submit and approve application"
        (command pena :submit-application :id app-id) => ok?

        (fact "can change bulletin process"
          (command sonja :change-bulletin-process :id app-id :process "julkipano")
          => ok?)

        (command sonja :update-app-bulletin-op-description :id app-id
                 :description "This is description from approve application tab") => ok?
        (command sonja :approve-application :id app-id :lang "fi") => ok?)

      (fact "No application bulletins yet"
        (query sonja :verdict-bulletins :id app-id)
        => fail?)

      (let [{:keys [verdict-id]}        (command sonja :new-pate-verdict-draft
                                                 :id app-id
                                                 :template-id template-id)
            bulletin-id                 (str app-id "_" verdict-id)
            {verdict-draft :verdict
             references    :references} (query sonja :pate-verdict
                                               :id app-id
                                               :verdict-id verdict-id)]

        (fact "can fetch preview data from empty draft"
          (let [resp (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)]
            resp => ok?
            (:bulletin resp) => {:process "julkipano"}))

        (fact "Bulletin description in draft is from template"
          (get-in verdict-draft [:data :bulletin-op-description])
          => "Pate bulletin description")

        (facts "Fill verdict data and publish"
          (fill-verdict sonja app-id verdict-id
                        :verdict-code "hyvaksytty"
                        :verdict-text "Verdict given"
                        :verdict-date verdict-date
                        :giver-first-name "Pena"
                        :giver-last-name "Päättäjä"
                        :julkipano julkipano-date
                        :bulletin-op-description "Changed pate bulletin description")
          (fact "Add plans"
            (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                     :path [:plans]
                     :value (->> references :plans (map :id))) => no-errors?)
          (add-verdict-condition sonja app-id verdict-id "Condition 1")
          (add-verdict-condition sonja app-id verdict-id "Condition 2")
          (add-verdict-condition sonja app-id verdict-id "Condition 3"))

        (fact "can fetch preview data from filled draft"
          (let [resp (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)]
            resp => ok?
            (:bulletin resp) => {:process       "julkipano"
                                 :description   "Changed pate bulletin description"
                                 :publish-start (-> julkipano-date date/start-of-day date/timestamp)
                                 :publish-end   (-> julkipano-end-date
                                                    date/zoned-date-time
                                                    pate-date/forward-to-work-day
                                                    date/end-of-day
                                                    date/timestamp)}))

        (fact "Publish PATE verdict"
          (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

        (fact "cannot fetch preview data after publishing"
          (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id) => (err :error.verdict.not-editable))

        (facts "Bulletin is created"
          (let [store           (atom {})
                cookie-store    (doto (->cookie-store store)
                                  (.addCookie test-db-cookie))
                _               (vetuma-util/authenticate-to-vetuma! cookie-store)
                public-bulletin (:bulletin (query pena :public-bulletin :bulletinId bulletin-id))
                vetuma-bulletin (:bulletin (query pena :vetuma-bulletin :bulletinId bulletin-id :cookie-store cookie-store))]
            (fact "Bulletin description is from pate verdict"
              (:bulletinOpDescription public-bulletin) => "Changed pate bulletin description")

            (fact "Markup? is true"
              (:markup? public-bulletin) => true)

            (fact "Appeal period is correct"
              (:appealPeriodStartsAt public-bulletin) => julkipano-date
              (:appealPeriodEndsAt public-bulletin) => (-> julkipano-end-date
                                                           date/zoned-date-time
                                                           pate-date/forward-to-work-day
                                                           date/timestamp))

            (fact "Vetuma bulletin has data from verdict"
              (let [lupamaaraykset (-> vetuma-bulletin :verdicts first :paatokset first :lupamaaraykset)]
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
          => [(bulletins-itest-util/mock-bulletin-item bulletin-id "1" julkipano-date (-> julkipano-end-date
                                                                                          date/zoned-date-time
                                                                                          pate-date/forward-to-work-day
                                                                                          date/timestamp))])

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

          (fact "can fetch preview data from empty muutoksenhaku draft"
            (let [resp (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)]
              resp => ok?
              (:bulletin resp) => {:process "julkipano"}))

          (facts "Fill verdict data including muutoksenhaku and publish"
            (fill-verdict sonja app-id verdict-id
                          :verdict-code "hyvaksytty"
                          :verdict-text "Verdict given"
                          :verdict-date verdict-date
                          :giver-first-name "Pena"
                          :giver-last-name "Päättäjä"
                          :julkipano julkipano-date
                          :muutoksenhaku muutoksenhaku-date
                          :bulletin-op-description "Changed pate bulletin description"))

          (fact "can fetch preview data from filled draft"
            (let [resp (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)]
              resp => ok?
              (:bulletin resp) => {:process       "julkipano"
                                   :description   "Changed pate bulletin description"
                                   :publish-start (-> julkipano-date date/start-of-day date/timestamp)
                                   :publish-end   (-> muutoksenhaku-date date/end-of-day date/timestamp)}))

          (fact "Publish PATE verdict"
            (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

          (fact "cannot fetch preview data after publishing"
            (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id) => (err :error.verdict.not-editable))

          (facts "Bulletin is created and the appeal period ends at muutoksenhaku date"
            (:bulletin (query pena :public-bulletin :bulletinId (str app-id "_" verdict-id)))
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
                            :giver-first-name "Pena"
                            :giver-last-name "Päättäjä"
                            :julkipano julkipano-date
                            :muutoksenhaku muutoksenhaku-date)
              (command sonja :publish-pate-verdict :id app-id :verdict-id new-verdict-id)
              => no-errors?
              (fact "Old bulletin removed from bulletins"
                (query pena :public-bulletin :bulletinId (str app-id "_" verdict-id))
                => (err :error.bulletin.not-found)))))))))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Pate bulletin (kuulutus)"
    (let [app-id (create-app-id pena
                                :propertyId sipoo-property-id
                                :operation :kerrostalo-rivitalo)]

      (facts "Submit and approve application"
        (command pena :submit-application :id app-id) => ok?
        (command sonja :update-app-bulletin-op-description :id app-id
                 :description "This is description from approve application tab") => ok?
        (command sonja :approve-application :id app-id :lang "fi") => ok?)

      (fact "No application bulletins yet"
        (query sonja :verdict-bulletins :id app-id)
        => fail?)

      (let [{:keys [verdict-id]}        (command sonja :new-pate-verdict-draft
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

        (fact "can fetch preview data from empty draft"
          (let [resp (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)]
            resp => ok?
            (:bulletin resp) => {:process "kuulutus"}))

        (facts "Fill verdict data and publish"
          (fill-verdict sonja app-id verdict-id
                        :verdict-code "hyvaksytty"
                        :verdict-text "Verdict given"
                        :verdict-date verdict-date
                        :giver-first-name "Pena"
                        :giver-last-name "Päättäjä"
                        :k-kuulutus julkipano-date
                        :k-muutoksenhaku muutoksenhaku-date
                        :bulletin-op-description "Changed pate bulletin description")
          (fact "Add plans"
            (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                     :path [:plans]
                     :value (->> references :plans (map :id))) => no-errors?)
          (add-verdict-condition sonja app-id verdict-id "Condition 1")
          (add-verdict-condition sonja app-id verdict-id "Condition 2")
          (add-verdict-condition sonja app-id verdict-id "Condition 3"))

        (fact "can fetch preview data from filled draft"
          (let [resp (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)]
            resp => ok?
            (:bulletin resp) => {:process       "kuulutus"
                                 :description   "Changed pate bulletin description"
                                 :publish-start (-> julkipano-date date/start-of-day date/timestamp)
                                 :publish-end   (-> muutoksenhaku-date date/end-of-day date/timestamp)}))

        (fact "Publish PATE verdict"
          (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

        (fact "cannot fetch preview data after publishing"
          (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id) => (err :error.verdict.not-editable))

        (facts "Bulletin is created"
          (let [store           (atom {})
                cookie-store    (doto (->cookie-store store)
                                  (.addCookie test-db-cookie))
                _               (vetuma-util/authenticate-to-vetuma! cookie-store)
                public-bulletin (:bulletin (query pena :public-bulletin :bulletinId bulletin-id))
                vetuma-bulletin (:bulletin (query pena :vetuma-bulletin :bulletinId bulletin-id :cookie-store cookie-store))]

            (fact "Bulletin description is from pate verdict"
              (:bulletinOpDescription public-bulletin) => "Changed pate bulletin description")

            (fact "Markup? is true"
              (:markup? public-bulletin) => true)

            (fact "Appeal period is correct"
              (:appealPeriodStartsAt public-bulletin) => julkipano-date
              (:appealPeriodEndsAt public-bulletin) => muutoksenhaku-date)

            (fact "Vetuma bulletin has data from verdict"
              (let [lupamaaraykset (-> vetuma-bulletin :verdicts first :paatokset first :lupamaaraykset)]
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
          => [(bulletins-itest-util/mock-bulletin-item bulletin-id "1" julkipano-date muutoksenhaku-date)])

        (facts "Bulletin information pdf"
          (pdf-response?
            (raw sonja :bulletin-report-pdf :id app-id
                 :bulletinId bulletin-id
                 :lang "fi")
            {"Content-Disposition" (format "filename=\"Kuulutuksen tiedot %s %s.pdf\""
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
                          :giver-first-name "Pena"
                          :giver-last-name "Päättäjä"
                          :k-kuulutus julkipano-date
                          :k-muutoksenhaku muutoksenhaku-date
                          :bulletin-op-description "Changed pate bulletin description"))

          (fact "Publish PATE verdict"
            (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

          (facts "Bulletin is created and the appeal period ends at muutoksenhaku date"
            (:bulletin (query pena :public-bulletin :bulletinId (str app-id "_" verdict-id)))
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
                            :giver-first-name "Pena"
                            :giver-last-name "Päättäjä"
                            :k-kuulutus julkipano-date
                            :k-muutoksenhaku muutoksenhaku-date)
              (command sonja :publish-pate-verdict :id app-id :verdict-id new-verdict-id)
              => no-errors?
              (fact "Old bulletin removed from bulletins"
                (query pena :public-bulletin :bulletinId (str app-id "_" verdict-id))
                => (err :error.bulletin.not-found))))))))

  (facts "Pate verdict without bulletin"
    (let [app-id (create-app-id mikko
                                :propertyId sipoo-property-id
                                :operation :kerrostalo-rivitalo)]
      ;; Similar test below for kuulutus
      (toggle-bulletins false)
      (facts "Submit and approve application"
        (command mikko :submit-application :id app-id) => ok?

        (fact "can change bulletin process"
          (command sonja :change-bulletin-process :id app-id :process "julkipano")
          => ok?)

        (command sonja :update-app-bulletin-op-description :id app-id
                 :description "Cannot be set") => fail?
        (command sonja :approve-application :id app-id :lang "fi") => ok?)
      (facts "Give Pate verdict"
        (let [{:keys [verdict-id]} (command sonja :new-pate-verdict-draft
                                            :id app-id
                                            :template-id template-id)]

          (fact "cannot fetch preview data if bulletins disabled"
            (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)
            => (err :error.bulletins-not-enabled-for-scope))

          (fill-verdict sonja app-id verdict-id
                        :verdict-code "hyvaksytty"
                        :verdict-text "Verdict given"
                        :verdict-date verdict-date
                        :giver-first-name "Pena"
                        :giver-last-name "Päättäjä"
                        :julkipano julkipano-date
                        :muutoksenhaku muutoksenhaku-date)
          (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id)
          => no-errors?
          (fact "No bulletin created"
            (query mikko :public-bulletin :bulletinId (str app-id "_" verdict-id))
            => (err :error.bulletin.not-found)))))

    (facts "Pate verdict without bulletin (kuulutus)"
      (let [app-id (create-app-id mikko
                                  :propertyId sipoo-property-id
                                  :operation :kerrostalo-rivitalo)]
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

            (fact "cannot fetch preview data if bulletins disabled"
              (query sonja :preview-pate-verdict-data :id app-id :verdict-id verdict-id)
              => (err :error.bulletins-not-enabled-for-scope))

            (fill-verdict sonja app-id verdict-id
                          :verdict-code "hyvaksytty"
                          :verdict-text "Verdict given"
                          :verdict-date verdict-date
                          :giver-first-name "Pena"
                          :giver-last-name "Päättäjä"
                          :k-kuulutus julkipano-date
                          :k-muutoksenhaku muutoksenhaku-date)
            (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id)
            => no-errors?
            (fact "No bulletin created"
              (query mikko :public-bulletin :bulletinId (str app-id "_" verdict-id))
              => (err :error.bulletin.not-found))))))

    (toggle-bulletins true)

    (facts "Pate legacy verdict bulletin"

      (fact "Turn Pate off"
        (toggle-pate "753-R" false) => true?)

      (let [{legacy-app-id :id} (create-and-submit-application pena
                                                               :operation :pientalo
                                                               :propertyId sipoo-property-id)]
        ;; Similar test below for kuulutus
        (fact "can change bulletin process"
          (command sonja :change-bulletin-process :id legacy-app-id :process "julkipano")
          => ok?)

        (facts "Approve application"
          (command sonja :update-app-bulletin-op-description :id legacy-app-id
                   :description "This is description from approve application tab") => ok?
          (command sonja :approve-application :id legacy-app-id :lang "fi") => ok?)

        (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft :id legacy-app-id)
              edit                 (partial edit-verdict sonja legacy-app-id verdict-id)]

          (fact "can fetch preview data from empty legacy draft"
            (let [resp (query sonja :preview-pate-verdict-data :id legacy-app-id :verdict-id verdict-id)]
              resp => ok?
              (:bulletin resp) => {:process "julkipano"}))

          (facts "Fill legacy verdict"
            (fill-verdict sonja legacy-app-id verdict-id
                          :kuntalupatunnus "123-456"
                          :verdict-code "1"
                          :verdict-text "Verdict given"
                          :anto anto-date
                          :giver "viranhaltija"
                          :giver-first-name "Ville"
                          :giver-last-name "Viranhaltija"
                          :lainvoimainen lainvoimainen-date
                          :julkipano julkipano-date
                          :bulletin-op-description "This is pate legacy bulletin description")

            (add-legacy-review sonja legacy-app-id verdict-id "First review" :paikan-merkitseminen)

            (fact "Add condition"
              (let [condition-id (-> (edit :add-condition true) :changes flatten second)]
                (edit [:conditions condition-id :type] "muu-lupamaarays")
                (edit [:conditions condition-id :name] "Strict condition")
                => (contains {:filled true})))

            (fact "Add foreman"
              (let [foreman-id (-> (edit :add-foreman true) :changes flatten second)]
                (edit [:foremen foreman-id :role] "KVV-ty\u00f6njohtaja")
                => (contains {:filled true})))

            (fact "can fetch preview data from filled draft"
              (let [resp (query sonja :preview-pate-verdict-data :id legacy-app-id :verdict-id verdict-id)]
                resp => ok?
                (:bulletin resp) => (just {:process       "julkipano"
                                           :description   "This is pate legacy bulletin description"
                                           :publish-start (-> julkipano-date date/start-of-day date/timestamp)
                                           :publish-end   (fn [d]
                                                            (=ignoring-holidays (-> anto-date
                                                                                    (date/plus :days 14)
                                                                                    date/end-of-day)
                                                                                d))})))

            (fact "Publish legacy verdict"
              (command sonja :publish-legacy-verdict :id legacy-app-id :verdict-id verdict-id) => ok?)

            (fact "cannot fetch preview data after publishing"
              (query sonja :preview-pate-verdict-data :id legacy-app-id :verdict-id verdict-id)
              => (err :error.verdict.not-editable)))

          (facts "Bulletin is created"
            (let [store           (atom {})
                  cookie-store    (doto (->cookie-store store)
                                    (.addCookie test-db-cookie))
                  _               (vetuma-util/authenticate-to-vetuma! cookie-store)
                  public-bulletin (:bulletin (query pena :public-bulletin :bulletinId (str legacy-app-id "_" verdict-id)))
                  vetuma-bulletin (:bulletin (query pena :vetuma-bulletin :bulletinId (str legacy-app-id "_" verdict-id) :cookie-store cookie-store))]
              (fact "Bulletin description is from pate verdict"
                (:bulletinOpDescription public-bulletin) => "This is pate legacy bulletin description")

              (fact "legacy verdict bulletins do not have verdict-date.."
                (:verdictDate public-bulletin) => nil?)

              (fact ".. instead they have verdictGivenAt/anto date"
                (:verdictGivenAt public-bulletin) => anto-date)

              (fact "Appeal period is correct"
                (:appealPeriodStartsAt public-bulletin) => julkipano-date
                (:appealPeriodEndsAt public-bulletin) => (fn [d]
                                                           (=ignoring-holidays (-> anto-date
                                                                                   (date/plus :days 14))
                                                                               d)))

              (fact "Vetuma bulletin has data from verdict"
                (let [lupamaaraykset (-> vetuma-bulletin :verdicts first :paatokset first :lupamaaraykset)]
                  (:vaaditutTyonjohtajat lupamaaraykset) => "KVV-ty\u00f6njohtaja"
                  (:vaaditutKatselmukset lupamaaraykset) => [{:tarkastuksenTaiKatselmuksenNimi "First review"}]
                  (:maaraykset lupamaaraykset) => [{:sisalto "Strict condition"}])))))))

    (facts "Pate legacy verdict bulletin (kuulutus)"

      ;; Pate already disabled in previous test

      (let [{legacy-app-id :id} (create-and-submit-application pena
                                                               :operation :pientalo
                                                               :propertyId sipoo-property-id)]

        (facts "Approve application"
          (command sonja :update-app-bulletin-op-description :id legacy-app-id
                   :description "This is description from approve application tab") => ok?
          (command sonja :approve-application :id legacy-app-id :lang "fi") => ok?)

        (let [{:keys [verdict-id]} (command sonja :new-legacy-verdict-draft :id legacy-app-id)
              edit                 (partial edit-verdict sonja legacy-app-id verdict-id)]

          (fact "can fetch preview data from empty legacy draft"
            (let [resp (query sonja :preview-pate-verdict-data :id legacy-app-id :verdict-id verdict-id)]
              resp => ok?
              (:bulletin resp) => {:process "kuulutus"}))

          (facts "Fill legacy verdict"
            (fill-verdict sonja legacy-app-id verdict-id
                          :kuntalupatunnus "123-456"
                          :verdict-code "1"
                          :verdict-text "Verdict given"
                          :giver "viranhaltija"
                          :giver-first-name "Ville"
                          :giver-last-name "Viranhaltija"
                          :k-verdict-date anto-date
                          :k-lainvoimainen lainvoimainen-date
                          :k-kuulutus julkipano-date
                          :bulletin-op-description "This is pate legacy bulletin description")

            (add-legacy-review sonja legacy-app-id verdict-id "First review" :paikan-merkitseminen)

            (fact "Add condition"
              (let [condition-id (-> (edit :add-condition true) :changes flatten second)]
                (edit [:conditions condition-id :type] "muu-lupamaarays")
                (edit [:conditions condition-id :name] "Strict condition")
                => (contains {:filled true})))

            (fact "Add foreman"
              (let [foreman-id (-> (edit :add-foreman true) :changes flatten second)]
                (edit [:foremen foreman-id :role] "KVV-ty\u00f6njohtaja")
                => (contains {:filled true})))

            (fact "can fetch preview data from filled draft"
              (let [resp (query sonja :preview-pate-verdict-data :id legacy-app-id :verdict-id verdict-id)]
                resp => ok?
                (:bulletin resp) => (just {:process       "kuulutus"
                                           :description   "This is pate legacy bulletin description"
                                           :publish-start (-> julkipano-date date/start-of-day date/timestamp)
                                           :publish-end   (fn [d]
                                                            (=ignoring-holidays (-> julkipano-date
                                                                                    (date/plus :days 37)
                                                                                    date/end-of-day)
                                                                                d))})))

            (fact "Publish legacy verdict"
              (command sonja :publish-legacy-verdict :id legacy-app-id :verdict-id verdict-id) => ok?)

            (fact "cannot fetch preview data after publishing"
              (query sonja :preview-pate-verdict-data :id legacy-app-id :verdict-id verdict-id)
              => (err :error.verdict.not-editable)))

          (facts "Bulletin is created"
            (let [store           (atom {})
                  cookie-store    (doto (->cookie-store store)
                                    (.addCookie test-db-cookie))
                  _               (vetuma-util/authenticate-to-vetuma! cookie-store)
                  public-bulletin (:bulletin (query pena :public-bulletin :bulletinId (str legacy-app-id "_" verdict-id)))
                  vetuma-bulletin (:bulletin (query pena :vetuma-bulletin :bulletinId (str legacy-app-id "_" verdict-id) :cookie-store cookie-store))]
              (fact "Bulletin description is from pate verdict"
                (:bulletinOpDescription public-bulletin) => "This is pate legacy bulletin description")

              (fact "verdict-date"
                (:verdictDate public-bulletin) => anto-date)

              (fact "Appeal period is correct"
                (:appealPeriodStartsAt public-bulletin) => julkipano-date
                (:appealPeriodEndsAt public-bulletin) => (fn [d]
                                                           ;; 30 + 7 default
                                                           ;; .. but end date might be moved if it lands on a weekend
                                                           (=ignoring-holidays (-> julkipano-date
                                                                                   (date/plus :days 37))
                                                                               d)))

              (fact "Vetuma bulletin has data from verdict"
                (let [lupamaaraykset (-> vetuma-bulletin :verdicts first :paatokset first :lupamaaraykset)]
                  (:vaaditutTyonjohtajat lupamaaraykset) => "KVV-ty\u00f6njohtaja"
                  (:vaaditutKatselmukset lupamaaraykset) => [{:tarkastuksenTaiKatselmuksenNimi "First review"}]
                  (:maaraykset lupamaaraykset) => [{:sisalto "Strict condition"}])))))))

    (facts "Legacy verdict without bulletin"
      (let [app-id (create-app-id mikko
                                  :propertyId sipoo-property-id
                                  :operation :pientalo)]
        ;; Similar test below for kuulutus
        (toggle-bulletins false)
        (facts "Submit and approve application"
          (command mikko :submit-application :id app-id) => ok?

          (fact "can change bulletin process"
            (command sonja :change-bulletin-process :id app-id :process "julkipano")
            => ok?)

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
                          :giver "viranhaltija"
                          :giver-first-name "Ville"
                          :giver-last-name "Viranhaltija"
                          :anto anto-date
                          :lainvoimainen lainvoimainen-date
                          :julkipano julkipano-date)
            (command sonja :publish-legacy-verdict :id app-id :verdict-id verdict-id)
            => no-errors?
            (fact "No bulletin created"
              (query mikko :public-bulletin :bulletinId (str app-id "_" verdict-id))
              => (err :error.bulletin.not-found))))))

    (facts "Legacy verdict without bulletin (kuulutus)"
      (let [app-id (create-app-id mikko
                                  :propertyId sipoo-property-id
                                  :operation :pientalo)]
        ;; bulletins already disabled in previous test
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
                          :giver "viranhaltija"
                          :giver-first-name "Ville"
                          :giver-last-name "Viranhaltija"
                          :k-verdict-date anto-date
                          :k-lainvoimainen lainvoimainen-date
                          :k-kuulutus julkipano-date)
            (command sonja :publish-legacy-verdict :id app-id :verdict-id verdict-id)
            => no-errors?
            (fact "No bulletin created"
              (query mikko :public-bulletin :bulletinId (str app-id "_" verdict-id))
              => (err :error.bulletin.not-found))))))

    (fact "Include attachments into verdict template and republish"
      (toggle-pate "753-R" true) => true?
      (doseq [[section value] [["attachments" false] ["buildings" true]]]
        (command sipoo :save-verdict-template-draft-value
                 :organizationId "753-R"
                 :template-id template-id
                 :path ["removed-sections" section]
                 :value value) => ok?)
      (publish-verdict-template sipoo "753-R" template-id) => ok?)

    (facts "Bulletin attachment"
      (toggle-bulletins true)
      (let [app-id (create-app-id pena
                                  :propertyId sipoo-property-id
                                  :operation :pientalo)]
        (facts "Submit and approve application"
          (command pena :submit-application :id app-id) => ok?

          (fact "can change bulletin process"
            (command sonja :change-bulletin-process :id app-id :process "julkipano")
            => ok?)

          (command sonja :update-app-bulletin-op-description :id app-id
                   :description "This is description from approve application tab") => ok?
          (fact "Add file and mark it as a verdict attachment"
            (let [_                      (upload-file-and-bind pena app-id {:type     {:type-group "paapiirustus"
                                                                                       :type-id    "asemapiirros"}
                                                                            :filename "dev-resources/test-pdf.pdf"
                                                                            :contents "Public"
                                                                            :group    {}})
                  file-id2               (upload-file-and-bind pena app-id {:type     {:type-group "paapiirustus"
                                                                                       :type-id    "asemapiirros"}
                                                                            :filename "dev-resources/test-pdf.pdf"
                                                                            :contents "Confidential"
                                                                            :group    {}})
                  att-id2                (->> (query-application sonja app-id)
                                              :attachments
                                              (filter (comp (partial = file-id2) :fileId :latestVersion))
                                              first :id)

                  verdict-attachment-ids (->> (query-application sonja app-id)
                                              :attachments
                                              (keep #(when (#{"Public" "Confidential"} (:contents %))
                                                       (:id %))))]
              (command pena :set-attachment-visibility :id app-id
                       :attachmentId att-id2
                       :value "asiakas-ja-viranomainen")
              => ok?
              (command sonja :set-attachments-as-verdict-attachment
                       :id app-id
                       :selectedAttachmentIds verdict-attachment-ids
                       :unSelectedAttachmentIds [])
              => ok?))

          (command sonja :approve-application :id app-id :lang "fi") => ok?)

        (fact "Publish verdict"
          (let [{:keys [verdict-id]}        (command sonja :new-pate-verdict-draft
                                                     :id app-id
                                                     :template-id template-id)
                bulletin-id                 (str app-id "_" verdict-id)
                {verdict-draft :verdict
                 references    :references} (query sonja :pate-verdict
                                                   :id app-id
                                                   :verdict-id verdict-id)]

            (facts "Fill verdict data and publish"
              (fill-verdict sonja app-id verdict-id
                            :verdict-code "hyvaksytty"
                            :verdict-text "Verdict given"
                            :verdict-date verdict-date
                            :giver-first-name "Pena"
                            :giver-last-name "Päättäjä"
                            :julkipano julkipano-date
                            :muutoksenhaku muutoksenhaku-date
                            :bulletin-op-description "Changed pate bulletin description"))

            (fact "Publish PATE verdict"
              (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?)

            (facts "Bulletin is created"
              (let [store           (atom {})
                    cookie-store    (doto (->cookie-store store)
                                      (.addCookie test-db-cookie))
                    _               (vetuma-util/authenticate-to-vetuma! cookie-store)
                    public-bulletin (:bulletin (query pena :public-bulletin :bulletinId bulletin-id))
                    vetuma-bulletin (:bulletin (query pena :vetuma-bulletin :bulletinId bulletin-id :cookie-store cookie-store))]
                (fact "Public bulletin does not include attachments"
                  (:attachments public-bulletin) => nil)

                (fact "Vetuma bulletin includes only the public attachment."
                  (:attachments vetuma-bulletin)
                  => (just (contains {:contents "Public"
                                      :type     {:type-group "paapiirustus"
                                                 :type-id    "asemapiirros"}})))

                (let [public-file-id (->> vetuma-bulletin :attachments
                                          (util/find-by-key :contents "Public")
                                          :latestVersion :fileId)]
                  (fact "Only Vetuma authenticated user can download the attachment."
                    public-file-id => truthy
                    (raw mikko :download-bulletin-attachment
                         :bulletin-id bulletin-id :attachment-id public-file-id
                         :cookie-store cookie-store)
                    => (contains {:body truthy :status 200})
                    => (contains {:status 404 :reason-phrase "Not Found"})
                    (raw mikko :download-bulletin-attachment
                         :bulletin-id bulletin-id :attachment-id public-file-id)
                    => (contains {:status 404 :reason-phrase "Not Found"})))

                (fact "Non-existing attachment"
                  (raw mikko :download-bulletin-attachment
                       :bulletin-id bulletin-id :attachment-id "no-such-file-id"
                       :cookie-store cookie-store)
                  => (contains {:status 404 :reason-phrase "Not Found"}))))))))))
