(ns sade.email-test
  (:require [sade.email :refer :all]
            [midje.sweet :refer :all]
            [postal.core :as postal]))

(facts "Facts about sending emails"
  ; Need some body:
  (send-mail ...from... ...to... ...subject... nil nil) => (throws AssertionError)
  
  ; Send plain text only:
  (send-mail ...from... ...to... ...subject... "plain text" nil) => nil
    (provided (postal/send-message irrelevant {:from ...from...
                                               :to ...to...
                                               :subject ...subject...
                                               :body [{:type "text/plain; charset=utf-8"
                                                       :content "plain text"}]}) => nil)
  
  ; Send html only:
  (send-mail ...from... ...to... ...subject... nil "html text") => nil
    (provided (postal/send-message irrelevant {:from ...from...
                                               :to ...to...
                                               :subject ...subject...
                                               :body [{:type "text/html; charset=utf-8"
                                                       :content "html text"}]}) => nil)
  
  ; Send both plain and html body, content is Multi-Part/alternative, and plain text is first:
  (send-mail ...from... ...to... ...subject... "plain text" "html text") => nil
    (provided (postal/send-message irrelevant {:from ...from...
                                               :to ...to...
                                               :subject ...subject...
                                               :body [:alternative
                                                      {:type "text/plain; charset=utf-8"
                                                       :content "plain text"}
                                                      {:type "text/html; charset=utf-8"
                                                       :content "html text"}]}) => nil)
    
  ; Return error when postal returns an error:
  (send-mail ...from... ...to... ...subject... "plain text" "html text") => {:error "oh noes"}
    (provided (postal/send-message irrelevant {:from ...from...
                                               :to ...to...
                                               :subject ...subject...
                                               :body [:alternative
                                                      {:type "text/plain; charset=utf-8"
                                                       :content "plain text"}
                                                      {:type "text/html; charset=utf-8"
                                                       :content "html text"}]}) => {:error "oh noes"}))

