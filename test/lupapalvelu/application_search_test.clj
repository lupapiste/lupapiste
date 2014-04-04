(ns lupapalvelu.application-search-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [lupapalvelu.test-util :refer :all]
             [lupapalvelu.application-search :refer :all]))

(testable-privates lupapalvelu.application-search make-sort make-query operation-names)

(facts "operation-names"
  (operation-names "bil") => [:auto-katos]
  (operation-names "grilli") => [:auto-katos]
  (operation-names "Ty\u00f6njohtaja") => [:tyonjohtajan-nimeaminen]
  (operation-names "ANNAN") => [:muu-tontti-tai-kort-muutos :ya-kayttolupa-muu-kayttolupa :muu-laajentaminen :varasto-tms :mainoslaite]
  (operation-names "S\u00e4hk\u00f6-, data ja muiden kaapelien sijoittaminen") => [:ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen])

(facts "sorting parameter parsing"
  (make-sort {:iSortCol_0 0 :sSortDir_0 "asc"})  => {:infoRequest 1}
  (make-sort {:iSortCol_0 1 :sSortDir_0 "desc"}) => {:address -1}
  (make-sort {:iSortCol_0 2 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 3 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 4 :sSortDir_0 "asc"})  => {:submitted 1}
  (make-sort {:iSortCol_0 5 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 6 :sSortDir_0 "desc"}) => {}
  (make-sort {:iSortCol_0 7 :sSortDir_0 "asc"})  => {:modified 1}
  (make-sort {:iSortCol_0 8 :sSortDir_0 "asc"})  => {:state 1}
  (make-sort {:iSortCol_0 9 :sSortDir_0 "asc"})  => {:authority 1}
  (make-sort {:iSortCol_0 {:injection "attempt"}
              :sSortDir_0 "; drop database;"})   => {}
  (make-sort {})                                 => {}
  (make-sort nil)                                => {})

(fact "make-query (LUPA-519) with filter-user checks both authority and auth.id"
  (make-query {} {:filter-kind  "both"
                  :filter-state "all"
                  :filter-user  "123"}
              {:role "authority"}) => (contains {"$or" [{"auth.id" "123"} {"authority.id" "123"}]}))
