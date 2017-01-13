(ns lupapalvelu.email-test
  (:require [lupapalvelu.email :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.java.io :as io]))

(testable-privates lupapalvelu.email preprocess-context)

(facts "apply-template"
  (facts "invalid template"
   (apply-template "does-not-exist.md" {:receiver "foobar"}) => (throws IllegalArgumentException))

  (against-background [(fetch-template "master.md" nil)      => "{{>header}}\n\n{{>body}}\n\n{{>footer}}"
                       (fetch-template "header.md" nil)      => "# {{header}}"
                       (fetch-template "footer.md" nil)      => "## {{footer}}"
                       (fetch-template "test.md"   nil)      => "This is *test* message for {{receiver}} [link text](http://link.url \"alt text\")"
                       (find-resource "html-wrap.html" nil)  => (io/input-stream (.getBytes "<html><body></body></html>"))]
    (facts "header and footer"
      (let [[plain html] (apply-template "test.md" {:header "HEADER" :footer "FOOTER" :receiver "foobar"})]
        plain => "\nHEADER\n\nThis is test message for foobar link text: http://link.url \n\nFOOTER\n"
        html => "<html><body><h1>HEADER</h1><p>This is <em>test</em> message for foobar <a href=\"http://link.url\" title=\"alt text\">link text</a></p><h2>FOOTER</h2></body></html>"))))

(facts "User language markdown templates: test body"
       (fetch-template "testbody.md") => (contains "suomi")
       (fetch-template "testbody.md" "sv") => (contains "Svenska")
       (fetch-template "testbody.md" "fi") => (contains "suomi")
       (fetch-template "testbody.md" "cn") => (contains "suomi"))

(facts "User language markdown templates: footer"
       (fetch-template "footer.md") => (contains "automaattinen")
       (fetch-template "footer.md" "sv") => (contains "automatiskt")
       (fetch-template "footer.md" "fi") => (contains "automaattinen")
       (fetch-template "footer.md" "cn") => (contains "automaattinen"))

(facts "User language html templates: test body"
       (fetch-template "testbody.html") => (contains "<em>suomi")
       (fetch-template "testbody.html" "sv") => (contains "<em>Svenska")
       (fetch-template "testbody.html" "fi") => (contains "<em>suomi")
       (fetch-template "testbody.html" "cn") => (contains "<em>suomi"))

(defn mail-check [s & [html?]]
  (fn [[txt html]]
    (fact "text" txt => (contains (str "\n" (when html? "    ") s) :gaps-ok))
    (fact "html" html => (contains (str (if html? "<em>" "<p>") s) :gaps-ok))))

(facts "Apply markdown template"
       (let [ctx {:hej "Morgon" :moi "Mortonki"}]
         (apply-template "testbody.md" ctx) => (mail-check "suomi Mortonki")
         (apply-template "testbody.md" (assoc ctx :lang "cn"))
         => (mail-check "suomi Mortonki")
         (apply-template "testbody.md" (assoc ctx :lang "sv"))
         => (mail-check "Svenska Morgon")))

(facts "Apply html template"
       (let [ctx {:hej "Morgon" :moi "Mortonki"}]
         (apply-template "testbody.html" ctx) => (mail-check "suomi Mortonki" true)
         (apply-template "testbody.html" (assoc ctx :lang "cn"))
         => (mail-check "suomi Mortonki" true)
         (apply-template "testbody.html" (assoc ctx :lang "sv"))
         => (mail-check "Svenska Morgon" true)))

(facts "preprocess-context"
       (let [ctx {:phrases {:hello (fn [lang] (get {:fi "Hei" :sv "Hej"} (keyword lang) "Greetings humans"))
                            :ok "OK"}
                  :key "value"}]
         (preprocess-context (assoc ctx :lang "fi")) => {:lang "fi" :phrases {:hello "Hei" :ok "OK"} :key "value"}
         (preprocess-context (assoc ctx :lang "en")) => {:lang "en" :phrases {:hello "Greetings humans" :ok "OK"} :key "value"}))
