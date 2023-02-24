(ns lupapalvelu.statement.statement-canonical-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.integrations.statement-canonical :refer :all]
            [lupapalvelu.statement :as stmnt]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.schema-generators :as ssg]
            [sade.schema-utils :as ssu]
            [sade.shared-schemas :as sschemas]
            [sade.strings :as ss]
            [schema-tools.core :as st]))

(def sonja-requester {:firstName "Sonja" :lastName "Sibbo" :organization {:name {:fi "Sipoo"}}})

(fact "statement to canonical"
  (let [statement (stmnt/create-statement 123 "saate"
                                          (-> (date/now) (date/plus :week) (date/timestamp))
                                          (ssg/generate (st/required-keys stmnt/StatementGiver)))
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
                                            :PyyntoPvm   (date/xml-date (get statement :requested))
                                            :Maaraaika   (date/xml-date (get statement :dueDate))
                                            :Lausunnonantaja (contains (ss/trim (get-in statement [:person :name])))}))
    (fact :qc "statement response, full data"
      (tc/quick-check
        200
        (prop/for-all [full-statement (-> (st/required-keys stmnt/Statement)
                                          (st/update :person st/required-keys)
                                          (ssu/select-keys [:id :saateText :status :text
                                                            :dueDate :requested :given :person])
                                          ssg/generator)]
          (let [statement (statement-as-canonical sonja-requester full-statement "fi")]
            (fact "mandatory keys"
              statement => (contains [:LausuntoTunnus  (:id full-statement)]
                                     [:Lausunnonantaja (contains (ss/trim (get-in full-statement [:person :name])))]
                                     [:PyyntoPvm       (date/xml-date (get full-statement :requested))]
                                     [:Pyytaja         (just "Sipoo (Sonja Sibbo)")]))
            (fact "other keys"
              (when (:saateText full-statement) (:saateText full-statement) => (:Saateteksti statement))
              (when (:dueDate full-statement) (date/xml-date (:dueDate full-statement)) => (:Maaraaika statement))
              (when (:given full-statement) (date/xml-date (:given full-statement)) => (:LausuntoPvm statement))
              (when (:status full-statement) (:status full-statement) => (:Puolto statement))
              (when (:text full-statement) (:text full-statement) => (:LausuntoTeksti statement)))))) => passing-quick-check )))
