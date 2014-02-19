(ns lupapalvelu.document.ymparistolupa-canonical-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.document.ymparistolupa-canonical :as ylc]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.document.canonical-test-common :refer :all]
            [lupapalvelu.document.ymparisto-schemas]))

(def application {:id "LP-638-2014-00001"
                  :attachments []
                  :auth [{:lastName "Borga" :firstName "Pekka" :username "pekka" :type "owner" :role "owner" :id "777777777777777777000033"}]
                  :authority {:role "authority" :lastName "Borga" :firstName "Pekka" :username "pekka" :id "777777777777777777000033"}
                  :address "Londb\u00f6lentie 97"
                  :created 1391415025497
                  :documents []
                  :drawings []
                  :infoRequest false
                  :location {:x 428195.77099609 :y 6686701.3931274}
                  :neighbors {}
                  :modified 1391415696674
                  :municipality "638"
                  :operations [{:id "abba1" :name "yl-uusi-toiminta" :created 1391415025497}]
                  :openInfoRequest false
                  :opened 1391415025497
                  :organization "638-R"
                  :permitType "YL"
                  :permitSubtype nil
                  :propertyId "63844900010004"
                  :schema-version 1
                  :sent nil
                  :started nil
                  :state "submitted"
                  :statements statements
                  :submitted 1391415717396
                  :title "Londb\u00f6lentie 97"})

(facts* "Ymparistolupa to canonical"
  (let [canonical (ylc/ymparistolupa-canonical application "fi") => truthy
        Ymparistoluvat (:Ymparistoluvat canonical) => truthy
        toimituksenTiedot (:toimituksenTiedot Ymparistoluvat) => truthy
        aineistonnimi (:aineistonnimi toimituksenTiedot) => (:title application)

        ymparistolupatieto (:ymparistolupatieto Ymparistoluvat) => truthy
        Ymparistolupa (:Ymparistolupa ymparistolupatieto)
        kasittelytietotieto (:kasittelytietotieto Ymparistolupa) => truthy

        luvanTunnistetiedot (:luvanTunnistetiedot Ymparistolupa) => truthy
        LupaTunnus (:LupaTunnus luvanTunnistetiedot) => truthy
        muuTunnustieto (:muuTunnustieto LupaTunnus) => truthy
        MuuTunnus (:MuuTunnus muuTunnustieto) => truthy
        tunnus (:tunnus MuuTunnus) => (:id application)
        sovellus (:sovellus MuuTunnus) => "Lupapiste"

        lausuntotieto (:lausuntotieto Ymparistolupa) => truthy
        Lausunto (:Lausunto (first lausuntotieto)) => truthy
        viranomainen (:viranomainen Lausunto) => "Paloviranomainen"
        pyyntoPvm (:pyyntoPvm Lausunto) => "2013-09-17"
        lausuntotieto (:lausuntotieto Lausunto) => truthy
        annettu-lausunto (:Lausunto lausuntotieto) => truthy
        lausunnon-antanut-viranomainen (:viranomainen annettu-lausunto) => "Paloviranomainen"
        varsinainen-lausunto (:lausunto annettu-lausunto) => "Lausunto liitteen\u00e4."
        lausuntoPvm (:lausuntoPvm annettu-lausunto) => "2013-09-17"


        ]

    ; (clojure.pprint/pprint canonical)
))