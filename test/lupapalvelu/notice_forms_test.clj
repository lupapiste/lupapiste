(ns lupapalvelu.notice-forms-test
  (:require [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as krysp]
            [lupapalvelu.document.rakennuslupa-canonical :refer [aloitusilmoitus-canonical]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.notice-forms :refer :all]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate-itest-util :refer [err]]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.xml.disk-writer :as writer]
            [midje.sweet :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]))

(facts "foreman-check"
  (fact "No application"
    (foreman-check {}) => nil)
  (fact "No foreman applications"
    (foreman-check {:application "app"}) => (err :error.no-suitable-foreman)
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app") => []))
  (fact "No responsible foreman"
    (foreman-check {:application "app"}) => (err :error.no-suitable-foreman)
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app")
              => [{:state     "submitted"
                   :documents [{:data {:kuntaRoolikoodi {:value "hello"}}}]}
                  {:documents [{:data {:kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"}}}]}
                  {:documents [{:data {:kuntaRoolikoodi {:value "world"}}}]}]))
  (fact "Responsible foreman"
    (foreman-check {:application "app"}) => nil
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app")
              => [{:documents [{:data {:kuntaRoolikoodi {:value "hello"}}}]}
                  {:state     "foremanVerdictGiven"
                   :documents [{:data {:kuntaRoolikoodi {:value "vastaava ty\u00f6njohtaja"}}}]}
                  {:documents [{:data {:kuntaRoolikoodi {:value "world"}}}]}])))

(facts "foreman-list"
  (fact "One foreman: acknowledged"
    (foreman-list "app") => [{:status     "ok"
                              :stateLoc   "acknowledged"
                              :foremanLoc :osapuoli.tyonjohtaja.kuntaRoolikoodi.foo
                              :fullname   "Hello World"}]
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app")
              => [{:state                 "acknowledged"
                   :documents             [{:data {:henkilotiedot   {:etunimi  {:value "Hello"}
                                                                     :sukunimi {:value "World"}}
                                                   :kuntaRoolikoodi {:value "foo"}}}]
                   :latest-verdict-status {:status "ok"}}]))
  (fact "One foreman: draft, party doc not filled"
    (foreman-list "app") => [{:status     "new"
                              :stateLoc   "draft"
                              :foremanLoc :osapuoli.tyonjohtaja.kuntaRoolikoodi.foo
                              :fullname   "Hello World"}]
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app")
              => [{:state                 "draft"
                   :auth                  [{:role "foobar"}
                                           {:role      "foreman"
                                            :firstName "Hello"
                                            :lastName  "World"}]
                   :documents             [{:data {:henkilotiedot   {:etunimi  {:value ""}
                                                                     :sukunimi {:value " "}}
                                                   :kuntaRoolikoodi {:value "foo"}}}]
                   :latest-verdict-status {:status "new"}}]))
  (fact "One foreman: verdict given"
    (foreman-list "app") => [{:status     "rejected"
                              :stateLoc   :pate-r.verdict-code.evatty
                              :foremanLoc :osapuoli.tyonjohtaja.kuntaRoolikoodi.foo
                              :fullname   "Hello"}]
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app")
              => [{:state                 "draft"
                   :auth                  [{:role "foobar"}
                                           {:role      "foreman"
                                            :firstName "Hello"
                                            :lastName  "World"}]
                   :documents             [{:data {:henkilotiedot   {:etunimi  {:value "Hello"}
                                                                     :sukunimi {:value " "}}
                                                   :kuntaRoolikoodi {:value "foo"}}}]
                   :latest-verdict-status {:status    "rejected"
                                           :statusLoc :pate-r.verdict-code.evatty}}]))
  (fact "Two foremen"
    (foreman-list "app") => [{:status     "rejected"
                              :stateLoc   :pate-r.verdict-code.evatty
                              :foremanLoc :osapuoli.tyonjohtaja.kuntaRoolikoodi.foo
                              :fullname   "Hello"}
                             {:status     "new"
                              :stateLoc   "submitted"
                              :foremanLoc :osapuoli.tyonjohtaja.kuntaRoolikoodi.bar
                              :fullname   "World"}]
    (provided (lupapalvelu.foreman/get-linked-foreman-applications "app")
              => [{:state                 "draft"
                   :auth                  [{:role "foobar"}
                                           {:role      "foreman"
                                            :firstName "Hello"
                                            :lastName  "World"}]
                   :documents             [{:data {:henkilotiedot   {:etunimi  {:value "Hello"}
                                                                     :sukunimi {:value " "}}
                                                   :kuntaRoolikoodi {:value "foo"}}}]
                   :latest-verdict-status {:status    "rejected"
                                           :statusLoc :pate-r.verdict-code.evatty}}
                  {:state                 "submitted"
                   :auth                  [{:role "foobar"}
                                           {:role      "foreman"
                                            :firstName "  "
                                            :lastName  "World"}]
                   :documents             [{:data {:henkilotiedot   {:sukunimi {:value " "}}
                                                   :kuntaRoolikoodi {:value "bar"}}}]
                   :latest-verdict-status {:status "new"}}])))

(facts "buildings"
  (let [op1         {:id   "op1"
                     :name "pientalo"}
        op2         {:id   "op2"
                     :name "vapaa-ajan-asuinrakennus"}
        op3         {:id   "op3"
                     :name "varasto-tms"}
        op4         {:id   "op4"
                     :name "rakennustietojen-korjaus"}
        op5         {:id   "op5"
                     :name "puun-kaataminen"}
        house1      {:buildingId  "one"
                     :description "First House"
                     :nationalId  "111"
                     :operationId "op1"}
        house2      {:buildingId  "two"
                     :description "Second House"
                     :nationalId  "222"
                     :operationId "op2"}
        house3      {:buildingId  "three"
                     :description "Third House"
                     :nationalId  "333"
                     :operationId "op3"}
        house4      {:buildingId  "four"
                     :description "Fourth House"
                     :nationalId  "444"
                     :operationId "op4"}
        house5      {:buildingId  "five"
                     :description "Fifth House"
                     :nationalId  "555"
                     :operationId "op5"}
        application {:primaryOperation    op1
                     :secondaryOperations [op2 op3 op4 op5]}
        ops         {:op1 op1
                     :op2 op2
                     :op3 op3
                     :op4 op4
                     :op5 op5}
        housed      (fn [{op-id :operationId :as house}]
                      (-> house
                          (assoc :opName (get-in ops [(keyword op-id) :name]))
                          (dissoc :operationId)))]
    (fact "housed"
      (housed house1) => (-> house1
                             (assoc :opName "pientalo")
                             (dissoc :operationId))
      (housed house2) => (-> house2
                             (assoc :opName "vapaa-ajan-asuinrakennus")
                             (dissoc :operationId))
      (housed house3) => (-> house3
                             (assoc :opName "varasto-tms")
                             (dissoc :operationId)))
    (fact "Initial buildings for construction and terrain"
      (buildings (assoc application :buildings [(assoc house1 :foo "bar") house2  house3])
                 "construction")
      => (map housed [(dissoc house1 :foo) house2 house3])
      (buildings (assoc application :buildings [house1 house2  house3])
                 "terrain")
      => (map housed [house1 house2 house3]))
    (fact "No initial location buildings (no approved terrains)"
      (buildings (assoc application :buildings [house1 house2  house3])
                 "location")
      => empty?)
    (facts "Open forms"
      (fact "... with one building"
        (buildings (assoc application
                          :notice-forms [{:type        "construction"
                                          :buildingIds ["two"]
                                          :history     [{:state "open"}]}]
                          :buildings    [house1 house2 house3])
                   "construction")
        => (map housed [house1 house3]))
      (fact "... with two buildings"
        (buildings (assoc application
                          :notice-forms [{:type        "construction"
                                          :buildingIds ["one" "two"]
                                          :history     [{:state "open"}]}]
                          :buildings    [house1 house2 house3])
                   "construction")
        => [(housed house3)]

        (fact "Two forms"
          (buildings (assoc application
                            :notice-forms [{:type        "construction"
                                            :buildingIds ["one"]
                                            :history     [{:state "open"}]}
                                           {:type        "construction"
                                            :buildingIds ["two"]
                                            :history     [{:state "open"}]}]
                            :buildings    [house1 house2 house3])
                     "construction")
          => [(housed house3)]))
      (fact "Forms in different states"
        (buildings (assoc application
                          :notice-forms [{:type        "construction"
                                          :buildingIds ["one"]
                                          :history     [{:state "open"}
                                                        {:state "ok"}]}
                                         {:type        "construction"
                                          :buildingIds ["two"]
                                          :history     [{:state "open"}]}
                                         {:type        "construction"
                                          :buildingIds ["two"]
                                          :history     [{:state "open"}
                                                        {:state "rejected"}]}
                                         {:type        "terrain"
                                          :buildingIds ["one"]
                                          :history     [{:state "open"}]}
                                         {:type        "construction"
                                          :buildingIds ["three"]
                                          :history     [{:state "open"}
                                                        {:state "rejected"}]}]
                          :buildings    [house1 house2 house3])
                   "construction")
        => (map housed [house1 house3])))
    (facts "Location forms"
      (fact "One approved terrain building"
        (buildings (assoc application
                          :notice-forms [{:type        "terrain"
                                          :buildingIds ["one"]
                                          :history     [{:state "ok"}]}]
                          :buildings    [house1 house2 house3])
                   "location")
        => [(housed house1)])
      (fact "Two approved terrain buildings, one open location"
        (buildings (assoc application
                          :notice-forms [{:type        "terrain"
                                          :buildingIds ["one" "two"]
                                          :history     [{:state "ok"}]}
                                         {:type        "location"
                                          :buildingIds ["two"]
                                          :history     [{:state "open"}]}]
                          :buildings    [house1 house2 house3])
                   "location")
        => [(housed house1)]
        (buildings (assoc application
                          :notice-forms [{:type        "terrain"
                                          :buildingIds ["one"]
                                          :history     [{:state "ok"}]}
                                         {:type        "terrain"
                                          :buildingIds ["two"]
                                          :history     [{:state "ok"}]}
                                         {:type        "location"
                                          :buildingIds ["two"]
                                          :history     [{:state "open"}]}]
                          :buildings    [house1 house2 house3])
                   "location")
        => [(housed house1)]))
    (fact "Unsupported operations"
      (buildings (assoc application
                        :buildings [house4 house5]) "construction")
      => [(housed house5)])
    (fact "Unsupported operations"
      (buildings (assoc application
                        :buildings [house4 house5]) "terrain")
      => empty)
    (fact "form-buildings"
      (form-buildings "fi" (assoc application :buildings [house1 house2 house3])
                      {:buildingIds (map :buildingId [house1 house2 house3 {:buildingId "bad"}])})
      => [(str (i18n/localize "fi" "operations.pientalo") " - First House - 111")
          (str (i18n/localize "fi" "operations.vapaa-ajan-asuinrakennus") " - Second House - 222")
          (str (i18n/localize "fi" "operations.varasto-tms") " - Third House - 333")
          "Rakennusta ei en\u00e4\u00e4 l\u00f6ydy."]
      (form-buildings "fi" (assoc application :buildings [(dissoc house1 :description)
                                                          (dissoc house2 :nationalId)
                                                          (dissoc house3 :description :nationalId)])
                      {:buildingIds (map :buildingId [house1 house2 house3])})
      => [(str (i18n/localize "fi" "operations.pientalo") " - 111")
          (str (i18n/localize "fi" "operations.vapaa-ajan-asuinrakennus") " - Second House")
          (str (i18n/localize "fi" "operations.varasto-tms"))])))

(fact "authority-in-organization?"
  (let [sonja {:id       "777777777777777777000023"
               :orgAuthz {:753-R         ["authority" "approver"]
                          :753-YA        ["approver"]
                          :998-R-TESTI-2 ["authority" "approver"]}
               :enabled  true
               :role     "authority"}
        pena  {:id      "777777777777777777000020"
               :enabled true
               :role    "applicant"}]
    (authority-in-organization? sonja "186-R") => false
    (authority-in-organization? sonja "753-YA") => false
    (authority-in-organization? pena "186-R") => false
    (authority-in-organization? (assoc sonja :role "badrole")
                                "753-R") => false
    (authority-in-organization? sonja nil) => false
    (authority-in-organization? sonja "") => false
    (authority-in-organization? nil "186-R") => false
    (authority-in-organization? nil nil) => false
    (authority-in-organization? nil "") => false
    (authority-in-organization? sonja "753-R") => true
    (authority-in-organization? (assoc sonja :enabled false)
                                "753-R") => false))

(def test-organization {:id                               "753-R"
                        :krysp                            {:R {:url     "http://test"
                                                               :ftpUser "dev_sipoo"
                                                               :version "2.2.2"}}
                        :use-attachment-links-integration true
                        :notice-forms                     {:construction {:integration true}}})
(facts "KuntaGML"
  (let [application         {:id           "LP-notice-form-test"
                             :state        "verdictGiven"
                             :address      "Rue de Notice Form"
                             :municipality "753"
                             :organization "753-R"
                             :permitType   "R"
                             :submitted    (date/timestamp "29.3.2019")
                             :buildings    [{:description    "Hello"
                                             :localShortId   "001"
                                             :buildingId     "house1"
                                             :index          "1"
                                             :usage          "021 rivitalot"
                                             :location-wgs84 nil
                                             :nationalId     "199887766E"
                                             :area           "281"
                                             :propertyId     "75341600550007"
                                             :location       nil
                                             :operationId    "op1"}
                                            {:description    "World"
                                             :localShortId   "002"
                                             :buildingId     "house2"
                                             :index          "2"
                                             :usage          "021 rivitalot"
                                             :location-wgs84 nil
                                             :nationalId     "188997766A"
                                             :area           "100"
                                             :propertyId     "75341600550007"
                                             :location       nil
                                             :operationId    "op2"}]
                             :attachments  [{:target        {:type "foobar"
                                                             :id   "form-id"}
                                             :id            "bad1"
                                             :contents      "Bad"
                                             :modified      12345
                                             :type          {:type-group "muut"
                                                             :type-id    "muu"}
                                             :latestVersion {:filename "bad.pdf"
                                                             :fileId   "bad-file"
                                                             :version  {:major "0"
                                                                        :minor "1"}}}
                                            {:target        {:type "notice-form"
                                                             :id   "form-id"}
                                             :id            "good1"
                                             :contents      "Good"
                                             :modified      12345
                                             :type          {:type-group "muut"
                                                             :type-id    "muu"}
                                             :latestVersion {:filename "good.pdf"
                                                             :fileId   "good-file"
                                                             :version  {:major "0"
                                                                        :minor "1"}}}]}
        sonja               {:city      "Sipoo"
                             :email     "sonja.sibbo@sipoo.fi"
                             :firstName "Sonja"
                             :lastName  "Sibbo"
                             :phone     "03121991"
                             :role      "authority"
                             :street    "Katuosoite 1 a 1"
                             :zip       "33456"}
        form                {:text        "This is notice"
                             :type        "construction"
                             :id          "form-id"
                             :buildingIds ["house2"]
                             :history     [{:state     "open"
                                            :timestamp (date/timestamp "1.4.2019")
                                            :user      {:firstName "Pena"
                                                        :lastName  "Panaani"}}
                                           {:state     "ok"
                                            :timestamp (date/timestamp "2.4.2019")
                                            :user      {:firstName "Sonja"
                                                        :lastName  "Sibbo"}}]}
        command             {:application  (assoc application
                                                  :notice-forms [form])
                             :lang         "fi"
                             :user         sonja
                             :organization (delay test-organization)}
        output-dir          (:directory (sftp-ctx/default-context command))
        delete-old-messages #(doseq [file (writer/krysp-xml-files (:id application) output-dir)]
                               (io/delete-file file))
        message-count       #(count (writer/krysp-xml-files (:id application) output-dir))]
    (fact "aloitusilmoitus-canonical"
      (aloitusilmoitus-canonical command form)
      => {:Rakennusvalvonta
          {:rakennusvalvontaAsiatieto
           {:RakennusvalvontaAsia
            {:kasittelynTilatieto [{:Tilamuutos {:kasittelija ""
                                                 :pvm         (date/xml-date "2019-03-29")
                                                 :tila        "vireill\u00e4"}}]
             :katselmustieto      {:Katselmus
                                   {:huomautukset                    {:huomautus {:kuvaus "This is notice"}}
                                    :katselmuksenLaji                "muu katselmus"
                                    :vaadittuLupaehtonaKytkin        false
                                    :katselmuksenRakennustieto       [{:jarjestysnumero        "2"
                                                                       :kiinttun               "75341600550007"
                                                                       :rakennuksenSelite      "World"
                                                                       :valtakunnallinenNumero "188997766A"}]
                                    :muuTunnustieto                  [{:MuuTunnus {:sovellus "Lupapiste"
                                                                                   :tunnus   "form-id"}}]
                                    :osittainen                      "lopullinen"
                                    :pitaja                          "Pena Panaani"
                                    :pitoPvm                         (date/xml-date "2019-04-01")
                                    :tarkastuksenTaiKatselmuksenNimi "Aloitusilmoitus"}}
             :kayttotapaus        "Aloitusilmoitus"
             :luvanTunnisteTiedot {:LupaTunnus {:muuTunnustieto {:MuuTunnus {:sovellus "Lupapiste"
                                                                             :tunnus   "LP-notice-form-test"}}
                                                :saapumisPvm    (date/xml-date "2019-03-29")}}
             :osapuolettieto      {:Osapuolet
                                   {:osapuolitieto
                                    [{:Osapuoli
                                      {:henkilo
                                       {:nimi             {:etunimi  "Sonja"
                                                           :sukunimi "Sibbo"}
                                        :osoite           {:osoitenimi           {:teksti "Katuosoite 1 a 1"}
                                                           :postinumero          "33456"
                                                           :postitoimipaikannimi "Sipoo"}
                                        :puhelin          "03121991"
                                        :sahkopostiosoite "sonja.sibbo@sipoo.fi"}
                                       :kuntaRooliKoodi "Ilmoituksen tekij\u00e4"}}]}}}}
           :toimituksenTiedot {:aineistonnimi      "Rue de Notice Form"
                               :aineistotoimittaja "lupapiste@solita.fi"
                               :kielitieto         "fi"
                               :kuntakoodi         "753"
                               :tila               "keskener\u00e4inen"
                               :toimitusPvm        (date/xml-date (now))}}})
    (delete-old-messages)
    (fact "save-aloitusilmoitus-as-krysp"
      (krysp/save-aloitusilmoitus-as-krysp command form)
      (let [files (writer/krysp-xml-files (:id application) output-dir)]
        (fact "One file created"
          (count files) => 1)
        (fact "... with proper name"
          (re-find (re-pattern (str (:id application) "_.*_aloitusilmoitus.xml"))
                   (first files)) => truthy)
        (let [xml         (->> (first files)
                               clojure.xml/parse
                               sade.common-reader/strip-xml-namespaces)
              attachments (sade.xml/select xml :linkkiliitteeseen)]
          (fact "There is only one attachment and it is goooooood"
            (count attachments) => 1
            (re-find #"=good1$" (-> attachments first :content first))
            => truthy))))
    (facts "construction-form-kuntagml"
      (delete-old-messages)
      (fact "State not OK"
        (construction-form-kuntagml command "form-id" "rejected")
        (message-count) => zero?)
      (fact "Integration not active"
        (construction-form-kuntagml (assoc command
                                           :organization
                                           (delay (assoc-in test-organization
                                                            [:notice-forms :construction :integration]
                                                            false)))
                                    "form-id" "ok")
        (message-count) => zero?)
      (fact "Form not construction form"
        (construction-form-kuntagml command "form-id" "ok") => nil
        (provided (lupapalvelu.mongo/by-id :applications (:id application) [:notice-forms])
                  => {:notice-forms [(assoc form :type "terrain")]})
        (message-count) => zero?)
      (fact "Create message successfully"
        (construction-form-kuntagml command "form-id" "ok") => ["good-file"]
        (provided (lupapalvelu.mongo/by-id :applications (:id application) [:notice-forms])
                  => {:notice-forms [form]})
        (message-count) => 1)))
  (against-background
    (org/get-organization "753-R") => test-organization))
