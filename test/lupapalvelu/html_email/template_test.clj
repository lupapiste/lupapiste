(ns lupapalvelu.html-email.template-test
  (:require [lupapalvelu.html-email.template :refer :all]
            [lupapalvelu.test-util :refer [in-text]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]
            [selmer.filter-parser :refer [escape-html]]))

(testable-privates lupapalvelu.html-email.template
                   process-args get-in-ctx canonize-args)

(facts localize!
  (localize! :unsupported :open) => (throws AssertionError)
  (localize! :fi :unsupported.bad.term) => (throws AssertionError)
  (localize! :fi :application.assignment.type) => "Tyyppi"
  (localize! "fi" "application.assignment.type") => "Tyyppi"
  (localize! :fi [:application "assignment.type"]) => "Tyyppi"
  (localize! :fi [:application nil "assignment.type"])
  => (throws AssertionError))

(facts process-args
  (process-args nil) => {:args      []
                         :escape-fn escape-html}
  (process-args ["|safe"]) => {:args      []
                               :escape-fn identity}
  (process-args ["| safe"]) => {:args      ["| safe"]
                                :escape-fn escape-html}
  (process-args ["a" ":b" "c" ":d"]) => {:args      ["a" :b "c" :d]
                                         :escape-fn escape-html}
  (process-args ["a" ":b" "c" ":d" "|safe"]) => {:args      ["a" :b "c" :d]
                                                 :escape-fn identity})

(facts get-in-ctx
  (get-in-ctx {:one {:two {:three 3}}}) => nil
  (get-in-ctx {:one {:two {:three 3}}} []) => nil
  (get-in-ctx {:one {:two {:three 3}}} :one.two.three) => 3
  (get-in-ctx {:one {:two {:three 3}}} :one.two nil :three nil) => 3
  (get-in-ctx {:one {:two {:three 3}}} "one.two.three") => 3
  (get-in-ctx {:one {:two {:three 3}}} :one "two" :three) => 3
  (get-in-ctx {:one {:two {:three 3}}} [:one.two.three]) => 3
  (get-in-ctx {:one {:two {:three 3}}} :one "two" [:three]) => 3
  (get-in-ctx {:one {:two {:three 3}}} [:one "two"] [:three]) => 3
  (get-in-ctx {:one {:two {:three 3}}} [[[:one "two"]] [:three]]) => 3
  (get-in-ctx {:one {:two {:three 3}}} :one "two" :three :four) => nil
  (get-in-ctx {:one {:two {:three 3}}} :one "two") => {:three 3})


(facts loc-tag
  (facts "Absolute 1l0n path"
    (loc-tag [":application.assignment" ":type"] {:lang "sv"}) => "Typ"
    (loc-tag [":application.assignment" ":type" "|safe"] {:lang "sv"}) => "Typ"
    (loc-tag [":application.assignment" ":type" "|safe"] {})
    => (throws AssertionError)
    (loc-tag [":application.assignment" ":type" "|safe"] {:lang :unsupported})
    => (throws AssertionError)
    (loc-tag [":foo.bar"] {:lang :fi}) => "&lt;hello&gt;"
    (provided (localize! :fi [:foo.bar]) => "<hello>")
    (loc-tag [":foo.bar" "|safe"] {:lang :fi}) => "<hello>"
    (provided (localize! :fi [:foo.bar]) => "<hello>"))

  (facts "Shortcuts"
    (loc-tag ["municipality"] {:lang "fi" :municipality "186"}) => "Järvenpää"
    (loc-tag ["municipality" "|safe"] {:lang "fi" :municipality "186"})
    => "Järvenpää"
    (loc-tag ["municipality"] {:lang :fi :municipality "bad"})
    => (throws AssertionError)
    (loc-tag ["municipality"] {:lang "fi"}) => (throws AssertionError)
    (loc-tag ["municipality" "|safe"] {:lang :fi :municipality "bad"})
    => (throws AssertionError)
    (loc-tag ["municipality" "|safe"] {:lang "fi"}) => (throws AssertionError)

    (loc-tag ["operation"] {:lang "fi" :operation "aita"}) => "Aidan rakentaminen"
    (loc-tag ["operation"] {:lang "fi" :operation "bad-aita"})
    => (throws AssertionError)
    (loc-tag ["operation"] {:lang "fi"}) => (throws AssertionError)

    (loc-tag ["operation" "|safe"] {:lang "sv" :operation "aita"})
    => "Byggande av inhägnad"
    (loc-tag ["operation" "|safe"] {:lang "fi" :operation "bad-aita"})
    => (throws AssertionError)
    (loc-tag ["operation" "|safe"] {:lang "fi"}) => (throws AssertionError)

    (loc-tag ["attachment-type"] {:lang "fi" :attachment-type "muut.muu"})
    => "Muu liite"
    (loc-tag ["attachment-type"] {:lang "fi" :attachment-type "bads.bad"})
    => (throws AssertionError)
    (loc-tag ["attachment-type"] {:lang "fi"}) => (throws AssertionError)

    (loc-tag ["attachment-type" "|safe"] {:lang "fi" :attachment-type "muut.muu"})
    => "Muu liite"
    (loc-tag ["attachment-type" "|safe"] {:lang "fi" :attachment-type "bads.bad"})
    => (throws AssertionError)
    (loc-tag ["attachment-type" "|safe"] {:lang "fi"}) => (throws AssertionError))

  (facts "One context arg, not a shortcut"
    (loc-tag ["hello"] {:lang "fi" :hello "foo.bar"}) => "&lt;hello&gt;"
    (provided (localize! "fi" "foo.bar") => "<hello>")
    (loc-tag ["hello" "|safe"] {:lang "fi" :hello "foo.bar"}) => "<hello>"
    (provided (localize! "fi" "foo.bar") => "<hello>")
    (loc-tag ["hello"] {:lang "fi"}) => (throws AssertionError))

  (facts "Mixed path"
    (loc-tag [":application" "hello" ":type"]
             {:lang "fi" :hello "assignment"}) => "Tyyppi"
    (loc-tag ["hello" ":type"]
             {:lang "fi" :hello "application.assignment"}) => "Tyyppi"
    (loc-tag [":application" "MISSING" "hello"]
             {:lang "fi" :hello "assignment.type"})
    => (throws AssertionError)
    (loc-tag [":application" ":MISSING" "hello"]
             {:lang "fi" :hello "assignment.type"})
    => (throws AssertionError)

    (loc-tag [":application" "hello" ":type" "|safe"]
             {:lang "fi" :hello "assignment"}) => "Tyyppi"
    (loc-tag ["hello" ":type" "|safe"]
             {:lang "fi" :hello "application.assignment"}) => "Tyyppi"
    (loc-tag [":application" "MISSING" "hello" "|safe"]
             {:lang "fi" :hello "assignment.type"})
    => (throws AssertionError)
    (loc-tag [":application" ":MISSING" "hello" "|safe"]
             {:lang "fi" :hello "assignment.type"})
    => (throws AssertionError)

    (loc-tag [":foo" "hello"] {:lang "fi" :hello "bar"}) => "&lt;hello&gt;"
    (provided (localize! "fi" [:foo "bar"]) => "<hello>")
    (loc-tag [":foo" "hello" "|safe"] {:lang "fi" :hello "bar"}) => "<hello>"
    (provided (localize! "fi" [:foo "bar"]) => "<hello>")))

(facts app-tag
  (facts "Shortcuts"
    (app-tag [":operation"]
             {:lang        "sv"
              :application {:primaryOperation {:name "aita"}}})
    => "Byggande av inhägnad"
    (app-tag [":operation" "|safe"]
             {:lang        "sv"
              :application {:primaryOperation {:name "aita"}}})
    => "Byggande av inhägnad"
    (app-tag ["operation"] {:lang        "sv"
                            :application {:operation "<hello>"}})
    => "&lt;hello&gt;"
    (app-tag ["operation" "|safe"] {:lang        "sv"
                                    :application {:operation "<hello>"}})
    => "<hello>"
    (app-tag ["operation"] {:lang "sv"}) => ""
    (app-tag ["operation" "|safe"] {:lang "sv"}) => nil

    (app-tag [":municipality"]
             {:lang        "fi"
              :application {:municipality "186"}})
    => "Järvenpää"
    (app-tag [":municipality" "|safe"]
             {:lang        "fi"
              :application {:municipality "186"}})
    => "Järvenpää"
    (app-tag ["municipality"]
             {:lang        "fi"
              :application {:municipality "<hello>"}})
    =>  "&lt;hello&gt;"
    (app-tag ["municipality" "|safe"]
             {:lang        "fi"
              :application {:municipality "<hello>"}})
    => "<hello>"
    (app-tag ["municipality"] {:lang "sv"}) => ""
    (app-tag ["municipality" "|safe"] {:lang "sv"}) => nil

    (app-tag [":state"]
             {:lang        "fi"
              :application {:state "open"}})
    => "Näkyy viranomaiselle"
    (app-tag [":state" "|safe"]
             {:lang        "fi"
              :application {:state "open"}})
    => "Näkyy viranomaiselle"
    (app-tag ["state"]
             {:lang        "fi"
              :application {:state "<hello>"}})
    =>  "&lt;hello&gt;"
    (app-tag ["state" "|safe"]
             {:lang        "fi"
              :application {:state "<hello>"}})
    => "<hello>"
    (app-tag ["state"] {:lang "sv"}) => ""
    (app-tag ["state" "|safe"] {:lang "sv"}) => nil)

  (facts "Regular access"
    ;; Not a reasonable use case but works.
    (app-tag [":one"] {:application {:one {:two "<hello>"}}})
    =>  "{:two &quot;&lt;hello&gt;&quot;}"
    (app-tag ["one" "|safe"] {:application {:one {:two "<hello>"}}})
    => {:two "<hello>"}

    (app-tag [":one" "two"] {:application {:one {:two "<hello>"}}})
    =>  "&lt;hello&gt;"
    (app-tag ["one" ":two" "|safe"] {:application {:one {:two "<hello>"}}})
    => "<hello>"
    (app-tag ["one" ":two"] {:application {}}) => ""
    (app-tag ["one" ":two" "|safe"] {:application {}}) => nil))

(def bad-tag (throws Exception "Bad tag"))

(facts usr-tag
  (usr-tag [] {}) => bad-tag
  (usr-tag ["first"] {:user {}}) => bad-tag
  (usr-tag ["last"] {:user {}}) => bad-tag
  (usr-tag ["full"] {:user {}}) => bad-tag
  (usr-tag ["first"] {}) => bad-tag
  (usr-tag ["last"] {}) => bad-tag
  (usr-tag ["full"] {}) => bad-tag
  (usr-tag ["first"] {:user {:firstName "<First>"}}) => "&lt;First&gt;"
  (usr-tag ["first" "|safe"] {:user {:firstName "<First>"}}) => "<First>"
  (usr-tag ["full"] {:user {:firstName "First"}}) => "First"
  (usr-tag ["full"] {:user {:firstName "First" :lastName "   "}}) => "First"
  (usr-tag ["full"] {:user {}}) => bad-tag
  (usr-tag ["full"] {:user {:firstName "<First"
                            :lastName  "Last>"}}) => "&lt;First Last&gt;"
  (usr-tag ["full" "|safe"] {:user {:firstName "<First"
                                    :lastName  "Last>"}}) => "<First Last>"
  (usr-tag ["full"] {:user {:lastName "Last"}}) => "Last"
  (usr-tag ["last"] {:user {:lastName "<Last>"}}) => "&lt;Last&gt;"
  (usr-tag ["last" "|safe"] {:user {:firstName "First"
                                    :lastName  "<Last>"}}) => "<Last>"
  (usr-tag ["last" "|safe"] {:user {:firstName "First"
                                    :lastName  "  "}}) => bad-tag
  (usr-tag ["bad"] {:user {:bad "bad"}}) => bad-tag)

(facts greet-tag
  (greet-tag ["Ni" "hao"] {:user {:firstName "Randy"}}) => "Ni hao Randy,"
  (greet-tag ["<Ni" "hao>"] {:user {:firstName "R&y"}})
  => "&lt;Ni hao&gt; R&amp;y,"
  (greet-tag ["<Ni" "hao>" "|safe"] {:user {:firstName "R&y"}})
  => "&lt;Ni hao&gt; |safe R&amp;y," ; LOL
  (greet-tag ["Ni" "hao"] {:user {}}) => "Ni hao."
  (greet-tag ["Zao"] {}) => "Zao.")

(defn link [expected]
  #(= % (str (env/value :host) "/app/" expected)))

(facts app-link-tag
  (app-link-tag [] {}) => bad-tag
  (app-link-tag [] {:application {:id "LP-123"}})
  => (link "fi/applicant#!/application/LP-123")
  (app-link-tag [] {:application {:id          "LP-123"
                                  :infoRequest true}
                    :lang        "sv"})
  => (link "sv/applicant#!/inforequest/LP-123")
  (app-link-tag ["foo"] {:application {:id "LP-123"}
                         :user        {:role "authority"}
                         :lang        "en"})
  => (link "en/authority#!/application/LP-123")
  (app-link-tag ["foo"] {:application {:id "LP-123"}
                         :user        {:role "authority"}
                         :lang        "en"
                         :foo         :bar})
  => (link "en/authority#!/application/LP-123/bar")
  (app-link-tag [":foo"] {:application {:id "LP-123"}
                          :user        {:role "admin"}
                          :lang        "en"})
  => (link "en/admin#!/application/LP-123/foo"))

(fact subject-block
  (subject-block nil nil {:subject {:content "First\n  second  \n\n third\n \n"}})
  => (str SUBJECT "First second third"))

(facts canonize-args
  (canonize-args [":hoo" "hii" "::foo"]) => ["hoo" "hii" ":foo"])

(facts style-attr-tag
  (style-attr-tag [] {}) => nil
  (style-attr-tag [":foo"] {}) => nil
  (style-attr-tag [":foo"] {:styles {:foo {:one "1px"
                                           :two "2em"}}})
  => "style=\"one:1px;two:2em\""
  (style-attr-tag [":foo" "bar"] {:styles {:foo   {:one "1px"
                                                   :two "2em"}
                                           :three {:four "4px 4px"
                                                   :one  "10rem"
                                                   :bar  {:five "fiver"}}}})
  =>  "style=\"one:10rem;two:2em;four:4px 4px;five:fiver\"")

(facts button-block
  (button-block [] {} {}) => (throws AssertionError)
  (button-block [] {} {:button {:content "https://www.lupapiste.fi"}})
  => (throws AssertionError)
  (button-block [] {} {:button {:content "  https://www.lupapiste.fi  "}})
  => (throws AssertionError)
  (in-text (button-block [] {}
                         {:button {:content " https://www.lupapiste.fi \n Lupapiste  "}})
           "href=\"https://www.lupapiste.fi\"" ">Lupapiste<"))

(against-background
  [(env/feature? :template-placeholder) => true]
  (fact "Placeholder support"
    (in-text (render-template :non-existing {} "en")
             "Placeholder" "non-existing.en.djhtml")))

(against-background
  [(env/feature? :template-placeholder) => false]
  (fact "No placeholder support"
    (render-template :non-existing {} "en")
    => (throws Exception "Template file non-existing.en.djhtml not found")))

(when-not (env/feature? :template-placeholder)
  (fact "No missing templates"
    (missing-templates) => []))
