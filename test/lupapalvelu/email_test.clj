(ns lupapalvelu.email-test
  (:require [lupapalvelu.email :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.states :as states]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [net.cgrand.enlive-html :as enlive]))

(testable-privates lupapalvelu.email preprocess-context)

(facts "apply-template"
  (facts "invalid template"
    (apply-template "does-not-exist.md" {:receiver "foobar"}) => (throws #"error.empty-email"))



  (against-background [(fetch-template "master.md")           => "{{>header}}\n\n{{>body}}\n\n{{>footer}}"
                       (fetch-template "en-footer.md")        => "## {{footer}}"
                       (fetch-template "en-header.md")        => "## {{header}}"
                       (fetch-template "en-test.md")          => "This is *test* message for {{applicationRole.hakija}} {{receiver}} [link text](http://link.url \"alt text\")"
                       (fetch-html-template "html-wrap.html") => (enlive/html-resource (io/input-stream (.getBytes "<html><body></body></html>")))
                       (fetch-template "en-email-test.md")    => "foo pena@example.com bar"]
    (facts "header and footer"
      (let [[plain html] (apply-template "test.md" {:footer "FOOTER" :header "HEADER"
                                                    :receiver "foobar" :lang "en"})]
        plain => "\nHEADER\n\nThis is test message for applicant foobar link text: http://link.url \n\nFOOTER\n"
        html => "<html><body><h2>HEADER</h2>\n<p>This is <em>test</em> message for applicant foobar <a href=\"http://link.url\" title=\"alt text\">link text</a></p>\n<h2>FOOTER</h2>\n</body></html>"))

    (facts "email address must not be rendered as a link"
      (-> (apply-template "email-test.md" {:lang "en"})
          second)
      => "<html><body><h2></h2>\n<p>foo pena@example.com bar</p>\n<h2></h2>\n</body></html>")))

(facts "User language markdown templates: test body"
  (fetch-template "testbody.md") => (contains "suomi")
  (fetch-template "sv-testbody.md") => (contains "Svenska")
  (fetch-template "fi-testbody.md") => (contains "suomi")
  (fetch-template "cn-testbody.md") => (throws IllegalArgumentException))

(facts "User language markdown templates: footer"
  (fetch-template "footer.md") => (throws IllegalArgumentException)
  (fetch-template "fi-footer.md") => (contains "automaattinen")
  (fetch-template "sv-footer.md") => (contains "automatiskt")
  (fetch-template "cn-footer.md") => (throws IllegalArgumentException))

(facts "fetch by language"
  (fetch-template-by-lang "footer.md" "fi") => (contains "automaattinen")
  (fetch-template-by-lang "footer.md" nil) => (throws IllegalArgumentException)
  (fetch-template-by-lang "footer.md" "cn") => (throws IllegalArgumentException))

(facts "User language html templates: test body as enlive"
  (fetch-html-template "testbody.html") => seq?
  (fetch-html-template "testbody.html" "sv") => seq?
  (fetch-html-template "testbody.html" "fi") => (throws IllegalArgumentException)
  (fetch-html-template "testbody.html" "cn") => (throws IllegalArgumentException))

(defn mail-check [s & [html?]]
  (fn [[txt html]]
    (fact "text" txt => (contains s :gaps-ok))
    (fact "html" html => (contains (str (if html? "<em>" "<p>") s) :gaps-ok))))

(facts "Apply markdown template"
  (let [ctx {:hej "Morgon" :moi "Mortonki" :hi "Hi y'all"}]
    (fact "If language is not provided, the templates in supported languages are catenated"
      (apply-template "testbody.md" ctx) => (every-checker (mail-check "Svenska Morgon")
                                                           (mail-check "suomi Mortonki")))
    (apply-template "testbody.md" (assoc ctx :lang "fi")) => (mail-check "suomi Mortonki")
    (apply-template "testbody.md" (assoc ctx :lang "cn"))
    => (throws #"error.empty-email")
    (apply-template "testbody.md" (assoc ctx :lang "sv"))
    => (mail-check "Svenska Morgon")))

(facts "Apply html template"
       (let [ctx {:hej "Morgon" :moi "Mortonki"}]
         (apply-template "testbody.html" ctx) => (mail-check "suomi Mortonki" true)
         (apply-template "testbody.html" (assoc ctx :lang "cn"))
         => (throws IllegalArgumentException)
         (apply-template "testbody.html" (assoc ctx :lang "sv"))
         => (mail-check "Svenska Morgon" true)))

(facts "localization-keys-from-template"

  (fact "finds both escaped and unescaped variables"
    (localization-keys-from-template "{{this}} {{& and.this }} {{{and.also.this}}}")
    => [["this"] ["and" "this"] ["and" "also" "this"]])

  (fact "ignores comments"
    (localization-keys-from-template "{{!not.mistaken.for.variable}}") => [])

  (fact "ignores everything within a section"
    (localization-keys-from-template "{{#section}}\n{{this.is.ignored}} {{/section}} {{this.is.not}}")
    => [["this" "is" "not"]]
    (localization-keys-from-template "{{^inverted-section}} {{ignored}}\n{{/inverted-section}}") => []))

(facts "preprocess-context"

  (fact "replaces function values in the context by calling them with the language (:lang) as argument"
    (preprocess-context "Just a dummy template"
                        {:lang "fi" :nationality #(get {"fi" "Finnish"} % "unknown")})
    => {:lang "fi" :nationality "Finnish"})

   (fact "adds localizations from i18n files for keys present in the template but missing from the context"
     (preprocess-context "{{applicationRole.authority}}" {:lang "fi"})
     => {:lang "fi" :applicationRole {:authority "viranomainen"}})

   (fact "gives context localizations precedence over those in i18n files"
     (preprocess-context "{{applicationRole.authority}}  {{applicationRole.foreman}}"
                         {:lang "en" :applicationRole {:authority "definitely not 'authority'"}})
     => {:lang "en" :applicationRole {:authority "definitely not 'authority'" :foreman "supervisor"}})

   (fact "throws when it encounters a missing localization key in the template"
     (preprocess-context "{{nonexistent.localization.key}}" {:lang "sv"}) => (throws #"No localization")))

(def ajanvaraus-templates #{"en-accept-appointment.md" "en-decline-appointment.md"
                            "en-suggest-appointment-authority.md" "en-suggest-appointment-from-applicant.md"
                            "en-suggest-appointment-to-applicant.md"
                            "sv-accept-appointment.md" "sv-decline-appointment.md"
                            "sv-suggest-appointment-authority.md" "sv-suggest-appointment-from-applicant.md"
                            "sv-suggest-appointment-to-applicant.md"})

(facts "templates exist check"
  (let [this-path  (util/this-jar lupapalvelu.main)
        templates  (if (ss/ends-with this-path ".jar")      ; are we inside jar
                     (filter #(ss/ends-with % ".md") (util/list-jar this-path "email-templates/"))
                     (util/get-files-by-regex "resources/email-templates/" #".+\.md$")) ; dev
        template-names (->> templates
                            (map #(if (string? %) % (.getName %)))
                            (filter #(ss/starts-with % (str (name i18n/default-lang) "-")))) ; when writing default is "fi"
        excluded-templates (set/union ajanvaraus-templates
                                      #{"en-onnistuu-success.md" ; Onnistuu internal mail
                                        "sv-onnistuu-success.md"
                                        "en-onnistuu-success-campaign.md"
                                        "sv-onnistuu-success-campaign.md"})]
    (doseq [lang (disj (set i18n/supported-langs) :fi)
            template-name template-names
            :let [template-suffix (last (re-find  #"\w+\-(.+)" template-name))
                  filename        (str (name lang) "-" template-suffix)]
            :when (not (excluded-templates filename))]
      (fact {:midje/description (str filename " exists")}
            (find-resource filename) =not=> (throws IllegalArgumentException)))
    ; These tests ensure that excluded-templates is updated when new templates are added
    (doseq [filename excluded-templates]
      (fact {:midje/description (str filename " does NOT exist")}
        (find-resource filename) => (throws IllegalArgumentException)))))

(facts "All states have state-description text for emails"
  (doseq [lang i18n/supported-langs
          state states/all-states]
    (fact {:midje/description (str lang " - " (name state))}
      (i18n/has-term? lang (str "email.state-description." (name state))) => true)))
