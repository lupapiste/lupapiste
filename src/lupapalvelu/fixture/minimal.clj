(ns lupapalvelu.fixture.minimal
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [lupapalvelu.fixture.core :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [sade.core :refer :all]
            [sade.env :as env]))

(def now-year (t/year (tc/from-long (now))))
(def- local-krysp "http://localhost:8000/dev/krysp")
(def- local-3d-map "http://localhost:8000/dev/3dmap")
(def- local-krysp-receiver (str (env/server-address) "/dev/krysp/receiver"))
(def- always-ok (str (env/server-address) "/dev/statusecho/200"))

(def users
  [;; Solita admin:  admin / admin
   {:id        "777777777777777777000099"
    :email     "admin@solita.fi"
    :enabled   true
    :role      "admin"
    :language  "fi"
    :firstName "Admin"
    :lastName  "Admin"
    :phone     "03030303"
    :username  "admin"
    :private   {:password "$2a$10$WHPur/hjvaOTlm41VFjtjuPI5hBoIMm8Y1p2vL4KqRi7QUvHMS1Ie"
                :apikey   "5087ba34c2e667024fbd5992"}}

   ;; ETL export user: solita-etl / solita-etl
   {:id        "solita-etl"
    :username  "solita-etl"
    :email     "etl@lupapiste.fi"
    :firstName "Solita"
    :lastName  "DW ETL-lataus"
    :language  "fi"
    :enabled   true
    :role      "trusted-etl"
    :private   {:password "$2a$10$uog/cI4n4vxFBNgku4xTpu6lcrF56cttBDW5zkTfDaSClgEw54/Nm"}}

   ;; Salesforce export user: salesforce-etl / salesforce-etl
   {:id        "salesforce-etl"
    :username  "salesforce-etl"
    :email     "sf-etl@lupapiste.fi"
    :firstName "Solita"
    :lastName  "SF ETL-lataus"
    :language  "fi"
    :enabled   true
    :role      "trusted-salesforce"
    :private   {:password "$2a$10$9PjOuMzuY/5oIpKR4PVACOWn2AFrwhTT2xtDe5sFXlJwRmk.6T6ji"}}

   ;; Vantaa

   ;; Vantaa YA paakayttaja:  vantaa-ya / vantaa123
   {:id        "092-YA"
    :enabled   true
    :lastName  "Vantaa"
    :firstName "Paakayttaja-YA"
    :city      "Vantaa"
    :language  "fi"
    :username  "vantaa-ya"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "vantaa-ya@example.com"
    :role      "authority"
    :zip       "10203"
    :orgAuthz  {:092-YA #{:authorityAdmin}}
    :private   {:password "$2a$10$PlE7fwuEM7Hos2Dl5dJtje9ygW7sO3.FyVHtoRGYZjxMpJGTuSTnS"
                :apikey   "vantaaYAapikey"}}

   ;; Esa Viranomainen - vantaalainen YA-lupa-arkkitehti:  esa / vantaa123
   {:id        "777777777777777777000088"
    :email     "esa.viranomainen@vantaa.fi"
    :enabled   true
    :role      "authority"
    :language  "fi"
    :username  "esa"
    :orgAuthz  {:092-YA #{:authority :approver}}
    :firstName "Esa"
    :lastName  "Viranomainen"
    :phone     "1231234567"
    :street    "Katuosoite 1 a 1"
    :zip       "33456"
    :city      "Vantaa"
    :private   {:password "$2a$10$BG/RVbBIMF.Bv08u8QcRXeQP7MlTpeYJZsl4YCnGSd/AdQ0qorfyO"
                :apikey   "5051ba0caa2480f374dcttee"}}

   {:id        "matti-rest-api-user"
    :username  "matti-rest-api-user"
    :email     "vantaa@example.com"
    :firstName "MATTI REST API user"
    :lastName  ""
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {:password "$2a$10$SfwXyczJskN.zaJeme9k.u6mSWdhTuYteT0Z5CCW8ddqInIMNM6Ia"} ;vantaa
    :orgAuthz  {:092-R  #{:authority}
                :092-YA #{:authority}}}

   ;; Vantaa R laskuttaja: vantaa-r-biller / vantaa
   {:id        "41cbff1bccdb6b92f7b64edd085447c0"
    :username  "vantaa-r-biller"
    :role      "authority"
    :enabled   true
    :language  "fi"
    :email     "r-biller@vantaa.fi"
    :orgAuthz  {:092-R #{:authority
                         :biller}}
    :firstName "Vantaa-R"
    :lastName  "Biller"
    :private   {:password "$2a$10$rSk9uFh5/fynTtaTgzKuiOw.odqOG4JXs3n9UC2qGA10/v/2KoZUa"
                :apikey   "vantaa-r-biller-apikey"}}

   ;; Tampere

   ;; Tampere YA paakayttaja:  tampere-ya / tampere
   {:id        "837-YA"
    :enabled   true
    :lastName  "Tampere"
    :firstName "Paakayttaja-YA"
    :city      "Tampere"
    :language  "fi"
    :username  "tampere-ya"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "tampere-ya@example.com"
    :role      "authority"
    :zip       "10203"
    :orgAuthz  {:837-YA #{:authorityAdmin}}
    :private   {:password "$2a$10$hkJ5ZQhqL66iM2.3m4712eDIH1K1Ez6wp7FeV9DTkPCNEZz8IfrAe" :apikey "tampereYAapikey"}}

   {:id        "837-R" ; tampere / tampere
    :enabled   true
    :lastName  "Tampere"
    :firstName "Paakayttaja"
    :city      "Tampere"
    :language  "fi"
    :username  "tampere"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "tampere@example.com"
    :role      "authority"
    :zip       "10203"
    :orgAuthz  {:837-R #{:authorityAdmin}}
    :private   {:password "$2a$10$hkJ5ZQhqL66iM2.3m4712eDIH1K1Ez6wp7FeV9DTkPCNEZz8IfrAe" :apikey "tampereapikey"}}

   {:id        "tampe-rest" ; tampe-rest / tampere
    :enabled   true
    :lastName  "REST"
    :firstName "Tampere"
    :city      "Tampere"
    :language  "fi"
    :username  "tampe-rest"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "tampe-rest@example.com"
    :role      "rest-api"
    :zip       "10203"
    :orgAuthz  {:837-R #{:authority}}
    :private   {:password "$2a$10$hkJ5ZQhqL66iM2.3m4712eDIH1K1Ez6wp7FeV9DTkPCNEZz8IfrAe"}}

   ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti:  veikko / veikko
   {:id        "777777777777777777000016"
    :email     "veikko.viranomainen@tampere.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:837-R #{:authority :approver :authorityAdmin}}
    :firstName "Veikko"
    :lastName  "Viranomainen"
    :phone     "03121991"
    :username  "veikko"
    :private   {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuLF5AQqkSO5S1DJOgziMep.xJLYm3.xG"
                :apikey   "5051ba0caa2480f374dcfeff"}}

   ;; Jussi Viranomainen - tamperelainen YA-lupa-arkkitehti:  jussi / jussi
   {:id        "777777777777777777000017"
    :email     "jussi.viranomainen@tampere.fi"
    :enabled   true
    :role      "authority"
    :language  "fi"
    :username  "jussi"
    :orgAuthz  {:837-YA #{:authority}}
    :firstName "Jussi"
    :lastName  "Viranomainen"
    :phone     "1231234567"
    :street    "Katuosoite 1 a 1"
    :zip       "33456"
    :city      "Tampere"
    :private   {:password "$2a$10$Wl49diVWkO6UpBABzjYR4e8zTwIJBDKiEyvw1O2EMOtV9fqHaXPZq"
                :apikey   "5051ba0caa2480f374dcfefg"}}

   ;; Tampere YA laskuttaja: tampere-ya-biller / tampere
   {:id        "bbca12d01f5a20b320daf73c214d945c"
    :username  "tampere-ya-biller"
    :role      "authority"
    :enabled   true
    :language  "fi"
    :email     "ya-biller@tampere.fi"
    :orgAuthz  {:837-YA #{:authority
                          :authorityAdmin
                          :biller}}
    :firstName "Tampere-YA"
    :lastName  "Biller"
    :private   {:password "$2a$10$1VNZrLXkNuZc2MHodTvg4u91KlaMiWnj1izt4PGvvcwsLlALJFoHO"
                :apikey   "tampere-ya-biller-apikey"}}

   ;; Kuopio

   ;; Sakari Viranomainen - Kuopion YA-lupa-arkkitehti:  sakari / sakari
   {:id        "77777777777777777700669"
    :email     "sakari.viranomainen@kuopio.fi"
    :enabled   true
    :role      "authority"
    :language  "fi"
    :username  "sakari"
    :orgAuthz  {:297-YA #{:authority}}
    :firstName "Sakari"
    :lastName  "Viranomainen"
    :phone     "1231234567"
    :street    "Katuosoite 1 a 1"
    :zip       "33456"
    :city      "Kuopio"
    :private   {:password "$2a$10$VnwROer5dhRJCQxoZusOney/hyN7Vk4ILQMSVqT8iZMO4XiQz.8Cm"}}

   ;; Kuopio YA-paakayttaja:  kuopio-ya / kuopio
   {:id        "297-YA"
    :enabled   true
    :lastName  "Kuopio"
    :firstName "Paakayttaja-YA"
    :city      "Kuopio"
    :language  "fi"
    :username  "kuopio-ya"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "kuopio-ya@example.com"
    :role      "authority"
    :zip       "10203"
    :orgAuthz  {:297-YA #{:authorityAdmin}}
    :private   {:password "$2a$10$YceveAiQXbeUs65B4FZ6lez/itf0UEXooHcZlygI2WnQGhF0dJ1jO"}}

   ;; Velho Viranomainen - Kuopio R viranomainen:  velho / velho
   {:id             "77777777777777777700645"
    :email          "velho.viranomainen@kuopio.fi"
    :enabled        true
    :role           "authority"
    :username       "velho"
    :language       "fi"
    :orgAuthz       {:297-R  #{:authority :approver}
                     :297-YA #{:authority :approver}}
    :firstName      "Velho"
    :lastName       "Viranomainen"
    :phone          "1231234567"
    :street         "Katuosoite 2 a 4"
    :zip            "33456"
    :personId       "180495-754N"
    :personIdSource "identification-service"
    :city           "Kuopio"
    :private        {:password "$2a$10$me2UOXOUfEbseJeLUBde8u2rlqOwHuqxbFT00q70QEvTpskHKol2m"
                     :apikey   "e1vshYravGWKA1QXL3NeWMmyzzBJmcgq6IUqKZmh"}}

   ;; Kuopio R-paakayttaja:  kuopio-r / kuopio

   {:id        "297-R"
    :enabled   true
    :lastName  "Kuopio"
    :firstName "Paakayttaja-R"
    :city      "Kuopio"
    :language  "fi"
    :username  "kuopio-r"
    :street    "Paapankuja 12"
    :phone     "0102030405"
    :email     "kuopio-r@kuopio.fi"
    :role      "authority"
    :zip       "10203"
    :orgAuthz  {:297-R #{:authorityAdmin}}
    :private   {:password "$2a$10$YceveAiQXbeUs65B4FZ6lez/itf0UEXooHcZlygI2WnQGhF0dJ1jO"
                :apikey   "lhIqT1YwOMH8HuiCGcjBtGggfeRaxZL5OUNd3r4u"}}

   ;; Sipoo

   ;; Simo Suurvisiiri - Sipoon R paakayttaja:  sipoo / sipoo
   {:id        "50ac77ecc2e6c2ea6e73f83e"
    :email     "admin@sipoo.fi"
    :enabled   true
    :role      "authority"
    :orgAuthz  {:753-R #{:authorityAdmin}}
    :firstName "Simo"
    :language  "fi"
    :lastName  "Suurvisiiri"
    :username  "sipoo"
    :private   {:password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
                :apikey   "50ac788ec2e6c2ea6e73f83f"}}

   ;; Simo YA-Suurvisiiri - Sipoon YA paakayttaja:  sipoo-ya / sipoo
   {:id        "50ac77eaf2e6c2ea6e73f81e"
    :email     "admin-ya@sipoo.fi"
    :enabled   true
    :role      "authority"
    :orgAuthz  {:753-YA #{:authorityAdmin}}
    :firstName "Simo"
    :language  "fi"
    :lastName  "YA-Suurvisiiri"
    :username  "sipoo-ya"
    :private   {:password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
                :apikey   "55cdafd8abc1d91e7ccd60b2"}}

   ;; Sonja Sibbo - Sipoon lupa-arkkitehti:  sonja / sonja
   {:id                 "777777777777777777000023"
    :username           "sonja"
    :role               "authority"
    :enabled            true
    :email              "sonja.sibbo@sipoo.fi"
    :orgAuthz           {:753-R         #{:authority :approver}
                         :753-YA        #{:authority :approver}
                         :998-R-TESTI-2 #{:authority :approver}}
    :firstName          "Sonja"
    :lastName           "Sibbo"
    :language           "fi"
    :phone              "03121991"
    :street             "Katuosoite 1 a 1"
    :zip                "33456"
    :city               "Sipoo"
    :private            {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
                         :apikey   "5056e6d3aa24a1c901e6b9d1"}
    :applicationFilters [{:id     "foobar"
                          :title  "Foobar"
                          :sort   {:asc   false
                                   :field "modified"}
                          :filter {:handlers      []
                                   :tags          []
                                   :operations    []
                                   :organizations []
                                   :areas         []}}
                         {:id     "barfoo"
                          :title  "Barfoo"
                          :sort   {:asc   false
                                   :field "modified"}
                          :filter {:handlers      []
                                   :tags          []
                                   :operations    []
                                   :organizations []
                                   :areas         []}}]}

   ;; Ronja Sibbo - Sipoon lupa-arkkitehti:  ronja / sonja
   {:id        "777777777777777777000024"
    :username  "ronja"
    :role      "authority"
    :enabled   true
    :language  "fi"
    :email     "ronja.sibbo@sipoo.fi"
    :orgAuthz  {:753-R  #{:authority :authorityAdmin}
                :753-YA #{:authorityAdmin}}
    :firstName "Ronja"
    :lastName  "Sibbo"
    :phone     "03121991"
    :street    "Katuosoite 1 a 1"
    :zip       "33456"
    :city      "Sipoo"
    :private   {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
                :apikey   "5056e6d3aa24a1c901e6b9dd"}}

   ;; Luukas Lukija - Sipoon katselija:  luukas / luukas
   {:id          "777777777777777777000025"
    :username    "luukas"
    :role        "authority"
    :enabled     true
    :language    "fi"
    :email       "luukas.lukija@sipoo.fi"
    :orgAuthz    {:753-R #{:reader}}
    :firstName   "Luukas"
    :lastName    "Lukija"
    :oauth-roles [{:client-id "oauth-test" :role "admin"}]
    :phone       "03121992"
    :street      "Katuosoite 1 a 2"
    :zip         "04130"
    :city        "Sipoo"
    :private     {:password "$2a$10$YM2XkcJVjM5JiqqR2qg7U.iUuY10LPYexYTfV/21RHOayn1xIf2sS"
                  :apikey   "5056e6d3aa24a1c901e6b9de"}}

   ;; Kosti Kommentoija - Sipoon kommentoija: kosti / kosti
   {:id          "777777777777777777000026"
    :username    "kosti"
    :role        "authority"
    :enabled     true
    :language    "fi"
    :email       "kosti.kommentoija@sipoo.fi"
    :orgAuthz    {:753-R #{:commenter}}
    :firstName   "Kosti"
    :lastName    "Kommentoija"
    :oauth-roles [{:client-id "foobar" :role "admin"}]
    :phone       "03121992"
    :street      "Katuosoite 1 a 3"
    :zip         "04130"
    :city        "Sipoo"
    :private     {:password "$2a$10$d2Ut/qSvKylOGhYm/7jXB..1ZC7/x39q5e/PFdtjHLqV1XW9wr3oO"
                  :apikey   "XDnPTeDDpPqU5yoYQEERgZ0p4H6dff1RIdYgyDCk"}}


   ;; Laura Laskuttaja - Sipoon laskuttaja: laura / laura
   {:id        "laura-laskuttaja"
    :username  "laura"
    :role      "authority"
    :enabled   true
    :language  "fi"
    :email     "laura@sipoo.fi"
    :orgAuthz  {:753-R   #{:biller}
                :753-YA  #{:biller}
                :FOO-ORG #{:biller}}
    :firstName "Laura"
    :lastName  "Laskuttaja"
    :phone     "555123123"
    :street    "Laskukatu 7"
    :zip       "04130"
    :city      "Sipoo"
    :private   {:password "$2a$10$WWdLOh4HmHEHvDUsrv4ULOkdTo5O13CqkeNQOdh9WX1FAWQfwwxRy"
                :apikey   "laura-laskuttaja-apikey"}}

   {:id        "sipoo-r-backend"
    :username  "sipoo-r-backend"
    :email     "sipoo-r-backend@sipoo.fi"
    :firstName "Sipoo"
    :lastName  "Taustaj\u00E4rjestelm\u00E4"
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {:password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"} ;sipoo
    :orgAuthz  {:753-R #{:authority}}}

   ;; Porvoo

   ;; Pekka Borga - Porvoon lupa-arkkitehti:  pekka / pekka
   {:id        "777777777777777777000033"
    :email     "pekka.borga@porvoo.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:638-R #{:authority :approver}}
    :firstName "Pekka"
    :lastName  "Borga"
    :phone     "121212"
    :username  "pekka"
    :private   {:password "$2a$10$C65v2OgWcCzo4SVDtofawuP8xXDnZn5.URbODSpeOWmRABxUU01k6"
                :apikey   "4761896258863737181711425832653651926670"}}

   ;; Oulu

   ;; Oulu Ymp Admin - Oulun YMP paakayttaja:  ymp-admin@oulu.fi / oulu
   ;; Viranomaisena myos Naantalissa
   {:id        "777777777777734777000034"
    :email     "ymp-admin@oulu.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:564-YMP #{:authorityAdmin}}
    :firstName "Oulu Ymp"
    :lastName  "Admin"
    :phone     "121212"
    :username  "ymp-admin@oulu.fi"
    :private   {:password "$2a$10$JA1Ec/bEUBrKLzeZX3aKNeyXcfCtjDdWyUQPTlL0rldhFhjq5Drje"
                :apikey   "YEU26a6TXHlapM18QGYST7WBYEU26a6TXHlapM18"}}

   ;; Olli Ule\u00E5borg - Oulun lupa-arkkitehti:  olli / olli
   ;; Viranomaisena myos Naantalissa
   {:id        "777777777777777777000034"
    :email     "olli.uleaborg@ouka.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:564-R   #{:authority :approver}
                :529-R   #{:authority :approver}
                :564-YMP #{:authority :approver}}
    :firstName "Olli"
    :lastName  "Ule\u00E5borg"
    :phone     "121212"
    :username  "olli"
    :private   {:password "$2a$10$JXFA55BPpNDpI/jDuPv76uW9TTgGHcDI2l5daelFcJbWvefB6THmi"
                :apikey   "7634919923210010829057754770828315568705"}}

   ;; YA viranomainen
   ;; Olli Ule\u00E5borg - Oulun lupa-arkkitehti:  olli-ya / olli
   ;; Viranomaisena myos Naantalissa
   {:id        "777777777777777777000035"
    :email     "olliya.uleaborg@ouka.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:564-YA  #{:authority :approver :archivist :tos-editor :tos-publisher}
                :564-YMP #{:authority :approver}}
    :firstName "Olli-ya"
    :lastName  "Ule\u00E5borg"
    :phone     "121212"
    :username  "olli-ya"
    :private   {:password "$2a$10$JXFA55BPpNDpI/jDuPv76uW9TTgGHcDI2l5daelFcJbWvefB6THmi"
                :apikey   "7634919923210010829057754770828315568706"}}

   ;; Naantali

   ;; Naantali R paakayttaja: admin@naantali.fi / naantali
   {:id        "50ac77ecd2e6c2ea6e73f83f"
    :email     "admin@naantali.fi"
    :enabled   true
    :role      "authority"
    :orgAuthz  {:529-R #{:authorityAdmin}}
    :firstName "Admin"
    :language  "fi"
    :lastName  "Naantali"
    :username  "admin@naantali.fi"
    :private   {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
                :apikey   "a0ac77ecd2e6c2ea6e73f83f"}}
   ;; rakennustarkastaja@naantali.fi / naantali
   ;; Viranomainen myos Jarvenpaassa
   {:id        "50ac77ecd2e6c2ea6e73f840"
    :email     "rakennustarkastaja@naantali.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:529-R #{:authority}
                :186-R #{:authority}}
    :firstName "Rakennustarkastaja"
    :lastName  "Naantali"
    :username  "rakennustarkastaja@naantali.fi"
    :private   {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
                :apikey   "a0ac77ecd2e6c2ea6e73f840"}}
   ;; lupasihteeri@naantali.fi / naantali
   {:id        "50ac77ecd2e6c2ea6e73f841"
    :email     "lupasihteeri@naantali.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:529-R #{:authority}}
    :firstName "Lupasihteeri"
    :lastName  "Naantali"
    :username  "lupasihteeri@naantali.fi"
    :private   {:password "$2a$10$4pvNDXk2g5XgxT.whx1Ua.RKkAoyjOb8C91r7aBMrgf7zNPMjhizq"
                :apikey   "a0ac77ecd2e6c2ea6e73f841"}}

   ;; Jarvenpaa

   ;; Jarvenpaan R paakayttaja: admin@jarvenpaa.fi / jarvenpaa
   {:id        "50ac77ecd2e6c2ea6e73f850"
    :email     "admin@jarvenpaa.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:186-R #{:authorityAdmin}}
    :firstName "Admin"
    :lastName  "J\u00E4rvenp\u00E4\u00E4"
    :username  "admin@jarvenpaa.fi"
    :private   {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
                :apikey   "a0ac77ecd2e6c2ea6e73f850"}}
   ;; rakennustarkastaja@jarvenpaa.fi / jarvenpaa
   {:id        "50ac77ecd2e6c2ea6e73f851"
    :email     "rakennustarkastaja@jarvenpaa.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:186-R #{:authority :archivist :approver}}
    :firstName "Rakennustarkastaja"
    :lastName  "J\u00E4rvenp\u00E4\u00E4"
    :username  "rakennustarkastaja@jarvenpaa.fi"
    :private   {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
                :apikey   "a0ac77ecd2e6c2ea6e73f851"}}
   ;; lupasihteeri@jarvenpaa.fi / jarvenpaa
   {:id        "50ac77ecd2e6c2ea6e73f852"
    :email     "lupasihteeri@jarvenpaa.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:186-R #{:authority}}
    :firstName "Lupasihteeri"
    :lastName  "J\u00E4rvenp\u00E4\u00E4"
    :username  "lupasihteeri@jarvenpaa.fi"
    :private   {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
                :apikey   "a0ac77ecd2e6c2ea6e73f852"}}
   ;; digitoija@jarvenpaa.fi / jarvenpaa
   {:id        "50ac77ecd2e6c2ea6e73f853"
    :email     "digitoija@jarvenpaa.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:186-R #{:digitizer}}
    :firstName "Dingo"
    :lastName  "Digitoija"
    :username  "digitoija@jarvenpaa.fi"
    :private   {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
                :apikey   "a0ac77ecd2e6c2ea6e73f853"}}
   ;; "projektikayttaja-186-r@lupapiste.fi" / jarvenpaa
   {:id                        "50ac77ecd2e6c2ea6e73f854"
    :email                     "projektikayttaja-186-r@lupapiste.fi"
    :enabled                   true
    :language                  "fi"
    :role                      "authority"
    :orgAuthz                  {:186-R #{:digitizer :digitization-project-user}}
    :firstName                 "Masa"
    :lastName                  "Masanen"
    :username                  "projektikayttaja-186-r@lupapiste.fi"
    :digitization-project-user true
    :private                   {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"
                                :apikey   "a0ac77ecd2e6c2ea6e73f855"}}
   ;; Torsti Tossavainen - Jarvenpaan TOS-vastaava: torsti / torsti
   {:id        "777777777777777777000027"
    :username  "torsti"
    :role      "authority"
    :enabled   true
    :language  "fi"
    :email     "torsti.tossavainen@jarvenpaa.fi"
    :orgAuthz  {:186-R #{:tos-editor :tos-publisher}} ; Note that :authority is not present
    :firstName "Torsti"
    :lastName  "Tossavainen"
    :private   {:password "$2a$10$eo2H257MMoJHMUCxAmEXVeRTDjaEL9qPpbQQiC/kdI38lPwXutklS"
                :apikey   "a0ac77ecd2e6c2ea6e73f854"}}

   {:id        "jarvenpaa-backend"
    :username  "jarvenpaa-backend"
    :email     "jarvenpaa@example.com"
    :firstName "J\u00E4rvenp\u00E4\u00E4"
    :lastName  "Taustaj\u00E4rjestelm\u00E4"
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {:password "$2a$10$eYl/SxvzYzOfIDIqjQIZ8.uhi57zPKg0m8J1BHwnAIx/sBcxYojvS"} ;jarvenpaa
    :orgAuthz  {:186-R #{:authority}}}

   {:id        "porvoo-backend"
    :username  "porvoo-backend"
    :email     "porvoo@example.com"
    :firstName "Porvoo"
    :lastName  "Taustaj\u00E4rjestelm\u00E4"
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {} ; testing autologin
    :orgAuthz  {:638-R #{:authority}}}

   ;; Loppi

   ;; Arto Viranomainen - Lopen R lupa-arkkitehti:  arto / arto
   {:id        "77775577777777777700769"
    :email     "arto.viranomainen@loppi.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :username  "arto"
    :orgAuthz  {:433-R #{:authority :authorityAdmin}}
    :firstName "Arto"
    :lastName  "Viranomainen"
    :phone     "1231234567"
    :street    "Katuosoite 1 a 1"
    :zip       "33456"
    :city      "Loppi"
    :private   {:password "$2a$10$MX9RWSqjBocwxg1ikSp/POV8lXd6lKA4yDymRIg7.GsXdRigZxmjK"
                :apikey   "o9uyecqzew89paklwkg0"}}

   ;; Helsinki - rakennustarkastaja@hel.fi / helsinki
   {:id        "5770f6ffd358719a09397a45"
    :email     "rakennustarkastaja@hel.fi"
    :enabled   true
    :language  "fi"
    :role      "authority"
    :orgAuthz  {:091-R  #{:authority :approver :archivist :tos-editor :tos-publisher}
                :091-YA #{:authority :approver}}
    :firstName "Hannu"
    :lastName  "Helsinki"
    :username  "rakennustarkastaja@hel.fi"
    :private   {:password "$2a$10$/bi569g4ijAitS82ES9MO.TDqGZrNlBrBPC1rE6N8v7uqTJbiHTNW"
                :apikey   "a0ac77ecd2e6c2ea6e73f860"}}

   ;; Helsinki Admin - Helsinki R paakayttaja:  helsinki / helsinki
   {:id        "50ac77ecc2e6c2ea6e73f665"
    :email     "admin@hel.fi"
    :enabled   true
    :role      "authority"
    :orgAuthz  {:091-R #{:authorityAdmin}}
    :firstName "Heikki"
    :language  "fi"
    :lastName  "Helsinki"
    :username  "helsinki"
    :private   {:password "$2a$10$/bi569g4ijAitS82ES9MO.TDqGZrNlBrBPC1rE6N8v7uqTJbiHTNW"
                :apikey   "50ac788ec2e6c2ea6e73f665"}}

   ;; Pori

   ;; Porin paakayttaja - pori/pori
   {:id        "pori"
    :username  "pori"
    :role      "authority"
    :enabled   true
    :email     "pertteli.porilainen@pori.fi"
    :orgAuthz  {:609-R #{:authorityAdmin}}
    :firstName "Pertteli"
    :lastName  "Porilainen"
    :language  "fi"
    :phone     "0501233210"
    :street    "V\u00E4h\u00E4uusikatu 1 a 1"
    :zip       "28100"
    :city      "Pori"
    :private   {:password "$2a$10$H3D35GclLgHRUIUfQtEQZe5FlrVR2iYoV8Babw4C8D8ANLvuulEmu"
                :apikey   "asHn33JQbGBvhABZfjxP1jnHcvfWM4ZUzRDO0Enp"}}

   ;; Hakijat

   ;; Hakija: Mikko's neighbour - teppo@example.com / teppo69
   {:lastName             "Nieminen"
    :firstName            "Teppo"
    :enabled              true
    :language             "fi"
    :username             "teppo@example.com"
    :private              {:password "$2a$10$KKBZSYTFTEFlRrQPa.PYPe9wz4q1sRvjgEUCG7gt8YBXoYwCihIgG"
                           :apikey   "502cb9e58426c613c8b85abb"}
    :phone                "0505503171"
    :email                "teppo@example.com"
    :personId             "210281-0002"
    :personIdSource       "identification-service"
    :role                 "applicant"
    :id                   "5073c0a1c2e6c470aef589a5"
    :allowDirectMarketing true
    :street               "Mutakatu 7"
    :zip                  "33560"
    :city                 "Tampere"}

   ;; sven@example.com / sven
   ;; Svens's language is omitted in order to test the implicit
   ;; language setting.
   {:role                 "applicant",
    :email                "sven@example.com",
    :personId             "070842-559U",
    :personIdSource       "identification-service"
    :private              {:apikey   "bfxLwCerNjNUpmJ2HqZbfxLwCerNjNUpmJ2HqZ",
                           :password "$2a$10$i8O320oYo76R6QoV6bh5MunFXeNy.FcS/xqTOKwLnxQOLBg721Ouy"},
    :phone                "0505504444",
    :city                 "Tampere",
    :username             "sven@example.com",
    :firstName            "Sven",
    :street               "Ericsgatan 8",
    :allowDirectMarketing true,
    :zip                  "33310",
    :id                   "578731b78ca8231afeca99e8",
    :lastName             "Svensson",
    :enabled              true}

   ;; Hakija: Mikko Intonen - mikko@example.com / mikko123
   {:id                   "777777777777777777000010"
    :username             "mikko@example.com"
    :enabled              true
    :language             "fi"
    :role                 "applicant"
    :personId             "210281-9988" ; = Nordea demo
    :personIdSource       "identification-service"
    :firstName            "Mikko"
    :lastName             "Intonen"
    :email                "mikko@example.com"
    :street               "Rambokuja 6"
    :zip                  "55550"
    :city                 "Sipoo"
    :phone                "0505503171"
    :architect            true
    :degree               "kirvesmies"
    :graduatingYear       "2000"
    :companyName          "Yritys Oy"
    :companyId            "1234567-1"
    :fise                 "f"
    :fiseKelpoisuus       "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"
    :allowDirectMarketing false
    :private              {:password "$2a$10$sVFCAX/MB7wDKA2aNp1greq7QlHCU/r3WykMX/JKMWmg7d1cp7HSq"
                           :apikey   "502cb9e58426c613c8b85abc"}}

   ;; Hakija: pena / pena
   {:id                   "777777777777777777000020"
    :username             "pena"
    :enabled              true
    :language             "fi"
    :role                 "applicant"
    :personId             "010203-040A"
    :personIdSource       "identification-service"
    :firstName            "Pena"
    :lastName             "Panaani"
    :email                "pena@example.com"
    :street               "Paapankuja 12"
    :zip                  "10203"
    :city                 "Piippola"
    :phone                "0102030405"
    :allowDirectMarketing true
    :private              {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
                           :apikey   "502cb9e58426c613c8b85abd"}}

   ;; Dummy Hakijat

   ;; Dummy hakija 1: dummy / pena
   {:id        "51112424c26b7342d92acf3c"
    :enabled   false
    :language  "fi"
    :username  "dummy"
    :firstName "Duff"
    :lastName  "Dummy"
    :email     "dummy@example.com"
    :private   {:apikey "602cb9e58426c613c8b85abe"} ; Dummy user has apikey, should not actually happen
    :role      "dummy"}
   ;; Dummy hakija 2: dummy2 / pena
   {:id        "51112424c26b7342d92acf3d"
    :enabled   false
    :language  "fi"
    :username  "dummy2"
    :firstName "Duff2"
    :lastName  "Dummy2"
    :email     "dummy2@example.com"
    :private   {}
    :role      "dummy"}
   ;; Dummy hakija 3: dummy3 / pena
   {:id        "51112424c26b7342d92acf3e"
    :enabled   false
    :language  "fi"
    :username  "dummy3"
    :firstName ""
    :lastName  ""
    :email     "dummy3@example.com"
    :private   {}
    :role      "dummy"}

   ;; Solita company admin
   ;; kaino@solita.fi  / kaino123
   {:id        "kainosolita"
    :enabled   true
    :language  "fi"
    :username  "kaino@solita.fi" ;
    :firstName "Kaino"
    :lastName  "Solita"
    :email     "kaino@solita.fi"
    :street    "Sensorintie 7"
    :zip       "12345"
    :city      "Forssa"
    :private   {:password "$2a$10$QjKZTnJy77sxiWaBKR0jQezFf1LSpKfg/sljmsSq4YIq05HRZI.l."
                :apikey   "502cb9e58426c613c8b85abe"}
    :role      "applicant"
    :architect true
    :company   {:id "solita" :role "admin" :submit true}}

   ;; Esimerkki company admin
   ;; erkki@example.com / esimerkki
   {:id             "erkkiesimerkki"
    :enabled        true
    :language       "fi"
    :username       "erkki@example.com" ;
    :firstName      "Erkki"
    :lastName       "Esimerkki"
    :personId       "010203-041B"
    :personIdSource "identification-service"
    :phone          "556677"
    :email          "erkki@example.com"
    :street         "Merkintie 88"
    :zip            "12345"
    :city           "Humppila"
    :private        {:password "$2a$10$XzjXRA80jV.O3v35cOmtc.Teqqk.1.d8rBd3P52UCfa2C/oiVeeVG"
                     :apikey   "502cb9e58426c613c8b85eba"}
    :role           "applicant"
    :architect      true
    :company        {:id "esimerkki" :role "admin" :submit true}}

   ;; Viranomainen: Priscilla Panaani from Pori - used in ad_login-itests
   {:id                   "7777777777777777770000112"
    :username             "priscilla"
    :enabled              true
    :language             "fi"
    :role                 "authority"
    :personId             "131052-308T"
    :orgAuthz             {:609-R #{:authority :approver}}
    :personIdSource       "identification-service"
    :firstName            "Priscilla"
    :lastName             "Panaani"
    :private              {:apikey "8o1QTOrNpkxp6oU2yGE4"}
    :email                "priscilla.panaani@pori.fi"
    :street               "Keksikari 1 A 13"
    :zip                  "28100"
    :city                 "Pori"
    :phone                "040-1234567"
    :allowDirectMarketing true}

   ;; Docstore
   {:id        "docstore"
    :username  "docstore"
    :email     "docstore@lupapiste.fi"
    :firstName "Docstore"
    :lastName  "API-user"
    :enabled   true
    :language  "fi"
    :role      "docstore-api"
    :private   {:password "$2a$10$LqhU/xPaLEsiPYkIJlT3UuBkzZ0wJyLr.0NBcOAlaP4/DW7AHbeGy"} ; basicauth
    :oauth     {:client-id     "docstore"
                :client-secret "docstore"
                :scopes        ["read" "pay"]
                :display-name  {:fi "Lupapiste kauppa"
                                :sv "Lupapiste butik"
                                :en "Lupapiste store"}
                :callback-url  "http://localhost:8014"}}

   ;; Virkapääte
   {:id        "docdepartmental"
    :username  "docdepartmental"
    :email     "docdeparmental@example.com"
    :firstName "Docdeparmental"
    :lastName  "API-user"
    :enabled   true
    :language  "fi"
    :role      "docstore-api"
    :private   {:password "$2a$10$LqhU/xPaLEsiPYkIJlT3UuBkzZ0wJyLr.0NBcOAlaP4/DW7AHbeGy"} ; basicauth
    :oauth     {:client-id     "docdepartmental"
                :client-secret "docdepartmental"
                :scopes        ["read"]
                :display-name  {:fi "Lupapiste Virkapääte"
                                :sv "Lupapiste Kontorsterminal"
                                :en "Lupapiste Department Terminal"}
                :callback-url  "http://localhost:8014"}}

   ;; OAuth test client
   {:email     "oauth-test-client@example.com"
    :enabled   true
    :firstName "OAuth"
    :id        "oauth-test-client"
    :language  "fi"
    :lastName  "Test"
    :oauth     {:callback-url  "http://localhost:8000/dev/oauth-test"
                :client-id     "oauth-test"
                :client-secret "oauth-test-secret"
                :display-name  {:en "OAuth Test (English)"
                                :fi "OAuth Test (suomi)"
                                :sv "OAuth Test (svenska)"}
                :registration? true
                :token-minutes 100
                :scopes        ["read" "pay"]}
    :role      "rest-api"
    :username  "oauth-test"}

   ;; Onkalo
   {:id        "onkalo"
    :username  "onkalo"
    :email     "onkalo@lupapiste.fi"
    :firstName "Onkalo"
    :lastName  "API-user"
    :enabled   true
    :language  "fi"
    :role      "onkalo-api"
    :private   {:password "$2a$10$LqhU/xPaLEsiPYkIJlT3UuBkzZ0wJyLr.0NBcOAlaP4/DW7AHbeGy"}} ; basicauth

   {:id        "tiedonohjausjarjestelma"
    :username  "tiedonohjausjarjestelma"
    :email     "tiedonohjausjarjestelma@lupapiste.fi"
    :firstName "TOJ"
    :lastName  "API-user"
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {:apikey "tojTOJtojTOJtojTOJtoj"}}

   ;; Solita admin:  financial / admin
   {:id        "financial"
    :email     "financial@ara.fi"
    :enabled   true
    :role      "financialAuthority"
    :language  "fi"
    :firstName "ARA-k\u00e4sittelij\u00e4"
    :lastName  ""
    :phone     ""
    :username  "financial"
    :private   {:password "$2a$10$WHPur/hjvaOTlm41VFjtjuPI5hBoIMm8Y1p2vL4KqRi7QUvHMS1Ie"
                :apikey   "5087ba34c2e667024fbd5999"}}
   ])

(def- ya-default-attachments-for-operations {:ya-kayttolupa-tapahtumat                                          [[:muut :muu]]
                                             :ya-kayttolupa-harrastustoiminnan-jarjestaminen                    [[:muut :muu]]
                                             :ya-kayttolupa-metsastys                                           [[:muut :muu]]
                                             :ya-kayttolupa-vesistoluvat                                        [[:muut :muu]]
                                             :ya-kayttolupa-terassit                                            [[:muut :muu]]
                                             :ya-kayttolupa-kioskit                                             [[:muut :muu]]
                                             :ya-kayttolupa-muu-kayttolupa                                      [[:muut :muu]]
                                             :ya-kayttolupa-nostotyot                                           [[:muut :muu]]
                                             :ya-kayttolupa-vaihtolavat                                         [[:muut :muu]]
                                             :ya-kayttolupa-kattolumien-pudotustyot                             [[:muut :muu]]
                                             :ya-katulupa-muu-liikennealuetyo                                   [[:muut :muu]]
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
                                             :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen            [[:muut :muu]]
                                             :ya-sijoituslupa-ilmajohtojen-sijoittaminen                        [[:muut :muu]]
                                             :ya-sijoituslupa-muuntamoiden-sijoittaminen                        [[:muut :muu]]
                                             :ya-sijoituslupa-jatekatoksien-sijoittaminen                       [[:muut :muu]]
                                             :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen        [[:muut :muu]]
                                             :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen          [[:muut :muu]]
                                             :ya-sijoituslupa-muu-sijoituslupa                                  [[:muut :muu]]
                                             :ya-jatkoaika                                                      [[:muut :muu]]})

(def- default-keys-for-organizations {:app-required-fields-filling-obligatory false
                                      :validate-verdict-given-date true
                                      :kopiolaitos-email nil
                                      :kopiolaitos-orderer-address nil
                                      :kopiolaitos-orderer-email nil
                                      :kopiolaitos-orderer-phone nil
                                      :calendars-enabled false
                                      :use-attachment-links-integration false
                                      :inspection-summaries-enabled false
                                      :docstore-info org/default-docstore-info})

(defn- verdict-templates [user]
  {:templates [{:id "5d88e6bf5df546ee8a459488"
                :name {:_value "Päätöspohja" :_user user :_modified 1569307999724}
                :category "ya"
                :modified 1569313397703
                :deleted {:_value false :_user user :_modified 1569253055269}
                :draft {:appeal {:_value "Hakemuksen oheen tuleva muutoksenhakuohje."
                                 :_user user
                                 :_modified 1569253269756}
                        :verdict-dates {:_value ["anto"] :_user user :_modified 1569253167081}
                        :bulletinOpDescription {:_value "Toimenpideteksti julkipanoon."
                                                :_user user
                                                :_modified 1569253185079}
                        :handler-titles {:5d88e8705df546ee8a45948e {:text-fi "Tehtävänimike"
                                                                    :text-sv "Tehtävänimike svenska"
                                                                    :text-en "Tehtävänimike english"
                                                                    :included {:_value true
                                                                               :_user user
                                                                               :_modified 1569496739833}
                                                                    :selected {:_value true
                                                                               :_user user
                                                                               :_modified 1569496741173}}}
                        :giver {:_value "viranhaltija" :_user user :_modified 1569253137014}
                        :plans {:5d88e8525df546ee8a45948d {:text-fi "Suunnitelma suomeksi"
                                                           :text-sv "Suunnitelma på svenska"
                                                           :text-en "Suunnitelma in english"
                                                           :included {:_value true
                                                                      :_user user
                                                                      :_modified 1569418188434}
                                                           :selected {:_value true
                                                                      :_user user
                                                                      :_modified 1569418189826}}}
                        :verdict-code {:_value "hyvaksytty" :_user user :_modified 1569253544503}
                        :conditions {:5d88e7675df546ee8a45948a {:condition {:_value "Lupaehto tai -määräys."
                                                                            :_user user
                                                                            :_modified 1569253234757}}}
                        :language {:_value "fi" :_user user :_modified 1569253130915}
                        :reviews {:5d88e8115df546ee8a45948b {:text-fi "Aloituskatselmus suomeksi"
                                                             :text-sv "Aloituskatselmus  på svenska"
                                                             :text-en "Aloituskatselmus in english"
                                                             :included {:_value true
                                                                        :_user user
                                                                        :_modified 1569418160927}
                                                             :selected {:_value true
                                                                        :_user user
                                                                        :_modified 1569418175457}}
                                  :5d88e8315df546ee8a45948c {:text-fi "Loppukatselmus suomeksi"
                                                             :text-sv "Loppukatselmus  på svenska"
                                                             :text-en "Loppukatselmus in english"
                                                             :included {:_value true
                                                                        :_user user
                                                                        :_modified 1569418160008}
                                                             :selected {:_value true
                                                                        :_user user
                                                                        :_modified 1569418174804}}
                                  :5d8caecde416566b64a4eeea {:text-fi "Muu valvontakäynti suomeksi"
                                                             :text-sv "Muu valvontakäynti på svenska"
                                                             :text-en "Muu valvontakäynti in english"
                                                             :included {:_value true
                                                                        :_user user
                                                                        :_modified 1569500913554}
                                                             :selected {:_value true
                                                                        :_user user
                                                                        :_modified 1569500914558}}}
                        :paatosteksti {:_value "Päätöksen teksti." :_user user :_modified 1569253157731}
                        :review-info {:_value "Lisäohje katselmuksille." :_user user :_modified 1569253190912}
                        :upload {:_value true :_user user :_modified 1569253206088}}
                :published {:published 1569313420265
                            :data {:appeal "Hakemuksen oheen tuleva muutoksenhakuohje."
                                   :verdict-dates ["anto"]
                                   :bulletinOpDescription "Toimenpideteksti julkipanoon."
                                   :giver "viranhaltija"
                                   :verdict-code "hyvaksytty"
                                   :conditions [{:condition "Lupaehto tai -määräys."}]
                                   :language "fi"
                                   :paatosteksti "Päätöksen teksti."
                                   :review-info "Lisäohje katselmuksille."
                                   :upload true}
                            :inclusions ["inform-others"
                                         "appeal"
                                         "verdict-dates"
                                         "handler-titles-link"
                                         "bulletinOpDescription"
                                         "handler-titles"
                                         "giver"
                                         "plans"
                                         "proposaltext"
                                         "verdict-code"
                                         "reviews-link"
                                         "conditions"
                                         "verdict-code-link"
                                         "language"
                                         "plans-link"
                                         "reviews"
                                         "paatosteksti"
                                         "review-info"
                                         "upload"
                                         "statements"
                                         "add-condition"]
                            :settings {:verdict-code ["evatty"
                                                      "myonnetty-aloitusoikeudella"
                                                      "myonnetty"
                                                      "hyvaksytty"
                                                      "annettu-lausunto"]
                                       :organization-name "Vantaa YA"
                                       :date-deltas {:julkipano {:delta 1 :unit "days"}
                                                     :anto {:delta 2 :unit "days"}
                                                     :muutoksenhaku {:delta 3 :unit "days"}
                                                     :lainvoimainen {:delta 4 :unit "days"}
                                                     :aloitettava {:delta 5 :unit "years"}
                                                     :voimassa {:delta 0 :unit "days"}}
                                       :plans [{:fi "Suunnitelma suomeksi"
                                                :sv "Suunnitelma på svenska"
                                                :en "Suunnitelma in english"
                                                :selected true}]
                                       :reviews [{:fi "Aloituskatselmus suomeksi"
                                                  :sv "Aloituskatselmus  på svenska"
                                                  :en "Aloituskatselmus in english"
                                                  :type "aloituskatselmus"
                                                  :selected true}
                                                 {:fi "Loppukatselmus suomeksi"
                                                  :sv "Loppukatselmus  på svenska"
                                                  :en "Loppukatselmus in english"
                                                  :type "loppukatselmus"
                                                  :selected true}
                                                 {:fi "Muu valvontakäynti suomeksi"
                                                  :sv "Muu valvontakäynti på svenska"
                                                  :en "Muu valvontakäynti in english"
                                                  :type "valvonta"
                                                  :selected true}]
                                       :handler-titles [{:fi "Tehtävänimike"
                                                         :sv "Tehtävänimike svenska"
                                                         :en "Tehtävänimike english"
                                                         :selected true}]}}}
               ;; Sopimus
               {:id "5d95d5e71bca46013988dff4"
                :draft {:language {:_value "fi" :_user user :_modified 1570100715797}
                        :contract-text {:_value "Tämä on sopimus."
                                        :_user user
                                        :_modified 1570100725807}
                        :conditions {:5d95d5f51bca46013988dff5 {:condition {:_value "Sopimusehto joka on täytettävä."
                                                                            :_user user
                                                                            :_modified 1570100809301}}}}
                :name {:_value "Sopimuspohja" :_user user :_modified 1570100711708}
                :category "contract"
                :modified 1570100809301
                :deleted {:_value false :_user user :_modified 1570100711708}
                :published {:published 1570100809375
                            :data {:language "fi"
                                   :contract-text "Tämä on sopimus."
                                   :conditions [{:condition "Sopimusehto, joka on täytettävä."}]}
                            :inclusions ["contract-text"
                                         "language"
                                         "conditions"
                                         "add-condition"
                                         "attachments"]
                            :settings {:organization-name "Vantaa YA"
                                       :date-deltas {:julkipano {:delta 0 :unit "days"}
                                                     :anto {:delta 0 :unit "days"}
                                                     :muutoksenhaku {:delta 0 :unit "days"}
                                                     :lainvoimainen {:delta 0 :unit "days"}
                                                     :aloitettava {:delta 0 :unit "days"}
                                                     :voimassa {:delta 0 :unit "days"}}
                                       :plans []
                                       :reviews []
                                       :handler-titles []}}}]
   :settings {:ya {:modified 1569253518476
                   :draft {:julkipano {:_value "1" :_user user :_modified 1569253297995}
                           :boardname {:_value "Lautakunnan/jaoston nimi" :_user user :_modified 1569253393752}
                           :handler-titles {:5d88e8705df546ee8a45948e {:fi {:_value "Tehtävänimike"
                                                                            :_user user
                                                                            :_modified 1569253497717}
                                                                       :sv {:_value "Tehtävänimike svenska"
                                                                            :_user user
                                                                            :_modified 1569253506565}
                                                                       :en {:_value "Tehtävänimike english"
                                                                            :_user user
                                                                            :_modified 1569253518476}}}
                           :muutoksenhaku {:_value "3" :_user user :_modified 1569253299554}
                           :anto {:_value "2" :_user user :_modified 1569253298700}
                           :plans {:5d88e8525df546ee8a45948d {:fi {:_value "Suunnitelma suomeksi"
                                                                   :_user user
                                                                   :_modified 1569253470789}
                                                              :sv {:_value "Suunnitelma på svenska"
                                                                   :_user user
                                                                   :_modified 1569253478404}
                                                              :en {:_value "Suunnitelma in english"
                                                                   :_user user
                                                                   :_modified 1569253488037}}}
                           :aloitettava {:_value "5" :_user user :_modified 1569253302547}
                           :organization-name {:_value "Vantaa YA" :_user user :_modified 1569253291477}
                           :verdict-code {:_value ["evatty"
                                                   "myonnetty-aloitusoikeudella"
                                                   "myonnetty"
                                                   "hyvaksytty"
                                                   "annettu-lausunto"]
                                          :_user user
                                          :_modified 1569253370259}
                           :reviews {:5d88e8115df546ee8a45948b {:fi {:_value "Aloituskatselmus suomeksi"
                                                                     :_user user
                                                                     :_modified 1569253454177}
                                                                :sv {:_value "Aloituskatselmus  på svenska"
                                                                     :_user user
                                                                     :_modified 1569253455548}
                                                                :en {:_value "Aloituskatselmus in english"
                                                                     :_user user
                                                                     :_modified 1569253458446}
                                                                :type {:_value "aloituskatselmus"
                                                                       :_user user
                                                                       :_modified 1569253423206}}
                                     :5d88e8315df546ee8a45948c {:fi {:_value "Loppukatselmus suomeksi"
                                                                     :_user user
                                                                     :_modified 1569253448597}
                                                                :sv {:_value "Loppukatselmus  på svenska"
                                                                     :_user user
                                                                     :_modified 1569253446911}
                                                                :en {:_value "Loppukatselmus in english"
                                                                     :_user user
                                                                     :_modified 1569253445607}
                                                                :type {:_value "loppukatselmus"
                                                                       :_user user
                                                                       :_modified 1569253438101}}
                                     :5d8caecde416566b64a4eeea {:fi {:_value "Muu valvontakäynti suomeksi"
                                                                     :_user user
                                                                     :_modified 1569500894138}
                                                                :sv {:_value "Muu valvontakäynti på svenska"
                                                                     :_user user
                                                                     :_modified 1569500899829}
                                                                :en {:_value "Muu valvontakäynti in english"
                                                                     :_user user
                                                                     :_modified 1569500904564}
                                                                :type {:_value "valvonta"
                                                                       :_user user
                                                                       :_modified 1569500880789}}}
                           :lainvoimainen {:_value "4" :_user user :_modified 1569253300558}
                           :lautakunta-muutoksenhaku {:_value "30" :_user user :_modified 1569253376456}}}
              :contract {:draft {:organization-name {:_value "Vantaa YA" :_user user :_modified 1570100760736}}
                         :modified 1570100760736}}})

(defn- names [names-map]
  (i18n/with-default-localization names-map (:fi names-map)))

(defn- link [link-names link-url]
  {:name (names link-names)
   :url (i18n/with-default-localization {:fi link-url} link-url)})

(def organizations (map
                    (partial merge default-keys-for-organizations)
                    [;; Jarvenpaa R
                     {:id "186-R"
                      :name (names {:fi "J\u00E4rvenp\u00E4\u00E4n rakennusvalvonta"
                                    :sv "J\u00E4rvenp\u00E4\u00E4n rakennusvalvonta"})
                      :scope [{:municipality "186"
                               :permitType "R"
                               :inforequest-enabled true
                               :new-application-enabled true}]
                      :links [(link {:fi "J\u00E4rvenp\u00E4\u00E4" :sv "Tr\u00E4skenda"}
                                    "http://www.jarvenpaa.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://www.jarvenpaa.fi/sivu/index.tmpl?sivu_id=182" )]
                      :operations-attachments {:kerrostalo-rivitalo [[:paapiirustus :asemapiirros]
                                                                     [:paapiirustus :pohjapiirustus]
                                                                     [:hakija :valtakirja]
                                                                     [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]]}
                      :krysp {:R {:url local-krysp :version "2.1.3" :ftpUser "dev_jarvenpaa"}}
                      :handler-roles [{:id "abba11111111111111111186"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :assignments-enabled true
                      :inspection-summaries-enabled true
                      :permanent-archive-enabled true
                      :digitizer-tools-enabled true
                      :permanent-archive-in-use-since 1451613600000
                      :earliest-allowed-archiving-date 0
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Sipoo R
                     {:id "753-R"
                      :name (names {:fi "Sipoon rakennusvalvonta"
                                    :sv "Sipoon rakennusvalvonta"})
                      :scope [{:municipality "753"
                               :permitType "R"
                               :inforequest-enabled true
                               :new-application-enabled true
                               :bulletins {:enabled true
                                           :url "http://localhost:8000/dev/julkipano"
                                           :notification-email "sonja.sibbo@sipoo.fi"
                                           :descriptions-from-backend-system false}
                                        ; NB! Enabling Pate WILL BREAK (robot) tests
                               :pate {:enabled false}}
                              {:municipality "753" :permitType "P" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "YM" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "YI" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "YL" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "MAL" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "VVVL" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "KT" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "753" :permitType "MM" :inforequest-enabled true :new-application-enabled true}]
                      :links [(link {:fi "Sipoo", :sv "Sibbo"}
                                    "http://sipoo.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta")]
                      :operations-attachments {:kerrostalo-rivitalo [[:paapiirustus :asemapiirros]
                                                                     [:paapiirustus :pohjapiirustus]
                                                                     [:hakija :valtakirja]
                                                                     [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]]
                                               :vapaa-ajan-asuinrakennus [[:paapiirustus :pohjapiirustus]
                                                                          [:hakija :ote_kauppa_ja_yhdistysrekisterista]
                                                                          [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]
                                                                          [:suunnitelmat :valaistussuunnitelma]]
                                               :poikkeamis [[:paapiirustus :asemapiirros]]
                                               :meluilmoitus [[:kartat :kartta-melun-ja-tarinan-leviamisesta]
                                                              [:muut :muu]]
                                               :yl-uusi-toiminta [[:muut :muu]]
                                               :maa-aineslupa [[:muut :muu]]
                                               "vvvl-vesijohdosta" [[:muut :muu]]}
                      :krysp {:R {:url local-krysp, :ftpUser "dev_sipoo", :version "2.2.2"}
                              :P {:url local-krysp :ftpUser "dev_poik_sipoo" :version "2.1.2"}
                              :YI {:url local-krysp :ftpUser "dev_ymp_sipoo" :version "2.2.1"}
                              :YL {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "2.2.1"}
                              :MAL {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "2.2.1"}
                              :VVVL {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "2.2.1"}
                              :KT {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "1.0.2"}
                              :MM {:url local-krysp, :ftpUser "dev_ymp_sipoo", :version "1.0.1"}}
                      :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                      :handler-roles [{:id "abba1111111111111111acdc"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}
                                      {:id "abba1111111111111112acdc"
                                       :name {:fi "KVV-K\u00e4sittelij\u00e4"
                                              :sv "KVV-Handl\u00e4ggare"
                                              :en "KVV-Handler"}}]
                      :automatic-assignment-filters [{:id "dead1111111111111111beef"
                                                      :rank 0
                                                      :criteria {:attachment-types ["ennakkoluvat_ja_lausunnot.elyn_tai_kunnan_poikkeamapaatos"
                                                                                    "ennakkoluvat_ja_lausunnot.naapurin_suostumus"]}
                                                      :name "ELY ja naapuri"
                                                      :modified 1580647526159}
                                                     {:id "dead1111111111111112beef"
                                                      :rank 0
                                                      :criteria  {:attachment-types ["paapiirustus.aitapiirustus"
                                                                   "paapiirustus.asemapiirros"]}
                                                      :name "Aita ja asema"
                                                      :modified 1580647552458
                                                      :target {:handler-role-id "abba1111111111111111acdc"}}]
                      :kopiolaitos-email "sipoo@example.com"
                      :kopiolaitos-orderer-address "Testikatu 2, 12345 Sipoo"
                      :kopiolaitos-orderer-email "tilaaja@example.com"
                      :kopiolaitos-orderer-phone "0501231234"
                      :calendars-enabled true
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R" "P" "YI" "YL" "YM" "MAL" "VVVL" "KT" "MM"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :permanent-archive-in-use-since 1451613600000
                      :earliest-allowed-archiving-date 0
                      :tags [{:id "111111111111111111111111" :label "yl\u00E4maa"} {:id "222222222222222222222222" :label "ullakko"}]
                      :assignments-enabled true
                      :areas {:type "FeatureCollection"
                              :features [{:id "sipoo_keskusta",
                                          :properties {:nimi "Keskusta", :id 3},
                                          :geometry
                                          {:coordinates
                                           [[[[402644.2941 6693912.6002]
                                              [401799.0131 6696356.5649]
                                              [406135.6722 6695272.4001]
                                              [406245.9263 6693673.7164]
                                              [404059.221 6693545.0867]
                                              [404059.221 6693545.0867]
                                              [402644.2941 6693912.6002]]]],
                                           :type "MultiPolygon"},
                                          :type "Feature"}]}
                      :areas-wgs84 {:type "FeatureCollection"
                                    :features [{:id "sipoo_keskusta"
                                                :properties { :id 3, :nimi "Keskusta"}
                                                :geometry
                                                {:coordinates
                                                 [[[[25.2346903951971, 60.3699135383472]
                                                    [25.218174131471, 60.3916425519997]
                                                    [25.2973279375514, 60.382941834376]
                                                    [25.3000746506076, 60.3686195326001]
                                                    [25.2605085377224, 60.3669529618652]
                                                    [25.2605085377224, 60.3669529618652]
                                                    [25.2346903951971, 60.3699135383472]]]]
                                                 :type "MultiPolygon"}
                                                :type "Feature"}]}
                      ;; Admin admin enforces HTTPS requirement for
                      ;; 3D map server backend. Thus, the development
                      ;; backend must be set in the minimal
                      :3d-map {:enabled true
                               :server {:url local-3d-map
                                        :username "3dmap"
                                        :password "Xma8r8GMkPvibmg9PoclOA=="
                                        :crypto-iv "vOztjZQ8O2Szk8uI13844g=="}
                               }
                      :stamps [{:id "123456789012345678901234"
                                :name "Oletusleima"
                                :position {:x 10 :y 200}
                                :background 0
                                :page :first
                                :qrCode true
                                :rows [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type "current-date"}]
                                       [{:type :backend-id} {:type :section}]
                                       [{:type :organization}]]}
                               {:id "112233445566778899001122"
                                :name "KV-leima"
                                :position {:x 10 :y 10}
                                :background 0
                                :page :all
                                :qrCode true
                                :rows [[{:type :custom-text :text "Custom text"} {:type "current-date"}]
                                       [{:type :extra-text :text "Extra text"}]
                                       [{:type :application-id} {:type :backend-id}]
                                       [{:type :user}]
                                       [{:type :organization}]]}]
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true
                      :buildings-extinct-enabled true
                      :docstore-info (assoc org/default-docstore-info
                                            :docStoreInUse true
                                            :docTerminalInUse true
                                            :documentPrice 314)

                      :suomifi-messages {:verdict {:enabled true
                                                   :message "Tämä on päätös rakennuslupa-asiaasi liittyen"
                                                   :attachments []}
                                         :authority-id "Viranomaistunnus-uus"
                                         :service-id "Palvelutunnus6"}

                      :local-bulletins-page-settings
                      {:texts
                       (names
                        {:fi {:heading1 "Sipoo",
                              :heading2 "Sipoo R julkipanot",
                              :caption ["Tervetuloa"
                                        "Viranhaltijan p\u00e4\u00e4t\u00f6ksi\u00e4 tehd\u00e4\u00e4n p\u00e4ivitt\u00e4in. Oikaisuvaatimusaika on 14 p\u00e4iv\u00e4\u00e4 p\u00e4\u00e4t\u00f6sten tiedoksiannosta."
                                        "Listat ovat n\u00e4ht\u00e4vill\u00e4 kunnan virastossa"]},
                         :sv {:heading1 "Sibbo",
                              :heading2 "Sibbo julkipano",
                              :caption ["Bygglovssektionens beslut om bygglov meddelas efter den offentliga delgivningen d\u00e5 de anses ha kommit till vederb\u00f6randes k\u00e4nnedom. Besv\u00e4rstiden \u00e4r 30 dagar."
                                        "Tj\u00e4nsteinnehavarbeslut fattas dagligen. Besv\u00e4rstiden \u00e4r 14 dagar fr\u00e5n det att besluten kungjorts."]}})}}

                     ;; Sipoo YA
                     ;; Keeping :inforequest-enabled true and :new-application-enabled true to allow krysp itests pass.
                     {:id "753-YA"
                      :name (names {:fi "Sipoon yleisten alueiden rakentaminen"
                                    :sv "Sipoon yleisten alueiden rakentaminen"})
                      :scope [{:municipality "753"
                               :permitType "YA"
                               :inforequest-enabled true
                               :new-application-enabled true}
                              {:municipality "753"
                               :permitType "A"
                               :inforequest-enabled true
                               :new-application-enabled true}]
                      :links [(link {:fi "Sipoo", :sv "Sibbo"}
                                    "http://sipoo.fi")]
                      :krysp {:YA {:url local-krysp :ftpUser "dev_ya_sipoo" :version "2.2.1"}}
                      :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                      :handler-roles [{:id "abba1111111111111111b753"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations [:ya-katulupa-vesi-ja-viemarityot
                                            :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen
                                            :ya-kayttolupa-mainostus-ja-viitoitus
                                            :ya-kayttolupa-terassit
                                            :ya-kayttolupa-vaihtolavat
                                            :ya-kayttolupa-nostotyot
                                            :promootio
                                            :lyhytaikainen-maanvuokraus]
                      :operations-attachments ya-default-attachments-for-operations
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :tags [{:id "735001000000000000000000" :label "YA kadut"} {:id "735002000000000000000000" :label "YA ojat"}]
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Kuopio YA
                     {:id "297-YA"
                      :name (names {:fi "Kuopio yleisten alueiden kaytto"
                                    :sv "Kuopio yleisten alueiden kaytto"})
                      :scope [{:municipality "297"
                               :permitType "YA"
                               :inforequest-enabled true
                               :new-application-enabled true
                               :caseManagement {:ftpUser "dev_ah_kuopio" :enabled true :version "1.1"}
                               :pate {:enabled true}}]
                      :links [(link {:fi "Kuopio", :sv "Kuopio"}
                                    "http://www.kuopio.fi")]
                      :krysp {:YA {:url local-krysp :version "2.1.2" :ftpUser "dev_ya_kuopio"}}
                      :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                      :handler-roles [{:id "abba1111111111111111b753"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :operations-attachments ya-default-attachments-for-operations
                      :selected-operations (map first (filter (fn [[_ v]] (#{"YA"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}


                     ;; Tampere R
                     {:id "837-R"
                      :name (names {:fi "Tampereen rakennusvalvonta"
                                    :sv "Tampereen rakennusvalvonta"})
                      :scope [{:municipality "837"
                               :permitType "R"
                               :inforequest-enabled true
                               :new-application-enabled true
                               :pate {:enabled true}}]
                      :links [(link {:fi "Tampere" :sv "Tammerfors"}
                                    "http://tampere.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://www.tampere.fi/asuminenjarakentaminen/rakennusvalvonta.html")
                              (link {:fi "Lomakkeet" :sv "Lomakkeet"}
                                    "http://www.tampere.fi/asuminenjarakentaminen/rakennusvalvonta/lomakkeet.html")]
                      :operations-attachments {:kerrostalo-rivitalo [[:paapiirustus :asemapiirros]
                                                                     [:paapiirustus :pohjapiirustus]
                                                                     [:hakija :valtakirja]
                                                                     [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]]
                                               :vapaa-ajan-asuinrakennus [[:paapiirustus :pohjapiirustus]
                                                                          [:hakija :ote_kauppa_ja_yhdistysrekisterista]
                                                                          [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]
                                                                          [:suunnitelmat :valaistussuunnitelma]]
                                               :varasto-tms [[:paapiirustus :asemapiirros]
                                                             [:paapiirustus :julkisivupiirustus]
                                                             [:paapiirustus :leikkauspiirustus]
                                                             [:ennakkoluvat_ja_lausunnot :naapurin_kuuleminen]
                                                             [:paapiirustus :pohjapiirustus]
                                                             [:rakennuspaikan_hallinta :todistus_hallintaoikeudesta]]
                                               :asuinrakennus [[:paapiirustus :asemapiirros]
                                                               [:selvitykset :energiataloudellinen_selvitys]
                                                               [:selvitykset :energiatodistus]
                                                               [:paapiirustus :julkisivupiirustus]
                                                               [:paapiirustus :leikkauspiirustus]
                                                               [:ennakkoluvat_ja_lausunnot :naapurin_kuuleminen]
                                                               [:pelastusviranomaiselle_esitettavat_suunnitelmat :paloturvallisuussuunnitelma]
                                                               [:suunnitelmat :piha_tai_istutussuunnitelma]
                                                               [:paapiirustus :pohjapiirustus]
                                                               [:rakennuspaikan_hallinta :todistus_hallintaoikeudesta]]
                                               :auto-katos [[:paapiirustus :asemapiirros]
                                                            [:ennakkoluvat_ja_lausunnot :naapurin_kuuleminen]
                                                            [:rakennuspaikan_hallinta :todistus_hallintaoikeudesta]]}
                      :krysp {:R {:url local-krysp :version "2.2.2"
                                  :http (merge
                                         {:enabled true
                                          :auth-type "basic"
                                          :partner "matti"
                                          :path {:application "hakemus-path"
                                                 :review  "katselmus-path"
                                                 :verdict "verdict-path"}
                                          :url local-krysp-receiver
                                          :headers [{:key "x-vault" :value "vaultti"}]}
                                         (org/encode-credentials "kuntagml" "kryspi"))}}
                      :handler-roles [{:id "abba1111111111111111a837"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true
                      :state-change-endpoint {:url always-ok}
                      :state-change-msg-enabled true}

                     ;; Tampere YA
                     {:id "837-YA",
                      :name (names {:fi "Tampere yleiset alueet"
                                    :sv "Tammerfors yleiset alueet"})
                      :scope [{:municipality "837"
                               :permitType "YA"
                               :inforequest-enabled true
                               :new-application-enabled true
                               :invoicing-enabled true}]
                      :statementGivers [{:id "521f1e82e4b0d14f5a87f179"
                                         :text "Paloviranomainen"
                                         :email "jussi.viranomainen@tampere.fi"
                                         :name "Jussi Viranomainen"}]
                      :krysp {:YA {:url local-krysp :ftpUser "dev_ya_tampere" :version "2.1.2"}}
                      :handler-roles [{:id "abba1111111111111111b837"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :operations-attachments ya-default-attachments-for-operations
                      :selected-operations (map first (filter (fn [[_ v]] (#{"YA"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Porvoo R
                     {:id "638-R"
                      :name (names {:fi "Porvoon rakennusvalvonta"
                                    :sv "Porvoon rakennusvalvonta"})
                      :scope [{:municipality "638" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "638" :permitType "YI" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "638" :permitType "YL" :inforequest-enabled true :new-application-enabled true}]
                      :links [(link {:fi "Porvoo", :sv "Borg\u00e5"}
                                    "http://www.porvoo.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://www.porvoo.fi/fi/haku/palveluhakemisto/?a=viewitem&itemid=1030")]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R" "YI" "YL"} (name (:permit-type v)))) operations/operations))
                      :allowedAutologinIPs ["0:0:0:0:0:0:0:1" "127.0.0.1" "172.17.144.220" "109.204.231.126"]
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :krysp {:R {:url local-krysp, :ftpUser "dev_porvoo", :version "2.1.6"}}
                      :handler-roles [{:id "abba11111111111111111638"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Oulu R
                     {:id "564-R"
                      :name (names {:fi "Oulun rakennusvalvonta"
                                    :sv "Oulun rakennusvalvonta"})
                      :scope [{:municipality "564" :permitType "R" :inforequest-enabled true :new-application-enabled true}]
                      :links [(link {:fi "Oulu", :sv "Ule\u00E5borg"}
                                    "http://www.ouka.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Fastigheter"}
                                    "http://oulu.ouka.fi/rakennusvalvonta/")]
                      :handler-roles [{:id "abba1111111111111111a564"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Oulu YA
                     {:id "564-YA"
                      :name (names {:fi "Oulun yleiset alueet"
                                    :sv "Oulun yleiset alueet"})
                      :scope [{:municipality "564" :permitType "YA" :inforequest-enabled true :new-application-enabled true}]
                      :statementGivers [{:id "521f1e82e4b0d14f5a87f179"
                                         :text "Paloviranomainen"
                                         :email "oulu.viranomainen@oulu.fi"
                                         :name "Oulu Viranomainen"}]
                      :handler-roles [{:id "abba1111111111111111b564"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :links [(link {:fi "Oulu", :sv "Ule\u00E5borg"}
                                    "http://www.ouka.fi")]
                      :operations-attachments ya-default-attachments-for-operations
                      :selected-operations (map first (filter (fn [[_ v]] (#{"YA"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled true
                      :digitizer-tools-enabled true
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Naantali R
                     {:id "529-R"
                      :name (names {:fi "Naantalin rakennusvalvonta"
                                    :sv "Naantalin rakennusvalvonta"})
                      :scope [{:municipality "529" :permitType "R" :inforequest-enabled true :new-application-enabled true}]
                      :handler-roles [{:id "abba11111111111111111529"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Peruspalvelukuntayhtyma Selanne R
                     {:id "069-R"
                      :name (names {:fi "Peruspalvelukuntayhtym\u00E4 Sel\u00E4nne"
                                    :sv "Peruspalvelukuntayhtym\u00E4 Sel\u00E4nne"})
                      :scope [{:municipality "069" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "317" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "626" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                              {:municipality "691" :permitType "R" :inforequest-enabled true :new-application-enabled true}]
                      :handler-roles [{:id "abba11111111111111111069"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}


                     ;; Loppi R
                     ;; Organisation for municipality "Loppi" that uses the "neuvontapyynnon-avaus" system.
                     ;; Nice address for testing "Ojatie 1, Loppi"
                     {:id "433-R"
                      :name (names {:fi "Loppi rakennusvalvonta"
                                    :sv "Loppi rakennusvalvonta"})
                      :scope [{:municipality "433"
                               :permitType "R"
                               :new-application-enabled false
                               :inforequest-enabled true
                               :open-inforequest true
                               :open-inforequest-email "erajorma@example.com"}]
                      :links []
                      :handler-roles [{:id "abba11111111111111111433"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Turku R with a public WFS server
                     {:id "853-R"
                      :name (names {:fi "Turku rakennusvalvonta"
                                    :sv "\u00c5bo byggnadstillsyn"})
                      :scope [{:municipality "853"
                               :permitType "R"
                               :new-application-enabled false
                               :inforequest-enabled true
                               :open-inforequest true
                               :open-inforequest-email "turku@example.com"}]
                      :links []
                      :krysp {:osoitteet {:url "http://opaskartta.turku.fi/TeklaOGCWeb/WFS.ashx"}}
                      :handler-roles [{:id "abba11111111111111111853"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled false
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Kuopio R, has case management (asianhallinta) enabled
                     {:id "297-R"
                      :name (names {:fi "Kuopio rakennusvalvonta"
                                    :sv "Kuopio byggnadstillsyn"})
                      :scope [{:open-inforequest-email nil
                               :open-inforequest false
                               :new-application-enabled true
                               :inforequest-enabled true
                               :municipality "297"
                               :permitType "R"}
                              {:open-inforequest-email nil
                               :open-inforequest false
                               :new-application-enabled true
                               :inforequest-enabled true
                               :municipality "297"
                               :permitType "P"
                               :caseManagement {:ftpUser "dev_ah_kuopio" :enabled true :version "1.1"}}]
                      :links [(link {:fi "Rakentamisen s\u00E4hk\u00F6iset lupapalvelut Kuopiossa"
                                     :sv "Rakentamisen s\u00E4hk\u00F6iset lupapalvelut Kuopiossa"}
                                    "http://www.kuopio.fi/web/tontit-ja-rakentaminen/rakentamisen-sahkoiset-lupapalvelut")
                              (link {:fi "Kuopion alueellinen rakennusvalvonta"
                                     :sv "Kuopion alueellinen rakennusvalvonta"}
                                    "http://www.kuopio.fi/web/tontit-ja-rakentaminen/rakennusvalvonta")
                              (link {:fi "Kuopion kaupunki"
                                     :sv "Kuopion kaupunki"}
                                    "http://www.kuopio.fi")]
                      :krysp {:R
                              {:ftpUser "dev_kuopio"
                               :url local-krysp
                               :version "2.1.5"}
                              :P
                              {:ftpUser "dev_kuopio"
                               :url local-krysp
                               :version "2.1.5"}}
                      :handler-roles [{:id "abba1111111111111111b297"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :operations-attachments {:poikkeamis [[:paapiirustus :asemapiirros]]}
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R" "P"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported false}

                     ;; Pori R, has ad-login settings and only-use-inspection-from-backend set to true
                     {:id "609-R"
                      :name (names {:fi "Pori - Rakennusvalvonta"
                                    :sv "Bj\u00f6rneborg - Byggnadstilsyn"})
                      :scope [{:open-inforequest-email nil
                               :open-inforequest false
                               :new-application-enabled true
                               :inforequest-enabled true
                               :municipality "609"
                               :permitType "R"}
                              {:open-inforequest-email nil
                               :open-inforequest false
                               :new-application-enabled true
                               :inforequest-enabled true
                               :municipality "609"
                               :permitType "P"}]
                      :handler-roles [{:id "abba1111111111111111b297"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :operations-attachments {:poikkeamis [[:paapiirustus :asemapiirros]]}
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R" "P"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported false
                      :only-use-inspection-from-backend true
                      :ad-login {:enabled true
                                 :idp-cert "MIICpzCCAhACCQDuFX0Db5iljDANBgkqhkiG9w0BAQsFADCBlzELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEjAQBgNVBAcMCVBhbG8gQWx0bzEQMA4GA1UECgwHU2FtbGluZzEPMA0GA1UECwwGU2FsaW5nMRQwEgYDVQQDDAtjYXByaXphLmNvbTEmMCQGCSqGSIb3DQEJARYXZW5naW5lZXJpbmdAY2Fwcml6YS5jb20wHhcNMTgwNTE1MTgxMTEwWhcNMjgwNTEyMTgxMTEwWjCBlzELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEjAQBgNVBAcMCVBhbG8gQWx0bzEQMA4GA1UECgwHU2FtbGluZzEPMA0GA1UECwwGU2FsaW5nMRQwEgYDVQQDDAtjYXByaXphLmNvbTEmMCQGCSqGSIb3DQEJARYXZW5naW5lZXJpbmdAY2Fwcml6YS5jb20wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAJEBNDJKH5nXr0hZKcSNIY1l4HeYLPBEKJLXyAnoFTdgGrvi40YyIx9lHh0LbDVWCgxJp21BmKll0CkgmeKidvGlr3FUwtETro44L+SgmjiJNbftvFxhNkgA26O2GDQuBoQwgSiagVadWXwJKkodH8tx4ojBPYK1pBO8fHf3wOnxAgMBAAEwDQYJKoZIhvcNAQELBQADgYEACIylhvh6T758hcZjAQJiV7rMRg+Omb68iJI4L9f0cyBcJENR+1LQNgUGyFDMm9Wm9o81CuIKBnfpEE2Jfcs76YVWRJy5xJ11GFKJJ5T0NEB7txbUQPoJOeNoE736lF5vYw6YKp8fJqPW0L2PLWe9qTn8hxpdnjo3k6r5gXyl8tk="
                                 :idp-uri "http://localhost:7000"
                                 :role-mapping {:reader "GG_Lupapiste_RAVA_read"}
                                 :trusted-domains ["pori.fi"]}}

                     ;; Helsinki R
                     {:id "091-R"
                      :name (names {:fi "Helsingin rakennusvalvontavirasto"
                                    :sv "Helsingfors byggnadstillsynsverket"})
                      :scope [{:municipality "091"
                               :permitType "R"
                               :new-application-enabled true
                               :inforequest-enabled true}]
                      :links [(link {:fi "Helsinki" :sv "Helsingfors"}
                                    "http://www.hel.fi")
                              (link {:fi "Rakennusvalvontavirasto", :sv "Byggnadstillsynsverket"}
                                    "http://www.hel.fi/www/rakvv/fi")]
                      :krysp {:R {:url local-krysp :version "2.2.0" :ftpUser "dev_helsinki"}}
                      :handler-roles [{:id "abba1111111111111111a091"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :operations-attachments {:kerrostalo-rivitalo [[:paapiirustus :asemapiirros]
                                                                     [:paapiirustus :pohjapiirustus]]}
                      :assignments-enabled true
                      :permanent-archive-enabled true
                      :digitizer-tools-enabled true
                      :permanent-archive-in-use-since 1451613600000
                      :earliest-allowed-archiving-date 0
                      :use-attachment-links-integration true
                      :operations-tos-functions {:masto-tms "10 03 00 01"}
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Helsinki YA
                     {:id "091-YA"
                      :name (names {:fi "Helsingin yleiset alueet"
                                    :sv "Helsingfors yleiset alueet"})
                      :scope [{:municipality "091"
                               :permitType "YA"
                               :new-application-enabled true
                               :inforequest-enabled true}
                              {:municipality "091"
                               :permitType "A"
                               :inforequest-enabled true
                               :new-application-enabled true}]
                      :links [(link {:fi "Helsinki" :sv "Helsingfors"}
                                    "http://www.hel.fi")]
                      :krysp {:R {:url local-krysp :version "2.2.0" :ftpUser "dev_helsinki"}}
                      :handler-roles [{:id "abba1111111111111111a091"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :operations-attachments ya-default-attachments-for-operations
                      :selected-operations (map first (filter (fn [[_ v]] (#{"YA" "A"} (name (:permit-type v)))) operations/operations))
                      :assignments-enabled true
                      :permanent-archive-enabled true
                      :digitizer-tools-enabled true
                      :permanent-archive-in-use-since 1451613600000
                      :earliest-allowed-archiving-date 0
                      :use-attachment-links-integration true
                      :operations-tos-functions {:masto-tms "10 03 00 01"}
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Vantaa rakennusvalvonta
                     {:id "092-R"
                      :name (names {:fi "Vantaa - Rakennusvalvonta"
                                    :sv "Vanda - Byggnadstillsyn"})
                      :scope [{:municipality "092"
                               :permitType "R"
                               :new-application-enabled true
                               :inforequest-enabled true}]
                      :links [(link {:fi "Vantaa" :sv "Vanda"}
                                    "http://www.vantaa.fi")]
                      :krysp {:R {:url local-krysp :version "2.2.0" :ftpUser "dev_vantaa"}}
                      :handler-roles [{:id "abba1111111111111111a092"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :operations-attachments {:kerrostalo-rivitalo [[:paapiirustus :asemapiirros]
                                                                     [:paapiirustus :pohjapiirustus]]}
                      :assignments-enabled true
                      :permanent-archive-enabled true
                      :digitizer-tools-enabled true
                      :permanent-archive-in-use-since 1451613600000
                      :earliest-allowed-archiving-date 0
                      :use-attachment-links-integration true
                      :operations-tos-functions {:masto-tms "10 03 00 01"}
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Vantaa YA
                     {:id "092-YA"
                      :name (names {:fi "Vantaa - Yleiset alueet"
                                    :sv "Vanda - Yleiset alueet"})
                      :scope [{:municipality "092"
                               :permitType "YA"
                               :new-application-enabled true
                               :inforequest-enabled true
                               :pate {:enabled true}}]
                      :links [(link {:fi "Vantaa" :sv "Vanda"}
                                    "http://www.vantaa.fi")]
                      :krysp {:YA {:url local-krysp :version "2.2.4" :ftpUser "dev_ya_vantaa" ;; TODO: Ota :ftpUser pois, kun siirretään kamat Mattiin ftp folderin sijaan.
                                   :backend-system "matti"
                                   :http (merge
                                           {:enabled true
                                            :auth-type "basic"
                                            :partner "matti"
                                            :path {:application "hakemus-path"
                                                   :review  "katselmus-path"
                                                   :verdict "verdict-path"}
                                            :url local-krysp-receiver
                                            :headers [{:key "x-vault" :value "vaultti"}]}
                                           (org/encode-credentials "kuntagml" "kryspi"))}}
                      :handler-roles [{:id "abba1111111111111111a092"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (->> operations/operations
                                                (filter (fn [[_ v]] (#{"YA"} (name (:permit-type v)))))
                                                (map first))
                      :verdict-templates (verdict-templates "vantaa-ya")
                      :operations-attachments ya-default-attachments-for-operations
                      :assignments-enabled true
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled true
                      :permanent-archive-in-use-since 1451613600000
                      :earliest-allowed-archiving-date 0
                      :use-attachment-links-integration true
                      :operations-tos-functions {:masto-tms "10 03 00 01"}
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;;
                     ;; Ymparisto organisaatiot
                     ;;
                     {:id "564-YMP"
                      :name (names {:fi "Oulun ymparisto"
                                    :sv "Oulun ymparisto"})
                      :scope [{:municipality "564" :permitType "YM" :inforequest-enabled true :new-application-enabled true :caseManagement {:ftpUser "dev_ah_oulu" :enabled false :version "1.1"}}
                              {:municipality "564" :permitType "YI" :inforequest-enabled true :new-application-enabled true :caseManagement {:ftpUser "dev_ah_oulu" :enabled false :version "1.1"}}
                              {:municipality "564" :permitType "YL" :inforequest-enabled true :new-application-enabled true :caseManagement {:ftpUser "dev_ah_oulu" :enabled false :version "1.1"}}
                              {:municipality "564" :permitType "MAL" :inforequest-enabled true :new-application-enabled true :caseManagement {:ftpUser "dev_ah_oulu" :enabled false :version "1.1"}}
                              {:municipality "564" :permitType "VVVL" :inforequest-enabled true :new-application-enabled true :caseManagement {:ftpUser "dev_ah_oulu" :enabled false :version "1.1"}}]
                      :links [(link {:fi "Oulu", :sv "Ule\u00E5borg"}
                                    "http://www.ouka.fi")]
                      :statementGivers [{:id "516560d6c2e6f603beccc144"
                                         :text "Paloviranomainen",
                                         :email "olli.uleaborg@ouka.fi",
                                         :name "Olli Ule\u00E5borg"}]
                      :handler-roles [{:id "abba1111111111111111c564"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"YI" "YL" "YM" "MAL" "VVVL"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true
                      :ely-uspa-enabled true}

                     ;;
                     ;; Testeissa kaytettavia organisaatioita
                     ;;

                     ;; Sipoo R - New applications disabled
                     {:id "997-R-TESTI-1"
                      :name (names {:fi "Sipoon rakennusvalvonta"
                                    :sv "Sipoon rakennusvalvonta"})
                      :scope [{:municipality "980" :permitType "R" :inforequest-enabled true :new-application-enabled false}]
                      :links [(link {:fi "Sipoo", :sv "Sibbo"}
                                    "http://sipoo.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta")]
                      :krysp {:R {:url local-krysp, :version "2.1.2"
                                  :ftpUser "dev_sipoo"}}
                      :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                      :handler-roles [{:id "abba11111111111111111997"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Sipoo R - Inforequests disabled
                     {:id "998-R-TESTI-2"
                      :name (names {:fi "Sipoon rakennusvalvonta"
                                    :sv "Sipoon rakennusvalvonta"})
                      :scope [{:municipality "981" :permitType "R" :inforequest-enabled false :new-application-enabled true}]
                      :links [(link {:fi "Sipoo", :sv "Sibbo"}
                                    "http://sipoo.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta")]
                      :krysp {:R {:url local-krysp, :version "2.1.2"}}
                      :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                      :handler-roles [{:id "abba11111111111111111998"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}

                     ;; Sipoo R - Both new applications and inforequests disabled
                     {:id "999-R-TESTI-3"
                      :name (names {:fi "Sipoon rakennusvalvonta"
                                    :sv "Sipoon rakennusvalvonta"})
                      :scope [{:municipality "989" :permitType "R" :inforequest-enabled false :new-application-enabled false}]
                      :links [(link {:fi "Sipoo", :sv "Sibbo"}
                                    "http://sipoo.fi")
                              (link {:fi "Rakennusvalvonta", :sv "Rakennusvalvonta"}
                                    "http://sipoo.fi/fi/palvelut/asuminen_ja_rakentaminen/rakennusvalvonta")]
                      :krysp {:R {:url local-krysp :version "2.1.2"}}
                      :statementGivers [{:id "516560d6c2e6f603beb85147"
                                         :text "Paloviranomainen",
                                         :email "sonja.sibbo@sipoo.fi",
                                         :name "Sonja Sibbo"}]
                      :handler-roles [{:id "abba11111111111111111999"
                                       :name {:fi "K\u00e4sittelij\u00e4"
                                              :sv "Handl\u00e4ggare"
                                              :en "Handler"}
                                       :general true}]
                      :selected-operations (map first (filter (fn [[_ v]] (#{"R"} (name (:permit-type v)))) operations/operations))
                      :permanent-archive-enabled false
                      :digitizer-tools-enabled false
                      :automatic-review-fetch-enabled true
                      :automatic-ok-for-attachments-enabled true
                      :multiple-operations-supported true}]))

(def companies [{:id "solita"
                 :accountType "account5"
                 :billingType "monthly"
                 :customAccountLimit nil
                 :created 1412959886600
                 :name "Solita Oy"
                 :address1 "\u00c5kerlundinkatu 11"
                 :zip "33100"
                 :po "Tampere"
                 :country "FINLAND"
                 :y "1060155-5"
                 :netbill "solitabilling"
                 :ovt "003710601555"
                 :pop "BAWCFI22"
                 :reference "Lupis"
                 :process-id "CkaekKfpEymHUG0nn5z4MLxwNm34zIdpAXHqQ3FM"
                 :tags [{:id "7a67a67a67a67a67a67a67a6"
                         :label "Projekti1"}]}
                {:id "esimerkki"
                 :accountType "account5"
                 :billingType "monthly"
                 :customAccountLimit nil
                 :created 1493200035783
                 :name "Esimerkki Oy"
                 :address1 "Merkintie 88"
                 :zip "12345"
                 :po "Humppila"
                 :country "Suomi"
                 :y "7208863-8"
                 :netbill "samplebilling"
                 :ovt "003710601555"
                 :pop "BAWCFI22"
                 :reference "Esim"
                 :process-id "CkaekKfpEymHUG0nn5z4MLxwNm34zIdpAXHqQMF3"}])

(defn dummy-onkalo-log-entry
  "Generates a dummy log entry with timestamp between start-ts and end-ts"
  [start-ts end-ts & [logged?]]
  (let [ts (+ start-ts (long (rand (- end-ts start-ts))))]
    (merge {:organization (rand-nth ["753-R" "091-R" "092-R" "837-R" "297-R"])
            :timestamp ts}
           (when logged?
             {:logged ts}))))

(def allu-data
  [{:promotion {:drawings [{:name           "Asema-aukio (1)",
                            :allu-section   "A"
                            :id             1,
                            :source         "promotion",
                            :geometry       "POLYGON((385681.076 6672257.494,385687.361 6672257.604,385687.436 6672254.541,385681.164 6672254.368,385681.076 6672257.494))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.93957812 60.17116784]
                                                            [24.93969125 60.17117059]
                                                            [24.93969432 60.17114312]
                                                            [24.93958145 60.17113982]
                                                            [24.93957812 60.17116784]]]}}
                           {:name           "Asema-aukio (2)",
                            :allu-section   "B"
                            :id             2,
                            :source         "promotion",
                            :geometry       "POLYGON((385717.531 6672222.775,385717.557 6672228.84,385720.618 6672228.895,385720.672 6672222.82,385717.531 6672222.775))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.94025417 60.17086651]
                                                            [24.94025123 60.17092094]
                                                            [24.94030633 60.17092228]
                                                            [24.94031072 60.17086779]
                                                            [24.94025417 60.17086651]]]}}

                           {:name           "Kaivopuisto",
                            :id             3,
                            :source         "promotion",
                            :geometry       "POLYGON((386592.444 6670633.049,386600.829 6670630.96,386613.236 6670630.808,386626.562 6670604.722,386619.147 6670594.85,386612.184 6670585.505,386608.097 6670581.441,386602.496 6670576.104,386599.634 6670574.123,386598.811 6670573.738,386596.3 6670573.849,386593.225 6670574.903,386564.465 6670594.618,386563.945 6670596.72,386564.245 6670599.984,386566.533 6670606.015,386569.504 6670611.283,386571.929 6670619.239,386568.754 6670637.002,386569.14 6670639.382,386570.785 6670640.929,386573.85 6670642.983,386577.289 6670644.117,386580.817 6670643.59,386586.863 6670636.641,386592.444 6670633.049))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.95689721 60.15684539]
                                                            [24.95704932 60.15682897]
                                                            [24.95727276 60.15683105]
                                                            [24.95752718 60.15660067]
                                                            [24.95739919 60.15651003]
                                                            [24.95727905 60.15642424]
                                                            [24.95720775 60.15638663]
                                                            [24.95710989 60.15633719]
                                                            [24.95705947 60.15631862]
                                                            [24.95704486 60.15631493]
                                                            [24.95699961 60.15631524]
                                                            [24.95694366 60.15632384]
                                                            [24.95641494 60.15649276]
                                                            [24.9564044 60.15651148]
                                                            [24.95640799 60.15654085]
                                                            [24.95644581 60.1565956]
                                                            [24.95649637 60.1566437]
                                                            [24.95653559 60.15671577]
                                                            [24.95646853 60.15687428]
                                                            [24.95647417 60.15689574]
                                                            [24.95650291 60.15691008]
                                                            [24.95655695 60.15692936]
                                                            [24.95661822 60.1569405]
                                                            [24.95668202 60.15693675]
                                                            [24.95679473 60.15687607]
                                                            [24.95689721 60.15684539]]]}}

                           {:name           "Kolmensepänaukio",
                            :id             4,
                            :source         "promotion",
                            :geometry       "POLYGON((385734.877 6671988.076,385740.332 6671990.963,385744.935 6671983.177,385739.812 6671980.331,385734.877 6671988.076))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.94069842 60.16876535]
                                                            [24.94079503 60.16879279]
                                                            [24.94088231 60.16872421]
                                                            [24.94079164 60.16869723]
                                                            [24.94069842 60.16876535]]]}}
                           {:name           "Mauno Koiviston aukio",
                            :id             5,
                            :source         "promotion",
                            :geometry       "POLYGON((385431.134 6672153.621,385424.624 6672163.464,385425.287 6672172.53,385445.594 6672186.812,385449.643 6672182.012,385454.195 6672179.498,385455.87 6672177.498,385448.398 6672164.752,385441.78 6672160.499,385431.134 6672153.621))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.93513522 60.17016569]
                                                            [24.93501244 60.17025219]
                                                            [24.93501927 60.17033372]
                                                            [24.93537694 60.17046758]
                                                            [24.93545256 60.17042565]
                                                            [24.93553596 60.17040437]
                                                            [24.93556725 60.17038689]
                                                            [24.93543986 60.17027042]
                                                            [24.93532308 60.17023039]
                                                            [24.93513522 60.17016569]]]}}
                           {:name           "Narinkka (6)",
                            :allu-section   "A"
                            :id             6,
                            :source         "promotion",
                            :geometry       "POLYGON((385399.675 6672122.201,385422.295 6672136.613,385436.674 6672113.76,385413.876 6672099.413,385399.675 6672122.201))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.93458638 60.16987492]
                                                            [24.93498563 60.17001059]
                                                            [24.93525744 60.16980957]
                                                            [24.93485495 60.16967443]
                                                            [24.93458638 60.16987492]]]}}
                           {:name           "Narinkka (7)",
                            :allu-section   "B"
                            :id             7,
                            :source         "promotion",
                            :geometry       "POLYGON((385413.876 6672099.413,385391.139 6672084.757,385376.599 6672107.848,385399.675 6672122.201,385413.876 6672099.413))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.93485496 60.16967443]
                                                            [24.93445374 60.16953654]
                                                            [24.93417889 60.16973966]
                                                            [24.93458638 60.16987493]
                                                            [24.93485496 60.16967443]]]}}
                           {:name           "Narinkka (8)",
                            :allu-section   "C"
                            :id             8,
                            :source         "promotion",
                            :geometry       "POLYGON((385428.404 6672076.633,385413.876 6672099.412,385436.673 6672113.76,385451.086 6672090.992,385428.404 6672076.633))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.93512942 60.1694741]
                                                            [24.93485496 60.16967442]
                                                            [24.93525743 60.16980956]
                                                            [24.93552981 60.16960931]
                                                            [24.93512942 60.1694741]]]}}
                           {:name           "Narinkka (9)",
                            :allu-section   "D"
                            :id             9,
                            :source         "promotion",
                            :geometry       "POLYGON((385413.876 6672099.412,385428.404 6672076.633,385405.45 6672062.169,385391.138 6672084.757,385413.876 6672099.412))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.93485496 60.16967442]
                                                            [24.93512943 60.1694741]
                                                            [24.9347242 60.16933786]
                                                            [24.93445373 60.16953653]
                                                            [24.93485496 60.16967442]]]}}

                           {:name           "Senaatintori",
                            :id             10,
                            :source         "promotion",
                            :geometry       "POLYGON((386324.291 6672075.585,386433.22 6672077.643,386434.339 6672027.433,386325.105 6672024.804,386324.291 6672075.585))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[24.95126406 60.16971515]
                                                            [24.95322462 60.16976393]
                                                            [24.95327281 60.1693137]
                                                            [24.95130709 60.16925971]
                                                            [24.95126406 60.16971515]]]}}
                           {:name           "Säiliö 468",
                            :id             11,
                            :source         "promotion",
                            :geometry       "POLYGON((389685.457 6671412.276,389684.654 6671416.839,389685.106 6671421.346,389686.647 6671425.236,389689.146 6671429.008,389692.745 6671432.259,389697.686 6671434.569,389703.362 6671434.945,389707.941 6671434.291,389712.706 6671431.963,389716.457 6671428.497,389718.8 6671424.62,389720.201 6671420.178,389720.369 6671415.983,389719.488 6671411.14,389716.927 6671406.17,389713.312 6671402.671,389708.753 6671400.314,389704.203 6671399.218,389698.871 6671399.673,389694.109 6671401.224,389689.952 6671404.545,389686.82 6671408.324,389685.457 6671412.276))",
                            :geometry-wgs84 {:type        "Polygon",
                                             :coordinates [[[25.01215756 60.16468501]
                                                            [25.01214062 60.16472574]
                                                            [25.01214632 60.16476631]
                                                            [25.01217197 60.16480163]
                                                            [25.0122149 60.16483616]
                                                            [25.01227795 60.1648663]
                                                            [25.01236567 60.16488837]
                                                            [25.01246768 60.16489328]
                                                            [25.0125505 60.16488864]
                                                            [25.01263755 60.16486904]
                                                            [25.01270699 60.16483895]
                                                            [25.01275128 60.16480479]
                                                            [25.01277891 60.16476531]
                                                            [25.01278421 60.16472771]
                                                            [25.01277097 60.16468401]
                                                            [25.01272755 60.16463872]
                                                            [25.01266434 60.16460634]
                                                            [25.01258354 60.16458396]
                                                            [25.0125022 60.1645729]
                                                            [25.01240594 60.16457554]
                                                            [25.01231935 60.16458817]
                                                            [25.01224268 60.16461685]
                                                            [25.01218425 60.16464992]
                                                            [25.01215756 60.16468501]]]}}
                           ]}
    :dog-training-event {:drawings
                         [{:name "Heikinlaakson kenttä",
                           :id 100,
                           :source "dog-training-event",
                           :geometry
                           "POLYGON((392961.67 6682446.613,392952.162 6682432.257,392938.78 6682413.103,392926.485 6682417.385,392917.798 6682423.25,392915.19 6682425.381,392929.209 6682445.259,392940.024 6682461.691,392961.67 6682446.613))",
                           :geometry-wgs84
                           {:type "Polygon",
                            :coordinates
                                  [[[25.06532897 60.26457769]
                                    [25.06516484 60.26444635]
                                    [25.06493328 60.26427094]
                                    [25.06470893 60.26430613]
                                    [25.06454891 60.26435648]
                                    [25.06450069 60.26437492]
                                    [25.06474336 60.26455699]
                                    [25.06493 60.26470729]
                                    [25.06532897 60.26457769]]]}}
                          {:name "Kivikon kenttä",
                           :id 101,
                           :source "dog-training-event",
                           :geometry
                           "POLYGON((392292.384 6678996.062,392290.437 6678991.156,392287.371 6678988.831,392278.322 6678983.778,392268.038 6678978.96,392260.473 6678981.232,392252.905 6678984.178,392241.108 6678988.398,392229.467 6678992.516,392222.354 6678996.418,392220.416 6679001.967,392220.203 6679007.137,392222.817 6679011.287,392228.044 6679013.349,392234.431 6679015.943,392238.755 6679020.981,392244.782 6679026.149,392251.218 6679029.834,392258.091 6679026.72,392272.222 6679019.516,392284.378 6679013.397,392289.195 6679010.027,392292.313 6679002.395,392292.384 6678996.062))",
                           :geometry-wgs84
                           {:type "Polygon",
                            :coordinates
                                  [[[25.05507754 60.23343661]
                                    [25.05504501 60.23339207]
                                    [25.05499092 60.23337039]
                                    [25.05483033 60.23332266]
                                    [25.05464731 60.2332767]
                                    [25.05450958 60.23329508]
                                    [25.05437146 60.23331952]
                                    [25.05415634 60.23335427]
                                    [25.05394408 60.23338814]
                                    [25.05381364 60.23342128]
                                    [25.05377572 60.23347056]
                                    [25.05376913 60.23351689]
                                    [25.05381409 60.23355483]
                                    [25.0539073 60.23357471]
                                    [25.05402119 60.23359968]
                                    [25.05409654 60.23364604]
                                    [25.05420254 60.23369401]
                                    [25.05431672 60.23372878]
                                    [25.05444239 60.23370265]
                                    [25.05470124 60.23364175]
                                    [25.05492385 60.23359005]
                                    [25.05501257 60.23356108]
                                    [25.05507288 60.23349342]
                                    [25.05507754 60.23343661]]]}}
                          {:name "Koneen kenttä",
                           :id 102,
                           :source "dog-training-event",
                           :geometry
                           "POLYGON((382099.757 6675499.479,382085.42 6675508.478,382083.01 6675508.771,382076.247 6675513.475,382054.721 6675527.145,382053.622 6675529.893,382055.703 6675531.748,382068.437 6675553.894,382069.999 6675559.101,382070.733 6675560.277,382079.146 6675555.963,382085.442 6675551.569,382096.32 6675546.027,382108.396 6675542.7,382118.738 6675536.212,382121.214 6675534.392,382099.757 6675499.479))",
                           :geometry-wgs84
                           {:type "Polygon",
                            :coordinates
                                  [[[24.87320028 60.19923895]
                                    [24.87293665 60.19931555]
                                    [24.87289304 60.19931749]
                                    [24.87276839 60.19935774]
                                    [24.87237246 60.19947417]
                                    [24.87235105 60.19949851]
                                    [24.87238749 60.19951576]
                                    [24.87260415 60.19971815]
                                    [24.87262928 60.19976532]
                                    [24.87264182 60.19977608]
                                    [24.87279598 60.19973981]
                                    [24.87291201 60.19970221]
                                    [24.8731113 60.19965563]
                                    [24.8733309 60.19962927]
                                    [24.87352107 60.19957404]
                                    [24.87356677 60.19955842]
                                    [24.87320028 60.19923895]]]}}]}
    :id "fixed-locations"}
   {:id                "application-kinds",
    :short-term-rental ["bridge-banner" "benji" "promotion-or-sales" "urban-farming"
                        "keskuskatu-sales" "summer-theater" "dog-training-field"
                        "dog-training-event" "small-art-and-culture" "season-sale"
                        "circus" "art" "storage-area" "other"]}])

(deffixture "minimal" {}
  (mongo/clear!)
  (mongo/insert-batch :ssoKeys [{:_id "12342424c26b7342d92a4321" :ip "127.0.0.1" :key "ozckCE8EESo+wMKWklGevQ==" :crypto-iv "V0HaDa6lpWKj+W0uMKyHBw=="}
                                {:_id "12342424c26b7342d92a4322" :ip "172.17.144.220" :key "ozckCE8EESo+wMKWklGevQ==" :crypto-iv "V0HaDa6lpWKj+W0uMKyHBw=="}
                                {:_id "12342424c26b7342d92a9876" :ip "109.204.231.126" :key "ozckCE8EESo+wMKWklGevQ==" :crypto-iv "V0HaDa6lpWKj+W0uMKyHBw=="}])
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies companies)
  (mongo/insert-batch :organizations organizations)
  (mongo/insert-batch :archive-api-usage [(dummy-onkalo-log-entry 0 (now) true)
                                          (dummy-onkalo-log-entry 0 (now) true)
                                          (dummy-onkalo-log-entry 0 (now) true)])
  (mongo/insert-batch :allu-data allu-data))
