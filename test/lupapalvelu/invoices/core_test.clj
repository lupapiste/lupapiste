(ns lupapalvelu.invoices.core-test
  (:require [lupapalvelu.invoices.core :as invoices]
            [lupapalvelu.invoices.transfer-batch :as tb]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.price-catalogues :as cat]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]
            [sade.util :as util]))

(testable-privates lupapalvelu.invoices.core
                   row-field-updates invoice-field-updates)

(facts "calculate-row-total"
  (invoices/calculate-row-total nil) => 0
  (invoices/calculate-row-total {}) => 0
  (invoices/calculate-row-total {:price-per-unit 10}) => 0
  (invoices/calculate-row-total {:price-per-unit 10
                                 :units          0}) => 0
  (invoices/calculate-row-total {:units 10}) => 0
  (invoices/calculate-row-total {:price-per-unit 0
                                 :units          10}) => 0
  (invoices/calculate-row-total {:price-per-unit   10
                                 :units            1
                                 :discount-percent 100}) => 0
  (fact "sums invoice-row with 1 unit of 10 to ten"
    (invoices/calculate-row-total {:price-per-unit 10
                                   :units          1}) => 1000
    (invoices/calculate-row-total {:price-per-unit   10
                                   :units            1
                                   :discount-percent 0}) => 1000)
  (fact "sums invoice-row with 2 unit of 10 to 20"
    (invoices/calculate-row-total {:price-per-unit   10
                                   :units            2
                                   :discount-percent 0}) => 2000)
  (fact "sums invoice-row with 2 unit of 10 to 20 with discount of 32% to 13.60"
    (invoices/calculate-row-total {:price-per-unit   10
                                   :units            2
                                   :discount-percent 32}) => 1360))

(defn make-row
  ([ppu units discount]
   (let [row (util/strip-nils {:price-per-unit   ppu
                               :units            units
                               :discount-percent discount})]
     (assoc row :total-cents (invoices/calculate-row-total row))))
  ([ppu units]
   (make-row ppu units nil)))

(defn make-invoice [& row-datas]
  (->> row-datas
       (map (partial apply make-row))
       (hash-map :rows)))

(facts "enrich-sum-total"
  (fact "sums invoice with rows worth 10 and 10"
    (let [test-invoice (make-invoice [10 1 0] [10 1])]
      (invoices/enrich-sum-total test-invoice)
      => (contains {:sum-total-cents 2000})))
  (fact "sums invoice without rows"
    (:sum-total-cents (invoices/enrich-sum-total {:rows []})) => 0
    (:sum-total-cents (invoices/enrich-sum-total {})) => 0)
  (fact "sums invoice with rows worth 10 and 2 * 10 without discounts as 30"
    (let [test-invoice (make-invoice [10 1] [10 2])]
      (:sum-total-cents (invoices/enrich-sum-total test-invoice)) => 3000))
  (fact "sums invoice with rows worth 10 and 2 * 10  and 10 without discounts as 40"
    (let [test-invoice (make-invoice [10 1] [10 2] [10 1])]
      (:sum-total-cents (invoices/enrich-sum-total test-invoice)) => 4000))
  (fact "sums invoice with rows worth 10 and 2 * 10  and 2* 10 with discount 20"
    (let [test-invoice (make-invoice [10 1] [10 2 20] [10 1])]
      (:sum-total-cents (invoices/enrich-sum-total test-invoice)) => 3600)))

(defn application-with-payer [selected]
  {:documents [{:schema-info {:name    "maksaja"
                              :version 1
                              :type    "party"
                              :subtype :maksaja}
                :data        {:_selected  {:value selected}
                              :henkilo    {:userId        {:value "777777777777777777000020"}
                                           :henkilotiedot {:etunimi           {:value "Pena"}
                                                           :sukunimi          {:value "Panaani"}
                                                           :hetu              {:value "010203-040A"}
                                                           :turvakieltoKytkin {:value false}}
                                           :osoite        {:katu                 {:value "Paapankuja 12"}
                                                           :postinumero          {:value "92620"}
                                                           :postitoimipaikannimi {:value "Piippola"}
                                                           :maa                  {:value "FIN"}}}
                              :yritys     {:yritysnimi           {:value "Feikki Oy"}
                                           :liikeJaYhteisoTunnus {:value "0813000-2"}
                                           :osoite               {:katu                 {:value "Lumekatu 5"}
                                                                  :postinumero          {:value "92620"}
                                                                  :postitoimipaikannimi {:value "Huijala"}
                                                                  :maa                  {:value "FIN"}}
                                           :verkkolaskutustieto  {:ovtTunnus       {:value "003708130002"}
                                                                  :valittajaTunnus {:value "OPERATOR"}}
                                           :yhteyshenkilo        {:henkilotiedot {:etunimi  {:value "Sonja"}
                                                                                  :sukunimi {:value "Sibbo"}}}}
                              :laskuviite {:value "laskuviite"}}}

               {:schema-info {:name    "maksaja"
                              :type    "party"
                              :subtype :maksaja}
                :data        {:_selected {:value "henkilo"}
                              :henkilo   {:userId        {:value "777777777777777777000020"}
                                          :henkilotiedot {:etunimi  {:value "YLIMAARAINEN"}
                                                          :sukunimi {:value "MAKSAJA"}}}}}]})

(defn make-vat-row
  ([total-cents alv]
   (cond-> {:total-cents       total-cents}
     alv
     (assoc-in [:product-constants :alv] alv)))
  ([total-cents]
   (make-vat-row total-cents nil)))

(defn make-vat-invoice [& row-datas]
  (->> row-datas
       (map (partial apply make-vat-row))
       (hash-map :rows)
       invoices/enrich-vat-info
       invoices/enrich-sum-total))

(defn no-vat [invoice]
  (fact "No VAT"
    (:sum-vat-info (invoices/enrich-vat-info invoice)) => nil))

(facts "VAT"
  (facts "No VAT info"
    (no-vat nil)
    (no-vat {})
    (no-vat {:rows []})
    (no-vat (make-vat-invoice [100]
                              [100 ""]
                              [100 "0%"]
                              [100 "-1%"]
                              [100 "101%"]
                              [0 "25%"]
                              [nil "25%"]
                              [100 "bad"]
                              [-100 "25%"]
                              [-100 "25.0%"])))
  (facts "With VAT info"
    (make-vat-invoice [120 "25%"])
    => {:rows            [{:product-constants {:alv "25%"}
                           :total-cents       120
                           :vat-info          {:taxfree-cents  96
                                               :vat-cents      24
                                               :vat-percentage 25}}]
        :sum-total-cents 120
        :sum-vat-info    {:taxfree-cents 96
                          :vat-cents     24}}
    (make-vat-invoice [100 " % 33 %"])
    => {:rows            [{:product-constants {:alv " % 33 %"}
                           :total-cents       100
                           :vat-info          {:taxfree-cents  75
                                               :vat-cents      25
                                               :vat-percentage 33}}]
        :sum-total-cents 100
        :sum-vat-info    {:taxfree-cents 75
                          :vat-cents     25}}
    (make-vat-invoice [100 "10%"]
                      [200 "24 %"]
                      [50 nil])
    => {:rows            [{:product-constants {:alv "10%"}
                           :total-cents       100
                           :vat-info          {:taxfree-cents  91
                                               :vat-cents      9
                                               :vat-percentage 10}}
                          {:product-constants {:alv "24 %"}
                           :total-cents       200
                           :vat-info          {:taxfree-cents  161
                                               :vat-cents      39
                                               :vat-percentage 24}}
                          {:total-cents 50}]
        :sum-total-cents 350
        :sum-vat-info    {:taxfree-cents 302
                          :vat-cents     48}}
    (make-vat-invoice [100 "0%"]
                      [200 "bad24 %"]
                      [50 nil])
    => {:rows            [{:product-constants {:alv "0%"} :total-cents 100}
                          {:product-constants {:alv "bad24 %"} :total-cents 200}
                          {:total-cents 50}]
        :sum-total-cents 350}

    (make-vat-invoice [100 "%"] [200 ""] [50 nil]
                      [300 "20"] [200 ""] [0 "%50"]
                      [123 "15"] [55 "5%"] [212 "%12"]
                      [90 "14"] [-100 "25%"] [103 "%22"])
    => {:rows            [{:product-constants {:alv "%"}
                           :total-cents       100}
                          {:product-constants {:alv ""}
                           :total-cents       200}
                          {:total-cents 50}
                          {:product-constants {:alv "20"}
                           :total-cents       300
                           :vat-info          {:taxfree-cents  250
                                               :vat-cents      50
                                               :vat-percentage 20}}
                          {:product-constants {:alv ""}
                           :total-cents       200}
                          {:product-constants {:alv "%50"}
                           :total-cents       0}
                          {:product-constants {:alv "15"}
                           :total-cents       123
                           :vat-info          {:taxfree-cents  107
                                               :vat-cents      16
                                               :vat-percentage 15}}
                          {:product-constants {:alv "5%"}
                           :total-cents       55
                           :vat-info          {:taxfree-cents  52
                                               :vat-cents      3
                                               :vat-percentage 5}}
                          {:product-constants {:alv "%12"}
                           :total-cents       212
                           :vat-info          {:taxfree-cents  189
                                               :vat-cents      23
                                               :vat-percentage 12}}
                          {:product-constants {:alv "14"}
                           :total-cents       90
                           :vat-info          {:taxfree-cents  79
                                               :vat-cents      11
                                               :vat-percentage 14}}
                          {:product-constants {:alv "25%"}
                           :total-cents       -100}
                          {:product-constants {:alv "%22"}
                           :total-cents       103
                           :vat-info          {:taxfree-cents  84
                                               :vat-cents      19
                                               :vat-percentage 22}}]
        :sum-total-cents 1333 ;; (+ 100 200 50 300 200 0 123 55 212 90 -100 103)
        :sum-vat-info    {:taxfree-cents 1211
                          :vat-cents     122 ;; (+ 50 16 3 23 11 19)
                          }}))

(def runeberg (date/timestamp "5.2.2020"))
(def kalevala (date/timestamp "28.2.2020"))
(def good     (date/timestamp "10.4.2020"))
(def mayday   (date/timestamp "1.5.2020"))

(facts "enrich-workdays"
  (fact "No dates"
    (invoices/enrich-workdays {:application {:permitType "YA"}
                               :created     12345}
                              {:state "draft"})
    => {:state "draft"})

  (fact "Dates from the application"
    (invoices/enrich-workdays {:application {:permitType "YA"
                                             :created    12345
                                             :started    good
                                             :closed     mayday}}
                              {:state "draft"})
    => {:state         "draft"
        :work-end-ms   mayday
        :work-start-ms good
        :workdays      {:billable-days 22 :days 22 :free-days 0}})

  (fact "Not an YA application"
    (invoices/enrich-workdays {:application {:permitType "R"
                                             :created    12345
                                             :started    good
                                             :closed     mayday}}
                              {:state "draft"})
    => {:state "draft"})

  (fact "Invoice dates override application dates"
    (invoices/enrich-workdays {:application {:permitType "YA"
                                             :created    12345
                                             :started    runeberg
                                             :closed     mayday}}
                              {:state         "draft"
                               :work-start-ms runeberg})
    => {:state         "draft"
        :work-start-ms runeberg}
    (invoices/enrich-workdays {:application {:permitType "YA"
                                             :created    12345
                                             :started    runeberg
                                             :closed     mayday}}
                              {:state         "draft"
                               :work-start-ms runeberg
                               :work-end-ms   kalevala})
    => {:state         "draft"
        :work-start-ms runeberg
        :work-end-ms   kalevala
        :workdays      {:billable-days 24 :days 24 :free-days 0}})

  (fact "Start and end can be the same day"
    (invoices/enrich-workdays {:application {:permitType "YA"
                                             :created    12345
                                             :started    good
                                             :closed     good}}
                              {:state "draft"})
    => {:state         "draft"
        :work-start-ms good
        :work-end-ms   good
        :workdays      {:billable-days 1 :days 1 :free-days 0}})

  (fact "Bad dates"
    (invoices/enrich-workdays {:application {:permitType "YA"
                                             :created    12345
                                             :started    good
                                             :closed     kalevala}}
                              {:state "draft"})
    => {:state         "draft"
        :work-start-ms good
        :work-end-ms   kalevala})

  (against-background (cat/application-price-catalogues anything anything) => []))

(defn make-config [enabled? numbers codes?]
  {:invoicing-config            {:backend-id? (boolean enabled?)}
   :invoicing-backend-id-config (util/strip-nils
                                  {:numbers numbers
                                   :codes   (when codes?
                                              (for [code ["AA" "BB" "CC" "DD"]]
                                                {:code code
                                                 :text (str "Backend code is " code)}))})})


(facts "row-field-updates"
  (fact "default"
    (row-field-updates nil nil :foo "bar") => {:row-in {:foo "bar"}})
  (facts "price-per-unit. Editable if custom row or price range is defined"
    (row-field-updates nil nil :price-per-unit "10")
    => {:row-error {:price-per-unit :error.read-only}}
    (row-field-updates nil nil :price-per-unit "10bad")
    => (throws)
    (row-field-updates nil {:custom? true} :price-per-unit "10")
    => {:row-in {:price-per-unit 10 :total-cents 0}}
    (row-field-updates nil {:custom? true} :price-per-unit "")
    => {:row-in  {:total-cents 0}
        :row-out [:price-per-unit]}
    (row-field-updates nil {:custom? true :units 1}
                       :price-per-unit "10")
    => {:row-in {:price-per-unit 10 :total-cents 1000}}
    (row-field-updates nil {:min-unit-price 10}
                       :price-per-unit "10")
    => {:row-in {:price-per-unit 10 :total-cents 0}}
    (row-field-updates nil {:min-unit-price 10}
                       :price-per-unit "9")
    => {:row-error {:price-per-unit [:invoice.error.min-unit-price "10,00 €"]}}
    (row-field-updates nil {:max-unit-price 20}
                       :price-per-unit "20")
    => {:row-in {:price-per-unit 20 :total-cents 0}}
    (row-field-updates nil {:max-unit-price 20}
                       :price-per-unit "22")
    => {:row-error {:price-per-unit [:invoice.error.max-unit-price "20,00 €"]}}
    (row-field-updates nil {:max-unit-price 20
                            :units          3
                            :min-unit-price 10}
                       :price-per-unit "15")
    => {:row-in {:price-per-unit 15 :total-cents 4500}})

  (facts "unit and text are only allowed for custom rows"
    (row-field-updates nil nil :unit "m2")
    => {:row-error {:unit :error.read-only}}
    (row-field-updates nil {:custom? true} :unit "m2")
    => {:row-in {:unit "m2"}}
    (row-field-updates nil nil :text "Hello")
    => {:row-error {:text :error.read-only}}
    (row-field-updates nil {:custom? true} :text "World")
    => {:row-in {:text "World"}})

  (facts "units. Granularity in two decimals"
    (row-field-updates nil nil :units "2")
    => {:row-in {:total-cents 0 :units 2}}
    (row-field-updates nil {:price-per-unit 12} :units "")
    => {:row-in {:total-cents 0} :row-out [:units]}
    (row-field-updates nil {:price-per-unit 12} :units "5")
    => {:row-in {:total-cents 6000 :units 5}}
    (row-field-updates nil {} :units " 5,456 ")
    => {:row-in {:total-cents 0, :units 5.46}}
    (row-field-updates nil {} :units " -5.456 ")
    => {:row-in {:total-cents 0, :units -5.46}}
    (row-field-updates nil {} :units " 5.99 ")
    => {:row-in {:total-cents 0, :units 5.99}}
    (row-field-updates nil {} :units " 5,9 ")
    => {:row-in {:total-cents 0, :units 5.9}})

  (facts "discount-percent. Granularity in two decimals"
    (row-field-updates nil nil :discount-percent "% 20 %")
    => {:row-in {:discount-percent 20 :total-cents 0}}
    (row-field-updates nil nil :discount-percent "  ")
    => {:row-in  {:total-cents 0}
        :row-out [:discount-percent]}
    (row-field-updates nil {:units 1 :price-per-unit 150}
                       :discount-percent "12.565")
    => {:row-in {:discount-percent 12.57 :total-cents 13114}}
    (row-field-updates nil nil :discount-percent "bad")
    => (throws)
    (row-field-updates nil nil :discount-percent "-1")
    => {:row-error {:discount-percent :invoice.error.percentage}}
    (row-field-updates nil nil :discount-percent "100.01")
    => {:row-error {:discount-percent :invoice.error.percentage}}))

(defn failed! [k]
  (throws (re-pattern (name k))))

(facts "invoice-field-updates"

  (facts "default"
    (invoice-field-updates nil nil :foo "bar")
    => {:in {:foo "bar"}}
    (invoice-field-updates nil {:application-payer? true} :foo "bar")
    => {:in {:foo "bar"}}
    (invoice-field-updates nil {:application-payer? true} :phone "123")
    => (failed! :error.read-only))

  (facts "backend-code"
    (let [cmd {:organization (delay (make-config true 8 true))}]
      (invoice-field-updates cmd {:backend-id "already-id"} :backend-code "AA")
      => (failed! :error.backend-id-already-defined)
      (invoice-field-updates cmd nil :backend-code "  ")
      => {:out [:backend-code]}
      (invoice-field-updates cmd nil :backend-code nil)
      => {:out [:backend-code]}
      (invoice-field-updates cmd {} :backend-code "BAD")
      => (failed! :error.unknown-backend-code)
      (invoice-field-updates {} {} :backend-code "BAD")
      =>  (failed! :error.unknown-backend-code)
      (invoice-field-updates cmd {} :backend-code "BB")
      => {:in {:backend-code "BB"}}))

  (against-background
    [(tb/transfer-batch-for-invoice anything "good") => "good"
     (tb/transfer-batch-for-invoice anything "bad") => nil]
    (facts "transfer-batch-id"
      (doseq [state [nil "draft" "checked" "transferred"]]
        (invoice-field-updates nil (util/strip-nils {:state state})
                               :transfer-batch-id "good")
        => (failed! :error.invoice.illegal-state))
      (invoice-field-updates nil {:state "confirmed"} :transfer-batch-id "good")
      => {:in {:transfer-batch-id "good"}}
      (invoice-field-updates nil {:state "confirmed"} :transfer-batch-id "bad")
      => (failed! :error.not-found)))

  (facts "application-payer?"
    (invoice-field-updates {:application (application-with-payer "henkilo")}
                           nil :application-payer? true)
    => {:in {:application-payer? true
             :billing-reference  "laskuviite"
             :city               "Piippola"
             :company-id         ""
             :country            "FIN"
             :email              ""
             :entity-address     "Paapankuja 12"
             :entity-name        ""
             :first-name         "Pena"
             :last-name          "Panaani"
             :netbill            ""
             :operator           ""
             :ovt                ""
             :payer-type         "person"
             :person-id          "010203-040A"
             :phone              ""
             :postal-code        "92620"
             :protected?         false}}
    (invoice-field-updates {:application (application-with-payer "yritys")}
                           nil :application-payer? false)
    => {:in {:application-payer? false
             :billing-reference  "laskuviite"
             :city               "Huijala"
             :company-id         "0813000-2"
             :country            "FIN"
             :email              ""
             :entity-address     "Lumekatu 5"
             :entity-name        "Feikki Oy"
             :first-name         "Sonja"
             :last-name          "Sibbo"
             :netbill            ""
             :operator           "OPERATOR"
             :ovt                "003708130002"
             :payer-type         "company"
             :person-id          ""
             :phone              ""
             :postal-code        "92620"
             :protected?         false}}
    (invoice-field-updates {:application {}}
                           nil :application-payer? true)
    => {:in {:application-payer? true
             :billing-reference  ""
             :city               ""
             :company-id         ""
             :country            "FIN"
             :email              ""
             :entity-address     ""
             :entity-name        ""
             :first-name         ""
             :last-name          ""
             :netbill            ""
             :operator           ""
             :ovt                ""
             :payer-type         "person"
             :person-id          ""
             :phone              ""
             :postal-code        ""
             :protected?         false}})
  (against-background
    [(mongo/any? :price-catalogues
                 {:_id             "cat-id"
                  :organization-id "123-R"
                  :state           :published
                  :type            :R}) => true
     (mongo/any? :price-catalogues
                 {:_id             "cat-id"
                  :organization-id "123-R"
                  :state           :published
                  :type            :YA}) => false]
    (facts "price-catalogue-id"
      (invoice-field-updates nil nil :price-catalogue-id nil)
      => {:out [:price-catalogue-id]}
      (invoice-field-updates nil nil :price-catalogue-id "  ")
      => {:out [:price-catalogue-id]}
      (invoice-field-updates {:application {:organization "123-R"
                                            :permitType   "R"}}
                             nil :price-catalogue-id "cat-id")
      => {:in {:price-catalogue-id "cat-id"}}
      (invoice-field-updates {:application {:organization "123-R"
                                            :permitType   "YA"}}
                             nil :price-catalogue-id "cat-id")
      => (failed! :error.not-found)))

  (against-background
    [(mongo/get-next-sequence-value "invoices-123-R") => 12
     (tb/transfer-batch-for-invoice anything) => "batch-id"]
    (let [make-cmd (fn [enabled?]
                     {:organization (-> (make-config enabled? 4 true)
                                        (assoc :id "123-R")
                                        delay)})
          cfg-yes  (make-cmd true)
          cfg-no   (make-cmd false)]
      (facts "state"
        (invoice-field-updates nil {:rows  [{:id "1"}]
                                    :state "draft"} :state "checked")
        => {:in {:state "checked"}}
        (invoice-field-updates nil {:state "checked"} :state "draft")
        => {:in {:state              "draft"
                 :application-payer? true}}
        (invoice-field-updates nil {:state "checked"} :state "confirmed")
        => {:in {:state             "confirmed"
                 :transfer-batch-id "batch-id"}}
        (invoice-field-updates cfg-no
                               {:state "checked" :backend-code "AA"}
                               :state "confirmed")
        => {:in  {:state             "confirmed"
                  :transfer-batch-id "batch-id"}
            :out [:backend-code]}
        (invoice-field-updates cfg-yes
                               {:state        "checked"
                                :backend-code "BAD"}
                               :state "confirmed")
        => {:in  {:state             "confirmed"
                  :transfer-batch-id "batch-id"}
            :out [:backend-code]}
        (invoice-field-updates cfg-yes
                               {:state        "checked"
                                :backend-code "BB"}
                               :state "confirmed")
        => {:in {:state             "confirmed"
                 :backend-id        "BB0012"
                 :transfer-batch-id "batch-id"}}
        (invoice-field-updates cfg-yes
                               {:state        "checked"
                                :backend-code "BB"
                                :backend-id   "already-id"}
                               :state "confirmed")
        => {:in {:state             "confirmed"
                 :transfer-batch-id "batch-id"}}
        (invoice-field-updates nil {:state "confirmed"} :state "checked")
        => {:in  {:state "checked"}
            :out [:transfer-batch-id]}

        (doseq [from  [:draft :checked :confirmed :bad]
                to    [:draft :checked :confirmed :bad]
                :when (not (contains? #{:draft.checked :checked.draft
                                        :checked.confirmed :confirmed.checked}
                                      (util/kw-path from to)))]
          (facts "illegal"
            (invoice-field-updates nil {:state (name from)} :state (name to))
            => (failed! :error.invoice.illegal-state)))))))
