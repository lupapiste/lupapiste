(ns lupapalvelu.asiointi.notifications.notify-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.asiointi.notifications.common :as common]
            [lupapalvelu.asiointi.notifications.notification :as notification]
            [lupapalvelu.asiointi.notifications.notify :as notify]
            [lupapalvelu.asiointi.notifications.registration :as registration]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [sade.util :as util])
  (:import [java.util Date]
           [org.bson.types ObjectId]))

(set! *warn-on-reflection* true)

(use-fixtures :each (itest/local-fixture-factory (-> {}
                                                     test-system/with-remote-components)))

(def ctx {:url-host-whitelist     #{"localhost"}
          :url-protocol-whitelist #{"http"}})

(defn- date?
  [v]
  (instance? Date v))

(defn- all-notifications
  []
  (mongo/select :asiointiapi-notifications {}))

(defn- send-and-verify-notification!
  [notification]
  ;; Use our own "mock" notification API to "receive" the notification
  ;; Our API doesn't really do anything, it should just return 204 if the notification
  ;; was valid (= it coerced correctly).
  (let [url                   (format "%s/asiointi-notifications/api/notify" (itest/server-address))
        {:keys [status body]} (itest/http-post url {:throw-exceptions false
                                                    :content-type     :json
                                                    :body             (json/encode notification)})]
    (is (= 204 status))
    (is (nil? body))))

(deftest notify-one-registration-twice-test
  ;; Happy path, where we queue two pubsub messages for a single registration
  ;; before either one is processed
  (let [organization-id "753-R"
        application-id  "LP-753-2025-90000"
        user            {:username "user"}
        *published      (atom [])
        *notified       (atom [])
        publisher       (fn [message]
                          (swap! *published conj message))
        notifier        (fn [registration content]
                          (send-and-verify-notification! content)
                          (swap! *notified conj [registration content])
                          true)
        permanent-id    (str (random-uuid))
        old-app         {:id               application-id
                         :organization     organization-id
                         :primaryOperation {:name "asuinrakennus"}
                         :ryhti            {:permanentPermitIdentifier permanent-id}
                         :state            "draft"}
        new-app         (assoc old-app :state "submitted")
        context         {:notification-type :application-state-change
                         :organization-id   organization-id
                         :publisher         publisher
                         :command           {:created     1771927054628
                                             :application old-app}
                         :new-app           new-app}]
    (is (set/superset? (set notify/application-projection-keys)
                       (set (keys new-app))))
    (is (= :ryhti/building (operations/application->control-case old-app)))

    (testing "initial state"
      (is (empty? (registration/org-id->registrations organization-id)))
      (is (empty? (all-notifications)))
      (is (empty? (deref *published))))

    (testing "try to publish with no registrations"
      (notify/create-and-publish-notifications! context)
      ;; in other words, if a tree falls in the forest and there are no registered
      ;; notification consumers, we will NOT store a notification about it.
      (testing "-> nothing happens"
        (is (empty? (registration/org-id->registrations organization-id)))
        (is (empty? (all-notifications)))
        (is (empty? (deref *published)))))

    (testing "register and publish two messages"
      (let [{registration-id :id
             :as             registration} (-> (util/mmap {:created 0
                                                           :target  {:url "http://localhost"}}
                                                          organization-id user)
                                               (registration/create! ctx)
                                               :registration)]

        ;; Two events happen in Lupapiste and we handle both before
        ;; receiving any pubsub messages
        (notify/create-and-publish-notifications! context)
        (notify/create-and-publish-notifications! context)

        (testing "two messages are published"
          (let [[first-message second-message :as messages] (deref *published)
                notification-id                             (ObjectId. ^String (:message-id first-message))
                notification-pre-delivery                   (notification/get-notification notification-id)]
            (is (= 2 (count messages)))
            (is (= (str registration-id)
                   (:registration-id first-message)))
            ;; Messages are identical as they both refer to the same notification!
            (is (= first-message
                   second-message))

            ;; Empty the pubsub queue
            (reset! *published [])

            ;; Just pending, delivery not yet attempted
            (is (match? (m/nested-equals
                          {:application-id      application-id
                           :control-case        :building
                           :created-at          date?
                           :extra-data          {:new-state :submitted
                                                 :old-state :draft}
                           :id                  notification-id
                           :notification-type   :application-state-change
                           :organization-id     organization-id
                           :pending             true
                           :permanent-permit-id permanent-id
                           :registration-id     registration-id})
                        notification-pre-delivery))

            (is (= notification-id
                   (notification/registration->pending-notification-id registration-id)))
            (is (= 2 (count (all-notifications))))

            (testing "submit first message to pubsub handler manually"
              ;; Pubsub handler receives the first message
              (is (true? (notify/handle-pubsub-message {:publisher publisher
                                                        :notifier  notifier}
                                                       first-message)))
              (testing "data supplied to the notifier"
                (is (match? (m/nested-equals [[registration
                                               {:application-id      application-id
                                                :control-case        :building
                                                :created-at          string?
                                                :extra-data          {:new-state :submitted
                                                                      :old-state :draft}
                                                :notification-id     (str notification-id)
                                                :notification-type   :application-state-change
                                                :organization-id     organization-id
                                                :permanent-permit-id permanent-id
                                                :registration-id     (str registration-id)}]])
                            (deref *notified)))
                (reset! *notified [])))

            (testing "verify notification after delivery"
              (let [notification-after-delivery (notification/get-notification notification-id)]
                (testing "first notification delivered, no longer pending"
                  (is (match? (m/nested-equals
                                {:application-id      application-id
                                 :control-case        :building
                                 :created-at          date?
                                 :delivered-at        date?
                                 :extra-data          {:new-state :submitted
                                                       :old-state :draft}
                                 :id                  notification-id
                                 :last-attempt-at     date?
                                 :notification-type   :application-state-change
                                 :organization-id     organization-id
                                 :permanent-permit-id permanent-id
                                 :registration-id     registration-id})
                              notification-after-delivery))

                  ;; Another notification was queued, so the publisher
                  ;; published another message
                  (let [[third-message :as messages] (deref *published)
                        previous-notification-id     notification-id
                        notification-id              (ObjectId. ^String (:message-id third-message))
                        notification-pre-delivery    (notification/get-notification notification-id)]
                    (is (= 1 (count messages)))
                    (is (not= previous-notification-id
                              notification-id))
                    (is (= notification-id
                           (notification/registration->pending-notification-id registration-id)))

                    ;; Again just pending, delivery not yet attempted
                    (is (match? (m/nested-equals
                                  {:application-id      application-id
                                   :control-case        :building
                                   :created-at          date?
                                   :extra-data          {:new-state :submitted
                                                         :old-state :draft}
                                   :id                  notification-id
                                   :notification-type   :application-state-change
                                   :organization-id     organization-id
                                   :pending             true
                                   :permanent-permit-id permanent-id
                                   :registration-id     registration-id})
                                notification-pre-delivery))

                    ;; receive the second(!) message, the third is still in pubsub queue
                    ;; this causes no effects, as the notification is already delivered
                    ;; we will regardless consume the message
                    (is (true? (notify/handle-pubsub-message {:publisher publisher
                                                              :notifier  notifier}
                                                             second-message)))

                    (testing "the second message is ignored, as the notification is already delivered"
                      (is (zero? (count (deref *notified)))))

                    (is (true? (notify/handle-pubsub-message {:publisher publisher
                                                              :notifier  notifier}
                                                             third-message)))

                    (testing "again, data supplied to the notifier"
                      (is (= 1 (count (deref *notified))))
                      (is (match? (m/nested-equals [[registration
                                                     {:application-id      application-id
                                                      :control-case        :building
                                                      :created-at          string?
                                                      :extra-data          {:new-state :submitted
                                                                            :old-state :draft}
                                                      :notification-id     (str notification-id)
                                                      :notification-type   :application-state-change
                                                      :organization-id     organization-id
                                                      :permanent-permit-id permanent-id
                                                      :registration-id     (str registration-id)}]])
                                  (deref *notified)))
                      (reset! *notified []))

                    ;; both notifications are now deliverd
                    (let [notification-after-delivery (notification/get-notification notification-id)]
                      (is (match? (m/nested-equals
                                    {:application-id      application-id
                                     :control-case        :building
                                     :created-at          date?
                                     :delivered-at        date?
                                     :extra-data          {:new-state :submitted
                                                           :old-state :draft}
                                     :id                  notification-id
                                     :last-attempt-at     date?
                                     :notification-type   :application-state-change
                                     :organization-id     organization-id
                                     :permanent-permit-id permanent-id
                                     :registration-id     registration-id})
                                  notification-after-delivery)))))))))))))

(deftest notify-two-registrations-once-test
  ;; Happy path, where we queue two pubsub messages for two registrations
  ;; before either one is processed
  (let [organization-id "753-R"
        application-id  "LP-753-2025-90000"
        user            {:username "user"}
        *published      (atom [])
        *notified       (atom [])
        publisher       (fn [message]
                          (swap! *published conj message))
        notifier        (fn [registration notification]
                          (swap! *notified conj [registration notification]))
        permanent-id    (str (random-uuid))
        old-app         {:id               application-id
                         :organization     organization-id
                         :primaryOperation {:name "poikkeamis"}
                         :ryhti            {:permanentPermitIdentifier permanent-id}
                         :state            "draft"}
        new-app         (assoc old-app :state "submitted")
        context         {:notification-type :application-state-change
                         :organization-id   organization-id
                         :publisher         publisher
                         :command           {:created     1771927054628
                                             :application old-app}
                         :new-app           new-app}]
    (is (set/superset? (set notify/application-projection-keys)
                       (set (keys new-app))))
    (is (= :ryhti/deviation (operations/application->control-case old-app)))

    (testing "register and publish two messages"
      (let [{registration-id-a :id
             :as               registration-a} (-> (util/mmap {:created 0
                                                               :target  {:url "http://localhost"}}
                                                              organization-id user)
                                                   (registration/create! ctx)
                                                   :registration)
            {registration-id-b :id
             :as               registration-b} (-> (util/mmap {:created 0
                                                               :target  {:url "http://localhost/another-one"}}
                                                              organization-id user)
                                                   (registration/create! ctx)
                                                   :registration)]

        (is registration-id-a)
        (is registration-id-b)

        ;; A single event happens in Lupapiste and we queue one message
        ;; for each registered consumer
        (notify/create-and-publish-notifications! context)

        (testing "two messages are published"
          (let [[message-a message-b :as messages] (deref *published)
                notification-id-a                  (ObjectId. ^String (:message-id message-a))
                notification-id-b                  (ObjectId. ^String (:message-id message-b))
                notification-pre-delivery-a        (notification/get-notification notification-id-a)
                notification-pre-delivery-b        (notification/get-notification notification-id-b)]
            (is (= 2 (count messages)))
            (doseq [[registration-id
                     notification-id
                     message] [[registration-id-a
                                notification-id-a
                                message-a]
                               [registration-id-b
                                notification-id-b
                                message-b]]]
              (is (= (str notification-id)
                     (:message-id message)))
              (is (= (str registration-id)
                     (:registration-id message))))
            ;; Messages are note identical as they target different consumers & notifications
            (is (not= message-a
                      message-b))

            ;; Empty the pubsub queue
            (reset! *published [])

            ;; Just pending, delivery not yet attempted
            (doseq [[notification-id
                     registration-id
                     notification-pre-delivery] [[notification-id-a
                                                  registration-id-a
                                                  notification-pre-delivery-a]
                                                 [notification-id-b
                                                  registration-id-b
                                                  notification-pre-delivery-b]]]
              (is (match? (m/nested-equals
                            {:application-id      application-id
                             :control-case        :deviation
                             :created-at          date?
                             :extra-data          {:new-state :submitted
                                                   :old-state :draft}
                             :id                  notification-id
                             :notification-type   :application-state-change
                             :organization-id     organization-id
                             :pending             true
                             :permanent-permit-id permanent-id
                             :registration-id     registration-id})
                          notification-pre-delivery))
              (is (= notification-id
                     (notification/registration->pending-notification-id registration-id))))

            (is (= 2 (count (all-notifications))))

            ;; handle both messages
            (doseq [[message
                     registration
                     notification-id] [[message-a
                                        registration-a
                                        notification-id-a]
                                       [message-b
                                        registration-b
                                        notification-id-b]]]
              (testing "submit message to pubsub handler manually"
                ;; Pubsub handler receives the message and delivers it
                (is (true? (notify/handle-pubsub-message {:publisher publisher
                                                          :notifier  notifier}
                                                         message)))
                (testing "data supplied to the notifier"
                  (is (match? (m/nested-equals [[registration
                                                 {:application-id      application-id
                                                  :control-case        :deviation
                                                  :created-at          string?
                                                  :extra-data          {:new-state :submitted
                                                                        :old-state :draft}
                                                  :notification-id     (str notification-id)
                                                  :notification-type   :application-state-change
                                                  :organization-id     organization-id
                                                  :permanent-permit-id permanent-id
                                                  :registration-id     (str (:id registration))}]])
                              (deref *notified)))
                  (reset! *notified [])))

              (testing "verify notification after delivery"
                (let [notification-after-delivery (notification/get-notification notification-id)]
                  (testing "notification delivered, no longer pending"
                    (is (match? (m/nested-equals
                                  {:application-id      application-id
                                   :control-case        :deviation
                                   :created-at          date?
                                   :delivered-at        date?
                                   :extra-data          {:new-state :submitted
                                                         :old-state :draft}
                                   :id                  notification-id
                                   :last-attempt-at     date?
                                   :notification-type   :application-state-change
                                   :organization-id     organization-id
                                   :permanent-permit-id permanent-id
                                   :registration-id     (:id registration)})
                                notification-after-delivery))
                    (testing "nothing new to publish for this registration"
                      (is (= [] (deref *published))))))))))))))

(deftest notification-type-test
  ;; Happy path, where we check every known notification type
  (doseq [notification-type common/notification-types]
    (let [organization-id                "753-R"
          application-id                 "LP-753-2025-90000"
          user                           {:username "user"}
          *published                     (atom [])
          *notified                      (atom [])
          publisher                      (fn [message]
                                           (swap! *published conj message))
          notifier                       (fn [registration content]
                                           (swap! *notified conj [registration content])
                                           true)
          permanent-id                   (str (random-uuid))
          old-app                        {:id               application-id
                                          :organization     organization-id
                                          :primaryOperation {:name "asuinrakennus"}
                                          :ryhti            {:permanentPermitIdentifier permanent-id}
                                          :state            "draft"}
          new-app                        (assoc old-app :state "submitted")
          context                        {:notification-type notification-type
                                          :organization-id   organization-id
                                          :publisher         publisher
                                          :command           {:created     1771927054628
                                                              :application old-app}
                                          :new-app           new-app}
          {registration-id :id
           :as             registration} (-> (util/mmap {:created 0
                                                         :target  {:url "http://localhost"}}
                                                        organization-id user)
                                             (registration/create! ctx)
                                             :registration)]
      (is (set/superset? (set notify/application-projection-keys)
                         (set (keys new-app))))
      (is (= :ryhti/building (operations/application->control-case old-app)))

      (notify/create-and-publish-notifications! context)
      (testing "message is published"
        (let [[message :as messages]    (deref *published)
              notification-id           (ObjectId. ^String (:message-id message))
              notification-pre-delivery (notification/get-notification notification-id)]
          (is (= 1 (count messages)))
          (is (= (str registration-id)
                 (:registration-id message)))

          ;; Empty the pubsub queue
          (reset! *published [])

          ;; Just pending, delivery not yet attempted
          (is (match? {:application-id    application-id
                       :control-case      :building
                       :created-at        date?
                       :id                notification-id
                       :notification-type notification-type
                       :organization-id   organization-id
                       :pending           true
                       :registration-id   registration-id}
                      notification-pre-delivery))

          (testing "submit message to pubsub handler manually"
            (is (true? (notify/handle-pubsub-message {:publisher publisher
                                                      :notifier  notifier}
                                                     message)))
            (testing "data supplied to the notifier"
              (is (match? [[registration
                            {:notification-id   (str notification-id)
                             :registration-id   (str registration-id)
                             :application-id    application-id
                             :organization-id   organization-id
                             :notification-type notification-type
                             :created-at        string?}]]
                          (deref *notified)))
              (reset! *notified [])))

          (testing "verify notification after delivery"
            (let [notification-after-delivery (notification/get-notification notification-id)]
              (testing "notification delivered, no longer pending"
                (is (match? {:application-id    application-id
                             :control-case      :building
                             :created-at        date?
                             :delivered-at      date?
                             :id                notification-id
                             :last-attempt-at   date?
                             :notification-type notification-type
                             :organization-id   organization-id
                             :registration-id   registration-id}
                            notification-after-delivery))))))))))

(deftest delivery-error-test
  ;; Simulate problems (e.g. HTTP timeout) when notifying the registered consumer
  (let [organization-id "753-R"
        application-id  "LP-753-2025-90000"
        user            {:username "user"}
        *published      (atom [])
        publisher       (fn [message]
                          (swap! *published conj message))
        notifier        (fn [registration content]
                          (throw (ex-info "oh noes" {:registration registration
                                                     :content      content})))
        permanent-id    (str (random-uuid))
        old-app         {:id               application-id
                         :organization     organization-id
                         :primaryOperation {:name "asuinrakennus"}
                         :ryhti            {:permanentPermitIdentifier permanent-id}
                         :state            "draft"}
        new-app         (assoc old-app :state "submitted")
        context         {:notification-type :application-state-change
                         :organization-id   organization-id
                         :publisher         publisher
                         :command           {:created     1771927054628
                                             :application old-app}
                         :new-app           new-app}]
    (is (set/superset? (set notify/application-projection-keys)
                       (set (keys new-app))))
    (is (= :ryhti/building (operations/application->control-case old-app)))
    (testing "register and publish"
      (let [{registration-id :id} (-> (util/mmap {:created 0
                                                  :target  {:url "http://localhost"}}
                                                 organization-id user)
                                      (registration/create! ctx)
                                      :registration)]
        (is registration-id)
        ;; Two events happen in Lupapiste and we handle both before
        ;; receiving any pubsub messages
        (notify/create-and-publish-notifications! context)

        (testing "one message is published"
          (let [[message :as messages]    (deref *published)
                notification-id           (ObjectId. ^String (:message-id message))
                notification-pre-delivery (notification/get-notification notification-id)]
            (is (= 1 (count messages)))
            (is (= (str registration-id)
                   (:registration-id message)))
            (is (match? (m/nested-equals
                          {:application-id      application-id
                           :control-case        :building
                           :created-at          date?
                           :extra-data          {:new-state :submitted
                                                 :old-state :draft}
                           :id                  notification-id
                           :notification-type   :application-state-change
                           :organization-id     organization-id
                           :pending             true
                           :permanent-permit-id permanent-id
                           :registration-id     registration-id})
                        notification-pre-delivery))

            ;; Empty the pubsub queue
            (reset! *published [])

            (testing "exception occurs when handling message"
              ;; Pubsub handler receives the first message but encounters problems
              ;; false => pubsub will handle retrying later
              (is (false? (notify/handle-pubsub-message {:publisher publisher
                                                         :notifier  notifier}
                                                        message)))

              ;; delivery was attempted
              (is (match? (m/nested-equals (assoc notification-pre-delivery
                                                  :last-attempt-at date?))
                          (notification/get-notification notification-id))))))))))

(deftest no-permanent-id-test
  ;; Old application has no permanent-id
  (let [organization-id "753-R"
        application-id  "LP-753-2025-90000"
        user            {:username "user"}
        *published      (atom [])
        publisher       (fn [message]
                          (swap! *published conj message))
        permanent-id    nil
        old-app         {:id               application-id
                         :organization     organization-id
                         :primaryOperation {:name "asuinrakennus"}
                         :ryhti            {:permanentPermitIdentifier permanent-id}
                         :state            "draft"}
        new-app         (assoc old-app :state "submitted")
        context         {:notification-type :application-state-change
                         :organization-id   organization-id
                         :publisher         publisher
                         :command           {:created     1771927054628
                                             :application old-app}
                         :new-app           new-app}]
    (is (set/superset? (set notify/application-projection-keys)
                       (set (keys new-app))))
    (is (= :ryhti/building (operations/application->control-case old-app)))
    (testing "register and publish"
      (let [{registration-id :id} (-> (util/mmap {:created 0
                                                  :target  {:url "http://localhost"}}
                                                 organization-id user)
                                      (registration/create! ctx)
                                      :registration)]
        (is registration-id)
        ;; Two events happen in Lupapiste and we handle both before
        ;; receiving any pubsub messages
        (notify/create-and-publish-notifications! context)

        (testing "nothing is published"
          (is (zero? (count (deref *published)))))))))
