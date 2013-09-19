(ns lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test)


(def documents [{:created 1379592634015,
                 :data
                 {:_selected {:modified 1379592879374, :value "yritys"},
                  :henkilo
                  {:henkilotiedot
                   {:hetu {:modified 1379592871249, :value "260880-023L"},
                    :etunimi {:modified 1379592634015, :value "Pena"},
                    :sukunimi {:modified 1379592634015, :value "Panaani"}},
                   :osoite
                   {:katu {:modified 1379592634015, :value "Paapankuja 12"},
                    :postinumero {:modified 1379592875469, :value "33580"},
                    :postitoimipaikannimi
                    {:modified 1379592634015, :value "Piippola"}},
                   :userId
                   {:modified 1379592634015, :value "777777777777777777000020"},
                   :yhteystiedot
                   {:email {:modified 1379592634015, :value "pena@example.com"},
                    :puhelin {:modified 1379592634015, :value "0102030405"}}},
                  :yritys
                  {:liikeJaYhteisoTunnus
                   {:modified 1379592855027, :value "1234567-1"},
                   :osoite
                   {:katu {:modified 1379592634015, :value "Paapankuja 12"},
                    :postinumero {:modified 1379592858416, :value "33580"},
                    :postitoimipaikannimi
                    {:modified 1379592634015, :value "Piippola"}},
                   :yhteyshenkilo
                   {:henkilotiedot
                    {:etunimi {:modified 1379592634015, :value "Pena"},
                     :sukunimi {:modified 1379592634015, :value "Panaani"}},
                    :yhteystiedot
                    {:email {:modified 1379592634015, :value "pena@example.com"},
                     :puhelin {:modified 1379592634015, :value "0102030405"}}},
                   :yritysnimi {:modified 1379592848441, :value "Yritys Oy Ab"}}},
                 :id "523ae9ba94a7542b3520e64b",
                 :schema-info
                 {:approvable true,
                  :subtype "hakija",
                  :name "hakija-ya",
                  :removable false,
                  :repeating false,
                  :version 1,
                  :type "party",
                  :order 3}}
                {:created 1379592634015,
                 :data
                 {:kaivuLuvanTunniste {:modified 1379592648571, :value "1234567890"},
                  :kayttotarkoitus
                  {:modified 1379592642729, :value "Hankkeen kuvaus."}},
                 :id "523ae9ba94a7542b3520e64a",
                 :schema-info
                 {:order 65,
                  :version 1,
                  :repeating false,
                  :name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa",
                  :op
                  {:id "523ae9ba94a7542b3520e649",
                   :name
                   "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen",
                   :created 1379592634015},
                  :removable false}}
                {:created 1379592634015,
                 :data
                 {:lisatietoja-sijoituskohteesta
                  {:modified 1379592841005, :value "Lisätietoja."},
                  :muu-sijoituksen-tarkoitus
                  {:modified 1379592733099, :value "Muu sijoituksen tarkoitus."},
                  :sijoituksen-tarkoitus {:modified 1379592651471, :value "other"}},
                 :id "523ae9ba94a7542b3520e64c",
                 :schema-info
                 {:name "sijoituslupa-sijoituksen-tarkoitus",
                  :removable false,
                  :repeating false,
                  :version 1,
                  :order 66}}])

(def neighbors {:523aeb5794a7542b3520e7e4
                {:neighbor
                 {:propertyId "75342300040104",
                  :owner
                  {:name "Esko Naapuri",
                   :email "esko.naapuri@sipoo.fi",
                   :address {:street "Osoite 1 a 1", :city "Sipoo", :zip "33580"}}},
                 :status [{:state "open", :created 1379593047958}]}})

(def sijoituslupa-application {:neighbors neighbors,
 :schema-version 1,
 :authority
 {:role "authority",
  :lastName "Sibbo",
  :firstName "Sonja",
  :username "sonja",
  :id "777777777777777777000023"},
 :auth
 [{:lastName "Panaani",
   :firstName "Pena",
   :username "pena",
   :type "owner",
   :role "owner",
   :id "777777777777777777000020"}
  {:id "777777777777777777000023",
   :username "sonja",
   :firstName "Sonja",
   :lastName "Sibbo",
   :role "writer"}],
 :submitted 1379592916811,
 :state "submitted",
 :location {:x 410168.0, :y 6690500.0},
 :attachments
 [{:id "523aeac694a7542b3520e699",
   :latestVersion
   {:fileId "523aeac694a7542b3520e696",
    :version {:major 1, :minor 0},
    :size 44755,
    :created 1379592902883,
    :filename "lupapiste-attachment-testi.pdf",
    :contentType "application/pdf",
    :user
    {:role "applicant",
     :lastName "Panaani",
     :firstName "Pena",
     :username "pena",
     :id "777777777777777777000020"},
    :stamped false,
    :accepted nil},
   :locked false,
   :modified 1379592902883,
   :op nil,
   :state "requires_authority_action",
   :target nil,
   :type {:type-group "yleiset-alueet", :type-id "suunnitelmakartta"},
   :versions
   [{:fileId "523aeac694a7542b3520e696",
     :version {:major 1, :minor 0},
     :size 44755,
     :created 1379592902883,
     :filename "lupapiste-attachment-testi.pdf",
     :contentType "application/pdf",
     :user
     {:role "applicant",
      :lastName "Panaani",
      :firstName "Pena",
      :username "pena",
      :id "777777777777777777000020"},
     :stamped false,
     :accepted nil}]}
  {:id "523aeb0494a7542b3520e716",
   :latestVersion
   {:fileId "523aeb0494a7542b3520e713",
    :version {:major 0, :minor
1},
    :size 52356,
    :created 1379592964729,
    :filename "lupapiste-attachment-testi.docx",
    :contentType
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    :user
    {:role "authority",
     :lastName "Sibbo",
     :firstName "Sonja",
     :username "sonja",
     :id "777777777777777777000023"},
    :stamped false,
    :accepted nil},
   :locked true,
   :modified 1379592964729,
   :op nil,
   :state "requires_authority_action",
   :target {:type "statement", :id "523aeaf294a7542b3520e704"},
   :type {:type-group "muut", :type-id "muu"},
   :versions
   [{:fileId "523aeb0494a7542b3520e713",
     :version {:major 0, :minor 1},
     :size 52356,
     :created 1379592964729,
     :filename "lupapiste-attachment-testi.docx",
     :contentType
     "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
     :user
     {:role "authority",
      :lastName "Sibbo",
      :firstName "Sonja",
      :username "sonja",
      :id "777777777777777777000023"},
     :stamped false,
     :accepted nil}]}],
 :statements
 [{:given 1379592969643,
   :id "523aeaf294a7542b3520e704",
   :person
   {:text "Paloviranomainen",
    :name "Sonja Sibbo",
    :email "sonja.sibbo@sipoo.fi",
    :id "516560d6c2e6f603beb85147"},
   :requested 1379592946063,
   :status "yes",
   :text "Lausunto."}],
 :organization "753-YA",
 :title "Hirvimäentie 112",
 :operations
 [{:id "523ae9ba94a7542b3520e649",
   :name
   "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen",
   :created 1379592634015}],
 :infoRequest false,
 :opened 1379592902883,
 :created 1379592634015,
 :_comments-seen-by {:777777777777777777000020 1379592907811},
 :propertyId "75342300010054",
 :documents documents,
 :_statements-seen-by {:777777777777777777000023 1379593051030},
 :_software_version "1.0.5",
 :modified 1379592969636,
 :allowedAttachmentTypes
 [["yleiset-alueet"
   ["aiemmin-hankittu-sijoituspaatos"
    "tilapainen-liikennejarjestelysuunnitelma"
    "tyyppiratkaisu"
    "tieto-kaivupaikkaan-liittyvista-johtotiedoista"
    "liitoslausunto"
    "asemapiirros"
    "rakennuspiirros"
    "suunnitelmakartta"]]
  ["muut" ["muu"]]],
 :comments
 [{:text "Liite.",
   :target
   {:type "attachment",
    :id "523aeac694a7542b3520e699",
    :version {:major 1, :minor 0},
    :filename "lupapiste-attachment-testi.pdf",
    :fileId "523aeac694a7542b3520e696"},
   :created 1379592902883,
   :to nil,
   :user
   {:role "applicant",
    :lastName "Panaani",
    :firstName "Pena",
    :username "pena",
    :id "777777777777777777000020"}}
  {:text "Lausuntoliite.",
   :target
   {:type "attachment",
    :id "523aeb0494a7542b3520e716",
    :version {:major 0, :minor 1},
    :filename "lupapiste-attachment-testi.docx",
    :fileId "523aeb0494a7542b3520e713"},
   :created 1379592964729,
   :to nil,
   :user
   {:role "authority",
    :lastName "Sibbo",
    :firstName "Sonja",
    :username "sonja",
    :id "777777777777777777000023"}}
  {:text "Hakemukselle lisätty lausunto.",
   :target {:type "statement", :id "523aeaf294a7542b3520e704"},
   :created 1379592969636,
   :to nil,
   :user
   {:role "authority",
    :lastName "Sibbo",
    :firstName "Sonja",
    :username "sonja",
    :id "777777777777777777000023"}}],
 :address "Hirvimäentie 112",
 :permitType "YA",
 :id
 "LP-753-2013-00001",
 :municipality "753"})