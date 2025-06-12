(ns lupapalvelu.migration.conversion-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.migration.conversion :as conv-mig]))

(def parse-combined-property-id #'conv-mig/parse-combined-property-id)
(def ->int-or-s #'conv-mig/->int-or-s)

(deftest ->int-or-s-test
  (testing "int"
    (is (= 12345 (->int-or-s "  12345 "))))
  (testing "string"
    (is (= "hello" (->int-or-s " hello ")))
    (is (= "" (->int-or-s "  ")))
    (is (= "" (->int-or-s nil)))))

(deftest parse-combined-property-id-test
  (testing "good"
    (is (= ["186-401-10-4" "M0605"]
           (parse-combined-property-id "186-401-0010-0004-M-0605")))
    (is (= ["186-401-10-4" "M0001"]
           (parse-combined-property-id "186-401-0010-0004-M-1")))
    (is (= ["186-401-10-4"]
           (parse-combined-property-id " 000186-0000401-0010-0004 ")))
    (is (nil? (parse-combined-property-id " ")))
    (is (nil? (parse-combined-property-id nil))))
  (testing "bad"
    (is (= ["186-401-10-4-605"]
           (parse-combined-property-id "186-401-0010-0004-0605")))
    (is (= ["bad"] (parse-combined-property-id " bad ")))
    (is (= ["one-two-three" "Mfoobar"]
           (parse-combined-property-id "  one - two - three -M- foobar ")))))

(deftest site-document-id-components-test
  (testing "blank"
    (is (nil? (conv-mig/site-document-id-components {})))
    (is (nil?(conv-mig/site-document-id-components
               {:documents [{:schema-info {:name "rakennuspaikka-kuntagml"}
                             :data {:kiinteisto {:kiinteistotunnus {:value "  "}}}}]}))))
  (testing "good"
    (is (= [" 186-401-0010-0004-M-0605 " "186-401-10-4" "M0605"]
           (conv-mig/site-document-id-components
             {:documents [{:schema-info {:name "rakennuspaikka-kuntagml"}
                           :data {:kiinteisto {:kiinteistotunnus {:value " 186-401-0010-0004-M-0605 "}}}}]})))
    (is (= ["00186-00401-0010-0000004 " "186-401-10-4"]
           (conv-mig/site-document-id-components
             {:documents [{:schema-info {:name "rakennuspaikka-kuntagml"}
                           :data {:kiinteisto {:kiinteistotunnus {:value "00186-00401-0010-0000004 "}}}}]})))))

(defn art [user-ids]
  (->> (map-indexed (fn [i id]
                      {:state (inc i) :user {:id id}})
                    user-ids)
       (hash-map :history)
       conv-mig/application-reset-state))

(def bid "batchrun-user")
(def cid "conversion-user")

(deftest application-reset-state-test
  (testing "No state"
    (doseq [user-ids [["hello"] [] ["world" bid]]]
      (is (nil? (art user-ids)))))
  (testing "State"
    (is (= 1 (art [bid])))
    (is (= 1 (art [cid])))
    (is (= 2 (art [bid bid])))
    (is (= 2 (art [bid cid])))
    (is (= 2 (art [cid bid "hello"])))
    (is (= 1 (art [bid "world" cid])))))

(def viili {:address    "Viilarinkatu"
            :x          393371
            :y          6707943
            :propertyId "18600999030006"})

(def point-xml (slurp "resources/krysp/dev/verdict-rakval-from-kuntalupatunnus-query.xml"))

(deftest conversion-document-reset-location-test
  (testing "location"
    (is (= {:x 393371.0 :y 6707943.0}
           (conv-mig/conversion-document-reset-location {:location viili}))))
  (testing "kuntagml"
    (is (= {:x 393033.614 :y 6707228.994}
           (conv-mig/conversion-document-reset-location
             {:location {:address    "No idea"
                         :propertyId "18600999030000"}
              :kuntagml {:xml point-xml}}))))
  (testing "nope"
    (doseq [doc [{} {:location {:x 1 :y 2}}
                 {:kuntagml {:error "bad" :xml point-xml}}]]
      (is (nil? (conv-mig/conversion-document-reset-location doc))))))
