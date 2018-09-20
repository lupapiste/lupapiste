(ns lupapalvelu.document.document-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer [unauthorized? ok?]]
            [lupapalvelu.document.document :refer :all]
            [lupapalvelu.authorization :as auth]))

(testable-privates lupapalvelu.document.document
                   deny-remove-of-last-document
                   deny-remove-of-non-removable-doc
                   document-assignment-info
                   describe-parties-assignment-targets
                   describe-non-party-document-assignment-targets)

(defn- keywordize-schema-info-types [command]
  (update-in command [:application :documents] (partial map (fn [doc] (update-in doc [:schema-info :type] keyword)))))

(facts create-doc-validator
  ; type is "YA" and at least one doc is "party" -> fail
  (let [command {:application {:permitType "YA"
                               :documents  [{:schema-info {:type "festivity"}}
                                            {:schema-info {:type "party"}}
                                            {:schema-info {:type "celebration"}}]}}]
    (create-doc-validator command) => {:ok false, :text "error.create-doc-not-allowed"}
    (create-doc-validator (keywordize-schema-info-types command)) => {:ok false, :text "error.create-doc-not-allowed"})

  ; none of the docs are "party"
  (let [command {:application {:permitType "YA"
                               :documents  [{:schema-info {:type "festivity"}}
                                            {:schema-info {:type "celebration"}}]}}]
    (create-doc-validator command) => nil
    (create-doc-validator (keywordize-schema-info-types command)) => nil)

  ; type is not "YA"
  (let [command {:application {:permitType "R"
                               :documents  [{:schema-info {:type "festivity"}}
                                            {:schema-info {:type "party"}}
                                            {:schema-info {:type "celebration"}}]}}]
    (create-doc-validator command) => nil
    (create-doc-validator (keywordize-schema-info-types command)) => nil))

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

(facts "document context function"
  (let [guest {:id "1" :role "guest"}
        writer {:id "2" :role "writer"}
        foreman {:id "3" :role "foreman"}
        application {:auth         [guest
                                    writer
                                    foreman]
                     :organization "000-R"
                     :documents    [{:id "1", :schema-info {:name "hakija-r"}}
                                    {:id "2", :schema-info {:name "tyonjohtaja-v2"}}]}
        authority {:id       "5"
                   :orgAuthz {"000-R" ["authority"]}}
        command {:application application
                 :permissions #{}}]

    (fact "authority does not receive permissions through the context"
      (document-context (assoc command :user authority :data {:doc "1"})) => {:application application
                                                                              :user        authority
                                                                              :permissions #{}
                                                                              :data        {:doc "1"}
                                                                              :document    {:id "1", :schema-info {:name "hakija-r"}}})

    (fact "writer receives edit permissions for hakija-r document"
      (document-context (assoc command :user writer :data {:doc "1"})) => {:application application
                                                                           :user        writer
                                                                           :permissions #{:document/edit
                                                                                          :document/edit-draft}
                                                                           :data        {:doc "1"}
                                                                           :document    {:id "1", :schema-info {:name "hakija-r"}}})

    (fact "foreman does not receive edit permissions for hakija-r"
      (document-context (assoc command :user foreman :data {:doc "1"})) => {:application application
                                                                            :user        foreman
                                                                            :permissions #{}
                                                                            :data        {:doc "1"}
                                                                            :document    {:id "1", :schema-info {:name "hakija-r"}}})

    (fact "foreman receives edit permissions for tyonjohtaja-v2"
      (document-context (assoc command :user foreman :data {:doc "2"})) => {:application application
                                                                            :user        foreman
                                                                            :permissions #{:document/edit
                                                                                           :document/edit-draft}
                                                                            :data        {:doc "2"}
                                                                            :document    {:id "2", :schema-info {:name "tyonjohtaja-v2"}}})

    (fact "guest does not receive permissions for tyonjohtaja-v2"
      (document-context (assoc command :user guest :data {:doc "2"})) => {:application application
                                                                          :user        guest
                                                                          :permissions #{}
                                                                          :data        {:doc "2"}
                                                                          :document    {:id "2", :schema-info {:name "tyonjohtaja-v2"}}})

    (fact "docId works as data key"
      (document-context (assoc command :user writer :data {:docId "1"})) => {:application application
                                                                           :user        writer
                                                                           :permissions #{:document/edit
                                                                                          :document/edit-draft}
                                                                           :data        {:docId "1"}
                                                                           :document    {:id "1", :schema-info {:name "hakija-r"}}})

    (fact "documentId works as data key"
      (document-context (assoc command :user writer :data {:documentId "1"})) => {:application application
                                                                             :user        writer
                                                                             :permissions #{:document/edit
                                                                                            :document/edit-draft}
                                                                             :data        {:documentId "1"}
                                                                             :document    {:id "1", :schema-info {:name "hakija-r"}}})

    (fact "function returns unaltered command when proper data key is missing"
      (document-context (assoc command :user writer :data {})) => (assoc command :user writer :data {}))

    (fact "invalid document id results in a failure"
      (document-context (assoc command :user writer :data {:documentId "999"})) => (throws Exception))))

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

  (against-background (auth/has-auth-via-company? anything "123") => false)
  (against-background (auth/has-auth-via-company? anything "345") => false))
