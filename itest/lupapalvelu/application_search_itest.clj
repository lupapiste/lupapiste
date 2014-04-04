(ns lupapalvelu.application-search-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet  :refer :all]
            ))

(apply-remote-minimal)

(defn- num-of-results? [n response]
  (and
    (ok? response)
    (= n (get-in response [:data :iTotalDisplayRecords]))
    (= n (count (get-in response [:data :aaData])))))

(def no-results? (partial num-of-results? 0))
(def one-result? (partial num-of-results? 1))

(defn- search [query-s]
  (datatables mikko :applications-for-datatables :params {:filter-search query-s}))

(facts* "Search"
  (let [property-id (str sonja-muni "-123-0000-1234")
        application (create-and-submit-application mikko
                      :municipality sonja-muni
                      :address "Hakukuja 123"
                      :propertyId (sade.util/to-property-id property-id)
                      :operation "muu-uusi-rakentaminen") => truthy
        application-id (:id application)
        id-matches? (fn [response]
                      (and
                        (one-result? response)
                        (= (get-in response [:data :aaData 0 :id]) application-id)))]

    (facts "by address"
      (fact "no matches" (search "hakukatu") => no-results?)
      (fact "one match" (search "hakukuja") => id-matches?))

    (facts "by ID"
      (fact "no matches" (search (str "LP-" sonja-muni "-2010-00001")) => no-results?)
      (fact "one match" (search application-id) => id-matches?)
      (fact "one match - lower case query" (search (clojure.string/lower-case application-id)) => id-matches?))

    (facts "by property ID"
      (fact "no matches" (search (str sonja-muni "-123-0000-1230")) => no-results?)
      (fact "one match" (search property-id) => id-matches?))

    (facts "by verdict ID"
      (fact "no verdict, matches" (search "Hakup\u00e4\u00e4t\u00f6s-2014") => no-results?)
      (command sonja :give-verdict :id application-id :verdictId "Hakup\u00e4\u00e4t\u00f6s-2014-1" :status 1 :name "" :given 123 :official 124) => ok?
      (fact "no matches" (search "Hakup\u00e4\u00e4t\u00f6s-2014-2") => no-results?)
      (fact "one match" (search "Hakup\u00e4\u00e4t\u00f6s-2014") => id-matches?))

    (facts "by operation name"
      (fact "no matches" (search "vaihtolavan sijoittaminen") => no-results?)
      (fact "one match" (search "Muun  rakennuksen:   rakentaminen") => id-matches?))

    ))

