(ns lupapalvelu.application-search-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [lupapalvelu.test-util :refer :all]
             [lupapalvelu.application-search :refer :all]))

(testable-privates lupapalvelu.application-search make-sort)

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
