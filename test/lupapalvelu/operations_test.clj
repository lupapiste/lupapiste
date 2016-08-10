(ns lupapalvelu.operations-test
  (:require [lupapalvelu.operations :refer :all]
            [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [sade.env :as env]))

(facts "check that every operation refers to existing schema"
  (doseq [[op {:keys [schema required]}] operations
          schema (cons schema required)]
    (schemas/get-schema (schemas/get-latest-schema-version) schema) => truthy))

(facts "check that correct operations require a linkPermit"
  (fact "operation names"
    (let [ops [:ya-jatkoaika
               :tyonjohtajan-nimeaminen
               :tyonjohtajan-nimeaminen-v2
               :suunnittelijan-nimeaminen
               :jatkoaika
               :aloitusoikeus
               :raktyo-aloit-loppuunsaat]]
      (every? link-permit-required-operations ops) => truthy))
  (fact "operations count" (count link-permit-required-operations) => 7))

(defn- check-leaf [pair]
  (fact (count pair) => 2)
  (fact (first pair) => string?)
  (fact (second pair) => keyword?)
  (second pair))

(defn- check-tree [tree & [results]]
  (flatten
    (concat
      (for [ops tree]
        (do
          (facts "tree node is not a single value"
           (string? ops) => falsey
           (count ops) => (partial <= 2))
          (if (sequential? (second ops))
            (concat results (check-tree (second ops)))
            (check-leaf ops))) ))))

(let [leaf-operations (check-tree operation-tree)]
  (fact "Operation tree has no duplicates"
    (count leaf-operations) => (count (distinct leaf-operations)))
  (fact "Meta: all operations were checked"
    (count leaf-operations) => (count (filter keyword? (flatten operation-tree)))))


(facts "operations-for-permit-type"

  (fact "rakval, only addable operations included"
    (let [filtering-fn (fn [node] (= "R" (permit-type-of-operation node)))]
      (operations-filtered filtering-fn true) => [["Rakentaminen ja purkaminen"
                                                   [["Uuden rakennuksen rakentaminen"
                                                     [["kerrostalo-rivitalo" :kerrostalo-rivitalo]
                                                      ["pientalo" :pientalo]
                                                      ["Vapaa-ajan asuinrakennus" :vapaa-ajan-asuinrakennus]
                                                      ["Varasto, sauna, autotalli tai muu talousrakennus" :varasto-tms]
                                                      ["teollisuusrakennus" :teollisuusrakennus]
                                                      ["Muun rakennuksen rakentaminen" :muu-uusi-rakentaminen]]]
                                                    ["Rakennuksen-laajentaminen"
                                                     [["kerrostalo-rt-laaj" :kerrostalo-rt-laaj]
                                                      ["pientalo-laaj" :pientalo-laaj]
                                                      ["vapaa-ajan-rakennus-laaj" :vapaa-ajan-rakennus-laaj]
                                                      ["talousrakennus-laaj" :talousrakennus-laaj]
                                                      ["teollisuusrakennus-laaj" :teollisuusrakennus-laaj]
                                                      ["muu-rakennus-laaj" :muu-rakennus-laaj]]]
                                                    ["Rakennuksen korjaaminen tai muuttaminen"
                                                     [["kayttotark-muutos" :kayttotark-muutos]
                                                      ["sisatila-muutos" :sisatila-muutos]
                                                      ["Rakennuksen julkisivun tai katon muuttaminen" :julkisivu-muutos]
                                                      ["Markatilan laajentaminen" :markatilan-laajentaminen]
                                                      ["linjasaneeraus" :linjasaneeraus]
                                                      ["Parvekkeen tai terassin lasittaminen" :parveke-tai-terassi]
                                                      ["Perustusten tai kantavien rakenteiden muuttaminen tai korjaaminen" :perus-tai-kant-rak-muutos]
                                                      ["Takan ja savuhormin rakentaminen" :takka-tai-hormi]
                                                      ["Asuinhuoneiston jakaminen tai yhdistaminen" :jakaminen-tai-yhdistaminen]]]
                                                    ["Rakennelman rakentaminen"
                                                     [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
                                                      ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
                                                      ["Mainoslaite" :mainoslaite]
                                                      ["Aita" :aita]
                                                      ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
                                                      ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]]]
                                                    ["Rakennuksen purkaminen" :purkaminen]
                                                    ["Maisemaa muutava toimenpide"
                                                     [["Puun kaataminen" :puun-kaataminen]
                                                      ["tontin-jarjestelymuutos" :tontin-jarjestelymuutos]
                                                      ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]
                                                      ["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
                                                      ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
                                                    ["rakennustyo-muutostoimenpiteet"
                                                     [["rak-valm-tyo" :rak-valm-tyo]]]]]]))

  (fact "poikkarit"
    (let [filtering-fn (fn [node] (= "P" (permit-type-of-operation node)))]
      (operations-filtered filtering-fn false) => [["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis]]))


  (fact "meluilmoitus"
    (let [filtering-fn (fn [node] (= "YI" (permit-type-of-operation node)))]
      (operations-filtered filtering-fn false) => [["Ymp\u00e4rist\u00f6luvat"
                                                    [["ymparistonsuojelulain-mukaiset-ilmoitukset"
                                                      [["Meluilmoitus" :meluilmoitus]]]]]]))

  (fact "ymparistolupa"
    (let [filtering-fn (fn [node] (= "YL" (permit-type-of-operation node)))]
      (operations-filtered filtering-fn false) => [["Ymp\u00e4rist\u00f6luvat"
                                                    [["Pima" :pima]
                                                     ["ympariston-pilaantumisen-vaara" [["uusi-toiminta" :yl-uusi-toiminta]
                                                                                        ["olemassa-oleva-toiminta" :yl-olemassa-oleva-toiminta]
                                                                                        ["toiminnan-muutos" :yl-toiminnan-muutos]]]
                                                     ["puiden-kaataminen" [["ilmoitus-puiden-kaatamisesta-asemakaava-alueella" :yl-puiden-kaataminen]]]]]])))
