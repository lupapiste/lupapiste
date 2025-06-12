(ns lupapalvelu.exports-test
  (:require [clojure.set :refer [difference]]
            [lupapalvelu.application :as app]
            [lupapalvelu.building-types :refer [rakennuksen-rakennusluokka]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.exports :as exports
             :refer [exported-application kayttotarkoitus-hinnasto
                     rakennusluokka-hinnasto price-classes-for-operation
                     permit-type-price-codes]]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.permit :as permit]
            [lupapiste-commons.usage-types :as usages]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]
            [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]))

(testable-privates lupapalvelu.exports resolve-price-class)

(def keyset (comp set keys))

(fact "Every operation has price class definition"
  (difference (keyset ops/operations) (keyset price-classes-for-operation)) => empty?)

(fact "Every kayttotarkoitus has price class"
  (let [every-kayttotarkoitus (map :name usages/rakennuksen-kayttotarkoitus)]
    (difference (set every-kayttotarkoitus) (keyset @kayttotarkoitus-hinnasto))) => empty?)

(fact "Every rakennusluokka has price class"
  (let [every-rakennusluokka (map :name rakennuksen-rakennusluokka)]
    (difference (set every-rakennusluokka) (keyset @rakennusluokka-hinnasto)))
  => empty?)

(fact "Every permit type has price code"
  (doseq [permit-type (keys (permit/permit-types))]
    (fact {:midje/description permit-type}
      (get permit-type-price-codes permit-type) => number?)))

(fact "Uusi kerrostalo-rivitalo"
  (let [app-info      (util/assoc-when-pred
                        {:id             (ssg/generate ssc/ApplicationId)
                         :organization   {:id    "753-R"
                                          :name  {:fi "Testi" :sv "Testi"}
                                          :scope [{:permitType   "R" :inforequest-enabled false :new-application-enabled false
                                                   :municipality "753"}]}
                         :operation-name "kerrostalo-rivitalo"
                         :location       (app/->location (ssg/generate ssc/LocationX)
                                                         (ssg/generate ssc/LocationY))
                         :propertyId     "01234567891234"
                         :address        "address"}
                        ss/not-blank?
                        :propertyIdSource "location-service"
                        :municipality "753")
        application   (app/make-application app-info [] {} 123 nil)
        application   (assoc application :documents (-> (vec (:documents application)) (assoc-in [1 :data :kaytto :kayttotarkoitus] {:value "021 rivitalot"})))
        uusi-rakennus (domain/get-document-by-name application "uusiRakennus")]

    (fact "Default value '021 rivitalot' = B"
      (let [op (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "B"
        (:priceCode op) => 900
        (:use op) => "021 rivitalot"
        (:useFi op) => "021 rivitalot"
        (:useSv op) => "021 radhus"
        (:usagePriceCode op) => 906))

    (fact "Missing value defaults to C"
      (let [doc         (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus] {})
            application (assoc application :documents [doc])
            op          (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "C"
        (:usagePriceCode op) => 907))

    (fact "Empty value defaults to C"
      (let [doc         (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "")
            application (assoc application :documents [doc])
            op          (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "C"
        (:usagePriceCode op) => 907))

    (fact "021 rivitalot = B"
      (let [doc         (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "021 rivitalot")
            application (assoc application :documents [doc])
            op          (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "B"
        (:usagePriceCode op) => 906))

    (fact "041 vapaa-ajan asuinrakennukset = C"
      (let [doc         (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "041 vapaa-ajan asuinrakennukset")
            application (assoc application :documents [doc])
            op          (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "C"
        (:usagePriceCode op) => 907))

    (fact "121 hotellit yms = A"
      (let [doc         (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "121 hotellit yms")
            application (assoc application :documents [doc])
            op          (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "A"
        (:usagePriceCode op) => 905))

    (fact "999 muualla luokittelemattomat rakennukset = D"
      (let [doc         (assoc-in uusi-rakennus [:data :kaytto :kayttotarkoitus :value] "999 muualla luokittelemattomat rakennukset")
            application (assoc application :documents [doc])
            op          (resolve-price-class application (:primaryOperation application))]
        (:priceClass op) => "D"
        (:usagePriceCode op) => 908))

    (facts "Resolving price using rakennusluokka"
      (against-background
        [(lupapalvelu.organization/get-application-organization anything)
         => {:rakennusluokat-enabled true
             :krysp                  {:R {:version "2.2.4"}}}]
        (fact "If rakennusluokka and kayttotarkoitus both have value rakennusluokka AND rakennusluokka enabled, it is used to resolve price"
          (let [doc         (assoc-in uusi-rakennus [:data :kaytto :rakennusluokka :value] "0112 rivitalot")
                doc         (assoc-in doc [:data :kaytto :kayttotarkoitus :value] "121 hotellit yms")
                application (assoc application :documents [doc])
                op          (resolve-price-class application (:primaryOperation application))]
            (:priceClass op) => "B"
            (:usagePriceCode op) => 906
            (:use op) => "0112 rivitalot"
            (:useFi op) => "0112 rivitalot")))

      (against-background
        [(lupapalvelu.organization/get-application-organization anything) => {}]
        (fact "Rakennusluokka and kayttotarkoitus both have value but rakennusluokka not enabled."
          (let [doc         (assoc-in uusi-rakennus [:data :kaytto :rakennusluokka :value] "0112 rivitalot")
                doc         (assoc-in doc [:data :kaytto :kayttotarkoitus :value] "121 hotellit yms")
                application (assoc application :documents [doc])
                op          (resolve-price-class application (:primaryOperation application))]
            (:priceClass op) => "A"
            (:usagePriceCode op) => 905
            (:use op) => "121 hotellit yms"
            (:useFi op) => "121 hotellit yms.")))

      (against-background
        [(lupapalvelu.organization/get-application-organization anything)
         => {:rakennusluokat-enabled true
             :krysp                  {:R {:version "2.2.4"}}}]
        (fact "Rakennusluokka is empty => kayttotarkoitus is used to resolve price"
          (let [doc         (assoc-in uusi-rakennus [:data :kaytto :rakennusluokka :value] "")
                doc         (assoc-in doc [:data :kaytto :kayttotarkoitus :value] "121 hotellit yms")
                application (assoc application :documents [doc])
                op          (resolve-price-class application (:primaryOperation application))]
            (:priceClass op) => "A"
            (:usagePriceCode op) => 905
            (:use op) => "121 hotellit yms"
            (:useFi op) => "121 hotellit yms."))

        (fact "0211 osavuotiseen käyttöön soveltuvat vapaa-ajan asuinrakennukset => C"
          (let [doc         (assoc-in uusi-rakennus [:data :kaytto :rakennusluokka :value] "0211 osavuotiseen käyttöön soveltuvat vapaa-ajan asuinrakennukset")
                application (assoc application :documents [doc])
                op          (resolve-price-class application (:primaryOperation application))]
            (:priceClass op) => "C"
            (:usagePriceCode op) => 907
            (:use op) => "0211 osavuotiseen käyttöön soveltuvat vapaa-ajan asuinrakennukset"
            (:useFi op) => "0211 osavuotiseen käyttöön soveltuvat vapaa-ajan asuinrakennukset"))

        (fact "0320 hotellit = A"
          (let [doc         (assoc-in uusi-rakennus [:data :kaytto :rakennusluokka :value] "0320 hotellit")
                application (assoc application :documents [doc])
                op          (resolve-price-class application (:primaryOperation application))]
            (:priceClass op) => "A"
            (:usagePriceCode op) => 905
            (:use op) => "0320 hotellit"
            (:useFi op) => "0320 hotellit"))))))

(facts "YA jatkoaika"
  (let [application {:permitType "YA"}
        op (resolve-price-class application {:name "ya-jatkoaika"})]
    (:priceClass op) => "D"
    (:priceCode op) => 902
    (:usagePriceCode op) => 908
    (:use op) => nil))

(fact "Paperilupa"
  (let [application {:permitType "R"}
        op (resolve-price-class application {:name "aiemmalla-luvalla-hakeminen"})]
    (:priceClass op) => "D"
    (:priceCode op) => 914
    (:usagePriceCode op) => 914
    (:use op) => nil))

(fact "verdict"
  (let [application {:verdicts [(domain/->paatos {:verdictId 1 :backendId "kuntalupatunnus1" :text "paatosteksti" :timestamp 10 :name "Viranomainen" :given 9 :status :1 :official 11})]}
        {verdics :verdicts} (exported-application application)
        verdict (first verdics)]
    (count verdics) => 1
    verdict => {:id 1
                :kuntalupatunnus "kuntalupatunnus1"
                :timestamp 10
                :paatokset [{:paatoksentekija "Viranomainen"
                             :paatos "paatosteksti"
                             :paatospvm 9
                             :anto 9
                             :lainvoimainen 11}]}))

;;
;; Export Onkalo API usage
;;
(defn to-long [& args]
  (->> (zipmap [:year :month :day :hour :minute :second :zone-id] args)
       date/zoned-date-time
       date/timestamp))

(facts exports/timestamp->end-of-month-date-string
  (fact "returns timestamp string representing the last day of the timestamp's month"
    (exports/timestamp->end-of-month-date-string (to-long 2018 5 3 9 45 54))
    => "2018-05-31")
  (fact "handles leap days"
    (exports/timestamp->end-of-month-date-string (to-long 2016 2 21 10 11 0))
    => "2016-02-29"
    (exports/timestamp->end-of-month-date-string (to-long 2016 2 29 10 11 0))
    => "2016-02-29")
  (fact "interprets timestamps in Helsinki time zone"
    (exports/timestamp->end-of-month-date-string (to-long 2018 5 31 23 59 0 date/utc-zone-id))
    => "2018-06-30"))

(facts exports/onkalo-log-entries->salesforce-export-entries
  (fact "no log entries results in no export entries"
    (exports/onkalo-log-entries->salesforce-export-entries [] 1234)
    => {:lastRunTimestampMillis 1235
        :transactions []})
  (fact "single entry results in single entry"
    (exports/onkalo-log-entries->salesforce-export-entries [{:organization "753-R"
                                                             :timestamp (to-long 2018 5 3)
                                                             :logged (to-long 2018 5 3)}]
                                                           1234)
    => {:lastRunTimestampMillis (inc (to-long 2018 5 3))
        :transactions [{:organization "753-R" :lastDateOfTransactionMonth "2018-05-31" :quantity 1}]})
  (fact "quantity shows the number of entries for given organization in given month"
    (exports/onkalo-log-entries->salesforce-export-entries [{:organization "753-R" :timestamp (to-long 2018 5 3) :logged (to-long 2018 5 3)}
                                                            {:organization "753-R" :timestamp (to-long 2018 5 3) :logged (to-long 2018 5 4)}]
                                                           1234)
    => {:lastRunTimestampMillis (inc (to-long 2018 5 4))
        :transactions [{:organization "753-R" :lastDateOfTransactionMonth "2018-05-31" :quantity 2}]})
  (fact "each organization has own entries"
    (-> (exports/onkalo-log-entries->salesforce-export-entries [{:organization "753-R" :timestamp (to-long 2018 5 3) :logged (to-long 2018 5 3)}
                                                                {:organization "091-R" :timestamp (to-long 2018 5 3) :logged (to-long 2018 5 3)}]
                                                               1234)
        :transactions)
    => (contains [{:organization "753-R" :lastDateOfTransactionMonth "2018-05-31" :quantity 1} {:organization "091-R" :lastDateOfTransactionMonth "2018-05-31" :quantity 1}]
                 :in-any-order))
  (fact "each month has own entries"
    (-> (exports/onkalo-log-entries->salesforce-export-entries [{:organization "753-R" :timestamp (to-long 2018 5 3) :logged (to-long 2018 5 3)}
                                                                {:organization "753-R" :timestamp (to-long 2018 6 3) :logged (to-long 2018 6 3)}]
                                                               1234)
        :transactions)
    => (contains [{:organization "753-R" :lastDateOfTransactionMonth "2018-05-31" :quantity 1} {:organization "753-R" :lastDateOfTransactionMonth "2018-06-30" :quantity 1}]
                 :in-any-order))
  (fact "throws on invalid input data"
    (exports/onkalo-log-entries->salesforce-export-entries [{:not-organization "753-R" :timestamp (to-long 2018 5 3) :logged (to-long 2018 5 3)}
                                                            {:organization "753-R" :timestamp (to-long 2018 6 3) :logged (to-long 2018 6 3)}]
                                                           1234)
    => (throws #"does not match schema")))
