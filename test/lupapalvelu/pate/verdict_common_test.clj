(ns lupapalvelu.pate.verdict-common-test
  (:require [lupapalvelu.pate-test-util :refer :all]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict-common :as vc]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(testable-privates lupapalvelu.pate.verdict-common
                   verdict-section-string
                   title-fn verdict-string)

(defn publish [verdict ts]
  (-> verdict
      (assoc :state (metadata/wrap "publisher" ts "published"))
      (assoc-in [:published :published] ts)))

(facts "verdict-section"
  (vc/verdict-section {}) => nil
  (vc/verdict-section {:category "r" :data {:verdict-section " 22 "}})
  => "22"
  (vc/verdict-section {:category "r" :data {:verdict-section "  "}})
  => nil
  (vc/verdict-section {:category "r" :data {:verdict-section nil}})
  => nil
  (vc/verdict-section {:category "r"
                       :legacy?  true
                       :data     {:verdict-section " 22 "}})
  => "22"
  (vc/verdict-section {:category "r" :data {:verdict-section 12}})
  => "12"
  (vc/verdict-section {:paatokset [{:poytakirjat [{:pykala 91}]}]})
  => "91"
  (vc/verdict-section {:paatokset [{:poytakirjat [{:pykala " 91 "}]}]})
  => "91"
  (vc/verdict-section {:paatokset [{:poytakirjat [{:pykala "  "}]}]})
  => nil
  (vc/verdict-section {:paatokset [{:poytakirjat []}]})
  => nil)

(fact "verdict-section-string"
  (verdict-section-string {:category "r" :data {:verdict-section " 22 "}}) => "\u00a722"
  (verdict-section-string {:category "r" :data {:verdict-section ""}}) => ""
  (verdict-section-string {:category "r" :data {:verdict-section "\u00a722"}}) => "\u00a722")

(fact "title-fn"
  (let [fun (partial format "(%s)")]
    (title-fn " hello " fun) => "(hello)"
    (title-fn " " fun) => ""
    (title-fn nil fun) => ""
    (title-fn 1 fun) => "(1)"))

(facts "verdict-string"
  (fact "legacy verdict-code"
    (verdict-string "fi"
                    {:legacy? true
                     :category "r"
                     :data {:verdict-code "8"}
                     :template {:inclusions ["verdict-code"]}}
                    :verdict-code)
    => "Työhön liittyy ehto")
  (fact "modern verdict-code"
    (verdict-string "en"
                    {:category "r"
                     :data {:verdict-code "hallintopakko"}
                     :template {:inclusions ["verdict-code"]}}
                    :verdict-code)
    => "Administrative enforcement/penalty proceedings discontinued.")
  (fact "verdict-type"
    (verdict-string "fi"
                    {:category "ya"
                     :data {:verdict-type "katulupa"}
                     :template {:inclusions ["verdict-type"]}}
                    :verdict-type)
    => "Katulupa"))

(facts "Verdict summary"
  (let [section-strings    {"v1" "\u00a71" "v2" "\u00a72"}
        verdict            {:id         "v1"
                            :category   "r"
                            :data       {:verdict-code    "ehdollinen"
                                         :handler         "Foo Bar"
                                         :verdict-section "1"
                                         :verdict-date    876543}
                            :modified   12345
                            :template   {:inclusions ["verdict-code"
                                                      "handler"
                                                      "verdict-date"
                                                      "verdict-section"]
                                         :giver      "viranhaltija"}
                            :references {:boardname "Broad board abroad"}}
        backend-verdict    {:kuntalupatunnus "13-0185-R"
                            :paatokset       [{:paivamaarat {:aloitettava      1377993600000
                                                             :lainvoimainen    1378080000000
                                                             :voimassaHetki    1378166400000
                                                             :raukeamis        1378252800000
                                                             :anto             1378339200000
                                                             :viimeinenValitus 1536192000000
                                                             :julkipano        1378512000000}
                                               :poytakirjat [{:paatoskoodi     "myönnetty"
                                                              :paatospvm       112233
                                                              :pykala          "1"
                                                              :paatoksentekija "viranomainen"
                                                              :paatos          "Päätös 1"
                                                              :status          "1"
                                                              :urlHash         "236d9b2cfff88098d4f8ad532820c9fb93393237"}
                                                             {:paatoskoodi     "ehdollinen"
                                                              :paatospvm       998877
                                                              :pykala          "2"
                                                              :paatoksentekija "Mölli Keinonen"
                                                              :status          "6"
                                                              :urlHash         "b55ae9c30533428bd9965a84106fb163611c1a7d"}]
                                               :id          "5b99044bfb2de0f550b64e44"}
                                              {:paivamaarat {:anto 1378339200000}
                                               :poytakirjat [{:paatoskoodi     "myönnetty"
                                                              :paatospvm       445566
                                                              :paatoksentekija "johtava viranomainen"
                                                              :paatos          "Päätös 2"
                                                              :status          "1"}
                                                             {:paatospvm nil}
                                                             {}
                                                             nil]
                                               :id          "5b99044cfb2de0f550b64e4f"}]
                            :id              "backend-id"
                            :draft           false
                            :timestamp       12345}
        ya-backend-verdict (-> backend-verdict
                               (assoc :kuntalupatunnus "14-0185-YA")
                               (update :paatokset (comp vec butlast))
                               (update-in [:paatokset 0 :poytakirjat] (fn [pks]
                                                                        (map #(dissoc % :paatospvm :urlHash) pks)))
                               (assoc-in [:paatokset 0 :paivamaarat :paatosdokumentinPvm] 220033))
        legacy-contract    {:id       "legacy-contract"
                            :category "contract"
                            :legacy?  true
                            :data     {:handler      "Foo Bar"
                                       :verdict-date 54321}
                            :modified 12345}]

    (fact "Nil"
      (vc/draft? nil) => false
      (vc/published? nil) => false
      (vc/draft? {}) => false
      (vc/published? {}) => true
      (vc/verdict-summary "fi" {} nil nil)
      => {:category  "backing-system"
          :legacy?   false
          :proposal? false
          :replaced? false
          :title     "Luonnos"})
    (fact "Draft"
      (vc/draft? verdict) => true
      (vc/published? verdict) => false
      (vc/verdict-summary "fi" {} section-strings verdict)
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :giver         "Foo Bar"
          :verdict-date  876543
          :title         "Luonnos"
          :verdict-state :published})
    (fact "Board draft"
      (vc/verdict-summary "fi" {} section-strings
                          (assoc-in verdict [:template :giver] "lautakunta"))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :giver         "Broad board abroad"
          :verdict-date  876543
          :title         "Luonnos"
          :verdict-state :published})
    (fact "Verdict proposal"
      (vc/verdict-summary "fi" {} section-strings
                          (-> verdict
                              (assoc-in [:template :giver] "lautakunta")
                              (assoc :state "proposal")))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     true
          :replaced?     false
          :giver         "Broad board abroad"
          :verdict-date  876543
          :title         "Päätösehdotus"
          :verdict-state :proposal})
    (fact "Replacement draft"
      (vc/verdict-summary "fi" {"v1" "v2"} section-strings
                          (assoc-in verdict [:replacement :replaces] "v2"))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :giver         "Foo Bar"
          :verdict-date  876543
          :title         "Luonnos (korvaa päätöksen \u00a72)"
          :verdict-state :published})
    (fact "Published"
      (let [verdict (publish verdict 121212)]
        (vc/draft? verdict) => false
        (vc/published? verdict) => true
        (vc/verdict-summary "fi" {} section-strings verdict))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     121212
          :giver         "Foo Bar"
          :verdict-date  876543
          :title         "\u00a71 Ehdollinen"
          :verdict-state :published})
    (fact "Published, no section"
      (let [verdict (-> verdict
                        (publish 121212)
                        (assoc-in [:data :verdict-section] nil))]
        (vc/draft? verdict) => false
        (vc/published? verdict) => true
        (vc/verdict-summary "fi" {} {} verdict))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     121212
          :giver         "Foo Bar"
          :verdict-date  876543
          :title         "Ehdollinen"
          :verdict-state :published})
    (fact "Published replacement"
      (vc/verdict-summary "fi" {"v1" "v2"} section-strings
                          (-> verdict
                              (publish 121212)
                              (assoc-in [:replacement :replaces] "v2")))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     121212
          :giver         "Foo Bar"
          :verdict-date  876543
          :title         "\u00a71 Ehdollinen (korvaa päätöksen \u00a72)"
          :verdict-state :published})
    (fact "Published replacement, no section"
      (vc/verdict-summary "fi" {"v1" "v2"} (dissoc section-strings "v2")
                          (-> verdict
                              (publish 121212)
                              (assoc-in [:replacement :replaces] "v2")))
      => {:id            "v1"
          :category      "r"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     121212
          :giver         "Foo Bar"
          :verdict-date  876543
          :title         "\u00a71 Ehdollinen (korvaava päätös)"
          :verdict-state :published})
    (fact "Legacy draft"
      (let [verdict (-> verdict
                        (assoc :legacy? true)
                        (assoc-in [:data :anto] 98765))]
        (vc/draft? verdict) => true
        (vc/published? verdict) => false
        (vc/verdict-summary "fi" {} section-strings verdict))
      => {:id            "v1"
          :category      "r"
          :legacy?       true
          :modified      12345
          :proposal?     false
          :replaced?     false
          :giver         "Foo Bar"
          :verdict-date  98765
          :title         "Luonnos"
          :verdict-state :published})
    (fact "Legacy published"
      (let [verdict (-> verdict
                        (publish 676767)
                        (assoc :legacy? true)
                        (assoc-in [:data :anto] 98765)
                        (assoc-in [:data :verdict-code] "41"))]
        (vc/draft? verdict) => false
        (vc/published? verdict) => true
        (vc/verdict-summary "fi" {} section-strings verdict))
      => {:id            "v1"
          :category      "r"
          :legacy?       true
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     676767
          :giver         "Foo Bar"
          :verdict-date  98765
          :title         "\u00a71 Ilmoitus merkitty tiedoksi"
          :verdict-state :published})
    (fact "latest-pk"
      (vc/latest-pk backend-verdict)
      => {:paatoskoodi     "ehdollinen"
          :paatospvm       998877
          :pykala          "2"
          :paatoksentekija "Mölli Keinonen"
          :status          "6"
          :urlHash         "b55ae9c30533428bd9965a84106fb163611c1a7d"})
    (fact "Backend verdit"
      (vc/draft? backend-verdict) => false
      (vc/published? backend-verdict) => true
      (vc/verdict-summary "fi" {} section-strings backend-verdict)
      => {:id            "backend-id"
          :category      "backing-system"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     12345
          :giver         "Mölli Keinonen"
          :verdict-date  998877
          :title         "ehdollinen"
          :verdict-state :published})
    (fact "YA backend verdit"
      (vc/draft? ya-backend-verdict) => false
      (vc/draft? (assoc ya-backend-verdict :draft true)) => true
      (vc/published? ya-backend-verdict) => true
      (vc/verdict-summary "fi" {} section-strings ya-backend-verdict)
      => {:id            "backend-id"
          :category      "backing-system"
          :legacy?       false
          :modified      12345
          :proposal?     false
          :replaced?     false
          :published     12345
          :giver         "Mölli Keinonen"
          :verdict-date  220033
          :title         "ehdollinen"
          :verdict-state :published})
    (facts "YA verdict-type"
      (let [verdict (-> verdict
                        (assoc :category "ya")
                        (assoc-in [:data :verdict-code] "annettu-lausunto")
                        (assoc-in [:data :verdict-type] "sijoituslupa")
                        (update-in [:template :inclusions] conj "verdict-type"))]
        (fact "Draft"
          (vc/verdict-summary "fi" {} section-strings verdict)
          => {:id            "v1"
              :category      "ya"
              :legacy?       false
              :modified      12345
              :proposal?     false
              :replaced?     false
              :giver         "Foo Bar"
              :verdict-date  876543
              :title         "Luonnos"
              :verdict-state :published})
        (fact "Published"
          (vc/verdict-summary "fi" {} section-strings
                              (publish verdict 656565))
          => {:id            "v1"
              :category      "ya"
              :legacy?       false
              :modified      12345
              :proposal?     false
              :replaced?     false
              :published     656565
              :giver         "Foo Bar"
              :verdict-date  876543
              :title         "\u00a71 Sijoituslupa - Annettu lausunto"
              :verdict-state :published})
        (fact "Published replacement"
          (vc/verdict-summary "fi" {"v1" "v2"} section-strings
                              (assoc (publish verdict 656565)
                                     :replacement {:replaces "v2"}))
          => {:id            "v1"
              :category      "ya"
              :legacy?       false
              :modified      12345
              :proposal?     false
              :replaced?     false
              :published     656565
              :giver         "Foo Bar"
              :verdict-date  876543
              :title         "\u00a71 Sijoituslupa - Annettu lausunto (korvaa päätöksen \u00a72)"
              :verdict-state :published})))
    (facts "Legacy contract"
      (fact "Draft"
        (vc/verdict-summary "fi" {} section-strings legacy-contract)
        => {:category      "contract"
            :giver         "Foo Bar"
            :id            "legacy-contract"
            :legacy?       true
            :modified      12345
            :proposal?     false
            :replaced?     false
            :title         "Luonnos"
            :verdict-date  54321
            :verdict-state :published})
      (fact "Published"
        (vc/verdict-summary "fi" {} section-strings (publish legacy-contract 88888))
        => {:category      "contract"
            :giver         "Foo Bar"
            :id            "legacy-contract"
            :legacy?       true
            :modified      12345
            :proposal?     false
            :replaced?     false
            :title         "Sopimus"
            :verdict-date  54321
            :published     88888
            :verdict-state :published}))
    (facts "verdict-attachment-id"
      (vc/verdict-attachment-id nil) => nil
      (vc/verdict-attachment-id {}) => nil
      (vc/verdict-attachment-id verdict) => nil
      (vc/verdict-attachment-id (assoc verdict :published {:attachment-id "aabbccddee"}))
      => "aabbccddee"
      (vc/verdict-attachment-id backend-verdict)
      => "b55ae9c30533428bd9965a84106fb163611c1a7d"
      (vc/verdict-attachment-id ya-backend-verdict) => nil)))

(facts "verdict-list"
  (let [v1 (make-verdict :id "v1" :code "ei-tutkittu-1" :section "11" :published 10 :replaced-by "v2")
        v2 (make-verdict :id "v2" :code "ei-lausuntoa" :section "" :published 20 :replaced-by "v3")
        v3 (make-verdict :id "v3" :code "asiakirjat palautettu" :section "33" :modified 30 :replaces "v2")
        v4 (make-verdict :id "v4" :code "ei-puollettu" :section "44" :published 25)
        c1 {:id        "c1"
            :category  "contract"
            :data      {:handler      "Writer"
                        :verdict-date 12345}
            :modified  50
            :published {:published 50}
            :state     "published"}
        c2 {:id       "c2"
            :category "contract"
            :legacy?  true
            :data     {:handler      "Author"
                       :verdict-date 54321}
            :modified 60
            :state    "draft"}]
    (vc/verdict-list {:lang        "fi"
                      :application {:pate-verdicts [v1 v2 v3 v4]
                                    :permitType    "R"}})
    => (just [(contains {:id       "v3"
                         :modified 30
                         :title    "Luonnos (korvaava päätös)"})
              (contains {:id        "v2"
                         :modified  20
                         :replaced? true
                         :title     "Ei lausuntoa (korvaa päätöksen \u00a711)"})
              (contains {:id        "v1"
                         :modified  10
                         :replaced? true
                         :title     "\u00a711 Ei tutkittu"})
              (contains {:id       "v4"
                         :modified 25
                         :title    "\u00a744 Ei puollettu"})])
    (vc/verdict-list {:lang        "fi"
                      :application {:pate-verdicts [c1 c2]
                                    :permitType    "YA"
                                    :permitSubtype "sijoitussopimus"}})
    => (just (contains {:category      "contract"
                        :giver         "Author"
                        :id            "c2"
                        :legacy?       true
                        :modified      60
                        :title         "Luonnos"
                        :verdict-date  54321
               :verdict-state :draft})
             (contains {:category      "contract"
                        :giver         "Writer"
                        :id            "c1"
                        :modified      50
                        :published     50
                        :title         "Sopimus"
                        :verdict-date  12345
                        :verdict-state :published}))))

(facts "required reviews"
  (facts "R"
    (fact "Pate verdict"
      (vc/verdict-required-reviews
        {:category   :r
         :data       {:reviews-included true
                      :reviews          ["review0"]}
         :references {:reviews [{:id   "review0"
                                 :fi   "Review fi"
                                 :sv   "Review sv"
                                 :en   "Review en"
                                 :type :osittainen-loppukatselmus}
                                {:id   "review1"
                                 :fi   "Review"
                                 :sv   "Review"
                                 :en   "Review"
                                 :type :rakennekatselmus}]}})
      => [{:id   "review0"
           :fi   "Review fi"
           :sv   "Review sv"
           :en   "Review en"
           :type "osittainen loppukatselmus"}]
      (vc/verdict-required-reviews
        {:category   :r
         :data       {:reviews-included false
                      :reviews          ["review0"]}
         :references {:reviews [{:id   "review0"
                                 :fi   "Review fi"
                                 :sv   "Review sv"
                                 :en   "Review en"
                                 :type :osittainen-loppukatselmus}
                                {:id   "review1"
                                 :fi   "Review"
                                 :sv   "Review"
                                 :en   "Review"
                                 :type :rakennekatselmus}]}})
      => nil
      (vc/verdict-required-reviews
        {:category   :r
         :data       {:reviews ["review0"]}
         :references {:reviews [{:id   "review0"
                                 :fi   "Review fi"
                                 :sv   "Review sv"
                                 :en   "Review en"
                                 :type :osittainen-loppukatselmus}
                                {:id   "review1"
                                 :fi   "Review"
                                 :sv   "Review"
                                 :en   "Review"
                                 :type :rakennekatselmus}]}})
      => nil)

    (fact "Legacy verdict"
      (vc/verdict-required-reviews
        {:legacy?  true
         :category :r
         :data     {:reviews {"review0" {:name "Review"
                                         :type :aloituskokous}
                              "review1" {:name "Check"
                                         :type :paikan-tarkastaminen}}}})
      => [{:id   "review0"
           :fi   "Review"
           :sv   "Review"
           :en   "Review"
           :type "aloituskokous"}
          {:id   "review1"
           :fi   "Check"
           :sv   "Check"
           :en   "Check"
           :type "rakennuksen paikan tarkastaminen"}])

    (fact "Backing system verdict"
      (vc/verdict-required-reviews
        {:paatokset [{:lupamaaraykset {:vaaditutKatselmukset [{:tarkastuksenTaiKatselmuksenNimi "Review"
                                                               :katselmuksenLaji                "osittainen loppukatselmus"}
                                                              {:katselmuksenLaji "joku ihme katselmus"}]}}]})
      => [{:fi   "Review"
           :sv   "Review"
           :en   "Review"
           :type "osittainen loppukatselmus"}
          {:fi   "joku ihme katselmus"
           :sv   "joku ihme katselmus"
           :en   "joku ihme katselmus"
           :type "joku ihme katselmus"}]))

  (facts "YA"
    (fact "Pate verdict"
      (vc/verdict-required-reviews
        {:category   :ya
         :data       {:reviews-included true
                      :reviews          ["review0"]}
         :references {:reviews [{:id   "review0"
                                 :fi   "Review fi"
                                 :sv   "Review sv"
                                 :en   "Review en"
                                 :type :aloituskatselmus}
                                {:id   "review1"
                                 :fi   "Review"
                                 :sv   "Review"
                                 :en   "Review"
                                 :type :loppukatselmus}]}})
      => [{:id   "review0"
           :fi   "Review fi"
           :sv   "Review sv"
           :en   "Review en"
           :type "Aloituskatselmus"}]
      (vc/verdict-required-reviews
        {:category   :ya
         :data       {:reviews-included false
                      :reviews          ["review0"]}
         :references {:reviews [{:id   "review0"
                                 :fi   "Review fi"
                                 :sv   "Review sv"
                                 :en   "Review en"
                                 :type :aloituskatselmus}
                                {:id   "review1"
                                 :fi   "Review"
                                 :sv   "Review"
                                 :en   "Review"
                                 :type :loppukatselmus}]}})
      => nil
      (vc/verdict-required-reviews
        {:category   :ya
         :data       {:reviews          ["review0"]}
         :references {:reviews [{:id   "review0"
                                 :fi   "Review fi"
                                 :sv   "Review sv"
                                 :en   "Review en"
                                 :type :aloituskatselmus}
                                {:id   "review1"
                                 :fi   "Review"
                                 :sv   "Review"
                                 :en   "Review"
                                 :type :loppukatselmus}]}})
      => nil)

    (fact "Legacy verdict"
      (vc/verdict-required-reviews
        {:legacy?  true
         :category :ya
         :data     {:reviews {"review0" {:name "Review"
                                         :type :aloituskatselmus}
                              "review1" {:name "Check"
                                         :type :valvonta}}}})
      => [{:id   "review0"
           :fi   "Review"
           :sv   "Review"
           :en   "Review"
           :type "Aloituskatselmus"}
          {:id   "review1"
           :fi   "Check"
           :sv   "Check"
           :en   "Check"
           :type "Muu valvontakäynti"}])

    (fact "Backing system verdict"
      (vc/verdict-required-reviews
        {:paatokset [{:lupamaaraykset {:vaaditutKatselmukset [{:tarkastuksenTaiKatselmuksenNimi "Review"
                                                               :katselmuksenLaji                "Muu valvontakäynti"}
                                                              {:katselmuksenLaji "joku ihme katselmus"}]}}]})
      => [{:fi   "Review"
           :sv   "Review"
           :en   "Review"
           :type "Muu valvontakäynti"}
          {:fi   "joku ihme katselmus"
           :sv   "joku ihme katselmus"
           :en   "joku ihme katselmus"
           :type "joku ihme katselmus"}])))

(facts "required foremen"
  (fact "Pate verdict"
    (vc/verdict-required-foremen
      {:category :r
       :data     {:foremen-included true
                  :foremen          [:vv-tj :erityis-tj :XYZ]}})
    => ["KVV-työnjohtaja" "erityisalojen työnjohtaja" "ei tiedossa"]
    (vc/verdict-required-foremen
      {:category :r
       :data     {:foremen-included false
                  :foremen          [:vv-tj :erityis-tj :XYZ]}})
    => nil
    (vc/verdict-required-foremen
      {:category :r
       :data     {:foremen [:vv-tj :erityis-tj :XYZ]}})
    => nil)

  (fact "Legacy verdict"
    (vc/verdict-required-foremen
      {:legacy?  true
       :category :r
       :data     {:foremen {"foreman0" {:role "KVV-työnjohtaja"}
                            "foreman1" {:role "erityisalojen työnjohtaja"}
                            "foreman3" {:role "ei tiedossa"}}}})
    => ["KVV-työnjohtaja" "erityisalojen työnjohtaja" "ei tiedossa"])

  (fact "Backing system verdict"
    (vc/verdict-required-foremen
      {:paatokset [{:lupamaaraykset {:vaaditutTyonjohtajat
                                     "KVV-työnjohtaja, Erityisalojen työnjohtaja, Vastaava työnjohtaja, Joku outo"}}]})
    => ["KVV-työnjohtaja" "Erityisalojen työnjohtaja" "Vastaava työnjohtaja" "Joku outo"]))

(facts "required conditions"
  (fact "Pate verdict"
    (vc/verdict-required-conditions
      {:category :r
       :data {:conditions {:id0 {:condition "Muu lupaehto"}
                           :id1 {:condition "  vesijohto ja viemärisuunnitelma  "}
                           :id2 {}
                           :id3 {:condition "    "}}}})
    => ["Muu lupaehto" "vesijohto ja viemärisuunnitelma"])

  (fact "Legacy verdict"
    (vc/verdict-required-conditions
      {:legacy? true
       :category :r
       :data {:conditions
              {"id" {:name {:_value " rakennepiirustukset "
                            :_user "Verdict draft Pate migration"
                            :_modified 1537491445896}}
               "foo" {:name {:_value " "
                             :_user "Verdict draft Pate migration"
                             :_modified 1537491445896}}
               "bar" {}}}})
    => ["rakennepiirustukset"])

  (fact "Backing system verdict"
    (vc/verdict-required-conditions
      {:paatokset [{:lupamaaraykset {:maaraykset
                                     [{:sisalto "rakennepiirustukset "}
                                      {}
                                      {:sisalto "  "}
                                      {:sisalto " vesijohto ja viemärisuunnitelma"}]}}]})
    => ["rakennepiirustukset"
        "vesijohto ja viemärisuunnitelma"]))

(facts "required plans"
  (fact "Pate verdict"
    (vc/verdict-required-plans
      {:category   :r
       :data       {:plans-included true
                    :plans          ["plan0" "plan2"]}
       :references {:plans [{:id "plan0"
                             :fi "Suunnitelma"
                             :sv "Plan"
                             :en "Plan"}
                            {:id "plan1"}
                            {:id "plan2"
                             :fi "Toinen suunnitelma"
                             :sv "Annan plan"
                             :en "Another plan"}]}})
    => [{:id "plan0"
         :fi "Suunnitelma"
         :sv "Plan"
         :en "Plan"}
        {:id "plan2"
         :fi "Toinen suunnitelma"
         :sv "Annan plan"
         :en "Another plan"}]
    (vc/verdict-required-plans
      {:category   :r
       :data       {:plans-included false
                    :plans          ["plan0" "plan2"]}
       :references {:plans [{:id "plan0"
                             :fi "Suunnitelma"
                             :sv "Plan"
                             :en "Plan"}
                            {:id "plan1"}
                            {:id "plan2"
                             :fi "Toinen suunnitelma"
                             :sv "Annan plan"
                             :en "Another plan"}]}})
    => nil
    (vc/verdict-required-plans
      {:category   :r
       :data       {:plans ["plan0" "plan2"]}
       :references {:plans [{:id "plan0"
                             :fi "Suunnitelma"
                             :sv "Plan"
                             :en "Plan"}
                            {:id "plan1"}
                            {:id "plan2"
                             :fi "Toinen suunnitelma"
                             :sv "Annan plan"
                             :en "Another plan"}]}})
    => nil)

  (fact "Legacy verdict"
    (vc/verdict-required-plans
      {:legacy?  true
       :category :r
       :data     irrelevant})
    => [])

  (fact "Backing system verdict"
    (vc/verdict-required-plans
      {:paatokset [{:lupamaaraykset {:vaaditutErityissuunnitelmat
                                     ["KVV-suunnitelmat"]}}]})
    => [{:id nil
         :fi "KVV-suunnitelmat"
         :sv "KVV-suunnitelmat"
         :en "KVV-suunnitelmat"}]))

(facts "parking space requirements"
  (fact "Pate verdict"
    (vc/verdict-parking-space-requirements
      {:category :r
       :data     {:buildings {"building0" {:rakennetut-autopaikat  "1"
                                           :kiinteiston-autopaikat "2"
                                           :autopaikat-yhteensa    "3"
                                           :vss-luokka             "4"
                                           :paloluokka             "5"}
                              "building1" {:rakennetut-autopaikat  "1"
                                           :kiinteiston-autopaikat "1"
                                           :autopaikat-yhteensa    "1"
                                           :vss-luokka             "1"
                                           :paloluokka             "1"}}}})
    => {:autopaikkojaEnintaan      nil
        :autopaikkojaVahintaan     nil
        :autopaikkojaRakennettava  4
        :autopaikkojaRakennettu    2
        :autopaikkojaKiinteistolla 3
        :autopaikkojaUlkopuolella  nil})

  (fact "Legacy verdict"
    (vc/verdict-parking-space-requirements
      {:legacy?  true
       :category :r
       :data     irrelevant})
    => {:autopaikkojaEnintaan      nil
        :autopaikkojaVahintaan     nil
        :autopaikkojaRakennettava  nil
        :autopaikkojaRakennettu    nil
        :autopaikkojaKiinteistolla nil
        :autopaikkojaUlkopuolella  nil})

  (fact "Backing system verdict"
    (vc/verdict-parking-space-requirements
      {:paatokset [{:lupamaaraykset {:autopaikkojaEnintaan  2
                                     :autopaikkojaVahintaan 1}}]})
    => {:autopaikkojaEnintaan      2
        :autopaikkojaVahintaan     1
        :autopaikkojaRakennettava  nil
        :autopaikkojaRakennettu    nil
        :autopaikkojaKiinteistolla nil
        :autopaikkojaUlkopuolella  nil}))

(facts "parking space requirements"
  (fact "Pate verdict"
    (vc/verdict-area-requirements
      {:category :r
       :data irrelevant})
    => {:kerrosala nil
        :kokonaisala nil
        :rakennusoikeudellinenKerrosala nil})

  (fact "Legacy verdict"
    (vc/verdict-area-requirements
      {:legacy? true
       :category :r
       :data irrelevant})
    => {:kerrosala nil
        :kokonaisala nil
        :rakennusoikeudellinenKerrosala nil})

  (fact "Backing system verdict"
    (vc/verdict-area-requirements
      {:paatokset [{:lupamaaraykset {:kerrosala "72"
                                     :kokonaisala "72"
                                     :rakennusoikeudellinenKerrosala "72.000"}}]})
    => {:kerrosala 72
        :kokonaisala 72
        :rakennusoikeudellinenKerrosala 72}))

(facts "Kokoontumistilan henkilomaara"
  (vc/verdict-kokoontumistilan-henkilomaara
    {:category :r
     :data {:buildings {"building0" {:rakennetut-autopaikat        "1"
                                     :kiinteiston-autopaikat       "2"
                                     :autopaikat-yhteensa          "3"
                                     :vss-luokka                   "4"
                                     :paloluokka                   "5"
                                     :kokoontumistilanHenkilomaara "6"}
                        "building1" {:rakennetut-autopaikat        "1"
                                     :kiinteiston-autopaikat       "1"
                                     :autopaikat-yhteensa          "1"
                                     :vss-luokka                   "1"
                                     :paloluokka                   "1"
                                     :kokoontumistilanHenkilomaara "7"}}}})
  => 13

  (vc/verdict-kokoontumistilan-henkilomaara
    {:category :r
     :data {:buildings {"building0" {:rakennetut-autopaikat        "1"
                                     :kiinteiston-autopaikat       "2"
                                     :autopaikat-yhteensa          "3"
                                     :vss-luokka                   "4"
                                     :paloluokka                   "5"}
                        "building1" {:rakennetut-autopaikat        "1"
                                     :kiinteiston-autopaikat       "1"
                                     :autopaikat-yhteensa          "1"
                                     :vss-luokka                   "1"
                                     :paloluokka                   "1"}}}})
  => nil

  (fact "Backing system verdict"
    (vc/verdict-kokoontumistilan-henkilomaara
      {:paatokset [{:lupamaaraykset {:kokoontumistilanHenkilomaara "7"}}]})
    => 7)

  (fact "no data"
    (vc/verdict-kokoontumistilan-henkilomaara
      {:paatokset [{:lupamaaraykset {}}]})
    => nil))

(facts "verdict-giver"
  (let [verdict {:category "r"
                 :data     {:handler {:_value    "Handler"
                                      :_modified 12345
                                      :_user     "User"}
                            :giver   {:_value    "Giver"
                                      :_modified 12345
                                      :_user     "User"}}
                 :template {:giver "viranhaltija"}}]
    (fact "giver"
      (vc/verdict-giver verdict) => "Giver")
    (fact "board-verdict?"
      (vc/board-verdict? verdict) => false)
    (fact "handler"
      (vc/verdict-giver (util/dissoc-in verdict [:data :giver]))
      => "Handler"
      (vc/verdict-giver (assoc-in verdict [:data :giver :_value] "   "))
      => "Handler")
    (fact "boardname"
      (let [verdict (-> verdict
                        (assoc-in [:template :giver] "lautakunta")
                        (assoc-in [:references :boardname] "Boardname"))]
        (vc/verdict-giver verdict) => "Giver"
        (vc/verdict-giver (util/dissoc-in verdict [:data :giver]))
        => "Boardname"
        (vc/verdict-giver (assoc-in verdict [:data :giver :_value] "   "))
        => "Boardname"
        (fact "board-verdict?"
          (vc/board-verdict? verdict) => true)))
    (fact "paatoksentekija"
      (letfn [(vfn [& xs]
                (some->> (partition 2 xs)
                         (map #(hash-map :paatospvm (first %)
                                         :paatoksentekija (second %)))
                         (hash-map :poytakirjat)))]
        (vfn 123 "Hello" 456 "World")
        => {:poytakirjat [{:paatospvm 123
                           :paatoksentekija "Hello"}
                          {:paatospvm 456
                           :paatoksentekija "World"}]}
        (vc/verdict-giver {:paatokset [(vfn 123 "Hello" 456 "World")]})
        => "World"
        (vc/verdict-giver {:paatokset [(vfn 923 "Hello" 456 "World")]})
        => "Hello"
        (vc/verdict-giver {:paatokset [(vfn 123 "Hello" 456 "World")
                                       (vfn 999 "Foobar" 11 "Hiihoo" 99 "Dum")]})
        => "Foobar"))
    (fact "contract"
      (vc/verdict-giver (assoc verdict :category "contract"))
      => "Handler")))

(let [attachments (map #(hash-map :id (str "att" %)) (range 1 11))]
  (facts "selected-attachment-ids"
    (vc/selected-attachment-ids attachments {}) => empty?
    (vc/selected-attachment-ids attachments {:data {:selected-attachments []}}) => empty?
    (vc/selected-attachment-ids attachments {:data {:selected-attachments ["att2" "att4" "foo" "bar" "att8"]}})
    => ["att2" "att4" "att8"]))
