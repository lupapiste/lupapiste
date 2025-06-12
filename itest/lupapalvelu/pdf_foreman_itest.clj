(ns lupapalvelu.pdf-foreman-itest
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer [no-errors?]]
            [sade.core :as core]
            [sade.env :as env]
            [sade.strings :as ss]))

(defn- setup-app-with-initiated-foreman []
  (let [app               (create-and-submit-application pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")
        app-id            (:id app)
        {vid :verdict-id} (command sonja :new-pate-verdict-draft :id app-id
                                   :template-id (-> pate-fixture/verdict-templates-r first :id))]
    (is (ss/blank-as-nil vid))
    (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id vid :path [:automatic-verdict-dates] :value true))
    (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id vid :path [:verdict-date] :value (core/now)))
    (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id vid :path [:verdict-code] :value "hyvaksytty"))
    (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id vid :path [:giver-first-name] :value "Gi"))
    (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id vid :path [:giver-last-name] :value "Ver"))
    (no-errors? (command sonja :publish-pate-verdict :id app-id :verdict-id vid))
    ;; `create-and-submit-application` creates empty foremen documents by default, no need to create a new foreman.
    ;; We can just get a foreman document and then call `set-user-to-document`
    (let [app            (query-application sonja app-id)
          foreman-doc    (domain/get-document-by-name app "tyonjohtaja-v3")
          foreman-doc-id (:id foreman-doc)]
      (is (ss/blank-as-nil foreman-doc-id))
      (is (ok? (command sonja :set-user-to-document :id app-id :documentId (:id foreman-doc) :userId pena-id :path "")))
      (is (ok? (command sonja :update-doc :id app-id :doc (:id foreman-doc) :updates (default-foreman-updates))))
      (is (ok? (command pena :foreman-consent :id app-id :foreman-doc-id (:id foreman-doc) :consent true)))
      (is (ok? (command sonja :declare-foreman :id app-id :foreman-doc-id (:id foreman-doc))))
      {:app-id         app-id
       :foreman-doc-id foreman-doc-id})))

(deftest pdf-export-foreman-tests
  (with-test-system (-> {:fixture "pate-verdict"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (env/with-feature-value :new-foreman-invite true
      (let [{:keys [app-id foreman-doc-id]} (setup-app-with-initiated-foreman)]
        (testing "pdf-foreman endpoint test"
          (testing "Authority can download foreman PDF"
            (let [resp (raw sonja :pdf-foreman :id app-id :foreman-doc-id foreman-doc-id)]
              (is (= 200 (:status resp)))
              (is (= "application/pdf" (get-in resp [:headers "Content-Type"])))))

          (testing "Assigned foreman user (pena) can download their own foreman PDF"
            (let [resp (raw pena :pdf-foreman :id app-id :foreman-doc-id foreman-doc-id)]
              (is (= 200 (:status resp)))
              (is (= "application/pdf" (get-in resp [:headers "Content-Type"])))))

          (testing "User with no access to the application is rejected"
            (let [resp (raw mikko :pdf-foreman :id app-id :foreman-doc-id foreman-doc-id)]
              (is (not= 200 (:status resp)))))

          (testing "Non-foreman document id is rejected"
            (let [non-foreman-doc-id (->> (query-application sonja app-id)
                                          :documents
                                          (remove :tj-document)
                                          first
                                          :id)
                  resp               (raw sonja :pdf-foreman :id app-id :foreman-doc-id non-foreman-doc-id)]
              (is (not= 200 (:status resp)))))

          (testing "Missing foreman-doc-id is rejected"
            (let [resp (raw sonja :pdf-foreman :id app-id)]
              (is (not= 200 (:status resp))))))

        (testing "pdf-foreman-list endpoint test"
          (let [app-id-no-foreman (create-app-id pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")]
            (testing "No initiated foremen — foreman list download fails"
              (let [resp (raw sonja :pdf-foreman-list :id app-id-no-foreman :lang "fi")]
                (is (not= 200 (:status resp)))))

            (testing "Authority without archivist role (sonja) is rejected"
              (let [resp (raw sonja :pdf-foreman-list :id app-id :lang "fi")]
                (is (not= 200 (:status resp)))))

            (testing "Applicant (pena) without archivist role is rejected"
              (let [resp (raw pena :pdf-foreman-list :id app-id :lang "fi")]
                (is (not= 200 (:status resp)))))

            (testing "Archivist can download foreman list PDF"
              (is (ok? (command admin :set-organization-boolean-attribute
                                :organizationId "753-R"
                                :attribute "permanent-archive-enabled"
                                :enabled true)))
              (is (ok? (command sipoo :update-user-roles
                                :organizationId "753-R"
                                :email "sonja.sibbo@sipoo.fi"
                                :roles ["authority" "approver" "archivist"]
                                :oauth-roles [])))
              (let [resp (raw sonja :pdf-foreman-list :id app-id :lang "fi")]
                (is (= 200 (:status resp)))
                (is (= "application/pdf" (get-in resp [:headers "Content-Type"]))))

              ;; Reset
              (is (ok? (command sipoo :update-user-roles
                                :organizationId "753-R"
                                :email "sonja.sibbo@sipoo.fi"
                                :roles ["authority" "approver"]
                                :oauth-roles [])))
              (is (ok? (command admin :set-organization-boolean-attribute
                                :organizationId "753-R"
                                :attribute "permanent-archive-enabled"
                                :enabled false))))))))))
