(ns lupapalvelu.pate.pate-replacement-verdict-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :as core]
            [sade.shared-util :as util]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))

(defn check-verdict-task-count [app-id verdict-id task-count]
  (facts {:midje/description (str "Verdict task count is " task-count)}
        (->> (query-application sonja app-id)
             :tasks
             (filter #(= (-> % :source :id) verdict-id))
             count) => task-count))

(facts "PATE verdict replacement"

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (facts "Publish and replace verdict"
    (let [template-id              (-> pate-fixture/verdict-templates-setting-r :templates first :id)
          {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                            :id app-id
                                            :template-id template-id)]
      (fact "User summary is Sonja"
        (-> (query-application sonja  app-id)
            :pate-verdicts last :state :_user)
        => "sonja")
      (fact "Set automatic calculation of other dates"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:automatic-verdict-dates] :value true) => no-errors?)
      (fact "Verdict date"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-date] :value (core/now)) => no-errors?)
      (fact "Verdict code"
        (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id
                 :path [:verdict-code] :value "hyvaksytty") => no-errors?)

      (fact "Draft cannot be replaced"
        (command sonja :new-pate-verdict-draft :id app-id
                 :template-id template-id
                 :replacement-id verdict-id)=> fail?)
      (fact "Pseudo query also fails"
        (query sonja :replace-pate-verdict :id app-id
               :verdict-id verdict-id) => fail?)

      (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => no-errors?

      (check-verdict-task-count app-id verdict-id 6)

      (facts "Replacement verdict"
        (fact "First replacement draft"
          (fact "Pseudo query succeeds"
            (query sonja :replace-pate-verdict :id app-id
                   :verdict-id verdict-id) => ok?)
          (let [{vid1 :verdict-id} (command sonja :new-pate-verdict-draft :id app-id
                                            :template-id template-id
                                            :replacement-id verdict-id)
                =>                 ok?]
            (fact "Appeal is not allowed for replaced verdict"
              (command sonja :upsert-pate-appeal :id app-id
                       :verdict-id verdict-id
                       :type "appeal"
                       :author "One"
                       :datestamp 12345
                       :filedatas []) => (err :error.verdict-replaced))
            (fact "Only one replacement draft at the time"
              (command sonja :new-pate-verdict-draft :id app-id
                 :template-id template-id
                 :replacement-id verdict-id) => fail?)
            (fact "Delete the only replacement draft"
              (command sonja :delete-pate-verdict :id app-id
                       :verdict-id vid1) => ok?)
            (fact "Appeal is now allowed"
              (command sonja :upsert-pate-appeal :id app-id
                       :verdict-id verdict-id
                       :type "appeal"
                       :author "One"
                       :datestamp 12345
                       :filedatas []) => ok?)))
        (check-verdict-task-count app-id verdict-id 6)

        (let [{replacement-verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                            :id app-id
                                                            :template-id template-id
                                                            :replacement-id verdict-id)]

          (fact "Fill replacement verdict"
            (command sonja :edit-pate-verdict :id app-id :verdict-id replacement-verdict-id
                     :path [:automatic-verdict-dates] :value true) => no-errors?
            (command sonja :edit-pate-verdict :id app-id :verdict-id replacement-verdict-id
                     :path [:verdict-date] :value (core/now)) => no-errors?
            (command sonja :edit-pate-verdict :id app-id :verdict-id replacement-verdict-id
                     :path [:verdict-code] :value "hyvaksytty") => no-errors?)

          (fact "Only replacement verdict have replacement information before publishing"
            (let [application (query-application sonja app-id)
                  old-verdict (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))
                  new-verdict (first (filter #(= replacement-verdict-id (:id %)) (:pate-verdicts application)))]
              (:replacement old-verdict) => nil?
              (get-in new-verdict [:replacement :replaces]) => verdict-id))

          (fact "Publish replacement verdict"
            (command sonja :publish-pate-verdict
                     :id app-id
                     :verdict-id replacement-verdict-id) => ok?)

          (fact "Appeal is not allowed for replaced verdict"
              (command sonja :upsert-pate-appeal :id app-id
                       :verdict-id verdict-id
                       :type "appeal"
                       :author "One"
                       :datestamp 12345
                       :filedatas []) => (err :error.verdict-replaced))

          (fact "Verdicts have replacement information"
            (let [application (query-application sonja app-id)
                  old-verdict (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))
                  new-verdict (first (filter #(= replacement-verdict-id (:id %)) (:pate-verdicts application)))]
              (get-in old-verdict [:replacement :replaced-by]) => replacement-verdict-id
              (get-in new-verdict [:replacement :replaces]) => verdict-id))

          (fact "Old verdict tasks are gone"
            (check-verdict-task-count app-id verdict-id 0)
            (check-verdict-task-count app-id replacement-verdict-id 6))

          (facts "Copy verdict as base of replacement verdict"
            (let [result      (command sonja :copy-pate-verdict-draft
                                       :id app-id
                                       :replacement-id replacement-verdict-id)
                  application (query-application sonja app-id)
                  verdict-id  (:verdict-id result)
                  old-verdict (util/find-by-id replacement-verdict-id (:pate-verdicts application))
                  new-verdict (util/find-by-id verdict-id (:pate-verdicts application))]

              (fact "Make draft from old verdict"
                result => ok?
                (:verdict-id result) => string?)

              (fact "Copied draft contains same data as old one"
                (get-in new-verdict [:data :address :_value]) =>  (get-in old-verdict [:data :address])
                (get-in new-verdict [:data :anto :_value]) =>  (get-in old-verdict [:data :anto])
                (get-in new-verdict [:data :bulletinOpDescription :_value]) =>  (get-in old-verdict [:data :bulletinOpDescription])
                (get-in new-verdict [:data :foremen :_value]) =>  (get-in old-verdict [:data :foremen])
                (get-in new-verdict [:data :julkipano :_value]) =>  (get-in old-verdict [:data :julkipano])
                (get-in new-verdict [:data :lainvoimainen :_value]) =>  (get-in old-verdict [:data :lainvoimainen])
                (get-in new-verdict [:data :language :_value]) =>  (get-in old-verdict [:data :language])
                (get-in new-verdict [:data :neighbor-states :_value]) =>  (get-in old-verdict [:data :neighbor-states])
                (get-in new-verdict [:data :operation :_value]) =>  (get-in old-verdict [:data :operation])
                (get-in new-verdict [:data :plans :_value]) =>  (get-in old-verdict [:data :plans])
                (get-in new-verdict [:data :reviews :_value]) =>  (get-in old-verdict [:data :reviews])
                (get-in new-verdict [:data :statements :_value]) =>  (get-in old-verdict [:data :statements])
                (get-in new-verdict [:data :verdict-code :_value]) =>  (get-in old-verdict [:data :verdict-code])
                (get-in new-verdict [:data :verdict-date :_value]) =>  (get-in old-verdict [:data :verdict-date])
                (get-in new-verdict [:data :verdict-text :_value]) =>  (get-in old-verdict [:data :verdict-text]))

              (facts "Templates are identical"
                (:template new-verdict) =>  (:template old-verdict))

              (fact "Section is cleared"
                (get-in old-verdict [:data :verdict-section]) => "2"
                (get-in new-verdict [:data :verdict-section]) => nil)

              (fact "Template is same"
                (:template new-verdict) => (:template old-verdict))

              (fact "Id and modified ts is different"
                (:id new-verdict) =not=> (:id old-verdict)
                (:modified new-verdict) =not=> (:modified old-verdict))

              (fact "Copied verdict draft can be published"
                (command sonja :publish-pate-verdict
                         :id app-id
                         :verdict-id (:verdict-id result)) => ok?)

              (fact "Verdicts have replacement information"
                (let [application (query-application sonja app-id)
                      old-verdict (util/find-by-id replacement-verdict-id (:pate-verdicts application))
                      new-verdict (util/find-by-id verdict-id (:pate-verdicts application))]
                  (get-in old-verdict [:replacement :replaced-by]) => verdict-id
                  (get-in new-verdict [:replacement :replaces]) => replacement-verdict-id))
              (fact "Replaced verdict cannot be deleted"
                (command sonja :delete-pate-verdict :id app-id
                         :verdict-id replacement-verdict-id)
                => (err :error.verdict-replaced))
              (fact "The replacing verdict can be deleted"
                (command sonja :delete-pate-verdict :id app-id
                         :verdict-id verdict-id) => ok?)
              (fact "Replacement information has been removed from the replaced verdict"
                (->> (query-application sonja app-id)
                     :pate-verdicts
                     (util/find-by-id replacement-verdict-id)
                     :replacement) => nil)
              (fact "Thus, the older verdict can be deleted now as well"
                (command sonja :delete-pate-verdict :id app-id
                         :verdict-id replacement-verdict-id) => ok?))))))))
