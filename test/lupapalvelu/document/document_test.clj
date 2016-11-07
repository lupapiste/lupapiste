(ns lupapalvelu.document.document-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [unauthorized? ok?]]
            [lupapalvelu.document.document-api :as dapi]
            [lupapalvelu.document.document :refer [create-doc-validator]]))

(testable-privates lupapalvelu.document.document deny-remove-of-last-document document-assignment-info)

(facts create-doc-validator
  ; type is "YA" and at least one doc is "party" -> fail
  (create-doc-validator {:application {:permitType "YA"
                                       :documents [{:schema-info {:type "festivity"}}
                                                   {:schema-info {:type "party"}}
                                                   {:schema-info {:type "celebration"}}]}}) => {:ok false, :text "error.create-doc-not-allowed"}
  ; none of the docs are "party"
  (create-doc-validator {:application {:permitType "YA"
                                       :documents [{:schema-info {:type "festivity"}}
                                                   {:schema-info {:type "celebration"}}]}}) => nil
  ; type is not "YA"
  (create-doc-validator {:application {:permitType "R"
                                       :documents [{:schema-info {:type "festivity"}}
                                                   {:schema-info {:type "party"}}
                                                   {:schema-info {:type "celebration"}}]}}) => nil)

(facts "deny-remove-of-last-document"
  (deny-remove-of-last-document nil nil) => falsey
  (deny-remove-of-last-document {} nil) => falsey
  (deny-remove-of-last-document nil []) => falsey

  (fact "poikkeamis rakennushanke"
    (let [rakennushanke {:schema-info {:name "rakennushanke"}}]
      (deny-remove-of-last-document rakennushanke {:documents []}) => true
      (deny-remove-of-last-document rakennushanke {:documents [rakennushanke]}) => true
      (deny-remove-of-last-document rakennushanke {:documents [rakennushanke rakennushanke]}) => falsey))

  (fact "suunnittelija"
    (let [suunnittelija {:schema-info {:name "suunnittelija"}}]
     (deny-remove-of-last-document suunnittelija {:documents [suunnittelija]}) => falsey
     (deny-remove-of-last-document suunnittelija {:documents [suunnittelija suunnittelija]}) => falsey)))

(facts "document user-authz-roles validation"
  (let [command {:application
                 {:auth [{:id "1" :role "guest"}
                         {:id "2" :role "owner"}
                         {:id "3" :role "writer"}
                         {:id "4" :role "foreman"}]
                  :organization "000-R"
                  :documents [{:id "1", :schema-info {:name "tyonjohtaja-v2"}}
                              {:id "2", :schema-info {:name "hakija-tj"}}]}}]

    (fact "guest does not have access to docs"
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "1"), (assoc-in [:user :id] "1"))) => unauthorized?
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "2"), (assoc-in [:user :id] "1"))) => unauthorized?)

    (fact "owner has access to docs"
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "1"), (assoc-in [:user :id] "2"))) => nil
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "2"), (assoc-in [:user :id] "2"))) => nil)

    (fact "writer has access to docs"
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "1"), (assoc-in [:user :id] "3"))) => nil
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "2"), (assoc-in [:user :id] "3"))) => nil)

    (fact "authority has access to docs"
      (let [authority {:id "-1", :orgAuthz {:000-R #{:authority}}, :role "authority"}]
        (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "1"), (assoc :user authority))) => nil
        (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "2"), (assoc :user authority))) => nil))

    (fact "foreman has access to foreman doc"
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "1"), (assoc-in [:user :id] "4"))) => nil)

    (fact "foreman does not have access to applicant doc"
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "2"), (assoc-in [:user :id] "4"))) => unauthorized?)))

(facts document-assignment-info
  (fact "yritys"
    (document-assignment-info nil
                              {:id "4e2d57b9eb6b91890f33efd7",
                               :schema-info {:name "hakija-r",
                                             :i18name "osapuoli",
                                             :type "party",
                                             :accordion-fields [["_selected"]
                                                                ["henkilo" "henkilotiedot" "etunimi"]
                                                                ["henkilo" "henkilotiedot" "sukunimi"]
                                                                ["yritys" "yritysnimi"]],
                                             :subtype "hakija"}
                               :data {:_selected {:value "yritys"},
                                      :henkilo {:userId {:value nil},
                                                :henkilotiedot {:etunimi {:value "Pena", :modified 1458471382290},
                                                                :sukunimi {:value "Panaani", :modified 1458471382290}}},
                                      :yritys {:yritysnimi {:value "Firma 5", :modified 1458471382290},
                                               :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}, :sukunimi {:value ""}}}}}})
    => {:id "4e2d57b9eb6b91890f33efd7", :type-key "hakija-r._group_label", :description "Firma 5"})

  (fact "henkilo"
    (document-assignment-info nil
                              {:id "4e2d57b9eb6b91890f33efd7",
                               :schema-info {:name "hakija-r",
                                             :i18name "osapuoli",
                                             :type "party",
                                             :accordion-fields [["_selected"]
                                                                ["henkilo" "henkilotiedot" "etunimi"]
                                                                ["henkilo" "henkilotiedot" "sukunimi"]
                                                                ["yritys" "yritysnimi"]],
                                             :subtype "hakija"}
                               :data {:_selected {:value "henkilo"},
                                      :henkilo {:userId {:value nil},
                                                :henkilotiedot {:etunimi {:value "Pena", :modified 1458471382290},
                                                                :sukunimi {:value "Panaani", :modified 1458471382290}}},
                                      :yritys {:yritysnimi {:value "Firma 5", :modified 1458471382290},
                                               :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}, :sukunimi {:value ""}}}}}})
    => {:id "4e2d57b9eb6b91890f33efd7", :type-key "hakija-r._group_label", :description "Pena Panaani"})

  (fact "without accordion fields"
    (document-assignment-info nil
                              {:id "4e2d57b9eb6b91890f33efd7",
                               :schema-info {:name "hakija-r",
                                             :i18name "osapuoli",
                                             :type "party",,
                                             :subtype "hakija"}
                               :data {:_selected {:value "henkilo"},
                                      :henkilo {:userId {:value nil},
                                                :henkilotiedot {:etunimi {:value "Pena", :modified 1458471382290},
                                                                :sukunimi {:value "Panaani", :modified 1458471382290}}},
                                      :yritys {:yritysnimi {:value "Firma 5", :modified 1458471382290},
                                               :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}, :sukunimi {:value ""}}}}}})
    => {:id "4e2d57b9eb6b91890f33efd7", :type-key "hakija-r._group_label"}))

(fact "operation doc description > accordion-field"
  (document-assignment-info [{:id "581c7225b5a61a6ed47bfb43"
                              :description "FOO"}]
                            {:id "581c7226b5a61a6ed47bfb48",
                             :schema-info {:op {:id "581c7225b5a61a6ed47bfb43",
                                                :name "kerrostalo-rivitalo",
                                                :description nil,
                                                :created 1478259237586},
                                           :removable true,
                                           :name "uusiRakennus",
                                           :approvable true,
                                           :accordion-fields [["valtakunnallinenNumero"]],
                                           :version 1},
                             :created 1478259237586,
                             :data {:valtakunnallinenNumero {:value "1234"}}}) => {:id "581c7226b5a61a6ed47bfb48", :type-key "uusiRakennus._group_label", :description "FOO"})

(fact "without operation description"
  (document-assignment-info [{:id "581c7225b5a61a6ed47bfb43"
                              :description nil}]
                            {:id "581c7226b5a61a6ed47bfb48",
                             :schema-info {:op {:id "581c7225b5a61a6ed47bfb43",
                                                :name "kerrostalo-rivitalo",
                                                :description nil,
                                                :created 1478259237586},
                                           :removable true,
                                           :name "uusiRakennus",
                                           :approvable true,
                                           :accordion-fields [["valtakunnallinenNumero"]],
                                           :version 1},
                             :created 1478259237586,
                             :data {:valtakunnallinenNumero {:value "1234"}}}) => {:id "581c7226b5a61a6ed47bfb48", :type-key "uusiRakennus._group_label", :description "1234"})
