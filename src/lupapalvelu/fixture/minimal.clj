(ns lupapalvelu.fixture.minimal
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.attachment :as attachment]))

(def ^:private local-krysp "http://localhost:8000/dev/krysp")

(def users
  [;; Solita admin:  admin / admin
   {:id "777777777777777777000099"
    :email "admin@solita.fi"
    :enabled true
    :role "admin"
    :firstName "Admin"
    :lastName "Admin"
    :phone "03030303"
    :username "admin"
    :private {:password "$2a$10$WHPur/hjvaOTlm41VFjtjuPI5hBoIMm8Y1p2vL4KqRi7QUvHMS1Ie"
              :apikey "5087ba34c2e667024fbd5992"}}

   ;; Admin
   {:id "505718b0aa24a1c901e6ba24"
    :enabled true
    :firstName "Judge"
    :lastName "Dread"
    :email "judge.dread@example.com"
    :role "admin"
    :private {:apikey "505718b0aa24a1c901e6ba24"}}

   ;; Tampere

   ;; Tampere YA paakayttaja:  tampere-ya / tampere
   {:id "837-YA"
    :enabled true
    :lastName "Tampere"
    :firstName "Paakayttaja-YA"
    :city "Tampere"
    :username "tampere-ya"
    :street "Paapankuja 12"
    :phone "0102030405"
    :email "tampere-ya"
    :role "authorityAdmin"
    :zip "10203"
    :organizations ["837-YA"]
    :private {:password "$2a$10$hkJ5ZQhqL66iM2.3m4712eDIH1K1Ez6wp7FeV9DTkPCNEZz8IfrAe" :apikey "tampereYAapikey"}}

   ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti:  veikko / veikko
   {:id "777777777777777777000016"
    :email "veikko.viranomainen@tampere.fi"
    :enabled true
    :role "authority"
    :organizations ["837-R"]
    :firstName "Veikko"
    :lastName "Viranomainen"
    :phone "03121991"
    :username "veikko"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuLF5AQqkSO5S1DJOgziMep.xJLYm3.xG"
              :apikey "5051ba0caa2480f374dcfeff"}}

   ;; Jussi Viranomainen - tamperelainen YA-lupa-arkkitehti:  jussi / jussi
   {:id "777777777777777777000017"
    :email "jussi.viranomainen@tampere.fi"
    :enabled true
    :role "authority"
    :username "jussi"
    :organizations ["837-YA"]
    :firstName "Jussi"
    :lastName "Viranomainen"
    :phone "1231234567"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Tampere"
    :private {:password "$2a$10$Wl49diVWkO6UpBABzjYR4e8zTwIJBDKiEyvw1O2EMOtV9fqHaXPZq"
              :apikey "5051ba0caa2480f374dcfefg"}}

   ;; Kuopio

   ;; Sakari Viranomainen - Kuopion YA-lupa-arkkitehti:  sakari / sakari
   {:id "77777777777777777700669"
    :email "sakari.viranomainen@kuopio.fi"
    :enabled true
    :role "authority"
    :username "sakari"
    :organizations ["297-YA"]
    :firstName "Sakari"
    :lastName "Viranomainen"
    :phone "1231234567"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Kuopio"
    :private {:password "$2a$10$VnwROer5dhRJCQxoZusOney/hyN7Vk4ILQMSVqT8iZMO4XiQz.8Cm"}}
   ;; Kuopio YA-paakayttaja:  kuopio-ya / kuopio
   {:id "297-YA"
    :enabled true
    :lastName "Kuopio"
    :firstName "Paakayttaja-YA"
    :city "Kuopio"
    :username "kuopio-ya"
    :street "Paapankuja 12"
    :phone "0102030405"
    :email "kuopio-ya"
    :role "authorityAdmin"
    :zip "10203"
    :organizations ["297-YA"]
    :private {:password "$2a$10$YceveAiQXbeUs65B4FZ6lez/itf0UEXooHcZlygI2WnQGhF0dJ1jO"}}

   ;; Sipoo

   ;; Simo Sippo - Sipoon R paakayttaja:  sipoo / sipoo
   {:id "50ac77ecc2e6c2ea6e73f83e"
    :email "admin@sipoo.fi"
    :enabled true
    :role "authorityAdmin"
    :organizations ["753-R"]
    :firstName "Simo"
    :lastName "Suurvisiiri"
    :username "sipoo"
    :private {:password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
              :apikey "50ac788ec2e6c2ea6e73f83f"}}

   ;; Sonja Sibbo - Sipoon lupa-arkkitehti:  sonja / sonja
   {:id "777777777777777777000023"
    :username "sonja"
    :role "authority"
    :enabled true
    :email "sonja.sibbo@sipoo.fi"
    :organizations ["753-R" "753-YA" "998-R-TESTI-2"]
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Sipoo"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :apikey "5056e6d3aa24a1c901e6b9d1"}}
   ;; Ronja Sibbo - Sipoon lupa-arkkitehti:  ronja / sonja
   {:id "777777777777777777000024"
    :username "ronja"
    :role "authority"
    :enabled true
    :email "ronja.sibbo@sipoo.fi"
    :organizations ["753-R"]
    :firstName "Ronja"
    :lastName "Sibbo"
    :phone "03121991"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Sipoo"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :apikey "5056e6d3aa24a1c901e6b9dd"}}

   ;; Porvoo

   ;; Pekka Borga - Porvoon lupa-arkkitehti:  pekka / pekka
   {:id "777777777777777777000033"
     :email "pekka.borga@porvoo.fi"
     :enabled true
     :role "authority"
     :organizations ["638-R"]
     :firstName "Pekka"
     :lastName "Borga"
     :phone "121212"
     :username "pekka"
     :private {:password "$2a$10$C65v2OgWcCzo4SVDtofawuP8xXDnZn5.URbODSpeOWmRABxUU01k6"
               :apikey "4761896258863737181711425832653651926670"}}

   ;; Oulu

   ;; Olli Ule\u00E5borg - Oulun lupa-arkkitehti:  olli / olli
   {:id "777777777777777777000034"
     :email "olli.uleaborg@ouka.fi"
     :enabled true
     :role "authority"
     :organizations ["564-R"]
     :firstName "Olli"
     :lastName "Ule\u00E5borg"
     :phone "121212"
     :username "olli"
     :private {:password "$2a$10$JXFA55BPpNDpI/jDuPv76uW9TTgGHcDI2l5daelFcJbWvefB6THmi"
               :apikey "7634919923210010829057754770828315568705"}}

   ;; Naantali

   ;; Naantali R paakayttaja: admin@naantali.fi / naantali
   {:id "50ac77ecd2e6c2ea6e73f83f"
    :email "admin@naantali.fi"
    :enabled true
    :role "authorityAdmin"
    :organizations ["529-R"]
    :firstName "Admin"
    :lastName "Naantali"
    :username "admin@naantali.fi"
    :private {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f83f"}}
   ;; rakennustarkastaja@naantali.fi / naantali
   {:id "50ac77ecd2e6c2ea6e73f840"
    :email "rakennustarkastaja@naantali.fi"
    :enabled true
    :role "authority"
    :organizations ["529-R"]
    :firstName "Rakennustarkastaja"
    :lastName "Naantali"
    :username "rakennustarkastaja@naantali.fi"
    :private {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f840"}}
   ;; lupasihteeri@naantali.fi / naantali
   {:id "50ac77ecd2e6c2ea6e73f841"
    :email "lupasihteeri@naantali.fi"
    :enabled true
    :role "authority"
    :organizations ["529-R"]
    :firstName "Lupasihteeri"
    :lastName "Naantali"
    :username "lupasihteeri@naantali.fi"
    :private {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f841"}}

   ;; Jarvenpaa

   ;; Jarvenpaan R paakayttaja: admin@jarvenpaa.fi / jarvenpaa
   {:id "50ac77ecd2e6c2ea6e73f850"
    :email "admin@jarvenpaa.fi"
    :enabled true
    :role "authorityAdmin"
    :organizations ["186-R"]
    :firstName "Admin"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "admin@jarvenpaa.fi"
    :private {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f850"}}
   ;; rakennustarkastaja@jarvenpaa.fi / jarvenpaa
   {:id "50ac77ecd2e6c2ea6e73f851"
    :email "rakennustarkastaja@jarvenpaa.fi"
    :enabled true
    :role "authority"
    :organizations ["186-R"]
    :firstName "Rakennustarkastaja"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "rakennustarkastaja@jarvenpaa.fi"
    :private {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f851"}}
   ;; lupasihteeri@jarvenpaa.fi / jarvenpaa
   {:id "50ac77ecd2e6c2ea6e73f852"
    :email "lupasihteeri@jarvenpaa.fi"
    :enabled true
    :role "authority"
    :organizations ["186-R"]
    :firstName "Lupasihteeri"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "lupasihteeri@jarvenpaa.fi"
    :private {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f852"}}

   ;; Loppi

   ;; Arto Viranomainen - Lopen R lupa-arkkitehti:  arto / arto
   {:id "77775577777777777700769"
    :email "arto.viranomainen@loppi.fi"
    :enabled true
    :role "authority"
    :username "arto"
    :organizations ["433-R"]
    :firstName "Arto"
    :lastName "Viranomainen"
    :phone "1231234567"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Loppi"
    :private {:password "$2a$10$MX9RWSqjBocwxg1ikSp/POV8lXd6lKA4yDymRIg7.GsXdRigZxmjK"}}

   ;; Hakijat

   ;; Hakija: Mikko's neighbour - teppo@example.com / teppo69
   {:lastName "Nieminen"
    :firstName "Teppo"
    :enabled true
    :postalCode "33200"
    :username "teppo@example.com"
    :private {:password "$2a$10$KKBZSYTFTEFlRrQPa.PYPe9wz4q1sRvjgEUCG7gt8YBXoYwCihIgG"
              :apikey "502cb9e58426c613c8b85abb"}
    :phone "0505503171"
    :email "teppo@example.com"
    :personId "210281-0002"
    :role "applicant"
    :id "5073c0a1c2e6c470aef589a5"
    :street "Mutakatu 7"
    :zip "33560"
    :city "Tampere"}
   ;; Hakija: Mikko Intonen - mikko@example.com / mikko123
   {:id "777777777777777777000010"
    :username "mikko@example.com"
    :enabled true
    :role "applicant"
    :personId "210281-0002"
    :firstName "Mikko"
    :lastName "Intonen"
    :email "mikko@example.com"
    :street "Rambokuja 6"
    :zip "55550"
    :city "Sipoo"
    :phone "0505503171"
    :architect true
    :degree "Tutkinto"
    :graduatingYear "2000"
    :companyName "Yritys Oy"
    :companyId "1234567-1"
    :fise "f"
    :private {:password "$2a$10$sVFCAX/MB7wDKA2aNp1greq7QlHCU/r3WykMX/JKMWmg7d1cp7HSq"
              :apikey "502cb9e58426c613c8b85abc"}}
   ;; Hakija: pena / pena
   {:id "777777777777777777000020"
    :username "pena"
    :enabled true
    :role "applicant"
    :personId "010203-040A"
    :firstName "Pena"
    :lastName "Panaani"
    :email "pena@example.com"
    :street "Paapankuja 12"
    :zip "10203"
    :city "Piippola"
    :phone "0102030405"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
              :apikey "502cb9e58426c613c8b85abd"}}

   ;; Dummy Hakijat

   ;; Dummy hakija 1: pena / pena
   {:id  "51112424c26b7342d92acf3c"
    :enabled  false
    :username  "dummy"
    :firstName "Duff"
    :lastName "Dummy"
    :email  "dummy@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
              :apikey "602cb9e58426c613c8b85abe"} ; Dummy user has apikey, should not actually happen
    :role "applicant"}
   ;; Dummy hakija 2: pena / pena
   {:id  "51112424c26b7342d92acf3d"
    :enabled  false
    :username  "dummy2"
    :firstName "Duff"
    :lastName "Dummy2"
    :email  "dummy2@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"}
    :role "applicant"}
   ;; Dummy hakija 3: pena / pena
   {:id  "51112424c26b7342d92acf3e"
    :enabled  false
    :username  "dummy3"
    :firstName "Duff"
    :lastName "Dummy3"
    :email  "dummy3@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"}
    :role "applicant"}
   ])

(def ya-default-attachments-for-operations {:ya-kayttolupa-tapahtumat                                          [[:muut :muu]]
                                            :ya-kayttolupa-harrastustoiminnan-jarjestaminen                    [[:muut :muu]]
                                            :ya-kayttolupa-metsastys                                           [[:muut :muu]]
                                            :ya-kayttolupa-vesistoluvat                                        [[:muut :muu]]
                                            :ya-kayttolupa-terassit                                            [[:muut :muu]]
                                            :ya-kayttolupa-kioskit                                             [[:muut :muu]]
                                            :ya-kayttolupa-muu-kayttolupa                                      [[:muut :muu]]
                                            :ya-kayttolupa-nostotyot                                           [[:muut :muu]]
                                            :ya-kayttolupa-vaihtolavat                                         [[:muut :muu]]
                                            :ya-kayttolupa-kattolumien-pudotustyot                             [[:muut :muu]]
                                            :ya-kayttolupa-muu-liikennealuetyo                                 [[:muut :muu]]
                                            :ya-kayttolupa-talon-julkisivutyot                                 [[:muut :muu]]
                                            :ya-kayttolupa-talon-rakennustyot                                  [[:muut :muu]]
                                            :ya-kayttolupa-muu-tyomaakaytto                                    [[:muut :muu]]
                                            :ya-kayttolupa-mainostus-ja-viitoitus                              [[:muut :muu]]
                                            :ya-katulupa-vesi-ja-viemarityot                                   [[:muut :muu]]
                                            :ya-katulupa-kaukolampotyot                                        [[:muut :muu]]
                                            :ya-katulupa-kaapelityot                                           [[:muut :muu]]
                                            :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat           [[:muut :muu]]
                                            :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen             [[:muut :muu]]
                                            :ya-sijoituslupa-maalampoputkien-sijoittaminen                     [[:muut :muu]]
                                            :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen      [[:muut :muu]]
                                            :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        [[:muut :muu]]
                                            :ya-sijoituslupa-muuntamoiden-sijoittaminen                        [[:muut :muu]]
                                            :ya-sijoituslupa-jatekatoksien-sijoittaminen                       [[:muut :muu]]
                                            :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        [[:muut :muu]]
                                            :ya-sijoituslupa-muu-sijoituslupa                                  [[:muut :muu]]
                                            :ya-jatkoaika                                                      [[:muut :muu]]})

(def organizations [;; Jarvenpaa R
                    {:id "186-R"
                     :name {:fi "J\u00E4rvenp\u00E4\u00E4n rakennusvalvonta"}
                     :scope [{:municipality "186"
                              :permitType "R"
                              :inforequest-enabled true
                              :new-application-enabled true}]
                     :links [{:name {:fi "J\u00E4rvenp\u00E4\u00E4" :sv "Tr\u00E4skenda"}
                              :url "http://www.jarvenpaa.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://www.jarvenpaa.fi/sivu/index.tmpl?sivu_id=182"}]
                     :krysp {:R {:url local-krysp :version "2.1.3" :ftpUser "dev_jarvenpaa"}}}

                    ;; Sipoo R
                    {:id "753-R"
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "753" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "753" :permitType "P" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "753" :permitType "YI" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "753" :permitType "YL" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "753" :permitType "MAL" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "753" :permitType "VVVL" :inforequest-enabled true :new-application-enabled true}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :operations-attachments {:asuinrakennus [[:paapiirustus :asemapiirros]
                                                              [:paapiirustus :pohjapiirros]
                                                              [:hakija :valtakirja]
                                                              [:muut :vaestonsuojasuunnitelma]]
                                              :vapaa-ajan-asuinrakennus [[:paapiirustus :pohjapiirros]
                                                                         [:hakija :ote_kauppa_ja_yhdistysrekisterista]
                                                                         [:muut :vaestonsuojasuunnitelma]
                                                                         [:muut :valaistussuunnitelma]]
                                              :poikkeamis [[:paapiirustus :asemapiirros]]
                                              :meluilmoitus [[:kartat :kartta-melun-ja-tarinan-leviamisesta]
                                                             [:muut :muu]]
                                              :yl-uusi-toiminta [[:muut :muu]]
                                              :maa-aineslupa [[:muut :muu]]
                                              "vvvl-vesijohdosta" [[:muut :muu]]
                                              }
                     :krysp {:R {:url local-krysp, :ftpUser "dev_sipoo", :version "2.1.2"}
                             :P {:ftpUser "dev_poik_sipoo" :version "2.1.2"}
                             :YI {:ftpUser "dev_ymp_sipoo" :version "2.1.2"}
                             :YL {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "2.1.2"}
                             :MAL {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "2.1.2"}
                             :VVVL {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "2.1.3"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Sipoo YA
                    ;; Keeping :inforequest-enabled true and :new-application-enabled true to allow krysp itests pass.
                    {:id "753-YA"
                     :name {:fi "Sipoon yleisten alueiden rakentaminen"}
                     :scope [{:municipality "753"
                              :permitType "YA"
                              :inforequest-enabled true
                              :new-application-enabled true}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}]
                     :krysp {:YA {:ftpUser "dev_ya_sipoo" :version "2.1.3"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                     :operations-attachments ya-default-attachments-for-operations}

                    ;; Kuopio YA
                    {:id "297-YA"
                     :name {:fi "Kuopio yleisten alueiden kaytto"}
                     :scope [{:municipality "297"
                              :permitType "YA"
                              :inforequest-enabled true
                              :new-application-enabled true}]
                     :links [{:name {:fi "Kuopio", :sv "Kuopio"}
                              :url "http://www.kuopio.fi"}]
                     :krysp {:YA {:url local-krysp, :version "2.1.2"
                                  :ftpUser "dev_ya_kuopio"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                     :operations-attachments ya-default-attachments-for-operations}


                    ;; Tampere R
                    {:id "837-R"
                     :name {:fi "Tampereen rakennusvalvonta"}
                     :scope [{:municipality "837"
                              :permitType "R"
                              :inforequest-enabled true
                              :new-application-enabled true}]
                     :links [{:name {:fi "Tampere" :sv "Tammerfors"}
                              :url "http://tampere.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://www.tampere.fi/asuminenjarakentaminen/rakennusvalvonta.html"}
                             {:name {:fi "Lomakkeet" :sv "Lomakkeet"}
                              :url "http://www.tampere.fi/asuminenjarakentaminen/rakennusvalvonta/lomakkeet.html"}]
                     :operations-attachments {:asuinrakennus [[:paapiirustus :asemapiirros]
                                                              [:paapiirustus :pohjapiirros]
                                                              [:hakija :valtakirja]
                                                              [:muut :vaestonsuojasuunnitelma]]
                                              :vapaa-ajan-asuinrakennus [[:paapiirustus :pohjapiirros]
                                                                         [:hakija :ote_kauppa_ja_yhdistysrekisterista]
                                                                         [:muut :vaestonsuojasuunnitelma]
                                                                         [:muut :valaistussuunnitelma]]}
                     :krysp {:R {:url local-krysp :version "2.1.4" :ftpUser "dev_tampere"}}}

                    ;; Tampere YA
                    {:id "837-YA",
                     :name {:fi "Tampere yleiset alueet"
                            :sv "Tammerfors yleiset alueet"}
                     :scope [{:municipality "837"
                              :permitType "YA"
                              :inforequest-enabled true
                              :new-application-enabled true}]
                     :statementGivers [{:id "521f1e82e4b0d14f5a87f179"
                                        :text "Paloviranomainen"
                                        :email "jussi.viranomainen@tampere.fi"
                                        :name "Jussi Viranomainen"}]
                     :krysp {:YA {:ftpUser "dev_ya_tampere" :version "2.1.2"}}
                     :operations-attachments ya-default-attachments-for-operations}

                    ;; Porvoo R
                    {:id "638-R"
                     :name {:fi "Porvoon rakennusvalvonta"}
                     :scope [{:municipality "638" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "638" :permitType "YI" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "638" :permitType "YL" :inforequest-enabled true :new-application-enabled true}]
                     :links [{:name {:fi "Porvoo", :sv "Borg\u00e5"}
                              :url "http://www.porvoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://www.porvoo.fi/fi/haku/palveluhakemisto/?a=viewitem&itemid=1030"}]}

                    ;; Oulu R
                    {:id "564-R"
                     :name {:fi "Oulun rakennusvalvonta"}
                     :scope [{:municipality "564" :permitType "R" :inforequest-enabled true :new-application-enabled true}]
                     :links [{:name {:fi "Oulu", :sv "Ule\u00E5borg"}
                              :url "http://www.ouka.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Fastigheter"}
                              :url "http://oulu.ouka.fi/rakennusvalvonta/"}]}

                    ;; Naantali R
                    {:id "529-R"
                     :name {:fi "Naantalin rakennusvalvonta"}
                     :scope [{:municipality "529" :permitType "R" :inforequest-enabled true :new-application-enabled true}]}

                    ;; Peruspalvelukuntayhtyma Selanne R
                    {:id "069-R"
                     :name {:fi "Peruspalvelukuntayhtym\u00E4 Sel\u00E4nne"}
                     :scope [{:municipality "069" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "317" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "626" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                             {:municipality "691" :permitType "R" :inforequest-enabled true :new-application-enabled true}]}

                    ;; Mikkeli Y
                    {:id "491-Y"
                     :name {:fi "Mikkeli ymp\u00E4rist\u00F6toimi" :sv "S:t Michel ymp\u00E4rist\u00F6toimi"}
                     :scope [{:municipality "491" :permitType "Y" :inforequest-enabled true :new-application-enabled true}]}

                    ;; Loppi R
                    ;; Organisation for municipality "Loppi" (known as "Takahikia") that uses the "neuvontapyynnon-avaus" system.
                    ;; Nice address for testing "Ojatie 1, Loppi"
                    {:id "433-R"
                     :name {:fi "Takahiki\u00e4n rakennusvalvonta"}
                     :scope [{:municipality "433"
                              :permitType "R"
                              :new-application-enabled false
                              :inforequest-enabled true
                              :open-inforequest true
                              :open-inforequest-email "erajorma@takahikia.fi"}]
                     :links [{:name {:fi "Takahiki\u00e4", :sv "Tillbakasvettas"}
                              :url "http://urbaanisanakirja.com/word/takahikia/"}]}


                    ;;
                    ;; Testeissa kaytettavia organisaatioita
                    ;;

                    ;; Sipoo R - New applications disabled
                    {:id "997-R-TESTI-1"
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "997" :permitType "R" :inforequest-enabled true :new-application-enabled false}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :krysp {:R {:url local-krysp, :version "2.1.2"
                                 :ftpUser "dev_sipoo"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Sipoo R - Inforequests disabled
                    {:id "998-R-TESTI-2"
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "998" :permitType "R" :inforequest-enabled false :new-application-enabled true}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :krysp {:R {:url local-krysp, :version "2.1.2"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Sipoo R - Both new applications and inforequests disabled
                    {:id "999-R-TESTI-3"
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "999" :permitType "R" :inforequest-enabled false :new-application-enabled false}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :krysp {:R {:url local-krysp :version "2.1.2"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}])

(deffixture "minimal" {}
  (mongo/clear!)
  (dorun (map (partial mongo/insert :users) users))
  (dorun (map (partial mongo/insert :organizations) organizations)))
