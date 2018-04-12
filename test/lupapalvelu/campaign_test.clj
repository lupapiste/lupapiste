(ns lupapalvelu.campaign-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.campaign :as camp]
            [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clj-time.coerce :as coerce]))

(testable-privates lupapalvelu.campaign
                   timestamp datestring command->campaign campaign->front)

(defn err [error]
  (contains {:ok false :text (name error)}))

(def good {:code             "good"
           :starts           "2017-4-3"
           :ends             "2017-5-3"
           :account5         1
           :account15        2
           :account30        3
           :lastDiscountDate "2017-7-1"})

(facts "Good campaign pre-checks"
       (fact "Good"
             (camp/good-campaign {:data good}) => nil?)
       (fact "Missing key"
             (camp/good-campaign {:data (dissoc good :account15)})
             => (err :error.invalid-campaign))
       (fact "Account must be number"
             (camp/good-campaign {:data (assoc good :account15 "foo")})
             => (err :error.invalid-campaign))
       (fact "Date format is yyyy-mm-dd"
             (camp/good-campaign {:data (assoc good :lastDiscountDate "1.3.2018")})
             => (err :error.invalid-campaign))
       (fact "Campaign cannot be activated before start"
             (camp/good-campaign {:data (assoc good :starts "2017-5-5")})
             => (err :error.campaign-period))
       (fact "Discount date cannot be before activation period ends"
             (camp/good-campaign {:data (assoc good :lastDiscountDate "2015-5-5")})
             => (err :error.campaign-last-date))
       (fact "Pricing must be consistent: small teams cannot be pricier than bigger teams"
             (camp/good-campaign {:data (assoc good :account5 10)})
             => (err :error.campaign-pricing))
       (fact "No negative prices"
             (camp/good-campaign {:data (assoc good :account5 -1)})
             => (err :error.campaign-pricing))
        (fact "Code must be in the correct format"
             (camp/good-campaign {:data (assoc good :code "no spaces")})
             => (err :error.invalid-campaign))
         (fact "Code is trimmed and case does not matter"
               (camp/good-campaign {:data (assoc good :code "  SpAcEs  ")})
             => nil?
             (camp/code->id "  SpAcEs  ") => "spaces"))

(facts "Date and time handling"
       (fact "To timestamp and back"
             (-> good :starts timestamp datestring) => "2017-04-03")
       (fact "Timestamp takes timezone into account"
             (let [ts (timestamp (:ends good))
                   utc (-> good :ends coerce/to-long)]
               (fact "Timestamps are not equal"
                     ts =not=> utc)
               (fact "Offset"
                     (+ ts (.getOffset (time/default-time-zone) ts)) => utc)
               (fact "Custom formatter"
                     (datestring ts (fmt/formatter "d.M.yyyy"))
                     => "3.5.2017"))))

(facts "Campaign back and front"
       (let [back  (command->campaign {:data (assoc good
                                                    :code "   HiiHoo  "
                                                    :ends (:starts good))})]
         (fact "Code to id and unchanged properties"
               back => (contains {:id               "hiihoo"
                                  :account5         1
                                  :account15        2
                                  :account30        3
                                  :lastDiscountDate "2017-7-1"}))
         (fact "Period as timestamps"
               (:ends back) => (+ (:starts back) (* (+ (* (+ (* 23 60) 59) 60)
                                                       59)
                                                    1000) ;; 23:59:59
                                  ))
         (fact "Front version has neither id nor timestamps"
               (campaign->front back) => {:code             "hiihoo"
                                          :starts           "2017-04-03"
                                          :ends             "2017-04-03"
                                          :account5         1
                                          :account15        2
                                          :account30        3
                                          :lastDiscountDate "2017-7-1"})))

(fact "Contract info"
      (camp/contract-info {:campaign "good" :accountType "account15"})
      => {:price 2
          :lastDiscount "1.7.2017"
          :firstRegular "2.7.2017"}
      (provided (camp/active-campaign "good") => good))

(fact "Contract info assert"
      (camp/contract-info {:campaign "good" :accountType "account15"})
      => (throws AssertionError #"Campaign good is not active.")
      (provided (camp/active-campaign "good") => nil))

(fact "Campaign is active prechecker: ok"
      (camp/campaign-is-active {:data {:company {:campaign "good"}}}) => nil?
      (provided (camp/active-campaign "good") => good))

(fact "Campaign is active prechecker: fail"
      (camp/campaign-is-active {:data {:company {:campaign "bad"}}})
      => (err :error.campaign-not-found)
      (provided (camp/active-campaign "bad") => nil))

(fact "Campaign is active prechecker: blank is ok"
      (camp/campaign-is-active {:data {:company {:campaign "  "}}}) => nil)
(fact "Campaign is active prechecker: missing is ok"
      (camp/campaign-is-active {:data {:company {}}}) => nil)
