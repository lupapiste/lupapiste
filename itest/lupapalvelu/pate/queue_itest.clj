(ns lupapalvelu.pate.queue-itest
  "Pate message queue resilience tests."
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.verdict :as verdict]
            [lupapalvelu.pate.verdict-interface :as vif]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all])
  (:import [java.util.concurrent Executors]))

(testable-privates lupapalvelu.pate.pdf
                   can-attach-verdict-pdf?
                   upload-and-attach-pdf!)

(testable-privates lupapalvelu.pate.verdict
                   pdf--verdict)

(mongo/connect!)
(mongo/with-db test-db-name
  (fixture/apply-fixture "minimal")
  (facts "Message queue"
    (let [{app-id :id}         (create-and-submit-local-application pena
                                                                    :propertyId sipoo-property-id
                                                                    :operation "pientalo")
          {:keys [verdict-id]} (local-command sonja :new-legacy-verdict-draft
                                              :id app-id)
          no-attachments       (fn []
                                 (fact "No attachments"
                                   (:attachments (query-application local-query sonja app-id))
                                   => empty?))]
      (no-attachments)
      (facts "Create and fill local legacy verdict"
        (->> (merge {:kuntalupatunnus "888-10-12"
                     :verdict-code    "1" ;; Granted
                     :verdict-text    "Lorem ipsum"
                     :handler         "Decider"
                     :anto            (timestamp "21.5.2018")
                     :lainvoimainen   (timestamp "30.5.2018")})
             (into [])
             (apply concat)
             (apply (partial local-fill-verdict sonja app-id verdict-id)))
        (no-attachments)

        (verdict/send-command nil nil)
        (verdict/send-command ::verdict/verdict {})
        (verdict/send-command ::verdict/verdict {:data {:id app-id}})
        (verdict/send-command ::verdict/verdict {:data {:id         "bad-app-id"
                                                        :verdict-id verdict-id}})
        (verdict/send-command nil {:data {:id         "bad-app-id"
                                          :verdict-id verdict-id}})
        (verdict/send-command :bad-mode {:data {:id         "bad-app-id"
                                                :verdict-id verdict-id}})
        (verdict/send-command ::verdict/verdict {:data {:verdict-id verdict-id}})
        (verdict/send-command ::verdict/verdict {:data {:id         app-id
                                                        :verdict-id "bad-verdict-id"}})
        (verdict/send-command ::verdict/verdict {:data {:id         "bad-app-id"
                                                        :verdict-id "bad-verdict-id"}})
        (verdict/send-command ::verdict/verdict {:data {:id         app-id
                                                        :verdict-id verdict-id}})

        (local-command sonja :publish-legacy-verdict
                       :id app-id
                       :verdict-id verdict-id) => ok?
        (verdict/send-command ::verdict/verdict {:data {:id         app-id
                                                        :verdict-id verdict-id}})
        (loop [tries 0]
          (if (= tries 10)
            (fact "Attachment not created. Giving up."
              true => false)
            (let [application (query-application local-query sonja app-id)]
              (if-let [att-id (some-> (vif/find-verdict application verdict-id)
                                      :published
                                      :attachment-id)]
                (fact "Proper attachment and nothing else"
                  (:attachments application)
                  => (just [(contains {:id       att-id
                                       :contents "Päätös"
                                       :source   {:type "verdicts"
                                                  :id   verdict-id}})]))
                (do (Thread/sleep 1000)
                    (recur (inc tries))))))))

      (let [application (query-application local-query sonja app-id)
            verdict     (vif/find-verdict application verdict-id)]
        (fact "Verdict is published"
          (:published verdict) => (contains {:published     pos?
                                             :attachment-id string?}))
        (fact "Cannot generate another verdict attachment"
          (pdf/create-verdict-attachment {:application application
                                          :data        {:verdict-id verdict-id
                                                        :id         app-id}
                                          :user        (find-user-from-minimal "sonja")
                                          :created     12345}
                                         verdict) => nil)
        (fact "Cannot generate verdict attachment if the verdict no longer exists"
          (local-command sonja :delete-legacy-verdict :id app-id :verdict-id verdict-id)
          => ok?
          (pdf/create-verdict-attachment {:application application} verdict) => nil))))

  (facts "Attachment creation and timings"
    (let [{app-id :id}         (create-and-submit-local-application pena
                                                                    :propertyId sipoo-property-id
                                                                    :operation "pientalo")
          {:keys [verdict-id]} (local-command sonja :new-legacy-verdict-draft
                                              :id app-id)
          get-application      #(query-application local-query sonja app-id)
          get-verdict          #(vif/find-verdict (get-application)
                                                  verdict-id)]
      (->> (merge {:kuntalupatunnus "888-10-12"
                   :verdict-code    "1" ;; Granted
                   :verdict-text    "Lorem ipsum"
                   :handler         "Decider"
                   :anto            (timestamp "21.5.2018")
                   :lainvoimainen   (timestamp "30.5.2018")})
           (into [])
           (apply concat)
           (apply (partial local-fill-verdict sonja app-id verdict-id)))
      (fact "Can attach? Not published"
        (can-attach-verdict-pdf? app-id verdict-id) => falsey)
      (fact "Publish the verdict"
        (local-command sonja :publish-legacy-verdict
                       :id app-id
                       :verdict-id verdict-id) => ok?)
      (fact "Can attach? Eventually fails"
        (loop [tries 20]
          (Thread/sleep 1000)
          (cond
            (zero? tries)
            (fact "Attachment not created. Giving up." true => false)

            (not (can-attach-verdict-pdf? app-id verdict-id)) true

            :else (recur (dec tries))))
        => true)
      (let [{:keys [attachments]
             :as   application} (get-application)]
        (fact "There is only one attachment"
          attachments => (just [(contains {:source {:id   verdict-id
                                                    :type "verdicts"}})]))
        (fact "Delete attachment and 'rewind' verdict"
          (att/delete-attachments! application (map :id attachments))
          (mongo/update-by-id :applications app-id
                              {$unset {:pate-verdicts.0.published.attachment-id true}})
          (:attachments (get-application))
          => empty?
          (-> (get-verdict) :published :attachment-id) => nil))
      (facts "Simulate race condition poorly."
        (let [verdict (get-verdict)
              dbname  mongo/*db-name*
              command {:application (get-application)
                       :data        {:verdict-id verdict-id
                                     :id         app-id}
                       :user        (find-user-from-minimal "sonja")
                       :created     12345}
              pool    (Executors/newFixedThreadPool 4)
              tasks   (map
                       (fn [i]
                         (fn []
                           (mongo/with-db dbname
                             ;; Without some differing delay the
                             ;; threads could still update mongo
                             ;; erroneously (window of "opportunity" is
                             ;; between checking and attaching). We
                             ;; assume optimistically that this never
                             ;; happens in the production.
                             (Thread/sleep (* i 200))
                             (pdf--verdict command verdict))))
                       (range 4))]
          (doseq [future (.invokeAll pool tasks)]
            (.get future))
          (.shutdown pool)
          (fact "There is only one attachment and it is verdict"
            (let [{:keys [attachments
                          pate-verdicts]} (get-application)]
              attachments => (just [(contains {:source {:id   (-> pate-verdicts first :id)
                                                        :type "verdicts"}})])
              (-> pate-verdicts first :published :attachment-id)
              => (-> attachments first :id))))))))
