(ns lupapalvelu.document.model-test
  (:use [lupapalvelu.document.model]
        [lupapalvelu.document.schemas]
        [lupapalvelu.document.validators]
        [midje.sweet]))

;; Simple test schema:

(def schema {:info {:name "test-model"
                    :version 1}
             :body [{:name "a" :type :group
                     :body [{:name "aa" :type :string}
                            {:name "ab" :type :string :min-len 2 :max-len 3}
                            {:name "ac" :type :string :min-len 2 :max-len 3}
                            {:name "b" :type :group
                             :body [{:name "ba" :type :string :min-len 2}
                                    {:name "bb" :type :boolean}]}
                            {:name "c" :type :list
                             :body [{:name "ca" :type :string}
                                    {:name "cb" :type :checkbox}]}
                            {:name "d" :type :select
                             :body [{:name "A"}
                                    {:name "B"}
                                    {:name "C"}]}]}]})

(def schema-with-repetition {:info {:name "repetition-model" :version 1}
                             :body [{:name "single" :type :string}
                                    {:name "repeats" :type :group :repeating true
                                     :body [{:name "single2" :type :string}
                                            {:name "repeats2" :type :string :subtype :digit :repeating true}]}]})

;; Tests for internals:

(def find-by-name #'lupapalvelu.document.model/find-by-name)

(facts "Facts about internals"
  (fact (find-by-name (:body schema) ["a"])          => (-> schema :body first))
  (fact (find-by-name (:body schema) ["a" "aa"])     => {:name "aa" :type :string})
  (fact (find-by-name (:body schema) ["a" "b" "bb"]) => {:name "bb" :type :boolean})
  (fact (find-by-name (:body schema) ["a" "b" "bc"]) => nil))

(facts "has-errors?"
  (has-errors? [])                  => false
  (has-errors? [{:result [:warn]}]) => false
  (has-errors? [{:result [:warn]}
                {:result [:err]}])  => true)

; field type validation

(facts "dates"
  (validate-field {:type :date} "abba") => [:warn "illegal-value:date"]
  (validate-field {:type :date} "") => nil
  (validate-field {:type :date} "11.12.2013") => nil)

;;
;; validate
;;

(facts "validate"
  {:schema {:info {:name "schema"}
            :body [{:name "a" :type :group
                    :body [{:name "aa" :type :string}
                           {:name "ab" :type :string :min-len 2 :max-len 3}]}]}
   :data {:a {:aa {:value "kukka"}
              :ab {:value "123"}}}} => valid?

  {:schema {:info {:name "schema"}
            :body [{:name "a" :type :group
                    :body [{:name "aa" :type :string}
                           {:name "ab" :type :string :min-len 2 :max-len 3}]}]}
   :data {:c {:aa {:value "kukka"}
              :ab {:value "123"}}}} => invalid?)

;; Validation tests:

(facts "Simple validations"
  (let [document (new-document schema ..now..)]
    (-> document
      (apply-update [:a :ab] "foo"))   => valid?
    (-> document
      (apply-update [:a :ab] "f"))     => (invalid-with? [:warn "illegal-value:too-short"])
    (-> document
      (apply-update [:a :ab] "foooo")) => (invalid-with? [:err "illegal-value:too-long"])
    (-> document
      (apply-update [:a :ab] "\u00d6\u00e9\u00c8")) => valid?
    (-> document
      (apply-update [:a :ab] "\u047e\u0471")) => (invalid-with? [:warn "illegal-value:not-latin1-string"])))

(facts "Select"
  (let [document (new-document schema ..now..)]
    (-> document
      (apply-update [:a :d] "A")) => valid?
    (-> document
      (apply-update [:a :d] "")) => valid?
    (-> document
      (apply-update [:a :d] "D")) => (invalid-with? [:warn "illegal-value:select"])))

(facts "with real schemas - important field for paasuunnittelija"
  (let [document (new-document (schemas "paasuunnittelija") ..now..)]
    (-> document
      (apply-update [:henkilotiedot :etunimi] "Tauno")
      (apply-update [:henkilotiedot :sukunimi] "Palo")
      (apply-update [:osoite :postinumero] "12345")
      (apply-update [:yhteystiedot :email] "tauno@example.com")
      (apply-update [:yhteystiedot :puhelin] "050")) => valid?
    (-> document
      (apply-update [:henkilotiedot :etunimiz] "Tauno")) => (invalid-with? [:err "illegal-key"])
    (-> document
      (apply-update [:henkilotiedot :sukunimiz] "Palo")) => (invalid-with? [:err "illegal-key"])))

(facts "Repeating section"
  (let [document (new-document schema-with-repetition ..now..)]

    (fact "Single value contains no nested sections"
      (-> document
        (apply-update [:single :1 :single2] "foo")) => (invalid-with? [:err "illegal-key"]))

    (fact "Repeating section happy case"
      (-> document
        (apply-update [:repeats :1 :single2] "foo")) => valid?)

    (fact "Invalid key under nested section"
      (-> document
        (apply-update [:repeats :1 :single3] "foo")) => (invalid-with? [:err "illegal-key"]))

    (fact "Unindexed repeating section"
      (-> document
        (apply-update [:repeats :single2] "foo")) => (invalid-with? [:err "illegal-key"]))

    (fact "Repeating string, 0"
      (-> document
        (apply-update [:repeats :1 :repeats2 :0] "1")) => valid?)

    (fact "Repeating string, 1"
      (-> document
        (apply-update [:repeats :1 :repeats2 :1] "foo")) => (invalid-with? [:warn "illegal-number"]))))

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

(facts "Required fields"
  (let [document (new-document schema-with-required ..now..)]

    document => (invalid-with? [:warn "illegal-value:required"])

    (-> document
      (apply-update [:a :b :aa] " ")
      (apply-update [:a :b :ab] " ")) => (invalid-with? [:warn "illegal-value:required"])

    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")) => valid?

    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")
      (apply-update [:a :c :0 :raa] "value")) => (invalid-with? [:warn "illegal-value:required"])
    
    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")
      (apply-update [:a :c :0 :rab] "value")
      (apply-update [:a :c :6 :rab] "value")) => valid?
    
    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")
      (apply-update [:a :c :0 :rab] "value")
      (apply-update [:a :d :0 :d2 :0 :od1] "value")) => (invalid-with? [:warn "illegal-value:required"])
    
    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")
      (apply-update [:a :c :0 :rab] "value")
      (apply-update [:a :d :0 :d2 :0 :od1] "value")
      (apply-update [:a :d :0 :d2 :0 :od2] "value")) => (invalid-with? [:warn "illegal-value:required"])
    
    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")
      (apply-update [:a :c :0 :rab] "value")
      (apply-update [:a :d :0 :d2 :0 :od1] "value")
      (apply-update [:a :d :0 :d2 :0 :rd] "value")
      (apply-update [:a :d :0 :d2 :0 :od2] "value")) => valid?
    
    (-> document
      (apply-update [:a :b :aa] "value")
      (apply-update [:a :b :ab] "value")
      (apply-update [:a :c :0 :rab] "value")
      (apply-update [:a :d :0 :d2 :0 :od1] "value")
      (apply-update [:a :d :0 :d2 :0 :rd] "value")
      (apply-update [:a :d :1 :d2 :6 :rd] "value")) => valid?))

    ;;
;; Updates
;;

(fact "updating document"

  (fact "single value"
    (apply-update  {} [:b :c] "kikka") => {:data {:b {:c {:value "kikka"}}}})

  (fact "unsetting value"
    (-> {}
      (apply-update [:b :c] "kikka")
      (apply-update [:b :c])) => {:data {:b {:c {:value ""}}}})

  (fact "updates"
    (apply-updates {} [[[:b :c] "kikka"]
                       [[:b :d] "kukka"]]) => {:data {:b {:c {:value "kikka"}
                                                          :d {:value "kukka"}}}})
  (fact "update a map value"
    (apply-update {} [:a :b] {:c 1 :d {:e 2}}) => {:data {:a {:b {:c {:value 1}
                                                                  :d {:e {:value 2}}}}}}))

(fact "map2updates"
  (map2updates [:a :b] {:c 1 :d {:e 2}}) => (just [[[:a :b :c] 1]
                                                   [[:a :b :d :e] 2]] :in-any-order))
