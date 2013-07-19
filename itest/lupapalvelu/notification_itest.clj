(ns lupapalvelu.notification-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]))


(facts "neighbor invite email has correct link"
  (let [[application neighbor-id] (create-app-with-neighbor)
        application-id            (:id application)
        _                         (command pena :neighbor-send-invite
                                                :id application-id
                                                :neighborId neighbor-id
                                                :email "abba@example.com")
        _                         (Thread/sleep 20) ; delivery time
        email                     (query pena :last-email)
        body                      (get-in email [:message :body])
        [_ a-id n-id token]       (re-matches #"(?sm).*neighbor-show/([^/]+)/([^/]+)/([^/]*)\".*" body)]
    a-id => application-id
    n-id => neighbor-id
    token => #"[A-Za-z0-9]{48}"))

(facts "email is sent on comment"

  (let [reset (query mikko :sent-emails :reset true)
        id    (:id (create-app mikko :municipality sonja-muni))
        _     (command mikko :add-comment :id id :text "..." :target "application")
        assigned  (command sonja :assign-to-me :id id)
        commented (command sonja :add-comment :id id :text "Hi, my name is Sonja! How may I help you?" :target "application")]

    assigned => ok?
    commented => ok?

    ; XXX Wait for mail delivery
    (Thread/sleep 2000)

    (let [resp (query mikko :sent-emails :reset true)
          messages (:messages resp)]
      (count messages) => 1)))
