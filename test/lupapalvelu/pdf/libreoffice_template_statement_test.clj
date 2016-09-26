(ns lupapalvelu.pdf.libreoffice-template-statement-test
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [trace debug]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [lupapalvelu.test-util :as test-util]
    [lupapalvelu.i18n :refer [with-lang loc localize] :as i18n]
    [lupapalvelu.pdf.libreoffice-template-statement :as statement]
    [lupapalvelu.pdf.libreoffice-template-base-test :refer :all])
  (:import (java.io File)))

(facts "Statement fodt export "
       (doseq [lang test-util/test-languages]
         (let [tmp-file (File/createTempFile (str "statement-" (name lang) "-") ".fodt")]
           (statement/write-statement-libre-doc application2 "101" lang tmp-file)
           (let [res (s/split (slurp tmp-file) #"\r?\n")
                 doc-start-row (start-pos res)]
             (.delete tmp-file)
             (fact {:midje/description (str " Statement libre document title (" (name lang) ")")} (nth res doc-start-row) => #(s/includes? % (localize lang "application.statement.status")))
             (fact {:midje/description (str " Statement libre document osoite ")} (nth res (+ doc-start-row 1)) => #(s/includes? % "Korpikuusen kannon alla 6"))
             (fact {:midje/description (str " Statement libre document Paikka ")} (nth res (+ doc-start-row 9)) => #(s/includes? % (if (= :fi lang) "J\u00e4rvenp\u00e4\u00e4" "Tr\u00e4sk\u00e4nda")))
             (fact {:midje/description (str " Statement libre document id ")} (nth res (+ doc-start-row 29)) => #(s/includes? % "LP-000-0000-0000"))
             (fact {:midje/description (str " Statement libre document hankkeen osoite ")} (nth res (+ doc-start-row 39)) => #(s/includes? % "Korpikuusen kannon alla 6"))
             (fact {:midje/description (str " Statement libre document sub title (" (name lang) ")")} (nth res (+ doc-start-row 54)) => #(s/includes? % (localize lang "application.statement.status")))
             (fact {:midje/description (str " Statement libre document pyynto (" (name lang) ")")} (nth res (+ doc-start-row 62)) => #(s/includes? % "02.01.2016"))
             (fact {:midje/description (str " Statement libre document content (" (name lang) ")")} (nth res (+ doc-start-row 66)) => #(s/includes? % "Pelastusviranomainen"))
             (fact {:midje/description (str " Statement libre document anto (" (name lang) ")")} (nth res (+ doc-start-row 72)) => #(s/includes? % "01.02.2016"))
             (fact {:midje/description (str " Statement libre document antaja (" (name lang) ")")} (nth res (+ doc-start-row 76)) => #(s/includes? % "Pia Palomies"))
             (fact {:midje/description (str " Statement libre document teksti (" (name lang) ")")} (nth res (+ doc-start-row 82)) => #(s/includes? % "Lausunto liitteen"))
             (fact {:midje/description (str " Statement libre document status (" (name lang) ")")} (nth res (+ doc-start-row 89)) => #(s/includes? % "ehdoilla"))
             (fact {:midje/description (str " Statement libre document attachments (" (name lang) ")")} (nth res (+ doc-start-row 93)) => #(s/includes? % (str "1 " (i18n/localize lang "unit.kpl"))))))))
