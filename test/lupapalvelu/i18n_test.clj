(ns lupapalvelu.i18n-test
  (:require [lupapalvelu.i18n :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.mime :as mime]
            [sade.strings :as ss]
            [sade.env :as env]))

(testable-privates lupapalvelu.i18n merge-localization-maps)

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

(fact "State.open.clarification is a weird special case.

       Open state in swedish text is semantically different from Finnish and English. Hence, clarification text should not be
       shown in sv-site.

       <span /> is used as sv-translation instead of emtpy string because key would otherwise be included in the missing
       translations Excel file. This is a workaround and maybe it's not needed in future."
  (let [terms (get-localizations)]
    (get-in terms [:sv "state.open.clarification"]) => "<span />"
    (get-in terms [:fi "state.open.clarification"]) => "(ei jätetty)"))

(facts "regression test for line parsing"
  (read-lines ["error.vrk:BR319:lammitustapa: this: should: work!"
               "kukka: kakka"]) => {"error.vrk:BR319:lammitustapa" "this: should: work!"
                                    "kukka" "kakka"})

(fact "every supported mime type has a display name"
  (doseq [lang [:fi :sv :en]
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
             => "Aktivoi uusi tunnuksesi")
       (fact "Swedish"
             (localize-fallback :sv [["email.title" "change-foo"] "sldfkjasdf" ["email.title" "change-email"]])
             => "Aktivera ditt nya anv\u00e4ndarnamn")
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

(def localization-map
  {:languages [:fi :sv :en]
   :translations
   {(with-meta 'avain1 {:source-name "file1.txt"})
    {:fi "Vain suomeksi"}
    (with-meta 'avain2 {:source-name "file1.txt"})
    {:fi "Suomeksi ja ruotsiksi" :sv "Finska och svenska"}
    (with-meta 'avain3 {:source-name "file2.txt"})
    {:fi "Kaikki" :sv "Alla" :en "All"}
    (with-meta 'avain4 {:source-name "file2.txt"})
    {:fi "Suomeksi ja englanniksi" :en "Finnish and English"}}})

(def new-translations-map
  {:languages [:fi :en]
   :translations
   {'avain1 {:fi "Vain suomeksi" :en "Only in Finnish"}
    'avain2 {:fi "Suomeksi ja ruotsiksi" :en "In finnish and in Swedish"}
    'avain3 {:fi "Kaikki" :en "All, Mk.2"}}})

(defn translations-unchanged-for [loc-map lang]
  (fn [actual]
    (every? identity
            (apply map (fn [old new]
                         (= (get old lang)
                            (get new lang)))
                   (map (comp (apply juxt (keys (:translations loc-map)))
                              :translations)
                        [loc-map actual])))))

(defn localization-for [loc-map key lang]
  (-> loc-map :translations key lang))

(fact "Missing translations"
      (let [missing-sv (missing-translations localization-map :sv)
            missing-en (missing-translations localization-map :en)]
        (:languages missing-sv)                => (contains [:fi :sv])
        (map first (:translations missing-sv)) => (contains ['avain1 'avain4] :in-any-order)
        (:languages missing-en)                => (contains [:fi :en])
        (map first (:translations missing-en)) => (contains ['avain1 'avain2] :in-any-order)))

(facts "Merging in new translations"
      (let [merged-map (merge-new-translations localization-map
                                               new-translations-map
                                               :en)]
        (fact "The merged map contains fi, sv and en languages"
              (:languages merged-map) => (contains [:fi :sv :en] :in-any-order))
        (fact "Merging only changes the translations for one language"
              merged-map => (translations-unchanged-for localization-map :fi)
              merged-map => (translations-unchanged-for localization-map :sv))
        (fact "New translations are added if there was no previous translation"
              (localization-for merged-map 'avain1 :en) => (localization-for new-translations-map 'avain1 :en))
        (fact "New translations overwrite the old ones"
              (localization-for merged-map 'avain3 :en) => (localization-for new-translations-map 'avain3 :en))))

(facts "Error handling when merging translations"
       (fact "Exception is thrown when unexpected translation is encountered"
             (merge-new-translations localization-map
                                     new-translations-map
                                     :sv) => (throws #"unexpected language"))
       (fact "Exception is thrown when the Finnish text differs in original and translation"
             (merge-new-translations localization-map
                                     (assoc-in new-translations-map
                                               [:translations 'avain1 :fi]
                                               "Suomen teksti muuttunut")
                                     :en) => (throws #"does not match"))
       (fact "Exception is thrown when Finnish text is missing in original"
             (merge-new-translations (assoc-in localization-map
                                               [:translations 'avain1 :fi]
                                               nil)
                                     new-translations-map
                                     :en) => (throws #"text not found")))

(defn keys-for-file [file keys]
  (comp (contains (sort keys))
        #(get % file)))

(facts "Splitting translations back to their respective source files"
       (let [merged-map (merge-new-translations localization-map
                                                new-translations-map
                                                :en)
             grouped-by-source (group-translations-by-source merged-map)]
         (map first (get grouped-by-source "file1.txt")) => (contains ['avain1 'avain2])
         (map first (get grouped-by-source "file2.txt")) => (contains ['avain3 'avain4])))

(fact "An error is thrown when duplicate key is encountered"
      (merge-localization-maps [{:translations {'avain {:fi "avain"}}}
                                {:translations {'ei-liity {:fi "jotain muuta"}}}
                                {:translations {'avain {:fi "aivan"}}}])
      => (throws #"same key appears in multiple sources"))

(fact "english is a supported language iff feature.english = true"
      (or (and (env/feature? :english)
               (contains? (set supported-langs) :en))
          (not (contains? (set supported-langs) :en))) => truthy)

(facts "Localize and fill"
       (fact "Simple usage"
             (localize-and-fill :fi "register.company.price" "foo")
             => "foo \u20ac"
             (localize-and-fill "fi" :register.company.price 88)
             => "88 \u20ac"
             (localize-and-fill :fi ["register" "company" "price"] :hi)
             => "hi \u20ac"
             (localize-and-fill "fi" [:register "company"  :price] 99)
             => "99 \u20ac")
       (fact "Multiple substitutions"
             (localize-and-fill :en "applications.results" "one" 2 :three "out")
             => "Applications one - 2 / three"
             (localize-and-fill :en "applications.results" "one" 2)
             => "Applications one - 2 / {2}")
       (fact "No substitutions"
             (localize-and-fill :fi :applications.operation "foo" "bar")
             => "Toimenpide"))

(let [unknown-as-fi "Odottamaton virhe"
      lang-map (to-lang-map #(localize % "error.unknown"))]
  (fact "to-lang-map"
    lang-map => map?
    (:fi lang-map) => unknown-as-fi
    (keys lang-map) => supported-langs
    (= lang-map
       (zipmap supported-langs (map #(localize % "error.unknown") supported-langs))) => true)
  (fact "with string"
    (:fi (to-lang-map "error.unknown")) => unknown-as-fi))

(facts "localize"
  (localize "fi" "verdict.status") => "P\u00e4\u00e4t\u00f6s"
  (localize "fi" "verdict" "status") => "P\u00e4\u00e4t\u00f6s"
  (localize :fi :verdict :status) => "P\u00e4\u00e4t\u00f6s"
  (localize :fi :verdict nil :status) => "???verdict..status???"
  (localize :fi :verdict :status nil) => "???verdict.status.???"
  (localize :fi :verdict :status :6) => "Ehdollinen"
  (localize :fi :verdict :status "6") => "Ehdollinen"
  (localize :fi :verdict :status 6) => "Ehdollinen"
  (localize :fi :verdict.status.6) => "Ehdollinen"
  (localize :fi "verdict" :status 6) => "Ehdollinen")
