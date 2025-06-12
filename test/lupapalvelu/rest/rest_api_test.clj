(ns lupapalvelu.rest.rest-api-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.api-common :as api-common]
            [lupapalvelu.json :as json]
            [lupapalvelu.rest.rest-api :as rest-api]
            [sade.strings :as ss]
            [schema.core :as sc]))

(def ^:dynamic *valid-user* true)

(defn- valid-user?
  [_]
  *valid-user*)

(rest-api/defendpoint-for valid-user?
  "path/no/params"
  false
  {:returns sc/Str}
  "Hello world")

(declare GETpath--no--params)

(rest-api/defendpoint-for valid-user?
  "path/with/both/params"
  false
  {:parameters          [:a-string sc/Str
                         :a-int sc/Int]
   :optional-parameters [:a-flag sc/Bool]
   :returns             [(sc/one sc/Str 's)
                         (sc/one sc/Int 'i)
                         (sc/optional sc/Bool 'f)]}
  (->> [a-string a-int a-flag]
       (keep identity)))

(declare GETpath--with--both--params)

(defn do-request
  [endpoint request]
  (let [{{:strs [Content-Type]} :headers :as resp} (endpoint request)]
    (cond-> resp
      (ss/includes? Content-Type "application/json")
      (update :body (fn [v] (json/decode v keyword))))))

(deftest defendpoint-for-test
  (with-redefs [api-common/basic-authentication (constantly {:enabled true})]

    (testing "API which takes no parameters"
      (let [endpoint GETpath--no--params]
        (testing "happy path"
          (is (= {:status  200
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    "Hello world"}
                 (do-request endpoint {}))))
        (testing "happy path, unexpected params"
          (is (= {:status  200
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    "Hello world"}
                 (do-request endpoint {:surprise "123"}))))))

    (testing "API which takes both required and optional parameters"
      (let [endpoint GETpath--with--both--params]
        (testing "happy path, all params"
          (is (= {:status  200
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    ["hello" 42 true]}
                 (do-request endpoint {:a-string "hello"
                                       :a-int    42
                                       :a-flag   true}))))
        (testing "happy path, only required params"
          (is (= {:status  200
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    ["hello" 42]}
                 (do-request  endpoint {:a-string "hello"
                                        :a-int    42}))))
        (testing "happy path, unexpected params"
          (is (= {:status  200
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    ["hello" 42 true]}
                 (do-request endpoint {:a-string "hello"
                                       :a-int    42
                                       :a-flag   true
                                       :surprise "123"}))))

        (testing "required param missing"
          (is (= {:status  400
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    {:ok   false
                            :text "error.input-validation-error"}}
                 (do-request endpoint {})))
          (is (= {:status  400
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    {:ok   false
                            :text "error.input-validation-error"}}
                 (do-request endpoint {:a-string "hello"}))))

        (testing "incorrect param type"
          (is (= {:status  400
                  :headers {"Content-Type" "application/json; charset=utf-8"}
                  :body    {:ok   false
                            :text "error.input-validation-error"}}
                 (do-request endpoint {:a-string "hello"
                                       :a-int    "42"}))))

        (testing "invalid user"
          (binding [*valid-user* false]
            (is (= {:status 401
                    :body   "Unauthorized"}
                   (do-request endpoint {:a-string "hello"
                                         :a-int    42
                                         :a-flag   true})))))))))
