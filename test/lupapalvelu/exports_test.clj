(ns lupapalvelu.exports-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.set :refer [difference]]
            [lupapalvelu.exports-api :refer [kayttotarkoitus-hinnasto price-classes-for-operation permit-type-price-codes]]
            [lupapalvelu.application :as app]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.permit :as permit]
            [lupapiste-commons.usage-types :as usages]))

(testable-privates lupapalvelu.exports-api resolve-price-class)

(def keyset (comp set keys))

(fact "Every operation has price class definition"
  (difference (keyset ops/operations) (keyset price-classes-for-operation) ) => empty?)

(fact "Every kayttotarkoitus has price class"
  (let [every-kayttotarkoitus (map :name usages/rakennuksen-kayttotarkoitus)]
    (difference (set every-kayttotarkoitus) (keyset @kayttotarkoitus-hinnasto))) => empty?)

(fact "Every permit type has price code"
  (doseq [permit-type (keys (permit/permit-types))]
    (fact {:midje/description permit-type}
      (get permit-type-price-codes permit-type) => number?)))

(fact "Uusi kerrostalo-rivitalo"
  (let [application (app/make-application "LP-123" "kerrostalo-rivitalo" 0 0 "address" "01234567891234" "753" {:id "753-R"} false false [] {} 123 nil)
        uusi-rakennus (domain/get-document-by-name application "uusiRakennus")]

    (fact "Default value '021 rivitalot' = B"
      (let [op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "B"
        (:priceCode op) => 900
        (:usagePriceCode op) => 906))

    (fact "Missing value defaults to C"
      (let [doc (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus] {})
            application (assoc application :documents [doc])
            op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "C"
        (:usagePriceCode op) => 907))

    (fact "Empty value defaults to C"
      (let [doc (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "")
            application (assoc application :documents [doc])
            op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "C"
        (:usagePriceCode op) => 907))

    (fact "021 rivitalot = B"
      (let [doc (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "021 rivitalot")
            application (assoc application :documents [doc])
            op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "B"
        (:usagePriceCode op) => 906))

    (fact "041 vapaa-ajan asuinrakennukset = C"
      (let [doc (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "041 vapaa-ajan asuinrakennukset")
            application (assoc application :documents [doc])
            op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "C"
        (:usagePriceCode op) => 907))

    (fact "121 hotellit yms = A"
      (let [doc (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "121 hotellit yms")
            application (assoc application :documents [doc])
            op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "A"
        (:usagePriceCode op) => 905))

    (fact "999 muualla luokittelemattomat rakennukset = D"
      (let [doc (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "999 muualla luokittelemattomat rakennukset")
            application (assoc application :documents [doc])
            op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "D"
        (:usagePriceCode op) => 908))))

(facts "YA jatkoaika"
  (let [application {:permitType "YA"}
        op (resolve-price-class application {:name "ya-jatkoaika"})]
    (:priceClass op) => "D"
    (:priceCode op) => 902
    (:usagePriceCode op) => nil
    (:use op) => nil))
