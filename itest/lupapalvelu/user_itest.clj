(ns lupapalvelu.user-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.fixture :as fixture]
            [lupapalvelu.fixture.minimal]))

(fact
  (fixture/apply-fixture "minimal")
  (:user (query pena :get-user-info)) => (contains {:enabled true
                                                    :firstName "Pena"
                                                    :lastName "Panaani"
                                                    :username "pena"
                                                    :street "Paapankuja 12"
                                                    :city "Piippola"
                                                    :zip "010203"
                                                    :phone "0102030405"
                                                    :email "pena"
                                                    :personId "010203-0405"
                                                    :role "applicant"})
  (command pena :save-user-info :firstName "f" :lastName "l" :street "s" :city "c" :zip "z" :phone "p")
  (:user (query pena :get-user-info)) => (contains {:firstName "f"
                                                    :lastName "l"
                                                    :street "s"
                                                    :city "c"
                                                    :zip "z"
                                                    :phone "p"}))

