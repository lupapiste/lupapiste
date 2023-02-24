(ns lupapalvelu.fixture.submitted-application
  (:require [lupapalvelu.fixture.core :refer :all]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [sade.shared-util :as util]))

(def users (filter (comp #{"admin" "sonja" "pena"} :username) minimal/users))
(def pena (util/find-by-key :username "pena" users))

(def organizations (filter (comp (set (mapcat (comp keys :orgAuthz) users)) keyword :id) minimal/organizations))

(def created 1514877090000)
(def submitted (+ 100 created))

(def applications [{:id "LP-753-2018-90001"
                    :startedBy {}
                    :archived {:initial nil
                               :application nil
                               :completed nil}
                    :suti {:id nil
                           :added false}
                    :started nil
                    :closed nil
                    :address "Penakuja 3"
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
                    :_applicantIndex ["Pena Panaani"]
                    :complementNeeded nil
                    :infoRequest false
                    :history [{:state "draft"
                               :ts created
                               :user (usr/summary pena)}
                              {:state "submitted"
                               :ts submitted
                               :user (usr/summary pena)}]
                    :created created
                    :submitted submitted
                    :modified submitted
                    :municipality "753"
                    :state "submitted"
                    :opened submitted
                    :permitType "R"
                    :organization "753-R"
                    :warrantyEnd nil
                    :handlers []
                    :finished nil
                    :auth [(-> pena
                               (usr/user-in-role "writer")
                               (assoc :unsubscribed false))]
                    :urgency "normal"
                    :sent nil
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
                    :applicant "Pena Pansku"
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
                    :statements []}])

(deffixture "submitted-application" {}
  (mongo/clear!)
  (mongo/insert-batch :users users)
  (mongo/insert-batch :organizations organizations)
  (mongo/insert-batch :applications applications)
  (mongo/insert :sequences {:_id "applications-753-2018" :count 1}))
