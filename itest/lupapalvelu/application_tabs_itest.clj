(ns lupapalvelu.application-tabs-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(facts "Application tab pseudo queries"
       (let [{app-id :id} (create-and-submit-application pena
                                                         :propertyId sipoo-property-id
                                                         :operation "pientalo")]
         (fact "Authority invites statement giver"
               (command sonja :request-for-statement :id app-id
                        :functionCode nil
                        :selectedPersons [{:email (email-for-key mikko)
                                           :name "Mikko"
                                           :text "Ni hao!"}]) => ok?)

         (defn check-queries [& {:keys [tasks info summary]}]
           (doseq [username ["pena" "sonja" "mikko@example.com" "luukas"]
                 :let [apikey (apikey-for username)]]
             (facts {:midje/description (str username " pseudo queries")}
                    (fact "Tasks tab"
                          (query apikey :tasks-tab-visible :id app-id)
                          => (if tasks ok? fail?))
                    (fact "Info tab "
                          (query apikey :application-info-tab-visible :id app-id)
                          => (if info ok? fail?))
                    (fact "Summary tab "
                          (query apikey :application-summary-tab-visible :id app-id)
                          => (if summary ok? fail?)))))
         (fact "Tabs pre-verdict"
               (check-queries :tasks false :info true :summary false))
         (fact "Tabs pre-verdict canceled"
               (command pena :cancel-application :id app-id :lang "fi" :text "") => ok?
               (check-queries :tasks false :info true :summary false))
         (fact "Authority reverts cancellation and gets verdict"
               (command sonja :undo-cancellation :id app-id) => ok?
               (command sonja :check-for-verdict :id app-id) => ok?)
         (fact "Tabs post-verdict"
               (check-queries :tasks true :info false :summary true))
         (fact "Tabs post-verdict canceled"
               (command sonja :cancel-application-authority :id app-id :lang "fi" :text "") => ok?
               (check-queries :tasks false :info false :summary true))))
