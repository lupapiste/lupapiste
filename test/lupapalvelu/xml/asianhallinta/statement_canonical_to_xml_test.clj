(ns lupapalvelu.xml.asianhallinta.statement-canonical-to-xml-test
  (:require [midje.sweet :refer :all]
            [midje.util :as mu]
            [lupapalvelu.test-util :refer [passing-quick-check]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check :as tc]
            [clojure.data.xml :as xml]
            [lupapalvelu.document.asianhallinta-canonical :as ah]
            [lupapalvelu.document.poikkeamis-canonical-test :as poikkeus-test]
            [lupapalvelu.statement :as stmnt]
            [lupapalvelu.xml.asianhallinta.asianhallinta-mapping :as ah-mapping]
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :as validator]
            [sade.common-reader :as reader]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.schema-utils :as ssu]
            [sade.schema-generators :as ssg]
            [sade.xml :as sxml]
            [sade.shared-schemas :as sschemas]))

(mu/testable-privates lupapalvelu.xml.asianhallinta.asianhallinta-mapping
                      create-statement-request-canonical)

(def sonja-requester {:firstName "Sonja" :lastName "Sibbo" :organization {:name {:fi "Sipoo"}}})

(def attachments [{:id :attachment1
                   :type {:type-group "paapiirustus"
                          :type-id    "asemapiirros"}
                   :latestVersion {:version { :major 1 :minor 0 }
                                   :fileId "file321"
                                   :filename "asemapiirros.pdf"
                                   :contentType "application/pdf"}
                   :modified 1424248442767}
                  {:id :attachment2
                   :type {:type-group "hakija"
                          :type-id    "valtakirja"}
                   :latestVersion {:version { :major 1 :minor 0 }
                                   :fileId "file123"
                                   :filename "valtakirja.pdf"
                                   :contentType "application/pdf"}
                   :op [{:id "523844e1da063788effc1c56"}]
                   :modified 1424248442767}
                  {:id :attachment3
                   :type {:type-group "paapiirustus"
                          :type-id    "pohjapiirros"}
                   :versions []}
                  {:id :attachment4
                   :type {:type-group "paapiirustus"
                          :type-id    "pohjapiirros"}
                   :target {:type "statement"
                            :id "testi1"}
                   :latestVersion {:version { :major 1 :minor 0 }
                                   :fileId "file123"
                                   :filename "lausunto.pdf"
                                   :contentType "application/pdf"}}])

(fact :qc "Statement to XML"
  (tc/quick-check
    150
    (prop/for-all
      [full-statement (ssg/generator (ssu/select-keys
                                       stmnt/Statement
                                       [:id :saateText :status :text :dueDate :requested :given :person :external]))]
      (let [application    (ah-mapping/enrich-application
                             (assoc poikkeus-test/poikkari-hakemus :attachments attachments))
            canonical      (create-statement-request-canonical
                             sonja-requester application full-statement "fi")
            canonical      (assoc-in canonical
                                     [:UusiAsia :Liitteet :Liite]
                                     (ah/get-attachments-as-canonical (:attachments application) "sftp://foo.faa" #(not= "verdict" (-> % :target :type))))
            schema-version "ah-1.3"
            mapping        (ah-mapping/get-uusi-asia-mapping (ss/suffix schema-version "-"))
            xml            (element-to-xml canonical mapping)
            xml-s          (xml/indent-str xml) => truthy
            xml-parsed     (reader/strip-xml-namespaces (sxml/parse xml-s))]
        (fact "xml ok"
          xml => truthy)
        (fact "Validate UusiAsia Lausuntopyynto XML"
          (validator/validate xml-s (:permitType application) schema-version) => nil)
        (fact "version attribute"
          (sxml/select1-attribute-value xml-parsed [:UusiAsia] :version) => (ss/suffix schema-version "-"))
        (fact "Tyyppi"
          (sxml/get-text xml-parsed [:Tyyppi]) => "Lausuntopyynt\u00f6")
        (fact "TyypinTarkenne"
          (when-let [tarkenne (get-in full-statement [:external :subtype])]
            (sxml/get-text xml-parsed [:TyypinTarkenne]) => tarkenne))
        (facts "Lausuntopyynto element"
          (let [lausuntopyynto (sxml/select1 xml-parsed [:Lausuntopyynto])]
            (facts "required keys"
              (doseq [[elem pattern] [[:LausuntoTunnus sschemas/object-id-pattern]
                                      [:Pyytaja #"Sipoo.+Sonja"]
                                      [:PyyntoPvm #"^\d\d\d\d-\d\d-\d\d$"]]]
                (fact {:midje/description (name elem)}
                  (sxml/get-text lausuntopyynto [elem]) => pattern)))
            (facts "optional keys"
              (doseq [[elem key fn] [[:Saateteksti :saateText]
                                     [:Maaraaika :dueDate util/to-xml-date]]
                      :let [val (if (fn? fn)
                                  (fn (get full-statement key))
                                  (get full-statement key))]
                      :when (ss/not-blank? val)]
                (fact {:midje/description (name elem)}
                  (sxml/get-text lausuntopyynto [elem]) => val)))))
        (fact "Liiiteet"
          (let [liitteet (:content (sxml/select1 xml-parsed [:UusiAsia :Liitteet]))]
            (count liitteet) => (dec (count attachments)))) ; one without latestVersion is declined
        ))) => passing-quick-check )
