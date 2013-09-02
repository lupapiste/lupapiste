(ns lupapalvelu.organization-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.organization :refer :all]
            [lupapalvelu.core :refer [ok?]]
            [lupapalvelu.itest-util :refer [pena query]]
            [sade.util :refer [fn->]]))

(facts "Organization itests"
  (fact "Operation details query works"
   (let [resp  (query pena "organization-details" :municipality "753" :operation "asuinrakennus" :lang "fi")]
     resp => ok?
     resp => (fn-> :attachmentsForOp count (> 0))
     resp => (fn-> :links count (> 0)))))
