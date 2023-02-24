(ns lupapalvelu.invoices.shared.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.invoices.shared.util :as util]))

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste ""
                              :projekti         "d"
                              :kohde            "e"
                              :muu-tunniste     "f"})

(facts "rows-with-index-by-operation"
       (fact "returns a map of the form {<operation> [<row><row>...] where each row has the key :index with value matching the index in the original :rows vector in the catalogue}"
             (let [catalogue {:id "bar-1-id"
                              :organization-id "753-R"
                              :state "draft"
                              :valid-from 123456
                              :rows [{:code "123"
                                      :text "Taksarivi 1"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :product-constants dummy-product-constants
                                      :operations ["toimenpide1" "toimenpide2"]}
                                     {:code "abc"
                                      :text "Taksarivi 2"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :operations ["toimenpide1"]}]
                              :meta {:created 12345
                                     :created-by "dummy-user"}}]
               (util/rows-with-index-by-operation catalogue)  => {"toimenpide1" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :product-constants dummy-product-constants
                                                                                  :operations ["toimenpide1" "toimenpide2"]}
                                                                                 {:index 1
                                                                                  :code "abc"
                                                                                  :text "Taksarivi 2"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1"]}]
                                                                  "toimenpide2" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :product-constants dummy-product-constants
                                                                                  :operations ["toimenpide1" "toimenpide2"]}]}))

       (fact "does not include rows that do not operations key or its value is an empty coll}"
             (let [catalogue {:id "bar-1-id"
                              :organization-id "753-R"
                              :state "draft"
                              :valid-from 123456
                              :rows [{:code "123"
                                      :text "Taksarivi 1"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :operations ["toimenpide1" "toimenpide2"]}
                                     {:code "abc"
                                      :text "Taksarivi 2"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      }]
                              :meta {:created 12345
                                     :created-by "dummy-user"}}]
               (util/rows-with-index-by-operation catalogue)  => {"toimenpide1" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1" "toimenpide2"]}]
                                                                  "toimenpide2" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1" "toimenpide2"]}]})))

(facts "remove-maps-with-value"
       (fact "filters out maps that have any of the given values in a given key"
             (let [maps [{:name "George" :info "foo"}
                         {:name "Liz"    :info "foo"}
                         {:name "George" :info "blahblah"}
                         {:name "Liz"    :info "bar"}]]

               (util/remove-maps-with-value maps :name ["Liz"]) =>  [{:name "George" :info "foo"}
                                                                     {:name "George" :info "blahblah"}]

               (util/remove-maps-with-value maps :info ["foo"]) =>  [{:name "George" :info "blahblah"}
                                                                     {:name "Liz"    :info "bar"}]

               (util/remove-maps-with-value maps :info ["foo" "bar"]) =>  [{:name "George" :info "blahblah"}])))

(facts "empty-rows-by-operation"
       (fact "should return empty map when no operations given"
             (util/empty-rows-by-operation []) => {}
             (util/empty-rows-by-operation nil) => {})

       (fact "should return map of the form {operation1 [] operation2 [] ...}"
             (util/empty-rows-by-operation ["pientalo"]) => {"pientalo" []}
             (util/empty-rows-by-operation ["pientalo" "aita"]) => {"pientalo" []
                                                                    "aita" []}))

(facts "get-operations-from-tree"

       (let [operation-tree [["Rakentaminen ja purkaminen"
                              [["Uuden rakennuksen rakentaminen"
                                [["kerrostalo-rivitalo" "kerrostalo-rivitalo"]
                                 ["pientalo" "pientalo"]]]
                               ["Rakennuksen-laajentaminen"
                                [["kerrostalo-rt-laaj" "kerrostalo-rt-laaj"]
                                 ["pientalo-laaj" "pientalo-laaj"]
                                 ["vapaa-ajan-rakennus-laaj" "vapaa-ajan-rakennus-laaj"]
                                 ["talousrakennus-laaj" "talousrakennus-laaj"]
                                 ["teollisuusrakennus-laaj" "teollisuusrakennus-laaj"]
                                 ["muu-rakennus-laaj" "muu-rakennus-laaj"]]]]]

                             ["Poikkeusluvat ja suunnittelutarveratkaisut" "poikkeamis"]

                             ["Ympäristöluvat"
                              [["ymparistonsuojelulain-mukaiset-ilmoitukset"
                                [["Meluilmoitus" "meluilmoitus"]
                                 ["koeluontoinen-toiminta" "koeluontoinen-toiminta"]
                                 ["ilmoitus-poikkeuksellisesta-tilanteesta"
                                  "ilmoitus-poikkeuksellisesta-tilanteesta"]]]
                               ["maatalouden-ilmoitukset"
                                [["lannan-varastointi" "lannan-varastointi"]]]
                               ["Pima" "pima"]
                               ]]

                             ["maanmittaustoimitukset"
                              [["tonttijako" "tonttijako"]
                               ["kiinteistonmuodostus" "kiinteistonmuodostus"]
                               ["rasitetoimitus" "rasitetoimitus"]
                               ["rajankaynti" "rajankaynti"]]]
                             ["maankayton-muutos"
                              [["asemakaava" "asemakaava"]
                               ["ranta-asemakaava" "ranta-asemakaava"]
                               ["yleiskaava" "yleiskaava"]]]]]

         (fact "should return empty map when no operations given"
               (util/get-operations-from-tree [] ["Rakentaminen ja purkaminen"]) => [])

         (fact "should return empty map when no categories given"
               (util/get-operations-from-tree operation-tree []) => [])

         (fact "should return operations for one category"
               (util/get-operations-from-tree operation-tree ["Rakentaminen ja purkaminen"])
               => ["kerrostalo-rivitalo"
                   "pientalo"
                   "kerrostalo-rt-laaj"
                   "pientalo-laaj"
                   "vapaa-ajan-rakennus-laaj"
                   "talousrakennus-laaj"
                   "teollisuusrakennus-laaj"
                   "muu-rakennus-laaj"])

         (fact "should return operations for two categories"
               (util/get-operations-from-tree operation-tree ["Rakentaminen ja purkaminen" "Ympäristöluvat"])
               => [;;Rakentaminen ja purkaminen
                   "kerrostalo-rivitalo"
                   "pientalo"
                   "kerrostalo-rt-laaj"
                   "pientalo-laaj"
                   "vapaa-ajan-rakennus-laaj"
                   "talousrakennus-laaj"
                   "teollisuusrakennus-laaj"
                   "muu-rakennus-laaj"

                   ;;Ymparistoluvat
                   "meluilmoitus"
                   "koeluontoinen-toiminta"
                   "ilmoitus-poikkeuksellisesta-tilanteesta"
                   "lannan-varastointi"
                   "pima"])

         (fact "should return operations for categories that have different levels of depth"
               (let [operations [["category-with-no-children" "level-1-operation"]
                                 ["category-with-children"
                                  [["sub-category-with-no-children" "level-2-operation"]
                                   ["sub-category-with-children"
                                    [["name-b" "level-3-operation-1"]
                                     ["name-c" "level-3-operation-2"]]]]]]]
                 (util/get-operations-from-tree operations ["category-with-no-children"
                                                            "category-with-children"])
                    => ["level-1-operation"
                        "level-2-operation"
                        "level-3-operation-1"
                        "level-3-operation-2"]))))

(facts "indexed-product-rows"
       (fact "returns a coll of rows in price catalogue in the form [{:index 0, :code '123', text 'foo'..}{:index 1, :code '342', text 'bar'..} ...]"
             (let [catalogue {:id "bar-1-id"
                              :organization-id "753-R"
                              :state "published"
                              :valid-from 123456
                              :rows [{:code "123"
                                      :text "Taksarivi 1"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :product-constants dummy-product-constants
                                      :operations ["toimenpide1" "toimenpide2"]}
                                     {:code "abc"
                                      :text "Taksarivi 2"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :operations ["toimenpide1"]}]
                              :meta {:created 12345
                                     :created-by "dummy-user"}}]
               (util/indexed-rows catalogue)  => [{:index 0
                                                   :code "123"
                                                   :text "Taksarivi 1"
                                                   :unit "kpl"
                                                   :price-per-unit 23
                                                   :discount-percent 50
                                                   :product-constants dummy-product-constants
                                                   :operations ["toimenpide1" "toimenpide2"]}
                                                  {:index 1
                                                   :code "abc"
                                                   :text "Taksarivi 2"
                                                   :unit "kpl"
                                                   :price-per-unit 23
                                                   :discount-percent 50
                                                   :operations ["toimenpide1"]}])))

(facts "find-map"
       (fact "returns the first matching item in a coll of maps"
             (let [maps [{:id "a" :text "foo"}
                         {:id "b" :text "bar"}
                         {:id "c" :text "baz"}]]
               (util/find-map maps :id "b") => {:id "b" :text "bar"})))

(facts "->invoice-row"
       (fact "returns row"
             (fact "without min and max unit prices when they do not have values in the catalogue"
                   (util/->invoice-row {:code "123"
                                        :text "tuoterivi 1"
                                        :unit "kpl"
                                        :price-per-unit 3
                                        :discount-percent 10
                                        :product-constants dummy-product-constants
                                        :order-number 1})
                   => {:type "from-price-catalogue"
                       :code "123"
                       :text "tuoterivi 1"
                       :unit "kpl"
                       :units 0
                       :price-per-unit 3
                       :discount-percent 10
                       :product-constants dummy-product-constants
                       :order-number 1})

             (fact "with discount percent as 0 when value not present in price catalogue"
                   (util/->invoice-row {:code "123"
                                        :text "tuoterivi 1"
                                        :unit "kpl"
                                        :price-per-unit 3
                                        :product-constants dummy-product-constants
                                        :order-number 1})
                   => {:type "from-price-catalogue"
                       :code "123"
                       :text "tuoterivi 1"
                       :unit "kpl"
                       :units 0
                       :price-per-unit 3
                       :discount-percent 0
                       :product-constants dummy-product-constants
                       :order-number 1})

             (fact "with min-unit-price when the corresponding value in the price catalogue"
                   (util/->invoice-row {:code "123"
                                        :text "tuoterivi 1"
                                        :unit "kpl"
                                        :price-per-unit 6
                                        :discount-percent 10
                                        :product-constants dummy-product-constants
                                        :min-total-price 5
                                        :order-number 1})
                   => {:type "from-price-catalogue"
                       :code "123"
                       :text "tuoterivi 1"
                       :unit "kpl"
                       :units 0
                       :price-per-unit 6
                       :discount-percent 10
                       :product-constants dummy-product-constants
                       :min-unit-price 5
                       :order-number 1})

             (fact "with max-unit-price when the corresponding value in the price catalogue"
                   (util/->invoice-row {:code "123"
                                        :text "tuoterivi 1"
                                        :unit "kpl"
                                        :price-per-unit 3
                                        :discount-percent 10
                                        :product-constants dummy-product-constants
                                        :max-total-price 5
                                        :order-number 1})
                   => {:type "from-price-catalogue"
                       :code "123"
                       :text "tuoterivi 1"
                       :unit "kpl"
                       :units 0
                       :price-per-unit 3
                       :discount-percent 10
                       :product-constants dummy-product-constants
                       :max-unit-price 5
                       :order-number 1})

             (fact "with min-unit-price AND max-unit-price when the corresponding values in the price catalogue"
                   (util/->invoice-row {:code "123"
                                        :text "tuoterivi 1"
                                        :unit "kpl"
                                        :price-per-unit 6
                                        :discount-percent 10
                                        :product-constants dummy-product-constants
                                        :min-total-price 5
                                        :max-total-price 10
                                        :order-number 1})
                   => {:type "from-price-catalogue"
                       :code "123"
                       :text "tuoterivi 1"
                       :unit "kpl"
                       :units 0
                       :price-per-unit 6
                       :discount-percent 10
                       :product-constants dummy-product-constants
                       :min-unit-price 5
                       :max-unit-price 10
                       :order-number 1})

             (fact "row with price-per-unit as is "
                   (fact "when it is between min-total-price and max-total-price"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 6
                                              :discount-percent 10
                                              :min-total-price 5
                                              :max-total-price 10})
                         => (contains {:price-per-unit 6
                                       :min-unit-price 5
                                       :max-unit-price 10}))

                   (fact "when min price nil and price-per-unit less than max price"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 6
                                              :discount-percent 10
                                              :min-total-price nil
                                              :max-total-price 10})
                         => (contains {:price-per-unit 6
                                       :max-unit-price 10}))

                   (fact "when max price nil and price-per-unit more than min price"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 8
                                              :discount-percent 10
                                              :min-total-price 6
                                              :max-total-price nil})
                         => (contains {:price-per-unit 8
                                       :min-unit-price 6})))

             (fact "row with price-per-unit as min-unit-price"
                   (fact "when price-per-unit less than min price in catalogue row"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 2    ;;Less than min-total-price
                                              :discount-percent 10
                                              :min-total-price 5
                                              :max-total-price 10})
                         => (contains {:price-per-unit 5
                                       :min-unit-price 5
                                       :max-unit-price 10}))

                   (fact "when price-per-unit more than max price in catalogue row"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 15    ;;More than min-total-price
                                              :discount-percent 10
                                              :min-total-price 5
                                              :max-total-price 10})
                         => (contains {:price-per-unit 5
                                       :min-unit-price 5
                                       :max-unit-price 10})))

             (fact "row with price-per-unit as 0"
                   (fact "when price-per-unit larger than max price and min price is nil"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 15   ;; Larger than max price
                                              :discount-percent 10
                                              :min-total-price nil ;; No min price
                                              :max-total-price 10})
                         => (contains {:price-per-unit 0
                                       :max-unit-price 10}))

                   (fact "when min price nil and price-per-unit less than max price"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 6
                                              :discount-percent 10
                                              :min-total-price nil
                                              :max-total-price 10})
                         => (contains {:price-per-unit 6
                                       :max-unit-price 10}))

                   (fact "when max price nil and price-per-unit more than min price"
                         (util/->invoice-row {:code "123"
                                              :text "tuoterivi 1"
                                              :unit "kpl"
                                              :price-per-unit 8
                                              :discount-percent 10
                                              :min-total-price 6
                                              :max-total-price nil})
                         => (contains {:price-per-unit 8
                                       :min-unit-price 6})))
             ))

(facts "between?"
       (fact "returns true when"
             (fact "value between min and max when min and max given"
                   (util/between? 1 3 2) => true ;;directly between
                   (util/between? 1 3 1) => true ;;same as min
                   (util/between? 1 3 3) => true ;;same as max
                   )
             (fact "value greater than or equal to min and max is nil"
                   (util/between? 1 nil 1) => true ;;equals min
                   (util/between? 1 nil 2) => true ;;larger than min
                   )
             (fact "value less than or equal to max and min is nil"
                   (util/between? nil 2 2) => true ;;equals max
                   (util/between? nil 3 2) => true ;;less than than max
                   )
             (fact "bot min and max are nil (= no boundaries)"
                   (util/between? nil nil 1) => true))
       (fact "returns false when"
             (fact "value is nil"
                   (util/between? 1 2 nil) => false)
             (fact "value less than min"
                   (util/between? 1 2 0) => false)
             (fact "value more than max"
                   (util/between? 1 2 3) => false)))

(facts "invoice-row-editable?"
       (fact "returns true when"
             (fact "price-per-unit is set to zero and min and max are not set"
                   (util/unit-price-editable? {:price-per-unit 0}) => truthy
                   (util/unit-price-editable? {:price-per-unit 0
                                               :min-unit-price nil
                                               :max-unit-price nil}) => truthy)

             (fact "price-per-unit is set to non-zero and min or max unit price is set"
                   (util/unit-price-editable? {:price-per-unit 2
                                               :min-unit-price 1
                                               :max-unit-price 10}) => truthy
                   (util/unit-price-editable? {:price-per-unit 2
                                               :min-unit-price 0
                                               :max-unit-price 10}) => truthy
                   (util/unit-price-editable? {:price-per-unit 2
                                               :min-unit-price 2
                                               :max-unit-price nil}) => truthy
                   (util/unit-price-editable? {:price-per-unit 2
                                               :min-unit-price nil
                                               :max-unit-price 9}) =>  truthy))

       (fact "returns false when"
             (fact "price-per-unit is set to non-zero value set and no min-unit-price or max-unit-price set"
                   (util/unit-price-editable? {:price-per-unit 1}) => falsey
                   (util/unit-price-editable? {:price-per-unit -1}) => falsey
                   (util/unit-price-editable? {:price-per-unit 1
                                               :min-unit-price nil
                                               :max-unit-price nil}) => falsey)
             (fact "price-per-unit is set to non-zero"
                   (fact "and min-unit-price is zero and max-unit-price is nil"
                         (util/unit-price-editable? {:price-per-unit 1
                                                     :min-unit-price 0
                                                     :max-unit-price nil}) => falsey)

                   (fact "and min-unit-price is nil and max-unit-price is zero"
                         (util/unit-price-editable? {:price-per-unit 1
                                                     :min-unit-price nil
                                                     :max-unit-price 0}) => falsey)

                   (fact "and both min-unit-price and max-unit-price are zero"
                         (util/unit-price-editable? {:price-per-unit 8
                                                     :min-unit-price 0
                                                     :max-unit-price 0}) => falsey))))
