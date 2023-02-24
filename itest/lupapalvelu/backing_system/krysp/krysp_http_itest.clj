(ns lupapalvelu.backing-system.krysp.krysp-http-itest
  (:require [artemis-server]
            [clj-http.client :as http-client]
            [lupapalvelu.backing-system.krysp.http :as krysp-http]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as common-reader]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.shared-util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(def jms-test-db (str "test_krysp_http_jms" (now)))

(defn get-integration-messages [app-id]
  (->> (mongo/select :integration-messages {:application.id app-id})
       (remove #(= "KuntaGML hakemus-path" (:messageType %))))) ; remove messages logged by /dev/krysp dummy endpoint

(when (env/value :integration-message-queue)
  (mount/start #'mongo/connection)
  (mongo/with-db jms-test-db
    (fixture/apply-fixture "minimal")
    (against-background [(sade.http/post (str (env/server-address) "/dev/krysp/receiver/hakemus-path") anything) => nil
                         (sade.http/post (str (env/server-address) "/dev/statusecho/200") anything) => nil
                         (before :facts (if (= (env/value :integration-message-queue) "pubsub")
                                          (mount/start #'krysp-http/kuntagml-pubsub-consumer)
                                          (mount/start #'artemis-server/embedded-broker #'jms/state
                                                       #'krysp-http/kuntagml-jms-session #'krysp-http/kuntagml-consumer)))]
      (facts "Sending KuntaGML via JMS and HTTP"            ; Tampere is configured to use HTTP krysp in minimal
      (let [{application-id :id} (create-local-app pena
                                                   :x 329072
                                                   :y 6823200
                                                   :propertyId "83712103620001"
                                                   :address "Pub Harald")
            application (query-application local-query pena application-id)]
        (generate-documents! application pena true)
        (local-command pena :submit-application :id application-id) => ok?
        (let [resp (local-command veikko :approve-application :id application-id :lang "fi")]
          (fact "Veikko moves to backing system via HTTP"
            resp => ok?
            (:integrationAvailable resp) => true))
        (Thread/sleep 100)
        (loop [retries 10
               msgs (get-integration-messages application-id)]
          (let [sent-message (util/find-first (fn [msg] (= (:messageType msg) "KuntaGML application")) msgs)]
            (if-not (or (zero? retries) (= (:status sent-message) "done"))
              (do
                (Thread/sleep 1000)
                (recur (dec retries) (get-integration-messages application-id)))
              (facts "integration-messages"
                (count msgs) => 3                               ; 2x state-change 1x KuntaGML
                (fact "message is delivered via queue"
                  (:messageType sent-message) => "KuntaGML application"
                  (:direction sent-message) => "out"
                  (fact "is processed by consumer"
                    (:status sent-message) => "done")))))))))))


(apply-remote-minimal)
(def original-mq-feature-flag (get-in (query pena :features) [:features :integration-message-queue] false))
(facts "Sending KuntaGML via HTTP"                    ; Tampere is configured to use HTTP krysp in minimal
  (when (true? original-mq-feature-flag)
    (fact "Disable MQ for this test"
      (command pena :set-feature :feature "integration-message-queue" :value false) => ok?))
  (let [application-id (create-app-id pena
                                      :x 329072
                                      :y 6823200
                                      :propertyId "83712103620001"
                                      :address "Pub Harald")
        application    (query-application pena application-id)
        ts             (now)]
    (generate-documents! application pena)
    (command pena :submit-application :id application-id) => ok?

    (let [resp (command veikko :approve-application :id application-id :lang "fi")]
      (fact "Veikko moves to backing system via HTTP"
        resp => ok?
        (:integrationAvailable resp) => true))

    (let [msgs             (filter #(ss/contains? (:messageType %) "KuntaGML") (integration-messages application-id :test-db-name test-db-name))
          sent-message     (first msgs)
          received-message (second msgs)]
      (facts "integration-messages"
        (count msgs) => 2
        (fact "sent message is saved"
          (:messageType sent-message) => "KuntaGML application"
          (:direction sent-message) => "out"
          (fact "after success, message is acknowledged"
            (> (or (:acknowledged sent-message) 0) ts) => true
            (:status sent-message) => "done"))
        (fact "received message is saved (dummy receiver)"
          ; 'hakemus-path' below is deduced in web.clj as :path, which is actually defined in organization in minimal fixture
          (:messageType received-message) => "KuntaGML hakemus-path"
          (:direction received-message) => "in"
          (ss/starts-with (:data received-message) "<?xml") => truthy
          (ss/contains? (:data received-message) "Pub Harald") => true))
      (facts "REST API access for generated PDFs"
        (let [[export-url submitted-url] (-> sent-message :data (xml/parse-string "utf-8")
                                             common-reader/strip-xml-namespaces
                                             (xml/select :linkkiliitteeseen enlive/content))]
             (facts "/rest/pdf-export"
               (let [{:keys [uri query-string]} (http-client/parse-url export-url)]
                 uri => "/rest/pdf-export"
                 (http-client/form-decode query-string) => {"id"   application-id
                                                            "lang" "fi"}
                 (facts "Tampere REST user can access"
                   (pdf-response?
                     (http-get export-url {:basic-auth       ["tampe-rest" "tampere"]
                                           :throw-exceptions false})))
                 (fact "Sipoo REST user cannot access"
                   (http-get export-url {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                         :throw-exceptions false})
                   => (contains {:status  404
                                 :body    "error.application-not-found"
                                 :headers (contains {"Content-Type" "text/plain"})}))
                 (fact "Unauthed user cannot access"
                   (http-get export-url {:throw-exceptions false})
                   => (contains {:status 401
                                 :body   "Unauthorized"}))
                 (fact "Bad password"
                   (http-get export-url {:basic-auth       ["tampe-rest" "bad"]
                                         :throw-exceptions false})
                   => (contains {:status 401
                                 :body   "Unauthorized"}))
                 (fact "ARK applications cannot be exported"
                   (http-get (format "%s/dev/change-permit-type/%s/ARK"
                                     (target-server-or-localhost-address)
                                     application-id) {})
                   => (contains {:status 200 :body "ARK"})
                   (http-get export-url {:basic-auth       ["tampe-rest" "tampere"]
                                         :throw-exceptions false})
                   => (contains {:status 403
                                 :body   "error.unsupported-permit-type"})
                   (http-get (format "%s/dev/change-permit-type/%s/R"
                                     (target-server-or-localhost-address)
                                     application-id) {})
                   => (contains {:status 200 :body "R"}))
                 (facts "Parameter tests"
                   (letfn [(test-get [app-id lang]
                             (http-get
                               (ss/join "?" [(format "%s/%s" (target-server-or-localhost-address) uri)
                                             (http-client/generate-query-string (util/assoc-when {}
                                                                                                 :id app-id
                                                                                                 :lang lang))])
                               {:basic-auth       ["tampe-rest" "tampere"]
                                :throw-exceptions false}))]
                     (fact "Bad application id"
                       (test-get "bad" "fi")
                       => (contains {:status 400
                                     :body   #"error\.input-validation-error"}))
                     (fact "Bad lang"
                       (test-get application-id "bad")
                       => (contains {:status 400
                                     :body   #"error\.input-validation-error"}))
                     (fact "Lang can be missing"
                       (test-get application-id nil) => (contains {:status 200}))
                     (fact "Application id cannot be missing"
                       (test-get nil "fi") => (contains {:status 400
                                                         :body   #"error\.input-validation-error"}))))))

             (fact "/rest/submitted-application-pdf-export"
               (let [{:keys [uri query-string]} (http-client/parse-url submitted-url)]
                 uri => "/rest/submitted-application-pdf-export"
                 (http-client/form-decode query-string) => {"id"   application-id
                                                            "lang" "fi"}
                 (facts "Tampere REST user can access"
                   (pdf-response? (http-get export-url {:basic-auth       ["tampe-rest" "tampere"]
                                                        :throw-exceptions false})))
                 (fact "Sipoo REST user cannot access"
                   (http-get export-url {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                         :throw-exceptions false})
                   => (contains {:status  404
                                 :body    "error.application-not-found"
                                 :headers (contains {"Content-Type" "text/plain"})}))
                 (fact "Unauthed user cannot access"
                   (http-get export-url {:throw-exceptions false})
                   => (contains {:status 401
                                 :body   "Unauthorized"}))
                 (fact "Bad password"
                   (http-get export-url {:basic-auth       ["tampe-rest" "bad"]
                                         :throw-exceptions false})
                   => (contains {:status 401
                                 :body   "Unauthorized"}))
                 (facts "Parameter tests"
                   (letfn [(test-get [app-id lang]
                             (http-get
                               (ss/join "?" [(format "%s/%s" (target-server-or-localhost-address) uri)
                                             (http-client/generate-query-string (util/assoc-when {}
                                                                                                 :id app-id
                                                                                                 :lang lang))])
                               {:basic-auth       ["tampe-rest" "tampere"]
                                :throw-exceptions false}))]
                     (fact "Bad application id"
                       (test-get "bad" "fi")
                       => (contains {:status 400
                                     :body   #"error\.input-validation-error"}))
                     (fact "Bad lang"
                       (test-get application-id "bad")
                       => (contains {:status 400
                                     :body   #"error\.input-validation-error"}))
                     (fact "Lang can be missing"
                       (test-get application-id nil) => (contains {:status 200}))
                     (fact "Application id cannot be missing"
                       (test-get nil "fi") => (contains {:status 400
                                                         :body   #"error\.input-validation-error"})))))))))

    (fact "Sending fails without proper http endpoint"
      (command veikko :request-for-complement :id application-id) => ok?
      (command admin :set-kuntagml-http-endpoint :url "http://invalid" :organization "837-R" :permitType "R") => ok?
      (command veikko :approve-application :id application-id :lang "fi") => fail?
      (command admin :set-kuntagml-http-endpoint :partner "matti"
               :url (str (server-address) "/dev/krysp/receiver") :organization "837-R" :permitType "R"
               :username "kuntagml" :password "invalid") => ok?
      (command veikko :approve-application :id application-id :lang "fi") => fail?)
    (fact "updating correct creds makes integration work again"
      (command admin :set-kuntagml-http-endpoint
               :url (str (server-address) "/dev/krysp/receiver") :organization "837-R" :permitType "R"
               :username "kuntagml" :password "kryspi" :partner "matti") => ok?
      (command veikko :approve-application :id application-id :lang "fi") => ok?)
    (facts "Endpoint path is mandatory"
      (command veikko :request-for-complement :id application-id) => ok?
      (command admin :set-kuntagml-http-endpoint
               :url (str (server-address) "/dev/krysp/receiver")
               :organization "837-R" :permitType "R"
               :path {:verdict "foobar"}) => ok?
      (fact "No endpoint: fails"
        (command veikko :approve-application :id application-id :lang "fi") => fail?)
      (command admin :set-kuntagml-http-endpoint
               :url (str (server-address) "/dev/krysp/receiver")
               :organization "837-R" :permitType "R"
               :path {:application "foobar"})
      (fact "Endpoint OK"
        (command veikko :approve-application :id application-id :lang "fi") => ok?)))
  (when (true? original-mq-feature-flag)
    (fact "Restore MQ feature"
      (command pena :set-feature :feature "integration-message-queue" :value original-mq-feature-flag) => ok?)))

(facts "set-kuntagml-http-endpoint"
  (command admin :set-kuntagml-http-endpoint :organization "837-R"
           :url "http://good.example.com"
           :permitType "R"
           :auth-type "x-header"
           :username "theuser" :password "secret"
           :partner "dmcity"
           :path {:verdict "MyVerdictEndpoint"}
           :headers [{:key "x-foo" :value "bar"}]) => ok?
  (-> (query admin :organization-by-id :organizationId "837-R")
      :data :krysp :R :http)
  => (contains {:auth-type "x-header"
                :crypto-iv truthy
                :headers   [{:key "x-foo" :value "bar"}]
                :partner   "dmcity"
                :path      {:verdict "MyVerdictEndpoint"}
                :password  #(not= % "secret")
                :url       "http://good.example.com"
                :username  "theuser"}))
