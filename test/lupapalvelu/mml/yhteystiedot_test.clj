(ns lupapalvelu.mml.yhteystiedot-test
  (:require [lupapalvelu.mml.yhteystiedot :as lh]
            [sade.env :as env]
            [sade.http :as http]
            [midje.sweet :refer :all]))

(defn- mock-response [xml-file] {:body (slurp (clojure.java.io/resource xml-file))})

(fact "Kaksi luonnollista henkil\u00F6\u00E4" (lh/get-owners "1234") 
      => '( {:lukuuntoiminnanlaji "OL"
             :henkilolaji "LU"
             :etunimet "Ahma Ky\u00F6sti Jaakkima"
             :sukunimi "Voller"
             :syntymapvm "1975-01-08"
             :ulkomaalainen false
             :jakeluosoite "Valli & kuja I/X:s Gaatta"
             :postinumero "00100"
             :paikkakunta "Helsinki"} 
            {:lukuuntoiminnanlaji "OL"
             :henkilolaji "LU"
             :etunimet "Jaakko Jaakkima Jorma"
             :sukunimi "Pakkanen"
             :syntymapvm "1979-12-12"
             :ulkomaalainen true
             :jakeluosoite "Valli & kuja I/X:s Gaatta"
             :postinumero "00100"
             :paikkakunta "Helsinki"
             })
      (provided (http/get anything) => (mock-response "mml/yhteystiedot-LU.xml")))

(fact "Juridinen henkil\u00F6" (lh/get-owners "1234") 
      => '( {:lukuuntoiminnanlaji "OL"
             :henkilolaji "JU"
             :nimi "Hokki-kiinteist\u00F6t Oy"
             :ytunnus "0704458-3"})
      (provided (http/get anything) => (mock-response "mml/yhteystiedot-JU.xml")))

(fact "Kaksi kuolinpes\u00E4\u00E4" (lh/get-owners "1234") 
      => '( {:lukuuntoiminnanlaji "OL"
             :henkilolaji "KP"
             :etunimet "Pjotr Seppo Risto"
             :sukunimi "Yl\u00E4m\u00E4rssy"
             :syntymapvm "1917-12-09"
             :kuolinpvm "1995-05-02"
             :ulkomaalainen false
             :yhteyshenkilo {:henkilolaji "LU"
                             :etunimet "Seppo Matias Unto"
                             :sukunimi "Lahti"
                             :syntymapvm "1951-01-07"
                             :ulkomaalainen false
                             :jakeluosoite "Valli & kuja I/X:s Gaatta"
                             :postinumero "00100"
                             :paikkakunta "Helsinki"
                             }} 
            {:lukuuntoiminnanlaji "OL"
             :henkilolaji "KP"
             :etunimet "Legolas Kalervo Jalmari"
             :sukunimi "Niinist\u00F6"
             :syntymapvm "1922-07-20"
             :kuolinpvm "1988-06-09"
             :ulkomaalainen false
             :yhteyshenkilo {:henkilolaji "LU"
                             :etunimet "Seppo Matias Unto"
                             :sukunimi "Lahti"
                             :syntymapvm "1951-01-07"
                             :ulkomaalainen false
                             :jakeluosoite "Valli & kuja I/X:s Gaatta"
                             :postinumero "00100"
                             :paikkakunta "Helsinki"
                             }})
      (provided (http/get anything) => (mock-response "mml/yhteystiedot-KP.xml")))
