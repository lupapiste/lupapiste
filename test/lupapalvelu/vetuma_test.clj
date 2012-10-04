(ns lupapalvelu.vetuma-test
  (:use lupapalvelu.vetuma
        clojure.test
        midje.sweet))

(facts "digest calculation based on vetuma docs"
  (let [data     "***REMOVED***1&VETUMA-APP2&20061218151424309&6&0,1,2,3,6&LOGIN&EXTAUTH&fi&https://localhost/Show.asp&https://localhost/ShowCancel.asp&https://localhost/ShowError.asp&***REMOVED***&trid1234567890&***REMOVED***1-***REMOVED***&"]
    (fact (mac data) => "72A72A046BD5561BD1C47F3B77FC9456AD58C9C428CACF44D502834C9F8C02A3")))
