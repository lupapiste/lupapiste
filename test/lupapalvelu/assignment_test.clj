(ns lupapalvelu.assignment-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.assignment :refer :all]
            [sade.util :as util]))


(def test-doc
  {:id "123",
   :schema-info {:group-help "hakija.group.help",
                 :approvable true,
                 :name "hakija-r",
                 :section-help "party.section.help",
                 :i18name "osapuoli",
                 :type "party",
                 :accordion-fields [["_selected"]
                                    ["henkilo" "henkilotiedot" "etunimi"]
                                    ["henkilo" "henkilotiedot" "sukunimi"]
                                    ["yritys" "yritysnimi"]],
                 :after-update "applicant-index-update",
                 :repeating true,
                 :order 3,
                 :removable true,
                 :deny-removing-last-document true,
                 :version 1,
                 :subtype "hakija"},
   :data {:_selected {:value "yritys", :modified 1458471343392},
          :henkilo {:userId {:value nil},
                    :henkilotiedot {:etunimi {:value "Pena"},
                                    :sukunimi {:value "Panaani"},
                                    :hetu {:value nil},
                                    :turvakieltoKytkin {:value false}},
                    :osoite {:katu {:value ""},
                             :postinumero {:value ""},
                             :postitoimipaikannimi {:value ""},
                             :maa {:value "FIN"}},
                    :yhteystiedot {:puhelin {:value ""}, :email {:value ""}},
                    :kytkimet {:suoramarkkinointilupa {:value false}, :vainsahkoinenAsiointiKytkin {:value false}}},
          :yritys {:companyId {:value nil},
                   :yritysnimi {:value "Firma 5", :modified 1458471382290},
                   :liikeJaYhteisoTunnus {:value "", :modified 1463122443139},
                   :osoite {:katu {:value "", :modified 1463122781506},
                            :postinumero {:value "", :modified 1463122804052},
                            :postitoimipaikannimi {:value "", :modified 1458471398754},
                            :maa {:value "FIN"}},
                   :yhteyshenkilo {:henkilotiedot {:etunimi {:value "", :modified 1458471402935},
                                                   :sukunimi {:value "", :modified 1458471408515},
                                                   :turvakieltoKytkin {:value false}},
                                   :yhteystiedot {:puhelin {:value "", :modified 1458471417860},
                                                  :email {:value "", :modified 1458471435086}},
                                   :kytkimet {:suoramarkkinointilupa {:value false},
                                              :vainsahkoinenAsiointiKytkin {:value false}}}}}}
  )

(facts "document display text"
  (fact "yritys"
    (display-text-for-document test-doc "fi") => (contains "Firma 5"))
  (fact "no accordion fields"
    (display-text-for-document (util/dissoc-in test-doc [:schema-info :accordion-fields]) "fi") => "Hakija (hankkeeseen ryhtyv\u00e4)"))
