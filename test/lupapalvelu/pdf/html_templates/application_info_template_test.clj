(ns lupapalvelu.pdf.html-templates.application-info-template-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.pdf.html-templates.application-info-template :refer :all]))

(testable-privates lupapalvelu.pdf.html-templates.application-info-template get-handlers get-operations get-foremen)

(facts get-handlers
  (fact "one handler"
    (let [application {:handlers [{:name {:fi "K\u00e4sittelij\u00e4" :sv "Handlare" :en "Handler"}
                                   :firstName "Viran"
                                   :lastName  "Omainen"}]}]
      (get-handlers application "fi")) => ["Viran Omainen (K\u00e4sittelij\u00e4)"])
  (fact "empty handlers"
    (let [application {:handlers []}]
      (get-handlers application "fi")) => [])
  (fact "nil handlers"
    (let [application {:handlers nil}]
      (get-handlers application "fi")) => [])
  (fact "multiple handlers"
    (let [application {:handlers [{:name {:fi "K\u00e4sittelij\u00e4" :sv "Handlare" :en "Handler"}
                                   :firstName "Viran"
                                   :lastName  "Omainen"}
                                  {:name {:fi "Muu k\u00e4sittelij\u00e4" :sv "Andra handlare" :en "Other handler"}
                                   :firstName "Tarkas"
                                   :lastName  "Taja"}
                                  {:name {:fi "Yli k\u00e4sittelij\u00e4" :sv "\u00f6ver handlare" :en "\u00dcber handler"}
                                   :firstName "Arkki"
                                   :lastName  "Tehti"}]}]
      (get-handlers application "en")) => ["Viran Omainen (Handler)"
                                           "Tarkas Taja (Other handler)"
                                           "Arkki Tehti (\u00dcber handler)"]))


(facts get-operation-info
  (fact "with tunnus, description and accordion fields"
    (let [application {:documents [{:id ..doc-id..,
                                    :schema-info {:op {:id ..op-id.., :name "pientalo-laaj", :description nil},
                                                  :name "rakennuksen-laajentaminen",
                                                  :accordion-fields [[[ "valtakunnallinenNumero" ]]],
                                                  :version 1},
                                    :data {:valtakunnallinenNumero { :value "bld_123456" }
                                           :tunnus                 { :value "BID" } } }]}
          operation   {:id ..op-id.., :name "pientalo-laaj", :description "Some description for operation"}]
      (get-operation-info application "fi" operation)) => "Asuinpientalon laajentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo) - BID: Some description for operation - bld_123456")

  (fact "with tunnus"
    (let [application {:documents [{:id ..doc-id..,
                                    :schema-info {:op {:id ..op-id.., :name "pientalo-laaj", :description nil},
                                                  :name "rakennuksen-laajentaminen"},
                                    :data {:valtakunnallinenNumero { :value nil }
                                           :tunnus                 { :value "BID" } } }]}
          operation   {:id ..op-id.., :name "pientalo-laaj", :description nil}]
      (get-operation-info application "fi" operation)) => "Asuinpientalon laajentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo) - BID")

  (fact "with accordion fields - blank tunnus"
    (let [application {:documents [{:id ..doc-id..,
                                    :schema-info {:op {:id ..op-id.., :name "pientalo-laaj", :description nil},
                                                  :name "rakennuksen-laajentaminen",
                                                  :accordion-fields [[[ "valtakunnallinenNumero" ]]]},
                                    :data {:valtakunnallinenNumero { :value "bld_123456" }
                                           :tunnus                 { :value "" } } }]}
          operation   {:id ..op-id.., :name "pientalo-laaj", :description nil}]
      (get-operation-info application "fi" operation)) => "Asuinpientalon laajentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo) - bld_123456"))



(facts get-operations
  (fact "only primary operation"
    (let [application {:primaryOperation    {:id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                       :documents           [{:id ..doc-id1..,
                                              :schema-info {:op {:id ..sec2-op-id.., :name "pientalo-laaj", :description nil},
                                                            :name "rakennuksen-laajentaminen"}}
                                             {:id ..doc-id2..,
                                              :schema-info {:op { :id ..sec1-op-id.., :name "rakennuksen-laajentaminen", :description nil}
                                                            :name "uusiRakennus"}
                                              :data {:tunnus { :value "BID" } }}
                                             {:id ..doc-id3..,
                                              :schema-info {:op { :id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                                                            :name "uusiRakennus"}}]}]
      (get-operations application "fi")) => ["Asuinkerrostalon tai rivitalon rakentaminen"])

  (fact "no operations"
    (let [application {:primaryOperation    nil
                       :secondaryOperations []
                       :documents           [{:id ..doc-id1..,
                                              :schema-info {:op {:id ..sec2-op-id.., :name "pientalo-laaj", :description nil},
                                                            :name "rakennuksen-laajentaminen"}}
                                             {:id ..doc-id2..,
                                              :schema-info {:op { :id ..sec1-op-id.., :name "rakennuksen-laajentaminen", :description nil}
                                                            :name "uusiRakennus"}
                                              :data {:tunnus { :value "BID" } }}
                                             {:id ..doc-id3..,
                                              :schema-info {:op { :id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                                                            :name "uusiRakennus"}}]}]
      (get-operations application "fi")) => [])

  (fact "multiple handlers"
    (let [application {:primaryOperation    { :id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                       :secondaryOperations [{:id ..sec1-op-id.., :name "pientalo-laaj", :description "Vanha talo"}
                                             {:id ..sec2-op-id.., :name "markatilan-laajentaminen", :description "WC:n laajentaminen"} ]
                       :documents           [{:id ..doc-id1..,
                                              :schema-info {:op {:id ..sec2-op-id.., :name "pientalo-laaj", :description nil},
                                                            :name "rakennuksen-laajentaminen"}}
                                             {:id ..doc-id2..,
                                              :schema-info {:op { :id ..sec1-op-id.., :name "rakennuksen-laajentaminen", :description nil}
                                                            :name "uusiRakennus"}
                                              :data {:tunnus { :value "BID" } }}
                                             {:id ..doc-id3..,
                                              :schema-info {:op { :id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                                                            :name "uusiRakennus"}}]}]
      (get-operations application "fi")) => ["Asuinkerrostalon tai rivitalon rakentaminen"
                                             "Asuinpientalon laajentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo) - BID: Vanha talo"
                                             "M\u00e4rk\u00e4tilan muuttaminen tai laajentaminen - WC:n laajentaminen"]))

(facts get-foremen
  (fact "one foreman app"
    (get-foremen [{:documents [{:schema-info {:name "tyonjohtaja-v2"},
                                :data {:kuntaRoolikoodi {:value "erityisalojen ty\u00F6njohtaja"},
                                       :yritys {:yritysnimi {:value ""},
                                                :liikeJaYhteisoTunnus {:value ""}},
                                       :patevyys-tyonjohtaja {:koulutusvalinta {:value "rakennusmestari"},
                                                              :koulutus {:value ""},
                                                              :valmistumisvuosi {:value "1966"}, :kokemusvuodet {:value "50"}, :valvottavienKohteidenMaara {:value "13"}},
                                       :henkilotiedot {:etunimi {:value "Pena"}, :sukunimi {:value "Panaani"}, :hetu {:value "010203-040A"}}, :patevyysvaatimusluokka {:value "A"}}}]}]
                 "fi") => ["Pena Panaani (Erityisalojen ty\u00F6njohtaja)"])

  (fact "no foreman apps"
    (get-foremen [] "fi") => [])

  (fact "two foreman apps"
    (get-foremen [{:documents [{:schema-info {:name "tyonjohtaja-v2"},
                                :data {:kuntaRoolikoodi {:value "erityisalojen ty\u00F6njohtaja"},
                                       :yritys {:yritysnimi {:value ""},
                                                :liikeJaYhteisoTunnus {:value ""}},
                                       :patevyys-tyonjohtaja {:koulutusvalinta {:value "rakennusmestari"},
                                                              :koulutus {:value ""},
                                                              :valmistumisvuosi {:value "1966"}, :kokemusvuodet {:value "50"}, :valvottavienKohteidenMaara {:value "13"}},
                                       :henkilotiedot {:etunimi {:value "Pena"}, :sukunimi {:value "Panaani"}, :hetu {:value "010203-040A"}}, :patevyysvaatimusluokka {:value "A"}}}]}
                  {:documents [{:schema-info {:name "tyonjohtaja-v2"},
                                :data {:kuntaRoolikoodi {:value "IV-ty\u00F6njohtaja"},
                                       :yritys {:yritysnimi {:value ""},
                                                :liikeJaYhteisoTunnus {:value ""}},
                                       :patevyys-tyonjohtaja {:koulutusvalinta {:value "rakennusmestari"},
                                                              :koulutus {:value ""},
                                                              :valmistumisvuosi {:value "1966"}, :kokemusvuodet {:value "50"}, :valvottavienKohteidenMaara {:value "13"}},
                                       :henkilotiedot {:etunimi {:value "Ilkka"}, :sukunimi {:value "Ilmastoija"}, :hetu {:value "010266-010B"}}, :patevyysvaatimusluokka {:value "A"}}}]}]
                 "fi") => ["Pena Panaani (Erityisalojen ty\u00F6njohtaja)" "Ilkka Ilmastoija (IV-ty\u00F6njohtaja)"]))

(def application-info-snippet (enlive/snippet (enlive/html application-info-template)
                                              [:#application-info]
                                              [application foreman-apps lang]
                                              [:#application-info] (application-info-transformation application foreman-apps lang)))

(facts application-info-transformation
  (let [html (application-info-snippet {:id "LP-2002-111-00222"
                                        :municipality "111"
                                        :state "verdictGiven"
                                        :propertyId "123123"
                                        :submitted 1489528634011
                                        :address "Rakennuskuja 3"
                                        :_applicantIndex ["Harri Hakija" "Toni Toinenhakija"]
                                        :handlers [{:name {:fi "K\u00e4sittelij\u00e4" :sv "Handlare" :en "Handler"} :firstName "Viran" :lastName  "Omainen"}]
                                        :primaryOperation    { :id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                                        :documents           [{:id ..doc-id..,
                                                               :schema-info {:op { :id ..primary-op-id.., :name "kerrostalo-rivitalo", :description nil}
                                                                             :name "uusiRakennus"}}]}
                                       [{:documents [{:schema-info {:name "tyonjohtaja-v2"},
                                                      :data {:kuntaRoolikoodi {:value "IV-ty\u00F6njohtaja"},
                                                             :yritys {:yritysnimi {:value ""},
                                                                      :liikeJaYhteisoTunnus {:value ""}},
                                                             :patevyys-tyonjohtaja {:koulutusvalinta {:value "rakennusmestari"},
                                                                                    :koulutus {:value ""},
                                                                                    :valmistumisvuosi {:value "1966"}, :kokemusvuodet {:value "50"}, :valvottavienKohteidenMaara {:value "13"}},
                                                             :henkilotiedot {:etunimi {:value "Ilkka"}, :sukunimi {:value "Ilmastoija"}, :hetu {:value "010266-010B"}}, :patevyysvaatimusluokka {:value "A"}}}]}]
                                       "fi")]
    (fact "application id"
      (->> [:#id-title] (enlive/select html) (map :content)) => [["Asiointitunnus:"]]
      (->> [:#id-value] (enlive/select html) (map :content)) => [["LP-2002-111-00222"]])

    (fact "municipality"
      (->> [:#municipality-title] (enlive/select html) (map :content)) => [["Asiointikunta:"]]
      (->> [:#municipality-value] (enlive/select html) (map :content)) => [["Heinola"]])

    (fact "application state"
      (->> [:#state-title] (enlive/select html) (map :content)) => [["Tila:"]]
      (->> [:#state-value] (enlive/select html) (map :content)) => [["P\u00e4\u00e4t\u00f6s annettu"]])

    (fact "property id"
      (->> [:#property-title] (enlive/select html) (map :content)) => [["Kiinteist\u00f6tunnus:"]]
      (->> [:#property-value] (enlive/select html) (map :content)) => [["123-1-2-3"]])

    (fact "submitted date"
      (->> [:#submitted-title] (enlive/select html) (map :content)) => [["Hakemus j\u00e4tetty:"]]
      (->> [:#submitted-value] (enlive/select html) (map :content)) => [["14.03.2017"]])

    (fact "address"
      (->> [:#address-title] (enlive/select html) (map :content)) => [["Hankkeen osoite"]]
      (->> [:#address-value] (enlive/select html) (map :content)) => [["Rakennuskuja 3"]])

    (fact "applicants"
      (->> [:#applicant-title] (enlive/select html) (map :content)) => [["Hakija"]]
      (->> [:#applicant-value] (enlive/select html) (map :content)) => [[{:attrs {}, :content ["Harri Hakija"], :tag :div}
                                                                         {:attrs {}, :content ["Toni Toinenhakija"], :tag :div}]])

    (fact "handlers"
      (->> [:#handlers-title] (enlive/select html) (map :content)) => [["K\u00e4sittelij\u00e4(t):"]]
      (->> [:#handlers-value] (enlive/select html) (map :content)) => [[{:attrs {}, :content ["Viran Omainen (K\u00e4sittelij\u00e4)"], :tag :div}]])

    (fact "foremen"
      (->> [:#foremen-title] (enlive/select html) (map :content)) => [["Kaikki ty\u00F6njohtajat"]]
      (->> [:#foremen-value] (enlive/select html) (map :content)) => [[{:attrs {}, :content ["Ilkka Ilmastoija (IV-ty\u00F6njohtaja)"], :tag :div}]])

    (fact "operations"
      (->> [:#operations-title] (enlive/select html) (map :content)) => [["Toimenpiteet"]]
      (->> [:#operations-value] (enlive/select html) (map :content)) => [[{:attrs {}, :content ["Asuinkerrostalon tai rivitalon rakentaminen"], :tag :div}]])))
