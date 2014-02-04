(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical-test
  (:require [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as yic]
            [lupapalvelu.factlet :as fl]
            [midje.sweet :refer :all]))

(def meluilmoitus-application {:sent nil,
                               :neighbors {},
                               :schema-version 1,
                               :authority
                               {:role "authority",
                                :lastName "Borga",
                                :firstName "Pekka",
                                :username "pekka",
                                :id "777777777777777777000033"},
                               :auth
                               [{:lastName "Borga",
                                 :firstName "Pekka",
                                 :username "pekka",
                                 :type "owner",
                                 :role "owner",
                                 :id "777777777777777777000033"}],
                               :drawings [],
                               :submitted 1391415717396,
                               :state "submitted",
                               :permitSubtype nil,
                               :tasks [],
                               :_verdicts-seen-by {},
                               :location {:x 428195.77099609, :y 6686701.3931274},
                               :attachments [],
                               :statements [],
                               :organization "638-R",
                               :buildings [],
                               :title "Londbölentie 97",
                               :started nil,
                               :closed nil,
                               :operations
                               [{:id "52ef4ef14206428d3c0394b4",
                                 :name "meluilmoitus",
                                 :created 1391415025497}],
                               :infoRequest false,
                               :openInfoRequest false,
                               :opened 1391415025497,
                               :created 1391415025497,
                               :_comments-seen-by {},
                               :propertyId "63844900010004",
                               :verdicts [],
                               :documents
                               [{:created 1391415025497,
                                 :data
                                 {:_selected {:value "henkilo"},
                                  :henkilo
                                  {:henkilotiedot
                                   {:etunimi
                                    {:modified 1391415662591, :value "Pekka"},
                                    :hetu {:modified 1391415675117, :value "133450-2356"},
                                    :sukunimi {:modified 1391415662591, :value "Borga"}},
                                   :osoite
                                   {:katu {:modified 1391415683882, :value "Murskaajankatu 5"},
                                    :postinumero {:modified 1391415686665, :value "36570"},
                                    :postitoimipaikannimi
                                    {:modified 1391415696674, :value "Kaivonto"}},
                                   :userId
                                   {:modified 1391415662621, :value "777777777777777777000033"},
                                   :yhteystiedot
                                   {:email {:modified 1391415662591, :value "pekka.borga@porvoo.fi"},
                                    :puhelin {:modified 1391415662591, :value "121212"}}}},
                                 :id "52ef4ef14206428d3c0394b6",
                                 :schema-info
                                 {:approvable true,
                                  :subtype "hakija",
                                  :name "hakija",
                                  :removable true,
                                  :repeating true,
                                  :version 1,
                                  :type "party",
                                  :order 3}}
                                {:created 1391415025497,
                                 :data
                                 {:melu
                                  {:melu10mdBa {:modified 1391415596372, :value "150"},
                                   :mittaus {:modified 1391415612510, :value "dbsid?"},
                                   :paivalla {:modified 1391415601672
                                              :value "150"},
                                   :yolla {:modified 1391415602101, :value "0"}},
                                  :rakentaminen
                                  {:koneet
                                   {:modified 1391415557870,
                                    :value
                                    "Murskauksen ja rammeroinnin vaatimat koneet, sekä pyöräkuormaaja. "},
                                   :kuvaus
                                   {:modified 1391415519512,
                                    :value
                                    "Meluilmoitus louhinnasta, rammeroinnista ja murskauksesta"},
                                   :melua-aihettava-toiminta
                                   {:modified 1391415423129, :value "louhinta"}},
                                  :tapahtuma
                                  {:kuvaus
                                   {:modified 1391415593261,
                                    :value "Vähän virkistystä tähän väliin"},
                                   :nimi {:modified 1391415570121, :value "Louhijouden saunailta"},
                                   :ulkoilmakonsertti {:modified 1391415571551, :value true}}},
                                 :id "52ef4ef14206428d3c0394b5",
                                 :schema-info
                                 {:order 50,
                                  :version 1,
                                  :name "meluilmoitus",
                                  :op
                                  {:id "52ef4ef14206428d3c0394b4",
                                   :name "meluilmoitus",
                                   :created 1391415025497},
                                  :removable true}}
                                {:created 1391415025497,
                                 :data
                                 {:kesto
                                  {:alku {:modified 1391415615718, :value "03.02.2014"},
                                   :kello

                                   {:arkisin {:modified 1391415637288, :value "07.00 - 16:00"},
                                    :lauantait {:modified 1391415639677, :value "-"},
                                    :pyhat {:modified 1391415640276, :value "-"}},
                                   :loppu {:modified 1391415618809, :value "07.02.2014"}}},
                                 :id "52ef4ef14206428d3c0394b7",
                                 :schema-info {:name "ymp-ilm-kesto", :version 1, :order 60}}],
                               :_statements-seen-by {},
                               :modified 1391415696674,
                               :comments [],
                               :address "Londbölentie 97",
                               :permitType "YI",
                               :id "LP-638-2014-00001",
                               :municipality "638"})

(fl/facts* "Meluilmoitus to canonical"
           (let [canonical (yic/meluilmoitus-canonical meluilmoitus-application "fi") => truthy
                 Ilmoitukset (:Ilmoitukset canonical) => truthy
                 toimutuksenTiedot (:toimutuksenTiedot Ilmoitukset) => truthyu
                 aineistonnimi (:aineistonnimi toimutuksenTiedot) => (:title meluilmoitus-application)
                 ]
             (clojure.pprint/pprint canonical)))