(ns lupapalvelu.document.transformations-test
  (:require [lupapalvelu.document.transformations :refer :all]
            [midje.sweet :refer :all]))

(fact "rakennusjateselvitys-updates - one value"
  (rakennusjateselvitys-updates {:data 
                                 {:rakennusJaPurkujate 
                                  {:0 
                                   {:suunniteltuMaara {:value "2"}}}}}) 
  => [[[:rakennusJaPurkujate :suunniteltuJate :0 :suunniteltuMaara] "2"]])

(fact "rakennusjateselvitys-updates - two values"
  (rakennusjateselvitys-updates {:data 
                                 {:rakennusJaPurkujate 
                                  {:0
                                   {:suunniteltuMaara {:value "2"}
                                    :tyyppi {:value "jate"}}}}}) 
  => (just #{[[:rakennusJaPurkujate :suunniteltuJate :0 :suunniteltuMaara] "2"] 
             [[:rakennusJaPurkujate :suunniteltuJate :0 :tyyppi] "jate"]}))

(fact "rakennusjateselvitys-updates - no value"
  (rakennusjateselvitys-updates {:data 
                                 {:rakennusJaPurkujate 
                                  {:0 
                                   {:suunniteltuMaara {:notvalue "2"}
                                    :tyyppi {:notvalue "jate"}}}}}) 
  => [])

(fact "rakennusjateselvitys-updates - wrong group name"
  (rakennusjateselvitys-updates {:data 
                                 {:wrongName 
                                  {:0 
                                   {:suunniteltuMaara {:value "2"}
                                    :tyyppi {:value "jate"}}}}}) 
  => [])

(fact "rakennusjateselvitys-updates - mixed values"
  (rakennusjateselvitys-updates {:data 
                                 {:rakennusJaPurkujate
                                  {:0 
                                   {:suunniteltuMaara {:value "2"}
                                    :tyyppi {:value "jate"}}}
                                  :vaarallisetAineet
                                  {:0
                                   {:suunniteltuMaara {:notvalue "2"}
                                    :tyyppi {:value "aine"}}}}}) 
  => (just #{[[:rakennusJaPurkujate :suunniteltuJate :0 :suunniteltuMaara] "2"] 
             [[:rakennusJaPurkujate :suunniteltuJate :0 :tyyppi] "jate"]
             [[:vaarallisetAineet  :suunniteltuJate :0 :tyyppi] "aine"]}))

(fact "rakennusjateselvitys-removed-paths - one removed row"
  (rakennusjateselvitys-removed-paths {}

                                      {:data 
                                       {:rakennusJaPurkujate
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => [[:rakennusJaPurkujate :suunniteltuJate :0]])

(fact "rakennusjateselvitys-removed-paths - no removed rows"
  (rakennusjateselvitys-removed-paths {:data 
                                       {:rakennusJaPurkujate
                                        {:0
                                         {:suunniteltuMaara {:value "2"}}}}}
                                      
                                      {:data 
                                       {:rakennusJaPurkujate
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => [])

(fact "rakennusjateselvitys-removed-paths - new row"
  (rakennusjateselvitys-removed-paths {:data 
                                       {:rakennusJaPurkujate
                                        {:0
                                         {:suunniteltuMaara {:value "2"}}
                                         :1
                                         {:suunniteltuMaara {:value "2"}}}}}

                                      {:data 
                                       {:rakennusJaPurkujate
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => [])

(fact "rakennusjateselvitys-removed-paths - different group name"
  (rakennusjateselvitys-removed-paths {:data 
                                       {:vaarallisetAineet
                                        {:0
                                         {:suunniteltuMaara {:value "2"}}}}}

                                      {:data 
                                       {:rakennusJaPurkujate
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => [[:rakennusJaPurkujate :suunniteltuJate :0]])

(fact "rakennusjateselvitys-removed-paths - different row index"
  (rakennusjateselvitys-removed-paths {:data 
                                       {:rakennusJaPurkujate
                                        {:1
                                         {:suunniteltuMaara {:value "2"}}}}}

                                      {:data 
                                       {:rakennusJaPurkujate
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => [[:rakennusJaPurkujate :suunniteltuJate :0]])

(fact "rakennusjateselvitys-removed-paths - wrong group name"
  (rakennusjateselvitys-removed-paths {:data 
                                       {:rakennusJaPurkujate
                                        {:1
                                         {:suunniteltuMaara {:value "2"}}}}}

                                      {:data 
                                       {:ingnoreThisGroup
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => [])

(fact "rakennusjateselvitys-removed-paths - two reomved one left"
  (rakennusjateselvitys-removed-paths {:data 
                                       {:rakennusJaPurkujate
                                        {:1
                                         {:suunniteltuMaara {:value "2"}}}}}

                                      {:data 
                                       {:rakennusJaPurkujate
                                        {:suunniteltuJate
                                         {:0
                                          {:suunniteltuMaara {:value "2"}}
                                          :1
                                          {:suunniteltuMaara {:value "2"}}}}
                                        :vaarallisetAineet
                                        {:suunniteltuJate
                                         {:9
                                          {:suunniteltuMaara {:value "2"}}}}}})
  => (just #{[:rakennusJaPurkujate :suunniteltuJate :0] 
             [:vaarallisetAineet :suunniteltuJate :9]}))
