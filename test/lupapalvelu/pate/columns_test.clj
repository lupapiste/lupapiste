(ns lupapalvelu.pate.columns-test
  (:require [lupapalvelu.pate.columns :as cols]
            [lupapalvelu.pate.pdf-layouts :as layouts]
            [lupapalvelu.pate.verdict-schemas :as verdict-schemas]
            [midje.sweet :refer :all]))

(fact "join-non-blanks"
  (cols/join-non-blanks "-" "foo" " " "bar" nil "baz")
  => "foo-bar-baz"
  (cols/join-non-blanks "-")
  => ""
  (cols/join-non-blanks "-" "  " nil "")
  => "")

(fact "loc-non-blank"
  (cols/loc-non-blank :fi :hii.hoo " ")
  => nil
  (cols/loc-non-blank :fi :pdf "page")
  => "Sivu")

(fact "loc-fill-non-blank"
  (cols/loc-fill-non-blank :fi :pdf.not-later-than " ")
  => nil
  (cols/loc-fill-non-blank :fi :pdf.not-later-than nil)
  => nil
  (cols/loc-fill-non-blank :fi :pdf.not-later-than "5.2.2018")
  => "viimeist\u00e4\u00e4n 5.2.2018"
  (cols/loc-fill-non-blank :fi :pdf.voimassa.text "1.1.2000" " ")
  => nil
  (fact "Only values matter, not the placeholders"
    (cols/loc-fill-non-blank :fi :pdf.voimassa.text "1.1.2000")
    =not=> nil)
  (cols/loc-fill-non-blank :fi :pdf.voimassa.text nil "1.1.2000")
  => nil
  (cols/loc-fill-non-blank :fi :pdf.voimassa.text "30.10.1999" "1.1.2000")
  =not=> nil)

(facts "pathify"
  (cols/pathify nil) => nil
  (cols/pathify []) => nil
  (cols/pathify :hello.world.foo.bar) => [:hello :world :foo :bar]
  (cols/pathify :hello) => [:hello]
  (cols/pathify "hello") => [:hello]
  (cols/pathify "hello.world") => [:hello :world]
  (cols/pathify [:hello :world]) => [:hello :world]
  (cols/pathify [:hello :world :foo.bar]) => [:hello :world :foo :bar]
  (cols/pathify [:hello :world nil :foo.bar nil]) => [:hello :world :foo :bar]
  (cols/pathify [:hello "world" "foo.bar"]) => [:hello :world :foo :bar])

(facts "doc-value"
  (let [app {:documents [{:schema-info {:name "foo"}
                          :data {:bar {:baz "hello"}}}]}]
    (cols/doc-value app :foo :hii.hoo) => nil
    (cols/doc-value app :foo :bar) => {:baz "hello"}
    (cols/doc-value app :foo :bar.baz) => "hello"
    (cols/doc-value app :bad :bar.baz) => nil
    (cols/doc-value app :foo :bar.baz.bam) => nil))

(facts "dict-value"
  (against-background (verdict-schemas/verdict-schema :r nil)
                      => {:dictionary {:ts   {:date {}}
                                       :txt  {:phrase-text {:category :yleinen}}
                                       :foo  {:text {}}
                                       :list {:repeating {:day  {:date {}}
                                                          :word {:phrase-text {:category :yleinen}}
                                                          :bar  {:toggle {}}}}}})
  (let [verdict {:category :r
                 :data     {:ts   1524648212778
                            :txt  "_markup text_"
                            :foo  "*regular text*"
                            :list {:id1 {:day  1524733200000
                                         :word "### Title"
                                         :bar  true}}}}]
    (cols/dict-value verdict :ts)
    => "25.4.2018"
    (cols/dict-value verdict :txt)
    => '([:div.markup ([:span {} [:em {} "markup text"] [:br {}]])])
    (cols/dict-value verdict :foo)
    => "*regular text*"
    (cols/dict-value {:verdict verdict} :list.id1.day)
    => "26.4.2018"
    (cols/dict-value verdict :list.id1.word)
    => '([:div.markup ([:h3 {} "Title"])])
    (cols/dict-value {:verdict verdict} :list.id1.bar)
    => true))

(fact "add-unit"
  (layouts/add-unit :fi :ha " ") => nil
  (layouts/add-unit :fi :ha nil) => nil
  (layouts/add-unit :fi :ha "20") => "20 ha"
  (layouts/add-unit :fi :ha 10) => "10 ha"
  (layouts/add-unit :fi :m2 88) => [:span {} 88 " m"[:sup 2]]
  (layouts/add-unit :fi :m3 "hello") => [:span {} "hello" " m"[:sup 3]]
  (layouts/add-unit :fi :kpl 8) => "8 kpl"
  (layouts/add-unit :fi :section 88) => "\u00a788"
  (layouts/add-unit :fi :eur "foo") => "foo\u20ac")

(fact "collateral"
  (cols/collateral {:lang    :fi
                   :verdict {:category :r
                             :data     {:collateral-flag true
                                        :collateral      "20 000"
                                        :collateral-type "Shekki"
                                        :collateral-date "5.2.2018"}}})
  => "20 000\u20ac, Shekki, 5.2.2018"
  (cols/collateral {:lang    :fi
                   :verdict {:category :r
                             :data     {:collateral-flag true
                                        :collateral      "20 000"}}})
  => "20 000\u20ac"
  (cols/collateral {:lang    :fi
                   :verdict {:category :r
                             :data     {:collateral-flag true}}})
  => ""
  (cols/collateral {:lang    :fi
                   :verdict {:category :r
                             :data     {:collateral-flag false
                                        :collateral      "20 000"
                                        :collateral-type "shekki"
                                        :collateral-date "5.2.2018"}}})
  => nil
  (cols/collateral {:lang    :fi
                   :verdict {:category :r
                             :data     {}}}) => nil)

(def runeberg 1580860800000) ; 5.2.2020
(def flagday  1591254570278) ; 4.6.2020

(defn make-period-verdict [start end]
  {:lang    :fi
   :verdict {:category :r
             :data     {:aloitettava start
                        :voimassa    end}}})

(fact "voimassaolo"
  (cols/voimassaolo (make-period-verdict runeberg flagday))
  => "Rakennustyöt on aloitettava viimeistään 5.2.2020 ja saatettava loppuun 4.6.2020 mennessä. Lupa raukeaa, mikäli voimassaoloaikaa ei erityisestä syystä pidennetä."
  (cols/voimassaolo (make-period-verdict flagday runeberg))
  => "Rakennustyöt on aloitettava viimeistään 4.6.2020 ja saatettava loppuun 5.2.2020 mennessä. Lupa raukeaa, mikäli voimassaoloaikaa ei erityisestä syystä pidennetä."
  (cols/voimassaolo (make-period-verdict nil flagday))
  => "Rakennustyöt on saatettava loppuun 4.6.2020 mennessä. Lupa raukeaa, mikäli voimassaoloaikaa ei erityisestä syystä pidennetä."
  (cols/voimassaolo (make-period-verdict flagday nil)) => nil
  (cols/voimassaolo (make-period-verdict nil nil)) => nil)

(fact "complexity"
  (cols/complexity {:lang :fi :verdict {:category :r
                                       :data     {}}})
  => nil
  (cols/complexity {:lang :fi :verdict {:category :r
                                       :data     {:complexity "large"}}})
  => ["Vaativa"]
  (cols/complexity {:lang    :fi
                   :verdict {:category :r
                             :data     {:complexity-text "Tai nan le."}}})
  => '(([:div.markup ([:span {} "Tai nan le." [:br {}]])]))
  (cols/complexity {:lang    :fi
                   :verdict {:category :r
                             :data     {:complexity-text "Tai nan le."
                                        :complexity      "large"}}})
  => ["Vaativa" '([:div.markup ([:span {}"Tai nan le." [:br {}]])])])

(fact "statements"
  (cols/statements {:lang    :fi
                   :verdict {:category :r
                             :data     {}}})
  => nil
  (cols/statements {:lang    :fi
                   :verdict {:category :r
                             :data     {:statements [{:given  1518017023967
                                                      :text   "Stakeholder"
                                                      :status "ei-huomautettavaa"}
                                                     {:given  1517930824494
                                                      :text   "Interested"
                                                      :status "ehdollinen"}]}}})
  => ["Stakeholder, 7.2.2018, Ei huomautettavaa"
      "Interested, 6.2.2018, Ehdollinen"])

(fact "handler"
  (cols/handler {:verdict {:category :r
                          :data     {}}}) => nil
  (cols/handler {:verdict {:category :r
                          :data     {:handler "Hank Handler  "}}})
  => "Hank Handler"
  (cols/handler {:verdict {:category :r
                          :data     {:handler       " Hank Handler"
                                     :handler-title "Title"}}})
  => "Title Hank Handler"
  (cols/handler {:verdict {:category :r
                          :data     {:handler-title " Title  "}}})
  => nil)

(fact "handler ya"
  (let [opts {:lang    :en
              :verdict {:category   :ya
                        :data       {:language                "en"
                                     :handler                 " Hank Handler"
                                     :handler-title           "id"
                                     :handler-titles-included true}
                        :references {:handler-titles [{:id "id"
                                                       :fi "Nimike"
                                                       :en "Title"}]}}}]
    (cols/handler opts) => "Title Hank Handler"
    (cols/handler (assoc opts :lang :fi)) => "Nimike Hank Handler"
    (cols/handler (assoc-in opts [:verdict :data :handler-titles-included] false))
    => "Hank Handler"
    (cols/handler (assoc-in opts [:verdict :data :handler-title] "bad"))
    => "Hank Handler"))

(fact "giver"
  (cols/giver {:verdict {:category :r}}) => nil
  (cols/giver {:verdict {:category :r
                         :data     {:giver-title "Title"}}})
  => nil
  (cols/giver {:verdict {:category :r
                         :data     {:giver       "   "
                                    :giver-title "Title"}}})
  => nil
  (cols/giver {:verdict {:category :r
                         :data     {:giver       " "
                                    :giver-title " "}}})
  => nil
  (cols/giver {:verdict {:category :r
                         :data     {:giver       nil
                                    :giver-title nil}}})
  => nil

  (cols/giver {:verdict {:category :r
                         :data     {:giver "Verdict Giver"}}})
  => "Verdict Giver"
  (cols/giver {:verdict {:category :r
                         :data     {:giver-title "  "
                                    :giver       "Verdict Giver"}}})
  => "Verdict Giver"

  (cols/giver {:verdict {:category :r
                         :data     {:giver       "  Verdict Giver  "
                                    :giver-title "  Title  "}}})
  => "Title Verdict Giver")

(facts "references"
  (let [refs {:reviews [{:id "id1" :fi "Yksi" :sv "Ett" :en "One"}
                        {:id "id2" :fi "Kaksi" :sv "Tv\u00e5" :en "Two"}
                        {:id "id3" :fi "Kolme" :sv "Tre" :en "Three"}
                        {:id "id4" :fi "Nelj\u00e4" :sv "Fyra" :en "Four"}]}]
    (fact "not included"
      (cols/references {:lang    :fi
                       :verdict {:category   :r
                                 :data       {:reviews-included false
                                              :reviews          ["id1" "id3"]}
                                 :references refs}} :reviews)
      => nil)
    (fact "Included"
      (cols/references {:lang    :fi
                       :verdict {:category   :r
                                 :data       {:reviews-included true
                                              :reviews          ["id1" "bad" "id3"]}
                                 :references refs}} :reviews)
      => ["Yksi" "Kolme"])
    (fact "Included, but empty"
      (cols/references {:lang    :fi
                       :verdict {:category   :r
                                 :data       {:reviews-included true
                                              :reviews          []}
                                 :references refs}} :reviews)
      => nil)
    (fact "Included, but no data"
      (cols/references {:lang    :fi
                       :verdict {:category   :r
                                 :data       {:reviews-included true}
                                 :references refs}} :reviews)
      => nil)))

(facts "conditions"
  (fact "No conditions"
    (cols/conditions {:verdict {:category :r
                               :data     {}}})
    => nil)
  (fact "Only empty conditions"
    (cols/conditions {:verdict {:category :r
                               :data     {:conditions {:bbb {:condition ""}
                                                       :ccc {}
                                                       :aaa {:condition "    "}}}}})
    => nil)
  (fact "Some conditions"
    (cols/conditions {:verdict {:category :r
                               :data     {:conditions {:bbb {:condition "Should be last."}
                                                       :ccc {:condition "  "}
                                                       :aaa {:condition "Probably first."}}}}})
    => [[:div.markup '(([:span {} "Probably first." [:br {}]])
                       ([:span {} "Should be last." [:br {}]]))] nil]))

(facts "resolve-cell"
  (cols/resolve-cell {:lang "fi"} "hello" nil)
  => [:div.cell {} "hello"]
  (cols/resolve-cell {:lang "fi"} [:span [:strong.foo "hello"]] nil)
  => [:div.cell {} [:span [:strong.foo "hello"]]]
  (cols/resolve-cell {:lang "fi"} "hello" {:text "undo" :loc-prefix :phrase
                                           :width 50 :styles :not-supported})
  => [:div.cell {:class '("cell--50")} "Kumoa fraasi"]
  (cols/resolve-cell {:lang "fi"} 123 {:width 80 :styles [:bold :right]
                                       :unit :m2})
  => [:div.cell {:class '("cell--80" "bold" "right")} [:span {} 123 " m" [:sup 2]]]
  (cols/resolve-cell {:lang "fi"} "hello" {:text "world"})
  => [:div.cell {} "world"]
  (cols/resolve-cell {:lang "fi"} "hello" {:text "add" :loc-prefix "phrase"})
  => [:div.cell {} "Lisää fraasi"])

(facts "foremen"
  (let [refs {:foremen ["this" "is" "correct" "order"]}]
    (fact "Not included"
      (cols/foremen {:verdict {:category   :r
                               :data       {:foremen-included false
                                            :foremen          ["order" "is" "correct"]}
                               :references refs}})
      => nil)
    (fact "Included"
      (cols/foremen {:verdict {:category   :r
                               :data       {:foremen-included true
                                            :foremen          ["order" "is" "correct"]}
                               :references refs}})
      => ["is" "correct" "order"])
    (fact "Included, but empty"
      (cols/foremen {:verdict {:category   :r
                               :data       {:foremen-included true
                                            :foremen          []}
                               :references refs}})
      => nil)
    (fact "Included, but no data"
      (cols/foremen {:verdict {:category   :r
                               :data       {:foremen-included true}
                               :references refs}})
      => nil)))
