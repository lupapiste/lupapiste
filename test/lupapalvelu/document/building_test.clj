(ns lupapalvelu.document.building-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.building :as building]
            [monger.operators :refer :all]))

(def ^:private huoneistot-updates #'building/huoneistot-updates)

(deftest huoneistot-updates-test
  (let [command           {:application {} :created 12345}
        doc               {:schema-info {:name "rakennuksen-laajentaminen"}}
        doc-no-apartments {:schema-info {:name "kaupunkikuvatoimenpide-ei-tunnusta"}}]

    (testing "No apartments"
      (is (= {:mongo-updates {$set {:documents.$.data.huoneistot {}}}}
             (huoneistot-updates command doc {}))))

    (testing "Empty apartments"
      (is (= {:mongo-updates {$set {:documents.$.data.huoneistot {}}}}
             (huoneistot-updates command doc {:huoneistot {}}))))

    (testing "One empty apartment"
      (is (= {:mongo-updates {$set {:documents.$.data.huoneistot {:1 {}}}}}
             (huoneistot-updates command doc {:huoneistot {:1 {}}}))))

    (testing "One apartment"
      (is (= {:mongo-updates {$set {:documents.$.data.huoneistot
                                    {:0 {:WCKytkin     {:modified    12345
                                                        :source      "krysp"
                                                        :sourceValue true
                                                        :value       true}
                                         :huoneistoala {:modified    12345
                                                        :source      "krysp"
                                                        :sourceValue "10"
                                                        :value       "10"}}}}}}
             (huoneistot-updates command doc
                                 {:huoneistot {:0 {:WCKytkin     true
                                                   :huoneistoala "10"}}}))))

    (testing "Two apartments, non-schema fields are ignored"
      (is (= {:mongo-updates {$set {:documents.$.data.huoneistot
                                    {:0 {:WCKytkin     {:modified    12345
                                                        :source      "krysp"
                                                        :sourceValue true
                                                        :value       true}
                                         :huoneistoala {:modified    12345
                                                        :source      "krysp"
                                                        :sourceValue "10"
                                                        :value       "10"}}
                                     :2 {:jakokirjain {:modified    12345
                                                       :source      "krysp"
                                                       :sourceValue "j"
                                                       :value       "j"}
                                         :porras      {:modified    12345
                                                       :source      "krysp"
                                                       :sourceValue "C"
                                                       :value       "C"}}}}}}
             (huoneistot-updates command doc
                                 {:huoneistot {:0 {:WCKytkin     true
                                                   :huoneistoala "10"
                                                   :unsupported  "field"}
                                               :2 {:jakokirjain "j"
                                                   :porras      "C"
                                                   :foo         "bar"}}}))))

    (testing "Fail! on validation error, but only if muutostapa is defined and not poisto.
Note, that the building data from the backing system does not have muutostapa so in
practise we can read garbage. But, this should not matter, since untouched data is not
sent back. And if it is edited, it should also be corrected."
      (is (= {:mongo-updates {$set {:documents.$.data.huoneistot
                                    {:0 {:WCKytkin     {:modified    12345
                                                        :source      "krysp"
                                                        :sourceValue "hello"
                                                        :value       "hello"}
                                         :huoneistoala {:modified    12345
                                                        :source      "krysp"
                                                        :sourceValue "10"
                                                        :value       "10"}}}}}}
             (huoneistot-updates command doc
                                 {:huoneistot {:0 {:WCKytkin     "hello"
                                                   :huoneistoala "10"}}})))
      (let [e (is (thrown? Exception
                           (huoneistot-updates command doc
                                               {:huoneistot {:0 {:muutostapa   "lisäys"
                                                                 :WCKytkin     "hello"
                                                                 :huoneistoala "10"}}})))]
        (is (= {:ok      false
                :results '((({:document {:id     nil
                                         :locKey "rakennuksen-laajentaminen"
                                         :name   "rakennuksen-laajentaminen"
                                         :type   nil}
                              :element  {:i18nkey "huoneistot.WCKytkin"
                                         :label   false
                                         :locKey  "huoneistot.WCKytkin"
                                         :name    "WCKytkin"
                                         :type    :checkbox}
                              :path     [:huoneistot :0 :WCKytkin]
                              :result   [:err "illegal-value:not-a-boolean"]})))}
                 (select-keys (ex-data e) [:ok :results])))))

    (testing "No apartments in schema"
      (is (nil? (huoneistot-updates command doc-no-apartments
                                    {:huoneistot {:0 {:WCKytkin     true
                                                      :huoneistoala "10"}}}))))))
