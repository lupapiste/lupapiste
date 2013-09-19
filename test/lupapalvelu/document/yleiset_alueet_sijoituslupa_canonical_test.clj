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

(def operation {:id "523ae9ba94a7542b3520e649",
                :name
                "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen",
                :created 1379592634015})

;(def neighbors {:523aeb5794a7542b3520e7e4
;                {:neighbor
;                 {:propertyId "75342300040104",
;                  :owner
;                  {:name "Esko Naapuri",
;                   :email "esko.naapuri@sipoo.fi",
;                   :address {:street "Osoite 1 a 1", :city "Sipoo", :zip "33580"}}},
;                 :status [{:state "open", :created 1379593047958}]}})

;(def allowedAttachmentTypes [["yleiset-alueet"
;                              ["aiemmin-hankittu-sijoituspaatos"
;                               "tilapainen-liikennejarjestelysuunnitelma"
;                               "tyyppiratkaisu"
;                               "tieto-kaivupaikkaan-liittyvista-johtotiedoista"
;                               "liitoslausunto"
;                               "asemapiirros"
;                               "rakennuspiirros"
;                               "suunnitelmakartta"]]
;                             ["muut" ["muu"]]])

(def sijoituslupa-application {:schema-version 1,
                               :id "LP-753-2013-00001",
                               :created 1379592634015,
                               :opened 1379592902883,
                               :modified 1379592969636,
                               :submitted 1379592916811,
                               :permitType "YA",
                               :organization "753-YA",
                               :infoRequest false,
                               :authority sonja,
                               :state "submitted",
                               :title "Hirvimäentie 112",
                               :address "Hirvimäentie 112",
                               :location location,
                               :attachments [],
                               :operations [operation],
                               :propertyId "75342300010054",
                               :documents documents,
                               ; :allowedAttachmentTypes allowedAttachmentTypes,
                               ; :neighbors neighbors,
                               :municipality municipality,
                               :statements statements})