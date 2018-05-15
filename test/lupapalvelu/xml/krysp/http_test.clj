(ns lupapalvelu.xml.krysp.http-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [midje.sweet :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.schema-generators :as ssg]
            [sade.schema-utils :as ssu]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.organization :as org]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [lupapalvelu.xml.krysp.http :refer :all])
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
  (fact "url alone OK"
    (create-url :application {:url "http://testi.fi"}) => "http://testi.fi")
  (fact "url alone also stripped slashes"
    (create-url :application {:url "http://testi.fi///"}) => "http://testi.fi")
  (fact "correct path is selected"
    (create-url :review {:url "http://testi.fi" :path {:application "testi/" :review "katselmus"}}) => "http://testi.fi/katselmus"))


(when (and (env/feature? :embedded-artemis) (env/feature? :jms))
  (def msgs (atom []))
  (def app-id "LP-123-1970-99999")
  (def test-queue-name (str kuntagml-queue "_test" (now)))
  (def xml-string "<?xml ?>><jee>testi</jee>")
  (defn handler [data] (swap! msgs conj data))
  (def consumer (jms/create-nippy-consumer test-queue-name handler))
  (def test-producer (jms/create-nippy-producer test-queue-name))
  (with-redefs [lupapalvelu.xml.krysp.http/nippy-producer test-producer]
    (facts "JMS"
      (against-background
        [(lupapalvelu.integrations.messages/save anything) => nil
         (lupapalvelu.integrations.messages/update-message anything anything) => nil]
        (fact "send OK"
          (send-xml
            {:id app-id :organization "123-TEST"}
            {:id "fuuser" :username "test"}
            :application
            xml-string
            (ssg/generate org/KryspHttpConf)) => nil
          (provided (POST anything anything anything) => nil :times 0))
        (facts "consumer caught message"
          (Thread/sleep 100)
          (fact "one message" (count @msgs) => 1)
          (fact "data is clojure and xml-string"
            (:xml (first @msgs)) => xml-string)))))
  (.close consumer))
