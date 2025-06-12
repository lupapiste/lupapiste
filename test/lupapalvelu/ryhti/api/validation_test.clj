(ns lupapalvelu.ryhti.api.validation-test
  (:require [clojure.test :as test :refer [deftest testing is]]
            [lupapalvelu.ryhti.api.validation :as sut]))

(deftest parse-error-path-test
  (is (= (sut/parse-error-path "a.b.c")
         [:a :b :c]))
  (is (= (sut/parse-error-path "a[0]")
         [:a 0]))
  (is (= (sut/parse-error-path "a[0].b[1][2]")
         [:a 0 :b 1 2])))

(deftest add-error-values-test
  (is (= [{:instance "buildingPermitIssue.buildingSite.buildingSiteAddress[0].addressName"
           :value "{:fin \"foobar\"}"}
          {:instance "buildingPermitIssue.nonExistingValue"
           :value "<<no value>>"}]
         (sut/add-error-values [{:instance "buildingPermitIssue.buildingSite.buildingSiteAddress[0].addressName"}
                                {:instance "buildingPermitIssue.nonExistingValue"}]
                               {:buildingSite {:buildingSiteAddress [{:addressName {:fin "foobar"}}]}}))))
