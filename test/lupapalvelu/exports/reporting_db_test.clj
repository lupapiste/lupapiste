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
                           :etunimi "Pena"
                           :kuntaRooliKoodi "Rakennusvalvonta-asian laskun maksaja"
                           :postinumero nil
                           :postiosoite nil
                           :postitoimipaikka nil
                           :sukunimi "Penttilä"}
                          {:VRKrooliKoodi "maksaja"
                           :etunimi "Pena"
                           :kuntaRooliKoodi "Rakennusvalvonta-asian laskun maksaja"
                           :postinumero "33800"
                           :postiosoite "katu"
                           :postitoimipaikka "Tuonela"
                           :sukunimi "Penttilä"}
                          {:VRKrooliKoodi "muu osapuoli"
                           :etunimi "Pena"
                           :kuntaRooliKoodi "Hakijan asiamies"
                           :postinumero "33800"
                           :postiosoite "katu"
                           :postitoimipaikka "Tuonela"
                           :sukunimi "Penttilä"}
                          {:VRKrooliKoodi "hakija"
                           :etunimi "Pena"
                           :kuntaRooliKoodi "Rakennusvalvonta-asian hakija"
                           :postinumero "33800"
                           :postiosoite "katu"
                           :postitoimipaikka "Tuonela"
                           :sukunimi "Penttilä"}
                          {:VRKrooliKoodi "hakija"
                           :etunimi "Pena"
                           :kuntaRooliKoodi "Rakennusvalvonta-asian hakija"
                           :postinumero nil
                           :postiosoite nil
                           :postitoimipaikka nil
                           :sukunimi "Penttilä"}]
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
