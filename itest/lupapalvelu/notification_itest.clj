(ns lupapalvelu.notification-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]))

(facts "email is sent on comment"

  (let [reset (query mikko :sent-emails :reset true)
        id    (:id (create-app mikko :municipality sonja-muni))
        _     (command mikko :add-comment :id id :text "..." :target "application")
        assigned  (command sonja :assign-to-me :id id)
        commented (command sonja :add-comment :id id :text "Hi, my name is Sonja! How may I help you?" :target "application")]

    assigned => ok?
    commented => ok?

    (let [resp (query mikko :sent-emails :reset true)
          messages (:messages resp)]
      (count messages) => 1)))
