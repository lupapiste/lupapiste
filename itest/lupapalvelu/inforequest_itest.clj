(ns lupapalvelu.inforequest-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas])
  )


(fact "creating inforequest with message"
  (let [resp            (create-app pena :messages ["hello"] :infoRequest true :permitType "infoRequest")
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)
        hakija (domain/get-document-by-name application "hakija")]
    (:state application) => "open"
    (:opened application) => truthy
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"
    ))
