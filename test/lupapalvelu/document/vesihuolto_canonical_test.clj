(ns lupapalvelu.document.vesihuolto-canonical-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.document.canonical-test-common :as ctc]
            [lupapalvelu.document.vesihuolto-canonical :as vc]))

(def talousvedet {:id "532bddd0da068d67611f92f1",
                  :created 1395383760912,
                  :schema-info {:order 4,
                                :version 1,
                                :repeating false,
                                :name "talousvedet",
                                :approvable false,
                                :op {:id "532bddd0da068d67611f92f0",
                                     :name "vvvl-vesijohdosta-ja-viemarista",
                                     :created 1395383760912}}
                  :data {:hankinta {:modified 1395388002250, :value "Kiinteist\u00f6n porakaivosta"},
                         :johdatus {:modified 1395388005092,
                                    :value "johdetaan paineellisena vesijohtoa pitkin rakennukseen"},
                         :riittavyys {:modified 1395388008290,
                                      :value "vesi riitt\u00e4\u00e4 talouden tarpeisiin"}}})

(def kuvaus {:id "532bddd0da068d67611f92f3",
             :created 1395383760912,
             :schema-info {:approvable true,
                           :name "hankkeen-kuvaus-vesihuolto",
                           :version 1,
                           :order 1}
             :data {:kuvaus {:modified 1395383870539,
                             :value
                             "Uudehko talo, jonka rakentamisen yhteydess\u00e4 tehty porakaivo ja pienpuhdistamo, josta j\u00e4tevedet johdetaan pois pohjaveden valuma-alueeta."}}})

(def rakennukset {:id "532bddd0da068d67611f92f4",
                  :created 1395383760912,
                  :schema-info {:approvable true,
                                :name "vesihuolto-kiinteisto",
                                :repeating false,
                                :version 1,
                                :order 2}
                  :data {:kiinteisto {:maapintaala {:modified 1395383760912, :value "62.4191"},
                                      :rekisterointipvm {:modified 1395383760912, :value "11.04.2012"},
                                      :tilanNimi
                                      {:modified 1395383760912, :value "Sibbo Skyttegille r.f."},
                                      :vesipintaala {:modified 1395383760912, :value ""}},
                         :kiinteistoonKuuluu {:0 {:kohteenVarustelutaso {:Astianpesukone {:modified 1395383879113, :value true},
                                                                         :Lamminvesivaraaja {:modified 1395383880724, :value true},
                                                                         :Pyykinpesukone {:modified 1395383879901, :value true},
                                                                         :Suihku {:modified 1395383877618, :value true},
                                                                         :Tiskiallas {:modified 1395383878315, :value true},
                                                                         :WC {:modified 1395383882309, :value true}},
                                                  :rakennuksenTyypi {:modified 1395383871947, :value "Asuinrakennus"},
                                                  :rakennusvuosi {:modified 1395383875066, :value "2008"},
                                                  :vapautus {:modified 1395383875159, :value true}},
                                              :1 {:rakennuksenTyypi {:modified 1395383901694, :value "ei tiedossa"},
                                                  :rakennusvuosi {:modified 1395383910696, :value "2010"}},
                                              :2 {:kohteenVarustelutaso {:Kuivakaymala {:modified 1395383932868, :value true},
                                                                         :Suihku {:modified 1395383934961, :value false}},
                                                  :rakennuksenTyypi {:modified 1395383915531, :value "Saunarakennus"},
                                                  :rakennusvuosi {:modified 1395383924600, :value "2013"}}}}})

(def viemari {:id "532bddd0da068d67611f92f5",
              :created 1395383760912,
              :data {:kuvaus {:modified 1395383983695,
                              :value "Labkon biokem 6 panospudistamo, josta k\u00e4siteltu j\u00e4tevesi johdetaan pois pohjaveden valuma-alueelta."}},
              :schema-info {:approvable false,
                            :name "jatevedet",
                            :repeating false,
                            :version 1,
                            :order 5}})

(def vapautus-vesijohdosta-ja-viemarista-hakemus {:sent nil,
                                                  :neighbors [],
                                                  :schema-version 1,
                                                  :handlers [{:lastName "Borga",
                                                               :firstName "Pekka",
                                                               :general true}],
                                                  :auth [{:lastName "Borga",
                                                          :firstName "Pekka",
                                                          :username "pekka",
                                                          :role "writer",
                                                          :id "777777777777777777000033"}],
                                                  :drawings [],
                                                  :submitted 1395388008290,
                                                  :state "submitted",
                                                  :permitSubtype nil,
                                                  :tasks [],
                                                  :closedBy {},
                                                  :_verdicts-seen-by {},
                                                  :location [410169.875, 6692360.0],
                                                  :attachments [],
                                                  :statements ctc/statements,
                                                  :organization "753-R",
                                                  :buildings [],
                                                  :title "Ampumaradantie 113",
                                                  :address "Ampumaradantie 113",
                                                  :started nil,
                                                  :closed nil,
                                                  :primaryOperation {:id "532bddd0da068d67611f92f0",
                                                                     :name "vvvl-vesijohdosta-ja-viemarista",
                                                                     :created 1395383760912},
                                                  :secondaryOperations [],
                                                  :infoRequest false,
                                                  :openInfoRequest false,
                                                  :opened 1395388008290,
                                                  :created 1395383760912,
                                                  :_comments-seen-by {},
                                                  :propertyId "75342300020226",
                                                  :verdicts [],
                                                  :startedBy {},
                                                  :documents [ctc/henkilohakija
                                                              ctc/yrityshakija
                                                              talousvedet
                                                              kuvaus
                                                              rakennukset
                                                              viemari],
                                                  :_statements-seen-by {},
                                                  :modified 1395384005343,
                                                  :comments [],
                                                  :permitType "VVVL",
                                                  :id "LP-753-2014-00001",
                                                  :municipality "753"})

(ctc/validate-all-documents vapautus-vesijohdosta-ja-viemarista-hakemus)

(fl/facts* "Vesijohto ja viemari"
  (let [canonical (vc/vapautus-canonical vapautus-vesijohdosta-ja-viemarista-hakemus "fi") => truthy
        Vesihuoltolaki (:Vesihuoltolaki canonical) => truthy
        _ (:toimituksenTiedot Vesihuoltolaki) => truthy
        vapautukset (:vapautukset Vesihuoltolaki) => truthy
        Vapautus (:Vapautus vapautukset) => truthy
        kasittelytietotieto (:kasittelytietotieto Vapautus) => truthy
        Kasittelytieto (:KasittelyTieto kasittelytietotieto) => truthy
        _ (:muutosHetki Kasittelytieto) => "2014-03-21T06:40:05"
        _ (:hakemuksenTila Kasittelytieto) => "1 Vireill\u00e4"
        _ (:kasittelija Kasittelytieto) => {:henkilo {:nimi {:etunimi "Pekka", :sukunimi "Borga"}}}

        luvanTunnistetiedot (:luvanTunnistetiedot Vapautus) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        _ (:tunnus MuuTunnus) => (:id vapautus-vesijohdosta-ja-viemarista-hakemus)
        _ (:sovellus MuuTunnus) => "Lupapiste"

        lausuntotieto (:lausuntotieto Vapautus) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        _ (:viranomainen Lausunto) => "Paloviranomainen"
        _ (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        _ (:viranomainen annettu-lausunto) => "Paloviranomainen"
        _ (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        _ (:lausuntoPvm annettu-lausunto) => "2013-09-17"

        _ (:vapautusperuste Vapautus) => "" ;xml pit\u00e4\u00e4 saada tyhja vapautusperuste elementti
        vapautushakemustieto (:vapautushakemustieto Vapautus) => truthy
        Vapautushakemus (:Vapautushakemus vapautushakemustieto) => truthy
        _ (:haetaan Vapautushakemus) => nil
        ;; Only one applicant (hakija) is included
        _ (:hakija Vapautushakemus) => (just [(contains {:etunimi "Pekka"
                                                         :sukunimi "Borga"
                                                         :henkilotunnus "210281-9988"})])
        kohde (:kohde Vapautushakemus) => truthy

        _ (:kiinteistorekisteritunnus kohde) => "75342300020226"
        kiinteistonRakennusTieto (:kiinteistonRakennusTieto kohde) => truthy
        _ (count kiinteistonRakennusTieto) => 3
        kr1 (nth kiinteistonRakennusTieto 0) => truthy
        KiinteistonRakennus (:KiinteistonRakennus kr1) => truthy
        kayttotarkoitustieto (:kayttotarkoitustieto KiinteistonRakennus) => truthy
        _ (:kayttotarkoitus kayttotarkoitustieto) => "asuinrakennus"
        kohteenVarustelutaso (:kohteenVarustelutaso KiinteistonRakennus) => truthy
        _ (count kohteenVarustelutaso) => 6
        vss (set kohteenVarustelutaso)
        _ (vss "Astianpesukone") => truthy
        _ (vss "L\u00e4mminvesivaraaja") => truthy
        _ (vss "Pyykinpesukone") => truthy
        _ (vss "Suihku") => truthy
        _ (vss "Tiskiallas") => truthy
        _ (vss "WC(vesik\u00e4ym\u00e4l\u00e4)") => truthy
        _ (:haetaanVapautustaKytkin KiinteistonRakennus) => true?

        kr2 (nth kiinteistonRakennusTieto 1) => truthy
        KiinteistonRakennus (:KiinteistonRakennus kr2) => truthy
        kayttotarkoitustieto (:kayttotarkoitustieto KiinteistonRakennus) => truthy
        _ (:kayttotarkoitus kayttotarkoitustieto) => "ei tiedossa"
        _ (:kohteenVarustelutaso KiinteistonRakennus) => nil
        _ (:haetaanVapautustaKytkin KiinteistonRakennus) => false?

        kr3 (nth kiinteistonRakennusTieto 2) => truthy
        KiinteistonRakennus (:KiinteistonRakennus kr3) => truthy
        kayttotarkoitustieto (:kayttotarkoitustieto KiinteistonRakennus) => truthy
        _ (:kayttotarkoitus kayttotarkoitustieto) => "saunarakennus"
        kohteenVarustelutaso (:kohteenVarustelutaso KiinteistonRakennus) => truthy
        _ (count kohteenVarustelutaso) => 1
        vss (set kohteenVarustelutaso)
        _ (vss "Kuivak\u00e4ym\u00e4l\u00e4") => truthy
        _ (vss "Suihku") => nil
        _ (:haetaanVapautustaKytkin KiinteistonRakennus) => false
        sijaintitieto (:sijaintitieto Vapautushakemus) => truthy
        s1 (first sijaintitieto) => truthy
        _ (:Sijainti s1) => truthy
        _ (:asianKuvaus Vapautus) => "Vapautus vesijohtoon ja j\u00e4tevesiviem\u00e4riin liittymisest\u00e4 (VHL 11 \u00a7) / Uudehko talo, jonka rakentamisen yhteydess\u00e4 tehty porakaivo ja pienpuhdistamo, josta j\u00e4tevedet johdetaan pois pohjaveden valuma-alueeta."

        talousvedet (:talousvedet kohde) => truthy
        hankinta (:hankinta talousvedet) => truthy
        _ (:hankinta hankinta) => "kiinteist\u00f6n porakaivosta"

        _ (:jatevedet kohde) => "Labkon biokem 6 panospudistamo, josta k\u00e4siteltu j\u00e4tevesi johdetaan pois pohjaveden valuma-alueelta."
        ]))
