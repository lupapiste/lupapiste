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

       )
