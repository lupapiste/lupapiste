(ns lupapalvelu.reports.store-billing-test
  (:require [lupapalvelu.reports.store-billing :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.date :as date]))

(testable-privates lupapalvelu.reports.store-billing
                   total-price-of-documents)

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
    {:id "244"
     :name {:fi "Kempele" :sv "Kempele" :en "Kempele"}
     :permitType "R"}
    :organization "244-R"
    :paatospvm "2016-04-14T21:00:00.000Z"
    :document_type "paatoksenteko.paatos"
    :building_ids []
    :created_at "2018-06-06T10:56:14.159Z"}
   {:description "Muu IV-suunnitelma"
    :transaction_id "09d881b1-9a1f-4c5b-bbee-a4acfb1a64e3"
    :address "Testikatu 42"
    :property_id "09100400860001"
    :price_in_cents_without_vat 384
    :organization_fee 200
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
    :organization_fee 200
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
    :organization_fee 200
    :municipality
    {:id "091"
     :name {:fi "Helsinki" :sv "Helsingfors" :en "Helsinki"}
     :permitType "R"}
    :organization "092-R"
    :paatospvm "2016-11-29T22:00:00.000Z"
    :document_type "erityissuunnitelmat.iv_suunnitelma"
    :building_ids []
    :created_at "2018-06-07T13:01:31.032Z"}])

(def downloads-for-all-org
  [{:organization "106-R",
    :apiUser "document_store",
    :metadata {:applicationId "LP-1",
               :address "Mokukatu 1",
               :type "erityissuunnitelmat.iv_suunnitelma",
               :propertyId "75342600110011"},
    :timestamp 1529062955886,
    :id "5b23a62be7f604328bc62b04"}
    {:organization "257-R",
     :apiUser "kirkkonummi_sito",
     :metadata {:applicationId "LP-3",
                :address "Rokukatu 3",
                :type "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja",
                :propertyId "75342600110013"},
     :timestamp 1561547760410,
     :id "5d1353f059412f04a8c7c7a5"}
    {:organization "257-R",
     :apiUser "kirkkonummi_sito",
     :metadata {:applicationId "LP-4",
                :address "Sokukatu 4",
                :type "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja",
                :propertyId "75342600110014"},
     :timestamp 1521547760411,
     :id "5d1353f059412f04a8c7c7a6"}
    {:organization "257-R",
     :apiUser "document_store",
     :metadata {:applicationId "LP-5",
                :address "Tokukatu 5",
                :type "paapiirustus.julkisivupiirustus",
                :propertyId "75342600110014"},
     :timestamp 1561547760411,
     :id "5d1353f059412f04a8c7c7a7"}
    {:organization "753-R",
     :apiUser "vantaa_sito",
     :metadata {:foo "foo"},
     :timestamp 1529062956530,
     :id "5b23a62c59412f1eb0bd541a"}])

(def downloads-for-257
  [{:organization "257-R",
   :apiUser "kirkkonummi_sito",
   :metadata {:applicationId "LP-3",
              :address "Rokukatu 3",
              :type "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja",
              :propertyId "75342600110013"},
   :timestamp 1561547760410,
   :id "5d1353f059412f04a8c7c7a5"}
  {:organization "257-R",
   :apiUser "kirkkonummi_sito",
   :metadata {:applicationId "LP-4",
              :address "Sokukatu 4",
              :type "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja",
              :propertyId "75342600110014"},
   :timestamp 1521547760411,
   :id "5d1353f059412f04a8c7c7a6"}
  {:organization "257-R",
   :apiUser "document_store",
   :metadata {:applicationId "LP-5",
              :address "Tokukatu 5",
              :type "paapiirustus.julkisivupiirustus",
              :propertyId "75342600110014"},
   :timestamp 1561547760411,
   :id "5d1353f059412f04a8c7c7a8"}
   {:organization "257-R",
    :apiUser "kirkkonummi_sito",
    :metadata {:applicationId "LP-6",
               :address "Nokukatu 4",
               :type "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja",
               :propertyId "75342600110014"},
    :timestamp 1562547760411,
    :id "5d1353f059412f04a8c7c7a9"}])

(facts resolve-fee
  (fact "Organization fee"
    (resolve-fee {:organization_fee 200 :price_in_cents_without_vat 600}) => 200)
  (fact "Fixed commission"
    (resolve-fee {:price_in_cents_without_vat 600}) => 500)
  (fact "Organization fee is zero -> fixed commission"
    (resolve-fee {:organization_fee 0 :price_in_cents_without_vat 600}) => 500))

(fact total-price-of-documents
  (total-price-of-documents [{:organization_fee 200 :price_in_cents_without_vat 600}
                             {:organization_fee 100 :price_in_cents_without_vat 500}
                             {:organization_fee 0 :price_in_cents_without_vat 400}
                             {:price_in_cents_without_vat 300}])
  => 8.0)

(def header-row-in-finnish
  ["Tilaustunnus" "Tilausaika" "Osoite" "Kiinteist\u00F6tunnus" "Rakennustunnukset" "Dokumenttityyppi" "Dokumentin kuvaus" "P\u00E4\u00E4t\u00F6sp\u00E4iv\u00E4m\u00E4\u00E4r\u00E4" "Kunnan taksa (\u20AC)"])

(defn documents-in-summary [sheet]
  (-> sheet :data last (nth 1)))

(defn total-price-in-summary [sheet]
  (-> sheet :data last butlast last))

(facts "Billing entries sheet"
  (fact "No entries"
    (billing-entries-sheet 0 1528874580463 :fi [])
    => {:sheet-name (str (date/finnish-date 0 :zero-pad)
                         " - "
                         (date/finnish-date 1528874580463 :zero-pad))
        :header header-row-in-finnish
        :row-fn identity
        :data [[]
               [nil "Dokumentteja (kpl)" "Summa (\u20AC)" "ALV (%)"]
               ["Yhteens\u00E4" 0 0.0 0]]})

  (facts "One entry"
    (let [sheet (billing-entries-sheet 0 10000000000 :fi (take 1 docstore-response))]
      (fact "has right number of rows"
        (count (:data sheet)) => 4) ; 1 data row + 3 rows of summary

      (fact "has the right header columns"
        (:header sheet) => header-row-in-finnish)

      (fact "has 1 as the amount of documents in summary section"
        (documents-in-summary sheet) => 1)

      (fact "has the price of the single document as the total price"
        (-> sheet :data first last) => (total-price-in-summary sheet)
        (-> sheet :data first last) => (-> docstore-response first resolve-fee cents->euros))))

  (facts "Multiple entries"
    (let [sheet (billing-entries-sheet 0 10000000000 :fi docstore-response)]
      (fact "has right number of rows"
        (count (:data sheet)) => (+ (count docstore-response) 3)) ; x data rows + 3 rows of summary

      (fact "has the right header columns"
        (:header sheet) => (into ["Kunta"] header-row-in-finnish))

      (fact "has the right number of documents in summary section"
        (documents-in-summary sheet) => (count docstore-response))

      (fact "has the combined price of all documents in summary section"
        (total-price-in-summary sheet) => (->> docstore-response (map resolve-fee) (apply +) cents->euros)))))

(defn nth-row
  [data kw nth]
  (-> (get data kw) vec (get nth)))

(facts "Downloads entries sheet"
  (against-background
    [(docstore-downloads nil 1461547760416 1563547760416) => downloads-for-all-org
     (docstore-downloads "257-R" 1461547760416 1563547760416) => downloads-for-257]
    (facts "Downloaded documents for all organizations"
      (let [rows (downloads-entries-sheets nil 1461547760416 "1563547760416" "fi")]
        (fact "Sheet name"
          (:sheet-name rows) => (format "%s - %s"
                                        (date/finnish-date 1461547760416)
                                        (date/finnish-date 1563547760416))
          )
        (fact "Header is correct"
          (:header rows) => ["Organisaation nimi" "Rajapintatunnus" "Tilausaika" "Hakemus" "Dokumenttityyppi" "Osoite" "Kiinteist\u00f6tunnus"])

        (facts "Rows for downloads are correct"
          (nth-row rows :data 0) => ["Hyvink\u00e4\u00e4"    "document_store"   "15.06.2018 14.42" "LP-1" "IV-suunnitelma"          "Mokukatu 1" "753-426-11-11"]
          (nth-row rows :data 1) => ["Kirkkonummi" "document_store"   "26.06.2019 14.16" "LP-5" "Julkisivupiirustus"      "Tokukatu 5" "753-426-11-14"]
          (nth-row rows :data 2) => ["Kirkkonummi" "kirkkonummi_sito" "20.03.2018 14.09" "LP-4" "Katselmuksen p\u00f6yt\u00e4kirja" "Sokukatu 4" "753-426-11-14"]
          (nth-row rows :data 3) => ["Kirkkonummi" "kirkkonummi_sito" "26.06.2019 14.16" "LP-3" "Katselmuksen p\u00f6yt\u00e4kirja" "Rokukatu 3" "753-426-11-13"]
          (nth-row rows :data 4) => ["Sipoo"       "vantaa_sito"      "15.06.2018 14.42" nil nil nil nil]
          (nth-row rows :data 5) => [])

        (facts "Summary rows are correct"
          (nth-row rows :data 6) => ["Yhteenveto:"]
          (nth-row rows :data 7) => ["Organisaation nimi"  "Rajapintatunnus" "Latausten m\u00e4\u00e4r\u00e4"]
          (nth-row rows :data 8) => ["Hyvink\u00e4\u00e4" "document_store" 1]
          (nth-row rows :data 9) => ["Kirkkonummi" "document_store" 1]
          (nth-row rows :data 10) => ["Kirkkonummi" "kirkkonummi_sito" 2]
          (nth-row rows :data 11) => ["Sipoo" "vantaa_sito" 1])))

    (facts "Downloaded documents for 257-R"
      (let [rows (downloads-entries-sheets "257-R" "1461547760416" "1563547760416" "fi")]
        (fact "Header is correct"
          (:header rows) => ["Organisaation nimi" "Rajapintatunnus" "Tilausaika" "Hakemus" "Dokumenttityyppi" "Osoite" "Kiinteist\u00f6tunnus"])

        (facts "Rows for downloads are correct"
          (nth-row rows :data 0) => ["Kirkkonummi" "document_store"   "26.06.2019 14.16" "LP-5" "Julkisivupiirustus"      "Tokukatu 5" "753-426-11-14"]
          (nth-row rows :data 1) => ["Kirkkonummi" "kirkkonummi_sito" "20.03.2018 14.09" "LP-4" "Katselmuksen p\u00f6yt\u00e4kirja" "Sokukatu 4" "753-426-11-14"]
          (nth-row rows :data 2) => ["Kirkkonummi" "kirkkonummi_sito" "26.06.2019 14.16" "LP-3" "Katselmuksen p\u00f6yt\u00e4kirja" "Rokukatu 3" "753-426-11-13"]
          (nth-row rows :data 3) => ["Kirkkonummi" "kirkkonummi_sito" "08.07.2019 04.02" "LP-6" "Katselmuksen p\u00f6yt\u00e4kirja" "Nokukatu 4" "753-426-11-14"]
          (nth-row rows :data 4) => [])

        (facts "Summary rows are correct"
          (nth-row rows :data 5) => ["Yhteenveto:"]
          (nth-row rows :data 6) => ["Organisaation nimi" "Rajapintatunnus" "Latausten m\u00e4\u00e4r\u00e4"]
          (nth-row rows :data 7) => ["Kirkkonummi" "document_store" 1]
          (nth-row rows :data 8) => ["Kirkkonummi" "kirkkonummi_sito" 3])))))
