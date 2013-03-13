(ns lupapalvelu.fixture.minimal
  (:use lupapalvelu.fixture)
  (:require [lupapalvelu.mongo :as mongo]))

(def users
  [{:id "777777777777777777000099" ;; admin
    :email "admin@solita.fi"
    :enabled true
    :role :admin
    :personId "solita123"
    :firstName "Admin"
    :lastName "Admin"
    :phone "03030303"
    :username "admin"
    :private {:password "$2a$10$WHPur/hjvaOTlm41VFjtjuPI5hBoIMm8Y1p2vL4KqRi7QUvHMS1Ie"
              :salt "$2a$10$WHPur/hjvaOTlm41VFjtju"
              :apikey "5087ba34c2e667024fbd5992"}}
   {:id "777777777777777777000016" ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti
    :email "veikko.viranomainen@tampere.fi"
    :enabled true
    :role :authority
    :municipality "837"
    :firstName "Veikko"
    :lastName "Viranomainen"
    :phone "03121991"
    :username "veikko"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuLF5AQqkSO5S1DJOgziMep.xJLYm3.xG"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "5051ba0caa2480f374dcfeff"}}
   ;; Sonja Sibbo - Sipoon lupa-arkkitehti
   ;; sonja / sonja
   {:id "777777777777777777000023"
    :email "sonja.sibbo@sipoo.fi"
    :enabled true
    :role :authority
    :municipality "753"
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :username "sonja"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "5056e6d3aa24a1c901e6b9d1"}}
   ;; Ronja Sibbo - Sipoon lupa-arkkitehti
   ;; ronja / sonja
   {:id "777777777777777777000024"
    :email "ronja.sibbo@sipoo.fi"
    :enabled true
    :role :authority
    :municipality "753"
    :firstName "Ronja"
    :lastName "Sibbo"
    :phone "03121991"
    :username "ronja"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "5056e6d3aa24a1c901e6b9dd"}}
  {:id "777777777777777777000033"
    :email "pekka.borga@porvoo.fi"
    :enabled true
    :role :authority
    :municipality "638"
    :firstName "Pekka"
    :lastName "Borga"
    :phone "121212"
    :username "pekka"
    :private {:password "$2a$10$C65v2OgWcCzo4SVDtofawuP8xXDnZn5.URbODSpeOWmRABxUU01k6"
              :salt "$2a$10$C65v2OgWcCzo4SVDtofawu"
              :apikey "4761896258863737181711425832653651926670"}}
  {:id "777777777777777777000034"
    :email "olli.uleaborg@ouka.fi"
    :enabled true
    :role :authority
    :municipality "564"
    :personId "kunta564"
    :firstName "Olli"
    :lastName "Ule\u00E5borg"
    :phone "121212"
    :username "olli"
    :private {:password "$2a$10$JXFA55BPpNDpI/jDuPv76uW9TTgGHcDI2l5daelFcJbWvefB6THmi"
              :salt "$2a$10$JXFA55BPpNDpI/jDuPv76u"
              :apikey "7634919923210010829057754770828315568705"}}
    ;; sipoo / sipoo
   {:id "50ac77ecc2e6c2ea6e73f83e" ;; Simo Sippo
    :email "admin@sipoo.fi"
    :enabled true
    :role :authorityAdmin
    :municipality "753"
    :firstName "Simo"
    :lastName "Suurvisiiri"
    :username "sipoo"
    :private {:salt "$2a$10$VFcksPILCd9ykyl.1FIhwO",
              :password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
              :apikey "50ac788ec2e6c2ea6e73f83f"}}
   {:id "50ac77ecd2e6c2ea6e73f83f" ;; naantali
    :email "admin@naantali.fi"
    :enabled true
    :role :authorityAdmin
    :municipality "529"
    :firstName "Admin"
    :lastName "Naantali"
    :username "admin@naantali.fi"
    :private {:salt "$2a$10$4pvNDXk2g5XgxT.whx1Ua.",
              :password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f83f"}}
   {:id "50ac77ecd2e6c2ea6e73f840"
    :email "rakennustarkastaja@naantali.fi"
    :enabled true
    :role :authority
    :municipality "529"
    :firstName "Rakennustarkastaja"
    :lastName "Naantali"
    :username "rakennustarkastaja@naantali.fi"
    :private {:salt "$2a$10$4pvNDXk2g5XgxT.whx1Ua.",
              :password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f840"}}
   {:id "50ac77ecd2e6c2ea6e73f841"
    :email "lupasihteeri@naantali.fi"
    :enabled true
    :role :authority
    :municipality "529"
    :firstName "Lupasihteeri"
    :lastName "Naantali"
    :username "lupasihteeri@naantali.fi"
    :private {:salt "$2a$10$4pvNDXk2g5XgxT.whx1Ua.",
              :password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
              :apikey "a0ac77ecd2e6c2ea6e73f841"}}
   {:id "50ac77ecd2e6c2ea6e73f850" ;; jarvenpaa
    :email "admin@jarvenpaa.fi"
    :enabled true
    :role :authorityAdmin
    :municipality "186"
    :firstName "Admin"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "admin@jarvenpaa.fi"
    :private {:salt "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.",
              :password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f850"}}
   {:id "50ac77ecd2e6c2ea6e73f851"
    :email "rakennustarkastaja@jarvenpaa.fi"
    :enabled true
    :role :authority
    :municipality "186"
    :firstName "Rakennustarkastaja"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "rakennustarkastaja@jarvenpaa.fi"
    :private {:salt "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.",
              :password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f851"}}
   {:id "50ac77ecd2e6c2ea6e73f852"
    :email "lupasihteeri@jarvenpaa.fi"
    :enabled true
    :role :authority
    :municipality "186"
    :firstName "Lupasihteeri"
    :lastName "J\u00E4rvenp\u00E4\u00E4"
    :username "lupasihteeri@jarvenpaa.fi"
    :private {:salt "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.",
              :password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
              :apikey "a0ac77ecd2e6c2ea6e73f852"}}
   {:id "505718b0aa24a1c901e6ba24" ;; Admin
    :enabled true
    :firstName "Judge"
    :lastName "Dread"
    :email "judge.dread@example.com"
    :role :admin
    :private {:apikey "505718b0aa24a1c901e6ba24"}}
   {:lastName "Nieminen" ;; Mikkos neighbour
    :firstName "Teppo"
    :enabled true
    :postalCode "33200"
    :username "teppo@example.com"
    :private {:salt "$2a$10$KKBZSYTFTEFlRrQPa.PYPe"
              :password "$2a$10$KKBZSYTFTEFlRrQPa.PYPe9wz4q1sRvjgEUCG7gt8YBXoYwCihIgG"
              :apikey "502cb9e58426c613c8b85abb"}
    :phone "0505503171"
    :email "teppo@example.com"
    :personId "210281-0001"
    :role "applicant"
    :id "5073c0a1c2e6c470aef589a5"
    :street "Mutakatu 7"
    :zip "33560"
    :city "Tampere"}
   {:id "777777777777777777000010" ;; Mikko Intonen
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
    :private {:password "$2a$10$zwb/nvYQu4b1oZGpxz8.QOqHEBx3vXw9brc3NqDexgMbDuU2pwL9q"
              :salt "$2a$10$zwb/nvYQu4b1oZGpxz8.QO"
              :apikey "502cb9e58426c613c8b85abc"}}
   {:id "777777777777777777000020" ;; pena
    :username "pena"
    :enabled true
    :role "applicant"
    :personId "010203-0405"
    :firstName "Pena"
    :lastName "Panaani"
    :email "pena"
    :street "Paapankuja 12"
    :zip "010203"
    :city "Piippola"
    :phone "0102030405"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
              :salt "$2a$10$hLCt8BvzrJScTOGQcXJ34e"
              :apikey "502cb9e58426c613c8b85abd"}}
   {:id  "51112424c26b7342d92acf3c"
    :enabled  false
    :username  "dummy"
    :email  "dummy@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy" ; pena
              :salt "$2a$10$hLCt8BvzrJScTOGQcXJ34e"
              :apikey "602cb9e58426c613c8b85abe"} ; Dummy user has apikey, should not actually happen
    :role  "applicant"}
   {:id  "51112424c26b7342d92acf3d"
    :enabled  false
    :username  "dummy2"
    :email  "dummy2@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy" ; pena
              :salt "$2a$10$hLCt8BvzrJScTOGQcXJ34e"}
    :role  "applicant"}
   {:id  "51112424c26b7342d92acf3e"
    :enabled  false
    :username  "dummy3"
    :email  "dummy3@example.com"
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy" ; pena
              :salt "$2a$10$hLCt8BvzrJScTOGQcXJ34e"}
    :role  "applicant"}
   ])

(def municipalities [{:id "186"
                      :name {:fi "J\u00E4rvenp\u00E4\u00E4" :sv "Tr\u00E4skenda"}
                      :municipalityCode "186"
                      :links [{:name {:fi "J\u00E4rvenp\u00E4\u00E4" :sv "Tr\u00E4skenda"}
                               :url "http://www.jarvenpaa.fi"}
                              {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                               :url "http://www.jarvenpaa.fi/sivu/index.tmpl?sivu_id=182"}]}
                     {:id "529"
                      :name {:fi "Naantali", :sv "N\u00E5dendahl"}
                      :municipalityCode "529"}
                     {:id "564"
                      :name {:fi "Oulu", :sv "Ule\u00E5borg"}
                      :municipalityCode "564"
                      :links [{:name {:fi "Oulu", :sv "Ule\u00E5borg"}
                               :url "http://www.ouka.fi"}
                              {:name {:fi "Rakennusvalvonta", :sv "Fastigheter"}
                               :url "http://oulu.ouka.fi/rakennusvalvonta/"}]}
                     {:id "638"
                      :name {:fi "Porvoo", :sv "Porv\u00E5\u00E5"}
                      :municipalityCode "638"
                      :links [{:name {:fi "Porvoo", :sv "Porv\u00E5\u00E5"}
                               :url "http://www.porvoo.fi"}
                              {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                               :url "http://www.porvoo.fi/fi/haku/palveluhakemisto/?a=viewitem&itemid=1030"}]}
                     {:id "753"
                      :municipalityCode "753"
                      :name {:fi "Sipoo" :sv "Sibbo"}
                      :links [{:name {:fi "Sipoo", :sv "Sibbo"}
                               :url "http://sipoo.fi"}
                              {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                               :url "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta"}]
                      ;;:legacy "http://212.213.116.162/geoserver/wfs"}
                      :legacy "http://localhost:8000/krysp/building.xml"}
                     {:id "837"
                      :name {:fi "Tampere" :sv "Tammerfors"}
                      :municipalityCode "837"
                      :links [{:name {:fi "Tampere" :sv "Tammerfors"}
                               :url "http://tampere.fi"}
                              {:name {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                               :url "http://www.tampere.fi/asuminenjarakentaminen/rakennusvalvonta.html"}
                              {:name {:fi "Lomakkeet" :sv "Lomakkeet"}
                               :url "http://www.tampere.fi/asuminenjarakentaminen/rakennusvalvonta/lomakkeet.html"}]}
                     {:id "047"
                      :name {:fi "Enonteki\u00F6", :sv "Enontekis"}
                      :municipalityCode "047"}
                     {:id "078"
                      :name {:fi "Hanko", :sv "Hang\u00F6"}
                      :municipalityCode "078"}
                     {:id "079"
                      :name {:fi "Harjavalta", :sv "Harjavalta"}
                      :municipalityCode "079"}
                     {:id "090"
                      :name {:fi "Hein\u00E4vesi", :sv "Hein\u00E4vesi"}
                      :municipalityCode "090"}
                     {:id "106"
                      :name {:fi "Hyvink\u00E4\u00E4", :sv "Hyvinge"}
                      :municipalityCode "106"}
                     {:id "109"
                      :name {:fi "H\u00E4meenlinna", :sv "Tavastehus"}
                      :municipalityCode "109"}
                     {:id "165"
                      :name {:fi "Janakkala", :sv "Janakkala"}
                      :municipalityCode "165"}
                     {:id "205"
                      :name {:fi "Kajaani", :sv "Kajana"}
                      :municipalityCode "205"}
                     {:id "244"
                      :name {:fi "Kempele", :sv "Kempele"}
                      :municipalityCode "244"}
                     {:id "257"
                      :name {:fi "Kirkkonummi", :sv "Kyrksl\u00E4tt"}
                      :municipalityCode "257"}
                     {:id "297"
                      :name {:fi "Kuopio", :sv "Kuopio"}
                      :municipalityCode "297"}
                     {:id "491"
                      :name {:fi "Mikkeli", :sv "St.Michel"}
                      :municipalityCode "491"}
                     {:id "499"
                      :name {:fi "Mustasaari", :sv "Korsholm"}
                      :municipalityCode "499"}
                     {:id "535"
                      :name {:fi "Nivala", :sv "Nivala"}
                      :municipalityCode "535"}
                     {:id "050"
                      :name {:fi "Eura", :sv "Eura"}
                      :municipalityCode "050"}
                     {:id "319"
                      :name {:fi "K\u00F6yli\u00F6", :sv "Kjulo"}
                      :municipalityCode "319"}
                     {:id "783"
                      :name {:fi "S\u00E4kyl\u00E4", :sv "S\u00E4kyl\u00E4"}
                      :municipalityCode "783"}
                     {:id "732"
                      :name {:fi "Salla", :sv "Salla"}
                      :municipalityCode "732"}
                     {:id "734"
                      :name {:fi "Salo", :sv "Salo"}
                      :municipalityCode "734"}])

(deffixture "minimal" {}
  (mongo/clear!)
  (dorun (map (partial mongo/insert :users) users))
  (dorun (map (partial mongo/insert :municipalities) municipalities)))
