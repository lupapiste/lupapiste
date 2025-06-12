(ns lupapalvelu.singlepage-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.singlepage :refer [compose-resource]]
            [lupapalvelu.test-util :refer :all]
            [sade.env :as env]))

(deftest compose-resource-js-without-exceptions-test
  (with-redefs [env/feature? (fn [feature]
                               (case feature
                                 :no-minification              false
                                 :dummy-ident                  true
                                 :suomifi-ident                false
                                 :statistics-processing        false
                                 :ignore-kuntagml-publish-date true
                                 false))]

    (testing "common"
      (is (compose-resource :js :common)))

    (testing "welcome"
      (is (compose-resource :js :welcome)))

    (testing "applicant"
      (is (compose-resource :js :applicant)))

    (testing "authority"
      (is (compose-resource :js :authority)))

    (testing "authority-admin"
      (is (compose-resource :js :authority-admin)))

    (testing "oir"
      (is (compose-resource :js :oir)))

    (testing "neighbor"
      (is (compose-resource :js :neighbor)))

    (testing "hashbang"
      (is (compose-resource :js :hashbang)))))
