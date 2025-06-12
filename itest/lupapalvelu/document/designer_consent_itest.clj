(ns lupapalvelu.document.designer-consent-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.date :as date]
            [sade.util :as util]))

(def no-consent {:ok false :text "no-consent"})

(def missing-params {:ok     false
                     :reason "error.missing-parameters"
                     :text   "error.missing-parameters"})
(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components)
  (facts "designer consent and approve-doc"
    (let [;; submit so authority can also access to application
          application                (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
          application-id             (:id application)
          suunnittelija-resp         (command pena :create-doc :id application-id :schemaName "suunnittelija")
          suunnittelija-update-resp  (command pena :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates (data->updates (dissoc suunnittelija-required-data "osoite.katu")))

          suunnittelija2-resp        (command pena :create-doc :id application-id :schemaName "suunnittelija")
          suunnittelija2-update-resp (command pena :update-doc :id application-id :doc (:doc suunnittelija2-resp) :updates (data->updates suunnittelija-required-data))]

      (fact "Designer document auth model within application response"
        (let [actions (->> (query-application sonja application-id)
                           :documents
                           (util/find-by-id (:doc suunnittelija-resp))
                           :allowedActions)]
          (fact
              actions => (contains {:approve-doc            missing-params
                                    :designer-consent       {:ok false :text "error.user-is-not-the-designer"}
                                    :designer-consent-given no-consent}))))

      (fact "update-doc response contains authModel, applicant doesn't have permission for approve-doc"
        suunnittelija-update-resp => ok?
        suunnittelija2-update-resp => ok?
        (get-in suunnittelija-update-resp [:results 0]) => (contains {:path   ["osoite" "katu"]
                                                                      :result ["tip" "illegal-value:required"]})
        (get-in suunnittelija-update-resp [:authModel :approve-doc]) => {:ok false :text "error.unauthorized"}
        (get-in suunnittelija-update-resp [:authModel :designer-consent-given])
        => no-consent)

      (let [resp (command sonja :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates [["designerConsent" true]])]
        (fact "update-doc can't be used to set consent fields"
          resp => {:ok   false
                   :text "error-trying-to-update-readonly-field"}))

      (let [resp (command sonja :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates [])]
        (fact "update-doc response for authority user, missing required field"
          resp => ok?
          (get-in resp [:authModel :approve-doc]) => missing-params
          (get-in suunnittelija-update-resp [:authModel :designer-consent-given]) => no-consent))

      (let [resp (command sonja :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates [["osoite.katu" "Testi A"]])]
        (fact "update-doc response for authority user, valid document, missing consent"
          resp => ok?
          (get-in resp [:authModel :approve-doc]) => {:ok     false
                                                      :text   "error.designer.consent-missing"
                                                      :reason "error.designer.consent-missing"}
          (get-in resp [:authModel :designer-consent-given]) => no-consent))

      (let [resp (command pena :designer-consent :id application-id :documentId (:doc suunnittelija-resp) :consent true)]
        (fact "designer can give the consent"
          resp => ok?
          (get-in resp [:authModel :designer-consent-given]) => {:ok true}))

      (let [resp (command sonja :update-doc :id application-id :doc (:doc suunnittelija-resp) :updates [])]
        (fact "update-doc response for authority user, valid document, with consent"
          resp => ok?
          (get-in resp [:authModel :approve-doc]) => {:ok true}
          (get-in resp [:authModel :designer-consent-given]) => {:ok true}))

      (let [resp (command sonja :approve-doc :id application-id :doc (:doc suunnittelija-resp) :path nil :collection "documents")]
        (fact "approve-doc"
          resp => ok?))

      (fact "Check approve given with attachment works"
        (upload-file-and-bind sonja application-id
                              {:contents ""
                               :group    {:groupType "parties"}
                               :type     {:type-id    "suostumus"
                                          :type-group "osapuolet"}
                               :target   {:type "suunnittelija-suostumus"
                                          :id   (:doc suunnittelija2-resp)}})

        (let [app (query-application pena application-id)]
          (fact "attachment uploaded with target"
            (:attachments app) => (contains [(contains {:target {:id   (:doc suunnittelija2-resp)
                                                                 :type "suunnittelija-suostumus"}})])))

        (fact "auth-model correctly checks for attachments"
          (let [resp (command sonja :update-doc :id application-id :doc (:doc suunnittelija2-resp) :updates [])]
            (fact
                resp => ok?
                (get-in resp [:authModel :approve-doc]) => {:ok true}
                ;; no consent flag, this query doesn't include consent from attachment
                (get-in resp [:authModel :designer-consent-given]) => {:ok false :text "no-consent"})))

        (let [resp (command sonja :approve-doc :id application-id :doc (:doc suunnittelija2-resp) :path nil :collection "documents")]
          (fact "approve-doc correctly checks for the attachments"
            resp => ok?)))

      (fact "Check update-doc clears the approved state"
        (let [resp (query pena :application :id application-id)]
          (fact
              (-> resp :applicationApprovals (get (keyword (:doc suunnittelija2-resp))) :_root)
            => (just {:value     "approved"
                      :timestamp anything
                      :user      anything})))

        (let [update-resp (command pena :update-doc :id application-id :doc (:doc suunnittelija2-resp) :updates [["designerConsentAttachment" nil]])
              resp        (query pena :application :id application-id)]
          (fact "approved marked changed by update"
            update-resp => ok?

            (-> resp :applicationApprovals (get (keyword (:doc suunnittelija2-resp))) :_root)
            => (just {:value     "approved"
                      :timestamp anything
                      :user      anything
                      :changed   anything}))))

      (fact "Check designer-consent toggle clears approved state"
        (let [resp (command sonja :approve-doc :id application-id :doc (:doc suunnittelija2-resp) :path nil :collection "documents")]
          (fact "approve-doc correctly checks for the attachments"
            resp => ok?))

        (let [resp (query pena :application :id application-id)]
          (fact
              (-> resp :applicationApprovals (get (keyword (:doc suunnittelija2-resp))) :_root)
            => (just {:value     "approved"
                      :timestamp anything
                      :user      anything})))

        (let [update-resp (command pena :designer-consent :id application-id :documentId (:doc suunnittelija2-resp) :consent true)
              resp        (query pena :application :id application-id)]
          (fact "approved marked changed by designer-consent"
            update-resp => ok?

            (-> resp :applicationApprovals (get (keyword (:doc suunnittelija2-resp))) :_root)
            => (just {:value     "approved"
                      :timestamp anything
                      :user      anything
                      :changed   anything}))))

      (let [resp (command pena :update-doc :id application-id :doc (:doc suunnittelija2-resp) :updates [["yhteystiedot.email" "foo@example.com"]])]
        (fact "email update cleans the consent flag"
          resp => ok?
          (get-in resp [:authModel :designer-consent-given]) => no-consent)

        (let [resp (command pena :update-doc :id application-id :doc (:doc suunnittelija2-resp) :updates [["yhteystiedot.email" "pena@example.com"]])]
          (fact "restore email"
            resp => ok?
            (get-in resp [:authModel :designer-consent]) => {:ok true})))

      (fact "approve application"
        (let [resp (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon")]
          (fact
              resp => ok?))

        (let [resp (command sonja :approve-application :id application-id :lang "fi")]
          (fact
              resp => ok?))

        (let [app (query-application pena application-id)
              doc (domain/get-document-by-id app (:doc suunnittelija2-resp))]
          (fact "applicant can't call update-doc or designer-consent document on approved application"
            (:state app) => "sent"
            (get-in doc [:allowedActions :update-doc]) => {:ok false :text "error.document-not-editable-in-current-state"}
            (get-in doc [:allowedActions :designer-consent]) => {:ok false :text "error.document-not-editable-in-current-state"})))

      (fact "post verdict"
        (let [template-id              (->> pate-fixture/verdict-templates-r
                               (filter #(= "r" (:category %)))
                               (first)
                               :id)
              {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft :id application-id :template-id template-id)]
          (fill-verdict sonja application-id verdict-id
                        :verdict-code "hyvaksytty"
                        :verdict-text "Verdict given"
                        :verdict-date (date/timestamp "13.3.2024")
                        :giver "päättäjä")

          (fact "check for backend verdict"
            (command sonja :check-for-verdict :id application-id) => ok?
            (let [app (query-application pena application-id)
                  doc (domain/get-document-by-id app (:doc suunnittelija2-resp))]
              (fact
                  (:state app) => "verdictGiven"
                  (-> app :verdicts count) => pos?
                  (get-in doc [:allowedActions :update-doc]) => {:ok false :text "error.document.pre-verdict-document"}
                  (get-in doc [:allowedActions :designer-consent]) => {:ok false :text "error.document.pre-verdict-document"})))))

      (fact "post-verdict designer"
        (let [suunnittelija3-resp        (command pena :create-doc :id application-id :schemaName "suunnittelija")
              suunnittelija3-update-resp (command pena :update-doc :id application-id :doc (:doc suunnittelija3-resp) :updates (data->updates suunnittelija-required-data))]
          (fact
              suunnittelija3-update-resp => ok?)

          (let [resp (command pena :designer-consent :id application-id :documentId (:doc suunnittelija3-resp) :consent true)]
            (fact "designer can give the consent"
              resp => ok?
              (get-in resp [:authModel :designer-consent-given]) => {:ok true}))

          (let [resp (command sonja :approve-doc :id application-id :doc (:doc suunnittelija3-resp) :path nil :collection "documents")]
            (fact "approve-doc"
              resp => ok?))

          (let [app (query-application pena application-id)
                doc (domain/get-document-by-id app (:doc suunnittelija3-resp))]
            (fact "can't consent approved post-verdict designer"
              (:state app) => "verdictGiven"
              (get-in doc [:allowedActions :update-doc]) => {:ok false :text "error.document.approved"}
              (get-in doc [:allowedActions :designer-consent]) => {:ok false :text "error.document.approved"}))))

      (let [app (query-application sonja application-id)
            doc (domain/get-document-by-id app (:doc suunnittelija-resp))]
        (fact "approved meta has use-designer-approval flag"
          (:meta doc) => (contains {:_approved (contains {:use-designer-approval true
                                                          :value                 "approved"})}))))))
