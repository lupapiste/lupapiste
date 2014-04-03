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
        application (create-and-submit-application mikko :municipality sonja-muni :address "Hakukuja 123" :propertyId (sade.util/to-property-id property-id)) => truthy
        application-id (:id application)
        id-matches? (fn [response] (= (get-in response [:data :aaData 0 :id]) application-id))]

    (facts "by address"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search "hakukatu"})]
          results0 => ok?
          results0 => no-results?)

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search "hakukuja"})]
          results1 => ok?
          results1 => one-result?
          results1 => id-matches?))))

    (facts "by ID"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search (str "LP-" sonja-muni "-2010-00001")})]
          results0 => ok?
          results0 => no-results?)

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search application-id})]
          results1 => ok?
          results1 => one-result?
          results1 => id-matches?))))

    (facts "by property ID"

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search (str sonja-muni "-123-0000-1230")})]
          results0 => ok?
          results0 => no-results?))

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search property-id})]
          results1 => ok?
          results1 => one-result?
          results1 => id-matches?)))


    (facts "by verdict ID"

      (fact "no verdict, matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search "Hakup\u00e4\u00e4t\u00f6s-2014"})]
          results0 => ok?
          results0 => no-results?))

      (command sonja :give-verdict :id application-id :verdictId "Hakup\u00e4\u00e4t\u00f6s-2014-1" :status 1 :name "" :given 123 :official 124) => ok?

      (fact "no matches"
        (let [results0 (datatables mikko :applications-for-datatables :params {:filter-search "Hakup\u00e4\u00e4t\u00f6s-2014-2"})]
          results0 => ok?
          results0 => no-results?))

      (fact "one match"
        (let [results1 (datatables mikko :applications-for-datatables :params {:filter-search "Hakup\u00e4\u00e4t\u00f6s-2014"})]
          results1 => ok?
          results1 => one-result?
          results1 => id-matches?)))

    ))

