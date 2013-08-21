(ns lupapalvelu.organization-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [lupapalvelu.core :refer [ok?]]
            [lupapalvelu.itest-util :refer [pena query]]
            [sade.util :refer [fn->]]))

(fact "Operation details query"
   (let [resp  (query pena "organization-details" :municipality "753" :operation "asuinrakennus")]
     resp => ok?
     resp => (fn-> :attachmentsForOp count (> 0))
     resp => (fn-> :links count (> 0))))
