(ns lupapalvelu.application-itest
  (:use [lupapalvelu.itest-util]
        [midje.sweet]
        [clojure.pprint :only [pprint]])
  (:require [lupapalvelu.operations :as operations]
            [lupapalvelu.document.schemas :as schemas]))

(defn- create-app [& args]
  (let [args (->> args
               (apply hash-map)
               (merge {:permitType "buildingPermit"
                       :operation "asuinrakennus"
                       :propertyId "1"
                       :x 444444 :y 6666666
                       :address "foo 42, bar"
                       :municipality "753"})
               (mapcat seq))]
    (apply command pena :create-application args)))

(fact "can't inject js in 'x' or 'y' params"
  (create-app :x ";alert(\"foo\");" :y "what ever") => (contains {:ok false})
  (create-app :x "0.1x" :y "1.0") => (contains {:ok false})
  (create-app :x "1x2" :y "1.0") => (contains {:ok false})
  (create-app :x "2" :y "1.0") => (contains {:ok true}))

(fact "creating application without message"
  (apply-remote-minimal)
  (let [resp  (create-app)
        id    (:id resp)
        resp  (query pena :application :id id)
        app   (:application resp)]
    app => (contains {:id id
                      :state "draft"
                      :location {:x 444444.0 :y 6666666.0}
                      :permitType "buildingPermit"
                      :municipality "753"})
    (count (:comments app)) => 0
    (first (:auth app)) => (contains
                             {:firstName "Pena"
                              :lastName "Panaani"
                              :type "owner"
                              :role "owner"})
    (:allowedAttachmentTypes app) => (complement empty?)))

(fact "creating application with message"
  (let [resp            (create-app :messages ["hello"])
        application-id  (:id resp)
        resp            (query pena :application :id application-id)
        application     (:application resp)
        hakija          (some (fn [doc] (if (= (-> doc :schema :info :name) "hakija") doc)) (:documents application))]
    (:state application) => "draft"
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"
    (-> hakija :body :henkilo :henkilotiedot) => (contains {:firstName "Pena" :lastName "Panaani" :role "applicant"})))

(fact "Application in Sipoo has two possible authorities: Sonja and Ronja."
  (apply-remote-minimal)
  (let [application-id (:id (create-app))
        authorities  (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))]
    (count authorities) => 2))

(fact "Assign application to an authority"
  (apply-remote-minimal)
  (let [application-id (:id (create-app))
        ;; add a comment to change state to open
        comment (command pena :add-comment :id application-id :text "hello" :target "application")
        application (:application (query sonja :application :id application-id))
        roles-before-assignation (:roles application)
        authorities (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))
        authority (first authorities)
        resp (command sonja :assign-application :id application-id :assigneeId (:id authority))
        assigned-app (:application (query sonja :application :id application-id))
        roles-after-assignation (:roles assigned-app)]
    (count roles-before-assignation) => 1
    (count roles-after-assignation) => 2))

(fact "Assign application to an authority and then to no-one"
  (apply-remote-minimal)
  (let [application-id (:id (create-app))
        ;; add a comment change set state to open
        comment (command pena :add-comment :id application-id :text "hello" :target "application")
        application (:application (query sonja :application :id application-id))
        roles-before-assignation (:roles application)
        authorities (:authorityInfo (query sonja :authorities-in-applications-municipality :id application-id))
        authority (first authorities)
        resp (command sonja :assign-application :id application-id :assigneeId (:id authority))
        resp (command sonja :assign-application :id application-id :assigneeId nil)
        assigned-app (:application (query sonja :application :id application-id))
        roles-in-the-end (:roles assigned-app)]
    (count roles-before-assignation) => 1
    (count roles-in-the-end) => 1))

(comment
  (apply-remote-minimal)
  ; Do 70 applications in each municipality:
  (doseq [muni ["753" "837" "186"]
          address-type ["Katu " "Kuja " "V\u00E4yl\u00E4 " "Tie " "Polku " "H\u00E4meentie " "H\u00E4meenkatu "]
          address (map (partial str address-type) (range 1 11))]
    (create-app :municipality muni :address address)))

