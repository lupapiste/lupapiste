(ns lupapalvelu.pate.pate-verdict-itest
  (:require [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.inspection-summary :as inspection-summary]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.verdict :refer :all]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.core :refer [fail!]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util])
  (:import [clojure.lang ExceptionInfo]))

(testable-privates lupapalvelu.pate.verdict
                   next-section verdict-updates
                   panic-update-section
                   application-deviations
                   archive-info application-operation
                   verdict-attachment-items
                   bulletin-data
                   deletable-verdict-attachment-ids
                   app-documents-having-buildings)

(def rakennusjateselvitys
  {:id          "12345bb8e7f60415804219af"
   :created     1514376120409
   :schema-info {:name               "rakennusjateselvitys"
                 :order              201
                 :editable-in-states ["archived"
                                      "closed"
                                      "foremanVerdictGiven"
                                      "constructionStarted"
                                      "agreementPrepared"
                                      "appealed"
                                      "extinct"
                                      "inUse"
                                      "final"
                                      "finished"
                                      "agreementSigned"
                                      "acknowledged"
                                      "onHold"]
                 :blacklist          ["neighbor"]
                 :version            1}
   :data        {:rakennusJaPurkujate {:suunniteltuJate      {:0 {:jatetyyppi       {:value    "puu"
                                                                                     :modified nil}
                                                                  :suunniteltuMaara {:value    "2"
                                                                                     :modified nil}
                                                                  :yksikko          {:value    "m3"
                                                                                     :modified nil}
                                                                  :painoT           {:value    "05"
                                                                                     :modified nil}}
                                                              :1 {:jatetyyppi       {:value    "betoni"
                                                                                     :modified nil}
                                                                  :painoT           {:value    "02"
                                                                                     :modified nil}
                                                                  :yksikko          {:value    "m3"
                                                                                     :modified nil}
                                                                  :suunniteltuMaara {:value    "005"
                                                                                     :modified nil}}
                                                              :2 {:jatetyyppi       {:value    "muovi"
                                                                                     :modified nil}
                                                                  :painoT           {:value    "005"
                                                                                     :modified nil}
                                                                  :suunniteltuMaara {:value    "001"
                                                                                     :modified nil}
                                                                  :yksikko          {:value    "m3"
                                                                                     :modified nil}}}
                                       :suunnittelematonJate {:0 {:jatetyyppi            {:value nil}
                                                                  :toteutunutMaara       {:value ""}
                                                                  :yksikko               {:value nil}
                                                                  :painoT                {:value ""}
                                                                  :jatteenToimituspaikka {:value ""}}}}
                 :vaarallisetAineet   {:suunniteltuJate      {}
                                       :suunnittelematonJate {:0 {:vaarallinenainetyyppi {:value nil}
                                                                  :toteutunutMaara       {:value ""}
                                                                  :yksikko               {:value nil}
                                                                  :painoT                {:value ""}
                                                                  :jatteenToimituspaikka {:value ""}}}}
                 :contact             {:name  {:value ""}
                                       :phone {:value ""}
                                       :email {:value ""}}
                 :availableMaterials  {:0 {:aines      {:value ""}
                                           :maara      {:value ""}
                                           :yksikko    {:value nil}
                                           :saatavilla {:value nil}
                                           :kuvaus     {:value ""}}}}})

(def application-r
  {:address             "Latokuja 3"
   :primaryOperation    {:id          "5b34a9d2cea1d0f410db2403"
                         :name        "sisatila-muutos"
                         :description nil
                         :created     1530178002437}
   :buildings           []
   :comments            []
   :secondaryOperations []
   :attachments         []
   :history             [{:state "sent"
                          :ts    1200
                          :user  {:id        "777777777777777777000023"
                                  :username  "sonja"
                                  :firstName "Sonja"
                                  :lastName  "Sibbo"
                                  :role      "authority"}}]
   :operation-name      "sisatila-muutos"
   :state               "sent"
   :municipality        "753"
   :permitType          "R"
   :organization        "753-R"
   :modified            1530179094764
   :documents           [{:id          "5b34a9d3cea1d0f410db2404"
                          :schema-info {:name "rakennuksen-muuttaminen"
                                        :op   {:id   "5b34a9d2cea1d0f410db2403"
                                               :name "sisatila-muutos"}}
                          :data        {:buildingId                         {:value       "199887766E"
                                                                             :source      nil
                                                                             :sourceValue "199887766E"
                                                                             :modified    1530179094764}
                                        :muutostyolaji                      {:value       "rakennukse pääasiallinen käyttötarkoitusmuutos"
                                                                             :modified    1530179094764
                                                                             :source      nil
                                                                             :sourceValue "rakennukse pääasiallinen käyttötarkoitusmuutos"}
                                        :rakennuksenOmistajat               {:0 {:_selected        {:value       "yritys"
                                                                                                    :source      "krysp"
                                                                                                    :sourceValue "yritys"
                                                                                                    :modified    1530179094764}
                                                                                 :henkilo          {:userId        {:value       nil
                                                                                                                    :source      "krysp"
                                                                                                                    :sourceValue nil
                                                                                                                    :modified    1530179094764}
                                                                                                    :henkilotiedot {:etunimi           {:value       ""
                                                                                                                                        :modified    1530179094764
                                                                                                                                        :sourceValue ""
                                                                                                                                        :source      "krysp"}
                                                                                                                    :sukunimi          {:value       ""
                                                                                                                                        :sourceValue ""
                                                                                                                                        :source      "krysp"
                                                                                                                                        :modified    1530179094764}
                                                                                                                    :hetu              {:value       nil
                                                                                                                                        :sourceValue nil
                                                                                                                                        :modified    1530179094764
                                                                                                                                        :source      "krysp"}
                                                                                                                    :turvakieltoKytkin {:value       false
                                                                                                                                        :modified    1530179094764
                                                                                                                                        :source      "krysp"
                                                                                                                                        :sourceValue false}}
                                                                                                    :osoite        {:katu                 {:value       ""
                                                                                                                                           :sourceValue ""
                                                                                                                                           :modified    1530179094764
                                                                                                                                           :source      "krysp"}
                                                                                                                    :postinumero          {:value       ""
                                                                                                                                           :modified    1530179094764
                                                                                                                                           :sourceValue ""
                                                                                                                                           :source      "krysp"}
                                                                                                                    :postitoimipaikannimi {:value       ""
                                                                                                                                           :sourceValue ""
                                                                                                                                           :source      "krysp"
                                                                                                                                           :modified    1530179094764}
                                                                                                                    :maa                  {:value       "FIN"
                                                                                                                                           :sourceValue "FIN"
                                                                                                                                           :source      "krysp"
                                                                                                                                           :modified    1530179094764}}
                                                                                                    :yhteystiedot  {:puhelin {:value       ""
                                                                                                                              :sourceValue ""
                                                                                                                              :source      "krysp"
                                                                                                                              :modified    1530179094764}
                                                                                                                    :email   {:value       ""
                                                                                                                              :modified    1530179094764
                                                                                                                              :sourceValue ""
                                                                                                                              :source      "krysp"}}
                                                                                                    :kytkimet      {:suoramarkkinointilupa {:value       false
                                                                                                                                            :sourceValue false
                                                                                                                                            :modified    1530179094764
                                                                                                                                            :source      "krysp"}}}
                                                                                 :yritys           {:companyId            {:value       nil
                                                                                                                           :sourceValue nil
                                                                                                                           :modified    1530179094764
                                                                                                                           :source      "krysp"}
                                                                                                    :yritysnimi           {:value       "Testiyritys 9242"
                                                                                                                           :source      "krysp"
                                                                                                                           :modified    1530179094764
                                                                                                                           :sourceValue "Testiyritys 9242"}
                                                                                                    :liikeJaYhteisoTunnus {:value       "1234567-1"
                                                                                                                           :modified    1530179094764
                                                                                                                           :source      "krysp"
                                                                                                                           :sourceValue "1234567-1"}
                                                                                                    :osoite               {:katu                 {:value       "Testikatu 1 A 9242"
                                                                                                                                                  :modified    1530179094764
                                                                                                                                                  :sourceValue "Testikatu 1 A 9242"
                                                                                                                                                  :source      "krysp"}
                                                                                                                           :postinumero          {:value       "00380"
                                                                                                                                                  :sourceValue "00380"
                                                                                                                                                  :source      "krysp"
                                                                                                                                                  :modified    1530179094764}
                                                                                                                           :postitoimipaikannimi {:value       "HELSINKI"
                                                                                                                                                  :sourceValue "HELSINKI"
                                                                                                                                                  :source      "krysp"
                                                                                                                                                  :modified    1530179094764}
                                                                                                                           :maa                  {:value       "FIN"
                                                                                                                                                  :modified    1530179094764
                                                                                                                                                  :source      "krysp"
                                                                                                                                                  :sourceValue "FIN"}}
                                                                                                    :yhteyshenkilo        {:henkilotiedot {:etunimi           {:value       ""
                                                                                                                                                               :source      "krysp"
                                                                                                                                                               :sourceValue ""
                                                                                                                                                               :modified    1530179094764}
                                                                                                                                           :sukunimi          {:value       ""
                                                                                                                                                               :source      "krysp"
                                                                                                                                                               :modified    1530179094764
                                                                                                                                                               :sourceValue ""}
                                                                                                                                           :turvakieltoKytkin {:value       false
                                                                                                                                                               :sourceValue false
                                                                                                                                                               :source      "krysp"
                                                                                                                                                               :modified    1530179094764}}
                                                                                                                           :yhteystiedot  {:puhelin {:value       ""
                                                                                                                                                     :sourceValue ""
                                                                                                                                                     :modified    1530179094764
                                                                                                                                                     :source      "krysp"}
                                                                                                                                           :email   {:value       ""
                                                                                                                                                     :sourceValue ""
                                                                                                                                                     :modified    1530179094764
                                                                                                                                                     :source      "krysp"}}}}
                                                                                 :omistajalaji     {:value       nil
                                                                                                    :sourceValue nil
                                                                                                    :modified    1530179094764
                                                                                                    :source      "krysp"}
                                                                                 :muu-omistajalaji {:value       ""
                                                                                                    :modified    1530179094764
                                                                                                    :source      "krysp"
                                                                                                    :sourceValue ""}}}
                                        :varusteet                          {:viemariKytkin                       {:value       true
                                                                                                                   :sourceValue true
                                                                                                                   :modified    1530179094764
                                                                                                                   :source      "krysp"}
                                                                             :saunoja                             {:value       ""
                                                                                                                   :modified    1530179094764
                                                                                                                   :sourceValue ""
                                                                                                                   :source      "krysp"}
                                                                             :vesijohtoKytkin                     {:value       true
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :modified    1530179094764}
                                                                             :hissiKytkin                         {:value       false
                                                                                                                   :source      "krysp"
                                                                                                                   :modified    1530179094764
                                                                                                                   :sourceValue false}
                                                                             :vaestonsuoja                        {:value       ""
                                                                                                                   :source      "krysp"
                                                                                                                   :modified    1530179094764
                                                                                                                   :sourceValue ""}
                                                                             :kaasuKytkin                         {:value       false
                                                                                                                   :sourceValue false
                                                                                                                   :source      "krysp"
                                                                                                                   :modified    1530179094764}
                                                                             :aurinkopaneeliKytkin                {:value       false
                                                                                                                   :modified    1530179094764
                                                                                                                   :sourceValue false
                                                                                                                   :source      "krysp"}
                                                                             :liitettyJatevesijarjestelmaanKytkin {:value       false
                                                                                                                   :modified    1530179094764
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue false}
                                                                             :koneellinenilmastointiKytkin        {:value       true
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true
                                                                                                                   :modified    1530179094764}
                                                                             :sahkoKytkin                         {:value       true
                                                                                                                   :sourceValue true
                                                                                                                   :source      "krysp"
                                                                                                                   :modified    1530179094764}
                                                                             :lamminvesiKytkin                    {:value       true
                                                                                                                   :modified    1530179094764
                                                                                                                   :source      "krysp"
                                                                                                                   :sourceValue true}}
                                        :rakennusnro                        {:value       "002"
                                                                             :modified    1530179094764
                                                                             :source      "krysp"
                                                                             :sourceValue "002"}
                                        :verkostoliittymat                  {:viemariKytkin   {:value       true
                                                                                               :modified    1530179094764
                                                                                               :sourceValue true
                                                                                               :source      "krysp"}
                                                                             :vesijohtoKytkin {:value       true
                                                                                               :source      "krysp"
                                                                                               :modified    1530179094764
                                                                                               :sourceValue true}
                                                                             :sahkoKytkin     {:value       true
                                                                                               :source      "krysp"
                                                                                               :sourceValue true
                                                                                               :modified    1530179094764}
                                                                             :maakaasuKytkin  {:value       false
                                                                                               :source      "krysp"
                                                                                               :modified    1530179094764
                                                                                               :sourceValue false}
                                                                             :kaapeliKytkin   {:value       false
                                                                                               :modified    1530179094764
                                                                                               :source      "krysp"
                                                                                               :sourceValue false}}
                                        :kaytto                             {:rakentajaTyyppi {:value       nil
                                                                                               :source      "krysp"
                                                                                               :sourceValue nil
                                                                                               :modified    1530179094764}
                                                                             :kayttotarkoitus {:value       "021 rivitalot"
                                                                                               :source      "krysp"
                                                                                               :sourceValue "021 rivitalot"
                                                                                               :modified    1530179094764}}
                                        :huoneistot                         { :0 {:WCKytkin                {:value       true
                                                                                                            :sourceValue true
                                                                                                            :source      "krysp"
                                                                                                            :modified    1530179094764}
                                                                                  :huoneistoTyyppi         {:value       "asuinhuoneisto"
                                                                                                            :source      "krysp"
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue "asuinhuoneisto"}
                                                                                  :keittionTyyppi          {:value       "keittio"
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue "keittio"
                                                                                                            :source      "krysp"}
                                                                                  :huoneistoala            {:value       "108"
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "108"
                                                                                                            :modified    1530179094764}
                                                                                  :huoneluku               {:value       "4"
                                                                                                            :sourceValue "4"
                                                                                                            :source      "krysp"
                                                                                                            :modified    1530179094764}
                                                                                  :jakokirjain             {:value       ""
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue ""
                                                                                                            :source      "krysp"}
                                                                                  :ammeTaiSuihkuKytkin     {:value       true
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue true}
                                                                                  :saunaKytkin             {:value       true
                                                                                                            :sourceValue true
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"}
                                                                                  :huoneistonumero         {:value       "001"
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "001"
                                                                                                            :modified    1530179094764}
                                                                                  :porras                  {:value       "A"
                                                                                                            :sourceValue "A"
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"}
                                                                                  :muutostapa              {:value       nil
                                                                                                            :source      nil
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue nil}
                                                                                  :lamminvesiKytkin        {:value       true
                                                                                                            :sourceValue true
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"}
                                                                                  :parvekeTaiTerassiKytkin {:value       true
                                                                                                            :source      "krysp"
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue true}}
                                                                             :1  {:WCKytkin                {:modified    1530179094764
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue true
                                                                                                            :value       true}
                                                                                  :huoneistoTyyppi         {:value       "asuinhuoneisto"
                                                                                                            :sourceValue "asuinhuoneisto"
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"}
                                                                                  :keittionTyyppi          {:sourceValue "keittio"
                                                                                                            :source      "krysp"
                                                                                                            :modified    1530179094764
                                                                                                            :value       "keittio"}
                                                                                  :huoneistoala            {:modified    1530179094764
                                                                                                            :value       "106"
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue "106"}
                                                                                  :huoneluku               {:source      "krysp"
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue "4"
                                                                                                            :value       "4"}
                                                                                  :ammeTaiSuihkuKytkin     {:modified    1530179094764
                                                                                                            :sourceValue true
                                                                                                            :source      "krysp"
                                                                                                            :value       true}
                                                                                  :saunaKytkin             {:modified    1530179094764
                                                                                                            :source      "krysp"
                                                                                                            :sourceValue true
                                                                                                            :value       true}
                                                                                  :huoneistonumero         {:sourceValue "002"
                                                                                                            :value       "002"
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"}
                                                                                  :porras                  {:sourceValue "A"
                                                                                                            :source      "krysp"
                                                                                                            :value       "A"
                                                                                                            :modified    1530179094764}
                                                                                  :lamminvesiKytkin        {:value       true
                                                                                                            :sourceValue true
                                                                                                            :modified    1530179094764
                                                                                                            :source      "krysp"}
                                                                                  :parvekeTaiTerassiKytkin {:modified    1530179094764
                                                                                                            :sourceValue true
                                                                                                            :source      "krysp"
                                                                                                            :value       true}}}
                                        :lammitys                           {:lammitystapa    {:value       "vesikeskus"
                                                                                               :modified    1530179094764
                                                                                               :source      "krysp"
                                                                                               :sourceValue "vesikeskus"}
                                                                             :lammonlahde     {:value       "kevyt polttoöljy"
                                                                                               :source      "krysp"
                                                                                               :modified    1530179094764
                                                                                               :sourceValue "kevyt polttoöljy"}
                                                                             :muu-lammonlahde {:value       ""
                                                                                               :modified    1530179094764
                                                                                               :source      "krysp"
                                                                                               :sourceValue ""}}
                                        :kunnanSisainenPysyvaRakennusnumero {:value       ""
                                                                             :sourceValue ""
                                                                             :source      "krysp"
                                                                             :modified    1530179094764}
                                        :perusparannuskytkin                {:value       false
                                                                             :sourceValue false
                                                                             :source      nil
                                                                             :modified    1530179094764}
                                        :rakennustietojaEimuutetaKytkin     {:value       false
                                                                             :modified    1530179094764
                                                                             :source      nil
                                                                             :sourceValue false}
                                        :rakenne                            {:rakentamistapa      {:value       "paikalla"
                                                                                                   :modified    1530179094764
                                                                                                   :source      "krysp"
                                                                                                   :sourceValue "paikalla"}
                                                                             :kantavaRakennusaine {:value       "tiili"
                                                                                                   :sourceValue "tiili"
                                                                                                   :source      "krysp"
                                                                                                   :modified    1530179094764}
                                                                             :muuRakennusaine     {:value       ""
                                                                                                   :sourceValue ""
                                                                                                   :source      "krysp"
                                                                                                   :modified    1530179094764}
                                                                             :julkisivu           {:value       "tiili"
                                                                                                   :sourceValue "tiili"
                                                                                                   :source      "krysp"
                                                                                                   :modified    1530179094764}
                                                                             :muuMateriaali       {:value       ""
                                                                                                   :sourceValue ""
                                                                                                   :source      "krysp"
                                                                                                   :modified    1530179094764}}
                                        :osoite                             {:osoitenumero2        {:value       "5"
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764
                                                                                                    :sourceValue "5"}
                                                                             :huoneisto            {:value       ""
                                                                                                    :modified    1530179094764
                                                                                                    :sourceValue ""
                                                                                                    :source      "krysp"}
                                                                             :jakokirjain          {:value       ""
                                                                                                    :sourceValue ""
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764}
                                                                             :kunta                {:value       "245"
                                                                                                    :sourceValue "245"
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764}
                                                                             :jakokirjain2         {:value       ""
                                                                                                    :sourceValue ""
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764}
                                                                             :postinumero          {:value       "04200"
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764
                                                                                                    :sourceValue "04200"}
                                                                             :porras               {:value       ""
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764
                                                                                                    :sourceValue ""}
                                                                             :osoitenumero         {:value       "3"
                                                                                                    :modified    1530179094764
                                                                                                    :source      "krysp"
                                                                                                    :sourceValue "3"}
                                                                             :postitoimipaikannimi {:value       "KERAVA"
                                                                                                    :source      "krysp"
                                                                                                    :modified    1530179094764
                                                                                                    :sourceValue "KERAVA"}
                                                                             :maa                  {:value       "FIN"
                                                                                                    :sourceValue "FIN"
                                                                                                    :modified    1530179094764
                                                                                                    :source      "krysp"}
                                                                             :lahiosoite           {:value       "Kyllikintie"
                                                                                                    :modified    1530179094764
                                                                                                    :sourceValue "Kyllikintie"
                                                                                                    :source      "krysp"}}
                                        :mitat                              {:tilavuus                       {:value       "837"
                                                                                                              :source      "krysp"
                                                                                                              :modified    1530179094764
                                                                                                              :sourceValue "837"}
                                                                             :kerrosala                      {:value       "281"
                                                                                                              :source      "krysp"
                                                                                                              :modified    1530179094764
                                                                                                              :sourceValue "281"}
                                                                             :rakennusoikeudellinenKerrosala {:value       ""
                                                                                                              :modified    1530179094764
                                                                                                              :source      "krysp"
                                                                                                              :sourceValue ""}
                                                                             :kokonaisala                    {:value       "281"
                                                                                                              :modified    1530179094764
                                                                                                              :source      "krysp"
                                                                                                              :sourceValue "281"}
                                                                             :kerrosluku                     {:value       "2"
                                                                                                              :source      "krysp"
                                                                                                              :sourceValue "2"
                                                                                                              :modified    1530179094764}
                                                                             :kellarinpinta-ala              {:value       ""
                                                                                                              :modified    1530179094764
                                                                                                              :source      "krysp"
                                                                                                              :sourceValue ""}}
                                        :manuaalinen_rakennusnro            {:value       ""
                                                                             :sourceValue ""
                                                                             :source      "krysp"
                                                                             :modified    1530179094764}
                                        :luokitus                           {:energialuokka                {:value       nil
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue nil
                                                                                                            :source      "krysp"}
                                                                             :energiatehokkuusluku         {:value       ""
                                                                                                            :source      "krysp"
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue ""}
                                                                             :energiatehokkuusluvunYksikko {:value       "kWh/m2"
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue "kWh/m2"
                                                                                                            :source      "krysp"}
                                                                             :paloluokka                   {:value       nil
                                                                                                            :modified    1530179094764
                                                                                                            :sourceValue nil
                                                                                                            :source      "krysp"}}
                                        :valtakunnallinenNumero             {:value       "199887766E"
                                                                             :modified    1530179094764
                                                                             :source      "krysp"
                                                                             :sourceValue "199887766E"}}}

                         {:id          "5b34a9d3cea1d0f410db2409"
                          :schema-info {:name        "rakennuspaikka"
                                        :version     1
                                        :type        :location
                                        :approvable  true
                                        :order       2
                                        :copy-action :clear}
                          :created     1530178002437
                          :data        {:kiinteisto           {:maaraalaTunnus   {:value nil}
                                                               :tilanNimi        {:value ""}
                                                               :rekisterointipvm {:value ""}
                                                               :maapintaala      {:value ""}
                                                               :vesipintaala     {:value ""}}
                                        :hallintaperuste      {:value nil}
                                        :kaavanaste           {:value nil}
                                        :kaavatilanne         {:value nil}
                                        :hankkeestaIlmoitettu {:hankkeestaIlmoitettuPvm {:value nil}}}}


                         {:id          "5b34a9d3cea1d0f410db240c"
                          :schema-info {:name      "rakennusjatesuunnitelma"
                                        :order     200
                                        :blacklist [:neighbor]}
                          :created     1530178002437
                          :data        {:rakennusJaPurkujate {:0 {:jatetyyppi       {:value    "kipsi"
                                                                                     :modified 1530178832555}
                                                                  :suunniteltuMaara {:value    "1"
                                                                                     :modified 1530178835793}
                                                                  :yksikko          {:value    "tonni"
                                                                                     :modified 1530178840071}
                                                                  :painoT           {:value    "1"
                                                                                     :modified 1530178844422}}
                                                              :1 {:jatetyyppi       {:value    "lasi"
                                                                                     :modified 1530178850654}
                                                                  :suunniteltuMaara {:value    "20"
                                                                                     :modified 1530178853738}
                                                                  :yksikko          {:value    "kg"
                                                                                     :modified 1530178855943}
                                                                  :painoT           {:value    "0"
                                                                                     :modified 1530178860517}}}
                                        :vaarallisetAineet   {:0 {:vaarallinenainetyyppi {:value    "aerosolipullot"
                                                                                          :modified 1530178864753}
                                                                  :suunniteltuMaara      {:value    "10"
                                                                                          :modified 1530178867910}
                                                                  :yksikko               {:value    "kg"
                                                                                          :modified 1530178872036}
                                                                  :painoT                {:value    "0"
                                                                                          :modified 1530178891502}}}}}]
   :location-wgs84       [25.266 60.36938]
   :id                   "LP-753-2018-90008"
   :propertyId           "75341600550007"
   :location             [404369.304 6693806.957]
   :inspection-summaries []
   :schema-version       1})

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Finalize verdict"
    (let [command {:user        {:id        "user-id" :username "user-email"
                                 :firstName "Hello"   :lastName "World"}
                   :created     12345
                   :application application-r
                   :system      (test-system/get-common-components)}
          verdict {:id             "vid"
                   :schema-version 1
                   :category       "r"
                   :state          "draft"
                   :data           {:language     "fi"
                                    :verdict-code "myonnetty"
                                    :handler      "Foo Bar"
                                    :verdict-date 876543}
                   :modified       12300
                   :template       {:inclusions ["verdict-code"
                                                 "handler"
                                                 "verdict-date"]
                                    :giver      "viranhaltija"}
                   :references     {:boardname "Broad board abroad"}}
          c-v-a   (hash-map :command command
                            :verdict verdict
                            :application application-r)]

      (facts "finalize pipeline"
        (fact "clear-publishing-state-for-verdict is called"
          (let [command (assoc command :data {:verdict-id "123"}
                               :application {:pate-verdicts [{:id "123" :state {:_value "draft"}}]})]
            (process-finalize-pipeline {:command     command
                                        :application application-r
                                        :verdict     verdict}
                                       finalize--verdict)
            => (throws ExceptionInfo #"Value does not match schema")
            (provided
              (command->verdict command true) => {:state "publishing-verdict"}
              (update-verdict-state command "123" {:_value "draft"}) => "ok")))

        (fact "returns ok when pipeline is fine"
          (let [vid     (mongo/create-id)
                verdict (assoc verdict :id vid)
                command (assoc command :data {:verdict-id vid}
                               :application {:pate-verdicts [{:id vid :state {:_value "draft"}}]})]
            (process-finalize-pipeline {:command     command
                                        :application application-r
                                        :verdict     verdict}
                                       finalize--verdict
                                       finalize--pdf-tags)
            => ok?
            (provided
              (pdf/verdict-tags anything anything anything) => [:html [:h1 "testi"]]
              (create-verdict-pdf anything anything) => {:updates "jee"}
              (app-at-time-of-verdict-pdf anything) => nil
              (verdict-update command "jee") => nil
              (clear-publishing-state-for-verdict anything) => nil)))

        (fact "returns fail when pdf creations throws"
          (let [vid     (mongo/create-id)
                verdict (assoc verdict :id vid)
                command (assoc command :data {:verdict-id vid}
                               :application {:pate-verdicts [{:id vid :state {:_value "draft"}}]})]
            (process-finalize-pipeline {:command     command
                                        :application application-r
                                        :verdict     verdict}
                                       finalize--verdict
                                       finalize--pdf-tags)
            => {:ok false :text "error.attachment-pdf-generation-failed"}
            (provided
              (pdf/verdict-tags anything anything anything) => [:html [:h1 "testi"]]
              (create-verdict-pdf anything anything) => (fail! :pate.pdf-verdict-error)
              (app-at-time-of-verdict-pdf anything) => nil :times 0
              (clear-publishing-state-for-verdict anything) => nil))))

      (fact finalize--verdict
        (finalize--verdict c-v-a)
        => {:verdict (util/deep-merge verdict
                                      {:archive   {:verdict-date  876543
                                                   :verdict-giver "Foo Bar"}
                                       :published {:published 12345}
                                       :state     {:_value    "published"
                                                   :_user     "user-email"
                                                   :_modified 12345}})
            :updates {$set {:pate-verdicts.$.archive             {:verdict-date  876543
                                                                  :verdict-giver "Foo Bar"}
                            :pate-verdicts.$.data.handler        "Foo Bar"
                            :pate-verdicts.$.data.language       "fi"
                            :pate-verdicts.$.data.verdict-code   "myonnetty"
                            :pate-verdicts.$.data.verdict-date   876543
                            :pate-verdicts.$.published.published 12345
                            :pate-verdicts.$.template.inclusions ["verdict-code"
                                                                  "handler"
                                                                  "verdict-date"]
                            :pate-verdicts.$.state               {:_value    "published"
                                                                  :_user     "user-email"
                                                                  :_modified 12345}}}})

      (fact "finalize--application-state: waste plan - If there is a waste plan but a waste report does not exist, it is added to documents"
        (-> application-r :documents count) => 3
        (let [{:keys [application updates commit-fn]} (finalize--application-state c-v-a)]
          application => (merge application {:state :verdictGiven})
          updates => {$push {:history {:state :verdictGiven
                                       :ts    12345
                                       :user  {:firstName "Hello"
                                               :id        "user-id"
                                               :lastName  "World"
                                               :username  "user-email"}}}
                      $set  {:modified 12345
                             :state    :verdictGiven}}
          ;; assoc-in is used just to avoid using provided, which did
          ;; not like having facts inside the let binding
          (assoc-in (commit-fn (assoc c-v-a :application application) :dry-run)
                    [:mongo-updates $set "documents.3" :id]  "static-id")
          => {:mongo-updates
              {$set {"documents.3" {:id          "static-id"
                                    :created     12345
                                    :data        {:availableMaterials  {:0 {:aines  {:value ""}
                                                                            :kuvaus {:value ""}
                                                                            :maara  {:value ""}}}
                                                  :contact             {:email {:value ""}
                                                                        :name  {:value ""}
                                                                        :phone {:value ""}}
                                                  :rakennusJaPurkujate {:suunniteltuJate      {:0 {:jatetyyppi       {:modified nil
                                                                                                                      :value    "kipsi"}
                                                                                                   :painoT           {:modified nil
                                                                                                                      :value    "1"}
                                                                                                   :suunniteltuMaara {:modified nil
                                                                                                                      :value    "1"}
                                                                                                   :yksikko          {:modified nil
                                                                                                                      :value    "tonni"}}
                                                                                               :1 {:jatetyyppi       {:modified nil
                                                                                                                      :value    "lasi"}
                                                                                                   :painoT           {:modified nil
                                                                                                                      :value    "0"}
                                                                                                   :suunniteltuMaara {:modified nil
                                                                                                                      :value    "20"}
                                                                                                   :yksikko          {:modified nil
                                                                                                                      :value    "kg"}}}
                                                                        :suunnittelematonJate {:0 {:jatteenToimituspaikka {:value ""}
                                                                                                   :painoT                {:value ""}
                                                                                                   :toteutunutMaara       {:value ""}}}}
                                                  :vaarallisetAineet   {:suunniteltuJate      {:0 {:painoT                {:modified nil
                                                                                                                           :value    "0"}
                                                                                                   :suunniteltuMaara      {:modified nil
                                                                                                                           :value    "10"}
                                                                                                   :vaarallinenainetyyppi {:modified nil
                                                                                                                           :value    "aerosolipullot"}
                                                                                                   :yksikko               {:modified nil
                                                                                                                           :value    "kg"}}}
                                                                        :suunnittelematonJate {:0 {:jatteenToimituspaikka {:value ""}
                                                                                                   :painoT                {:value ""}
                                                                                                   :toteutunutMaara       {:value ""}}}}}
                                    :schema-info {:name    "rakennusjateselvitys"
                                                  :version 1}}}}}))

      (fact "finalize--application-state: waste report - If a waste report document exists, it is updated"
        (let [c-v-a                                   (update-in c-v-a [:application :documents]
                                                                 #(conj % rakennusjateselvitys))
              {:keys [application updates commit-fn]} (finalize--application-state c-v-a)
              waste-report-id                         (:id rakennusjateselvitys)]
          (commit-fn (assoc c-v-a :application application) :dry-run)
          => {:mongo-query   {:documents {$elemMatch {:id waste-report-id}}}
              :mongo-updates {$set   {"documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.jatetyyppi.modified"          12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.jatetyyppi.value"             "kipsi"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.painoT.modified"              12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.painoT.value"                 "1"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.suunniteltuMaara.modified"    12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.suunniteltuMaara.value"       "1"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.yksikko.modified"             12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.0.yksikko.value"                "tonni"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.jatetyyppi.modified"          12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.jatetyyppi.value"             "lasi"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.painoT.modified"              12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.painoT.value"                 "0"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.suunniteltuMaara.modified"    12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.suunniteltuMaara.value"       "20"
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.yksikko.modified"             12345
                                      "documents.$.data.rakennusJaPurkujate.suunniteltuJate.1.yksikko.value"                "kg"
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.painoT.modified"                12345
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.painoT.value"                   "0"
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.suunniteltuMaara.modified"      12345
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.suunniteltuMaara.value"         "10"
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.vaarallinenainetyyppi.modified" 12345
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.vaarallinenainetyyppi.value"    "aerosolipullot"
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.yksikko.modified"               12345
                                      "documents.$.data.vaarallisetAineet.suunniteltuJate.0.yksikko.value"                  "kg"
                                      :modified                                                                             12345}
                              $unset {"documents.$.data.rakennusJaPurkujate.suunniteltuJate.2" ""
                                      "documents.$.meta.rakennusJaPurkujate.suunniteltuJate.2" ""}}
              :post-results  []}))

      (fact "finalize--application-state: no waste plan - If there is no waste plan, waste report document is not added or updated"
        (let [application                             (update application-r :documents drop-last)
              {:keys [application updates commit-fn]} (finalize--application-state (assoc c-v-a :application application))]
          application => (merge application
                                {:state :verdictGiven})
          updates => {$push {:history {:state :verdictGiven
                                       :ts    12345
                                       :user  {:firstName "Hello"
                                               :id        "user-id"
                                               :lastName  "World"
                                               :username  "user-email"}}}
                      $set  {:modified 12345
                             :state    :verdictGiven}}
          (commit-fn (assoc c-v-a :application application) :dry-run) => nil))

      (let [verdict (-> verdict
                        (assoc-in [:data :reviews] ["5a156dd40e40adc8ee064463"
                                                    "6a156dd40e40adc8ee064463"])
                        (assoc-in [:data :reviews-included] true)
                        (assoc-in [:data :foremen] ["erityis-tj"
                                                    "iv-tj"
                                                    "vastaava-tj"
                                                    "vv-tj"])
                        (assoc-in [:data :foremen-included] true)
                        (assoc-in [:data :plans] ["5a156ddf0e40adc8ee064464"
                                                  "6a156ddf0e40adc8ee064464"])
                        (assoc-in [:data :plans-included] true)
                        (assoc :references {:foremen ["erityis-tj" "iv-tj" "vastaava-tj" "vv-tj" "tj"]
                                            :plans   [{:id "5a156ddf0e40adc8ee064464"
                                                       :fi "Suunnitelmat"
                                                       :sv "Planer"
                                                       :en "Plans"}
                                                      {:id "6a156ddf0e40adc8ee064464"
                                                       :fi "Suunnitelmat2"
                                                       :sv "Planer2"
                                                       :en "Plans2"}]
                                            :reviews [{:id   "5a156dd40e40adc8ee064463"
                                                       :fi   "Katselmus"
                                                       :sv   "Syn"
                                                       :en   "Review"
                                                       :type "muu-katselmus"}
                                                      {:id   "6a156dd40e40adc8ee064463"
                                                       :fi   "Katselmus2"
                                                       :sv   "Syn2"
                                                       :en   "Review2"
                                                       :type "paikan-merkitseminen"}]})
                        (update-in [:template :inclusions] concat [:reviews :reviews-included
                                                                   :foremen :foremen-included
                                                                   :plans :plans-included]))
            review1 {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:katselmuksenLaji   {:modified 12345
                                                        :value    "muu katselmus"}
                                   :katselmus          {:huomautukset {:kuvaus  {:value ""}
                                                                       :toteaja {:value ""}}
                                                        :lasnaolijat  {:value ""}
                                                        :poikkeamat   {:value ""}
                                                        :tiedoksianto {:value false}}
                                   :muuTunnus          {:value ""}
                                   :muuTunnusSovellus  {:value ""}
                                   :rakennus           {:0 {:rakennus {:jarjestysnumero                    {:modified 12345
                                                                                                            :value    "1"}
                                                                       :kiinttun                           {:modified 12345
                                                                                                            :value    "75341600550007"}
                                                                       :kunnanSisainenPysyvaRakennusnumero {:modified 12345
                                                                                                            :value    nil}
                                                                       :rakennusnro                        {:modified 12345
                                                                                                            :value    "002"}
                                                                       :valtakunnallinenNumero             {:modified 12345
                                                                                                            :value    "199887766E"}}
                                                            :tila     {:kayttoonottava {:modified 12345
                                                                                        :value    false}
                                                                       :tila           {:modified 12345
                                                                                        :value    ""}}}}
                                   :vaadittuLupaehtona {:modified 12345 :value true}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-katselmus"
                                   :subtype :review
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Katselmus"}
            review2 {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:katselmuksenLaji   {:modified 12345
                                                        :value    "rakennuksen paikan merkitseminen"}
                                   :katselmus          {:huomautukset {:kuvaus  {:value ""}
                                                                       :toteaja {:value ""}}
                                                        :lasnaolijat  {:value ""}
                                                        :poikkeamat   {:value ""}
                                                        :tiedoksianto {:value false}}
                                   :muuTunnus          {:value ""}
                                   :muuTunnusSovellus  {:value ""}
                                   :rakennus           {:0 {:rakennus {:jarjestysnumero                    {:modified 12345
                                                                                                            :value    "1"}
                                                                       :kiinttun                           {:modified 12345
                                                                                                            :value    "75341600550007"}
                                                                       :kunnanSisainenPysyvaRakennusnumero {:modified 12345
                                                                                                            :value    nil}
                                                                       :rakennusnro                        {:modified 12345
                                                                                                            :value    "002"}
                                                                       :valtakunnallinenNumero             {:modified 12345
                                                                                                            :value    "199887766E"}}
                                                            :tila     {:kayttoonottava {:modified 12345
                                                                                        :value    false}
                                                                       :tila           {:modified 12345
                                                                                        :value    ""}}}}
                                   :vaadittuLupaehtona {:modified 12345 :value true}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-katselmus"
                                   :subtype :review
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Katselmus2"}
            plan1   {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:kuvaus                      {:value ""}
                                   :maarays                     {:value    "Suunnitelmat"
                                                                 :modified 12345}
                                   :vaaditutErityissuunnitelmat {:value ""}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-lupamaarays"
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Suunnitelmat"}
            plan2   {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:kuvaus                      {:value ""}
                                   :maarays                     {:value    "Suunnitelmat2"
                                                                 :modified 12345}
                                   :vaaditutErityissuunnitelmat {:value ""}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-lupamaarays"
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Suunnitelmat2"}
            tj1     {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:asiointitunnus  {:value ""}
                                   :kuntaRoolikoodi {:value    "erityisalojen työnjohtaja"
                                                     :modified 12345}
                                   :osapuolena      {:value false}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                   :subtype :foreman
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Erityisalojen työnjohtaja"}
            tj2     {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:asiointitunnus  {:value ""}
                                   :kuntaRoolikoodi {:value    "IV-työnjohtaja"
                                                     :modified 12345}
                                   :osapuolena      {:value false}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                   :subtype :foreman
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Ilmanvaihtotyönjohtaja"}
            tj3     {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:asiointitunnus  {:value ""}
                                   :kuntaRoolikoodi {:value    "vastaava työnjohtaja"
                                                     :modified 12345}
                                   :osapuolena      {:value false}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                   :subtype :foreman
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Vastaava työnjohtaja"}
            tj4     {:assignee    {}
                     :closed      nil
                     :created     12345
                     :data        {:asiointitunnus  {:value ""}
                                   :kuntaRoolikoodi {:value    "KVV-työnjohtaja"
                                                     :modified 12345}
                                   :osapuolena      {:value false}}
                     :duedate     nil
                     :id          "id"
                     :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                   :subtype :foreman
                                   :type    :task
                                   :version 1}
                     :source      {:id "vid" :type "verdict"}
                     :state       :requires_user_action
                     :taskname    "Vesi- ja viemärityönjohtaja"}
            house   {:area           "281"
                     :buildingId     "199887766E"
                     :description    ""
                     :index          "1"
                     :localShortId   "002"
                     :location       nil
                     :location-wgs84 nil
                     :nationalId     "199887766E"
                     :operationId    "5b34a9d2cea1d0f410db2403"
                     :propertyId     "75341600550007"
                     :usage          "021 rivitalot"
                     :building-type  ""
                     :apartments     nil}]
        (env/with-feature-value :new-foreman-invite false
          (fact "finalize--building-and-tasks: tasks and buildings"
            (finalize--buildings-tasks-and-docs (assoc c-v-a :verdict verdict))
            => {:application (assoc application-r
                                    :buildings [house]
                                    :tasks [review1 review2
                                            plan1 plan2
                                            tj1 tj2 tj3 tj4])
                :updates     {$push {:tasks {$each [review1 review2
                                                    plan1 plan2
                                                    tj1 tj2 tj3 tj4]}}
                              $set  {:buildings [house]}}}
            (provided (lupapalvelu.mongo/create-id) => "id"))
          (fact "finalize--building-and-tasks: tasks, no buildings"
            (let [application (update application-r :documents rest)
                  review1     (assoc-in review1 [:data :rakennus] {})
                  review2     (assoc-in review2 [:data :rakennus] {})]
              (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                         :application application
                                                         :verdict verdict))
              => {:application (assoc application
                                      :tasks [review1 review2
                                              plan1 plan2
                                              tj1 tj2 tj3 tj4])
                  :updates     {$push {:tasks {$each [review1 review2
                                                      plan1 plan2
                                                      tj1 tj2 tj3 tj4]}}
                                $set  {:buildings []}}}
              (provided (lupapalvelu.mongo/create-id) => "id")))
          (fact "finalize--building-and-tasks: reviews not included, plans, foremen, buildings"
            (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                       :verdict (assoc-in verdict [:data :reviews-included] false)))
            => {:application (assoc application-r
                                    :buildings [house]
                                    :tasks [plan1 plan2 tj1 tj2 tj3 tj4])
                :updates     {$push {:tasks {$each [plan1 plan2 tj1 tj2 tj3 tj4]}}
                              $set  {:buildings [house]}}}
            (provided (lupapalvelu.mongo/create-id) => "id"))

          (let [new-app (assoc-in application-r [:primaryOperation :description] "Hello world!")]
            (fact "finalize--building-and-tasks: buildings, no tasks"
              (let [house (assoc house :description "Hello world!")]
                (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                           :verdict (-> verdict
                                                                        (assoc-in [:data :reviews] [])
                                                                        (assoc-in [:data :plans-included] false)
                                                                        (assoc-in [:data :foremen-included] false))
                                                           :application new-app))
                => {:application (assoc new-app
                                        :buildings [house])
                    :updates     {$set {:buildings [house]}}})))
          (let [new-app (update-in application-r
                                   [:documents 0]
                                   #(-> %
                                        (assoc-in [:schema-info :name] "rakennuskohteenLaajentaminen")
                                        (assoc :data {:ryhti                   {}
                                                      :manuaalinen_rakennusnro nil
                                                      :rakennuskohteen-osa     {:ulkokuorentiedot {:kokonaisala "100"}}
                                                      :pysyva-rakennustunnus   "1223344556R"})))]
            (fact "finalize--building-and-tasks: RYHTI"
              (let [house (merge house
                                 {:nationalId   "1223344556R"
                                  :buildingId   "1223344556R"
                                  :area         "100"
                                  :localShortId nil
                                  :usage        ""})]
                (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                           :verdict (-> verdict
                                                                        (assoc-in [:data :reviews] [])
                                                                        (assoc-in [:data :plans-included] false)
                                                                        (assoc-in [:data :foremen-included] false))
                                                           :application new-app))
                => {:application (assoc new-app :buildings [house])
                    :updates     {$set {:buildings [house]}}})))
          (fact "finalize--building-and-tasks: buildings, reviews, plans, conditions, foremen"
            (let [c1 (-> plan1
                         (assoc-in [:data :maarays :value] "Hello")
                         (assoc :taskname "Hello"))
                  c2 (-> plan1
                         (assoc-in [:data :maarays :value] "World")
                         (assoc :taskname "World"))]
              (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                         :verdict (assoc-in verdict [:data :conditions]
                                                                            {:c1 {:condition "Hello"}
                                                                             :c2 {:condition "World"}})))
              => {:application (assoc application-r
                                      :buildings [house]
                                      :tasks [review1 review2 plan1 plan2 c1 c2 tj1 tj2 tj3 tj4])
                  :updates     {$push {:tasks {$each [review1 review2 plan1 plan2 c1 c2 tj1 tj2 tj3 tj4]}}
                                $set  {:buildings [house]}}}
              (provided (lupapalvelu.mongo/create-id) => "id"))))
        (env/with-feature-value :new-foreman-invite true
          (facts "finalize--building-and-tasks: new foreman documents"
            (fact "adds all defined foreman documents"
              (let [application (update application-r :documents rest)
                    result      (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                                           :application application
                                                                           :verdict verdict))]

                (->> result :application :documents (keep #(get-in % [:data :kuntaRoolikoodi :value])))
                => (just ["KVV-työnjohtaja" "vastaava työnjohtaja" "IV-työnjohtaja" "erityisalojen työnjohtaja"] :in-any-order)

                (->> result :application :documents (filter #(-> % :schema-info :name (= "rakennuspaikka"))) count)
                => 1

                (->> (get-in result [:updates $push :documents $each]) (keep #(get-in % [:data :kuntaRoolikoodi :value])))
                => (just ["KVV-työnjohtaja" "vastaava työnjohtaja" "IV-työnjohtaja" "erityisalojen työnjohtaja"] :in-any-order)))
            (fact "doesn't add documents already found on the application"
              (let [application (-> (update application-r :documents rest)
                                    (update :documents into [(foreman/new-foreman-document "vastaava työnjohtaja" command)]))
                    result      (finalize--buildings-tasks-and-docs (assoc c-v-a
                                                                           :application application
                                                                           :verdict verdict))]

                (->> result :application :documents (keep #(get-in % [:data :kuntaRoolikoodi :value])))
                => (just ["KVV-työnjohtaja" "vastaava työnjohtaja" "IV-työnjohtaja" "erityisalojen työnjohtaja"] :in-any-order)

                (->> (get-in result [:updates $push :documents $each]) (keep #(get-in % [:data :kuntaRoolikoodi :value])))
                => (just ["KVV-työnjohtaja" "IV-työnjohtaja" "erityisalojen työnjohtaja"] :in-any-order))))))

      (fact "finalize--inspection-summary: inspection summaries disabled"
        (inspection-summary/finalize--inspection-summary c-v-a) => nil
        (provided (lupapalvelu.organization/get-organization "753-R")
                  => {:inspection-summaries-enabled false}))
      (fact "finalize--inspection-summary: inspection summaries enabled"
        (inspection-summary/finalize--inspection-summary c-v-a)
        => {:application (assoc
                           application-r
                           :inspection-summaries [{:id      "id"
                                                   :name    "Inspector Template"
                                                   :op      {:description nil
                                                             :id          "5b34a9d2cea1d0f410db2403"
                                                             :name        "sisatila-muutos"}
                                                   :targets '({:finished    false
                                                               :id          "id"
                                                               :target-name "First item"}
                                                              {:finished    false
                                                               :id          "id"
                                                               :target-name "Second item"})}])
            :updates     {$push {:inspection-summaries {"$each" [{:id      "id"
                                                                  :name    "Inspector Template"
                                                                  :op      {:description nil
                                                                            :id          "5b34a9d2cea1d0f410db2403"
                                                                            :name        "sisatila-muutos"}
                                                                  :targets '({:finished    false
                                                                              :id          "id"
                                                                              :target-name "First item"}
                                                                             {:finished    false
                                                                              :id          "id"
                                                                              :target-name "Second item"})}]}}}}
        (provided
          (lupapalvelu.mongo/create-id) => "id"
          (lupapalvelu.organization/get-organization "753-R")
          => {:inspection-summaries-enabled true
              :inspection-summary           {:operations-templates
                                             {:sisatila-muutos ["5b35d34ecea1d0863491149c"]}
                                             :templates [{:name     "Inspector Template",
                                                          :modified 1530254158347,
                                                          :id       "5b35d34ecea1d0863491149c",
                                                          :items    ["First item" "Second item"]}]}}))

      (let [att-paatosote {:id            "att1"
                           :type          {:type-id    "paatosote"
                                           :type-group "paatoksenteko"}
                           :target        {:type "verdict"
                                           :id   (:id verdict)}
                           :latestVersion {:fileId "a"}}
            att-ilmoitus  {:id            "att2"
                           :type          {:type-id    "ilmoitus"
                                           :type-group "paatoksenteko"}
                           :latestVersion {:fileId "b"}}
            att-empty     {:id   "att3"
                           :type {:type-id    "ilmoitus"
                                  :type-group "paatoksenteko"}}
            att-ilmoitus2 {:id            "att4"
                           :type          {:type-id    "ilmoitus"
                                           :type-group "paatoksenteko"}
                           :target        {:type "verdict"
                                           :id   (:id verdict)}
                           :latestVersion {:fileId "c"}}
            att-ilmoitus3 {:id            "att5"
                           :type          {:type-id    "ilmoitus"
                                           :type-group "paatoksenteko"}
                           :latestVersion {:fileId "d"}}
            att-paatos    {:id            "att6"
                           :type          {:type-id    "paatos"
                                           :type-group "paatoksenteko"}
                           :latestVersion {:fileId "e"}}
            att-paatos2   {:id            "att7"
                           :type          {:type-id    "paatos"
                                           :type-group "paatoksenteko"}
                           :target        {:type "verdict"
                                           :id   (:id verdict)}
                           :latestVersion {:fileId "f"}}]
        (fact "finalize--attachments: no attachments"
          (finalize--attachments c-v-a)
          => (contains {:application (assoc application-r :attachments [])
                        :updates     {$set {:pate-verdicts.$.data.attachments          []
                                            :pate-verdicts.$.data.selected-attachments []}}
                        :verdict     (-> verdict
                                         (assoc-in [:data :attachments] [])
                                         (assoc-in [:data :selected-attachments] []))})
          (provided
            (att/attachment-array-updates
              "LP-753-2018-90008" anything
              :metadata.nakyvyys "julkinen"
              :metadata.draftTarget false
              :target {:type "verdict" :id "vid"}
              :readOnly true :locked true)
            => nil))
        (fact "verdict-attachment-items"
          (verdict-attachment-items {:application {:attachments [att-paatosote att-ilmoitus att-empty]}}
                                    {:id   (:id verdict)
                                     :data {:atts ["att2" "att3"]}}
                                    :atts)
          => (just [{:id "att1" :type-group "paatoksenteko" :type-id "paatosote"}
                    {:id "att2" :type-group "paatoksenteko" :type-id "ilmoitus"}]
                   :in-any-order))
        (fact "attachment-items"
          (let [{:keys [update-fn]} (attachment-items
                                      {:application {:attachments [att-paatosote att-ilmoitus att-empty
                                                                   att-ilmoitus2 att-ilmoitus3 att-paatos
                                                                   att-paatos2]
                                                     :permitType  "R"}}
                                      {:id   (:id verdict)
                                       :data {:attachments ["att2" "att3" "gone" "att5" "att6"]}})]
            (update-fn {:foo 8})
            => (just {:foo         8
                      :attachments (just [{:type-group "paatoksenteko" :type-id "paatosote" :amount 1}
                                          {:type-group "paatoksenteko" :type-id "ilmoitus" :amount 3}
                                          {:type-group "paatoksenteko" :type-id "paatos" :amount 2}]
                                         :in-any-order)})))
        (fact "finalize--attachments: new, selected and empty"
          (finalize--attachments (-> c-v-a
                                     (assoc-in [:command :application :attachments]
                                               [att-paatosote att-ilmoitus att-empty
                                                att-ilmoitus2 att-ilmoitus3 att-paatos
                                                att-paatos2])
                                     (assoc-in [:verdict :data :attachments]
                                               ["att2" "att3"  "gone" "att5" "att6"])))
          => (contains {:updates (just {$set (just {:pate-verdicts.$.data.attachments
                                                    (just [{:amount     2
                                                            :type-group "paatoksenteko"
                                                            :type-id    "paatos"}
                                                           {:amount     1
                                                            :type-group "paatoksenteko"
                                                            :type-id    "paatosote"}
                                                           {:amount     3
                                                            :type-group "paatoksenteko"
                                                            :type-id    "ilmoitus"}]
                                                          :in-any-order)
                                                    :pate-verdicts.$.data.selected-attachments
                                                    ["att2" "att3"  "gone" "att5" "att6"]})})})
          (provided
            (att/attachment-array-updates
              "LP-753-2018-90008" anything
              :metadata.nakyvyys "julkinen"
              :metadata.draftTarget false :target {:type "verdict" :id "vid"}
              :readOnly true :locked true)
            => nil)))

      (let [att-ref1  {:id "att-ref1" :target {:type "verdict-ref" :id "vid"}}
            ;; Non-sensical
            att-ref2  {:id "att-ref2" :target {:type "verdict-ref" :id "vid"} :metadata {:draftTarget true}}
            att-src   {:id "att-src" :source {:type "verdicts" :id "vid"}}
            att-draft {:id "att-draft" :target {:type "verdict" :id "vid"} :metadata {:draftTarget true}}
            att-trg   {:id "att-trg" :target {:type "verdict" :id "vid"} :metadata {:draftTarget false}}
            att-nod1  {:id "att-nod1" :target {:type "verdict" :id "vid"}}
            att-nod2  {:id "att-nod2" :target {:type "verdict" :id "vid"} :metadata {:hello "world"}}
            att-oth1  {:id "att-oth1" :source {:type "verdicts" :id "oth"}}
            att-oth2  {:id "att-oth2" :target {:type "verdict" :id "oth"} :metadata {:draftTarget false}}
            att-oth3  {:id "att-oth3" :target {:type "verdict" :id "oth"}}
            att-oth4  {:id "att-oth4" :target {:type "verdict-ref" :id "oth"} :metadata {:draftTarget false}}
            app       {:attachments [att-ref1 att-ref2 att-src att-draft att-trg
                                     att-nod1 att-nod2 att-oth1 att-oth2 att-oth3 att-oth4]}]
        (fact "Deletable verdict attachment ids"
          (deletable-verdict-attachment-ids app "vid")
          => (just ["att-src" "att-draft" "att-trg"] :in-any-order)
          (deletable-verdict-attachment-ids app "oth")
          => (just ["att-oth1" "att-oth2"] :in-any-order)))

      (fact "finalize--proposal-pdf-tags"
        (get-in (finalize--pdf-tags c-v-a) [:updates $set :pate-verdicts.$.published.tags])
        => string?
        (provided (lupapalvelu.organization/get-organization-name "753-R" "fi") => "Sipoon rakennusvalvonta"))

      (fact "create-verdict-pdf called with bad args (no tags)"
        (create-verdict-pdf c-v-a false) => (throws Exception))

      (against-background [(html-pdf/html->pdf anything anything anything) => {:ok true}
                           (pdf/create-verdict-attachment anything anything anything false) => {:id "A98765"}
                           (pdf/create-verdict-attachment anything anything anything true) => {:id "B98765"}
                           (org/bulletins-enabled? anything anything anything anything) => true
                           (app-at-time-of-verdict-pdf anything) => nil]
        (fact "create-verdict-pdf for verdict"
          (let [result (create-verdict-pdf (assoc-in c-v-a [:verdict :published :tags] "{:body \"hiccup\"}") false)]
            result => map?
            (:updates result) => {"$set" {:pate-verdicts.$.published.attachment-id "A98765"}}
            (-> result :verdict :published :attachment-id) => "A98765"
            (:verdict-attachment-id result) => "A98765"
            (:bulletin-attachment-id result) => nil))

        (fact "create-verdict-pdf for bulletin"
          (let [result (create-verdict-pdf (assoc-in c-v-a [:verdict :published :tags] "{:body \"hiccup\"}") true)]
            result => map?
            (:updates result) => {"$set" {:pate-verdicts.$.published.bulletin-attachment-id "B98765"}}
            (-> result :verdict :published :attachment-id) => nil
            (:verdict-attachment-id result) => nil
            (:bulletin-attachment-id result) => "B98765"))

        (fact "create-verdict-pdfs"
          (let [result (-> c-v-a
                           (assoc-in [:verdict :published :tags] "{:body \"hiccup\"}")
                           (assoc-in [:verdict :data :k-kuulutus] 12345)
                           create-verdict-pdfs)]
            result => map?
            (:updates result) =>  {"$set" {:pate-verdicts.$.published.bulletin-attachment-id "B98765"
                                           :pate-verdicts.$.published.attachment-id          "A98765"}}
            (-> result :verdict :published :attachment-id) => "A98765"
            (:verdict-attachment-id result) => "A98765"
            (:bulletin-attachment-id result) => "B98765"
            (:app-at-time-of-verdict-pdf-id result) => nil))

        (fact "create-verdict-pdfs includes app-at-time-of-verdict-pdf-id when app pdf is created"
          (:app-at-time-of-verdict-pdf-id (-> c-v-a
                                              (assoc-in [:verdict :published :tags] "{:body \"hiccup\"}")
                                              create-verdict-pdfs))
          => "PDF-ATT-ID"
          (provided
            (app-at-time-of-verdict-pdf anything) => {:id "PDF-ATT-ID"}))

        (fact "create-verdict-pdf-version when html->pdf returns fail"
          (create-verdict-pdf (assoc-in c-v-a [:verdict :published :tags] "{:body \"very nice hiccup\"}") false)
          => (throws Exception)
          (provided
            (html-pdf/html->pdf anything anything anything) => {:ok false})))

      (facts "finalize--signatures"
        (let [contract {:id             "cid"
                        :schema-version 1
                        :category       "contract"
                        :state          "draft"
                        :data           {:handler      "Handler"
                                         :giver        "Signer"
                                         :verdict-date 22334455}}
              c-c-a    {:command     command
                        :verdict     contract
                        :application application-r}]
          (fact "Verdicts are not signed"
            (finalize--signatures c-v-a) => nil)
          (fact "Pate contract: Signed by giver"
            (finalize--signatures c-c-a)
            => {:updates {"$set" {:pate-verdicts.$.signatures [{:date    22334455
                                                                :name    "Signer"
                                                                :user-id "user-id"}]}}
                :verdict (assoc contract :signatures [{:date    22334455
                                                       :name    "Signer"
                                                       :user-id "user-id"}])})
          (fact "Pate contract: No giver, no signature"
            (finalize--signatures (assoc-in c-c-a [:verdict :data :giver] "    "))
            => nil
            (finalize--signatures (assoc-in c-c-a [:verdict :data :giver] nil))
            => nil
            (finalize--signatures (util/dissoc-in c-c-a [:verdict :data :giver]))
            => nil)

          (fact "Legacy contract: Signed by handler"
            (let [contract (assoc contract :legacy? true)
                  c-c-a    (assoc c-c-a :verdict contract)]
              (finalize--signatures c-c-a)
              => {:updates {"$set" {:pate-verdicts.$.signatures [{:date    22334455
                                                                  :name    "Handler"
                                                                  :user-id "user-id"}]}}
                  :verdict (assoc contract :signatures [{:date    22334455
                                                         :name    "Handler"
                                                         :user-id "user-id"}])}))))

      (facts "app-at-time-of-verdict-pdf"
        (let [app      {:id          "LP-001"
                        :documents   [{:schema-info {:name "hakija-r"}
                        :data        {:henkilo {:henkilotiedot {:hetu {:value "010101-0101"
                                                                       :type  :hetu}}}}}]
                        :attachments []}
              cmd      {:lang     "fi"
                        :created  1234567890
                        :user     {:id "user-1"}
                        :username "testuser"}
              a-c-d    {:application app
                        :command     cmd
                        :dry-run?    false}]
          (fact "returns nil when dry-run? is true"
            (app-at-time-of-verdict-pdf (assoc a-c-d :dry-run? true))
            => nil)

          (fact "nil pdf-stream returns nil and does not call upload"
            (let [upload-called? (atom false)]
              (with-redefs [app-utils/with-masked-person-ids     (fn [a _] a)
                            pdf-export/generate-application-pdfa (constantly nil)
                            pdf/upload-and-attach-pdf!           (fn [& _] (reset! upload-called? true))]
                (app-at-time-of-verdict-pdf a-c-d))
              => nil
              @upload-called? => false))

          (fact "uploads with correct attachment type and pdf stream"
            (let [upload-opts (atom nil)
                  mock-stream (java.io.ByteArrayInputStream. (byte-array 0))]
              (with-redefs [app-utils/with-masked-person-ids     (fn [a _] a)
                            pdf-export/generate-application-pdfa (constantly mock-stream)
                            att/get-attachments-by-type          (constantly [])
                            i18n/localize                        (constantly "contents")
                            i18n/localize-and-fill               (constantly "filename.pdf")
                            date/finnish-datetime                (constantly "2021-01-01")
                            pdf/upload-and-attach-pdf!           (fn [opts] (reset! upload-opts opts) {:id "att-456"})]
                (app-at-time-of-verdict-pdf a-c-d))
              (get-in @upload-opts [:attachment-options :attachment-type])
              => {:type-group "muut" :type-id "paatoksen_aikaiset_hankkeen_tiedot"}
              (get-in @upload-opts [:pdf :pdf-file-stream]) => mock-stream
              (:command @upload-opts) => cmd
              (get-in @upload-opts [:attachment-options :archive-only?]) => true))

          (fact "attachment-options includes existing attachment data if present"
            (let [upload-opts (atom nil)
                  mock-stream (java.io.ByteArrayInputStream. (byte-array 0))]
              (with-redefs [app-utils/with-masked-person-ids     (fn [a _] a)
                            pdf-export/generate-application-pdfa (constantly mock-stream)
                            att/get-attachments-by-type          (constantly [{:id            "att-123"
                                                                               :latestVersion {:originalFileId "file-456"}}])
                            i18n/localize                        (constantly "contents")
                            i18n/localize-and-fill               (constantly "filename.pdf")
                            date/finnish-datetime                (constantly "2021-01-01")
                            pdf/upload-and-attach-pdf!           (fn [opts] (reset! upload-opts opts) {:id "att-123"})]
                (app-at-time-of-verdict-pdf a-c-d))
              (get-in @upload-opts [:pdf :pdf-file-stream]) => mock-stream
              (get-in @upload-opts [:attachment-options :attachment-id]) => "att-123"
              (get-in @upload-opts [:attachment-options :original-file-id]) => "file-456"
              (get-in @upload-opts [:attachment-options :original-file-already-linked?]) => true)))))))
