(ns lupapalvelu.document.document-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [unauthorized? ok?]]
            [lupapalvelu.document.document-api :as dapi]
            [lupapalvelu.document.document :refer [create-doc-validator]]
            [lupapalvelu.document.schemas :as schemas]))

(testable-privates lupapalvelu.document.document
                   deny-remove-of-last-document
                   deny-remove-of-non-removable-doc
                   document-assignment-info
                   describe-parties-assignment-targets
                   describe-non-party-document-assignment-targets)

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
  (fact "default with nil args"
    (deny-remove-of-last-document nil nil nil) => falsey)
  (fact "default with empty schema"
    (deny-remove-of-last-document {} nil nil) => falsey)

  (fact "last removable by none"
    (let [rakennushanke {:schema-info {:name "rakennushanke", :removable-by :all, :last-removable-by :none}}]
      (fact "no docs"
        (deny-remove-of-last-document rakennushanke {:documents []} ..user..) => true
        (provided (lupapalvelu.authorization/application-role {:documents []} ..user..) => :authority))
      (fact "one doc - applicant"
        (deny-remove-of-last-document rakennushanke {:documents [rakennushanke]} ..user..) => true
        (provided (lupapalvelu.authorization/application-role {:documents [rakennushanke]} ..user..) => :applicant))
      (fact "one doc - authority"
        (deny-remove-of-last-document rakennushanke {:documents [rakennushanke]} ..user..) => true
        (provided (lupapalvelu.authorization/application-role {:documents [rakennushanke]} ..user..) => :authority))
      (fact "two docs - applicant"
        (deny-remove-of-last-document rakennushanke {:documents [rakennushanke rakennushanke]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role {:documents [rakennushanke rakennushanke]} ..user..) => :applicant))
      (fact "two docs - authority"
        (deny-remove-of-last-document rakennushanke {:documents [rakennushanke rakennushanke]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role {:documents [rakennushanke rakennushanke]} ..user..) => :authority))))

  (fact "last removable by authority"
    (let [maksaja {:schema-info {:name "maksaja", :removable-by :all, :last-removable-by :authority}}]
      (fact "one doc - applicant"
        (deny-remove-of-last-document maksaja {:documents [maksaja]} ..user..) => true
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :applicant))
      (fact "one doc - authority"
        (deny-remove-of-last-document maksaja {:documents [maksaja]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :authority))
      (fact "two docs - applicant"
        (deny-remove-of-last-document maksaja {:documents [maksaja maksaja]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :applicant))
      (fact "two docs - authority"
        (deny-remove-of-last-document maksaja {:documents [maksaja maksaja]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :authority))))

  (fact "removing last is not restricted"
    (let [suunnittelija {:schema-info {:name "suunnittelija", :removable-by :all}}]
      (fact "one doc"
        (deny-remove-of-last-document suunnittelija {:documents [suunnittelija]} ..user..) => falsey)
      (fact "two docs"
        (deny-remove-of-last-document suunnittelija {:documents [suunnittelija suunnittelija]} ..user..) => falsey))))

(facts "deny-remove-of-non-removable-doc"
  (fact "default with nil args"
    (deny-remove-of-non-removable-doc nil nil nil) => true)
  (fact "default with empty schema"
    (deny-remove-of-non-removable-doc {} nil nil) => true)

  (fact "removable by none"
    (let [doc {:schema-info {:name "rakennushanke", :removable-by :none}}]
      (fact "applicant"
        (deny-remove-of-non-removable-doc doc {:documents [doc]} ..user..) => true
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :applicant))
      (fact "authority"
        (deny-remove-of-non-removable-doc doc {:documents [doc]} ..user..) => true
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :authority))))

  (fact "removable by all"
    (let [doc {:schema-info {:name "rakennushanke", :removable-by :all}}]
      (fact "applicant"
        (deny-remove-of-non-removable-doc doc {:documents [doc]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :applicant))
      (fact "authority"
        (deny-remove-of-non-removable-doc doc {:documents [doc]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :authority))))

  (fact "removable by authority"
    (let [doc {:schema-info {:name "rakennushanke", :removable-by :authority}}]
      (fact "applicant"
        (deny-remove-of-non-removable-doc doc {:documents [doc]} ..user..) => true
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :applicant))
      (fact "authority"
        (deny-remove-of-non-removable-doc doc {:documents [doc]} ..user..) => falsey
        (provided (lupapalvelu.authorization/application-role anything ..user..) => :authority)))))

(facts "document user-authz-roles validation"
  (let [command {:application
                 {:auth [{:id "1" :role "guest"}
                         {:id "2" :role "writer"}
                         {:id "3" :role "writer"}
                         {:id "4" :role "foreman"}]
                  :organization "000-R"
                  :documents [{:id "1", :schema-info {:name "tyonjohtaja-v2"}}
                              {:id "2", :schema-info {:name "hakija-tj"}}]}}]

    (fact "guest does not have access to docs"
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "1"), (assoc-in [:user :id] "1"))) => unauthorized?
      (dapi/validate-user-authz-by-doc (-> command (assoc-in [:data :doc] "2"), (assoc-in [:user :id] "1"))) => unauthorized?)

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

(def selected-accordion-fields [{:type :selected
                                 :paths [["henkilo" "henkilotiedot" "etunimi"]
                                         ["henkilo" "henkilotiedot" "sukunimi"]
                                         ["yritys" "yritysnimi"]]}])

(facts document-assignment-info
  (fact "yritys"
    (document-assignment-info nil
                              {:id "4e2d57b9eb6b91890f33efd7",
                               :schema-info {:name "hakija-r",
                                             :i18name "osapuoli",
                                             :type "party",
                                             :accordion-fields selected-accordion-fields
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
                                             :accordion-fields selected-accordion-fields
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
    => {:id "4e2d57b9eb6b91890f33efd7", :type-key "hakija-r._group_label"})

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
                               :data {:valtakunnallinenNumero {:value "1234"}}})
    => {:id "581c7226b5a61a6ed47bfb48", :type-key "uusiRakennus._group_label", :description "FOO"})

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
                                             :accordion-fields [[["valtakunnallinenNumero"]]],
                                             :version 1},
                               :created 1478259237586,
                               :data {:valtakunnallinenNumero {:value "1234"}}})
    => {:id "581c7226b5a61a6ed47bfb48", :type-key "uusiRakennus._group_label", :description "1234"}))

(facts "sorting of documents for assignment targets"
  (let [schema1 {:id "s1",
                 :schema-info {:name "hakija-r",
                               :i18name "osapuoli",
                               :type "party",
                               :order 1
                               :accordion-fields selected-accordion-fields
                               :subtype "hakija"}
                 :data {:_selected {:value "yritys"},
                        :henkilo {:userId {:value nil},
                                  :henkilotiedot {:etunimi {:value "Pena", :modified 1458471382290},
                                                  :sukunimi {:value "Panaani", :modified 1458471382290}}},
                        :yritys {:yritysnimi {:value "Firma 5", :modified 1458471382290},
                                 :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}, :sukunimi {:value ""}}}}}}
        schema2 {:id "s2",
                 :schema-info {:name "hakija-r",
                               :i18name "osapuoli",
                               :type "party",
                               :accordion-fields selected-accordion-fields
                               :subtype "hakija"}
                 :data {:_selected {:value "yritys"},
                        :henkilo {:userId {:value nil},
                                  :henkilotiedot {:etunimi {:value "Pena", :modified 1458471382290},
                                                  :sukunimi {:value "Panaani", :modified 1458471382290}}},
                        :yritys {:yritysnimi {:value "Firma 5", :modified 1458471382290},
                                 :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}, :sukunimi {:value ""}}}}}}
        schema3 {:id "s3",
                 :schema-info {:name "hakija-r",
                               :i18name "osapuoli",
                               :type "party",
                               :order 3
                               :accordion-fields selected-accordion-fields
                               :subtype "hakija"}
                 :data {:_selected {:value "yritys"},
                        :henkilo {:userId {:value nil},
                                  :henkilotiedot {:etunimi {:value "Pena", :modified 1458471382290},
                                                  :sukunimi {:value "Panaani", :modified 1458471382290}}},
                        :yritys {:yritysnimi {:value "Firma 5", :modified 1458471382290},
                                 :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}, :sukunimi {:value ""}}}}}}]
    (fact "party targets"
      (->> (describe-parties-assignment-targets {:documents [schema1 schema2 schema3]})
           (map :id)) => (just ["s1" "s3" "s2"]))
    (fact "document targets"
      (let [docs (map #(update % :schema-info (fn [schema] (dissoc schema :type))) [schema1 schema2 schema3])]
        (->> (describe-non-party-document-assignment-targets {:documents docs})
             (map :id))) => (just ["s1" "s3" "s2"]))))
