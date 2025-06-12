(ns lupapalvelu.backing-system.krysp.krysp-http-itest
  (:require [clj-http.client :as http-client]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as common-reader]
            [sade.core :refer [now]]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(set! *warn-on-reflection* true)

(defn get-integration-messages
  "remove messages logged by `/dev/dummy-dmcity/endpoint`"
  [app-id]
  (->> (mongo/select :integration-messages {:application.id app-id})
       (remove #(= "KuntaGML hakemus-path" (:messageType %)))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      (test-system/with-pubsub-subscriber :backing-system/http)
                      (test-system/with-pubsub-publisher :backing-system/http))
  (against-background
      [(http/post (str (server-address) "/dev/dummy-dmcity/endpoint/hakemus-path") anything) => nil
       (http/post (str (server-address) "/dev/dummy-dmcity/endpoint/HakemuksenTilapaivitys") anything) => nil]
    (facts "Sending KuntaGML via message queue and HTTP"
      ;; Tampere is configured to use dmcity
      (let [{application-id :id} (create-local-app pena
                                                   :x 329072
                                                   :y 6823200
                                                   :propertyId "83712103620001"
                                                   :address "Pub Harald")
            application          (query-application local-query pena application-id)]
        (generate-documents! application pena true)
        (local-command pena :submit-application :id application-id) => ok?
        (let [resp (local-command veikko :approve-application :id application-id :lang "fi")]
          (fact "Veikko moves to backing system via HTTP"
            resp => ok?
            (:integrationAvailable resp) => true))
        (Thread/sleep 100)
        (loop [retries 10
               msgs    (get-integration-messages application-id)]
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
                    (:status sent-message) => "done"))))))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Sending KuntaGML via HTTP"
    ;; Tampere is configured to use HTTP krysp in minimal
    (fact "Disable MQ for this test"
      (command pena :set-feature :feature "integration-message-queue" :value false) => ok?)
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

      (let [[sent-message received-message
             :as msgs] (filter #(ss/includes? (:messageType %) "KuntaGML")
                               (integration-messages application-id
                                                     :test-db-name test-db-name))]
        (facts "integration-messages"
          (count msgs) => 2
          (fact "sent message is saved"
            (:messageType sent-message) => "KuntaGML application"
            (:direction sent-message) => "out"
            (fact "after success, message is acknowledged"
              (> (or (:acknowledged sent-message) 0) ts) => true
              (:status sent-message) => "done"))
          (fact "received message is saved (dummy receiver)"
            ;; 'hakemus-path' below is deduced in web.clj as :path, which is actually
            ;; defined in organization in minimal fixture
            (:messageType received-message) => "KuntaGML hakemus-path"
            (:direction received-message) => "in"
            (-> received-message :data :body (ss/starts-with?  "<?xml")) => truthy
            (-> received-message :data :target) => "hakemus-path"
            (-> received-message :data :body (ss/includes? "Pub Harald")) => true))
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
                                    (server-address)
                                    application-id) {})
                  => (contains {:status 200 :body "ARK"})
                  (http-get export-url {:basic-auth       ["tampe-rest" "tampere"]
                                        :throw-exceptions false})
                  => (contains {:status 403
                                :body   "error.unsupported-permit-type"})
                  (http-get (format "%s/dev/change-permit-type/%s/R"
                                    (server-address)
                                    application-id) {})
                  => (contains {:status 200 :body "R"}))
                (facts "Parameter tests"
                  (letfn [(test-get [app-id lang]
                            (http-get
                              (ss/join "?" [(format "%s/%s" (server-address) uri)
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
                              (ss/join "?" [(format "%s/%s" (server-address) uri)
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
        (command admin :set-dmcity-url :url "http://invalid" :organizationId "837-R") => ok?
        (command veikko :approve-application :id application-id :lang "fi") => fail?
        (command admin :upsert-dmcity-configuration :organizationId "837-R"
                 :configuration {:url      (str (server-address) "/dev/dummy-dmcity/endpoint")
                                 :targets  {:R true :state true}
                                 :vault    "vaultti"
                                 :username "kuntagml" :password "invalid"}) => ok?
        (command veikko :approve-application :id application-id :lang "fi") => fail?)
      (fact "updating correct creds makes integration work again"
        (command admin :upsert-dmcity-configuration :organizationId "837-R"
                 :configuration {:url      (str (server-address) "/dev/dummy-dmcity/endpoint")
                                 :targets  {:R true :state true}
                                 :vault    "test-vault"
                                 :username "test-user"
                                 :password "test-secret"})
        => ok?
        (command veikko :approve-application :id application-id :lang "fi") => ok?))

    (fact "Restore MQ feature"
      (command pena :set-feature
               :feature "integration-message-queue"
               :value true) => ok?)))
