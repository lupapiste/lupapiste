(ns lupapalvelu.xml.krysp.verdict-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.xml.krysp.verdict :refer :all]))

(facts "verdict mapping"
  (facts "by id"
    (verdict-id "lausunto")  => "33"
    (verdict-id "INVALID")   => nil)
  (facts "by name"
    (verdict-name "33")      => "lausunto"
    (verdict-name :33)       => "lausunto"
    (verdict-name 33)        => "lausunto"
    (verdict-name "INVALID") => nil))
