(ns lupapalvelu.document.rakennusoikeus-validator-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.document.rakennusoikeus-validator :as r-validator]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]))

(defn make-find-xf
  [{:keys [path expected-matches more-than]}]
  (comp
    ;; Find all registered schemas, which define a `rakennusoikeusValinta` field and count the matches
    (map (fn [[schema-name schema]]
           (let [matches (->> schema
                              ;; Child nodes are under :body -> {:body [ ... children ...]}
                              (tree-seq :body :body)
                              (filter (fn [node]
                                        (= "rakennusoikeusValinta" (:name node))))
                              count)]
             [schema-name schema matches])))
    ;; If asked, find schemas which have an exact number of matches
    (filter (if expected-matches
              (fn [[_schema-name _schema matches]]
                (= expected-matches matches))
              (constantly true)))
    ;; If asked, find schemas which have at least the required amount of matches
    (filter (if more-than
              (fn [[_schema-name _schema matches]]
                (<= more-than matches))
              (constantly true)))
    ;; Transform schemas to dummy documents so that we can just `get-in` the path
    (map (fn [[schema-name schema matches]]
           [schema-name {:data (tools/create-document-data schema (partial tools/dummy-values "123"))} matches]))
    ;; If asked, find schemas which have the field in the given path
    (filter (if path
              (fn [[_schema-name doc]]
                (get-in doc (conj path :rakennusoikeusValinta)))
              (constantly true)))
    (map first)))

(deftest find-schemas-with-rakennusoikeusvalinta-test
  (testing "Find all schemas which have at least one reference to `:rakennusoikeusValinta` anywhere"
    (is (= r-validator/mitat-schemas
           (into #{} (make-find-xf {:more-than 1}) (schemas/get-schemas 1)))))
  (testing "there are no schemas, which define `:rakennusoikeusValinta` only once and also in an unexpected path"
    (is (= (set/union r-validator/common-mitat-schemas
                      r-validator/sijoittamislupa-schemas
                      r-validator/ryhti-schemas)
           (into #{} (make-find-xf {:expected-matches 1}) (schemas/get-schemas 1))))
    (is (= r-validator/common-mitat-schemas
           (into #{} (make-find-xf {:expected-matches 1
                                    :path             [:data :mitat]}) (schemas/get-schemas 1))))
    (is (= r-validator/sijoittamislupa-schemas
           (into #{} (make-find-xf {:expected-matches 1
                                    :path             [:data :uusiRakennus :mitat]}) (schemas/get-schemas 1))))
    (is (= r-validator/ryhti-schemas
           (into #{} (make-find-xf {:expected-matches 1
                                    :path             [:data :rakennuskohteen-osa :ulkokuorentiedot]}) (schemas/get-schemas 1)))))
  (testing "laajentaminen schemas might match two paths"
    (is (= r-validator/laajentaminen-schemas
           (into #{} (make-find-xf {:expected-matches 2}) (schemas/get-schemas 1))))
    (is (= (set/difference r-validator/laajentaminen-schemas r-validator/ryhti-laajentaminen-schemas)
           (into #{} (make-find-xf {:expected-matches 2
                                    :path             [:data :mitat]}) (schemas/get-schemas 1))))
    (is (= r-validator/laajentaminen-schemas
           (into #{} (make-find-xf {:expected-matches 2
                                    :path             [:data :laajennuksen-tiedot :mitat]}) (schemas/get-schemas 1)))))
  (testing "No schema contains 3 or more matches`"
    (is (empty? (into #{} (make-find-xf {:more-than 3}) (schemas/get-schemas 1))))))

(deftest invalid-rakennusoikeus-fields-test
  (testing "invalid"
    (is (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" "asd" ""))
    (is (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" "123" ""))
    (is (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" "  " " 123 "))
    (is (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" nil " 123 ")))
  (testing "valid"
    (is (not (r-validator/invalid-rakennusoikeus-fields? "tilavuus" "  " nil)))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "tilavuus" "  " " ")))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "tilavuus" "1" " ")))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" "" "")))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" nil "")))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" nil nil)))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" "" "   ")))
    (is (not (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" " " "")))
    ;; Other validators handle case where input is not a number
    (is (not (r-validator/invalid-rakennusoikeus-fields? "harjakorkeus" " asd" " 123")))))
