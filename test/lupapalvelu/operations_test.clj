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

(facts "verify that every operation has link-permit-required set"
  (doseq [[op propeties] operations]
    (let [result (doc-result (contains? propeties :link-permit-required) op)]
      (fact result => (doc-check truthy)))))

(facts "check that correct operations requires linkPermit"
  (fact (:ya-jatkoaika link-permit-required-operations) => truthy)
  (fact (:tyonjohtajan-nimeaminen link-permit-required-operations) => truthy)
  (fact (:suunnittelijan-nimeaminen link-permit-required-operations) => truthy)
  (fact (:jatkoaika link-permit-required-operations) => truthy)
  (fact (:aloitusoikeus link-permit-required-operations) => truthy)
  (fact (count link-permit-required-operations) => 5))

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
    (operations-for-permit-type "R" true) => (vector
                                               ["Rakentaminen ja purkaminen"
                                                [["Uuden rakennuksen rakentaminen"
                                                  [["Asuinrakennus" :asuinrakennus]
                                                   ["Vapaa-ajan asuinrakennus" :vapaa-ajan-asuinrakennus]
                                                   ["Varasto, sauna, autotalli tai muu talousrakennus" :varasto-tms]
                                                   ["Julkinen rakennus" :julkinen-rakennus]
                                                   ["Muun rakennuksen rakentaminen" :muu-uusi-rakentaminen]]]
                                                 ["Rakennuksen korjaaminen tai muuttaminen"
                                                  [["Rakennuksen laajentaminen tai korjaaminen" :laajentaminen]
                                                   ["Perustusten tai kantavien rakenteiden muuttaminen tai korjaaminen" :perus-tai-kant-rak-muutos]
                                                   ["Kayttotarkoituksen muutos" :kayttotark-muutos]
                                                   ["Rakennuksen julkisivun tai katon muuttaminen" :julkisivu-muutos]
                                                   ["Asuinhuoneiston jakaminen tai yhdistaminen"
                                                    :jakaminen-tai-yhdistaminen]
                                                   ["Markatilan laajentaminen" :markatilan-laajentaminen]
                                                   ["Takan ja savuhormin rakentaminen" :takka-tai-hormi]
                                                   ["Parvekkeen tai terassin lasittaminen" :parveke-tai-terassi]
                                                   ["Muu rakennuksen muutostyo" :muu-laajentaminen]]]
                                                 ["Rakennelman rakentaminen"
                                                  [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
                                                   ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
                                                   ["Mainoslaite" :mainoslaite]
                                                   ["Aita" :aita]
                                                   ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
                                                   ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]
                                                   ["Muun rakennelman rakentaminen" :muu-rakentaminen]]]
                                                 ["Rakennuksen purkaminen" :purkaminen]]]
                                               ["Elinympariston muuttaminen"
                                                [["Maisemaa muutava toimenpide"
                                                  [["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
                                                   ["Puun kaataminen" :puun-kaataminen]
                                                   ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
                                                 ["Tontti tai korttelialueen jarjestelymuutos"
                                                  [["Tontin ajoliittyman muutos" :tontin-ajoliittyman-muutos]
                                                   ["Paikoitusjarjestelyihin liittyvat muutokset" :paikoutysjarjestus-muutos]
                                                   ["Korttelin yhteisiin alueisiin liittyva muutos" :kortteli-yht-alue-muutos]
                                                   ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]]]]]
                                               (when (env/feature? :pima)
                                                 ["Ymp\u00e4rist\u00f6luvat" [["Pima" :pima]]])))

  (fact "poikkarit"
    (operations-for-permit-type "P" false) => [["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis]])


  (when (env/feature? :ymparisto)
    (fact "meluilmoitus"
          (operations-for-permit-type "YI" false) => [["Ymp\u00e4rist\u00f6luvat" [["Meluilmoitus" :meluilmoitus]]]]))

  (when (env/feature? :ymparisto)
    (fact "ymparistolupa"
          (operations-for-permit-type "YL" false) => [["Ymp\u00e4rist\u00f6luvat"
                                                       [["ympariston-pilaantumisen-vaara" [["uusi-toiminta" :yl-uusi-toiminta]
                                                                                           ["olemassa-oleva-toiminta" :yl-olemassa-oleva-toiminta]
                                                                                           ["toiminnan-muutos" :yl-toiminnan-muutos]]]]]])))

