(ns lupapalvelu.vetuma-test
  (:use lupapalvelu.vetuma
        clojure.test
        midje.sweet))

(facts "digest calculation based on vetuma docs"
  (let [data     "***REMOVED***1&VETUMA-APP2&20061218151424309&6&0,1,2,3,6&LOGIN&EXTAUTH&fi&https://localhost/Show.asp&https://localhost/ShowCancel.asp&https://localhost/ShowError.asp&***REMOVED***&trid1234567890&***REMOVED***1-***REMOVED***&"]
    (fact (mac data) => "72A72A046BD5561BD1C47F3B77FC9456AD58C9C428CACF44D502834C9F8C02A3")))

#_(def ret {"RCVID" "***REMOVED***1"
          "USERID" "210281-9988"
          "ERRURL" "https://localhost:8443/vetuma/error"
          "RETURL" "https://localhost:8443/vetuma/return"
          "MAC" "CB25FB2CAF6CF7CB2577B053C1604D3F4174A225E94F2551CAA2C9F2669B7CEB"
          "TIMESTMP" "20121004131351353"
          "STATUS" "SUCCESSFUL"
          "SUBJECTDATA" "ETUNIMI=PORTAALIA, SUKUNIMI=TESTAA"
          "TRID" "58775279672526028038"
          "EXTRADATA" "HETU=210281-9988"
          "LG" "fi"
          "SO" "62"
          "CANURL" "https://localhost:8443/vetuma/cancel"})