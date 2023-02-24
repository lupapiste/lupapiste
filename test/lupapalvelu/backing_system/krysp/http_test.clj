(ns lupapalvelu.backing-system.krysp.http-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [midje.sweet :refer :all]
            [artemis-server]
            [sade.core :refer :all]
            [sade.schema-generators :as ssg]
            [sade.schema-utils :as ssu]
            [lupapalvelu.organization :as org]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [lupapalvelu.backing-system.krysp.http :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(def conf-with-auth-type
  (prop/for-all [krysp-conf (gen/no-shrink (ssg/generator org/KryspHttpConf))
                 options (gen/no-shrink (gen/one-of [(gen/map gen/keyword gen/string-ascii)
                                                     (gen/map
                                                       (gen/return :headers)
                                                       (gen/map gen/string-ascii gen/string-ascii))]))]
    (let [authentication (wrap-authentication options krysp-conf)]
      (case (:auth-type krysp-conf)

        "basic"    (fact "basic-auth"
                     authentication => (every-checker
                                         map?
                                         (contains {:basic-auth (just ["username" "pw"])})))

        "x-header" (fact "x-header"
                     authentication => (every-checker
                                         map?
                                         (contains {:headers (contains
                                                               {"x-username" "username"
                                                                "x-password" "pw"})})))

        nil (fact "without auth-type options are returned"
              authentication => options)))))

(fact :qc "wrap-authentication"
  (tc/quick-check 180 conf-with-auth-type :max-size 40) => passing-quick-check
  (provided
    (org/get-credentials anything) => ["username" "pw"]))

(fact :qc "create-headers"
  (tc/quick-check
    180
    (prop/for-all [headers (gen/no-shrink (ssg/generator (ssu/get org/KryspHttpConf :headers)))]
      (let [result (create-headers headers)]
        (if (empty? headers)
          (is (nil? result) "empty headers -> nil")
          (fact "key value pairs as string"
            result => map?
            (keys result) => (has every? string?)
            (vals result) => (has every? string?)))))
    :max-size 40) => passing-quick-check)

(facts "create-url"
  (fact "type validated"
    (create-url :lol {:url "http://testi.fi" :path {:verdict "testi"}}) => (throws ExceptionInfo))
  (fact "verdict path joined"
    (create-url :application {:url "http://testi.fi" :path {:application "testi"}}) => "http://testi.fi/testi")
  (fact "slash is stripped"
    (create-url :application {:url "http://testi.fi" :path {:application "testi/"}}) => "http://testi.fi/testi")
  (fact "url alone fails"
    (create-url :application {:url "http://testi.fi"}) => (throws AssertionError))
  (fact "correct path is selected"
    (create-url :review {:url "http://testi.fi" :path {:application "testi/" :review "katselmus"}}) => "http://testi.fi/katselmus"))

