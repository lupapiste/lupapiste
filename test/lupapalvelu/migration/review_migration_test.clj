(ns lupapalvelu.migration.review-migration-test
  (:require [lupapalvelu.migration.review-migration :refer :all]
            [lupapalvelu.mongo :refer [create-id]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(fact "duplicate?"
  (duplicate? nil nil) => false
  (duplicate? {} {}) => false
  (duplicate? {:data {:muuTunnus "foo"}}
              {:data {:muuTunnus "foo"}}) => false
  (duplicate? {:data {:muuTunnus "foo"}}
              {:data {:muuTunnus ""}}) => false
  (duplicate? {:data {:muuTunnus ""}}
              {:data {:muuTunnus "foo"}}) => true
  (duplicate? {:data {:muuTunnus ""}}
              {:data {:muuTunnus ""}}) => true
  (duplicate? {:id   "foo"
               :data {:muuTunnus ""}}
              {:id   "bar"
               :data {:muuTunnus ""}}) => true
  (duplicate? {:id   "foo"
               :data {:muuTunnus ""
                      :hii       {:hoo [{:one 1} {:two 2}]}}}
              {:id   "bar"
               :data {:muuTunnus ""
                      :hii       {:hoo [{:one 1} {:two 2}]}}}))

(defn make-task [task-id subtype taskname other-id created]
  {:id          task-id
   :schema-info {:subtype subtype}
   :taskname    taskname
   :created     created
   :data        (merge {:katselmuksenLaji {:value    taskname
                                           :modified (rand-int 1000)}
                        :katselmus        {:pitaja {:value    "Bob"
                                                    :modified (rand-int 1000)}}}
                       (when other-id
                         {:muuTunnus {:value    other-id
                                      :modified (rand-int 1000)}}))})

(let [attachments [{:id "att1"}
                   {:id     "att2"
                    :source {:id   "one"
                             :type "tasks"}
                    :target {:id   "one"
                             :type "task"}}
                   {:id     "att3"
                    :target {:id   "one"
                             :type "task"}}
                   {:id     "att4"
                    :source {:id   "one"
                             :type "tasks"}}
                   {:id     "att5"
                    :source {:id "one"}
                    :target {:id "one"}}
                   {:id     "att6"
                    :source {:id   "two"
                             :type "tasks"}
                    :target {:id   "two"
                             :type "task"}}
                   {:id     "att7"
                    :source {:id   "one"
                             :type "task"}
                    :target {:id   "one"
                             :type "tasks"}}
                   {:id     "att8"
                    :source {:id   "three"
                             :type "tasks"}
                    :target {:id   "three"
                             :type "task"}}
                   {:id     "att9"
                    :source {:id   "two"
                             :type "tasks"}
                    :target {:id   "two"
                             :type "task"}}
                   {:id     "att10"
                    :source {:type "tasks"}
                    :target {:type "task"}}
                   {:id     "att11"
                    :source {:id   ""
                             :type "tasks"}
                    :target {:id   ""
                             :type "task"}}
                   {:id     "att12"
                    :source {:id   "old-six"
                             :type "tasks"}
                    :target {:id   "old-six"
                             :type "task"}}]
      t1          (make-task "one" "review-backend" "One" "" 2000)
      t1-old      (make-task "old-one" "review-backend" "One" "LP-foo-bar" 1500)
      t2          (make-task "two" "review-backend" "Two" "" 2200)
      t2-old      (make-task "old-two" "review-backend" "Two" "LP-foo-bar" 1700)
      t3          (make-task "three" "review-backend" "Three" "not-empty" 3000)
      t3-old      (make-task "old-three" "review-backend" "Three" "not-empty" 3000)
      t4          (make-task "four" "wrong" "Four" "" 4000)
      t4-old      (make-task "old-four" "wrong" "Four" "" 1600)
      t5          (make-task "five" "review-backend" "Five" "not-empty" 5000)
      t5-old      (make-task "old-five" "review-backend" "Five" "" 2000)
      t6          (make-task "six" "review-backend" "Six" "" 6000)
      t6-old      (make-task "old-six" "review-backend" "Six" "" 5000)
      t6-older    (make-task "older-six" "review-backend" "Six" "LP-foo-bar" 4000)
      t7          (make-task "seven" "review-backend" "Seven" "" 7000)
      application {:tasks       (shuffle [t1 t1-old t2 t2-old t3 t3-old
                                          t4 t4-old t5 t5-old
                                          t6 t6-old t6-older t7])
                   :attachments attachments}]

  (fact "reviews-attachment-ids"
    (reviews-attachment-ids [] attachments) => nil
    (reviews-attachment-ids nil attachments) => nil
    (reviews-attachment-ids ["foo"] attachments) => nil
    (reviews-attachment-ids ["one"] attachments)
    => ["att2"]
    (reviews-attachment-ids ["one"] []) => nil
    (reviews-attachment-ids ["one"] nil) => nil
    (reviews-attachment-ids ["foo" "one" "bar"] attachments)
    => ["att2"]
    (reviews-attachment-ids [nil] attachments) => nil
    (reviews-attachment-ids [""] attachments) => nil
    (reviews-attachment-ids ["two"] attachments)
    => ["att6" "att9"]
    (reviews-attachment-ids ["one" "two" "three"] attachments)
    => ["att2" "att6" "att8" "att9"])

  (fact "duplicate-backend-reviews"
    (duplicate-backend-reviews application)
    => {:task-ids       ["six" "old-six" "two" "one"]
        :attachment-ids ["att2" "att6" "att9" "att12"]}
    (duplicate-backend-reviews {:attachments attachments
                                :tasks       [t6]})
    => {:task-ids       []
        :attachment-ids nil}
    (duplicate-backend-reviews {:attachments attachments
                                :tasks       [t6-old t6]})
    => {:task-ids       ["six"]
        :attachment-ids nil}
    (duplicate-backend-reviews {:attachments attachments
                                :tasks       [t6-older t6-old t6]})
    => {:task-ids       ["six" "old-six"]
        :attachment-ids ["att12"]}
    (duplicate-backend-reviews {:attachments attachments
                                :tasks       [t6-older t6-old]})
    => {:task-ids       ["old-six"]
        :attachment-ids ["att12"]}))
