(ns lupapalvelu.neighbor-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [clojure.string :as s]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.tools :as tools]
            [sade.util :refer [fn->]]))

(defn invalid-token? [resp] (= resp {:ok false, :text "token-not-found"}))
(defn invalid-response? [resp] (= (dissoc resp :response) {:ok false, :text "invalid-response"}))
(defn invalid-vetuma? [resp] (= (dissoc resp :response) {:ok false, :text "invalid-vetuma-user"}))

(facts "add neigbor with missing optional data"
  (let [application-id (create-app-id pena :municipality sonja-muni)]
    (command pena :add-comment :id application-id :text "foo" :target "application") => ok?
    (fact "no name" (command sonja "neighbor-add" :id application-id :propertyId "p" :street "s" :city "c" :zip "z" :email "e") => ok?)
    (fact "no street" (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n"  :city "c" :zip "z" :email "e") => ok?)
    (fact "no city" (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n" :street "s"  :zip "z" :email "e") => ok?)
    (fact "no zip" (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n" :street "s" :city "c" :email "e") => ok?)
    (fact "no email" (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n" :street "s" :city "c" :zip "z") => ok?)))

(defn- create-app-with-neighbor [& args]
  (let [application-id (apply create-app-id pena args)
        resp (command pena :add-comment :id application-id :text "foo" :target "application")
        resp (command sonja "neighbor-add" :id application-id :propertyId "p" :name "n" :street "s" :city "c" :zip "z" :email "e")
        neighborId (:neighborId resp)
        application (query-application pena application-id)
        neighbors (:neighbors application)]
    [application neighborId neighbors]))

(defn- find-by-id [neighborId neighbors]
  (some (fn [neighbor] (when (= neighborId (:neighborId neighbor)) neighbor)) neighbors))

(facts "create app, add neighbor"
  (let [[application neighborId neighbors] (create-app-with-neighbor)
        neighbor (find-by-id neighborId neighbors)]
    (fact (:neighbor neighbor) => {:propertyId "p"
                                   :owner {:name "n"
                                           :address {:street "s" :city "c" :zip "z"}
                                           :email "e"}})
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, update neighbor"
  (let [[application neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-update" :id application-id :neighborId neighborId :propertyId "p2" :name "n2" :street "s2" :city "c2" :zip "z2" :email "e2")
        application (query-application pena application-id)
        neighbors (:neighbors application)
        neighbor (find-by-id neighborId neighbors)]
    (fact (count neighbors) => 1)
    (fact (:neighbor neighbor) => {:propertyId "p2"
                                   :owner {:name "n2"
                                           :address {:street "s2" :city "c2" :zip "z2"}
                                           :email "e2"}})
    (fact (count (:status neighbor)) => 1)
    (fact (first (:status neighbor)) => (contains {:state "open" :created integer?}))))

(facts "create app, remove neighbor"
  (let [[application neighborId] (create-app-with-neighbor)
        application-id (:id application)
        _ (command sonja "neighbor-remove" :id application-id :neighborId neighborId)
        application (query-application pena application-id)
        neighbors (:neighbors application)]
    (fact (count neighbors) => 0)))

(facts "neighbor invite email has correct link"
  (let [neighbor-email-addr       "abba@example.com"
        [application neighbor-id] (create-app-with-neighbor :address "Naapurikuja 3")
        application-id            (:id application)
        _                         (command pena :neighbor-send-invite
                                                :id application-id
                                                :neighborId neighbor-id
                                                :email neighbor-email-addr)
        email                     (last-email)
        [_ a-id n-id token]       (re-find #"(?sm)/neighbor/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)" (get-in email [:body :plain]))]

    (:to email) => neighbor-email-addr
    (:subject email) => "Lupapiste.fi: Naapurikuja 3 - naapurin kuuleminen"
    a-id => application-id
    n-id => neighbor-id
    token => #"[A-Za-z0-9]{48}"))

(facts "neighbour invite & view on application"
  (let [[{application-id :id :as application} neighborId] (create-app-with-neighbor)
        _               (upload-attachment-to-all-placeholders pena application)
        _               (command pena :neighbor-send-invite
                                      :id application-id
                                      :neighborId neighborId
                                      :email "abba@example.com")
        application     (query-application pena application-id)
        hakija-doc-id   (:id (domain/get-document-by-name application "hakija"))
        uusirak-doc-id  (:id (domain/get-document-by-name application "uusiRakennus"))
        _               (command pena :update-doc
                                      :id application-id
                                      :doc hakija-doc-id
                                      :updates [["henkilo.henkilotiedot.etunimi"  "Zebra"]
                                                ["henkilo.henkilotiedot.sukunimi" "Zorro"]
                                                ["henkilo.henkilotiedot.hetu"     "123456789"]
                                                ["henkilo.yhteystiedot.puhelin"     "040-1234567"]])
        _               (command pena :update-doc
                                      :id application-id
                                      :doc uusirak-doc-id
                                      :updates [["rakennuksenOmistajat.0.henkilo.henkilotiedot.etunimi"  "Gustav"]
                                                ["rakennuksenOmistajat.0.henkilo.henkilotiedot.sukunimi" "Golem"]
                                                ["rakennuksenOmistajat.0.henkilo.henkilotiedot.hetu"     "abba"]
                                                ["rakennuksenOmistajat.0.henkilo.henkilotiedot.turvakieltoKytkin" true]
                                                ["rakennuksenOmistajat.0.henkilo.osoite.katu" "Katuosoite"]
                                                ["rakennuksenOmistajat.0.henkilo.yhteystiedot.puhelin"     "040-2345678"]])]

    application => truthy

    (let [email                     (query pena :last-email)
          body                      (get-in email [:message :body :plain])
          [_ a-id n-id token]       (re-find #"(?sm)/neighbor/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)" body)]

      token => truthy
      token =not=> #"="

      (fact "application query returns set document info"
        (let [application (query-application pena application-id)
              hakija-doc  (tools/unwrapped (domain/get-document-by-id application hakija-doc-id))
              uusirak-doc (tools/unwrapped (domain/get-document-by-id application uusirak-doc-id))]

          (-> hakija-doc :data :henkilo :henkilotiedot :etunimi) => "Zebra"
          (-> hakija-doc :data :henkilo :henkilotiedot :sukunimi) => "Zorro"
          (-> hakija-doc :data :henkilo :henkilotiedot :hetu) => "123456789"
          (-> hakija-doc :data :henkilo :yhteystiedot  :puhelin) => "040-1234567"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :etunimi) => "Gustav"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :sukunimi) => "Golem"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu) => "abba"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :turvakieltoKytkin) => true
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :osoite :katu) => "Katuosoite"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :yhteystiedot :puhelin) => "040-2345678"))

      (fact "neighbor application query does not return hetu"
        (let [resp        (query pena :neighbor-application
                                      :applicationId application-id
                                      :neighborId neighborId
                                      :token token)
              application (:application resp)
              hakija-doc  (tools/unwrapped (domain/get-document-by-id application hakija-doc-id))
              uusirak-doc (tools/unwrapped (domain/get-document-by-id application uusirak-doc-id))]

          resp => truthy
          resp => (contains {:ok true})
          application => truthy

          (-> hakija-doc :data :henkilo :henkilotiedot :etunimi) => "Zebra"
          (-> hakija-doc :data :henkilo :henkilotiedot :sukunimi) => "Zorro"
          (-> hakija-doc :data :henkilo :henkilotiedot :hetu) => nil
          (-> hakija-doc :data :henkilo :henkilotiedot :turvakieltoKytkin) => nil
          (-> hakija-doc :data :henkilo :yhteystiedot  :puhelin) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :etunimi) => "Gustav"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :sukunimi) => "Golem"
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :turvakieltoKytkin) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :henkilotiedot :hetu) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :yhteystiedot :puhelin) => nil
          (-> uusirak-doc :data :rakennuksenOmistajat :0 :henkilo :osoite :katu) => nil

          (facts "random testing about content"
            (:comments application) => nil
            (count (:documents application)) => 5 ; evil

            (fact "attachments"
              (fact "there are some attachments"
                (->> application :attachments count) => pos?)
              (fact "everyone is paapiirustus"
                (->> application :attachments (some (fn-> :type :type-group (not= "paapiirustus")))) => falsey)

              (let [file-id (->> application :attachments first :latestVersion :fileId)]
                (fact "downloading should be possible"
                  (raw nil "neighbor-download-attachment" :neighbor-id neighborId :token token :file-id file-id) => http200?)

                (fact "downloading with wrong token should not be possible"
                  (raw nil "neighbor-download-attachment" :neighbor-id neighborId :token "h4x3d token" :file-id file-id) => http401?)))

            (:auth application) => nil)))

      (fact "without tupas, neighbor can't give response"
        (command pena :neighbor-response
          :applicationId application-id
          :neighborId (name neighborId)
          :token token
          :stamp "INVALID"
          :response "ok"
          :message "kehno suunta") => invalid-vetuma?)

      (fact "with vetuma"
        (let [stamp (vetuma-stamp!)]

          (fact "neighbor cant give ill response"
            (command pena :neighbor-response
              :applicationId application-id
              :neighborId (name neighborId)
              :stamp stamp
              :token token
              :response "ime parsaa!"
              :message "kehno suunta") => invalid-response?)

          (fact "neighbor can give response"
            (command pena :neighbor-response
              :applicationId application-id
              :neighborId (name neighborId)
              :stamp stamp
              :token token
              :response "comments"
              :message "kehno suunta") => ok?)

          (fact "neighbour cant regive response 'cos vetuma has expired"
            (command pena :neighbor-response
              :applicationId application-id
              :neighborId (name neighborId)
              :stamp stamp
              :token token
              :response "comments"
              :message "kehno suunta") => invalid-vetuma?)

          (fact "neighbour cant regive response with new tupas 'con token has expired"
            (command pena :neighbor-response
              :applicationId application-id
              :neighborId (name neighborId)
              :stamp (vetuma-stamp!)
              :token token
              :response "comments"
              :message "kehno suunta") => invalid-token?)

          (fact "neighbour cant see application anymore"
            (query pena :neighbor-application
              :applicationId application-id
              :neighborId (name neighborId)
              :token token) => invalid-token?))))))

