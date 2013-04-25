(ns lupapalvelu.xml.krysp.verdict-test
  (:use [lupapalvelu.xml.krysp.verdict]
        [midje.sweet]))

(facts "verdict mapping"
  (facts "by id"
    (verdict-id "lausunto")  => "33"
    (verdict-id "INVALID")   => nil)
  (facts "by name"
    (verdict-name "33")      => "lausunto"
    (verdict-name :33)       => "lausunto"
    (verdict-name 33)        => "lausunto"
    (verdict-name "INVALID") => nil))
