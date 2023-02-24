(ns lupapalvelu.company-contract-test
  (:require [lupapalvelu.company-contract :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.fixture.minimal :refer [companies]]
            [schema.core :as sc]))

(testable-privates lupapalvelu.company-contract
                   parse-title parse-single
                   fill-placeholders make-tag
                   sections-for-tags text->tags
                   contract-tags ->context
                   html link)

(def BAD truthy)

(facts "Lang schema"
  (sc/check Lang "fi") => nil
  (sc/check Lang :sv) => nil
  (sc/check Lang :cn) => BAD
  (sc/check Lang nil) => BAD)

(facts "Contact schema"
  (sc/check Contact {}) => BAD
  (sc/check Contact nil) => BAD
  (sc/check Contact {:firstName ""}) => BAD
  (sc/check Contact {:lastName "  "}) => BAD
  (sc/check Contact {:firstName "  " :lastName ""}) => BAD
  (sc/check Contact {:firstName "  " :lastName " " :foo :bar}) => BAD
  (sc/check Contact {:firstName 2 :lastName "One"}) => BAD
  (sc/check Contact {:firstName "Two" :lastName "Three"}) => nil
  (sc/check Contact {:firstName "Two" :lastName "Three"}) => nil
  (sc/check Contact {:firstName "Two" :lastName "Three" :foo :bar}) => nil
  (sc/check Contact {:firstName "  Four  "}) => nil
  (sc/check Contact {:lastName "  Five "}) => nil)

(def account {:type        "account5"
              :price       10
              :billingType "monthly"})

(facts "Account schema"
  (sc/check Account account) => nil
  (sc/check Account (assoc account
                           :type :account15
                           :billingType :yearly)) => nil
  (sc/check Account (assoc account :type :account20)) => BAD
  (sc/check Account (assoc account :billingType "weekly")) => BAD
  (sc/check Account (assoc account :billingType nil)) => BAD
  (sc/check Account (assoc account :price -10))=> BAD
  (sc/check Account (assoc account :price 0))=> BAD)

(facts "parse-title"
  (parse-title nil) => (throws Exception)
  (parse-title "no title") => nil
  (parse-title "") => nil
  (parse-title "  # Preceding whitespace") => nil
  (parse-title "# Good one") => [:h1 "Good one"]
  (parse-title "##      Good two   ") => [:h2 "Good two"]
  (parse-title "###   Good   three") => [:h3 "Good   three"]
  (parse-title "#### H4 not supported") => nil
  (parse-title "# Multiple\nlines not supported \n") => nil
  (parse-title "##no whitespace") => nil
  (parse-title "#") => nil
  (parse-title "##") => nil
  (parse-title "###") => nil
  (parse-title "# ") => [:h1 ""])

(facts "parse-single"
  (parse-single nil) => (throws Exception)
  (parse-single "no single") => nil
  (parse-single "") => nil
  (parse-single "  #-- Preceding whitespace") => nil
  (parse-single "#-- Good one") => [:p.single "Good one"]
  (parse-single "#----      Good indent   ") => [:p.single.indent "Good indent"]
  (parse-single "#--- Bad single") => nil
  (parse-single "#----- Bad single") => nil
  (parse-single "#-- Multiple\nlines not supported \n") => nil
  (parse-single "#----no whitespace") => nil
  (parse-single "#--") => [:p.single ""]
  (parse-single "#----") => [:p.single.indent ""]
  (parse-single "#-- ") => [:p.single ""]
  (parse-single "#----   ") => [:p.single.indent ""])

(facts "fill-placeholders"
  (fill-placeholders {} nil) => (throws Exception)
  (fill-placeholders {} "") => []
  (fill-placeholders {} " ") => [" "]
  (fill-placeholders {} "No placeholders") => ["No placeholders"]
  (fill-placeholders {:foo "bar"} "Foo is ${foo}, right?") => ["Foo is " "bar" ", right?"]
  (fill-placeholders {:foo "bar"} "Foo-foo is ${foo}-${foo}!")
  => ["Foo-foo is " "bar" "-" "bar" "!"]
  (fill-placeholders {:foo   "bar"
                      :level {:down "Hello"
                              :up   [{:world true}]}}
                     "First ${foo} then level down ${level.down} and ${level.up} again.")
  => ["First " "bar" " then level down " "Hello" " and " [{:world true}] " again."]
  (fill-placeholders {:one {:two 2}} "Not found ${one.three}") => (throws AssertionError)
  (fill-placeholders {:one {:two 2}} "Not found ${one.two.three}") => (throws AssertionError)
  (fill-placeholders {:one {:two 2}} "Not found ${two}") => (throws AssertionError)
  (fill-placeholders {:one {:two [0 1 2 :three]}} "Only maps supported ${one.two.3}")
  => (throws AssertionError))

(facts "make-tag"
  (make-tag {} :p "") => [:p]
  (make-tag {} :p " ") => [:p " "]
  (make-tag {:one 1} :a "one by ${one}") => [:a "one by " 1]
  (make-tag {} :foo "Bad tag") => (throws Exception))

(facts "sections-for-tags"
  (sections-for-tags []) => []
  (sections-for-tags [[:p "No sections"]]) => [[:p "No sections"]]
  (sections-for-tags [[:h1 "Title"]
                      [:p "Before section"]
                      [:h2 "First section"]
                      [:p "One"] [:p.single "Two"]
                      [:h2 "Second section"]
                      [:h2 "Third section"]
                      [:h3 "Three"]])
  => [[:h1 "Title"]
      [:p "Before section"]
      [:div.section [:h2 "1. " "First section"]
       [:p "One"] [:p.single "Two"]]
      [:div.section [:h2 "2. " "Second section"]]
      [:div.section [:h2 "3. " "Third section"]
       [:h3 "Three"]]])

(facts "text->tags"
  (text->tags {} "") => []
  (text->tags {} "%% ${foo} <- ignored") => []
  (text->tags {} "hello") => [[:p "hello"]]
  (text->tags {:w "world"} "hello ${w}!") => [[:p "hello " "world" "!"]]
  (text->tags {:one "One" :two "Two" :three "Three"}
              "
%% Comment
# Main title ${one} ${two} ${three}

## Section ${one} starts
Some paragraph ${two} text.
%% Another comment
#-- Single line
## Section ${two}

All right.

## Third section

### Level ${three} title
Final word.
%% Last comment")
  => [[:h1 "Main title " "One" " " "Two" " " "Three"]
      [:div.section
       [:h2 "1. " "Section " "One" " starts"]
       [:p "Some paragraph " "Two" " text."]
       [:p.single "Single line"]]
      [:div.section
       [:h2 "2. " "Section " "Two"]
       [:p "All right."]]
      [:div.section
       [:h2 "3. " "Third section"]
       [:h3 "Level " "Three" " title"]
       [:p "Final word."]]])

(def company (assoc (first companies)
                    :name "  Uniquelowbar  "
                    :po "   Company Town  "
                    :address1 "  Bar Street 12  "))

(facts "link"
  (link :fi :tos) => [:a {:href "https://www.lupapiste.fi/page/terms/fi"}
                      "https://www.lupapiste.fi/page/terms/fi"]
  (link :fi :tos "Click me!") => [:a {:href "https://www.lupapiste.fi/page/terms/fi"}
                                  "Click me!"]
  (link {:lang "fi" :url-key :tos :text nil :tag :a})
  => [:a {:href "https://www.lupapiste.fi/page/terms/fi"}
      "https://www.lupapiste.fi/page/terms/fi"]
  (link {:lang "fi" :url-key :tos :text "Termos" :tag :a.gap})
  => [:a.gap {:href "https://www.lupapiste.fi/page/terms/fi"} "Termos"])


(facts "->context"
  (fact "Finnish monthly"
   (->context "fi"
              company
              {:firstName "  Firdinand "
               :lastName  " Lastenson "}
              account)
   => (just {:account        {:months 1
                              :price  10
                              :type   "Yritystili 5"}
             :company        (contains {:accountType "account5"
                                        :address1    "Bar Street 12"
                                        :billingType "monthly"
                                        :name        "Uniquelowbar"
                                        :po          "Company Town"
                                        :y           "1060155-5"
                                        :zip         "33100"})
             :contact        {:firstName "Firdinand"
                              :lastName  "Lastenson"}
             :lang           "fi"
             :lupapiste-link [:a.gap {:href "https://www.lupapiste.fi"} "www.lupapiste.fi"]
             :pro-link       [:a
                              {:href "https://cloudpermit.com/fi-fi/palvelut-yrityksille"}
                              "https://cloudpermit.com/fi-fi/palvelut-yrityksille"]
             :prices-link    [:a
                              {:href "https://www.lupapiste.fi/app/fi/welcome#!/register-company-account-type"}
                              "https://www.lupapiste.fi/app/fi/welcome#!/register-company-account-type"]
             :registry-link  [:a
                              {:href "https://www.lupapiste.fi/page/registry/fi"}
                              "https://www.lupapiste.fi/page/registry/fi"]
             :tos-link       [:a
                              {:href "https://www.lupapiste.fi/page/terms/fi"}
                              "https://www.lupapiste.fi/page/terms/fi"]}))
  (fact "Swedish yearly"
   (->context "sv"
              company
              {:firstName "  Firdinand "
               :lastName  " Lastenson "}
              {:type        "account30"
               :price       313
               :billingType "yearly"})
   => (just {:account        {:months 12
                              :price  313
                              :type   "Företagskonto 30"}
             :company        (contains {:accountType "account5"
                                        :address1    "Bar Street 12"
                                        :billingType "monthly"
                                        :name        "Uniquelowbar"
                                        :po          "Company Town"
                                        :y           "1060155-5"
                                        :zip         "33100"})
             :contact        {:firstName "Firdinand"
                              :lastName  "Lastenson"}
             :lang           "sv"
             :lupapiste-link [:a.gap {:href "https://www.lupapiste.fi"} "www.lupapiste.fi"]
             :pro-link       [:a
                              {:href "https://cloudpermit.com/sv-fi/tjanster-for-foretag"}
                              "https://cloudpermit.com/sv-fi/tjanster-for-foretag"]
             :prices-link    [:a
                              {:href "https://www.lupapiste.fi/app/sv/welcome#!/register-company-account-type"}
                              "https://www.lupapiste.fi/app/sv/welcome#!/register-company-account-type"]
             :registry-link  [:a
                              {:href "https://www.lupapiste.fi/page/registry/sv"}
                              "https://www.lupapiste.fi/page/registry/sv"]
             :tos-link       [:a
                              {:href "https://www.lupapiste.fi/page/terms/fi"}
                              "https://www.lupapiste.fi/page/terms/fi"]})))

(facts "contract-tags in Finnish"
  (let [html (->> (->context "fi"
                             company
                             {:firstName "  Firdinand "
                              :lastName  " Lastenson "}
                             account)
                  contract-tags
                  html)]
    (doseq [target ["laskutuskausi on 1 kk" "10 €" "Yritystili 5"
                    "Bar Street 12" "Uniquelowbar" "Company Town"
                    "1060155-5" "33100" "Firdinand" "Lastenson"
                    "https://www.lupapiste.fi/page/terms/fi"]]
      (fact {:midje/description target}
        (re-find (re-pattern target) html) => truthy))))

(facts "contract-tags in Swedish"
  (let [html (->> (->context "sv"
                             company
                             {:firstName "  Firdinand "
                              :lastName  " Lastenson "}
                             account)
                  contract-tags
                  html)]
    (doseq [target ["faktureringsperiod är 1 månader" "10 €" "Företagskonto 5"
                    "Bar Street 12" "Uniquelowbar" "Company Town"
                    "1060155-5" "33100" "Firdinand" "Lastenson"
                    "https://www.lupapiste.fi/page/terms/fi"]]
      (fact {:midje/description target}
        (re-find (re-pattern target) html) => truthy))))
