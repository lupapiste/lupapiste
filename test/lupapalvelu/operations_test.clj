(ns lupapalvelu.operations-test
  (:require [clojure.set :as set]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.operations :refer :all]
            [lupapalvelu.test-util :refer :all]
            [lupapiste-commons.operations :as commons-operations]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]
            [sade.env :as env]))

(testable-privates lupapalvelu.operations r-operations p-operations)

(facts "check that every operation refers to existing schema"
  (doseq [[_ {:keys [schema required]}] operations
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
  (fact "operations count" (count link-permit-required-operations) => 8))

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
            (check-leaf ops)))))))

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
                                                      ["Asuinhuoneiston jakaminen tai yhdistaminen" :jakaminen-tai-yhdistaminen]
                                                      ["rakennustietojen-korjaus" :rakennustietojen-korjaus]]]
                                                    ["Rakennelman rakentaminen"
                                                     [["Auto- tai grillikatos, vaja, kioski tai vastaava" :auto-katos]
                                                      ["Masto, piippu, sailio, laituri tai vastaava" :masto-tms]
                                                      ["Mainoslaite" :mainoslaite]
                                                      ["Aita" :aita]
                                                      ["Maalampokaivon poraaminen tai lammonkeruuputkiston asentaminen" :maalampo]
                                                      ["Rakennuksen jatevesijarjestelman uusiminen" :jatevesi]]]
                                                    ["Rakennuksen purkaminen" :purkaminen]
                                                    ["Maisemaa muuttava toimenpide"
                                                     [["Puun kaataminen" :puun-kaataminen]
                                                      ["tontin-jarjestelymuutos" :tontin-jarjestelymuutos]
                                                      ["Muu-tontti-tai-korttelialueen-jarjestelymuutos" :muu-tontti-tai-kort-muutos]
                                                      ["Kaivaminen, louhiminen tai maan tayttaminen" :kaivuu]
                                                      ["Muu maisemaa muuttava toimenpide" :muu-maisema-toimenpide]]]
                                                    ["rakennustyo-muutostoimenpiteet"
                                                     [["rak-valm-tyo" :rak-valm-tyo]]]
                                                    ["rasite-tai-yhteisjarjestely" :rasite-tai-yhteisjarjestely]]]]))

  (fact "poikkarit"
    (let [filtering-fn (fn [node] (= "P" (permit-type-of-operation node)))]
      (operations-filtered filtering-fn false) => [["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis]]))

  (fact "ympäristönsuojelulain-mukaiset-ilmoitukset"
    (let [filtering-fn (fn [node] (= "YI" (permit-type-of-operation node)))
          expected
            [["Ymp\u00e4rist\u00f6luvat"
              [["ymparistonsuojelulain-mukaiset-ilmoitukset"
                [["Meluilmoitus" :meluilmoitus]
                 ["yleinen-ilmoitus" :yleinen-ilmoitus]
                 ["ylijaamamaiden-hyodyntaminen" :ylijaamamaiden-hyodyntaminen]
                 ["jatteiden-hyodyntaminen-maarakentamisessa" :jatteiden-hyodyntaminen-maarakentamisessa]
                 ["rekisterointi-ilmoitus" :rekisterointi-ilmoitus]]]
               ["maatalouden-ilmoitukset"
                [["lannan-levittamisesta-poikkeustilanteessa" :lannan-levittamisesta-poikkeustilanteessa]]]
               ["leirintaalueilmoitus" :leirintaalueilmoitus]
               ["kirjallinen-vireillepano" :kirjallinen-vireillepano]]]]]
      (operations-filtered filtering-fn false) => expected))

  (fact "muut-ymparisto-luvat"
    (let [filtering-fn (fn [node] (= "YM" (permit-type-of-operation node)))
          expected
            [["Ympäristöluvat"
              [["ymparistonsuojelulain-mukaiset-ilmoitukset"
                [["koeluontoinen-toiminta" :koeluontoinen-toiminta]
                 ["ilmoitus-poikkeuksellisesta-tilanteesta" :ilmoitus-poikkeuksellisesta-tilanteesta]]]
               ["maatalouden-ilmoitukset" [["lannan-varastointi-v2" :lannan-varastointi-v2]]]
               ["kunnan-ympmaarayksista-poikkeaminen"
                [["kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan"
                  :kaytostapoistetun-oljy-tai-kemikaalisailion-jattaminen-maaperaan]
                 ["talousjatevesien-kasittelysta-poikkeaminen" :talousjatevesien-kasittelysta-poikkeaminen]]]
               ["maa-ainekset"
                [["maa-ainesten-kotitarveotto" :maa-ainesten-kotitarveotto]]]
               ["yhteiskasittely-maa-aines-ja-ymparistoluvalle" :yhteiskasittely-maa-aines-ja-ymparistoluvalle]
               ["jatelain-mukaiset-ilmoitukset"
                [["jatteen-keraystoiminta" :jatteen-keraystoiminta]]]
               ["luonnonsuojelulain-mukaiset-ilmoitukset"
                [["muistomerkin-rauhoittaminen" :muistomerkin-rauhoittaminen]]]
               ["maastoliikennelaki-kilpailut-ja-harjoitukset" :maastoliikennelaki-kilpailut-ja-harjoitukset]
               ["vesiliikennelaki-kilpailut-ja-harjoitukset" :vesiliikennelaki-kilpailut-ja-harjoitukset]
               ["ymparistoluvan-selventaminen"
                [["ymparistoluvan-selventaminen" :ymparistoluvan-selventaminen]]]]]]]
      (operations-filtered filtering-fn false) => expected))

  (fact "ymparistolupa"
    (let [filtering-fn (fn [node] (= "YL" (permit-type-of-operation node)))]
      (operations-filtered filtering-fn false)
      =>
      [["Ymp\u00e4rist\u00f6luvat"
        [["Pima" :pima]
         ["ympariston-pilaantumisen-vaara" [["uusi-toiminta" :yl-uusi-toiminta]
                                            ["olemassa-oleva-toiminta" :yl-olemassa-oleva-toiminta]
                                            ["toiminnan-muutos" :yl-toiminnan-muutos]]]
         ["puiden-kaataminen" [["ilmoitus-puiden-kaatamisesta-asemakaava-alueella" :yl-puiden-kaataminen]]]]]])))

(facts "operation definitions in lupapalvelu.operations should match lupapiste-commons.operations"
  (fact "R-operations match"
    (let [lp-ops (set (keys @r-operations))
          commons-ops (set commons-operations/r-operations)]
      (set/difference lp-ops commons-ops) => #{}))

  (fact "YA-operations match"
    (let [lp-ops (set (keys ya-operations))
          commons-ops (set commons-operations/ya-operations)]
      (= lp-ops commons-ops) => true))

  (fact "P-operations match"
    (let [lp-ops (set (keys @p-operations))
          commons-ops (set commons-operations/p-operations)]
      (= lp-ops commons-ops) => true)))

(fact "operation-id exists?"
  (operation-id-exists? {} nil) => false
  (operation-id-exists? nil nil) => false
  (operation-id-exists? {} "foo") => false
  (operation-id-exists? {:primaryOperation {:id nil}} nil) => false
  (operation-id-exists? {:primaryOperation {:id "123"}} nil) => false
  (operation-id-exists? {:primaryOperation {:id "123"}} "1") => false
  (operation-id-exists? {:primaryOperation {:id "123"}} "123") => true
  (operation-id-exists? {:primaryOperation    {:id "123"}
                         :secondaryOperations [{:id "321"}]} "123") => true
  (operation-id-exists? {:primaryOperation    {:id "123"}
                         :secondaryOperations [{:id "123"}]} "123") => true
  (operation-id-exists? {:primaryOperation    {:id "123"}
                         :secondaryOperations [{:id "321"} {:id "1"}]} "1") => true
  (operation-id-exists? {:primaryOperation    {:id "123"}
                         :secondaryOperations [{:id "321"} {:id "1"}]} 1) => false)

(facts "get-selected-operation-infos creates a projection on the selected operation metadata."
  (fact "operation :permit-type is required and used in info request (Neuvontapyyntö) view to
         decide what localization keys should be used in the forms"
    (let [actual (get-selected-operation-infos [{:selected-operations ["asuinrakennus"]}
                                                {:selected-operations ["leirintaalueilmoitus"]}])]
     (get-in actual [:asuinrakennus :permit-type]) => "R"
     (get-in actual [:leirintaalueilmoitus :permit-type]) => "YI")))

(facts "selected-operations-for-organizations"
  (fact "deprecated operations are not in the operation tree"
    (selected-operations-for-organizations [{:selected-operations ["asuinrakennus"]}])
    => [])

  (fact "overlapping selected operations do not affect the form of the operation tree"
    (selected-operations-for-organizations [{:selected-operations ["mainoslaite"
                                                                   "yleiskaava"
                                                                   "laajentaminen"]}
                                            {:selected-operations ["yleiskaava"
                                                                   "laajentaminen"
                                                                   "auto-katos"]}
                                            {:selected-operations ["laajentaminen"
                                                                   "auto-katos"
                                                                   "maasto-tms"]}])
    => (selected-operations-for-organizations [{:selected-operations ["mainoslaite"
                                                                      "yleiskaava"]}
                                               {:selected-operations ["laajentaminen"
                                                                      "auto-katos"
                                                                      "maasto-tms"]}]))

  (fact "the operation tree can be further pruned by providing a predicate function as a second argument"
    (selected-operations-for-organizations [{:selected-operations ["pientalo"
                                                                   "ya-katulupa-vesi-ja-viemarityot"]}]
                                           #(= "YA" (permit-type-of-operation %)))
    => (selected-operations-for-organizations [{:selected-operations ["ya-katulupa-vesi-ja-viemarityot"]}])))

(defn check-site-responsible
  "Operation must either have `tyomaastaVastaava` as required or `tyomaasta-vastaava-optional` as
  optional but not both."
  [[op-name {:keys [required optional]}]]
  (let [tv-req? (util/includes-as-kw? required :tyomaastaVastaava)
        tv-opt? (util/includes-as-kw? optional :tyomaastaVastaava)
        tvo-req? (util/includes-as-kw? required :tyomaasta-vastaava-optional)
        tvo-opt? (util/includes-as-kw? optional :tyomaasta-vastaava-optional)]
    (facts {:midje/description (name op-name)}
      (fact "tyomaastaVastaava never optional"
        tv-opt? => false)
      (fact "tyomaasta-vastaava-optional never required"
        tvo-req? => false)
      (fact "Either tyomaastaVastaava or tyomaasta-vastaava-optional but not both"
        (or (and tv-req? (not tvo-opt?))
            (and tvo-opt? (not tv-req?))) => true))))

(facts "YA: tyomaastaVastaava vs. tyomaasta-vastaava-optional"
  (doseq [op ya-operations]
    (check-site-responsible op)))
