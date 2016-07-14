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
