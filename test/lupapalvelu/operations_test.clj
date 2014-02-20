(ns lupapalvelu.operations-test
  (:require [lupapalvelu.operations :refer :all]
            [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.document.schemas :as schemas]))

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

  (fact "poikkarit"
    (operations-for-permit-type "P") => [["Poikkeusluvat ja suunnittelutarveratkaisut" :poikkeamis]])

  (fact "meluilmoitus"
    (operations-for-permit-type "YI") => [["Ymp\u00e4rist\u00f6luvat" [["Meluilmoitus" :meluilmoitus]]]])

  (fact "ymparistolupa"
    (operations-for-permit-type "YL") => [["Ymp\u00e4rist\u00f6luvat"
                                           [["uusi-toiminta" :yl-uusi-toiminta]
                                            ["olemassa-oleva-toiminta" :yl-olemassa-oleva-toiminta]
                                            ["toiminnan-muutos" :yl-toiminnan-muutos]
                                            ["lupamaaraysten-tarkistaminen" :yl-lupamaaraysten-tarkistaminen]
                                            ["toiminnan-aloittamislupa" :yl-toiminnan-aloittamislupa]]]])

  )

