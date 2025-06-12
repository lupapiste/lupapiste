(ns lupapalvelu.conversion.proxy-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion-test-util :refer [with-proxy]]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.conversion.proxy :as conv-p]
            [lupapalvelu.mongo :as mongo]
            [ring.util.codec :as codec]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(def proxy-config #'conv-p/proxy-config)

(def proxy-data {:role "proxy" :secret "paranoid android"})
(def client-data {:role   "client"
                  :host   "http://example.net"
                  :secret "paranoid android"})

(deftest proxy-config-test
  (testing "Bad or empty configs"
    (doseq [data [nil {} {:foo "bar"}
                  (assoc proxy-data :foo "bar")
                  (assoc proxy-data :role "bad")
                  (assoc client-data :host "bad url")
                  (dissoc client-data :host)
                  "hello" 123]]
      (is (nil? (with-proxy data (proxy-config))))))
  (testing "Good configs"
    (is (= {:role :proxy :secret "paranoid android"}
           (with-proxy proxy-data (proxy-config))))
    (is (= {:role :proxy :secret "paranoid android"}
           (with-proxy
             (util/map-values #(str " " % " ") proxy-data)
             (proxy-config))))
    (is (= {:role   :client
            :host   "http://example.net"
            :secret "paranoid android"}
           (with-proxy client-data (proxy-config)))))
  (testing "endpoint?"
    (with-redefs
      [env/dev-mode? (constantly false)]
      (is (true? (with-proxy proxy-data (conv-p/endpoint?))))
      (is (false? (with-proxy client-data (conv-p/endpoint?))))
      (is (false? (with-proxy nil (conv-p/endpoint?)))))
    (with-redefs
      [env/dev-mode? (constantly true)]
      (is (true? (with-proxy proxy-data (conv-p/endpoint?))))
      (is (true? (with-proxy client-data (conv-p/endpoint?))))
      (is (true? (with-proxy nil (conv-p/endpoint?))))))
  (testing "use-proxy?"
    (is (false? (with-proxy proxy-data (conv-p/use-proxy?))))
    (is (true? (with-proxy client-data (conv-p/use-proxy?))))
    (is (false? (with-proxy nil (conv-p/use-proxy?))))))

(def nippy-password #'conv-p/nippy-password)

(deftest nippy-password-test
  (let [pw {:password [:cached "paranoid android"]}]
    (is (= pw (with-proxy proxy-data (nippy-password))))
    (is (= pw (with-proxy client-data (nippy-password))))
    (is (thrown-with-msg? AssertionError #"Assert failed: %"
                          (with-proxy nil (nippy-password))))))

(def encode-ts #'conv-p/encode-timestamp)
(def decode-ts #'conv-p/decode-timestamp)

(defn make-auth [auth]
  (->> auth
       (str "Basic ")
       (assoc-in {} [:headers "authorization"])))

(defn make-request [password]
  (-> (str "tester:" password)
      .getBytes
      codec/base64-encode
      make-auth))

(deftest timestamps-test
  (with-proxy proxy-data
    (let [now             (date/now)
          minute-ago      (date/minus now :minute)
          second-ago      (date/minus now :second)
          second-future   (date/plus now :second)
          ten-sec-future  (date/plus now :seconds 10)
          [ts-minute-ago ts-second-ago ts-second-future
           ts-ten-future] (for [ts [minute-ago second-ago second-future
                                    ten-sec-future]]
                            (with-redefs
                              [date/now (constantly ts)]
                              (encode-ts)))
          valid-auth?     (util/fn-> make-request conv-p/valid-basic-auth?)]
      (testing "decode-timestamp"
        (is (string? ts-minute-ago))
        (is (string? ts-second-ago))
        (is (date/eq? minute-ago (decode-ts ts-minute-ago)))
        (is (date/eq? second-ago (decode-ts ts-second-ago)))
        (is (date/eq? second-future (decode-ts ts-second-future)))
        (is (date/eq? ten-sec-future (decode-ts ts-ten-future))))

      (with-redefs [date/now (constantly now)]
        (testing "valid-basic-auth?"
          (is (false? (valid-auth? ts-minute-ago)))
          (is (true? (valid-auth? ts-second-ago)))
          (is (true? (valid-auth? ts-second-future)))
          (is (false? (valid-auth? ts-ten-future)))))
      (testing "garble"
        (doseq [bad [123 (random-uuid) nil " " identity Integer/MAX_VALUE
                     "hello" {} "left:right"]]
          (is (false? (valid-auth? bad)))
          (is (false? (conv-p/valid-basic-auth? bad)))
          (is (false? (conv-p/valid-basic-auth? (make-auth bad))))
          (is (false? (conv-p/valid-basic-auth? (make-auth (str "still:" bad))))))))))

(def endpoint-url #'conv-p/endpoint-url)

(deftest endpoint-url-test
  (is (= "http://example.net/conversion-proxy"
         (with-proxy client-data (endpoint-url))))
  (is (= "https://test.example.org/conversion-proxy"
         (with-proxy
           (assoc client-data :host " https://test.example.org/ ")
           (endpoint-url))))
  (is (nil? (with-proxy proxy-data (endpoint-url))))
  (is (nil? (with-proxy nil (endpoint-url)))))

(defn check-response? [proxy request params status body]
  (let [r (with-proxy proxy
            (conv-p/proxy-response request params))]
    (and (= (:status r) status)
         (cond
           (string? body) (= (:body r) body)
           (nil? body)    true
           :else          (re-find body (:body r))))))

(deftest proxy-response-test
  (with-redefs [env/dev-mode? (constantly false)]
    (testing "Errors"
      (doseq [p [nil {} {:foo "bar"} client-data]]
        (is (true? (check-response? p {} {} 404 "Not found"))))
      (doseq [params [{} nil {:organization-id 10}
                      {:backend-id :hello}
                      {:organization-id " " :backend-id ""}
                      [1 2 3 4]]]
        (is (true? (check-response? proxy-data {} params 400 nil))))
      (is (true? (check-response? proxy-data (make-request "foobar")
                                  {:organization-id "oid"
                                   :backend-id      "12-1234-R"}
                                  401 "Unauthorized")))
      (with-redefs [mongo/select-one (constantly nil)]
        (is (true? (check-response? proxy-data
                                    (with-proxy proxy-data
                                      (make-request (encode-ts)))
                                    {:organization-id "oid"
                                     :backend-id      "12-1234-R"}
                                    400 "Bad request"))))
      (with-redefs [mongo/select-one    (constantly {})
                    conv-util/fetch-xml (constantly {:error "Oh dear"})]
        (is (true? (check-response? proxy-data
                                    (with-proxy proxy-data
                                      (make-request (encode-ts)))
                                    {:organization-id "oid"
                                     :backend-id      "12-1234-R"}
                                    400 "Oh dear")))))
    (testing "Chaos"
      (with-redefs [mongo/select-one (fn [& _]
                                       (throw (ex-info "Kaboom!" {})))]
        (is (true? (check-response? proxy-data
                                    (with-proxy proxy-data
                                      (make-request (encode-ts)))
                                    {:organization-id "oid"
                                     :backend-id      "12-1234-R"}
                                    500 "Kaboom!")))))
    (testing "Success"
      (with-redefs [mongo/select-one    (constantly {})
                    conv-util/fetch-xml (constantly {:xml "<foo>KuntaGML</foo>"})]
        (is (= {:body    "<foo>KuntaGML</foo>"
                :headers {"Content-Type" "text/xml; charset=utf-8"}
                :status  200}
               (with-proxy proxy-data
                 (conv-p/proxy-response (make-request (encode-ts))
                                        {:organization-id "oid"
                                         :backend-id      "12-1234-R"}))))))))
