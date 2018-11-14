(ns lupapalvelu.invoices.shared.util-test
  (:require
   [lupapalvelu.invoices.shared.util :as util]
   [midje.sweet :refer :all]))

(facts "rows-by-operation"
       (fact "returns a map of the form {<operation> [<row><row>...]}"
             (let [catalogue {:id "bar-1-id"
                              :organization-id "753-R"
                              :state "draft"
                              :valid-from 123456
                              :rows [{:code "123"
                                      :text "Taksarivi 1"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :operations ["toimenpide1" "toimenpide2"]}
                                     {:code "abc"
                                      :text "Taksarivi 2"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :operations ["toimenpide1"]}]
                              :meta {:created 12345
                                     :created-by "dummy-user"}}]
               (util/rows-by-operation catalogue)  => {"toimenpide1" [{:code "123"
                                                                       :text "Taksarivi 1"
                                                                       :unit "kpl"
                                                                       :price-per-unit 23
                                                                       :discount-percent 50
                                                                       :operations ["toimenpide1" "toimenpide2"]}
                                                                      {:code "abc"
                                                                       :text "Taksarivi 2"
                                                                       :unit "kpl"
                                                                       :price-per-unit 23
                                                                       :discount-percent 50
                                                                       :operations ["toimenpide1"]}]
                                                       "toimenpide2" [{:code "123"
                                                                       :text "Taksarivi 1"
                                                                       :unit "kpl"
                                                                       :price-per-unit 23
                                                                       :discount-percent 50
                                                                       :operations ["toimenpide1" "toimenpide2"]}]}))

       (fact "does not include rows that do not operations key or its value is an empty coll}"
             (let [catalogue {:id "bar-1-id"
                              :organization-id "753-R"
                              :state "draft"
                              :valid-from 123456
                              :rows [{:code "123"
                                      :text "Taksarivi 1"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      :operations ["toimenpide1" "toimenpide2"]}
                                     {:code "abc"
                                      :text "Taksarivi 2"
                                      :unit "kpl"
                                      :price-per-unit 23
                                      :discount-percent 50
                                      }]
                              :meta {:created 12345
                                     :created-by "dummy-user"}}]
               (util/rows-by-operation catalogue)  => {"toimenpide1" [{:code "123"
                                                                       :text "Taksarivi 1"
                                                                       :unit "kpl"
                                                                       :price-per-unit 23
                                                                       :discount-percent 50
                                                                       :operations ["toimenpide1" "toimenpide2"]}]
                                                       "toimenpide2" [{:code "123"
                                                                       :text "Taksarivi 1"
                                                                       :unit "kpl"
                                                                       :price-per-unit 23
                                                                       :discount-percent 50
                                                                       :operations ["toimenpide1" "toimenpide2"]}]}))
       )
