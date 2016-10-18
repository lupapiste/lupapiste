(ns lupapalvelu.assignment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.assignment-api :refer :all]))

(apply-remote-minimal)

(fact "only authorities can have assignments"
      (query sonja :assignments) => ok?
      (query pena :assignments)  => #(= (:text %) "error.unauthorized"))
