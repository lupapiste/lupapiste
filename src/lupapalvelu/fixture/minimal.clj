(ns lupapalvelu.fixture.minimal
  (:use lupapalvelu.fixture)
  (:require [lupapalvelu.mongo :as mongo]))

(def users
  [
   {:id "777777777777777777000099" ;; admin
    :email "admin@solita.fi"
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
    :role :authority
    :authority :Tampere
    :personId "kunta 122"
    :firstName "Veikko"
    :lastName "Viranomainen"
    :phone "03121991"
    :username "veikko"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuLF5AQqkSO5S1DJOgziMep.xJLYm3.xG"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "5051ba0caa2480f374dcfeff"}}
   {:id "777777777777777777000023" ;; Sonja Sibbo - Sipoon lupa-arkkitehti
    :email "sonja.sibbo@sipoo.fi"
    :role :authority
    :authority :Sipoo
    :personId "kunta123"
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :username "sonja"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :salt "$2a$10$s4OOPduvZeH5yQzsCFSKIu"
              :apikey "5056e6d3aa24a1c901e6b9dd"}}
   {:id "50ac77ecc2e6c2ea6e73f83e" ;; Simo Sippo
    :email "admin@sipoo.fi"
    :role :authority-admin
    :authority :Sipoo
    :firstName "Simo"
    :lastName "Suurvisiiri"
    :username "sipoo"
    :private {:salt "$2a$10$VFcksPILCd9ykyl.1FIhwO",
              :password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
              :apikey "50ac788ec2e6c2ea6e73f83f"}}
   {:id "505718b0aa24a1c901e6ba24" ;; Admin
    :firstName "Judge"
    :lastName "Dread"
    :role :admin
    :private {:apikey "505718b0aa24a1c901e6ba24"}}
   {:lastName "Nieminen" ;; Mikkos neighbour
    :firstName "Teppo"
    :postalCode "33200"
    :username "teppo@example.com"
    :private {:salt "$2a$10$KKBZSYTFTEFlRrQPa.PYPe"
              :password "$2a$10$KKBZSYTFTEFlRrQPa.PYPe9wz4q1sRvjgEUCG7gt8YBXoYwCihIgG"}
    :phone "0505503171"
    :email "teppo@example.com"
    :personId "210281-0001"
    :role "applicant"
    :id "5073c0a1c2e6c470aef589a5"
    :street "Mutakatu 7"
    :zip "33560"
    :city "Tampere"
     }
   {:id "777777777777777777000010" ;; Mikko Intonen
    :username "mikko@example.com"
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
              :apikey "602cb9e58426c613c8b85abc"}}
   ])

(deffixture "minimal" {}
  (mongo/clear!)
  (dorun (map #(mongo/insert mongo/users %) users)))
