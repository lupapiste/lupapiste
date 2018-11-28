(ns lupapalvelu.invoices.shared.util-test
  (:require
   [lupapalvelu.invoices.shared.util :as util]
   [midje.sweet :refer :all]))

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
