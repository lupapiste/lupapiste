(ns lupapalvelu.attachment-test
  (:use [lupapalvelu.attachment]
        [clojure.test]
        [midje.sweet])
  (:require [clojure.string :as s]
            [monger.core :as monger]
            [lupapalvelu.core :as core]
            [lupapalvelu.mongo :as mongo]))

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

(facts "Test file name encoding"
  (fact (encode-filename nil)                                 => nil)
  (fact (encode-filename "foo.txt")                           => "foo.txt")
  (fact (encode-filename (apply str (repeat 255 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x) ".txt"))  => (has-suffix ".txt"))
  (fact (encode-filename "\u00c4\u00e4kk\u00f6si\u00e4")      => (just ascii-pattern))
  (fact (encode-filename "/root/secret")                      => (just ascii-pattern))
  (fact (encode-filename "\\Windows\\cmd.exe")                => (just ascii-pattern))
  (fact (encode-filename "12345\t678\t90")                    => (just ascii-pattern))
  (fact (encode-filename "12345\n678\r\n90")                  => (just ascii-pattern)))

(facts "Test parse-attachment-type"
  (fact (parse-attachment-type "foo.bar")  => [:foo :bar])
  (fact (parse-attachment-type "foo.")     => nil)
  (fact (parse-attachment-type "")         => nil)
  (fact (parse-attachment-type nil)        => nil))

(def test-attachments [{:id "1", :latestVersion {:version {:major 9, :minor 7}}}])

(facts "Test attachment-latest-version"
  (fact (attachment-latest-version test-attachments "1")    => {:major 9, :minor 7})
  (fact (attachment-latest-version test-attachments "none") => nil?))

(def next-attachment-version #'lupapalvelu.attachment/next-attachment-version)

(facts "Facts about next-attachment-version"
  (fact (next-attachment-version {:major 1 :minor 1} {:role :authority})  => {:major 1 :minor 2})
  (fact (next-attachment-version {:major 1 :minor 1} {:role :dude})       => {:major 2 :minor 0}))

(def allowed-attachment-type-for? #'lupapalvelu.attachment/allowed-attachment-type-for?)

(facts "Facts about allowed-attachment-type-for?"
  (fact (allowed-attachment-type-for? :buildingPermit {:type-group "hakija" :type-id "valtakirja"})          => truthy)
  (fact (allowed-attachment-type-for? :buildingPermit {:type-group "paapiirustus" :type-id "asemapiirros"})  => truthy)
  (fact (allowed-attachment-type-for? :buildingPermit {:type-group "hakija" :type-id "asemapiirros"})        => falsey)
  (fact (allowed-attachment-type-for? :buildingPermit {})                                                    => falsey))

; The result of attachment-types-for has very strict format that is required by upload.html. The
; structure should be a vector that looks like this:
;
; [{:key :hakija
;   :types [{:key :valtakirja}
;           {:key :ote_kauppa_ja_yhdistysrekisterista}
;           {:key :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta}]}
;  {:key :rakennuspaikan_hallinta
;   :types [{:key :jaljennos_myonnetyista_lainhuudoista}
;           ...

(def attachment-types-for #'lupapalvelu.attachment/attachment-types-for)

(facts "The result of attachment-types-for has very strict format that is required by upload.html"
  (fact (attachment-types-for :buildingPermit) => sequential?)
  (fact (first (attachment-types-for :buildingPermit)) => associative?)
  (fact (:group (first (attachment-types-for :buildingPermit))) => keyword?)
  (fact (:types (first (attachment-types-for :buildingPermit))) => sequential?)
  (fact (first (:types (first (attachment-types-for :buildingPermit)))) => associative?)
  (fact (:name (first (:types (first (attachment-types-for :buildingPermit))))) => keyword?))
