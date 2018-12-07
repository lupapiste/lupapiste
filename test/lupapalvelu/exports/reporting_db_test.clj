(ns lupapalvelu.exports.reporting-db-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.exports.reporting-db :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rakennuslupa-canonical-util :refer [application-rakennuslupa]]))

(def review {:data {:katselmus {:pitoPvm {:value 1354532324658}
                                :pitaja {:value "Reijo Revyy"}
                                :lasnaolijat {:value nil}
                                :poikkeamat {:value nil}
                                :tila {:value nil}
                                :tiedoksianto {:value nil}
                                :huomautukset []}
                    :katselmuksenLaji {:value "Aloitusilmoitus"}
                    :vaadittuLupaehtona {:value nil}
                    :rakennus {:0 {:tila {:tila {:value "osittainen"}}
                                   :rakennus {:rakennusnro {:value "002"}
                                              :jarjestysnumero {:value 1}
                                              :kiinttun {:value "21111111111111"}}}
                               :1 {:tila {:tila {:value "lopullinen"}}
                                   :rakennus {:rakennusnro {:value "003"}
                                              :jarjestysnumero {:value 3}
                                              :kiinttun {:value "21111111111111"}
                                              :valtakunnallinenNumero {:value "1234567892"}}}
                               :2 {:tila {:tila {:value ""}}
                                   :rakennus {:rakennusnro {:value "004"}
                                              :jarjestysnumero {:value 3}
                                              :kiinttun {:value "21111111111111"}
                                              :valtakunnallinenNumero {:value "1234567892"}}}}
                    :muuTunnus "review1"
                    :muuTunnusSovellus "RakApp"}
             :id "123"
             :taskname "Aloitusilmoitus 1"})

(facts "->reporting-result"
  (against-background
    (org/pate-scope? irrelevant) => false)
  (->reporting-result (assoc application-rakennuslupa
                             :tasks [review])
                      "fi")
  => (contains {:id (:id application-rakennuslupa)
                :address (:address application-rakennuslupa)
                :location-etrs-tm35fin (:location application-rakennuslupa)
                :location-wgs84 (:location-wgs84 application-rakennuslupa)
                :permitType "R"
                :projectDescription "Uuden rakennuksen rakentaminen tontille.\n\nPuiden kaataminen:Puun kaataminen"
                :parties [{:VRKrooliKoodi "maksaja"
                           :henkilo {:nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                                     :puhelin "03-389 1380"
                                     :sahkopostiosoite "yritys@example.com"}
                           :kuntaRooliKoodi "Rakennusvalvonta-asian laskun maksaja"
                           :suoramarkkinointikieltoKytkin true
                           :turvakieltoKytkin true
                           :yritys {:liikeJaYhteisotunnus "1060155-5"
                                    :nimi "Solita Oy"
                                    :postiosoite {:osoitenimi {:teksti "katu"}
                                                  :postinumero "33800"
                                                  :postitoimipaikannimi "Tuonela"
                                                  :ulkomainenLahiosoite "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen "CHN"
                                                  :valtioSuomeksi "Kiina"}
                                    :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                                     :postinumero "33800"
                                                                     :postitoimipaikannimi "Tuonela"
                                                                     :ulkomainenLahiosoite "katu"
                                                                     :ulkomainenPostitoimipaikka "Tuonela"
                                                                     :valtioKansainvalinen "CHN"
                                                                     :valtioSuomeksi "Kiina"}}
                                    :puhelin "03-389 1380"
                                    :sahkopostiosoite "yritys@example.com"
                                    :vainsahkoinenAsiointiKytkin false
                                    :verkkolaskutustieto {:Verkkolaskutus {:ovtTunnus "003712345671"
                                                                           :valittajaTunnus "BAWCFI22"
                                                                           :verkkolaskuTunnus "laskutunnus-1234"}}}}
                          {:VRKrooliKoodi "maksaja"
                           :henkilo {:henkilotunnus "210281-9988"
                                     :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                                     :osoite {:osoitenimi {:teksti "katu"}
                                              :postinumero "33800"
                                              :postitoimipaikannimi "Tuonela"
                                              :ulkomainenLahiosoite "katu"
                                              :ulkomainenPostitoimipaikka "Tuonela"
                                              :valtioKansainvalinen "CHN"
                                              :valtioSuomeksi "Kiina"}
                                     :puhelin "+358401234567"
                                     :sahkopostiosoite "pena@example.com"
                                     :vainsahkoinenAsiointiKytkin false}
                           :kuntaRooliKoodi "Rakennusvalvonta-asian laskun maksaja"
                           :suoramarkkinointikieltoKytkin true
                           :turvakieltoKytkin true}
                          {:VRKrooliKoodi "muu osapuoli"
                           :henkilo {:henkilotunnus "210281-9988"
                                     :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                                     :osoite {:osoitenimi {:teksti "katu"}
                                              :postinumero "33800"
                                              :postitoimipaikannimi "Tuonela"
                                              :ulkomainenLahiosoite "katu"
                                              :ulkomainenPostitoimipaikka "Tuonela"
                                              :valtioKansainvalinen "CHN"
                                              :valtioSuomeksi "Kiina"}
                                     :puhelin "+358401234567"
                                     :sahkopostiosoite "pena@example.com"
                                     :vainsahkoinenAsiointiKytkin true}
                           :kuntaRooliKoodi "Hakijan asiamies"
                           :suoramarkkinointikieltoKytkin true
                           :turvakieltoKytkin true}
                          {:VRKrooliKoodi "hakija"
                           :henkilo {:henkilotunnus "210281-9988"
                                     :nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                                     :osoite {:osoitenimi {:teksti "katu"}
                                              :postinumero "33800"
                                              :postitoimipaikannimi "Tuonela"
                                              :ulkomainenLahiosoite "katu"
                                              :ulkomainenPostitoimipaikka "Tuonela"
                                              :valtioKansainvalinen "CHN"
                                              :valtioSuomeksi "Kiina"}
                                     :puhelin "+358401234567"
                                     :sahkopostiosoite "pena@example.com"
                                     :vainsahkoinenAsiointiKytkin true}
                           :kuntaRooliKoodi "Rakennusvalvonta-asian hakija"
                           :suoramarkkinointikieltoKytkin true
                           :turvakieltoKytkin true}
                          {:VRKrooliKoodi "hakija"
                           :henkilo {:nimi {:etunimi "Pena" :sukunimi "Penttilä"}
                                     :puhelin "03-389 1380"
                                     :sahkopostiosoite "yritys@example.com"}
                           :kuntaRooliKoodi "Rakennusvalvonta-asian hakija"
                           :suoramarkkinointikieltoKytkin false
                           :turvakieltoKytkin true
                           :yritys {:liikeJaYhteisotunnus "1060155-5"
                                    :nimi "Solita Oy"
                                    :postiosoite {:osoitenimi {:teksti "katu"}
                                                  :postinumero "33800"
                                                  :postitoimipaikannimi "Tuonela"
                                                  :ulkomainenLahiosoite "katu"
                                                  :ulkomainenPostitoimipaikka "Tuonela"
                                                  :valtioKansainvalinen "CHN"
                                                  :valtioSuomeksi "Kiina"}
                                    :postiosoitetieto {:postiosoite {:osoitenimi {:teksti "katu"}
                                                                     :postinumero "33800"
                                                                     :postitoimipaikannimi "Tuonela"
                                                                     :ulkomainenLahiosoite "katu"
                                                                     :ulkomainenPostitoimipaikka "Tuonela"
                                                                     :valtioKansainvalinen "CHN"
                                                                     :valtioSuomeksi "Kiina"}}
                                    :puhelin "03-389 1380"
                                    :sahkopostiosoite "yritys@example.com"
                                    :vainsahkoinenAsiointiKytkin true}}]
                :reviews [{:type "ei tiedossa"
                           :reviewer "Reijo Revyy"
                           :date "2012-12-03"
                           :verottajanTvLl false}]
                :state "submitted"
                :stateChangeTs 12345
                :araFunding false})

  (->reporting-result (update application-rakennuslupa
                              :documents
                              (partial map #(if (= (-> % :schema-info :name)
                                                   "hankkeen-kuvaus")
                                              (assoc-in % [:data :rahoitus :value] true)
                                              %)))
                      "fi")
  => (contains {:araFunding true}))
