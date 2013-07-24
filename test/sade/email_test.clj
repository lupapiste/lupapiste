(ns sade.email-test
  (:require [sade.email :refer :all]
            [midje.sweet :refer :all]
            [postal.core :as postal]
            [clojure.java.io :as io]))

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


(facts "Facts about apply-template"
  (apply-template "does-not-exists.md" {:receiver "foobar"}) => (throws IllegalArgumentException))

(against-background [(fetch-template "master.md")      => "{{>header}}\n\n{{>body}}\n\n{{>footer}}"
                     (fetch-template "header.md")      => "# {{header}}"
                     (fetch-template "footer.md")      => "## {{footer}}"
                     (fetch-template "test.md")        => "This is *test* message for {{receiver}} [link text](http://link.url \"alt text\")"
                     (find-resource "html-wrap.html")  => (io/input-stream (.getBytes "<html><body></body></html>"))]
  (facts "More facts about apply-template"
    (let [[plain html] (apply-template "test.md" {:header "HEADER" :footer "FOOTER" :receiver "foobar"})]
      plain => "\nHEADER\n\nThis is test message for foobar link text: http://link.url\n\nFOOTER\n"
      html => "<html><body><h1>HEADER</h1><p>This is <em>test</em> message for foobar <a href=\"http://link.url\" title=\"alt text\">link text</a></p><h2>FOOTER</h2></body></html>")))
