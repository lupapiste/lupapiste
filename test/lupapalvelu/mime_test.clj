(ns lupapalvelu.mime-test
  (:use [lupapalvelu.mime]
        [midje.sweet]))

(facts "Facts about mime-type"
  (fact (mime-type "foo.exe") => "application/x-msdownload")
  (fact (mime-type "foo.bar") => nil?) ; TODO: Should be application/octet-stream?
  (fact (mime-type "foo.doc") => "application/msword")
  (fact (mime-type "foo.pdf") => "application/pdf"))

(facts "Facts about allowed-file?"
  (fact (allowed-file? "foo.exe") => falsey)
  (fact (allowed-file? "foo.bar") => falsey)
  (fact (allowed-file? "foo.doc") => truthy)
  (fact (allowed-file? "foo.pdf") => truthy))
