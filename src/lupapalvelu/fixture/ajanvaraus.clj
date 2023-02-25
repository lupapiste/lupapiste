(ns lupapalvelu.fixture.ajanvaraus
  (:require [lupapalvelu.fixture.core :refer :all]
            [sade.core :refer :all]
            [lupapalvelu.calendars-api]))

(def sonja "5056e6d3aa24a1c901e6b9d1")

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

   ;; Sipoo

   ;; Simo Suurvisiiri - Sipoon R paakayttaja:  sipoo / sipoo
   {:id "50ac77ecc2e6c2ea6e73f83e"
    :email "admin@sipoo.fi"
    :enabled true
    :role "authority"
    :orgAuthz {:753-R-TESTI #{:authorityAdmin}
               :753-R #{:authorityAdmin}}
    :firstName "Simo"
    :lastName "Suurvisiiri"
    :username "sipoo"
    :private {:password "$2a$10$VFcksPILCd9ykyl.1FIhwO/tEYby9SsqZL7GsIAdpJ1XGvAG2KskG"
              :apikey "50ac788ec2e6c2ea6e73f83f"}}

   ;; Sonja Sibbo - Sipoon lupa-arkkitehti:  sonja / sonja
   {:id "777777777777777777888823"
    :username "sonja"
    :role "authority"
    :enabled true
    :email "sonja.sibbo@sipoo.fi"
    :orgAuthz {:753-R-TESTI #{:authority :approver}
               :753-R #{:authority :approver}
               :998-R-TESTI-2 #{:authority :approver}}
    :firstName "Sonja"
    :lastName "Sibbo"
    :phone "03121991"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Sipoo"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :apikey sonja}
    :applicationFilters [{:id "foobar"
                          :title "Foobar"
                          :sort {:asc false
                                 :field "modified"}
                          :filter {:handlers []
                                   :tags []
                                   :operations []
                                   :organizations []
                                   :areas []}}
                         {:id "barfoo"
                          :title "Barfoo"
                          :sort {:asc false
                                 :field "modified"}
                          :filter {:handlers []
                                   :tags []
                                   :operations []
                                   :organizations []
                                   :areas []}}]}

   ;; Ronja Sibbo - Sipoon lupa-arkkitehti:  ronja / sonja
   {:id "777777777777777777000024"
    :username "ronja"
    :role "authority"
    :enabled true
    :language "fi"
    :email "ronja.sibbo@sipoo.fi"
    :orgAuthz {:753-R #{:authority}}
    :firstName "Ronja"
    :lastName "Sibbo"
    :phone "03121991"
    :street "Katuosoite 1 a 1"
    :zip "33456"
    :city "Sipoo"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuVKiwbKvNs90f80zc57FDiPnGjuMbuf2"
              :apikey "5056e6d3aa24a1c901e6b9dd"}}

   ;; Hakija: Mikko's neighbour - teppo@example.com / teppo69
   {:lastName "Nieminen"
    :firstName "Teppo"
    :enabled true
    :language "fi"
    :username "teppo@example.com"
    :private {:password "$2a$10$KKBZSYTFTEFlRrQPa.PYPe9wz4q1sRvjgEUCG7gt8YBXoYwCihIgG"
              :apikey "502cb9e58426c613c8b85abb"}
    :phone "0505503171"
    :email "teppo@example.com"
    :personId "210281-0002"
    :role "applicant"
    :id "5073c0a1c2e6c470aef589a5"
    :allowDirectMarketing true
    :street "Mutakatu 7"
    :zip "33560"
    :city "Tampere"}

   ;; Veikko Viranomainen - tamperelainen Lupa-arkkitehti:  veikko / veikko
   {:id "777777777777777777000016"
    :email "veikko.viranomainen@tampere.fi"
    :enabled true
    :role "authority"
    :orgAuthz {:837-R #{:authority}}
    :firstName "Veikko"
    :lastName "Viranomainen"
    :phone "03121991"
    :username "veikko"
    :private {:password "$2a$10$s4OOPduvZeH5yQzsCFSKIuLF5AQqkSO5S1DJOgziMep.xJLYm3.xG"
              :apikey "5051ba0caa2480f374dcfeff"}}

   ;; Hakija: pena / pena
   {:id "777777777777777777000020"
    :username "pena"
    :enabled true
    :language "fi"
    :role "applicant"
    :personId "010203-040A"
    :firstName "Pena"
    :lastName "Panaani"
    :email "pena@example.com"
    :street "Paapankuja 12"
    :zip "10203"
    :city "Piippola"
    :phone "0102030405"
    :allowDirectMarketing true
    :private {:password "$2a$10$hLCt8BvzrJScTOGQcXJ34ea5ovSfS5b/4X0OAmPbfcs/x3hAqEDxy"
              :apikey "502cb9e58426c613c8b85abd"}}


   ])

(def- default-keys-for-organizations {:app-required-fields-filling-obligatory false
                                      :validate-verdict-given-date true
                                      :kopiolaitos-email nil
                                      :kopiolaitos-orderer-address nil
                                      :kopiolaitos-orderer-email nil
                                      :kopiolaitos-orderer-phone nil
                                      :calendars-enabled false})

(def organizations (map
                     (partial merge default-keys-for-organizations)
                     [
                      {:id "753-R"
                       :name {:fi "Sipoon rakennusvalvonta"
                              :sv "Sipoon rakennusvalvonta"}
                       :scope [{:municipality "753" :permitType "R" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "P" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "YM" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "YI" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "YL" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "MAL" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "VVVL" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "KT" :inforequest-enabled true :new-application-enabled true}
                               {:municipality "753" :permitType "MM" :inforequest-enabled true :new-application-enabled true}]
                       :calendars-enabled true}
                      {:id "753-R-TESTI"
                       :name {:fi "Sipoon rakennusvalvonta TESTI"
                              :sv "Sipoon rakennusvalvonta TESTI"}
                       :calendars-enabled true}
                      {:id "837-R"
                       :name {:fi "Tampereen rakennusvalvonta"
                              :sv "Tampereen rakennusvalvonta"}
                       :calendars-enabled false}]))

#_(deffixture "ajanvaraus" {}
  (mongo/clear!)
  (mongo/insert-batch :ssoKeys [{:_id "12342424c26b7342d92a4321" :ip "127.0.0.1" :key "ozckCE8EESo+wMKWklGevQ==" :crypto-iv "V0HaDa6lpWKj+W0uMKyHBw=="}
                                {:_id "12342424c26b7342d92a9876" :ip "109.204.231.126" :key "ozckCE8EESo+wMKWklGevQ==" :crypto-iv "V0HaDa6lpWKj+W0uMKyHBw=="}])
  (mongo/insert-batch :users users)
  (mongo/insert-batch :organizations organizations)
  (lupapalvelu.calendars-api/clear-database))
