(ns lupapalvelu.common-actions-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.common-actions]))

(fact (executed "ping" {:action "ping"}) => {:ok true :text "pong"})
(fact (executed {:action "ping"}) => {:ok true :text "pong"})
