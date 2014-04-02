(ns lupapalvelu.application-search-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            ))

(apply-remote-minimal)

(facts* "Search"
  (let [application-id-addr (create-app-id mikko :municipality sonja-muni :address "Hakukuja 123") => truthy]

    (facts "by address"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search "hakukatu"})]
          results0 => ok?
          (get-in results0 [:data :iTotalDisplayRecords]) => 0
          (count (get-in results0 [:data :aaData])) => 0))

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search "hakukuja"})]
          results1 => ok?
          (get-in results1 [:data :iTotalDisplayRecords]) => 1
          (count (get-in results1 [:data :aaData])) => 1
          (get-in results1 [:data :aaData 0 :id]) => application-id-addr)))

    (facts "by ID"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search (str "LP-" sonja-muni "-2010-00001")})]
          results0 => ok?
          (get-in results0 [:data :iTotalDisplayRecords]) => 0
          (count (get-in results0 [:data :aaData])) => 0))

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search application-id-addr})]
          results1 => ok?
          (get-in results1 [:data :iTotalDisplayRecords]) => 1
          (count (get-in results1 [:data :aaData])) => 1
          (get-in results1 [:data :aaData 0 :id]) => application-id-addr))
      )

    )
  )

