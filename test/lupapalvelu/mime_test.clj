(ns lupapalvelu.mime-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.mime :refer :all]))

(facts "Facts about mime-type"
  (fact (mime-type "foo.exe") => "application/x-msdownload")
  (fact (mime-type "foo.bar") => nil?)
  (fact (mime-type "foo.doc") => "application/msword")
  (fact (mime-type "foo.pdf") => "application/pdf"))

(facts "Test mime.types parser"
  (fact (mime-types "pdf") => "application/pdf")
  (fact (mime-types "jpeg") => "image/jpeg")
  (fact (mime-types "jpg") => "image/jpeg"))

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

  (fact (allowed-file? "virus.exe")    => falsey)
  (fact (allowed-file? "virus.bat")    => falsey)
  (fact (allowed-file? "virus.sh")     => falsey)
  (fact (allowed-file? "virus.")       => falsey)
  (fact (allowed-file? "foo.bar")      => falsey)
  (fact (allowed-file? "")             => falsey)
  (fact (allowed-file? nil)            => falsey))

(facts "sanitize-filename"
  (sanitize-filename nil)                 => nil
  (sanitize-filename "")                  => ""
  (sanitize-filename "foo")               => "foo"
  (sanitize-filename "foo/bar")           => "bar"
  (sanitize-filename "foo\\bar")          => "bar"
  (sanitize-filename "a\\b/c\\d/r//bar")  => "bar")
