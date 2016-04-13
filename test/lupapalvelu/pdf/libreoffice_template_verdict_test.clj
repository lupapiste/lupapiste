(ns lupapalvelu.pdf.libreoffice-template-verdict-test
  (:require
    [clojure.java.io :refer [writer reader resource]]
    [clojure.string :as s]
    [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [sade.util :as util]
    [sade.core :as sade]
    [lupapalvelu.organization :refer :all]
    [lupapalvelu.i18n :refer [with-lang loc localize] :as i18n]
    [lupapalvelu.pdf.libreoffice-template-verdict :as verdict]
    [lupapalvelu.pdf.libre-template-test :refer :all])
  (:import (java.io File StringWriter)))

(def applicant-index #'lupapalvelu.pdf.libreoffice-template-verdict/applicant-index)
(fact "Applicant index"
      (applicant-index application2) => '[["Testaaja Testi"]])

(facts "Verdict lupamaaraykset "
       (def verdict-lupamaaraykset #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-lupamaaraykset)
       (fact {:midje/description (str " krysp")}
             (verdict-lupamaaraykset application1 "a1" 0 :fi) => '[["Kerrosala" "100m2"]])
       (fact {:midje/description (str " non-krysp")}
             (verdict-lupamaaraykset application2 "a1" 0 :fi) => '[]))

(facts "Verdict foremen "
       (def verdict-foremen #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-foremen)
       (fact {:midje/description (str " krysp")}
             (verdict-foremen application1 "a1" 0) => '[["Vastaava ty\u00f6njohtaja"] ["Toinen ty\u00f6njohtaja"]])
       (fact {:midje/description (str " non-krysp")}
             (verdict-foremen application2 "a1" 0) => '[["Joku ty\u00f6hohtaja"] ["Joku toinen ty\u00f6hohtaja"]]))

(facts "Verdict vaaditutErityissuunnitelmat "
       (def verdict-vaaditutErityissuunnitelmat #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-vaaditutErityissuunnitelmat)
       (fact {:midje/description (str " krysp")}
             (verdict-vaaditutErityissuunnitelmat application1 "a1" 0) => '[["Joku erityissuunnitelma"] ["Joku toinen erityissuunnitelma"]])
       (fact {:midje/description (str " non-krysp")}
             (verdict-vaaditutErityissuunnitelmat application2 "a1" 0) => '[]))

(facts "Verdict other muutMaaraykset "
       (def verdict-muutMaaraykset #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-muutMaaraykset)
       (fact {:midje/description (str " krysp")}
             (verdict-muutMaaraykset application1 "a1" 0) => '[])
       (fact {:midje/description (str " non-krysp")}
             (verdict-muutMaaraykset application2 "a1" 0) => '[["Joku lupam\u00e4\u00e4r\u00e4ys"]]))

(facts "Verdict vaaditutKatselmukset "
       (def verdict-vaaditutKatselmukset #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-vaaditutKatselmukset)
       (fact {:midje/description (str "verdict-reviews krysp")}
             (verdict-vaaditutKatselmukset application1 "a1" 0 :fi) => '[[" muu katselmus " "* KVV-tarkastus"] [" muu katselmus " " * S\u00e4hk\u00f6tarkastus "] [" muu katselmus " " * Rakennetarkastus "] [" loppukatselmus " ""] [" muu katselmus " " Aloitusilmoitus "]])
       (fact {:midje/description (str "verdict-reviews non-krysp")}
             (verdict-vaaditutKatselmukset application2 "a1" 0 :fi) => '[("Muu katselmus")]))

(facts "Verdict signatures "
       (def verdict-signatures #'lupapalvelu.pdf.libreoffice-template-verdict/verdict-signatures)
       (fact
         (let [verdict (first (filter #(= "a1" (:id %)) (:verdicts application2)))
               paatos (nth (:paatokset verdict) 0)]
           (verdict-signatures verdict paatos) => '[["Tytti Mäntyoja" "04.02.2016"] ["Matti Mallikas" "23.02.2015"] ["Minna Mallikas" "23.02.2015"]])))


(defn- start-pos [res]
  (first (first (filter #(s/includes? (second %) "</draw:frame>") (map-indexed vector res)))))

(background
  (get-organization "753-R") => {:name {:fi "org-name-fi"}})

(facts "Verdict export krysp "
       (doseq [lang i18n/languages]
         (let [tmp-file (File/createTempFile (str "verdict-krysp-" (name lang) "-") ".fodt")]
           (verdict/write-verdict-libre-doc application1 "a1" 0 lang tmp-file)
           (let [res (s/split (slurp tmp-file) #"\r?\n")
                 pos (start-pos res)]
             (.delete tmp-file)
             (fact {:midje/description (str " verdict libre document title (" (name lang) ")")} (nth res pos) => #(s/includes? % (localize lang "application.verdict.title")))
             (fact {:midje/description (str " verdict libre document id ")} (nth res (+ pos 1)) => #(s/includes? % "LP-000-0000-0000"))
             (fact {:midje/description (str " verdict libre document kuntalupatunnus ")} (nth res (+ pos 2)) => #(s/includes? % "20160043"))
             ;;TODO: test rest of the lines
             ))))

(facts "Verdict export non-krysp "
         (doseq [lang i18n/languages]
           (let [tmp-file (File/createTempFile (str "verdict-" (name lang) "-") ".fodt")]
             (verdict/write-verdict-libre-doc application2 "a1" 0 lang tmp-file)
             (let [res (s/split (slurp tmp-file) #"\r?\n")
                   pos (start-pos res)]
               (.delete tmp-file)
               (fact {:midje/description (str " verdict libre document title (" (name lang) ")")} (nth res pos) => #(s/includes? % (localize lang "application.verdict.title")))
               (fact {:midje/description (str " verdict libre document id ")} (nth res (+ pos 1)) => #(s/includes? % "LP-000-0000-0000"))
               (fact {:midje/description (str " verdict libre document kuntalupatunnus ")} (nth res (+ pos 2)) => #(s/includes? % "20160043"))
               ;;TODO: test rest of the lines
               ))))

(facts "Verdict-contract export non-krysp "
       (doseq [lang i18n/languages]
           (let [tmp-file (File/createTempFile (str "verdict-contract-" (name lang) "-") ".fodt")]
             (verdict/write-verdict-libre-doc (assoc application2 :verdicts (map #(assoc % :sopimus true) (:verdicts application2))) "a1" 0 lang tmp-file)
             (let [res (s/split (slurp tmp-file) #"\r?\n")
                   pos (start-pos res)]
               (.delete tmp-file)
               (fact {:midje/description (str " verdict libre document title (" (name lang) ")")} (nth res pos) => #(s/includes? % (localize lang "userInfo.company.contract")))
               (fact {:midje/description (str " verdict libre document id ")} (nth res (+ pos 1)) => #(s/includes? % "LP-000-0000-0000"))
               (fact {:midje/description (str " verdict libre document kuntalupatunnus ")} (nth res (+ pos 2)) => #(s/includes? % "20160043"))
               (fact {:midje/description (str " verdict libre document last signature ")} (nth res (+ pos 87)) => #(s/includes? % "Minna Mallikas"))
               ))))
