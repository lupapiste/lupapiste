(ns lupapalvelu.attachment.stamps-test
  (:require [lupapalvelu.attachment.stamps :refer :all]
            [lupapalvelu.pate.verdict-interface :as vif]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]))

(testable-privates lupapalvelu.attachment.stamps
                   tag-content
                   fill-rows)

(def today (date/finnish-date (date/now) :zero-pad))

(def application
  {:id "LP-753-2017-90001"
   :verdicts [{:kuntalupatunnus "17-0753-R"
               :paatokset [{:paivamaarat {:lainvoimainen 0
                                          :anto 1491460000000}
                            :poytakirjat [{:paatospvm 1491470000000
                                           :pykala 666}]
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
             :page       :first
             :qrCode    true
             :rows       [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type :current-date}]
                          [{:type :backend-id}]
                          [{:type :building-id}]
                          [{:type :organization}]]}]})

(def sonja
  {:role "authority"
   :email "sonja.sibbo@sipoo.fi"
   :user "sonja"
   :firstName "Sonja"
   :orgAuthz
   {:753-R        #{:commenter :tos-editor :tos-publisher :authority :approver :reader :archivist}
    :753-YA       #{:commenter :tos-editor :tos-publisher :authority :approver :reader :archivist}}
   :expires 1491485085837
   :language "fi"
   :id "777777777777777777000023"
   :lastName "Sibbo"})

(def multiple-stamps
  [{:id         "123456789012345678901234"
    :name       "Oletusleima"
    :position   {:x 10 :y 200}
    :background 0
    :page       :first
    :qrCode    true
    :rows       [[{:type :custom-text :text "Hyv\u00e4ksytty"} {:type :current-date}]
                 [{:type :backend-id}]
                 [{:type :organization}]]}
   {:id         "112233445566778899004567"
    :name       "KV-Leima"
    :position   {:x 15 :y 250}
    :background 10
    :page       :last
    :qrCode    false
    :rows       [[{:type :custom-text :text "Verdict given"} {:type :verdict-date}]
                 [{:type :backend-id} {:type :user}]
                 [{:type :organization}]
                 [{:type :application-id}]]}
   {:id         "999999888887777722223300"
    :name       "YA-Leima"
    :position   {:x 50 :y 50}
    :background 15
    :page       :all
    :qrCode    true
    :rows       [[{:type :current-date} {:type :verdict-date}]
                 [{:type :extra-text :text "Some extra text"}]
                 [{:type :application-id}]]}])

(def info-fields
  [[{:type "custom-text", :value "Hyv\u00e4ksytty"}
    {:type "current-date", :value "26.04.2017"}]
   [{:type "backend-id", :value "KLT-123"}]
   [{:type "organization", :value "Sipoon rakennusvalvonta"}]
   [{:type "building-id", :value "Rakennustunnus"}]])

(facts tag-content
  (let [context {:organization organization :application application :user sonja
                 :verdict (vif/latest-published-verdict {:application application})}]
    (tag-content {:type :custom-text :text "Custom text"} context) => {:type :custom-text :value "Custom text"}
    (tag-content {:type :extra-text :text "Extra text"} context) => {:type :extra-text :value "Extra text"}
    (tag-content {:type :current-date} context) => {:type :current-date :value today}
    (tag-content {:type :verdict-date} context) => {:type :verdict-date :value "06.04.2017"}
    (tag-content {:type :backend-id} context) => {:type :backend-id :value "17-0753-R"}
    (tag-content {:type :user} context) => {:type :user :value "Sonja Sibbo"}
    (tag-content {:type :organization} context) => {:type :organization :value "Sipoon rakennusvalvonta"}
    (tag-content {:type :application-id} context) => {:type :application-id :value "LP-753-2017-90001"}
    (tag-content {:type :building-id} context) => {:type :building-id :value "Rakennustunnus"}
    (tag-content {:type :section} context) => {:type :section :value "\u00a7 666"}))

(facts "Stamp rows should be formed correctly"
  (fill-rows (first (:stamps organization)) {:organization organization
                                             :application  application
                                             :user         sonja
                                             :verdict      (vif/latest-published-verdict {:application application})})
  =>
  [[{:type :custom-text :value "Hyv\u00e4ksytty"}
    {:type :current-date :value today}]
   [{:type :backend-id :value "17-0753-R"}]
   [{:type :building-id :value "Rakennustunnus"}]
   [{:type :organization :value "Sipoon rakennusvalvonta"}]])

(facts "Stamps should be formed correctly"
  (fact "Default stamp is formed ok"
    (stamps organization application sonja)
    =>
    [{:id         "123456789012345678901234"
      :name       "Oletusleima"
      :position   {:x 10 :y 200}
      :background 0
      :page       :first
      :qrCode     true
      :rows       [[{:type :custom-text :value "Hyv\u00e4ksytty"}
                    {:type :current-date
                     :value today}]
                   [{:type :backend-id :value "17-0753-R"}]
                   [{:type :building-id :value "Rakennustunnus"}]
                   [{:type :organization :value "Sipoon rakennusvalvonta"}]]}])
  (fact "Multiple stamps are formed ok"
    (stamps (assoc organization :stamps multiple-stamps) application sonja)
    =>
    [{:id         "123456789012345678901234"
      :name       "Oletusleima"
      :position   {:x 10 :y 200}
      :background 0
      :page       :first
      :qrCode     true
      :rows       [[{:type :custom-text :value "Hyv\u00e4ksytty"}
                    {:type :current-date
                     :value today}]
                   [{:type :backend-id :value "17-0753-R"}]
                   [{:type :organization :value "Sipoon rakennusvalvonta"}]]}
     {:id         "112233445566778899004567"
      :name       "KV-Leima"
      :position   {:x 15 :y 250}
      :background 10
      :page       :last
      :qrCode     false
      :rows       [[{:type :custom-text :value "Verdict given"}
                    {:type :verdict-date :value "06.04.2017"}]
                   [{:type :backend-id :value "17-0753-R"}
                    {:type :user, :value "Sonja Sibbo"}]
                   [{:type :organization :value "Sipoon rakennusvalvonta"}]
                   [{:type :application-id :value "LP-753-2017-90001"}]]}
     {:id         "999999888887777722223300"
      :name       "YA-Leima"
      :position   {:x 50 :y 50}
      :background 15
      :page       :all
      :qrCode     true
      :rows       [[{:type :current-date
                     :value today}
                    {:type :verdict-date :value "06.04.2017"}]
                   [{:type :extra-text :value "Some extra text"}]
                   [{:type :application-id :value "LP-753-2017-90001"}]]}]))

(facts "Should get row value by type"
    (let [stamp (first (stamps organization application sonja))]
      (value-by-type (:rows stamp) :custom-text) => "Hyv\u00e4ksytty"
      (value-by-type (:rows stamp) :extra-text) => nil
      (row-value-by-type stamp :custom-text) => "Hyv\u00e4ksytty"
      (row-value-by-type stamp :backend-id) => "17-0753-R"
      (row-value-by-type stamp :organization) => "Sipoon rakennusvalvonta"
      (row-value-by-type stamp :extra-text) => nil
      (row-value-by-type stamp :unknown-key) => (throws AssertionError #"Assert failed")
      (row-value-by-type {} :custom-text) => (throws Exception #"Value does not match schema")))

(facts "Should return rows without tag of given type"
  (let [stamp (first (stamps organization application sonja))]
    (dissoc-tag-by-type (:rows stamp) :building-id)
    =>
    [[{:type :custom-text :value "Hyv\u00e4ksytty"}
      {:type :current-date :value today}]
     [{:type :backend-id :value "17-0753-R"}]
     [{:type :organization :value "Sipoon rakennusvalvonta"}]]

    (dissoc-tag-by-type (:rows stamp) :backend-id)
    =>
    [[{:type :custom-text :value "Hyv\u00e4ksytty"}
      {:type :current-date :value today}]
     [{:type :building-id :value "Rakennustunnus"}]
     [{:type :organization :value "Sipoon rakennusvalvonta"}]]))

(facts "Should get rows as strings"
  (let [stamp (first (stamps organization application sonja))]
    (non-empty-stamp-rows->vec-of-string-value-vecs (:rows stamp)) => [["Hyv\u00e4ksytty" today]
                                                                       ["17-0753-R"]
                                                                       ["Rakennustunnus"]
                                                                       ["Sipoon rakennusvalvonta"]]))

(facts "Should update value for given type"
  (let [stamp (first (stamps organization application sonja))]
    (assoc-tag-by-type (:rows stamp) :building-id "7878787878")
    =>
    [[{:type :custom-text :value "Hyv\u00e4ksytty"}
      {:type :current-date :value today}]
     [{:type :backend-id :value "17-0753-R"}]
     [{:type :building-id :value "7878787878"}]
     [{:type :organization :value "Sipoon rakennusvalvonta"}]]

    (assoc-tag-by-type info-fields :building-id "55556666")
    =>
    [[{:type "custom-text" :value "Hyv\u00e4ksytty"}
      {:type "current-date" :value "26.04.2017"}]
     [{:type "backend-id" :value "KLT-123"}]
     [{:type "organization" :value "Sipoon rakennusvalvonta"}]
     [{:type "building-id" :value "55556666"}]]))
