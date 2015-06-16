(ns lupapalvelu.authorization-api-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.test-util :refer :all]
            [lupapalvelu.authorization-api :refer :all]
            ))

(facts "generate-remove-invalid-user-from-docs-updates"
  (generate-remove-invalid-user-from-docs-updates nil) => empty?
  (generate-remove-invalid-user-from-docs-updates {:auth nil, :documents nil}) => empty?

  (generate-remove-invalid-user-from-docs-updates {:auth nil
                                                   :documents [{:schema-info {:name "hakija-r" :version 1}
                                                                :data {:henkilo {:userId {:value "123"}}}}
                                                               {:schema-info {:name "hakija" :version 1}
                                                                :data {:henkilo {:userId {:value "345"}}}}]})
  => {"documents.0.data.henkilo.userId" ""
      "documents.1.data.henkilo.userId" ""}

  (generate-remove-invalid-user-from-docs-updates {:auth [{:id "123"}]
                                                   :documents [{:schema-info {:name "hakija-r" :version 1}
                                                                :data {:henkilo {:userId {:value "123"}}}}]})
  => empty?

  (generate-remove-invalid-user-from-docs-updates {:auth nil
                                                   :documents [{:schema-info {:name "uusiRakennus" :version 1}
                                                                :data {:rakennuksenOmistajat {:0 {:henkilo {:userId {:value "123"}}}
                                                                                              :1 {:henkilo {:userId {:value "345"}}}}}}]})
  => {"documents.0.data.rakennuksenOmistajat.0.henkilo.userId" ""
      "documents.0.data.rakennuksenOmistajat.1.henkilo.userId" ""}

  )
