(ns lupapalvelu.application-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.string :refer [join]]
            [sade.core :refer [unauthorized]]
            [sade.env :as env]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-api :as app]
            [lupapalvelu.application :as a]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.operations :as op]
            [lupapalvelu.document.tools :as tools]))

(apply-remote-minimal)

(command admin :set-organization-scope-pate-value
         :permitType "R"
         :municipality "753"
         :value true)

(fact "can't inject js in 'x' or 'y' params"
   (create-app pena :x ";alert(\"foo\");" :y "what ever") =not=> ok?
   (create-app pena :x "0.1x" :y "1.0")                   =not=> ok?
   (create-app pena :x "1x2" :y "1.0")                    =not=> ok?
   (create-app pena :x "2" :y "1.0")                      =not=> ok?
   (create-app pena :x "410000.1" :y "6610000.1")         => ok?)

(fact "creating application without message"
  (let [id    (create-app-id pena)
        app   (query-application pena id)]
    app => (contains {:id id
                      :state "draft"
                      :location {:x 444444.0 :y 6666666.0}
                      :organization "753-R"})
    (count (:comments app)) => 0
    (first (:auth app)) => (contains
                             {:firstName "Pena"
                              :lastName "Panaani"
                              :role "writer"})
    (fact "has allowedAttachmentTypes" (:allowedAttachmentTypes app) => seq)

    (fact "Draft is not returned by latest-applications"
      (let [resp (query pena :latest-applications)]
        resp => ok?
        (-> resp :applications count) => 0))))

(fact "creating application with message"
  (let [application-id  (create-app-id pena :messages ["hello"])
        application     (query-application pena application-id)]
    (:state application) => "draft"
    (:opened application) => nil
    (count (:comments application)) => 1
    (-> (:comments application) first :text) => "hello"))

(fact "application created to Sipoo belongs to organization Sipoon Rakennusvalvonta"
  (let [application-id  (create-app-id pena :propertyId sipoo-property-id)
        application     (query-application pena application-id)]
    (:organization application) => "753-R"))

(fact "the ready-calculated validation errors about required document fields, included by a newly created application, are updated when those application fields are filled"
  (let [application-id  (create-app-id pena :propertyId sipoo-property-id)
        application     (query-application pena application-id)
        hakija          (domain/get-applicant-document (:documents application))
        errs            (:validationErrors hakija)]
    (count errs) => pos?
    (some #(= "illegal-value:required" (-> % :result second)) errs)

    (generate-documents application pena)

    (not-any? #(= "illegal-value:required" (-> % :result second)) errs)))

(fact "application created to Tampere belongs to organization Tampereen Rakennusvalvonta"
  (let [application-id  (create-app-id pena :propertyId tampere-property-id)
        application     (query-application pena application-id)]
    (:organization application) => "837-R"))

(fact "application created to Reisjarvi belongs to organization Peruspalvelukuntayhtyma Selanne"
  (let [application-id  (create-app-id pena :propertyId "62600000000000")
        application     (query-application pena application-id)]
    (:organization application) => "069-R"))

(facts upsert-application-handler
  (let [{app-id :id modified :modified :as application} (create-and-submit-application pena :propertyId sipoo-property-id)
        resp     (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111111acdc" :userId ronja-id)]

    (fact "Initially there is no handlers"
      (:handlers application) => empty?)

    (facts "Insert new handler"
      (let [handlers (:handlers (query-application sonja app-id))]

        resp => ok?
        (:id resp) => string?

        (fact "one handler exists" (count handlers) => 1)
        (fact "handler has all data" (-> handlers first) => {:id (:id resp)
                                                             :roleId "abba1111111111111111acdc"
                                                             :userId ronja-id
                                                             :firstName "Ronja"
                                                             :lastName "Sibbo"
                                                             :general true
                                                             :name {:en "Handler", :fi "K\u00e4sittelij\u00e4", :sv "Handl\u00e4ggare"}})))

    (facts "Applicant is not allowed to edit handlers"
      (command pena :upsert-application-handler :id app-id :roleId "abba1111111111111112acdc" :userId sonja-id)
      => (partial expected-failure? :error.unauthorized))

    (facts "Cannot insert handler that is not organization authority"
      (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111112acdc" :userId veikko-id)
      => (partial expected-failure? :error.unknown-handler))

    (facts "Cannot insert handler with role that is not in organization roles"
      (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111111fooo" :userId ronja-id)
      => (partial expected-failure? :error.unknown-handler))

    (facts "Cannot insert handler with same role twice"
      (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111111acdc" :userId ronja-id)
      => (partial expected-failure? :error.duplicate-handler-role))

    (facts "Set existing handler role"
      (let [update-resp (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111112acdc" :userId ronja-id :handlerId (:id resp))
            handlers    (:handlers (query-application sonja app-id))]

        update-resp => ok?
        (:id update-resp) => (:id resp)

        (fact "handler upsert should not update the modified timestamp"
          (:modified (query-application sonja app-id)) => modified)

        (fact "one handler still exists" (count handlers) => 1)
        (fact "handler has all data" (-> handlers first) => {:id (:id resp)
                                                             :roleId "abba1111111111111112acdc"
                                                             :userId ronja-id
                                                             :firstName "Ronja"
                                                             :lastName "Sibbo"
                                                             :name {:en "KVV-Handler", :fi "KVV-K\u00e4sittelij\u00e4", :sv "KVV-Handl\u00e4ggare"}})))

    (facts "Set existing handler user"
      (let [update-resp (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111112acdc" :userId sonja-id :handlerId (:id resp))
            handlers    (:handlers (query-application sonja app-id))]

        update-resp => ok?
        (:id update-resp) => (:id resp)

        (fact "one handler still exists" (count handlers) => 1)
        (fact "handler has all data" (-> handlers first) => {:id (:id resp)
                                                             :roleId "abba1111111111111112acdc"
                                                             :userId sonja-id
                                                             :firstName "Sonja"
                                                             :lastName "Sibbo"
                                                             :name {:en "KVV-Handler", :fi "KVV-K\u00e4sittelij\u00e4", :sv "KVV-Handl\u00e4ggare"}})))

    (facts "Insert second handler"
      (let [insert-resp (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111111acdc" :userId sonja-id)
            handlers    (:handlers (query-application sonja app-id))]

        resp => ok?
        (:id insert-resp) => string?

        (fact "two handlers exists" (count handlers) => 2)
        (fact "second handler has all data" handlers => [{:id (:id resp)
                                                          :roleId "abba1111111111111112acdc"
                                                          :userId sonja-id
                                                          :firstName "Sonja"
                                                          :lastName "Sibbo"
                                                          :name {:en "KVV-Handler", :fi "KVV-K\u00e4sittelij\u00e4", :sv "KVV-Handl\u00e4ggare"}}
                                                         {:id (:id insert-resp)
                                                          :roleId "abba1111111111111111acdc"
                                                          :userId sonja-id
                                                          :firstName "Sonja"
                                                          :lastName "Sibbo"
                                                          :general true
                                                          :name {:en "Handler", :fi "K\u00e4sittelij\u00e4", :sv "Handl\u00e4ggare"}}])))

    (facts "Handler changes are stored in application history"
      (let [history (:history (query-application sonja app-id))
            handler-history (filter :handler history)]

        (fact "total handler entries"
          (count handler-history) => 4)

        (fact "new entries"
          (count (filter (comp :new-entry :handler) handler-history)) => 2)))))

(facts remove-application-handler
  (let [{app-id :id :as application} (create-and-submit-application pena :propertyId sipoo-property-id)
        resp (command sonja :upsert-application-handler :id app-id :roleId "abba1111111111111111acdc" :userId ronja-id)]

    (fact "Handler is added"
      resp => ok?)

    (fact "Handler is in application"
      (:handlers (query-application sonja app-id)) => not-empty)

    (fact "Handler role cannot be removed if not in application handlers"
      (command sonja :remove-application-handler :id app-id :handlerId sonja-id) => (partial expected-failure? :error.unknown-handler))

    (facts "Applicant is not allowed to remove handlers"
      (command pena :remove-application-handler :id app-id :handlerId (:id resp))
      => (partial expected-failure? :error.unauthorized))

    (fact "Authority removes handler"
      (command sonja :remove-application-handler :id app-id :handlerId (:id resp)) => ok?)

    (fact "Handler is removed"
      (:handlers (query-application sonja app-id)) => empty?)

    (facts "Handler changes are stored in application history"
      (let [history (:history (query-application sonja app-id))
            handler-history (filter :handler history)]

        (fact "total handler entries"
          (count handler-history) => 2)

        (fact "remove entries"
          (count (filter (comp :removed :handler) handler-history)) => 1)))))

(fact "Authority is able to create an application to a municipality in own organization"
  (let [application-id  (create-app-id sonja :propertyId sipoo-property-id)]
    (fact "Application is open"
      (let [application (query-application sonja application-id)]
        application => truthy
        (:state application) => "open"
        (:opened application) => truthy
        (:opened application) => (:created application)))
    (fact "Authority could submit her own application"
      sonja => (allowed? :submit-application :id application-id))
    (fact "Application is submitted"
      (let [resp        (command sonja :submit-application :id application-id)
            application (query-application sonja application-id)]
        resp => ok?
        (:state application) => "submitted"
        (-> application :history last :state) => "submitted"
        (-> application :history butlast last :state) => "open"))))

(facts* "Application has opened when submitted from draft"
  (let [{id :id :as app1} (create-application pena) => truthy
        _ (comment-application pena id true)
        resp (command pena :submit-application :id id) => ok?
        app2 (query-application pena id) => truthy]
    (:opened app1) => nil
    (:opened app2) => number?))

(facts* "authority cannot submit application for applicant before it has been opened"
  (let [{id :id :as app} (create-application pena)
        resp (command sonja :submit-application :id id)]
    (:state (query-application sonja id)) => "draft"
    resp => fail?))

(facts* "Authority can submit application for applicant after it has been opened"
  (let [{id :id :as app} (create-application pena)
        _ (comment-application pena id true)
        resp (command sonja :submit-application :id id)]
    (:state (query-application sonja id)) => "submitted"
    resp => ok?))

(facts "enable-accordions pseudo-query"
  (let [{id :id :as app} (create-and-open-application
                           pena
                           :propertyId tampere-property-id
                           :operation "ya-sijoituslupa-ilmajohtojen-sijoittaminen")]
    (fact "accordions are enabled for applicant"
      (query pena :enable-accordions :id id) => ok?)
    (fact "accordions are enabled for authorities in the applications organization"
      (query jussi :enable-accordions :id id) => ok?)
    (fact "accordions are not enabled for other authorities"
      (query veikko :enable-accordions :id id) =not=> ok?))

  (let [{id :id :as app} (create-and-open-application
                           pena
                           :propertyId tampere-property-id)]
    (fact "accordions are not enabled for authorities on non-YA applications"
      (query jussi :enable-accordions :id id) =not=> ok?
      (query veikko :enable-accordions :id id) =not=> ok?)
    (fact "accordions are enabled for applicants on non-YA applications"
      (query pena :enable-accordions :id id) => ok?)))

(facts* "cancel application authority"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Peruutustie 23")
        application-id (:id application)]

    (fact "Mikko sees the application" (query mikko :application :id application-id) => ok?)
    (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)
    (fact "Sonja can cancel Mikko's application"
      (command sonja :cancel-application :id application-id :text nil :lang "fi") => ok?)
    (fact "Sonja sees the canceled application"
      (let [application (query-application sonja application-id)]
        (-> application :history last :state) => "canceled"))

    (let [email (last-email)]
      (:to email) => (contains (email-for-key mikko))
      (:subject email) => "Lupapiste: Peruutustie 23, Sipoo - hankkeen tila on nyt Peruutettu"
      (get-in email [:body :plain]) => (contains "Peruutettu")
      email => (partial contains-application-link? application-id "applicant")))

  (fact "Authority can cancel own application"
    (let [application-id  (create-app-id sonja :propertyId sipoo-property-id)]
      (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)
      (fact "Sonja can cancel the application"
        (let [r (command sonja :cancel-application :id application-id :text nil :lang "fi")]
          r => ok?
          (fact "No comments exists from cancel" (-> r :application :comments count) => 0)))))

  (fact "Authority can cancel with reason text, which is added as comment"
    (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Peruutustie 23")
          cancel-reason "Testihakemus"]
      (command sonja :cancel-application :id (:id application) :text cancel-reason :lang "fi") => ok?

      (fact "Mikko sees cancel reason text in comments"
        (let [application (:application (query mikko :application :id (:id application)))]
          (count (:comments application)) => 1
          (-> application :comments (first) :text) => (contains cancel-reason))))))

(facts "Pena cancels his application"
  (last-email) ; Inbox zero

  (let [application (create-and-submit-application pena :propertyId sipoo-property-id :address "Penahouse 88")
        intial-submitted (:submitted application)
        application-id (:id application)
        generated-messages (integration-messages application-id)
        reason-text "Cancellation notice."]
    (fact "integration messages"
      (count generated-messages) => 1
      (first generated-messages) => (contains {:partner "matti"
                                               :data    (contains {:fromState (contains {:name "draft"})
                                                                   :toState   (contains {:name "submitted"})})}))

    (fact "Pena sees the application" (query pena :application :id application-id) => ok?)
    (fact "Sonja sees the application" (query sonja :application :id application-id) => ok?)

    ; Invite & approve Teppo
    (command pena :invite-with-role :id application-id :email (email-for-key teppo) :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
    (command teppo :approve-invite :id application-id) => ok?

    (last-email)                                            ; reset

    (fact "Pena can cancel his application with reason text"
      (command pena :cancel-application :id application-id :text reason-text :lang "fi") => ok?)
    (fact "Sonja sees the canceled application"
      (let [application (query-application sonja application-id)]
        (:state application) => "canceled"
        (-> application :history last :state) => "canceled"))
    (doseq [apikey [pena sonja teppo]]
      (fact {:midje/description (str (email-for-key apikey) " sees the cancel reason text in comments")}
        (let [application (query-application apikey application-id)]
          (count (:comments application)) => 1
          (-> application :comments (first) :text) => (contains reason-text))))

    (let [emails (sent-emails)
          email1 (first emails)]
      (count emails) => 2
      (fact "Pena and Teppo get email" (map :to emails) => (just [#"teppo@example.com" #"pena@example.com"]))

      (:to email1) => (contains (email-for-key teppo))
      (:subject email1) => "Lupapiste: Penahouse 88, Sipoo - hankkeen tila on nyt Peruutettu"
      (get-in email1 [:body :plain]) => (contains "Peruutettu")
      email1 => (partial contains-application-link? application-id "applicant"))

    (fact "Luukas (reader) can't undo-cancellation"
      (command luukas :undo-cancellation :id application-id) => unauthorized?)
    (fact "Teppo can't undo cancellation, as he is not the canceler"
      (command teppo :undo-cancellation :id application-id) => unauthorized?)
    (fact "Pena can undo his cancellation"
      (command pena :undo-cancellation :id application-id) => ok?)

    (let [email (last-email)]
      (:to email) => (contains (email-for-key pena))
      (:subject email) => "Lupapiste: Penahouse 88, Sipoo - hanke on palautunut tilaan Hakemus j\u00e4tetty"
      (get-in email [:body :plain]) => (contains "hakemus on palautettu aktiiviseksi")
      email => (partial contains-application-link? application-id "applicant"))

    (fact "Teppo can now cancel"
      (command teppo :cancel-application :id application-id :text "I want it canceled!" :lang "fi") => ok?
      (:state (query-application teppo application-id)) => "canceled")

    (fact "Pena can't undo Teppos cancelation, although he is the writer"
      (command pena :undo-cancellation :id application-id) => unauthorized?)

    (fact "Sonja can undo cancellation"
      (command sonja :undo-cancellation :id application-id) => ok?
      (let [{:keys [state history canceled submitted]} (query-application pena application-id)]
        state => "submitted"
        canceled => nil
        (fact "new submitted history entry is added"
          (-> history last :state) => "submitted")
        (fact "timestamp is not updated"
          (= submitted intial-submitted)) => true
        (fact "old canceled history entry is preserved"
          (-> history butlast last :state) => "canceled")))))

(facts "Add operations"
  (let [operation "kerrostalo-rivitalo"
        application-id  (create-app-id mikko :propertyId tampere-property-id :operation operation)]
    (comment-application mikko application-id true) => ok?

    (fact "Applicant is able to add operation"
      (success (command mikko :add-operation :id application-id :operation "puun-kaataminen")) => true
      (let [{docs :documents} (query-application mikko application-id)]
        (fact "Only one non-repeating location exists"
          (count (filter #(= "location" (get-in % [:schema-info :type])) docs)) => 1)))


    (fact "Authority is able to add operation"
      (success (command veikko :add-operation :id application-id :operation "kerrostalo-rivitalo")) => true)

    (fact "All added attachments are valid"
      (let [{attachments :attachments} (query-application mikko application-id)]
        ((ssc/json-coercer [att/Attachment]) attachments)))))

(facts "Users need approver role to approve applications"
  (let [application    (create-and-submit-application mikko :municipality sonja-muni)
        application-id (:id application)]
    (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?

    (fact "Applicant cannot approve application"
      (command mikko :approve-application :id application-id :lang "fi") => unauthorized?)

    (fact "Authority without approver role cannot approve application"
      (command ronja :approve-application :id application-id :lang "fi") => unauthorized?)

    (fact "Approver with approver role is authorized"
          (command sonja :approve-application :id application-id :lang "fi") => ok?)
    (facts "Application state is sent with matching history"
           (let [{:keys [state history]} (query-application sonja application-id)]
             (fact "State is closed" state => "sent")
             (fact "History is correct"
                   (map :state history) => ["draft" "open" "submitted" "sent"])))))

(facts "link to backend system"
  (let [application    (create-and-submit-application mikko :municipality sonja-muni)
        application-id (:id application)
        redirect-url   "http://www.taustajarjestelma.fi/servlet/kohde?kohde=rakennuslupatunnus&lupatunnus="]

    (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
    (command sonja :approve-application :id application-id :lang "fi")

    (-> (query-application sonja application-id) :history last :state) => "sent"

    (facts "no vendor backend id (kuntalupatunnus)"
      (fact* "redirects to LP backend url if configured"
        (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val redirect-url) => ok?
        (let [resp (raw sonja :redirect-to-vendor-backend :id application-id) => http303?]
          (get-in resp [:headers "location"]) => (str redirect-url application-id)))

      (fact "error if no LP backend url configured"
        (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val "") => ok?
        (raw sonja :redirect-to-vendor-backend :id application-id) => http404?

        (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val redirect-url) => ok?
        (raw sonja :redirect-to-vendor-backend :id application-id) => http404?))

    (facts "vendor backend id available (kuntalupatunnus)"
      (command sonja :check-for-verdict :id application-id)
      (let [{verdicts :verdicts} (query-application sonja application-id)
            vendor-backend-id    (app/get-vendor-backend-id verdicts)]
        (fact* "redirect to backend id url if configured"
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val redirect-url) => ok?
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val "http://dontgohere.com") => ok?
          (let [resp (raw sonja :redirect-to-vendor-backend :id application-id) => http303?]
            (get-in resp [:headers "location"]) => (str redirect-url vendor-backend-id)))

        (fact* "redirect to LP backend url if available and backend id url not configured"
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val "") => ok?
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val redirect-url) => ok?
          (let [resp (raw sonja :redirect-to-vendor-backend :id application-id) => http303?]
            (get-in resp [:headers "location"]) => (str redirect-url application-id)))

        (fact "error if no LP backend url or backend id url configured"
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForBackendId :val "") => ok?
          (command sipoo :save-vendor-backend-redirect-config :key :vendorBackendUrlForLpId :val "") => ok?
          (raw sonja :redirect-to-vendor-backend :id application-id) => http404?)))))

(fact "Pena cannot create app for organization that has new applications disabled"
  (let [resp  (create-app pena :propertyId "99700000000000")]
    resp =not=> ok?
    (:text resp) => "error.new-applications-disabled"))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(defn- set-and-check-person [api-key application-id initial-document path]
  (fact "new person is set"
    (command api-key :set-user-to-document :id application-id :documentId (:id initial-document) :userId mikko-id :path (if (seq path) (join "." path) "")) => ok?
    (let [updated-app (query-application mikko application-id)
          update-doc (domain/get-document-by-id updated-app (:id initial-document))
          schema-name  (get-in update-doc [:schema-info :name])
          person-path  (into [] (concat [:data] (map keyword path) [:henkilotiedot]))
          company-path (into [] (concat [:data] (map keyword path) [:yritys]))
          experience-path (into [] (concat [:data] (map keyword path) [:patevyys]))
          suunnittelija? (in? ["paasuunnittelija" "suunnittelija"] schema-name )]
      (get-in update-doc (concat (drop-last person-path) [:userId :value])) => mikko-id
      (get-in update-doc (into person-path [:etunimi :value])) => "Mikko"
      (get-in update-doc (into person-path [:sukunimi :value])) => "Intonen"
      (get-in update-doc (into person-path [:hetu :value])) => "******-****"
      (get-in update-doc (into company-path [:yritysnimi :value])) => (if suunnittelija? "Yritys Oy" nil)
      (get-in update-doc (into company-path [:liikeJaYhteisoTunnus :value])) => (if suunnittelija? "1234567-1" nil)
      (get-in update-doc (into experience-path [:koulutusvalinta :value])) => (if suunnittelija? "kirvesmies" nil)
      (get-in update-doc (into experience-path [:valmistumisvuosi :value])) => (if suunnittelija? "2000" nil)
      (get-in update-doc (into experience-path [:fise :value])) => (if suunnittelija? "f" nil)
      (get-in update-doc (into experience-path [:fiseKelpoisuus :value])) => (if suunnittelija? "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)" nil))))

(defn- check-empty-person
  ([document doc-path args]
   (let [empty-person {:etunimi  {:value ""}
                       :sukunimi {:value ""}
                       :hetu     {:value nil}}
         empty-person (merge empty-person args)]
     document => truthy
     (get-in document doc-path) => empty-person))
  ([document doc-path] (check-empty-person document doc-path {}))
  )

(facts "Set user to document"
  (let [application      (create-and-submit-application mikko :propertyId sipoo-property-id)
        application-id   (:id application)
        paasuunnittelija (domain/get-document-by-name application "paasuunnittelija")
        suunnittelija    (domain/get-document-by-name application "suunnittelija")
        hakija           (domain/get-applicant-document (:documents application))
        maksaja          (domain/get-document-by-name application "maksaja")]

    (fact "initially person data is empty"
      (check-empty-person paasuunnittelija [:data :henkilotiedot])
      (check-empty-person hakija [:data :henkilo :henkilotiedot] {:turvakieltoKytkin {:value false}})
      (check-empty-person maksaja [:data :henkilo :henkilotiedot] {:turvakieltoKytkin {:value false}}))

    (set-and-check-person mikko application-id paasuunnittelija [])
    (set-and-check-person mikko application-id hakija ["henkilo"])
    (set-and-check-person mikko application-id maksaja ["henkilo"])

    (fact "Hakija can be set empty, but field values remain"
      (command mikko :set-user-to-document :id application-id
               :documentId (:id hakija) :userId "" :path "henkilo")
      => ok?
      (let [updated-app (query-application mikko application-id)
            update-doc  (domain/get-document-by-id updated-app (:id hakija))]
        (get-in update-doc [:data :henkilo :userId :value]) => ""
        (get-in update-doc [:data :henkilo :henkilotiedot :etunimi :value]) => "Mikko"
        (get-in update-doc [:data :henkilo :henkilotiedot :sukunimi :value]) => "Intonen"))

    (fact "there is no suunnittelija"
      suunnittelija => truthy
      (->> (get-in suunnittelija [:data :henkilotiedot])
           (map (fn [[k v]] [k (:value v)]))
           (into {}))
      => {:etunimi "", :hetu nil, :sukunimi ""})

    (let [doc-id (:id suunnittelija)
          code   "RAK-rakennesuunnittelija"]

      (fact "suunnittelija kuntaroolikoodi is set"
        (command mikko :update-doc :id application-id :doc doc-id :updates [["kuntaRoolikoodi" code]]) => ok?
        (let [updated-app           (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          updated-suunnittelija => truthy
          (get-in updated-suunnittelija [:data :kuntaRoolikoodi :value]) => code))

      (fact "suunnittelija patevyys is set"
        (command mikko :update-doc :id application-id :doc doc-id :updates
                                   [["patevyys.kokemus" "10"]
                                    ["patevyys.patevyysluokka" "AA"]
                                    ["patevyys.patevyys" "Lis\u00e4tietoa patevyydest\u00e4"]
                                    ["patevyys.fise" "fise-linkki"]
                                    ["patevyys.fiseKelpoisuus" "vaativa akustiikkasuunnittelu (uudisrakentaminen)"]]) => ok?
        (let [updated-app           (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          updated-suunnittelija => truthy
          (get-in updated-suunnittelija [:data :patevyys :patevyys :value]) => "Lis\u00e4tietoa patevyydest\u00e4"
          (get-in updated-suunnittelija [:data :patevyys :patevyysluokka :value]) => "AA"
          (get-in updated-suunnittelija [:data :patevyys :kokemus :value]) => "10"
          (get-in updated-suunnittelija [:data :patevyys :fise :value]) => "fise-linkki"
          (get-in updated-suunnittelija [:data :patevyys :fiseKelpoisuus :value]) => "vaativa akustiikkasuunnittelu (uudisrakentaminen)"))

      (fact "new suunnittelija is set"
        (command mikko :set-user-to-document :id application-id :documentId (:id suunnittelija) :userId mikko-id :path "") => ok?
        (let [updated-app           (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          (get-in updated-suunnittelija [:data :userId :value]) => mikko-id
          (get-in updated-suunnittelija [:data :henkilotiedot :etunimi :value]) => "Mikko"
          (get-in updated-suunnittelija [:data :henkilotiedot :sukunimi :value]) => "Intonen"
          (get-in updated-suunnittelija [:data :yritys :yritysnimi :value]) => "Yritys Oy"
          (get-in updated-suunnittelija [:data :yritys :liikeJaYhteisoTunnus :value]) => "1234567-1"
          (get-in updated-suunnittelija [:data :patevyys :koulutusvalinta :value]) => "kirvesmies"
          (get-in updated-suunnittelija [:data :patevyys :koulutus :value]) => ""
          (get-in updated-suunnittelija [:data :patevyys :valmistumisvuosi :value]) => "2000"
          (get-in updated-suunnittelija [:data :patevyys :fise :value]) => "f"
          (get-in updated-suunnittelija [:data :patevyys :fiseKelpoisuus :value]) => "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"

          (fact "applicant sees fully masked person id"
            (get-in updated-suunnittelija [:data :henkilotiedot :hetu :value]) => "******-****")

          (fact "suunnittelija kuntaroolikoodi is preserved (LUPA-774)"
            (get-in updated-suunnittelija [:data :kuntaRoolikoodi :value]) => code)))

      (fact "Suunnittelija can be set empty, but other fields are not cleared"
        (command mikko :set-user-to-document :id application-id :documentId (:id suunnittelija) :userId "" :path "") => ok?
        (let [updated-app           (query-application mikko application-id)
              updated-suunnittelija (domain/get-document-by-id updated-app doc-id)]
          (get-in updated-suunnittelija [:data :userId :value]) => ""
          (get-in updated-suunnittelija [:data :henkilotiedot :etunimi :value]) => "Mikko"
          (get-in updated-suunnittelija [:data :henkilotiedot :sukunimi :value]) => "Intonen"
          (get-in updated-suunnittelija [:data :yritys :yritysnimi :value]) => "Yritys Oy"
          (get-in updated-suunnittelija [:data :yritys :liikeJaYhteisoTunnus :value]) => "1234567-1"
          (get-in updated-suunnittelija [:data :patevyys :koulutusvalinta :value]) => "kirvesmies"
          (get-in updated-suunnittelija [:data :patevyys :koulutus :value]) => ""
          (get-in updated-suunnittelija [:data :patevyys :valmistumisvuosi :value]) => "2000"
          (get-in updated-suunnittelija [:data :patevyys :fise :value]) => "f"
          (get-in updated-suunnittelija [:data :patevyys :fiseKelpoisuus :value]) => "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"))

      (fact "application is unassigned, Sonja does not see the full person IDs"
        (let [app           (query-application sonja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (:handlers app) => empty?
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-****"))

      (fact "application is unassigned, Ronja does not see the full person IDs"
        (let [app           (query-application ronja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-****"))

      (fact "Sonja assigns the application to herself and sees the full person ID"
        (command sonja :upsert-application-handler :id application-id :userId sonja-id :roleId sipoo-general-handler-id) => ok?
        (let [app           (query-application sonja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-9988"))

      (fact "Ronja still does not see the full person ID"
        (let [app           (query-application ronja application-id)
              suunnittelija (domain/get-document-by-id app doc-id)]
          (get-in suunnittelija [:data :henkilotiedot :hetu :value]) => "210281-****")))))

(defn get-doc-value [doc path-prefix path]
  (tools/unwrapped (get-in doc (into path-prefix path))))

(defn company-to-document [apikey app-id company-id user-data doc-id]
  (fact "doc-id"
    doc-id => truthy)
  (fact "set-company-to-document"
    (command apikey :set-company-to-document
             :id app-id
             :companyId company-id
             :path "yritys"
             :documentId doc-id)) => ok?
  (let [app (query-application apikey app-id)
        doc (domain/get-document-by-id app doc-id)
        company (company-from-minimal-by-id company-id)
        check (partial get-doc-value doc [:data :yritys])]
    (fact "Company to document"
      (fact "yritysnimi" (check [:yritysnimi]) => (:name company))
      (fact "liikeJaYhteisoTunnus" (check [:liikeJaYhteisoTunnus]) => (:y company))

      (when (-> doc :schema-info :name (= "maksaja"))
        (fact "ovtTunnus" (check [:verkkolaskutustieto :ovtTunnus]) => (:ovt company))
        (fact "valittajaTunnus" (check [:verkkolaskutustieto :valittajaTunnus]) => (:pop company)))

      (fact "etunimi" (check [:yhteyshenkilo :henkilotiedot :etunimi]) => (:firstName user-data))
      (fact "sukunimi" (check [:yhteyshenkilo :henkilotiedot :sukunimi]) => (:lastName user-data))
      (fact "email" (check [:yhteyshenkilo :yhteystiedot :email]) => (:email user-data))
      (fact "puhelin" (check [:yhteyshenkilo :yhteystiedot :puhelin]) => (:phone user-data)))
    doc-id))

(facts "Set company to document"
  (let [{app-id :id :as app} (create-and-open-application pena :propertyId sipoo-property-id)
        hakija              (domain/get-document-by-name app "hakija-r")]

    (fact "initially hakija company is empty"
      (let [check (partial get-doc-value hakija [:data :yritys])]
        (doseq [path [[:yritysnimi]
                      [:liikeJaYhteisoTunnus]
                      [:verkkolaskutustieto :ovtTunnus]
                      [:verkkolaskutustieto :valittajaTunnus]
                      [:yhteyshenkilo :henkilotiedot :etunimi]]]
          (check path) => ss/blank?)))

    (fact "Add company Esimerkki to application"
      (invite-company-and-accept-invitation pena app-id "esimerkki" erkki) => ok?)
    (fact "Add company Solita to application"
      (invite-company-and-accept-invitation pena app-id "solita" kaino) => ok?)

    (fact "No company auth, no company user"
      (->> (command pena :create-doc
                           :id app-id
                           :collection "documents"
                           :schemaName "hakija-r")
           :doc
           (company-to-document pena app-id "esimerkki"
                                {:firstName "" :lastName ""
                                 :email "" :phone ""})))
    (fact "No company auth, company user"
      (->> (command erkki :create-doc
                           :id app-id
                           :collection "documents"
                           :schemaName "hakija-r")
           :doc
           (company-to-document erkki app-id "esimerkki"
                                {:firstName "Erkki" :lastName "Esimerkki"
                                 :email "erkki@example.com" :phone "556677"})))
    (fact "Pena joins Esimerkki"
          (command erkki :company-invite-user :firstName "Pena" :lastName "Panaani"
                   :email "pena@example.com" :admin false :submit true) => ok?
          (->> (last-email) (token-from-email "pena@example.com") http-token-call) => true)
    (fact "Company auth, no company user"
      (->> (command sonja :create-doc
                           :id app-id
                           :collection "documents"
                           :schemaName "hakija-r")
           :doc
           (company-to-document sonja app-id "esimerkki"
                                {:firstName "Pena" :lastName "Panaani"
                                 :email "pena@example.com" :phone "0102030405"} )))
    (fact "Company auth, different company user"
      (->> (command erkki :create-doc
                           :id app-id
                           :collection "documents"
                           :schemaName "hakija-r")
           :doc
           (company-to-document erkki app-id "solita"
                                {:firstName "" :lastName ""
                                 :email "" :phone ""}))))
  (fact "Company auth, company user"
    (let [{app-id :id :as app} (create-and-open-application pena :propertyId sipoo-property-id)
          doc-id (->> (command erkki :create-doc
                           :id app-id
                           :collection "documents"
                           :schemaName "hakija-r")
                      :doc
                      (company-to-document erkki app-id "esimerkki"
                                           {:firstName "Erkki" :lastName "Esimerkki"
                                            :email "erkki@example.com" :phone "556677"} ))]
      (fact "Contact person info cleared, if not available"
        (company-to-document erkki app-id "solita"
                             {:firstName "" :lastName ""
                              :email "" :phone ""}
                             doc-id))))

  (fact "Company auth, company user, maksaja doc"
    (let [{app-id :id :as app} (create-and-open-application pena :propertyId sipoo-property-id)
          maksaja              (domain/get-document-by-name app "maksaja")
          doc-id               (company-to-document erkki app-id "esimerkki"
                                                    {:firstName "Erkki" :lastName "Esimerkki"
                                                     :email "erkki@example.com" :phone "556677"} (:id maksaja))]
      (fact "Contact person info cleared, if not available"
        (company-to-document erkki app-id "solita"
                             {:firstName "" :lastName ""
                              :email "" :phone ""}
                             doc-id)))))

(facts "Facts about update operation description"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application (query-application pena application-id)
        op (:primaryOperation application)
        test-desc "Testdesc"]
    (fact "operation desc is empty" (-> op :description empty?) => truthy)
    (command pena :update-op-description :id application-id :op-id (:id op) :desc test-desc :collection "operations")
    (let [updated-app (query-application pena application-id)
          updated-op (:primaryOperation updated-app)]
      (fact "description is set" (:description updated-op) => test-desc))))

(facts "Changing application location"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        application    (query-application pena application-id)]

    (fact "applicant should be able to change location when state is draft"
      (:state application) => "draft"
      (command pena :change-location :id application-id
                                     :x (-> application :location :x) - 1
                                     :y (-> application :location :y) + 1
                                     :address (:address application) :propertyId (:propertyId application) :refreshBuildings false) => ok?)

    ; applicant submits and authority gives verdict
    (command pena :submit-application :id application-id)
    (command sonja :check-for-verdict :id application-id)

    (fact "applicant should not be authorized to change location anymore"
      (command pena :change-location :id application-id
                                     :x (-> application :location :x) - 1
                                     :y (-> application :location :y) + 1
                                     :address (:address application) :propertyId (:propertyId application) :refreshBuildings false) => fail?)

    (fact "authority should still be authorized to change location"
      (command sonja :change-location :id application-id
                                      :x (-> application :location :x) - 1
                                      :y (-> application :location :y) + 1
                                      :address (:address application) :propertyId (:propertyId application) :refreshBuildings false) => ok?)))

(fact "Authority can access drafts, but can't use most important commands"
  (let [id (create-app-id pena)
        app (query-application sonja id)
        user (find-user-from-minimal-by-apikey sonja)
        denied-actions #{:delete-attachment :delete-attachment-version :upload-attachment :change-location
                         :new-verdict-draft :create-attachments :remove-document-data :remove-doc :update-doc
                         :reject-doc :approve-doc :stamp-attachments :create-task :cancel-application
                         :add-link-permit :set-tos-function-for-application :set-tos-function-for-operation
                         :unsubscribe-notifications :subscribe-notifications :upsert-application-handler :remove-application-handler
                         :neighbor-add :change-permit-sub-type :refresh-ktj :merge-details-from-krysp :remove-link-permit-by-app-id
                         :set-attachment-type :move-attachments-to-backing-system :add-operation :remove-auth :create-doc
                         :set-company-to-document :set-user-to-document :set-current-user-to-document
                         :approve-application :submit-application :create-foreman-application
                         :change-application-state :change-application-state-targets}]
    (fact "meta"
      app => map?
      user => map?)

    (doseq [command (action/foreach-action {:web {} :user user :application {} :data app})
          :let [action (keyword (:action command))
                result (a/validate-authority-in-drafts command)]]
    (fact {:midje/description (name action)}
     (when (denied-actions action)
       result => (some-fn nil? unauthorized?))))))

(fact "Primary operation can be changed"
  (let [id (create-app-id pena)]
    (command pena :add-operation :id id :operation "varasto-tms") => ok?
    (let [app (query-application pena id)
          secondary-op (first (:secondaryOperations app))
          primary-op (:primaryOperation app)]
      (:name secondary-op) => "varasto-tms"
      (:name primary-op) => "kerrostalo-rivitalo"
      (fact "Primary operation cannot be changed to unknown operation"
        (command pena :change-primary-operation :id id :secondaryOperationId "foobar") => {:ok false, :text "error.unknown-operation"})
      (command pena :change-primary-operation :id id :secondaryOperationId (:id secondary-op)) => ok?)
    (let [app (query-application pena id)
          secondary-op (first (:secondaryOperations app))
          primary-op (:primaryOperation app)]
      (:name secondary-op) => "kerrostalo-rivitalo"
      (:name primary-op) => "varasto-tms")))

(facts "Changing application state after verdict"
  (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
    ; applicant submits and authority gives verdict
    (command pena :submit-application :id application-id)
    (command sonja :check-for-verdict :id application-id)

    (fact "Sonja changes application state to constructionStarted"
          (command sonja :change-application-state :id application-id :state "constructionStarted") => ok?
          (:state (query-application sonja application-id)) => "constructionStarted")
    (fact "verdictGiven is not included in the target states"
          (let [res (query sonja :change-application-state-targets :id application-id)
                states (set (:states res))]
            (states "verdictGiven") => nil
            (command sonja :change-application-state :id application-id :state "verdicGiven") => fail?))
    (fact "Change state to appeal. verdictGiven is possible next state"
          (command sonja :change-application-state :id application-id :state "appealed") => ok?
          (-> (query sonja :change-application-state-targets :id application-id) :states set (contains? "verdictGiven")) => true
          (command sonja :change-application-state :id application-id :state "verdictGiven") => ok?)))

(defn- return-to-draft [authority application-id]
  (command authority :return-to-draft :id application-id :text "comment-text" :lang :fi))

(facts "Returning application to draft state after submission"
  (fact "Only applications in 'submitted' state can be returned to draft"
    (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
      (return-to-draft sonja application-id) => (partial expected-failure? :error.command-illegal-state)
      (command pena :submit-application :id application-id)
      (return-to-draft sonja application-id) => ok?
      (command pena :submit-application :id application-id) => ok?
      (command sonja :check-for-verdict :id application-id)
      (return-to-draft sonja application-id) => (partial expected-failure? :error.command-illegal-state)))
  (fact "The applicants and witers are notified of the return to draft"
    (let [application-id (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
          application    (query-application pena application-id)
          hakija         (domain/get-applicant-document (:documents application))]
      (command pena :invite-with-role :id application-id :email (email-for-key mikko) :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
      (command mikko :approve-invite :id application-id) => ok?
      (set-and-check-person pena application-id hakija ["henkilo"])
      (command pena :submit-application :id application-id)

      (last-email) ; Inbox zero

      (return-to-draft sonja application-id)
      (let [emails (sent-emails)]
        (fact "only mikko gets email, since pena is company user and does not have personal authorization"
          (count emails) => 1
          emails => (partial every? (comp (partial re-find #"hankkeen Asuinkerrostalon tai rivitalon rakentaminen osoitteessa") :html :body))
          emails => (partial every? (comp (partial re-find #"comment-text") :html :body))
          emails => (partial some (comp (partial re-find (re-pattern (email-for-key mikko))) :to))))))
  (let [application-id (create-app-id teppo :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
    (command teppo :submit-application :id application-id)
    (fact "Set Ronja as the handler"
      (command sonja :upsert-application-handler
               :id application-id
               :roleId "abba1111111111111111acdc"
               :userId ronja-id) => ok?
      (-> (query-application sonja application-id) :handlers first :userId) => ronja-id)
    (return-to-draft sonja application-id)

    (let [email (last-email)]
      (:to email) => (contains "teppo")
      (:subject email) => (contains "Hakemus palautettiin Luonnos-tilaan")
      (get-in email [:body :plain]) => (contains "comment-text"))

    (let [application (query-application teppo application-id)]
      (fact "The return to draft can be seen in application history"
        (:state application) => "draft"
        (-> application :history last :state) => "draft")

      (fact "The authority's comments are also stored in application comments"
        (-> application :comments last :text) => "comment-text")
      (fact "Application's submission date is nil"
        (-> application :submitted) => nil?)
      (fact "Application still has handler"
        (:handlers application) => (just [(contains {:userId ronja-id})])))))

(fact "Remove handlers from reverted draft flag"
  (let [application-id (create-app-id teppo :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)]
    (command teppo :submit-application :id application-id)
    (fact "Set Ronja as the handler"
      (command sonja :upsert-application-handler
               :id application-id
               :roleId "abba1111111111111111acdc"
               :userId ronja-id) => ok?
      (-> (query-application sonja application-id) :handlers first :userId) => ronja-id)
    (fact "Set the remove handlers from reverted draft flag"
      (command sipoo :set-organization-remove-handlers-from-reverted-draft
               :enabled true) => ok?)
    (return-to-draft sonja application-id)
    (let [application (query-application teppo application-id)]
      (fact "Application has no handlers"
        (:handlers application) => empty?))))

(facts "Authority can create application in other organisation"
       (let [application-id (create-app-id luukas
                                            :operation "kerrostalo-rivitalo"
                                            :propertyId oulu-property-id)
             {:keys [state]} (query-application luukas application-id)]
         (fact "Application state is draft (not open)"
               state => "draft")
         (facts "As an application writer, authority can use applicant commands"
                (doseq [cmd [:submit-application :cancel-application]]
                  (fact {:midje/description cmd}
                        luukas => (allowed? cmd :id application-id))))
         (fact "Writer submits application"
               (command luukas :submit-application :id application-id) => ok?)
         (fact "Outside authority cannot use authority commands"
               (command luukas :approve-application :id application-id :lang "fi") => unauthorized?)
         (fact "(Outside) authority cannot access the application"
               (query sonja :application :id application-id) => not-accessible?)
         (fact "Outside authority's comments are applicant comments"
               (command luukas :add-comment :id application-id :text "Zao!" :target {:type "application"}
                        :roles #{:applicant :authority}) => ok?
               (query-application luukas application-id) => (util/fn-> :comments first ((contains {:type "applicant"
                                                                                                   :text "Zao!"}))))
         (fact "Local authority's comments are authority comments"
               (command olli :add-comment :id application-id :text "Zaoshang hao!" :target {:type "application"}
                        :roles #{:applicant :authority}) => ok?
               (query-application olli application-id) => (util/fn-> :comments last ((contains {:type "authority"
                                                                                                :text "Zaoshang hao!"}))))

         (facts "Applications search result contains correct applications"
                (let [sipoo-draft     (create-app-id pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
                      luukas-canceled (create-app-id luukas
                                                     :operation "kerrostalo-rivitalo"
                                                     :propertyId oulu-property-id)
                      _               (command luukas :cancel-application :id luukas-canceled :lang "fi" :text "")
                      luukas-draft    (create-app-id luukas
                                                     :operation "kerrostalo-rivitalo"
                                                     :propertyId oulu-property-id)
                      applications    (->> (query luukas :applications-search :applicationType "all" :limit 100)
                                           :data :applications  (map :id) set)]
                  (fact "Created application is listed"
                        (contains? applications application-id) => true)
                  (fact "Sipoo draft is not listed"
                        (contains? applications sipoo-draft) => false)
                  (fact "Canceled application is not listed"
                        (contains? applications luukas-canceled) => false)
                  (fact "Created draft is listed"
                        (contains? applications luukas-draft) => true)))))

(facts "all operations in operation tree"
  (let [app-id (create-app-id pena :operation :pientalo :propertyId sipoo-property-id)]

  (fact "get everything"
    (:operations (query pena :all-operations-in :id app-id :path ""))
    => (just (->> (flatten op/operation-tree)
                  (filter keyword?)
                  (map name)
                  set)
             :in-any-order :gaps-ok))

  (fact "YA operations - sijoitusluvat - muu sijoituslupa"
    (:operations (query pena :all-operations-in :id app-id :path "yleisten-alueiden-luvat.sijoituslupa.muu-sijoituslupa"))
    => ["ya-sijoituslupa-muu-sijoituslupa"])

  (fact "R operation - rakennelman rakentaminen"
    (:operations (query pena :all-operations-in :id app-id :path "Rakentaminen ja purkaminen.Rakennelman rakentaminen"))
    => (just #{"auto-katos" "masto-tms" "mainoslaite" "aita" "maalampo" "jatevesi"} :in-any-order :gaps-ok))))

(facts "Application organization archive enabled"
       (let [app-id (create-app-id pena :operation :pientalo :propertyId sipoo-property-id)]
         (fact "No archive"
               (query pena :application-organization-archive-enabled :id app-id )
               => {:ok false :text "error.archive-not-enabled"})
         (fact "Enable archive in Sipoo"
               (command admin :set-organization-boolean-attribute :attribute "permanent-archive-enabled" :enabled true :organizationId "753-R"))
         (fact "Archive enabled"
               (query pena :application-organization-archive-enabled :id app-id )
               => ok?)))

(facts "Multiple operations support can be selected"
  (let [app-id-1 (create-app-id pena :operation :pientalo :propertyId sipoo-property-id)
        app-id-2 (create-app-id velho :operation :pientalo :propertyId kuopio-property-id)]
    (fact "Multiple operations is allowed in Sipoo"
      pena => (allowed? :add-operation :id app-id-1 :operation "vapaa-ajan-asuinrakennus"))
    (fact "Multiple operations is not allowed in Kuopio"
      velho =not=> (allowed? :add-operation :id app-id-2 :operation "vapaa-ajan-asuinrakennus"))))
