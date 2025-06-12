(ns lupapalvelu.application-search-api-test
  (:require [clojure.test :refer [deftest testing is]]
            [lupapalvelu.application-search-api :as app-search-api]
            [sade.env :as env]))

(defn mock-fn
  "Creates a mock function that returns values from a map of parameter vectors,
   falling back to calling the else-fn for unmocked parameters."
  [params-map else-fn]
  (fn [& args]
    (let [args-vec (vec args)]
      (if (contains? params-map args-vec)
        (params-map args-vec)
        (apply else-fn args)))))



(def empty-op {:id nil :created nil :name nil :description nil :displayNameFi "Ei tiedossa" :displayNameSv "Ok\u00e4nd"})

(deftest localize-application-test
  ; Presence of state is checked with a smoke test.
  ; Primary operation might be missing in legacy data.
  (testing "Only state set"
    (is (= (app-search-api/localize-application {:state :open})
           {:state               :open
            :stateNameFi         "N\u00e4kyy viranomaiselle"
            :stateNameSv         "Under beredning"
            :primaryOperation    empty-op
            :secondaryOperations []})))

  (testing "Primary operation name is localized"
    (is (= (app-search-api/localize-application {:state            :open
                                                 :primaryOperation {:name "ya-jatkoaika"}})
           {:state               :open
            :stateNameFi         "N\u00e4kyy viranomaiselle"
            :stateNameSv         "Under beredning"
            :primaryOperation    {:id            nil
                                  :created       nil
                                  :name          "ya-jatkoaika"
                                  :description   nil
                                  :displayNameFi "Jatkoajan hakeminen"
                                  :displayNameSv "Ans\u00f6kan om till\u00e4ggstid"}
            :secondaryOperations []})))

  (testing "Secondary operation name is localized, empty operation info is removed"
    (is (= (app-search-api/localize-application {:state               :open
                                                 :secondaryOperations [{:name "ya-jatkoaika"} {}]})
           {:state               :open
            :stateNameFi         "N\u00e4kyy viranomaiselle"
            :stateNameSv         "Under beredning"
            :primaryOperation    empty-op
            :secondaryOperations [{:id            nil
                                   :created       nil
                                   :name          "ya-jatkoaika"
                                   :description   nil
                                   :displayNameFi "Jatkoajan hakeminen"
                                   :displayNameSv "Ans\u00f6kan om till\u00e4ggstid"}]}))))

(deftest applications-search-elastic
  (testing "Elasticsearch is set always on"
    (with-redefs [app-search-api/global-es-application-search
                  (constantly true)

                  env/value
                  (mock-fn {[:elasticsearch :enabled] true} env/value)]
      (is (nil?
            (app-search-api/elasticsearch-allowed :application-search {:user {}})))
      (is (nil?
            (app-search-api/elasticsearch-allowed :application-search {:user {:impersonating true}})))
      (is (= {:ok   false
              :text "error.applications-search-elastic.is-set-always-on"}
             (app-search-api/show-elastic-checkbox :application-search {:user {}})))
      (is (= {:ok   false
              :text "error.applications-search-elastic.is-set-always-on"}
             (app-search-api/show-elastic-checkbox :application-search {:user {:impersonating true}}))))

    (testing "Elasticsearch is NOT set always on - everyone sees the checkbox"
      (with-redefs [app-search-api/global-es-application-search
                    (constantly false)

                    env/value
                    (mock-fn {[:elasticsearch :enabled]                  true
                              [:applications-search-elastic :user-scope] "all"} env/value)]
        (is (nil?
              (app-search-api/elasticsearch-allowed :application-search {:user {}})))
        (is (nil?
              (app-search-api/elasticsearch-allowed :application-search {:user {:impersonating true}})))
        (is (nil?
              (app-search-api/show-elastic-checkbox :application-search {:user {}})))
        (is (nil?
              (app-search-api/show-elastic-checkbox :application-search {:user {:impersonating true}})))))

    (testing "Elasticsearch is NOT set always on - impersonators see checkbox"
      (with-redefs [app-search-api/global-es-application-search
                    (constantly false)

                    env/value
                    (mock-fn {[:elasticsearch :enabled]                  true
                              [:applications-search-elastic :user-scope] "impersonators"} env/value)]
        (is (= {:ok   false
                :text "error.applications-search-elastic.user-not-in-scope"}
               (app-search-api/elasticsearch-allowed :application-search {:user {}})))
        (is (nil?
              (app-search-api/elasticsearch-allowed :application-search {:user {:impersonating true}})))
        (is (= {:ok   false
                :text "error.applications-search-elastic.elasticsearch-not-allowed"}
               (app-search-api/show-elastic-checkbox :application-search {:user {}})))
        (is (nil?
              (app-search-api/show-elastic-checkbox :application-search {:user {:impersonating true}})))))))

