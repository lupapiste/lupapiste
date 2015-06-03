(ns lupapalvelu.xml.krysp.yleiset-alueet-canonical-to-krysp-xml-test
  (:require [lupapalvelu.document.yleiset-alueet-canonical :refer [application-to-canonical]]
            [lupapalvelu.document.yleiset-alueet-kaivulupa-canonical-test :refer [kaivulupa-application-with-link-permit-data]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-canonical-test :refer [kayttolupa-application]]
            [lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test :refer [sijoituslupa-application valmistumisilmoitus]]
            [lupapalvelu.document.yleiset-alueet-kayttolupa-mainostus-viitoitus-canonical-test
             :refer [mainostus-application viitoitus-application]]
            [lupapalvelu.xml.emit :refer :all]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping :refer [get-yleiset-alueet-krysp-mapping yleisetalueet-element-to-xml]]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.xml.validator :refer :all :as validator]
            [lupapalvelu.xml.krysp.canonical-to-krysp-xml-test-common :refer :all :as xml-test-common]
            [lupapalvelu.xml.krysp.mapping-common :refer :all :as mapping-common]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key]]
            [midje.sweet :refer :all]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            [lupapalvelu.domain :refer [get-document-by-name]]))

(defn- do-test [application]
  (let [operation-name-key (-> application :primaryOperation :name keyword)
        lupa-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        canonical (application-to-canonical application "fi")
        xml-212 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.2")
        xml-213 (yleisetalueet-element-to-xml canonical lupa-name-key "2.1.3")
        xml-212s (indent-str xml-212)
        xml-213s (indent-str xml-213)
        lp-xml-212 (cr/strip-xml-namespaces (xml/parse xml-212s))
        lp-xml-213 (cr/strip-xml-namespaces (xml/parse xml-213s))]

    (fact ":tag is set"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.1.2")) => true)

    (fact ":tag is set"
      (xml-test-common/has-tag
        (get-yleiset-alueet-krysp-mapping lupa-name-key "2.1.3")) => true)

    (fact "2.1.2: xml exist" xml-212 => truthy)
    (fact "2.1.3: xml exist" xml-213 => truthy)

    ;; Alla oleva tekee jo validoinnin,
    ;; mutta annetaan olla tuossa alla viela tuo validointi, jottei joku tule ja riko olemassa olevaa validointia.
    (mapping-to-krysp/save-application-as-krysp
      application "fi" application {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.2"}}})
    (mapping-to-krysp/save-application-as-krysp
      application "fi" application {:krysp {:YA {:ftpUser "dev_sipoo" :version "2.1.3"}}})

    (validator/validate xml-212s (:permitType application) "2.1.2")
    (validator/validate xml-213s (:permitType application) "2.1.3")

    (fact "LP-tunnus"
      (xml/get-text lp-xml-212 [:MuuTunnus :tunnus]) => (:id application)
      (xml/get-text lp-xml-213 [:MuuTunnus :sovellus]) => "Lupapiste")

    (when (get-document-by-name application :yleiset-alueet-maksaja)
      (fact "Verkkolaskutus fields are present in xml under yleiset-alueet-maksaja"
        (let [Verkkolaskutus (xml/select lp-xml-213 [:verkkolaskutustieto :Verkkolaskutus])]
          (xml/get-text Verkkolaskutus [:ovtTunnus]) => "003712345671"
          (xml/get-text Verkkolaskutus [:verkkolaskuTunnus]) => "laskutunnus-1234"
          (xml/get-text Verkkolaskutus [:valittajaTunnus]) => "BAWCFI22")))

    (when (:linkPermitData application)
      (fact "Link permit"
        (let [muut-tunnukset (xml/select lp-xml-213 [:MuuTunnus])
              link-permit (second muut-tunnukset)]
          (xml/get-text link-permit [:tunnus]) => (-> application :linkPermitData first :id)
          (xml/get-text link-permit [:sovellus]) => "Viitelupa")))))


(facts "YA permits to canonical and then to xml with schema validation"

    (fact "Kaivulupa application -> canonical -> xml"
      (do-test kaivulupa-application-with-link-permit-data))

    (fact "Kayttolupa application -> canonical -> xml"
      (do-test kayttolupa-application))

    (fact "Sijoituslupa application -> canonical -> xml"
      (do-test sijoituslupa-application))

    (fact "Mainostuslupa application -> canonical -> xml"
      (do-test mainostus-application))

    (fact "Viitoituslupa application -> canonical -> xml"
      (do-test viitoitus-application))
    (fact "Valmistumisilmoitus application -> canonical -> xml"
      (do-test valmistumisilmoitus)))
