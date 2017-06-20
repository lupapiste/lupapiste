(ns lupapalvelu.matti_test
  (:require [lupapalvelu.matti.schemas :as schemas]
            [lupapalvelu.matti.shared  :as shared]
            [midje.sweet :refer :all]))

(facts "Verdict template schema data"

  (fact "section"
    (:section (schemas/schema-data shared/default-verdict-template
                                   ["matti-foremen" "pdf"]))
    => (contains {:path [:pdf]}))

  (fact "date-delta"
    (:date-delta (schemas/schema-data shared/default-verdict-template
                                      ["matti-verdict" "1" "3" "lainvoimainen" "enabled"]))
    => (contains {:path [:enabled]
                  :data {:unit :days}}))

  (fact "docgen: select"
    (:docgen (schemas/schema-data shared/default-verdict-template
                                  ["matti-verdict" "2" "0" "giver"]))
    => (contains {:path [:giver]
                  :schema {:info {:name "matti-verdict-giver" :version 1}
                           :body '({:name "matti-verdict-giver"
                                   :type :select
                                   :body [{:name "viranhaltija"}
                                          {:name "lautakunta"}]})}
                  :data "matti-verdict-giver"}))

  (fact "docgen: checkbox"
    (:docgen (schemas/schema-data shared/default-verdict-template
                                  ["matti-buildings" "0" "0" "info" "1" "vss-luokka"]))
    => (contains {:path [:vss-luokka]
                  :schema {:info {:name "matti-verdict-check", :version 1}
                           :body '({:name "matti-verdict-check"
                                    :type :checkbox})}
                  :data "matti-verdict-check"}))

  (fact "reference-list: select"
    (:reference-list (schemas/schema-data shared/default-verdict-template
                                          ["matti-verdict" "2" "1" "verdict-code"]))
    => (contains {:path [:verdict-code],
                  :data (contains {:path [:settings :matti-r :0 :0 :verdict-code]
                                   :type :select})}))

  (fact "reference-list: multi-select"
    (:reference-list (schemas/schema-data shared/default-verdict-template
                                          ["matti-reviews" "0" "0" "small"]))
    => (contains {:path [:small],
                  :data (contains {:path [:settings :matti-r :2 :0 :reviews]
                                   :type :multi-select})})))

(facts "Settings template schema data"
  )
