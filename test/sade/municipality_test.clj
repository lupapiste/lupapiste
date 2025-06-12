(ns sade.municipality-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.i18n :as i18n]
            [sade.municipality :as muni :refer [resolve-municipality]]))

(deftest resolve-municipality-test
  (testing "Sipoo"
    (is (= "753" (resolve-municipality "753"))))
  (testing "Juankoski is merged into Kuopio"
    (is (= "297" (resolve-municipality "174"))))
  (doseq [[k v] muni/municipality-mapping]
    (testing (str k " -> " v)
      (is (= v (resolve-municipality k)))))
  (testing "Bad municipalities"
    (is (nil? (resolve-municipality "bad")))
    (is (nil? (resolve-municipality nil)))
    (is (nil? (resolve-municipality 753)))
    (is (nil? (resolve-municipality "123")))
    (is (nil? (resolve-municipality " 753 ")))))

(deftest mustasaari2-test
  (testing "Mustasaari2 l10n"
    (is (= "899" (resolve-municipality "899")) )
    (is (= "Mustasaari 2"
           (i18n/localize :fi :municipality.899)
           (i18n/localize :en :municipality.899)))
    (is (= "Korsholm 2"
           (i18n/localize :sv :municipality.899)))))
