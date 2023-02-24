(ns lupapalvelu.mml.yhteystiedot-test
  (:require [lupapalvelu.mml.yhteystiedot :as lh]
            [sade.common-reader :as cr]
            [sade.xml :as xml]
            [sade.date :as date]
            [midje.sweet :refer :all]
            [clojure.java.io :as io]))

(defn- mock-response [xml-file] {:body (io/input-stream (io/resource xml-file))})

(fact "Kaksi luonnollista henkil\u00F6\u00E4" (lh/get-owners "12312312341234")
      => [{:henkilolaji :luonnollinen
           :etunimet "Ahma Ky\u00F6sti Jaakkima"
           :sukunimi "Voller"
           ;:syntymapvm (date/timestamp "1975-01-08")
           ;:ulkomaalainen false
           :jakeluosoite "Valli & kuja I/X:s Gaatta"
           :postinumero "00100"
           :paikkakunta "Helsinki"}
          {:henkilolaji :luonnollinen
           :etunimet "Jaakko Jaakkima Jorma"
           :sukunimi "Pakkanen"
           ;:syntymapvm (date/timestamp "1979-12-12")
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
           ;:syntymapvm (date/timestamp "1917-12-09")
           :kuolinpvm (date/timestamp "1995-05-02")
           ;:ulkomaalainen false
           :yhteyshenkilo {:henkilolaji :luonnollinen
                           :etunimet "Seppo Matias Unto"
                           :sukunimi "Lahti"
                           ;:syntymapvm (date/timestamp "1951-01-07")
                           ;:ulkomaalainen false
                           :jakeluosoite "Valli & kuja I/X:s Gaatta"
                           :postinumero "00100"
                           :paikkakunta "Helsinki"
                           }}
          {:henkilolaji :kuolinpesa
           :etunimet "Legolas Kalervo Jalmari"
           :sukunimi "Niinist\u00F6"
           ;:syntymapvm (date/timestamp "1922-07-20")
           :kuolinpvm (date/timestamp "1988-06-09")
           ;:ulkomaalainen false
           :yhteyshenkilo {:henkilolaji :luonnollinen
                           :etunimet "Seppo Matias Unto"
                           :sukunimi "Lahti"
                           ;:syntymapvm (date/timestamp "1951-01-07")
                           ;:ulkomaalainen false
                           :jakeluosoite "Valli & kuja I/X:s Gaatta"
                           :postinumero "00100"
                           :paikkakunta "Helsinki"
                           }}]
      (provided (cr/get-xml anything anything anything anything) => (xml/parse (:body (mock-response "mml/yhteystiedot-KP.xml")))))
