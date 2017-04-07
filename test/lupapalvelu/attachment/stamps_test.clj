(ns lupapalvelu.attachment.stamps-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.stamps :refer :all]))

(testable-privates lupapalvelu.attachment.stamps
                   tag-content
                   rows)

(def application
  {:id "LP-753-2017-90001"
   :verdicts [{:kuntalupatunnus "17-0753-R"
               :paatokset [{:paivamaarat {:lainvoimainen 0
                                          :anto 1491470000000}
                            :id "5829b90bf63a8034c04f1492"}],
               :id "5829b90bf63a8034c04f148f"}]
   :buildings [:description "Rakennus"
               :nationalId "100840657D"
               :buildingId "100840657D"]
   :documents [{:schema-info {:name "rakennuksen-muuttaminen"
                              :op {:id "57603a99edf02d7047774554"}}
                :data {:buildingId {:value "100840657D"
                                    :sourceValue "100840657D"}
                       :valtakunnallinenNumero {:value "100840657D"
                                                :modified 1491470000000
                                                :source "krysp"
                                                :sourceValue "100840657D"}
                       :tunnus {:value "100840657D"}
                       :id "1321321"}}]})

(def organization
  {:id     "753-R"
   :name   {:fi "Sipoon rakennusvalvonta"
            :sv "Sipoon rakennusvalvonta"}
   :stamps [{:id         "123456789012345678901234"
             :name       "Oletusleima"
             :position   {:x 10 :y 200}
             :background 0
             :page       "first"
             :qr-code    true
             :rows       [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type :current-date}]
                          [{:type :backend-id}]
                          [{:type :organization}]]}]})

(def sonja
  {:role "authority"
   :email "sonja.sibbo@sipoo.fi"
   :username "sonja"
   :firstName "Sonja"
   :orgAuthz
   {:753-R        #{:commenter :tos-editor :tos-publisher :authority :approver :reader :archivist}
    :753-YA       #{:commenter :tos-editor :tos-publisher :authority :approver :reader :archivist}}
   :expires 1491485085837
   :language "fi"
   :id "777777777777777777000023"
   :lastName "Sibbo"})

(facts tag-content
  (tag-content {:type :custom-text :text "Custom text"} {:organization organization :application application :user sonja}) => "Custom text"
  (tag-content {:type :extra-tex :text "Extra text"} {:organization organization :application application :user sonja}) => "Extra text"
  (tag-content {:type :current-date} {:organization organization :application application :user sonja}) => (sade.util/to-local-date (sade.core/now))
  (tag-content {:type :verdict-date} {:organization organization :application application :user sonja}) => "06.04.2017"
  (tag-content {:type :backend-id} {:organization organization :application application :user sonja}) => "17-0753-R"
  (tag-content {:type :username} {:organization organization :application application :user sonja}) => "Sonja Sibbo"
  (tag-content {:type :organization} {:organization organization :application application :user sonja}) => "Sipoon rakennusvalvonta"
  (tag-content {:type :agreement-id} {:organization organization :application application :user sonja}) => "LP-753-2017-90001"
  (tag-content {:type :building-id} {:organization organization :application application :user sonja}) => [{:national-id "100840657D"
                                                                                                             :operation-id "57603a99edf02d7047774554"
                                                                                                             :short-id "100840657D"}]
  (tag-content {:type :section :text "Section"} {:organization organization :application application :user sonja}) => "Section")

(facts "Stamp rows should be formed correctly"
  (rows (first (:stamps organization)) {:organization organization
                                        :application application
                                        :user sonja})               => [["Hyv\u00e4ksytty" (sade.util/to-local-date (sade.core/now))]
                                                                        ["17-0753-R"]
                                                                        ["Sipoon rakennusvalvonta"]])


