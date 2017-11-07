(ns lupapalvelu.verdict-review-util-test
  (:require [lupapalvelu.verdict-review-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import [java.nio.charset StandardCharsets]))

(testable-privates lupapalvelu.verdict-review-util
                   attachment-type-from-krysp-type content-disposition-filename)

(facts verdict-attachment-type
  (fact "R"
    (verdict-attachment-type {:permitType "R"}) => {:type-group "paatoksenteko" :type-id "paatosote"})
  (fact "P"
    (verdict-attachment-type {:permitType "P"}) => {:type-group "paatoksenteko" :type-id "paatosote"})
  (fact "YA"
    (verdict-attachment-type {:permitType "YA"}) => {:type-group "muut" :type-id "paatosote"})
  (fact "YI"
    (verdict-attachment-type {:permitType "YI"}) => {:type-group "muut" :type-id "paatosote"})
  (fact "VVVL"
    (verdict-attachment-type {:permitType "VVVL"}) => {:type-group "muut" :type-id "paatosote"})
  (fact "R - with type"
    (verdict-attachment-type {:permitType "R"} anything) => {:type-group "paatoksenteko" :type-id anything})
  (fact "YA - with type"
    (verdict-attachment-type {:permitType "YA"} anything) => {:type-group "muut" :type-id anything})
  (fact "R - review attachment"
    (verdict-attachment-type {:permitType "R"} "katselmuksen_tai_tarkastuksen_poytakirja")
    => {:type-group "katselmukset_ja_tarkastukset"
        :type-id    "katselmuksen_tai_tarkastuksen_poytakirja"}))

(facts attachment-type-from-krysp-type
  (attachment-type-from-krysp-type "Paatosote") => "paatosote"
  (attachment-type-from-krysp-type "katselmuksen_tai_tarkastuksen_poytakirja")
  => "katselmuksen_tai_tarkastuksen_poytakirja"
  (attachment-type-from-krysp-type "LUPAEHTO") => "muu"
  (attachment-type-from-krysp-type "ldskfjalsdkjf") => "paatos"
  (attachment-type-from-krysp-type nil) => "paatos")

(facts "Content-Disposition and string encoding"
       (fact "No header"
             (content-disposition-filename {:headers {}}) => nil?)
       (fact "No decoding"
             (content-disposition-filename {:headers {"content-disposition" "attachment; filename=P\u00e4\u00e4t\u00f6sote.txt"}})
             => "P\u00e4\u00e4t\u00f6sote.txt")
       (fact "Encoding: Microsoft-IIS/7.5"
             (content-disposition-filename {:headers {"content-disposition" (String. (.getBytes  "attachment; filename=\"P\u00e4\u00e4t\u00f6sote.txt\""
                                                                                                 StandardCharsets/UTF_8)
                                                                                     StandardCharsets/ISO_8859_1)
                                                      "server"              "Microsoft-IIS/7.5"}})
             => "P\u00e4\u00e4t\u00f6sote.txt"))
