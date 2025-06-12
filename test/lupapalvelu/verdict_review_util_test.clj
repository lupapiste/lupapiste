(ns lupapalvelu.verdict-review-util-test
  (:require [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-review-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [ring.util.io :as io]
            [sade.http :as http])
  (:import [java.nio.charset StandardCharsets]))

(set! *warn-on-reflection* true)

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

    (fact "Päätösote"
      (attachment-type-from-krysp-type {:type "verdict"} "Päätösote") => "paatosote")

    (fact "ldskfjalsdkjf"
      (attachment-type-from-krysp-type {:type "verdict"} "ldskfjalsdkjf") => "paatos")

    (fact "nil"
      (attachment-type-from-krysp-type {:type "verdict"} nil) => "paatos"))

  (facts "task target"

    (fact "Paatos"
      (attachment-type-from-krysp-type {:type "task"} "Paatosote") => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "katselmuksen_tai_tarkastuksen_poytakirja"
      (attachment-type-from-krysp-type {:type "task"} "katselmuksen_tai_tarkastuksen_poytakirja")  => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "katselmuksen_tai_tarkastuksen_pöytäkirja"
      (attachment-type-from-krysp-type {:type "task"} "katselmuksen_tai_tarkastuksen_pöytäkirja")  => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "Katselmuksen_tai_tarkastuksen_p\u00e6yt\u00c4kirja"
      (attachment-type-from-krysp-type {:type "task"} "Katselmuksen_tai_tarkastuksen_p\u00e6yt\u00c4kirja")  => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "tarkastusasiakirjan_yhteeveto"
      (attachment-type-from-krysp-type {:type "task"} "tarkastusasiakirjan_yhteeveto")  => "tarkastusasiakirjan_yhteeveto")

    (fact "tarkastusasiakirja"
      (attachment-type-from-krysp-type {:type "task"} "tarkastusasiakirja")  => "tarkastusasiakirja")

    (fact "aloituskokouksen_poytakirja"
      (attachment-type-from-krysp-type {:type "task"} "aloituskokouksen_poytakirja")  => "aloituskokouksen_poytakirja"
      (attachment-type-from-krysp-type {:type "task"} "let's start!")  => "katselmuksen_tai_tarkastuksen_poytakirja"
      (attachment-type-from-krysp-type {:type "task" :review-type " ALoiTUSkoKOUS"} "let's start!")
      => "aloituskokouksen_poytakirja")

    (fact "ldskfjalsdkjf"
      (attachment-type-from-krysp-type {:type "task"} "ldskfjalsdkjf") => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "nil"
      (attachment-type-from-krysp-type {:type "task"} nil) => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "Loppukatselmus"
      (attachment-type-from-krysp-type {:type "task" :review-type "  LoPPuKatselmuS  "}
                                       "katselmuksen_tai_tarkastuksen_poytakirja")
      => "loppukatselmuksen_poytakirja")

    (fact "Osittainen loppukatselmus"
      (attachment-type-from-krysp-type {:type "task" :review-type "osittainen loppukatselmus"}
                                       "katselmuksen_tai_tarkastuksen_poytakirja")
      => "katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "Pohjakatselmus"
      (attachment-type-from-krysp-type {:type "task" :review-type "pohjakatselmus"}
                                       "katselmuksen_tai_tarkastuksen_poytakirja")
      => "katselmuksen_tai_tarkastuksen_poytakirja"))

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
    (content-disposition-filename {:headers {"content-disposition" "attachment; filename=Päätösote.txt"}})
    => "Päätösote.txt")
  (fact "Encoding: Microsoft-IIS/7.5"
    (content-disposition-filename {:headers {"content-disposition" (String. (.getBytes  "attachment; filename=\"Päätösote.txt\""
                                                                                        StandardCharsets/UTF_8)
                                                                            StandardCharsets/ISO_8859_1)
                                             "server"              "Microsoft-IIS/7.5"}})
    => "Päätösote.txt"))

(def download-and-store-poytakirja! #'lupapalvelu.verdict-review-util/download-and-store-poytakirja!)

(facts "urlHash"
  (let [pk         {:liite {:linkkiliitteeseen "http://foo.bar/paatosote123.pdf"}}
        url-hash   "73d84addb5dc77651a612e24698025f1f8684b8b"
        app        {:permitType "R" :id "1234567890"}
        user       (usr/batchrun-user "000-R")
        ts         1513942123747
        verdict-id (mongo/create-id)
        system     {}]
    (fact "Url hash calculation"
      (urlHash (:liite pk)) => url-hash)
    (fact "upload-and-attach! returns falsey => download-and-store returns nil"
      (download-and-store-poytakirja! system app user ts {:type "verdict" :id verdict-id} true
                                      {:linkkiliitteeseen "http://foo.bar/paatosote123.pdf"})
      => nil
      (provided
        (attachment/upload-and-attach! {:application app :user user :system system} anything anything) => nil
        (domain/get-application-as "1234567890" user :include-canceled-apps? nil) => app
        (http/get "http://foo.bar/paatosote123.pdf" :as :stream :throw-exceptions false :conn-timeout 10000)
        => {:status  200
            :headers {"content-length" 2}
            :body    (io/string-input-stream "12")}))
    (fact "HTTP request to municipality WFS fails => download-and-store returns nil"
      (download-and-store-poytakirja! system app user ts {:type "verdict" :id verdict-id} true
                                      {:linkkiliitteeseen "http://foo.bar/paatosote123.pdf"})
      => nil
      (provided
        (domain/get-application-as "1234567890" user :include-canceled-apps? nil) => app
        (http/get "http://foo.bar/paatosote123.pdf" :as :stream :throw-exceptions false :conn-timeout 10000) => {:status 500}))
    (fact "included upon successful download-and-store"
      (get-poytakirja! system app user ts {:type "verdict" :id verdict-id} pk)
      => (contains {:urlHash       url-hash
                    :attachment-id "658c20b7edd5ac1696bbdcdd"})
      (provided
        (#'lupapalvelu.verdict-review-util/download-and-store-poytakirja!
          system app user ts
          {:type "verdict" :id verdict-id} true
          {:linkkiliitteeseen "http://foo.bar/paatosote123.pdf"})
        => "658c20b7edd5ac1696bbdcdd"))
    (fact "omitted upon failure"
      (:urlHash (get-poytakirja! system app user ts {:type "verdict" :id verdict-id} pk)) => nil
      (provided
        (#'lupapalvelu.verdict-review-util/download-and-store-poytakirja!
          system app user ts
          {:type "verdict" :id verdict-id} true
          {:linkkiliitteeseen "http://foo.bar/paatosote123.pdf"}) => nil))))
