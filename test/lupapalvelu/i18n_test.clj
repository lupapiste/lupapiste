(ns lupapalvelu.i18n-test
  (:require [lupapalvelu.i18n :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.mime :as mime]
            [sade.strings :as ss]
            [sade.env :as env]))

(facts
  (fact "in dev-mode messy placeholder is returned"
    (unknown-term "kikka") => "???kikka???"
    (unknown-term :kikka "kakkonen") => "???kikka.kakkonen???"
    (provided
      (env/dev-mode?) => true))

  (fact "in non-dev-mode empty string is returned"
    (unknown-term "kikka") => ""
    (provided
      (env/dev-mode?) => false)))

(facts "regression test for line parsing"
  (read-lines ["error.vrk:BR319:lammitustapa: this: should: work!"
               "kukka: kakka"]) => {"error.vrk:BR319:lammitustapa" "this: should: work!"
                                    "kukka" "kakka"})

(fact "every supported mime type has a display name"
  (doseq [lang [:fi :sv]
          allowed-mime (filter #(re-matches mime/mime-type-pattern %) (vals mime/mime-types))]
    (fact {:midje/description (str (name lang) ": " allowed-mime)}
      (has-term? lang allowed-mime) => true
      (let [desc (localize lang allowed-mime)]
        desc => ss/not-blank?
        desc =not=> (contains "???")))))

(facts "Valid languages"
       (fact "Supported language FI" (valid-language {:data {:lang "FI"}})
             => nil)
       (fact "Supported language sV" (valid-language {:data {:lang "sV"}})
             => nil)
       (fact "Empty language is valid" (valid-language {:data {}}))
       (fact "Unsupported language CN" (valid-language {:data {:lang "CN"}})
             => {:ok false, :text "error.unsupported-language"}))

(facts "Localize with fallback"
       (fact "Finnish"
             (localize-fallback :fi [["email.title" "change-foo"] "sldfkjasdf" ["email.title" "change-email"]])
             => "Uuden s\u00e4hk\u00f6postiosoitteen vahvistus")
       (fact "Swedish"
             (localize-fallback :sv [["email.title" "change-foo"] "sldfkjasdf" ["email.title" "change-email"]])
             => "Verifiering av ny e-postadress")
       (fact "Chinese (default fallback is Finnish)"
             (localize-fallback :cn [["email.title" "change-foo"] "no" ["email.title" "change-email"]])
             => "Ei")
       (fact "Chinese fallbacking to Swedish"
             (localize-fallback :cn [["email.title" "change-foo"] "no" ["email.title" "change-email"]] :sv)
             => "Nej")
       (fact "Chinese fallbacking to Chinese"
             (localize-fallback :cn [["email.title" "change-foo"] "no" ["email.title" "change-email"]] :cn)
             => (throws AssertionError))
              (fact "Chinese fallbacking to Japanese to Finnish"
             (localize-fallback :cn [["email.title" "change-foo"] "no" ["email.title" "change-email"]] :jp)
             => "Ei")
       (fact "Finnish (not found)"
             (localize-fallback :fi [["email.title" "change-foo"] "sldfkjasdf" ["email.titledafasf" "change-email"]])
             => (contains "???"))
       (fact "Chinese fallbacking to Japanese to Finnish (not found)"
             (localize-fallback :cn [["email.title" "change-foo"] "asdf" ["email.adsftitle" "change-email"]] :jp)
             => (contains "???"))
       (fact "Simple term"
             (localize-fallback :cn "no" :jp)
             => "Ei")
       (fact "Nil fallbacks to default language"
             (localize-fallback nil "no") => "Ei"))

(facts "Missing translations"
       (let [localization-map
             {:languages [:fi :sv :en]
              :translations
              {'avain1 {:fi "Vain suomeksi"}
               'avain2 {:fi "Suomeksi ja ruotsiksi" :sv "Finska och svenska"}
               'avain3 {:fi "Kaikki" :sv "Alla" :en "All"}
               'avain4 {:fi "Suomeksi ja englanniksi" :en "Finnish and English"}}}
             missing-sv (missing-translations localization-map :sv)
             missing-en (missing-translations localization-map :en)]
         (:languages missing-sv) => (contains [:fi :sv])
         (map first (:translations missing-sv)) => (contains ['avain1 'avain4] :in-any-order)
         (:languages missing-en) => (contains [:fi :en])
         (map first (:translations missing-en)) => (contains ['avain1 'avain2] :in-any-order)))
