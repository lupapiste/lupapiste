(ns lupapalvelu.pate.verdict-date-test
  (:require [lupapalvelu.pate.verdict-date :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(facts "make-update"
  (make-update :foo "bar") => {$set {:foo "bar"}}
  (make-update :foo nil) => {$unset {:foo true}}
  (make-update :foo false) => {$set {:foo false}})

(defn make-verdict [published verdict-date aloitettava voimassa]
  {:timestamp published
   :paatokset [{:poytakirjat [{:paatospvm verdict-date}]
                :paivamaarat {:aloitettava   aloitettava
                              :voimassaHetki voimassa}}]})

(defn make-pate-verdict [published verdict-date aloitettava voimassa]
  {:published {:published published}
   :category  "r"
   :data      {:verdict-date verdict-date
               :aloitettava  aloitettava
               :voimassa     voimassa}})


(facts "verdictDate-update"
  (verdictDate-update {:verdicts      [(make-verdict 1 2 3 4)]
                       :pate-verdicts [(make-pate-verdict 10 12 13 14)]})
  => {$set {:verdictDate 12}}
  (verdictDate-update {:verdicts      [(make-verdict 21 22 23 24)]
                       :pate-verdicts [(make-pate-verdict 10 12 13 14)]})
  => {$set {:verdictDate 22}}
  (verdictDate-update {:verdicts      [(assoc (make-verdict 21 22 23 24) :draft true)]
                       :pate-verdicts [(dissoc (make-pate-verdict 10 12 13 14) :published)]})
  => {$unset {:verdictDate true}}
  (verdictDate-update {}) => {$unset {:verdictDate true}})

(facts "deadlines-update"
  (deadlines-update {:verdicts      [(make-verdict 1 2 3 4)]
                     :pate-verdicts [(make-pate-verdict 10 12 13 14)]})
  => {$set {:deadlines {:aloitettava 3 :voimassa 4}}}
  (deadlines-update {:verdicts      [(make-verdict 21 22 23 24)]
                     :pate-verdicts [(make-pate-verdict 10 12 13 14)]})
  => {$set {:deadlines {:aloitettava 13 :voimassa 14}}}
  (deadlines-update {:verdicts      [(assoc (make-verdict 21 22 23 24) :draft true)]
                     :pate-verdicts [(dissoc (make-pate-verdict 10 12 13 14) :published)]})
  => {$unset {:deadlines true}}
  (deadlines-update {}) => {$unset {:deadlines true}}
  (deadlines-update {:verdicts [(make-verdict 1 2 nil nil)]}) => {$unset {:deadlines true}}
  (deadlines-update {:verdicts [(make-verdict 1 2 3 nil)]}) =>
  {$set {:deadlines {:aloitettava 3}}}
  (deadlines-update {:verdicts [(make-verdict 1 2 nil 4)]}) =>
  {$set {:deadlines {:voimassa 4}}})

(facts "update-verdict-date"
  (fact "verdictDate, full deadlines"
    (update-verdict-date "app-id") => nil
    (provided
      (lupapalvelu.mongo/by-id :applications "app-id" [:verdicts :pate-verdicts])
      => {:id "app-id" :verdicts [(make-verdict 1 2 3 4)]}
      (lupapalvelu.mongo/update-by-id :applications
                                      "app-id"
                                      {$set {:verdictDate 2
                                             :deadlines   {:aloitettava 3
                                                           :voimassa    4}}})
      => nil))
  (fact "verdictDate, no deadlines"
    (update-verdict-date "app-id") => nil
    (provided
      (lupapalvelu.mongo/by-id :applications "app-id" [:verdicts :pate-verdicts])
      => {:id "app-id" :verdicts [(make-verdict 1 2 nil nil)]}
      (lupapalvelu.mongo/update-by-id :applications
                                      "app-id"
                                      {$set   {:verdictDate 2}
                                       $unset {:deadlines true}})
      => nil))
  (fact "verdictDate, one deadline"
    (update-verdict-date "app-id1") => nil
    (provided
      (lupapalvelu.mongo/by-id :applications "app-id1" [:verdicts :pate-verdicts])
      => {:id "app-id1" :verdicts [(make-verdict 1 2 3 nil)]}
      (lupapalvelu.mongo/update-by-id :applications
                                      "app-id1"
                                      {$set {:verdictDate 2
                                             :deadlines   {:aloitettava 3}}})
      => nil)
    (update-verdict-date "app-id2") => nil
    (provided
      (lupapalvelu.mongo/by-id :applications "app-id2" [:verdicts :pate-verdicts])
      => {:id "app-id2" :pate-verdicts [(make-pate-verdict 1 2 nil 4)]}
      (lupapalvelu.mongo/update-by-id :applications
                                      "app-id2"
                                      {$set {:verdictDate 2
                                             :deadlines   {:voimassa 4}}})
      => nil)))
