(ns lupapalvelu.suomifi-messages-test
  (:require [clojure.string :as s]
            [clojure.test :refer [deftest is testing]]
            [clj-time.coerce :refer [to-long]]
            [clj-time.local :refer [local-now]]
            [lupapalvelu.suomifi-messages :as sm]
            [lupapalvelu.suomifi-messages-api :refer [->Recipient]])
  (:import [java.util UUID]))

(def mock-responses
  {:success {:body "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
                   <soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                   <soap:Header xmlns:asi=\"http://www.suomi.fi/asiointitili\"/>
                   <soap:Body xmlns:asi=\"http://www.suomi.fi/asiointitili\">
                   <LahetaViestiResponse xmlns=\"http://www.suomi.fi/asiointitili\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                   <LahetaViestiResult> <TilaKoodi>
                   <TilaKoodi>202</TilaKoodi>
                   <TilaKoodiKuvaus>Asia tallennettuna asiointitilipalvelun k\u00e4sittelyjonoon
                   mutta se ei vielä n\u00e4y asiakkaan asiointitilill\u00e4. Lopullinen vastaus on haettavissa erikseen erillisell\u00e4 kutsulla.</TilaKoodiKuvaus>
                   <SanomaTunniste>1519043354_997202863</SanomaTunniste> </TilaKoodi>
                   </LahetaViestiResult> </LahetaViestiResponse>
                   </soap:Body> </soap:Envelope>"}
   :full-response-map {:request-time 538
                       :repeatable? false
                       :protocol-version {:name "HTTP"
                                          :major 1
                                          :minor 1}
                       :streaming? true
                       :chunked? false
                       :cookies {"BIGipServer~vaka_intra_st3~pdf.vvp.qa.suomi.vyv.fi_https_pool" {:discard true
                                                                                                  :path "/"
                                                                                                  :secure true
                                                                                                  :value "rd14o00000000000000000000ffff0a7a5212o8082"
                                                                                                  :version 0}}
                       :reason-phrase "OK"
                       :headers {"Server" "Jetty(9.4.12.v20180830)"
                                 "Content-Type" "text/xml;charset=utf-8"
                                 "X-Content-Type-Options" "nosniff"
                                 "Content-Length" "498"
                                 "X-Frame-Options" "DENY"
                                 "Connection" "close"
                                 "Accept" "application/json"
                                 "operation" "LahetaViesti"
                                 "Expires" "0"
                                 "breadcrumbId" "ID-vkctvvpserv1-valtiokonttori-fi-1560828600514-0-296953"
                                 "X-Forwarded-For" "185.18.78.166"
                                 "Accept-Encoding" "gzip
                                                   deflate"
                                 "Date" "Tue
                                        25 Jun 2019 12:58:32 GMT"
                                 "X-XSS-Protection" "1; mode=block"}
                       :orig-content-encoding nil
                       :status 200
                       :length 498
                       :body "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                             <soap:Body><LahetaViestiResponse xmlns=\"http://www.suomi.fi/asiointitili\">
                             <LahetaViestiResult><TilaKoodi><TilaKoodi>202</TilaKoodi>
                             <TilaKoodiKuvaus>Asia tallennettuna asiointitilipalvelun k\u00e4sittelyjonoon mutta se ei viel\u00e4 n\u00e4y asiakkaan asiointitilill\u00e4.</TilaKoodiKuvaus>
                             <SanomaTunniste>eebeec40-9748-11e9-94e5-ab54eb21ca4d</SanomaTunniste>
                             </TilaKoodi></LahetaViestiResult></LahetaViestiResponse></soap:Body></soap:Envelope>"
                       :trace-redirects ["https://qat.integraatiopalvelu.fi/Asiointitili/ViranomaispalvelutWSInterfaceNonSigned"]}
   :content-error {:body "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
                         <soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                         <soap:Header xmlns:asi=\"http://www.suomi.fi/asiointitili\"/> <soap:Body xmlns:asi=\"http://www.suomi.fi/asiointitili\">
                         <LahetaViestiResponse xmlns=\"http://www.suomi.fi/asiointitili\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                         <LahetaViestiResult> <TilaKoodi> <TilaKoodi>525</TilaKoodi>
                         <TilaKoodiKuvaus>Asian tietosis\u00e4ll\u00f6ss\u00e4 virheit\u00e4.</TilaKoodiKuvaus> <SanomaTunniste>1519114545_875730352</SanomaTunniste>
                         </TilaKoodi> </LahetaViestiResult></LahetaViestiResponse> </soap:Body></soap:Envelope>"}
   :other-error {:body "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
                       <soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                       <soap:Header xmlns:asi=\"http://www.suomi.fi/asiointitili\"/> <soap:Body xmlns:asi=\"http://www.suomi.fi/asiointitili\">
                       <LahetaViestiResponse xmlns=\"http://www.suomi.fi/asiointitili\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">
                       <LahetaViestiResult> <TilaKoodi>
                       <TilaKoodi>550</TilaKoodi>
                       <TilaKoodiKuvaus>Muu virhe.</TilaKoodiKuvaus> <SanomaTunniste>1521094754_459310592</SanomaTunniste>
                       </TilaKoodi> </LahetaViestiResult></LahetaViestiResponse> </soap:Body>
                       </soap:Envelope>"}})

(deftest client-element
  (let [recipient (->Recipient "Percy"
                               "Panaani"
                               "Tutti Frutin katu 6 T 9"
                               "00102"
                               "Piippola"
                               "FI"
                               "310599-955C")]
    (testing "Record can be formed from sane data"
      (let [rec (sm/->asiakas recipient)]
        (is (record? rec))
        (is (true? (not-any? empty? (vals rec))))))
    (testing "Validation throws an error when fields contains invalid values"
      (is (thrown-with-msg? Exception #"->asiakas does not match schema" (sm/->asiakas (assoc recipient :zipcode 23))))
      (is (thrown-with-msg? Exception #"->asiakas does not match schema" (sm/->asiakas (update recipient :person-id drop-last)))))))

(deftest response-parsing
  (testing "Checking that the example responses are parsed correctly"
    (let [{:keys [SanomaTunniste TilaKoodi TilaKoodiKuvaus]} (sm/parse-response (:success mock-responses))]
      (is (= "202" TilaKoodi))
      (is (s/includes? TilaKoodiKuvaus "Asia tallennettuna asiointitilipalvelun k\u00e4sittelyjonoon"))
      (is (= SanomaTunniste "1519043354_997202863")))
    (let [{:keys [SanomaTunniste TilaKoodi TilaKoodiKuvaus]} (sm/parse-response (:content-error mock-responses))]
      (is (= "525" TilaKoodi))
      (is (s/includes? TilaKoodiKuvaus "Asian tietosis\u00e4ll\u00f6ss\u00e4 virheit\u00e4"))
      (is (= SanomaTunniste "1519114545_875730352")))
    (let [{:keys [SanomaTunniste TilaKoodi TilaKoodiKuvaus]} (sm/parse-response (:other-error mock-responses))]
      (is (= "550" TilaKoodi))
      (is (s/includes? TilaKoodiKuvaus "Muu virhe."))
      (is (= SanomaTunniste "1521094754_459310592"))))
  (testing "Check that parsing of the full example response map works"
    (let [res (:full-response-map mock-responses)
          {:keys [TilaKoodi TilaKoodiKuvaus SanomaTunniste]} (sm/parse-response res)]
      (is (= "202" TilaKoodi))
      (is (s/includes? TilaKoodiKuvaus "Asia tallennettuna asiointitilipalvelun k\u00e4sittelyjonoon"))
      (is (= "eebeec40-9748-11e9-94e5-ab54eb21ca4d" SanomaTunniste)))))

(deftest too-large-messages
  (testing "We can detect too large messages for which the sending will fail"
    (let [huge-string (s/join (take (* 1024 1024 3) (repeat "a")))]
      (is (false? (sm/message-too-large? huge-string)))
      (is (true? (sm/message-too-large? (str huge-string "a")))))))

(def test-app
  {:attachments [{:type {:type-id "ote_kauppa_ja_yhdistysrekisterista" :type-group "hakija"}
                  :latestVersion {:contentType "application/pdf"
                                  :filename "ote.pdf"
                                  :fileId "5cfe4116d2315419cea4dd0b"}}
                 {:type {:type-id "julkisivupiirustus" :type-group "paapiirustus"}
                  :latestVersion {:contentType "application/pdf"
                                  :filename "paapiirustus.pdf"
                                  :fileId "5b02bf8559412f2f66ea0d26"}}]})

(def test-attachment-selection
  [{:type-id "ote_kauppa_ja_yhdistysrekisterista"
    :title "Ote kauppa- ja yhdistysrekisterist\u00e4"
    :type-group "hakija"}])

(deftest attachment-ids
  (testing "We can get the id's of of attachments we want to include to the message"
    (is (= (vector "5cfe4116d2315419cea4dd0b") (sm/get-attachment-ids test-app test-attachment-selection)))
    (is (= (vector "5cfe4116d2315419cea4dd0b" "5b02bf8559412f2f66ea0d26")
           (sm/get-attachment-ids test-app (conj test-attachment-selection
                                                 {:type-id "julkisivupiirustus"
                                                  :title "Julkisivupiirustus"
                                                  :type-group "paapiirustus"}))))
    (is (= [] (sm/get-attachment-ids (update-in test-app [:attachments] rest) test-attachment-selection)))
    (is (= [] (sm/get-attachment-ids {} test-attachment-selection)))))

(deftest integration-message
  (testing "We can build and save an integration message if the inputs fit the schema"
    (let [ts (to-long (local-now))]
      (is (= (sm/build-integration-message "5d10b39b26be76653a9b82fa"
                                           {:id "5d10b3b626be76653a9b82fb" :username "jartsa@jarvisuomi.com"}
                                           ts
                                           {:id "LP-753-2019-12345" :organization "753-R" :state "verdictGiven"}
                                           {:Tiedostot []}
                                           "5d10b4ab26be76653a9b82fd"
                                           test-attachment-selection)
             {:action "send-suomifi-verdict"
              :application {:id "LP-753-2019-12345"
                            :organization "753-R"
                            :state "verdictGiven"}
              :attachmentsCount 0
              :created ts
              :direction "out"
              :partner "suomifi-messages"
              :format "xml"
              :id "5d10b39b26be76653a9b82fa"
              :messageType "suomifi-messages-verdict"
              :status "processing"
              :transferType "http"
              :initiator {:id "5d10b3b626be76653a9b82fb"
                         :username "jartsa@jarvisuomi.com"}})))))

(def test-suomifi-settings
  (merge
    {:authority-id "Viranomaistunnus-uus"
     :service-id "Palvelutunnus6"
     :verdict {:enabled true
               :message "T\u00e4m\u00e4 on p\u00e4t\u00f6s rakennuslupa-asiaasi liittyen."
               :attachments []}}
    {:cert-common-name "MegaPalvelu2000"
     :url "http://localhost:8000"
     :sign false
     :dummy false}))

(deftest building-hae-asiakkaita-message ;;TODO refactor to take into account the new signature
  (testing "->HaeAsiakkaitaXMLString converts a HaeAsiakkaitaMessage record to a proper XML string"
    (let [msg-id (UUID/fromString "48052aa2-a474-11e9-a2a3-2a2ae2dbcce4")
          record (sm/build-hae-asiakkaita-message "010203-040A" "753-R" "foo@viranomainen.fi" msg-id test-suomifi-settings)
          expected-xml
          "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:asi=\"http://www.suomi.fi/asiointitili\">
   <soapenv:Body>
      <asi:HaeAsiakkaita>
        <asi:Viranomainen>
          <asi:ViranomaisTunnus>Viranomaistunnus-uus</asi:ViranomaisTunnus>
          <asi:PalveluTunnus>Palvelutunnus6</asi:PalveluTunnus>
          <asi:KayttajaTunnus>foo@viranomainen.fi</asi:KayttajaTunnus>
          <asi:SanomaTunniste>48052aa2-a474-11e9-a2a3-2a2ae2dbcce4</asi:SanomaTunniste>
          <asi:SanomaVersio>1.0</asi:SanomaVersio>
          <asi:SanomaVarmenneNimi>MegaPalvelu2000</asi:SanomaVarmenneNimi>
        </asi:Viranomainen>
         <asi:Kysely>
             <asi:KyselyLaji>Asiakkaat</asi:KyselyLaji>
             <asi:Asiakkaat>
                 <asi:Asiakas AsiakasTunnus=\"010203-040A\" TunnusTyyppi=\"SSN\"/>
             </asi:Asiakkaat>
         </asi:Kysely>
      </asi:HaeAsiakkaita>
   </soapenv:Body>
</soapenv:Envelope>
"]
      (is (= (sm/HaeAsiakkaitaMessage->string record) expected-xml)))))


(defn hae-asiakkaita-resp-xml [{:keys [can-receive-msgs? account-disabled?] }]
  (let [account-state-value (if can-receive-msgs?
                              300
                              310)
        account-disabled-value (if account-disabled?
                                 1
                                 0)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">
  <soapenv:Header xmlns:asi=\"http://www.suomi.fi/asiointitili\"/>
  <soapenv:Body xmlns:asi=\"http://www.suomi.fi/asiointitili\">
    <asi:HaeAsiakkaitaResponse>
      <asi:HaeAsiakkaitaResult>
        <asi:TilaKoodi>
          <asi:TilaKoodi>0</asi:TilaKoodi>
          <asi:TilaKoodiKuvaus>Onnistui</asi:TilaKoodiKuvaus>
          <asi:SanomaTunniste>1524033786_837807286</asi:SanomaTunniste>
        </asi:TilaKoodi>
        <asi:Asiakkaat>
          <asi:Asiakas AsiakasTunnus=\"010101-0101\" TunnusTyyppi=\"SSN\">
            <asi:Tila>" account-state-value "</asi:Tila>
            <asi:TilaPvm>2018-02-22T11:00:41.143+02:00</asi:TilaPvm>
            <asi:TiliPassivoitu>" account-disabled-value "</asi:TiliPassivoitu>
          </asi:Asiakas>
        </asi:Asiakkaat>
      </asi:HaeAsiakkaitaResult>
    </asi:HaeAsiakkaitaResponse>
  </soapenv:Body>
</soapenv:Envelope>")))

(deftest interpreting-hae-asiakkaita-response
  (testing "allows-suomifi-messages? checkgs if the response message confirms that asiakas has suomi.fi messages enabled"
    (is (true?  (sm/allows-suomifi-messages? (hae-asiakkaita-resp-xml {:can-receive-msgs? true :account-disabled? false}))))
    (is (false? (sm/allows-suomifi-messages? (hae-asiakkaita-resp-xml {:can-receive-msgs? false :account-disabled? false}))))
    (is (false? (sm/allows-suomifi-messages? (hae-asiakkaita-resp-xml {:can-receive-msgs? false :account-disabled? true}))))
    (is (false? (sm/allows-suomifi-messages? (hae-asiakkaita-resp-xml {:can-receive-msgs? true :account-disabled? true}))))))

