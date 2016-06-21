(ns lupapalvelu.document.subtype-test
  (:use [lupapalvelu.document.subtype]
        [midje.sweet]))

(facts "Facts about generic subtype validation"
  (fact (subtype-validation {} "what-ever") => nil?)
  (fact (subtype-validation {:subtype :foobar} "what-ever") => [:err "illegal-subtype"]))

(facts "Facts about email validation"
  (fact (subtype-validation {:subtype :email} "") => nil?)
  (fact (subtype-validation {:subtype :email} "a") => [:warn "illegal-email"])
  (fact (subtype-validation {:subtype :email} "a@b.") => [:warn "illegal-email"])
  (fact (subtype-validation {:subtype :email} "a@b.c") => nil?))

(facts "Facts about tel validation"
  (fact (subtype-validation {:subtype :tel} "") => nil?)
  (fact (subtype-validation {:subtype :tel} "fozzaa") => [:warn "illegal-tel"])
  (fact (subtype-validation {:subtype :tel} "1+2") => [:warn "illegal-tel"])
  (fact (subtype-validation {:subtype :tel} "1") => nil?)
  (fact (subtype-validation {:subtype :tel} "123-456") => nil?)
  (fact (subtype-validation {:subtype :tel} "+358-123 456") => nil?))

(facts "Facts about number validation"
  (fact (subtype-validation {:subtype :number} "123") => nil?)
  (fact (subtype-validation {:subtype :number} "-123") => nil?)
  (fact (subtype-validation {:subtype :number} "001") => nil?)
  (fact (subtype-validation {:subtype :number} "1.02") => [:warn "illegal-number"])
  (fact (subtype-validation {:subtype :number} "abc") => [:warn "illegal-number"])
  (fact (subtype-validation {:subtype :number} " 123 ") => [:warn "illegal-number"])
  (fact "with min and max"
    (fact (subtype-validation {:subtype :number :min -1 :max 12} "-2") => [:warn "illegal-number:too-small"])
    (fact (subtype-validation {:subtype :number :min -1 :max 12} "-1") => nil?)
    (fact (subtype-validation {:subtype :number :min -1 :max 12} "12") => nil?)
    (fact (subtype-validation {:subtype :number :min -1 :max 12} "13") => [:warn "illegal-number:too-big"])))

(facts "Recent year validation"
  (facts (subtype-validation {:subtype :recent-year :range 10} "9000") => [:warn "illegal-recent-year:future"])
  (facts (subtype-validation {:subtype :recent-year :range 10} "2000") => [:warn "illegal-recent-year:too-past"])
  (facts (subtype-validation {:subtype :recent-year :range 10} "x2000") => [:warn "illegal-recent-year:not-integer"])
  (facts (subtype-validation {:subtype :recent-year :range 10} "2000x") => [:warn "illegal-recent-year:not-integer"])
  (facts (subtype-validation {:subtype :recent-year :range 10} "0x2000") => [:warn "illegal-recent-year:not-integer"])
  (facts (subtype-validation {:subtype :recent-year :range 10} "02016") => [:warn "illegal-recent-year:not-integer"])
  (facts (subtype-validation {:subtype :recent-year :range 10} "2016") => nil?)
  (facts (subtype-validation {:subtype :recent-year :range 15} "2011") => nil?))


(facts "Facts about decimal validation"
  (fact (subtype-validation {:subtype :decimal} "0")    => nil?)
  (fact (subtype-validation {:subtype :decimal} "0,0")  => nil?)
  (fact (subtype-validation {:subtype :decimal} "-1,0") => nil?)
  (fact (subtype-validation {:subtype :decimal} "1,0")  => nil?)
  (fact (subtype-validation {:subtype :decimal} "abc")  => [:warn "illegal-decimal"])
  (facts "with min and max"
    (fact (subtype-validation {:subtype :decimal :min -1.1 :max 1.1} "0")    => nil?)
    (fact (subtype-validation {:subtype :decimal :min -1.1 :max 1.1} "-1,1") => nil?)
    (fact (subtype-validation {:subtype :decimal :min -1.1 :max 1.1} "1,1")  => nil?)
    (fact (subtype-validation {:subtype :decimal :min -1.1 :max 1.1} "-1,2") => [:warn "illegal-decimal:too-small"])
    (fact (subtype-validation {:subtype :decimal :min -1.1 :max 1.1} "1,2")  => [:warn "illegal-decimal:too-big"])))

(facts "Facts about letter validation"
  (subtype-validation {:subtype :letter} "a") => nil?
  (subtype-validation {:subtype :letter} "A") => nil?
  (subtype-validation {:subtype :letter} "\u00e4") => nil?
  (subtype-validation {:subtype :letter} "1") => [:warn "illegal-letter:any"]
  (subtype-validation {:subtype :letter} "@") => [:warn "illegal-letter:any"]
  (fact "with upper & lower case definitions"
    (subtype-validation {:subtype :letter :case :upper} "A") => nil?
    (subtype-validation {:subtype :letter :case :upper} "a") => [:warn "illegal-letter:upper"]
    (subtype-validation {:subtype :letter :case :lower} "a") => nil?
    (subtype-validation {:subtype :letter :case :lower} "A") => [:warn "illegal-letter:lower"]))

;; Subtype zip is not currently used. See subtype definitions for details.
;; (facts "Facts about zip validation"
;;   (fact (subtype-validation {:subtype :zip} "") => nil?)
;;   (fact (subtype-validation {:subtype :zip} "33800") => nil?)
;;   (fact (subtype-validation {:subtype :zip} "123") => [:warn "illegal-zip"]))

(facts "VRK compliant name validation"
  (subtype-validation {:subtype :vrk-name} "") => nil?
  (subtype-validation {:subtype :vrk-name} "Matti M\u00e4kinen") => nil?
  (subtype-validation {:subtype :vrk-name} "Juha-Matti M\u00e4kinen") => nil?
  (subtype-validation {:subtype :vrk-name} "Juha/Matti M\u00e4kinen") => nil?
  (subtype-validation {:subtype :vrk-name} "Juha *Matti* M\u00e4kinen") => nil?
  (subtype-validation {:subtype :vrk-name} "Pertti \"Veltto\" Virtanen") => [:warn "illegal-name"]
  (subtype-validation {:subtype :vrk-name} "Carl the 16th Gustav") => [:warn "illegal-name"]
  (subtype-validation {:subtype :vrk-name} "Carl XVI Gustav") => nil?)

(facts "VRK compliant address validation"
  (subtype-validation {:subtype :vrk-address} "") => nil?
  (subtype-validation {:subtype :vrk-address} "\u00e4h\u00e4kutti74: ()-/ &.,:*") => nil?
  (subtype-validation {:subtype :vrk-address} "Suur-\"Halli\" 66") => [:warn "illegal-address"])

(facts "y-tunnus validation"
  (subtype-validation {:subtype :y-tunnus} "") => nil?
  (subtype-validation {:subtype :y-tunnus} "2341528-4") => nil?
  (subtype-validation {:subtype :y-tunnus} "2341528-1") => [:warn "illegal-y-tunnus"])

(facts "Pysyva rakennustunnus"
  (subtype-validation {:subtype :rakennustunnus} "") => nil?
  (subtype-validation {:subtype :rakennustunnus} nil) => nil?
  (subtype-validation {:subtype :rakennustunnus} "100012345N") => nil?
  (subtype-validation {:subtype :rakennustunnus} "1234567892") => nil?
  (subtype-validation {:subtype :rakennustunnus} "23456789A") => [:warn "illegal-rakennustunnus"])
