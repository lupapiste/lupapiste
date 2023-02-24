(ns lupapalvelu.printing-order.mylly-client-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.printing-order.mylly-client :as mylly]
            [lupapalvelu.mongo :as mongo]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream)))

(def login-result-xml
"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">
<s:Body>
  <LoginResponse xmlns=\"http://kopijyva.fi/OrderingSystem.AuthenticationService\">
    <LoginResult>1234567890123456789012345678901234</LoginResult>
  </LoginResponse>
</s:Body>
</s:Envelope>")

(def order-result-xml
"<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">
  <s:Body>
    <AddOrderResponse xmlns=\"http://kopijyva.fi/OrderingSystem.OrderService\">
      <AddOrderResult>
        <Order>
          <CAD>
            <SonetOrderNumber>1234567890</SonetOrderNumber>
          </CAD>
        </Order>
      </AddOrderResult>
    </AddOrderResponse>
  </s:Body>
  </s:Envelope>")


(defn encoded-file [file-name]
  (with-open [orig (io/input-stream file-name)
              output (ByteArrayOutputStream.)]
    (base64/encoding-transfer orig output)
    (.toString output)))

(def mock-order
  {:projectName     "Lupapisteen hankkeen LP-999-2017-88888 liitteet"
   :orderer         {:companyName "Testiyritys"
                     :email "pena@lupapiste.fi"}
   :payer           {:companyName "Testiyritys"
                     :email "pena@lupapiste.fi"}
   :delivery        {:companyName      "Testiyritys"
                     :email "pena@lupapiste.fi"
                     :printedMaterials [{:fileId     "5950e6d4fc2a8807af72a777"
                                         :copyAmount 2}]}
   :internalOrderId (mongo/create-id)
   :files           [{:fileId  "5950e6d4fc2a8807af72a777"
                      :name    "test-pdf.pdf"
                      :size    13963
                      :content (encoded-file
                                 "dev-resources/test-pdf.pdf")}]})

(facts "mylly authentication service calls"
  (fact "fetch login token with ok request"
    (against-background
      (mylly/post mylly/authentication-service-url mylly/authentication-service-ns :Login anything)
        => {:status 200
            :body login-result-xml})
    (against-background
      (mylly/post mylly/order-service-url mylly/order-service-ns :AddOrder anything)
        => {:status 200
            :body order-result-xml})
    (mylly/fetch-login-token!) => "1234567890123456789012345678901234"
    (mylly/login-and-send-order! mock-order) => {:ok true :orderNumber "1234567890"}
    (against-background (mylly/in-dummy-mode?) => false)))

(facts "mylly backend returns a SOAP error"
  (fact "Faultcode in order message"
   (against-background
     (mylly/post mylly/authentication-service-url mylly/authentication-service-ns :Login anything)
     => {:status 200
         :body login-result-xml})
   (against-background
     (mylly/post mylly/order-service-url mylly/order-service-ns :AddOrder anything)
     => {:status 500
         :body ""})
   (mylly/login-and-send-order! mock-order) => {:ok false}
   (against-background (mylly/in-dummy-mode?) => false)))

(fact "oversized order is not sent over the network"
      (against-background
        (mylly/post mylly/order-service-url mylly/order-service-ns :AddOrder anything)
        => {:status 500
            :body ""})
      (mylly/login-and-send-order! (assoc-in mock-order [:files 0 :size] 2000000000)) => {:ok false :reason :error.order-too-large}
      (against-background (mylly/in-dummy-mode?) => false))
