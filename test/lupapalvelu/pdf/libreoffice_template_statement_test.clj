(ns lupapalvelu.pdf.libreoffice-template-statement-test
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [trace debug]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [lupapalvelu.i18n :refer [with-lang loc localize] :as i18n]
    [lupapalvelu.pdf.libreoffice-template-statement :as statement]
    [lupapalvelu.pdf.libreoffice-template-base-test :refer :all])
  (:import (java.io File)))

(facts "Statement fodt export "
       (doseq [lang i18n/languages]
         (let [tmp-file (File/createTempFile (str "statement-" (name lang) "-") ".fodt")]
           (statement/write-statement-libre-doc application2 "101" lang tmp-file)
           (let [res (s/split (slurp tmp-file) #"\r?\n")
                 pos (start-pos res)]
             (.delete tmp-file)
             (fact {:midje/description (str " Statement libre document title (" (name lang) ")")} (nth res pos) => #(s/includes? % (localize lang "application.statement.status")))
             (fact {:midje/description (str " Statement libre document osoite ")} (nth res (+ pos 1)) => #(s/includes? % "Korpikuusen kannon alla 6"))
             (fact {:midje/description (str " Statement libre document Paikka ")} (nth res (+ pos 9)) => #(s/includes? % (if (= :fi lang) "J\u00e4rvenp\u00e4\u00e4" "Tr\u00e4sk\u00e4nda")))
             (fact {:midje/description (str " Statement libre document id ")} (nth res (+ pos 29)) => #(s/includes? % "LP-000-0000-0000"))
             (fact {:midje/description (str " Statement libre document hankkeen osoite ")} (nth res (+ pos 39)) => #(s/includes? % "Korpikuusen kannon alla 6"))
             (fact {:midje/description (str " Statement libre document sub title (" (name lang) ")")} (nth res (+ pos 54)) => #(s/includes? % (localize lang "application.statement.status")))
             (fact {:midje/description (str " Statement libre document pyynto (" (name lang) ")")} (nth res (+ pos 62)) => #(s/includes? % "02.01.2016"))
             (fact {:midje/description (str " Statement libre document content (" (name lang) ")")} (nth res (+ pos 66)) => #(s/includes? % "Pelastusviranomainen"))
             (fact {:midje/description (str " Statement libre document anto (" (name lang) ")")} (nth res (+ pos 72)) => #(s/includes? % "01.02.2016"))
             (fact {:midje/description (str " Statement libre document antaja (" (name lang) ")")} (nth res (+ pos 76)) => #(s/includes? % "Pia Palomies"))
             (fact {:midje/description (str " Statement libre document teksti (" (name lang) ")")} (nth res (+ pos 82)) => #(s/includes? % "Lausunto liitteen"))
             (fact {:midje/description (str " Statement libre document status (" (name lang) ")")} (nth res (+ pos 89)) => #(s/includes? % "ehdoilla"))))))
