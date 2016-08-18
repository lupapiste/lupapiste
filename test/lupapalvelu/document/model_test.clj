(ns lupapalvelu.document.model-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.model :refer :all]
            [lupapalvelu.document.validators :refer [valid-against? invalid-with? valid?]]
            [lupapalvelu.document.schemas :as schemas]
            [sade.util :as util]))

;; Define a "random" timestamp used in test.
;; Midje metaconstraints seems to mess with tools/unwrapped.
(def some-time 123456789)

;; Simple test schema:

(def schema {:info {:name "test-model"
                    :version 1}
             :body [{:name "a" :type :group
                     :body [{:name "aa" :type :string}
                            {:name "ab" :type :string :min-len 2 :max-len 3}
                            {:name "ac" :type :string :min-len 2 :max-len 3}
                            {:name "b" :type :group
                             :body [{:name "ba" :type :string :min-len 2}
                                    {:name "bb" :type :checkbox}]}
                            {:name "c" :type :group
                             :body [{:name "ca" :type :string}
                                    {:name "cb" :type :checkbox}]}
                            {:name "partytype" :type :radioGroup :body [{:name "henkilo"} {:name "yritys"}] :default "henkilo"}
                            {:name "d" :type :select :sortBy :displayname
                             :body [{:name "A"}
                                    {:name "B"}
                                    {:name "C"}]}]}]})

(def schema-with-repetition {:info {:name "repetition-model" :version 1}
                             :body [{:name "single" :type :string}
                                    {:name "repeats" :type :group :repeating true
                                     :body [{:name "single2" :type :string}
                                            {:name "repeats2" :type :string :subtype :digit :repeating true}]}]})

(facts "Find-by-name"
  (fact (find-by-name (:body schema) ["a"])          => (-> schema :body first))
  (fact (find-by-name (:body schema) ["a" :b])       => {:name "b" :type :group
                                                         :body [{:name "ba" :type :string :min-len 2}
                                                                {:name "bb" :type :checkbox}]})
  (fact (find-by-name (:body schema) ["a" "aa"])     => {:name "aa" :type :string})
  (fact (find-by-name (:body schema) ["a" "b" "bb"]) => {:name "bb" :type :checkbox})
  (fact (find-by-name (:body schema) [:a :b :bb])    => {:name "bb" :type :checkbox})
  (fact (find-by-name (:body schema) ["a" "b" "bc"]) => nil))

;; Tests for internals:

(facts "has-errors?"
  (has-errors? [])                  => false
  (has-errors? [{:result [:warn]}]) => false
  (has-errors? [{:result [:warn]}
                {:result [:err]}])  => true)

; field type validation

(facts "dates"
       (validate-field {} {:type :date} "abba") => [:warn "illegal-value:date"]
       (validate-field {} {:type :date} "") => nil
       (validate-field {} {:type :date} "11.12.2013") => nil
       (validate-field {} {:type :date} "1.2.2013") => nil
       (validate-field {} {:type :date} "01.02.2013") => nil
       (validate-field {} {:type :date} "11.12.13") => [:warn "illegal-value:date"]
       (validate-field {} {:type :date} "41.12.2013") => [:warn "illegal-value:date"]
       (validate-field {} {:type :date} "1.13.2013") => [:warn "illegal-value:date"])

(facts "times"
  (validate-field {} {:type :time} "abba") => [:warn "illegal-value:time"]
  (validate-field {} {:type :time} "") => nil
  (validate-field {} {:type :time} "11:12") => nil
  (validate-field {} {:type :time} "1:2") => nil
  (validate-field {} {:type :time} "1:20") => nil
  (validate-field {} {:type :time} "00:00") => nil
  (validate-field {} {:type :time} "00:00:00") => nil
  (validate-field {} {:type :time} "00:00:00.1") => nil
  (validate-field {} {:type :time} "23:59") => nil
  (validate-field {} {:type :time} "23:59:59") => nil
  (validate-field {} {:type :time} "23:59:59.9") => nil
  (validate-field {} {:type :time} "24:00") => [:warn "illegal-value:time"]
  (validate-field {} {:type :time} "23:60") => [:warn "illegal-value:time"]
  (validate-field {} {:type :time} "-1:10") => [:warn "illegal-value:time"])

(facts "hetu validation"
  (validate-field {} {:type :hetu} "") => nil?
  (validate-field {} {:type :hetu} "210281-9988") => nil?
  (validate-field {} {:type :hetu} "210281+9988") => nil?
  (validate-field {} {:type :hetu} "070550A907P") => nil?
  (validate-field {} {:type :hetu} "010170-960F") => nil?
  (validate-field {} {:type :hetu} "210281_9988") => [:err "illegal-hetu"]
  (validate-field {} {:type :hetu} "210281-9987") => [:err "illegal-hetu"]
  (validate-field {} {:type :hetu} "300281-998V") => [:err "illegal-hetu"])

;;
;; validate
;;

(facts "validate"
  (validate
    {}
    {:data {:a {:aa {:value "kukka"}
                :ab {:value "123"}}}}
    {:body [{:name "a" :type :group
             :body [{:name "aa" :type :string}
                    {:name "ab" :type :string :min-len 2 :max-len 3}]}]}) => empty?
  (validate
    {}
    {:data {:a {:aa {:value "kukka"}
                :ab {:value "1234"}}}}
    {:body [{:name "a" :type :group
             :body [{:name "aa" :type :string}
                    {:name "ab" :type :string :min-len 2 :max-len 3}]}]}) =not=> empty?)

;; Validation tests:

(facts "Simple validations"
  (with-timestamp some-time
    (let [document (new-document schema ..now..)]
      (apply-update document [:a :ab] "foo")   => (valid-against? schema)
      (apply-update document [:a :ab] "f")     => (invalid-with? schema [:warn "illegal-value:too-short"])
      (apply-update document [:a :ab] "foooo") => (invalid-with? schema [:err "illegal-value:too-long"])
      (apply-update document [:a :ab] "\u00d6\u00e9\u00c8") => (valid-against? schema)
      (apply-update document [:a :ab] "\u047e\u0471") => (invalid-with? schema [:warn "illegal-value:not-latin1-string"]))))

(facts "Select"
  (with-timestamp some-time
    (let [document (new-document schema ..now..)]
      (apply-update document [:a :d] "A") => (valid-against? schema)
      (apply-update document [:a :d] "")  => (valid-against? schema)
      (apply-update document [:a :d] "D") => (invalid-with? schema [:warn "illegal-value:select"]))))

(facts "Radio group"
  (with-timestamp some-time
    (let [document (new-document schema ..now..)]
      (facts "valid cases"
        (apply-update document [:a :partytype] "henkilo") => (valid-against? schema)
        (apply-update document [:a :partytype] "yritys") => (valid-against? schema)
        (apply-update document [:a :partytype] "")  => (valid-against? schema))
      (fact "invalid value"
        (apply-update document [:a :partytype] "neither") => (invalid-with? schema [:warn "illegal-value:select"])))))

(facts "with real schemas - important field for paasuunnittelija"
  (with-timestamp some-time
    (let [document (new-document (schemas/get-schema (schemas/get-latest-schema-version) "paasuunnittelija") ..now..)]
      (-> document
        (apply-update [:henkilotiedot :etunimi] "Tauno")
        (apply-update [:henkilotiedot :sukunimi] "Palo")
        (apply-update [:henkilotiedot :hetu] "210281-9988")
        (apply-update [:yritys :liikeJaYhteisoTunnus] "1060155-5")
        (apply-update [:yritys :yritysnimi] "Suunnittelu Palo")
        (apply-update [:osoite :katu] "katu")
        (apply-update [:osoite :postinumero] "12345")
        (apply-update [:osoite :postitoimipaikannimi] "Demola")
        (apply-update [:patevyys :koulutusvalinta] "rakennusmestari")
        (apply-update [:patevyys :koulutus] "tekniikan kandidaatti")
        (apply-update [:patevyys :valmistumisvuosi] "2010")
        (apply-update [:patevyys :patevyysluokka] "AA")
        (apply-update [:suunnittelutehtavanVaativuusluokka] "B")
        (apply-update [:yhteystiedot :email] "tauno@example.com")
        (apply-update [:yhteystiedot :puhelin] "050")) => valid?
      (apply-update document [:henkilotiedot :etunimiz] "Tauno") => (invalid-with? [:err "illegal-key"])
      (apply-update document [:henkilotiedot :sukunimiz] "Palo") => (invalid-with? [:err "illegal-key"]))))

(facts "Repeating section"
  (with-timestamp some-time
    (let [document (new-document schema-with-repetition ..now..)]
      (fact "Single value contains no nested sections"
        (apply-update (util/dissoc-in document [:data :single]) [:single :1 :single2] "foo") => (invalid-with? schema-with-repetition [:err "illegal-key"]))

      (fact "Repeating section happy case"
        (apply-update document [:repeats :1 :single2] "foo") => (valid-against? schema-with-repetition))

      (fact "Invalid key under nested section"
        (apply-update document [:repeats :1 :single3] "foo") => (invalid-with? schema-with-repetition [:err "illegal-key"]))

      (fact "Unindexed repeating section"
        (apply-update document [:repeats :single2] "foo") => (invalid-with? schema-with-repetition [:err "illegal-key"]))

      (fact "Repeating string, 0"
        (apply-update document [:repeats :1 :repeats2 :0] "1") => (valid-against? schema-with-repetition))

      (fact "Repeating string, 1"
        (apply-update document [:repeats :1 :repeats2 :1] "foo") => (invalid-with? schema-with-repetition [:warn "illegal-number"])))))

(def schema-with-required {:info {:name "with-required" :version 1}
                           :body [{:name "a" :type :group
                                   :body [{:name "b" :type :group
                                           :body [{:name "aa" :type :string :required true}
                                                  {:name "ab" :type :string :required true}]}
                                          {:name "c" :type :group :repeating true
                                           :body [{:name "raa" :type :string}
                                                  {:name "rab" :type :string :required true}]}
                                          {:name "d" :type :group :repeating true
                                           :body [{:name "d2" :type :group :repeating true
                                                   :body [{:name "od1" :type :string}
                                                          {:name "rd" :type :string :required true}
                                                          {:name "od2" :type :string}]}]}]}]})

(def missing-required-fields? (invalid-with? schema-with-required [:tip "illegal-value:required"]))

(facts "Required fields"
  (with-timestamp some-time
    (let [document (new-document schema-with-required ..now..)]

      document => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")) => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :raa] "value")) => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :c :6 :rab] "value")) => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :d :0 :d2 :0 :od1] "value")) => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :d :0 :d2 :0 :od1] "value")
          (apply-update [:a :d :0 :d2 :0 :od2] "value")) => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :d :0 :d2 :0 :od1] "value")
          (apply-update [:a :d :0 :d2 :0 :rd] "value")
          (apply-update [:a :d :0 :d2 :0 :od2] "value")) => (valid-against? schema-with-required)

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :d :0 :d2 :0 :od1] "value")
          (apply-update [:a :d :0 :d2 :0 :rd] "value")
          (apply-update [:a :d :1 :d2 :6 :rd] "value")) => (valid-against? schema-with-required)))

  (facts "with real schemas - required fields for henkilo hakija"
    (with-timestamp some-time
      (let [schema (schemas/get-schema (schemas/get-latest-schema-version) "hakija")
            document (-> (new-document schema ..now..)
                         (apply-update [:_selected] "henkilo")
                         (apply-update [:henkilo :henkilotiedot :etunimi] "Tauno")
                         (apply-update [:henkilo :henkilotiedot :sukunimi] "Palo")
                         (apply-update [:henkilo :henkilotiedot :hetu] "230470-658B")
                         (apply-update [:henkilo :osoite :katu] "katu")
                         (apply-update [:henkilo :osoite :postinumero] "12345")
                         (apply-update [:henkilo :osoite :postitoimipaikannimi] "Demola")
                         (apply-update [:henkilo :osoite :maa] "FIN")
                         (apply-update [:henkilo :yhteystiedot :email] "tauno@example.com")
                         (apply-update [:henkilo :yhteystiedot :puhelin] "050"))]
        document => valid?
        (-> document
            (apply-update [:yritys :osoite :postinumero] "000")) => (invalid-with? schema [:err "bad-postal-code"])
        (-> document
            (apply-update [:henkilo :osoite :katu])) => missing-required-fields?
        (-> document
            (apply-update [:henkilo :osoite :postinumero])) => missing-required-fields?
        (-> document
            (apply-update [:henkilo :osoite :postitoimipaikannimi]))=> missing-required-fields?
        (-> document
            (apply-update [:henkilo :osoite :postinumero] "000")) => (invalid-with? schema [:err "bad-postal-code"])
        (-> document
            (apply-update [:henkilo :osoite :postinumero] "000")
            (apply-update [:henkilo :osoite :maa] "CHN")) => valid?))))

(facts "with real schemas - required fields for yritys hakija"
  (with-timestamp some-time
    (let [document (-> (new-document (schemas/get-schema (schemas/get-latest-schema-version) "hakija") ..now..)
                     (apply-update [:_selected] "yritys")
                     (apply-update [:yritys :yritysnimi] "Solita")
                     (apply-update [:yritys :liikeJaYhteisoTunnus] "1060155-5")
                     (apply-update [:yritys :osoite :katu] "Satakunnankatu 18 A")
                     (apply-update [:yritys :osoite :postinumero] "33720")
                     (apply-update [:yritys :osoite :postitoimipaikannimi] "Tampere")
                     (apply-update [:yritys :osoite :maa] "FIN")
                     (apply-update [:yritys :yhteyshenkilo :henkilotiedot :etunimi] "Tauno")
                     (apply-update [:yritys :yhteyshenkilo :henkilotiedot :sukunimi] "Palo")
                     (apply-update [:yritys :yhteyshenkilo :yhteystiedot :email] "tauno@example.com")
                     (apply-update [:yritys :yhteyshenkilo  :yhteystiedot :puhelin] "050"))]
      document => valid?
      (-> document
        (apply-update [:yritys :osoite :katu])) => missing-required-fields?
      (-> document
        (apply-update [:yritys :osoite :postinumero])) => missing-required-fields?
      (-> document
        (apply-update [:yritys :osoite :postitoimipaikannimi])) => missing-required-fields?)))

(facts "Pertinent validation"
       (with-timestamp some-time
         (let [schema    (schemas/get-schema (schemas/get-latest-schema-version) "hakija")
               document  (-> (new-document schema ..now..)
                             (apply-update [:_selected] "henkilo")
                             ;; etunimi missing
                             (apply-update [:henkilo :henkilotiedot :sukunimi] "Palo")
                             (apply-update [:henkilo :henkilotiedot :hetu] "bad-hetu")
                             (apply-update [:henkilo :osoite :katu] "katu")
                             (apply-update [:henkilo :osoite :postinumero] "12345")
                             (apply-update [:henkilo :osoite :postitoimipaikannimi] "Demola")
                             (apply-update [:henkilo :osoite :maa] "FIN")
                             (apply-update [:henkilo :yhteystiedot :email] "tauno@example.com")
                             (apply-update [:henkilo :yhteystiedot :puhelin] "050")
                             ;; yritysnimi missing
                             (apply-update [:yritys :liikeJaYhteisoTunnus] "bad-y")
                             (apply-update [:yritys :osoite :katu] "Satakunnankatu 18 A")
                             (apply-update [:yritys :osoite :postinumero] "33720")
                             (apply-update [:yritys :osoite :postitoimipaikannimi] "Tampere")
                             (apply-update [:yritys :osoite :maa] "FIN")
                             (apply-update [:yritys :yhteyshenkilo :henkilotiedot :etunimi] "Tauno")
                             (apply-update [:yritys :yhteyshenkilo :henkilotiedot :sukunimi] "Palo")
                             (apply-update [:yritys :yhteyshenkilo :yhteystiedot :email] "tauno@example.com")
                             (apply-update [:yritys :yhteyshenkilo  :yhteystiedot :puhelin] "050"))
               all       (validate {} document)
               pertinent (validate-pertinent {} document)]
           (fact "Result counts"
                 [(count all) (count pertinent)] => [3 2])
           (fact "All contains ytunnus with ignore"
                 all => (contains [(contains {:ignore true
                                              :path [:yritys :liikeJaYhteisoTunnus]})]))
           (fact "Pertinent does not contain ytunnus"
                 pertinent =not=> (contains [(contains {:path [:yritys :liikeJaYhteisoTunnus]})]))
           (facts "Change _selected"
                  (let [document  (apply-update document [:_selected] "yritys")
                        all       (validate {} document)
                        pertinent (validate-pertinent {} document)]
                    (fact "Result counts"
                          [(count all) (count pertinent)] => [3 2])
                    (fact "All contains hetu with ignore"
                          all => (contains [(contains {:ignore true
                                                       :path [:henkilo :henkilotiedot :hetu]})]))
                    (fact "Pertinent does not contain hetu"
                          pertinent =not=> (contains [(contains {:path [:henkilo :henkilotiedot :hetu]})])))))))

(facts "Rakennuksen omistaja: omistajalaji"
  (with-timestamp some-time
    (let [schema {:info {:name "rakennuksen-omistajat"} :body schemas/rakennuksen-omistajat}
          document (-> (new-document schema ..now..)
                     (apply-update [:rakennuksenOmistajat :0 :_selected] "yritys")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :yritysnimi] "Solita")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :liikeJaYhteisoTunnus] "1060155-5")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :osoite :katu] "Satakunnankatu 18 A")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :osoite :postinumero] "33720")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :osoite :postitoimipaikannimi] "Tampere")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :osoite :maa] "FIN")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo :henkilotiedot :etunimi] "Tauno")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo :henkilotiedot :sukunimi] "Palo")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo :yhteystiedot :email] "tauno@example.com")
                     (apply-update [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo  :yhteystiedot :puhelin] "050")
                     (apply-update [:rakennuksenOmistajat :0 :muu-omistajalaji] "jotain muuta"))]

      (facts "content and required value are checked"
        document => (invalid-with? schema [:tip "illegal-value:required"])
        (apply-update document [:rakennuksenOmistajat :0 :omistajalaji] "jotain muuta") => (invalid-with? schema [:warn "illegal-value:select"])
        (apply-update document [:rakennuksenOmistajat :0 :omistajalaji] "ei tiedossa") => (valid-against? schema))

      (fact "other value (LPK-1945)"
        (fact
          "valid when the other key field muu-omistajalaji is filled"
          (apply-update document [:rakennuksenOmistajat :0 :omistajalaji] "other") => (valid-against? schema))
        (fact "invalid when the other key field is empty"
          (->
            document
            (apply-update [:rakennuksenOmistajat :0 :omistajalaji] "other")
            (apply-update [:rakennuksenOmistajat :0 :muu-omistajalaji] "")) => (invalid-with? schema [:tip "illegal-value:required"]))))))

(fact "apply-approval"
  (apply-approval {} nil ..status.. {:id ..id.. :firstName ..fn.. :lastName ..ln..} ..now..)
  =>
  {:meta {:_approved {:value ..status..
                      :user {:id ..id.. :firstName ..fn.. :lastName ..ln..}
                      :timestamp ..now..}}}
  (apply-approval {} [] ..status.. {:id ..id.. :firstName ..fn.. :lastName ..ln..} ..now..)
  =>
  {:meta {:_approved {:value ..status..
                      :user {:id ..id.. :firstName ..fn.. :lastName ..ln..}
                      :timestamp ..now..}}}
  (apply-approval {} [:a] ..status.. {:id ..id.. :firstName ..fn.. :lastName ..ln..} ..now..)
  =>
  {:meta {:a {:_approved {:value ..status..
                          :user {:id ..id.. :firstName ..fn.. :lastName ..ln..}
                          :timestamp ..now..}}}}
  (apply-approval {} [:a :b] ..status.. {:id ..id.. :firstName ..fn.. :lastName ..ln..} ..now..)
  =>
  {:meta {:a {:b {:_approved {:value ..status..
                              :user {:id ..id.. :firstName ..fn.. :lastName ..ln..}
                              :timestamp ..now..}}}}})

(def approvable-schema {:info {:name "approval-model" :version 1 :approvable true}
                        :body [{:name "s" :type :string}]})

(facts "approvable schema"
  (approvable? (new-document approvable-schema ..now..) []))

(facts "non-approvable schema"
  (approvable? nil) => false
  (approvable? {}) => false
  (approvable? (new-document schema ..now..)) => false)

(def schema-without-approvals {:info {:name "approval-model-without-approvals"
                                      :version 1
                                      :approvable false}
                               :body [{:name "single" :type :string :approvable true}
                                      {:name "single2" :type :string}
                                      {:name "repeats" :type :group :repeating true :approvable true
                                       :body [{:name "single3" :type :string}]}]})

(def schema-with-approvals {:info {:name "approval-model-with-approvals"
                                   :version 1
                                   :approvable true}
                            :body [{:name "single" :type :string :approvable true}
                                   {:name "single2" :type :string}
                                   {:name "repeats" :type :group :repeating true :approvable true
                                    :body [{:name "single3" :type :string}]}]})

(schemas/defschema 1 schema-without-approvals)
(schemas/defschema 1 schema-with-approvals)

(facts "approve document part"
  (let [document (new-document schema-with-approvals ..now..)]
    (approvable? document schema-with-approvals [:single]) => true
    (approvable? document schema-with-approvals [:single2]) => false
    (approvable? document schema-with-approvals [:repeats :1]) => true
    (approvable? document schema-with-approvals [:repeats :0 :single3]) => false))

(def uusiRakennus
  {:schema-info {:name "uusiRakennus",
                 :version 1
                 :removable true
                 :approvable true,
                 :op {:id "51b59c112438736b8f1b9d0d",
                      :name "kerrostalo-rivitalo",
                      :created 1370856465069}}
   :meta {:rakennuksenOmistajat {:0 {:_approved {:value "rejected"
                                                 :user {:lastName "Sibbo", :firstName "Sonja", :id "777777777777777777000023"}
                                                 :timestamp 1370856511356}}}}
   :data {:huoneistot {:0 {:huoneistonumero {:value "001"}}}
          :kaytto {:kayttotarkoitus {:value "011 yhden asunnon talot"}}
          :rakennuksenOmistajat {:0 {:_selected {:value "henkilo"}
                                     :henkilo {:henkilotiedot {:etunimi {:modified 1370856477455, :value "Pena"}
                                                               :sukunimi {:modified 1370856477455, :value "Panaani"}
                                                               :hetu     {:modified 1370856477455, :value "010203-040A"}
                                                               :turvakieltoKytkin {:modified 1370856477455, :value false}}
                                               :osoite {:katu {:modified 1370856477455, :value "Paapankuja 12"}
                                                        :postinumero {:value "10203", :modified 1370856487304}
                                                        :postitoimipaikannimi {:modified 1370856477455, :value "Piippola"}
                                                        :maa {:modified 1370856477455, :value "FIN"}}
                                               :userId {:value "777777777777777777000020", :modified 1370856477473}
                                               :yhteystiedot {:email {:modified 1370856477455, :value "pena@example.com"}
                                                              :puhelin {:modified 1370856477455, :value "0102030405"}}}}}}})

(facts "modifications-since-approvals"
  (with-timestamp 10
    (modifications-since-approvals nil) => 0
    (modifications-since-approvals {}) => 0
    (let [base-doc (-> (new-document schema-with-approvals 0) (apply-update [:single] "''"))]
      (modifications-since-approvals base-doc) => 1
      (modifications-since-approvals (apply-approval base-doc [:single] "approved" {} 9)) => 1
      (modifications-since-approvals (apply-approval base-doc [:single] "approved" {} 10)) => 0
      (modifications-since-approvals (apply-approval base-doc [] "approved" {} 9)) => 1
      (modifications-since-approvals (apply-approval base-doc [] "approved" {} 10)) => 0
      (modifications-since-approvals
        (-> base-doc
          (apply-approval [] "approved" {} 9)
          (apply-update [:single2] "")
          (apply-update [:repeats :0 :single3] "")
          (apply-update [:repeats :1 :single3] ""))) => 4
      (modifications-since-approvals
        (-> base-doc
          (apply-approval [] "approved" {} 11)
          (apply-update [:single2] "")
          (apply-update [:repeats :0 :single3] "")
          (apply-update [:repeats :1 :single3] ""))) => 0)
    (let [base-doc (-> (new-document schema-without-approvals 0) (apply-update [:single] "''"))]
      (modifications-since-approvals base-doc) => 1
      (modifications-since-approvals (apply-approval base-doc [:single] "approved" {} 9)) => 1
      (modifications-since-approvals (apply-approval base-doc [:single] "approved" {} 10)) => 0
      (modifications-since-approvals (apply-approval base-doc [] "approved" {} 9)) => 1
      (modifications-since-approvals (apply-approval base-doc [] "approved" {} 10)) => 0
      (modifications-since-approvals
        (-> base-doc
          (dissoc :data)
          (apply-approval [:repeats :0] "approved" {} 9)
          (apply-update [:single2] "")
          (apply-update [:repeats :0 :single3] "")
          (apply-update [:repeats :1 :single3] ""))) => 2
      (modifications-since-approvals
        (-> base-doc
          (dissoc :data)
          (apply-approval [:repeats :0] "approved" {} 11)
          (apply-update [:single2] "")
          (apply-update [:repeats :0 :single3] "")
          (apply-update [:repeats :1 :single3] ""))) => 1

      (fact "meta._indicator_reset.timestamp overrides approval indicator"
        (modifications-since-approvals (assoc-in base-doc [:meta :_indicator_reset :timestamp] 9)) => 1
        (modifications-since-approvals (assoc-in base-doc [:meta :_indicator_reset :timestamp] 10)) => 0)))

  (fact "real world uusiRakennus document has no modifications since approvals"
    (modifications-since-approvals uusiRakennus) => 0))

;;
;; Updates
;;

(facts "updating document"
  (with-timestamp some-time
    (fact "single value"
      (apply-update  {} [:b :c] "kikka") => {:data {:b {:c {:value "kikka" :modified some-time}}}})

    (fact "unsetting value"
      (-> {}
        (apply-update [:b :c] "kikka")
        (apply-update [:b :c])) => {:data {:b {:c {:value "" :modified some-time}}}})

    (fact "updates"
      (apply-updates {} [[[:b :c] "kikka"]
                         [[:b :d] "kukka"]]) => {:data {:b {:c {:value "kikka" :modified some-time}
                                                            :d {:value "kukka" :modified some-time}}}})
    (fact "update a map value"
      (apply-update {} [:a :b] {:c 1 :d {:e 2}}) => {:data {:a {:b {:c {:value 1 :modified some-time}
                                                                    :d {:e {:value 2 :modified some-time}}}}}})))

(fact "map2updates"
  (map2updates [:a :b] {:c 1 :d {:e 2}}) => (just [[[:a :b :c] 1]
                                                   [[:a :b :d :e] 2]] :in-any-order))

;;
;; Blacklist
;;

(def hakija {:schema-info {:name "hakija" :version 1}
             :data (get-in uusiRakennus [:data :rakennuksenOmistajat :0])})

(facts "meta tests"
  (let [app {:auth [{:id "777777777777777777000020"
                     :firstName "Pena"
                     :lastName "Panaani"
                     :username "pena"
                     :type "owner"
                     :role "owner"}
                     {:id "777777777777777777000023"
                      :firstName "Sonja"
                      :lastName "Sibbo"
                      :username "sonja"
                      :role "statementGiver"
                      :statementId "537c655dbc45cf55abf434a6"}]}]
    (has-errors? (validate app uusiRakennus))
    (has-errors? (validate app hakija))))

(facts "blacklists"
  (fact "no blacklist, no changes"
    (strip-blacklisted-data nil nil) => nil
    (strip-blacklisted-data {:schema-info {}} nil) => {:schema-info {}}
    (strip-blacklisted-data hakija nil) => hakija
    (strip-blacklisted-data hakija :x) => hakija
    (strip-blacklisted-data uusiRakennus :x) => uusiRakennus)

  (fact "schema-info is preserved"
    (:schema-info (strip-blacklisted-data hakija :neighbor)) => (:schema-info hakija))

  (fact "no hetu for neighbor, case hakija"
    (get-in hakija [:data :henkilo :henkilotiedot :hetu :value]) => truthy
    (get-in (strip-blacklisted-data hakija :neighbor) [:data :henkilo :henkilotiedot :hetu]) => nil)

  (fact "no hetu for neighbor, case asuintalo"
    (get-in uusiRakennus [:data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu]) => truthy
    (get-in (strip-blacklisted-data uusiRakennus :neighbor) [:data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu]) => nil))

(fact "strip blacklisted turvakielto data from hakija"
  (strip-blacklisted-data
    {:data (lupapalvelu.document.tools/wrapped
             {:henkilo
              {:henkilotiedot
               {:etunimi "Gustav",
                :sukunimi "Golem",
                :hetu "070550A907P",
                :turvakieltoKytkin true},
               :osoite {:katu "Katuosoite"},
               :yhteystiedot nil}})
     :schema-info {:name "hakija" :version 1}}
    :turvakieltoKytkin) =>
  {:data {:henkilo {:henkilotiedot {:etunimi {:value "Gustav"}, :hetu nil, :sukunimi {:value "Golem"}, :turvakieltoKytkin nil}, :osoite nil, :yhteystiedot nil}},
   :schema-info {:name "hakija" :version 1}})

(fact "strip-turvakielto-data from minimal uusiRakennus"
  (let [doc {:data (lupapalvelu.document.tools/wrapped
             {:rakennuksenOmistajat
              {:0
               {:henkilo
                {:henkilotiedot
                 {:etunimi "Gustav",
                  :sukunimi "Golem",
                  :hetu "070550-907P",
                  :turvakieltoKytkin true},
                 :osoite {:katu "Katuosoite"},
                 :yhteystiedot {}}}}})
     :schema-info {:name "uusiRakennus" :version 1}}]
    (has-errors? (validate {} doc)) => false
    (strip-turvakielto-data doc) =>
    {:data {:rakennuksenOmistajat {:0 {:henkilo {:henkilotiedot {:etunimi {:value "Gustav"}, :hetu nil, :sukunimi {:value "Golem"}, :turvakieltoKytkin nil}, :osoite nil, :yhteystiedot nil}}}},
     :schema-info {:name "uusiRakennus" :version 1}}))

(def hakija-with-turvakielto  (apply-update hakija [:henkilo :henkilotiedot (keyword schemas/turvakielto)] true))
(def uusiRakennus-with-turvakielto
  (assoc-in uusiRakennus [:data :rakennuksenOmistajat]
    {:0 (:data hakija)
     :1 (:data hakija-with-turvakielto)
     :2 (:data hakija)
     :3 (:data hakija-with-turvakielto)}))

(fact "Meta test: fixture is valid"
  (let [app {:auth [{:lastName "Panaani",
                     :firstName "Pena",
                     :username "pena",
                     :type "owner",
                     :role "owner",
                     :id "777777777777777777000020"}]}]
    (has-errors? (validate app hakija-with-turvakielto)) => false
    (has-errors? (validate app uusiRakennus-with-turvakielto)) => false))

(facts "turvakielto"

  (fact "no turvakielto, no changes"
    (strip-turvakielto-data nil) => nil
    (strip-turvakielto-data {}) => {}
    (strip-turvakielto-data hakija) => hakija
    (strip-turvakielto-data uusiRakennus) => uusiRakennus)

  (let [stripped-hakija (strip-turvakielto-data hakija-with-turvakielto)
        stripped-uusirakennus (strip-turvakielto-data uusiRakennus-with-turvakielto)]

    (fact "schema-info is preserved"
      (:schema-info stripped-hakija) => (:schema-info hakija)
      (:schema-info stripped-uusirakennus) => (:schema-info uusiRakennus))

    (facts "stripped documents are valid"
      (let [app {:auth [{:lastName "Panaani",
                     :firstName "Pena",
                     :username "pena",
                     :type "owner",
                     :role "owner",
                     :id "777777777777777777000020"}]}]
        (has-errors? (validate app stripped-hakija)) => false
        (has-errors? (validate app stripped-uusirakennus)) => false))

    (fact "meta test: turvakielto is set, there is data to be filtered"
      (get-in hakija-with-turvakielto [:data :henkilo :henkilotiedot :turvakieltoKytkin :value]) => true
      (get-in hakija-with-turvakielto [:data :henkilo :yhteystiedot]) => truthy
      (get-in hakija-with-turvakielto [:data :henkilo :osoite]) => truthy
      (get-in hakija-with-turvakielto [:data :henkilo :henkilotiedot :hetu]) => truthy)

    (fact "turvakielto data is stripped from hakija"
      (get-in stripped-hakija [:data :henkilo :henkilotiedot schemas/turvakielto]) => nil
      (get-in stripped-hakija [:data :henkilo :yhteystiedot]) => nil
      (get-in stripped-hakija [:data :henkilo :osoite]) => nil
      (get-in stripped-hakija [:data :henkilo :henkilotiedot :hetu]) => nil
      (get-in stripped-hakija [:data :henkilotiedot :etunimi]) => (get-in hakija [:data :henkilotiedot :etunimi])
      (get-in stripped-hakija [:data :henkilotiedot :sukunimi]) => (get-in hakija [:data :henkilotiedot :sukunimi]))

    (facts "turvakielto data is stripped from uusiRakennus"
      (fact "without owners there are no changes"
        (util/dissoc-in uusiRakennus [:data :rakennuksenOmistajat]) => (util/dissoc-in stripped-uusirakennus [:data :rakennuksenOmistajat]))

      (fact "has 4 owners"
        (keys (get-in stripped-uusirakennus [:data :rakennuksenOmistajat])) => (just [:0 :1 :2 :3]))

      (fact "owners 0 & 2 are intact"
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :0]) => (:data hakija)
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :2]) => (:data hakija))

      (fact "some henkilotiedot"
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :1 :henkilo :henkilotiedot]) => truthy)

      (fact "no hetu"
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :1 :henkilo :henkilotiedot :hetu]) => nil)

      (fact "no turvakieltoKytkin"
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :1 :henkilo :henkilotiedot :turvakieltoKytkin]) => nil)

      (fact "owners 1 & 3 match stripped-hakija"
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :1]) => (:data stripped-hakija)
        (get-in stripped-uusirakennus [:data :rakennuksenOmistajat :3]) => (:data stripped-hakija)))))

(facts "hetu-mask"
  (fact "ending is masked"
    (let [masked (mask-person-id-ending hakija)]
      (get-in masked [:data :henkilo :henkilotiedot :hetu]) => truthy
      (get-in masked [:data :henkilo :henkilotiedot :hetu :value]) => "010203-****"
      (fact "non-related field is not changed"
        (get-in masked [:data :henkilo :henkilotiedot :etunimi :value]) => (get-in hakija [:data :henkilo :henkilotiedot :etunimi :value]))))
  (fact "birthday is masked"
    (let [masked (mask-person-id-birthday hakija)]
      (get-in masked [:data :henkilo :henkilotiedot :hetu]) => truthy
      (get-in masked [:data :henkilo :henkilotiedot :hetu :value]) => "******-040A"
      (fact "non-related field is not changed"
        (get-in masked [:data :henkilo :henkilotiedot :etunimi :value]) => (get-in hakija [:data :henkilo :henkilotiedot :etunimi :value]))))
  (fact "combination"
    (let [masked (-> hakija mask-person-id-ending mask-person-id-birthday)]
      (get-in masked [:data :henkilo :henkilotiedot :hetu :value]) => "******-****")))

(facts without-user-id
  (without-user-id nil) => nil
  (without-user-id {}) => {}
  (without-user-id {:a nil}) => {:a nil}
  (without-user-id {:userId nil}) => {}
  (without-user-id {:data {:userId nil}, :schema-info {}}) => {:data {}, :schema-info {}}
  (without-user-id {:data {:henkilo {:userId {:value "x"} :henkilotiedot {}}}}) => {:data {:henkilo {:henkilotiedot {}}}})

(facts
  (fact "all fields are mapped"
    (->henkilo {:id        "id"
                :firstName "firstName"
                :lastName  "lastName"
                :email     "email"
                :phone     "phone"
                :street    "street"
                :zip       "zip"
                :city      "city"}) => {:userId                        {:value "id"}
                                        :henkilotiedot {:etunimi       {:value "firstName"}
                                                        :sukunimi      {:value "lastName"}}
                                        :yhteystiedot {:email          {:value "email"}
                                                       :puhelin        {:value "phone"}}
                                        :osoite {:katu                 {:value "street"}
                                                 :postinumero          {:value "zip"}
                                                 :postitoimipaikannimi {:value "city"}}})

  (fact "all fields are mapped - empty defaults"
    (->henkilo {:id "id" :lastName "lastName" :city "city"} :with-empty-defaults? true)
    => {:userId                        {:value "id"}
        :henkilotiedot {:etunimi       {:value ""}
                        :sukunimi      {:value "lastName"}
                        :hetu          {:value ""}
                        :turvakieltoKytkin {:value false}}
        :yhteystiedot {:email          {:value ""}
                       :puhelin        {:value ""}}
        :kytkimet {:suoramarkkinointilupa {:value false}}
        :osoite {:katu                 {:value ""}
                 :postinumero          {:value ""}
                 :postitoimipaikannimi {:value "city"}}
        :patevyys {:fise {:value ""}
                   :fiseKelpoisuus {:value ""}
                   :koulutusvalinta {:value ""}
                   :valmistumisvuosi {:value ""}}
        :patevyys-tyonjohtaja {:koulutusvalinta {:value ""}
                               :valmistumisvuosi {:value ""}}
        :yritys   {:liikeJaYhteisoTunnus {:value ""}
                   :yritysnimi {:value ""}}} )

  (fact "no fields are mapped"
    (->henkilo {} => {}))

  (fact "some fields are mapped"
    (->henkilo {:firstName "firstName"
                :zip       "zip"
                :turvakieltokytkin true}) => {:henkilotiedot {:etunimi {:value "firstName"}
                                                              :turvakieltoKytkin {:value true}}
                                              :osoite {:postinumero {:value "zip"}}})

  (fact "hetu is mapped"
    (->henkilo {:id       "id"
                :personId "123"} :with-hetu true) => {:userId               {:value "id"}
                                                      :henkilotiedot {:hetu {:value "123"}}}))

(facts "has-hetu?"
  (fact "direct find"
    (has-hetu? schemas/party)            => true
    (has-hetu? schemas/party [:henkilo]) => true
    (has-hetu? schemas/party [:invalid]) => false)
  (fact "nested find"
    (has-hetu? [{:name "a"
                 :type :group
                 :body [{:name "b"
                         :type :group
                         :body schemas/party}]}] [:a :b :henkilo]) => true))

(facts inspect-repeating-for-duplicate-rows

  (fact "do not match non-repeating data"
    (inspect-repeating-for-duplicate-rows
     {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
     [:huoneistonumero]) => nil)

  (fact "two equal rows"
    (inspect-repeating-for-duplicate-rows
     {:0 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
      :1 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}}
     [:porras :huoneistonumero :jakokirjain :muutostapa]) => (just #{:0 :1}))

  (fact "two rows not equal"
    (inspect-repeating-for-duplicate-rows
     {:0 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
      :1 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}}
     [:porras :huoneistonumero :jakokirjain :muutostapa]) => nil)

  (fact "inspect one field out of four"
    (inspect-repeating-for-duplicate-rows
     {:0 {:porras "A" :huoneistonumero "1" :jakokirjain "g" :muutostapa "muutos"}
      :1 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "lisays"}}
     [:huoneistonumero]) => (just #{:0 :1}))

  (fact "two equal pairs"
    (inspect-repeating-for-duplicate-rows
     {:0 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
      :1 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
      :2 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
      :3 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}}
     [:porras :huoneistonumero :jakokirjain :muutostapa]) => (just #{:0 :1 :2 :3}))

  (fact "all together"
    (inspect-repeating-for-duplicate-rows
     {:0 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"} ; equal with :3, :8
      :1 {:porras "A" :huoneistonumero "2" :jakokirjain "f" :muutostapa "muutos"}
      :2 {:porras "A" :huoneistonumero "3" :jakokirjain "f" :muutostapa "muutos"}
      :3 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"} ; equal with :1, :8
      :4 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"} ; equal with :6
      :5 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "lisays"}
      :6 {:porras "B" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"} ; equal with :4
      :7 {:porras "B" :huoneistonumero "1" :jakokirjain "g" :muutostapa "muutos"}
      :8 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}} ; equal with :1, :3
     [:porras :huoneistonumero :jakokirjain :muutostapa]) => (just #{:0 :3 :4 :6 :8})))
