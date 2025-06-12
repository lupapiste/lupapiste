(ns lupapalvelu.document.shared-tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.document.shared-tools :as shared-tools]
            [sade.util :as util]))

(deftest shared-tools-test
  (testing "context passing"
    (let [schema {:info {:name "example schema"}
                  :body [{:name "g-1"
                          :body [{:name "g-1-e-1"}
                                 {:name "g-1-e-2"}
                                 {:name "g-1-e-3"}]}
                         {:name "g-2"
                          :body [{:name "g-2-e-1"}
                                 {:name "g-2-e-2"}
                                 {:name "g-2-e-3"}]}
                         {:name "g-3"
                          :body [{:name "g-3-g-A"
                                  :body [{:name "g-3-g-A-e-1"}]}]}]}]
      (testing "no context"
        (is (= {[:g-1 :g-1-e-1]              {:name "g-1-e-1"}
                [:g-1 :g-1-e-2]              {:name "g-1-e-2"}
                [:g-1 :g-1-e-3]              {:name "g-1-e-3"}
                [:g-1]                       {:name "g-1"}
                [:g-2 :g-2-e-1]              {:name "g-2-e-1"}
                [:g-2 :g-2-e-2]              {:name "g-2-e-2"}
                [:g-2 :g-2-e-3]              {:name "g-2-e-3"}
                [:g-2]                       {:name "g-2"}
                [:g-3 :g-3-g-A :g-3-g-A-e-1] {:name "g-3-g-A-e-1"}
                [:g-3 :g-3-g-A]              {:name "g-3-g-A"}
                [:g-3]                       {:name "g-3"}}
               (into {} (shared-tools/walk-schema
                          schema
                          (fn [{:keys [path element]}]
                            {:result [path (dissoc element :body)]}))))))
      (testing "context"
        (is (= {[:g-1 :g-1-e-1]              {:name "g-1-e-1" :depth 2 :parents ["g-1"]}
                [:g-1 :g-1-e-2]              {:name "g-1-e-2" :depth 2 :parents ["g-1"]}
                [:g-1 :g-1-e-3]              {:name "g-1-e-3" :depth 2 :parents ["g-1"]}
                [:g-1]                       {:name "g-1" :depth 1}
                [:g-2 :g-2-e-1]              {:name "g-2-e-1" :depth 2 :parents ["g-2"]}
                [:g-2 :g-2-e-2]              {:name "g-2-e-2" :depth 2 :parents ["g-2"]}
                [:g-2 :g-2-e-3]              {:name "g-2-e-3" :depth 2 :parents ["g-2"]}
                [:g-2]                       {:name "g-2" :depth 1}
                [:g-3 :g-3-g-A :g-3-g-A-e-1] {:name "g-3-g-A-e-1" :depth 3 :parents ["g-3" "g-3-g-A"]}
                [:g-3 :g-3-g-A]              {:name "g-3-g-A" :depth 2 :parents ["g-3"]}
                [:g-3]                       {:name "g-3" :depth 1}}
               (into {} (shared-tools/walk-schema
                          schema
                          (fn [{:keys [path element context]}]
                            (let [context (update context :depth (fnil inc 0))]
                              {:result  [path (util/assoc-when (dissoc element :body)
                                                               :parents (:parents context)
                                                               :depth (:depth context))]
                               :context (update context :parents (fnil conj []) (:name element))}))))))))))
