(ns sade.excel-reader-test
  (:require [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.excel-reader :refer [read-map]]))

(fact "two-columns (dev resource)"
  (read-map "two-columns.xlsx")
  => {"123.0"                         435.0
      "Flag"                          true
      "Hello"                         "World"
      "Runeberg"                      (date/zoned-date-time "5.2.2022")
      "Sun May 01 00:00:00 EEST 2022" "May Day"})

(defn real-check
  "Every value is a string. Returns the parsed map."
  [resource-name]
  (let [m (read-map resource-name)]
    (fact "String values"
      (every? string? (vals m)) => true)
    m))

(facts "Real use cases"
  (fact "tutkinto-mapping"
    (real-check "tutkinto-mapping.xlsx")
    => (contains {"a"        "other"
                  "puuseppä" "kirvesmies"
                  "yo"       "other"}))

  (fact "kayttotarkoitus-hinnasto"
    (real-check "kayttotarkoitus-hinnasto.xlsx")
    => (contains {"011 yhden asunnon talot"                                        "C"
                  "541 järjestöjen, liittojen, työnantajien yms opetusrakennukset" "A"
                  "ei tiedossa"                                                    "C"}))

  (fact "rakennusluokka_hinnasto"
    (real-check "rakennusluokka_hinnasto.xlsx")
    => (contains {"0110 omakotitalot"                           "C"
                  "0910 yleiskäyttöiset teollisuushallit"       "A"
                  "1919 muualla luokittelemattomat rakennukset" "D"})))
