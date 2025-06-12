(ns lupapalvelu.notices-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest-util
             :refer [command create-and-submit-local-application create-local-app expected-failure? fail? last-email
                     local-fixture-factory midje? ok? pena query query-application sipoo sonja unauthorized? admin]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notices.batchrun :as batchrun]
            [lupapalvelu.notices.core :as notices]
            [lupapalvelu.notices.util :as notices-util]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer [contains just truthy]]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(use-fixtures :once (local-fixture-factory (-> {}
                                               test-system/with-remote-components
                                               test-system/with-mock-laundry
                                               (test-system/with-pubsub-publisher :notices/resolution-queue)
                                               (test-system/with-pubsub-subscriber :notices/resolution-queue))))

(defn use-ryhti-in-organization [user organization-id]
  (let [result (command user :set-organization-ryhti-data-2026-start-ts
                        :organizationId organization-id
                        :ryhti-data-2026-start-ts 1)]
    (when (not (:ok result))
      (throw (ex-info "Failed to enable Ryhti in organization" {:organization-id organization-id
                                                                :result result})))))

(use-fixtures :each (fn [run-tests]
                      (use-ryhti-in-organization admin "753-R")))

(defn update-doc [apikey app-id doc-id path value]
  (command apikey :update-doc :id app-id
           :collection "documents"
           :doc doc-id
           :updates [[(name path) value]]))

(defn has-document? [application doc-name]
  (->> application
       :documents
       (some (util/fn-> :schema-info :name (= doc-name)))
       boolean))

(def no-document? (complement has-document?))

(def kw->typeseq (util/fn->> util/split-kw-path (mapv name)))

(defn has-attachment? [application att-type]
  (let [att-type (kw->typeseq att-type)]
    (->> application
         :attachments
         (some (fn [{type :type}]
                 (= ((juxt :type-group :type-id) type)
                    att-type)))
         boolean)))

(def no-attachment? (complement has-attachment?))

(defn state-email-sent [app-id state]
  (let [state (i18n/localize :fi state)
        email (last-email)]
    (is (ss/ends-with? (:subject email) (str "hankkeen tila on nyt " state)))
    (is (-> email :body :plain (ss/includes? app-id)))))

(defn initialize-tos-functions []
  ;; NOTE: Previously set-organization-boolean-attribute + set-tos-function-for-operation,
  ;; but the TOJ server used by CI iTest doesn't have reliably tosFunctions values set
  ;; for organizations, so tosFunction validation fails.
  ; (is (ok? (command itest-util/admin :set-organization-boolean-attribute
  ;                   :attribute "permanent-archive-enabled"
  ;                   :enabled true
  ;                   :organizationId "753-R")))
  ; (is (ok? (command itest-util/sipoo :set-tos-function-for-operation
  ;                   :organizationId "753-R"
  ;                   :operation "purkaminen"
  ;                   :functionCode "10 03 00 04")))
  ; (is (ok? (command itest-util/sipoo :set-tos-function-for-operation
  ;                   :organizationId "753-R"
  ;                   :operation "purkamisilmoitus"
  ;                   :functionCode "10 03 00 09")))
  ;; Go setup the organization config for tosFunctions in Mongo directly, so we can skip the validation.
  (org/update-organization "753-R"
                           {$set {:permanent-archive-enabled true
                                  "operations-tos-functions.purkaminen" "10 03 00 04"
                                  "operations-tos-functions.purkamisilmoitus" "10 03 00 09"}}))

(deftest demolition-notice-to-application-test
  (initialize-tos-functions)
  (let [{app-id :id
         :as    application} (create-and-submit-local-application pena
         :address "Ruins Road"
         :operation "purkamisilmoitus")
        doc-id               (:id (notices-util/purkamisilmoitus-doc application))
        pena-update          (partial update-doc pena app-id doc-id)
        page                 :rakennuspaikka.karttaote
        plan                 :suunnitelmat.opastesuunnitelma]
    (is (ss/not-blank? app-id))
    (is (ss/not-blank? doc-id))
    (is (no-document? application "suunnittelija"))
    (is (no-document? application "paasuunnittelija"))
    (is (no-document? application "paatoksen-toimitus-rakval"))
    (is (nil? (:convertedToApplication application)))
    (is (ok? (command sipoo :organization-operations-attachments
                      :organizationId "753-R"
                      :operation "purkaminen"
                      :attachments [(kw->typeseq page)
                                    (kw->typeseq plan)]))
        "Required attachments for purkaminen")
    (is (no-attachment? application page))
    (is (no-attachment? application plan))
    (is (= "10 03 00 09" (:tosFunction application)))

    (testing "Non-building demolition cannot be changed into an application."
      (is (ok? (pena-update :buildingDemolition false)))
      (is (ok? (pena-update :kohde "Wipeout")))
      (is (expected-failure? :error.not-building-demolition
                             (command pena :demolition-notice-to-application :id app-id))))

    (testing "Building demolition can be changed into an application."
      (is (ok? (pena-update :buildingDemolition true)))
      (is (ok? (command pena :merge-details-from-krysp :id app-id
                        :buildingId "199887766E"
                        :documentId doc-id
                        :overwrite true)))
      (is (ok? (command pena :demolition-notice-to-application :id app-id)))
      (testing "Notice is now an application."
        (let [a   (mongo/by-id :applications app-id)
              doc (util/find-by-id doc-id (:documents a))]
          (is (= "purkaminen" (:operation-name a)))
          (is (-> a :primaryOperation :name (= "purkaminen")))
          (is (-> doc :schema-info :name (= "rakennuskohteenPurkaminen")))
          (is (-> doc :schema-info :op :name (= "purkaminen")))
          (is (= "10 03 00 04" (:tosFunction a)))
          (is (pos? (:convertedToApplication a)))
          (is (no-document? a "purkamisilmoitus"))
          (is (has-document? a "suunnittelija"))
          (is (has-document? a "paasuunnittelija"))
          (is (has-document? a "paatoksen-toimitus-rakval"))
          (is (= #{:buildingId :rakennuksenOmistajat :kuvaus :kaytto :latauspiste
                   :rakennuskohteen-osa :ryhti :sijaintitiedot :osoite :mitat
                   :manuaalinen_rakennusnro :pysyva-rakennelmatunnus :pysyva-rakennustunnus}
                 (-> doc :data keys set)))
          (is (has-attachment? a page))
          (is (has-attachment? a plan))))
      (testing "Application cannot be retransformed"
        (is (expected-failure? :error.unauthorized
                               (command pena :demolition-notice-to-application :id app-id)))))))

(deftest notice-resolution-test
  (let [{app-id :id} (create-local-app pena :address "Wreckage Way"
                                       :operation "purkamisilmoitus")
        application  (query-application pena app-id)
        doc-id       (:id (notices-util/purkamisilmoitus-doc application))
        pena-update  (partial update-doc pena app-id doc-id)]
    (is (ss/not-blank? doc-id))
    (testing "No resolution functionality for drafts."
      (is (unauthorized? (query pena :notice-resolution :id app-id)))
      (is (unauthorized? (query sonja :notice-resolution :id app-id)))
      (is (unauthorized? (command sonja :save-notice-resolution :id app-id
                                  :state "approved"
                                  :text [{:insert ""}]))))

    (testing "Editing resolution."
      (is (ok? (command pena :submit-application :id app-id)))
      (is (unauthorized? (query pena :notice-resolution :id app-id)))
      (is (nil? (:resolution (query sonja :notice-resolution :id app-id))))
      (is (unauthorized? (command pena :save-notice-resolution :id app-id
                                  :state "approved"
                                  :text [{:insert "Hello"}])))
      (is (ok? (command sonja :save-notice-resolution :id app-id
                        :state "approved"
                        :text [{:insert "Hello"}])))
      (testing "Bad saves."
        (doseq [args [[:state "bad"] [:text [{:bad "bad"}]]]]
          (is (fail? (apply command sonja :save-notice-resolution :id app-id
                            args)))))
      (is (midje? (just {:state    "approved"
                         :text     [{:insert "Hello"}]
                         :modified pos?
                         :user     {:firstName "Sonja" :lastName "Sibbo"}})
                  (:resolution (query sonja :notice-resolution :id app-id))))
      (is (nil? (:_noticeResolution (query-application sonja app-id)))
          "Resolution not part of the application query response."))

    (testing "Deleting resolution."
      (is (unauthorized? (command pena :delete-draft-notice-resolution :id app-id)))
      (is (unauthorized? (command pena :delete-published-notice-resolution
                                  :id app-id)))
      (is (unauthorized? (command sonja :delete-published-notice-resolution
                                  :id app-id)))
      (is (ok? (command sonja :delete-draft-notice-resolution :id app-id)))
      (is (nil? (:resolution (query sonja :notice-resolution :id app-id)))))

    (testing "Publishing resolution."
      (is (unauthorized? (command pena :publish-notice-resolution :id app-id
                                  :lang "fi"
                                  :state "approved"
                                  :text [{:insert "Hello"}])))
      (testing "Demolition target not defined"
        (let [publish-fail? #(expected-failure? :error.notice.demolition-target
                                                (command sonja :publish-notice-resolution :id app-id
                                                         :lang "fi"
                                                         :state "approved"
                                                         :text [{:insert "Hello"}]))]
          (is (publish-fail?) "No building selected.")
          (is (ok? (pena-update :buildingId "other")))
          (is (ok? (pena-update :manuaalinen_rakennusnro "   ")))
          (is (publish-fail?) "No manual building number.")
          (is (ok? (pena-update :buildingDemolition false)))
          (is (ok? (pena-update :kohde "   \n  ")))
          (is (publish-fail?) "No non-building note.")))
      (testing "Success."
        (is (ok? (pena-update :kohde "Everything must go!")))
        (let [{:keys [resolution]}    (command sonja :publish-notice-resolution
                                               :id app-id
                                               :lang "fi"
                                               :state "approved"
                                               :text [{:insert "Fine by me."}])
              {:keys [state history]} (query-application pena app-id)]
          (is (midje? (just {:state     "approved"
                             :text      [{:insert "Fine by me."}]
                             :published pos?
                             :user      {:firstName "Sonja" :lastName "Sibbo"}
                             :job-id    ss/not-blank?})
                      resolution))
          (is (= "ready" state))
          (is (midje? (just {:state "ready" :ts pos?
                             :user  (-> sonja
                                        usr/get-user-with-apikey
                                        usr/summary)})
                      (last history)))
          (testing "PDF generation."
            (let [{:keys [attachment-id]
                   :as   resolution} (:resolution (query pena :poll-notice-resolution
                                                         :id app-id))]
              (is (midje? (just {:state         "approved"
                                 :text          [{:insert "Fine by me."}]
                                 :published     pos?
                                 :user          {:firstName "Sonja" :lastName "Sibbo"}
                                 :attachment-id ss/not-blank?})
                          resolution))
              (is (midje? (contains {:contents         "Asian käsittely"
                                     :applicationState "ready"
                                     :type             {:type-group "paatoksenteko"
                                                        :type-id    "ilmoitus"}
                                     :readOnly         true
                                     :locked           true
                                     :latestVersion    truthy})
                          (->> (query-application pena app-id)
                               :attachments
                               (util/find-by-id attachment-id)))))))
        (state-email-sent app-id :ready))
      (testing "Delete published resolution"
        (is (unauthorized? (command pena :delete-published-notice-resolution
                                    :id app-id)))
        (is (expected-failure? :error.notice-resolution-published
                               (command sonja :delete-draft-notice-resolution
                                        :id app-id)))
        (is (ok? (command sonja :delete-published-notice-resolution
                          :id app-id)))
        (is (midje? (contains {:state       "submitted"
                               :attachments empty?})
                    (query-application sonja app-id)))
        (state-email-sent app-id :submitted))
      (testing "Long poll"
        (binding [notices/*sleep-in-ms* 10]
          (ok? (command sonja :publish-notice-resolution :id app-id
                        :lang "fi"
                        :state "rejected"
                        :text [{:insert "Not my cup of tea."}]))
          (is (= {:ok true :waiting true}
                 (query sonja :poll-notice-resolution :id app-id))))
        (is (midje? (just {:state         "rejected"
                           :published     pos?
                           :text          [{:insert "Not my cup of tea."}]
                           :user          {:firstName "Sonja" :lastName "Sibbo"}
                           :attachment-id truthy})
                    (:resolution (query sonja :poll-notice-resolution :id app-id))))
        (state-email-sent app-id :ready)))))

(deftest notice-submitted-to-application
  (initialize-tos-functions)
  (let [{app-id :id} (create-local-app pena
                                       :address "Wreckage Way"
                                       :operation "purkamisilmoitus"
                                       :kohde "Foo")
        application  (query-application pena app-id)
        doc-id       (:id (notices-util/purkamisilmoitus-doc application))]

    (is (ss/not-blank? doc-id))

    (is (= "draft" (:state application)))
    (is (nil? (:submitted application)))

    (is (ok? (command pena :submit-application :id app-id)))

    (let [application (query-application sonja app-id)]
      (is (= "10 03 00 09" (:tosFunction application)))

      ;; UI shows the "Käsittelyaika: x päivää jäljellä"
      ;; if application state is submitted and submitted timestamp is present
      (is (= "submitted" (:state application)))
      (is (some? (:submitted application)))

      (is (ok? (command sonja :demolition-notice-to-application :id app-id)))

      (let [application (query-application sonja app-id)]

        (is (= "10 03 00 04" (:tosFunction application)))

        ;; presumably to hide the käsittelyaika when ilmoitus is converted to "regular" application,
        ;; state and/or submitted value need to be changed or cleared?

        (is (= "open" (:state application)))
        (is (nil? (:submitted application)))
        ))))

(deftest batchrun-test
  (let [{app-id :id} (create-and-submit-local-application pena
                                                          :address "Batch Building"
                                                          :operation "purkamisilmoitus")
        application  (query-application pena app-id)
        doc-id       (:id (notices-util/purkamisilmoitus-doc application))]
    (is (ok? (command pena :merge-details-from-krysp :id app-id
                      :documentId doc-id :buildingId "122334455R"
                      :overwrite true)))
    (testing "Batchrun: automatic approval"
      (mongo/update-by-id :applications app-id {$set {:submitted (date/timestamp "1.6.2025")}})
      (is (= 1 (batchrun/process-due-demolition-notices (test-system/get-common-components))))
      (is (midje? (just {:state         "approved"
                         :published     pos?
                         :text          [{:insert (i18n/localize :fi :notice.automatic-ready.text)}]
                         :user          {:firstName "Lupapiste" :lastName "Eräajo"}
                         :attachment-id truthy})
                  (:resolution (query sonja :poll-notice-resolution :id app-id))))
      (is (midje? (just {:state    "ready" :ts pos?
                         :info-loc "notice.automatic-ready.text"
                         :user     (usr/summary (usr/batchrun-user ["753-R"]))})
                  (-> (mongo/by-id :applications app-id [:history])
                      :history last)))
      (state-email-sent app-id :ready)
      (is (zero? (batchrun/process-due-demolition-notices (test-system/get-common-components))))
      (is (midje? (contains {:action    "Ei asetettu tiedonohjaussuunnitelmassa"
                             :documents (just (contains {:contents "Asian käsittely"
                                                         :type     {:type-group "paatoksenteko"
                                                                    :type-id    "ilmoitus"}
                                                         :user     "Eräajo Lupapiste"}))
                             :info      (i18n/localize :fi :notice.automatic-ready.text)
                             :user      "Eräajo Lupapiste"})
                  (->> (query sonja :case-file-data :id app-id :lang "fi")
                       :process last))
          "Case file data"))))
