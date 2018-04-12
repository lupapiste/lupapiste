(ns lupapalvelu.application-replace-operation-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.string :refer [join]]
            [sade.core :refer [unauthorized]]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-api :as app]
            [lupapalvelu.application :as a]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.operations :as op]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.application-replace-operation :as replace-operation]
            [lupapalvelu.organization :as org]
            [lupapalvelu.application :as application]
            [monger.operators :refer [$set]]))

(apply-remote-minimal)

(org/update-organization "753-R"
                         {$set {:operations-attachments
                                {:mainoslaite
                                                                       [["paapiirustus" "julkisivupiirustus"]
                                                                        ["hakija" "ote_kauppa_ja_yhdistysrekisterista"]
                                                                        ["hakija"
                                                                         "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :auto-katos                           [["paapiirustus" "asemapiirros"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :masto-tms                            [["paapiirustus" "asemapiirros"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]]
                                 :vapaa-ajan-asuinrakennus             [["paapiirustus" "asemapiirros"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["suunnitelmat" "jatevesijarjestelman_suunnitelma"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :varasto-tms                          [["paapiirustus" "asemapiirros"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :jatevesi                             [["paapiirustus" "asemapiirros"]
                                                                        ["suunnitelmat" "jatevesijarjestelman_suunnitelma"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]]
                                 :aita                                 [["paapiirustus" "asemapiirros"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :asuinrakennus                        [["paapiirustus" "asemapiirros"]
                                                                        ["selvitykset" "energiataloudellinen_selvitys"]
                                                                        ["selvitykset" "energiatodistus"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["pelastusviranomaiselle_esitettavat_suunnitelmat"
                                                                         "paloturvallisuussuunnitelma"]
                                                                        ["suunnitelmat" "piha_tai_istutussuunnitelma"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :julkisivu-muutos                     [["paapiirustus" "asemapiirros"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :kerrostalo-rivitalo                  [["paapiirustus" "asemapiirros"]
                                                                        ["selvitykset" "energiataloudellinen_selvitys"]
                                                                        ["suunnitelmat" "julkisivujen_varityssuunnitelma"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["rakennuspaikka" "ote_asemakaavasta_jos_asemakaava_alueella"]
                                                                        ["hakija" "ote_kauppa_ja_yhdistysrekisterista"]
                                                                        ["suunnitelmat" "piha_tai_istutussuunnitelma"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]
                                                                        ["rakennuspaikka" "tonttikartta_tarvittaessa"]]
                                 :pientalo                             [["paapiirustus" "asemapiirros"]
                                                                        ["selvitykset" "energiataloudellinen_selvitys"]
                                                                        ["selvitykset" "energiatodistus"]
                                                                        ["paapiirustus" "julkisivupiirustus"]
                                                                        ["paapiirustus" "leikkauspiirustus"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["paapiirustus" "pohjapiirustus"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]
                                 :puun-kaataminen                      [["paapiirustus" "asemapiirros"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]]
                                 :kaivuu                               [["paapiirustus" "asemapiirros"]
                                                                        ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                                                        ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]}}})

(org/update-organization "753-R"
                         {$set {:mainoslaite
                                [["paapiirustus" "julkisivupiirustus"]
                                 ["hakija" "ote_kauppa_ja_yhdistysrekisterista"]
                                 ["hakija"
                                  "ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :auto-katos
                                [["paapiirustus" "asemapiirros"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :masto-tms
                                [["paapiirustus" "asemapiirros"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]],
                                :vapaa-ajan-asuinrakennus
                                [["paapiirustus" "asemapiirros"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["suunnitelmat" "jatevesijarjestelman_suunnitelma"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :varasto-tms
                                [["paapiirustus" "asemapiirros"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :jatevesi
                                [["paapiirustus" "asemapiirros"]
                                 ["suunnitelmat" "jatevesijarjestelman_suunnitelma"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]],
                                :aita
                                [["paapiirustus" "asemapiirros"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :asuinrakennus
                                [["paapiirustus" "asemapiirros"]
                                 ["selvitykset" "energiataloudellinen_selvitys"]
                                 ["selvitykset" "energiatodistus"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["pelastusviranomaiselle_esitettavat_suunnitelmat"
                                  "paloturvallisuussuunnitelma"]
                                 ["suunnitelmat" "piha_tai_istutussuunnitelma"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :julkisivu-muutos
                                [["paapiirustus" "asemapiirros"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :kerrostalo-rivitalo
                                [["paapiirustus" "asemapiirros"]
                                 ["selvitykset" "energiataloudellinen_selvitys"]
                                 ["suunnitelmat" "julkisivujen_varityssuunnitelma"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["rakennuspaikka" "ote_asemakaavasta_jos_asemakaava_alueella"]
                                 ["hakija" "ote_kauppa_ja_yhdistysrekisterista"]
                                 ["suunnitelmat" "piha_tai_istutussuunnitelma"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]
                                 ["rakennuspaikka" "tonttikartta_tarvittaessa"]],
                                :pientalo
                                [["paapiirustus" "asemapiirros"]
                                 ["selvitykset" "energiataloudellinen_selvitys"]
                                 ["selvitykset" "energiatodistus"]
                                 ["paapiirustus" "julkisivupiirustus"]
                                 ["paapiirustus" "leikkauspiirustus"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["paapiirustus" "pohjapiirustus"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]],
                                :puun-kaataminen
                                [["paapiirustus" "asemapiirros"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]],
                                :kaivuu
                                [["paapiirustus" "asemapiirros"]
                                 ["ennakkoluvat_ja_lausunnot" "naapurin_kuuleminen"]
                                 ["rakennuspaikan_hallinta" "todistus_hallintaoikeudesta"]]}})

(facts "Replacing primary operation"
  (let [app (create-application pena :operation :kerrostalo-rivitalo :propertyId sipoo-property-id)
        app-id (:id app)
        op-id (-> app :primaryOperation :id)
        type-group "pelastusviranomaiselle_esitettavat_suunnitelmat"
        type-id "savunpoistosuunnitelma"
        organization (org/get-organization (:organization app))]

    (println (->> app :attachments (map :type)))

    (fact "Pena adds attachment"
      (upload-attachment pena app-id {:id "" :type {:type-group type-group
                                                    :type-id    type-id}} true) => truthy)

    (fact "Pena adds operation"
      (command pena :add-operation :id app-id :operation "varasto-tms"))

    (fact "Pena replaces primary operation"
      (command pena :replace-operation :id app-id :opId op-id :operation "vapaa-ajan-asuinrakennus") => ok?)

    (let
      [updated-app (query-application pena app-id)
       updated-app-document-names (->> updated-app :documents (map #(-> % :schema-info :name)))
       operations (cons (:primaryOperation updated-app) (:secondaryOperations updated-app))]

      (fact "Application has new primaryOperation"
        (-> updated-app :primaryOperation :name) => "masto-tms")

      (fact "Application still has the added attachment"
        (->> updated-app
             :attachments
             (filter #(and (= (-> % :type :type-group) type-group)
                           (= (-> % :type :type-id) type-id)))
             (count)) => 1)

      (fact "Application has new primary operation document and old secondary operation document"
        (->> updated-app
             :documents
             (filter #(= "uusiRakennus" (-> % :schema-info :op :name)))) => empty?

        (->> updated-app
             :documents
             (filter #(= "masto-tms" (-> % :schema-info :op :name)))
             (count)) => 1)

      (fact "Application has correct documents"
        (= (set updated-app-document-names)
           (replace-operation/get-required-document-schema-names-for-operations organization operations)))

      (fact "Application also has the secondary operation"
        (->> updated-app
             :secondaryOperations
             (first)
             :name) => "varasto-tms"

        (->> updated-app
             :documents
             (filter #(= "varasto-tms" (-> % :schema-info :op :name)))
             (count)) => 1)

      (fact "Application has correct attachments"
        (let [existing-attachment-types (->> updated-app :attachments (map :type) (set))
              _ (println (count (:attachments updated-app)))
              _ (println "existing attachment-types: " existing-attachment-types)
              required-attachment-types (->> operations
                                             (map #(application/new-attachment-types-for-operation organization % nil))
                                             (apply concat)
                                             ((fn [arg] (println "new-types " arg) arg))
                                             (map (fn [type] {:type-group (:type-group type) :type-id (:type-id type)}))
                                             ((fn [arg] (println "new-types only types " arg) arg))
                                             (set))
              required-attachments-exist? (->> required-attachment-types
                                               ((fn [arg] (println "required attachment types" arg) arg))
                                               (map existing-attachment-types)
                                               ((fn [arg] (println arg) arg))
                                               (some nil?)
                                               ((fn [arg] (println arg) arg))
                                               (not))]
          (or (nil? required-attachment-types) required-attachments-exist?) => true)))))