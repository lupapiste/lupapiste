(ns lupapalvelu.ident.ad-login_test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.ident.ad-login :refer :all]))

(facts "Relevant Lupapiste roles can be deducted from the SAML payload & ad-login settings"
  (let [ad-params ["GG_Lupapiste_RAVA_Kommentointi" "Jeejee" "Kikka"]
        org-roles {:commenter "GG_Lupapiste_RAVA_Kommentointi"
                   :writer "writer"}]
    (fact "Commenter role is resolved"
      (resolve-roles org-roles ad-params) => #{"commenter"})
    (facts "If the matching role is removed, an empty set is returned"
      (resolve-roles (dissoc org-roles :commenter) ad-params) => #{}
      (resolve-roles org-roles (rest ad-params)) => #{})))

