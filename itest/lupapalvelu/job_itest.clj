(ns lupapalvelu.job-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.job :as job]))

(facts "job utility"
  (let [id1 "abc"
        id2 "def"
        initial-value {id1 {:status :pending}
                       id2 {:status :pending}}
        j1 (job/start initial-value)]

    (fact "job start returns correct job data"
      j1 => (just {:id string?
                   :status :running
                   :value initial-value
                   :version 0}))

    (fact "job can be updated by id in value map"
      (job/update-by-id (:id j1) id1 {:status :running}) => 1)

    (fact "job status returns updated state"
      (job/status (:id j1) 0 10000) => {:job {:id (:id j1)
                                              :status "running"
                                              :value {id1 {:status "running"} id2 {:status "pending"}}
                                              :version 1}
                                        :result :update})

    (fact "job can be updated with a provided function"
      (job/update (:id j1) assoc id1 {:status :done}) => 2
      (job/status (:id j1) 1 10000) => {:job {:id (:id j1)
                                              :status "running"
                                              :value {id1 {:status "done"} id2 {:status "pending"}}
                                              :version 2}
                                        :result :update})

    (fact "job gets completed when all tasks are done"
      (job/update-by-id (:id j1) id2 {:status :done}) => 4
      (job/status (:id j1) 1 10000) => {:job {:id (:id j1)
                                              :status "done"
                                              :value {id1 {:status "done"} id2 {:status "done"}}
                                              :version 4}
                                        :result :update})

    (fact "job gets completed when all tasks end in error"
      (let [j2 (job/start initial-value)]
        (job/update-by-id (:id j2) id1 {:status :error}) => 1
        (job/update-by-id (:id j2) id2 {:status :error}) => 3

        (job/status (:id j2) 3 10000) => {:job {:id (:id j2)
                                                :status "done"
                                                :value {id1 {:status "error"} id2 {:status "error"}}
                                                :version 3}
                                          :result :update}))

    (fact "value with mongo-incompatible keys works"
      (let [id3 "$foo.bar.baz"
            id4 "boo.$.mur"
            j3 (job/start {id3 {:status :pending}
                           id4 {:status :pending}})]
        (job/update-by-id (:id j3) id3 {:status :running}) => 1
        (job/update-by-id (:id j3) id3 {:status :done}) => 2
        (job/update (:id j3) assoc id4 {:status :running}) => 3
        (job/update (:id j3) assoc id4 {:status :done}) => 4
        (job/status (:id j3) 1 10000) => {:job {:id (:id j3)
                                                :status "done"
                                                :value {id3 {:status "done"}
                                                        id4 {:status "done"}}
                                                :version 4}
                                          :result :update}))))
