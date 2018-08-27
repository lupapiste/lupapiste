(ns lupapalvelu.migration.pate-verdict-migration-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.migration.pate-verdict-migration :refer :all]
            [lupapalvelu.pate.metadata :as metadata]))

(defn wrap [x]
  (metadata/wrap "Verdict draft Pate migration" ts x))

(fact "->pate-legacy-verdict"
  (let [verdict-id "verdict-id"
        kuntalupatunnus "lupatunnus"
        timestamp 1234
        anto 2345
        lainvoimainen 3456
        paatospvm 4567
        handler "handler"
        verdict-text "Decisions were made."
        section "1"
        code 2
        test-verdict {:id verdict-id
                      :kuntalupatunnus kuntalupatunnus
                      :draft true
                      :timestamp timestamp
                      :sopimus false
                      :metadata nil
                      :paatokset [{:id "5b7e5772e7d8a1a88e669357"
                                   :paivamaarat {:anto anto
                                                 :lainvoimainen lainvoimainen}
                                   :poytakirjat [{:paatoksentekija handler
                                                  :urlHash "5b7e5772e7d8a1a88e669356"
                                                  :status 2
                                                  :paatos verdict-text
                                                  :paatospvm paatospvm
                                                  :pykala section
                                                  :paatoskoodi code}]}]}
        test-application {:id "app-id"
                          :verdicts [test-verdict]
                          :permitType "R"
                          :tasks [{:id "katselmus-id"
                                   :schema-info {:name "task-katselmus"}
                                   :taskname "AloituskoKKous"
                                   :data {:katselmuksenLaji {:value "aloituskokous"}}
                                   :source {:id verdict-id}}
                                  {:id "foreman-id"
                                   :schema-info {:name "task-vaadittu-tyonjohtaja"}
                                   :taskname "Supervising supervisor"
                                   :source {:id verdict-id}}
                                  {:id "condition-id1"
                                   :schema-info {:name "task-lupamaarays"}
                                   :taskname "Muu 1"
                                   :source {:id verdict-id}}
                                  {:id "condition-id2"
                                   :schema-info {:name "task-lupamaarays"}
                                   :taskname "Muu 2"
                                   :source {:id verdict-id}}]}]
    (->pate-legacy-verdict test-application
                           test-verdict
                           ts)
    =>
    {:id verdict-id
     :modified 1234
     :user "TODO"
     :category :r
     :data {:handler (wrap handler)
            :kuntalupatunnus (wrap kuntalupatunnus)
            :verdict-section (wrap section)
            :verdict-code    (wrap (str code))
            :verdict-text    (wrap verdict-text)
            :anto            (wrap anto)
            :lainvoimainen   (wrap lainvoimainen)
            :reviews         {"katselmus-id" {:name (wrap "AloituskoKKous")
                                              :type (wrap "aloituskokous")}}
            :foremen         {"foreman-id" {:role (wrap "Supervising supervisor")}}
            :conditions      {"condition-id1" {:name (wrap "Muu 1")}
                              "condition-id2" {:name (wrap "Muu 2")}}
            }
     :template "TODO"
     :legacy? true}

    (->pate-legacy-verdict (assoc test-application :permitSubtype "sijoitussopimus")
                           test-verdict
                           ts)
    => (contains {:category :contract})))
