(ns lupapalvelu.perf-mon-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(def sample-timing {:responseEnd 1442817640771,
                    :responseStart 1442817640770,
                    :domInteractive 1442817641846,
                    :navigationStart 1442817640376,
                    :domContentLoadedEventEnd 1442817642108,
                    :fetchStart 1442817640394,
                    :domComplete 1442817642295,
                    :redirectEnd 1442817640392,
                    :unloadEventStart 0,
                    :loadEventEnd 1442817642296,
                    :loadEventStart 1442817642295,
                    :domLoading 1442817640771,
                    :connectEnd 1442817640394,
                    :redirectStart 1442817640377,
                    :requestStart 1442817640395,
                    :domainLookupStart 1442817640394,
                    :domContentLoadedEventStart 1442817641868,
                    :unloadEventEnd 0,
                    :connectStart 1442817640394,
                    :domainLookupEnd 1442817640394})

(fact "No perf data in empty DB"
  (let [resp (http-get (str (server-address) "/perfmon/data") {:as :json})]
    (:status resp) => 200
    (:body resp) => empty?))

(fact "browser-timing command returns ok:true"
  (command mikko :browser-timing :timing sample-timing :pathname "/") => ok?)

(fact "Some perf data in DB"
  (let [resp (http-get (str (server-address) "/perfmon/data") {:as :json})]
    (:status resp) => 200
    (:body resp) => seq))
