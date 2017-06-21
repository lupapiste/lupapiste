(ns lupapalvelu.matti_test
  (:require [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared  :as shared]
            [midje.sweet :refer :all]
            [schema.core :refer [defschema] :as sc]))

(facts "Verdict template schema data"

  (fact "section"
    (:section (schemas/schema-data shared/default-verdict-template
                                   ["matti-foremen" "pdf"]))
    => (contains {:path [:pdf]}))

  (fact "date-delta"
    (:date-delta (schemas/schema-data shared/default-verdict-template
                                      ["matti-verdict" "1" "lainvoimainen" "enabled"]))
    => (contains {:path [:enabled]
                  :data {:unit :days}}))

  (fact "docgen: select"
    (:docgen (schemas/schema-data shared/default-verdict-template
                                  ["matti-verdict" "2" "giver"]))
    => (contains {:path []
                  :schema {:info {:name "matti-verdict-giver" :version 1}
                           :body '({:name "matti-verdict-giver"
                                   :type :select
                                   :body [{:name "viranhaltija"}
                                          {:name "lautakunta"}]})}
                  :data "matti-verdict-giver"}))

  (fact "docgen: checkbox"
    (:docgen (schemas/schema-data shared/default-verdict-template
                                  ["matti-buildings" "0" "0" "vss-luokka"]))
    => (contains {:path []
                  :schema {:info {:name "matti-verdict-check", :version 1}
                           :body '({:name "matti-verdict-check"
                                    :type :checkbox})}
                  :data "matti-verdict-check"}))

  (fact "reference-list: select"
    (:reference-list (schemas/schema-data shared/default-verdict-template
                                          ["matti-verdict" "2" "1" "verdict-code"]))
    => (contains {:path [:verdict-code],
                  :data (contains {:path [:settings :verdict :0 :verdict-code]
                                   :type :select})}))

  (fact "reference-list: multi-select"
    (:reference-list (schemas/schema-data shared/default-verdict-template
                                          ["matti-reviews" "0" "0" "small"]))
    => (contains {:path [:small],
                  :data (contains {:path [:settings :reviews :0 :reviews]
                                   :type :multi-select})})))

(facts "Settings template schema data"
  (fact "multi-select"
    (:multi-select (schemas/schema-data shared/r-settings
                                ["verdict" "0" "0" "verdict-code"]))
    => (contains {:path [:verdict-code]})))

(def test-template
  {:name "test"
   :sections [{:id "one"
               :grid {:columns 4
                      :rows [[{:col 2
                               :id "a"
                               :schema {:docgen "matti-verdict-check"}}]
                             {:id "row"
                              :row [{:col 2
                                     :id "b"
                                     :schema {:date-delta {:unit :years}}}]}]}}
              {:id "two"
               :grid {:columns 2
                      :rows [[{}
                              {:id "c"
                               :schema {:multi-select {:items [:foo :bar]}}}]
                             {:id "list-row"
                              :row [{:id "d"
                                     :schema {:list
                                              {:items [{:schema {:docgen "matti-string"}}
                                                       {:id "delta"
                                                        :schema {:date-delta {:unit :days}}}
                                                       {:id "ref"
                                                        :schema {:reference-list {:type :select
                                                                                  :path [:path :to :somewhere]}}}]}}}]}]}}]})

(facts "Test template is valid"
  (sc/validate shared/MattiVerdict test-template)
  => test-template)

(facts "Id and index are interchangeable for value paths"
  (fact "No row id"
    (let [result {:schema {:info {:name "matti-verdict-check" :version 1}
                           :body '({:name "matti-verdict-check"
                                    :type :checkbox})}
                  :data   "matti-verdict-check"
                  :path []}]
      (:docgen (schemas/schema-data test-template ["one" "0" "0"]))
      => result
      (:docgen (schemas/schema-data test-template ["one" "0" "a"]))
      => result
      (:docgen (schemas/schema-data test-template ["0" "0" "0"]))
      => result))
  (fact "Row id"
    (let [result (contains {:date-delta (contains {:path []
                                                   :data {:unit :years}})})]
      (schemas/schema-data test-template ["one" "row" "b"]) => result
      (schemas/schema-data test-template ["0" "row" "b"]) => result
            (schemas/schema-data test-template ["0" "1" "b"]) => result
      (schemas/schema-data test-template ["0" "1" "0"]) => result
      (schemas/schema-data test-template ["0" "row" "0"]) => result

      (fact "Index can be string, keyword or number. Id can be string or keyword."
        (schemas/schema-data test-template [0 :1 "b"]) => result
        (schemas/schema-data test-template [:0 "1" :b]) => result
        (schemas/schema-data test-template [0 1 :b]) => result))))

(fact "Multi-select"
  (:multi-select (schemas/schema-data test-template ["two" 0 "c"]))
  => (contains {:path []
                :data {:items [:foo :bar]}}))

(fact "List items"
  (:docgen (schemas/schema-data test-template [:two :list-row "d" 0]))
  => (contains {:path []
                :data "matti-string"})
  (:date-delta (schemas/schema-data test-template [:two :list-row "d" :delta :enabled]))
  => (contains {:path [:enabled]
                :data {:unit :days}})
  (:reference-list (schemas/schema-data test-template [:two :list-row 0 2]))
  => (contains {:path []
                :data {:path [:path :to :somewhere]
                       :type :select}}))

(facts "Bad paths"
  (fact "Nil path"
    (schemas/schema-data test-template nil) => nil)
    (fact "Empty path"
      (schemas/schema-data test-template []) => nil)
    (fact "Bad branch"
      (schemas/schema-data test-template ["one" "foo" "bar"]) => nil)
    (fact "Bad leaves are sometimes allowed"
      (schemas/schema-data test-template ["one" "bad"])
      => (contains {:section (contains {:path [:bad]})})
      (schemas/schema-data test-template ["one" 0 "a" "bad"])
      => (contains {:docgen (contains {:path [:bad] :data "matti-verdict-check"})}))
    (fact "Bad section"
      (schemas/schema-data test-template ["bad" "row" "b"]) => nil
      (schemas/schema-data test-template [20 "row" "b"]) => nil
      (schemas/schema-data test-template [-1 "row" "b"]) => nil)
    (fact "Bad row"
      (schemas/schema-data test-template ["one" "bad" "b"]) => nil
      (schemas/schema-data test-template ["one" 30 "b"]) => nil
      (schemas/schema-data test-template ["one" -1 "b"]) => nil)
    (fact "Bad column"
      (schemas/schema-data test-template ["one" "row" "bad"]) => nil
      (schemas/schema-data test-template ["one" "row" 40]) => nil
      (schemas/schema-data test-template ["one" "row" -1]) => nil))
