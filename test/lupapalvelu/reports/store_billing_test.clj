(ns lupapalvelu.reports.store-billing-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.reports.store-billing :refer :all]
            [sade.util :as util]))

(def docstore-response
  [{:description "Aloituskokous"
    :transaction_id "f7ede026-027f-4e1a-a302-eb3cb6257a80"
    :address "Merimiehenkatu 35"
    :property_id "09100501140023"
    :price_in_cents_without_vat 384
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "091-R"
    :paatospvm "2016-04-14T21:00:00.000Z"
    :document_type
    "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja"
    :building_ids []
    :created_at "2018-06-06T10:56:14.159Z"}
   {:description "Aloituskokous"
    :transaction_id "f7ede026-027f-4e1a-a302-eb3cb6257a80"
    :address "Merimiehenkatu 35"
    :property_id "09100501140023"
    :price_in_cents_without_vat 384
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "091-R"
    :paatospvm "2016-04-14T21:00:00.000Z"
    :document_type
    "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja"
    :building_ids []
    :created_at "2018-06-06T10:56:14.159Z"}
   {:description "P\u00E4\u00E4t\u00F6s"
    :transaction_id "f7ede026-027f-4e1a-a302-eb3cb6257a80"
    :address "Merimiehenkatu 35"
    :property_id "09100501140023"
    :price_in_cents_without_vat 384
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "091-R"
    :paatospvm "2016-04-14T21:00:00.000Z"
    :document_type "paatoksenteko.paatos"
    :building_ids []
    :created_at "2018-06-06T10:56:14.159Z"}
   {:description "Muu IV-suunnitelma"
    :transaction_id "09d881b1-9a1f-4c5b-bbee-a4acfb1a64e3"
    :address "Testikatu 42"
    :property_id "09100400860001"
    :price_in_cents_without_vat 384
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "091-R"
    :paatospvm "2016-11-29T22:00:00.000Z"
    :document_type "erityissuunnitelmat.iv_suunnitelma"
    :building_ids []
    :created_at "2018-06-07T12:34:25.921Z"}
   {:description "Muu IV-suunnitelma"
    :transaction_id "f1e429a7-9c4a-48be-b4d2-f41ec455d5e8"
    :address "Testikatu 42"
    :property_id "09100400860001"
    :price_in_cents_without_vat 384
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "091-R"
    :paatospvm "2016-11-29T22:00:00.000Z"
    :document_type "erityissuunnitelmat.iv_suunnitelma"
    :building_ids []
    :created_at "2018-06-07T13:00:26.785Z"}
   {:description "Muu IV-suunnitelma"
    :transaction_id "d38a2d88-90ae-4830-8790-d673858bc1fc"
    :address "Testikatu 42"
    :property_id "09100400860001"
    :price_in_cents_without_vat 384
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "091-R"
    :paatospvm "2016-11-29T22:00:00.000Z"
    :document_type "erityissuunnitelmat.iv_suunnitelma"
    :building_ids []
    :created_at "2018-06-07T13:01:31.032Z"}])

(def header-row-in-finnish
  ["Tilaustunnus" "Tilausaika" "Osoite" "Kiinteist\u00F6tunnus" "Rakennustunnukset" "Dokumenttityyppi" "Dokumentin kuvaus" "P\u00E4\u00E4t\u00F6sp\u00E4iv\u00E4m\u00E4\u00E4r\u00E4" "Hinta (\u20AC)"])

(defn documents-in-summary [sheet]
  (-> sheet :data last (nth 1)))

(defn total-price-in-summary [sheet]
  (-> sheet :data last last))

(facts "Billing entries sheet"
  (fact "No entries"
    (billing-entries-sheet 0 1528874580463 [] :fi)
    => {:sheet-name (str (util/to-local-date 0)
                         " - "
                         (util/to-local-date 1528874580463))
        :header header-row-in-finnish
        :row-fn identity
        :data [[]
               [nil "Dokumentteja (kpl)" "Summa (\u20AC)"]
               ["Yhteens\u00E4" 0 0.0]]})

  (facts "One entry"
    (let [sheet (billing-entries-sheet 0 10000000000 (take 1 docstore-response) :fi)]
      (fact "has right number of rows"
        (count (:data sheet)) => 4) ; 1 data row + 3 rows of summary

      (fact "has 1 as the amount of documents in summary section"
        (documents-in-summary sheet) => 1)

      (fact "has the price of the single document as the total price"
        (-> sheet :data first last) => (total-price-in-summary sheet)
        (-> sheet :data first last) => (-> docstore-response first :price_in_cents_without_vat cents->euros))))

  (facts "Multiple entries"
    (let [sheet (billing-entries-sheet 0 10000000000 docstore-response :fi)]
      (fact "has right number of rows"
        (count (:data sheet)) => (+ (count docstore-response) 3)) ; 1 data row + 3 rows of summary

      (fact "has the right number of documents in summary section"
        (documents-in-summary sheet) => (count docstore-response))

      (fact "has the combined price of all documents in summary section"
        (total-price-in-summary sheet) => (->> docstore-response (map :price_in_cents_without_vat) (apply +) cents->euros)))))
