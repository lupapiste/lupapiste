(ns lupapalvelu.application-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]))

(apply-remote-minimal)

(fact "can't inject js in 'x' or 'y' params"
  (create-app pena :x ";alert(\"foo\");" :y "what ever") => (contains {:ok false})
  (create-app pena :x "0.1x" :y "1.0") => (contains {:ok false})
  (create-app pena :x "1x2" :y "1.0") => (contains {:ok false})
  (create-app pena :x "2" :y "1.0") => (contains {:ok true}))

(fact "creating application without message"
  (let [resp  (create-app pena)
        id    (:id resp)
        resp  (query pena :application :id id)
        app   (:application resp)]
    app => (contains {:id id
                      :state "draft"
                      :location {:x 444444.0 :y 6666666.0}
                      :municipality "753"})
    (count (:comments app)) => 0
    (first (:auth app)) => (contains
                             {:firstName "Pena"
                              :lastName "Panaani"
                              :type "owner"
                              :role "owner"})
    (:allowedAttachmentTypes app) => (complement empty?)))

(fact "creating application with message"
  (let [resp            (create-app pena :messages ["hello"])
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)
        hakija (domain/get-document-by-name application "hakija")]
    (:state application) => "draft"
    (:opened application) => nil
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"
    (-> hakija :body :henkilo :henkilotiedot) => (contains {:etunimi "Pena" :sukunimi "Panaani"})))

(fact "Application in Sipoo has two possible authorities: Sonja and Ronja."
  (let [created-resp (create-app pena :municipality sonja-muni)
        id (:id created-resp)]
    (success created-resp) => true
    (comment-application id pena)
    (let [query-resp   (query sonja :authorities-in-applications-municipality :id id)]
      (success query-resp) => true
      (count (:authorityInfo query-resp)) => 2)))

(fact "Assign application to an authority"
  (let [application-id (:id (create-app pena :municipality sonja-muni))
        ;; add a comment to change state to open
        _ (comment-application application-id pena)
        application (:application (query sonja :application :id application-id))
        authority-before-assignation (:authority application)
        authorities (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))
        authority (first authorities)
        resp (command sonja :assign-application :id application-id :assigneeId (:id authority))
        assigned-app (:application (query sonja :application :id application-id))
        authority-after-assignation (:authority assigned-app)]
    application-id => truthy
    application => truthy
    (success resp) => true
    authority-before-assignation => nil
    authority-after-assignation => (contains {:id (:id authority)})
    (fact "Authority is not able to submit"
          (let [resp (query sonja :allowed-actions :id application-id)]
                (success resp) => true
                (get-in resp [:actions :submit-application :ok]) => falsey))

    ))

(fact "Assign application to an authority and then to no-one"
  (let [application-id (:id (create-app pena :municipality sonja-muni))
        ;; add a comment change set state to open
        _ (comment-application application-id pena)
        application (:application (query sonja :application :id application-id))
        authority-before-assignation (:authority application)
        authorities (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))
        authority (first authorities)
        resp (command sonja :assign-application :id application-id :assigneeId (:id authority))
        resp (command sonja :assign-application :id application-id :assigneeId nil)
        assigned-app (:application (query sonja :application :id application-id))
        authority-in-the-end (:authority assigned-app)]
    authority-before-assignation => nil
    authority-in-the-end => nil))

(fact "Applicaton shape is saved"
  (let [shape "POLYGON((460620 7009542,362620 6891542,467620 6887542,527620 6965542,460620 7009542))"
        application-id (:id (create-app pena))
        resp (command pena :save-application-shape :id application-id :shape shape)
        resp (query pena :application :id application-id)
        app   (:application resp)]
    (first (:shapes app)) => shape))


(fact "Authority is able to create an application to own municipality"
      (let [command-resp    (create-app sonja :municipality sonja-muni)
            application-id  (:id command-resp)]
        (success command-resp) => true
        (fact "Application is open"
              (let [query-resp      (query sonja :application :id application-id)
                    application     (:application query-resp)]
                (success query-resp)   => true
                application => truthy
                (:state application) => "open"
                (:opened application) => truthy
                (:opened application) => (:created application)))
        (fact "Authority could submit her own application"
              (let [resp (query sonja :allowed-actions :id application-id)]
                (success resp) => true
                (get-in resp [:actions :submit-application :ok]) => true))
        (fact "Application is submitted"
              (let [resp        (command sonja :submit-application :id application-id)
                    application (:application (query sonja :application :id application-id))]
                (success resp) => true
                (:state application) => "submitted"
              ))))

(fact "Authority in unable to create an application to other municipality"
      (unauthorized (create-app sonja :municipality veikko-muni)) => true)

(facts "Add operations"
  (let [command-resp (create-app mikko :municipality veikko-muni)
        application-id  (:id command-resp)]
    (success command-resp) => true
    (success (command mikko  :open-application   :id application-id)) => true
    (success (command veikko :assign-application :id application-id :assigneeId veikko-id)) => true

    (fact "Applicant is able to add operation"
          (success (command mikko :add-operation :id application-id :operation "varasto-tms")) => true)

    (fact "Authority is able to add operation"
          (success (command veikko :add-operation :id application-id :operation "muu-uusi-rakentaminen")) => true)))

(comment
  (apply-remote-minimal)
  ; Do 70 applications in each municipality:
  (doseq [muni ["753" "837" "186"]
          address-type ["Katu " "Kuja " "V\u00E4yl\u00E4 " "Tie " "Polku " "H\u00E4meentie " "H\u00E4meenkatu "]
          address (map (partial str address-type) (range 1 11))]
    (create-app pena :municipality muni :address address)))

