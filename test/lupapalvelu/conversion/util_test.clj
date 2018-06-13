(ns lupapalvelu.conversion.util-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.conversion.util :as util]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [sade.strings :as ss]))

(def ^:private verdict-a
  (krysp-fetch/get-local-application-xml-by-filename "./dev-resources/krysp/verdict-r-2.1.6-type-A.xml" "R"))

(def ^:private verdict-tjo
  (krysp-fetch/get-local-application-xml-by-filename "./dev-resources/krysp/verdict-r-2.1.6-type-TJO.xml" "R"))

(fact "viitelupatunnus-id's can be extracted from messages"
  (let [ids-a (krysp-reader/->viitelupatunnukset verdict-a)
        ids-tjo (krysp-reader/->viitelupatunnukset verdict-tjo)]
    (facts "the results are not empty"
      (count ids-a) => pos?
      (count ids-tjo) => pos?)
    (facts "the ids are returned in the right format (with type code last)"
      (letfn [(get-code [id]
                (-> id (ss/split #"-") last))
              (code-is-valid? [code]
                (= code (apply str (filter #(Character/isLetter %) code))))
              (every-code-is-valid? [idseq]
                (->> idseq
                     (map (comp code-is-valid? get-code))
                     (every? true?)))]
        (every-code-is-valid? ids-a) => true
        (every-code-is-valid? ids-tjo) => true))))

(facts "kuntalupatunnus ids can be extracted from XML"
  (krysp-reader/->kuntalupatunnus verdict-a) => "01-0001-12-A"
  (krysp-reader/->kuntalupatunnus verdict-tjo) => "01-0012-00-TJO")

(facts "It can be deduced if the verdict is about a foreman or not"
  (krysp-reader/is-foreman-application? verdict-a) => false
  (krysp-reader/is-foreman-application? verdict-tjo) => true)

(fact "Conversion of permit ids from 'database-format' works as expected"
  (facts "ids in 'database-format' are converted into 'ui-format'"
    (util/normalize-permit-id "12-0089-D 17") => "17-0089-12-D"
    (util/normalize-permit-id "10-0088-A 98") => "98-0088-10-A"
    (util/normalize-permit-id "16-0549-DJ 75") => "75-0549-16-DJ")
  (facts "ids already in 'ui-format' are not altered"
    (util/normalize-permit-id "17-0089-12-D") => "17-0089-12-D"
    (util/normalize-permit-id "98-0088-10-A") => "98-0088-10-A"
    (util/normalize-permit-id "75-0549-16-DJ") => "75-0549-16-DJ"))
