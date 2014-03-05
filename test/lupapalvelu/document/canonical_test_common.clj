(ns lupapalvelu.document.canonical-test-common
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :refer [validate get-document-schema]]
            [midje.sweet :refer :all]))

;;
;; Document validator predicate
;;

(defn validate-against-current-schema
  "Validates document against the latest schema and returns list of errors."
  [document]
  (validate document (get-document-schema document)))

(defn valid-against-current-schema? [document]
  (or (fact (validate-against-current-schema document) => empty?) true))

(defn validate-all-documents [documents]
  (fact "Meta test: all documents in fixture are valid" documents => (has every? valid-against-current-schema?)))

;; Fixture

(def statements [{:given 1379423133068
                  :id "52385377da063788effc1e93"
                  :person {:text "Paloviranomainen"
                           :name "Sonja Sibbo"
                           :email "sonja.sibbo@sipoo.fi"
                           :id "516560d6c2e6f603beb85147"}
                  :requested 1379423095616
                  :status "yes"
                  :text "Lausunto liitteen\u00e4."}])

(def yrityshakija {:created 1391683428266,
                   :data
                   {:_selected {:modified 1391769554143, :value "yritys"},
                    :yritys
                    {:liikeJaYhteisoTunnus {:modified 1391770449943, :value "1060155-5"},
                     :osoite {:katu {:modified 1391769571984, :value "H\u00e4meenkatu 3 "},
                              :postinumero {:modified 1391770395709, :value "43640"},
                              :postitoimipaikannimi {:modified 1391769576504, :value "kuuva"}},
                     :yhteyshenkilo {:henkilotiedot
                                     {:etunimi {:modified 1391769580313, :value "Pertti"},
                                      :sukunimi {:modified 1391769583050, :value "Yritt\u00e4j\u00e4"}},
                                     :yhteystiedot
                                     {:email {:modified 1391769600334, :value "tew@gjr.fi"},
                                      :puhelin {:modified 1391769589423, :value "060222155"}}},
                     :yritysnimi {:modified 1391769558483, :value "Yrtti Oy"}}},
                   :id "52f3676442067dc3ba4f1ba8",
                   :meta
                   {:_approved
                    {:value "approved",
                     :user
                     {:lastName "Borga",
                      :firstName "Pekka",
                      :id "777777777777777777000033"},
                     :timestamp 1391769601559}},
                   :schema-info
                   {:approvable true,
                    :subtype "hakija",
                    :name "hakija",
                    :removable true,
                    :repeating true,
                    :version 1,
                    :type "party",
                    :order 3}})

(def henkilohakija {:created 1391415025497,
                    :data
                    {:_selected {:value "henkilo"},
                     :henkilo
                     {:henkilotiedot
                      {:etunimi
                       {:modified 1391415662591, :value "Pekka"},
                       :hetu {:modified 1391415675117, :value "210281-9988"},
                       :sukunimi {:modified 1391415662591, :value "Borga"}},
                      :osoite
                      {:katu {:modified 1391415683882, :value "Murskaajankatu 5"},
                       :postinumero {:modified 1391415686665, :value "36570"},
                       :postitoimipaikannimi
                       {:modified 1391415696674, :value "Kaivanto"}},
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
                     :order 3}})

(fact "Meta test: henkilohakija" henkilohakija  => valid-against-current-schema?)
(fact "Meta test: henkilohakija" yrityshakija  => valid-against-current-schema?)

(def drawings [{:id 1,
               :name "alue",
               :desc "alue 1",
               :category "123",
               :geometry
               "LINESTRING(530856.65649413 6972312.1564941,530906.40649413 6972355.6564941,530895.65649413 6972366.9064941,530851.15649413 6972325.9064941,530856.65649413 6972312.4064941)",
               :area "",
               :height "1000"}
              {:id 2,
               :name "Viiva",
               :desc "Viiiva",
               :category "123",
               :geometry
               "LINESTRING(530825.15649413 6972348.9064941,530883.65649413 6972370.1564941,530847.65649413 6972339.4064941,530824.90649413 6972342.4064941)",
               :area "",
               :height ""}
              {:id 3,
               :name "Piste",
               :desc "Piste jutska",
               :category "123",
               :geometry "POINT(530851.15649413 6972373.1564941)",
               :area "",
               :height ""}
              {:id 4
               :name "Alueen nimi"
               :desc "Alueen kuvaus"
               :category "123"
               :geometry "POLYGON((530859.15649413 6972389.4064941,530836.40649413 6972367.4064941,530878.40649413 6972372.6564941,530859.15649413 6972389.4064941))",
               :area "402",
               :height  ""
               }])

(def neighbors {:53158d0c42069e0c20033977
                {:neighbor
                 {:propertyId "75342600060211",
                  :owner
                  {:type "tuntematon",
                   :name "Lindh, Tor-Erik Bertel",
                   :email "t@t.fi",
                   :businessID nil,
                   :nameOfDeceased nil,
                   :address {:street nil, :city nil, :zip nil}}},
                 :status
                 [{:state "open", :created 1393921292598}
                  {:state "email-sent",
                   :email "t@t.fi",
                   :token "6uWPBLYwxZpvEiSCYi8t9NfnOKf7Vu8mK2QT6LuJRYswXV6i",
                   :user
                   {:enabled true,
                    :lastName "Sibbo",
                    :firstName "Sonja",
                    :city "Sipoo",
                    :username "sonja",
                    :street "Katuosoite 1 a 1",
                    :phone "03121991",
                    :email "sonja.sibbo@sipoo.fi",
                    :role "authority",
                    :zip "33456",
                    :organizations ["753-R" "753-YA"],
                    :id "777777777777777777000023"},
                   :created 1393921672676}
                  {:state "response-given-ok",
                   :message "",
                   :user nil,
                   :vetuma
                   {:stamp "06485919427433614295",
                    :userid "210281-9988",
                    :city nil,
                    :zip nil,
                    :street nil,
                    :lastName "TESTAA",
                    :firstName "PORTAALIA"},
                   :created 1393921693863}]},
                :53158d1242069e0c20033986
                {:neighbor
                 {:propertyId "75342600020137",
                  :owner
                  {:type "luonnollinen",
                   :name "Wickström, Stig Gunnar",
                   :email "e@e.fi",
                   :businessID nil,
                   :nameOfDeceased nil,
                   :address
                   {:street "Grankullavägen 38", :city "PAIPIS", :zip "04170"}}},
                 :status
                 [{:state "open", :created 1393921298073}
                  {:state "email-sent",
                   :email "e@e.fi",
                   :token "OlOWC6Bdp7tWTlaaBMgzmhiqyZm2o1OeY87KGnydG8RR3Pit",
                   :user
                   {:enabled true,
                    :lastName "Sibbo",
                    :firstName "Sonja",
                    :city "Sipoo",
                    :username "sonja",
                    :street "Katuosoite 1 a 1",
                    :phone "03121991",
                    :email "sonja.sibbo@sipoo.fi",
                    :role "authority",
                    :zip "33456",
                    :organizations ["753-R" "753-YA"],
                    :id "777777777777777777000023"},
                   :created 1393921476093}]},
                :53158d4b42069e0c200339b0
                {:neighbor
                 {:propertyId "75342600020050",
                  :owner
                  {:type "luonnollinen",
                   :name "Bergström, Georg Fredrik",
                   :email nil,

                   :businessID nil,
                   :nameOfDeceased nil,
                   :address {:street "Almvägen 4", :city "SIBBO", :zip "04130"}}},
                 :status [{:state "open", :created 1393921355438}]},
                :53158d9942069e0c200339f6
                {:neighbor
                 {:propertyId "75342600090092",
                  :owner
                  {:type "luonnollinen",
                   :name "Lönnqvist, Rauno Georg Christian",
                   :email nil,
                   :businessID nil,
                   :nameOfDeceased nil,
                   :address
                   {:street "Aspängsvägen 27", :city "PAIPIS", :zip "04170"}}},
                 :status
                 [{:state "open", :created 1393921433258}
                  {:state "mark-done",
                   :user
                   {:enabled true,
                    :lastName "Sibbo",
                    :firstName "Sonja",
                    :city "Sipoo",
                    :username "sonja",
                    :street "Katuosoite 1 a 1",
                    :phone "03121991",
                    :email "sonja.sibbo@sipoo.fi",
                    :role "authority",
                    :zip "33456",
                    :organizations ["753-R" "753-YA"],
                    :id "777777777777777777000023"},
                   :created 1393921738151}]}})