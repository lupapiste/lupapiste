(ns sade.password-test
  (:require [clojure.test :refer [deftest is testing]]
            [sade.password :as pw]))

(deftest password-tests
  (testing "Bad passwords"
    (is (= 0 (pw/measure-password-quality nil)))
    (is (= 0 (pw/measure-password-quality "")))
    (is (= 0 (pw/measure-password-quality "password")))
    (is (= 1 (pw/measure-password-quality "salasana"))))
  (testing "Finnish names"
    (is (= 2 (pw/measure-password-quality "orpopetteri")))
    (is (= 2 (pw/measure-password-quality "riikkapurra")))
    (is (= 1 (pw/measure-password-quality "valtonenelina")))
    (is (= 2 (pw/measure-password-quality "villetavio")))
    (is (= 1 (pw/measure-password-quality "merileena")))
    (is (= 3 (pw/measure-password-quality "perttijormaerkki")))
    (is (= 3 (pw/measure-password-quality "jennaellinoora"))))
  (testing "Finnish words"
    (is (= 1 (pw/measure-password-quality "kissa")))
    (is (= 0 (pw/measure-password-quality "pääministeri")))
    (is (= 0 (pw/measure-password-quality "sunnuntaina")))
    (is (= 1 (pw/measure-password-quality "tunnus")))
    (is (= 3 (pw/measure-password-quality "salaisuus")))
    (is (= 0 (pw/measure-password-quality "suomi"))))
  (testing "too long"
    (let [almost-too-long (apply str (range 0 65))
          too-long        (apply str (range 0 70))]
      (is (= 120 (count almost-too-long)))
      (is (= 130 (count too-long)))
      (is (= 4 (pw/measure-password-quality almost-too-long)))
      (is (= 0 (pw/measure-password-quality too-long)))))
  (testing "with user dictionary"
    (is (= 4 (pw/measure-password-quality "FrdasdKdmnr")))
    (is (= 2 (pw/measure-password-quality "FrdasdKdmnr" {:firstName "Frdasd"})))
    (is (= 3 (pw/measure-password-quality "FrdasdKdmnr" {:lastName "Kdmnr"})))
    (is (= 1 (pw/measure-password-quality "FrdasdKdmnr" {:email "frdasd.kdmnr@company.fi"})))))

(deftest user->dict-test
  (testing ""
    (is (empty? (pw/user->dict {})))
    (is (= ["Foo" "Bar"]
           (pw/user->dict {:firstName "Foo"
                           :lastName  "Bar"})))
    (is (= ["etunimi.sukunimi@company.fi"
            "etunimi"
            "sukunimi@company"
            "fi"
            "etunimi.sukunimi"
            "sukunimi"
            "company.fi"
            "company"]
           (pw/user->dict {:email "etunimi.sukunimi@company.fi"})))))
