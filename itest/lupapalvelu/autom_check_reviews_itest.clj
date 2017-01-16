(ns lupapalvelu.autom-check-reviews-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.java.io :as io]
            [sade.core :refer [now fail]]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [sade.coordinate :as coordinate]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml]]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.tasks :refer [task-is-review?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.integrations-api]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.verdict-api]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.pdftk :as pdftk]
            [lupapalvelu.xml.krysp.application-from-krysp :as app-from-krysp]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.user :as usr])
  (:import [org.xml.sax SAXParseException]))

(def db-name (str "test_autom-check-reviews-itest_" (now)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(defn  query-tasks [user application-id]
  (:tasks (query-application local-query user application-id)))

(defn count-reviews [user app-id] (count (filter task-is-review? (query-tasks user app-id))))

(facts "Automatic checking for reviews"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil
                         ]
      (let [application-submitted          (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 17")
            application-id-submitted       (:id application-submitted)
            application-verdict-given-1    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
            application-id-verdict-given-1 (:id application-verdict-given-1)
            application-verdict-given-2    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 19")
            application-id-verdict-given-2 (:id application-verdict-given-2)
            ]

        (facts "Initial state of reviews before krysp reading is sane"
          (local-command sonja :approve-application :id application-id-verdict-given-1 :lang "fi") => ok?
          (local-command sonja :approve-application :id application-id-verdict-given-2 :lang "fi") => ok?
          (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) => 0
          (count (batchrun/fetch-verdicts)) => pos?
          (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) =not=> 0

          (give-local-verdict sonja application-id-verdict-given-1 :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          (give-local-verdict sonja application-id-verdict-given-2 :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          ;; (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
                application-verdict-given-1 (query-application local-query sonja application-id-verdict-given-1) => truthy
                application-verdict-given-2 (query-application local-query sonja application-id-verdict-given-2) => truthy]

            (fact "application state - submitted"
              (:state application-submitted) => "submitted")
            (fact "application state - verdictGiven 1"
              (:state application-verdict-given-1) => "verdictGiven")
            (fact "application state - verdictGiven 2"
              (:state application-verdict-given-2) => "verdictGiven")) => truthy

          (fact "tasks for verdictGiven 1"
            (count (:tasks application-verdict-given-1)) => 0)
          (fact "tasks for verdictGiven 2"
            (count (:tasks application-verdict-given-2)) => 0))

        (fact "failure in fetching multiple applications causes fallback into fetching consecutively"

          (let [review-count-before (count-reviews sonja application-id-verdict-given-1) => 3
                poll-result   (batchrun/poll-verdicts-for-reviews)
                last-review-1 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
                last-review-2 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-2)))
                app-1         (query-application local-query sonja application-id-verdict-given-1)
                app-2         (query-application local-query sonja application-id-verdict-given-2)]

            (fact "reviews for submitted application"
              (count-reviews sonja application-id-submitted) => 0)
            (fact "last review state for application 1"
              (:state last-review-1) => "requires_user_action")
            (fact "last review state for application 2"
              (:state last-review-2) => "sent")
            (fact "reviews for verdict given application 1"
              (count-reviews sonja application-id-verdict-given-1) => 3)
            (fact "reviews for verdict given application 2"
              (count-reviews sonja application-id-verdict-given-2) => 4)
            (fact "application 1 state not updated"
              (:state app-1) => "verdictGiven")
            (fact "application 2 state is updated"
              (:state app-2) => "constructionStarted")) => truthy

          ;; Trying to fetch with multiple ids throws xml parser exception -> causes fallback into consecutive fetching
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1 application-id-verdict-given-2] :application-id anything) =throws=> (SAXParseException. "msg" "id" "sid" 0 0))
          ;; Fallback - no xml found for application 1 by application id or backend-id
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1] :application-id anything) => nil)
          (provided (krysp-reader/rakval-application-xml anything anything ["2013-01"] :kuntalupatunnus anything) => nil)
          ;; Xml found for application 2
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-2] :application-id anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id-verdict-given-2)
                           (sxml/parse-string "utf-8"))))

        (fact "checking for reviews in correct states"

          (let [review-count-before (count-reviews sonja application-id-verdict-given-1) => 3
                poll-result (batchrun/poll-verdicts-for-reviews)
                last-review (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
                app         (query-application local-query sonja application-id-verdict-given-1)]

            (fact "reviews for submitted application"
              (count-reviews sonja application-id-submitted) => 0)
            (fact "last review state"
              (:state last-review) => "sent")
            (fact "reviews for verdict given application"
              (count-reviews sonja application-id-verdict-given-1) => 4)
            (fact "application state is updated"
              (:state app) => "constructionStarted")) => truthy

          ;; Application xml found for application 1 when fetching multiple applications
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1 application-id-verdict-given-2] :application-id anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id-verdict-given-1)
                           (sxml/parse-string "utf-8")))
          ;; Fetching xml for application 2 by kuntalupatunnus returns nil
          (provided (krysp-reader/rakval-application-xml anything anything ["2013-01"] :kuntalupatunnus anything) => nil))

        (fact "existing tasks are preserved"
          ;; should be seeing 1 added "aloituskokous" here compared to default verdict.xml
          (count-reviews sonja application-id-verdict-given-1) => 4
          (let [tasks (map tools/unwrapped  (query-tasks sonja application-id-verdict-given-1))
                reviews (filter task-is-review? tasks)
                review-types (map #(-> % :data :katselmuksenLaji) reviews)
                final-review? (fn [review]
                                (= (get-in review [:data :katselmus :tila]) "lopullinen"))]
            (fact "no validation errors"
              (not-any? :validationErrors reviews))
            (count (filter  (partial = "aloituskokous") review-types)) => 2
            (get-in (first (filter final-review? reviews)) [:data :rakennus :0 :tila :tila]) => "lopullinen"))))))

(facts "Automatic checking for reviews - application state and operation"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [application-id-submitted     (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "submitted 16"))
            application-id-canceled      (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "canceled 17"))
            application-id-verdict-given (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "verdict-given 18"))
            application-id-construction  (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "construction-started 19"))
            application-id-tj            (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "foreman 20" :operation "tyonjohtajan-nimeaminen-v2"))
            application-id-suunnittelija (:id (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "suunnittelija 21" :operation "suunnittelijan-nimeaminen"))]


        (facts "Initial state of applications before krysp reading is sane"
          (fact "cancel application"
            (local-command pena :cancel-application :id application-id-canceled :lang "fi" :text "cancelation") => ok?)

          (fact "approve verdictGiven app"   (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?)
          (fact "approve construction app"   (local-command sonja :approve-application :id application-id-construction  :lang "fi") => ok?)

          (fact "add link permit for tj app" (local-command pena :add-link-permit :id application-id-tj :linkPermitId application-id-verdict-given) => ok?)
          (fact "tj app as tj-ilmoitus"      (local-command pena  :change-permit-sub-type :id application-id-tj :permitSubtype "tyonjohtaja-hakemus") => ok?)
          (fact "submit tj app"              (local-command pena  :submit-application :id application-id-tj) => ok?)
          (fact "approve tj app"             (local-command sonja :approve-application :id application-id-tj :lang "fi") => ok?)

          (fact "add link permit for suunnittelija app" (local-command sonja :add-link-permit :id application-id-suunnittelija :linkPermitId application-id-verdict-given) => ok?)
          (fact "submit suunnittelija app"   (local-command sonja :submit-application :id application-id-suunnittelija) => ok?)

          (fact "give verdict for verdictGiven app" (give-local-verdict sonja application-id-verdict-given :verdictId "verdict-vg" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)
          (fact "give verdict for construction app" (give-local-verdict sonja application-id-construction :verdictId "verdict-cs" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)
          (fact "give verdict for tj app"    (give-local-verdict sonja application-id-tj :verdictId "verdict-tj" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)

          (fact "approve suunnittelija app"  (local-command sonja :approve-application :id application-id-suunnittelija :lang "fi") => ok?)
          (fact "give verdict for suunnittelija app"   (give-local-verdict sonja application-id-suunnittelija :verdictId "verdict-suun" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)


          (fact "give verdict for suunnittelja app"
            (give-local-verdict sonja application-id-suunnittelija :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)

          (fact "change construction application state to constructionStarted"
            (local-command sonja :change-application-state :id application-id-construction :state "constructionStarted")  => ok?)

          (let [application-submitted     (query-application local-query sonja application-id-submitted)     => truthy
                application-canceled      (query-application local-query sonja application-id-canceled)      => truthy
                application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy
                application-construction  (query-application local-query sonja application-id-construction)  => truthy
                application-tj            (query-application local-query sonja application-id-tj)            => truthy
                application-suunnittelija (query-application local-query sonja application-id-suunnittelija) => truthy]

            (fact "submitted"
              (:state application-submitted)     => "submitted")
            (fact "canceled"
              (:state application-canceled)      => "canceled")
            (fact "verdictGiven"
              (:state application-verdict-given) => "verdictGiven")
            (fact "constructionStarted"
              (:state application-construction)  => "constructionStarted")
            (fact "tyonjohtaja"
              (:state application-tj)            => "foremanVerdictGiven")
            (fact "suunnittelija"
              (:state application-suunnittelija) => "verdictGiven")) => truthy)

        (fact "checking review updates states"

          (let [poll-result (batchrun/poll-verdicts-for-reviews)
                application-submitted     (query-application local-query sonja application-id-submitted)     => truthy
                application-canceled      (query-application local-query sonja application-id-canceled)      => truthy
                application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy
                application-construction  (query-application local-query sonja application-id-construction)  => truthy
                application-tj            (query-application local-query sonja application-id-tj)            => truthy
                application-suunnittelija (query-application local-query sonja application-id-suunnittelija) => truthy]

            (fact "submitted"
              (:state application-submitted)     => "submitted")
            (fact "canceled"
              (:state application-canceled)      => "canceled")
            (fact "application state is updated from verdictGive to constructionStarted"
              (:state application-verdict-given) => "constructionStarted")
            (fact "constructionStarted state is updated"
              (:state application-construction)  => "inUse")
            (fact "tj application state is not updated"
              (:state application-tj) => "foremanVerdictGiven")
            (fact "suunnittelija application state is not updated"
              (:state application-suunnittelija) => "verdictGiven")) => truthy

          ;; Review query is made only for applications in eligible state -> xml found for verdict given application
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given application-id-construction] :application-id anything)
                    => (build-multi-app-xml [[{:lp-tunnus application-id-verdict-given} {:pvm "2016-06-05Z" :tila "rakennusty\u00f6t aloitettu"}]
                                             [{:lp-tunnus application-id-construction}  {:pvm "2016-06-06Z" :tila "osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on k\u00e4ytt\u00f6\u00f6notettu"}]])))

        (fact "checking review updates states again"
          (let [poll-result (batchrun/poll-verdicts-for-reviews)
                application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy
                application-construction  (query-application local-query sonja application-id-construction)  => truthy]

            (fact "verdict given application state is not updated"
              (:state application-verdict-given) => "constructionStarted")
            (fact "constructionStarted state is not updated, since fetched state is before current state"
              (:state application-construction)  => "inUse")) => truthy

          ;; Review query is made only for applications in eligible state -> xml found for verdict given application
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given application-id-construction] :application-id anything)
                    => (build-multi-app-xml [[{:lp-tunnus application-id-construction}  {:pvm "2016-06-07Z" :tila "rakennusty\u00f6t aloitettu"}]]))
          ;; No result for verdict given appwhen retried with kuntalupatunnus
          (provided (krysp-reader/rakval-application-xml anything anything ["verdict-vg"] :kuntalupatunnus anything) => nil))))))

(facts "Imported review PDF generation"
  (mongo/with-db db-name
    (let [parsed-xml     (sxml/parse-string (slurp "resources/krysp/dev/r-verdict-review.xml") "utf-8")
          application    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
          application-id (:id application)
          batchrun-user  (usr/batchrun-user (map :id (batchrun/orgs-for-review-fetch)))
          read-result    (verdict/read-reviews-from-xml batchrun-user (now) application parsed-xml)]
      (fact "give verdict"
        (give-local-verdict sonja (:id application) :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)
      (fact "read verdict"
        read-result => ok?)
      (verdict/save-review-updates batchrun-user application (:updates read-result) (:added-tasks-with-updated-buildings read-result))
      (let [updated-application (domain/get-application-no-access-checking application-id)
            last-attachment-id (last (get-attachment-ids updated-application))
            last-attachment-file-id (att/attachment-latest-file-id updated-application last-attachment-id)]
        (files/with-temp-file temp-pdf-path
          (with-open [content-fios ((:content (mongo/download last-attachment-file-id)))]
            (pdftk/uncompress-pdf content-fios (.getAbsolutePath temp-pdf-path)))
          (re-seq #"(?ms)\(Kiinteist.tunnus\).{1,100}18600303560006" (slurp temp-pdf-path :encoding "ISO-8859-1")) => not-empty
          (re-seq #"(?ms)\(Tila\).{1,100}lopullinen" (slurp temp-pdf-path :encoding "ISO-8859-1")) => truthy)))))
