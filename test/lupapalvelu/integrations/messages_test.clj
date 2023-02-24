(ns lupapalvelu.integrations.messages_test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.messages :refer [partner-for-permit-type]]
            ))


(facts partner-for-permit-type
  (partner-for-permit-type {} :R) => nil
  (partner-for-permit-type {} :R "dmcity") => "dmcity"
  (partner-for-permit-type {:krysp {:R {:http {:partner "matti"}}}} :R "dmcity") => "matti"
  (partner-for-permit-type {:krysp {:R {:http {:partner "matti"}}}} :P "dmcity") => "dmcity"

  (facts "Bad data or params"
    (partner-for-permit-type {:krysp {:R {:http {:partner "bad"}}}} :R)
    => (throws Exception)
    (partner-for-permit-type {:krysp {:R {:http {:partner "dmcity"}}}} :R "bad")
    => (throws Exception)
    (partner-for-permit-type {:krysp {:R {:http {:partner "dmcity"}}}} nil)
    => (throws Exception)))
