(ns lupapalvelu.application-replace-operation-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.application-replace-operation
                   get-operation-schemas get-required-document-schema-names-for-operations
                   replace-old-operation-doc-with-new pick-old-documents-that-new-op-needs
                   copy-docs-from-old-op-to-new get-attachment-types change-operation-in-attachments
                   copy-attachments-from-old-op-to-new get-new-operations copy-rakennuspaikka-data)

(facts "copying documents and attachments when replacing primary operation"
       (let [org              {:id "753-R"}
             old-op           {:name "kerrostalo-rivitalo"
                               :id "primaarinen-operaatio"}
             new-op           {:name "teollisuusrakennus"
                               :id "primaarinen-operaatio"}
             secondary-op     {:name "auto-katos"
                               :id "sekundaarinen-operaatio"}
             new-ops          [new-op secondary-op]

             old-op-doc       {:schema-info {:name "uusiRakennus" :op old-op :description "vanha"}
                               :data        {:kaytto {:kayttotarkoitus {:value "very big house in the country"}}}}
             new-op-doc       {:schema-info {:name "uusiRakennus" :op   new-op}}
             secondary-op-doc {:schema-info {:name "kaupunkikuvatoimenpide" :op secondary-op}
                               :data        {:kayttotarkoitus nil}}
             hakija-r-doc     {:schema-info {:name "hakija-r" :description "vanha"}
                               :data        {:henkilo {:henkilotiedot {:etunimi "city dweller" :sukunimi "successfull fella"}}}}
             old-docs         [old-op-doc
                               secondary-op-doc
                               hakija-r-doc
                               {:schema-info {:name "hankkeen-kuvaus" :description "vanha"}}
                               {:schema-info {:name "rakennuspaikka"
                                              :type :location}
                                :data {:kaavatilanne {:value "asemakaava"}}}]
             new-docs         [new-op-doc
                               secondary-op-doc
                               {:schema-info {:name "kaupunkikuvatoimenpide" :op secondary-op}
                                :data        {:kayttotarkoitus nil}}
                               {:schema-info {:name "hankkeen-kuvaus"}}
                               {:schema-info {:name "paatoksen-toimitus-rakval"}}
                               {:schema-info {:name "kaupunkikuvatoimenpide"}}
                               {:schema-info {:name "hakija-r"}
                                :data        {:henkilo {:henkilotiedot {:etunimi nil :sukunimi nil}}}}
                               {:schema-info {:name "rakennuspaikka"
                                              :type :location}
                                :data {:kaavatilanne {:value nil}}}]]

         (fact "new operations collection when primary operation is replaced"
               (let [mini-app {:primaryOperation old-op
                               :secondaryOperations [secondary-op]}]
                 (get-new-operations mini-app (:id old-op) new-op)
                 => [new-op secondary-op]))

         (fact "get operation schemas by name"
               (set (get-operation-schemas org "kerrostalo-rivitalo"))
               => #{"hakija-r" "hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija" "rakennusjatesuunnitelma"}

               (set (get-operation-schemas org "aloitusoikeus"))
               => #{"maksaja" "hakija-r"})

         (fact "multiple operations need lots of documents"
               (get-required-document-schema-names-for-operations org new-ops)
               => (clojure.set/union #{"hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija" "rakennusjatesuunnitelma"}
                                     #{"hakija-r" "hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija"}))

         (fact "replacing the operation document"
               (replace-old-operation-doc-with-new old-docs (:id old-op) new-docs)
               => [new-op-doc secondary-op-doc])

         (fact "picking relevant docs from old docs"
               (pick-old-documents-that-new-op-needs new-docs old-docs "hakija-r")
               => [hakija-r-doc])

         (fact "rakennuspaikka data persists"
               (let [old-doc-1 {:id "old-rakennuspaikka"
                                :schema-info {:name "rakennuspaikka-ilman-ilmoitusta"
                                              :type :location}
                                :data {:kiinteisto {:maaraalaTunnus {:value  "0000"}}
                                       :hallintaperuste {:value "oma"}
                                       :kaavatilanne {:value "asemakaava"}}}
                     old-doc-2 {:id "old-rakennuspaikka"
                                :schema-info {:name "rakennuspaikka"
                                              :type :location}
                                :data {:kiinteisto {:maaraalaTunnus {:value  "0000"}}
                                       :hallintaperuste {:value "oma"}
                                       :kaavatilanne {:value "asemakaava"}
                                       :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:value "05.04.2018"}}}}
                     new-doc-1 {:id "new-rakennuspaikka"
                                :schema-info {:name "rakennuspaikka"
                                              :type :location}
                                :data {:kiinteisto {:maaraalaTunnus {:value nil}}
                                       :hallintaperuste {:value nil}
                                       :kaavanaste {:value nil}
                                       :kaavatilanne {:value nil}
                                       :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:value nil}}}}
                     new-doc-2 {:id "new-rakennuspaikka"
                                :schema-info {:name "rakennuspaikka-ilman-ilmoitusta"
                                              :type :location}
                                :data {:kiinteisto {:maaraalaTunnus {:value nil}}
                                       :hallintaperuste {:value nil}
                                       :kaavanaste {:value nil}
                                       :kaavatilanne {:value nil}}}]

                 (copy-rakennuspaikka-data [hakija-r-doc old-doc-1] [hakija-r-doc new-doc-1])
                 => [{:id "new-rakennuspaikka"
                      :schema-info {:name "rakennuspaikka"
                                    :type :location}
                      :data {:kiinteisto {:maaraalaTunnus {:value  "0000"}}
                             :hallintaperuste {:value "oma"}
                             :kaavanaste {:value nil}
                             :kaavatilanne {:value "asemakaava"}
                             :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:value nil}}}}
                     hakija-r-doc]

                 (copy-rakennuspaikka-data [old-doc-2] [new-doc-2])
                 => [{:id "new-rakennuspaikka"
                      :schema-info {:name "rakennuspaikka-ilman-ilmoitusta"
                                    :type :location}
                      :data {:kiinteisto {:maaraalaTunnus {:value  "0000"}}
                             :hallintaperuste {:value "oma"}
                             :kaavanaste {:value nil}
                             :kaavatilanne {:value "asemakaava"}}}]

                 (copy-rakennuspaikka-data [old-doc-1] [old-doc-1])
                 => [old-doc-1]))

         (fact "merging relevant old documents to new"
               (let [merged-docs (copy-docs-from-old-op-to-new {:documents old-docs} org (:id old-op) new-ops new-docs)
                     expected    [new-op-doc
                                  secondary-op-doc
                                  {:schema-info {:name "rakennuspaikka"
                                                 :type :location}
                                   :data {:kaavatilanne {:value "asemakaava"}}}
                                  hakija-r-doc
                                  {:schema-info {:name "paatoksen-toimitus-rakval"}}
                                  {:schema-info {:name "hankkeen-kuvaus" :description "vanha"}}]]
                 merged-docs => expected))

         (let [old-attachments [{:type {:type-id "asemapiirros"
                                        :type-group "paapiirustus"}
                                 :versions [{:version {:major 0
                                                       :minor 1}}
                                            {:version {:major 1
                                                       :minor 1}}]
                                 :id "1"
                                 :op nil}
                                {:type {:type-id "valtakirja"
                                        :type-group "hakija"}
                                 :versions [{:version {:major 0
                                                       :minor 1}}]
                                 :id "2"
                                 :op [old-op]}
                                {:type {:type-id "muu"
                                        :type-group "muut"}
                                 :versions [{:version {:major 0
                                                       :minor 1}}]
                                 :id "3"
                                 :op nil}]
               new-attachments [{:type {:type-id "tutkintotodistus"
                                        :type-group "osapuolet"}
                                 :versions []
                                 :id "4"
                                 :op nil}]]

           (fact "getting attachment types"
                 (get-attachment-types old-attachments)
                 => #{{:type-id "asemapiirros"
                       :type-group "paapiirustus"}
                      {:type-id "valtakirja"
                       :type-group "hakija"}
                      {:type-id    "muu"
                       :type-group "muut"}})

           (fact "changing operation in attachment"
                 (change-operation-in-attachments old-op (first new-ops) old-attachments)
                 => [{:type {:type-id    "asemapiirros"
                             :type-group "paapiirustus"}
                      :versions [{:version {:major 0
                                            :minor 1}}
                                 {:version {:major 1
                                            :minor 1}}]
                      :id "1"
                      :op nil}
                     {:type {:type-id "valtakirja"
                             :type-group "hakija"}
                      :versions [{:version {:major 0
                                            :minor 1}}]
                      :id "2"
                      :op [new-op]}
                     {:type     {:type-id    "muu"
                                 :type-group "muut"}
                      :versions [{:version {:major 0
                                            :minor 1}}]
                      :id       "3"
                      :op       nil}])


           (fact "attachments copy as they should"
                 (sort-by :id (copy-attachments-from-old-op-to-new {:attachments old-attachments} (first new-ops) new-attachments))
                 => [{:type     {:type-id    "asemapiirros"
                                 :type-group "paapiirustus"}
                      :versions [{:version {:major 0
                                            :minor 1}}
                                 {:version {:major 1
                                            :minor 1}}]
                      :id       "1"
                      :op       nil}
                     {:type     {:type-id    "valtakirja"
                                 :type-group "hakija"}
                      :versions [{:version {:major 0
                                            :minor 1}}]
                      :id       "2"
                      :op       [new-op]}
                     {:type     {:type-id    "muu"
                                 :type-group "muut"}
                      :versions [{:version {:major 0
                                            :minor 1}}]
                      :id       "3"
                      :op       nil}
                     {:type     {:type-id    "tutkintotodistus"
                                 :type-group "osapuolet"}
                      :versions []
                      :id       "4"
                      :op       nil}])))
       (against-background (lupapalvelu.operations/common-rakval-org-schemas anything) => "rakennusjatesuunnitelma")
  )
