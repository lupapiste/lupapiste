(ns lupapalvelu.tiedonohjaus-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [lupapalvelu.tiedonohjaus :refer :all]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]))

(facts "about tiedonohjaus utils"
  (let [application {:organization "753-R"
                     :tosFunction  "10 03 00 01"
                     :created      100
                     :applicant    "Testaaja Testi"
                     :id           "1"
                     :authority    {:firstName "Monni" :lastName "Tiskaa"}
                     :processMetadata {:julkisuusluokka "julkinen"
                                       :henkilotiedot "ei-sisalla"
                                       :sailytysaika {:arkistointi :ikuisesti
                                                      :perusteli "Laki"}}
                     :statements   [{:person    {:text "Pelastusviranomainen"
                                                 :name "Pia Nyman"}
                                     :requested 653238400000
                                     :given     663238400000
                                     :status    "ehdollinen"
                                     :text      "Lausunto liitteen\u00e4"
                                     :state     "given"}
                                    {:person    {:text "Rakennussuunnittelu"
                                                 :name "Sampo S\u00e4levaara"}
                                     :requested 643238400000
                                     :given     nil
                                     :status    nil
                                     :state     "requested"}]
                     :neighbors    [{:propertyId "111"
                                     :owner      {:type "luonnollinen"
                                                  :name "Joku naapurin nimi"}
                                     :id         "112"
                                     :status     [{:state   "open"
                                                   :created 923238400000}
                                                  {:state   "mark-done"
                                                   :user    {:firstName "Etu" :lastName "Suku"}
                                                   :created 933238400000}]}]
                     :tasks        [{:data        {}
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
                     :attachments  [{:type     {:type-group "paapiirustus" :type-id "asemapiirros"}
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
                     :history      [{:state "open"
                                     :ts    100
                                     :user  {:firstName "Testi"
                                             :lastName  "Testaaja"}}
                                    {:state "submitted"
                                     :ts    523238400000
                                     :user  {:firstName "Testi"
                                             :lastName  "Testaaja"}}
                                    {:state "complementNeeded"
                                     :ts   1462060800000
                                     :user {:firstName "Heikki"
                                            :lastName "Hepokatti"}}]}]

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
                                                                  :version {:major 0 :minor 1}
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
                                                                  :user     " "}]}
                                                    {:action (i18n/localize :fi "caseFile.complementNeeded")
                                                     :start 1462060800000
                                                     :user "Hepokatti Heikki"
                                                     :documents []}]
      (provided
        (toimenpide-for-state "753-R" "10 03 00 01" "submitted") => {:name "Vireilletulo"}
        (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "Valmisteilla"}
        (toimenpide-for-state "753-R" "10 03 00 01" "complementNeeded") => {}))

    (fact "case file report can be generated as xml"
      (xml-case-file application :fi) => "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><lp:CaseFile xmlns:lp=\"http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1\"><lp:Created>1970-01-01+02:00</lp:Created><lp:NativeId>1</lp:NativeId><lp:Language>fi</lp:Language><lp:Restriction><lp:PublicityClass>Julkinen</lp:PublicityClass><lp:PersonalData>ei sis\u00e4ll\u00e4 henkil\u00f6tietoja</lp:PersonalData></lp:Restriction><lp:Title>K\u00e4sittelyprosessi: 1</lp:Title><lp:RetentionPeriod>999999</lp:RetentionPeriod><lp:Status>valmis</lp:Status><lp:ClassificationScheme><lp:MainFunction><lp:Title>Maank\u00e4ytt\u00f6, Rakentaminen ja Asuminen</lp:Title><lp:FunctionCode>10</lp:FunctionCode><lp:FunctionClassification><lp:Title>Rakentaminen, yll\u00e4pito ja k\u00e4ytt\u00f6</lp:Title><lp:FunctionCode>10 03</lp:FunctionCode><lp:SubFunction><lp:Title>Rakennusvalvonta</lp:Title><lp:FunctionCode>10 03 00</lp:FunctionCode><lp:SubFunction><lp:Title>Rakennuslupamenettely</lp:Title><lp:FunctionCode>10 03 00 01</lp:FunctionCode></lp:SubFunction></lp:SubFunction></lp:FunctionClassification></lp:MainFunction></lp:ClassificationScheme><lp:Function>10 03 00 01</lp:Function><lp:Agent><lp:Role>responsible</lp:Role><lp:Name>Monni Tiskaa</lp:Name></lp:Agent><lp:Action><lp:Created>1970-01-01+02:00</lp:Created><lp:Title>Valmisteilla</lp:Title><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent><lp:Type>Valmisteilla</lp:Type><lp:Record><lp:Created>1970-01-01+02:00</lp:Created><lp:NativeId>1-application</lp:NativeId><lp:Title>Hakemus</lp:Title><lp:Type>hakemus</lp:Type><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record><lp:Record><lp:Created>1970-01-01+02:00</lp:Created><lp:NativeId>2</lp:NativeId><lp:Description>Great attachment</lp:Description><lp:Title>Asemapiirros</lp:Title><lp:Type>paapiirustus.asemapiirros</lp:Type><lp:Version>1.0</lp:Version><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record></lp:Action><lp:Action><lp:Created>1986-08-01+03:00</lp:Created><lp:Title>Vireilletulo</lp:Title><lp:Custom><lp:ActionEvent><lp:Description>Vaatimus lis\u00e4tty: rakennuksen paikan tarkastaminen</lp:Description><lp:Type>request-review</lp:Type><lp:Created>1990-01-25+02:00</lp:Created><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Suku Etu</lp:Name></lp:Agent></lp:ActionEvent><lp:ActionEvent><lp:Description>Lausuntopyynt\u00f6 tehty: Rakennussuunnittelu</lp:Description><lp:Type>request-statement</lp:Type><lp:Created>1990-05-21+03:00</lp:Created></lp:ActionEvent><lp:ActionEvent><lp:Description>Lausuntopyynt\u00f6 tehty: Pelastusviranomainen</lp:Description><lp:Type>request-statement</lp:Type><lp:Created>1990-09-13+03:00</lp:Created></lp:ActionEvent><lp:ActionEvent><lp:Description>Naapurin kuuleminen tehty: Joku naapurin nimi</lp:Description><lp:Type>request-neighbor</lp:Type><lp:Created>1999-04-04+03:00</lp:Created></lp:ActionEvent></lp:Custom><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent><lp:Type>Vireilletulo</lp:Type><lp:Record><lp:Created>1989-10-01+02:00</lp:Created><lp:NativeId>3</lp:NativeId><lp:Title>Pohjapiirros</lp:Title><lp:Type>paapiirustus.pohjapiirros</lp:Type><lp:Version>0.1</lp:Version><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record><lp:Record><lp:Created>1991-01-07+02:00</lp:Created><lp:NativeId>2</lp:NativeId><lp:Description>Great attachment</lp:Description><lp:Title>Asemapiirros</lp:Title><lp:Type>paapiirustus.asemapiirros</lp:Type><lp:Version>2.2</lp:Version><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Testaaja Testi</lp:Name></lp:Agent></lp:Record></lp:Action><lp:Action><lp:Created>2016-05-01+03:00</lp:Created><lp:Title>Palautettu t\u00e4ydennett\u00e4v\u00e4ksi</lp:Title><lp:Agent><lp:Role>registrar</lp:Role><lp:Name>Hepokatti Heikki</lp:Name></lp:Agent><lp:Type>Palautettu t\u00e4ydennett\u00e4v\u00e4ksi</lp:Type></lp:Action></lp:CaseFile>"

      (provided
        (toimenpide-for-state "753-R" "10 03 00 01" "submitted") => {:name "Vireilletulo"}
        (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "Valmisteilla"}
        (toimenpide-for-state "753-R" "10 03 00 01" "complementNeeded") => {}
        (get-from-toj-api "753-R" false "10 03 00 01" "/classification") => "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a:ClassificationScheme xmlns:a=\"http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1\"><a:MainFunction><a:FunctionCode>10</a:FunctionCode><a:Title>Maank\u00e4ytt\u00f6, Rakentaminen ja Asuminen</a:Title><a:FunctionClassification><a:FunctionCode>10 03</a:FunctionCode><a:Title>Rakentaminen, yll\u00e4pito ja k\u00e4ytt\u00f6</a:Title><a:SubFunction><a:FunctionCode>10 03 00</a:FunctionCode><a:Title>Rakennusvalvonta</a:Title><a:SubFunction><a:FunctionCode>10 03 00 01</a:FunctionCode><a:Title>Rakennuslupamenettely</a:Title></a:SubFunction></a:SubFunction></a:FunctionClassification></a:MainFunction></a:ClassificationScheme>")))


  (fact "application and attachment state (tila) is changed correctly"
    (let [metadata {:tila                :luonnos
                    :salassapitoaika     5
                    :nakyvyys            :julkinen
                    :sailytysaika        {:arkistointi (keyword "m\u00E4\u00E4r\u00E4ajan")
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
          application {:id           1000
                       :organization "753-R"
                       :metadata     metadata
                       :processMetadata process-metadata
                       :attachments  [{:id 1 :metadata metadata}
                                      {:id 2 :metadata metadata}]
                       :verdicts     [{:paatokset [{:poytakirjat [{:paatospvm 1456696800000}]}]}]}
          command (action/application->command application)]
      (mark-app-and-attachments-final! 1000 12345678) => nil
      (provided
        (domain/get-application-no-access-checking 1000) => application

        (action/update-application command {$set {:modified        12345678
                                                  :metadata        {:tila                :valmis
                                                                    :salassapitoaika     5
                                                                    :nakyvyys            :julkinen
                                                                    :sailytysaika        {:arkistointi          (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                          :pituus               10
                                                                                          :perustelu            "foo"
                                                                                          :retention-period-end #inst "2026-02-28T22:00:00.000-00:00"}
                                                                    :myyntipalvelu       false
                                                                    :suojaustaso         :ei-luokiteltu
                                                                    :security-period-end #inst "2021-02-28T22:00:00.000-00:00"
                                                                    :kayttajaryhma       :viranomaisryhma
                                                                    :kieli               :fi
                                                                    :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                    :salassapitoperuste  "peruste"
                                                                    :henkilotiedot       :sisaltaa
                                                                    :julkisuusluokka     :salainen
                                                                    :kayttajaryhmakuvaus :muokkausoikeus}
                                                  :processMetadata {:julkisuusluokka     :salainen
                                                                    :salassapitoaika     5
                                                                    :security-period-end #inst "2021-02-28T22:00:00.000-00:00"}}}) => nil
        (action/update-application command
                                   {:attachments.id 1}
                                   {$set {:modified               12345678
                                          :attachments.$.metadata {:tila                :valmis
                                                                   :salassapitoaika     5
                                                                   :nakyvyys            :julkinen
                                                                   :sailytysaika        {:arkistointi          (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                         :pituus               10
                                                                                         :perustelu            "foo"
                                                                                         :retention-period-end #inst "2026-02-28T22:00:00.000-00:00"}
                                                                   :myyntipalvelu       false
                                                                   :suojaustaso         :ei-luokiteltu
                                                                   :security-period-end #inst "2021-02-28T22:00:00.000-00:00"
                                                                   :kayttajaryhma       :viranomaisryhma
                                                                   :kieli               :fi
                                                                   :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                   :salassapitoperuste  "peruste"
                                                                   :henkilotiedot       :sisaltaa
                                                                   :julkisuusluokka     :salainen
                                                                   :kayttajaryhmakuvaus :muokkausoikeus}}}) => nil
        (action/update-application command
                                   {:attachments.id 2}
                                   {$set {:modified               12345678
                                          :attachments.$.metadata {:tila                :valmis
                                                                   :salassapitoaika     5
                                                                   :nakyvyys            :julkinen
                                                                   :sailytysaika        {:arkistointi          (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                         :pituus               10
                                                                                         :perustelu            "foo"
                                                                                         :retention-period-end #inst "2026-02-28T22:00:00.000-00:00"}
                                                                   :myyntipalvelu       false
                                                                   :suojaustaso         :ei-luokiteltu
                                                                   :security-period-end #inst "2021-02-28T22:00:00.000-00:00"
                                                                   :kayttajaryhma       :viranomaisryhma
                                                                   :kieli               :fi
                                                                   :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                   :salassapitoperuste  "peruste"
                                                                   :henkilotiedot       :sisaltaa
                                                                   :julkisuusluokka     :salainen
                                                                   :kayttajaryhmakuvaus :muokkausoikeus}}}) => nil)))

  (fact "attachment state (tila) is changed correctly"
    (let [application {:id           1000
                       :organization "753-R"
                       :metadata     {:tila "luonnos"}
                       :verdicts     [{:paatokset [{:poytakirjat [{:paatospvm 1456696800000}]}]}]
                       :attachments  [{:id 1 :metadata {:tila "luonnos"}}
                                      {:id 2 :metadata {:tila                :luonnos
                                                        :salassapitoaika     5
                                                        :nakyvyys            :julkinen
                                                        :sailytysaika        {:arkistointi (keyword "m\u00E4\u00E4r\u00E4ajan")
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
          now 12345678
          attachment-id 2]
      (mark-attachment-final! application now attachment-id) => nil
      (provided
        (action/update-application (action/application->command application)
                                   {:attachments.id attachment-id}
                                   {$set {:modified               now
                                          :attachments.$.metadata {:tila                :valmis
                                                                   :salassapitoaika     5
                                                                   :nakyvyys            :julkinen
                                                                   :sailytysaika        {:arkistointi          (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                         :pituus               10
                                                                                         :perustelu            "foo"
                                                                                         :retention-period-end #inst "2026-02-28T22:00:00.000-00:00"}
                                                                   :myyntipalvelu       false
                                                                   :suojaustaso         :ei-luokiteltu
                                                                   :security-period-end #inst "2021-02-28T22:00:00.000-00:00"
                                                                   :kayttajaryhma       :viranomaisryhma
                                                                   :kieli               :fi
                                                                   :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                   :salassapitoperuste  "peruste"
                                                                   :henkilotiedot       :sisaltaa
                                                                   :julkisuusluokka     :salainen
                                                                   :kayttajaryhmakuvaus :muokkausoikeus}}}) => nil)))

  (fact "document metadata is updated correctly"
    (let [application {:id           1000
                       :organization "753-R"
                       :verdicts     [{:paatokset [{:poytakirjat [{:paatospvm 1456696800000}]}]}]
                       :metadata     {:tila     "valmis"
                                      :nakyvyys "julkinen"}}]
      (document-with-updated-metadata application "753-R" "10" application "hakemus") => {:id           1000
                                                                                          :organization "753-R"
                                                                                          :verdicts     [{:paatokset [{:poytakirjat [{:paatospvm 1456696800000}]}]}]
                                                                                          :metadata     {:tila                :valmis
                                                                                                         :salassapitoaika     5
                                                                                                         :nakyvyys            :julkinen
                                                                                                         :sailytysaika        {:arkistointi          (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                                                               :pituus               10
                                                                                                                               :perustelu            "foo"
                                                                                                                               :retention-period-end #inst "2026-02-28T22:00:00.000-00:00"}
                                                                                                         :myyntipalvelu       false
                                                                                                         :suojaustaso         :ei-luokiteltu
                                                                                                         :security-period-end #inst "2021-02-28T22:00:00.000-00:00"
                                                                                                         :kayttajaryhma       :viranomaisryhma
                                                                                                         :kieli               :fi
                                                                                                         :turvallisuusluokka  :ei-turvallisuusluokkaluokiteltu
                                                                                                         :salassapitoperuste  "peruste"
                                                                                                         :henkilotiedot       :sisaltaa
                                                                                                         :julkisuusluokka     :salainen
                                                                                                         :kayttajaryhmakuvaus :muokkausoikeus}}
      (provided
        (metadata-for-document "753-R" "10" "hakemus") => {:tila                :luonnos
                                                           :salassapitoaika     5
                                                           :nakyvyys            :julkinen
                                                           :sailytysaika        {:arkistointi (keyword "m\u00E4\u00E4r\u00E4ajan")
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
                                                           :kayttajaryhmakuvaus :muokkausoikeus})))

  (fact "process metadata retention is calculated based on longest document retention"
    (let [metadata {:tila            :valmis
                    :nakyvyys        :julkinen
                    :sailytysaika    {:arkistointi (keyword "m\u00E4\u00E4r\u00E4ajan")
                                      :pituus      10
                                      :perustelu   "foo"}
                    :myyntipalvelu   false
                    :kieli           :fi
                    :henkilotiedot   :sisaltaa
                    :julkisuusluokka :julkinen}
          process-metadata (assoc metadata :sailytysaika {:arkistointi :ei :perustelu "foobar"})
          attachments [{:id 1 :metadata (assoc metadata :sailytysaika {:arkistointi :ikuisesti
                                                                       :perustelu   "barfoo"})}
                       {:id 2 :metadata (assoc-in metadata [:sailytysaika :pituus] 5)}]
          attachments2 [{:id 2 :metadata (assoc-in metadata [:sailytysaika :pituus] 5)}]
          process-metadata2 (assoc metadata :sailytysaika {:arkistointi :ikuisesti :perustelu "perustelu-does-not-matter-in-compare"})]

      (calculate-process-metadata process-metadata metadata attachments) => {:tila            :valmis
                                                                             :nakyvyys        :julkinen
                                                                             :sailytysaika    {:arkistointi :ikuisesti
                                                                                               :perustelu   "barfoo"}
                                                                             :myyntipalvelu   false
                                                                             :kieli           :fi
                                                                             :henkilotiedot   :sisaltaa
                                                                             :julkisuusluokka :julkinen}

      (calculate-process-metadata process-metadata metadata attachments2) => {:tila            :valmis
                                                                              :nakyvyys        :julkinen
                                                                              :sailytysaika    {:arkistointi (keyword "m\u00E4\u00E4r\u00E4ajan")
                                                                                                :pituus      10
                                                                                                :perustelu   "foo"}
                                                                              :myyntipalvelu   false
                                                                              :kieli           :fi
                                                                              :henkilotiedot   :sisaltaa
                                                                              :julkisuusluokka :julkinen}

      (calculate-process-metadata process-metadata2 metadata attachments) => {:tila            :valmis
                                                                              :nakyvyys        :julkinen
                                                                              :sailytysaika    {:arkistointi :ikuisesti
                                                                                                :perustelu   "perustelu-does-not-matter-in-compare"}
                                                                              :myyntipalvelu   false
                                                                              :kieli           :fi
                                                                              :henkilotiedot   :sisaltaa
                                                                              :julkisuusluokka :julkinen})))
