(ns lupapalvelu.mml.yhteystiedot-test
  (:require [lupapalvelu.mml.yhteystiedot :as lh]
            [sade.env :as env]
            [sade.common-reader :as cr]
            [sade.xml :as xml]
            [midje.sweet :refer :all]))

(defn- mock-response [xml-file] {:body (slurp (clojure.java.io/resource xml-file))})

(defn- to-date [x] (cr/to-timestamp x))

(fact "Kaksi luonnollista henkil\u00F6\u00E4" (lh/get-owners "12312312341234")
      => [{:henkilolaji :luonnollinen
           :etunimet "Ahma Ky\u00F6sti Jaakkima"
           :sukunimi "Voller"
           ;:syntymapvm (to-date "1975-01-08")
           ;:ulkomaalainen false
           :jakeluosoite "Valli & kuja I/X:s Gaatta"
           :postinumero "00100"
           :paikkakunta "Helsinki"}
          {:henkilolaji :luonnollinen
           :etunimet "Jaakko Jaakkima Jorma"
           :sukunimi "Pakkanen"
           ;:syntymapvm (to-date "1979-12-12")
           ;:ulkomaalainen true
           :jakeluosoite "Valli & kuja I/X:s Gaatta"
           :postinumero "00100"
           :paikkakunta "Helsinki"
           }]
      (provided (cr/get-xml anything anything anything anything) => (xml/parse (:body (mock-response "mml/yhteystiedot-LU.xml")))))

(fact "Juridinen henkil\u00F6" (lh/get-owners "12312312341234")
      => [{:henkilolaji :juridinen
           :nimi "Hokki-kiinteist\u00F6t Oy"
           :ytunnus "0704458-3"}]
      (provided (cr/get-xml anything anything anything anything) => (xml/parse (:body (mock-response "mml/yhteystiedot-JU.xml")))))

(fact "Kaksi kuolinpes\u00E4\u00E4" (lh/get-owners "12312312341234")
      => [{:henkilolaji :kuolinpesa
           :etunimet "Pjotr Seppo Risto"
           :sukunimi "Yl\u00E4m\u00E4rssy"
           ;:syntymapvm (to-date "1917-12-09")
           :kuolinpvm (to-date "1995-05-02")
           ;:ulkomaalainen false
           :yhteyshenkilo {:henkilolaji :luonnollinen
                           :etunimet "Seppo Matias Unto"
                           :sukunimi "Lahti"
                           ;:syntymapvm (to-date "1951-01-07")
                           ;:ulkomaalainen false
                           :jakeluosoite "Valli & kuja I/X:s Gaatta"
                           :postinumero "00100"
                           :paikkakunta "Helsinki"
                           }}
          {:henkilolaji :kuolinpesa
           :etunimet "Legolas Kalervo Jalmari"
           :sukunimi "Niinist\u00F6"
           ;:syntymapvm (to-date "1922-07-20")
           :kuolinpvm (to-date "1988-06-09")
           ;:ulkomaalainen false
           :yhteyshenkilo {:henkilolaji :luonnollinen
                           :etunimet "Seppo Matias Unto"
                           :sukunimi "Lahti"
                           ;:syntymapvm (to-date "1951-01-07")
                           ;:ulkomaalainen false
                           :jakeluosoite "Valli & kuja I/X:s Gaatta"
                           :postinumero "00100"
                           :paikkakunta "Helsinki"
                           }}]
      (provided (cr/get-xml anything anything anything anything) => (xml/parse (:body (mock-response "mml/yhteystiedot-KP.xml")))))
