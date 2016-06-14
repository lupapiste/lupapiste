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
                     :statements   [{:person    {:text "Pelastusviranomainen"
                                                 :name "Pia Nyman"}
                                     :requested 302
                                     :given     500
                                     :status    "ehdollinen"
                                     :text      "Lausunto liitteen\u00e4"
                                     :state     "given"}
                                    {:person    {:text "Rakennussuunnittelu"
                                                 :name "Sampo S\u00e4levaara"}
                                     :requested 301
                                     :given     nil
                                     :status    nil
                                     :state     "requested"}]
                     :neighbors    [{:propertyId "111"
                                     :owner      {:type "luonnollinen"
                                                  :name "Joku naapurin nimi"}
                                     :id         "112"
                                     :status     [{:state   "open"
                                                   :created 600}
                                                  {:state   "mark-done"
                                                   :user    {:firstName "Etu" :lastName "Suku"}
                                                   :created 500}]}]
                     :tasks        [{:data        {}
                                     :state       "requires_user_action"
                                     :taskname    "rakennuksen paikan tarkastaminen"
                                     :schema-info {:name    "task-katselmus"
                                                   :version 1}
                                     :closed      nil
                                     :created     300
                                     :duedate     nil
                                     :assignee    {:lastName  "Suku"
                                                   :firstName "Etu"
                                                   :id        1111}
                                     :source      nil
                                     :id          "2222"}
                                    ]
                     :attachments  [{:type     {:foo :bar}
                                     :versions [{:version 1
                                                 :created 200
                                                 :user    {:firstName "Testi"
                                                           :lastName  "Testaaja"}}
                                                {:version 2
                                                 :created 500
                                                 :user    {:firstName "Testi"
                                                           :lastName  "Testaaja"}}]
                                     :id       "2"
                                     :contents "Great attachment"}
                                    {:type     {:foo :qaz}
                                     :id       "3"
                                     :versions [{:version 1
                                                 :created 300
                                                 :user    {:firstName "Testi"
                                                           :lastName  "Testaaja"}}]}]
                     :history      [{:state "draft"
                                     :ts    100
                                     :user  {:firstName "Testi"
                                             :lastName  "Testaaja"}}
                                    {:state "open"
                                     :ts    250
                                     :user  {:firstName "Testi"
                                             :lastName  "Testaaja"}}
                                    {:state "complementNeeded"
                                     :ts 4000
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
                                                                 {:type     {:foo :bar}
                                                                  :category :document
                                                                  :version  1
                                                                  :ts       200
                                                                  :user     "Testaaja Testi"
                                                                  :contents "Great attachment"
                                                                  :id       "2"}]}
                                                    {:action    "K\u00e4sittelyss\u00e4"
                                                     :start     250
                                                     :user      "Testaaja Testi"
                                                     :documents [{:type     {:foo :qaz}
                                                                  :category :document
                                                                  :version  1
                                                                  :ts       300
                                                                  :user     "Testaaja Testi"
                                                                  :contents nil
                                                                  :id       "3"}
                                                                 {:category :request-review, :ts 300, :text "rakennuksen paikan tarkastaminen", :user "Suku Etu"}
                                                                 {:category :request-statement, :ts 301, :text "Rakennussuunnittelu", :user ""}
                                                                 {:category :request-statement, :ts 302, :text "Pelastusviranomainen", :user ""}
                                                                 {:type     {:foo :bar}
                                                                  :category :document
                                                                  :version  2
                                                                  :ts       500
                                                                  :user     "Testaaja Testi"
                                                                  :contents "Great attachment"
                                                                  :id       "2"}
                                                                 {:text     "Joku naapurin nimi"
                                                                  :category :request-neighbor
                                                                  :ts       600
                                                                  :user     " "}]}
                                                    {:action (i18n/localize :fi "caseFile.complementNeeded")
                                                     :start 4000
                                                     :user "Hepokatti Heikki"
                                                     :documents []}]
      (provided
        (toimenpide-for-state "753-R" "10 03 00 01" "draft") => {:name "Valmisteilla"}
        (toimenpide-for-state "753-R" "10 03 00 01" "open") => {:name "K\u00e4sittelyss\u00e4"}
        (toimenpide-for-state "753-R" "10 03 00 01" "complementNeeded") => {})))


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
                                                                              :julkisuusluokka :julkinen}))

  )
