(ns lupapalvelu.xml.krysp.rakennuslupa_canonical_to_krysp_xml_test
  (:use [lupapalvelu.document.rakennuslupa_canonical-test]
        [midje.sweet]
        [lupapalvelu.document.rakennuslupa_canonical :only [by-type application-to-canonical]]
        [lupapalvelu.xml.emit]
        [lupapalvelu.xml.krysp.rakennuslupa-mapping]
        [lupapalvelu.xml.krysp.validator]
        [clojure.data.xml]
        [clojure.java.io]))


(defn- has-tag [m]
  (if (:tag m)
    (if-let [children (seq (:child m))]
      (if (reduce #(and %1 %2) (map has-tag children))
          true
          (do
            (println "Tag missing in:") (clojure.pprint/pprint children) (println)
            false))
      true)
    false)
  )

(fact ":tag is set" (has-tag rakennuslupa_to_krysp) => true)

(facts "Rakennuslupa to canonical and then to rakennuslupa xml with schema validation"
  (let [canonical (application-to-canonical application)
        xml (element-to-xml canonical rakennuslupa_to_krysp)
        xml-s (indent-str xml)]

    (println xml-s)
    ;Alla oleva tekee jo validoinnin, mutta annetaan olla tuossa alla viela validointi, jottei tule joku riko olemassa olevaa validointia
    (get-application-as-krysp application "fi" application {:rakennus-ftp-user "sipoo"}) ;TODO: own test

    ;(clojure.pprint/pprint application)

    ;(clojure.pprint/pprint rakennuslupa_to_krysp)
    ;(with-open [out-file (writer "/Users/terotu/example.xml" )]
    ;    (emit xml out-file))
    (fact xml => truthy)

    (let [xml-reader (java.io.StringReader. xml-s)
          xml-source (javax.xml.transform.stream.StreamSource. xml-reader)]
      ; Throws some exception if the markup is invalid
      (.validate schema-validator xml-source)
      )

    )
  )
