(ns lupapalvelu.migration.pate-verdict-migration-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.migration.pate-verdict-migration :refer :all]
            [lupapalvelu.pate.metadata :as metadata]))

(def ts 12345)

(def test-application {:_id "foo"
                       :verdicts [test-verdict]})

(defn wrap [x]
  (metadata/wrap "Verdict draft Pate migration" ts x))

(fact "->pate-legacy-verdict"
  (let [id "id"
        kuntalupatunnus "lupatunnus"
        timestamp 1234
        anto 2345
        lainvoimainen 3456
        paatospvm 4567
        handler "handler"
        verdict-text "Decisions were made."
        section "1"
        code 2
        test-verdict {:id id
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
                                   :source {:id id}}]}]
    (->pate-legacy-verdict test-application
                           test-verdict
                           ts)
    =>
    {:id "id"
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
            ;; :foremen         (wrap "TODO")
            ;; :conditions      (wrap "TODO")
            }
     :template "TODO"
     :legacy? true}

    (->pate-legacy-verdict (assoc test-application :permitSubtype "sijoitussopimus")
                           test-verdict
                           ts)
    => (contains {:category :contract})))
