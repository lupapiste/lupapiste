(ns lupapalvelu.autom-check-reviews-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.java.io :as io]
            [sade.core :refer [now fail]]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [sade.coordinate :as coordinate]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.action :as action]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.tasks :refer [task-is-review?]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.integrations-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.pdftk :as pdftk]
            [lupapalvelu.xml.krysp.application-from-krysp :as app-from-krysp]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.user :as usr])
  (:import [java.io File]
           [org.xml.sax SAXParseException]))

(def db-name (str "test_autom-check-reviews-itest_" (now)))

(testable-privates lupapalvelu.verdict save-reviews-from-xml)

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
    (against-background [(coordinate/convert anything anything anything anything) => nil
                         ]
      (let [application-submitted          (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 17")
            application-id-submitted       (:id application-submitted)
            application-verdict-given-1    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
            application-id-verdict-given-1 (:id application-verdict-given-1)
            application-verdict-given-2    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 19")
            application-id-verdict-given-2 (:id application-verdict-given-2)
            ]

        (fact "Initial state of reviews before krysp reading is sane"
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
                application-verdict-given-2 (query-application local-query sonja application-id-verdict-given-1) => truthy]

            (:state application-submitted) => "submitted"
            (:state application-verdict-given-1) => "verdictGiven"
            (:state application-verdict-given-2) => "verdictGiven")

          (count (:tasks application-verdict-given-1)) => 0
          (count (:tasks application-verdict-given-2)) => 0)

        (fact "failure in fetching multiple applications causes fallback into fetching consecutively"

          (let [app-1-before (query-application local-query sonja application-id-verdict-given-1)
                app-2-before (query-application local-query sonja application-id-verdict-given-2)
                review-count-before (count-reviews sonja application-id-verdict-given-1) => 3
                poll-result   (batchrun/poll-verdicts-for-reviews)
                last-review-1 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
                last-review-2 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-2)))
                app-1-after   (query-application local-query sonja application-id-verdict-given-2)
                app-1-after   (query-application local-query sonja application-id-verdict-given-2)]

            (fact "reviews for submitted application"
              (count-reviews sonja application-id-submitted) => 0)
            (fact "last review state for application 1"
              (:state last-review-1) => "requires_user_action")
            (fact "last review state for application 2"
              (:state last-review-2) => "sent")
            (fact "reviews for verdict given application 1"
              (count-reviews sonja application-id-verdict-given-1) => 3)
            (fact "reviews for verdict given application 2"
              (count-reviews sonja application-id-verdict-given-2) => 4)) => truthy

          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1] anything anything)
                    => nil)
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-2] anything anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id-verdict-given-2)
                           (sxml/parse-string "utf-8")))
          (provided (krysp-reader/rakval-application-xml anything anything anything anything anything)
                    =throws=> (SAXParseException. "msg" "id" "sid" 0 0)))

        (fact "checking for reviews in correct states"

          (let [app-before (query-application local-query sonja application-id-verdict-given-1)
                review-count-before (count-reviews sonja application-id-verdict-given-1) => 3
                poll-result (batchrun/poll-verdicts-for-reviews)
                last-review (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
                app-after (query-application local-query sonja application-id-verdict-given-1)]

            (fact "reviews for submitted application"
              (count-reviews sonja application-id-submitted) => 0)
            (fact "last review state"
              (:state last-review) => "sent")
            (fact "reviews for verdict given application"
              (count-reviews sonja application-id-verdict-given-1) => 4)) => truthy

          (provided (krysp-reader/rakval-application-xml anything anything anything anything anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id-verdict-given-1)
                           (sxml/parse-string "utf-8"))))

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
            (count (filter final-review? reviews)) => 1
            (get-in (first (filter final-review? reviews)) [:data :rakennus :0 :tila :tila]) => "lopullinen"))))))

(facts "Imported review PDF generation"
  (mongo/with-db db-name
    (let [parsed-xml (sxml/parse-string (slurp "resources/krysp/dev/r-verdict-review.xml") "utf-8")
          application    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
          application-id (:id application)
          batchrun-user (usr/batchrun-user (map :id (batchrun/orgs-for-review-fetch)))]
      (give-local-verdict sonja (:id application) :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
      (save-reviews-from-xml batchrun-user (now) application parsed-xml) ;; => ok?
      (let [updated-application (domain/get-application-no-access-checking application-id)
            last-attachment-id (last (get-attachment-ids updated-application))
            last-attachment-file-id (att/attachment-latest-file-id updated-application last-attachment-id)
            temp-pdf-path (File/createTempFile "review-test" ".tmp")]
        (try
          (with-open [content-fios ((:content (mongo/download last-attachment-file-id)))]
            (pdftk/uncompress-pdf content-fios (.getAbsolutePath temp-pdf-path)))
          (re-seq #"(?ms)\(Kiinteist.tunnus\).{1,100}18600303560006" (slurp temp-pdf-path :encoding "ISO-8859-1")) => not-empty
          (re-seq #"(?ms)\(Tila\).{1,100}lopullinen" (slurp temp-pdf-path :encoding "ISO-8859-1")) => truthy
          (finally
            (io/delete-file temp-pdf-path)))))))
