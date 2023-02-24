(ns lupapalvelu.location-test
  (:require [lupapalvelu.location :as location]
            [lupapalvelu.pate-itest-util :refer [err]]
            [midje.sweet :refer :all]))

(def building1 {:operationId "op1"
                :nationalId  "build1"
                :description "Building One"
                :location    [331795.463 6785651.655]})

(def building2 {:operationId "op2"
                :nationalId  "build2"
                :description "Building Two"
                :location    [332599.043 6785254.402]})

(def building3 {:operationId "op3"
                :nationalId  "build3"
                :description "Building Three"
                :location    [325248.394 6792856.331]})

(def building4 {:operationId "op4"
                :nationalId  "build4"
                :description "Building Four"
                :location    [-616785.787 6813412.265]})

(def op1 {:id "op1"
          :description "Operation One"
          :name "pientalo"})

(def op2 {:id "op2"
          :description "Operation Two"
          :name "aita"})

(def op3 {:id "op3"
          :description "Operation Three"
          :name "tonttijako"})

(def doc1 {:data        {:tunnus                 {:value "A"}
                         :valtakunnallinenNumero {:value "doc1"}}
           :schema-info {:op {:id          "op1"
                              :description "Document One"
                              :location    {:epsg3067 [332484.588 6786467.859]}}}})

(def doc2 {:data {:tunnus {:value "B"}
                  :valtakunnallinenNumero {:value "doc2"}}
           :schema-info {:op {:id "op2"
                              :description "Document Two"
                              :location {:epsg3067 [-620714.926 6817020.268]}}}})

(def doc3 {:data        {:tunnus                 {:value "C"}
                         :valtakunnallinenNumero {:value "doc3"}}
           :schema-info {:op {:id          "op3"
                              :description "Document Three"
                              :location    {:epsg3067 [323055.303 6791923.017]}}}})

(facts "location-operatons"
  (location/location-operations {:primaryOperation op1 :secondaryOperations [op2 op3]})
  => (just [op1 op2] :in-any-order)
  (location/location-operations {:primaryOperation op3 :secondaryOperations [op2 op1]})
  => (just [op1 op2] :in-any-order)
  (location/location-operations {:primaryOperation op1}) => [op1]
  (location/location-operations {:primaryOperation op3}) => nil
  (location/location-operations {}) => nil
  (location/location-operations {:secondaryOperations [op2 op3]}) => [op2])

(facts "has-location-operatons"
  (location/has-location-operations {:application {:primaryOperation op1 :secondaryOperations [op2 op3]}})
  => nil
  (location/has-location-operations {:application {:primaryOperation op1}}) => nil
  (location/has-location-operations {:application{:primaryOperation op3}})
  => (err :error.no-location-operations)
  (location/has-location-operations {:application {}}) => (err :error.no-location-operations)
  (location/has-location-operations {}) => nil)

(facts "valid-operation"
  (location/valid-operation {:application {:primaryOperation op1 :secondaryOperations [op2 op3]}
                             :data        {:operation-id "op1"}}) => nil
  (location/valid-operation {:application {:primaryOperation op1 :secondaryOperations [op2 op3]}
                             :data        {:operation-id "op2"}}) => nil
  (location/valid-operation {:application {:primaryOperation op1 :secondaryOperations [op2 op3]}
                             :data        {:operation-id "op3"}})
  => (err :error.operation-not-found)
  (location/valid-operation {:application {:primaryOperation op1 :secondaryOperations [op2 op3]}
                             :data        {:operation-id "bad"}})
  => (err :error.operation-not-found))

(facts "location-operation-list"
  (location/location-operation-list {:primaryOperation    op1
                                     :secondaryOperations [op2 op3]
                                     :buildings           [building1 building2
                                                           building3 building4]
                                     :documents           [doc1 doc2 doc3]})
  => (just [{:id  "op1" :operation   "pientalo"      :building-id "build1"
             :tag "A"   :description "Operation One" :location    (:location building1)}
            {:id  "op2" :operation   "aita"          :building-id "build2"
             :tag "B"   :description "Operation Two" :location    (:location building2)}]
           :in-any-order)

  (location/location-operation-list {:primaryOperation op1
                                     :buildings        []
                                     :documents        [doc1 doc2 doc3]})
  => [{:id  "op1" :operation   "pientalo"      :building-id "doc1"
       :tag "A"   :description "Operation One" :location    (-> doc1 :schema-info :op
                                                                :location :epsg3067)}]

  (location/location-operation-list {:primaryOperation op1
                                     :buildings        [(assoc building1
                                                               :nationalId "  "
                                                               :description "  "
                                                               :location [])]
                                     :documents        [doc1 doc2 doc3]})
  => [{:id  "op1" :operation   "pientalo"      :building-id "doc1"
       :tag "A"   :description "Operation One" :location    (-> doc1 :schema-info :op
                                                                :location :epsg3067)}]

  (location/location-operation-list {:primaryOperation (assoc op1 :description "  ")
                                     :buildings        [building1]
                                     :documents        [doc1]})
  => [{:id  "op1" :operation   "pientalo"      :building-id "build1"
       :tag "A"   :description "Building One" :location    (:location building1)}]

  (location/location-operation-list {:primaryOperation (assoc op1 :description "  ")
                                     :buildings        []
                                     :documents        [(-> doc1
                                                            (assoc-in [:data :valtakunnallinenNumero :value] "")
                                                            (assoc-in [:data :tunnus :value] "  ")
                                                            (update-in [:schema-info :op] dissoc :location))]})
  => [{:id  "op1" :operation   "pientalo"}]

  (location/location-operation-list {:primaryOperation op3
                                     :buildings        [building3]
                                     :documents        [doc3]})
  => [])
