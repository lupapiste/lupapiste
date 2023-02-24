(ns lupapalvelu.bulletin-report-test
  (:require [lupapalvelu.bulletin-report.core :as core]
            [lupapalvelu.bulletin-report.page :as page]
            [lupapalvelu.test-util :refer [in-text]]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]))

(facts "->Autopaikkoja"
  (core/->Autopaikkoja {}) => nil
  (core/->Autopaikkoja {:autopaikkojaEnintaan 10})
  => {:enintaan "10"}
  (core/->Autopaikkoja {:autopaikkojaEnintaan 0})
  => nil
  (core/->Autopaikkoja {:autopaikkojaEnintaan "  0 "})
  => nil
  (core/->Autopaikkoja {:autopaikkojaEnintaan -1})
  => {:enintaan "-1"}
  (core/->Autopaikkoja {:autopaikkojaEnintaan      100
                        :autopaikkojaVahintaan     90
                        :autopaikkojaRakennettava  80
                        :autopaikkojaRakennettu    70
                        :autopaikkojaKiinteistolla 60
                        :autopaikkojaUlkopuolella  50})
  => {:enintaan      "100"
      :vahintaan     "90"
      :rakennettava  "80"
      :rakennettu    "70"
      :kiinteistolla "60"
      :ulkopuolella  "50"}
  (core/->Autopaikkoja {:autopaikkojaEnintaan     100
                        :autopaikkojaVahintaan    nil
                        :autopaikkojaRakennettava 80
                        :autopaikkojaRakennettu   0
                        :autopaikkojaUlkopuolella 50
                        :foo                      "bar"
                        :other                    111})
  => {:enintaan     "100"
      :rakennettava "80"
      :ulkopuolella "50"})

(facts "->Maaraykset"
  (core/->Maaraykset {} true) => nil
  (core/->Maaraykset {:maaraykset [{:sisalto "*hello*"}
                                   {:sisalto "&"}
                                   {:sisalto "_world_!"}]} false)
  => ["*hello*" "&amp;" "_world_!"]
  (core/->Maaraykset {:maaraykset     [{:sisalto "*hello*"}
                                       {:sisalto "&"}
                                       {:sisalto "_world_!"}]
                      :muutMaaraykset []} true)
  => ["<span><strong>hello</strong><br/></span>"
      "<span>&amp;<br/></span>"
      "<span><em>world</em>!<br/></span>"]
  (core/->Maaraykset {:muutMaaraykset ["*hello*" "&" "_world_!"]} false)
  => ["*hello*" "&amp;" "_world_!"]
  (core/->Maaraykset {:muutMaaraykset ["*hello*" "&" "_world_!"]
                      :maaraykset     []} true)
  => ["<span><strong>hello</strong><br/></span>"
      "<span>&amp;<br/></span>"
      "<span><em>world</em>!<br/></span>"]
  (core/->Maaraykset {:maaraykset     [{:sisalto "*hello*"}]
                      :muutMaaraykset ["_world_!"]} false)
  => ["*hello*" "_world_!"]
  (core/->Maaraykset {:maaraykset     [{:sisalto "*hello*"}]
                      :muutMaaraykset ["_world_!"]} true)
  => ["<span><strong>hello</strong><br/></span>"
      "<span><em>world</em>!<br/></span>"]
  (core/->Maaraykset {:maaraykset     []
                      :muutMaaraykset []} true)
  => nil)

(facts "->Katselmukset"
  (core/->Katselmukset {} :fi) => nil
  (core/->Katselmukset {:vaaditutKatselmukset []} :fi) => nil
  (core/->Katselmukset {:vaaditutKatselmukset [{:tarkastuksenTaiKatselmuksenNimi "Hello"}
                                               {:katselmuksenLaji "loppukatselmus"}]} "sv")
  => ["Hello" "Slutsyn"]
  (core/->Katselmukset {} :bad)
  => (throws Exception #"Input to ->Katselmukset does not match schema:"))

(facts "->Tyonjohtajat"
  (core/->Tyonjohtajat nil) => nil
  (core/->Tyonjohtajat {:vaaditutTyonjohtajat "Hello, World"})
  => ["Hello" "World"]
  (core/->Tyonjohtajat {:vaaditutTyonjohtajat ""}) => nil
  (core/->Tyonjohtajat {:vaaditutTyonjohtajat "  ,  , ,,, "}) => nil
  (core/->Tyonjohtajat {:vaaditutTyonjohtajat "  ,  hello , ,  , world   ,"})
  => ["hello" "world"]
  (core/->Tyonjohtajat {:vaaditutTyonjohtajat "  Hello   "}) => ["Hello"])

(def runeberg (date/zoned-date-time {:day 5  :month 2 :year 2021 :hour 12}))
(def other    (date/zoned-date-time {:day 27 :month 5 :year 2021}))

(facts "->Poytakirjat"
  (core/->Poytakirjat {} true) => nil
  (core/->Poytakirjat {:poytakirjat []} true) => nil
  (core/->Poytakirjat {:poytakirjat [{}]} true) => nil
  (core/->Poytakirjat {:poytakirjat [{}]} true) => nil
  (core/->Poytakirjat {:poytakirjat [{:paatos          "*Hello* & World!"
                                      :paatoksentekija "Virka Into"
                                      :paatoskoodi     "Grudgingly accepted"
                                      :paatospvm       (date/timestamp runeberg)
                                      :pykala          "8"}]}
                      false)
  => [{:paatoksentekija "Virka Into"
       :text-html       "*Hello* &amp; World!"
       :paatoskoodi     "Grudgingly accepted"
       :verdict-date    runeberg
       :section         "8"}]
  (core/->Poytakirjat {:poytakirjat [{:paatos          "*Hello* & World!"
                                      :paatoksentekija "Virka Into"
                                      :paatoskoodi     "Grudgingly accepted"
                                      :paatospvm       (date/timestamp runeberg)}
                                     {:paatos          "<Yeah>"
                                      :paatoksentekija "Riihi Mäki"
                                      :paatoskoodi     "Celebrated"
                                      :paatospvm       (date/timestamp other)
                                      :pykala          9}]}
                      true)
  => [{:paatoksentekija "Virka Into"
       :paatoskoodi     "Grudgingly accepted"
       :text-html       "<span><strong>Hello</strong> &amp; World!<br/></span>"
       :verdict-date    runeberg}
      {:paatoksentekija "Riihi Mäki"
       :paatoskoodi     "Celebrated"
       :section         "9"
       :text-html       "<span>&lt;Yeah&gt;<br/></span>"
       :verdict-date    other}])

(facts "->Paatos"
  (core/->Paatos {:poytakirjat [{:paatos          "*Hello* & World!"
                                 :paatoksentekija "Virka Into"
                                 :paatoskoodi     "Grudgingly accepted"
                                 :paatospvm       (date/timestamp runeberg)
                                 :pykala          "8"}]}
                 {:lang :sv})
  => {:poytakirjat [{:paatoksentekija "Virka Into"
                     :text-html       "*Hello* &amp; World!"
                     :paatoskoodi     "Grudgingly accepted"
                     :verdict-date    runeberg
                     :section         "8"}]}
  (core/->Paatos
    {:lupamaaraykset {:autopaikkojaEnintaan        10
                      :maaraykset                  [{:sisalto "~Howdy~"}]
                      :vaaditutErityissuunnitelmat ["Roadmap"]
                      :vaaditutKatselmukset        [{:tarkastuksenTaiKatselmuksenNimi "Inspection"}
                                                    {:katselmuksenLaji "aloituskokous"}]
                      :vaaditutTyonjohtajat        "Bossman"
                      :kerrosala                   "200"}
     :poytakirjat    [{:paatos          "*Hello* & World!"
                       :paatoksentekija "Virka Into"
                       :paatoskoodi     "Grudgingly accepted"
                       :paatospvm       (date/timestamp runeberg)
                       :pykala          "8"}]}
    {:lang    :sv
     :markup? true})
  => {:autopaikkoja {:enintaan "10"}
      :katselmukset ["Inspection" "Inledande möte"]
      :kerrosala    "200"
      :maaraykset   ["<span><span class=\"underline\">Howdy</span><br/></span>"]
      :suunnitelmat ["Roadmap"]
      :tyonjohtajat ["Bossman"]
      :poytakirjat  [{:paatoksentekija "Virka Into"
                      :text-html       "<span><strong>Hello</strong> &amp; World!<br/></span>"
                      :paatoskoodi     "Grudgingly accepted"
                      :verdict-date    runeberg
                      :section         "8"}]})
(defn day [d]
  (date/zoned-date-time {:day d :month 5 :year 2021 :hour (rand-int 24)}))

(defn day-start [zoned] (date/with-time zoned))

(defn day-end [zoned] (date/end-of-day zoned))

(let [[Mon1 Tue1 Wed1 Thu1 Fri1 Sat1 Sun1
       Mon2 Tue2 Wed2 Thu2 Fri2 Sat2
       Sun2]    (map day (range 17 31))
      ts        date/timestamp
      paatos    {:lupamaaraykset {:autopaikkojaEnintaan        10
                                  :maaraykset                  [{:sisalto " ~Howdy~ "}]
                                  :vaaditutErityissuunnitelmat ["Roadmap"]
                                  :vaaditutKatselmukset        [{:tarkastuksenTaiKatselmuksenNimi " Inspection "}
                                                                {:katselmuksenLaji " aloituskokous "}]
                                  :vaaditutTyonjohtajat        " Bossman "
                                  :kerrosala                   " 200 "}
                 :poytakirjat    [{:paatos          " *Hello* & World! "
                                   :paatoksentekija " Virka Into "
                                   :paatoskoodi     " Grudgingly accepted "
                                   :paatospvm       (ts Sat1)
                                   :pykala          "8"}]
                 :paivamaarat    {:anto             (ts Mon1)
                                  :julkipano        (ts Tue1)
                                  :viimeinenValitus (ts Wed1)}}
      bulletin1 {:id       "BULL-1"
                 :versions [{}
                            {:address               " Bullpen 1 "
                             :application-id        " LP-753-2021-90002 "
                             :verdictData           {:section " 11 "}
                             :primaryOperation      {:name " pientalo "}
                             :bulletinOpDescription " This is _tiny_ house. "
                             :appealPeriodStartsAt  (ts Tue2)
                             :appealPeriodEndsAt    (ts Fri2)
                             :verdictGivenAt        (ts Wed2)
                             :markup?               true
                             :propertyId            " 75341600220025 "
                             :municipality          " 753 "
                             :visits                (map ts [Mon2 Sun2 Sat2 Thu2])
                             :verdicts              [{:draft true}
                                                     {:kuntalupatunnus " FOO "
                                                      :paatokset       [paatos]}]}]}
      bulletin2 {:id       "BULL-2"
                 :versions [{}
                            {:address          "Bullpen 2"
                             :application-id   "LP-753-2021-90002"
                             :primaryOperation {:name "pientalo"}
                             :markup?          false
                             :propertyId       "75341600220025"
                             :municipality     "753"
                             :visits           (map ts [Mon2 Sun2 Sat2 Thu2])
                             :verdicts         [{:draft true}
                                                {:kuntalupatunnus "FOO"
                                                 :paatokset       [paatos]}]}]}
      paatos2   {:poytakirjat [{:paatoskoodi     "OK"
                                :paatospvm       (ts Sun1)}]
                 :paivamaarat {:julkipano        (ts Tue1)
                               :viimeinenValitus (ts Wed1)}}
      bulletin3 {:id       "BULL-3"
                 :versions [{:address          "Bullpen 3"
                             :application-id   "LP-753-2021-90002"
                             :primaryOperation {:name "pientalo"}
                             :propertyId       "75341600220025"
                             :municipality     "753"
                             :verdicts         [{:draft     false
                                                 :paatokset [paatos2]}]}]}]
  (fact "bulletin-info: version and verdictData"
    (core/bulletin-info bulletin1 "sv")
    => {:address          "Bullpen 1"
        :application-id   "LP-753-2021-90002"
        :description-html "<span>This is <em>tiny</em> house.<br/></span>"
        :end-date         (day-end Fri2)
        :given-date       Wed2
        :id               "BULL-1"
        :kuntalupatunnus  "FOO"
        :municipality     "753"
        :paatokset        [{:autopaikkoja {:enintaan "10"}
                            :katselmukset ["Inspection" "Inledande möte"]
                            :kerrosala    "200"
                            :maaraykset   ["<span><span class=\"underline\">Howdy</span><br/></span>"]
                            :poytakirjat  [{:paatoksentekija "Virka Into"
                                            :paatoskoodi     "Grudgingly accepted"
                                            :section         "8"
                                            :text-html       "<span><strong>Hello</strong> &amp; World!<br/></span>"
                                            :verdict-date    Sat1}]
                            :suunnitelmat ["Roadmap"]
                            :tyonjohtajat ["Bossman"]}]
        :property-id      "753-416-22-25"
        :section          "11"
        :start-date       (day-start Tue2)
        :verdict-date     Sat1
        :visits           [Mon2 Thu2 Sat2 Sun2]})

  (fact "Also verdict data section can be integer"
    (core/bulletin-info (assoc-in bulletin1
                                  [:versions 1 :verdictData :section] 99)
                        "sv")
    => (contains {:section "99"}))

  (fact "bulletin-info: paatos fallback"
    (core/bulletin-info bulletin2 "fi")
    => {:address          "Bullpen 2"
        :application-id   "LP-753-2021-90002"
        :description-html "Asuinpientalon rakentaminen (enintään kaksiasuntoinen erillispientalo)"
        :given-date       Mon1
        :id               "BULL-2"
        :kuntalupatunnus  "FOO"
        :municipality     "753"
        :paatokset        [{:autopaikkoja {:enintaan "10"}
                            :katselmukset ["Inspection" "Aloituskokous"]
                            :kerrosala    "200"
                            :maaraykset   ["~Howdy~"]
                            :poytakirjat  [{:paatoksentekija "Virka Into"
                                            :paatoskoodi     "Grudgingly accepted"
                                            :section         "8"
                                            :text-html       "*Hello* &amp; World!"
                                            :verdict-date    Sat1}]
                            :suunnitelmat ["Roadmap"]
                            :tyonjohtajat ["Bossman"]}]
        :property-id      "753-416-22-25"
        :section          "8"
        :start-date       (day-start Tue1)
        :end-date         (day-end Wed1)
        :verdict-date     Sat1
        :visits           [Mon2 Thu2 Sat2 Sun2]})

  (fact "Mandatory fields only"
    (core/bulletin-info bulletin3 :sv)
    => {:address          "Bullpen 3"
        :application-id   "LP-753-2021-90002"
        :description-html "Byggande av småhus (högst ett fristående småhus för två bostäder)"
        :id               "BULL-3"
        :municipality     "753"
        :paatokset        [{:poytakirjat [{:paatoskoodi     "OK"
                                           :verdict-date    Sun1}]}]
        :property-id      "753-416-22-25"
        :start-date       (day-start Tue1)
        :end-date         (day-end Wed1)
        :verdict-date     Sun1})

  (facts "detail-list"
    (let [paatos1 (-> paatos
                      ss/trimwalk
                      (update :lupamaaraykset
                              merge {:autopaikkojaEnintaan      2
                                     :autopaikkojaVahintaan     4
                                     :autopaikkojaRakennettava  6
                                     :autopaikkojaRakennettu    "  8  "
                                     :autopaikkojaKiinteistolla 10
                                     :autopaikkojaUlkopuolella  12}))
          paatos2 (-> paatos
                      ss/trimwalk
                      (update :lupamaaraykset
                              merge {:autopaikkojaEnintaan  2
                                     :autopaikkojaVahintaan 4
                                     :kerrosala             nil})
                      (update-in [:lupamaaraykset :maaraykset] concat ["Ciao"])
                      (update-in [:lupamaaraykset :vaaditutErityissuunnitelmat] concat ["Design"])
                      (update-in [:lupamaaraykset :vaaditutKatselmukset] rest)
                      (assoc-in [:lupamaaraykset :vaaditutTyonjohtajat] "Tupu, Hupu, Lupu"))
          details (fn [paatos lang markup?]
                    (->> (core/detail-list (core/->Paatos paatos
                                                          {:lang    lang
                                                           :markup? markup?}))
                         (map (fn [{:keys [loc-fn] :as item}]
                                (-> item
                                    (dissoc :loc-fn)
                                    (assoc :loc (loc-fn lang)))))))]
      (details paatos1 :fi true)
      => (just {:items ["2"] :loc "Autopaikkoja enintään" :safe? nil}
               {:items ["4"] :loc "Autopaikkoja vähintään" :safe? nil}
               {:items ["6"] :loc "Autopaikkoja rakennettava" :safe? nil}
               {:items ["8"] :loc "Autopaikkoja rakennettu" :safe? nil}
               {:items ["10"] :loc "Autopaikkoja kiinteistöllä" :safe? nil}
               {:items ["12"] :loc "Autopaikkoja ulkopuolella" :safe? nil}
               {:items ["200"] :loc "Kerrosala" :safe? nil}
               {:items ["Bossman"] :loc "Vaadittu työnjohtaja" :safe? nil}
               {:items ["Inspection" "Aloituskokous"]
                :loc   "Vaaditut katselmukset"
                :safe? nil}
               {:items ["Roadmap"] :loc "Vaadittu erityissuunnitelma" :safe? nil}
               {:items ["<span><span class=\"underline\">Howdy</span><br/></span>"]
                :loc   "Määräys"
                :safe? true}
               :in-any-order)
      (details paatos2 :sv false)
      => [{:items ["2"] :loc "Parkeringsplatser högst" :safe? nil}
          {:items ["4"] :loc "Parkeringsplatser minst" :safe? nil}
          {:items ["Tupu" "Hupu" "Lupu"] :loc "Krävda arbetsledaren" :safe? nil}
          {:items ["Inledande möte"] :loc "Granskning som krävs" :safe? nil}
          {:items ["Roadmap" "Design"] :loc "Obligatoriska specialplaner" :safe? nil}
          {:items ["~Howdy~" "Ciao"] :loc "Bestämmelser" :safe? true}]))

  (facts "bulletin-context"
    (core/bulletin-context bulletin1 {:lang "fi"})
    => (contains {:address            "Bullpen 1"
                  :application-id     "LP-753-2021-90002"
                  :description-html   "<span>This is <em>tiny</em> house.<br/></span>"
                  :end-date           (day-end Fri2)
                  :given-date         Wed2
                  :id                 "BULL-1"
                  :kuntalupatunnus    "FOO"
                  :lang               "fi"
                  :municipality       "753"
                  :paatokset          (just [(just {:autopaikkoja   {:enintaan "10"}
                                                    :katselmukset   ["Inspection" "Aloituskokous"]
                                                    :kerrosala      "200"
                                                    :lupamaaraykset (just (contains {:items ["10"] :safe? nil})
                                                                          (contains {:items ["200"] :safe? nil})
                                                                          (contains {:items ["Bossman"] :safe? nil})
                                                                          (contains {:items ["Inspection" "Aloituskokous"]
                                                                                     :safe? nil})
                                                                          (contains {:items ["Roadmap"] :safe? nil})
                                                                          (contains {:items ["<span><span class=\"underline\">Howdy</span><br/></span>"]
                                                                                     :safe? true}))
                                                    :maaraykset     ["<span><span class=\"underline\">Howdy</span><br/></span>"]
                                                    :poytakirjat    [{:paatoksentekija "Virka Into"
                                                                      :paatoskoodi     "Grudgingly accepted"
                                                                      :section         "8"
                                                                      :text-html       "<span><strong>Hello</strong> &amp; World!<br/></span>"
                                                                      :verdict-date    Sat1}]
                                                    :suunnitelmat   ["Roadmap"]
                                                    :tyonjohtajat   ["Bossman"]})])
                  :past-end-date?     true
                  :past-given-date?   true
                  :past-start-date?   true
                  :past-verdict-date? true
                  :property-id        "753-416-22-25"
                  :section            "11"
                  :start-date         (day-start Tue2)
                  :verdict-date       Sat1
                  :visit-count        4
                  :visit-limit        nil
                  :visits             [Mon2 Thu2 Sat2 Sun2]}))

  (facts "In the future, with many visits"
    (core/bulletin-context (update-in bulletin1 [:versions 1 :visits] #(take 100 (cycle %))))
    => (contains {:past-end-date?     false
                  :past-given-date?   false
                  :past-start-date?   false
                  :past-verdict-date? false
                  :visit-count        100
                  :visit-limit        30
                  :visits             [Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2
                                       Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2 Mon2
                                       Mon2 Mon2 Mon2 Mon2 Mon2 Thu2 Thu2 Thu2 Thu2 Thu2]})
    (provided (sade.core/now) => (date/timestamp Thu1)))

  (facts "Missing dates"
    (select-keys (core/bulletin-context (update-in bulletin2 [:versions 1 :verdicts 1
                                                              :paatokset 0 :paivamaarat]
                                                   dissoc :anto))
                 [:past-end-date? :past-given-date? :past-start-date? :past-verdict-date?])
    => {:past-verdict-date? true
        :past-start-date?   true
        :past-end-date?     true})


  (letfn [(body-string [{body :body}]
            (->> (ss/split-lines body)
                 ss/trimwalk
                 (ss/join " ")))]
    (facts "html-report: all information"
      (-> (core/html-report bulletin1 {:lang :fi :markup? true})
          body-string
          (in-text "LP-753-2021-90002" "Bullpen 1" "on alkanut tiistaina"
                   "25.5.2021 klo 00:00" "päättyi perjantaina" "28.5.2021 klo 23:59"
                   "antopäivä on keskiviikko" "päätöksen  §11  päivämäärien"
                   "4 kertaa" "This is <em>tiny</em> house" "Sipoo"
                   "FOO" "kirjattu lauantaina" "antopäivä on keskiviikko"
                   "§8  Grudgingly accepted" "Hello" "Virka Into" "Autopaikkoja enintään"
                   "Kerrosala" "Vaadittu työnjohtaja"
                   ["ei ole käyty kertaakaan" "katsomassa  yhden kerran"
                    "Listassa näkyy 30 ensimmäistä katseluajankohtaa."])))

    (fact "html-report: minimal, no visits"
      (-> (core/html-report bulletin3 {:lang :fi :markup? false})
          body-string
          (in-text "alkaa tiistaina" "päättyy keskiviikkona"
                   "Päätös on kirjattu sunnuntaina"
                   "ei ole käyty kertaakaan katsomassa"
                   ["§" "katsomassa  yhden kerran" "Virka Into"
                    "Listassa näkyy 30 ensimmäistä katseluajankohtaa."]))
      => nil
      (provided (sade.core/now) => (date/timestamp Mon1)))

    (fact "html-report: one visit"
      (-> bulletin1
          (assoc-in [:versions 1 :visits] [(ts Fri1)])
          (core/html-report {:lang :fi :markup? false})
          body-string
          (in-text "katsomassa  yhden kerran"
                   ["ei ole käyty kertaakaan"
                    "Listassa näkyy 30 ensimmäistä katseluajankohtaa."])))

    (fact "html-report: hundred visits"
      (-> bulletin1
          (assoc-in [:versions 1 :visits] (take 100 (cycle [(ts Fri1)])))
          (core/html-report {:lang :fi :markup? false})
          body-string
          (in-text "100 kertaa" "Listassa näkyy 30 ensimmäistä katseluajankohtaa."
                   ["ei ole käyty kertaakaan" "katsomassa  yhden kerran"])))))

(against-background
  [(clojure.java.io/resource "templates/bulletin-report/foo.fi.djhtml") => true
   (clojure.java.io/resource "templates/bulletin-report/foo.sv.djhtml") => true
   (clojure.java.io/resource "templates/bulletin-report/foo.cn.djhtml") => false
   (selmer.parser/render-file "foo.fi.djhtml" anything anything) => "suomi.html"
   (selmer.parser/render-file "foo.sv.djhtml" anything anything) => "svenska.html"]
  (facts "render-template"
    (page/render-template :foo nil) => "suomi.html"
    (page/render-template :foo {}) => "suomi.html"
    (page/render-template :foo {:lang :fi}) => "suomi.html"
    (page/render-template :foo {:lang "fi"}) => "suomi.html"
    (page/render-template :foo {:lang :sv}) => "svenska.html"
    (page/render-template :foo {:lang "sv"}) => "svenska.html"
    (page/render-template :foo {:lang :cn}) => "suomi.html"))

(facts "string->html"
  (page/string->html nil) => nil
  (page/string->html "" true) => nil
  (page/string->html "hello") => "hello"
  (page/string->html "hello" true) => "<span>hello<br/></span>"
  (page/string->html "*hello* & _world_") => "*hello* &amp; _world_"
  (page/string->html "*hello* & _world_" false) => "*hello* &amp; _world_"
  (page/string->html "*hello* & _world_" true)
  => "<span><strong>hello</strong> &amp; <em>world</em><br/></span>")
