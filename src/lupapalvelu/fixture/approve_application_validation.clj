(ns lupapalvelu.fixture.approve-application-validation
  (:require [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]))

(def app {:_applicantIndex             ["Esimerkki Oy"]
          :_attachment_indicator_reset nil
          :_comments-seen-by           {}
          :_designerIndex              []
          :_projectDescriptionIndex
          "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus volutpat nibh gravida, luctus nulla ut, fringilla turpis. Pellentesque magna nunc, imperdiet non condimentum bibendum, condimentum ac ex. Morbi et laoreet augue, eget gravida nibh. Aenean ac porta odio. In a neque velit. Vivamus hendrerit id dui quis vulputate. Curabitur maximus augue non nulla molestie facilisis. Phasellus a condimentum tellus. Cras dignissim suscipit dui. Suspendisse placerat urna a eleifend luctus. Aenean eget neque non lacus tempor finibus et vestibulum urna. In dui mauris, bibendum sed leo vel, feugiat congue erat. Nulla dictum ex egestas, tristique tellus sit amet, scelerisque dui. Pellentesque ac nisi in turpis euismod lacinia vel sed augue."
          :_statements-seen-by         {}
          :_verdicts-seen-by           {}
          :acknowledged                nil
          :address                     "Validoinnin väylä 5"
          :agreementPrepared           nil
          :agreementSigned             nil
          :appealVerdicts              []
          :appeals                     []
          :applicant                   "Esimerkki Oy"
          :archived                    {:application nil, :completed nil, :initial nil}
          :attachments                 []
          :auth                        [{:firstName    "Esimerkki Oy"
                                         :id           "esimerkki"
                                         :lastName     ""
                                         :name         "Esimerkki Oy"
                                         :role         "writer"
                                         :type         "company"
                                         :unsubscribed false
                                         :username     "7208863-8"
                                         :y            "7208863-8"}
                                        {:firstName    "Erkki"
                                         :id           "erkkiesimerkki"
                                         :lastName     "Esimerkki"
                                         :role         "writer"
                                         :unsubscribed false
                                         :username     "erkki@example.com"}]
          :authority                   {:firstName "", :id nil, :lastName ""}
          :authorityNotice             ""
          :buildings                   []
          :bulletinOpDescription       nil
          :closed                      nil
          :closedBy                    {}
          :comments                    []
          :complementNeeded            nil
          :convertedToApplication      nil
          :created                     1556553194271
          :creator                     {:firstName "Erkki"
                                        :id        "erkkiesimerkki"
                                        :lastName  "Esimerkki"
                                        :role      "applicant"
                                        :username  "erkki@example.com"}
          :documents
          [{:created     1556553194271
            :data
            {:_selected {:modified 1556553329841, :value "yritys"}
             :henkilo
             {:henkilotiedot {:etunimi           {:value ""}
                              :hetu              {:value nil}
                              :sukunimi          {:value ""}
                              :turvakieltoKytkin {:value false}}
              :kytkimet      {:suoramarkkinointilupa       {:value false}
                              :vainsahkoinenAsiointiKytkin {:value true}}
              :osoite        {:katu                 {:value ""}
                              :maa                  {:value "FIN"}
                              :postinumero          {:value ""}
                              :postitoimipaikannimi {:value ""}}
              :userId        {:value nil}
              :yhteystiedot  {:email {:value ""}, :puhelin {:value ""}}}
             :yritys
             {:companyId            {:modified 1556553332130, :value "esimerkki"}
              :liikeJaYhteisoTunnus {:modified 1556553332071
                                     :value    "7208863-8"}
              :osoite
              {:katu                 {:modified 1556553332071, :value "Merkintie 88"}
               :maa                  {:value "FIN"}
               :postinumero          {:modified 1556553332071, :value "12345"}
               :postitoimipaikannimi {:modified 1556553332071
                                      :value    "Humppila"}}
              :yhteyshenkilo
              {:henkilotiedot
               {:etunimi           {:modified 1556553332071, :value "Erkki"}
                :sukunimi          {:modified 1556553332071
                                    :value    "Esimerkki"}
                :turvakieltoKytkin {:modified 1556553332071
                                    :value    false}}
               :kytkimet     {:suoramarkkinointilupa
                              {:modified 1556553332071, :value false}
                              :vainsahkoinenAsiointiKytkin {:value true}}
               :yhteystiedot {:email   {:modified 1556553332071
                                        :value    "erkki@example.com"}
                              :puhelin {:modified 1556553332071
                                        :value    "556677"}}}
              :yritysnimi           {:modified 1556553332071
                                     :value    "Esimerkki Oy"}}}
            :id          "5cc71dea4a35e17a1f73a00a"
            :schema-info {:name    "hakija-r"
                          :subtype "hakija"
                          :type    "party"
                          :version 1}}
           {:created     1556553194271
            :data
            {:buildingId                         {:modified 1556553246578, :value "other"}
             :kaytto                             {:kayttotarkoitus {:modified 1556553292488
                                                                    :value    "111 myymälähallit"}
                                                  :rakentajaTyyppi {:modified 1556553285994
                                                                    :value    "ei tiedossa"}}
             :kunnanSisainenPysyvaRakennusnumero {:value ""}
             :manuaalinen_rakennusnro            {:modified 1556553250236
                                                  :value    "123"}
             :mitat                              {:kellarinpinta-ala              {:value ""}
                                                  :kerrosala                      {:modified 1556553310908, :value "500"}
                                                  :kerrosluku                     {:modified 1556553304153, :value "1"}
                                                  :kokonaisala                    {:value ""}
                                                  :rakennusoikeudellinenKerrosala {:value ""}
                                                  :tilavuus                       {:value ""}}
             :osoite                             {:huoneisto            {:value ""}
                                                  :jakokirjain          {:value ""}
                                                  :jakokirjain2         {:value ""}
                                                  :kunta                {:value ""}
                                                  :lahiosoite           {:value ""}
                                                  :maa                  {:value "FIN"}
                                                  :osoitenumero         {:value ""}
                                                  :osoitenumero2        {:value ""}
                                                  :porras               {:value ""}
                                                  :postinumero          {:value ""}
                                                  :postitoimipaikannimi {:value ""}}
             :poistumanAjankohta                 {:modified 1556553241780
                                                  :value    "29.4.2019"}
             :poistumanSyy                       {:modified 1556553237517, :value "poistaminen"}
             :rakenne                            {:julkisivu           {:value nil}
                                                  :kantavaRakennusaine {:modified 1556553314503
                                                                        :value    "betoni"}
                                                  :muuMateriaali       {:value ""}
                                                  :muuRakennusaine     {:value ""}
                                                  :rakentamistapa      {:modified 1556553312471
                                                                        :value    "ei tiedossa"}}
             :rakennuksenOmistajat
             {:0
              {:_selected        {:modified 1556553264225, :value "yritys"}
               :henkilo
               {:henkilotiedot {:etunimi           {:value ""}
                                :hetu              {:value nil}
                                :sukunimi          {:value ""}
                                :turvakieltoKytkin {:value false}}
                :kytkimet      {:suoramarkkinointilupa {:value false}}
                :osoite        {:katu                 {:value ""}
                                :maa                  {:value "FIN"}
                                :postinumero          {:value ""}
                                :postitoimipaikannimi {:value ""}}
                :userId        {:value nil}
                :yhteystiedot  {:email   {:value ""}
                                :puhelin {:value ""}}}
               :muu-omistajalaji {:value ""}
               :omistajalaji     {:modified 1556553273800
                                  :value    "ei tiedossa"}
               :yritys           {:companyId            {:modified 1556553267566
                                                         :value    "esimerkki"}
                                  :liikeJaYhteisoTunnus {:modified 1556553267485
                                                         :value    "7208863-8"}
                                  :osoite               {:katu        {:modified 1556553267485
                                                                       :value    "Merkintie 88"}
                                                         :maa         {:value "FIN"}
                                                         :postinumero {:modified 1556553267485
                                                                       :value    "12345"}
                                                         :postitoimipaikannimi
                                                         {:modified 1556553267485
                                                          :value    "Humppila"}}
                                  :yhteyshenkilo
                                  {:henkilotiedot
                                   {:etunimi           {:modified 1556553267485
                                                        :value    "Erkki"}
                                    :sukunimi          {:modified 1556553267485
                                                        :value    "Esimerkki"}
                                    :turvakieltoKytkin {:modified
                                                        1556553267485
                                                        :value false}}
                                   :yhteystiedot
                                   {:email   {:modified 1556553267485
                                              :value    "erkki@example.com"}
                                    :puhelin {:modified 1556553267485
                                              :value    "556677"}}}
                                  :yritysnimi           {:modified 1556553267485
                                                         :value    "Esimerkki Oy"}}}}
             :rakennusnro                        {:value ""}
             :valtakunnallinenNumero             {:modified 1556553246090, :value ""}}
            :id          "5cc71dea4a35e17a1f73a009"
            :schema-info {:name    "purkaminen"
                          :op      {:created     1556553194271
                                    :description nil
                                    :id          "5cc71dea4a35e17a1f73a008"
                                    :name        "purkaminen"}
                          :version 1}}
           {:created     1556553194271
            :data
            {:kuvaus
             {:modified 1556553228709
              :value
              "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus volutpat nibh gravida, luctus nulla ut, fringilla turpis. Pellentesque magna nunc, imperdiet non condimentum bibendum, condimentum ac ex. Morbi et laoreet augue, eget gravida nibh. Aenean ac porta odio. In a neque velit. Vivamus hendrerit id dui quis vulputate. Curabitur maximus augue non nulla molestie facilisis. Phasellus a condimentum tellus. Cras dignissim suscipit dui. Suspendisse placerat urna a eleifend luctus. Aenean eget neque non lacus tempor finibus et vestibulum urna. In dui mauris, bibendum sed leo vel, feugiat congue erat. Nulla dictum ex egestas, tristique tellus sit amet, scelerisque dui. Pellentesque ac nisi in turpis euismod lacinia vel sed augue."}
             :poikkeamat {:value ""}
             :rahoitus   {:value nil}}
            :id          "5cc71dea4a35e17a1f73a00b"
            :schema-info {:name    "hankkeen-kuvaus"
                          :subtype "hankkeen-kuvaus"
                          :version 1}}
           {:created     1556553194271
            :data        {:fillMyInfo    {:value nil}
                          :henkilotiedot {:etunimi  {:modified 1556553319617
                                                     :value    "Erkki"}
                                          :sukunimi {:modified 1556553319617
                                                     :value    "Esimerkki"}}
                          :osoite        {:katu                 {:modified 1556553319617, :value "Merkintie 88"}
                                          :maa                  {:value "FIN"}
                                          :postinumero          {:modified 1556553319617, :value "12345"}
                                          :postitoimipaikannimi {:modified 1556553319617
                                                                 :value    "Humppila"}}
                          :yritys        {:yritysnimi {:modified 1556553319617
                                                       :value    "Esimerkki Oy"}}}
            :id          "5cc71dea4a35e17a1f73a00c"
            :schema-info {:name "paatoksen-toimitus-rakval", :version 1}}
           {:created     1556553194271
            :data
            {:_selected  {:modified 1556553366005, :value "yritys"}
             :henkilo    {:henkilotiedot {:etunimi           {:value ""}
                                          :hetu              {:value nil}
                                          :sukunimi          {:value ""}
                                          :turvakieltoKytkin {:value false}}
                          :kytkimet      {:suoramarkkinointilupa {:value false}}
                          :osoite        {:katu                 {:value ""}
                                          :maa                  {:value "FIN"}
                                          :postinumero          {:value ""}
                                          :postitoimipaikannimi {:value ""}}
                          :userId        {:value nil}
                          :yhteystiedot  {:email   {:value ""}
                                          :puhelin {:value ""}}}
             :laskuviite {:value ""}
             :yritys
             {:companyId            {:modified 1556553369125, :value "esimerkki"}
              :liikeJaYhteisoTunnus {:modified 1556553369070
                                     :value    "7208863-8"}
              :osoite
              {:katu                 {:modified 1556553369070, :value "Merkintie 88"}
               :maa                  {:value "FIN"}
               :postinumero          {:modified 1556553369070, :value "12345"}
               :postitoimipaikannimi {:modified 1556553369070
                                      :value    "Humppila"}}
              :verkkolaskutustieto
              {:ovtTunnus         {:modified 1556553369070
                                   :value    "003710601555"}
               :valittajaTunnus   {:modified 1556553369070
                                   :value    "BAWCFI22"}
               :verkkolaskuTunnus {:modified 1556553369070
                                   :value    "samplebilling"}}
              :yhteyshenkilo
              {:henkilotiedot
               {:etunimi           {:modified 1556553369070, :value "Erkki"}
                :sukunimi          {:modified 1556553369070
                                    :value    "Esimerkki"}
                :turvakieltoKytkin {:modified 1556553369070
                                    :value    false}}
               :kytkimet     {:suoramarkkinointilupa
                              {:modified 1556553369070, :value false}}
               :yhteystiedot {:email   {:modified 1556553369070
                                        :value    "erkki@example.com"}
                              :puhelin {:modified 1556553369070
                                        :value    "556677"}}}
              :yritysnimi           {:modified 1556553369070
                                     :value    "Esimerkki Oy"}}}
            :id          "5cc71dea4a35e17a1f73a00d"
            :schema-info {:name    "maksaja"
                          :subtype "maksaja"
                          :type    "party"
                          :version 1}}
           {:created 1556553194271
            :data    {:hallintaperuste      {:modified 1556553230322, :value "oma"}
                      :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:value
                                                                       nil}}
                      :kaavanaste           {:value nil}
                      :kaavatilanne         {:value nil}
                      :kiinteisto
                      {:maapintaala      {:modified 1556553209349, :value ""}
                       :maaraalaTunnus   {:value nil}
                       :rekisterointipvm {:modified 1556553209349, :value ""}
                       :tilanNimi        {:modified 1556553209349, :value ""}
                       :vesipintaala     {:modified 1556553209349, :value ""}}}
            :id      "5cc71dea4a35e17a1f73a00e"
            :schema-info
            {:name "rakennuspaikka", :type "location", :version 1}}
           {:created     1556553194271
            :data
            {:henkilotiedot
             {:etunimi  {:modified 1556553341583, :value "Erkki"}
              :hetu     {:modified 1556553341583, :value "010203-041B"}
              :sukunimi {:modified 1556553341583, :value "Esimerkki"}}
             :osoite
             {:katu                 {:modified 1556553341583, :value "Merkintie 88"}
              :maa                  {:value "FIN"}
              :postinumero          {:modified 1556553341583, :value "12345"}
              :postitoimipaikannimi {:modified 1556553341583
                                     :value    "Humppila"}}
             :patevyys
             {:fise             {:modified 1556553341583, :value ""}
              :fiseKelpoisuus   {:modified 1556553341583, :value ""}
              :kokemus          {:value ""}
              :koulutus         {:value ""}
              :koulutusvalinta  {:modified 1556553353036
                                 :value    "insinööri"}
              :patevyys         {:value ""}
              :patevyysluokka   {:modified 1556553356174, :value "A"}
              :valmistumisvuosi {:modified 1556553348299
                                 :value    "2000"}}
             :suunnittelutehtavanVaativuusluokka {:modified 1556553338539
                                                  :value    "A"}
             :userId                             {:modified 1556553341583, :value "erkkiesimerkki"}
             :yhteystiedot                       {:email   {:modified 1556553341583
                                                            :value    "erkki@example.com"}
                                                  :puhelin {:modified 1556553341583
                                                            :value    "556677"}}
             :yritys                             {:liikeJaYhteisoTunnus {:modified 1556553341583
                                                                         :value    "7208863-8"}
                                                  :yritysnimi           {:modified 1556553341583
                                                                         :value    "Esimerkki Oy"}}}
            :id          "5cc71dea4a35e17a1f73a00f"
            :schema-info {:name    "paasuunnittelija"
                          :subtype "suunnittelija"
                          :type    "party"
                          :version 1}}
           {:created     1556553194271
            :data        {:rakennusJaPurkujate {:0 {:jatetyyppi       {:value nil}
                                                    :painoT           {:value ""}
                                                    :suunniteltuMaara {:value ""}
                                                    :yksikko          {:value nil}}}
                          :vaarallisetAineet   {:0 {:painoT                {:value ""}
                                                    :suunniteltuMaara      {:value ""}
                                                    :vaarallinenainetyyppi {:value
                                                                            nil}
                                                    :yksikko               {:value nil}}}}
            :id          "5cc71dea4a35e17a1f73a011"
            :schema-info {:name "rakennusjatesuunnitelma", :version 1}}]
          :drawings                    []
          :finished                    nil
          :foreman                     ""
          :foremanRole                 ""
          :handlers                    []
          :history                     [{:state "draft"
                                         :ts    1556553194271
                                         :user  {:firstName "Erkki"
                                                 :id        "erkkiesimerkki"
                                                 :lastName  "Esimerkki"
                                                 :role      "applicant"
                                                 :username  "erkki@example.com"}}
                                        {:state "open"
                                         :ts    1556553387253
                                         :user  {:firstName "Erkki"
                                                 :id        "erkkiesimerkki"
                                                 :lastName  "Esimerkki"
                                                 :role      "applicant"
                                                 :username  "erkki@example.com"}}
                                        {:state "submitted"
                                         :ts    1556553387253
                                         :user  {:firstName "Erkki"
                                                 :id        "erkkiesimerkki"
                                                 :lastName  "Esimerkki"
                                                 :role      "applicant"
                                                 :username  "erkki@example.com"}}]
          :id                          "LP-753-2019-90006"
          :infoRequest                 false
          :inspection-summaries        []
          :location                    [404692.811 6695024.867]
          :location-wgs84              [25.27129 60.38038]
          :metadata                    {}
          :modified                    1556553387253
          :municipality                "753"
          :neighbors                   []
          :openInfoRequest             false
          :opened                      1556553387253
          :operation-name              "purkaminen"
          :options                     {}
          :organization                "753-R"
          :permitSubtype               nil
          :permitType                  "R"
          :primaryOperation            {:created     1556553194271
                                        :description nil
                                        :id          "5cc71dea4a35e17a1f73a008"
                                        :name        "purkaminen"}
          :processMetadata             {}
          :propertyId                  "75342300070096"
          :reservations                []
          :schema-version              1
          :secondaryOperations         []
          :sent                        nil
          :started                     nil
          :startedBy                   {}
          :state                       "submitted"
          :statements                  []
          :submitted                   1556553387253
          :suti                        {:added false, :id nil}
          :tasks                       []
          :title                       "Validoinnin väylä 5"
          :tosFunction                 nil
          :transfers                   []
          :urgency                     "normal"
          :verdicts                    []
          :warrantyEnd                 nil
          :warrantyStart               nil})

(def app-id (:id app))
(def purkaminen-doc-id "5cc71dea4a35e17a1f73a009")
(def hakija-doc-id "5cc71dea4a35e17a1f73a00a")

(deffixture "approve-application-validation" {}
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations minimal/organizations)
  (mongo/insert-batch :submitted-applications [app])
  (mongo/insert-batch :applications [app]))
