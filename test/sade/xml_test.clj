(ns sade.xml-test
  (:require [sade.xml :refer :all]
            [midje.sweet :refer :all]
            [clojure.string :as s]))


;; See https://www.owasp.org/index.php/XML_External_Entity_%28XXE%29_Processing

(def xxe-attack
  "<?xml version=\"1.0\"?>
<!DOCTYPE foo [
  <!ELEMENT foo ANY >
  <!ENTITY xxe SYSTEM \"../project.clj\" >]>
<foo>&xxe;</foo>")

(spit "target/xxe-attack.xml" xxe-attack)

;; See http://msdn.microsoft.com/en-us/magazine/ee335713.aspx

(def ent-len 64000)
(def ent-times 100) ; Processed XML will expand to 100 fold
(def bomb-yield (* ent-len ent-times))
(def xml-bomb
  (str "<?xml version=\"1.0\"?>
<!DOCTYPE foo [
  <!ENTITY a \"" (s/join (repeat ent-len "a")) "\">]>
<foo>" (s/join (repeat ent-times "&a;"))
    "</foo>"))

(fact "tparse does not process external entities"
  (parse "target/xxe-attack.xml") => (throws org.xml.sax.SAXParseException))

(fact "parse notices XML bombs"
  (parse xml-bomb) => (throws org.xml.sax.SAXParseException))

(fact "U+FEFF does not stop the parser"
  (parse "\uFEFF<?xml version=\"1.0\"?>
<ExceptionReport version=\"1.1.0\" xmlns=\"http://www.opengis.net/ows\">
<Exception><ExceptionText>TYPENAME yak:YleisetAlueet not defined</ExceptionText></Exception>
</ExceptionReport>") => truthy)
