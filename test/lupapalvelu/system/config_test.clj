(ns lupapalvelu.system.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.system.config :as config]
            [sade.env :as env]))

(defn- build-config
  [{:keys [config target-env mode port]}]
  (with-redefs [env/get-config (constantly config)
                env/target-env target-env
                env/mode       mode
                env/port       port]
    (config/config)))

(deftest env-config-test
  (let [target-env "dev"
        mode       :dev
        ctx        {:config     {}
                    :mode       mode
                    :port       nil
                    :target-env target-env}]
    (testing "a completely empty environment fails"
      (is (thrown-with-msg? Exception
                            #"Config does not match schema!"
                            (build-config ctx))))
    (testing "with mongodb properties"
      (let [ctx (assoc ctx :config {:mongodb {:dbname  "database"
                                              :servers {:0 {:host "localhost"
                                                            :port 1234}}}})]
        (testing "just mongo"
          (is (= {:dev-mode?  false
                  :migration  {}
                  :mongo      {:dbname      "database"
                               :servers     {:0 {:host "localhost"
                                                 :port 1234}}
                               :credentials {}}
                  :target-env "dev"}
                 (build-config ctx))))
        (testing "mongo + HTTP stuff"
          (let [ctx (-> ctx
                        (assoc :port 8080)
                        (update :config merge {:host        "localhost:8080"
                                               :cookie-name "my-test-cookie"}))]
            (is (= {:dev-mode?  false
                    :host       "localhost:8080"
                    :http       {:join?                false
                                 :port                 8080
                                 :send-server-version? false}
                    :migration  {}
                    :mongo      {:credentials {}
                                 :dbname      "database"
                                 :servers     {:0 {:host "localhost"
                                                   :port 1234}}}
                    :noir       {:cookie-name "my-test-cookie"
                                 :noir-opts   {:mode             :dev
                                               :ns               'lupapalvelu.web
                                               :resource-options {:mime-types {"js" "application/javascript; charset=utf-8"}}}}
                    :reitit     {:cookie-name "my-test-cookie"
                                 :refresh?    false}
                    :target-env "dev"}
                   (build-config ctx)))))))))
