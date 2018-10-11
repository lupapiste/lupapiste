(ns lupapalvelu.ident.ad-login_test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.ident.ad-login :refer :all]))

(def saml-info
  {:before {:assertions (list {:attrs {"displayName" (list "Iina Tuttifrutti")
                                       "email" (list "iina.tuttifrutti@pori.fi")
                                       "firstName" (list "Iina")
                                       "groups" (list "authority"
                                                      "archivist"
                                                      "writer")
                                       "lastName" (list "Tuttifrutti")
                                       "mobilePhone" (list "050 1234 578")
                                       "organization" (list "609-R")}
                               :audiences (list "http://www.lupapiste.fi/api/saml/ad-login/609-R")
                               :confirmation {:in-response-to "f8ea3430-c7b0-11e8-afbb-5795e0ed89a3"
                                              :not-before nil
                                              :not-on-or-after #inst "2018-10-04T09:39:15.470-00:00"
                                              :recipient "http://www.lupapiste.fi/api/saml/ad-login/609-R"}
                               :name-id {:format "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"
                                         :value "iina.tuttifrutti@pori.fi"}})
            :destination "http://www.lupapiste.fi/api/saml/ad-login/609-R"
            :inResponseTo "f8ea3430-c7b0-11e8-afbb-5795e0ed89a3"
            :issueInstant #inst "2018-10-04T08:39:15.000-00:00"
            :status "urn:oasis:names:tc:SAML:2.0:status:Success"
            :success? true
            :version "2.0"}
   :after {:assertions {:attrs {:displayName "Iina Tuttifrutti"
                                :email "iina.tuttifrutti@pori.fi"
                                :firstName "Iina"
                                :groups ["authority" "archivist" "writer"]
                                :lastName "Tuttifrutti"
                                :mobilePhone "050 1234 578"
                                :organization "609-R"}
                        :audiences "http://www.lupapiste.fi/api/saml/ad-login/609-R"
                        :confirmation {:in-response-to "f8ea3430-c7b0-11e8-afbb-5795e0ed89a3"
                                       :not-before nil
                                       :not-on-or-after #inst "2018-10-04T09:39:15.470-00:00"
                                       :recipient "http://www.lupapiste.fi/api/saml/ad-login/609-R"}
                        :name-id {:format "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"
                                  :value "iina.tuttifrutti@pori.fi"}}
           :destination "http://www.lupapiste.fi/api/saml/ad-login/609-R"
           :inResponseTo "f8ea3430-c7b0-11e8-afbb-5795e0ed89a3"
           :issueInstant #inst "2018-10-04T08:39:15.000-00:00"
           :status "urn:oasis:names:tc:SAML:2.0:status:Success"
           :success? true
   :version "2.0"}})

(def nested-garbage-map
  {:eka-taso (list {:toka-taso "Ah ma nauran"
                    "jeejee" (list "kun kuvani näin kauniina")
                    ":kolmas-taso" (list {:jee "peilistäin nääään"
                                        :howdy (list {:dadaa "Öhöhö"
                                                      :viihii (list {:right "on"})})})})})

(facts "Relevant Lupapiste roles can be deducted from the SAML payload & ad-login settings"
  (let [ad-params ["GG_Lupapiste_RAVA_Kommentointi" "Jeejee" "Kikka"]
        org-roles {:commenter "GG_Lupapiste_RAVA_Kommentointi"
                   :writer "writer"}]
    (fact "Commenter role is resolved"
      (resolve-roles org-roles ad-params) => #{"commenter"})
    (facts "If the matching role is removed, an empty set is returned"
      (resolve-roles (dissoc org-roles :commenter) ad-params) => #{}
      (resolve-roles org-roles (rest ad-params)) => #{})))

(facts "parse-saml-info converts the unwieldy SAML response into a friendly Clojure map"
  (fact "SAML response map parsing works as expected"
    (let [{:keys [before after]} saml-info]
      (parse-saml-info before) => after))
  (fact "Response parsing works for deeply nested maps"
    (let [parsed-and-flattened-map (->> nested-garbage-map
                                        parse-saml-info
                                        (into {} (mapcat (partial tree-seq (comp map? val) val))))]
      (not-any? list? (vals parsed-and-flattened-map)) => true
      (every? keyword? (keys parsed-and-flattened-map)) => true)))
