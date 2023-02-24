(ns lupapalvelu.mime-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.mime :refer :all]))

(facts "Facts about mime-type"
  (fact (mime-type "foo.exe") => "application/x-dosexec")
  (fact (mime-type "foo.bar") => "application/octet-stream")
  (fact (mime-type "foo.doc") => "application/msword")
  (fact (mime-type "foo.pdf") => "application/pdf")
  (fact (mime-type "foo.ifc") => "application/x-step")
  (fact (mime-type "foo-bar.png") => "image/png")
  (fact (mime-type "foo#bar.png") => "image/png")
  (fact (mime-type "#foo#bar#.png") => "image/png")
  (fact (mime-type "##.png") => "image/png")
  (fact (mime-type "#") => "application/octet-stream"))

(facts "Test mime.types parser"
  (fact (mime-types "pdf") => "application/pdf")
  (fact (mime-types "jpeg") => "image/jpeg")
  (fact (mime-types "jpg") => "image/jpeg")
  (fact (mime-types "ifc") => "application/x-step"))

(facts "Facts about allowed-file?"
  (fact (allowed-file? "foo.pdf")      => truthy)
  (fact (allowed-file? "FOO.PDF")      => truthy)
  (fact (allowed-file? "plan.dwg")     => truthy)
  (fact (allowed-file? "plan.pic")     => truthy)
  (fact (allowed-file? "word.doc")     => truthy)
  (fact (allowed-file? "word.docx")    => truthy)
  (fact (allowed-file? "excel.xls")    => truthy)
  (fact (allowed-file? "excel.xlsx")   => truthy)
  (fact (allowed-file? "text.ods")     => truthy)
  (fact (allowed-file? "plan.zip")     => truthy)
  (fact (allowed-file? "plan.7z")      => truthy)
  (fact (allowed-file? "cad.ifc")      => truthy)

  (fact (allowed-file? "virus.exe")    => falsey)
  (fact (allowed-file? "virus.bat")    => falsey)
  (fact (allowed-file? "virus.sh")     => falsey)
  (fact (allowed-file? "virus.")       => falsey)
  (fact (allowed-file? "foo.bar")      => falsey)
  (fact (allowed-file? "")             => falsey)
  (fact (allowed-file? nil)            => falsey)

  (fact (allowed-file? "foo-bar.png")    => truthy)
  (fact (allowed-file? "foo#bar.png")    => truthy)
  (fact (allowed-file? "#foo#bar#.png")  => truthy)
  (fact (allowed-file? "##.png")         => truthy)
  (fact (allowed-file? "#")              => falsey))


(let [u      (char 776) ;; umlaut
      normed "Älämölö"
      other  (str "A" u "la" u "mo" u "lo" u)]
  (facts "sanitize-filename"
   (sanitize-filename nil)                 => nil
   (sanitize-filename "")                  => ""
   (sanitize-filename "foo")               => "foo"
   (sanitize-filename "foo/bar")           => "bar"
   (sanitize-filename "foo\\bar")          => "bar"
   (sanitize-filename "a\\b/c\\d/r//bar")  => "bar"
   (sanitize-filename normed)              => normed
   (sanitize-filename other)               => normed))
