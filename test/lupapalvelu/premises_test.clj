(ns lupapalvelu.premises-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.premises :as prem]
            [midje.sweet :refer :all]))

(fact "picking header and data rows"
  (let [sample-data [["info-line"]
                     ["Porras" "Huoneistonumero" "Huoneiston jakokirjain" "Sijaintikerros"
                      "Huoneiden lukum\u00e4\u00e4r\u00e4" "Keitti\u00f6tyyppi" "Huoneistoala"
                      "Varusteena WC" "Varusteena amme/suihku" "Varusteena parveke"
                      "Varusteena Sauna"  "Varusteena l\u00e4mmin vesi"]
                     ["A" "001" "" "2" "2" "3" "56" "1" "1" "1" "1" "1"]]]
    (prem/pick-only-header-and-data-rows sample-data)
    => {:header [:porras :huoneistonumero :jakokirjain :sijaintikerros :huoneluku :keittionTyyppi :huoneistoala
                 :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin]
        :data [["A" "001" "" "2" "2" "3" "56" "1" "1" "1" "1" "1"]]}))

(fact "works with lupapiste labels also"
  (let [sample-data [["test-rows"]
                     ["info-line"]
                     ["Huoneiston tyyppi" "Porras" "Huoneiston numero" "Jakokirjain" "Huoneluku"
                        "Keitti\u00f6n tyyppi" "Huoneistoala m2" "WC" "Amme/ suihku" "Parveke/ terassi"
                        "Sauna" "L\u00e4mmin vesi"]
                     ["1" "A" "001" "" "2" "3" "56" "1" "1" "1" "1" "1"]]]
    (prem/pick-only-header-and-data-rows sample-data)
    => {:header [:huoneistoTyyppi :porras :huoneistonumero :jakokirjain :huoneluku :keittionTyyppi :huoneistoala
                 :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin]
        :data [["1" "A" "001" "" "2" "3" "56" "1" "1" "1" "1" "1"]]}))

(fact "premise data becomes updates data"
      (let [header [:huoneistoTyyppi :porras :huoneistonumero :jakokirjain :huoneluku :keittionTyyppi :huoneistoala
                    :WCKytkin :ammeTaiSuihkuKytkin :parvekeTaiTerassiKytkin :saunaKytkin :lamminvesiKytkin]
            data [["1" "A" "001" "" "2" "3" "56" "1" "1" "1" "1" "1"]]]
        (set (prem/data->model-updates header data)))
      => #{[[:huoneistot :0 :huoneistoTyyppi] "asuinhuoneisto"]
           [[:huoneistot :0 :porras] "A"]
           [[:huoneistot :0 :huoneistonumero] "001"]
           [[:huoneistot :0 :huoneluku] "2"]
           [[:huoneistot :0 :keittionTyyppi] "keittotila"]
           [[:huoneistot :0 :huoneistoala] "56"]
           [[:huoneistot :0 :WCKytkin] true]
           [[:huoneistot :0 :ammeTaiSuihkuKytkin] true]
           [[:huoneistot :0 :parvekeTaiTerassiKytkin] true]
           [[:huoneistot :0 :saunaKytkin] true]
           [[:huoneistot :0 :lamminvesiKytkin] true]})

(fact "set-premises-source-values-for-documents"
      (fact "returns the docs as is if they don't contain premises (=huoneistot)"
            (let [docs-without-premises [{:schema-info {:name "foo"}}
                                         {:schema-info {:name "bar"}}]]
              (prem/set-premises-source-values-for-documents "irrelevant-source" docs-without-premises) => docs-without-premises))

      (fact "returns the docs with source and source value added to premise data"
            (let [docs
                  [{:schema-info {:name "foo"}}
                   {:schema-info {:name "bar"}}
                   {:schema-info {:name "rakennuksen-muuttaminen"}
                    :data {:huoneistot {:0 {:WCKytkin {:value true, :modified 1599224823007}
                                                          :huoneistonumero {:value "001" :modified 1599224812922}}}}}]
                  ]
              (prem/set-premises-source-values-for-documents "LP-KANTALUPA" docs)
              => [{:schema-info {:name "foo"}}
                  {:schema-info {:name "bar"}}
                  {:schema-info {:name "rakennuksen-muuttaminen"}
                   :data {:huoneistot {:0 {:WCKytkin        {:value true, :modified 1599224823007 :source "LP-KANTALUPA" :sourceValue true}
                                           :huoneistonumero {:value "001" :modified 1599224812922 :source "LP-KANTALUPA" :sourceValue "001"}}}}}])))

(fact "set-premises-source-values"
      (fact "returns the docs as is when they don't contain premises (=huoneistot)"
            (let [doc-without-premises {:schema-info {:name "foo"}}]
              (prem/set-premises-source-values "irrelevant-source" doc-without-premises) => doc-without-premises))

      (fact "returns the doc with source and source value added to premise data"
            (let [doc {:schema-info {:name "rakennuksen-muuttaminen"}
                       :data {:huoneistot {:0 {:WCKytkin                {:value true             :modified 1599224823007}
                                               :huoneistoTyyppi         {:value "asuinhuoneisto" :modified 1599224807582}
                                               :keittionTyyppi          {:value "keittokomero"   :modified 1599224818917}
                                               :huoneistoala            {:value 30               :modified 1599224822925}
                                               :huoneluku               {:value 1                :modified 1599224815926}
                                               :jakokirjain             {:value ""                                      }
                                               :ammeTaiSuihkuKytkin     {:value false                                   }
                                               :saunaKytkin             {:value false                                   }
                                               :huoneistonumero         {:value "001"            :modified 1599224812922}
                                               :pysyvaHuoneistotunnus   {:value ""                                      }
                                               :status                  {:value ""                                      }
                                               :porras                  {:value "A"              :modified 1599224810404}
                                               :muutostapa              {:value "lisäys"         :modified 1599224805271}
                                               :lamminvesiKytkin        {:value false                                   }
                                               :parvekeTaiTerassiKytkin {:value false                                   }}

                                           :1 {:WCKytkin               {:value false             :modified 1599224823007}
                                               :huoneistoTyyppi         {:value "asuinhuoneisto" :modified 1599224807582}
                                               :keittionTyyppi          {:value "keittokomero"   :modified 1599224818917}
                                               :huoneistoala            {:value 35               :modified 1599224822925}
                                               :huoneluku               {:value 2                :modified 1599224815926}
                                               :jakokirjain             {:value ""                                      }
                                               :ammeTaiSuihkuKytkin     {:value false                                   }
                                               :saunaKytkin             {:value false                                   }
                                               :huoneistonumero         {:value "002"            :modified 1599224812922}
                                               :pysyvaHuoneistotunnus   {:value "FOO"                                   }
                                               :status                  {:value ""                                      }
                                               :porras                  {:value "A"              :modified 1599224810404}
                                               :muutostapa              {:value "lisäys"         :modified 1599224805271}
                                               :lamminvesiKytkin        {:value false                                   }
                                               :parvekeTaiTerassiKytkin {:value false                                   }}}}}]
              (prem/set-premises-source-values "LP-KANTALUPA" doc)
              => {:schema-info {:name "rakennuksen-muuttaminen"}
                  :data {:huoneistot {:0 {:WCKytkin                {:value true             :modified 1599224823007 :source "LP-KANTALUPA" :sourceValue true}
                                          :huoneistoTyyppi         {:value "asuinhuoneisto" :modified 1599224807582 :source "LP-KANTALUPA" :sourceValue "asuinhuoneisto"}
                                          :keittionTyyppi          {:value "keittokomero"   :modified 1599224818917 :source "LP-KANTALUPA" :sourceValue "keittokomero"}
                                          :huoneistoala            {:value 30               :modified 1599224822925 :source "LP-KANTALUPA" :sourceValue 30}
                                          :huoneluku               {:value 1                :modified 1599224815926 :source "LP-KANTALUPA" :sourceValue 1}
                                          :jakokirjain             {:value ""                                       :source "LP-KANTALUPA" :sourceValue ""}
                                          :ammeTaiSuihkuKytkin     {:value false                                    :source "LP-KANTALUPA" :sourceValue false}
                                          :saunaKytkin             {:value false                                    :source "LP-KANTALUPA" :sourceValue false}
                                          :huoneistonumero         {:value "001"            :modified 1599224812922 :source "LP-KANTALUPA" :sourceValue "001"}
                                          :pysyvaHuoneistotunnus   {:value ""                                       :source "LP-KANTALUPA" :sourceValue ""}
                                          :status                  {:value ""                                       :source "LP-KANTALUPA" :sourceValue ""}
                                          :porras                  {:value "A"              :modified 1599224810404 :source "LP-KANTALUPA" :sourceValue "A"}
                                          :muutostapa              {:value "lisäys"         :modified 1599224805271 :source "LP-KANTALUPA" :sourceValue "lisäys"}
                                          :lamminvesiKytkin        {:value false                                    :source "LP-KANTALUPA" :sourceValue false}
                                          :parvekeTaiTerassiKytkin {:value false                                    :source "LP-KANTALUPA" :sourceValue false}}

                                      :1 {:WCKytkin                {:value false            :modified 1599224823007 :source "LP-KANTALUPA" :sourceValue false}
                                          :huoneistoTyyppi         {:value "asuinhuoneisto" :modified 1599224807582 :source "LP-KANTALUPA" :sourceValue "asuinhuoneisto"}
                                          :keittionTyyppi          {:value "keittokomero"   :modified 1599224818917 :source "LP-KANTALUPA" :sourceValue "keittokomero"}
                                          :huoneistoala            {:value 35               :modified 1599224822925 :source "LP-KANTALUPA" :sourceValue 35}
                                          :huoneluku               {:value 2                :modified 1599224815926 :source "LP-KANTALUPA" :sourceValue 2}
                                          :jakokirjain             {:value ""                                       :source "LP-KANTALUPA" :sourceValue ""}
                                          :ammeTaiSuihkuKytkin     {:value false                                    :source "LP-KANTALUPA" :sourceValue false}
                                          :saunaKytkin             {:value false                                    :source "LP-KANTALUPA" :sourceValue false}
                                          :huoneistonumero         {:value "002"            :modified 1599224812922 :source "LP-KANTALUPA" :sourceValue "002"}
                                          :pysyvaHuoneistotunnus   {:value "FOO"                                    :source "LP-KANTALUPA" :sourceValue "FOO"}
                                          :status                  {:value ""                                       :source "LP-KANTALUPA" :sourceValue ""}
                                          :porras                  {:value "A"              :modified 1599224810404 :source "LP-KANTALUPA" :sourceValue "A"}
                                          :muutostapa              {:value "lisäys"         :modified 1599224805271 :source "LP-KANTALUPA" :sourceValue "lisäys"}
                                          :lamminvesiKytkin        {:value false                                    :source "LP-KANTALUPA" :sourceValue false}
                                          :parvekeTaiTerassiKytkin {:value false                                    :source "LP-KANTALUPA" :sourceValue false}}}}})))
