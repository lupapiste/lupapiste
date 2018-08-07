(ns lupapalvelu.statement.statement-canonical-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.statement-canonical :refer :all]
            [lupapalvelu.statement :as stmnt]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [sade.schema-generators :as ssg]
            [sade.schema-utils :as ssu]
            [sade.shared-schemas :as sschemas]
            [sade.strings :as ss]
            [sade.util :as util]))

(def sonja-requester {:firstName "Sonja" :lastName "Sibbo" :organization {:name {:fi "Sipoo"}}})

(fact "statement to canonical"
  (let [statement (stmnt/create-statement 123 "saate" (util/get-timestamp-from-now :day 7) (ssg/generate stmnt/StatementGiver))
        statement-request-canonical (statement-as-canonical
                                      sonja-requester
                                      statement
                                      "fi")]
    (fact "statement model format"
      statement => (just {:dueDate integer?
                          :requested integer?
                          :id (partial re-matches sschemas/object-id-pattern)
                          :person map?
                          :saateText "saate"
                          :state :requested}))
    (fact "statement request"                               ; only base information for statement
      statement-request-canonical => map?
      statement-request-canonical => (just {:LausuntoTunnus (:id statement)
                                            :Saateteksti (:saateText statement)
                                            :Pyytaja     (just "Sipoo (Sonja Sibbo)")
                                            :PyyntoPvm   (util/to-xml-date (get statement :requested))
                                            :Maaraaika   (util/to-xml-date (get statement :dueDate))
                                            :Lausunnonantaja (contains (ss/trim (get-in statement [:person :name])))}))
    (fact :qc "statement response, full data"
      (tc/quick-check
        200
        (prop/for-all [full-statement (ssg/generator
                                        (ssu/select-keys
                                          stmnt/Statement
                                          [:id :saateText :status :text :dueDate :requested :given :person]))]
          (let [statement (statement-as-canonical sonja-requester full-statement "fi")]
            (fact "mandatory keys"
              statement => (contains [:LausuntoTunnus  (:id full-statement)]
                                     [:Lausunnonantaja (contains (ss/trim (get-in full-statement [:person :name])))]
                                     [:PyyntoPvm       (util/to-xml-date (get full-statement :requested))]
                                     [:Pyytaja         (just "Sipoo (Sonja Sibbo)")]))
            (fact "other keys"
              (when (:saateText full-statement) (:saateText full-statement) => (:Saateteksti statement))
              (when (:dueDate full-statement) (util/to-xml-date (:dueDate full-statement)) => (:Maaraaika statement))
              (when (:given full-statement) (util/to-xml-date (:given full-statement)) => (:LausuntoPvm statement))
              (when (:status full-statement) (:status full-statement) => (:Puolto statement))
              (when (:text full-statement) (:text full-statement) => (:LausuntoTeksti statement)))))) => passing-quick-check )))
