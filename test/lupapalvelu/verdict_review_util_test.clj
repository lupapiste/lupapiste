(ns lupapalvelu.verdict-review-util-test
  (:require [lupapalvelu.verdict-review-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import [java.nio.charset StandardCharsets]))

(testable-privates lupapalvelu.verdict-review-util
                   content-disposition-filename)

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
  (facts "verdict target"
    (fact "Paatos"
      (attachment-type-from-krysp-type {:type "verdict"} "Paatosote") => "paatosote")

    (fact "katselmuksen_tai_tarkastuksen_poytakirja"
      (attachment-type-from-krysp-type {:type "verdict"} "katselmuksen_tai_tarkastuksen_poytakirja")  => "paatos")

    (fact "LUPAEHTO"
      (attachment-type-from-krysp-type {:type "verdict"} "LUPAEHTO") => "muu")

    (fact "P\u00e4\u00e4t\u00f6sote"
      (attachment-type-from-krysp-type {:type "verdict"} "P\u00e4\u00e4t\u00f6sote") => "paatosote")

    (fact "ldskfjalsdkjf"
      (attachment-type-from-krysp-type {:type "verdict"} "ldskfjalsdkjf") => "paatos")

    (fact "nil"
      (attachment-type-from-krysp-type {:type "verdict"} nil) => "paatos"))

  (facts "task target"

    (fact "Paatos"
      (attachment-type-from-krysp-type {:type "task"} "Paatosote") => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "katselmuksen_tai_tarkastuksen_poytakirja"
      (attachment-type-from-krysp-type {:type "task"} "katselmuksen_tai_tarkastuksen_poytakirja")  => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "katselmuksen_tai_tarkastuksen_p\u00f6yt\u00e4kirja"
      (attachment-type-from-krysp-type {:type "task"} "katselmuksen_tai_tarkastuksen_p\u00f6yt\u00e4kirja")  => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "Katselmuksen_tai_tarkastuksen_p\u00e6yt\u00c4kirja"
      (attachment-type-from-krysp-type {:type "task"} "Katselmuksen_tai_tarkastuksen_p\u00e6yt\u00c4kirja")  => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "tarkastusasiakirjan_yhteeveto"
      (attachment-type-from-krysp-type {:type "task"} "tarkastusasiakirjan_yhteeveto")  => "tarkastusasiakirjan_yhteeveto")

    (fact "tarkastusasiakirja"
      (attachment-type-from-krysp-type {:type "task"} "tarkastusasiakirja")  => "tarkastusasiakirja")

    (fact "aloituskokouksen_poytakirja"
      (attachment-type-from-krysp-type {:type "task"} "aloituskokouksen_poytakirja")  => "aloituskokouksen_poytakirja")

    (fact "ldskfjalsdkjf"
      (attachment-type-from-krysp-type {:type "task"} "ldskfjalsdkjf") => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "nil"
      (attachment-type-from-krysp-type {:type "task"} nil) => "katselmuksen_tai_tarkastuksen_poytakirja"))


  (facts "unknown target"

    (fact "Paatos"
      (attachment-type-from-krysp-type {:type "statement"} "Paatosote") => "muu")

    (fact "katselmuksen_tai_tarkastuksen_poytakirja"
      (attachment-type-from-krysp-type {:type nil} "katselmuksen_tai_tarkastuksen_poytakirja")  => "muu")

    (fact "ldskfjalsdkjf"
      (attachment-type-from-krysp-type {:type ""} "ldskfjalsdkjf") => "muu")

    (fact "nil"
      (attachment-type-from-krysp-type {:type "foo"} nil) => "muu")))


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
