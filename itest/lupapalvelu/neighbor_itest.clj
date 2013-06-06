(ns lupapalvelu.neighbor-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.document.tools :as tools]))

(defn- create-app-with-neighbor []
  (let [resp (create-app pena)
        application-id (:id resp)
        resp (command pena :add-comment :id application-id :text "foo" :target "application")
        resp (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n" :street "s" :city "c" :zip "z" :type :person :email "e")
        neighborId (keyword (:neighborId resp))
        resp (query pena :application :id application-id)
        application (:application resp)
        neighbors (:neighbors application)]
    [application neighborId neighbors]))

(defn- find-by-id [neighborId neighbors]
  (some (fn [neighbor] (when (= neighborId (keyword (:neighborId neighbor))) neighbor)) neighbors))

(facts "create app, add neighbor"
  (let [[application neighborId neighbors] (create-app-with-neighbor)
        neighbor (find-by-id neighborId neighbors)]
    (fact (:neighbor neighbor) => {:propertyId "p"
                                   :owner {:name "n"
                                           :address {:street "s" :city "c" :zip "z"}
                                           :type "person"
                                           :email "e"}})
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, update neighbor"
  (let [[application neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-update" :id application-id :neighborId neighborId :propertyId "p2" :name "n2" :street "s2" :city "c2" :zip "z2" :type :person :email "e2")
        application (:application (query pena :application :id application-id))
        neighbors (:neighbors application)
        neighbor (find-by-id neighborId neighbors)]
    (fact (count neighbors) => 1)
    (fact (:neighbor neighbor) => {:propertyId "p2"
                                   :owner {:name "n2"
                                           :address {:street "s2" :city "c2" :zip "z2"}
                                           :type "person"
                                           :email "e2"}})
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, remove neighbor"
  (let [[application neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-remove" :id application-id :neighborId neighborId)
        application (:application (query pena :application :id application-id))
        neighbors (:neighbors application)]
    (fact (count neighbors) => 0)))

(facts "neighbour invite & view on application"
  (let [[{application-id :id} neighborId] (create-app-with-neighbor)
        _               (command pena :neighbor-send-invite
                          :id application-id
                          :neighborId neighborId
                          :email "abba@example.com"
                          :message "welcome!")
        application     (-> (query pena :application :id application-id) :application)
        hakija-doc-id   (:id (domain/get-document-by-name application "hakija"))
        _               (command pena :update-doc
                          :id application-id
                          :doc hakija-doc-id
                          :updates [["henkilo.henkilotiedot.etunimi"  "Zebra"]
                                    ["henkilo.henkilotiedot.sukunimi" "Zorro"]
                                    ["henkilo.henkilotiedot.hetu"     "123456789"]])]

    application => truthy

    (let [response  (query pena :last-email)
          message   (-> response :message)
          token     (->> message :body (re-matches #"(?sm).*neighbor-show/.+/(.*)\".*") last)]

      token => truthy

      (fact "application query returns set document info"
        (let [application (-> (query pena :application :id application-id) :application)
              hakija-doc  (domain/get-document-by-id application hakija-doc-id)
              hakija-doc  (tools/unwrapped hakija-doc)]

          (-> hakija-doc :data :henkilo :henkilotiedot :etunimi) => "Zebra"
          (-> hakija-doc :data :henkilo :henkilotiedot :sukunimi) => "Zorro"
          (-> hakija-doc :data :henkilo :henkilotiedot :hetu) => "123456789"))

      (fact "neighbor applicaiton query does not return hetu"
        (let [application (-> (query pena :neighbor-application
                                :applicationId application-id
                                :neighborId (name neighborId)
                                :token token) :application)
              hakija-doc  (domain/get-document-by-id application hakija-doc-id)
              hakija-doc  (tools/unwrapped hakija-doc)]

          application => truthy

          (-> hakija-doc :data :henkilo :henkilotiedot :etunimi) => "Zebra"
          (-> hakija-doc :data :henkilo :henkilotiedot :sukunimi) => "Zorro"
          (-> hakija-doc :data :henkilo :henkilotiedot :hetu) => nil

          (facts "random testing about content"
            (:comments application) => nil
            (:attachments application) => empty? ; we could put some paapiirustus in there
            (:auth application) => nil)))

      (fact "neighbor can give response"
        (command pena :neighbor-response
          :applicationId application-id
          :neighborId (name neighborId)
          :token token
          :response "disapprove"
          :message "kehno suunta") => ok?

        (fact "neighbour cant regive response"
          (command pena :neighbor-response
            :applicationId application-id
            :neighborId (name neighborId)
            :token token
            :response "disapprove"
            :message "kehno suunta") => {:ok false, :text "token-not-found"})

        (fact "neighbour cant see application anymore"
          (query pena :neighbor-application
            :applicationId application-id
            :neighborId (name neighborId)
            :token token) => {:ok false, :text "token-not-found"})))))
