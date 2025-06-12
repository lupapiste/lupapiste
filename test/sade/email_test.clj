(ns sade.email-test
  (:require [midje.sweet :refer :all]
            [sade.email :refer :all]
            [sade.http :as http]))

(def actually-send #'sade.email/actually-send)
(def mailgun-payload #'sade.email/mailgun-payload)

(def mock-config {:mailgun-base-url "mock-url"
                  :mailgun-api-key  "mock-api-key"})

(facts "actually-send"
  (fact "no attachments; no multipart"
    (actually-send mock-config {:body {:html "html text"}})
    => {:status 200 :body {}}
    (provided (http/post "mock-url/messages" {:basic-auth  ["api" "mock-api-key"]
                                              :form-params {:html "html text"}})
              => {:status 200 :body {}}))

  (fact "multipart for attachments"
    (actually-send mock-config {:body {:html "html text" :attachments [{:filename "testi.txt"}]}})
    => {:status 200 :body {}}
    (provided (http/post "mock-url/messages" {:basic-auth ["api" "mock-api-key"]
                                              :multipart  [{:name "testi.txt" :content nil :part-name "attachment"}
                                                           {:name "html" :content "html text" :encoding "UTF-8"}]})
              => {:status 200 :body {}})))

(facts "get-mailgun-payload"
  (fact "no attachments"
    (mailgun-payload {:to   "Test 1 <test1@example.com>"
                      :from "Test 2 <test2@example.com>"
                      :body {:html  "html text"
                             :plain "plain text"}})
    => {:to   "Test 1 <test1@example.com>"
        :from "Test 2 <test2@example.com>"
        :html "html text"
        :text "plain text"})

  (fact "attachments"
    (mailgun-payload {:body {:attachments [{:filename "testitiedosto.txt"
                                            :content  "testisisältö-1"}
                                           {:filename "testitiedosto.pdf"
                                            :content  "testisisältö-2"}]}})
    => {:attachments [{:type      :attachment
                       :file-name "testitiedosto.txt"
                       :content   "testisisältö-1"}
                      {:type      :attachment
                       :file-name "testitiedosto.pdf"
                       :content   "testisisältö-2"}]}))

(facts "send-mail"
  ; Need some body:
  (send-mail ...to... ...subject...) => (throws AssertionError)

  ; Send plain text only:
  (send-mail ...to... ...subject... :plain "plain text") => nil
    (provided (deliver-email ...to... ...subject... {:plain "plain text"}) => nil)

  ; Send html only:
  (send-mail ...to... ...subject... :html "html text") => nil
    (provided (deliver-email ...to... ...subject... {:html "html text"}) => nil)

  ; Send both plain and html body:
  (send-mail ...to... ...subject... :plain "plain text" :html "html text") => nil
    (provided (deliver-email ...to... ...subject... {:plain "plain text" :html "html text"}) => nil)

  ; Return error when delivery returns an error:
  (send-mail ...to... ...subject... :plain "plain text") => {:error "oh noes"}
    (provided (deliver-email ...to... ...subject... irrelevant) => {:error "oh noes"}))

(facts "blacklisted?"
  (fact "sample or test addresses"
    (binding [sade.email/*blacklist* (re-pattern ".*@([0-9]{3}.fi|example\\.com|testi\\.fi)$")]
     (blacklisted? "mikko@example.com") => true
     (blacklisted? "mikko@testi.fi") => true
     (blacklisted? "mikko@000.fi") => true
     (blacklisted? "example.com@example.net") => false
     (blacklisted? "mikko@testaus.fi") => false
     (blacklisted? "mikko@0000.fi") => false))

  (fact "nil"
    (binding [sade.email/*blacklist* nil]
     (blacklisted? "mikko@example.com") => false
     (blacklisted? "mikko@testi.fi") => false
     (blacklisted? "mikko@000.fi") => false
     (blacklisted? "example.com@example.net") => false
     (blacklisted? "mikko@testaus.fi") => false
     (blacklisted? "mikko@0000.fi") => false))

  (fact "empty pattern"
    (binding [sade.email/*blacklist* (re-pattern "")]
     (blacklisted? "mikko@example.com") => false
     (blacklisted? "mikko@testi.fi") => false
     (blacklisted? "mikko@000.fi") => false
     (blacklisted? "example.com@example.net") => false
     (blacklisted? "mikko@testaus.fi") => false
     (blacklisted? "mikko@0000.fi") => false)))
