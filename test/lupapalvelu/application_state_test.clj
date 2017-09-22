(ns lupapalvelu.application-state-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [lupapalvelu.application-state :refer :all]))

(testable-privates lupapalvelu.application-state push-history-to-$each)

(facts "State transitions"
  (let [pena {:username "pena", :firstName "Pena" :lastName "Panaani"}]
    (fact "update"
      (state-transition-update :open 1 {:created 0 :permitType "R"} pena) => {$set {:state :open, :opened 1, :modified 1}, $push {:history {:state :open, :ts 1, :user pena}}}
      (state-transition-update :open 1 {:opened nil  :permitType "R"} pena) => {$set {:state :open, :opened 1, :modified 1}, $push {:history {:state :open, :ts 1, :user pena}}}
      (state-transition-update :submitted 2 {:created 0 :opened 1  :permitType "R"} pena) => {$set {:state :submitted, :submitted 2, :modified 2}, $push {:history {:state :submitted, :ts 2, :user pena}}}
      (state-transition-update :verdictGiven 3 {:created 0 :opened 1 :submitted 2  :permitType "R"} pena) => {$set {:state :verdictGiven, :modified 3}, $push {:history {:state :verdictGiven, :ts 3, :user pena}}})

    (fact "re-update"
      (state-transition-update :open 4 {:opened 3  :permitType "R"} pena) => {$set {:state :open, :modified 4}, $push {:history {:state :open, :ts 4, :user pena}}}
      (state-transition-update :submitted 5 {:submitted 4 :permitType "R"} pena) => {$set {:state :submitted, :modified 5}, $push {:history {:state :submitted, :ts 5, :user pena}}}
      (state-transition-update :constructionStarted 6 {:started 5 :permitType "R"} pena) => {$set {:state :constructionStarted, :modified 6}, $push {:history {:state :constructionStarted, :ts 6, :user pena}}})
    (fact "apply multiple-updates"
      (fact "empty -> error"
        (state-transition-updates []) => (throws AssertionError))
      (fact "only 4 args ok"
        (state-transition-updates [[:open]]) => (throws AssertionError)
        (state-transition-updates [[:open 1]]) => (throws AssertionError)
        (state-transition-updates [[:open 1 {:created 0 :permitType "R"}]]) => (throws AssertionError)
        (state-transition-updates [[:open 1 {:created 0 :permitType "R"} {:user 123}]]) => map?)
      (fact "types"
        (state-transition-updates [["open" 1 {:created 0 :permitType "R"} {:user 123}]]) => (throws AssertionError)
        (state-transition-updates [[:open "123" {:created 0 :permitType "R"} {:user 123}]]) => (throws AssertionError)
        (state-transition-updates [[:open 1 123 {:user 123}]]) => (throws AssertionError)
        (state-transition-updates [[:open 1 {:created 0 :permitType "R"} "user"]]) => (throws AssertionError)
        (state-transition-updates [[:open 1 {:created 0 :permitType "R"} {:user 123}]]) => map?)
      (fact "single update == state-transition-update"
        (state-transition-updates [[:open 1 {:created 0 :permitType "R"} pena]]) => (state-transition-update :open 1 {:created 0 :permitType "R"} pena))
      (fact "timestamps are set, history array pushed with $each"
        (state-transition-updates [[:open 1 {:created 0 :permitType "R"} pena]
                                   [:submitted 2 {:created 0 :opened 1  :permitType "R"} pena]]) => {$set {:state :submitted :modified 2 :submitted 2 :opened 1}
                                                                                                     $push {:history
                                                                                                            {$each [{:state :open, :ts 1, :user pena}
                                                                                                                    {:state :submitted, :ts 2, :user pena}]}}}
        (state-transition-updates [[:verdictGiven 4 {:permitType "R" :modified 4} pena]
                                   [:constructionStarted 5 {:permitType "R" :modified 4} pena]
                                   [:constructionStarted 6 {:started 5 :permitType "R"} pena]]) => {$set {:state :constructionStarted :modified 6 :started 5}
                                                                                                    $push {:history
                                                                                                           {$each
                                                                                                            [{:state :verdictGiven :ts 4 :user pena}
                                                                                                             {:state :constructionStarted :ts 5 :user pena}
                                                                                                             {:state :constructionStarted :ts 6 :user pena}]}}})))
  (facts "push-history-to-$each"
    (fact "$each is added for vanilla histories"
      (push-history-to-$each {:state :foo} {:state :faa}) => {$each [{:state :foo}
                                                                     {:state :faa}]})
    (fact "other keys from root are stripped and moved to $each array"
      (push-history-to-$each {:state :foo :modified 1} {:state :faa :modified 2}) => {$each [{:state :foo :modified 1}
                                                                                             {:state :faa :modified 2}]})
    (fact "old $each is preserved"
      (push-history-to-$each {$each [{:state :foo :modified 1}
                                     {:state :faa :modified 2}]}
                             {:state :fuu :modified 3}) => {$each [{:state :foo :modified 1}
                                                                   {:state :faa :modified 2}
                                                                   {:state :fuu :modified 3}]})))


(facts "Previous app state"
  (let [user {:username "pena"}
        now (now)
        state-seq [:one :two :three :four :five]]

    (dotimes [i (count state-seq)]
      (let [prev-state (get-previous-app-state
                         {:history (map
                                     #(history-entry % now user)
                                     (take (+ i 1) state-seq))})]
        (if (= i 0)
          (fact "no previous state" prev-state => nil)
          (fact {:midje/description prev-state}
            prev-state => (nth state-seq (- i 1))))))

    (fact "no previous state if no history"
      (get-previous-app-state nil) => nil
      (get-previous-app-state []) => nil)))

(facts "Get previous state (history)"
  (let [state-seq [:one nil :two :three nil nil]
        now (now)
        history {:history
                 (map-indexed
                   (fn [i state] (history-entry state (+ now i) {:username "Pena"}))
                   state-seq)}]
    (fact "only entries with :state are regarded"
      (get-previous-app-state history) => :two)))
