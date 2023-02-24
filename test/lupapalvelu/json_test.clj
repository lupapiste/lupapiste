(ns lupapalvelu.json-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.json :as json])
  (:import [java.io StringWriter StringReader BufferedReader]))

(deftest process-json-array
  (let [writer (StringWriter.)]
    (json/process-json-array (StringReader. (json/encode [{:one 1 :two 2}
                                                          {:three 3 :secret 4}
                                                          {:secret 5 :six 6}]))
                             writer
                             #(dissoc % :secret))
    (testing "Simple"
      (is (= [{:one 1 :two 2} {:three 3} {:six 6}]
             (json/decode-stream (BufferedReader. (StringReader. (str writer)))
                                 true))))
    (testing "Bad input"
      (is (thrown? com.fasterxml.jackson.core.JsonParseException
                   (json/process-json-array (StringReader. "Hello world!")
                                            writer
                                            identity)))
      (is (thrown? AssertionError
                   (json/process-json-array (StringReader. "{\"number\": 10}")
                                            writer
                                            identity)))
      (is (thrown? com.fasterxml.jackson.core.JsonParseException
                   (json/process-json-array (StringReader. "[{\"neverending\": 10}, {\"array\": 11}")
                                            writer
                                            identity))))))

(deftest make-json-array-reader
  (testing "Good read"
    (let [read-fn (json/make-json-array-reader (StringReader. (json/encode [{:one 1 :two 2}
                                                                            {:three 3 :secret 4}
                                                                            {:secret 5 :six 6}])))]
      (is (= {:one 1 :two 2} (read-fn)))
      (is (= {:three 3 :secret 4} (read-fn)))
      (is (= {:secret 5 :six 6} (read-fn)))
      (is (= ::json/end (read-fn)))))
  (testing "Bad input"
    (is (thrown? com.fasterxml.jackson.core.JsonParseException
                 (json/make-json-array-reader (StringReader. "Hello world!"))))
      (is (thrown? AssertionError
                   (json/make-json-array-reader (StringReader. "{\"number\": 10}"))))
      (let [read-fn (json/make-json-array-reader (StringReader. "[{\"neverending\": 10}"))]
        (is (= {:neverending 10} (read-fn)))
        (is (thrown? com.fasterxml.jackson.core.JsonParseException (read-fn))))))
