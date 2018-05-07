(ns lupapalvelu.pate.pate-reviews-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.shared-util :as shared-util]
            [sade.util :as util]
            [clojure.set :as set]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :varasto-tms
                           :address "Test Road 8"))

(facts "PATE reviews"

  (fact "submit" (command pena :submit-application :id app-id) => ok?)
  (fact "bulletinOpDescription..."
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bullet the blue sky.") => ok?)
  (fact "Sonja approves" (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                          :id app-id
                                          :template-id (-> pate-fixture/verdic-templates-setting
                                                           :templates
                                                           first
                                                           :id))
        verdict-draft            (query sonja :pate-verdict
                                        :id app-id
                                        :verdict-id verdict-id)
        verdict-data             (get-in verdict-draft [:verdict :data])
        references               (:references verdict-draft)
        ;; Every plan and review is selected in the fixture
        plan-ids                 (->> references :plans (map :id))
        review-ids               (->> references :reviews (map :id))
        find-review-id           #(:id (util/find-by-key :fi % (:reviews references)))
        find-plan-id             #(:id (util/find-by-key :fi % (:plans references)))
        {:keys [primaryOperation
                buildings]}      (query-application pena app-id)]
    (fact "Buildings empty"                                 ; they could be populated, but not implemented yet
      buildings => empty?)
    (fact "Verdict draft creation OK"
      verdict-draft => ok?)
    (fact "Verdict data from minimal (+ handler, operator and address)"
      verdict-data => (just {:handler               "Sonja Sibbo"
                             :address               "Test Road 8"
                             :bulletinOpDescription ""
                             :operation             ""
                             :deviations            ""
                             :buildings             {(keyword (:id primaryOperation)) {:building-id   ""
                                                                                       :description   ""
                                                                                       :operation     "varasto-tms"
                                                                                       :order         "0"
                                                                                       :show-building true
                                                                                       :tag           ""}}
                             :verdict-text          "Ver Dict"
                             :plans                 (just plan-ids :in-any-order)
                             :foremen               ["vastaava-tj"]
                             :plans-included        true
                             :language              "fi"
                             :reviews               (just review-ids :in-any-order)
                             :foremen-included      true
                             :neighbor-states       []
                             :reviews-included      true
                             :statements            []}))
    (fact "while preparing verdict, VTJ-PRT and location is updated to application"
      (api-update-building-data-call app-id {:form-params  {:operationId        (:id primaryOperation)
                                                            :nationalBuildingId "1234567881"
                                                            :location           {:x 406216.0 :y 6686856.0}}
                                             :content-type :json
                                             :as           :json
                                             :basic-auth   ["sipoo-r-backend" "sipoo"]}) => http200?
      (let [app (query-application sonja app-id)]
        (fact "VTJ-PRT in document"
          (->> (:documents app)
               (shared-util/find-first #(= (:id primaryOperation) (get-in % [:schema-info :op :id])))
               :data :valtakunnallinenNumero :value) => "1234567881")
        (fact "Buildings still empty"
              (:buildings app) => empty?)))

    (facts "fill required data"
      (fact "Set automatic calucation and verdict-date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?
        (fact "date is today"
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                   :path [:verdict-date] :value (now)) => no-errors?))
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?))

    (fact "Only Aloituskokous and Loppukatselmus are used in verdict"
      (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id ; removes 'Katselmus'
               :path [:reviews]
               :value [(find-review-id "Aloituskokous")
                       (find-review-id "Loppukatselmus")]) => no-errors?)
    (fact "Publish PATE verdict with two reviews"
      (command sonja :publish-pate-verdict :id app-id
               :verdict-id verdict-id) => no-errors?)
    (facts "Reviews have been created"                      ;
      (let [{:keys [tasks]} (query-application pena app-id)
            reviews         (filter #(= "task-katselmus" (get-in % [:schema-info :name])) tasks)
            aloituskokous   (first reviews)
            loppukatselmus  (second reviews)]
        (fact "two" (count reviews) => 2)
        (facts "aloituskokous"
          (fact "katselmuksenLaji" (get-in aloituskokous [:data :katselmuksenLaji :value]) => "aloituskokous")
          (fact "taskname correct" (:taskname aloituskokous)
                => (get (->> verdict-draft
                             :references
                             :reviews
                             (util/find-by-id (find-review-id "Aloituskokous")))
                        (keyword (:language verdict-data))))
          (fact "vaadittuLupaehtona" (get-in aloituskokous [:data :vaadittuLupaehtona :value]) =>  true)
          (fact "Buildings"
            (fact "has one" (count (keys (get-in aloituskokous [:data :rakennus]))) => 1)
            (fact "VTJ-PRT" (get-in aloituskokous [:data :rakennus :0 :rakennus :valtakunnallinenNumero :value]) => "1234567881")))
        (facts "loppukatselmus"
          (fact "katselmuksenLaji" (get-in loppukatselmus [:data :katselmuksenLaji :value]) => "loppukatselmus")
          (fact "taskname correct" (:taskname loppukatselmus)
                => (get (->> verdict-draft
                             :references
                             :reviews
                             (util/find-by-id (find-review-id "Loppukatselmus")))
                        (keyword (:language verdict-data))))
          (fact "vaadittuLupaehtona" (get-in loppukatselmus [:data :vaadittuLupaehtona :value]) =>  true)
          (fact "Buildings"
            (fact "has one" (count (keys (get-in loppukatselmus [:data :rakennus]))) => 1)
            (fact "VTJ-PRT" (get-in loppukatselmus [:data :rakennus :0 :rakennus :valtakunnallinenNumero :value]) => "1234567881")))
        #_(fact "can't delete because vaadittuLupaehtona"   ; TODO check this with dosent
            (command sonja :delete-task :id app-id :taskId (:id aloituskokous)) => (partial expected-failure? :error.task-is-required))))
    (facts "Plans have been created"
      (let [plans (->> (query-application pena app-id)
                           :tasks
                           (filter #(= "task-lupamaarays" (get-in % [:schema-info :name]))))
            find-plan #(util/find-by-key :taskname % plans)
            plain     (find-plan "Suunnitelmat")
            special   (find-plan "ErityisSuunnitelmat")]
        (fact "Two plans"
          plans => (just [plain special] :in-any-order))
        (fact "Generated from verdict"
          (:source plain) => {:type "verdict" :id verdict-id}
          (:source special) => {:type "verdict" :id verdict-id})))))
