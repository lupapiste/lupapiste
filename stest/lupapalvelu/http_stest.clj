(ns lupapalvelu.http-stest
  (:require [clojure.test :refer [deftest is testing]]
            [sade.http :as http])
  (:import [org.apache.http.client RedirectException]))

(deftest redirect-http-test
  ;; NOTE: Just to be clear, the purpose of this tests is to make sure we follow
  ;; redirects correctly. We just happen to use our own bulletin URL for this as
  ;; it performs several redirects and is within our control.
  (testing "respect options"
    (let [{:keys [status
                  trace-redirects]} (http/get "http://kuulutukset.lupapiste.fi"
                                              :redirect-strategy :none)]
      (is (= 301 status))
      (is (= []
             trace-redirects))))
  (testing "default max-redirects"
    (let [{:keys [status
                  trace-redirects]} (http/get "http://kuulutukset.lupapiste.fi")]
      (is (= 200 status))
      (is (= ["https://kuulutukset.lupapiste.fi:443/"
              "https://kuulutukset.lupapiste.fi:443/app/fi/bulletins"]
             trace-redirects))))
  (testing "limit max-redirects"
    (testing "without exceptions"
      (let [{:keys [status
                    trace-redirects]} (http/get "http://kuulutukset.lupapiste.fi"
                                                :max-redirects 1)]
        (is (= 302 status))
        (is (= ["https://kuulutukset.lupapiste.fi:443/"]
               trace-redirects))))
    (testing "with exceptions"
      (is (thrown-with-msg? RedirectException
                            #"Maximum redirects \(1\) exceeded"
                            (http/get "http://kuulutukset.lupapiste.fi"
                                      :max-redirects 1
                                      :throw-exceptions true))))))
