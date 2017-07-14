(ns lupapalvelu.statement.statement-canonical-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.statement-canonical :refer :all]
            [lupapalvelu.statement :as stmnt]
            [clojure.test.check.properties :as prop]
            [sade.util :as util]
            [sade.shared-schemas :as sschemas]
            [sade.schema-generators :as ssg]
            [lupapalvelu.user :as usr]
            [clojure.test.check.generators :as gen]
            [sade.schema-utils :as ssu]))


(fact "statement to canonical"
  (let [statement (stmnt/create-statement 123 nil "saate" (util/get-timestamp-from-now :day 7) (ssg/generate stmnt/StatementGiver))
        statement-request-canonical (statement-as-canonical
                                      {:firstName "Sonja" :lastName "Sibbo" :organization {:fi "Sipoo"}}
                                      statement
                                      "fi")]
    (fact "statement model format"
      statement => (just {:dueDate integer?
                          :requested integer?
                          :id (partial re-matches sschemas/object-id-pattern)
                          :person map?
                          :saateText "saate"
                          :state :requested}))
    (fact "canonical ok"
      statement-request-canonical => map?
      statement-request-canonical => (just {:LausuntoTunnus (:id statement)
                                            :Saateteksti (:saateText statement)
                                            :Pyytaja     (just "Sipoo (Sonja Sibbo)")
                                            :PyyntoPvm   (util/to-xml-date (get statement :requested))
                                            :Maaraaika   (util/to-xml-date (get statement :dueDate))
                                            :Lausunnonantaja (contains (get-in statement [:person :name]))})))
  #_(prop/for-all [statement (ssg/generator (ssu/select-keys stmnt/Statement [:id :person :requested :state]))]))
