(ns lupapalvelu.backing-system.dynasty.api.client-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.backing-system.dynasty.api.client :as client]
            [lupapalvelu.backing-system.dynasty.api.client-proto :as client-proto]))

(def server-config
  {:host         "localhost"
   :organization "3"
   :auth         {:host          "google.com"
                  :client-id     "client"
                  :client-secret "hunter2"}})

(def expected-request
  {:url     "localhost/dynasty.externalinterface.oauth/api/v2/foo"
   :auth    {:url           "google.com/dynasty.identityserver/connect/token"
             :client-id     "client"
             :client-secret "hunter2"}
   :headers {:organization "3"}})


(deftest request-build-test
  (let [*request    (atom nil)
        mock-client (reify client-proto/DynastyClient
                      (-get [_ req]
                        (reset! *request req)
                        nil)
                      (-post [_ req]
                        (reset! *request req)
                        nil))]
    (testing "get"
      (client/get! mock-client "/foo" server-config)
      (is (= expected-request
             (-> *request deref)))
      (testing "without auth"
        (client/get! mock-client "/foo" (dissoc server-config :auth))
        (is (= (dissoc expected-request :auth :headers)
               (-> *request deref)))))
    (testing "post"
      (testing "without params"
        (client/post! mock-client "/foo" server-config)
        (is (= expected-request
               (-> *request deref))))
      (testing "with form params"
        (client/post! mock-client "/foo" server-config {:form-params {:foo-bar-baz 1
                                                                      :schema-id   "1"
                                                                      :meta-json   {:param-a 1
                                                                                    :param-b "foo"
                                                                                    :param-c {:schema-id "id"}}}})
        (is (= (merge expected-request
                      {:form-params {:fooBarBaz 1
                                     :schemaID  "1"
                                     :metaJSON  "{\"ParamA\":1,\"ParamB\":\"foo\",\"ParamC\":{\"SchemaId\":\"id\"}}"}})
               (-> *request deref))))
      (testing "with multipart"
        (client/post! mock-client "/foo" server-config {:multipart [{:name "foo" :content {:foo-bar-baz 1
                                                                                           :schema-id   "1"
                                                                                           :meta-json   {}}}
                                                                    {:name "bar" :content "str"}]})
        (is (= (merge expected-request
                      {:multipart [{:name      "foo"
                                    :content   "{\"fooBarBaz\":1,\"schemaID\":\"1\",\"metaJSON\":\"{}\"}"
                                    :mime-type "application/json"
                                    :encoding  "UTF-8"}
                                   {:name    "bar"
                                    :content "str"}]})
               (-> *request deref)))))))

(deftest response-parse-test
  (let [response    {:fooBarBaz  1
                     :schemaID   "1"
                     :empty-map  {}
                     :empty-coll []
                     :empty-str  ""
                     :nil-value  nil
                     :bar        [{:eInvEDICode "123"
                                   :asd         nil}
                                  nil
                                  {:quuz ""}]}
        mock-client (reify client-proto/DynastyClient
                      (-get [_ req] response)
                      (-post [_ req] response))]
    (is (= {:foo-bar-baz 1
            :schema-id   "1"
            ;; Note that vectors are not trimmed
            :bar         [{:e-inv-edi-code "123"}
                          nil
                          {}]}
           (client/get! mock-client "/foo" server-config)))))
