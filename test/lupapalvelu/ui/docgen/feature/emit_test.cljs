(ns lupapalvelu.ui.docgen.feature.emit-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.application.consent]
            [lupapalvelu.ui.docgen.feature.emit :as emit]))

(deftest produce-emit-fx-test
  (let [cofx      {:db {}}
        emit-data {:app-id "LP-123"
                   :doc    {:id   "234"
                            :name "some-doc"}
                   :schema {:name "some-schema"}}]

    (testing "unknown -> do nothing"
      (let [args {:emit-data (assoc emit-data :emits #{:foobar})
                  :value     42}]
        (is (= {:db {}}
               (emit/produce-emit-fx cofx [:foo args])))))

    (testing "building demolition"
      (let [args {:emit-data (assoc emit-data :emits #{:buildingDemolition})}]
        (is (= {:db {}
                :fx [[:hub/send {:event :buildingDemolition
                                 :data  {:checked true}}]]}
               (emit/produce-emit-fx cofx [:foo (assoc args :value true)])))
        (is (= {:db {}
                :fx [[:hub/send {:event :buildingDemolition
                                 :data  {:checked false}}]]}
               (emit/produce-emit-fx cofx [:foo (assoc args :value nil)])))))

    (testing "hetu changed"
      (let [args {:emit-data (assoc emit-data :emits #{:hetuChanged})}]
        ;; NOTE: Different name for cljs event, so emits from cljs side don't trigger JS listeners (on development env)
        (is (= {:db {}
                :fx [[:hub/send {:event :hetuChangedCljs
                                 :data  {:docId "234"}}]]}
               (emit/produce-emit-fx cofx [:foo (assoc args :value "joku hetu")])))))

    (testing "email changed"
      (let [args {:emit-data (assoc emit-data
                                    :emits #{:emailChanged}
                                    :schema {:name "some-random-schema"})}]
        (is (= {:db {}}
               (emit/produce-emit-fx cofx [:foo (assoc args :value "joku email")]))))
      (let [args {:emit-data (assoc emit-data
                                    :emits #{:emailChanged}
                                    :doc {:id   "234"
                                          :name "tyonjohtaja-v3"})}]
        (is (= {:db     {}
                ;; TODO: `:emailChanged` handling is current broken due to changes in auth
                #_#_:fx [[:action/query {:name    :foreman-consent-allowed
                                         :params  {:id "LP-123"}
                                         :success :lupapalvelu.ui.application.data/foreman-consent-allowed.success}]]}
               (emit/produce-emit-fx cofx [:foo (assoc args :value "joku email")])))))))
