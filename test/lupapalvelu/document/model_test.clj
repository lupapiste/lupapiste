(ns lupapalvelu.document.model-test
  (:require [lupapalvelu.document.approval :as appr]
            [lupapalvelu.document.model :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.validators :refer [valid-against? invalid-with? valid?]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(testable-privates lupapalvelu.document.model
                   data-match? inspect-repeating-for-duplicate-rows)

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
                                    {:name "C"}]}
                            {:name "e" :type :autocomplete
                             :body [{:name "A"
                                     :group :hii}
                                    {:name "B"}
                                    {:name "C"}]}
                            {:name "pitaja" :type :review-officer-dropdown
                             :body [{:name "A" :code "a" :id 0 :_atomic-map? true}
                                    {:name "B" :code "b" :id 1 :_atomic-map? true}
                                    {:name "C" :code "c" :id 2 :_atomic-map? true}]}
                            ]}]})

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
  (validate-field {} {:type :date} "abba") => [:err "illegal-value:date"]
  (validate-field {} {:type :date} "") => nil
  (validate-field {} {:type :date} "11.12.2013") => nil
  (validate-field {} {:type :date} "1.2.2013") => nil
  (validate-field {} {:type :date} "01.02.2013") => nil
  (validate-field {} {:type :date} "11.12.13") => [:err "illegal-value:date"]
  (validate-field {} {:type :date} "41.12.2013") => [:err "illegal-value:date"]
  (validate-field {} {:type :date} "1.13.2013") => [:err "illegal-value:date"])

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

(facts "msDate validation"
  (validate-field {} {:type :msDate} 1479464714612) => nil?
  (validate-field {} {:type :msDate} "abcde") => [:err "illegal-value:msDate-NotValidDate"])

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

(facts "Autocomplete"
  (with-timestamp some-time
    (let [document (new-document schema ..now..)]
      (apply-update document [:a :e] "A") => (valid-against? schema)
      (apply-update document [:a :e] "")  => (valid-against? schema)
      (apply-update document [:a :e] "D") => (invalid-with? schema [:warn "illegal-value:autocomplete"]))))

(facts "Radio group"
  (with-timestamp some-time
    (let [document (new-document schema ..now..)]
      (facts "valid cases"
        (apply-update document [:a :partytype] "henkilo") => (valid-against? schema)
        (apply-update document [:a :partytype] "yritys") => (valid-against? schema)
        (apply-update document [:a :partytype] "")  => (valid-against? schema))
      (fact "invalid value"
        (apply-update document [:a :partytype] "neither") => (invalid-with? schema [:warn "illegal-value:select"])))))

(facts "Review officer dropdown"
  (with-timestamp some-time
    (let [document (new-document schema ..now..)]
      (fact "map value"
        (apply-update document [:a :pitaja] {:name "name" :code "code" :id "id"}) => (valid-against? schema))
      (fact "string value"
        (apply-update document [:a :pitaja] "pitaja") => (valid-against? schema)))))

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
                                                          {:name "od2" :type :string}]}]}
                                          {:name "e" :type :group
                                           :body [{:name "e1" :type :string :other-key "e2"}
                                                  {:name "e2" :type :string}]}]}]})

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
          (apply-update [:a :d :1 :d2 :6 :rd] "value")) => (valid-against? schema-with-required)

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :d :0 :d2 :0 :od1] "value")
          (apply-update [:a :d :0 :d2 :0 :rd] "value")
          (apply-update [:a :d :1 :d2 :6 :rd] "value")
          (apply-update [:a :e :e1] "other")) => missing-required-fields?

      (-> document
          (apply-update [:a :b :aa] "value")
          (apply-update [:a :b :ab] "value")
          (apply-update [:a :c :0 :rab] "value")
          (apply-update [:a :d :0 :d2 :0 :od1] "value")
          (apply-update [:a :d :0 :d2 :0 :rd] "value")
          (apply-update [:a :d :1 :d2 :6 :rd] "value")
          (apply-update [:a :e :e1] "other")
          (apply-update [:a :e :e2] "value")) => (valid-against? schema-with-required)))

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
        (fact "Non-selected is ignored"
          (-> document
              (apply-update [:yritys :osoite :postinumero] "000")) => valid?
          (-> document
              (apply-update [:_selected] "yritys")
              (apply-update [:yritys :osoite :postinumero] "000")) => (invalid-with? schema [:err "bad-postal-code"]))
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

(defn process
  "Takes the validation (regular or pertinent) results and returns kw-path - ignore map."
  [results]
  (->> results
       (map (fn [{:keys [path ignore]}]
              [(util/kw-path path) (boolean ignore)]))
       (into {})))

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
          pertinent (validate-pertinent {:application {}} document)]

      (fact "Result counts"
        [(count all) (count pertinent)] => [3 2])
      (fact "All results"
        (process all) => {:henkilo.henkilotiedot.etunimi false
                          :henkilo.henkilotiedot.hetu    false
                          :yritys.liikeJaYhteisoTunnus   true})
      (fact "Pertinent results"
        (process pertinent) => {:henkilo.henkilotiedot.etunimi false
                                :henkilo.henkilotiedot.hetu    false})
      (facts "Not a Finnish hetu"
        (let [doc (-> document
                      (apply-update [:henkilo :henkilotiedot :etunimi] "Name")
                      (apply-update [:yritys :liikeJaYhteisoTunnus] "0000000-0")
                      (apply-update [:henkilo :henkilotiedot :not-finnish-hetu] true))]
          (fact "Regular validation includes Finnish hetu (with ignore)"
            (process (validate {} doc))
            => {:henkilo.henkilotiedot.hetu                    true
                :henkilo.henkilotiedot.ulkomainenHenkilotunnus false})
          (fact "Pertinent validation without KuntaGML version"
            (process (validate-pertinent {:application {}} doc))
            => {:henkilo.henkilotiedot.hetu false})
          (fact "Pertinent validation with KuntaGML version 2.2.4"
            (process (validate-pertinent {:application {:permitType "R"}
                                          :organization (delay {:krysp {:R {:version "2.2.4"}}})}
                                         doc))
            => {:henkilo.henkilotiedot.ulkomainenHenkilotunnus false})))

      (fact "All contains ytunnus with ignore"
        all => (contains [(contains {:ignore true
                                     :path   [:yritys :liikeJaYhteisoTunnus]})]))
      (fact "Pertinent does not contain ytunnus"
        pertinent =not=> (contains [(contains {:path [:yritys :liikeJaYhteisoTunnus]})]))
      (facts "Change _selected"
        (let [document  (apply-update document [:_selected] "yritys")
              all       (validate {} document)
              pertinent (validate-pertinent {:application {}} document)]
          (fact "Result counts"
            [(count all) (count pertinent)] => [3 2])
          (fact "All contains hetu with ignore"
            all => (contains [(contains {:ignore true
                                         :path   [:henkilo :henkilotiedot :hetu]})]))
          (fact "Pertinent does not contain hetu"
            pertinent =not=> (contains [(contains {:path [:henkilo :henkilotiedot :hetu]})]))))
      (facts "Pertinent validation skips disabled documents"
        (fact "enabled"
          (validate-pertinent {:application {}} document) => (contains [(contains {:path [:henkilo :henkilotiedot :hetu]})]))
        (fact "disabled"
          (validate-pertinent {:application {}} (assoc document :disabled true))
          => nil))))

  (let [schema     (schemas/get-schema (schemas/get-latest-schema-version) "task-katselmus")
        empty-task (assoc (new-document schema some-time)
                          :state "requires_user_action")
        valid-task (-> empty-task
                       (apply-update [:katselmuksenLaji] "aloituskokous" )
                       (apply-update [:katselmus :pitaja] "Rebecca Reviewer" )
                       (apply-update [:katselmus :pitoPvm] "31.12.2020")
                       (apply-update [:katselmus :tila] "lopullinen"))]
    (facts "Pertinent validation skips sent reviews"
      (fact "Review draft: valid"
        (:state valid-task) => "requires_user_action"
        (validate-pertinent {:application {}} valid-task) => empty?)
      (fact "Review draft: empty"
        (:state empty-task) => "requires_user_action"
        (validate-pertinent {:application {}} empty-task) => not-empty)
      (fact "Review draft: pitoPvm missing"
        (validate-pertinent {:application {}} (apply-update valid-task [:katselmus :pitoPvm] nil))
        =>  (contains [(contains {:path [:katselmus :pitoPvm]})]))
      (fact "Sent review: valid"
        (validate-pertinent {:application {}} (assoc valid-task :state "sent")) => nil)
      (fact "Sent review: missing/invalid data is ignored"
        (validate-pertinent {:application {}}
                            (assoc empty-task :state "sent")) => nil
        (validate-pertinent {:application {}}
                            (apply-update (assoc valid-task :state "sent")
                                          [:katselmus :pitoPvm] nil)) => nil)
      (fact "Sent but not a review"
        (validate-pertinent {:application {}}
                            (-> empty-task
                                (assoc :state "sent")
                                (assoc-in [:schema-info :subtype] "foobar")))
        => not-empty))))

(defn ->result-by-path [validation-errors]
  (->> validation-errors
       (map (juxt :path :result))
       (into {})))

(facts "validate for huoneisto"
  (with-timestamp some-time
    (let [schema (schemas/get-schema (schemas/get-latest-schema-version) "rakennuksen-laajentaminen")
          document-with-huoneisto-values
          (-> (new-document schema ..now..)

              ;; First huoneisto with missing required fields and bad data
              (apply-update [:huoneistot :0 :WCKytkin] "bad")
              (apply-update [:huoneistot :0 :muutostapa] "muutos")

              ;; Second huoneisto with required fields present and good data
              (apply-update [:huoneistot :1 :keittionTyyppi] "keittio")
              (apply-update [:huoneistot :1 :huoneluku] "2")
              (apply-update [:huoneistot :1 :huoneistoala] "50")
              (apply-update [:huoneistot :1 :huoneistonumero] "1")
              (apply-update [:huoneistot :1 :muutostapa] "lisäys")

              ;; Third huoneisto with missing required fields and bad data but muutostapa is poisto
              (apply-update [:huoneistot :2 :WCKytkin] "bad")
              (apply-update [:huoneistot :2 :muutostapa] "poisto")

              ;; Fourth huoneisto with bad dat, missing both required fields and muutostapa
              (apply-update [:huoneistot :3 :WCKytkin] "bad"))

          result (->result-by-path (validate {} document-with-huoneisto-values))]

      (fact "reports missing required fields when huoneisto present but lacks the required fields"
        result => (contains {[:huoneistot :0 :WCKytkin] [:err "illegal-value:not-a-boolean"]})
        result => (contains {[:huoneistot :0 :keittionTyyppi] [:tip "illegal-value:required"]})
        result => (contains {[:huoneistot :0 :huoneluku] [:tip "illegal-value:required"]})
        result => (contains {[:huoneistot :0 :huoneistoala] [:tip "illegal-value:required"]})
        result => (contains {[:huoneistot :0 :huoneistonumero] [:tip "illegal-value:required"]}))

      (fact "does not report missing required fields when they are present"
        result =not=> (contains {[:huoneistot :1 :WCKytkin] [:err "illegal-value:not-a-boolean"]})
        result =not=> (contains {[:huoneistot :1 :keittionTyyppi] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :1 :huoneluku] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :1 :huoneistoala] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :1 :huoneistonumero] [:tip "illegal-value:required"]}))

      (fact "does not report missing required fields when muutostapa is poisto"
        result =not=> (contains {[:huoneistot :2 :WCKytkin] [:err "illegal-value:not-a-boolean"]})
        result =not=> (contains {[:huoneistot :2 :keittionTyyppi] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :2 :huoneluku] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :2 :huoneistoala] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :2 :huoneistonumero] [:tip "illegal-value:required"]}))

      (fact "does not report missing required fields when muutostapa is missing"
        result =not=> (contains {[:huoneistot :3 :WCKytkin] [:err "illegal-value:not-a-boolean"]})
        result =not=> (contains {[:huoneistot :3 :keittionTyyppi] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :3 :huoneluku] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :3 :huoneistoala] [:tip "illegal-value:required"]})
        result =not=> (contains {[:huoneistot :3 :huoneistonumero] [:tip "illegal-value:required"]}))

      (fact "No duplicate row warnings for empty porras/huoneistonumero/jokokirjain"
        (map second (vals result))) =not=> (contains "duplicate-apartment-data"))))

(def omistajat-schema {:info {:name "rakennuksen-omistajat-test-schema"}
                       :body schemas/rakennuksen-omistajat})

(against-background
  [(lupapalvelu.document.schemas/get-schema anything) => omistajat-schema]
  (facts "Rakennuksen omistajat (repeating with :_selected)"
   (with-timestamp some-time
     (let [schema   omistajat-schema
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

       (facts "Omistajalaji"
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
               (apply-update [:rakennuksenOmistajat :0 :muu-omistajalaji] "")) => (invalid-with? schema [:tip "illegal-value:required"]))))

       (facts "Repeating vs. :_selected"
         (let [doc-person  (-> document
                               (apply-update [:rakennuksenOmistajat :0 :yritys :liikeJaYhteisoTunnus] "bad-y")
                               (apply-update [:rakennuksenOmistajat :0 :_selected] "henkilo")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :henkilotiedot :etunimi] "Pauno")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :henkilotiedot :sukunimi] "Talo")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu] "bad-hetu")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :osoite :katu] "katu")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :osoite :postinumero] "12345")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :osoite :postitoimipaikannimi] "Demola")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :osoite :maa] "FIN")
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :yhteystiedot :puhelin] "050"))
               doc-firm    (-> doc-person
                               (apply-update [:rakennuksenOmistajat :0 :_selected] "yritys")
                               (apply-update [:rakennuksenOmistajat :0 :yritys :yhteyshenkilo :yhteystiedot :email] ""))
               doc-person2 (-> doc-person
                               (apply-update [:rakennuksenOmistajat :0 :henkilo :henkilotiedot :not-finnish-hetu] true))
               doc-firm2   (-> doc-person2
                               (apply-update [:rakennuksenOmistajat :0 :_selected] "yritys"))
               command     {:application  {:permitType "R"}
                            :organization (delay {:krysp {:R {:version "2.2.4"}}})}]

           (facts "Validate"
             (fact doc-person
               (process (validate {} doc-person schema))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu  false
                   :rakennuksenOmistajat.0.henkilo.yhteystiedot.email  false
                   :rakennuksenOmistajat.0.omistajalaji                false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus true})
             (fact doc-firm
               (process (validate {} doc-firm schema))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu              true
                   :rakennuksenOmistajat.0.yritys.yhteyshenkilo.yhteystiedot.email false
                   :rakennuksenOmistajat.0.omistajalaji                            false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus             false})
             (fact doc-person2
               (process (validate {} doc-person2 schema))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu                    true
                   :rakennuksenOmistajat.0.henkilo.henkilotiedot.ulkomainenHenkilotunnus false
                   :rakennuksenOmistajat.0.henkilo.yhteystiedot.email                    false
                   :rakennuksenOmistajat.0.omistajalaji                                  false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus                   true})
             (fact doc-firm2
               (process (validate {} doc-firm2 schema))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu  true
                   :rakennuksenOmistajat.0.omistajalaji                false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus false}))

           (facts "Validate pertinent"
             (fact doc-person
               (process (validate-pertinent {:application {}} doc-person))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu false
                   :rakennuksenOmistajat.0.henkilo.yhteystiedot.email false
                   :rakennuksenOmistajat.0.omistajalaji               false})
             (fact doc-firm
               (process (validate-pertinent {:application {}} doc-firm))
               => {:rakennuksenOmistajat.0.yritys.yhteyshenkilo.yhteystiedot.email false
                   :rakennuksenOmistajat.0.omistajalaji                            false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus             false})
             (fact doc-person2
               (process (validate-pertinent {:application {}} doc-person2))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu false
                   :rakennuksenOmistajat.0.henkilo.yhteystiedot.email false
                   :rakennuksenOmistajat.0.omistajalaji               false})
             (fact doc-firm2
               (process (validate-pertinent {:application {}} doc-firm2))
               => {:rakennuksenOmistajat.0.omistajalaji                false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus false}))

           (facts "Validate pertinent: R 2.2.4"
             (fact doc-person
               (process (validate-pertinent command doc-person))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu false
                   :rakennuksenOmistajat.0.henkilo.yhteystiedot.email false
                   :rakennuksenOmistajat.0.omistajalaji               false})
             (fact doc-firm
               (process (validate-pertinent command doc-firm))
               => {:rakennuksenOmistajat.0.yritys.yhteyshenkilo.yhteystiedot.email false
                   :rakennuksenOmistajat.0.omistajalaji                            false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus             false})
             (fact doc-person2
               (process (validate-pertinent command doc-person2))
               => {:rakennuksenOmistajat.0.henkilo.henkilotiedot.ulkomainenHenkilotunnus false
                   :rakennuksenOmistajat.0.henkilo.yhteystiedot.email                    false
                   :rakennuksenOmistajat.0.omistajalaji                                  false})
             (fact doc-firm2
               (process (validate-pertinent command doc-firm2))
               => {:rakennuksenOmistajat.0.omistajalaji                false
                   :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus false}))

           (facts "Two items"
             (let [doc (-> document
                           (apply-update [:rakennuksenOmistajat :0 :yritys :liikeJaYhteisoTunnus] "bad-y")
                           (apply-update [:rakennuksenOmistajat :1 :_selected] "henkilo")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :henkilotiedot :etunimi] "Pauno")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :henkilotiedot :sukunimi] "Talo")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :henkilotiedot :hetu] "bad-hetu")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :henkilotiedot :not-finnish-hetu] false)
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :henkilotiedot
                                          :ulkomainenHenkilotunnus] "")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :osoite :katu] "katu")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :osoite :postinumero] "12345")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :osoite :postitoimipaikannimi] "Demola")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :osoite :maa] "FIN")
                           (apply-update [:rakennuksenOmistajat :1 :henkilo :yhteystiedot :puhelin] "050"))]
               (facts "Validate"
                 (process (validate {} doc schema))
                 => {:rakennuksenOmistajat.0.omistajalaji                false
                     :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus false
                     :rakennuksenOmistajat.1.henkilo.henkilotiedot.hetu  false
                     :rakennuksenOmistajat.1.henkilo.yhteystiedot.email  false
                     :rakennuksenOmistajat.1.omistajalaji                false})
               (facts "Validate: non-Finnish hetu"
                 (process (validate {}
                                    (apply-update doc [:rakennuksenOmistajat :1 :henkilo :henkilotiedot
                                                       :not-finnish-hetu] true)
                                    schema))
                 => {:rakennuksenOmistajat.0.omistajalaji                                  false
                     :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus                   false
                     :rakennuksenOmistajat.1.henkilo.henkilotiedot.hetu                    true
                     :rakennuksenOmistajat.1.henkilo.henkilotiedot.ulkomainenHenkilotunnus false
                     :rakennuksenOmistajat.1.henkilo.yhteystiedot.email                    false
                     :rakennuksenOmistajat.1.omistajalaji                                  false})
               (facts "Validate pertinent"
                 (process (validate-pertinent {:application {}} doc))
                 => {:rakennuksenOmistajat.0.omistajalaji                false
                     :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus false
                     :rakennuksenOmistajat.1.henkilo.henkilotiedot.hetu  false
                     :rakennuksenOmistajat.1.henkilo.yhteystiedot.email  false
                     :rakennuksenOmistajat.1.omistajalaji                false})
               (facts "Validate pertinent: non-Finnish hetu"
                 (process (validate-pertinent {:application {}}
                                              (apply-update doc [:rakennuksenOmistajat :1 :henkilo :henkilotiedot
                                                                 :not-finnish-hetu] true)))
                 => {:rakennuksenOmistajat.0.omistajalaji                false
                     :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus false
                     :rakennuksenOmistajat.1.henkilo.henkilotiedot.hetu  false
                     :rakennuksenOmistajat.1.henkilo.yhteystiedot.email  false
                     :rakennuksenOmistajat.1.omistajalaji                false})
               (facts "Validate: non-Finnish hetu, KuntaGML R 2.2.4"
                 (process (validate-pertinent command
                                              (apply-update doc [:rakennuksenOmistajat :1 :henkilo :henkilotiedot
                                                                 :not-finnish-hetu] true)))
                 => {:rakennuksenOmistajat.0.omistajalaji                                  false
                     :rakennuksenOmistajat.0.yritys.liikeJaYhteisoTunnus                   false
                     :rakennuksenOmistajat.1.henkilo.henkilotiedot.ulkomainenHenkilotunnus false
                     :rakennuksenOmistajat.1.henkilo.yhteystiedot.email                    false
                     :rakennuksenOmistajat.1.omistajalaji                                  false})))))))))


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

(def uusiRakennus
  {:schema-info {:name "uusiRakennus",
                 :version 1
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

                                        ; This seems to have been in use at somepoint, but currently dead code.
                                        ; But used as utility in these tests, so let is be here.
(defn apply-approval
  "Merges approval meta data into a map.
   To be used within with-timestamp or with a given timestamp."
  ([document path status user]
   (assoc-in document (filter (comp not nil?) (flatten [:meta path :_approved])) (appr/->approved status user)))
  ([document path status user timestamp]
   (with-timestamp timestamp (apply-approval document path status user))))

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
                     :role "writer"}
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
                     :username "pena"
                     :role "writer",
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
                         :role "writer",
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

  (fact "all fields are mapped - empty defaults - note that ulkomainenHenkilotunnus and not-finnish-hetu are left"
    (->henkilo {:id "id" :lastName "lastName" :city "city"} :with-empty-defaults? true)
    => {:userId                                  {:value "id"}
        :henkilotiedot {:etunimi                 {:value ""}
                        :sukunimi                {:value "lastName"}
                        :hetu                    {:value ""}
                        :turvakieltoKytkin       {:value false}}
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
                :personId "123"} :with-hetu true) => {:userId                           {:value "id"}
                                                      :henkilotiedot {:hetu             {:value "123"}}})

  (fact "not-finnish-hetu is set to false if hetu and ulkomainen hetu exists"
    (->henkilo {:id                   "id"
                :personId             "123"
                :non-finnish-personId "534"
                :not-finnish-hetu     false} :with-hetu true)
    => {:henkilotiedot {:hetu {:value "123"}
                        :not-finnish-hetu        {:value false}
                        :ulkomainenHenkilotunnus {:value "534"}}
        :userId {:value "id"}})

  (fact "not-finnish-hetu is set to true if hetu does not exists but ulkomainen hetu exists"
    (->henkilo {:id                   "id"
                :non-finnish-personId "534"
                :not-finnish-hetu     true} :with-hetu true)
    => {:henkilotiedot {:not-finnish-hetu        {:value true}
                        :ulkomainenHenkilotunnus {:value "534"}}
        :userId {:value "id"}}))

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

(facts "inspect-repeating-for-duplicate-rows"

  (fact "do not match non-repeating data"
    (inspect-repeating-for-duplicate-rows
      {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
      [:huoneistonumero]) => nil)

  (fact "two equal rows"
    (inspect-repeating-for-duplicate-rows
      {:0 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}
       :1 {:porras "A" :huoneistonumero "1" :jakokirjain "f" :muutostapa "muutos"}}
      [:porras :huoneistonumero :jakokirjain :muutostapa]) => (just #{:0 :1}))

  (fact "two equal rows: 'blank rows' are ignored"
    (inspect-repeating-for-duplicate-rows
      {:0 {:porras "  " :huoneistonumero "" :jakokirjain nil :foo "bar"}
       :1 {:porras "" :huoneistonumero nil :jakokirjain "  " :bar "baz"}}
      [:porras :huoneistonumero :jakokirjain]) => nil)

  (fact "two equal rows: some data"
    (inspect-repeating-for-duplicate-rows
      {:0 {:porras "  " :huoneistonumero "1" :jakokirjain nil :foo "bar"}
       :1 {:porras "" :huoneistonumero "1" :jakokirjain "  " :bar "baz"}}
      [:porras :huoneistonumero :jakokirjain]) => (just #{:0 :1})
    (inspect-repeating-for-duplicate-rows
      {:0 {:porras "A" :huoneistonumero "" :jakokirjain nil :foo "bar"}
       :1 {:porras "A" :huoneistonumero "" :jakokirjain "  " :bar "baz"}
       :2 {:porras "" :huoneistonumero "2" :jakokirjain "  " :bar "baz"}}
      [:porras :huoneistonumero :jakokirjain]) => (just #{:0 :1}))

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

(def visibility-schema {:info {:name       "visibility-schema"
                               :version    1
                               :approvable false}
                        :body [{:name "rule1" :type :checkbox}
                               {:name "rule2" :type :string}
                               {:name "one" :type :time :required true :show-when {:path   "rule1"
                                                                                   :values #{true}}}
                               {:name "two" :type :date :required true :hide-when {:path   "rule1"
                                                                                   :values #{true}}}
                               {:name "three" :type :time :show-when {:path   "rule2"
                                                                      :values #{"yes" "juu" "dui"}}}
                               {:name      "four" :type :date
                                :hide-when {:path   "rule2"
                                            :values #{"no" "ei" "bu"}}
                                :show-when {:path   "rule1"
                                            :values #{true}}}]})

(def crossref-schema {:info {:name       "crossref-schema"
                             :version    1
                             :approvable false}
                      :body [{:name      "other1" :type :string :required true
                              :show-when {:document "visibility-schema"
                                          :path     "rule1"
                                          :values   #{true}}}]})

(schemas/defschemas 1 [visibility-schema
                       crossref-schema])

(defn doc-data [name data]
  {:schema-info {:name    name
                 :version 1}
   :data        (reduce-kv (fn [acc k v]
                             (assoc-in acc [k :value] v))
                           {}
                           data)})

(defn validate-visibility
  ([docs name data]
   (validate {:documents docs}
             (doc-data name data)))
  ([data]
   (validate-visibility [] "visibility-schema" data)))

(defn required [k]
  {:path   [k]
   :result [:tip "illegal-value:required"]})

(defn ignored [k]
  {:path   [k]
   :ignore true})

(defn bad [k]
  (let [t (:type (util/find-by-key :name (name k) (:body visibility-schema)))]
    {:path   [k]
     :result [(if (= t :date) :err :warn)  (str "illegal-value" t)]}))

(facts "hide-when and show-when vs. validation"
  (fact "Empty document"
    (validate-visibility {})
    => (just [(contains (required :two))]))
  (fact "Invisible errors are ignored"
    (validate-visibility {:one "bad"})
    => (just [(contains (ignored :one))
              (contains (required :two))]))
  (fact "Rule1 ON"
    (validate-visibility {:rule1 true})
    => (just [(contains (required :one))])
    (validate-visibility {:rule1 true :one "bad"})
    => (just [(contains (bad :one))])
    (validate-visibility {:rule1 true
                          :one "bad"
                          :two "bad"
                          :three "bad"
                          :four "bad"})
    => (just [(contains (bad :one))
              (contains (ignored :two))
              (contains (ignored :three))
              (contains (bad :four))]))
  (fact "Rule2 YES"
    (validate-visibility {:rule2 "dui"})
    => (just [(contains (required :two))])
    (validate-visibility {:rule2 "dui"
                          :one "bad"
                          :two "19.11.2018"
                          :three "bad"
                          :four "bad"})
    => (just [(contains (ignored :one))
              (contains (bad :three))
              (contains (ignored :four))]))
  (fact "Rule2 NO"
    (validate-visibility {:rule2 "bu"})
    => (just [(contains (required :two))])
    (validate-visibility {:rule2 "bu"
                          :one "bad"
                          :two "19.11.2018"
                          :three "bad"
                          :four "bad"})
    => (just [(contains (ignored :one))
              (contains (ignored :three))
              (contains (ignored :four))]))
  (fact "Rule1 ON Rule2 YES"
    (validate-visibility {:rule1 true :rule2 "yes"})
    => (just [(contains (required :one))])
    (validate-visibility {:rule1 true :rule2 "yes"
                          :one "19:16"
                          :two "bad"
                          :three "bad"
                          :four "bad"})
    => (just [(contains (ignored :two))
              (contains (bad :three))
              (contains (bad :four))]))
  (fact "Rule1 ON Rule2 NO"
    (validate-visibility {:rule1 true :rule2 "no"})
    => (just [(contains (required :one))])
    (validate-visibility {:rule1 true :rule2 "no"
                          :one "19:16"
                          :two "bad"
                          :three "bad"
                          :four "bad"})
    => (just [(contains (ignored :two))
              (contains (ignored :three))
              (contains (ignored :four))])))

(facts "data-match?"
  (data-match? {:documents [(doc-data "visibility-schema" {:rule1 true})]}
               nil nil
               {:path     "rule1"
                :values   #{true}
                :document "visibility-schema"}) => true
  (data-match? {:documents [(doc-data "visibility-schema" {})]}
               nil nil
               {:path     "rule1"
                :values   #{true}
                :document "visibility-schema"}) => false)

(facts "Cross-document visibility"
  (fact "Rule1 ON"
    (validate-visibility [(doc-data "visibility-schema" {:rule1 true})]
                         "crossref-schema"
                         {})
    => (just [(contains (required :other1))]))
  (fact "Rule1 OFF"
    (validate-visibility [(doc-data "visibility-schema" {})]
                         "crossref-schema"
                         {})
    => empty?
    (validate-visibility [(doc-data "visibility-schema" {:rule false})]
                         "crossref-schema"
                         {})
    => empty?))
