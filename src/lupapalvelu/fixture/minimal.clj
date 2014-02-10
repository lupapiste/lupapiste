(ns lupapalvelu.fixture.minimal
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture :refer :all]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.attachment :as attachment]))

(def ^:private local-krysp "http://localhost:8000/dev/krysp")

(def users
  ;; Solita admin:  admin / admin
  [{:id "777777777777777777000099"
    :email "admin@solita.fi"
    :enabled true
    :role :admin
    :personId "solita123"
    :firstName "Admin"
    :lastName "Admin"
    :phone "03030303"
    :username "admin"
    :private {:password "$2a$10$WHPur/hjvaOTlm41VFjtjuPI5hBoIMm8Y1p2vL4KqRi7QUvHMS1Ie"
              :apikey "5087ba34c2e667024fbd5992"}}
   ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti:  veikko / veikko
   {:id "777777777777777777000016"
    :email "veikko.viranomainen@tampere.fi"
    :enabled true
    :role :authority
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
    :private {:password "$2a$10$VnwROer5dhRJCQxoZusOney/hyN7Vk4ILQMSVqT8iZMO4XiQz.8Cm" ;; sakari
              }}

   ;; Sonja Sibbo - Sipoon lupa-arkkitehti:  sonja / sonja
   {:id "777777777777777777000023"
    :email "sonja.sibbo@sipoo.fi"
    :enabled true
    :role :authority
    :organizations ["753-R" "753-YA"]
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Sipoo"
    :username "sonja"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :apikey "5056e6d3aa24a1c901e6b9d1"}}
   ;; Ronja Sibbo - Sipoon lupa-arkkitehti:  ronja / sonja
   {:id "777777777777777777000024"
    :email "ronja.sibbo@sipoo.fi"
    :enabled true
    :role :authority
    :organizations ["753-R"]
    :firstName "Ronja"
    :lastName "Sibbo"
    :phone "03121991"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Sipoo"
    :username "ronja"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :apikey "5056e6d3aa24a1c901e6b9dd"}}
  {:id "777777777777777777000033"
    :email "pekka.borga@porvoo.fi"
    :enabled true
    :role :authority
    :organizations ["638-R"]
    :firstName "Pekka"
    :lastName "Borga"
    :phone "121212"
    :username "pekka"
    :private {:password "$2a$10$C65v2OgWcCzo4SVDtofawuP8xXDnZn5.URbODSpeOWmRABxUU01k6"
              :apikey "4761896258863737181711425832653651926670"}}
  {:id "777777777777777777000034"
    :email "olli.uleaborg@ouka.fi"
    :enabled true
    :role :authority
    :organizations ["564-R"]
    :personId "kunta564"
    :firstName "Olli"
    :lastName "Ule\u00E5borg"
    :phone "121212"
    :username "olli"
    :private {:password "$2a$10$JXFA55BPpNDpI/jDuPv76uW9TTgGHcDI2l5daelFcJbWvefB6THmi"
              :apikey "7634919923210010829057754770828315568705"}}
  ;; Simo Sippo:  sipoo / sipoo
  {:id "50ac77ecc2e6c2ea6e73f83e"
   :email "admin@sipoo.fi"
   :enabled true
   :role :authorityAdmin
   :organizations ["753-R"]
   :firstName "Simo"
   :lastName "Suurvisiiri"
   :username "sipoo"
   :private {:password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
             :apikey "50ac788ec2e6c2ea6e73f83f"}}
   {:id "50ac77ecd2e6c2ea6e73f83f" ;; naantali
    :email "admin@naantali.fi"
    :enabled true
    :role :authorityAdmin
    :organizations ["529-R"]
    :firstName "Admin"
    :lastName "Naantali"
    :username "admin@naantali.fi"
    :private {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f83f"}}
   {:id "50ac77ecd2e6c2ea6e73f840"
    :email "rakennustarkastaja@naantali.fi"
    :enabled true
    :role :authority
    :organizations ["529-R"]
    :firstName "Rakennustarkastaja"
    :lastName "Naantali"
    :username "rakennustarkastaja@naantali.fi"
    :private {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f840"}}
   {:id "50ac77ecd2e6c2ea6e73f841"
    :email "lupasihteeri@naantali.fi"
    :enabled true
    :role :authority
    :organizations ["529-R"]
    :firstName "Lupasihteeri"
    :lastName "Naantali"
    :username "lupasihteeri@naantali.fi"
    :private {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f841"}}
   {:id "50ac77ecd2e6c2ea6e73f850" ;; jarvenpaa
    :email "admin@jarvenpaa.fi"
    :enabled true
    :role :authorityAdmin
    :organizations ["186-R"]
    :firstName "Admin"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "admin@jarvenpaa.fi"
    :private {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f850"}}
   {:id "50ac77ecd2e6c2ea6e73f851"
    :email "rakennustarkastaja@jarvenpaa.fi"
    :enabled true
    :role :authority
    :organizations ["186-R"]
    :firstName "Rakennustarkastaja"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "rakennustarkastaja@jarvenpaa.fi"
    :private {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f851"}}
   {:id "50ac77ecd2e6c2ea6e73f852"
    :email "lupasihteeri@jarvenpaa.fi"
    :enabled true
    :role :authority
    :organizations ["186-R"]
    :firstName "Lupasihteeri"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "lupasihteeri@jarvenpaa.fi"
    :private {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f852"}}
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
    :private {:password "$2a$10$hkJ5ZQhqL66iM2.3m4712eDIH1K1Ez6wp7FeV9DTkPCNEZz8IfrAe" :apikey "tampereYAapikey"}} ;; tampere
   ;; Kuopio ya paakayttaja:  kuopio-ya / kuopio
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
   ;; Admin
   {:id "505718b0aa24a1c901e6ba24"
    :enabled true
    :firstName "Judge"
    :lastName "Dread"
    :email "judge.dread@example.com"
    :role :admin
    :private {:apikey "505718b0aa24a1c901e6ba24"}}
   ;; Mikko's neighbour
   {:lastName "Nieminen"
    :firstName "Teppo"
    :enabled true
    :postalCode "33200"
    :username "teppo@example.com"
    :private {:password "$2a$10$KKBZSYTFTEFlRrQPa.PYPe9wz4q1sRvjgEUCG7gt8YBXoYwCihIgG"
              :apikey "502cb9e58426c613c8b85abb"}
    :phone "0505503171"
    :email "teppo@example.com"
    :personId "210281-0001"
    :role "applicant"
    :id "5073c0a1c2e6c470aef589a5"
    :street "Mutakatu 7"
    :zip "33560"
    :city "Tampere"}
   ;; Mikko Intonen
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
    :personId "010203-0405"
    :firstName "Pena"
    :lastName "Panaani"
    :email "pena@example.com"
    :street "Paapankuja 12"
    :zip "10203"
    :city "Piippola"
    :phone "0102030405"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
              :apikey "502cb9e58426c613c8b85abd"}}
   ;; Dummy 1: pena / pena
   {:id  "51112424c26b7342d92acf3c"
    :enabled  false
    :username  "dummy"
    :firstName "Duff"
    :lastName "Dummy"
    :email  "dummy@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
              :apikey "602cb9e58426c613c8b85abe"} ; Dummy user has apikey, should not actually happen
    :role  "applicant"}
   ;; Dummy 2: pena / pena
   {:id  "51112424c26b7342d92acf3d"
    :enabled  false
    :username  "dummy2"
    :firstName "Duff"
    :lastName "Dummy2"
    :email  "dummy2@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"}
    :role  "applicant"}
   ;; Dummy 3: pena / pena
   {:id  "51112424c26b7342d92acf3e"
    :enabled  false
    :username  "dummy3"
    :firstName "Duff"
    :lastName "Dummy3"
    :email  "dummy3@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"}
    :role  "applicant"}
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

(def organizations [{:id "186-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "J\u00E4rvenp\u00E4\u00E4n rakennusvalvonta"}
                     :scope [{:municipality "186" :permitType "R"}]
                     :links [{:name {:fi "J\u00E4rvenp\u00E4\u00E4" :sv "Tr\u00E4skenda"}
                              :url "http://www.jarvenpaa.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://www.jarvenpaa.fi/sivu/index.tmpl?sivu_id=182"}]
                     :krysp {:R {:url local-krysp :version "2.1.3" :ftpUser "jarvenpaa"}}}

                    {:id "753-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "753" :permitType "R"} {:municipality "753" :permitType "P"}]
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
                                              :poikkeamis [[:paapiirustus :asemapiirros]]}
                     :krysp {:R {:url local-krysp, :version "2.1.2"
                                 :ftpUser "sipoo"}
                             :P {:ftpUser "poik_sipoo" :version "2.1.2"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Keeping :inforequest-enabled true and :new-application-enabled true to allow krysp itests pass.
                    {:id "753-YA"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Sipoon yleisten alueiden rakentaminen"}
                     :scope [{:municipality "753" :permitType "YA"}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}]
                     :krysp {:YA {:ftpUser "ya_sipoo" :version "2.1.2"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                     :operations-attachments ya-default-attachments-for-operations}

                    ;;Kuopio-ya
                    {:id "297-YA"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Kuopio yleisten alueiden kaytto"}
                     :scope [{:municipality "297" :permitType "YA"}]
                     :links [{:name {:fi "Kuopio", :sv "Kuopio"}
                              :url "http://www.kuopio.fi"}]
                     :krysp {:YA {:url local-krysp, :version "2.1.2"
                                  :ftpUser "ya_kuopio"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                     :operations-attachments ya-default-attachments-for-operations}



                    {:id "837-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Tampereen rakennusvalvonta"}
                     :scope [{:municipality "837" :permitType "R"}]
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
                                                                         [:muut :valaistussuunnitelma]]}}

                    {:id "837-YA",
                     :inforequest-enabled true
                     :name {:fi "Tampere yleiset alueet"
                            :sv "Tammerfors yleiset alueet"}
                     :new-application-enabled true
                     :scope [{:municipality "837" :permitType "YA"}]
                     :statementGivers [{:id "521f1e82e4b0d14f5a87f179"
                                        :text "Paloviranomainen"
                                        :email "jussi.viranomainen@tampere.fi"
                                        :name "Jussi Viranomainen"}]
                     :krysp {:YA {:ftpUser "ya_tampere" :version "2.1.2"}}
                     :operations-attachments ya-default-attachments-for-operations}

                    {:id "638-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Porvoon rakennusvalvonta"}
                     :scope [{:municipality "638" :permitType "R"}]
                     :links [{:name {:fi "Porvoo", :sv "Borg\u00e5"}
                              :url "http://www.porvoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://www.porvoo.fi/fi/haku/palveluhakemisto/?a=viewitem&itemid=1030"}]}

                    {:id "564-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Oulun rakennusvalvonta"}
                     :scope [{:municipality "564" :permitType "R"}]
                     :links [{:name {:fi "Oulu", :sv "Ule\u00E5borg"}
                              :url "http://www.ouka.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Fastigheter"}
                              :url "http://oulu.ouka.fi/rakennusvalvonta/"}]}

                    {:id "529-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Naantalin rakennusvalvonta"}
                     :scope [{:municipality "529" :permitType "R"}]}

                    {:id "069-R"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Peruspalvelukuntayhtym\u00E4 Sel\u00E4nne"}
                     :scope [{:municipality "069" :permitType "R"}
                             {:municipality "317" :permitType "R"}
                             {:municipality "626" :permitType "R"}
                             {:municipality "691" :permitType "R"}]}

                    {:id "491-Y"
                     :inforequest-enabled true
                     :new-application-enabled true
                     :name {:fi "Mikkeli ymp\u00E4rist\u00F6toimi" :sv "S:t Michel ymp\u00E4rist\u00F6toimi"}
                     :scope [{:municipality "491" :permitType "Y"}]}

                    ;;
                    ;; Testeissa kaytettavia organisaatioita
                    ;;

                    ;; New applications disabled
                    {:id "997-R-TESTI-1"
                     :inforequest-enabled true
                     :new-application-enabled false
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "997" :permitType "R"}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :krysp {:R {:url local-krysp, :version "2.1.2"
                                 :ftpUser "sipoo"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Inforequests disabled
                    {:id "998-R-TESTI-2"
                     :inforequest-enabled false
                     :new-application-enabled true
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "998" :permitType "R"}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :krysp {:R {:url local-krysp, :version "2.1.2"
                                 :ftpUser "sipoo"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Both new applications and inforequests disabled
                    {:id "999-R-TESTI-3"
                     :inforequest-enabled false
                     :new-application-enabled false
                     :name {:fi "Sipoon rakennusvalvonta"}
                     :scope [{:municipality "999" :permitType "R"}]
                     :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                              :url "http://sipoo.fi"}
                             {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                              :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                     :krysp {:R {:url local-krysp, :version "2.1.2"
                                 :ftpUser "sipoo"}}
                     :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]}

                    ;; Organisation for municipality "Loppi" (known as "Takahikia") that uses the "neuvontapyynon-avaus" system.
                    ;; Nice address for testing "Ojatie 1, Loppi"

                    {:id "433-R"
                     :open-inforequest true
                     :open-inforequest-email "erajorma@takahikia.fi"
                     :inforequest-enabled true
                     :new-application-enabled false
                     :name {:fi "Takahiki\u00e4n rakennusvalvonta"}
                     :scope [{:municipality "433" :permitType "R"}]
                     :links [{:name {:fi "Takahiki\u00e4", :sv "Tillbakasvettas"}
                              :url "http://urbaanisanakirja.com/word/takahikia/"}]}])

(deffixture "minimal" {}
  (mongo/clear!)
  (dorun (map (partial mongo/insert :users) users))
  (dorun (map (partial mongo/insert :organizations) organizations)))
