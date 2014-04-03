(ns lupapalvelu.application-search-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            ))

(apply-remote-minimal)

(defn- num-of-results? [n response]
  (and
    (= n (get-in response [:data :iTotalDisplayRecords]))
    (= n (count (get-in response [:data :aaData])))))

(def no-results? (partial num-of-results? 0))
(def one-result? (partial num-of-results? 1))

(facts* "Search"
  (let [property-id (str sonja-muni "-123-0000-1234")
        application-id-addr (create-app-id mikko :municipality sonja-muni :address "Hakukuja 123" :propertyId (sade.util/to-property-id property-id)) => truthy]

    (facts "by address"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search "hakukatu"})]
          results0 => ok?
          results0 => no-results?)

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search "hakukuja"})]
          results1 => ok?
          results1 => one-result?
          (get-in results1 [:data :aaData 0 :id]) => application-id-addr))))

    (facts "by ID"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search (str "LP-" sonja-muni "-2010-00001")})]
          results0 => ok?
          results0 => no-results?)

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search application-id-addr})]
          results1 => ok?
          results1 => one-result?
          ;(clojure.pprint/pprint results1)
          (get-in results1 [:data :aaData 0 :id]) => application-id-addr))))

    (facts "by property ID"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search (str sonja-muni "-123-0000-1230")})]
          results0 => ok?
          results0 => no-results?)

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search property-id})]
          results1 => ok?
          results1 => one-result?
          (get-in results1 [:data :aaData 0 :id]) => application-id-addr))))



    ))

