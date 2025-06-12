(ns lupapalvelu.conversion.building-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.building :as build]
            [sade.util :as util]))

(deftest vtj-part-schema-names-test
  (testing "sanity checks just in case"
    (let [names (build/vtj-prt-schema-names)]
      (is (true? (contains? names "uusiRakennus")))
      (is (true? (contains? names "purkaminen")))
      (is (true? (contains? names "uusi-rakennus-ei-huoneistoa")))
      (is (false? (contains? names "hakija-r")))
      (is (false? (contains? names "yleiset-alueet-hankkeen-kuvaus-kaivulupa")))
      (is (false? (contains? names "aiemman-luvan-toimenpide")))
      (is (false? (contains? names "archiving-project"))))))

(defn prep-docs [& docs]
  (#'build/prepare-documents {:documents docs}))

(defn make-doc [id name & [usage vtj-prt build-type build-id]]
  (util/strip-blanks
    {:id          (str "doc" id)
     :schema-info {:name name
                   :op   {:id (str "op" id)}}
     :data        {:kaytto                 {:kayttotarkoitus {:value usage}
                                            :rakennusluokka  {:value build-type}}
                   :valtakunnallinenNumero {:value vtj-prt}
                   :buildingId             {:value build-id}}}))

(deftest prepare-documents-test
  (testing "one document"
    (is (= [{:doc-id "doc2" :doc-op-id "op2" :doc-usage "hello"}]
           (prep-docs (make-doc 1 "foo")
                      (make-doc 2 "uusiRakennus" "hello")))))
  (testing "multiple documents"
    (is (= [{:doc-id "doc1" :doc-op-id "op1" :doc-usage "howdy"}
            {:doc-id "doc2" :doc-op-id "op2" :doc-usage "hello"}
            {:doc-id          "doc3" :doc-op-id "op3" :doc-usage "world"
             :doc-national-id "my-vtj-prt"}]
           (prep-docs (make-doc 1 "uusi-rakennus-ei-huoneistoa"
                                "howdy")
                      (make-doc 2 "uusiRakennus" "hello")
                      (make-doc 3 "kaupunkikuvatoimenpide" "world" "my-vtj-prt"))))
    (is (= [{:doc-id "doc1" :doc-op-id "op1" :doc-usage "hello" :doc-building-type "world"}
            {:doc-id          "doc2" :doc-op-id "op2" :doc-usage "hello"
             :doc-building-id "my-id"}
            {:doc-id          "doc3"       :doc-op-id       "op3" :doc-building-type "world"
             :doc-national-id "my-vtj-prt" :doc-building-id "my-id"}]
           (prep-docs (make-doc 1 "uusi-rakennus-ei-huoneistoa"
                                "hello" nil "world")
                      (make-doc 2 "rakennuksen-muuttaminen" "hello" nil nil "my-id")
                      (make-doc 3 "kaupunkikuvatoimenpide" nil "my-vtj-prt"
                                "world" "my-id"))))))

(defn house [ & [vtj-prt usage op-id building-type building-id]]
  (util/strip-nils
    {:nationalId    vtj-prt
     :usage         usage
     :buildingType building-type
     :buildingId    building-id
     :operationId   op-id}))

(def field-score #'build/field-score)

(deftest field-score-test
  (testing "no score"
    (is (nil? (field-score nil nil nil)))
    (is (nil? (field-score :foobar
                           {:doc-national-id "vtj-prt"}
                           (house "vtj-prt"))))
    (is (nil? (field-score :nationalid
                           {:doc-national-id "vtj-prt"}
                           (house nil "Hello"))))
    (is (nil? (field-score :operation-id
                           {:doc-building-id "bid"
                            :doc-op-id       "oops"}
                           (house "vtj-prt" nil "  "))))
    (is (nil? (field-score :building-id
                           {:doc-building-id "bid-foo"}
                             (house "vtj-prt" nil nil nil "  BID "))))
    (is (nil? (field-score :operation-id
                           {:doc-building-id "bid"
                            :doc-op-id       "oops"}
                             (house "vtj-prt" nil "oopsie" nil "  BID "))))
    (is (nil? (field-score :building-type
                           {:doc-building-type "123 Spa"
                            :doc-op-id         "oops"}
                            (house "vtj-prt" nil "oops" "   123 sAuNa  ")))))
  (testing "positive score"
    (is (= 1000 (field-score :national-id
                             {:doc-national-id "vtj-prt"}
                             (house "vtj-prt"))))
    (is (= 500 (field-score :building-id
                            {:doc-building-id "bid"}
                             (house "vtj-prt" nil nil nil "  BID "))))
    (is (= 10 (field-score :operation-id
                           {:doc-building-id "bid"
                            :doc-op-id       "oops"}
                            (house "vtj-prt" nil "oops" nil "  BID "))))
    (is (= 5 (field-score :building-type
                          {:doc-building-type "123 Sauna"
                           :doc-op-id         "oops"}
                            (house "vtj-prt" nil "oops" "   123 sAuNa  ")))))
  (testing "negative score"
    (is (= -1000 (field-score :national-id
                              {:doc-national-id "vtj-prt"}
                             (house "vtj-prt-foo"))))))

(def tally-scores #'build/tally-scores)

(deftest  tally-scores-test
  (testing "no score"
    (is (nil? (tally-scores {:doc-building-id "bid"
                             :doc-op-id       "oops"}
                            (house "vtj-prt" nil "  "))))
    (is (nil? (tally-scores {:doc-building-id   ""
                             :doc-op-id         "op1-foo"
                             :doc-usage         "hello-foo"
                             :doc-building-type "world-foo"}
                             (house "vtj-prt" "hello" " OP1 " "world" "bid")))))
  (testing "positive score"
    (is (= 1 (tally-scores {:doc-op-id         "op1"
                            :doc-usage         "hello"
                            :doc-building-type "world-foo"}
                           (house "vtj-prt" "hello" "  " "world" "bid")) ))
    (is (= 1516 (tally-scores {:doc-national-id   "vtj-prt"
                               :doc-building-id   "bid"
                               :doc-op-id         "op1"
                               :doc-usage         "hello"
                               :doc-building-type "world"}
                              (house "vtj-prt" "hello" " OP1 " "world" "bid")) ))
    (is (= 516 (tally-scores {:doc-building-id   "bid"
                              :doc-op-id         "op1"
                              :doc-usage         "hello"
                              :doc-building-type "world"}
                             (house "vtj-prt" "hello" " OP1 " "world" "bid")) ))
    (is (= 500 (tally-scores {:doc-building-id   "bid"
                              :doc-op-id         "op1-foo"
                              :doc-usage         "hello-foo"
                              :doc-building-type "world-foo"}
                             (house "vtj-prt" "hello" " OP1 " "world" "bid")) ))
    (is (= 15 (tally-scores {:doc-op-id         "op1"
                             :doc-usage         "hello-foo"
                             :doc-building-type "world"}
                            (house "vtj-prt" "hello" " OP1 " "world" "bid")) )))
  (testing "negative score"
    (is (= -1000 (tally-scores {:doc-national-id   "vtj-prt-foo"
                                :doc-building-id   "bid"
                                :doc-op-id         "op1-foo"
                                :doc-usage         "hello"
                                :doc-building-type "world-foo"}
                               (house "vtj-prt" "hello-bar" " OP1 " "world" "bid-bar")) ))
    (is (= -484 (tally-scores {:doc-national-id   "vtj-prt"
                               :doc-building-id   "bid"
                               :doc-op-id         "op1"
                               :doc-usage         "hello"
                               :doc-building-type "world"}
                              (house "vtj-prt-foo" "hello" " OP1 " "world" "bid")) ))
    ))

(def document-scores #'build/document-scores)

(deftest document-scores-test
  (testing "no scores"
    (is (nil? (document-scores {:doc-building-id "bid"
                                :doc-op-id       "oops"}
                               [(house "vtj-prt1" nil "  ")
                                (house "vtj-prt2" "hello" "  " "world" "bid-foo")]) )))
  (testing "scores"
    (let [doc {:doc-building-id "bid"
               :doc-op-id       "oops"
               :doc-usage       "ugh"}]
      (is (= [{:document doc :building (house "vtj-prt3" "hello" "oops" "world")
               :score    10}
              {:document doc :building (house "vtj-prt4" "ugh" "oops-bar" "world" "bid")
               :score    501}]
             (document-scores doc
                              [(house "vtj-prt1" nil "  ")
                               (house "vtj-prt2" "hello" "  " "world" "bid-foo")
                               (house "vtj-prt3" "hello" "oops" "world")
                               (house "vtj-prt4" "ugh" "oops-bar" "world" "bid")]))))))


(defn prep-blds [& buildings]
  (#'build/prepare-buildings {:buildings buildings}))

(deftest prepare-buildings-test
  (testing "no buildings"
    (is (nil? (prep-blds)))
    (is (nil? (prep-blds (house nil "one" "two" "three" "four")))))
  (testing "one building"
    (is (= [{:nationalId "vtj1" ::build/slots 1 ::build/i 0}]
           (prep-blds (house "vtj1" nil nil nil nil)))))
  (testing "buildings"

    (let [h1 (house "h1" "use1" "op1")
          h2 (house "h2" "use2" "op2")
          h3 (house "h3" "use3" "op3" "123 shed" "id3")
          h4 (house nil "use4" "op4")]
      (is (= {:nationalId "h1" :usage "use1" :operationId "op1"}
             h1))
      (is (= [(assoc h1 ::build/slots 1 ::build/i 0)
              (assoc h2 ::build/slots 1 ::build/i 1)
              (assoc h3 ::build/slots 1 ::build/i 2)]
             (prep-blds h1 h2 h3 h4)))

      (is (= [(assoc h3 ::build/slots 3 ::build/i 0)]
             (prep-blds h3 h3 h3))))))

(defn fbfd [buildings documents]
  (#'build/find-buildings-for-documents {:id        "app-id"
                                         :buildings buildings
                                         :documents documents}))

(deftest find-buildings-for-documents-test
  (let [h1 (house "h1" "use1" "op1")
        h2 (house "h2" "use2" "op2")
        h3 (house "h3" "use3" "op3")
        h4 (house "h4" "use4" "op4")]
    (testing "no updates"
      (is (nil? (fbfd [h1]
                      [(make-doc 11 "uusiRakennus")])))
      (is (nil? (fbfd [h1 h2]
                      [(make-doc 11 "uusiRakennus"
                                 "use1" "h1000")])))
      (is (nil? (fbfd [h1 h2 h3 h4]
                      [(make-doc 11 "uusiRakennus" "use1")
                       (make-doc 22 "purkaminen" "use1")])))
      (is (nil? (fbfd [(house "h1" "hello" "op1")
                       (house "h2" "hello" "op1")]
                      [(make-doc 1 "purkaminen" "hello")
                       (assoc (make-doc 1 "uusi-rakennus-ei-huoneistoa" "hello")
                              :id "doc20")
                       (make-doc 2 "uusiRakennus" "hello")
                       (assoc (make-doc 1 "uusiRakennus" "world")
                              :id "doc3")]))))

    (testing "updates"
      (is (= #{{:doc-id "doc1" :vtj-prt "h1"}}
             (fbfd [h1]
                   [(make-doc 1 "purkaminen" "hello")])))
      (is (= #{{:doc-id "doc1" :vtj-prt "h2"}}
             (fbfd [(assoc h2 :operationId "  ")]
                   [(make-doc 1 "purkaminen" "use2")])))
      (is (= #{;; usage
               {:doc-id "doc33" :vtj-prt "h1"}
               ;; op-id
               {:doc-id "doc4" :vtj-prt "h4"}}
             (fbfd [(dissoc h1 :operationId) h2 h3 h4]
                   [(make-doc 11 "purkaminen" "use2")
                    (make-doc 22 "purkaminen" "use1" "h2")
                    (make-doc 33 "purkaminen" "use1")
                    (make-doc 4 "uusi-rakennus-ei-huoneistoa" "use3")])))
      (is (= #{;; usage as a tiebreaker
               {:doc-id "bar" :vtj-prt "h1"}
               {:doc-id "foo" :vtj-prt "h2"}}
             (fbfd [(house "h1" "hello" "op1")
                    (house "h2" "world" "op1")
                    (house "h3" "hello" nil)
                    (house "h4" "world" nil)]
                   [(assoc (make-doc 1 "purkaminen" "howdy")
                           :id "foo")
                    (assoc (make-doc 1 "purkaminen" "hello")
                           :id "bar")])))
      (testing "ambiguity"
        (is (= #{;; The same building in two documents
                 {:doc-id "doc1" :vtj-prt "hut"}
                 {:doc-id "doc2" :vtj-prt "hut"}}
               (fbfd [(house "hut" "hello")
                      (house "hut" "hello")]
                     [(make-doc 1 "purkaminen" "hello")
                      (make-doc 2 "uusi-rakennus-ei-huoneistoa" "hello")])))
        (is (= #{;; The same building in two documents
                 {:doc-id "doc1" :vtj-prt "hut"}
                 {:doc-id "doc2" :vtj-prt "hut"}}
               (fbfd [(house "hut" "hello")
                      (house "hut" "hello")
                      (house "hut" "hello")]
                     [(make-doc 1 "purkaminen" "hello")
                      (make-doc 2 "uusi-rakennus-ei-huoneistoa" "hello")])))
        ;; One building two matches
        (is (nil? (fbfd [(house "hut" "hello")]
                        [(make-doc 1 "purkaminen" "hello")
                         (make-doc 2 "uusi-rakennus-ei-huoneistoa" "hello")])))
        ;; Two buildings three matches
        (is (nil? (fbfd [(house "hut" "hello")
                         (house "hut" "hello")]
                        [(make-doc 1 "purkaminen" "hello")
                         (make-doc 2 "uusi-rakennus-ei-huoneistoa" "hello")
                         (make-doc 3 "uusiRakennus" "hello")])))
        ;; Two buildings, two documents both match both
        (is (nil? (fbfd [(house "one" "hello")
                         (house "two" "hello")]
                        [(make-doc 1 "purkaminen" "hello")
                         (make-doc 2 "uusi-rakennus-ei-huoneistoa" "hello")]))))
      (testing "operation id and usage"
        (is (= #{{:doc-id "doc1" :vtj-prt "both"}
                 ;; Operation id
                 {:doc-id "doc11" :vtj-prt "operation"}
                 ;; Usages
                 {:doc-id "doc2" :vtj-prt "hello"}
                 {:doc-id "doc3" :vtj-prt "world"}}
               (fbfd [(house "both" "hello" "op1")
                     (house "operation" "world" "op1")
                      (house "world" "world" nil)
                      (house "hello" "hello" "")]
                     [(make-doc 1 "purkaminen" "hello")
                      (assoc (make-doc 1 "uusi-rakennus-ei-huoneistoa" "world")
                             :id "doc11")
                      (make-doc 2 "uusiRakennus" "hello")
                      (make-doc 3 "uusiRakennus" "world")]))))
      (testing "building-id and building-type"
        (is (= #{{:doc-id "doc1" :vtj-prt "both"}
                 ;; Building-types
                 {:doc-id "doc2" :vtj-prt "hello"}
                 {:doc-id "doc3" :vtj-prt "world"}
                 ;; Building-id
                 {:doc-id "doc4" :vtj-prt "hut-id"}}
               (fbfd [(house "both" "" "" "123 Both" "both-id")
                      (house "hut-id" "" "" "" "build-id")
                      (house "hello" "dum" nil " 002 two ")
                      (house "world" "dum" "" "003 Three")]
                     [(make-doc 1 "purkaminen" "" "" " 123 BOTH" " BOTH-id ")
                      (make-doc 2 "uusiRakennus" "hii" nil "002 Two")
                      (make-doc 3 "uusiRakennus" "hii" nil "003 Three")
                      (make-doc 4 "uusi-rakennus-ei-huoneistoa" "world"
                                nil "foo" "build-id")])))))))

(defn one-shot [buildings documents]
  (#'build/one-shot {:buildings buildings
                     :documents documents}))

(deftest one-shot-test
  (testing "too many documents or buildings"
    (is (nil? (one-shot [(house "yeah" "hello")
                         (house "foobar" "hello")]
                        [(make-doc 1 "rakennuksen-laajentaminen" "world")])))
    (is (nil? (one-shot [(house "yeah" "hello")]
                        [(make-doc 1 "rakennuksen-laajentaminen" "world")
                         (make-doc 2 "purkaminen" "foobar")]))))
  (testing "no suitable documents or buildings"
    (is (nil? (one-shot [(house "" "hello")]
                        [(make-doc 1 "rakennuksen-laajentaminen" "world")])))
    (is (nil? (one-shot [(house "yeah" "hello")]
                        [(make-doc 1 "rakennuksen-laajentaminen" "world" "ugh")]))))
  (testing "shot"
    (is (= #{{:doc-id "doc1" :vtj-prt "yeah"}}
           (one-shot [(house "yeah" "hello")]
                     [(make-doc 1 "rakennuksen-laajentaminen" "world")])))))
