(ns lupapalvelu.inforequest-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas])
  )

(facts "inforequest workflow"
  (let [resp (create-app pena :messages ["hello"] :infoRequest true :municipality sonja-muni)
        id   (:id resp)]

    (success resp) => true

    (fact "inforequest was created with message"
          (let [resp        (query pena :application :id id)
                application (:application resp)]
            (:state application) => "open"
            (:opened application) => truthy
            (count (:comments application)) => 1
            (-> (:comments application) first :text) => "hello"))

    (fact "Veikko can not assign inforequest to himself"
          (let [resp (command veikko :assign-application :id id :assigneeId veikko-id)]
            (unauthorized resp) => true))

    (fact "Sonja can assign inforequest to herself"
          (let [resp (command sonja :assign-application :id id :assigneeId sonja-id)]
            (success resp) => true))

    (fact "Pena can't mark inforequest answered"
          (let [resp (command pena :mark-inforequest-answered :id id)]
            (unauthorized resp) => true))

    (fact "Sonja can mark inforequest answered"
          (let [resp (command sonja :mark-inforequest-answered :id id)]
            (success resp) => true)
          (let [inforequest  (:application (query pena :application :id id))]
            (:state inforequest) => "answered"))

    (fact "Pena can convert-to-application"
          (let [resp (command pena :convert-to-application :id id)]
            (success resp) => true))
))
