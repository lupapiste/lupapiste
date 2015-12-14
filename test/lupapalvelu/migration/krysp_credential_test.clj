(ns lupapalvelu.migration.krysp-credential-test
  (:require [lupapalvelu.migration.migrations :refer :all]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]))

(facts extract-credentials-from-krysp-address

  (fact (second (extract-credentials-from-krysp-address ["" {:url "http://foo:bar.1@example.com/geoserver/wfs?"}]))
        => 
        {:url "http://example.com/geoserver/wfs?" :username "foo" :password "bar.1"})
  
  (fact (second (extract-credentials-from-krysp-address ["" {:url "http://foo:bar:1@example.com/geoserver/wfs?"}]))
        => 
        {:url "http://example.com/geoserver/wfs?" :username "foo" :password "bar:1"})

  (fact (second (extract-credentials-from-krysp-address ["" {:url "https://foo:bar.1@example.com/geoserver/wfs?"}]))
         => 
         {:url "https://example.com/geoserver/wfs?" :username "foo" :password "bar.1"})

  (fact (second (extract-credentials-from-krysp-address ["" {:url "http://example.com/geoserver/wfs?"}])) => nil)

  (fact (second (extract-credentials-from-krysp-address ["" {:url nil}])) => nil)

  (fact (second (extract-credentials-from-krysp-address ["" nil])) => nil))
