(ns lupapalvelu.ryhti.transform-test-util
  (:require [sade.util :as util]))

(def legacy-application
  {:_applicantIndex             ["Panaani Pena"]
   :_attachment_indicator_reset nil
   :_comments-seen-by           {}
   :_creatorIndex               "Pena Panaani"
   :_statements-seen-by         {}
   :_verdicts-seen-by           {}
   :acknowledged                nil
   :address                     "Transformer"
   :agreementPrepared           nil
   :agreementSigned             nil
   :appealVerdicts              []
   :appeals                     []
   :applicant                   "Panaani Pena"
   :archived                    {:application nil :completed nil :initial nil}
   :attachments                 [{:applicationState     "draft"
                                  :auth                 []
                                  :backendId            nil
                                  :contents             nil
                                  :forPrinting          false
                                  :groupType            "operation"
                                  :id                   "696e09a500ef524b6d633ede"
                                  :locked               false
                                  :modified             1768819108962
                                  :notNeeded            false
                                  :op                   [{:id   "696e09a500ef524b6d633edb"
                                                          :name "kerrostalo-rivitalo"}]
                                  :readOnly             false
                                  :requestedByAuthority false
                                  :required             true
                                  :signatures           []
                                  :target               nil
                                  :type                 {:type-group "paapiirustus"
                                                         :type-id    "asemapiirros"}
                                  :versions             []}
                                 {:applicationState     "draft"
                                  :auth                 []
                                  :backendId            nil
                                  :contents             nil
                                  :forPrinting          false
                                  :groupType            "operation"
                                  :id                   "696e09a500ef524b6d633edf"
                                  :locked               false
                                  :modified             1768819108962
                                  :notNeeded            false
                                  :op                   [{:id   "696e09a500ef524b6d633edb"
                                                          :name "kerrostalo-rivitalo"}]
                                  :readOnly             false
                                  :requestedByAuthority false
                                  :required             true
                                  :signatures           []
                                  :target               nil
                                  :type                 {:type-group "paapiirustus"
                                                         :type-id    "pohjapiirustus"}
                                  :versions             []}
                                 {:applicationState     "draft"
                                  :auth                 []
                                  :backendId            nil
                                  :contents             nil
                                  :forPrinting          false
                                  :groupType            "parties"
                                  :id                   "696e09a500ef524b6d633ee0"
                                  :locked               false
                                  :modified             1768819108962
                                  :notNeeded            false
                                  :op                   nil
                                  :readOnly             false
                                  :requestedByAuthority false
                                  :required             true
                                  :signatures           []
                                  :target               nil
                                  :type                 {:type-group "hakija" :type-id "valtakirja"}
                                  :versions             []}
                                 {:applicationState     "draft"
                                  :auth                 []
                                  :backendId            nil
                                  :contents             nil
                                  :forPrinting          false
                                  :groupType            "operation"
                                  :id                   "696e09a500ef524b6d633ee1"
                                  :locked               false
                                  :modified             1768819108962
                                  :notNeeded            false
                                  :op                   [{:id   "696e09a500ef524b6d633edb"
                                                          :name "kerrostalo-rivitalo"}]
                                  :readOnly             false
                                  :requestedByAuthority false
                                  :required             true
                                  :signatures           []
                                  :target               nil
                                  :type                 {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat"
                                                         :type-id    "vaestonsuojasuunnitelma"}
                                  :versions             []}
                                 {:applicationState     "draft"
                                  :auth                 []
                                  :backendId            nil
                                  :contents             nil
                                  :forPrinting          false
                                  :id                   "696f9ad8044a512d714aca35"
                                  :locked               false
                                  :modified             1768921816270
                                  :notNeeded            false
                                  :op                   nil
                                  :readOnly             false
                                  :requestedByAuthority true
                                  :required             true
                                  :signatures           []
                                  :target               nil
                                  :type                 {:type-group "tietomallien_liitteet"
                                                         :type-id    "tarkastustulos"}
                                  :versions             []}
                                 {:applicationState     "draft"
                                  :auth                 []
                                  :backendId            nil
                                  :contents             nil
                                  :forPrinting          false
                                  :groupType            "reports"
                                  :id                   "696f9ad8044a512d714aca36"
                                  :locked               false
                                  :modified             1768921816270
                                  :notNeeded            false
                                  :op                   nil
                                  :readOnly             false
                                  :requestedByAuthority true
                                  :required             true
                                  :signatures           []
                                  :target               nil
                                  :type                 {:type-group "selvitykset"
                                                         :type-id    "selvitys_liittymisesta_ymparoivaan_rakennuskantaan"}
                                  :versions             []}]
   :auth                        [{:firstName    "Pena"
                                  :id           "777777777777777777000020"
                                  :lastName     "Panaani"
                                  :role         "writer"
                                  :unsubscribed false
                                  :username     "pena"}
                                 {:company-role "admin"
                                  :firstName    "Esimerkki Oy"
                                  :id           "esimerkki"
                                  :invite       {:created 1768821958888
                                                 :role    "writer"
                                                 :user    {:id "esimerkki"}}
                                  :inviter      {:firstName "Pena"
                                                 :id        "777777777777777777000020"
                                                 :lastName  "Panaani"
                                                 :role      "applicant"
                                                 :username  "pena"}
                                  :lastName     ""
                                  :name         "Esimerkki Oy"
                                  :role         "reader"
                                  :type         "company"
                                  :username     "7208863-8"
                                  :y            "7208863-8"}]
   :authorityNotice             ""
   :building-product-catalogs   [{:building-ref "696e09a500ef524b6d633edc"
                                  :created      1768819370208
                                  :data         {:laatija             {:value ""}
                                                 :sisaltyvat-tuotteet {:alapohja                {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :hormit                  {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :jako-osat               {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :julkisivut              {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :kattorakenteet          {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :ovet-ja-ikkunat         {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :runko                   {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :tilapinnat              {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :tilavarusteet           {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :ulkotasot-ja-parvekkeet {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}}}
                                  :id           "696e0aaa00ef524b6d633ee9"
                                  :schema-info  {:name    "rakennustuoteluettelo"
                                                 :type    "building-product-catalog"
                                                 :version 1}}
                                 {:building-ref "696e09a500ef524b6d633ee5"
                                  :created      1768819370208
                                  :data         {:laatija             {:value ""}
                                                 :sisaltyvat-tuotteet {:alueen-rakenteet {:0 {:maara   {:value ""}
                                                                                              :tuote   {:value ""}
                                                                                              :yksikko {:value "kpl"}}}
                                                                       :paallysteet      {:0 {:maara   {:value ""}
                                                                                              :tuote   {:value ""}
                                                                                              :yksikko {:value "kpl"}}}
                                                                       :perustukset      {:0 {:maara   {:value ""}
                                                                                              :tuote   {:value ""}
                                                                                              :yksikko {:value "kpl"}}}
                                                                       :tuennat          {:0 {:maara   {:value ""}
                                                                                              :tuote   {:value ""}
                                                                                              :yksikko {:value "kpl"}}}}}
                                  :id           "696e0aaa00ef524b6d633eea"
                                  :schema-info  {:name    "rakennustuoteluettelo-rakennuspaikka"
                                                 :type    "building-product-catalog"
                                                 :version 1}}
                                 {:building-ref "696e119700ef524b6d633eed"
                                  :created      1768821454581
                                  :data         {:laatija             {:value ""}
                                                 :sisaltyvat-tuotteet {:alapohja                {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :hormit                  {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :jako-osat               {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :julkisivut              {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :kattorakenteet          {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :ovet-ja-ikkunat         {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :runko                   {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :tilapinnat              {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :tilavarusteet           {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}
                                                                       :ulkotasot-ja-parvekkeet {:0 {:maara   {:value ""}
                                                                                                     :tuote   {:value ""}
                                                                                                     :yksikko {:value "kpl"}}}}}
                                  :id           "696e12ce00ef524b6d633ef0"
                                  :schema-info  {:name    "rakennustuoteluettelo"
                                                 :type    "building-product-catalog"
                                                 :version 1}}]
   :buildings                   []
   :bulletinOpDescription       nil
   :closed                      nil
   :closedBy                    {}
   :comments                    [{:created 1769162318170
                                  :roles   ["applicant" "authority"]
                                  :target  {:type "application"}
                                  :text    "Hi."
                                  :to      nil
                                  :type    "applicant"
                                  :user    {:firstName "Pena"
                                            :id        "777777777777777777000020"
                                            :lastName  "Panaani"
                                            :role      "applicant"
                                            :username  "pena"}}]
   :complementNeeded            nil
   :convertedToApplication      nil
   :created                     1768819108962
   :creator                     {:firstName "Pena"
                                 :id        "777777777777777777000020"
                                 :lastName  "Panaani"
                                 :role      "applicant"
                                 :username  "pena"}
   :document-buildings          [{:building {:huoneistot                         {:0 {:WCKytkin                true
                                                                                      :ammeTaiSuihkuKytkin     true
                                                                                      :huoneistoTyyppi         "asuinhuoneisto"
                                                                                      :huoneistoala            "108"
                                                                                      :huoneistonumero         "001"
                                                                                      :huoneluku               "4"
                                                                                      :keittionTyyppi          "keittio"
                                                                                      :lamminvesiKytkin        true
                                                                                      :parvekeTaiTerassiKytkin true
                                                                                      :porras                  "A"
                                                                                      :pysyvaHuoneistotunnus   "1234567890"
                                                                                      :saunaKytkin             true}
                                                                                  :1 {:WCKytkin                true
                                                                                      :ammeTaiSuihkuKytkin     true
                                                                                      :huoneistoTyyppi         "asuinhuoneisto"
                                                                                      :huoneistoala            "106"
                                                                                      :huoneistonumero         "002"
                                                                                      :huoneluku               "4"
                                                                                      :keittionTyyppi          "keittio"
                                                                                      :lamminvesiKytkin        true
                                                                                      :parvekeTaiTerassiKytkin true
                                                                                      :porras                  "A"
                                                                                      :saunaKytkin             true}}
                                             :kaytto                             {:esteetonKytkin             false
                                                                                  :kayttotarkoitus            "021 rivitalot"
                                                                                  :tilapainenRakennusKytkin   false
                                                                                  :valiaikainenRakennusKytkin false}
                                             :kiinttun                           "75300301050006"
                                             :kunnanSisainenPysyvaRakennusnumero ""
                                             :lammitys                           {:lammitystapa    "vesikeskus"
                                                                                  :lammonlahde     "kevyt polttoöljy"
                                                                                  :muu-lammonlahde ""}
                                             :luokitus                           {:energiatehokkuusluku         ""
                                                                                  :energiatehokkuusluvunYksikko "kWh/m2"}
                                             :manuaalinen_rakennusnro            ""
                                             :mitat                              {:harjanKorkeusrakennusalalla    ""
                                                                                  :kellarinpinta-ala              ""
                                                                                  :kerrosala                      "281"
                                                                                  :kerrosluku                     "2"
                                                                                  :kokonaisala                    "281"
                                                                                  :rakennusoikeudellinenKerrosala ""
                                                                                  :rakennusoikeusKuutiometreina   ""
                                                                                  :rakennusoikeusValinta          "kerrosala"
                                                                                  :tilavuus                       "837"}
                                             :osoite                             {:huoneisto            ""
                                                                                  :jakokirjain          ""
                                                                                  :jakokirjain2         ""
                                                                                  :kunta                "245"
                                                                                  :lahiosoite           "Kyllikintie"
                                                                                  :maa                  "FIN"
                                                                                  :osoitenumero         "3"
                                                                                  :osoitenumero2        "5"
                                                                                  :porras               ""
                                                                                  :postinumero          "04200"
                                                                                  :postitoimipaikannimi "KERAVA"}
                                             :rakenne                            {:julkisivu           "tiili"
                                                                                  :kantavaRakennusaine "tiili"
                                                                                  :muuMateriaali       ""
                                                                                  :muuRakennusaine     ""
                                                                                  :rakentamistapa      "paikalla"}
                                             :rakennuksenOmistajat               {:0 {:_selected        "yritys"
                                                                                      :henkilo          {:henkilotiedot {:etunimi                 ""
                                                                                                                         :not-finnish-hetu        false
                                                                                                                         :sukunimi                ""
                                                                                                                         :turvakieltoKytkin       false
                                                                                                                         :ulkomainenHenkilotunnus ""}
                                                                                                         :kytkimet      {:suoramarkkinointilupa false}
                                                                                                         :osoite        {:katu                 ""
                                                                                                                         :maa                  "FIN"
                                                                                                                         :postinumero          ""
                                                                                                                         :postitoimipaikannimi ""}
                                                                                                         :yhteystiedot  {:email   ""
                                                                                                                         :puhelin ""}}
                                                                                      :muu-omistajalaji ""
                                                                                      :yritys           {:liikeJaYhteisoTunnus "1234567-1"
                                                                                                         :osoite               {:katu                 "Testikatu 1 A 9242"
                                                                                                                                :maa                  "FIN"
                                                                                                                                :postinumero          "00380"
                                                                                                                                :postitoimipaikannimi "HELSINKI"}
                                                                                                         :yhteyshenkilo        {:henkilotiedot {:etunimi           ""
                                                                                                                                                :sukunimi          ""
                                                                                                                                                :turvakieltoKytkin false}
                                                                                                                                :yhteystiedot  {:email   ""
                                                                                                                                                :puhelin ""}}
                                                                                                         :yritysnimi           "Testiyritys 9242"}}
                                                                                  :1 {:_selected "yritys"
                                                                                      :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                                                                  :osoite               {:katu                 "Testikatu 1 A 9240"
                                                                                                                         :postinumero          "00380"
                                                                                                                         :postitoimipaikannimi "HELSINKI"}
                                                                                                  :yritysnimi           "Testiyritys 9240"}}
                                                                                  :2 {:_selected "yritys"
                                                                                      :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                                                                  :osoite               {:katu                 "Testikatu 1 A 9241"
                                                                                                                         :postinumero          "00380"
                                                                                                                         :postitoimipaikannimi "HELSINKI"}
                                                                                                  :yritysnimi           "Testiyritys 9241"}}
                                                                                  :3 {:_selected "yritys"
                                                                                      :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                                                                  :osoite               {:katu                 "Testikatu 1 A 9239"
                                                                                                                         :postinumero          "00380"
                                                                                                                         :postitoimipaikannimi "HELSINKI"}
                                                                                                  :yritysnimi           "Testiyritys 9239"}}}
                                             :rakennusnro                        "002"
                                             :valtakunnallinenNumero             "199887766E"
                                             :varusteet                          {:aurinkopaneeliKytkin                false
                                                                                  :hissiKytkin                         false
                                                                                  :kaasuKytkin                         false
                                                                                  :kokoontumistilanHenkilomaara        ""
                                                                                  :koneellinenilmastointiKytkin        true
                                                                                  :lamminvesiKytkin                    true
                                                                                  :liitettyJatevesijarjestelmaanKytkin false
                                                                                  :sahkoKytkin                         true
                                                                                  :saunoja                             ""
                                                                                  :vaestonsuoja                        ""
                                                                                  :vesijohtoKytkin                     true
                                                                                  :viemariKytkin                       true}
                                             :verkostoliittymat                  {:kaapeliKytkin   false
                                                                                  :maakaasuKytkin  false
                                                                                  :sahkoKytkin     true
                                                                                  :vesijohtoKytkin true
                                                                                  :viemariKytkin   true}}
                                  :created  1768821186494
                                  :id       "696e11c200ef524b6d633eef"
                                  :vtj-prt  "199887766E"}
                                 {:building {:huoneistot                         {:0  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "521"
                                                                                       :huoneistonumero         "001"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :1  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "86"
                                                                                       :huoneistonumero         "002"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :10 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "86"
                                                                                       :huoneistonumero         "011"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :11 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "52"
                                                                                       :huoneistonumero         "012"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :12 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "77"
                                                                                       :huoneistonumero         "013"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :13 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "77"
                                                                                       :huoneistonumero         "014"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :14 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "52"
                                                                                       :huoneistonumero         "015"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :15 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "86"
                                                                                       :huoneistonumero         "016"
                                                                                       :huoneluku               "3"
                                                                                       :jakokirjain             "a"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :16 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "52"
                                                                                       :huoneistonumero         "017"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :17 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "77"
                                                                                       :huoneistonumero         "018"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :18 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "1145"
                                                                                       :huoneistonumero         "019"
                                                                                       :huoneluku               "4"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :19 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "775"
                                                                                       :huoneistonumero         "020"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :2  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "80"
                                                                                       :huoneistonumero         "003"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :20 {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "1145"
                                                                                       :huoneistonumero         "021"
                                                                                       :huoneluku               "4"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :3  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "77"
                                                                                       :huoneistonumero         "004"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :4  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "52"
                                                                                       :huoneistonumero         "005"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :5  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "86"
                                                                                       :huoneistonumero         "006"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :6  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "52"
                                                                                       :huoneistonumero         "007"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :7  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "77"
                                                                                       :huoneistonumero         "008"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :8  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "77"
                                                                                       :huoneistonumero         "009"
                                                                                       :huoneluku               "3"
                                                                                       :keittionTyyppi          "keittio"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}
                                                                                  :9  {:WCKytkin                true
                                                                                       :ammeTaiSuihkuKytkin     true
                                                                                       :huoneistoTyyppi         "asuinhuoneisto"
                                                                                       :huoneistoala            "52"
                                                                                       :huoneistonumero         "010"
                                                                                       :huoneluku               "2"
                                                                                       :keittionTyyppi          "keittokomero"
                                                                                       :lamminvesiKytkin        true
                                                                                       :parvekeTaiTerassiKytkin true
                                                                                       :porras                  "A"
                                                                                       :saunaKytkin             true}}
                                             :kaytto                             {:esteetonKytkin                false
                                                                                  :kayttotarkoitus               "039 muut asuinkerrostalot"
                                                                                  :rakennusluokka                "0110 omakotitalot"
                                                                                  :rakentajaTyyppi               "muu"
                                                                                  :tilapainenRakennusKytkin      true
                                                                                  :tilapainenRakennusvoimassaPvm "10.10.2005"
                                                                                  :valiaikainenRakennusKytkin    false}
                                             :kiinttun                           "75300301050006"
                                             :kunnanSisainenPysyvaRakennusnumero ""
                                             :lammitys                           {:lammitystapa    "vesikeskus"
                                                                                  :lammonlahde     "kauko tai aluelämpö"
                                                                                  :muu-lammonlahde ""}
                                             :luokitus                           {:energialuokka                "10"
                                                                                  :energiatehokkuusluku         ""
                                                                                  :energiatehokkuusluvunYksikko "kWh/m2"
                                                                                  :paloluokka                   "P1 / P2"}
                                             :manuaalinen_rakennusnro            ""
                                             :mitat                              {:harjanKorkeusrakennusalalla    ""
                                                                                  :kellarinpinta-ala              "100"
                                                                                  :kerrosala                      "1785"
                                                                                  :kerrosluku                     "5"
                                                                                  :kokonaisala                    "2682"
                                                                                  :rakennusoikeudellinenKerrosala ""
                                                                                  :rakennusoikeusKuutiometreina   ""
                                                                                  :rakennusoikeusValinta          "kerrosala"
                                                                                  :tilavuus                       "8240"}
                                             :osoite                             {:huoneisto            "99"
                                                                                  :jakokirjain          "a"
                                                                                  :jakokirjain2         "b"
                                                                                  :kunta                "245"
                                                                                  :lahiosoite           "Vehkalantie"
                                                                                  :maa                  "FIN"
                                                                                  :osoitenumero         "2"
                                                                                  :osoitenumero2        "3"
                                                                                  :porras               "G"
                                                                                  :postinumero          "04200"
                                                                                  :postitoimipaikannimi "KERAVA"}
                                             :rakenne                            {:julkisivu           "betoni"
                                                                                  :kantavaRakennusaine "betoni"
                                                                                  :muuMateriaali       ""
                                                                                  :muuRakennusaine     ""
                                                                                  :rakentamistapa      "elementti"}
                                             :rakennuksenOmistajat               {:0 {:_selected        "yritys"
                                                                                      :henkilo          {:henkilotiedot {:etunimi                 ""
                                                                                                                         :not-finnish-hetu        false
                                                                                                                         :sukunimi                ""
                                                                                                                         :turvakieltoKytkin       false
                                                                                                                         :ulkomainenHenkilotunnus ""}
                                                                                                         :kytkimet      {:suoramarkkinointilupa false}
                                                                                                         :osoite        {:katu                 ""
                                                                                                                         :maa                  "FIN"
                                                                                                                         :postinumero          ""
                                                                                                                         :postitoimipaikannimi ""}
                                                                                                         :yhteystiedot  {:email   ""
                                                                                                                         :puhelin ""}}
                                                                                      :muu-omistajalaji ""
                                                                                      :yritys           {:liikeJaYhteisoTunnus "1234567-1"
                                                                                                         :osoite               {:katu                 "Testikatu 1 A 11477"
                                                                                                                                :maa                  "FIN"
                                                                                                                                :postinumero          "00380"
                                                                                                                                :postitoimipaikannimi "HELSINKI"}
                                                                                                         :yhteyshenkilo        {:henkilotiedot {:etunimi           ""
                                                                                                                                                :sukunimi          ""
                                                                                                                                                :turvakieltoKytkin false}
                                                                                                                                :yhteystiedot  {:email   ""
                                                                                                                                                :puhelin ""}}
                                                                                                         :yritysnimi           "Testiyritys 11477"}}
                                                                                  :1 {:_selected "yritys"
                                                                                      :yritys    {:liikeJaYhteisoTunnus "1234567-1"
                                                                                                  :osoite               {:katu                 "Testikatu 1 A 11476"
                                                                                                                         :postinumero          "00380"
                                                                                                                         :postitoimipaikannimi "HELSINKI"}
                                                                                                  :yritysnimi           "Testiyritys 11476"}}}
                                             :rakennusnro                        "001"
                                             :valtakunnallinenNumero             "122334455R"
                                             :varusteet                          {:aurinkopaneeliKytkin                false
                                                                                  :hissiKytkin                         false
                                                                                  :kaasuKytkin                         false
                                                                                  :kokoontumistilanHenkilomaara        ""
                                                                                  :koneellinenilmastointiKytkin        true
                                                                                  :lamminvesiKytkin                    true
                                                                                  :liitettyJatevesijarjestelmaanKytkin false
                                                                                  :sahkoKytkin                         true
                                                                                  :saunoja                             ""
                                                                                  :vaestonsuoja                        "54"
                                                                                  :vesijohtoKytkin                     true
                                                                                  :viemariKytkin                       true}
                                             :verkostoliittymat                  {:kaapeliKytkin   false
                                                                                  :maakaasuKytkin  false
                                                                                  :sahkoKytkin     true
                                                                                  :vesijohtoKytkin true
                                                                                  :viemariKytkin   true}}
                                  :created  1769001708831
                                  :id       "6970d2ec044a512d714aca9c"
                                  :vtj-prt  "122334455R"}]
   :documents                   [{:created     1768819108962
                                  :data        {:_selected {:modified 1768819126301
                                                            :value    "henkilo"}
                                                :henkilo   {:henkilotiedot {:etunimi                 {:modified 1768819126301
                                                                                                      :value    "Pena"}
                                                                            :hetu                    {:modified 1768819126301
                                                                                                      :value    "010203-040A"}
                                                                            :not-finnish-hetu        {:value false}
                                                                            :sukunimi                {:modified 1768819126301
                                                                                                      :value    "Panaani"}
                                                                            :turvakieltoKytkin       {:modified 1768819126301
                                                                                                      :value    false}
                                                                            :ulkomainenHenkilotunnus {:value ""}}
                                                            :kytkimet      {:suoramarkkinointilupa       {:modified 1768819126301
                                                                                                          :value    true}
                                                                            :vainsahkoinenAsiointiKytkin {:value true}}
                                                            :osoite        {:katu                 {:modified 1768819126301
                                                                                                   :value    "Paapankuja 12"}
                                                                            :maa                  {:value "FIN"}
                                                                            :postinumero          {:modified 1768819214585
                                                                                                   :value    "04130"}
                                                                            :postitoimipaikannimi {:modified 1768819214585
                                                                                                   :value    "SIPOO"}}
                                                            :userId        {:modified 1768819126301
                                                                            :value    "777777777777777777000020"}
                                                            :yhteystiedot  {:email   {:modified 1768819126301
                                                                                      :value    "pena@example.com"}
                                                                            :puhelin {:modified 1768819126301
                                                                                      :value    "0102030405"}}}
                                                :yritys    {:liikeJaYhteisoTunnus {:value ""}
                                                            :osoite               {:katu                 {:value ""}
                                                                                   :maa                  {:value "FIN"}
                                                                                   :postinumero          {:value ""}
                                                                                   :postitoimipaikannimi {:value ""}}
                                                            :yhteyshenkilo        {:henkilotiedot {:etunimi           {:value ""}
                                                                                                   :sukunimi          {:value ""}
                                                                                                   :turvakieltoKytkin {:value false}}
                                                                                   :kytkimet      {:suoramarkkinointilupa       {:value false}
                                                                                                   :vainsahkoinenAsiointiKytkin {:value true}}
                                                                                   :yhteystiedot  {:email   {:value ""}
                                                                                                   :puhelin {:value ""}}}
                                                            :yritysnimi           {:value ""}}}
                                  :id          "696e09a500ef524b6d633edd"
                                  :schema-info {:name    "hakija-r"
                                                :subtype "hakija"
                                                :type    "party"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:huoneistot                   {:0 {:WCKytkin                {:modified 1768819526890
                                                                                                             :value    true}
                                                                                   :ammeTaiSuihkuKytkin     {:modified 1768819528755
                                                                                                             :value    true}
                                                                                   :huoneistoTyyppi         {:modified 1768819510588
                                                                                                             :value    "asuinhuoneisto"}
                                                                                   :huoneistoala            {:modified 1768819526755
                                                                                                             :value    "100"}
                                                                                   :huoneistonumero         {:modified 1768819515272
                                                                                                             :value    "001"}
                                                                                   :huoneluku               {:modified 1768819520689
                                                                                                             :value    "3"}
                                                                                   :jakokirjain             {:value ""}
                                                                                   :keittionTyyppi          {:modified 1768819522402
                                                                                                             :value    "keittio"}
                                                                                   :lamminvesiKytkin        {:modified 1768819535428
                                                                                                             :value    true}
                                                                                   :muutostapa              {:modified 1768819108962
                                                                                                             :value    "lisäys"}
                                                                                   :parvekeTaiTerassiKytkin {:modified 1768819534156
                                                                                                             :value    true}
                                                                                   :porras                  {:modified 1768819518560
                                                                                                             :value    "A"}
                                                                                   :pysyvaHuoneistotunnus   {:value ""}
                                                                                   :saunaKytkin             {:modified 1768819529897
                                                                                                             :value    true}}
                                                                               :1 {:WCKytkin                {:modified 1768819559822
                                                                                                             :value    true}
                                                                                   :ammeTaiSuihkuKytkin     {:modified 1768819561606
                                                                                                             :value    true}
                                                                                   :huoneistoTyyppi         {:modified 1768819545590
                                                                                                             :value    "toimitila"}
                                                                                   :huoneistoala            {:modified 1768819559708
                                                                                                             :value    "120"}
                                                                                   :huoneistonumero         {:modified 1768819549961
                                                                                                             :value    "002"}
                                                                                   :huoneluku               {:modified 1768819551895
                                                                                                             :value    "4"}
                                                                                   :jakokirjain             {:modified 1768819539140
                                                                                                             :value    ""}
                                                                                   :keittionTyyppi          {:modified 1768819554586
                                                                                                             :value    "keittokomero"}
                                                                                   :lamminvesiKytkin        {:modified 1768819563719
                                                                                                             :value    true}
                                                                                   :muutostapa              {:modified 1768819539140
                                                                                                             :value    "lisäys"}
                                                                                   :parvekeTaiTerassiKytkin {:modified 1768819539140
                                                                                                             :value    false}
                                                                                   :porras                  {:modified 1768819539140
                                                                                                             :value    ""}
                                                                                   :pysyvaHuoneistotunnus   {:modified 1768819539140
                                                                                                             :value    ""}
                                                                                   :saunaKytkin             {:modified 1768819539140
                                                                                                             :value    false}}}
                                                :kaytto                       {:esteetonKytkin             {:modified 1768819362104
                                                                                                            :value    true}
                                                                               :kayttotarkoitus            {:modified 1768819352447
                                                                                                            :value    "013 muut erilliset talot"}
                                                                               :rakennusluokka             {:modified 1768819357709
                                                                                                            :value    "0112 rivitalot"}
                                                                               :rakentajaTyyppi            {:modified 1768819347148
                                                                                                            :value    "muu"}
                                                                               :tilapainenRakennusKytkin   {:value false}
                                                                               :valiaikainenRakennusKytkin {:value false}}
                                                :lammitys                     {:lammitystapa    {:modified 1768821170085
                                                                                                 :value    "ilmakeskus"}
                                                                               :lammonlahde     {:modified 1768819448453
                                                                                                 :value    "kauko tai aluelämpö"}
                                                                               :muu-lammonlahde {:value ""}}
                                                :luokitus                     {:energialuokka                {:modified 1768819488865
                                                                                                              :value    "D"}
                                                                               :energiatehokkuusluku         {:modified 1768819493456
                                                                                                              :value    "30"}
                                                                               :energiatehokkuusluvunYksikko {:value "kWh/m2"}
                                                                               :paloluokka                   {:modified 1768819503246
                                                                                                              :value    "P1/P2/P3"}}
                                                :mitat                        {:harjanKorkeusrakennusalalla    {:modified 1768819421034
                                                                                                                :value    "20"}
                                                                               :kellarinpinta-ala              {:modified 1768819387903
                                                                                                                :value    "500"}
                                                                               :kerrosala                      {:modified 1768819378596
                                                                                                                :value    "200"}
                                                                               :kerrosluku                     {:modified 1768819383464
                                                                                                                :value    "2"}
                                                                               :kokonaisala                    {:modified 1768819380520
                                                                                                                :value    "1000"}
                                                                               :rakennusoikeudellinenKerrosala {:value ""}
                                                                               :rakennusoikeusKuutiometreina   {:modified 1768819409740
                                                                                                                :value    "2000"}
                                                                               :rakennusoikeusValinta          {:modified 1768819402854
                                                                                                                :value    "harjakorkeus"}
                                                                               :tilavuus                       {:modified 1768819376090
                                                                                                                :value    "2000"}}
                                                :puhtaan-siirtyman-lupa       {:puhtaan-siirtyman-lupa-enabled {:value false}}
                                                :rakenne                      {:julkisivu           {:modified 1768819437307
                                                                                                     :value    "kivi"}
                                                                               :kantavaRakennusaine {:modified 1768819433785
                                                                                                     :value    "betoni"}
                                                                               :muuMateriaali       {:value ""}
                                                                               :muuRakennusaine     {:value ""}
                                                                               :rakentamistapa      {:modified 1768819428319
                                                                                                     :value    "elementti"}}
                                                :rakennuksenOmistajat         {:0 {:_selected        {:value "henkilo"}
                                                                                   :henkilo          {:henkilotiedot {:etunimi                 {:modified 1768819316851
                                                                                                                                                :value    "Pena"}
                                                                                                                      :hetu                    {:modified 1768819316851
                                                                                                                                                :value    "010203-040A"}
                                                                                                                      :not-finnish-hetu        {:value false}
                                                                                                                      :sukunimi                {:modified 1768819316851
                                                                                                                                                :value    "Panaani"}
                                                                                                                      :turvakieltoKytkin       {:modified 1768819316851
                                                                                                                                                :value    false}
                                                                                                                      :ulkomainenHenkilotunnus {:value ""}}
                                                                                                      :kytkimet      {:suoramarkkinointilupa {:modified 1768819316851
                                                                                                                                              :value    true}}
                                                                                                      :osoite        {:katu                 {:modified 1768819316851
                                                                                                                                             :value    "Paapankuja 12"}
                                                                                                                      :maa                  {:value "FIN"}
                                                                                                                      :postinumero          {:modified 1768819331203
                                                                                                                                             :value    "04130"}
                                                                                                                      :postitoimipaikannimi {:modified 1768819331203
                                                                                                                                             :value    "SIPOO"}}
                                                                                                      :userId        {:modified 1768819316851
                                                                                                                      :value    "777777777777777777000020"}
                                                                                                      :yhteystiedot  {:email   {:modified 1768819316851
                                                                                                                                :value    "pena@example.com"}
                                                                                                                      :puhelin {:modified 1768819316851
                                                                                                                                :value    "0102030405"}}}
                                                                                   :muu-omistajalaji {:value ""}
                                                                                   :omistajalaji     {:modified 1768821606209
                                                                                                      :value    "yksityinen yritys (osake- avoin- tai kommandiittiyhtiö osuuskunta)"}
                                                                                   :yritys           {:liikeJaYhteisoTunnus {:value ""}
                                                                                                      :osoite               {:katu                 {:value ""}
                                                                                                                             :maa                  {:value "FIN"}
                                                                                                                             :postinumero          {:value ""}
                                                                                                                             :postitoimipaikannimi {:value ""}}
                                                                                                      :yhteyshenkilo        {:henkilotiedot {:etunimi           {:value ""}
                                                                                                                                             :sukunimi          {:value ""}
                                                                                                                                             :turvakieltoKytkin {:value false}}
                                                                                                                             :yhteystiedot  {:email   {:value ""}
                                                                                                                                             :puhelin {:value ""}}}
                                                                                                      :yritysnimi           {:value ""}}}}
                                                :rakennusluokka-vahahiilisyys {:rakennusluokka-vahahiilisyys {:modified 1768819370208
                                                                                                              :value    "rivitalo"}}
                                                :show-clean-transition        {:value false}
                                                :tunnus                       {:modified 1768819296968 :value "A123"}
                                                :valtakunnallinenNumero       {:value ""}
                                                :varusteet                    {:aurinkopaneeliKytkin                {:value false}
                                                                               :hissiKytkin                         {:value false}
                                                                               :kaasuKytkin                         {:value false}
                                                                               :kokoontumistilanHenkilomaara        {:modified 1768819480756
                                                                                                                     :value    "20"}
                                                                               :koneellinenilmastointiKytkin        {:modified 1768819467740
                                                                                                                     :value    true}
                                                                               :lamminvesiKytkin                    {:value false}
                                                                               :liitettyJatevesijarjestelmaanKytkin {:value false}
                                                                               :sahkoKytkin                         {:modified 1768819462204
                                                                                                                     :value    true}
                                                                               :saunoja                             {:modified 1768819474374
                                                                                                                     :value    "3"}
                                                                               :vaestonsuoja                        {:modified 1768819477974
                                                                                                                     :value    "10"}
                                                                               :vesijohtoKytkin                     {:modified 1768819464185
                                                                                                                     :value    true}
                                                                               :viemariKytkin                       {:modified 1768819463203
                                                                                                                     :value    true}}
                                                :verkostoliittymat            {:kaapeliKytkin   {:modified 1768819453170
                                                                                                 :value    true}
                                                                               :maakaasuKytkin  {:value false}
                                                                               :sahkoKytkin     {:modified 1768819450058
                                                                                                 :value    true}
                                                                               :vesijohtoKytkin {:modified 1768819458072
                                                                                                 :value    true}
                                                                               :viemariKytkin   {:modified 1768819454936
                                                                                                 :value    true}}}
                                  :id          "696e09a500ef524b6d633edc"
                                  :meta        {:_approved {:timestamp 1769712699737
                                                            :user      {:firstName "Sonja"
                                                                        :id        "777777777777777777000023"
                                                                        :lastName  "Sibbo"}
                                                            :value     "approved"}}
                                  :schema-info {:name    "uusiRakennus"
                                                :op      {:created     1768819108962
                                                          :description nil
                                                          :id          "696e09a500ef524b6d633edb"
                                                          :name        "kerrostalo-rivitalo"}
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:kuvaus {:value ""} :poikkeamat {:value ""}}
                                  :id          "696e09a500ef524b6d633ee2"
                                  :schema-info {:name    "hankkeen-kuvaus"
                                                :subtype "hankkeen-kuvaus"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:henkilotiedot {:etunimi  {:value ""}
                                                                :sukunimi {:value ""}}
                                                :osoite        {:katu                 {:value ""}
                                                                :maa                  {:value "FIN"}
                                                                :postinumero          {:value ""}
                                                                :postitoimipaikannimi {:value ""}}
                                                :yritys        {:yritysnimi {:value ""}}}
                                  :id          "696e09a500ef524b6d633ee3"
                                  :schema-info {:name    "paatoksen-toimitus-rakval"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:_selected  {:value "henkilo"}
                                                :henkilo    {:henkilotiedot {:etunimi                 {:value ""}
                                                                             :not-finnish-hetu        {:value false}
                                                                             :sukunimi                {:value ""}
                                                                             :turvakieltoKytkin       {:value false}
                                                                             :ulkomainenHenkilotunnus {:value ""}}
                                                             :kytkimet      {:suoramarkkinointilupa {:value false}}
                                                             :osoite        {:katu                 {:value ""}
                                                                             :maa                  {:value "FIN"}
                                                                             :postinumero          {:value ""}
                                                                             :postitoimipaikannimi {:value ""}}
                                                             :yhteystiedot  {:email   {:value ""}
                                                                             :puhelin {:value ""}}}
                                                :laskuviite {:value ""}
                                                :yritys     {:liikeJaYhteisoTunnus {:value ""}
                                                             :osoite               {:katu                 {:value ""}
                                                                                    :maa                  {:value "FIN"}
                                                                                    :postinumero          {:value ""}
                                                                                    :postitoimipaikannimi {:value ""}}
                                                             :verkkolaskutustieto  {:ovtTunnus         {:value ""}
                                                                                    :verkkolaskuTunnus {:value ""}}
                                                             :yhteyshenkilo        {:henkilotiedot {:etunimi           {:value ""}
                                                                                                    :sukunimi          {:value ""}
                                                                                                    :turvakieltoKytkin {:value false}}
                                                                                    :kytkimet      {:suoramarkkinointilupa {:value false}}
                                                                                    :yhteystiedot  {:email   {:value ""}
                                                                                                    :puhelin {:value ""}}}
                                                             :yritysnimi           {:value ""}}}
                                  :id          "696e09a500ef524b6d633ee4"
                                  :schema-info {:name    "maksaja"
                                                :subtype "maksaja"
                                                :type    "party"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:hallintaperuste {:modified 1768819269027
                                                                  :value    "oma"}
                                                :kaavatilanne    {:modified 1768819272383
                                                                  :value    "asemakaava"}
                                                :kiinteisto      {:maapintaala      {:modified 1768819108962
                                                                                     :value    ""}
                                                                  :rekisterointipvm {:modified 1768819108962
                                                                                     :value    ""}
                                                                  :tilanNimi        {:modified 1768819108962
                                                                                     :value    ""}
                                                                  :vesipintaala     {:modified 1768819108962
                                                                                     :value    ""}}}
                                  :id          "696e09a500ef524b6d633ee5"
                                  :schema-info {:name    "rakennuspaikka"
                                                :type    "location"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:henkilotiedot {:etunimi                 {:value ""}
                                                                :not-finnish-hetu        {:value false}
                                                                :sukunimi                {:value ""}
                                                                :ulkomainenHenkilotunnus {:value ""}}
                                                :osoite        {:katu                 {:value ""}
                                                                :maa                  {:value "FIN"}
                                                                :postinumero          {:value ""}
                                                                :postitoimipaikannimi {:value ""}}
                                                :patevyys      {:fise             {:value ""}
                                                                :kokemus          {:value ""}
                                                                :koulutus         {:value ""}
                                                                :patevyys         {:value ""}
                                                                :valmistumisvuosi {:value ""}}
                                                :yhteystiedot  {:email   {:value ""}
                                                                :puhelin {:value ""}}
                                                :yritys        {:liikeJaYhteisoTunnus {:value ""}
                                                                :yritysnimi           {:value ""}}}
                                  :id          "696e09a500ef524b6d633ee6"
                                  :schema-info {:name    "paasuunnittelija"
                                                :subtype "suunnittelija"
                                                :type    "party"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:henkilotiedot         {:etunimi                 {:value ""}
                                                                        :not-finnish-hetu        {:value false}
                                                                        :sukunimi                {:value ""}
                                                                        :ulkomainenHenkilotunnus {:value ""}}
                                                :muuSuunnittelijaRooli {:value ""}
                                                :osoite                {:katu                 {:value ""}
                                                                        :maa                  {:value "FIN"}
                                                                        :postinumero          {:value ""}
                                                                        :postitoimipaikannimi {:value ""}}
                                                :patevyys              {:fise             {:value ""}
                                                                        :kokemus          {:value ""}
                                                                        :koulutus         {:value ""}
                                                                        :patevyys         {:value ""}
                                                                        :valmistumisvuosi {:value ""}}
                                                :yhteystiedot          {:email   {:value ""}
                                                                        :puhelin {:value ""}}
                                                :yritys                {:liikeJaYhteisoTunnus {:value ""}
                                                                        :yritysnimi           {:value ""}}}
                                  :id          "696e09a500ef524b6d633ee7"
                                  :schema-info {:name    "suunnittelija"
                                                :subtype "suunnittelija"
                                                :type    "party"
                                                :version 1}}
                                 {:created     1768819108962
                                  :data        {:extended-contact           {:email {:value ""}
                                                                             :name  {:value ""}
                                                                             :phone {:value ""}}
                                                :kaivettavaMaa              {:ainekset {:0 {:aines-group    {:muu {:value ""}}
                                                                                            :hyodynnetaan   {:value ""}
                                                                                            :poisajettavia  {:value ""}
                                                                                            :sijoituspaikka {:value ""}}}}
                                                :muutKaivettavatMassat      {:ainekset                     {:0 {:aines-group    {:muu {:value ""}}
                                                                                                                :hyodynnetaan   {:value ""}
                                                                                                                :poisajettavia  {:value ""}
                                                                                                                :sijoituspaikka {:value ""}}}
                                                                             :kaivettavienMassojenSelvitys {:muuSelvitystapa {:value ""}}}
                                                :orgaaninenAines            {:ainekset {:0 {:aines-group    {:muu {:value ""}}
                                                                                            :hyodynnetaan   {:value ""}
                                                                                            :poisajettavia  {:value ""}
                                                                                            :sijoituspaikka {:value ""}}}}
                                                :pilaantuneetMaat           {:ilmoitusPuhdistuksesta       {:ilmoitusTehty      {:value false}
                                                                                                            :lisattyLiitteisiin {:value false}}
                                                                             :poistettavatAinekset         {:value ""}
                                                                             :sijoituspaikka               {:value ""}
                                                                             :tutkimusPilaantuneistaMaista {:lisattyLiitteisiin {:value false}
                                                                                                            :syy                {:value ""}}}
                                                :purkaminen                 {:etaisyysPohjavesialueesta          {:value ""}
                                                                             :ilmoitusHairitsevastaMelusta       {:lisattyLiitteisiin {:value false}
                                                                                                                  :syy                {:value ""}}
                                                                             :mineraalisenPurkujatteenKasittely  {:muuKasittelytapa {:value ""}}
                                                                             :oljysailioidenLukumaara            {:value ""}
                                                                             :polynLeviamisenEsto                {:value ""}
                                                                             :purettavaKerrosala                 {:value ""}
                                                                             :rakennusvuosi                      {:value ""}
                                                                             :sailioilleSuunnitellutToimenpiteet {:muuToimenpide {:value ""}}
                                                                             :tarkeallaPohjavesialueella         {:value false}}
                                                :rakennusJaPurkujate        {:muuJate            {:0 {:jate-group     {:muu {:value ""}}
                                                                                                      :maara          {:value ""}
                                                                                                      :sijoituspaikka {:value ""}}}
                                                                             :vaarallisetJatteet {:0 {:jate-group     {:muu {:value ""}}
                                                                                                      :maara          {:value ""}
                                                                                                      :sijoituspaikka {:value ""}}}}
                                                :reusable-materials         {:0 {:aines  {:value ""}
                                                                                 :kuvaus {:value ""}
                                                                                 :maara  {:value ""}}}
                                                :reusable-materials-contact {:email {:value ""}
                                                                             :name  {:value ""}
                                                                             :phone {:value ""}}
                                                :toteutus                   {:paaurakoitsijanYhteystiedot {:value ""}}
                                                :vaarallisetAineet          {:eiVaarallisiaAineita           {:value false}
                                                                             :kartoitusVaarallisistaAineista {:lisattyLiitteisiin {:value false}
                                                                                                              :syy                {:value ""}}}
                                                :vieraslajit                {:selvitysVieraslajeista {:value ""}}}
                                  :id          "696e09a500ef524b6d633ee8"
                                  :schema-info {:name    "laajennettuRakennusjateselvitys-v2"
                                                :version 1}}
                                 {:created     1768821143302
                                  :data        {:buildingId                         {:modified    1768821186232
                                                                                     :source      nil
                                                                                     :sourceValue "199887766E"
                                                                                     :value       "199887766E"}
                                                :huoneistot                         {:0 {:WCKytkin                {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :ammeTaiSuihkuKytkin     {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :huoneistoTyyppi         {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "asuinhuoneisto"
                                                                                                                   :value       "asuinhuoneisto"}
                                                                                         :huoneistoala            {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "108"
                                                                                                                   :value       "108"}
                                                                                         :huoneistonumero         {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "001"
                                                                                                                   :value       "001"}
                                                                                         :huoneluku               {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "4"
                                                                                                                   :value       "4"}
                                                                                         :keittionTyyppi          {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "keittio"
                                                                                                                   :value       "keittio"}
                                                                                         :lamminvesiKytkin        {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :parvekeTaiTerassiKytkin {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :porras                  {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "A"
                                                                                                                   :value       "A"}
                                                                                         :pysyvaHuoneistotunnus   {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "1234567890"
                                                                                                                   :value       "1234567890"}
                                                                                         :saunaKytkin             {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}}
                                                                                     :1 {:WCKytkin                {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :ammeTaiSuihkuKytkin     {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :huoneistoTyyppi         {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "asuinhuoneisto"
                                                                                                                   :value       "asuinhuoneisto"}
                                                                                         :huoneistoala            {:modified    1768821518721
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "106"
                                                                                                                   :value       "200"}
                                                                                         :huoneistonumero         {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "002"
                                                                                                                   :value       "002"}
                                                                                         :huoneluku               {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "4"
                                                                                                                   :value       "4"}
                                                                                         :keittionTyyppi          {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "keittio"
                                                                                                                   :value       "keittio"}
                                                                                         :lamminvesiKytkin        {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :muutostapa              {:modified 1768821518721
                                                                                                                   :value    "muutos"}
                                                                                         :parvekeTaiTerassiKytkin {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}
                                                                                         :porras                  {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue "A"
                                                                                                                   :value       "A"}
                                                                                         :saunaKytkin             {:modified    1768821186232
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :value       true}}
                                                                                     :2 {:WCKytkin                {:modified 1768821543980
                                                                                                                   :value    true}
                                                                                         :ammeTaiSuihkuKytkin     {:modified 1768821522785
                                                                                                                   :value    false}
                                                                                         :huoneistoTyyppi         {:modified 1768821526400
                                                                                                                   :value    "toimitila"}
                                                                                         :huoneistoala            {:modified 1768821543914
                                                                                                                   :value    "100"}
                                                                                         :huoneistonumero         {:modified 1768821532756
                                                                                                                   :value    "003"}
                                                                                         :huoneluku               {:modified 1768821534514
                                                                                                                   :value    "2"}
                                                                                         :jakokirjain             {:modified 1768821522785
                                                                                                                   :value    ""}
                                                                                         :keittionTyyppi          {:modified 1768821536970
                                                                                                                   :value    "keittokomero"}
                                                                                         :lamminvesiKytkin        {:modified 1768821547868
                                                                                                                   :value    true}
                                                                                         :muutostapa              {:modified 1768821522785
                                                                                                                   :value    "lisäys"}
                                                                                         :parvekeTaiTerassiKytkin {:modified 1768821522785
                                                                                                                   :value    false}
                                                                                         :porras                  {:modified 1768821529573
                                                                                                                   :value    "A"}
                                                                                         :pysyvaHuoneistotunnus   {:modified 1768821522785
                                                                                                                   :value    ""}
                                                                                         :saunaKytkin             {:modified 1768821522785
                                                                                                                   :value    false}}}
                                                :kaytto                             {:esteetonKytkin             {:modified    1768821445379
                                                                                                                  :source      "krysp"
                                                                                                                  :sourceValue false
                                                                                                                  :value       false}
                                                                                     :kayttotarkoitus            {:modified    1768821427374
                                                                                                                  :source      "krysp"
                                                                                                                  :sourceValue "021 rivitalot"
                                                                                                                  :value       "151 toimistorakennukset"}
                                                                                     :rakennusluokka             {:modified 1768821436851
                                                                                                                  :value    "0400 toimistorakennukset"}
                                                                                     :rakentajaTyyppi            {:modified 1768821408171
                                                                                                                  :value    "liiketaloudellinen"}
                                                                                     :tilapainenRakennusKytkin   {:modified    1768821186232
                                                                                                                  :source      "krysp"
                                                                                                                  :sourceValue false
                                                                                                                  :value       false}
                                                                                     :valiaikainenRakennusKytkin {:modified    1768821186232
                                                                                                                  :source      "krysp"
                                                                                                                  :sourceValue false
                                                                                                                  :value       false}}
                                                :kunnanSisainenPysyvaRakennusnumero {:modified    1768821186232
                                                                                     :source      "krysp"
                                                                                     :sourceValue ""
                                                                                     :value       ""}
                                                :laajennuksen-tiedot                {:mitat               {:harjanKorkeusrakennusalalla    {:modified    1768821186232
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue ""
                                                                                                                                            :value       ""}
                                                                                                           :huoneistoala                   {:0 {:kayttotarkoitusKoodi {:modified 1768821369493
                                                                                                                                                                       :value    "muuta huoneistoalaan kuuluvaa tilaa"}
                                                                                                                                                :pintaAla             {:modified    1768821363864
                                                                                                                                                                       :source      nil
                                                                                                                                                                       :sourceValue ""
                                                                                                                                                                       :value       "1000"}}
                                                                                                                                            :1 {:kayttotarkoitusKoodi {:modified 1768821394327
                                                                                                                                                                       :value    "opetustilaa"}
                                                                                                                                                :pintaAla             {:modified 1768821390142
                                                                                                                                                                       :value    "1500"}}}
                                                                                                           :kerrosala                      {:modified    1768821354750
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue ""
                                                                                                                                            :value       "2500"}
                                                                                                           :kokonaisala                    {:modified    1768821358480
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue ""
                                                                                                                                            :value       "2000"}
                                                                                                           :rakennusoikeudellinenKerrosala {:modified    1768821403170
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue ""
                                                                                                                                            :value       "4000"}
                                                                                                           :rakennusoikeusKuutiometreina   {:modified    1768821186232
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue ""
                                                                                                                                            :value       ""}
                                                                                                           :rakennusoikeusValinta          {:modified    1768821186232
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue "kerrosala"
                                                                                                                                            :value       "kerrosala"}
                                                                                                           :tilavuus                       {:modified    1768821351872
                                                                                                                                            :source      nil
                                                                                                                                            :sourceValue ""
                                                                                                                                            :value       "2000"}}
                                                                                     :perusparannuskytkin {:modified    1768821186232
                                                                                                           :source      nil
                                                                                                           :sourceValue false
                                                                                                           :value       false}}
                                                :lammitys                           {:lammitystapa    {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue "vesikeskus"
                                                                                                       :value       "vesikeskus"}
                                                                                     :lammonlahde     {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue "kevyt polttoöljy"
                                                                                                       :value       "kevyt polttoöljy"}
                                                                                     :muu-lammonlahde {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue ""
                                                                                                       :value       ""}}
                                                :luokitus                           {:energiatehokkuusluku         {:modified    1768821186232
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue ""
                                                                                                                    :value       ""}
                                                                                     :energiatehokkuusluvunYksikko {:modified    1768821186232
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "kWh/m2"
                                                                                                                    :value       "kWh/m2"}}
                                                :manuaalinen_rakennusnro            {:modified    1768821186232
                                                                                     :source      "krysp"
                                                                                     :sourceValue ""
                                                                                     :value       ""}
                                                :mitat                              {:harjanKorkeusrakennusalalla    {:modified    1768821186232
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       ""}
                                                                                     :kellarinpinta-ala              {:modified    1768821489620
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       "550"}
                                                                                     :kerrosala                      {:modified    1768821463204
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "281"
                                                                                                                      :value       "1281"}
                                                                                     :kerrosluku                     {:modified    1768821186232
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "2"
                                                                                                                      :value       "2"}
                                                                                     :kokonaisala                    {:modified    1768821486000
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "281"
                                                                                                                      :value       "2281"}
                                                                                     :rakennusoikeudellinenKerrosala {:modified    1768821186232
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       ""}
                                                                                     :rakennusoikeusKuutiometreina   {:modified    1768821186232
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       ""}
                                                                                     :rakennusoikeusValinta          {:modified    1768821186232
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "kerrosala"
                                                                                                                      :value       "kerrosala"}
                                                                                     :tilavuus                       {:modified    1768821461319
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "837"
                                                                                                                      :value       "1837"}}
                                                :osoite                             {:huoneisto            {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue ""
                                                                                                            :value       ""}
                                                                                     :jakokirjain          {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue ""
                                                                                                            :value       ""}
                                                                                     :jakokirjain2         {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue ""
                                                                                                            :value       ""}
                                                                                     :kunta                {:modified    1768822015229
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "245"
                                                                                                            :value       "Kerava"}
                                                                                     :lahiosoite           {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "Kyllikintie"
                                                                                                            :value       "Kyllikintie"}
                                                                                     :maa                  {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "FIN"
                                                                                                            :value       "FIN"}
                                                                                     :osoitenumero         {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "3"
                                                                                                            :value       "3"}
                                                                                     :osoitenumero2        {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "5"
                                                                                                            :value       "5"}
                                                                                     :porras               {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue ""
                                                                                                            :value       ""}
                                                                                     :postinumero          {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "04200"
                                                                                                            :value       "04200"}
                                                                                     :postitoimipaikannimi {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "KERAVA"
                                                                                                            :value       "KERAVA"}}
                                                :rakenne                            {:julkisivu           {:modified    1768914371389
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue "tiili"
                                                                                                           :value       "other"}
                                                                                     :kantavaRakennusaine {:modified    1768914355384
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue "tiili"
                                                                                                           :value       "other"}
                                                                                     :muuMateriaali       {:modified    1768914379239
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue ""
                                                                                                           :value       "Kvartsi"}
                                                                                     :muuRakennusaine     {:modified    1768914363682
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue ""
                                                                                                           :value       "Olki"}
                                                                                     :rakentamistapa      {:modified    1768821186232
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue "paikalla"
                                                                                                           :value       "paikalla"}}
                                                :rakennuksenOmistajat               {:0 {:_selected        {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "yritys"
                                                                                                            :value       "yritys"}
                                                                                         :henkilo          {:henkilotiedot {:etunimi                 {:modified    1768821186232
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue ""
                                                                                                                                                      :value       ""}
                                                                                                                            :not-finnish-hetu        {:modified    1768821186232
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue false
                                                                                                                                                      :value       false}
                                                                                                                            :sukunimi                {:modified    1768821186232
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue ""
                                                                                                                                                      :value       ""}
                                                                                                                            :turvakieltoKytkin       {:modified    1768821186232
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue false
                                                                                                                                                      :value       false}
                                                                                                                            :ulkomainenHenkilotunnus {:modified    1768821186232
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue ""
                                                                                                                                                      :value       ""}}
                                                                                                            :kytkimet      {:suoramarkkinointilupa {:modified    1768821186232
                                                                                                                                                    :source      "krysp"
                                                                                                                                                    :sourceValue false
                                                                                                                                                    :value       false}}
                                                                                                            :osoite        {:katu                 {:modified    1768821186232
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue ""
                                                                                                                                                   :value       ""}
                                                                                                                            :maa                  {:modified    1768821186232
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue "FIN"
                                                                                                                                                   :value       "FIN"}
                                                                                                                            :postinumero          {:modified    1768821186232
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue ""
                                                                                                                                                   :value       ""}
                                                                                                                            :postitoimipaikannimi {:modified    1768821186232
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue ""
                                                                                                                                                   :value       ""}}
                                                                                                            :yhteystiedot  {:email   {:modified    1768821186232
                                                                                                                                      :source      "krysp"
                                                                                                                                      :sourceValue ""
                                                                                                                                      :value       ""}
                                                                                                                            :puhelin {:modified    1768821186232
                                                                                                                                      :source      "krysp"
                                                                                                                                      :sourceValue ""
                                                                                                                                      :value       ""}}}
                                                                                         :muu-omistajalaji {:modified    1768821186232
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue ""
                                                                                                            :value       ""}
                                                                                         :yritys           {:liikeJaYhteisoTunnus {:modified    1768821186232
                                                                                                                                   :source      "krysp"
                                                                                                                                   :sourceValue "1234567-1"
                                                                                                                                   :value       "1234567-1"}
                                                                                                            :osoite               {:katu                 {:modified    1768821186232
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "Testikatu 1 A 9242"
                                                                                                                                                          :value       "Testikatu 1 A 9242"}
                                                                                                                                   :maa                  {:modified    1768821186232
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "FIN"
                                                                                                                                                          :value       "FIN"}
                                                                                                                                   :postinumero          {:modified    1768821186232
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "00380"
                                                                                                                                                          :value       "00380"}
                                                                                                                                   :postitoimipaikannimi {:modified    1768821186232
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "HELSINKI"
                                                                                                                                                          :value       "HELSINKI"}}
                                                                                                            :yhteyshenkilo        {:henkilotiedot {:etunimi           {:modified    1768821186232
                                                                                                                                                                       :source      "krysp"
                                                                                                                                                                       :sourceValue ""
                                                                                                                                                                       :value       ""}
                                                                                                                                                   :sukunimi          {:modified    1768821186232
                                                                                                                                                                       :source      "krysp"
                                                                                                                                                                       :sourceValue ""
                                                                                                                                                                       :value       ""}
                                                                                                                                                   :turvakieltoKytkin {:modified    1768821186232
                                                                                                                                                                       :source      "krysp"
                                                                                                                                                                       :sourceValue false
                                                                                                                                                                       :value       false}}
                                                                                                                                   :yhteystiedot  {:email   {:modified    1768821186232
                                                                                                                                                             :source      "krysp"
                                                                                                                                                             :sourceValue ""
                                                                                                                                                             :value       ""}
                                                                                                                                                   :puhelin {:modified    1768821186232
                                                                                                                                                             :source      "krysp"
                                                                                                                                                             :sourceValue ""
                                                                                                                                                             :value       ""}}}
                                                                                                            :yritysnimi           {:modified    1768821186232
                                                                                                                                   :source      "krysp"
                                                                                                                                   :sourceValue "Testiyritys 9242"
                                                                                                                                   :value       "Testiyritys 9242"}}}}
                                                :rakennusluokka-vahahiilisyys       {:rakennusluokka-vahahiilisyys {:modified 1768821454581
                                                                                                                    :value    "toimistorakennus-ja-terveyskeskus"}}
                                                :rakennusnro                        {:modified    1768821186232
                                                                                     :source      "krysp"
                                                                                     :sourceValue "002"
                                                                                     :value       "002"}
                                                :valtakunnallinenNumero             {:modified    1768821186232
                                                                                     :source      "krysp"
                                                                                     :sourceValue "199887766E"
                                                                                     :value       "199887766E"}
                                                :varusteet                          {:aurinkopaneeliKytkin                {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :hissiKytkin                         {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :kaasuKytkin                         {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :kokoontumistilanHenkilomaara        {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue ""
                                                                                                                           :value       ""}
                                                                                     :koneellinenilmastointiKytkin        {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :lamminvesiKytkin                    {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :liitettyJatevesijarjestelmaanKytkin {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :sahkoKytkin                         {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :saunoja                             {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue ""
                                                                                                                           :value       ""}
                                                                                     :vaestonsuoja                        {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue ""
                                                                                                                           :value       ""}
                                                                                     :vesijohtoKytkin                     {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :viemariKytkin                       {:modified    1768821186232
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}}
                                                :verkostoliittymat                  {:kaapeliKytkin   {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue false
                                                                                                       :value       false}
                                                                                     :maakaasuKytkin  {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue false
                                                                                                       :value       false}
                                                                                     :sahkoKytkin     {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue true
                                                                                                       :value       true}
                                                                                     :vesijohtoKytkin {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue true
                                                                                                       :value       true}
                                                                                     :viemariKytkin   {:modified    1768821186232
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue true
                                                                                                       :value       true}}}
                                  :id          "696e119700ef524b6d633eed"
                                  :meta        {:_approved {:note      "Please double check."
                                                            :timestamp 1769712729951
                                                            :user      {:firstName "Sonja"
                                                                        :id        "777777777777777777000023"
                                                                        :lastName  "Sibbo"}
                                                            :value     "rejected"}}
                                  :schema-info {:name    "rakennuksen-laajentaminen"
                                                :op      {:created     1768821143302
                                                          :description nil
                                                          :id          "696e119700ef524b6d633eec"
                                                          :name        "kerrostalo-rt-laaj"}
                                                :version 1}}
                                 {:created     1768821668898
                                  :data        {:buildingId                         {:modified 1768821771403
                                                                                     :value    "other"}
                                                :kaytto                             {:esteetonKytkin             {:value false}
                                                                                     :kayttotarkoitus            {:modified 1768822115677
                                                                                                                  :value    "041 vapaa-ajan asuinrakennukset"}
                                                                                     :rakennusluokka             {:modified 1768822124638
                                                                                                                  :value    "0211 osavuotiseen käyttöön soveltuvat vapaa-ajan asuinrakennukset"}
                                                                                     :rakentajaTyyppi            {:modified 1768822111189
                                                                                                                  :value    "muu"}
                                                                                     :tilapainenRakennusKytkin   {:value false}
                                                                                     :valiaikainenRakennusKytkin {:value false}}
                                                :kunnanSisainenPysyvaRakennusnumero {:value ""}
                                                :manuaalinen_rakennusnro            {:modified 1768821780288
                                                                                     :value    "567"}
                                                :mitat                              {:harjanKorkeusrakennusalalla    {:value ""}
                                                                                     :kellarinpinta-ala              {:modified 1768822161751
                                                                                                                      :value    "10"}
                                                                                     :kerrosala                      {:modified 1768822155027
                                                                                                                      :value    "50"}
                                                                                     :kerrosluku                     {:modified 1768822159109
                                                                                                                      :value    "1"}
                                                                                     :kokonaisala                    {:modified 1768822157668
                                                                                                                      :value    "200"}
                                                                                     :rakennusoikeudellinenKerrosala {:modified 1768822168383
                                                                                                                      :value    "250"}
                                                                                     :rakennusoikeusKuutiometreina   {:value ""}
                                                                                     :rakennusoikeusValinta          {:value "kerrosala"}
                                                                                     :tilavuus                       {:modified 1768822151416
                                                                                                                      :value    "90"}}
                                                :osoite                             {:huoneisto            {:value ""}
                                                                                     :jakokirjain          {:value ""}
                                                                                     :jakokirjain2         {:value ""}
                                                                                     :kunta                {:modified 1768822062319
                                                                                                            :value    "Jokioinen"}
                                                                                     :lahiosoite           {:modified 1768822067115
                                                                                                            :value    "Jokutie"}
                                                                                     :maa                  {:value "FIN"}
                                                                                     :osoitenumero         {:modified 1768822070237
                                                                                                            :value    "10"}
                                                                                     :osoitenumero2        {:value ""}
                                                                                     :porras               {:value ""}
                                                                                     :postinumero          {:modified 1768822102633
                                                                                                            :value    "31600"}
                                                                                     :postitoimipaikannimi {:modified 1768822102633
                                                                                                            :value    "JOKIOINEN"}}
                                                :poistumanAjankohta                 {:modified 1768821751873
                                                                                     :value    "05.02.2026"}
                                                :poistumanSyy                       {:modified 1768821741145
                                                                                     :value    "purettu uudisrakentamisen vuoksi"}
                                                :rakenne                            {:julkisivu           {:modified 1768822138997
                                                                                                           :value    "lasi"}
                                                                                     :kantavaRakennusaine {:modified 1768822142026
                                                                                                           :value    "ei tiedossa"}
                                                                                     :muuMateriaali       {:value ""}
                                                                                     :muuRakennusaine     {:value ""}
                                                                                     :rakentamistapa      {:modified 1768822144517
                                                                                                           :value    "ei tiedossa"}}
                                                :rakennuksenOmistajat               {:0 {:_selected        {:modified 1768821812112
                                                                                                            :value    "yritys"}
                                                                                         :henkilo          {:henkilotiedot {:etunimi                 {:value ""}
                                                                                                                            :not-finnish-hetu        {:value false}
                                                                                                                            :sukunimi                {:value ""}
                                                                                                                            :turvakieltoKytkin       {:value false}
                                                                                                                            :ulkomainenHenkilotunnus {:value ""}}
                                                                                                            :kytkimet      {:suoramarkkinointilupa {:value false}}
                                                                                                            :osoite        {:katu                 {:value ""}
                                                                                                                            :maa                  {:value "FIN"}
                                                                                                                            :postinumero          {:value ""}
                                                                                                                            :postitoimipaikannimi {:value ""}}
                                                                                                            :yhteystiedot  {:email   {:value ""}
                                                                                                                            :puhelin {:value ""}}}
                                                                                         :muu-omistajalaji {:value ""}
                                                                                         :omistajalaji     {:modified 1768821981187
                                                                                                            :value    "valtion liikelaitos"}
                                                                                         :yritys           {:companyId            {:modified 1768821963169
                                                                                                                                   :value    "esimerkki"}
                                                                                                            :liikeJaYhteisoTunnus {:modified 1768821963107
                                                                                                                                   :value    "7208863-8"}
                                                                                                            :osoite               {:katu                 {:modified 1768821963107
                                                                                                                                                          :value    "Merkintie 88"}
                                                                                                                                   :maa                  {:value "FIN"}
                                                                                                                                   :postinumero          {:modified 1768821963107
                                                                                                                                                          :value    "12345"}
                                                                                                                                   :postitoimipaikannimi {:modified 1768821963107
                                                                                                                                                          :value    "Humppila"}}
                                                                                                            :yhteyshenkilo        {:henkilotiedot {:etunimi           {:modified 1768821963107
                                                                                                                                                                       :value    ""}
                                                                                                                                                   :sukunimi          {:modified 1768821963107
                                                                                                                                                                       :value    ""}
                                                                                                                                                   :turvakieltoKytkin {:modified 1768821963107
                                                                                                                                                                       :value    false}}
                                                                                                                                   :yhteystiedot  {:email   {:modified 1768821963107
                                                                                                                                                             :value    ""}
                                                                                                                                                   :puhelin {:modified 1768821963107
                                                                                                                                                             :value    ""}}}
                                                                                                            :yritysnimi           {:modified 1768821963107
                                                                                                                                   :value    "Esimerkki Oy"}}}}
                                                :rakennusnro                        {:value ""}
                                                :valtakunnallinenNumero             {:modified 1768821771403
                                                                                     :value    ""}}
                                  :id          "696e13a400ef524b6d633ef2"
                                  :schema-info {:name    "purkaminen"
                                                :op      {:created     1768821668898
                                                          :description nil
                                                          :id          "696e13a400ef524b6d633ef1"
                                                          :name        "purkaminen"}
                                                :version 1}}
                                 {:created     1768921409557
                                  :data        {:kuvaus                 {:modified 1768921478584
                                                                         :value    "What is happening?"}
                                                :muita-kiinteistoja     {:0 {:pinta-ala {:value ""}
                                                                             :tunnus    {:value ""}}}
                                                :pintaAla               {:modified 1768921509878 :value "550"}
                                                :puhtaan-siirtyman-lupa {:puhtaan-siirtyman-lupa-enabled {:value false}}
                                                :show-clean-transition  {:value false}
                                                :tyyppi                 {:modified 1768921466969
                                                                         :value    "tapahtuma-alue"}}
                                  :id          "696f9941044a512d714aca31"
                                  :schema-info {:name    "erityista-toimintaa-varten-rakennettava-alue"
                                                :op      {:created     1768921409557
                                                          :description nil
                                                          :id          "696f9941044a512d714aca30"
                                                          :name        "erityista-toimintaa-alue"}
                                                :version 1}}
                                 {:created     1768921816270
                                  :data        {:kayttotarkoitus                 {:modified 1768921874737
                                                                                  :value    "Rantamuuri"}
                                                :kokonaisala                     {:modified 1768921844992
                                                                                  :value    "123"}
                                                :kuvaus                          {:modified 1768921950077
                                                                                  :value    "Sand castle."}
                                                :tilapainenRakennelmaKytkin      {:modified 1768921950146
                                                                                  :value    true}
                                                :tilapainenRakennelmavoimassaPvm {:modified 1768921960402
                                                                                  :value    "28.02.2026"}}
                                  :id          "696f9ad8044a512d714aca34"
                                  :schema-info {:name    "kaupunkikuvatoimenpide-ei-tunnusta"
                                                :op      {:created     1768921816270
                                                          :description nil
                                                          :id          "696f9ad8044a512d714aca33"
                                                          :name        "aita"}
                                                :version 1}}
                                 {:created     1769001637487
                                  :data        {:buildingId                         {:modified    1769001708655
                                                                                     :source      nil
                                                                                     :sourceValue "122334455R"
                                                                                     :value       "122334455R"}
                                                :huoneistot                         {:0  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "521"
                                                                                                                    :value       "521"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "001"
                                                                                                                    :value       "001"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :muutostapa              {:modified 1769001775393
                                                                                                                    :value    "poisto"}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :1  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "86"
                                                                                                                    :value       "86"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "002"
                                                                                                                    :value       "002"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :muutostapa              {:modified 1769001776873
                                                                                                                    :value    "poisto"}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :10 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "86"
                                                                                                                    :value       "86"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "011"
                                                                                                                    :value       "011"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :11 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "52"
                                                                                                                    :value       "52"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "012"
                                                                                                                    :value       "012"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :12 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "77"
                                                                                                                    :value       "77"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "013"
                                                                                                                    :value       "013"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :13 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "77"
                                                                                                                    :value       "77"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "014"
                                                                                                                    :value       "014"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :14 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "52"
                                                                                                                    :value       "52"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "015"
                                                                                                                    :value       "015"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :15 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "86"
                                                                                                                    :value       "86"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "016"
                                                                                                                    :value       "016"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :jakokirjain             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "a"
                                                                                                                    :value       "a"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :16 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "52"
                                                                                                                    :value       "52"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "017"
                                                                                                                    :value       "017"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :17 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "77"
                                                                                                                    :value       "77"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "018"
                                                                                                                    :value       "018"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :18 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "1145"
                                                                                                                    :value       "1145"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "019"
                                                                                                                    :value       "019"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "4"
                                                                                                                    :value       "4"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :19 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "775"
                                                                                                                    :value       "775"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "020"
                                                                                                                    :value       "020"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :2  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001831110
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "80"
                                                                                                                    :value       "120"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "003"
                                                                                                                    :value       "003"}
                                                                                          :huoneluku               {:modified    1769001822441
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "5"}
                                                                                          :jakokirjain             {:modified 1769001807142
                                                                                                                    :value    "a"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :muutostapa              {:modified 1769001807142
                                                                                                                    :value    "muutos"}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :20 {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "1145"
                                                                                                                    :value       "1145"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "021"
                                                                                                                    :value       "021"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "4"
                                                                                                                    :value       "4"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :3  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001844455
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "77"
                                                                                                                    :value       "150"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "004"
                                                                                                                    :value       "004"}
                                                                                          :huoneluku               {:modified    1769001825931
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "6"}
                                                                                          :jakokirjain             {:modified 1769001809072
                                                                                                                    :value    "b"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :muutostapa              {:modified 1769001809072
                                                                                                                    :value    "muutos"}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :4  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "52"
                                                                                                                    :value       "52"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "005"
                                                                                                                    :value       "005"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :5  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "86"
                                                                                                                    :value       "86"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "006"
                                                                                                                    :value       "006"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :6  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "52"
                                                                                                                    :value       "52"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "007"
                                                                                                                    :value       "007"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :7  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "77"
                                                                                                                    :value       "77"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "008"
                                                                                                                    :value       "008"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :8  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "77"
                                                                                                                    :value       "77"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "009"
                                                                                                                    :value       "009"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "3"
                                                                                                                    :value       "3"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittio"
                                                                                                                    :value       "keittio"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}
                                                                                     :9  {:WCKytkin                {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :ammeTaiSuihkuKytkin     {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :huoneistoTyyppi         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "asuinhuoneisto"
                                                                                                                    :value       "asuinhuoneisto"}
                                                                                          :huoneistoala            {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "52"
                                                                                                                    :value       "52"}
                                                                                          :huoneistonumero         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "010"
                                                                                                                    :value       "010"}
                                                                                          :huoneluku               {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "2"
                                                                                                                    :value       "2"}
                                                                                          :keittionTyyppi          {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "keittokomero"
                                                                                                                    :value       "keittokomero"}
                                                                                          :lamminvesiKytkin        {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :parvekeTaiTerassiKytkin {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}
                                                                                          :porras                  {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "A"
                                                                                                                    :value       "A"}
                                                                                          :saunaKytkin             {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue true
                                                                                                                    :value       true}}}
                                                :kaytto                             {:esteetonKytkin                {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue false
                                                                                                                     :value       false}
                                                                                     :kayttotarkoitus               {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue "039 muut asuinkerrostalot"
                                                                                                                     :value       "039 muut asuinkerrostalot"}
                                                                                     :rakennusluokka                {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue "0110 omakotitalot"
                                                                                                                     :value       "0110 omakotitalot"}
                                                                                     :rakentajaTyyppi               {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue "muu"
                                                                                                                     :value       "muu"}
                                                                                     :tilapainenRakennusKytkin      {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue true
                                                                                                                     :value       true}
                                                                                     :tilapainenRakennusvoimassaPvm {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue "10.10.2005"
                                                                                                                     :value       "10.10.2005"}
                                                                                     :valiaikainenRakennusKytkin    {:modified    1769001708655
                                                                                                                     :source      "krysp"
                                                                                                                     :sourceValue false
                                                                                                                     :value       false}}
                                                :kunnanSisainenPysyvaRakennusnumero {:modified    1769001708655
                                                                                     :source      "krysp"
                                                                                     :sourceValue ""
                                                                                     :value       ""}
                                                :lammitys                           {:lammitystapa    {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue "vesikeskus"
                                                                                                       :value       "vesikeskus"}
                                                                                     :lammonlahde     {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue "kauko tai aluelämpö"
                                                                                                       :value       "kauko tai aluelämpö"}
                                                                                     :muu-lammonlahde {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue ""
                                                                                                       :value       ""}}
                                                :luokitus                           {:energialuokka                {:modified    1769001751733
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "10"
                                                                                                                    :value       "E"}
                                                                                     :energiatehokkuusluku         {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue ""
                                                                                                                    :value       ""}
                                                                                     :energiatehokkuusluvunYksikko {:modified    1769001708655
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "kWh/m2"
                                                                                                                    :value       "kWh/m2"}
                                                                                     :paloluokka                   {:modified    1769001761413
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue "P1 / P2"
                                                                                                                    :value       "P1"}}
                                                :manuaalinen_rakennusnro            {:modified    1769001708655
                                                                                     :source      "krysp"
                                                                                     :sourceValue ""
                                                                                     :value       ""}
                                                :mitat                              {:harjanKorkeusrakennusalalla    {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       ""}
                                                                                     :kellarinpinta-ala              {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "100"
                                                                                                                      :value       "100"}
                                                                                     :kerrosala                      {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "1785"
                                                                                                                      :value       "1785"}
                                                                                     :kerrosluku                     {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "5"
                                                                                                                      :value       "5"}
                                                                                     :kokonaisala                    {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "2682"
                                                                                                                      :value       "2682"}
                                                                                     :muutosala                      {:modified    1769162358538
                                                                                                                      :source      nil
                                                                                                                      :sourceValue ""
                                                                                                                      :value       "6"}
                                                                                     :rakennusoikeudellinenKerrosala {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       ""}
                                                                                     :rakennusoikeusKuutiometreina   {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue ""
                                                                                                                      :value       ""}
                                                                                     :rakennusoikeusValinta          {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "kerrosala"
                                                                                                                      :value       "kerrosala"}
                                                                                     :tilavuus                       {:modified    1769001708655
                                                                                                                      :source      "krysp"
                                                                                                                      :sourceValue "8240"
                                                                                                                      :value       "8240"}}
                                                :muutostyolaji                      {:modified    1769001708655
                                                                                     :source      nil
                                                                                     :sourceValue "muut muutostyöt"
                                                                                     :value       "muut muutostyöt"}
                                                :osoite                             {:huoneisto            {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "99"
                                                                                                            :value       "99"}
                                                                                     :jakokirjain          {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "a"
                                                                                                            :value       "a"}
                                                                                     :jakokirjain2         {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "b"
                                                                                                            :value       "b"}
                                                                                     :kunta                {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "245"
                                                                                                            :value       "245"}
                                                                                     :lahiosoite           {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "Vehkalantie"
                                                                                                            :value       "Vehkalantie"}
                                                                                     :maa                  {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "FIN"
                                                                                                            :value       "FIN"}
                                                                                     :osoitenumero         {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "2"
                                                                                                            :value       "2"}
                                                                                     :osoitenumero2        {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "3"
                                                                                                            :value       "3"}
                                                                                     :porras               {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "G"
                                                                                                            :value       "G"}
                                                                                     :postinumero          {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "04200"
                                                                                                            :value       "04200"}
                                                                                     :postitoimipaikannimi {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "KERAVA"
                                                                                                            :value       "KERAVA"}}
                                                :perusparannuskytkin                {:modified    1769001708655
                                                                                     :source      nil
                                                                                     :sourceValue true
                                                                                     :value       true}
                                                :rakenne                            {:julkisivu           {:modified    1769001708655
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue "betoni"
                                                                                                           :value       "betoni"}
                                                                                     :kantavaRakennusaine {:modified    1769001708655
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue "betoni"
                                                                                                           :value       "betoni"}
                                                                                     :muuMateriaali       {:modified    1769001708655
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue ""
                                                                                                           :value       ""}
                                                                                     :muuRakennusaine     {:modified    1769001708655
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue ""
                                                                                                           :value       ""}
                                                                                     :rakentamistapa      {:modified    1769001708655
                                                                                                           :source      "krysp"
                                                                                                           :sourceValue "elementti"
                                                                                                           :value       "elementti"}}
                                                :rakennuksenOmistajat               {:0 {:_selected        {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "yritys"
                                                                                                            :value       "yritys"}
                                                                                         :henkilo          {:henkilotiedot {:etunimi                 {:modified    1769001708655
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue ""
                                                                                                                                                      :value       ""}
                                                                                                                            :not-finnish-hetu        {:modified    1769001708655
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue false
                                                                                                                                                      :value       false}
                                                                                                                            :sukunimi                {:modified    1769001708655
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue ""
                                                                                                                                                      :value       ""}
                                                                                                                            :turvakieltoKytkin       {:modified    1769001708655
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue false
                                                                                                                                                      :value       false}
                                                                                                                            :ulkomainenHenkilotunnus {:modified    1769001708655
                                                                                                                                                      :source      "krysp"
                                                                                                                                                      :sourceValue ""
                                                                                                                                                      :value       ""}}
                                                                                                            :kytkimet      {:suoramarkkinointilupa {:modified    1769001708655
                                                                                                                                                    :source      "krysp"
                                                                                                                                                    :sourceValue false
                                                                                                                                                    :value       false}}
                                                                                                            :osoite        {:katu                 {:modified    1769001708655
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue ""
                                                                                                                                                   :value       ""}
                                                                                                                            :maa                  {:modified    1769001708655
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue "FIN"
                                                                                                                                                   :value       "FIN"}
                                                                                                                            :postinumero          {:modified    1769001708655
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue ""
                                                                                                                                                   :value       ""}
                                                                                                                            :postitoimipaikannimi {:modified    1769001708655
                                                                                                                                                   :source      "krysp"
                                                                                                                                                   :sourceValue ""
                                                                                                                                                   :value       ""}}
                                                                                                            :yhteystiedot  {:email   {:modified    1769001708655
                                                                                                                                      :source      "krysp"
                                                                                                                                      :sourceValue ""
                                                                                                                                      :value       ""}
                                                                                                                            :puhelin {:modified    1769001708655
                                                                                                                                      :source      "krysp"
                                                                                                                                      :sourceValue ""
                                                                                                                                      :value       ""}}}
                                                                                         :muu-omistajalaji {:modified    1769001708655
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue ""
                                                                                                            :value       ""}
                                                                                         :yritys           {:liikeJaYhteisoTunnus {:modified    1769001708655
                                                                                                                                   :source      "krysp"
                                                                                                                                   :sourceValue "1234567-1"
                                                                                                                                   :value       "1234567-1"}
                                                                                                            :osoite               {:katu                 {:modified    1769001708655
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "Testikatu 1 A 11477"
                                                                                                                                                          :value       "Testikatu 1 A 11477"}
                                                                                                                                   :maa                  {:modified    1769001708655
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "FIN"
                                                                                                                                                          :value       "FIN"}
                                                                                                                                   :postinumero          {:modified    1769001708655
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "00380"
                                                                                                                                                          :value       "00380"}
                                                                                                                                   :postitoimipaikannimi {:modified    1769001708655
                                                                                                                                                          :source      "krysp"
                                                                                                                                                          :sourceValue "HELSINKI"
                                                                                                                                                          :value       "HELSINKI"}}
                                                                                                            :yhteyshenkilo        {:henkilotiedot {:etunimi           {:modified    1769001708655
                                                                                                                                                                       :source      "krysp"
                                                                                                                                                                       :sourceValue ""
                                                                                                                                                                       :value       ""}
                                                                                                                                                   :sukunimi          {:modified    1769001708655
                                                                                                                                                                       :source      "krysp"
                                                                                                                                                                       :sourceValue ""
                                                                                                                                                                       :value       ""}
                                                                                                                                                   :turvakieltoKytkin {:modified    1769001708655
                                                                                                                                                                       :source      "krysp"
                                                                                                                                                                       :sourceValue false
                                                                                                                                                                       :value       false}}
                                                                                                                                   :yhteystiedot  {:email   {:modified    1769001708655
                                                                                                                                                             :source      "krysp"
                                                                                                                                                             :sourceValue ""
                                                                                                                                                             :value       ""}
                                                                                                                                                   :puhelin {:modified    1769001708655
                                                                                                                                                             :source      "krysp"
                                                                                                                                                             :sourceValue ""
                                                                                                                                                             :value       ""}}}
                                                                                                            :yritysnimi           {:modified    1769001708655
                                                                                                                                   :source      "krysp"
                                                                                                                                   :sourceValue "Testiyritys 11477"
                                                                                                                                   :value       "Testiyritys 11477"}}}}
                                                :rakennusnro                        {:modified    1769001708655
                                                                                     :source      "krysp"
                                                                                     :sourceValue "001"
                                                                                     :value       "001"}
                                                :rakennustietojaEimuutetaKytkin     {:modified    1769162341065
                                                                                     :source      nil
                                                                                     :sourceValue false
                                                                                     :value       true}
                                                :valtakunnallinenNumero             {:modified    1769001708655
                                                                                     :source      "krysp"
                                                                                     :sourceValue "122334455R"
                                                                                     :value       "122334455R"}
                                                :varusteet                          {:aurinkopaneeliKytkin                {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :hissiKytkin                         {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :kaasuKytkin                         {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :kokoontumistilanHenkilomaara        {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue ""
                                                                                                                           :value       ""}
                                                                                     :koneellinenilmastointiKytkin        {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :lamminvesiKytkin                    {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :liitettyJatevesijarjestelmaanKytkin {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue false
                                                                                                                           :value       false}
                                                                                     :sahkoKytkin                         {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :saunoja                             {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue ""
                                                                                                                           :value       ""}
                                                                                     :vaestonsuoja                        {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue "54"
                                                                                                                           :value       "54"}
                                                                                     :vesijohtoKytkin                     {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}
                                                                                     :viemariKytkin                       {:modified    1769001708655
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue true
                                                                                                                           :value       true}}
                                                :verkostoliittymat                  {:kaapeliKytkin   {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue false
                                                                                                       :value       false}
                                                                                     :maakaasuKytkin  {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue false
                                                                                                       :value       false}
                                                                                     :sahkoKytkin     {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue true
                                                                                                       :value       true}
                                                                                     :vesijohtoKytkin {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue true
                                                                                                       :value       true}
                                                                                     :viemariKytkin   {:modified    1769001708655
                                                                                                       :source      "krysp"
                                                                                                       :sourceValue true
                                                                                                       :value       true}}}
                                  :id          "6970d2a5044a512d714aca9b"
                                  :schema-info {:name    "rakennuksen-muuttaminen"
                                                :op      {:created     1769001637487
                                                          :description nil
                                                          :id          "6970d2a5044a512d714aca9a"
                                                          :name        "sisatila-muutos"}
                                                :version 1}}
                                 {:created     1769075447954
                                  :data        {:kuvaus {:modified 1769075551355
                                                         :value    "Keep on hacking."}}
                                  :id          "6971f2f734898149b7d3dc03"
                                  :schema-info {:name    "maisematyo"
                                                :op      {:created     1769075447954
                                                          :description nil
                                                          :id          "6971f2f734898149b7d3dc02"
                                                          :name        "puun-kaataminen"}
                                                :version 1}}]
   :drawings                    []
   :finished                    nil
   :foreman                     ""
   :foremanRole                 ""
   :handlers                    []
   :history                     [{:state "draft"
                                  :ts    1768819108962
                                  :user  {:firstName "Pena"
                                          :id        "777777777777777777000020"
                                          :lastName  "Panaani"
                                          :role      "applicant"
                                          :username  "pena"}}
                                 {:state "open"
                                  :ts    1769162318170
                                  :user  {:firstName "Pena"
                                          :id        "777777777777777777000020"
                                          :lastName  "Panaani"
                                          :role      "applicant"
                                          :username  "pena"}}]
   :id                          "LP-753-2026-90008"
   :infoRequest                 false
   :inspection-summaries        []
   :location                    [405821.189 6695501.661]
   :location-wgs84              [25.29152 60.38493]
   :metadata                    {}
   :modified                    1769712749936
   :municipality                "753"
   :neighbors                   []
   :openInfoRequest             false
   :opened                      1769162318170
   :operation-name              "kerrostalo-rivitalo"
   :options                     {}
   :organization                "753-R"
   :permitSubtype               nil
   :permitType                  "R"
   :primaryOperation            {:created     1768819108962
                                 :description "New"
                                 :id          "696e09a500ef524b6d633edb"
                                 :name        "kerrostalo-rivitalo"}
   :processMetadata             {}
   :propertyId                  "75341200070043"
   :ryhti-data-2026             false
   :schema-version              1
   :secondaryOperations         [{:created     1768821143302
                                  :description "Expansion"
                                  :id          "696e119700ef524b6d633eec"
                                  :name        "kerrostalo-rt-laaj"}
                                 {:created     1768821668898
                                  :description "Demolition"
                                  :id          "696e13a400ef524b6d633ef1"
                                  :name        "purkaminen"}
                                 {:created     1768921409557
                                  :description "Main event"
                                  :id          "696f9941044a512d714aca30"
                                  :name        "erityista-toimintaa-alue"}
                                 {:created     1768921816270
                                  :description "Fencing"
                                  :id          "696f9ad8044a512d714aca33"
                                  :name        "aita"}
                                 {:created     1769001637487
                                  :description "Changes"
                                  :id          "6970d2a5044a512d714aca9a"
                                  :name        "sisatila-muutos"}
                                 {:created     1769075447954
                                  :description "Timber!"
                                  :id          "6971f2f734898149b7d3dc02"
                                  :name        "puun-kaataminen"}]
   :sent                        nil
   :started                     nil
   :startedBy                   {}
   :state                       "open"
   :statements                  []
   :submitted                   nil
   :suti                        {:added false :id nil}
   :tasks                       []
   :title                       "Transformer"
   :tosFunction                 nil
   :transfers                   []
   :urgency                     "normal"
   :verdicts                    []
   :warrantyEnd                 nil
   :warrantyStart               nil})

(def application-id         (:id legacy-application))
(def new-document-id        "696e09a500ef524b6d633edc")
(def expansion-document-id  "696e119700ef524b6d633eed")
(def demolition-document-id "696e13a400ef524b6d633ef2")
(def event-document-id      "696f9941044a512d714aca31")
(def wall-document-id       "696f9ad8044a512d714aca34")
(def change-document-id     "6970d2a5044a512d714aca9b")
(def tree-document-id       "6971f2f734898149b7d3dc03")

(defn document-by-id [application document-id]
  (util/find-by-id document-id (:documents application)))

(def new-document        (document-by-id legacy-application new-document-id))
(def expansion-document  (document-by-id legacy-application expansion-document-id))
(def demolition-document (document-by-id legacy-application demolition-document-id))
(def event-document      (document-by-id legacy-application event-document-id))
(def wall-document       (document-by-id legacy-application wall-document-id))
(def change-document     (document-by-id legacy-application change-document-id))
(def tree-document       (document-by-id legacy-application tree-document-id))
