(ns lupapalvelu.pate.pate-reviews-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.shared-util :as shared-util]
            [sade.util :as util]))

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
                                          :template-id (-> pate-fixture/verdict-templates-setting-r
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
        {:keys [primaryOperation
                buildings]}      (query-application pena app-id)]
    (fact "Buildings empty"                                 ; they could be populated, but not implemented yet
      buildings => empty?)
    (fact "Verdict draft creation OK"
      verdict-draft => ok?)
    (fact "Verdict data from minimal (+ handler, operator and address)"
      verdict-data => (just {:handler                 "Sonja Sibbo"
                             :address                 "Test Road 8"
                             :bulletin-op-description ""
                             :operation               ""
                             :deviations              ""
                             :buildings               {(keyword (:id primaryOperation)) {:building-id   ""
                                                                                         :description   ""
                                                                                         :operation     "varasto-tms"
                                                                                         :order         "0"
                                                                                         :show-building true
                                                                                         :tag           ""}}
                             :verdict-text            "Ver Dict"
                             :plans                   (just plan-ids :in-any-order)
                             :foremen                 ["vastaava-tj"]
                             :plans-included          true
                             :language                "fi"
                             :reviews                 (just review-ids :in-any-order)
                             :foremen-included        true
                             :neighbor-states         []
                             :reviews-included        true
                             :statements              []}))
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
      (fact "Set automatic calculation and verdict-date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?
        (fact "date is today"
          (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                   :path [:verdict-date] :value (now)) => no-errors?))
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?)
      (add-verdict-attachment sonja app-id verdict-id "Hello"))

    (fact "Only Aloituskokous and Loppukatselmus are used in verdict"
      (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id ; removes 'Katselmus'
               :path [:reviews]
               :value [(find-review-id "Aloituskokous")
                       (find-review-id "Loppukatselmus")]) => no-errors?)
    (fact "There is only one verdict"
      (:verdicts (query sonja :pate-verdicts :id app-id))
      => (just [(contains {:id        verdict-id
                           :category  "r"
                           :legacy?   false
                           :proposal? false
                           :replaced? false
                           :title     "Luonnos"})]))
    (fact "Publish PATE verdict with two reviews"
      (command sonja :publish-pate-verdict :id app-id
               :verdict-id verdict-id) => no-errors?)
    (verdict-pdf-queue-test sonja {:app-id app-id :verdict-id verdict-id})
    (facts "Reviews have been created"                      ;
      (let [{:keys [tasks attachments]} (query-application pena app-id)
            reviews                     (filter #(= "task-katselmus" (get-in % [:schema-info :name])) tasks)
            aloituskokous               (util/find-by-key :taskname "Aloituskokous" reviews)
            loppukatselmus              (util/find-by-key :taskname "Loppukatselmus" reviews)]
        (fact "two attachments"
          attachments => (just [(contains {:target {:type "verdict" :id verdict-id}})
                                (contains {:source {:type "verdicts" :id verdict-id}})]))
        (fact "two reviews" (count reviews) => 2)
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
      (let [plans     (->> (query-application pena app-id)
                           :tasks
                           (filter #(= "task-lupamaarays" (get-in % [:schema-info :name]))))
            find-plan #(util/find-by-key :taskname % plans)
            plain     (find-plan "Suunnitelmat")
            special   (find-plan "ErityisSuunnitelmat")]
        (fact "Two plans"
          plans => (just [plain special] :in-any-order))
        (fact "Generated from verdict"
          (:source plain) => {:type "verdict" :id verdict-id}
          (:source special) => {:type "verdict" :id verdict-id})))

    (facts "Backing system and Pate-verdicts can co-exist"
      (fact "Add appeal to verdict"
        (command sonja :upsert-pate-appeal :id app-id
                 :verdict-id verdict-id
                 :type "appeal"
                 :author "A"
                 :datestamp 12345
                 :text "Appeal"
                 :filedatas []) => ok?)
      (fact "Add appeal verdict to verdict"
        (command sonja :upsert-pate-appeal :id app-id
                 :verdict-id verdict-id
                 :type "appealVerdict"
                 :author "AV"
                 :datestamp 54321
                 :text "Appeal verdict"
                 :filedatas []) => ok?)
      (fact "Current status before checking verdict"
        (let [{:keys [tasks attachments appeals appealVerdicts
                      pate-verdicts verdicts]} (query-application sonja app-id)]
          (count tasks) => 5 ;; two plans, two reviews, one foreman
          (count attachments) => 2
          (count appeals) => 1
          (count appealVerdicts) => 1
          (count pate-verdicts) => 1
          (count verdicts) => 0))

      (fact "Fetch new verdict from the backing system"
        (command sonja :check-for-verdict :id app-id) => ok?
        (let [{:keys [tasks attachments appeals appealVerdicts
                      pate-verdicts verdicts]} (query-application sonja app-id)]
          (count tasks) => (+ 5 9) ;; Additions: three plans, three reviews, three foremen
          (count attachments) => (+ 2 2)
          (count appeals) => 1
          (count appealVerdicts) => 1
          (count pate-verdicts) => 1
          (count verdicts) => 2
          (fact "Add appeal to fetched verdicts"
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id (-> verdicts first :id)
                     :type "appeal"
                     :author "F1 A"
                     :datestamp 45454
                     :text "Fetch 1 appeal"
                     :filedatas []) => ok?
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id (-> verdicts last :id)
                     :type "appeal"
                     :author "F2 A"
                     :datestamp 56565
                     :text "Fetch 2 appeal"
                     :filedatas []) => ok?)
          (fact "Add appeal verdicts to fetched verdicts"
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id (-> verdicts first :id)
                     :type "appealVerdict"
                     :author "F1 AV"
                     :datestamp 89898
                     :text "Fetch 1 appeal verdict"
                     :filedatas []) => ok?
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id (-> verdicts last :id)
                     :type "appealVerdict"
                     :author "F2 AV"
                     :datestamp 78787
                     :text "Fetch 2 appeal verdict"
                     :filedatas []) => ok?)
          (fact "Check appeals and appeal verdicts count"
            (let [{:keys [appeals appealVerdicts]} (query-application sonja app-id)]
              (count appeals) => 3
              (count appealVerdicts) => 3))))
      (fact "Fetch verdict again"
        (command sonja :check-for-verdict :id app-id) => ok?
        (let [{:keys [tasks attachments appeals appealVerdicts
                      pate-verdicts verdicts]} (query-application sonja app-id)]
          (count tasks) => 14
          (count attachments) => 4
          (count appeals) => 1
          (count appealVerdicts) => 1
          (count pate-verdicts) => 1
          (count verdicts) => 2))
      (fact "New Pate verdict draft can be created"
        (command sonja :new-pate-verdict-draft
                 :id app-id
                 :template-id (-> pate-fixture/verdict-templates-setting-r
                                  :templates
                                  first
                                  :id)) => ok?)
      (fact "New legacy draft cannot be created"
        (command sonja :new-legacy-verdict-draft :id app-id)
        => (err :error.invalid-category))
      (fact "Disable Pate in 753-R and try again"
        (toggle-pate "753-R" false)
        (command sonja :new-legacy-verdict-draft :id app-id)
        => ok?)
      (facts "Deleting backend verdicts does not rewind the application state"
        (fact "Delete backend verdicts"
          (let [{:keys [verdicts]} (query-application sonja app-id)]
            (command sonja :delete-verdict
                     :id app-id
                     :verdict-id (-> verdicts first :id)) => ok?
            (command sonja :delete-verdict
                     :id app-id
                     :verdict-id (-> verdicts last :id)) => ok?))
        (fact "Check status"
          (let [{:keys [tasks attachments appeals appealVerdicts
                        pate-verdicts verdicts state]} (query-application sonja app-id)]
          (count tasks) => 5
          (count attachments) => 2
          (count appeals) => 1
          (count appealVerdicts) => 1
          (count pate-verdicts) => 3 ;; Published, draft, legacy draft
          (count verdicts) => 0
          state => "verdictGiven"))
        (fact "Delete Pate verdict"
          (command sonja :delete-pate-verdict :id app-id
                   :verdict-id verdict-id) => ok?)
        (fact "Check status for one final time"
          (let [{:keys [tasks attachments appeals appealVerdicts
                        pate-verdicts verdicts state]} (query-application sonja app-id)]
          (count tasks) => 0
          (count attachments) => 0
          (count appeals) => 0
          (count appealVerdicts) => 0
          (count pate-verdicts) => 2 ;; Draft, legacy draft
          (count verdicts) => 0
          (fact "Application state has been rewound"
            state => "sent")))))))
