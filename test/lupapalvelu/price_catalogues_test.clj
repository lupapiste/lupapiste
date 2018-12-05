(ns lupapalvelu.price-catalogues-test

  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [clj-time.format :as timeformat]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.price-catalogues :as catalogues]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :refer [to-millis-from-local-date-string to-finnish-date]]
            [taoensso.timbre :refer [trace tracef debug info infof warn warnf error errorf fatal spy]]))

(def timestamp to-millis-from-local-date-string)

(defn catalogue-with [properties]
  (merge {:id "foo-id"
          :organization-id "753-R"
          :state "draft"
          :valid-from (timestamp "1.1.2000")
          :valid-until nil
          :rows [{:code "12345"
                  :text "Taksarivi 1"
                  :unit "kpl"
                  :price-per-unit 23
                  :min-total-price nil
                  :max-total-price nil
                  :discount-percent 50
                  :operations ["toimenpide1" "toimenpide2"]}]
          :meta {:created (timestamp "01.12.2019")
                 :created-by {:id         "penan-id"
                              :firstName  "pena"
                              :lastName   "panaani"
                              :role       "authority"
                              :email      "pena@panaani.fi"
                              :username   "pena"}}}
         properties))

(facts "get-valid-price-catalogue-on-day"
       (fact "returns nil when no catalogues"
             (catalogues/get-valid-price-catalogue-on-day [] (timestamp "1.1.2018")) => nil?)

       (fact "returns nil when time before the start of any catalogue"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "5.5.2020")})]]
               (catalogues/get-valid-price-catalogue-on-day later-catalogues (timestamp "1.1.2018"))) => nil?)

       (fact "returns nil when time after all catalogues have ended"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018") :valid-until (timestamp "3.2.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018") :valid-until (timestamp "4.5.2020")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "5.5.2020") :valid-until (timestamp "9.8.2021")})]]
               (catalogues/get-valid-price-catalogue-on-day later-catalogues (timestamp "10.8.2021"))) => nil?)

       (fact "returns the catalogue that has valid-from exactly at timestamp and valid-until not set"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018") :valid-until (timestamp "3.2.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018") :valid-until (timestamp "4.5.2020")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "5.5.2020") :valid-until nil})]]
               (-> later-catalogues
                   (catalogues/get-valid-price-catalogue-on-day (timestamp "5.5.2020"))
                   :id) => "3"))

       (fact "returns the catalogue that has valid-from before the timestamp valid-until not set"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018") :valid-until (timestamp "3.2.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018") :valid-until (timestamp "4.5.2020")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "5.5.2020") :valid-until nil})]]
               (-> later-catalogues
                   (catalogues/get-valid-price-catalogue-on-day (timestamp "6.5.2020"))
                   :id) => "3"))

       (fact "returns the catalogue that has valid-until before the timestamp and valid-until after the timestamp"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018") :valid-until (timestamp "3.2.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018") :valid-until (timestamp "4.5.2020")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "5.5.2020") :valid-until nil})]]
               (-> later-catalogues
                   (catalogues/get-valid-price-catalogue-on-day (timestamp "3.3.2020"))
                   :id) => "2"))

       (fact "returns the catalogue that has valid-until exatcly at timestamp"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018") :valid-until (timestamp "3.2.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018") :valid-until (timestamp "4.5.2020")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "5.5.2020") :valid-until nil})]]
               (-> later-catalogues
                   (catalogues/get-valid-price-catalogue-on-day (timestamp "4.5.2020"))
                   :id) => "2"))

       (fact "returns nil when multiple valid catalogues found"
             (let [later-catalogues [(catalogue-with {:id "1" :valid-from (timestamp "2.1.2018") :valid-until (timestamp "3.2.2018")})
                                     (catalogue-with {:id "2" :valid-from (timestamp "4.2.2018") :valid-until (timestamp "4.5.2020")})
                                     (catalogue-with {:id "3" :valid-from (timestamp "1.5.2020") :valid-until nil})]]

               (-> later-catalogues
                   (catalogues/get-valid-price-catalogue-on-day (timestamp "4.5.2020"))) => nil?)))


(fact "fetch-valid-catalogue"

      (fact "finds the correct one when all catalogues are in the state published"
            (let [earlier (catalogue-with {:id "1" :valid-from (timestamp "2.1.2021") :valid-until (timestamp "3.2.2021") :organization-id "753-R" :state "published"})
                  valid   (catalogue-with {:id "2" :valid-from (timestamp "4.2.2021") :valid-until (timestamp "4.5.2021") :organization-id "753-R" :state "published"})
                  later   (catalogue-with {:id "3" :valid-from (timestamp "5.5.2021") :valid-until nil                    :organization-id "753-R" :state "published"})]

              (with-redefs [catalogues/fetch-price-catalogues (fn [organization-id] [earlier valid later])]
                (:id (catalogues/fetch-valid-catalogue "753-R" (timestamp "3.3.2021")))) => "2"))

      (fact "returns nil when the only valid catalogue is in a draft state"
            (let [earlier (catalogue-with {:id "1" :valid-from (timestamp "2.1.2021") :valid-until (timestamp "3.2.2021") :organization-id "753-R" :state "published"})
                  valid   (catalogue-with {:id "2" :valid-from (timestamp "4.2.2021") :valid-until (timestamp "4.5.2021") :organization-id "753-R" :state "draft"})
                  later   (catalogue-with {:id "3" :valid-from (timestamp "5.5.2021") :valid-until nil                    :organization-id "753-R" :state "published"})]

              (with-redefs [catalogues/fetch-price-catalogues (fn [organization-id] [earlier valid later])]
                (:id (catalogues/fetch-valid-catalogue "753-R" (timestamp "3.3.2021")))) => nil)))
