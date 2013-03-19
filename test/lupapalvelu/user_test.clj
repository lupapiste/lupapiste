(ns lupapalvelu.user-test
  (:use lupapalvelu.user
        midje.sweet))

(facts
  (applicationpage-for "applicant")      => "applicant"
  (applicationpage-for "authority")      => "authority"
  (applicationpage-for "authorityAdmin") => "authority-admin"
  (applicationpage-for "admin")          => "admin")
