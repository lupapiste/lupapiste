(ns lupapalvelu.application-submit-data-source-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.application.submit-data-source :as sds]
            [lupapalvelu.fixture.fixtures]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest :refer [command with-test-system]]
            [lupapalvelu.publish-dates :as pd]
            [lupapalvelu.test-util :refer [ok?]]
            [sade.date :as date]))

(def applicant itest/pena)
(def authority itest/sonja)
(def property-id itest/sipoo-property-id)

(use-fixtures :each
  (fn [run-tests]
    (with-test-system (-> {:fixture "minimal"}
                          test-system/with-remote-components)
      (run-tests))))

(deftest sds-permission-old-app-test
  (let [{app-id :id
         :as    app} (itest/create-and-submit-application applicant
                                                          :propertyId property-id
                                                          :address "Hakukuja 123"
                                                          :operation "muu-uusi-rakentaminen")]
    (is app)

    ;; Mock submission date before deadline
    (itest/set-application-submitted app-id (-> pd/bim-data-source-date
                                                (date/minus :day)
                                                date/timestamp))

    (testing "data source is locked and neither applicant nor authority may alter it"
      (let [app (itest/query-application applicant app-id)]
        (is (= sds/default-data-source (keyword (:submitDataSource app))))
        (is (:submitDataSourceLocked app))

        (doseq [user [applicant authority]]
          (is (= {:ok   false
                  :text "error.illegal-value:schema-validation"}
                 (command user :set-submit-data-source {:id app-id :datasource "foobar"}))))

        (is (= {:ok   false
                :text "error.unauthorized"}
               (command applicant :set-submit-data-source {:id app-id :datasource "document"}))
            "already submitted => applicant may not alter")

        (is (= {:ok   false
                :text "error.submit-data-source.locked"}
               (command authority :set-submit-data-source {:id app-id :datasource "document"}))
            "locked => authority may not alter")))))

(deftest sds-permission-test
  (let [{app-id :id
         :as    app} (itest/create-application applicant
                                               :propertyId property-id
                                               :address "Hakukuja 123"
                                               :operation "muu-uusi-rakentaminen")]
    (is app)

    (testing "default value in use, not locked"
      (is (= sds/default-data-source (keyword (:submitDataSource app))))
      (is (not (:submitDataSourceLocked app))))

    (is (= {:ok   false
            :text "error.unauthorized"}
           (command authority :set-submit-data-source {:id app-id :datasource "document"}))
        "authority may not alter while app is still a draft")

    (testing "applicant may alter before submitting"
      (is (ok? (command applicant :set-submit-data-source {:id app-id :datasource "document"}))))

    (is (ok? (command applicant :submit-application :id app-id)))

    ;; Mock submission date after deadline
    (itest/set-application-submitted app-id (-> pd/bim-data-source-date
                                                (date/plus :day)
                                                date/timestamp))

    (is (= {:ok   false
            :text "error.unauthorized"}
           (command applicant :set-submit-data-source {:id app-id :datasource "document"}))
        "already submitted => applicant may not alter")

    (is (ok? (command authority :set-submit-data-source {:id app-id :datasource "bim"})))

    (testing "value was changed"
      (let [app (itest/query-application applicant app-id)]
        (is (= :bim (keyword (:submitDataSource app))))
        (is (not (:submitDataSourceLocked app)))))))
