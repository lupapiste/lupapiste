(ns lupapalvelu.invoices.shared.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.invoices.shared.util :as util]))

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
