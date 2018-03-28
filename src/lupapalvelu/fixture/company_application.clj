(ns lupapalvelu.fixture.company-application
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]))

(def selected-users #{"admin" "sonja" "pena" "kaino@solita.fi" "erkki@example.com" "teppo@example.com"})

(defn teppo-to-solita [users]
  (letfn [(do-teppo [user]
            (if (= (:username user) "teppo@example.com")
              (assoc user :company {:id "solita" :role "user" :submit true})
              user))]
    (map do-teppo users)))

(def users (->> minimal/users
                (filter (comp selected-users :username))
                (teppo-to-solita)))

(def organizations (filter (comp (set (mapcat (comp keys :orgAuthz) users)) keyword :id) minimal/organizations))

(def companies minimal/companies)

(def created 1514877090000)

(def default-app-id (str "LP-753-" minimal/now-year "-90001"))

(def applications [{:id default-app-id
                    :startedBy {}
                    :archived {:initial nil
                               :application nil
                               :completed nil}
                    :suti {:id nil
                           :added false}
                    :started nil
                    :closed nil
                    :address "Latokuja 3"
                    :primaryOperation {:id "5a4b30a988c5cbc9a3a61782"
                                       :name "kerrostalo-rivitalo"
                                       :description nil
                                       :created created}
                    :permitSubtype nil
                    :bulletinOpDescription nil
                    :agreementPrepared nil
                    :foreman ""
                    :buildings []
                    :authorityNotice ""
                    :authority {:firstName ""
                                :lastName ""
                                :id nil}
                    :reminder-sent nil
                    :comments []
                    :secondaryOperations []

                    :attachments [{:groupType "operation"
                                   :type {:type-id "asemapiirros"
                                          :type-group "paapiirustus"}
                                   :op [{:id "5a4b30a988c5cbc9a3a61782"
                                         :name "kerrostalo-rivitalo"}]
                                   :auth []
                                   :modified created
                                   :requestedByAuthority false
                                   :applicationState "draft"
                                   :readOnly false
                                   :locked false
                                   :id "5a4b30ab88c5cbc9a3a61785"
                                   :notNeeded false
                                   :signatures []
                                   :backendId nil
                                   :forPrinting false
                                   :contents nil
                                   :target nil
                                   :versions []
                                   :required true}]

                    :schema-version 1
                    :openInfoRequest false
                    :_applicantIndex ["Solita Kaino"]
                    :complementNeeded nil
                    :infoRequest false
                    :history [{:state "draft"
                               :ts created
                               :user {:id "kainosolita"
                                      :username "kaino@solita.fi"
                                      :firstName "Kaino"
                                      :lastName "Solita"
                                      :role "applicant"}}]
                    :created created
                    :municipality "753"
                    :state "draft"
                    :opened nil
                    :permitType "R"
                    :organization "753-R"
                    :warrantyEnd nil
                    :handlers []
                    :finished nil
                    :auth [{:id "solita"
                            :name "Solita Oy"
                            :y "1060155-5"
                            :role "writer"
                            :type "company"
                            :username "1060155-5"
                            :firstName "Solita Oy"
                            :lastName ""}]
                    :modified created
                    :urgency "normal"
                    :sent nil
                    :submitted nil
                    :title "Latokuja 3"
                    :_attachment_indicator_reset nil
                    :tasks []
                    :verdicts []

                    :documents [{:id "5a4b30aa88c5cbc9a3a61784"
                                 :schema-info {:name "hakija-r"
                                               :version 1}
                                 :created created
                                 :data {:_selected {:value "henkilo"}
                                        :henkilo {:userId {:value nil}
                                                  :henkilotiedot {:etunimi {:value ""}
                                                                  :sukunimi {:value ""}
                                                                  :hetu {:value nil}
                                                                  :turvakieltoKytkin {:value false}}
                                                  :osoite {:katu {:value ""}
                                                           :postinumero {:value ""}
                                                           :postitoimipaikannimi {:value ""}
                                                           :maa {:value "FIN"}}
                                                  :yhteystiedot {:puhelin {:value ""}
                                                                 :email {:value ""}}
                                                  :kytkimet {:suoramarkkinointilupa {:value false}
                                                             :vainsahkoinenAsiointiKytkin {:value true}}}
                                        :yritys {:companyId {:value nil}
                                                 :yritysnimi {:value ""}
                                                 :liikeJaYhteisoTunnus {:value ""}
                                                 :osoite {:katu {:value ""}
                                                          :postinumero {:value ""}
                                                          :postitoimipaikannimi {:value ""}
                                                          :maa {:value "FIN"}}
                                                 :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}
                                                                                 :sukunimi {:value ""}
                                                                                 :turvakieltoKytkin {:value false}}
                                                                 :yhteystiedot {:puhelin {:value ""}
                                                                                :email {:value ""}}
                                                                 :kytkimet {:suoramarkkinointilupa {:value false}
                                                                            :vainsahkoinenAsiointiKytkin {:value true}}}}}}

                                {:id "5a4b30aa88c5cbc9a3a61783"
                                 :schema-info {:op {:id "5a4b30a988c5cbc9a3a61782"
                                                    :name "kerrostalo-rivitalo"
                                                    :description nil
                                                    :created  created}
                                               :name "uusiRakennus"
                                               :version 1 }
                                 :created created
                                 :data {:rakennuksenOmistajat {:0 {:_selected {:value "henkilo"}
                                                                   :henkilo {:userId {:value nil}
                                                                             :henkilotiedot {:etunimi {:value ""}
                                                                                             :sukunimi {:value ""}
                                                                                             :hetu {:value nil}
                                                                                             :turvakieltoKytkin {:value false}}
                                                                             :osoite {:katu {:value ""}
                                                                                      :postinumero {:value ""}
                                                                                      :postitoimipaikannimi {:value ""}
                                                                                      :maa {:value "FIN"}}
                                                                             :yhteystiedot {:puhelin {:value ""}
                                                                                            :email {:value ""}}
                                                                             :kytkimet {:suoramarkkinointilupa {:value false}}}
                                                                   :yritys {:companyId {:value nil}
                                                                            :yritysnimi {:value ""}
                                                                            :liikeJaYhteisoTunnus {:value ""}
                                                                            :osoite {:katu {:value ""}
                                                                                     :postinumero {:value ""}
                                                                                     :postitoimipaikannimi {:value ""}
                                                                                     :maa {:value "FIN"}}
                                                                            :yhteyshenkilo {:henkilotiedot {:etunimi {:value ""}
                                                                                                            :sukunimi {:value ""}
                                                                                                            :turvakieltoKytkin {:value false}}
                                                                                            :yhteystiedot {:puhelin {:value ""}
                                                                                                           :email {:value ""}}}}
                                                                   :omistajalaji {:value nil}
                                                                   :muu-omistajalaji {:value ""}}}
                                        :varusteet {:viemariKytkin {:value false}
                                                    :saunoja {:value ""}
                                                    :vesijohtoKytkin {:value false}
                                                    :hissiKytkin {:value false}
                                                    :vaestonsuoja {:value ""}
                                                    :kaasuKytkin {:value false}
                                                    :aurinkopaneeliKytkin {:value false}
                                                    :liitettyJatevesijarjestelmaanKytkin {:value false}
                                                    :koneellinenilmastointiKytkin {:value false}
                                                    :sahkoKytkin {:value false}
                                                    :lamminvesiKytkin {:value false}}
                                        :verkostoliittymat {:viemariKytkin {:value false}
                                                            :vesijohtoKytkin {:value false}
                                                            :sahkoKytkin {:value false}
                                                            :maakaasuKytkin {:value false}
                                                            :kaapeliKytkin {:value false}}
                                        :kaytto {:rakentajaTyyppi {:value nil}
                                                 :kayttotarkoitus {:value "021 rivitalot"
                                                                   :modified created}}
                                        :huoneistot {:0 {:WCKytkin {:value false}
                                                         :huoneistoTyyppi {:value nil}
                                                         :keittionTyyppi {:value nil}
                                                         :huoneistoala {:value ""}
                                                         :huoneluku {:value ""}
                                                         :jakokirjain {:value ""}
                                                         :ammeTaiSuihkuKytkin {:value false}
                                                         :saunaKytkin {:value false}
                                                         :huoneistonumero {:value "000"
                                                                           :modified created}
                                                         :porras {:value ""}
                                                         :muutostapa {:value "lis\u00e4ys"
                                                                      :modified created}
                                                         :lamminvesiKytkin {:value false}
                                                         :parvekeTaiTerassiKytkin {:value false}}}
                                        :lammitys {:lammitystapa {:value nil}
                                                   :lammonlahde {:value nil}
                                                   :muu-lammonlahde {:value ""}}
                                        :tunnus {:value ""}
                                        :rakenne {:rakentamistapa {:value nil}
                                                  :kantavaRakennusaine {:value nil}
                                                  :muuRakennusaine {:value ""}
                                                  :julkisivu {:value nil}
                                                  :muuMateriaali {:value ""}}
                                        :mitat {:tilavuus {:value ""}
                                                :kerrosala {:value ""}
                                                :rakennusoikeudellinenKerrosala {:value ""}
                                                :kokonaisala {:value ""}
                                                :kerrosluku {:value ""}
                                                :kellarinpinta-ala {:value ""}}
                                        :luokitus {:energialuokka {:value nil}
                                                   :energiatehokkuusluku {:value ""}
                                                   :energiatehokkuusluvunYksikko {:value "kWh/m2"}
                                                   :paloluokka {:value nil}}
                                        :valtakunnallinenNumero {:value ""}}}

                                {:id "5a4b30ab88c5cbc9a3a61789"
                                 :schema-info {:name "hankkeen-kuvaus"
                                               :version  1 }
                                 :created created
                                 :data {:kuvaus {:value ""}
                                        :poikkeamat {:value ""}
                                        :rahoitus {:value nil}}}]

                    :warrantyStart nil
                    :propertyIdSource nil
                    :agreementSigned nil
                    :_comments-seen-by {}
                    :closedBy {}
                    :acknowledged nil
                    :location-wgs84 [25.266
                                     60.36938]
                    :foremanRole ""
                    :applicant "Solita Kaino"
                    :transfers []
                    :_verdicts-seen-by {}
                    :options {}
                    :neighbors []
                    :appealVerdicts []
                    :tosFunction nil
                    :processMetadata {}
                    :propertyId "75341600550007"
                    :reservations []
                    :location [404369.304
                               6693806.957]
                    :inspection-summaries []
                    :drawings []
                    :metadata {}
                    :_statements-seen-by {}
                    :appeals []
                    :convertedToApplication nil
                    :statements []
                    :company-notes [{:companyId "solita"
                                     :tags ["7a67a67a67a67a67a67a67a6"]}]}])

(deffixture "company-application" {}
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :companies companies)
  (mongo/insert-batch :organizations organizations)
  (mongo/insert-batch :applications applications)
  (mongo/insert :sequences {:_id (str "applications-753-" minimal/now-year) :count 1}))
