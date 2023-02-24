(ns lupapalvelu.tasks-test
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate.verdict :refer [validate-tasks]]
            [lupapalvelu.tasks :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.util :as util]))

(testable-privates lupapalvelu.tasks
                   verdict->tasks get-muu-tunnus-data update-building)

(facts "Tyonjohtajat KRYSP yhteiset 2.1.1"
  (let [tasks (flatten (verdict->tasks {:paatokset [{:lupamaaraykset
                                                     {:vaadittuTyonjohtajatieto ["vastaava ty\u00f6njohtaja"
                                                                                 "ty\u00f6njohtaja"]}}]}
                                       nil {:buildings []}))
        task (first tasks)]
    (count tasks) => 2
    (get-in task [:schema-info :name]) => "task-vaadittu-tyonjohtaja"
    (get-in task [:data :kuntaRoolikoodi :value]) => "vastaava ty\u00f6njohtaja"
    (validate-tasks tasks) => truthy))

(facts "Non-KRYSP-conforming tyonjohtajat are changed to generic requirements"
  (let [tasks (flatten (verdict->tasks {:paatokset [{:lupamaaraykset
                                                     {:vaadittuTyonjohtajatieto ["10", "sinappimestari"]}}]}
                                       nil {:buildings []}))
        task (first tasks)]
    (count tasks) => 2
    (get-in task [:schema-info :name]) => "task-lupamaarays"
    (validate-tasks tasks) => truthy))

(facts "Rakennus data from buildings"
  (rakennus-data-from-buildings {} nil) => {}
  (rakennus-data-from-buildings {} []) => {}
  (rakennus-data-from-buildings nil nil) => nil

  (rakennus-data-from-buildings {:1 {:foo "faa"} :3 {:foo "fuu"}}
                                [{:index "1" :propertyId "123" :nationalId "3214M"}]) => {:1 {:foo "faa"}
                                                                                          :3 {:foo "fuu"}
                                                                                          :4 {:rakennus {:jarjestysnumero                    "1"
                                                                                                         :kiinttun                           "123"
                                                                                                         :rakennusnro                        nil
                                                                                                         :valtakunnallinenNumero             "3214M"
                                                                                                         :kunnanSisainenPysyvaRakennusnumero nil}
                                                                                              :tila {:tila ""
                                                                                                     :kayttoonottava false} }})


(facts "Review building info is updated but status info kept"
  (let [buildings [{:description "Talo A, Toinen selite",
                           :localShortId "value-from-buildings",
                           :buildingId "123456001M",
                           :index "1",
                    :operationId "abcdefghijklmnopqr"}]
        task-rakennus {:schema-info
                          {:version 1,
                           :type "task",
                           :name "task-katselmus"},
                          :closed nil,
                          :id "573996952374d10fe642f304",
                          :assignee {:lastName "Sibbo", :firstName "Sonja", :id "777777777777777777000023"},
                          :taskname "Aloituskokous",
                          :data
                          {:muuTunnus {:modified 1463391893160, :value ""},
                           :katselmus
                           {:poikkeamat {:modified 1463391893160, :value ""},
                            :lasnaolijat {:modified 1463391893160, :value ""},
                            :huomautukset
                            {:toteamisHetki {:value nil}, :toteaja {:value ""}, :maaraAika {:value nil}, :kuvaus {:modified 1463391893160, :value ""}},
                            :tiedoksianto {:value false},
                            :pitaja {:modified 1463391893160, :value nil},
                            :pitoPvm {:modified 1463391893160, :value nil},
                            :tila {:modified 1463391893160, :value nil}},
                           :rakennus
                           {:0
                            {:tila {:kayttoonottava {:modified 1463391893160, :value false}, :tila "value-from-task"},
                             :rakennus
                             {:rakennusnro {:modified 1463391893160, :value "to-be-overridden"},
                              :kiinttun {:modified 1463391893160, :value "18601234567890"},
                              :jarjestysnumero {:modified 1463391893160, :value "1"}}}},
                           :vaadittuLupaehtona {:modified 1463391893160, :value true},
                           :katselmuksenLaji {:modified 1463391893160, :value "aloituskokous"}}}]


    (get-in (update-task-buildings buildings task-rakennus) [:data :rakennus :0 :rakennus :rakennusnro :value]) => "value-from-buildings"
    (get-in (update-task-buildings buildings task-rakennus) [:data :rakennus :0 :tila :tila :value]) => "value-from-task"))


(facts get-muu-tunnus-data
  (fact "no muu-tunnus"
    (get-muu-tunnus-data {:katselmuksenLaji "aloituskokous"
                          :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"}) => {:muuTunnus "" :muuTunnusSovellus ""})

  (fact "muu-tunnus without :sovellus"
    (get-muu-tunnus-data {:katselmuksenLaji "aloituskokous"
                     :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"
                     :muuTunnustieto [{:MuuTunnus {:sovellus "" :tunnus "999"}} ]}) => {:muuTunnus "999" :muuTunnusSovellus ""})

  (fact "muu-tunnus with :sovellus"
    (get-muu-tunnus-data {:katselmuksenLaji "aloituskokous"
                     :tarkastuksenTaiKatselmuksenNimi "Aloituskokous"
                     :muuTunnustieto [{:MuuTunnus {:sovellus "RakApp" :tunnus "999"}} ]}) => {:muuTunnus "999" :muuTunnusSovellus "RakApp"})

  (fact "muu-tunnus multiple muuTunnus elements"
    (get-muu-tunnus-data {:katselmuksenLaji "muu tarkastus"
                     :muuTunnustieto [{:MuuTunnus {:sovellus "RakApp12" :tunnus "998"}}
                                      {:MuuTunnus {:sovellus "RakApp" :tunnus "997"}}]
                     :tarkastuksenTaiKatselmuksenNimi "K\u00E4ytt\u00F6\u00F6nottotarkastus"}) => {:muuTunnus "998" :muuTunnusSovellus "RakApp12"}))

(facts merge-rakennustieto
  (let [r1   {:jarjestysnumero                    "1"
              :katselmusOsittainen                "lopullinen"
              :kayttoonottoKytkin                 true
              :kunnanSisainenPysyvaRakennusnumero "local-id"
              :valtakunnallinenNumero             "vtj-prt"}
        r2   {:jarjestysnumero                    "2"
              :katselmusOsittainen                "osittainen"
              :kayttoonottoKytkin                 false
              :kunnanSisainenPysyvaRakennusnumero "local-id"
              :valtakunnallinenNumero             "vtj-prt"}
        b1   {:index        "1"
              :propertyId   "property-id"
              :localShortId "short-id"
              :localId      "local-id"
              :nationalId   "vtj-prt"}
        rdfb (rakennus-data-from-buildings {} [b1 (assoc b1 :index "2")])]
    (fact "rakennusdata-from-buildings"
      rdfb => {:0 {:rakennus {:jarjestysnumero                    "1"
                              :kunnanSisainenPysyvaRakennusnumero "local-id"
                              :valtakunnallinenNumero             "vtj-prt"
                              :kiinttun                           "property-id"
                              :rakennusnro                        "short-id"}
                   :tila     {:tila "" :kayttoonottava false}}
               :1 {:rakennus {:jarjestysnumero                    "2"
                              :kunnanSisainenPysyvaRakennusnumero "local-id"
                              :valtakunnallinenNumero             "vtj-prt"
                              :kiinttun                           "property-id"
                              :rakennusnro                        "short-id"}
                   :tila     {:tila "" :kayttoonottava false}}})
    (fact "r1 matches both buildings when it is the only review building"
      (merge-rakennustieto [r1] rdfb)
      => {:0 {:rakennus {:jarjestysnumero                    "1"
                         :kiinttun                           "property-id"
                         :kunnanSisainenPysyvaRakennusnumero "local-id"
                         :rakennusnro                        "short-id"
                         :valtakunnallinenNumero             "vtj-prt"}
              :tila     {:kayttoonottava true :tila "lopullinen"}}
          :1 {:rakennus {:jarjestysnumero                    "2"
                         :kiinttun                           "property-id"
                         :kunnanSisainenPysyvaRakennusnumero "local-id"
                         :rakennusnro                        "short-id"
                         :valtakunnallinenNumero             "vtj-prt"}
              :tila     {:kayttoonottava true :tila "lopullinen"}}})
    (fact "r1 and r2 match different buildings"
      (merge-rakennustieto [r1 r2] rdfb)
      => {:0 {:rakennus {:jarjestysnumero                    "1"
                         :kiinttun                           "property-id"
                         :kunnanSisainenPysyvaRakennusnumero "local-id"
                         :rakennusnro                        "short-id"
                         :valtakunnallinenNumero             "vtj-prt"}
              :tila     {:kayttoonottava true :tila "lopullinen"}}
          :1 {:rakennus {:jarjestysnumero                    "2"
                         :kiinttun                           "property-id"
                         :kunnanSisainenPysyvaRakennusnumero "local-id"
                         :rakennusnro                        "short-id"
                         :valtakunnallinenNumero             "vtj-prt"}
              :tila     {:kayttoonottava false :tila "osittainen"}}})))

(fact update-building
  (let [b1 {:index        "1"
            :propertyId   "property-id"
            :localShortId "short-id"
            :localId      "local-id"
            :nationalId   "vtj-prt"}
        r1 {:jarjestysnumero                    "1"
            :kunnanSisainenPysyvaRakennusnumero "local-id"
            :valtakunnallinenNumero             "vtj-prt"}
        r2 {:jarjestysnumero                    "2"
            :kunnanSisainenPysyvaRakennusnumero "local-id-2"
            :valtakunnallinenNumero             "vtj-prt"}]
    (fact "Exact match (by index and national-id)"
      (update-building (tools/wrapped {:0 {:rakennus r1
                                           :tila     {:tila           "r1"
                                                      :kayttoonottava true}}
                                       :1 {:rakennus (dissoc r1 :jarjestysnumero)
                                           :tila     {:tila           "bad"
                                                      :kayttoonottava true}}})
                       b1)
      => (assoc b1 :task-tila {:tila           "r1"
                               :kayttoonottava true}))
    (fact "Matching just national-id"
      (update-building (tools/wrapped {:0 {:rakennus r2
                                           :tila     {:tila           "r2"
                                                      :kayttoonottava true}}
                                       :1 {:rakennus (dissoc r2 :valtakunnallinenNumero)
                                           :tila     {:tila           "bad"
                                                      :kayttoonottava true}}})
                       b1)
      => (assoc b1 :task-tila {:tila           "r2"
                               :kayttoonottava true}))
    (fact "Just in case, the second building is more exact match (local-id and index vs. national-id)"
      (update-building (tools/wrapped {:0 {:rakennus r2
                                           :tila     {:tila           "r2"
                                                      :kayttoonottava true}}
                                       :1 {:rakennus (dissoc r1 :valtakunnallinenNumero)
                                           :tila     {:tila           "good"
                                                      :kayttoonottava true}}})
                       b1)
      => (assoc b1 :task-tila {:tila           "good"
                               :kayttoonottava true}))))

(let [make-officer    #(hash-map :code (str "code-" %1) :id (str "id-" %1) :name %2 :_atomic-map? true)
      org-with-list   {:review-officers-list-enabled true
                       :id                           "753-R"
                       :reviewOfficers               [(make-officer 0 "Simo Suurvisiiri")
                                                      (make-officer 1 "Ronja Sibbo")
                                                      (make-officer 2 "Sonja Sibbo")
                                                      (make-officer 3 "Petja Hibbo")]}
      non-list-org    (dissoc org-with-list :review-officers-list-enabled)
      update-with-org #(update-task-reviewer! {:application  {:id           "app-id-0"
                                                              :organization "753-R"
                                                              :permitType   "R"
                                                              :state        "constructionStarted"
                                                              :tasks        [{:id          "task-id-0"
                                                                              :state       "requires_user_action"
                                                                              :schema-info {:name "task-katselmus"}
                                                                              :data        {:katselmus {:pitaja {:value "Repe Reviewer"}}}}
                                                                             {:id          "task-id-1"
                                                                              :state       "requires_user_action"
                                                                              :schema-info {:name "task-katselmus"}
                                                                              :data        {:katselmus {}}}
                                                                             {:id          "task-id-2"
                                                                              :state       "requires_user_action"
                                                                              :schema-info {:name "task-katselmus"}
                                                                              :data        {:katselmus {:pitaja {:value ""}}}}
                                                                             {:id          "task-id-3"
                                                                              :state       "requires_user_action"
                                                                              :schema-info {:name "task-katselmus"}
                                                                              :data        {:katselmus {:pitaja {:value (make-officer 3 "Petja Hibbo")}}}}]}
                                               :created      12345
                                               :organization (delay %1)
                                               :user         (find-user-from-minimal-by-apikey sonja)}
                                              %2)]
  (fact update-task-reviewer!
    ;; Return the set reviewer from the update calls so we can check if it matches expectations
    (against-background [(action/update-application anything anything
                                                    {"$set" {:tasks.$.data.katselmus.pitaja {:value    (make-officer 2 "Sonja Sibbo")
                                                                                             :modified 12345}
                                                             :modified                      12345}})
                         => :sonja-map
                         (action/update-application anything anything
                                                    {"$set" {:tasks.$.data.katselmus.pitaja {:value    (make-officer 1 "Ronja Sibbo")
                                                                                             :modified 12345}
                                                             :modified                      12345}})
                         => :ronja-map
                         (action/update-application anything anything
                                                    {"$set" {:tasks.$.data.katselmus.pitaja {:value    "Sonja Sibbo"
                                                                                             :modified 12345}
                                                             :modified                      12345}})
                         => :sonja-string
                         (action/update-application anything anything
                                                    {"$set" {:tasks.$.data.katselmus.pitaja {:value    nil
                                                                                             :modified 12345}
                                                             :modified                      12345}})
                         => :reviewer-cleared])

    (facts "with officer list"
      (fact "won't update if there is already a known reviewer"
        (update-with-org org-with-list "task-id-3") => nil)
      (fact "will update if the current value is (no longer?) supported"
        (update-with-org org-with-list "task-id-0") => :sonja-map)
      (fact "will update if there is no reviewer at all"
        (update-with-org org-with-list "task-id-1") => :sonja-map)
      (fact "will update if there is a blank reviewer field"
        (update-with-org org-with-list "task-id-2") => :sonja-map)
      (fact "will update with closest matching on list if no exact match found"
        (update-with-org (update org-with-list :reviewOfficers #(util/drop-nth 2 %)) "task-id-1") => :ronja-map)
      (fact "will clear reviewer if no match found"
        (update-with-org (assoc org-with-list :reviewOfficers []) "task-id-3") => :reviewer-cleared))

    (facts "without officer list"
      (fact "won't update if there is already a reviewer"
        (update-with-org non-list-org "task-id-0") => nil)
      (fact "will update if there is no reviewer"
        (update-with-org non-list-org "task-id-1") => :sonja-string)
      (fact "will update if there is a blank reviewer field"
        (update-with-org non-list-org "task-id-2") => :sonja-string))))
