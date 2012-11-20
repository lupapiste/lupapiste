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