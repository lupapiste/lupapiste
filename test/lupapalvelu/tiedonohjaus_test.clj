(ns lupapalvelu.tiedonohjaus-test
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as o]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus :refer :all]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.env :as env])
  (:import [java.util Date]))

(def handler-roles [{:id "58933955cbc214d39ae9688c", :name {:fi "Käsittelijä", :sv "Handläggare", :en "Handler"}, :general true}
                    {:id "58a1ae0a9f5d940c0647d25a", :name {:fi "Testi-rooli", :sv "Test Role", :en "Test Role"}}
                    {:id "58aab772a20112dee7662c92", :name {:fi "Rooli", :sv "Role", :en "Role"}}
                    {:id "58b581558a73e50b5cd2e2fb", :name {:fi "KVV-Käsittelijä", :sv "KVV-Handläggare", :en "KVV-Handler"}}
                    {:id "58b7e9254cee47962aea4df4", :name {:fi "Uusi", :sv "Ny", :en "New"}, :disabled true}])

(facts "about tiedonohjaus utils"
  (let [application {:organization    "753-R"
                     :tosFunction     "10 03 00 01"
                     :created         100
                     :applicant       "Testaaja Testi"
                     :id              "1"
                     :authority       {:firstName "Monni" :lastName "Tiskaa"}
                     :processMetadata {:julkisuusluokka "julkinen"
                                       :henkilotiedot   "ei-sisalla"
                                       :sailytysaika    {:arkistointi :ikuisesti
                                                         :perustelu   "Laki"}}
                     :statements      [{:person    {:text "Pelastusviranomainen"
                                                    :name "Pia Nyman"}
                                        :requested 653238400000
                                        :given     663238400000
                                        :status    "ehdollinen"
                                        :text      "Lausunto liitteenä"
                                        :state     "given"}
                                       {:person    {:text "Rakennussuunnittelu"
                                                    :name "Sampo Sälevaara"}
                                        :requested 643238400000
                                        :given     nil
                                        :status    nil
                                        :state     "requested"}]
                     :neighbors       [{:propertyId "111"
                                        :owner      {:type "luonnollinen"
                                                     :name "Joku naapurin nimi"}
                                        :id         "112"
                                        :status     [{:state   "open"
                                                      :created 923238400000}]}
                                       {:propertyId "222"
                                        :owner      {:type "luonnollinen"
                                                     :name "Toinen naapuri"}
                                        :id         "223"
                                        :status     [{:state   "open"
                                                      :created 923238400000}
                                                     {:state   "mark-done"
                                                      :user    {:firstName "First" :lastName "Last"}
                                                      :created 933338400000}]}]
                     :tasks           [{:data        {}
                                        :state       "requires_user_action"
                                        :taskname    "rakennuksen paikan tarkastaminen"
                                        :schema-info {:name    "task-katselmus"
                                                      :version 1}
                                        :closed      nil
                                        :created     633238400000
                                        :duedate     nil
                                        :assignee    {:lastName  "Suku"
                                                      :firstName "Etu"
                                                      :id        1111}
                                        :source      nil
                                        :id          "2222"}
                                       ]
                     :attachments     [{:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                        :versions [{:version {:major 1 :minor 0}
                                                    :created 200
                                                    :user    {:firstName "Testi"
                                                              :lastName  "Testaaja"}}
                                                   {:version {:major 2 :minor 2}
                                                    :created 663238400000
                                                    :user    {:firstName "Testi"
                                                              :lastName  "Testaaja"}}]
                                        :id       "2"
                                        :contents "Great attachment"}
                                       {:type     {:type-group "paapiirustus" :type-id "pohjapiirros"}
                                        :id       "3"
                                        :versions [{:version {:major 0 :minor 1}
                                                    :created 623238400000
                                                    :user    {:firstName "Testi"
                                                              :lastName  "Testaaja"}}]}]
                     :history         [{:state "open"
                                        :ts    100
                                        :user  {:firstName "Testi"
                                                :lastName  "Testaaja"}}
                                       {:state "submitted"
                                        :ts    523238400000
                                        :user  {:firstName "Testi"
                                                :lastName  "Testaaja"}}
                                       {:state "complementNeeded"
                                        :ts    1462060800000
                                        :user  {:firstName "Heikki"
                                                :lastName  "Hepokatti"}}
                                       {:handler {:id        "123"
                                                  :roleId    "58933955cbc214d39ae9688c"
                                                  :userId    "777777777777777777000023"
                                                  :firstName "Sonja"
                                                  :lastName  "Sibbo"
                                                  :new-entry true}
                                        :ts      1462060800000
                                        :user    {:firstName "Heikki"
                                                  :lastName  "Hepokatti"}}
                                       {:handler {:id        "123"
                                                  :roleId    "58933955cbc214d39ae9688c"
                                                  :userId    "777777777777777777000024"
                                                  :firstName "Ronja"
                                                  :lastName  "Sibbo"}
                                        :ts      1462060800000
                                        :user    {:firstName "Heikki"
                                                  :lastName  "Hepokatti"}}
                                       {:handler {:id      "123"
                                                  :removed true}
                                        :ts      1462060800000
                                        :user    {:firstName "Heikki"
                                                  :lastName  "Hepokatti"}}]}]

    (fact "case file report data is generated from application"
      (generate-case-file-data application :fi) => [{:action    "Valmisteilla"
                                                     :start     100
                                                     :user      "Testaaja Testi"
                                                     :documents [{:type     :hakemus
                                                                  :category :document
                                                                  :ts       100
                                                                  :user     "Testaaja Testi"
                                                                  :id       "1-application"}
                                                                 {:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                                                  :category :document
                                                                  :version  {:major 1 :minor 0}
                                                                  :ts       200
                                                                  :user     "Testaaja Testi"
                                                                  :contents "Great attachment"
                                                                  :id       "2"}]}
                                                    {:action    "Vireilletulo"
                                                     :start     523238400000
                                                     :user      "Testaaja Testi"
                                                     :documents [{:type     {:type-group "paapiirustus" :type-id "pohjapiirros"}
                                                                  :category :document
                                                                  :version  {:major 0 :minor 1}
                                                                  :ts       623238400000
                                                                  :user     "Testaaja Testi"
                                                                  :contents nil
                                                                  :id       "3"}
                                                                 {:category :request-review, :ts 633238400000, :text "rakennuksen paikan tarkastaminen", :user "Suku Etu"}
                                                                 {:category :request-statement, :ts 643238400000, :text "Rakennussuunnittelu", :user ""}
                                                                 {:category :request-statement, :ts 653238400000, :text "Pelastusviranomainen", :user ""}
                                                                 {:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                                                                  :category :document
                                                                  :version  {:major 2 :minor 2}
                                                                  :ts       663238400000
                                                                  :user     "Testaaja Testi"
                                                                  :contents "Great attachment"
                                                                  :id       "2"}
                                                                 {:text     "Joku naapurin nimi"
                                                                  :category :request-neighbor
                                                                  :ts       923238400000
                                                                  :user     " "}
                                                                 {:text     "Toinen naapuri"
                                                                  :category :request-neighbor
                                                                  :ts       923238400000
                                                                  :user     " "}
                                                                 {:text     "Toinen naapuri"
                                                                  :category :request-neighbor-done
                                                                  :ts       933338400000
                                                                  :user     "Last First"}]}
                                                    {:action    (i18n/localize :fi "caseFile.complementNeeded")
                                                     :start     1462060800000
                                                     :user      "Hepokatti Heikki"
                                                     :documents [{:category :handler-added,
                                                                  :ts       1462060800000,
                                                                  :text     "Sibbo Sonja (Käsittelijä)",
                                                                  :user     "Hepokatti Heikki",
                                                                  :handler  {:id "123", :roleId "58933955cbc214d39ae9688c", :userId "777777777777777777000023", :firstName "Sonja", :lastName "Sibbo" :new-entry true}},
                                                                 {:category :handler-change,
                                                                  :ts       1462060800000,
                                                                  :text     "Sibbo Sonja -> Sibbo Ronja (Käsittelijä)",
                                                                  :user     "Hepokatti Heikki",
                                                                  :handler  {:id "123", :roleId "58933955cbc214d39ae9688c", :userId "777777777777777777000024", :firstName "Ronja", :lastName "Sibbo"}},
                                                                 {:category :handler-removed,
                                                                  :ts       1462060800000,
                                                                  :text     "Sibbo Ronja (Käsittelijä)",
                                                                  :user     "Hepokatti Heikki",
                                                                  :handler  {:id "123", :removed true}}]}]
      (provided
       (o/get-organization "753-R") => {:handler-roles handler-roles}
       (toimenpide-for-state "753-R" "10 03 00 01" "submitted") => {:name "Vireilletulo"}
       (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "Valmisteilla"}
       (toimenpide-for-state "753-R" "10 03 00 01" "complementNeeded") => {}))

    (fact "case file report generation should skip tasks without creation ts"
      (generate-case-file-data (update-in application [:tasks 0] dissoc :created) :fi)
      => [{:action    "Valmisteilla"
           :start     100
           :user      "Testaaja Testi"
           :documents [{:type     :hakemus
                        :category :document
                        :ts       100
                        :user     "Testaaja Testi"
                        :id       "1-application"}
                       {:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                        :category :document
                        :version  {:major 1 :minor 0}
                        :ts       200
                        :user     "Testaaja Testi"
                        :contents "Great attachment"
                        :id       "2"}]}
          {:action    "Vireilletulo"
           :start     523238400000
           :user      "Testaaja Testi"
           :documents [{:type     {:type-group "paapiirustus" :type-id "pohjapiirros"}
                        :category :document
                        :version  {:major 0 :minor 1}
                        :ts       623238400000
                        :user     "Testaaja Testi"
                        :contents nil
                        :id       "3"}
                       {:category :request-statement, :ts 643238400000, :text "Rakennussuunnittelu", :user ""}
                       {:category :request-statement, :ts 653238400000, :text "Pelastusviranomainen", :user ""}
                       {:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
                        :category :document
                        :version  {:major 2 :minor 2}
                        :ts       663238400000
                        :user     "Testaaja Testi"
                        :contents "Great attachment"
                        :id       "2"}
                       {:text     "Joku naapurin nimi"
                        :category :request-neighbor
                        :ts       923238400000
                        :user     " "}
                       {:text     "Toinen naapuri"
                        :category :request-neighbor
                        :ts       923238400000
                        :user     " "}
                       {:text     "Toinen naapuri"
                        :category :request-neighbor-done
                        :ts       933338400000
                        :user     "Last First"}]}
          {:action    (i18n/localize :fi "caseFile.complementNeeded")
           :start     1462060800000
           :user      "Hepokatti Heikki"
           :documents [{:category :handler-added,
                        :ts       1462060800000,
                        :text     "Sibbo Sonja (Käsittelijä)",
                        :user     "Hepokatti Heikki",
                        :handler  {:id "123", :roleId "58933955cbc214d39ae9688c", :userId "777777777777777777000023", :firstName "Sonja", :lastName "Sibbo" :new-entry true}},
                       {:category :handler-change,
                        :ts       1462060800000,
                        :text     "Sibbo Sonja -> Sibbo Ronja (Käsittelijä)",
                        :user     "Hepokatti Heikki",
                        :handler  {:id "123", :roleId "58933955cbc214d39ae9688c", :userId "777777777777777777000024", :firstName "Ronja", :lastName "Sibbo"}},
                       {:category :handler-removed,
                        :ts       1462060800000,
                        :text     "Sibbo Ronja (Käsittelijä)",
                        :user     "Hepokatti Heikki",
                        :handler  {:id "123", :removed true}}]}]
      (provided
       (o/get-organization "753-R") => {:handler-roles handler-roles}
       (toimenpide-for-state "753-R" "10 03 00 01" "submitted") => {:name "Vireilletulo"}
       (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "Valmisteilla"}
       (toimenpide-for-state "753-R" "10 03 00 01" "complementNeeded") => {}))

    (fact "case file report can be generated as xml"
      (xml-case-file application :fi) => "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><lp:CaseFile xmlns:lp=\"http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1\"><lp:Created>1970-01-01+02:00</lp:Created><lp:NativeId>1</lp:NativeId><lp:Language>fi</lp:Language><lp:Restriction><lp:PublicityClass>Julkinen</lp:PublicityClass><lp:PersonalData>ei sisällä henkilötietoja</lp:PersonalData></lp:Restriction><lp:Title>Käsittelyprosessi: 1</lp:Title><lp:RetentionPeriod>999999</lp:RetentionPeriod><lp:RetentionReason>Laki</lp:RetentionReason><lp:Status>valmis</lp:Status><lp:ClassificationScheme><lp:MainFunction><lp:Title>Maankäyttö, Rakentaminen ja Asuminen</lp:Title><lp:FunctionCode>10</lp:FunctionCode><lp:FunctionClassification><lp:Title>Rakentaminen, ylläpito ja käyttö</lp:Title><lp:FunctionCode>10 03</lp:FunctionCode><lp:SubFunction><lp:Title>Rakennusvalvonta</lp:Title><lp:FunctionCode>10 03 00</lp:FunctionCode><lp:SubFunction><lp:Title>Rakennuslupamenettely</lp:Title><lp:FunctionCode>10 03 00 01</lp:FunctionCode></lp:SubFunction></lp:SubFunction></lp:FunctionClassification></lp:MainFunction></lp:ClassificationScheme><lp:Function>10 03 00 01</lp:Function><lp:Agent><lp:Role>responsible</lp:Role><lp:Name>Monni Tiskaa</lp:Name></lp:Agent><lp:Action><lp:Created>1970-01-01+02:00</lp:Created><lp:Title>Valmisteilla</lp:Title><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent><lp:Type>Valmisteilla</lp:Type><lp:Record><lp:Created>1970-01-01+02:00</lp:Created><lp:NativeId>1-application</lp:NativeId><lp:Title>Lupapisteen hakemus</lp:Title><lp:Type>hakemus</lp:Type><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record><lp:Record><lp:Created>1970-01-01+02:00</lp:Created><lp:NativeId>2</lp:NativeId><lp:Description>Great attachment</lp:Description><lp:Title>Asemapiirros</lp:Title><lp:Type>paapiirustus.asemapiirros</lp:Type><lp:Version>1.0</lp:Version><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record></lp:Action><lp:Action><lp:Created>1986-08-01+03:00</lp:Created><lp:Title>Vireilletulo</lp:Title><lp:Custom><lp:ActionEvent><lp:Description>Vaatimus lisätty: rakennuksen paikan tarkastaminen</lp:Description><lp:Type>request-review</lp:Type><lp:Created>1990-01-25+02:00</lp:Created><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Suku Etu</lp:Name></lp:Agent></lp:ActionEvent><lp:ActionEvent><lp:Description>Lausuntopyyntö tehty: Rakennussuunnittelu</lp:Description><lp:Type>request-statement</lp:Type><lp:Created>1990-05-21+03:00</lp:Created></lp:ActionEvent><lp:ActionEvent><lp:Description>Lausuntopyyntö tehty: Pelastusviranomainen</lp:Description><lp:Type>request-statement</lp:Type><lp:Created>1990-09-13+03:00</lp:Created></lp:ActionEvent><lp:ActionEvent><lp:Description>Kuultava naapuri lisätty: Joku naapurin nimi</lp:Description><lp:Type>request-neighbor</lp:Type><lp:Created>1999-04-04+03:00</lp:Created></lp:ActionEvent><lp:ActionEvent><lp:Description>Kuultava naapuri lisätty: Toinen naapuri</lp:Description><lp:Type>request-neighbor</lp:Type><lp:Created>1999-04-04+03:00</lp:Created></lp:ActionEvent><lp:ActionEvent><lp:Description>Naapuri merkitty kuulluksi: Toinen naapuri</lp:Description><lp:Type>request-neighbor-done</lp:Type><lp:Created>1999-07-30+03:00</lp:Created><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Last First</lp:Name></lp:Agent></lp:ActionEvent></lp:Custom><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent><lp:Type>Vireilletulo</lp:Type><lp:Record><lp:Created>1989-10-01+02:00</lp:Created><lp:NativeId>3</lp:NativeId><lp:Title>Pohjapiirros</lp:Title><lp:Type>paapiirustus.pohjapiirros</lp:Type><lp:Version>0.1</lp:Version><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record><lp:Record><lp:Created>1991-01-07+02:00</lp:Created><lp:NativeId>2</lp:NativeId><lp:Description>Great attachment</lp:Description><lp:Title>Asemapiirros</lp:Title><lp:Type>paapiirustus.asemapiirros</lp:Type><lp:Version>2.2</lp:Version><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record></lp:Action><lp:Action><lp:Created>2016-05-01+03:00</lp:Created><lp:Title>Palautettu täydennettäväksi</lp:Title><lp:Custom><lp:ActionEvent><lp:Description>Uusi käsittelijä: Sibbo Sonja (Käsittelijä)</lp:Description><lp:Type>handler-added</lp:Type><lp:Created>2016-05-01+03:00</lp:Created><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Hepokatti Heikki</lp:Name></lp:Agent></lp:ActionEvent><lp:ActionEvent><lp:Description>Käsittelijä vaihdettu: Sibbo Sonja -&gt; Sibbo Ronja (Käsittelijä)</lp:Description><lp:Type>handler-change</lp:Type><lp:Created>2016-05-01+03:00</lp:Created><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Hepokatti Heikki</lp:Name></lp:Agent></lp:ActionEvent><lp:ActionEvent><lp:Description>Käsittelijä poistettu: Sibbo Ronja (Käsittelijä)</lp:Description><lp:Type>handler-removed</lp:Type><lp:Created>2016-05-01+03:00</lp:Created><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Hepokatti Heikki</lp:Name></lp:Agent></lp:ActionEvent></lp:Custom><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Hepokatti Heikki</lp:Name></lp:Agent><lp:Type>Palautettu täydennettäväksi</lp:Type></lp:Action></lp:CaseFile>"

      (provided
       (o/get-organization "753-R") => {:handler-roles handler-roles}
       (toimenpide-for-state "753-R" "10 03 00 01" "submitted") => {:name "Vireilletulo"}
       (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "Valmisteilla"}
       (toimenpide-for-state "753-R" "10 03 00 01" "complementNeeded") => {}
       (get-from-toj-api "753-R" false "10 03 00 01" "/classification") => "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a:ClassificationScheme xmlns:a=\"http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1\"><a:MainFunction><a:FunctionCode>10</a:FunctionCode><a:Title>Maankäyttö, Rakentaminen ja Asuminen</a:Title><a:FunctionClassification><a:FunctionCode>10 03</a:FunctionCode><a:Title>Rakentaminen, ylläpito ja käyttö</a:Title><a:SubFunction><a:FunctionCode>10 03 00</a:FunctionCode><a:Title>Rakennusvalvonta</a:Title><a:SubFunction><a:FunctionCode>10 03 00 01</a:FunctionCode><a:Title>Rakennuslupamenettely</a:Title></a:SubFunction></a:SubFunction></a:FunctionClassification></a:MainFunction></a:ClassificationScheme>"))))

(def wrap (partial metadata/wrap "User" 22222))

(defn assoc-verdict [application verdict-type & [ts]]
  {:pre [#{:backing-system :legacy :legacy-wrapped :pate :pate-wrapped} verdict-type]}
  (let [ts             (or ts 1456696800000)
        legacy-wrapped {:category  "r"
                        :legacy?   true
                        :published {:published 12345}
                        :data      {:anto (wrap ts)}}
        pate-wrapped   {:category  "r"
                        :published {:published 12345}
                        :data      {:verdict-date (wrap ts)}}]
    (merge application
           (case verdict-type
             :backing-system {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm ts}]}]}]}
             :legacy         {:pate-verdicts [(metadata/unwrap-all legacy-wrapped)]}
             :legacy-wrapped {:pate-verdicts [legacy-wrapped]}
             :pate           {:pate-verdicts [(metadata/unwrap-all pate-wrapped)]}
             :pate-wrapped   {:pate-verdicts [pate-wrapped]}))))

(defn state-change-test [verdict-type]
  (fact {:midje/description (str "Verdict type is " (name verdict-type))}
    (let [metadata         {:tila                :luonnos
                            :salassapitoaika     5
                            :nakyvyys            :julkinen
                            :sailytysaika        {:arkistointi (keyword "määräajan")
                                                  :pituus      10
                                                  :perustelu   "foo"}
                            :myyntipalvelu       false
                            :suojaustaso         :ei-luokiteltu
                            :kayttajaryhma       :viranomaisryhma
                            :kieli               :fi
                            :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                            :salassapitoperuste  "peruste"
                            :henkilotiedot       :sisaltaa
                            :julkisuusluokka     :salainen
                            :kayttajaryhmakuvaus :muokkausoikeus}
          process-metadata {:julkisuusluokka :salainen
                            :salassapitoaika 5}
          application      (assoc-verdict {:id              1000
                                           :organization    "753-R"
                                           :metadata        metadata
                                           :processMetadata process-metadata
                                           :attachments     [{:id 1 :metadata metadata}
                                                             {:id 2 :metadata metadata}]}
                                          verdict-type)
          command          (action/application->command application)]
      (mark-app-and-attachments-final! 1000 12345678) => nil
    (provided
     (domain/get-application-no-access-checking 1000) => application

     (action/update-application command {$set {:modified        12345678
                                               :metadata        {:tila                :valmis
                                                                 :salassapitoaika     5
                                                                 :nakyvyys            :julkinen
                                                                 :sailytysaika        {:arkistointi          (keyword "määräajan")
                                                                                       :pituus               10
                                                                                       :perustelu            "foo"
                                                                                       :retention-period-end #inst "2026-02-27T22:00:00.000-00:00"}
                                                                 :myyntipalvelu       false
                                                                 :suojaustaso         :ei-luokiteltu
                                                                 :security-period-end #inst "2021-02-27T22:00:00.000-00:00"
                                                                 :kayttajaryhma       :viranomaisryhma
                                                                 :kieli               :fi
                                                                 :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                 :salassapitoperuste  "peruste"
                                                                 :henkilotiedot       :sisaltaa
                                                                 :julkisuusluokka     :salainen
                                                                 :kayttajaryhmakuvaus :muokkausoikeus}
                                               :processMetadata {:julkisuusluokka     :salainen
                                                                 :salassapitoaika     5
                                                                 :security-period-end #inst "2021-02-27T22:00:00.000-00:00"}}}) => nil
     (action/update-application command
                                {:attachments {$elemMatch {:id 1}}}
                                {$set {:modified               12345678
                                       :attachments.$.metadata {:tila                :valmis
                                                                :salassapitoaika     5
                                                                :nakyvyys            :julkinen
                                                                :sailytysaika        {:arkistointi          (keyword "määräajan")
                                                                                      :pituus               10
                                                                                      :perustelu            "foo"
                                                                                      :retention-period-end #inst "2026-02-27T22:00:00.000-00:00"}
                                                                :myyntipalvelu       false
                                                                :suojaustaso         :ei-luokiteltu
                                                                :security-period-end #inst "2021-02-27T22:00:00.000-00:00"
                                                                :kayttajaryhma       :viranomaisryhma
                                                                :kieli               :fi
                                                                :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                :salassapitoperuste  "peruste"
                                                                :henkilotiedot       :sisaltaa
                                                                :julkisuusluokka     :salainen
                                                                :kayttajaryhmakuvaus :muokkausoikeus}}}) => nil
     (action/update-application command
                                {:attachments {$elemMatch {:id 2}}}
                                {$set {:modified               12345678
                                       :attachments.$.metadata {:tila                :valmis
                                                                :salassapitoaika     5
                                                                :nakyvyys            :julkinen
                                                                :sailytysaika        {:arkistointi          (keyword "määräajan")
                                                                                      :pituus               10
                                                                                      :perustelu            "foo"
                                                                                      :retention-period-end #inst "2026-02-27T22:00:00.000-00:00"}
                                                                :myyntipalvelu       false
                                                                :suojaustaso         :ei-luokiteltu
                                                                :security-period-end #inst "2021-02-27T22:00:00.000-00:00"
                                                                :kayttajaryhma       :viranomaisryhma
                                                                :kieli               :fi
                                                                :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                :salassapitoperuste  "peruste"
                                                                :henkilotiedot       :sisaltaa
                                                                :julkisuusluokka     :salainen
                                                                :kayttajaryhmakuvaus :muokkausoikeus}}}) => nil))))

(fact "application and attachment state (tila) is changed correctly"
  (state-change-test :backing-system)
  (state-change-test :legacy)
  (state-change-test :legacy-wrapped)
  (state-change-test :pate)
  (state-change-test :pate-wrapped))

(defn attachment-state-change-test [verdict-type]
  (fact {:midje/description (str "Attachment state change for " (name verdict-type))}
    (let [application   (assoc-verdict {:id           1000
                                       :organization "753-R"
                                       :metadata     {:tila "luonnos"}
                                       :attachments  [{:id 1 :metadata {:tila "luonnos"}}
                                                      {:id 2 :metadata {:tila                :luonnos
                                                                        :salassapitoaika     5
                                                                        :nakyvyys            :julkinen
                                                                        :sailytysaika        {:arkistointi (keyword "määräajan")
                                                                                              :pituus      10
                                                                                              :perustelu   "foo"}
                                                                        :myyntipalvelu       false
                                                                        :suojaustaso         :ei-luokiteltu
                                                                        :kayttajaryhma       :viranomaisryhma
                                                                        :kieli               :fi
                                                                        :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                        :salassapitoperuste  "peruste"
                                                                        :henkilotiedot       :sisaltaa
                                                                        :julkisuusluokka     :salainen
                                                                        :kayttajaryhmakuvaus :muokkausoikeus}}]}
                                      verdict-type)
         now           12345678
         attachment-id 2]
     (mark-attachment-final! application now attachment-id) => nil
     (provided
      (action/update-application (action/application->command application)
                                 {:attachments {$elemMatch {:id attachment-id}}}
                                 {$set {:modified               now
                                        :attachments.$.metadata {:tila                :valmis
                                                                 :salassapitoaika     5
                                                                 :nakyvyys            :julkinen
                                                                 :sailytysaika        {:arkistointi          (keyword "määräajan")
                                                                                       :pituus               10
                                                                                       :perustelu            "foo"
                                                                                       :retention-period-end #inst "2026-02-27T22:00:00.000-00:00"}
                                                                 :myyntipalvelu       false
                                                                 :suojaustaso         :ei-luokiteltu
                                                                 :security-period-end #inst "2021-02-27T22:00:00.000-00:00"
                                                                 :kayttajaryhma       :viranomaisryhma
                                                                 :kieli               :fi
                                                                 :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                 :salassapitoperuste  "peruste"
                                                                 :henkilotiedot       :sisaltaa
                                                                 :julkisuusluokka     :salainen
                                                                 :kayttajaryhmakuvaus :muokkausoikeus}}}) => nil))))

(fact "attachment state (tila) is changed correctly"
  (attachment-state-change-test :backing-system)
  (attachment-state-change-test :legacy)
  (attachment-state-change-test :legacy-wrapped)
  (attachment-state-change-test :pate)
  (attachment-state-change-test :pate-wrapped))

(defn document-metadata-update-test [verdict-type]
  (fact {:midje/description (str "Document metadata update for " (name verdict-type))}
    (let [backing-system? (= verdict-type :backing-system)
          verdicts        (-> {}
                              (assoc-verdict :backing-system (when-not backing-system? 12345))
                              (assoc-verdict (if backing-system?
                                               :pate
                                               verdict-type)
                                             (when backing-system? 12345)))
          application     (merge verdicts
                                 {:id           1000
                                  :organization "753-R"
                                  :metadata     {:tila     "valmis"
                                                 :nakyvyys "julkinen"}})
          result          (merge verdicts
                                 {:id           1000
                                  :organization "753-R"
                                  :metadata     {:tila                :valmis
                                                 :salassapitoaika     5
                                                 :nakyvyys            :julkinen
                                                 :sailytysaika        {:arkistointi          (keyword "määräajan")
                                                                       :pituus               12
                                                                       :perustelu            "foo"
                                                                       ;; Leap year
                                                                       :retention-period-end #inst "2028-02-28T22:00:00.000-00:00"}
                                                 :myyntipalvelu       false
                                                 :suojaustaso         :ei-luokiteltu
                                                 ;; Not a leap year
                                                 :security-period-end #inst "2021-02-27T22:00:00.000-00:00"
                                                 :kayttajaryhma       :viranomaisryhma
                                                 :kieli               :fi
                                                 :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                 :salassapitoperuste  "peruste"
                                                 :henkilotiedot       :sisaltaa
                                                 :julkisuusluokka     :salainen
                                                 :kayttajaryhmakuvaus :muokkausoikeus}})
          nil-dates       {:verdicts      [{:paatokset [{:poytakirjat [(if backing-system?
                                                                         {:paatospvm 1456696800000}
                                                                         {})]}]}]
                           :pate-verdicts [{:category  "r"
                                            :published {:published 10}
                                            :data      (if-not backing-system?
                                                         {:verdict-date 1456696800000}
                                                         {})}
                                           {:category  "ya"
                                            :legacy?   true
                                            :published {:published 10}
                                            :data      {}}]}
          anto-high       verdicts]
    (document-with-updated-metadata application "753-R" "10" application "hakemus")
    => result
    (provided
     (metadata-for-document "753-R" "10" "hakemus") => {:tila                :luonnos
                                                        :salassapitoaika     5
                                                        :nakyvyys            :julkinen
                                                        :sailytysaika        {:arkistointi (keyword "määräajan")
                                                                              :pituus      12
                                                                              :perustelu   "foo"}
                                                        :myyntipalvelu       false
                                                        :suojaustaso         :ei-luokiteltu
                                                        :kayttajaryhma       :viranomaisryhma
                                                        :kieli               :fi
                                                        :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                        :salassapitoperuste  "peruste"
                                                        :henkilotiedot       :sisaltaa
                                                        :julkisuusluokka     :salainen
                                                        :kayttajaryhmakuvaus :muokkausoikeus})
    (document-with-updated-metadata (merge application nil-dates) "753-R" "10"
                                    (merge application nil-dates) "hakemus")
    => (merge result nil-dates)
    (provided
     (metadata-for-document "753-R" "10" "hakemus") => {:tila                :luonnos
                                                        :salassapitoaika     5
                                                        :nakyvyys            :julkinen
                                                        :sailytysaika        {:arkistointi (keyword "määräajan")
                                                                              :pituus      12
                                                                              :perustelu   "foo"}
                                                        :myyntipalvelu       false
                                                        :suojaustaso         :ei-luokiteltu
                                                        :kayttajaryhma       :viranomaisryhma
                                                        :kieli               :fi
                                                        :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                        :salassapitoperuste  "peruste"
                                                        :henkilotiedot       :sisaltaa
                                                        :julkisuusluokka     :salainen
                                                        :kayttajaryhmakuvaus :muokkausoikeus})
    (document-with-updated-metadata (merge application anto-high) "753-R" "10"
                                    (merge application anto-high) "hakemus")
    => (merge result anto-high)
    (provided
     (metadata-for-document "753-R" "10" "hakemus") => {:tila                :luonnos
                                                        :salassapitoaika     5
                                                        :nakyvyys            :julkinen
                                                        :sailytysaika        {:arkistointi (keyword "määräajan")
                                                                              :pituus      12
                                                                              :perustelu   "foo"}
                                                        :myyntipalvelu       false
                                                        :suojaustaso         :ei-luokiteltu
                                                        :kayttajaryhma       :viranomaisryhma
                                                        :kieli               :fi
                                                        :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                        :salassapitoperuste  "peruste"
                                                        :henkilotiedot       :sisaltaa
                                                        :julkisuusluokka     :salainen
                                                        :kayttajaryhmakuvaus :muokkausoikeus})

    (fact "ei-arkistoida-virheellinen state is not changed"
      (document-with-updated-metadata {:metadata {:tila :ei-arkistoida-virheellinen}} "753-R" "10" application "hakemus") => {:metadata {:tila :ei-arkistoida-virheellinen}}))))

(fact "document metadata is updated correctly"
  (document-metadata-update-test :backing-system)
  (document-metadata-update-test :legacy)
  (document-metadata-update-test :legacy-wrapped)
  (document-metadata-update-test :pate)
  (document-metadata-update-test :pate-wrapped))

(fact "process metadata retention is calculated based on longest document retention"
  (let [metadata          {:tila            :valmis
                           :nakyvyys        :julkinen
                           :sailytysaika    {:arkistointi (keyword "määräajan")
                                             :pituus      10
                                             :perustelu   "foo"}
                           :myyntipalvelu   false
                           :kieli           :fi
                           :henkilotiedot   :sisaltaa
                           :julkisuusluokka :julkinen}
        process-metadata  (assoc metadata :sailytysaika {:arkistointi :ei :perustelu "foobar"})
        attachments       [{:id 1 :metadata (assoc metadata :sailytysaika {:arkistointi :ikuisesti
                                                                           :perustelu   "barfoo"})}
                           {:id 2 :metadata (assoc-in metadata [:sailytysaika :pituus] 5)}]
        attachments2      [{:id 2 :metadata (assoc-in metadata [:sailytysaika :pituus] 5)}]
        process-metadata2 (assoc metadata :sailytysaika {:arkistointi :ikuisesti :perustelu "perustelu-does-not-matter-in-compare"})]

    (calculate-process-metadata process-metadata metadata attachments)
    => {:tila            :valmis
        :nakyvyys        :julkinen
        :sailytysaika    {:arkistointi :ikuisesti
                          :perustelu   "barfoo"}
        :myyntipalvelu   false
        :kieli           :fi
        :henkilotiedot   :sisaltaa
        :julkisuusluokka :julkinen}

    (calculate-process-metadata process-metadata metadata attachments2)
    => {:tila            :valmis
        :nakyvyys        :julkinen
        :sailytysaika    {:arkistointi (keyword "määräajan")
                          :pituus      10
                          :perustelu   "foo"}
        :myyntipalvelu   false
        :kieli           :fi
        :henkilotiedot   :sisaltaa
        :julkisuusluokka :julkinen}

    (calculate-process-metadata process-metadata2 metadata attachments)
    => {:tila            :valmis
        :nakyvyys        :julkinen
        :sailytysaika    {:arkistointi :ikuisesti
                          :perustelu   "perustelu-does-not-matter-in-compare"}
        :myyntipalvelu   false
        :kieli           :fi
        :henkilotiedot   :sisaltaa
        :julkisuusluokka :julkinen}))

(def paatospvm-plus #'tiedonohjaus/paatospvm-plus-years)
(def timestamp-plus #'tiedonohjaus/timestamp-plus-years)
(def security-end-date #'tiedonohjaus/security-end-date)
(def ->date #'tiedonohjaus/->date)

(facts "date handling"

  (let [runeberg    (date/timestamp "5.2.2024")
        dateberg    (Date. (long (date/timestamp runeberg)))
        mayday      (date/timestamp "1.5.2024")
        christmas   (date/timestamp "25.12.2024")
        metadata    {:tila                :luonnos
                     :salassapitoaika     5
                     :nakyvyys            :julkinen
                     :sailytysaika        {:arkistointi "määräajan"
                                           :pituus      10
                                           :perustelu   "foo"}
                     :myyntipalvelu       false
                     :suojaustaso         :ei-luokiteltu
                     :kayttajaryhma       :viranomaisryhma
                     :kieli               :fi
                     :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                     :salassapitoperuste  "peruste"
                     :henkilotiedot       :sisaltaa
                     :julkisuusluokka     :salainen
                     :kayttajaryhmakuvaus :muokkausoikeus}
        forever     (assoc-in metadata [:sailytysaika :arkistointi] "ikuisesti")
        mayforeman  {:submitted        mayday
                     :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}
        ya-finished {:submitted        mayday
                     :finished         christmas
                     :primaryOperation {:name "ya-jatkoaika"}}]
    (facts "->date"
      (->date runeberg) => dateberg
      (->date "5.2.2024") => dateberg
      (->date nil) => nil)

    (facts "paatospvm-plus-years"
      (paatospvm-plus {} 5) => nil
      (paatospvm-plus (assoc-verdict {} :pate runeberg) 5)
      => (->date "5.2.2029")
      (paatospvm-plus (assoc-verdict {} :backing-system mayday) 10)
      => (->date "1.5.2034"))

    (facts "timestamp-plus-years"
      (timestamp-plus nil 8) => nil
      (timestamp-plus mayday 8) => (->date "1.5.2032"))

    (facts "retention-end-date"
      (fact "should return nil when no verdict is given and there is no finished timestamp")
        (retention-end-date metadata {}) => nil
      (fact "should calculate date from verdict timestamp when there is a verdict given")
        (retention-end-date metadata (assoc-verdict {} :pate-wrapped runeberg)) => (->date "5.2.2034")
      (fact "should return nil when arkistointi != määräajan"
        (retention-end-date forever (assoc-verdict {} :pate-wrapped runeberg)) => nil)
      (fact "should calculate date from submitted timestamp when foreman application")
        (retention-end-date metadata (assoc-verdict mayforeman :pate-wrapped runeberg)) => (->date "1.5.2034")
      (fact "should calculate date from 'finished' timestamp when arkistointi = 'määräajan' and no verdict is given"
        (retention-end-date metadata ya-finished) => (->date "25.12.2034")))

    (let [verdicts (vif/all-verdicts (assoc-verdict {} :legacy mayday))]
      (facts "security-end-date"
        (security-end-date metadata {}) => nil
        (security-end-date (dissoc metadata :salassapitoaika) verdicts) => nil
        (security-end-date (assoc metadata :julkisuusluokka "random")
                           verdicts)
        => nil
        (security-end-date metadata verdicts) => (->date "1.5.2029")
        (security-end-date (assoc metadata
                                  :julkisuusluokka "osittain-salassapidettava"
                                  :salassapitoaika 15)
                           verdicts)
        => (->date "1.5.2039")))

    (facts "update-end-dates"
      (update-end-dates metadata {}) => metadata
      (update-end-dates metadata (assoc-verdict {} :pate-wrapped runeberg))
      => (-> metadata
             (assoc-in [:sailytysaika :retention-period-end] (->date "5.2.2034"))
             (assoc :security-period-end (->date "5.2.2029")))
      (update-end-dates (-> metadata
                            (assoc-in [:sailytysaika :retention-period-end] (->date "5.2.2034"))
                            (assoc :security-period-end (->date "5.2.2029")))
                        {})
      => metadata
      (update-end-dates metadata mayforeman)
      => (assoc-in metadata [:sailytysaika :retention-period-end] (->date "1.5.2034"))
      (update-end-dates metadata (assoc-verdict mayforeman :pate runeberg))
      => (-> metadata
             (assoc :security-period-end (->date "5.2.2029"))
             (assoc-in [:sailytysaika :retention-period-end] (->date "1.5.2034"))))))

(facts "linked file target types"
  (linked-file-target-type->tos-type "bim-model-file" "architectural")
  => {:type-group :tietomallit :type-id :arkkitehtimalli_BIM}
  (linked-file-target-type->tos-type "bim-model-file" "structural")
  => {:type-group :tietomallit :type-id :rakennemalli_BIM}
  (linked-file-target-type->tos-type "bim-model-file" "technical")
  => {:type-group :tietomallit :type-id :talotekniikkamalli_BIM}
  (linked-file-target-type->tos-type "bim-model-file" "location")
  => {:type-group :tietomallit :type-id :rakennuspaikkamalli_BIM}
  (linked-file-target-type->tos-type "bim-inspection-report" nil)
  => {:type-group :tietomallien_liitteet :type-id :tarkastusraportti}
  (linked-file-target-type->tos-type "bim-additional-file" nil)
  => {:type-group :tietomallien_liitteet :type-id :muu_tietomallin_liite})

(env/with-feature-value :bim-archiving true
  (let [metadata-without-archiving-fields {:name "model.ifc"
                                           :model-type :architectural
                                           :model-phase :submission-time}
        metadata-after-update1 {:model-phase :submission-time
                                :model-type :architectural
                                :name "model.ifc"
                                :henkilotiedot "ei-sisalla"
                                :julkisuusluokka "julkinen"
                                :kieli "fi"
                                :myyntipalvelu false
                                :nakyvyys "julkinen"
                                :sailytysaika {:arkistointi "ikuisesti" :perustelu "Oma syy"}
                                :tila "luonnos"}
        archiving-metadata-update1 {:julkisuusluokka "julkinen"
                                    :sailytysaika {:arkistointi "ikuisesti" :perustelu "Oma syy"}
                                    :henkilotiedot "ei-sisalla"
                                    :kieli "fi"
                                    :tila "luonnos"
                                    :myyntipalvelu false
                                    :nakyvyys "julkinen"}
        archiving-metadata-update2 {:julkisuusluokka "julkinen"
                                    :sailytysaika {:arkistointi "määräajan"
                                                   :perustelu "AL/17413/07.01.01.03.01/2016"
                                                   :pituus 3}
                                    :henkilotiedot "ei-sisalla"
                                    :kieli "fi"
                                    :tila "luonnos"
                                    :myyntipalvelu false
                                    :nakyvyys "julkinen"}
        metadata-after-update2 {:model-phase :submission-time
                                :model-type :architectural
                                :name "model.ifc"
                                :henkilotiedot "ei-sisalla"
                                :julkisuusluokka "julkinen"
                                :kieli "fi"
                                :myyntipalvelu false
                                :nakyvyys "julkinen"
                                :sailytysaika {:arkistointi "määräajan"
                                               :perustelu "AL/17413/07.01.01.03.01/2016"
                                               :pituus 3}
                                :tila :luonnos}]
    (facts "linked file metadata update"
      (merge-linked-file-metadata-update {} metadata-without-archiving-fields nil)
      => metadata-without-archiving-fields
      (merge-linked-file-metadata-update {} metadata-without-archiving-fields archiving-metadata-update1)
      => metadata-after-update1
      (merge-linked-file-metadata-update {} metadata-after-update1 nil)
      => (assoc metadata-without-archiving-fields
                :tila :luonnos
                :nakyvyys "julkinen")
      (merge-linked-file-metadata-update {} metadata-after-update1 archiving-metadata-update2)
      => metadata-after-update2)))

(facts "process-metadata-update"
  (let [base-metadata {:julkisuusluokka :julkinen
                       :henkilotiedot   :ei-sisalla
                       :sailytysaika    {:arkistointi :ikuisesti :perustelu "test"}
                       :kieli           :fi
                       :myyntipalvelu   false
                       :nakyvyys        :julkinen}]

    (fact "coerces and sanitizes metadata"
      (process-metadata-update
        {:old-metadata  {:tila :luonnos}
         :new-metadata  base-metadata
         :user-roles    #{}
         :schema        tms/AsiakirjaMetaDataMap
         :update-dates? false
         :set-tila?     false}) => (contains {:julkisuusluokka :julkinen}))

    (fact "preserves tila from old metadata when set-tila? true"
      (process-metadata-update
        {:old-metadata  (assoc base-metadata :tila :valmis)
         :new-metadata  base-metadata
         :user-roles    #{}
         :schema        tms/AsiakirjaMetaDataMap
         :update-dates? false
         :set-tila?     true}) => (contains {:tila :valmis}))

    (fact "defaults tila to :luonnos when old has no tila"
      (process-metadata-update
        {:old-metadata  base-metadata
         :new-metadata  base-metadata
         :user-roles    #{}
         :schema        tms/AsiakirjaMetaDataMap
         :update-dates? false
         :set-tila?     true}) => (contains {:tila :luonnos}))

    (fact "preserves specified keys from old metadata"
      (process-metadata-update
        {:old-metadata  (assoc base-metadata :name "model.ifc" :model-type "architectural" :ryhti {:attachmentDocumentKey "abc-123"})
         :new-metadata  (assoc base-metadata :name "other.ifc")
         :user-roles    #{}
         :schema        tms/AsiakirjaMetaDataMap
         :update-dates? false
         :set-tila?     false
         :preserve-keys [:name :model-type :model-phase :ryhti]})
      => (contains {:name "model.ifc" :model-type "architectural" :ryhti {:attachmentDocumentKey "abc-123"}}))

    (fact "reverts unauthorized field modifications for non-archivist"
      ;; sailytysaika.arkistointi requires :archivist role
      (let [old-meta (assoc base-metadata :sailytysaika {:arkistointi :ikuisesti :perustelu "old"})
            new-meta (assoc base-metadata :sailytysaika {:arkistointi :ei :perustelu "new"})]
        (process-metadata-update
          {:old-metadata  old-meta
           :new-metadata  new-meta
           :user-roles    #{} ; no archivist role
           :schema        tms/AsiakirjaMetaDataMap
           :update-dates? false
           :set-tila?     false})
        => (contains {:sailytysaika (contains {:arkistointi :ikuisesti})})))

    (fact "allows field modifications for archivist"
      (let [old-meta (assoc base-metadata :sailytysaika {:arkistointi :ikuisesti :perustelu "old"})
            new-meta (assoc base-metadata :sailytysaika {:arkistointi :ei :perustelu "new"})]
        (process-metadata-update
          {:old-metadata  old-meta
           :new-metadata  new-meta
           :user-roles    #{:archivist}
           :schema        tms/AsiakirjaMetaDataMap
           :update-dates? false
           :set-tila?     false})
        => (contains {:sailytysaika (contains {:arkistointi :ei})})))))

(facts "revert-unauthorized-modifications"
  (fact "allows changes when user has required role"
    (let [old {:sailytysaika {:arkistointi :ikuisesti}}
          new {:sailytysaika {:arkistointi :ei}}]
      (revert-unauthorized-modifications new old #{:archivist})
      => (contains {:sailytysaika {:arkistointi :ei}})))

  (fact "reverts changes when user lacks required role"
    (let [old {:sailytysaika {:arkistointi :ikuisesti}}
          new {:sailytysaika {:arkistointi :ei}}]
      (revert-unauthorized-modifications new old #{})
      => (contains {:sailytysaika {:arkistointi :ikuisesti}}))))

(facts "sanitize-metadata"
  (let [valid-metadata {:julkisuusluokka :julkinen
                        :henkilotiedot   :ei-sisalla
                        :sailytysaika    {:arkistointi :ikuisesti :perustelu "test"}
                        :kieli           :fi}]
    (fact "passes valid metadata through"
      (sanitize-metadata valid-metadata)
      => valid-metadata)

    (fact "throws on invalid metadata"
      (sanitize-metadata {:invalid-field "bad"})
      => (throws))))

(facts "process-case-file-metadata-update"
  (let [base-process-metadata {:julkisuusluokka :julkinen
                               :henkilotiedot   :ei-sisalla
                               :sailytysaika    {:arkistointi :ikuisesti :perustelu "test"}
                               :kieli           :fi}
        app-metadata          {:julkisuusluokka :julkinen
                               :sailytysaika    {:arkistointi :ikuisesti :perustelu "app"}}
        application           {:metadata    app-metadata
                               :attachments []}]

    (fact "processes case file metadata with process-specific calculations"
      (process-case-file-metadata-update base-process-metadata base-process-metadata #{} application)
      => (contains {:julkisuusluokka :julkinen
                    :sailytysaika    (contains {:arkistointi :ikuisesti})}))

    (fact "reverts unauthorized modifications"
      (let [new-meta (assoc base-process-metadata :sailytysaika {:arkistointi :ei :perustelu "new"})]
        (process-case-file-metadata-update base-process-metadata new-meta #{} application)
        => (contains {:sailytysaika (contains {:arkistointi :ikuisesti})})))))
