(ns lupapalvelu.application-replace-operation-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.application-replace-operation
                   get-operation-by-key replace-op-in-attachment single-operation-attachment?
                   get-existing-operation-attachment-types non-empty-attachment? coll-contains-type?
                   new-attachment-duplicates not-needed-templates required-attachments-for-operations
                   replace-operation-in-attachment? get-document-schema-names-for-operation
                   required-document-schema-names-by-op copy-old-document-data)

(facts "attachments when replacing primary operation"
       (let [op-1           {:name "kerrostalo-rivitalo"
                               :id "1"}
             op-2           {:name "teollisuusrakennus"
                               :id "2"}
             op-3           {:name "auto-katos"
                               :id "3"}
             attachment-1 {:type {:type-group "paapiirustus" :type-id "julkisivupiirros"}
                           :versions [{:version {:major 0 :minor 1}}]
                           :id "1"
                           :op [op-1]}
             attachment-2 {:type {:type-group "paapiirustus" :type-id "asemapiirros"}
                           :versions [{:version {:major 0 :minor 1}}]
                           :id "2"
                           :op [op-1 op-2]}
             attachment-3 {:type {:type-group "paapiirustus" :type-id "pohjapiirustus"}
                           :versions [{:version {:major 0 :minor 1}}]
                           :id "3"
                           :op [op-3]}
             attachment-4 {:type {:type-group "paapiirustus" :type-id "pohjapiirustus"}
                           :versions []
                           :id "4"
                           :op [op-1]}
             attachment-5 {:type {:type-group "paapiirustus" :type-id "leikkauspiirros"}
                           :versions []
                           :id "5"
                           :op [op-1]}
             attachment-6 {:type {:type-group "paapiirustus" :type-id "julkisivupiirros"}
                           :versions []
                           :id "6"
                           :op [op-1]}
             attachment-7 {:type {:type-group "ennakkoluvat_ja_lausunnot" :type-id "naapurin_kuuleminen"}
                           :versions []
                           :id "7"
                           :op []}
             attachment-8  {:type {:type-group "paapiirustus" :type-id "leikkauspiirros"}
                            :versions []
                            :id "8"
                            :op [op-3]}
             application-1 {:attachments [attachment-1 attachment-3 attachment-7]
                            :primaryOperation op-1
                            :secondaryOperations [op-3]}
             application-2 {:attachments [attachment-5]
                            :primaryOperation op-1
                            :secondaryOperations [op-3]}]

         (fact "getting operations in application by key and value pair"
           (get-operation-by-key {:secondaryOperations [{:a 1 :c "d"} {:b 2 :e "f"}]} :a 1)
           => {:a 1 :c "d"})

         (fact "replacing operation in attachment"
           (replace-op-in-attachment attachment-1 op-1 op-2) => (assoc attachment-1 :op [op-2])
           (replace-op-in-attachment attachment-2 op-1 op-3) => (assoc attachment-2 :op [op-3 op-2]))
           (replace-op-in-attachment attachment-1 op-2 op-3) => (assoc attachment-1 :op [op-3 op-1])

         (fact "getting attachments that can only belong to one operation"
           (single-operation-attachment? op-1 attachment-1) => true
           (single-operation-attachment? op-2 attachment-2) => false
           (single-operation-attachment? op-2 attachment-1) => false)

         (fact "get attachment types for existing attachments"
           (get-existing-operation-attachment-types [attachment-1 attachment-2 attachment-3 attachment-4] op-1)
           => #{{:type-group "paapiirustus" :type-id "julkisivupiirros"} {:type-group "paapiirustus" :type-id "pohjapiirustus"}}

           (get-existing-operation-attachment-types [attachment-1 attachment-2 attachment-3 attachment-4] op-2)
           => #{}

           (get-existing-operation-attachment-types [attachment-1 attachment-2 attachment-3 attachment-4] op-3)
           => #{{:type-group "paapiirustus" :type-id "pohjapiirustus"}})

         (fact "nonempty attachments are nonempty"
           (non-empty-attachment? attachment-1) => true
           (non-empty-attachment? attachment-4) => false)

         (fact "a collection of attachments has the attachment in question of attachments to be deleted"
           (coll-contains-type? [attachment-1 attachment-2 attachment-3] attachment-3) => truthy
           (coll-contains-type? [attachment-1 attachment-2 attachment-3] attachment-5) => nil?
           (coll-contains-type? [attachment-1 attachment-2 attachment-3] nil) => nil?)

         (fact "empty attachments that are duplicates appear on a list"
           (new-attachment-duplicates [attachment-1 attachment-4 attachment-6] op-1) => ["6"]
           (new-attachment-duplicates [attachment-4 attachment-6] op-1) => [])

         (fact "not needed empty attachments appear on a list of attachments to be deleted"
           (not-needed-templates "753-R" application-1 {:id "3"}) => ["7"]
           (not-needed-templates "753-R" application-2 {:id "3"}) => []
           (provided (#'lupapalvelu.application-replace-operation/required-attachments-for-operations anything anything)
                     => #{{:type-group "paapiirustus" :type-id "leikkauspiirros"}}))

         (fact "operation in attachment is replaced when necessary"
           (let [new-op-att-types #{{:type-group "paapiirustus" :type-id "julkisivupiirros"}
                                    {:type-group "paapiirustus" :type-id "pohjapiirustus"}}]
             (replace-operation-in-attachment? new-op-att-types op-1 attachment-1) => true
             (replace-operation-in-attachment? new-op-att-types op-1 attachment-2) => false
             (replace-operation-in-attachment? new-op-att-types op-1 attachment-3) => false
             (replace-operation-in-attachment? new-op-att-types op-1 attachment-4) => true))))

(facts "documents when replacing operation"
  (let [org {:selected-operations [:tontin-ajoliittyman-muutos :auto-katos]
             :extended-construction-waste-report-enabled false}
        app {:primaryOperation {:name :auto-katos}
             :secondaryOperations [{:name :tontin-ajoliittyman-muutos}]}]

  (fact "document schema names for operation"
    (get-document-schema-names-for-operation org {:name "tontin-ajoliittyman-muutos"})
    => ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka-ilman-ilmoitusta" "suunnittelija"]

    (get-document-schema-names-for-operation org {:name "auto-katos"})
    => (concat ["hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija"]
               ["rakennusjatesuunnitelma"]))

  (fact "getting document schema names by operation"
    (required-document-schema-names-by-op org app)
    => {:auto-katos #{"hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka" "paasuunnittelija" "suunnittelija" "rakennusjatesuunnitelma"}
        :tontin-ajoliittyman-muutos #{"hankkeen-kuvaus" "paatoksen-toimitus-rakval" "maksaja" "rakennuspaikka-ilman-ilmoitusta" "suunnittelija"}})

  (fact "copying existing document data to new doc"
    (copy-old-document-data {:data {:a 1 :b 2 :c 3 :d 4}} {:data {:a nil :b nil :c nil}})
    => {:data {:a 1 :b 2 :c 3}}

    (copy-old-document-data {:data {:a 1 :b 2 :c 3}} {:data {:a nil :b nil :c nil :d nil}})
    => {:data {:a 1 :b 2 :c 3 :d nil}}

    (copy-old-document-data {:data {:a 1 :b 2 :c 3 :d 4} :foo "bar"} {:data {:a nil :b nil :c nil}})
    => {:data {:a 1 :b 2 :c 3}}

    (copy-old-document-data {:data {:a 1 :b 2 :c 3 :d 4}} {:data {:a nil :b nil :c nil} :foo "bar"})
    => {:data {:a 1 :b 2 :c 3} :foo "bar"})))