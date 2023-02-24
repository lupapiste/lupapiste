(ns lupapalvelu.document.persistence-test
  (:require [lupapalvelu.document.persistence :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.company :as com]))

(testable-privates lupapalvelu.document.persistence
                   empty-op-attachments-ids
                   validate-readonly-removes!
                   company-fields)

(def some-time 123456789)

(model/with-timestamp some-time
  (facts "Facts about ->update"
    (fact (->mongo-updates "foo" (->model-updates []) nil) => {})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"]]) nil) => {"foo.a.value" "A"
                                                                    "foo.a.modified" some-time})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"]]) {:source "krysp"}) => {"foo.a.value" "A"
                                                                                      "foo.a.sourceValue" "A"
                                                                                      "foo.a.modified" some-time
                                                                                      "foo.a.source" "krysp"})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"]]) {:source "krysp" :modifier "Me"}) => {"foo.a.value" "A"
                                                                                                     "foo.a.sourceValue" "A"
                                                                                                     "foo.a.modified" some-time
                                                                                                     "foo.a.source" "krysp"
                                                                                                     "foo.a.modifier" "Me"})
    (fact (->mongo-updates "foo" (->model-updates [["a" "A"] ["b.b" "B"]]) nil) => {"foo.a.value"      "A"
                                                                                    "foo.a.modified"   some-time
                                                                                    "foo.b.b.value"    "B"
                                                                                    "foo.b.b.modified" some-time}))

  (fact "create model updates from ui-protocol"
    (->model-updates [["mitat.kerrosluku" "44"]]) => [[[:mitat :kerrosluku] "44"]])

  (fact "create mongo updates from model-updates"
    (->mongo-updates "documents.$.data"
      [[[:mitat :kerrosluku] ..kerrosluku..]
       [[:henkilo :nimi] ..nimi..]] nil)
    => {"documents.$.data.mitat.kerrosluku.value"    ..kerrosluku..
        "documents.$.data.mitat.kerrosluku.modified" some-time
        "documents.$.data.henkilo.nimi.value"        ..nimi..
        "documents.$.data.henkilo.nimi.modified"     some-time}))


(def attachments [{:id "1" :op [{:id "123"}]}
                  {:id "2" :op nil}
                  {:id "3" :op [{:id "123"}] :versions [{:v 1}]}
                  {:id "4" :op [{:id "123"}] :versions [{:v 1}]}
                  {:id "5" :op [{:id "321"}] :versions [{:v 1}]}
                  {:id "6" :op [{:id "111"}]}
                  {:id "7" :op [{:id "112"}] :versions []}
                  {:id "9" :op [{:id "112"}] :versions nil}
                  {:id "8" :op [{:id "123"}]}])

(fact "removable attachments with operation"
  (empty-op-attachments-ids nil nil) => nil
  (empty-op-attachments-ids [] nil) => nil
  (empty-op-attachments-ids attachments nil) => nil
  (empty-op-attachments-ids attachments "") => nil
  (empty-op-attachments-ids attachments "012") => nil
  (empty-op-attachments-ids attachments "123") => ["1" "8"]
  (empty-op-attachments-ids attachments "321") => nil
  (empty-op-attachments-ids attachments "111") => ["6"]
  (empty-op-attachments-ids attachments "112") => ["7" "9"])

(fact "removing-updates-by-path - two paths"
  (removing-updates-by-path :someCollection "123" [[:path :to :removed :item] [:path "to" :another "removed" :item]])
  =>  {:mongo-query   {:someCollection {"$elemMatch" {:id "123"}}},
       :mongo-updates {"$unset" {"someCollection.$.data.path.to.removed.item" "",
                                 "someCollection.$.meta.path.to.removed.item" "",
                                 "someCollection.$.data.path.to.another.removed.item" "",
                                 "someCollection.$.meta.path.to.another.removed.item" ""}}})

(fact "new-doc - without updates"
  (-> (new-doc {} (schemas/get-schema 1 "rakennusjatesuunnitelma") "22")
      (get :data))
  => truthy)

(fact "new-doc - with updates"
  (-> [[[:rakennusJaPurkujate :0 :suunniteltuMaara] "123"]
       [[:rakennusJaPurkujate :1 :yksikko] "tonni"]]
      ((partial new-doc {} (schemas/get-schema 1 "rakennusjatesuunnitelma") "22"))
      (get-in [:data :rakennusJaPurkujate :1 :yksikko :value]))
  => "tonni")

(fact "new-doc - with failing updates"
  (-> [[[:rakennusJaPurkujate :0 :illegalKey] "123"] ]
      ((partial new-doc {} (schemas/get-schema 1 "rakennusjatesuunnitelma") "22"))
      (get-in [:data :rakennusJaPurkujate :1]))
  => (throws Exception))

(fact "removing-updates-by-path - no paths"
  (removing-updates-by-path :someCollection "123" [])
  => {})

(facts "validate-readonly-updates!"
  (fact "valid update"
    (validate-readonly-updates! {:schema-info {:name "rakennusjatesuunnitelma"}}
      [[:rakennusJaPurkujate :0 :suunniteltuMaara]])
    => nil)

  (fact "contains readonly"
    (validate-readonly-updates! {:schema-info {:name "rakennusjateselvitys"}}
      [[:rakennusJaPurkujate :suunniteltuJate :0 :suunniteltuMaara]])
    => (throws Exception))

  (fact "another valid update"
    (validate-readonly-updates! {:schema-info {:name "rakennusjateselvitys"}}
      [[:rakennusJaPurkujate :suunniteltuJate :0 :arvioituMaara]])
    => nil))

(facts "validate-readonly-removes!"
  (fact "valid"
    (validate-readonly-updates! {:schema-info {:name "rakennusjatesuunnitelma"}}
      [[:rakennusJaPurkujate :0]])
    => nil)

  (fact "contains readonly"
    (validate-readonly-removes! {:schema-info {:name "rakennusjateselvitys"}}
      [[:rakennusJaPurkujate :suunniteltuJate :0]])
    => (throws Exception)))

(fact "validate-pseudo-input-updates!"
  (validate-pseudo-input-updates! {:schema-info {:name "promootio-structures"}}
                                  [[:promootio-structures :traffic-link]])
  => (throws Exception #"error-trying-to-update-pseudo-input-field"
             #(some-> % ex-data :text (= "error-trying-to-update-pseudo-input-field")))
  (validate-pseudo-input-updates! {:schema-info {:name "promootio-structures"}}
                                  [[:promootio-structures :traffic-needed]])
  => nil)

(fact validate-flagged-input-updates!
  (let [command {:organization {:krysp {:R {:version "2.2.2"}}}
                 :application  {:permitType "R"}}
        info    {:schema-info {:name "hakija-r"}}
        path    [[:henkilo :henkilotiedot :ulkomainenHenkilotunnus]]]
    (validate-flagged-input-updates! command info path)
    => (throws Exception #"error-trying-to-update-excluded-input-field"
               #(some-> % ex-data :text (= "error-trying-to-update-excluded-input-field")))
    (validate-flagged-input-updates! (assoc-in command [:organization :krysp :R :version] "2.2.4")
                                     info path)
    => nil))

(facts "Transform values"
       (fact "Default (no transform)" (transform-value :foobar "Foobar") => "Foobar")
       (fact "Default (no transform) nil" (transform-value :foobar nil) => nil)
       (fact "Upper-case" (transform-value :upper-case "Foobar") => "FOOBAR")
       (fact "Lower-case" (transform-value :lower-case "FooBar") => "foobar")
       (fact "Upper-case nil" (transform-value :upper-case nil) => nil)
       (fact "Lower-case nil" (transform-value :lower-case nil) => nil)
       (fact "Zero-pad-4 1" (transform-value :zero-pad-4 "1") => "0001")
       (fact "Zero-pad-4  1 " (transform-value :zero-pad-4 " 1 ") => "0001")
       (fact "Zero-pad-4 12" (transform-value :zero-pad-4 "12") => "0012")
       (fact "Zero-pad-4  12  " (transform-value :zero-pad-4 " 12  ") => "0012")
       (fact "Zero-pad-4 1 2" (transform-value :zero-pad-4 "1 2") => "1 2")
       (fact "Zero-pad-4 1234" (transform-value :zero-pad-4 "1234") => "1234")
       (fact "Zero-pad-4 12345" (transform-value :zero-pad-4 "12345") => "12345")
       (fact "Zero-pad-4 hello" (transform-value :zero-pad-4 "hello") => "hello")
       (fact "Zero-pad-4 nil" (transform-value :zero-pad-4 nil) => nil)
       (fact "Zero-pad-4 " (transform-value :zero-pad-4 "") => "")
       (fact "Zero-pad-4 -56" (transform-value :zero-pad-4 "-56") => "-56")
       (facts "Strings are trimmed before transform"
              (fact "Default" (transform-value :foobar " Foobar   ") => "Foobar")
              (fact "Upper-case" (transform-value :upper-case " Foobar ") => "FOOBAR")
              (fact "Lower-case" (transform-value :lower-case " FooBar ") => "foobar")
              (fact "Zero-pad-4" (transform-value :zero-pad-4 " 1  ") => "0001")))

(facts "Company fields"
       (fact "No company auth, no company user"
             (company-fields {:auth [{:id "1234"} {:id "5678"}]}
                             "company"
                             {:id "1234" :zip "remove"
                              :firstName "U" :lastName "Ser"})
             => {:id "company" :name "Company" :y "y" :zip "zip"}
             (provided (com/find-company-by-id "company")
                       => {:id "company" :name "Company" :y "y" :zip "zip"}
                       (com/find-company-users "company")
                       => [{:id "foo" :firstName "Foo" :lastName "Bar"
                            :zip "bad"}]))
       (fact "Company auth, no company user"
             (company-fields {:auth [{:id "1234"} {:id "foo"}]}
                             "company"
                             {:id "1234" :zip "remove"
                              :firstName "U" :lastName "Ser"})
             => {:id "foo" :name "Company" :y "y" :zip "zip"
                 :firstName "Foo" :lastName "Bar"}
             (provided (com/find-company-by-id "company")
                       => {:id "company" :name "Company" :y "y" :zip "zip"}
                       (com/find-company-users "company")
                       => [{:id "foo" :firstName "Foo" :lastName "Bar" :zip "forb"}]))
       (fact "No company auth, company user"
             (company-fields {:auth [{:id "1234"} {:id "5678"}]}
                             "company"
                             {:id "1234" :zip "remove"
                              :firstName "U" :lastName "Ser" :company {:id "company"}})
             => {:id "1234" :name "Company" :y "y" :zip "zip"
                 :firstName "U" :lastName "Ser" :company {:id "company"}}
             (provided (com/find-company-by-id "company")
                       => {:id "company" :name "Company" :y "y" :zip "zip"})))


(facts "Company address type"
  (fact "Building owners uses contact address not depending document type"
    (company-address-type (schemas/get-schema {:name "uusiRakennus" :version 1}) ["rakennuksenOmistajat" "0" "yritys"])
      => :contact
    (company-address-type (schemas/get-schema {:name "purkaminen" :version 1}) ["rakennuksenOmistajat" "0" "yritys"])
      => :contact)
  (facts "Applicant uses contact address"
    (company-address-type (schemas/get-schema {:name "hakija-r" :version 1}) ["yritys"])
      => :contact)
  (facts "Payer uses billing address"
    (company-address-type (schemas/get-schema {:name "maksaja" :version 1}) ["yritys"])
      => nil))

(facts "whitelist"
  (fact "value not found"
    (validate-whitelist-properties {:k1 :foo} [:k1 [:faa]]) => nil)
  (fact "key not found"
    (validate-whitelist-properties {:k1 :foo} [:k2 [:foo]]) => nil)
  (fact "found"
    (validate-whitelist-properties {:k1 :foo} [:k1 [:foo]]) => :foo)
  (fact "string works"
    (validate-whitelist-properties {:k1 "foo"} [:k1 [:foo]]) => :foo)
  (fact "string not found"
    (validate-whitelist-properties {:k1 "faa"} [:k1 [:foo]]) => nil)
  (fact "whitelist values must be keyword"
    (validate-whitelist-properties {:k1 "foo"} [:k1 ["foo"]]) => (throws AssertionError))
  (fact "roles ok"
    (validate-whitelist-properties {:roles :authority :permitType :lol} [:roles [:authority]]) => :authority)
  (fact "permitType ok"
    (validate-whitelist-properties {:roles :authority :permitType :lol} [:permitType [:lol]]) => :lol)
  (fact "mapping nil"
    (validate-whitelist-properties nil [:permitType [:lol]]) => nil)
  (fact "k+v nil"
    (validate-whitelist-properties {:roles :authority :permitType :lol} nil) => nil)
  (fact "wrong permitType"
    (validate-whitelist-properties {:roles :authority :permitType :lol} [:permitType [:wrong]]) => nil)
  (fact "second parameter is MapEntry pair, only k+v supported"
    (validate-whitelist-properties {:roles :authority :permitType :lol} [:permitType [:wrong] :roles [:authority]]) => nil))
