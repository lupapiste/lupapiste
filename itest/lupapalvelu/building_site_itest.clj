(ns lupapalvelu.building-site-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.building-site :as bsite]
            [lupapalvelu.itest-util :refer :all]))

(use-fixtures :once (local-fixture-factory :db-name "test_building-site-api-itest"))

(def good-property-id "12340200070765")
(def bad-property-id (->> good-property-id reverse (apply str)))

(deftest building-site-information-test
  (with-redefs [bsite/fetch-ktj-tiedot (fn [property-id]
                                         (when (= property-id good-property-id)
                                           {:kiinteistotunnus     good-property-id
                                            :maapintaala          "0.4793"
                                            :nimi                 "MOUNTAIN"
                                            :olotila              "1"
                                            :rekisteriyksikkolaji "1"
                                            :rekisterointipvm     "19910905"}))]
    (testing "Bad input"
      (is (fails-with? :error.invalid-property-id
                       (query sonja :building-site-information
                              :propertyId "nope!"))))

    (testing "Property not found"
      (is (fails-with? :error.property-not-found
                       (query pena :building-site-information
                              :propertyId bad-property-id))))

    (testing "Success (both for authority and applicant)"
      (doseq [apikey [sonja pena]]
        (is (= {:ok   true
                :data {:nimi             "MOUNTAIN"
                       :rekisterointipvm "05.09.1991"
                       :maapintaala      "0.4793"}}
               (query apikey :building-site-information
                      :propertyId good-property-id)))))))
