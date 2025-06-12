(ns lupapalvelu.rest.verdict-itest
  (:require [clojure.set :as set]
            [clojure.test :refer [are deftest is testing use-fixtures]]
            [lupapalvelu.application :as application]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itu :refer [with-test-system]]
            [lupapalvelu.json :as json]
            [ring.util.http-predicates :as ruh]
            [sade.files :as files]
            [sade.shared-schemas :as ssc]
            [schema.core :as sc])
  (:import [java.io File]
           [java.time ZoneId ZonedDateTime]))

(set! *warn-on-reflection* true)

(def vantaa-org-id "092-YA")
(def operation "ya-kayttolupa-kattolumien-pudotustyot")
(def assignment-handler-email "assignment.handler@example.com")
(def integration-user "matti-rest-api-user")

;;; Helpers for invoice catalogues

(defn- insert-catalogue-draft!
  [apikey org-id]
  (let [{:keys [ok draft]} (itu/command apikey :new-price-catalogue-draft
                                        :organizationId org-id)]
    (is ok "catalogue draft was created")
    {:catalogue-id (:id draft)
     :row-ids      (->> draft
                        :rows
                        (mapv :id))}))

(defn- edit-catalogue-draft!
  [apikey org-id catalogue-id changes]
  (let [{:keys [ok]} (itu/command apikey :edit-price-catalogue-draft
                                  :organizationId org-id
                                  :price-catalogue-id catalogue-id
                                  :edit changes)]
    (is ok "catalogue draft was modified")))

(defn- publish-catalogue-draft!
  [apikey org-id catalogue-id]
  (let [{:keys [ok]} (itu/command apikey :publish-price-catalogue
                                  :organizationId org-id
                                  :price-catalogue-id catalogue-id)]
    (is ok "catalogue draft was published")))

(defn- create-and-publish-price-catalogue!
  [apikey org-id]
  (let [{:keys [catalogue-id row-ids]} (insert-catalogue-draft! apikey org-id)]
    (->> row-ids
         (map (fn [row-id]
                {:row {:id               row-id
                       :code             "A"
                       :text             "Some row"
                       :unit             "m2"
                       :price-per-unit   1
                       :discount-percent 5
                       :operations       [operation]}}))
         (run! (partial edit-catalogue-draft! apikey org-id catalogue-id)))
    (publish-catalogue-draft! apikey org-id catalogue-id)))

(defn query-invoices!
  [apikey application-id]
  (let [{:keys [ok invoices]} (itu/query apikey :application-invoices
                                         :id application-id)]
    (is ok "application invoices were queried")
    invoices))

(defn delete-invoice!
  [apikey application-id invoice-id]
  (let [{:keys [ok]} (itu/command apikey :delete-invoice
                                  :id application-id
                                  :invoice-id invoice-id)]
    (is ok "application invoice was deleted")))

;;; Fixtures

(use-fixtures :once (fn [test-fn]
                      (with-test-system (-> {:fixture            "invoicing-enabled"
                                             :schema-validation? false}
                                            test-system/with-remote-components)
                        ;; Register a filter to react to new attachments
                        (itu/upsert-automatic-assignment-filter
                          itu/vantaa-ya vantaa-org-id
                          {:name     "Verdict filter"
                           :criteria {:attachment-types ["muut.paatos"]}
                           :target   {:user-id itu/esa-id}
                           :email    {:emails  [assignment-handler-email]
                                      :message "New attachment!"}})
                        ;; Register an invoice catalogue
                        (create-and-publish-price-catalogue! itu/vantaa-ya vantaa-org-id)
                        (test-fn))))

(use-fixtures :each (fn [test-fn]
                      ;; Clear the email queue
                      (itu/sent-emails)
                      (test-fn)))

;;; Test helpers

(defn- submit!
  [{{:keys [verdict verdict-file attachments application-id]} :request
    :keys                                                     [options]}]
  (let [url   (format "%s/rest/application/%s/verdict" (itu/server-address) application-id)
        args  (merge {:throw-exceptions false
                      :basic-auth       [integration-user "vantaa"]}
                     (when (or verdict attachments)
                       {:multipart (->> (concat
                                          (when verdict
                                            [{:name      "verdict"
                                              :content   (json/encode verdict)
                                              :mime-type "application/json"
                                              :encoding  "UTF-8"}])
                                          (when verdict-file
                                            [{:name      "verdict"
                                              :content   verdict-file
                                              :mime-type "application/json"
                                              :encoding  "UTF-8"}])
                                          (->> attachments
                                               (map (fn [^File attachment]
                                                      {:name      (.getName attachment)
                                                       :content   attachment
                                                       :mime-type "text/plain"
                                                       :encoding  "UTF-8"}))))
                                        (into []))})
                     options)
        resp  (itu/http-post url args)
        json? (= "application/json;charset=utf-8"
                 (get-in resp [:headers "Content-Type"]))]
    (is json?)
    (if json?
      (update resp :body (fn [body] (json/decode body true)))
      resp)))

(defn- make-test-context!
  [{:keys [^File mock-attachment-file initial-state]}]
  (let [attachment-filename (.getName mock-attachment-file)
        operation!          (if (= :open initial-state)
                              itu/create-and-open-application
                              itu/create-and-submit-application)
        application         (operation!
                              itu/pena
                              :propertyId "09240900060124"
                              :operation operation)
        ;; Clear the email queue after creating the application
        _                   (itu/sent-emails)
        req                 {:options {}
                             :request {:application-id (:id application)
                                       :verdict        {:verdict-code  "granted"
                                                        :section       123
                                                        :decided-by    "Ahto Simakuutio"
                                                        :decision-date "2023-03-13"
                                                        :apply-date    "2023-04-01"
                                                        :attachments   {attachment-filename {:type        "verdict"
                                                                                             :created-at  "2023-03-12"
                                                                                             :description "Päätös"}}}
                                       :attachments    [mock-attachment-file]}}]
    {:filename    attachment-filename
     :application application
     :req         req}))

(defn- is-bad-request
  [{:keys [body] :as resp} text & errors]
  (is (ruh/bad-request? resp))
  (is (= {:status 400
          :ok     false
          :text   text
          :errors (into [] errors)}
         body)))

(defn- assert-no-side-effects
  [original-app]
  (let [{:keys [attachments] :as updated-app} (itu/query-application itu/pena (:id original-app))]
    (testing "No verdict was created"
      (is (= (:verdicts original-app)
             (:verdicts updated-app))))
    (testing "No attachment(s) were added"
      (is (= (-> original-app :attachments count)
             (count attachments))))
    (testing "application state was not changed"
      (is (= (:state original-app)
             (:state updated-app))))
    (testing "timestamp(s) were not updated"
      (let [dates (juxt :modified :verdictDate)]
        (is (= (dates original-app)
               (dates updated-app)))))
    (when (not= "open" (:state updated-app))
      ;; Don't query invoices for open apps -> throws an error
      (testing "invoice(s) were not generated"
        (let [invoices (query-invoices! itu/esa (:id updated-app))]
          (is (empty? invoices)))))
    (testing "email notification(s) were not sent"
      (is (empty? (itu/sent-emails))))))

(defn- timestamp-at-midnight
  [yyyy mm dd]
  (let [zdt (ZonedDateTime/of yyyy mm dd 0 0 0 0 (ZoneId/of "Europe/Helsinki"))]
    (-> zdt .toInstant .toEpochMilli)))

(defn- is-success
  "Verify the HTTP response and the various side-effects"
  [{:keys [body] :as resp} original-app]
  (is (ruh/success? resp))
  (is (= {:status 201
          :ok     true}
         (dissoc body :verdict-id)))
  (is (sc/validate ssc/ObjectIdStr (:verdict-id body)))
  (let [{:keys                  [attachments]
         [verdict :as verdicts] :verdicts
         :as                    updated-app} (itu/query-application itu/pena (:id original-app))]
    (testing "Verdict was created"
      (is (= 1 (count verdicts)))
      (is (= "Ahto Simakuutio"
             (get-in verdict [:paatokset 0 :poytakirjat 0 :paatoksentekija])))
      (is (= "123"
             (get-in verdict [:paatokset 0 :poytakirjat 0 :pykala])))
      (is (= "myonnetty"
             (get-in verdict [:paatokset 0 :poytakirjat 0 :verdict-code])))
      (is (= (timestamp-at-midnight 2023 3 13)
             (get-in verdict [:paatokset 0 :paivamaarat :anto])))
      (is (= (timestamp-at-midnight 2023 4 1)
             (get-in verdict [:paatokset 0 :paivamaarat :lainvoimainen]))))
    (testing "Attachment(s) were added"
      (is (= (-> original-app :attachments count inc)
             (count attachments))
          "One new attachment"))
    (testing "application state changed to verdict given"
      (is (not (application/verdict-given? original-app)))
      (is (application/verdict-given? updated-app)))
    (testing "timestamp(s) were updated"
      (is (< (:modified original-app)
             (:modified updated-app)))
      ;; NOTE: deadlines are not set, as we don't produce `:aloitettava` or `:voimassa` dates
      (is (= (timestamp-at-midnight 2023 3 13)
             (:verdictDate updated-app))))
    (testing "invoice(s) were generated"
      (let [[invoice :as invoices] (query-invoices! itu/esa (:id updated-app))]
        (is (= 1 (count invoices)))
        (is (= "draft"
               (-> invoice :state)))
        (is (= integration-user
               (-> invoice :created-by :username)))
        (delete-invoice! itu/esa (:id updated-app) (:id invoice))))
    (testing "email notification(s) were sent"
      (let [[state-change-email assignment-email :as emails] (->> (itu/sent-emails)
                                                                  (sort-by :to))]
        (is (= 2 (count emails)))
        (testing "email due to change in application state"
          (is (re-find #"pena@example.com" (:to state-change-email)))
          (is (re-find #"Päätös annettu" (:subject state-change-email))))
        (testing "email due to the automatic assignment filter"
          (is (re-find (re-pattern assignment-handler-email) (:to assignment-email)))
          (is (re-find #"Liite lisätty." (:subject assignment-email))))))))

(deftest rest-verdict-upload-as-string-i-test
  (files/with-temp-file mock-attachment-file
    (spit mock-attachment-file "some random content")
    (let [{:keys [application req]} (make-test-context!
                                      {:mock-attachment-file mock-attachment-file})]
      (testing "verdict (as a string) uploaded successfully"
        (let [resp (submit! req)]
          (is-success resp application)))

      (testing "uploading the same verdict twice fails"
        (let [app-with-verdict (itu/query-application itu/pena (:id application))
              resp             (submit! req)]
          (is-bad-request resp
                          "Application error"
                          {:field ["application-id"]
                           :error "application-has-verdict"})
          (assert-no-side-effects app-with-verdict))))))

(deftest rest-verdict-upload-as-json-i-test
  (files/with-temp-file mock-attachment-file
    (spit mock-attachment-file "some random content")
    (let [{:keys [application req]} (make-test-context!
                                      {:mock-attachment-file mock-attachment-file})]
      (testing "verdict (as a file) uploaded successfully"
        (files/with-temp-file verdict-file
          (let [json (-> req :request :verdict json/encode)
                _    (spit verdict-file json)
                req  (-> req
                         (update :request dissoc :verdict)
                         (update :request assoc :verdict-file verdict-file))
                resp (submit! req)]
            (is-success resp application)))))))

(deftest rest-verdict-failures-i-test
  (files/with-temp-file mock-attachment-file
    (spit mock-attachment-file "some random content")
    (let [{:keys [application filename req]} (make-test-context!
                                               {:mock-attachment-file mock-attachment-file})]
      (testing "various failure conditions"

        (testing "No user credentials provided"
          (let [req                     (assoc-in req [:options :basic-auth] [])
                {:keys [body] :as resp} (submit! req)]
            (is (ruh/forbidden? resp))
            (is (= {:status 403
                    :ok     false
                    :text   "Unauthenticated"
                    :errors []}
                   body))
            (assert-no-side-effects application)))

        (testing "User with no authorization provided"
          (let [req                     (assoc-in req [:options :basic-auth] ["sonja" "sonja"])
                {:keys [body] :as resp} (submit! req)]
            (is (ruh/unauthorized? resp))
            (is (= {:status 401
                    :ok     false
                    :text   "Unauthorized"
                    :errors []}
                   body))
            (assert-no-side-effects application)))

        (testing "invalid application id"
          (let [req  (assoc-in req [:request :application-id] "not-an-application-id")
                resp (submit! req)]
            (is-bad-request resp
                            "Invalid HTTP parameters"
                            {:field ["application-id"]
                             :error "not-valid"
                             :value "not-an-application-id"})
            (assert-no-side-effects application)))

        (testing "unknown application id"
          (let [req  (assoc-in req [:request :application-id] "LP-123-2023-12345")
                resp (submit! req)]
            (is-bad-request resp
                            "Application error"
                            {:field ["application-id"]
                             :error "application-not-found"
                             :value "LP-123-2023-12345"})
            (assert-no-side-effects application)))

        (testing "application not yet submitted"
          (let [{:keys [req application]} (make-test-context!
                                            {:mock-attachment-file mock-attachment-file
                                             :initial-state        :open})
                resp                      (submit! req)]
            (is-bad-request resp
                            "Application error"
                            {:field ["application-id"]
                             :error "application-incorrect-state"
                             :value "open"})
            (assert-no-side-effects application)))

        (testing "missing verdict"
          (let [req  (assoc-in req [:request :verdict] nil)
                resp (submit! req)]
            (is-bad-request resp
                            "Invalid HTTP parameters"
                            {:field ["verdict"]
                             :error "missing-required-key"})
            (assert-no-side-effects application)))

        (testing "invalid verdict"
          (are [value] (let [req  (assoc-in req [:request :verdict] value)
                             resp (submit! req)]
                         (is-bad-request resp
                                         "Failed to validate verdict data"
                                         {:field ["verdict"]
                                          :error "not-valid"
                                          :value value})
                         (assert-no-side-effects application))
            ;; Things which are not verdict maps
            42
            3.141
            [1 2 3]
            "asd"))

        (testing "missing verdict field"
          (let [req  (update-in req [:request :verdict] dissoc :section)
                resp (submit! req)]
            (is-bad-request resp
                            "Failed to validate verdict data"
                            {:field ["verdict" "section"]
                             :error "missing-required-key"})
            (assert-no-side-effects application)))

        (testing "unsupported verdict enumeration"
          (let [req  (assoc-in req [:request :verdict :verdict-code] "[object Object]")
                resp (submit! req)]
            (is-bad-request resp
                            "Failed to validate verdict data"
                            {:field    ["verdict" "verdict-code"]
                             :error    "not-valid"
                             :value    "[object Object]"
                             :expected ["accepted"
                                        "conditional"
                                        "denied"
                                        "granted"
                                        "granted-start"
                                        "statement"
                                        "tabled"
                                        "verdict-given"]})
            (assert-no-side-effects application)))

        (testing "incorrectly named verdict field"
          (let [req  (update-in req [:request :verdict] set/rename-keys {:section :section-nr})
                resp (submit! req)]
            (is-bad-request resp
                            "Failed to validate verdict data"
                            {:field ["verdict" "section"]
                             :error "missing-required-key"}
                            {:field ["verdict" "section-nr"]
                             :error "disallowed-key"})
            (assert-no-side-effects application)))

        (testing "missing attachment field"
          (let [req  (update-in req [:request :verdict :attachments filename] dissoc :created-at)
                resp (submit! req)]
            (is-bad-request resp
                            "Failed to validate verdict data"
                            {:field ["verdict" "attachments" filename "created-at"]
                             :error "missing-required-key"})
            (assert-no-side-effects application)))

        (testing "unsupported attachment type"
          (let [req  (update-in req [:request :verdict :attachments filename] assoc :type "invalid")
                resp (submit! req)]
            (is-bad-request resp
                            "Failed to validate verdict data"
                            {:field    ["verdict" "attachments" filename "type"]
                             :error    "not-valid"
                             :value    "invalid"
                             :expected ["verdict"
                                        "verdict-extract"]})
            (assert-no-side-effects application)))

        (testing "attachment name mismatch"
          (let [req  (update-in req [:request :verdict :attachments] set/rename-keys {filename (str "foo-" filename)})
                resp (submit! req)]
            (is-bad-request resp
                            "Failed to validate verdict data"
                            {:error    "missing-attachments"
                             :expected [(str "foo-" filename)]
                             :actual   [filename]})
            (assert-no-side-effects application)))))))

(deftest existing-draft-verdicts-test
  (files/with-temp-file mock-attachment-file
    (spit mock-attachment-file "some random content")
    (let [{:keys [application req]} (make-test-context!
                                      {:mock-attachment-file mock-attachment-file})
          app-id                    (:id application)]
      (testing "Draft verdict does not prohibit verdict upload."
        (is (itu/ok? (itu/command itu/esa :new-pate-verdict-draft :id app-id
                                  :template-id minimal/vantaa-ya-template-id)))
        (let [a    (itu/query-application itu/esa app-id)
              resp (submit! req)]
          (is-success resp a))))) )
