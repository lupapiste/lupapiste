(ns lupapalvelu.invoices.shared.util-test
  (:require
   [lupapalvelu.invoices.shared.util :as util]
   [midje.sweet :refer :all]))

(facts "rows-with-index-by-operation"
       (fact "returns a map of the form {<operation> [<row><row>...] where each row has the key :index with value matching the index in the original :rows vector in the catalogue}"
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
               (util/rows-with-index-by-operation catalogue)  => {"toimenpide1" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1" "toimenpide2"]}
                                                                                 {:index 1
                                                                                  :code "abc"
                                                                                  :text "Taksarivi 2"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1"]}]
                                                                  "toimenpide2" [{:index 0
                                                                                  :code "123"
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
               (util/rows-with-index-by-operation catalogue)  => {"toimenpide1" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1" "toimenpide2"]}]
                                                                  "toimenpide2" [{:index 0
                                                                                  :code "123"
                                                                                  :text "Taksarivi 1"
                                                                                  :unit "kpl"
                                                                                  :price-per-unit 23
                                                                                  :discount-percent 50
                                                                                  :operations ["toimenpide1" "toimenpide2"]}]})))

(facts "remove-maps-with-value"
       (fact "filters out maps that have any of the given values in a given key"
             (let [maps [{:name "George" :info "foo"}
                         {:name "Liz"    :info "foo"}
                         {:name "George" :info "blahblah"}
                         {:name "Liz"    :info "bar"}]]

               (util/remove-maps-with-value maps :name ["Liz"]) =>  [{:name "George" :info "foo"}
                                                                     {:name "George" :info "blahblah"}]

               (util/remove-maps-with-value maps :info ["foo"]) =>  [{:name "George" :info "blahblah"}
                                                                     {:name "Liz"    :info "bar"}]

               (util/remove-maps-with-value maps :info ["foo" "bar"]) =>  [{:name "George" :info "blahblah"}])))
