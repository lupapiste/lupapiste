(ns lupapalvelu.pate.schema-helper-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.pate.schema-helper :as helper]))

(deftest all-verdict-dates-test
  (let [all    (helper/all-verdict-dates)
        r      (helper/all-verdict-dates :r)
        p      (helper/all-verdict-dates :p)
        ya     (helper/all-verdict-dates :ya)
        tj     (helper/all-verdict-dates :tj)
        oik    (helper/all-verdict-dates :oik)
        others (helper/all-verdict-dates :unknown)]
    (testing "all should include dates"
      (is (set/subset? r all))
      (is (set/subset? p all))
      (is (set/subset? ya all))
      (is (set/subset? tj all))
      (is (set/subset? oik all))
      (is (set/subset? others all)))
    (is (= #{:aloitettava :anto :julkipano :lainvoimainen :muutoksenhaku :voimassa
             :k-aloitettava :k-kuulutus :k-lainvoimainen :k-muutoksenhaku :k-voimassa}
           r))
    (is (= #{:anto :julkipano :lainvoimainen :muutoksenhaku :voimassa
             :k-kuulutus :k-lainvoimainen :k-muutoksenhaku :k-voimassa}
           p))
    (is (= #{:aloitettava :anto :julkipano :lainvoimainen :muutoksenhaku}
           ya))
    (is (= #{:anto :lainvoimainen :muutoksenhaku}
           tj))
    (is (= #{:aloitettava :anto :julkipano :lainvoimainen :muutoksenhaku :voimassa
             :k-aloitettava :k-kuulutus :k-lainvoimainen :k-muutoksenhaku :k-voimassa}
           oik))
    (is (= #{:aloitettava :anto :julkipano :lainvoimainen :muutoksenhaku :voimassa}
           others))))
