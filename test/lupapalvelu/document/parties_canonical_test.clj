(ns lupapalvelu.document.parties-canonical-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.document.parties-canonical :refer :all]
            [sade.schema-generators :as ssg]
            [sade.strings :as ss]))

(testable-privates lupapalvelu.document.parties-canonical party-doc-to-canonical)

(def paasuunnittelija-schema      (dds/doc-data-schema "paasuunnittelija" true))
(def hankkeen-kuvaus-data-schema  (dds/doc-data-schema "hankkeen-kuvaus"))
(def hakija-r-data-schema         (dds/doc-data-schema "hakija-r" true))
(def suunnittelija-schema         (dds/doc-data-schema "suunnittelija" true))

(def docs [(ssg/generate hankkeen-kuvaus-data-schema)
           (ssg/generate hakija-r-data-schema)
           (ssg/generate paasuunnittelija-schema)
           (ssg/generate suunnittelija-schema)])

(def application {:id "LP-753-2016-90006",
                  :applicant "Panaani Pena",
                  :propertyId "75341600550007",
                  :documents docs
                  :authority {:username "sonja"
                              :firstName "Sonja"
                              :lastName "Sibbo"}
                  :primaryOperation {:id "5829761bf63a8012e88685a5",
                                     :name "kerrostalo-rivitalo",
                                     :description "GASD",
                                     :created 1479112219619},
                  :buildings [{:description "Talo A, Toinen selite",
                               :localShortId "101",
                               :buildingId "123456001M",
                               :index "1",
                               :created "2013",
                               :localId nil,
                               :usage "039 muut asuinkerrostalot",
                               :nationalId "123456001M",
                               :area "2000",
                               :propertyId "18601234567890",
                               :operationId "abcdefghijklmnopqr"}]
                  :attachments [],
                  :schema-version 1,
                  :_applicantIndex ["Panaani Pena"],
                  :infoRequest false,
                  :history [{:state "draft",
                             :ts 1479112219619,
                             :user {:id "777777777777777777000020",
                                    :username "pena",
                                    :firstName "Pena",
                                    :lastName "Panaani",
                                    :role "applicant"}}
                            {:state "open",
                             :ts 1479128758866,
                             :user {:id "777777777777777777000020",
                                    :username "pena",
                                    :firstName "Pena",
                                    :lastName "Panaani",
                                    :role "applicant"}}
                            {:state "submitted",
                             :ts 1479128758866,
                             :user {:id "777777777777777777000020",
                                    :username "pena",
                                    :firstName "Pena",
                                    :lastName "Panaani",
                                    :role "applicant"}}
                            {:state "sent",
                             :ts 1479129349977,
                             :user {:id "777777777777777777000023",
                                    :username "sonja",
                                    :firstName "Sonja",
                                    :lastName "Sibbo",
                                    :role "authority"}}
                            {:state "verdictGiven",
                             :ts 1479129355521,
                             :user {:id "777777777777777777000023",
                                    :username "sonja",
                                    :firstName "Sonja",
                                    :lastName "Sibbo",
                                    :role "authority"}}],
                  :created 1479112219619,
                  :municipality "753",
                  :state "verdictGiven",
                  :opened 1479128758866,
                  :permitType "R",
                  :organization "753-R",
                  :modified 1479368415240,
                  :sent 1479129349977,
                  :submitted 1479128758866,
                  :title "Latokuja 3",
                  :address "Latokuja 3",
                  :tasks [{:schema-info {:name "task-katselmus",
                                         :type "task",
                                         :subtype "review",
                                         :order 1,
                                         :section-help "authority-fills",
                                         :i18nprefix "task-katselmus.katselmuksenLaji",
                                         :user-authz-roles [],
                                         :version 1},
                           :closed nil,
                           :created 1479129355521,
                           :state "sent",
                           :source {:type "verdict", :id "5829b90bf63a8034c04f148f"},
                           :id "5829b90df63a8034c04f149e",
                           :assignee {:id "777777777777777777000020", :firstName "Pena", :lastName "Panaani"},
                           :duedate nil,
                           :taskname "Aloituskokous",
                           :data {:katselmuksenLaji {:value "aloituskokous", :modified 1479129355521},
                                  :vaadittuLupaehtona {:value true, :modified 1479129355521},
                                  :rakennus {:0 {:rakennus {:jarjestysnumero {:value "1", :modified 1479129355521},
                                                            :kiinttun {:value "18601234567890", :modified 1479129355521},
                                                            :rakennusnro {:value "101", :modified 1479129355521},
                                                            :valtakunnallinenNumero {:value "123456001M", :modified 1479129355521},
                                                            :kunnanSisainenPysyvaRakennusnumero {:value nil, :modified 1479129355521}},
                                                 :tila {:tila {:value "lopullinen", :modified 1479129380969},
                                                        :kayttoonottava {:value false, :modified 1479129355521}}},
                                             :1 {:rakennus {:jarjestysnumero {:value "2", :modified 1479129355521},
                                                            :kiinttun {:value "18601234567891", :modified 1479129355521},
                                                            :rakennusnro {:value "102", :modified 1479129355521},
                                                            :valtakunnallinenNumero {:value "123456002N", :modified 1479129355521},
                                                            :kunnanSisainenPysyvaRakennusnumero {:value nil, :modified 1479129355521}},
                                                 :tila {:tila {:value "", :modified 1479129355521},
                                                        :kayttoonottava {:value false, :modified 1479129355521}}},
                                             :2 {:rakennus {:jarjestysnumero {:value "3", :modified 1479129355521},
                                                            :kiinttun {:value "18601234567892", :modified 1479129355521},
                                                            :rakennusnro {:value "103", :modified 1479129355521},
                                                            :valtakunnallinenNumero {:value nil, :modified 1479129355521},
                                                            :kunnanSisainenPysyvaRakennusnumero {:value nil, :modified 1479129355521}},
                                                 :tila {:tila {:value "", :modified 1479129355521},
                                                        :kayttoonottava {:value false, :modified 1479129355521}}}},
                                  :katselmus {:tila {:value "lopullinen", :modified 1479129366687},
                                              :pitoPvm {:value "14.11.2016", :modified 1479129368282},
                                              :pitaja {:value "MIN\u00c4", :modified 1479129370001},
                                              :tiedoksianto {:value false},
                                              :huomautukset {:kuvaus {:value "", :modified 1479129387040},
                                                             :maaraAika {:value nil},
                                                             :toteaja {:value ""},
                                                             :toteamisHetki {:value nil}},
                                              :lasnaolijat {:value "Herkko", :modified 1479129393098},
                                              :poikkeamat {:value "", :modified 1479129355521}},
                                  :muuTunnus {:value "", :modified 1479129355521}}}]
                  :verdicts [{:kuntalupatunnus "13-0185-R",
                              :paatokset [{:lupamaaraykset {:autopaikkojaRakennettu 0,
                                                            :autopaikkojaKiinteistolla 7,
                                                            :autopaikkojaEnintaan 10,
                                                            :autopaikkojaUlkopuolella 3,
                                                            :autopaikkojaVahintaan 1,
                                                            :vaaditutTyonjohtajat "Vastaava ty\u00f6njohtaja, Vastaava IV-ty\u00f6njohtaja, Ty\u00f6njohtaja",
                                                            :autopaikkojaRakennettava 2,
                                                            :vaaditutKatselmukset [{:katselmuksenLaji "aloituskokous",
                                                                                    :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}
                                                                                   {:katselmuksenLaji "muu tarkastus",
                                                                                    :tarkastuksenTaiKatselmuksenNimi "K\u00e4ytt\u00f6\u00f6nottotarkastus"}],
                                                            :maaraykset [{:maaraysaika 1377648000000,
                                                                          :sisalto "Radontekninen suunnitelma",
                                                                          :toteutusHetki 1377734400000}
                                                                         {:maaraysaika 1377820800000,
                                                                          :sisalto "Ilmanvaihtosuunnitelma",
                                                                          :toteutusHetki 1377907200000}],
                                                            :kokonaisala "110",
                                                            :kerrosala "100"},
                                           :paivamaarat {:aloitettava 1377993600000,
                                                         :lainvoimainen 1378080000000,
                                                         :voimassaHetki 1378166400000,
                                                         :raukeamis 1378252800000,
                                                         :anto 1378339200000,
                                                         :viimeinenValitus 1378425600000,
                                                         :julkipano 1378512000000},
                                           :poytakirjat [{:paatoskoodi "my\u00f6nnetty",
                                                          :paatospvm 1375315200000,
                                                          :pykala 1,
                                                          :paatoksentekija "viranomainen",
                                                          :paatos "P\u00e4\u00e4t\u00f6s 1",
                                                          :status "1",
                                                          :urlHash "236d9b2cfff88098d4f8ad532820c9fb93393237"}
                                                         {:paatoskoodi "ehdollinen",
                                                          :paatospvm 1375401600000,
                                                          :pykala 2,
                                                          :paatoksentekija "M\u00f6lli Keinonen",
                                                          :paatos "Mieleni minun tekevi, aivoni ajattelevi l\u00e4hte\u00e4ni laulamahan, saa'ani sanelemahan, sukuvirtt\u00e4 suoltamahan, lajivirtt\u00e4 laulamahan. Sanat suussani sulavat, puhe'et putoelevat, kielelleni kerki\u00e4v\u00e4t, hampahilleni hajoovat.

                                                  Veli kulta, veikkoseni, kaunis kasvinkumppalini! Lahe nyt kanssa laulamahan, saa kera sanelemahan yhtehen yhyttyamme, kahta'alta kaytyamme! Harvoin yhtehen yhymme, saamme toinen toisihimme nailla raukoilla rajoilla, poloisilla Pohjan mailla.

                                                  Ly\u00f6k\u00e4mme k\u00e4si k\u00e4tehen, sormet sormien lomahan, lauloaksemme hyvi\u00e4, parahia pannaksemme, kuulla noien kultaisien, tiet\u00e4 mielitehtoisien, nuorisossa nousevassa, kansassa kasuavassa: noita saamia sanoja, virsi\u00e4 viritt\u00e4mi\u00e4 vy\u00f6lt\u00e4 vanhan V\u00e4in\u00e4m\u00f6isen, alta ahjon Ilmarisen, p\u00e4\u00e4st\u00e4 kalvan Kaukomielen, Joukahaisen jousen tiest\u00e4, Pohjan peltojen perilt\u00e4, Kalevalan kankahilta.",
                                                          :status "6",
                                                          :urlHash "b55ae9c30533428bd9965a84106fb163611c1a7d"}],
                                           :id "5829b90bf63a8034c04f1492"}
                                          {:lupamaaraykset {:vaaditutKatselmukset [{:katselmuksenLaji "loppukatselmus"}],
                                                            :maaraykset [{:sisalto "Valaistussuunnitelma"}]},
                                           :paivamaarat {:anto 1378339200000},
                                           :poytakirjat [{:paatoskoodi "my\u00f6nnetty",
                                                          :paatospvm 1377993600000,
                                                          :paatoksentekija "johtava viranomainen",
                                                          :paatos "P\u00e4\u00e4t\u00f6s 2",
                                                          :status "1"}],
                                           :id "5829b90df63a8034c04f149d"}],
                              :id "5829b90bf63a8034c04f148f",
                              :timestamp 1479129355521}],
                  :location-wgs84 [25.266 60.36938],
                  :location [404369.304 6693806.957]
                  :statements []})


(facts "suunnittelija canonical"
  (let [canonical          (party-doc-to-canonical (doc-tools/unwrapped application) "fi" (doc-tools/unwrapped (docs 2)))
        asia               (get-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia])
        krysp-element-keys (keys asia)
        ;; TODO: Use or remove:
        valid-names        (->> (:documents application)
                                (filter (fn [{info :schema-info}]
                                          (= (:subtype info) :suunnittelija)))
                                (remove (fn [{data :data}]  ; Canonical skips designers without sukunimi
                                          (ss/blank? (get-in data [:henkilotiedot :sukunimi :value])))))]
    canonical => map?
    (fact "no toimenpide or rakennuspaikka information"
      krysp-element-keys => (just [:osapuolettieto
                                   :kasittelynTilatieto
                                   :luvanTunnisteTiedot
                                   :viitelupatieto
                                   :kayttotapaus
                                   :asianTiedot
                                   :lisatiedot] :in-any-order))
    (fact "kayttotapaus"
      (get asia :kayttotapaus) => "Uuden suunnittelijan nime\u00e4minen")

    (fact "only one osapuoli: suunnittelija as one suunnittelijatieto"
      (keys (get-in asia [:osapuolettieto :Osapuolet])) => [:suunnittelijatieto]
      (mapcat keys (get-in asia [:osapuolettieto :Osapuolet :suunnittelijatieto])) => [:Suunnittelija])))

(facts "hakija canonical"
  (let [canonical          (party-doc-to-canonical (doc-tools/unwrapped application) "fi" (doc-tools/unwrapped (docs 1)))
        asia               (get-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia])
        krysp-element-keys (keys asia)
        ;; TODO: Use or remove:
        valid-names        (->> (:documents application)
                                (filter (fn [{info :schema-info}]
                                          (= (:subtype info) :suunnittelija)))
                                (remove (fn [{data :data}]  ; Canonical skips designers without sukunimi
                                          (ss/blank? (get-in data [:henkilotiedot :sukunimi :value])))))]
    canonical => map?
    (fact "no toimenpide or rakennuspaikka information"
      krysp-element-keys => (just [:osapuolettieto
                                   :kasittelynTilatieto
                                   :luvanTunnisteTiedot
                                   :viitelupatieto
                                   :kayttotapaus
                                   :asianTiedot
                                   :lisatiedot] :in-any-order))

    (fact "kayttotapaus"
      (get asia :kayttotapaus) => "Hakijatietojen muuttaminen")

    (fact "only hakija doc is included"
      (keys (get-in asia [:osapuolettieto :Osapuolet])) => [:osapuolitieto]
      (mapcat keys (get-in asia [:osapuolettieto :Osapuolet :osapuolitieto])) => [:Osapuoli])))

(facts "non party canonical - no method in multimethod"
  (party-doc-to-canonical (doc-tools/unwrapped application) "fi" (doc-tools/unwrapped (docs 0))) => (throws java.lang.IllegalArgumentException))
