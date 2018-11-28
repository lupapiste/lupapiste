(ns lupapalvelu.invoices.transfer-batch-test
  (:require [lupapalvelu.invoices.transfer-batch :refer [->invoice-transfer-batch-db]]
            [midje.sweet :refer :all]
            [sade.core]))

(facts "->invoice-transfer-batch-db"
       (let [user-data {:foo       "some-value"
                        :id        "penan-id"
                        :firstName "pena"
                        :lastName  "panaani"
                        :role      "authority"
                        :email     "pena@panaani.fi"
                        :username  "pena"}
             org-id "foo-org-id"
             transfer-batch-data {}
             test-timestamp 123456]
         (fact "returns proper invoice-transfer-batch-ready-to-be-stored-to-database"
               (with-redefs [sade.core/now (fn [] test-timestamp)]
                 (->invoice-transfer-batch-db transfer-batch-data org-id user-data)) => {:organization-id org-id
                                                                        :created test-timestamp
                                                                        :created-by {:id        "penan-id"
                                                                                         :firstName "pena"
                                                                                         :lastName  "panaani"
                                                                                         :role      "authority"
                                                                                         :email     "pena@panaani.fi"
                                                                                     :username  "pena"}
                                                                        :invoices []
                                                                                         :number-of-rows 0
                                                                                         :sum {:currency "" :major 0 :minor 0 :text ""}})))
