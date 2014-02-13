(ns lupapalvelu.inforequest-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(facts "inforequest workflow"
  (let [{id :id :as resp} (create-app pena :messages ["hello"] :infoRequest true :municipality sonja-muni)]

    resp => ok?

    (fact "inforequest was created with message"
      (let [application (query-application pena id)]
        application => (in-state? "info")
        (:opened application) => truthy
        (count (:comments application)) => 1
        (-> (:comments application) first :text) => "hello"))

    (fact "Veikko can not assign inforequest to himself"
      (command veikko :assign-application :id id :assigneeId veikko-id) => unauthorized?)

    (fact "Sonja can assign inforequest to herself"
      (command sonja :assign-application :id id :assigneeId sonja-id) => ok?)

    (fact "When Commenting on inforequest marks it answered"
      (query-application pena id)    => (in-state? :info)
      (comment-application id sonja false)
      (query-application pena id)    => (in-state? :answered))

    (fact "Pena can convert-to-application"
      (command pena :convert-to-application :id id) => ok?))


  (fact "Pena cannot create app for organization that has inforequests disabled"
  (let [resp  (create-app pena :infoRequest true :municipality "998")]
    resp =not=> ok?
    (:text resp) => "error.inforequests-disabled")))

(facts "Open inforequest"
  ; Reset emails
  (last-email)

  (let [application-id (create-app-id pena :municipality "433" :infoRequest true :address "OIR")
        application    (query-application pena application-id)]

    (fact "Inforequest was created"
      (:infoRequest application) => true)

    (fact "Inforequest is an open inforequest"
      (:openInfoRequest application) => true)

    (fact "Auhtority receives email about the new oir"
      (let [email          (last-email)
           [_ token lang] (re-find #"(?sm)/api/raw/openinforequest\?token-id=([A-Za-z0-9-]+)&lang=([a-z]{2})" (get-in email [:body :plain] ""))]
       (:to email) => "erajorma@takahikia.fi"
       (:subject email) => "Lupapiste.fi: OIR - Neuvontapyynt\u00f6"
       (count token) => pos?))

    (comment-application application-id pena false)

    (fact "Auhtority receives email about the comment"
      (let [email          (last-email)
           [_ token lang] (re-find #"(?sm)/api/raw/openinforequest\?token-id=([A-Za-z0-9-]+)&lang=([a-z]{2})" (get-in email [:body :plain] ""))]
       (:to email) => "erajorma@takahikia.fi"
       (:subject email) => "Lupapiste.fi: OIR - Neuvontapyynt\u00f6"
       (count token) => pos?)))

  )
