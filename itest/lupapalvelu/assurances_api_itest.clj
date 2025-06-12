(ns lupapalvelu.assurances-api-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(def org-id "753-R")

(defn get-assurance-templates []
  (-> (query sipoo :organization-by-user :organizationId org-id)
      :organization
      :application-submit-assurance-templates))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts

      (fact "authorityAdmin adds new assurances"
        (command sipoo :create-application-submit-assurance-template
                 {:organizationId org-id
                  :assurance      {:all-operations true
                                   :required       true
                                   :text           {:fi "Test fi"
                                                    :sv "Test sv"}}})
        => ok?
        (command sipoo :create-application-submit-assurance-template
                 {:organizationId org-id
                  :assurance      {:all-operations true
                                   :required       true
                                   :text           {:fi "Test 2 fi"
                                                    :sv "Test 2 sv"}}})
        => ok?)

    (fact "authorityAdmin modifies existing assurance"
      (let [assurances    (get-assurance-templates)
            assurance-ids (->> assurances (map :id))]

        assurances => (just [(contains {:all-operations true
                                        :id             string?
                                        :modified       integer?
                                        :required       true
                                        :text           {:fi "Test fi" :sv "Test sv"}})
                             (contains {:all-operations true
                                        :id             string?
                                        :modified       integer?
                                        :required       true
                                        :text           {:fi "Test 2 fi" :sv "Test 2 sv"}})])

        (command sipoo :save-application-submit-assurance-template
                 {:organizationId org-id
                  :assurance      {:id             (first assurance-ids)
                                   :text           {:fi "Test 1 fi"
                                                    :sv "Test 1 sv"}
                                   :required       false
                                   :all-operations false
                                   :operations     ["aita"]}})
        => ok?
        (let [modified-assurances (get-assurance-templates)]
          (->> modified-assurances
               (map #(dissoc % :modified))) => (just [{:id         (first assurance-ids)
                                                       :operations ["aita"]
                                                       :text       {:fi "Test 1 fi"
                                                                    :sv "Test 1 sv"}}
                                                      {:all-operations true
                                                       :id             (second assurance-ids)
                                                       :required       true
                                                       :text           {:fi "Test 2 fi"
                                                                        :sv "Test 2 sv"}}])
          ;; :modified timestamp of the first assurance should be bigger than before
          (map :modified modified-assurances)
          => (just [(partial < (-> assurances first :modified))
                    (partial = (-> assurances second :modified))]))))

    (fact "authorityAdmin reorders assurances"
      (let [assurance-ids (->> (get-assurance-templates) (map :id) reverse)]

        (command sipoo :store-application-submit-assurance-order
                 {:organizationId org-id
                  :assuranceIds   (take 1 assurance-ids)})
        => (partial expected-failure? :error.invalid-assurance-id-list)

        (command sipoo :store-application-submit-assurance-order
                 {:organizationId org-id
                  :assuranceIds   assurance-ids})
        => ok?

        (map :id (get-assurance-templates)) => assurance-ids))


    (fact "authorityAdmin removes assurances"

      (command sipoo :create-application-submit-assurance-template
               {:organizationId org-id
                :assurance      {:all-operations true
                                 :required       true
                                 :text           {:fi "Test 3 fi"
                                                  :sv "Test 3 sv"}}})
      => ok?

      (let [assurances (get-assurance-templates)]

        (command sipoo :remove-application-submit-assurance-template
                 {:organizationId org-id :assuranceId (mongo/create-id)})
        => (partial expected-failure? :error.assurance-not-found)

        (command sipoo :remove-application-submit-assurance-template
                 {:organizationId org-id :assuranceId (-> assurances first :id)})
        => ok?

        (get-assurance-templates) => (drop 1 assurances))))

  (facts "Submitting applications with assurances"

    (let [{app-id :id :as app} (create-app pena :propertyId sipoo-property-id :operation :aita)
          assurances           (:assurances (query pena :application-submit-assurance-templates :id app-id))
          required             (->> assurances
                                    (filter :required)
                                    (map #(select-keys % [:id :modified])))
          not-required         (->> assurances
                                    (remove :required)
                                    (map #(select-keys % [:id :modified])))]
      app => ok?

      (fact "Application cannot be submitted without proper assurances"
        (command pena :submit-application :id app-id)
        => (partial expected-failure? :error.required-assurances-not-filled))

      (fact "Application cannot be submitted with assurances that do not exist"
        (command pena :submit-application :id app-id
                 :assurances (conj required {:id       (mongo/create-id)
                                             :modified 1738666337114})
                 :assuranceLanguage "fi")
        => (partial expected-failure? :error.assurances-expired))

      (fact "Required assurances must be filled"
        (command pena :submit-application :id app-id :assuranceLanguage "fi")
        => (partial expected-failure? :error.required-assurances-not-filled)

        (command pena :submit-application :id app-id :assurances not-required :assuranceLanguage "fi")
        => (partial expected-failure? :error.required-assurances-not-filled)

        (command pena :submit-application :id app-id :assurances required :assuranceLanguage "fi")
        => ok?)

      (fact "Assurance data is stored"
        (let [app-assurance-data (:application-submit-assurances (query-application pena app-id))]
          (-> app-assurance-data :ts integer?) => true
          (:language app-assurance-data) => "fi"
          (:user app-assurance-data) => {:id pena-id :email (min-util/email-for-key pena)}
          (:assurances app-assurance-data) => (filter :required assurances)))

      (fact "Signed assurances can be fetched for application"
        (let [assurances-data (:data (query pena :get-assurances-for-application :id app-id))]
          (:user assurances-data)                => {:id pena-id :email (min-util/email-for-key pena)}
          (:language assurances-data)            => "fi"
          (-> assurances-data :assurances count) => 1))

      (fact "Returning application to draft clears stored assurances"
        (command sonja :return-to-draft :id app-id :lang "fi" :text "Return to draft") => ok?
        (let [app (query-application pena app-id)]
          (:id app) => app-id
          (:application-submit-assurances app) => nil?))))

  (facts "Authority admin modifies assurances after applicant has queried them but before submission"
    (let [{app-id :id :as app} (create-app pena :propertyId sipoo-property-id :operation :aita)
          assurances           (->> (query pena :application-submit-assurance-templates :id app-id)
                                    :assurances
                                    (map #(select-keys % [:id :modified])))]
      app => ok?

      (fact "Authority admin edits assurances"
        (let [assurance-id (->> (get-assurance-templates)
                                (filter :required)
                                first
                                :id)]
          (command sipoo :save-application-submit-assurance-template
                   {:organizationId org-id
                    :assurance      {:id             assurance-id
                                     :text           {:fi "Test 1 fi (modified)"
                                                      :sv "Test 1 sv (modified)"}
                                     :required       true
                                     :all-operations true}})
          => ok?))

      (fact "Submitting with previously valid assurances results an error"
        (command pena :submit-application :id app-id :assurances assurances :assuranceLanguage "fi")
        => (partial expected-failure? :error.assurances-expired))

      (fact "Submit succeeds after refetching assurances"
        (let [new-assurances (->> (query pena :application-submit-assurance-templates :id app-id)
                                  :assurances
                                  (map #(select-keys % [:id :modified])))]
          (command pena :submit-application :id app-id :assurances new-assurances :assuranceLanguage "fi")
          => ok?)))))
