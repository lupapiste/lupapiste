(ns lupapalvelu.price-catalogues-test
  (:require [lupapalvelu.price-catalogues :as catalogues]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :refer [timestamp]]))

(testable-privates lupapalvelu.price-catalogues
                   valid-catalog? compare-catalogs)

(defn catalogue-with [properties]
  (merge {:id              "foo-id"
          :organization-id "753-R"
          :state           "draft"
          :valid-from      (timestamp "1.1.2000")
          :rows            [{:code              "12345"
                             :text              "Taksarivi 1"
                             :unit              "kpl"
                             :price-per-unit    23
                             :discount-percent  50
                             :product-constants {:kustannuspaikka  "a"
                                                 :alv              "b"
                                                 :laskentatunniste "c"
                                                 :projekti         "d"
                                                 :kohde            "e"
                                                 :muu-tunniste     "f"}
                             :operations        ["toimenpide1" "toimenpide2"]}]
          :meta            {:created    (timestamp "01.12.2019")
                            :created-by {:id        "penan-id"
                                         :firstName "pena"
                                         :lastName  "panaani"
                                         :role      "authority"
                                         :email     "pena@panaani.fi"
                                         :username  "pena"}}}
         properties))

(defn row-with [properties]
  (merge {:code              "12345"
          :text              "Taksarivi 1"
          :unit              "kpl"
          :price-per-unit    23
          :discount-percent  50
          :product-constants {:kustannuspaikka  "a"
                              :alv              "b"
                              :laskentatunniste "c"
                              :projekti         "d"
                              :kohde            "e"
                              :muu-tunniste     "f"}
          :operations        ["toimenpide1" "toimenpide2"]}
         properties))


(def billable-work-start "20.2.2019")
(def billable-work-end "30.5.2019")

(facts no-billing-period-days-in-billable-work-time
       (fact "Returns #{} when billable-work-start is 20.2.2019 and end is 30.5.2019 and no no-billing-periods"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {}) => #{})
       (fact "Returns #{23.2.2019, 24.2.2019} when billing period is {:1 {:start 23.2.2019 :end 24.2.2019}}"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "23.2.2019" :end "24.2.2019"}})
             => #{"23.2.2019" "24.2.2019"})
       (fact "Returns #{23.2.2019, 24.2.2019} when billing period is {:1 {:start 23.2.2019 :end 24.2.2019}} :2 {:start 24.2.2019 :end 27.2.2019}"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "23.2.2019" :end "25.2.2019"} :2 {:start "24.2.2019" :end "27.2.2019"}})
             => #{"23.2.2019" "24.2.2019" "25.2.2019" "26.2.2019" "27.2.2019"})
       (fact "Returns #{23.2.2019, 24.2.2019} when billing period is {:1 {:start 23.2.2019 :end 24.2.2019}}"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "23.2.2019" :end "25.2.2019"}
               :2 {:start "24.2.2019" :end "27.2.2019"}})
             => #{"23.2.2019" "24.2.2019" "25.2.2019" "26.2.2019" "27.2.2019"})
       (fact "Returns #{20.2.2019, 21.2.2019} when billing period is {:1 {:start 20.2.2019 :end 21.2.2019}} tests that work start date is included"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "20.2.2019" :end "21.2.2019"}})
             => #{"20.2.2019" "21.2.2019"})
       (fact "Returns #{29.5.2019, 30.5.2019} when billing period is {:1 {:start 29.5.2019 :end 30.5.2019}} tests that work end date is included"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "29.5.2019" :end "30.5.2019"}})
             => #{"29.5.2019" "30.5.2019"})
       (fact "Returns #{20.2.2019, 21.2.2019} when billing period is {:1 {:start 10.2.2019 :end 21.2.2019}} tests that counts only start overlap"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "10.2.2019" :end "21.2.2019"}})
             => #{"20.2.2019" "21.2.2019"})
       (fact "Returns #{29.5.2019, 30.5.2019} when billing period is {:1 {:start 29.5.2.2019 :end 5.6.2019}} tests that counts only end overlap"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "29.5.2019" :end "5.6.2019"}})
             => #{"29.5.2019" "30.5.2019"})
       (fact "Returns #{} when billable work start is later than billable work end"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-end
              billable-work-start
              {:1 {:start "29.5.2019" :end "5.6.2019"}})
             => #{})
       (fact "Returns #{29.5.2019 30.5.2019} when billable work start is {:1 {:start 29.5.2.2019 :end 5.6.2019} :2 {:start 20.2.2019 :end 19.2.2019}} tests invalid values in no-billing-periods"
             (catalogues/no-billing-period-days-in-billable-work-time
              billable-work-start
              billable-work-end
              {:1 {:start "29.5.2019" :end "5.6.2019"}
               :2 {:start "20.2.2019" :end "19.2.2019"}})
             => #{"29.5.2019" "30.5.2019"}))

(facts valid-catalog?
  (valid-catalog? {} 12345) => true
  (valid-catalog? {:valid-from nil :valid-until nil} 12345) => true
  (valid-catalog? {:valid-from 10000 :valid-until 20000} 12345) => true
  (valid-catalog? {:valid-from 10000 :valid-until 20000} 12345) => true
  (valid-catalog? {:valid-from 10000} 12345) => true
  (valid-catalog? {:valid-from 10000 :valid-until nil} 12345) => true
  (valid-catalog? {:valid-until 20000} 12345) => true
  (valid-catalog? {:valid-from nil :valid-until 20000} 12345) => true
  (valid-catalog? {:valid-from 10000 :valid-until 12000} 12345) => false
  (valid-catalog? {:valid-until 12000} 12345) => false
  (valid-catalog? {:valid-from nil :valid-until 12000} 12345) => false
  (valid-catalog? {:valid-from 130000 :valid-until 20000} 12345) => false
  (valid-catalog? {:valid-from 130000} 12345) => false
  (valid-catalog? {:valid-from 130000 :valid-until nil} 12345) => false)

(defn make-catalog [from until modified]
  {:valid-from from :valid-until until :meta {:modified modified}})

(facts compare-catalogs
  (compare-catalogs 12345 (make-catalog nil nil 1000) (make-catalog nil nil 2000)) => 1
  (compare-catalogs 12345 (make-catalog nil nil 1000) (make-catalog nil nil 1000)) => 0
  (compare-catalogs 12345 (make-catalog nil nil 2000) (make-catalog nil nil 1000)) => -1
  (compare-catalogs 12345 (make-catalog nil nil 1000) (make-catalog 13000 nil 2000)) => -1
  (compare-catalogs 12345 (make-catalog nil 10000 3000) (make-catalog nil nil 2000)) => 1)

(let [catalogs [(make-catalog nil nil 10)
                (make-catalog 10000 20000 9)
                (make-catalog nil 10000 8)
                (make-catalog 20000 nil 7)]]
 (fact application-price-catalogues
   (catalogues/application-price-catalogues {:organization "FOO-R"} 12345) => catalogs
   (provided (lupapalvelu.mongo/select :price-catalogues anything)
             => (shuffle catalogs))))
