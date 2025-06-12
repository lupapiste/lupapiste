(ns lupapalvelu.next.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.next.util :as nutil])
  (:import [clojure.lang ExceptionInfo]))

(deftest merge-every-fn-test
  (let [cofx   {:db {:a 1}}
        run-fn (partial nutil/merge-every-fn cofx)]
    (testing "without args"
      (is (= {:db {:a 1}}
             (run-fn [])))
      (is (= {:db {:a 1}}
             (run-fn [[(constantly nil)]])))
      (is (= {:db {:a 2}}
             (run-fn [[(fn [{:keys [db]} _]
                         {:db (update db :a inc)})]])))
      (is (= {:fx [[:dispatch [::foo 1]]]
              :db {:a 1}}
             (run-fn [[(fn [_ _]
                         {:fx [[:dispatch [::foo 1]]]})]])))
      (is (= {:fx [[:dispatch [::foo 1]]
                   [:dispatch [::bar 2]]]
              :db {:a 2
                   :b 4}}
             (run-fn [[(fn [{:keys [db]} _]
                         {:db (update db :a inc)})]
                      [(fn [_ _]
                         {:fx [[:dispatch [::foo 1]]]})]
                      [(fn [{:keys [db]} _]
                         {:db (assoc db :b 4)
                          :fx [[:dispatch [::bar 2]]]})]])))
      (is (= {:fx [[:dispatch [::foo 1]]]
              :db {:a 1}}
             (run-fn [[(fn [_ _]
                         {:fx [[:dispatch [::foo 1]]]})]
                      [(fn [_ _]
                         {:fx [[:dispatch [::foo 1]]]})]
                      [(fn [_ _]
                         {:fx [[:dispatch [::foo 1]]]})]]))
          "duplicate :fx are dropped")
      (is (= {:fx [[:dispatch [::foo 1]]
                   [:dispatch [::foo 2]]]
              :db {:a 1}}
             (run-fn [nil
                      [(fn [_ _]
                         {:fx [[:dispatch [::foo 1]]]})]
                      nil
                      [(fn [_ _]
                         {:fx [[:dispatch [::foo 2]]]})]
                      nil]))
          "nil fns are ignored"))
    (testing "with args"
      (is (= {:db {:a 1
                   :x 42
                   :y 1337
                   :z "hello"}}
             (run-fn [[(fn [{:keys [db]} _]
                         {:db db})]
                      [(fn [{:keys [db]} [_ x]]
                         {:db (assoc db :x x)})
                       42]
                      [(fn [{:keys [db]} [_ y z]]
                         {:db (assoc db :y y :z z)})
                       1337
                       "hello"]]))))
    (testing "errors"
      (is (thrown-with-msg? ExceptionInfo
                            #"not a map nor nil"
                            (run-fn [[(fn [_ _]
                                        #{:foo})]])))
      (is (thrown-with-msg? ExceptionInfo
                            #"only :fx and :db supported"
                            (run-fn [[(fn [_ _]
                                        {:fx/foo 1})]]))))))
