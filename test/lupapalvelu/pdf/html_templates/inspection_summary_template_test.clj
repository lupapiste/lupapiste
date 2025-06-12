(ns lupapalvelu.pdf.html-templates.inspection-summary-template-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [lupapalvelu.test-util :refer [replace-in-schema MidjeMetaconstant]]
            [lupapalvelu.inspection-summary :refer [InspectionSummary]]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :refer :all]))

(testable-privates lupapalvelu.pdf.html-templates.inspection-summary-template target-attachments)


(facts target-attachments
  (fact "one attachemnt"
    (target-attachments {:attachments [{:target {:id ..target-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite.pdf"}}]}
                        {:id ..target-id..}) => ["liite.pdf"])

  (fact "no matching attachemnt"
    (target-attachments {:attachments [{:target {:id ..another-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite.pdf"}}]}
                        {:id ..target-id..}) => [])

  (fact "no target"
    (target-attachments {:attachments [{:target {:id ..target-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite.pdf"}}]}
                        nil) => [])

  (fact "multiple attachemnts"
    (target-attachments {:attachments [{:target {:id ..target-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite1.pdf"}}
                                       {:target {:id ..another-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite2.pdf"}}
                                       {:target {:id ..target-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite3.pdf"}}
                                       {:target {:id ..target-id.., :type "inspection-summary-item" }, :latestVersion {:filename "liite4.pdf"}}]}
                        {:id ..target-id..}) => ["liite1.pdf" "liite3.pdf" "liite4.pdf"]))

(def inspection-summary-snippet (enlive/snippet (enlive/html inspection-summary-template)
                                                [:#inspection-summary]
                                                [application lang summary-id]
                                                [:#inspection-summary] (inspection-summary-transformation application lang summary-id)))

(facts inspection-summary-transformation
  (let [app  {:primaryOperation     {:id ..op-id.., :name "pientalo-laaj", :description nil}
              :documents            [{:id ..doc-id..,
                                      :schema-info {:op {:id ..op-id.., :name "pientalo-laaj", :description nil},
                                                    :name "rakennuksen-laajentaminen",
                                                    :accordion-fields [[[ "valtakunnallinenNumero" ]]],
                                                    :version 1},
                                      :data {:valtakunnallinenNumero { :value "bld_123456" } } }]
              :attachments          [{:id ..attachment-id..,
                                      :groupType "operation",
                                      :type { :type-id :tarkastusasiakirja, :type-group :katselmukset_ja_tarkastukset },
                                      :op [ { :id ..op-id.., :name "pientalo-laaj" } ],
                                      :contents nil,
                                      :target { :id ..target-id-3.., :type "inspection-summary-item" },
                                      :versions [{:filename "liite.pdf",
                                                  :originalFileId ..file-id..,
                                                  :contentType "application/pdf",
                                                  :fileId ..file-id.. }],
                                      :latestVersion  {:filename "liite.pdf",
                                                       :originalFileId ..file-id..,
                                                       :contentType "application/pdf",
                                                       :fileId ..file-id.. }}]
              :inspection-summaries [{:name "Some inspection summary"
                                      :id ..summary-id..,
                                      :op {:id ..op-id..
                                           :name "pientalo-laaj"
                                           :description "" },
                                      :targets [{:finished false,
                                                 :id ..target-id-1..,
                                                 :target-name "first target"},
                                                {:finished true,
                                                 :id ..target-id-2..,
                                                 :target-name "second target",
                                                 :inspection-date 1488528634011,
                                                 :finished-date 1488528634011,
                                                 :finished-by {:id "..user-id.."
                                                               :username  "pena"
                                                               :firstName "Pena"
                                                               :lastName "Banaani"
                                                               :role "authority"}},
                                                {:finished false,
                                                 :id ..target-id-3..,
                                                 :target-name "third target"},
                                                {:finished false,
                                                 :id ..target-id-4..
                                                 :target-name "fourth target"}]}]}
        html (inspection-summary-snippet app "fi" ..summary-id..)]
    (fact "inspection summary test data matches schema"
      (sc/check [(replace-in-schema InspectionSummary ssc/ObjectIdStr MidjeMetaconstant)] (:inspection-summaries app)) => nil)
    (fact "summary name"
      (->> [:#inspection-summary-name] (enlive/select html) (map :content)) => [["Some inspection summary"]])
    (fact "summary operation"
      (->> [:#inspection-summary-operation] (enlive/select html) (map :content)) => [["Asuinpientalon laajentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo) - bld_123456"]])
    (fact "name column"
      (->> [:#name] (enlive/select html) (map :content))          => [["Tarkastuskohde"] ["first target"] ["second target"] ["third target"] ["fourth target"]])
    (fact "attachments column"
      (->> [:#attachments] (enlive/select html) (map :content))   => [["Liitteet"] [] [] [{:tag :div :attrs {} :content ["liite.pdf"]}] []])
    (fact "finished column"
      (->> [:#finished] (enlive/select html) (map :content))      => [["Merkitty tehdyksi"] [] ["Kyll\u00e4"] [] []])
    (fact "inspection date column"
      (->> [:#inspection-date] (enlive/select html) (map :content)) => [["Tarkastus pvm"] [] ["03.03.2017"] [] []])
    (fact "finished date column"
      (->> [:#finished-date] (enlive/select html) (map :content)) => [["Kuittaus pvm"] [] ["03.03.2017"] [] []])
    (fact "finished by column"
      (->> [:#finished-by] (enlive/select html) (map :content))   => [["Merkitsij\u00e4"] [""] ["Pena Banaani"] [""] [""]])))
