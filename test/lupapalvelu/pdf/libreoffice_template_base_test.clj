(ns lupapalvelu.pdf.libreoffice-template-base-test
  (:require
    [clojure.string :as s]
    [taoensso.timbre :refer [trace debug debugf]]
    [midje.sweet :refer :all]
    [midje.util :refer [testable-privates]]
    [lupapalvelu.domain :as domain]
    [lupapalvelu.pdf.libreoffice-template :refer :all]))

(def date-01012016 1451606400000)
(def date-02012016 1451692800000)
(def date-03012016 1456790400000)
(def date-30012016 1454112000000)
(def date-01022016 1454284800000)

(def application1 {:id           "LP-000-0000-0000"
                   :organization "753-R"
                   :tosFunction  "10 03 00 01"
                   :created      100
                   :applicant    "Testaaja Testi"
                   :address      "Korpikuusen kannon alla 6"
                   :municipality "186"
                   :documents    []
                   :verdicts     [{:id              "a1"
                                   :timestamp       1454562242169
                                   :kuntalupatunnus "20160043"
                                   :sopimus         false
                                   :paatokset       [{:id             "a2"
                                                      :paivamaarat    {:anto          1454544000000
                                                                       :lainvoimainen 1454544000000
                                                                       :voimassaHetki 1613520000000
                                                                       :aloitettava   1550448000000}

                                                      :lupamaaraykset {:kerrosala                   "100m2"
                                                                       :maaraykset                  [{:sisalto "Vaaditut erityissuunnitelmat: Vesijohto- ja viem\u00e4risuunnitelma"}]
                                                                       :vaaditutTyonjohtajat        "Vastaava ty\u00f6njohtaja"
                                                                       :vaadittuTyonjohtajatieto    ["Vastaava ty\u00f6njohtaja" "Toinen ty\u00f6njohtaja"]
                                                                       :vaaditutErityissuunnitelmat ["Joku erityissuunnitelma" "Joku toinen erityissuunnitelma"]
                                                                       :vaaditutKatselmukset        [{:tarkastuksenTaiKatselmuksenNimi "* KVV-tarkastus" :katselmuksenLaji " muu katselmus "}
                                                                                                     {:tarkastuksenTaiKatselmuksenNimi " * S\u00e4hk\u00f6tarkastus " :katselmuksenLaji " muu katselmus "}
                                                                                                     {:tarkastuksenTaiKatselmuksenNimi " * Rakennetarkastus " :katselmuksenLaji " muu katselmus "}
                                                                                                     {:katselmuksenLaji " loppukatselmus "}
                                                                                                     {:tarkastuksenTaiKatselmuksenNimi " Aloitusilmoitus " :katselmuksenLaji " muu katselmus "}]}

                                                      :poytakirjat    [{:urlHash         "4196f10a7fef9bec325dc567f1b87fbcd10163ce"
                                                                        :status          "1"
                                                                        :paatoksentekija "Tytti M\u00e4ntyoja"
                                                                        :paatos          "Lorelei ipsum sidith meth"
                                                                        :pykala          31
                                                                        :paatospvm       1454284800000
                                                                        :paatoskoodi     "my\u00f6nnetty"}]}]}]
                   :statements   [{:person    {:text "Pelastusviranomainen"
                                               :name "Pia Nyman"}
                                   :requested date-02012016
                                   :given     date-01022016
                                   :status    "ehdoilla"
                                   :text      "Lausunto liitteen\u00e4"
                                   :state     "given"}
                                  {:person    {:text "Rakennussuunnittelu"
                                               :name "Sampo S\u00e4levaara"}
                                   :requested date-03012016
                                   :given     nil
                                   :status    nil
                                   :state     "requested"}]
                   :neighbors    [{:propertyId "111"
                                   :owner      {:type "luonnollinen"
                                                :name "Joku naapurin nimi"}
                                   :id         "112"
                                   :status     [{:state   "open"
                                                 :created date-02012016}
                                                {:state   "mark-done"
                                                 :user    {:firstName "Etu" :lastName "Suku"}
                                                 :created 1453372412991}]}]
                   :tasks        [{:data        {}
                                   :state       "requires_user_action"
                                   :taskname    "rakennuksen paikan tarkastaminen"
                                   :schema-info {:name    "task-katselmus"
                                                 :version 1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      nil
                                   :id          "2222"}
                                  ]
                   :attachments  [{:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                   :versions [{:version {:major 1 :minor 0}
                                               :created date-02012016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}
                                              {:version {:major 2 :minor 0}
                                               :created date-01022016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}]
                                   :user     {:firstName "Testi"
                                              :lastName  "Testaaja"}
                                   :contents "Great attachment"}
                                  {:type     {:type-group "muut" :type-id "paatosote"}
                                   :versions [{::version {:major 1 :minor 0}
                                               :created  date-03012016
                                               :user     {:firstName "Testi"
                                                          :lastName  "Testaaja"}}]}]
                   :history      [{:state "draft"
                                   :ts    date-01012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}
                                  {:state "open"
                                   :ts    date-30012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}]})

(def application2 {:id           "LP-000-0000-0000"
                   :organization "753-R"
                   :tosFunction  "10 03 00 01"
                   :created      100
                   :applicant    "Testaaja Testi"
                   :address      "Korpikuusen kannon alla 6"
                   :municipality "186"
                   :documents    []
                   :verdicts     [{:id              "a1"
                                   :timestamp       1454562242169
                                   :kuntalupatunnus "20160043"
                                   :sopimus         false
                                   :signatures      [{:created 1424669824461
                                                      :user    {:role      "applicant"
                                                                :lastName  "Mallikas"
                                                                :firstName "Matti"
                                                                :username  "matti.maiilkas@example.com"}}
                                                     {:created 1424669824461
                                                      :user    {:role      "applicant"
                                                                :lastName  "Mallikas"
                                                                :firstName "Minna"
                                                                :username  "minna.maiilkas@example.com"}}
                                                     ]
                                   :paatokset       [{:id          "a2"
                                                      :paivamaarat {:anto          1454544000000
                                                                    :lainvoimainen 1454544000000
                                                                    :voimassaHetki 1613520000000
                                                                    :aloitettava   1550448000000}

                                                      :poytakirjat [{:urlHash         "4196f10a7fef9bec325dc567f1b87fbcd10163ce"
                                                                     :status          "1"
                                                                     :paatoksentekija "Tytti M\u00e4ntyoja"
                                                                     :paatos          "Lorem ipsum dolor sit amet
                                                                     rivi 2
                                                                     rivi 3
                                                                     rivi 4"
                                                                     :pykala          31
                                                                     :paatospvm       1454284800000
                                                                     :paatoskoodi     "my\u00f6nnetty"}]}]}]
                   :statements   [{:id        "101"
                                   :person    {:text "Pelastusviranomainen"
                                               :name "Pia Palomies"}
                                   :requested date-02012016
                                   :given     date-01022016
                                   :status    "ehdoilla"
                                   :text      "Lausunto liitteen\u00e4"
                                   :state     "given"}
                                  {:person    {:text "Rakennussuunnittelu"
                                               :name "Risto Rakentaja"}
                                   :requested date-03012016
                                   :given     nil
                                   :status    nil
                                   :state     "requested"}]
                   :neighbors    [{:propertyId "111"
                                   :owner      {:type "luonnollinen"
                                                :name "Joku naapurin nimi"}
                                   :id         "112"
                                   :status     [{:state   "open"
                                                 :created date-02012016}
                                                {:state   "mark-done"
                                                 :user    {:firstName "Etu" :lastName "Suku"}
                                                 :created 1453372412991}]}]
                   :tasks        [{:data        {:katselmuksenLaji {:value "muu katselmus"}}
                                   :state       "requires_user_action"
                                   :taskname    "rakennuksen paikan tarkastaminen"
                                   :schema-info {:name       "task-katselmus"
                                                 :i18nprefix "task-katselmus.katselmuksenLaji"
                                                 :version    1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      {:type "verdict"
                                                 :id   "a1"
                                                 }
                                   :id          "2222"}
                                  {:data        {:katselmuksenLaji {:value "muu katselmus"}}
                                   :state       "requires_user_action"
                                   :taskname    "YA paikan tarkastaminen"
                                   :schema-info {:name       "task-katselmus-ya"
                                                 :i18nprefix "task-katselmus.katselmuksenLaji"
                                                 :version    1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      {:type "verdict"
                                                 :id   "a1"
                                                 }
                                   :id          "2223"}
                                  {:data        {}
                                   :state       "requires_user_action"
                                   :taskname    "Joku ty\u00f6hohtaja"
                                   :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                                 :version 1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      {:type "verdict"
                                                 :id   "a1"
                                                 }
                                   :id          "2223"}
                                  {:data        {}
                                   :state       "requires_user_action"
                                   :taskname    "Joku toinen ty\u00f6hohtaja"
                                   :schema-info {:name    "task-vaadittu-tyonjohtaja"
                                                 :version 1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      {:type "verdict"
                                                 :id   "a1"
                                                 }
                                   :id          "2224"}
                                  {:data        {:maarays {:value "Jotain pitais tehda"}}
                                   :state       "requires_user_action"
                                   :taskname    "Joku lupam\u00e4\u00e4r\u00e4ys"
                                   :schema-info {:name    "task-lupamaarays"
                                                 :version 1}
                                   :closed      nil
                                   :created     date-02012016
                                   :duedate     nil
                                   :assignee    {:lastName  "Suku"
                                                 :firstName "Etu"
                                                 :id        1111}
                                   :source      {:type "verdict"
                                                 :id   "a1"
                                                 }
                                   :id          "2224"}]
                   :attachments  [{:type                 {:type-id "muu", :type-group "muut"},
                                   :state                "requires_authority_action",
                                   :op                   nil,
                                   :modified             1461325119200,
                                   :requestedByAuthority true,
                                   :applicationState     "submitted",
                                   :readOnly             false,
                                   :locked               true,
                                   :id                   "11",
                                   :latestVersion
                                                         {:missing-fonts  [],
                                                          :created        1461325119200,
                                                          :size           2832105,
                                                          :filename       "SomeFile.pdf",
                                                          :originalFileId "44444",
                                                          :contentType    "application/pdf",
                                                          :archivable     true,
                                                          :version        {:minor 2, :major 0},
                                                          :stamped        false,
                                                          :user
                                                                          {:role      "authority",
                                                                           :lastName  "X",
                                                                           :firstName "Mr",
                                                                           :username  "user4@example.com",
                                                                           :id        "666"},
                                                          :fileId         "444"},
                                   :notNeeded            false,
                                   :signatures           [],
                                   :forPrinting          false,
                                   :contents             nil,
                                   :target               {:id "101", :type "statement"},
                                   :versions             [],
                                   :metadata             {:nakyvyys "julkinen"},
                                   :required             false}
                                  {:type                 {:type-id "muu", :type-group "muut"},
                                   :state                "requires_authority_action",
                                   :op                   nil,
                                   :modified             1461325119200,
                                   :requestedByAuthority true,
                                   :applicationState     "submitted",
                                   :readOnly             false,
                                   :locked               true,
                                   :id                   "11",
                                   :latestVersion
                                                         {:missing-fonts  [],
                                                          :created        1461325119200,
                                                          :size           2832105,
                                                          :filename       "SomeFile2.pdf",
                                                          :originalFileId "44444",
                                                          :contentType    "application/pdf",
                                                          :archivable     true,
                                                          :version        {:minor 2, :major 0},
                                                          :stamped        false,
                                                          :user
                                                                          {:role      "authority",
                                                                           :lastName  "Lastname4",
                                                                           :firstName "fristname4",
                                                                           :username  "user4@example.com",
                                                                           :id        "666"},
                                                          :fileId         "444"},
                                   :notNeeded            false,
                                   :signatures           [],
                                   :forPrinting          false,
                                   :contents             nil,
                                   :target               {:id "a1", :type "verdict"},
                                   :versions             [],
                                   :metadata             {:nakyvyys "julkinen"},
                                   :required             false}

                                  {:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                   :versions [{:version {:major 1 :minor 0}
                                               :created date-02012016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}
                                              {:version {:major 2 :minor 0}
                                               :created date-01022016
                                               :user    {:firstName "Testi"
                                                         :lastName  "Testaaja"}}]
                                   :user     {:firstName "Testi"
                                              :lastName  "Testaaja"}
                                   :contents "Great attachment"}
                                  {:type     {:type-group "muut" :type-id "paatosote"}
                                   :versions [{::version {:major 1 :minor 0}
                                               :created  date-03012016
                                               :user     {:firstName "Testi"
                                                          :lastName  "Testaaja"}}]}]
                   :history      [{:state "draft"
                                   :ts    date-01012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}
                                  {:state "open"
                                   :ts    date-30012016
                                   :user  {:firstName "Testi"
                                           :lastName  "Testaaja"}}]})
(defn start-pos [res]
  (first (first (filter #(s/includes? (second %) "</draw:frame>") (map-indexed vector res)))))


(def user-field-line "    <text:user-field-decl office:value-type=\"string\" office:string-value=\"\" text:name=\"LPATITLE_ID\"/>\n")

(fact "Test userfield replacement" (replace-user-field user-field-line {"LPATITLE_ID" "xxx"}) => "    <text:user-field-decl office:value-type=\"string\" office:string-value=\"xxx\" text:name=\"LPATITLE_ID\"/>")

(defn build-user-field [value name]
   (str "    <text:user-field-decl office:value-type=\"string\" office:string-value=\"" (xml-escape value) "\" text:name=\"" name "\"/>"))

(fact "get-document-data"
      (get-in-document-data (assoc application2 :documents [{:schema-info {:name :tyomaastaVastaava}
                                                          :data           {:henkilo {:henkilotiedot {:etunimi  {:value "etu"}
                                                                                                  :sukunimi {:value "suku"}}}
                                                                        :yritys  {:yritysnimi {:value         "Yritys Oy Ab"
                                                                                               :yhteyshenkilo {:henkilotiedot {:etunimi  {:value "Mikko"}
                                                                                                                               :sukunimi {:value "Mallikas"}}}}}}}])
                            :tyomaastaVastaava [:henkilo]) => {:henkilotiedot {:etunimi  {:value "etu"}
                                                                            :sukunimi {:value "suku"}}})
