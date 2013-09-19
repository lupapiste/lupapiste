(ns lupapalvelu.document.yleiset-alueet-sijoituslupa-canonical-test)


(def operation {:id "523ae9ba94a7542b3520e649",
                :created 1379592634015,
                :name "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen"})

(def hankkeen-kuvaus-sijoituslupa {:id "523ae9ba94a7542b3520e64a",
                                   :created 1379592634015,
                                   :schema-info {:order 65,
                                                 :version 1,
                                                 :repeating false,
                                                 :removable false,
                                                 :name "yleiset-alueet-hankkeen-kuvaus-sijoituslupa",
                                                 :op operation},
                                   :data {:kaivuLuvanTunniste {:modified 1379592648571, :value "1234567890"},
                                          :kayttotarkoitus {:modified 1379592642729, :value "Hankkeen kuvaus."}}})

(def sijoituksen-tarkoitus {:id "523ae9ba94a7542b3520e64c",
                            :created 1379592634015,
                            :schema-info {:name "sijoituslupa-sijoituksen-tarkoitus",
                                          :removable false,
                                          :repeating false,
                                          :version 1,
                                          :order 66},
                            :data {:lisatietoja-sijoituskohteesta {:modified 1379592841005, :value "Lisätietoja."},
                                   :sijoituksen-tarkoitus {:modified 1379592651471, :value "other"},
                                   ;; Huom: tama näkyy vain, jos yllaolevan :sijoituksen-tarkoitus:n value on "other"
                                   :muu-sijoituksen-tarkoitus {:modified 1379592733099, :value "Muu sijoituksen tarkoitus."}}})

(def documents [hakija
                hankkeen-kuvaus-sijoituslupa
                sijoituksen-tarkoitus])

(def operation {:id "523ae9ba94a7542b3520e649",
                :created 1379592634015,
                :name "ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen"})

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