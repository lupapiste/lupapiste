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
   {:id "50ac77ecd2e6c2ea6e799909"
    :email "rakennustarkastaja@tampere.fi"
    :enabled true
    :role :authority
    :municipality "837"
    :firstName "Rakennustarkastaja"
    :lastName "Tampere"
    :username "rakennustarkastaja@tampere.fi"
    :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
              :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
              :apikey "50ac77ecd2e6c2ea6e799909"}}
		{:id "50ac77ecd2e6c2ea6e799910"
		 :email "rakennustarkastaja@enontekio.fi"
		 :enabled true
		 :role :authority
		 :municipality "047"
		 :firstName "Rakennustarkastaja"
		 :lastName "Enontekiö"
		 :username "rakennustarkastaja@enontekio.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799910"}}
		{:id "50ac77ecd2e6c2ea6e799911"
		 :email "rakennustarkastaja@hanko.fi"
		 :enabled true
		 :role :authority
		 :municipality "078"
		 :firstName "Rakennustarkastaja"
		 :lastName "Hanko"
		 :username "rakennustarkastaja@hanko.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799911"}}
		{:id "50ac77ecd2e6c2ea6e799912"
		 :email "rakennustarkastaja@harjavalta.fi"
		 :enabled true
		 :role :authority
		 :municipality "079"
		 :firstName "Rakennustarkastaja"
		 :lastName "Harjavalta"
		 :username "rakennustarkastaja@harjavalta.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799912"}}
		{:id "50ac77ecd2e6c2ea6e799913"
		 :email "rakennustarkastaja@heinavesi.fi"
		 :enabled true
		 :role :authority
		 :municipality "090"
		 :firstName "Rakennustarkastaja"
		 :lastName "Heinävesi"
		 :username "rakennustarkastaja@heinavesi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799913"}}
		{:id "50ac77ecd2e6c2ea6e799914"
		 :email "rakennustarkastaja@hyvinkaa.fi"
		 :enabled true
		 :role :authority
		 :municipality "106"
		 :firstName "Rakennustarkastaja"
		 :lastName "Hyvinkää"
		 :username "rakennustarkastaja@hyvinkaa.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799914"}}
		{:id "50ac77ecd2e6c2ea6e799915"
		 :email "rakennustarkastaja@hameenlinna.fi"
		 :enabled true
		 :role :authority
		 :municipality "109"
		 :firstName "Rakennustarkastaja"
		 :lastName "Hämeenlinna"
		 :username "rakennustarkastaja@hameenlinna.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799915"}}
		{:id "50ac77ecd2e6c2ea6e799916"
		 :email "rakennustarkastaja@janakkala.fi"
		 :enabled true
		 :role :authority
		 :municipality "165"
		 :firstName "Rakennustarkastaja"
		 :lastName "Janakkala"
		 :username "rakennustarkastaja@janakkala.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799916"}}
		{:id "50ac77ecd2e6c2ea6e799917"
		 :email "rakennustarkastaja@kajaani.fi"
		 :enabled true
		 :role :authority
		 :municipality "205"
		 :firstName "Rakennustarkastaja"
		 :lastName "Kajaani"
		 :username "rakennustarkastaja@kajaani.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799917"}}
		{:id "50ac77ecd2e6c2ea6e799918"
		 :email "rakennustarkastaja@kempele.fi"
		 :enabled true
		 :role :authority
		 :municipality "244"
		 :firstName "Rakennustarkastaja"
		 :lastName "Kempele"
		 :username "rakennustarkastaja@kempele.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799918"}}
		{:id "50ac77ecd2e6c2ea6e799919"
		 :email "rakennustarkastaja@kirkkonummi.fi"
		 :enabled true
		 :role :authority
		 :municipality "257"
		 :firstName "Rakennustarkastaja"
		 :lastName "Kirkkonummi"
		 :username "rakennustarkastaja@kirkkonummi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799919"}}
		{:id "50ac77ecd2e6c2ea6e799920"
		 :email "rakennustarkastaja@kuopio.fi"
		 :enabled true
		 :role :authority
		 :municipality "297"
		 :firstName "Rakennustarkastaja"
		 :lastName "Kuopio"
		 :username "rakennustarkastaja@kuopio.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799920"}}
		{:id "50ac77ecd2e6c2ea6e799921"
		 :email "rakennustarkastaja@mikkeli.fi"
		 :enabled true
		 :role :authority
		 :municipality "491"
		 :firstName "Rakennustarkastaja"
		 :lastName "Mikkeli"
		 :username "rakennustarkastaja@mikkeli.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799921"}}
		{:id "50ac77ecd2e6c2ea6e799922"
		 :email "rakennustarkastaja@mustasaari.fi"
		 :enabled true
		 :role :authority
		 :municipality "499"
		 :firstName "Rakennustarkastaja"
		 :lastName "Mustasaari"
		 :username "rakennustarkastaja@mustasaari.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799922"}}
		{:id "50ac77ecd2e6c2ea6e799923"
		 :email "rakennustarkastaja@nivala.fi"
		 :enabled true
		 :role :authority
		 :municipality "535"
		 :firstName "Rakennustarkastaja"
		 :lastName "Nivala"
		 :username "rakennustarkastaja@nivala.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799923"}}
		{:id "50ac77ecd2e6c2ea6e799924"
		 :email "rakennustarkastaja@eura.fi"
		 :enabled true
		 :role :authority
		 :municipality "050"
		 :firstName "Rakennustarkastaja"
		 :lastName "Eura"
		 :username "rakennustarkastaja@eura.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799924"}}
		{:id "50ac77ecd2e6c2ea6e799925"
		 :email "rakennustarkastaja@koylio.fi"
		 :enabled true
		 :role :authority
		 :municipality "319"
		 :firstName "Rakennustarkastaja"
		 :lastName "Köyliö"
		 :username "rakennustarkastaja@koylio.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799925"}}
		{:id "50ac77ecd2e6c2ea6e799926"
		 :email "rakennustarkastaja@sakyla.fi"
		 :enabled true
		 :role :authority
		 :municipality "783"
		 :firstName "Rakennustarkastaja"
		 :lastName "Säkylä"
		 :username "rakennustarkastaja@sakyla.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799926"}}
		{:id "50ac77ecd2e6c2ea6e799927"
		 :email "rakennustarkastaja@salla.fi"
		 :enabled true
		 :role :authority
		 :municipality "732"
		 :firstName "Rakennustarkastaja"
		 :lastName "salla"
		 :username "rakennustarkastaja@salla.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799927"}}
		{:id "50ac77ecd2e6c2ea6e799928"
		 :email "rakennustarkastaja@salo.fi"
		 :enabled true
		 :role :authority
		 :municipality "734"
		 :firstName "Rakennustarkastaja"
		 :lastName "Salo"
		 :username "rakennustarkastaja@salo.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799928"}}
  {:lastName "Hakija"
   :firstName "Testi"
   :enabled true
   :postalCode "33200"
   :username "hakija@testi.fi"
   :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
             :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		         :apikey "50ac77ecd2e6c2ea6e799929"}
   :phone "050123456"
   :email "hakija@testi.fi"
   :personId "150601-0001"
   :role "applicant"
   :id "5073c0a1c2e6c470aef88811"
   :street "Testikatu 2"
   :zip "33200"
   :city "Tampere"}  
  {:lastName "Hakijakaksi"
   :firstName "Testi"
   :enabled true
   :postalCode "33200"
   :username "hakija2@testi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799930"}
   :phone "050123456"
   :email "hakija2@testi.fi"
   :personId "150602-0002"
   :role "applicant"
   :id "5073c0a1c2e6c470aef88812"
   :street "Testikatu 2"
   :zip "33200"
   :city "Tampere"}
  {:lastName "Hakijakolme"
   :firstName "Testi"
   :enabled true
   :postalCode "33200"
   :username "hakija3@testi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799931"}
   :phone "050123456"
   :email "hakija3@testi.fi"
   :personId "150603-0003"
   :role "applicant"
   :id "5073c0a1c2e6c470aef88813"
   :street "Testikatu 2"
   :zip "33200"
   :city "Tampere"}
  {:lastName "Hakijanelja"
   :firstName "Testi"
   :enabled true
   :postalCode "33200"
   :username "hakija4@testi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799932"}
   :phone "050123456"
   :email "hakija4@testi.fi"
   :personId "150604-0004"
   :role "applicant"
   :id "5073c0a1c2e6c470aef88814"
   :street "Testikatu 4"
   :zip "33200"
   :city "Tampere"}
  {:lastName "Hakijaviisi"
   :firstName "Testi"
   :enabled true
   :postalCode "33200"
   :username "hakija5@testi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799933"}
   :phone "050123456"
   :email "hakija5@testi.fi"
   :personId "150605-0005"
   :role "applicant"
   :id "5073c0a1c2e6c470aef88815"
   :street "Testikatu 5"
   :zip "33200"
   :city "Tampere"}
  {:lastName "Hakijakuusi"
   :firstName "Testi"
   :enabled true
   :postalCode "33200"
   :username "hakija6@testi.fi"
		 :private {:salt "$2a$10$DC0FxcCzQO7ppdJsoYdTEO",
		           :password "$2a$10$DC0FxcCzQO7ppdJsoYdTEON8XlMzyS4t/FmjepX.qNjDwnZqrjHnO"
		           :apikey "50ac77ecd2e6c2ea6e799934"}
   :phone "050123456"
   :email "hakija6@testi.fi"
   :personId "150606-0006"
   :role "applicant"
   :id "5073c0a1c2e6c470aef88816"
   :street "Testikatu 6"
   :zip "33200"
   :city "Tampere"}
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
                      :name {:fi "Enontekiö", :sv "Enontekis"}
                      :municipalityCode "047"}
                     {:id "078"
                      :name {:fi "Hanko", :sv "Hangö"}
                      :municipalityCode "078"}
                     {:id "079"
                      :name {:fi "Harjavalta", :sv "Harjavalta"}
                      :municipalityCode "079"}
                     {:id "090"
                      :name {:fi "Heinävesi", :sv "Heinävesi"}
                      :municipalityCode "090"}
                     {:id "106"
                      :name {:fi "Hyvinkää", :sv "Hyvinge"}
                      :municipalityCode "106"}
                     {:id "109"
                      :name {:fi "Hämeenlinna", :sv "Tavastehus"}
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
                      :name {:fi "Kirkkonummi", :sv "Kyrkslätt"}
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
                      :name {:fi "Köyliö", :sv "Kjulo"}
                      :municipalityCode "319"}
                     {:id "783"
                      :name {:fi "Säkylä", :sv "Säkylä"}
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
