(ns lupapalvelu.application-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]]))

(defn- not-empty? [m] (not (empty? m)))

(fact
  (let [resp            (command pena :create-application :permitType "buildingPermit" :x 444444 :y 6666666 :street "s" :city "c" :zip "z")
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)]
    application => (contains {:id application-id
                              :state "draft"
                              :location {:x 444444 :y 6666666}
                              :permitType "buildingPermit"})
    (first (:auth application)) => (contains
                                     {:firstName "Pena"
                                      :lastName "Panaani"
                                      :type "owner"
                                      :role "owner"})
    (:allowedAttahmentTypes application) => not-empty?))
