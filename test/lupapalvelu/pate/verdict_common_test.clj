(ns lupapalvelu.pate.verdict-common-test
  (:require [lupapalvelu.pate-test-util :refer :all]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict-common :as vc]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.pate.verdict-common
                   verdict-section-string
                   title-fn verdict-string)

(defn publish [verdict ts]
  (-> verdict
      (assoc :state (metadata/wrap "publisher" ts "published"))
      (assoc-in [:published :published] ts)))

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
             => "Ty\u00f6h\u00f6n liittyy ehto")
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
                                                                        (map #(dissoc % :paatospvm) pks)))
                               (assoc-in [:paatokset 0 :paivamaarat :paatosdokumentinPvm] 220033))]

    (fact "Nil"
      (vc/draft? nil) => false
      (vc/published? nil) => false
      (vc/draft? {}) => false
      (vc/published? {}) => true
      (vc/verdict-summary "fi" nil nil)
      => {:category  "backing-system"
          :legacy?   false
          :proposal? false
          :title     "Luonnos"})
    (fact "Draft"
      (vc/draft? verdict) => true
      (vc/published? verdict) => false
      (vc/verdict-summary "fi" section-strings verdict)
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :giver        "Foo Bar"
          :verdict-date 876543
          :title        "Luonnos"})
    (fact "Board draft"
      (vc/verdict-summary "fi" section-strings
                          (assoc-in verdict [:template :giver] "lautakunta"))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :giver        "Broad board abroad"
          :verdict-date 876543
          :title        "Luonnos"})
    (fact "Verdict proposal"
      (vc/verdict-summary "fi" section-strings
                          (-> verdict
                              (assoc-in [:template :giver] "lautakunta")
                              (assoc :state "proposal")))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    true
          :giver        "Broad board abroad"
          :verdict-date 876543
          :title        "P\u00e4\u00e4t\u00f6sehdotus"})
    (fact "Replacement draft"
      (vc/verdict-summary "fi" section-strings
                          (assoc-in verdict [:replacement :replaces] "v2"))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :giver        "Foo Bar"
          :verdict-date 876543
          :replaces     "v2"
          :title        "Luonnos (korvaa p\u00e4\u00e4t\u00f6ksen \u00a72)"})
    (fact "Published"
      (let [verdict (publish verdict 121212)]
        (vc/draft? verdict) => false
        (vc/published? verdict) => true
        (vc/verdict-summary "fi" section-strings verdict))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :published    121212
          :giver        "Foo Bar"
          :verdict-date 876543
          :title        "\u00a71 Ehdollinen"})
    (fact "Published, no section"
      (let [verdict (-> verdict
                        (publish 121212)
                        (assoc-in [:data :verdict-section] nil))]
        (vc/draft? verdict) => false
        (vc/published? verdict) => true
        (vc/verdict-summary "fi" {} verdict))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :published    121212
          :giver        "Foo Bar"
          :verdict-date 876543
          :title        "Ehdollinen"})
    (fact "Published replacement"
      (vc/verdict-summary "fi" section-strings
                          (-> verdict
                              (publish 121212)
                              (assoc-in [:replacement :replaces] "v2")))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :published    121212
          :giver        "Foo Bar"
          :verdict-date 876543
          :replaces     "v2"
          :title        "\u00a71 Ehdollinen (korvaa p\u00e4\u00e4t\u00f6ksen \u00a72)"})
    (fact "Published replacement, no section"
      (vc/verdict-summary "fi" (dissoc section-strings "v2")
                          (-> verdict
                              (publish 121212)
                              (assoc-in [:replacement :replaces] "v2")))
      => {:id           "v1"
          :category     "r"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :published    121212
          :giver        "Foo Bar"
          :verdict-date 876543
          :replaces     "v2"
          :title        "\u00a71 Ehdollinen (korvaava p\u00e4\u00e4t\u00f6s)"})
    (fact "Legacy draft"
      (let [verdict (-> verdict
                        (assoc :legacy? true)
                        (assoc-in [:data :anto] 98765))]
        (vc/draft? verdict) => true
        (vc/published? verdict) => false
        (vc/verdict-summary "fi" section-strings verdict))
      => {:id           "v1"
          :category     "r"
          :legacy?      true
          :modified     12345
          :proposal?    false
          :giver        "Foo Bar"
          :verdict-date 98765
          :title        "Luonnos"})
    (fact "Legacy published"
      (let [verdict (-> verdict
                        (publish 676767)
                        (assoc :legacy? true)
                        (assoc-in [:data :anto] 98765)
                        (assoc-in [:data :verdict-code] "41"))]
        (vc/draft? verdict) => false
        (vc/published? verdict) => true
        (vc/verdict-summary "fi" section-strings verdict))
      => {:id           "v1"
          :category     "r"
          :legacy?      true
          :modified     12345
          :proposal?    false
          :published    676767
          :giver        "Foo Bar"
          :verdict-date 98765
          :title        "\u00a71 Ilmoitus merkitty tiedoksi"})
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
      (vc/verdict-summary "fi" section-strings backend-verdict)
      => {:id           "backend-id"
          :category     "backing-system"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :published    12345
          :giver        "Mölli Keinonen"
          :verdict-date 998877
          :title        "ehdollinen"})
    (fact "YA backend verdit"
      (vc/draft? ya-backend-verdict) => false
      (vc/draft? (assoc ya-backend-verdict :draft true)) => true
      (vc/published? ya-backend-verdict) => true
      (vc/verdict-summary "fi" section-strings ya-backend-verdict)
      => {:id           "backend-id"
          :category     "backing-system"
          :legacy?      false
          :modified     12345
          :proposal?    false
          :published    12345
          :giver        "Mölli Keinonen"
          :verdict-date 220033
          :title        "ehdollinen"})
    (facts "YA verdict-type"
      (let [verdict (-> verdict
                        (assoc :category "ya")
                        (assoc-in [:data :verdict-code] "annettu-lausunto")
                        (assoc-in [:data :verdict-type] "sijoituslupa")
                        (update-in [:template :inclusions] conj "verdict-type"))]
        (fact "Draft"
          (vc/verdict-summary "fi" section-strings verdict)
          => {:id           "v1"
              :category     "ya"
              :legacy?      false
              :modified     12345
              :proposal?    false
              :giver        "Foo Bar"
              :verdict-date 876543
              :title        "Luonnos"})
        (fact "Published"
          (vc/verdict-summary "fi" section-strings
                              (publish verdict 656565))
          => {:id           "v1"
              :category     "ya"
              :legacy?      false
              :modified     12345
              :proposal?    false
              :published    656565
              :giver        "Foo Bar"
              :verdict-date 876543
              :title        "\u00a71 Sijoituslupa - Annettu lausunto"})
        (fact "Published replacement"
          (vc/verdict-summary "fi" section-strings
                              (assoc (publish verdict 656565)
                                     :replacement {:replaces "v2"}))
          => {:id           "v1"
              :category     "ya"
              :legacy?      false
              :modified     12345
              :proposal?    false
              :published    656565
              :giver        "Foo Bar"
              :verdict-date 876543
              :replaces     "v2"
              :title        "\u00a71 Sijoituslupa - Annettu lausunto (korvaa p\u00e4\u00e4t\u00f6ksen \u00a72)"})))))

(facts "verdict-list"
  (let [v1 (make-verdict :id "v1" :code "ei-tutkittu-1" :section "11" :published 10)
        v2 (make-verdict :id "v2" :code "ei-lausuntoa" :section "" :published 20 :replaces "v1")
        v3 (make-verdict :id "v3" :code "asiakirjat palautettu" :section "33" :modified 30 :replaces "v2")
        v4 (make-verdict :id "v4" :code "ei-puollettu" :section "44" :published 25)]
    (vc/verdict-list {:lang        "fi"
                      :application {:pate-verdicts [v1 v2 v3 v4]
                                    :permitType    "R"}})
    => (just [(contains {:id       "v3"
                         :modified 30
                         :title    "Luonnos (korvaava p\u00e4\u00e4t\u00f6s)"})
              (contains {:id        "v2"
                         :modified  20
                         :replaced? true
                         :title     "Ei lausuntoa (korvaa p\u00e4\u00e4t\u00f6ksen \u00a711)"})
              (contains {:id        "v1"
                         :modified  10
                         :replaced? true
                         :title     "\u00a711 Ei tutkittu"})
              (contains {:id       "v4"
                         :modified 25
                         :title    "\u00a744 Ei puollettu"})])))

(facts "required reviews"
  (fact "Pate verdict"
    (vc/verdict-required-reviews
     {:category :r
      :data {:reviews ["review0"]}
      :references {:reviews [{:id "review0"
                              :fi "Review fi"
                              :sv "Review sv"
                              :en "Review en"
                              :type :osittainen-loppukatselmus}
                             {:id "review1"
                              :fi "Review"
                              :sv "Review"
                              :en "Review"
                              :type :rakennekatselmus}]}})
    => [{:id "review0"
         :fi "Review fi"
         :sv "Review sv"
         :en "Review en"
         :type "osittainen loppukatselmus"}])

  (fact "Legacy verdict"
    (vc/verdict-required-reviews
     {:legacy? true
      :category :r
      :data {:reviews {"review0" {:name "Review"
                                  :type :aloituskokous}
                       "review1" {:name "Check"
                                  :type :paikan-tarkastaminen}}}})
    => [{:id "review0"
         :fi "Review"
         :sv "Review"
         :en "Review"
         :type "aloituskokous"}
        {:id "review1"
         :fi "Check"
         :sv "Check"
         :en "Check"
         :type "rakennuksen paikan tarkastaminen"}])

  (fact "Backing system verdict"
    (vc/verdict-required-reviews
     {:paatokset [{:lupamaaraykset {:vaaditutKatselmukset [{:tarkastuksenTaiKatselmuksenNimi "Review"
                                                            :katselmuksenLaji "osittainen loppukatselmus"}
                                                           {:katselmuksenLaji "joku ihme katselmus"}]}}]})
    => [{:id nil
         :fi "Review"
         :sv "Review"
         :en "Review"
         :type "osittainen loppukatselmus"}
        {:id nil
         :fi nil
         :sv nil
         :en nil
         :type "joku ihme katselmus"}]))

(facts "required foremen"
  (fact "Pate verdict"
    (vc/verdict-required-foremen
     {:category :r
      :data {:foremen [:vv-tj :erityis-tj :XYZ]}})
    => ["KVV-työnjohtaja" "erityisalojen työnjohtaja" "ei tiedossa"])

  (fact "Legacy verdict"
    (vc/verdict-required-foremen
     {:legacy? true
      :category :r
      :data {:foremen {"foreman0" {:role "vv-tj"}
                       "foreman1" {:role "erityis-tj"}
                       "foreman3" {:role "XYZ"}}}})
    => ["KVV-työnjohtaja" "erityisalojen työnjohtaja" "ei tiedossa"])

  (fact "Backing system verdict"
    (vc/verdict-required-foremen
     {:paatokset [{:lupamaaraykset {:vaadittuTyonjohtajatieto
                                    ["KVV-työnjohtaja" "erityisalojen työnjohtaja"]}}]})
    => ["KVV-työnjohtaja" "erityisalojen työnjohtaja"]))

(facts "required conditions"
  (fact "Pate verdict"
    (vc/verdict-required-conditions
     {:category :r
      :data {:conditions {:id0 {:condition "Muu lupaehto"}
                          :id1 {:condition "vesijohto ja viemärisuunnitelma"}}}})
    => ["Muu lupaehto" "vesijohto ja viemärisuunnitelma"])

  (fact "Legacy verdict"
    (vc/verdict-required-conditions
     {:legacy? true
      :category :r
      :data {:conditions
             {"id" {:name {:_value "rakennepiirustukset"
                           :_user "Verdict draft Pate migration"
                           :_modified 1537491445896}}}}})
    => ["rakennepiirustukset"])

  (fact "Backing system verdict"
    (vc/verdict-required-conditions
     {:paatokset [{:lupamaaraykset {:maaraykset
                                    [{:sisalto "rakennepiirustukset"}
                                     {:sisalto "vesijohto ja viemärisuunnitelma"}]}}]})
    => ["rakennepiirustukset"
        "vesijohto ja viemärisuunnitelma"]))

(facts "required plans"
  (fact "Pate verdict"
    (vc/verdict-required-plans
     {:category :r
      :data {:plans ["plan0" "plan2"]}
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
         :en "Another plan"}])

  (fact "Legacy verdict"
    (vc/verdict-required-plans
     {:legacy? true
      :category :r
      :data irrelevant})
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
      :data {:buildings {"building0" {:rakennetut-autopaikat  "1"
                                      :kiinteiston-autopaikat "2"
                                      :autopaikat-yhteensa    "3"
                                      :vss-luokka             "4"
                                      :paloluokka             "5"}
                         "building1" {:rakennetut-autopaikat  "1"
                                      :kiinteiston-autopaikat "1"
                                      :autopaikat-yhteensa    "1"
                                      :vss-luokka             "1"
                                      :paloluokka             "1"}}}})
    => {:autopaikkojaEnintaan nil
        :autopaikkojaVahintaan nil
        :autopaikkojaRakennettava 4
        :autopaikkojaRakennettu 2
        :autopaikkojaKiinteistolla 3
        :autopaikkojaUlkopuolella nil})

  (fact "Legacy verdict"
    (vc/verdict-parking-space-requirements
     {:legacy? true
      :category :r
      :data irrelevant})
    => {:autopaikkojaEnintaan nil
        :autopaikkojaVahintaan nil
        :autopaikkojaRakennettava nil
        :autopaikkojaRakennettu nil
        :autopaikkojaKiinteistolla nil
        :autopaikkojaUlkopuolella nil})

  (fact "Backing system verdict"
    (vc/verdict-parking-space-requirements
     {:paatokset [{:lupamaaraykset {:autopaikkojaEnintaan 2
                                    :autopaikkojaVahintaan 1}}]})
    => {:autopaikkojaEnintaan 2
        :autopaikkojaVahintaan 1
        :autopaikkojaRakennettava nil
        :autopaikkojaRakennettu nil
        :autopaikkojaKiinteistolla nil
        :autopaikkojaUlkopuolella nil}))

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
