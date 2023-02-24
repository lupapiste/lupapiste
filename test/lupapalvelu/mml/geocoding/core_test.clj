(ns lupapalvelu.mml.geocoding.core-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is use-fixtures]]
            [lupapalvelu.json :as json]
            [lupapalvelu.mml.geocoding.client :as client]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [schema.core :as sc]))

(use-fixtures :once (fn [test-fn] (sc/with-fn-validation (test-fn))))

(defn mock-client
  [{:keys [path query]}]
  (let [filename (case path
                   "/geocoding/v2/pelias/reverse"
                   (case (:lang query)
                     "fi" "mml/point_search_fi.json"
                     "sv" "mml/point_search_sv.json")
                   "/geocoding/v2/pelias/search"
                   (case (:lang query)
                     "fi" "mml/address_search_fi.json"
                     "sv" "mml/address_search_sv.json"))]
    (some-> filename
            io/resource
            slurp
            (json/decode keyword))))

(def laivurinkatu {:municipality "091"
                   :location     {:x 385806.0
                                  :y 6670525.0}
                   :name         {:fi "Helsinki"
                                  :sv "Helsingfors"}
                   :street       "Laivurinkatu"
                   :number       "3"})

(def skepparegatan (assoc laivurinkatu :street "Skepparegatan"))

(deftest point-search-test
  (binding [client/*client* mock-client]
    (is (= laivurinkatu
           (geocoding/address-by-point! "fi" 385819.58749773 6670491.7004395)))
    (is (= skepparegatan
           (geocoding/address-by-point! "sv" 385819.58749773 6670491.7004395)))))

(def merikatu {:municipality "091"
               :location     {:x 386195.013
                              :y 6670561.341}
               :name         {:fi "Helsinki"
                              :sv "Helsingfors"}
               :street       "Merikatu"
               :number       "1"})

(def sjögatan (assoc merikatu :street "Sjögatan"))

(deftest address-search-test
  (binding [client/*client* mock-client]
    (is (= [merikatu]
           (geocoding/address-by-text! "fi" {:street "whatever"})))
    (is (= [merikatu]
           (geocoding/address-by-text! {:street "whatever"})))
    (is (= [sjögatan]
           (geocoding/address-by-text! "sv" {:street "whatever"})))
    (is (= [sjögatan]
           (geocoding/address-by-text! "sv" true {:street "whatever"})))))

(deftest property-id-test
  (binding [client/*client* mock-client]
    (is (= laivurinkatu
           (geocoding/address-by-property-id! "fi" "09100701830013")))
    (is (= laivurinkatu
           (geocoding/address-by-property-id! "fi" false "09100701830013")))
    (is (= laivurinkatu
           (geocoding/address-by-property-id! "mocked")))
    (is (= skepparegatan
           (geocoding/address-by-property-id! "sv" "mocked")))))
