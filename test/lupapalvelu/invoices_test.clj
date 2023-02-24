(ns lupapalvelu.invoices-test
  (:require [clojurewerkz.money.currencies :refer [EUR]]
            [lupapalvelu.invoices :refer [get-operations-from-application] :as invoices]
            [lupapalvelu.invoices.schemas :refer [->invoice-user ->invoice-db]]
            [lupapalvelu.invoices.util :refer [enrich-with-backend-id]]
            [lupapalvelu.money :as money]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.price-catalogues :refer [application-price-catalogues]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]
            [sade.util :as util]))

(def dummy-product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :muu-tunniste     "f"})

(facts "->invoice-user"
  (fact "throws (validation) error when user map given as argument lack require fields for constructing an (invoice) User"
    (->invoice-user {:foo "foo"
                     :id "user-id"}) => (throws Exception))
  (fact "returns a proper user object given user data map with the required fields"
    (let [user-data {:foo "foo"
                     :bar "bar"
                     :id        "penan-id"
                     :firstName "pena"
                     :lastName  "panaani"
                     :role      "authority"
                     :email     "pena@panaani.fi"
                     :username  "pena"}]
      (->invoice-user user-data) => {:id                                        "penan-id"
                                     :firstName                                 "pena"
                                     :lastName                                  "panaani"
                                     :role                                      "authority"
                                     :username                                  "pena"})))

(facts "->invoice-db"
  (let [user-data {:foo       "some-value"
                   :id        "penan-id"
                   :firstName "pena"
                   :lastName  "panaani"
                   :role      "authority"
                   :email     "pena@panaani.fi"
                   :username  "pena"}
        invoice-data {:state "draft"
                      :operations [{:operation-id "linjasaneeraus"
                                    :name "linjasaneeraus"
                                    :invoice-rows [{:text "Laskurivi1 kpl"
                                                    :type "from-price-catalogue"
                                                    :unit "kpl"
                                                    :price-per-unit 10
                                                    :units 2
                                                    :discount-percent 0
                                                    :product-constants dummy-product-constants}]}]}
        application-data {:id "LPK-1-TEST" :organization "123-R-TEST"}]
    (fact "returns a proper invoice map ready to be stored to the database"
      (with-redefs [sade.core/now (fn [] 12345)]
        (->invoice-db invoice-data {:application application-data} user-data)
        => {:state           "draft"
            :created         12345
            :created-by      {:id        "penan-id"
                              :firstName "pena"
                              :lastName  "panaani"
                              :role      "authority"
                              :username  "pena"}
            :application-id  "LPK-1-TEST"
            :description     "LPK-1-TEST"
            :organization-id "123-R-TEST"
            :operations      [{:operation-id "linjasaneeraus"
                               :name         "linjasaneeraus"
                               :invoice-rows [{:text              "Laskurivi1 kpl"
                                               :type              "from-price-catalogue"
                                               :unit              "kpl"
                                               :price-per-unit    10
                                               :units             2
                                               :discount-percent  0
                                               :product-constants dummy-product-constants}]}]}))))
(facts "get-operations-from-application"
  (let [primary-operation {:id "5bbc76b0b170d541a3c488ec"
                           :name "kerrostalo-rivitalo"
                           :description nil
                           :created 12344566}
        secondary-operation {:id "6bbc76b0b170d541a3c488ec"
                             :name "purkaminen"
                             :description nil
                             :created 12344566}
        mock-application-with-primary-operation-only {:primaryOperation primary-operation}
        mock-application-with-primary-operation-and-secondary-operations {:primaryOperation primary-operation :secondaryOperations [secondary-operation secondary-operation]}
        mock-application-with-primary-operation-and-empty-secondary-operations {:primaryOperation primary-operation :secondaryOperations []}
        mock-application-with-primary-operation-and-nil-secondary-operations {:primaryOperation primary-operation :secondaryOperations nil}]

    (fact "returns vector contaning primary operation when only primary operation is in application"
      (get-operations-from-application mock-application-with-primary-operation-only) => [primary-operation])

    (fact "returns vector contaning primary operation and secondary operations when we have primary operation and secondary operation"
      (get-operations-from-application mock-application-with-primary-operation-and-secondary-operations) => [primary-operation secondary-operation secondary-operation])

    (fact "returns vector contaning only primary operation when we have primary operation and secondary operation is empty vec"
      (get-operations-from-application mock-application-with-primary-operation-and-empty-secondary-operations) => [primary-operation])

    (fact "returns vector contaning only primary operation when we have primary operation and secondary operation is nil"
      (get-operations-from-application mock-application-with-primary-operation-and-nil-secondary-operations) => [primary-operation])))

(facts "sum-single-row"
  (fact "sums invoice-row with 1 unit of 10 to ten"
    (let [test-invoice-row {:text "Laskurivi1 kpl"
                            :type "from-price-catalogue"
                            :unit "kpl"
                            :price-per-unit 10
                            :units 1
                            :discount-percent 0
                            :product-constants dummy-product-constants}
          result (invoices/sum-single-row test-invoice-row)]

      (:without-discount result) => {:major    10
                                     :minor    1000
                                     :currency "EUR"
                                     :text     (money/->currency-text EUR 10.00)}
      (:with-discount result) => {:major    10
                                  :minor    1000
                                  :currency "EUR"
                                  :text     (money/->currency-text EUR 10.00)}))
  (fact "sums invoice-row with 2 unit of 10 to 20"
    (let [test-invoice-row {:text "Laskurivi1 kpl"
                            :type "from-price-catalogue"
                            :unit "kpl"
                            :price-per-unit 10
                            :units 2
                            :discount-percent 0}
          result (invoices/sum-single-row test-invoice-row)]

      (:without-discount result) => {:major    20
                                     :minor    2000
                                     :currency "EUR"
                                     :text     (money/->currency-text EUR 20.00)}
      (:with-discount result) => {:major    20
                                  :minor    2000
                                  :currency "EUR"
                                  :text     (money/->currency-text EUR 20.00)}))
  (fact "sums invoice-row with 2 unit of 10 to 20 with discount of 32% to 13.60 and w/o discount to 20"
    (let [test-invoice-row {:text "Laskurivi1 kpl"
                            :type "from-price-catalogue"
                            :unit "kpl"
                            :price-per-unit 10
                            :units 2
                            :discount-percent 32
                            :product-constants dummy-product-constants}
          result (invoices/sum-single-row test-invoice-row)]

      (:without-discount result) => {:major    20
                                     :minor    2000
                                     :currency "EUR"
                                     :text     (money/->currency-text EUR 20.00)}
      (:with-discount result) => {:major    13
                                  :minor    1360
                                  :currency "EUR"
                                  :text     (money/->currency-text EUR 13.60)})))

(facts "sum-invoice"
  (fact "sums invoice with rows worth 10 and 10 without discounts as 20"
    (let [test-invoice {:state "draft"
                        :operations [{:operation-id "linjasaneeraus"
                                      :name "linjasaneeraus"
                                      :invoice-rows [{:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0
                                                      :product-constants dummy-product-constants}
                                                     {:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0}]}]}]
      (:sum (invoices/sum-invoice test-invoice)) => {:major    20
                                                     :minor    2000
                                                     :currency "EUR"
                                                     :text     (money/->currency-text EUR 20.00)}))
  (fact "sums invoice with operation without invoices results in 0EUR"
    (let [test-invoice {:state "draft"
                        :operations [{:operation-id "linjasaneeraus"
                                      :name "linjasaneeraus"
                                      :invoice-rows []}]}]
      (:sum (invoices/sum-invoice test-invoice)) => {:major    0
                                                     :minor    0
                                                     :currency "EUR"
                                                     :text     (money/->currency-text EUR 0.00)}))
  (fact "sums invoice with rows worth 10 and 2 * 10 without discounts as 30"
    (let [test-invoice {:state "draft"
                        :operations [{:operation-id "linjasaneeraus"
                                      :name "linjasaneeraus"
                                      :invoice-rows [{:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0
                                                      :product-constants dummy-product-constants}
                                                     {:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 2
                                                      :discount-percent 0}]}]}]
      (:sum (invoices/sum-invoice test-invoice)) => {:major    30
                                                     :minor    3000
                                                     :currency "EUR"
                                                     :text     (money/->currency-text EUR 30.00)}))
  (fact "sums invoice with rows worth 10 and 2 * 10  and 10 without discounts as 40 when in two operatons"
    (let [test-invoice {:state "draft"
                        :operations [{:operation-id "linjasaneeraus"
                                      :name "linjasaneeraus"
                                      :invoice-rows [{:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0
                                                      :product-constants dummy-product-constants}
                                                     {:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 2
                                                      :discount-percent 0}]}
                                     {:operation-id "rakennuksen-purkaminen"
                                      :name "rakennuksen purkaminen"
                                      :invoice-rows [{:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0
                                                      :product-constants dummy-product-constants}
                                                     ]}]}]
      (:sum (invoices/sum-invoice test-invoice)) => {:major    40
                                                     :minor    4000
                                                     :currency "EUR"
                                                     :text     (money/->currency-text EUR 40.00)}))
  (fact "sums invoice with rows worth 10 and 2 * 10  and 2* 10 with discount 20 without discounts as 40 when in two operatons"
    (let [test-invoice {:state "draft"
                        :operations [{:operation-id "linjasaneeraus"
                                      :name "linjasaneeraus"
                                      :invoice-rows [{:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0
                                                      :product-constants dummy-product-constants}
                                                     {:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 2
                                                      :discount-percent 20}]}
                                     {:operation-id "rakennuksen-purkaminen"
                                      :name "rakennuksen purkaminen"
                                      :invoice-rows [{:text "Laskurivi1 kpl"
                                                      :type "from-price-catalogue"
                                                      :unit "kpl"
                                                      :price-per-unit 10
                                                      :units 1
                                                      :discount-percent 0}
                                                     ]}]}]
      (:sum (invoices/sum-invoice test-invoice)) => {:major    36
                                                     :minor    3600
                                                     :currency "EUR"
                                                     :text     (money/->currency-text EUR 36.00)})))

(facts "unit-price-within-limits"
  (let [row-with (fn [props]
                   (merge {:text "Laskurivi1 kpl"
                           :type "from-price-catalogue"
                           :unit "kpl"
                           :price-per-unit 10
                           :units 1
                           :discount-percent 0
                           :product-constants dummy-product-constants})
                   props)]

    (fact "returns true when"
      (fact "min-unit-price and max-unit-price not defined"
        (invoices/unit-price-within-limits? (row-with {:price-per-unit 10}))
        => true)


      (fact "price-per-unit is within min and max"
        ;; in between
        (invoices/unit-price-within-limits? (row-with {:price-per-unit 10
                                                       :min-unit-price 1
                                                       :max-unit-price 20}))
        => true
        ;; same as min
        (invoices/unit-price-within-limits? (row-with {:price-per-unit 10
                                                       :min-unit-price 10
                                                       :max-unit-price 20}))
        => true
        ;; same as max
        (invoices/unit-price-within-limits? (row-with {:price-per-unit 20
                                                       :min-unit-price 10
                                                       :max-unit-price 20}))
        => true)

      (fact "max-unit-price nil and"
        (fact "price-per-unit equal to min-unit-price"
          (invoices/unit-price-within-limits? (row-with {:price-per-unit 10
                                                         :min-unit-price 10
                                                         :max-unit-price nil}))
          => true)
        (fact "price-per-unit greater than min-unit-price"
          (invoices/unit-price-within-limits? (row-with {:price-per-unit 15
                                                         :min-unit-price 10
                                                         :max-unit-price nil}))
          => true))

      (fact "min-unit-price nil and"
        (fact "price-per-unit equal to max-unit-price"
          (invoices/unit-price-within-limits? (row-with {:price-per-unit 20
                                                         :min-unit-price nil
                                                         :max-unit-price 20}))
          => true)
        (fact "price-per-unit less than min-unit-price"
          (invoices/unit-price-within-limits? (row-with {:price-per-unit 10
                                                         :min-unit-price nil
                                                         :max-unit-price 20}))
          => true)))
    (fact "returns false when"
      (fact "price-per-unit is nil"
        (invoices/unit-price-within-limits? (row-with {:price-per-unit nil
                                                       :min-unit-price 10
                                                       :max-unit-price 20}))
        => false)
      (fact "price-per-unit less than min-unit-price"
        (invoices/unit-price-within-limits? (row-with {:price-per-unit 5
                                                       :min-unit-price 10
                                                       :max-unit-price 20}))
        => false)
      (fact "price-per-unit greater than max-unit-price"
        (invoices/unit-price-within-limits? (row-with {:price-per-unit 30
                                                       :min-unit-price 10
                                                       :max-unit-price 20}))
        => false))))


(facts "invoice-unit-price-within-limits"
  (fact "returns true when"
    (fact "all rows have price-per-unit values within min and max"
      (let [invoice {:operations [{:operation-id "rakennuksen-purkaminen"
                                   :name "rakennuksen purkaminen"
                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                   :type "from-price-catalogue"
                                                   :unit "kpl"
                                                   :price-per-unit 10
                                                   :units 1
                                                   :discount-percent 0
                                                   :product-constants dummy-product-constants}

                                                  ;; row with no min and max unit prices specified
                                                  ;; is interpreted as being within
                                                  {:text "Laskurivi1 kpl"
                                                   :type "from-price-catalogue"
                                                   :unit "kpl"
                                                   :price-per-unit 20
                                                   :min-unit-price 10
                                                   :max-unit-price 30
                                                   :units 1
                                                   :discount-percent 0
                                                   :product-constants dummy-product-constants}]}]}]
        (invoices/invoice-unit-prices-within-limits? invoice) => true
        )))
  (fact "returns false when any row has price-per-unit outside min and max"
    (fact "all rows have price-per-unit values within min and max"
      (let [invoice {:operations [{:operation-id "rakennuksen-purkaminen"
                                   :name "rakennuksen purkaminen"
                                   :invoice-rows [{:text "Laskurivi1 kpl"
                                                   :type "from-price-catalogue"
                                                   :unit "kpl"
                                                   :price-per-unit 20
                                                   :min-unit-price 10
                                                   :max-unit-price 30
                                                   :units 1
                                                   :discount-percent 0
                                                   :product-constants dummy-product-constants}

                                                  {:text "Laskurivi1 kpl"
                                                   :type "from-price-catalogue"
                                                   :unit "kpl"
                                                   :price-per-unit 50
                                                   :min-unit-price 10
                                                   :max-unit-price 30
                                                   :units 1
                                                   :discount-percent 0
                                                   :product-constants dummy-product-constants}]}]}]
        (invoices/invoice-unit-prices-within-limits? invoice) => false
        ))))

;;TODO test that empty invoice rows is not automatically invalid


(facts "unit-prices-within-limits?"
  (fact "returns nil when all unit prices within limits"
    (let [cmd {:data {:invoice {:operations [{:operation-id "rakennuksen-purkaminen"
                                              :name "rakennuksen purkaminen"
                                              :invoice-rows [{:text "Laskurivi1 kpl"
                                                              :type "from-price-catalogue"
                                                              :unit "kpl"
                                                              :price-per-unit 10
                                                              :units 1
                                                              :discount-percent 0
                                                              :product-constants dummy-product-constants}

                                                             ;; row with no min and max unit prices specified
                                                             ;; is interpreted as being within
                                                             {:text "Laskurivi1 kpl"
                                                              :type "from-price-catalogue"
                                                              :unit "kpl"
                                                              :price-per-unit 20
                                                              :min-unit-price 10
                                                              :max-unit-price 30
                                                              :units 1
                                                              :discount-percent 0
                                                              :product-constants dummy-product-constants}]}]}}}]
      (invoices/unit-prices-within-limits? cmd) => nil?))

  (fact "returns fail when (at least) one invoice row has unit price outside the allowed limits"
    (let [cmd {:data {:invoice {:operations [{:operation-id "rakennuksen-purkaminen"
                                              :name "rakennuksen purkaminen"
                                              :invoice-rows [{:text "Laskurivi1 kpl"
                                                              :type "from-price-catalogue"
                                                              :unit "kpl"
                                                              :price-per-unit 20
                                                              :min-unit-price 10
                                                              :max-unit-price 30
                                                              :units 1
                                                              :discount-percent 0
                                                              :product-constants dummy-product-constants}

                                                             {:text "Laskurivi1 kpl"
                                                              :type "from-price-catalogue"
                                                              :unit "kpl"
                                                              :price-per-unit 50
                                                              :min-unit-price 10
                                                              :max-unit-price 30
                                                              :units 1
                                                              :discount-percent 0
                                                              :product-constants dummy-product-constants}]}]}}}]
      (invoices/unit-prices-within-limits? cmd) => {:ok false :text "error.unit-price-not-within-allowed-limits"})))

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
                                                           :postinumero          {:value "10203"}
                                                           :postitoimipaikannimi {:value "Piippola"}
                                                           :maa                  {:value "FIN"}}}
                              :yritys     {:yritysnimi           {:value "Feikki Oy"}
                                           :liikeJaYhteisoTunnus {:value "0813000-2"}
                                           :osoite               {:katu                 {:value "Lumekatu 5"}
                                                                  :postinumero          {:value "12345"}
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

(facts "billing-data-from"
  (facts "extracts billing fields from application using the first entry found in application.documents"
    (fact "when payer is a person"
      (let [application (application-with-payer "henkilo")]
        (fact "billing data basic case"
          (invoices/billing-data-from application) => {:payer-type        "person"
                                                       :person-id         "010203-040A"
                                                       :entity-name       "Pena Panaani"
                                                       :entity-address    "Paapankuja 12 10203 Piippola"
                                                       :billing-reference "laskuviite"})

        (fact "but omits missing fields"

          (fact "entity-name"
            (invoices/billing-data-from (-> application
                                            (update-in [:documents 0 :data :henkilo :henkilotiedot]
                                                       dissoc :etunimi)
                                            (update-in [:documents 0 :data :henkilo :henkilotiedot]
                                                       dissoc :sukunimi)))
            => {:payer-type        "person"
                :person-id         "010203-040A"
                :entity-address    "Paapankuja 12 10203 Piippola"
                :billing-reference "laskuviite"})

          (fact "no hetu"
            (invoices/billing-data-from (update-in application [:documents 0 :data :henkilo :henkilotiedot]
                                                   dissoc :hetu))
            => {:payer-type        "person"
                :entity-name       "Pena Panaani"
                :entity-address    "Paapankuja 12 10203 Piippola"
                :billing-reference "laskuviite"})

          (fact "no address"
            (invoices/billing-data-from (-> application
                                            (update-in [:documents 0 :data :henkilo]
                                                       dissoc :osoite)))
            => {:payer-type        "person"
                :entity-name       "Pena Panaani"
                :person-id         "010203-040A"
                :billing-reference "laskuviite"})

          (fact "no laskuviite"
            (invoices/billing-data-from (update-in application [:documents 0 :data ]
                                                   dissoc :laskuviite))
            => {:payer-type     "person"
                :person-id      "010203-040A"
                :entity-name    "Pena Panaani"
                :entity-address "Paapankuja 12 10203 Piippola"}))))

    (fact "when payer is a company"
      (let [application (application-with-payer "yritys")]
        (invoices/billing-data-from application)
        => {:payer-type             "company"
            :company-id             "0813000-2"
            :entity-name            "Feikki Oy"
            :entity-address         "Lumekatu 5 12345 Huijala"
            :ovt                    "003708130002"
            :operator               "OPERATOR"
            :billing-reference      "laskuviite"
            :company-contact-person "Sonja Sibbo"}))))

(facts enrich-org-data
  (invoices/enrich-org-data {:organization (delay {:id   "org-id"
                                                   :name {:fi "Moi" :sv "Hej"}})}
                            {:id "invoice-id"})
  => {:id            "invoice-id"
      :enriched-data {:organization {:name {:fi "Moi" :sv "Hej"}}}}
  (invoices/enrich-org-data {:user-organizations [{:id   "org1"
                                                   :name {:fi "Yksi" :sv "Ett"}}
                                                  {:id   "org2"
                                                   :name {:fi "Kaksi" :sv "Två"}}
                                                  {:id   "org3"
                                                   :name {:fi "Kolmes" :sv "Tre"}}]}
                            {:id              "invoice-id"
                             :organization-id "org2"})
  => {:id              "invoice-id"
      :organization-id "org2"
      :enriched-data   {:organization {:name {:fi "Kaksi" :sv "Två"}}}})

(facts enrich-application-data
  (invoices/enrich-application-data (assoc (application-with-payer "henkilo")
                                           :address "Good Old Latokuja")
                                    {:id    "invoice-id"
                                     :state "checked"})
  => {:id             "invoice-id"
      :state          "checked"
      :entity-address "Paapankuja 12 10203 Piippola"
      :enriched-data  {:application {:address "Good Old Latokuja"
                                     :payer   {:payer-type        "person"
                                               :person-id         "010203-040A"
                                               :entity-name       "Pena Panaani"
                                               :entity-address    "Paapankuja 12 10203 Piippola"
                                               :billing-reference "laskuviite"}}}}
  (invoices/enrich-application-data {:address "Good Old Latokuja"}
                                    {:id             "invoice-id"
                                     :state          "checked"
                                     :entity-address "Will be wiped"})
  => {:id             "invoice-id"
      :state          "checked"
      :entity-address ""
      :enriched-data  {:application {:address "Good Old Latokuja"
                                     :payer   {:payer-type "person"}}}}
  (invoices/enrich-application-data [{:id      "app1"
                                      :address "Good Old Latokuja"}
                                     (assoc (application-with-payer "yritys")
                                            :id "app2"
                                            :address "Broken Loop")
                                     {:id      "app3"
                                      :address "Elsewhere"}]
                                    {:id             "invoice-id"
                                     :state          "checked"
                                     :application-id "app2"
                                     :entity-address "Will be wiped"})
  => {:id             "invoice-id"
      :state          "checked"
      :application-id "app2"
      :entity-address "Lumekatu 5 12345 Huijala"
      :enriched-data  {:application {:address "Broken Loop"
                                     :payer   {:payer-type             "company"
                                               :company-id             "0813000-2"
                                               :entity-name            "Feikki Oy"
                                               :entity-address         "Lumekatu 5 12345 Huijala"
                                               :ovt                    "003708130002"
                                               :operator               "OPERATOR"
                                               :billing-reference      "laskuviite"
                                               :company-contact-person "Sonja Sibbo"}}}}
  (facts "Entity-address is generated only for the checked state"
    (:entity-address (invoices/enrich-application-data (application-with-payer "henkilo")
                                                       {:id    "invoice-id"
                                                        :state "checked"}))
    => "Paapankuja 12 10203 Piippola"
    (:entity-address (invoices/enrich-application-data (application-with-payer "henkilo")
                                                       {:id    "invoice-id"
                                                        :state "draft"}))
    => nil
    (:entity-address (invoices/enrich-application-data (application-with-payer "henkilo")
                                                       {:id "invoice-id"}))
    => nil))

(testable-privates lupapalvelu.invoices update-invoice-row-vat-info)

(defn unchanged-row-vat-info [row]
  (fact "Unchanged row VAT info"
    (update-invoice-row-vat-info row) => row))

(defn make-row [sum-with-discount-minor alv]
  (-> {}
      (assoc-in [:sums :with-discount :minor] sum-with-discount-minor)
      (assoc-in [:product-constants :alv] alv)))

(defn check-vat [{:keys [sums vat-percentage vat-amount-minor]}]
  (fact {:midje/description (format "VAT: %s%%, %s" vat-percentage vat-amount-minor)}
    (let [total (-> sums :with-discount :minor)]
      (Math/round (* 0.01 (+ 100 vat-percentage) (- total vat-amount-minor))) => total)))

(facts "VAT"
  (facts "update-invoice-row-vat-info: no info"
    (unchanged-row-vat-info nil)
    (unchanged-row-vat-info {})
    (unchanged-row-vat-info {:sums {:with-discount {:minor 100}}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv ""}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv "0%"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv "100%"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv "-1%"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv "101%"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 0}}
                             :product-constants {:alv "25%"}})
    (unchanged-row-vat-info {:sums              {}
                             :product-constants {:alv "25%"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv "bad"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor 100}}
                             :product-constants {:alv "25% bad"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor -100}}
                             :product-constants {:alv "25%"}})
    (unchanged-row-vat-info {:sums              {:with-discount {:minor -100}}
                             :product-constants {:alv "25.0%"}}))
  (facts "update-invoice-row-vat-info: ok"
    (let [row (update-invoice-row-vat-info {:sums              {:with-discount {:minor 120}}
                                            :product-constants {:alv "25%"}})]
      row => {:sums              {:with-discount {:minor 120}}
              :product-constants {:alv "25%"}
              :vat-percentage    25
              :vat-amount-minor  24}
      (check-vat row))
    (let [row (update-invoice-row-vat-info {:sums              {:with-discount {:minor 100}}
                                            :product-constants {:alv " %%  33 %%% "}})]
      row => {:sums              {:with-discount {:minor 100}}
              :product-constants {:alv " %%  33 %%% "}
              :vat-percentage    33
              :vat-amount-minor  25}
      (check-vat row)))

  (facts "enrich-vat-info"
    (invoices/enrich-vat-info {:operations
                               [{:invoice-rows (map (partial apply make-row)
                                                    [[100 "10%"]
                                                     [200 "24 %"]
                                                     [50 nil]])}]})
    => {:operations      [{:invoice-rows [{:product-constants {:alv "10%"}
                                           :sums              {:with-discount {:minor 100}}
                                           :vat-amount-minor  9
                                           :vat-percentage    10}
                                          {:product-constants {:alv "24 %"}
                                           :sums              {:with-discount {:minor 200}}
                                           :vat-amount-minor  39
                                           :vat-percentage    24}
                                          {:product-constants {:alv nil}
                                           :sums              {:with-discount {:minor 50}}}]}]
        :vat-total-minor 48}
    (invoices/enrich-vat-info {:operations
                               [{:invoice-rows (map (partial apply make-row)
                                                    [[100 "0%"]
                                                     [200 "bad24 %"]
                                                     [50 nil]])}]})
    => {:operations [{:invoice-rows [{:product-constants {:alv "0%"}
                                      :sums              {:with-discount {:minor 100}}}
                                     {:product-constants {:alv "bad24 %"}
                                      :sums              {:with-discount {:minor 200}}}
                                     {:product-constants {:alv nil}
                                      :sums              {:with-discount {:minor 50}}}]}]}
    (invoices/enrich-vat-info {:operations
                               [{:invoice-rows (map (partial apply make-row)
                                                    [[100 "%"]
                                                     [200 ""]
                                                     [50 nil]])}
                                {:invoice-rows (map (partial apply make-row)
                                                    [[300 "20"]
                                                     [200 ""]
                                                     [0 "%50"]])}
                                {:invoice-rows (map (partial apply make-row)
                                                    [[123 "15"]
                                                     [55 "5%"]
                                                     [212 "%12"]])}
                                {:invoice-rows (map (partial apply make-row)
                                                    [[90 "14"]
                                                     [-100 "25%"]
                                                     [103 "%22"]])}]})
    => {:operations
        [{:invoice-rows [{:product-constants {:alv "%"}
                          :sums              {:with-discount {:minor 100}}}
                         {:product-constants {:alv ""}
                          :sums              {:with-discount {:minor 200}}}
                         {:product-constants {:alv nil}
                          :sums              {:with-discount {:minor 50}}}]}
         {:invoice-rows [{:product-constants {:alv "20"}
                          :sums              {:with-discount {:minor 300}}
                          :vat-amount-minor  50
                          :vat-percentage    20}
                         {:product-constants {:alv ""}
                          :sums              {:with-discount {:minor 200}}}
                         {:product-constants {:alv "%50"}
                          :sums              {:with-discount {:minor 0}}}]}
         {:invoice-rows [{:product-constants {:alv "15"}
                          :sums              {:with-discount {:minor 123}}
                          :vat-amount-minor  16
                          :vat-percentage    15}
                         {:product-constants {:alv "5%"}
                          :sums              {:with-discount {:minor 55}}
                          :vat-amount-minor  3
                          :vat-percentage    5}
                         {:product-constants {:alv "%12"}
                          :sums              {:with-discount {:minor 212}}
                          :vat-amount-minor  23
                          :vat-percentage    12}]}
         {:invoice-rows [{:product-constants {:alv "14"}
                          :sums              {:with-discount {:minor 90}}
                          :vat-amount-minor  11
                          :vat-percentage    14}
                         {:product-constants {:alv "25%"}
                          :sums              {:with-discount {:minor -100}}}
                         {:product-constants {:alv "%22"}
                          :sums              {:with-discount {:minor 103}}
                          :vat-amount-minor  19
                          :vat-percentage    22}]}]
        :vat-total-minor 122}))

(def runeberg (date/timestamp "5.2.2020"))
(def kalevala (date/timestamp "28.2.2020"))
(def good     (date/timestamp "10.4.2020"))
(def mayday   (date/timestamp "1.5.2020"))

(facts "enrich-workdays"
  (fact "No dates"
    (invoices/enrich-workdays {:application {:permitType "YA"}
                               :created     12345}
                              {:state "draft"})
    => {:workdays {} :state "draft"})

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
        :work-start-ms runeberg
        :workdays      {}}
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
        :work-end-ms   kalevala
        :workdays      {}})
  (against-background (application-price-catalogues anything anything) => []))

(defn make-invoice
  ([code backend-id]
   (util/assoc-when {:organization-id "123-FOO"}
                    :backend-code code
                    :backend-id backend-id))
  ([code]
   (make-invoice code nil)))

(defn make-config [enabled? numbers codes?]
  {:invoicing-config            {:backend-id? (boolean enabled?)}
   :invoicing-backend-id-config (util/strip-nils
                                  {:numbers numbers
                                   :codes   (when codes?
                                              (for [code ["AA" "BB" "CC" "DD"]]
                                                {:code code
                                                 :text (str "Backend code is " code)
                                                 :id   (mongo/create-id)}))})})

(against-background
  [(mongo/get-next-sequence-value "invoices-123-FOO") => 789]
  (facts enrich-with-backend-id
    (let [code-invoice  (make-invoice "BB")
          empty-invoice (make-invoice nil)
          both-invoice  (make-invoice "XY" "ID0001")
          id-invoice    (make-invoice nil "ID0001")]
     (fact "Backend-id not enabled"
       (enrich-with-backend-id code-invoice) => empty-invoice
       (provided
         (mongo/by-id :organizations "123-FOO" anything) => (make-config false 8 true)
         (mongo/get-next-sequence-value anything) => irrelevant :times 0))
     (fact "Code not defined"
       (enrich-with-backend-id empty-invoice) => empty-invoice
       (provided
         (mongo/get-next-sequence-value anything) => irrelevant :times 0))
     (fact "Code no longer available"
       (enrich-with-backend-id (make-invoice "OLD")) => empty-invoice
       (provided
         (mongo/by-id :organizations "123-FOO" anything) => (make-config true 8 true)
         (mongo/get-next-sequence-value anything) => irrelevant :times 0))
     (fact "Backend-id already defined"
       (enrich-with-backend-id id-invoice) => id-invoice
       (provided
         (mongo/get-next-sequence-value anything) => irrelevant :times 0)
       (enrich-with-backend-id both-invoice) => both-invoice
       (provided
         (mongo/get-next-sequence-value anything) => irrelevant :times 0))
     (fact "Generates backend-id: 8 numbers"
       (enrich-with-backend-id code-invoice)
       => (make-invoice nil "BB00000789")
       (provided
         (mongo/by-id :organizations "123-FOO" anything) => (make-config true 8 true)))
     (fact "Generates backend-id: 1 numbers"
       (enrich-with-backend-id code-invoice)
       => (make-invoice nil "BB789")
       (provided
         (mongo/by-id :organizations "123-FOO" anything) => (make-config true 1 true)))
     (fact "Generates backend-id: no numbers"
       (enrich-with-backend-id code-invoice)
       => (make-invoice nil "BB789")
       (provided
         (mongo/by-id :organizations "123-FOO" anything) => (make-config true nil true))))))
