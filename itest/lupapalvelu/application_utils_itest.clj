(ns lupapalvelu.application-utils-itest
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.itest-util :refer [local-fixture-factory]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.statistics.core :as stats]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [monger.operators :refer :all]))

(use-fixtures :once (local-fixture-factory :db-name "test_application-utils-itest"))

(deftest test-application-link-permits
  (let [app-id-1                     "LP-1"
        app-id-2                     "LP-2"
        app-id-3                     "LP-3"
        app-id-4                     "LP-4"
        app-id-5                     "LP-5"
        mongo-apps                   [{:_id     app-id-1 :submitted 100
                                       :created 10       :address   "One"}
                                      {:_id     app-id-2 :submitted 50
                                       :created 20       :address   "Two"}
                                      {:_id     app-id-3 :created 30
                                       :address "Three"}
                                      {:_id     app-id-4 :submitted 200
                                       :created 5        :address   "Four"}
                                      {:_id     app-id-5 :submitted 75
                                       :created 40       :address   "Five"}]
        [_app1 app2 app3 app4 _app5] (mapv #(set/rename-keys % {:_id :id})
                                           mongo-apps)]
    (mongo/insert-batch :applications mongo-apps)

    (mongo/insert :app-links {:link [app-id-1 app-id-2 app-id-3 app-id-4]})

    (testing "Every link permit fully"
      (is (match? (m/in-any-order [app2 app3 app4])
                  (app-utils/application-link-permits app-id-1))))
    (testing "Projection"
      (is (match? (m/in-any-order (mapv #(select-keys % [:id :address])
                                        [app2 app3 app4]))
                  (app-utils/application-link-permits app-id-1 :projection [:address])))
      (is (match? (m/in-any-order (mapv (partial hash-map :id)
                                        [app-id-2 app-id-3 app-id-4]))
                  (app-utils/application-link-permits app-id-1 :projection [:foobar]))))
    (testing "Query"
      (is (=  [app2]
              (app-utils/application-link-permits app-id-1 :query {:submitted {$lt 100}}))))
    (testing "Both"
      (is (match? (m/in-any-order (mapv #(select-keys % [:id :created :address])
                                        [app2 app3]))
                  (app-utils/application-link-permits app-id-1
                                                      :query {:created {$gt 10}}
                                                      :projection [:created :address :foobar]))))

    (testing "No results"
      (is (nil? (app-utils/application-link-permits app-id-1 :query {:closed {$gt 0}})))
      (is (nil? (app-utils/application-link-permits app-id-5))))))

(deftest test-source-application
  (let [app-id-1   "LP-123-2026-90001"
        app-id-2   "LP-123-2026-90002"
        app-id-3   "LP-123-2026-90003"
        mongo-apps [{:_id app-id-1 :address "Source One" :n 1}
                    {:_id    app-id-2 :address "Source Two" :n 2
                     :_stats {:processing {:status 0}}}
                    {:_id                 app-id-3
                     :address             "Target Three" :n 3
                     :_source-application {:id app-id-1 :kind :change}}]]

    (mongo/insert-batch :applications mongo-apps)

    (testing "Returns source application when valid"
      (is (= {:id app-id-1 :address "Source One" :n 1}
             (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}))))

    (testing "Returns nil when kind mismatch"
      (is (nil? (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}
                                              :kind :continuation))))

    (testing "Returns source application when kind matches"
      (is (= {:id app-id-1 :address "Source One" :n 1}
             (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}
                                           :kind :change))))

    (testing "Projection"
      (is (= {:id app-id-2 :address "Source Two"}
             (app-utils/source-application {:_source-application {:id app-id-2 :kind :change}}
                                           :projection [:address]))))

    (testing "Query"
      (is (= {:id app-id-1 :address "Source One" :n 1}
             (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}
                                           :kind :change :query {:n 1})))
      (is (nil? (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}
                                              :kind :change :query {:n 10}))))

    (testing "Both"
      (is (= {:id app-id-1 :address "Source One"}
             (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}
                                           :kind :change :query {:n 1}
                                           :projection [:address])))
      (is (nil? (app-utils/source-application {:_source-application {:id app-id-1 :kind :change}}
                                              :kind :change :query {:n 10}
                                              :projection [:address]))))

    (testing "Returns nil when source app missing from db"
      (is (nil? (app-utils/source-application {:_source-application {:id   "LP-404-2026-90001"
                                                                     :kind :change}}))))

    (testing "Returns nil when no source app in application"
      (is (nil? (app-utils/source-application {:_id "no-source"}))))

    (testing "unsupported-change-permit?"
      (is (true? (stats/unsupported-change-permit? {:id                  "LP-123-2026-90010"
                                                    :permitSubtype       "muutoslupa"
                                                    :_source-application {:id app-id-1 :kind :change}})))
      (is (true? (stats/unsupported-change-permit? {:id                  "LP-123-2026-90010"
                                                    :permitSubtype       "muutoslupa"
                                                    :_source-application {:id app-id-2 :kind :copy}})))
      (is (false? (stats/unsupported-change-permit? {:id                  "LP-123-2026-90010"
                                                     :permitSubtype       "muutoslupa"
                                                     :_source-application {:id app-id-2 :kind :change}}))))))
