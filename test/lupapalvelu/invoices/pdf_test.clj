(ns lupapalvelu.invoices.pdf-test
  (:require [lupapalvelu.invoices.pdf :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]))

(testable-privates lupapalvelu.invoices.pdf
                   void-anywhere? worktime)

(facts "void-anywhere?"
  (void-anywhere? [nil]) => true
  (void-anywhere? [""]) => true
  (void-anywhere? [" "]) => true
  (void-anywhere? []) => false
  (void-anywhere? {}) => false
  (void-anywhere? #{}) => false
  (void-anywhere? nil) => false
  (void-anywhere? ["hello" nil "world"]) => true
  (void-anywhere? ["foo" ["bar" ["one" " " "two"] "baz"]]) => true)

(facts "smart-join"
  (smart-join [" hello "]) => "hello"
  (smart-join [" hello " :space " world " :comma]) => "hello world"
  (smart-join [:colon " hello " :space " world " :comma]) => "hello world"
  (smart-join [" hello " :space :comma :newline" world " :comma]) => "hello world"
  (smart-join [" hello " :space ["one" :comma ["two" nil] :comma]]) => "hello"
  (smart-join [" hello " :space ["one" :comma [:colon "two" :comma] :newline "world"]])
  => "hello one, two, world"
  (smart-join [" hello " :space :ignored :ignored :ignored "world"])
  => "hello world")

(facts "price->string"
  (price->string nil) => (throws AssertionError)
  (price->string "100") => (throws AssertionError)
  (price->string "100.0") => (throws AssertionError)
  (price->string 100) => "1,00"
  (price->string 12345) => "123,45"
  (price->string -12345) => "-123,45"
  (price->string -12300) => "-123,00"
  (price->string 12300) => "123,00"
  (price->string 1) => "0,01"
  (price->string 0) => "0,00")

(facts "pos-or-nil"
  (pos-or-nil nil) => nil
  (pos-or-nil "") => nil
  (pos-or-nil "hello") => nil
  (pos-or-nil :foo) => nil
  (pos-or-nil {}) => nil
  (pos-or-nil -1) => nil
  (pos-or-nil -1.23) => nil
  (pos-or-nil -1.23) => nil
  (pos-or-nil 0) => nil
  (pos-or-nil 0.0) => nil
  (pos-or-nil 1.23) => 1.23
  (pos-or-nil 1) => 1)

(defn worktime-options [work-start work-end non-billable]
  {:lang      "fi"
   :invoice   {:work-start-ms (date/timestamp work-start)
               :work-end-ms   (date/timestamp work-end)}
   :catalogue {:no-billing-periods (some->> non-billable
                                            (map-indexed (fn [i [a b]]
                                                           [(str i) {:start a
                                                                     :end   b}]))
                                            (into {}))}})

(facts "worktime"
  (fact "Work whole week"
    (worktime (worktime-options "09.12.2019" "15.12.2019" nil))
    => {:days     "9.12.2019 \u2013 15.12.2019"
        :billable "7 laskutettavaa päivää"})
  (fact "Work whole week. Weekends free."
    (worktime (worktime-options "09.12.2019 22:05" "15.12.2019 9.15" [["14.12.2019" "15.12.2019"]]))
    => {:days     "9.12.2019 \u2013 15.12.2019"
        :billable "5 laskutettavaa päivää"})
  (fact "Work whole week. Only one billable day"
    (worktime (worktime-options "09.12.2019" "15.12.2019" [["14.12.2019" "15.12.2019"]
                                                           ["9.12.2019" "12.12.2019"]]))
    => {:days     "9.12.2019 \u2013 15.12.2019"
        :billable "Yksi laskutettava päivä"})
  (fact "Work whole week. No billable days"
    (worktime (worktime-options "09.12.2019" "15.12.2019" [["1.12.2019" "31.12.2019"]]))
    => {:days     "9.12.2019 \u2013 15.12.2019"
        :billable "Ei laskutettavia päiviä"})
  (fact "Work one day. Billable."
    (worktime (worktime-options "09.12.2019 23.10" "9.12.2019 8:45" [["14.12.2019" "15.12.2019"]]))
    => {:days     "9.12.2019"
        :billable "Yksi laskutettava päivä"})
  (fact "Work one day. Free."
    (worktime (worktime-options "09.12.2019" "9.12.2019" [["1.12.2019" "15.12.2019"]]))
    => {:days     "9.12.2019"
        :billable "Ei laskutettavia päiviä"})
  (fact "No start date"
    (worktime (worktime-options nil "15.12.2019" nil)) => nil)
  (fact "No end date"
    (worktime (worktime-options "15.12.2019" nil nil)) => nil)
  (fact "No dates"
    (worktime (worktime-options nil nil nil)) => nil)
  (fact "Start date after end"
    (worktime (worktime-options "15.12.2019" "9.12.2019" nil)) => nil))

(let [start-ts (date/timestamp "15.6.2022 12.35")
      end-ts   (date/timestamp "15.6.2022 2:35")]
  (facts "Workdays"
    (> start-ts end-ts) => true
    (workdays {} {}) => nil
    (workdays {:work-start-ms 1} {}) => nil
    (workdays {:work-end-ms 1} {}) => nil
    (workdays {:work-start-ms start-ts
               :work-end-ms   end-ts}
              {})
    => {:days 1 :free-days 0 :billable-days 1}
    (workdays {:work-start-ms start-ts
               :work-end-ms   end-ts}
              {:no-billing-periods {:one {:start "15.6.2022" :end "15.06.2022"}}})
    => {:days 1 :free-days 1 :billable-days 0}
    (workdays {:work-start-ms (date/timestamp "16.6.2022")
               :work-end-ms   (date/timestamp "15.6.2022")}
              {})
   => nil))

(defn make-person [first-name last-name phone email street zip po country]
  {:henkilotiedot {:etunimi  first-name
                   :sukunimi last-name}
   :osoite        {:katu                 street
                   :postinumero          zip
                   :postitoimipaikannimi po
                   :maa                  country}
   :yhteystiedot  {:puhelin phone
                   :email   email}})

(defn make-company [company-name y first-name last-name phone email street zip po country
                    netbilling ovt relay]
  {:yritysnimi           company-name
   :liikeJaYhteisoTunnus y
   :osoite               {:katu                 street
                          :postinumero          zip
                          :postitoimipaikannimi po
                          :maa                  country}
   :yhteyshenkilo        {:henkilotiedot {:etunimi  first-name
                                          :sukunimi last-name}
                          :yhteystiedot  {:puhelin phone
                                          :email   email}}
   :verkkolaskutustieto  {:verkkolaskuTunnus netbilling
                          :ovtTunnus         ovt
                          :valittajaTunnus   relay}})

(defn make-party [selected person company reference subtype]
  {:schema-info {:subtype subtype}
   :data        {:_selected  selected
                 :henkilo    person
                 :yritys     company
                 :laskuviite reference}})

(facts "payer"
  (fact "Person: fully filled"
    (payer "fi" {:documents [(make-party "henkilo"
                                         (make-person " Piper " " Payer " " 12345678 " " piper@example.com "
                                                      " Street 123  " " 89000 " " Town " "USA")
                                         nil
                                         "invoice"
                                         "maksaja")]})
    => {:payer-name      "Piper Payer"
        :payer-address   "Street 123\n89000 Town\nYhdysvallat (USA)"
        :payer-contact   "12345678, piper@example.com"
        :payer-reference "invoice"})
  (facts "Person: partly filled"
    (payer "fi" {:documents [(make-party "henkilo"
                                         (make-person nil "Payer" nil "piper@example.com"
                                                      nil "89000" nil nil)
                                         nil
                                         nil
                                         "maksaja")]})
    => {:payer-name    "Payer"
        :payer-address "89000"
        :payer-contact "piper@example.com"}
    (payer "fi" {:documents [(make-party "henkilo"
                                         (make-person "Piper" nil "12345678" nil
                                                      "Street 123" nil "Town" "USA")
                                         nil
                                         "invoice"
                                         "maksaja")]})
    => {:payer-name      "Piper"
        :payer-address   "Street 123\nTown\nYhdysvallat (USA)"
        :payer-contact   "12345678"
        :payer-reference "invoice"}
    (payer "fi" {:documents [(make-party "henkilo"
                                         (make-person "" "" "" ""
                                                      "" "" "" "USA")
                                         nil
                                         ""
                                         "maksaja")]})
    => {:payer-address "Yhdysvallat (USA)"})
  (fact "Person from Finland"
    (payer "fi" {:documents [(make-party "henkilo"
                                         (make-person "Piper" "Payer" "12345678" "piper@example.com"
                                                      "Street 123" "89000" "Town" "FIN")
                                         nil
                                         "invoice"
                                         "maksaja")]})
    => {:payer-name      "Piper Payer"
        :payer-address   "Street 123\n89000 Town"
        :payer-contact   "12345678, piper@example.com"
        :payer-reference "invoice"})
  (fact "Wrong subtype"
    (payer "fi" {:documents [(make-party "henkilo"
                                         (make-person "Piper" "Payer" "12345678" "piper@example.com"
                                                      "Street 123" "89000" "Town" "USA")
                                         nil
                                         "invoice"
                                         "bad")]})
    => {})
  (fact "Person not selected"
    (payer "fi" {:documents [(make-party "yritys"
                                         (make-person "Piper" "Payer" "12345678" "piper@example.com"
                                                      "Street 123" "89000" "Town" "FIN")
                                         {}
                                         "invoice"
                                         "maksaja")]})
    => {:payer-reference "invoice"})
  (fact "Company: fully filled"
    (payer "fi" {:documents [(make-party "yritys"
                                         nil
                                         (make-company " Company Ltd " " 1234-5 " " Piper " " Payer " " 12345678 " " piper@example.com "
                                                       " Street 123 " " 89000 " " Town " "USA"
                                                       "  netmoney " " my-ovt " "BAWCFI22")
                                         "invoice"
                                         "maksaja")]})
    => {:payer-name       "Company Ltd, Piper Payer"
        :payer-y          "1234-5"
        :payer-address    "Street 123\n89000 Town\nYhdysvallat (USA)"
        :payer-contact    "12345678, piper@example.com"
        :payer-netbilling "Verkkolaskuosoite: netmoney\nOVT-tunnus: my-ovt\nVälittäjä: Basware Oyj (BAWCFI22)"
        :payer-reference  "invoice"})
  (facts "Company: partly filled"
    (payer "fi" {:documents [(make-party "yritys"
                                         nil
                                         (make-company " " "1234-5"" Piper " nil " 12345678 " "  "
                                                       " Street 123 " "  " " Town " ""
                                                       "  netmoney " "  " "BAWCFI22")
                                         "invoice"
                                         "maksaja")]})
    => {:payer-name       "Piper"
        :payer-y          "1234-5"
        :payer-address    "Street 123\nTown"
        :payer-contact    "12345678"
        :payer-netbilling "Verkkolaskuosoite: netmoney\nVälittäjä: Basware Oyj (BAWCFI22)"
        :payer-reference  "invoice"}

    (payer "fi" {:documents [(make-party "yritys"
                                         nil
                                         (make-company " Company Ltd " nil "  " nil "  " "  "
                                                       "  " nil  " Town " "FIN"
                                                       "  " " my-ovt " nil)
                                         ""
                                         "maksaja")]})
    => {:payer-name       "Company Ltd"
        :payer-address    "Town"
        :payer-netbilling "OVT-tunnus: my-ovt"}

    (payer "fi" {:documents [(make-party "yritys"
                                         nil
                                         (make-company "  " " foo " "  " " Payer " nil " piper@example.com "
                                                       "  " " 89000 " "  " "USA"
                                                       "  " " " nil)
                                         "  invoice  "
                                         "maksaja")]})
    => {:payer-name      "Payer"
        :payer-y         "foo"
        :payer-address   "89000\nYhdysvallat (USA)"
        :payer-contact   "piper@example.com"
        :payer-reference "invoice"})

  (fact "Company: empty document"
    (payer "fi" {:documents [(make-party "yritys"
                                         nil
                                         (make-company "  " " " "  " " " "  " "  "
                                                       " " " " " " ""
                                                       " " "  " "")
                                         nil
                                         "maksaja")]})
    => {}))

(defn make-invoice-row [code text n unit unit-price discount comment]
  (assoc-in {:code           code       :text             text
             :units          n          :unit             unit
             :price-per-unit unit-price :discount-percent (or discount 0)
             :comment        comment}
            [:sums :with-discount :minor]
            (* n unit-price (- 100 (or discount 0)))))

(defn add-vat [row vat-percentage vat-amount-minor]
  (assoc row :vat-percentage vat-percentage :vat-amount-minor vat-amount-minor))

(facts "invoice-operation"
  (fact "One row, no discounts"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)]})
    => {:discounts?            false
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}]
        :operation-id          "pientalo"
        :operation-total-price "20,00"})

  (fact "Cents are rounded correctly (TT-19935)"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(assoc-in (make-invoice-row "R1" "Round" 3 "kpl" 1.15 nil nil)
                                                     [:sums :with-discount :minor] 345)]})
    => {:discounts?            false
        :rows                  [{:discount-percent 0
                                 :text             "R1 Round"
                                 :price-per-unit   "1,15"
                                 :unit             "kpl"
                                 :units            3
                                 :total-price      345}]
        :operation-id          "pientalo"
        :operation-total-price "3,45"})

  (fact "Three rows, but only one included (others zero price or units)"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "I1" "Ignore 1" 0 "kpl" 10 nil nil)
                                           (make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)
                                           (make-invoice-row "I2" "Ignore 2" 2 "kpl" 0 nil nil)]})
    => {:discounts?            false
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}]
        :operation-id          "pientalo"
        :operation-total-price "20,00"})

  (fact "Two rows, one discount without comment"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)
                                           (make-invoice-row "C2" "Two" 4 "pv" 5  25 "  ")]})
    => {:discounts?            true
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}
                                {:text             "C2 Two"
                                 :price-per-unit   "5,00"
                                 :unit             "pv"
                                 :units            4
                                 :discount-percent 25
                                 :total-price      1500}]
        :operation-id          "pientalo"
        :operation-total-price "35,00"})

  (fact "Two rows, one discount with comment"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)
                                           (make-invoice-row "C2" "Two" 4 "pv" 5  25 "Promotion")]})
    => {:discounts?            true
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}
                                {:text             "C2 Two"
                                 :comment          "Promotion"
                                 :price-per-unit   "5,00"
                                 :unit             "pv"
                                 :units            4
                                 :discount-percent 25
                                 :total-price      1500}]
        :operation-id          "pientalo"
        :operation-total-price "35,00"
        :discount-info         "Promotion"})

  (fact "Three rows, two discounts, one with comment"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)
                                           (make-invoice-row "C2" "Two" 4 "pv" 5  25 "  Promotion  ")
                                           (make-invoice-row "" "  Freebie " 1 "m3" 20  100 "  ")]})
    => {:discounts?            true
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}
                                {:text             "C2 Two"
                                 :comment          "Promotion"
                                 :price-per-unit   "5,00"
                                 :unit             "pv"
                                 :units            4
                                 :discount-percent 25
                                 :discount-index   1
                                 :total-price      1500}
                                {:text             "Freebie"
                                 :price-per-unit   "20,00"
                                 :unit             "m3"
                                 :units            1
                                 :discount-percent 100
                                 :total-price      0}]
        :operation-id          "pientalo"
        :operation-total-price "35,00"
        :discount-info         [{:discount-index 1 :comment "Promotion"}]})

  (fact "Three rows, two discounts, both with comments"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)
                                           (make-invoice-row "C2" "Two" 4 "pv" 5  25 "  Promotion  ")
                                           (make-invoice-row "" "  Freebie " 1 "m3" 20  100 "Free stuff!")]})
    => {:discounts?            true
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}
                                {:text             "C2 Two"
                                 :comment          "Promotion"
                                 :price-per-unit   "5,00"
                                 :unit             "pv"
                                 :units            4
                                 :discount-percent 25
                                 :discount-index   1
                                 :total-price      1500}
                                {:text             "Freebie"
                                 :price-per-unit   "20,00"
                                 :unit             "m3"
                                 :units            1
                                 :discount-index   2
                                 :comment          "Free stuff!"
                                 :discount-percent 100
                                 :total-price      0}]
        :operation-id          "pientalo"
        :operation-total-price "35,00"
        :discount-info         [{:discount-index 1 :comment "Promotion"}
                                {:discount-index 2 :comment "Free stuff!"}]})

  (fact "Three rows, one discount with comment, one vat"
    (invoice-operation :fi {:operation-id "pientalo"
                            :invoice-rows [(make-invoice-row "C1" "One" 2 "kpl" 10 nil nil)
                                           (make-invoice-row "C2" "Two" 4 "pv" 5  25 "  Promotion  ")
                                           (add-vat (make-invoice-row "" "  VAT " 1 "m3" 20 nil "Ignored")
                                                    24 387)]})
    => {:discounts?            true
        :vat?                  true
        :rows                  [{:discount-percent 0
                                 :text             "C1 One"
                                 :price-per-unit   "10,00"
                                 :unit             "kpl"
                                 :units            2
                                 :total-price      2000}
                                {:text             "C2 Two"
                                 :comment          "Promotion"
                                 :price-per-unit   "5,00"
                                 :unit             "pv"
                                 :units            4
                                 :discount-percent 25
                                 :total-price      1500}
                                {:text             "VAT"
                                 :comment          "Ignored"
                                 :price-per-unit   "20,00"
                                 :unit             "m3"
                                 :units            1
                                 :discount-percent 0
                                 :total-price      2000
                                 :total-taxfree    1613
                                 :vat              "24 % (3,87 €)"
                                 :vat-amount-minor 387}]
        :operation-id          "pientalo"
        :operation-total-price "55,00"
        :discount-info         "Promotion"}))
