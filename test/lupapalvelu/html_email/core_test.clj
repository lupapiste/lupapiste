(ns lupapalvelu.html-email.core_test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.html-email.core
                   make-to-field)

(facts make-to-field
  (make-to-field nil) => nil
  (make-to-field "bad email") => nil
  (make-to-field " tRoLl@eXaMpLe.cOm ") => nil
  (provided (sade.email/blacklisted? "troll@example.com") => true)
  (make-to-field " gOOd@exAMPle.net ") => "good@example.net"
  (make-to-field " gO<<Od@ex>>AM,Pl,e.net ") => "good@example.net"
  (make-to-field {:email " TROLL@EXAMPLE.com "}) => nil
  (provided (sade.email/blacklisted? "troll@example.com") => true)
  (make-to-field {:email " TRO,L,L@EX<AMP><LE.com "}) => nil
  (provided (sade.email/blacklisted? "troll@example.com") => true)
  (make-to-field {:email " <gOOd@exAMPle.net> "}) => "good@example.net"
  (make-to-field {:email     " gOOd@exAMPle.net "
                  :firstName "  <Ra,ndy>  "}) => "Randy <good@example.net>"
  (make-to-field {:email    " gOOd@exAMPle.net "
                  :lastName "  R,a<nd>om  "}) => "Random <good@example.net>"
  (make-to-field {:email     " gOOd@exAMPle.net "
                  :firstName "  <Randy>  "
                  :lastName  " >Random< "}) => "Randy Random <good@example.net>")
