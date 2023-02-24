(ns lupapalvelu.municipality-api-test
  (:require [lupapalvelu.municipality-api :refer [active-municipalities-from-organizations]]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(def week-ago      (-> (date/now) (date/minus :week) (date/timestamp)))
(def week-from-now (-> (date/now) (date/plus :week) (date/timestamp)))

(def orgs [{:scope [{:permitType              "R"
                     :municipality            "753"
                     :new-application-enabled false
                     :inforequest-enabled     true
                     :open-inforequest        false
                     :opening                 week-ago}
                    {:permitType              "P"
                     :municipality            "186"
                     :new-application-enabled false
                     :inforequest-enabled     false
                     :open-inforequest        false
                     :opening                 week-from-now}]}
           {:scope [{:permitType              "YM"
                     :municipality            "753"
                     :new-application-enabled false
                     :inforequest-enabled     false
                     :open-inforequest        false
                     :opening                 week-from-now}
                    {:permitType              "YI"
                     :municipality            "753"
                     :new-application-enabled false
                     :inforequest-enabled     false
                     :open-inforequest        false
                     :opening                 week-from-now}]}])

(def kotka [{:scope [{:open-inforequest        false
                      :new-application-enabled true
                      :inforequest-enabled     true
                      :municipality            "285"
                      :permitType              "R"
                      :opening                 week-ago}]}
            {:scope [{:opening                 nil
                      :municipality            "285"
                      :permitType              "YI"
                      :inforequest-enabled     true
                      :open-inforequest        false
                      :new-application-enabled true
                      :open-inforequest-email  "" }
                     {:opening                 week-ago
                      :municipality            "285"
                      :permitType              "MAL"
                      :inforequest-enabled     true
                      :open-inforequest        false
                      :new-application-enabled true}
                     {:opening                 week-ago
                      :municipality            "285"
                      :permitType              "VVVL"
                      :inforequest-enabled     false
                      :open-inforequest        false
                      :new-application-enabled false}
                     {:municipality            "285"
                      :permitType              "YM"
                      :inforequest-enabled     false
                      :open-inforequest        false
                      :new-application-enabled false}
                     {:opening                 week-from-now
                      :municipality            "285"
                      :permitType              "YL"
                      :inforequest-enabled     false
                      :open-inforequest        false
                      :new-application-enabled false}]}])

(def today (sade.core/now))

(facts "Municipality facts"
  (fact "Sipoo and Jarvenpaa" (count (active-municipalities-from-organizations orgs today)) => 2)
  (facts "Sipoo"
    (util/find-by-id "753" (active-municipalities-from-organizations orgs today))
    => (contains {:id           "753"
                  :infoRequests (just "R")
                  :applications empty?
                  :opening      (just #{(just {:opening number? :permitType "YM"})
                                        (just {:opening number? :permitType "YI"})})})
    (fact "R is enabled"
      (let [sipoo (-<>> (assoc-in orgs [0 :scope 0 :new-application-enabled] true)
                        (active-municipalities-from-organizations <> today)
                        (util/find-by-id "753"))]
        (fact "Inforequests and applications true"
          sipoo => (contains {:id           "753"
                              :infoRequests (just "R")
                              :applications (just "R")}))
        (fact "opening does not have R anymore"
          sipoo => (contains {:opening (just #{(just {:opening number? :permitType "YM"})
                                               (just {:opening number? :permitType "YI"})})}))))
    (fact "Empty result"
      (let [empty (-> orgs
                      (assoc-in [0 :scope 0 :inforequest-enabled] false)
                      (assoc-in [0 :scope 0 :opening] nil)
                      (update-in [1 :scope 0] assoc :opening nil)
                      (update-in [1 :scope 1] assoc :opening nil))]
        (util/find-by-id "753" (active-municipalities-from-organizations empty today))
        => (contains {:id           "753"  :infoRequests empty?
                      :applications empty? :opening      empty?})))
    (fact "Only opening"
      (let [opening (-> orgs
                        (assoc-in [0 :scope 0 :inforequest-enabled] false)
                        (assoc-in [0 :scope 0 :opening] nil)
                        (update-in [1 :scope 0] assoc :opening week-from-now)
                        (update-in [1 :scope 1] assoc :opening nil))]
        (util/find-by-id "753" (active-municipalities-from-organizations opening today))
        => (contains {:id           "753" :infoRequests empty?
                      :applications empty?
                      :opening      (just [(just {:opening number? :permitType "YM"})])}))))
  (facts "Jarvenpaa"
    (fact "Only opening"
      (util/find-by-id "186" (active-municipalities-from-organizations orgs today))
      => (contains {:id           "186"
                    :applications empty?
                    :infoRequests empty?
                    :opening      (just #{(just {:opening number? :permitType "P"})})}))
    (fact "Open inforequests is set enabled"
      (-<>> (assoc-in orgs [0 :scope 1 :open-inforequest] true)
           (active-municipalities-from-organizations <> today)
           (util/find-by-id "186"))
      => (contains {:id           "186"
                    :applications empty?
                    :infoRequests (just "P")
                    :opening      (just #{(just {:opening number? :permitType "P"})})})))

  (facts "Kotka"
    (count (active-municipalities-from-organizations kotka today)) => 1
    (util/find-by-id "285" (active-municipalities-from-organizations kotka today))
    => (contains {:id           "285"
                  :applications (just ["R" "YI" "MAL"] :in-any-order)
                  :infoRequests (just ["R" "YI" "MAL"] :in-any-order)
                  :opening      (just [(just {:opening pos? :permitType "YL"})])})))
