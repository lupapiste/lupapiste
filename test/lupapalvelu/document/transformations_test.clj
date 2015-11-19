(ns lupapalvelu.document.transformations-test
  (:require [lupapalvelu.document.transformations :refer :all]
            [midje.sweet :refer :all]))

(fact "rakennusjateselvitys-updates - one value"
  (rakennusjateselvitys-updates {:data {:jatteet {:0 {:suunniteltuMaara {:value "2"}}}}}) 
   => [[[:jatteet :0 :toteutunutMaara] "2"]])

(fact "rakennusjateselvitys-updates - two values"
  (rakennusjateselvitys-updates {:data 
                                 {:jatteet 
                                  {:0
                                   {:suunniteltuMaara {:value "2"}
                                    :tyyppi {:value "jate"}}}}}) 
  => (just #{[[:jatteet :0 :toteutunutMaara] "2"] 
             [[:jatteet :0 :tyyppi] "jate"]}))

(fact "rakennusjateselvitys-updates - no value"
  (rakennusjateselvitys-updates {:data 
                                 {:jatteet 
                                  {:0 
                                   {:suunniteltuMaara {:notvalue "2"}
                                    :tyyppi {:notvalue "jate"}}}}}) 
  => [])

(fact "rakennusjateselvitys-updates - mixed values"
  (rakennusjateselvitys-updates {:data 
                                 {:jatteet 
                                  {:0 
                                   {:suunniteltuMaara {:value "2"}
                                    :tyyppi {:value "jate"}}}
                                  :aineet
                                  {:0 
                                   {:suunniteltuMaara {:notvalue "2"}
                                    :tyyppi {:value "aine"}}}}}) 
  => (just #{[[:jatteet :0 :toteutunutMaara] "2"] 
             [[:jatteet :0 :tyyppi] "jate"]  
             [[:aineet :0 :tyyppi] "aine"]}))
