(ns lupapalvelu.application-options-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.itest-system :as test-system]))

(use-fixtures :once (local-fixture-factory (-> {:db-name "test_application-options-itest"}
                                               ;; for `check-for-verdict`
                                               test-system/with-remote-components)))

(deftest municipality-hears-neighbors-test
  (let [{app-id :id :as app} (create-application pena
                                                 :propertyId sipoo-property-id
                                                 :operation "pientalo")]
    (testing "Checkbox originally unchecked and visible"
      (is (not (some-> app :options :municipalityHearsNeighbors)))
      (is (= "R" (:permitType app)))
      (is (:ok (query pena :municipality-hears-neighbors-visible
                      :id app-id)))
      (is (:ok (command pena :set-municipality-hears-neighbors
                        :id app-id
                        :enabled true))
          "Check the checkbox")
      (is (true? (some-> (query-application pena app-id)
                         :options :municipalityHearsNeighbors))))
    (testing "Submit application"
      (is (:ok (command pena :submit-application :id app-id))))

    (testing "Applicant cannot check the checkbox after submit"
      (is (fails-with? :error.command-illegal-state
                       (command pena :set-municipality-hears-neighbors
                                :id app-id
                                :enabled false))))
    (testing "Authority can check the checkbox after submit"
      (is (:ok (command sonja :set-municipality-hears-neighbors
                        :id app-id
                        :enabled false)))
      (is (:ok (command sonja :set-municipality-hears-neighbors
                        :id app-id
                        :enabled true)))
      (testing "but not if they are from another organization"
        (is (fails-with? :error.application-not-accessible
                         (command raktark-jarvenpaa :set-municipality-hears-neighbors
                                  :id app-id
                                  :enabled false)))))
    (testing "Sonja fetches verdict for the application"
      (is (:ok (command sonja :check-for-verdict :id app-id))))
    (testing "Neither authority nor applicant may check the checkbox after verdict"
      (is (fails-with? :error.command-illegal-state
                       (command pena :set-municipality-hears-neighbors
                                :id app-id
                                :enabled false)))
      (is (fails-with? :error.command-illegal-state
                       (command sonja :set-municipality-hears-neighbors
                                :id app-id
                                :enabled false))))

    (testing "Foreman application"
      (let [for-id (create-foreman-application app-id
                                               pena
                                               mikko-id
                                               "vastaava työnjohtaja"
                                               "A")]
        (testing "Check not visible"
          (is (fails-with? :error.foreman-application
                           (query pena :municipality-hears-neighbors-visible
                                  :id for-id))))
        (testing "... and cannot be toggled"
          (is (fails-with? :error.foreman-application
                           (command pena :set-municipality-hears-neighbors
                                    :id for-id
                                    :enabled true))))))))



(deftest authority-as-applicant-test
  ;; raktark-jarvenpaa is an authority user in another organization
  (let [{app-id :id :as app} (create-application raktark-jarvenpaa
                                                 :propertyId sipoo-property-id
                                                 :operation "pientalo")]
    (is (not (some-> app :options :municipalityHearsNeighbors)))
    (is (= "R" (:permitType app)))
    (testing "Checkbox visible"
      (is (:ok (query raktark-jarvenpaa :municipality-hears-neighbors-visible
                      :id app-id))))
    (testing "Check the checkbox"
      (is (:ok (command raktark-jarvenpaa :set-municipality-hears-neighbors
                    :id app-id
                    :enabled true)))
      (is (true? (some-> (query-application raktark-jarvenpaa app-id)
                         :options :municipalityHearsNeighbors))))
    (testing "Submit application"
      (is (:ok (command raktark-jarvenpaa :submit-application :id app-id))))
    (testing "Applicant cannot check the checkbox after submit, even if they are an authority"
      (is (fails-with? :error.command-illegal-state
                       (command raktark-jarvenpaa :set-municipality-hears-neighbors
                                :id app-id
                                :enabled false))))
    (testing "Authority can check the checkbox after submit"
      (is (:ok (command sonja :set-municipality-hears-neighbors
                        :id app-id
                        :enabled false)))
      (is (:ok (command sonja :set-municipality-hears-neighbors
                    :id app-id
                    :enabled true))))
    (testing "Sonja fetches verdict for the application"
      (is (:ok (command sonja :check-for-verdict :id app-id))))
    (testing "Neither authority nor applicant may check the checkbox after verdict"
      (is (fails-with? :error.command-illegal-state
                       (command raktark-jarvenpaa :set-municipality-hears-neighbors
                                :id app-id
                                :enabled false)))
      (is (fails-with? :error.command-illegal-state
                       (command sonja :set-municipality-hears-neighbors
                                :id app-id
                                :enabled false))))
    (testing "Change permit (muutoslupa) supports checkbox"
      (let [{change-id :id} (command raktark-jarvenpaa :create-change-permit :id app-id)
            change-app      (query-application raktark-jarvenpaa change-id)]
        (is (= "R" (:permitType change-app)))
        (is (= "muutoslupa" (:permitSubtype change-app)))
        (is (not (some-> change-app :options :municipalityHearsNeighbors)))
        (is (:ok (query raktark-jarvenpaa :municipality-hears-neighbors-visible
                        :id change-id)))
        (is (:ok (command raktark-jarvenpaa :set-municipality-hears-neighbors
                          :id change-id
                          :enabled true)))
        (is (true? (some-> (query-application raktark-jarvenpaa change-id)
                           :options :municipalityHearsNeighbors)))))))
