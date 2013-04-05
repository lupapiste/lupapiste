(ns lupapalvelu.document.tools-test
  (:use [lupapalvelu.document.tools]
        [midje.sweet]))

(def schema
  {:info {:name "band"},
   :data
   [{:name "band",
     :type :group,
     :body
     [{:name "name", :type :string}
      {:name "genre", :type :string}
      {:name "members"
       :type :group
       :repeating true
       :body [{:name "name", :type :string}
              {:name "instrument", :type :string}]}]}]})

(fact "simple schema"
  (-> schema
    (create nil-values)
    flattened) => {:band {:name nil
                          :genre nil
                          :members {:0 {:members {:name nil
                                                  :instrument nil}}}}})
