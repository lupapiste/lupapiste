(ns lupapalvelu.autom-check-reviews-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.java.io :as io]
            [slingshot.support]
            [sade.core :refer [now fail]]
            [sade.files :as files]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
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
            [lupapalvelu.user :as usr]
            [lupapalvelu.review :as review]
            [lupapalvelu.organization :as organization])
  (:import [org.xml.sax SAXParseException]))

(def db-name (str "test_autom-check-reviews-itest_" (now)))

(defn- slingshot-exception [ex-map]
  (-> (slingshot.support/make-context ex-map (str "throw+: " ex-map) nil (slingshot.support/stack-trace))
      (slingshot.support/wrap)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        review-assignment-trigger (organization/create-trigger
                                     nil
                                     ["katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja"]
                                     {:id "abba1111111111111111acdc"
                                      :name {:fi "Käsittelijä" :sv "Handläggare" :en "Handler"}} "review-test-trigger")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)
        organizations (map
                        (fn [org] (if (= "753-R" (:id org))
                                    (update-in org [:assignment-triggers] conj review-assignment-trigger)
                                    org))
                        organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(defn  query-tasks [user application-id]
  (:tasks (query-application local-query user application-id)))

(defn count-reviews [user app-id] (count (filter task-is-review? (query-tasks user app-id))))

(comment
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
            application-id-verdict-given-2 (:id application-verdict-given-2)]

        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-1 :description "otsikko julkipanoon") => ok?
        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-2 :description "otsikko julkipanoon") => ok?

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
              (count-reviews sonja application-id-verdict-given-1) => 7)
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
          (count-reviews sonja application-id-verdict-given-1) => 7
          (let [tasks (map tools/unwrapped  (query-tasks sonja application-id-verdict-given-1))
                reviews (filter task-is-review? tasks)
                review-types (map #(-> % :data :katselmuksenLaji) reviews)
                final-review? (fn [review]
                                (= (get-in review [:data :katselmus :tila]) "lopullinen"))]
            (fact "no validation errors"
              (not-any? :validationErrors reviews))
            (count (filter  (partial = "aloituskokous") review-types)) => 3
            (get-in (first (filter final-review? reviews)) [:data :rakennus :0 :tila :tila]) => "lopullinen"))))))

(fact "Automatic checking for reviews - 404 in fetching multiple applications causes fallback into fetching consecutively"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [application-verdict-given-1    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
            application-id-verdict-given-1 (:id application-verdict-given-1)
            application-verdict-given-2    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 19")
            application-id-verdict-given-2 (:id application-verdict-given-2)]

        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-1 :description "otsikko julkipanoon") => ok?
        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-2 :description "otsikko julkipanoon") => ok?
        (local-command sonja :approve-application :id application-id-verdict-given-1 :lang "fi") => ok?
        (local-command sonja :approve-application :id application-id-verdict-given-2 :lang "fi") => ok?
        (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) => 0
        (count (batchrun/fetch-verdicts)) => pos?
        (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) =not=> 0

        (give-local-verdict sonja application-id-verdict-given-1 :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
        (give-local-verdict sonja application-id-verdict-given-2 :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

        ;; Trying to fetch with multiple ids throws xml parser exception -> causes fallback into consecutive fetching
        (let [review-count-before (count-reviews sonja application-id-verdict-given-1) => 3
              poll-result   (batchrun/poll-verdicts-for-reviews)
              last-review-1 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
              last-review-2 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-2)))
              app-1         (query-application local-query sonja application-id-verdict-given-1)
              app-2         (query-application local-query sonja application-id-verdict-given-2)]

          (fact "last review state for application 1"
            (:state last-review-1) => "requires_user_action")
          (fact "last review state for application 2"
            (:state last-review-2) => "sent")
          (fact "application 1 state not updated"
            (:state app-1) => "verdictGiven")
          (fact "application 2 state is updated"
            (:state app-2) => "constructionStarted")) => truthy

            (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1 application-id-verdict-given-2] :application-id anything) =throws=> (slingshot-exception {:sade.core/type :sade.core/fail, :status 404}))
            ;; Fallback - no xml found for application 1 by application id or backend-id
            (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1] :application-id anything) => nil)
            (provided (krysp-reader/rakval-application-xml anything anything ["2013-01"] :kuntalupatunnus anything) => nil)
            ;; Xml found for application 2
            (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-2] :application-id anything)
                      => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                             (ss/replace #"LP-186-2014-90009" application-id-verdict-given-2)
                             (sxml/parse-string "utf-8")))))))

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

        (doseq [id [application-id-verdict-given application-id-construction]]
          (local-command sonja :update-app-bulletin-op-description :id id :description "otsikko julkipanoon") => ok?)


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
          read-result    (review/read-reviews-from-xml batchrun-user (now) application parsed-xml)]
      (fact "give verdict"
        (give-local-verdict sonja (:id application) :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)
      (fact "read verdict"
        read-result => ok?)
      (fact "tasks have created timestamp"
        (:added-tasks-with-updated-buildings read-result) => (has every? :created))
      (let [application (domain/get-application-no-access-checking application-id)]
        (review/save-review-updates (assoc (action/application->command application) :user batchrun-user)
                                    (:updates read-result)
                                    (:added-tasks-with-updated-buildings read-result)
                                    {})
        (let [updated-application (domain/get-application-no-access-checking application-id)
              last-attachment-id (last (get-attachment-ids updated-application))
              last-attachment-file-id (att/attachment-latest-file-id updated-application last-attachment-id)]
          (files/with-temp-file temp-pdf-path
            (with-open [content-fios ((:content (mongo/download last-attachment-file-id)))]
              (pdftk/uncompress-pdf content-fios (.getAbsolutePath temp-pdf-path)))
            ; Note that these checks are highly dependent on the PDF structure. Check if your (raw source) PDF content has changed if these fail.
            (re-seq #"(?ms)\(Kiinteist.{1,4}tunnus\).{1,200}18600303560006" (slurp temp-pdf-path :encoding "ISO-8859-1")) => not-empty
            (re-seq #"(?ms)\(Tila\).{1,200}lopullinen" (slurp temp-pdf-path :encoding "ISO-8859-1")) => truthy))))))

(facts "Automatic checking for reviews - overwriting existing reviews"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [application-id (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "submitted 16"))]
        (local-command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
        (facts "Initial state of applications before krysp reading is sane"
          (fact "approve app"          (local-command sonja :approve-application :id application-id :lang "fi") => ok?)
          (fact "give verdict for app" (give-local-verdict sonja application-id :verdictId "verdict-vg" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)

          (let [application (query-application local-query sonja application-id) => truthy]

            (fact "verdictGiven"
                  (:state application) => "verdictGiven"))
          => truthy)

        (fact "first batchrun"

          (let [poll-result (batchrun/poll-verdicts-for-reviews)
                application (query-application local-query sonja application-id) => truthy]
            (fact "application reviews contain the one that will be changed"
              (->> application :tasks (util/find-first #(= (-> % :data :katselmus :pitoPvm :value)
                                                           "29.09.2014"))
                 :data :katselmus :huomautukset :kuvaus :value) => (contains "aloituskokouksessa")))
          => truthy

          (provided (krysp-reader/rakval-application-xml anything anything [application-id] :application-id anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id)
                           (sxml/parse-string "utf-8"))))

        (fact "second batchrun, no overwrite"

          (let [poll-result (batchrun/poll-verdicts-for-reviews)
                application (query-application local-query sonja application-id) => truthy]

            (fact "application state is updated from verdictGive to constructionStarted"
                  (:state application) => "constructionStarted")

            ;; Since :overwrite-background-reviews? is nil, reviews
            ;; are not updated even though the review data in the XML
            ;; differs from the current review data in the application
            (fact "application reviews are not updated"
              (->> application :tasks (util/find-first #(= (-> % :data :katselmus :pitoPvm :value)
                                                           "29.09.2014"))
                   :data :katselmus :huomautukset :kuvaus :value) => (contains "aloituskokouksessa")) ;; <- NOTE!

            (fact "there are no faulty attachments"
              (let [faulty-attachments (->> application :attachments
                                            (filter #(= (-> % :metadata :tila)
                                                        "ei-arkistoida-virheellinen")))]
                faulty-attachments => empty?)))
          => truthy

          (provided (krysp-reader/rakval-application-xml anything anything [application-id] :application-id anything)
             => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                    (ss/replace #"LP-186-2014-90009" application-id)
                    (ss/replace #"aloituskokouksessa" "aloituskoKKouksessa") ;; <- NOTE!
                    (sxml/parse-string "utf-8"))))

        (fact "second batchrun, overwrite with the same data"
          (let [poll-result (batchrun/poll-verdicts-for-reviews :overwrite-background-reviews? true)
                application (query-application local-query sonja application-id) => truthy]

            ;; :overwrite-background-reviews? is true, but the review
            ;; data in the XML does not differ from the current review
            ;; data in the application
            (fact "there are no faulty attachments"
                  (let [faulty-attachments (->> application :attachments
                                                (filter #(= (-> % :metadata :tila)
                                                            "ei-arkistoida-virheellinen")))]
                    faulty-attachments => empty?)))
          => truthy

          (provided (krysp-reader/rakval-application-xml anything anything [application-id] :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id)
                   (sxml/parse-string "utf-8"))))

        (fact "third batchrun, overwrite with differing data"

          (let [application-before-rewrite (query-application local-query sonja application-id) => truthy
                old-task (->> application-before-rewrite :tasks
                              (util/find-first #(= (-> % :data :katselmus :pitoPvm :value)
                                                   "29.09.2014")))
                poll-result (batchrun/poll-verdicts-for-reviews :overwrite-background-reviews? true)
                application (query-application local-query sonja application-id) => truthy
                overwritten-task (->> application :tasks
                                      (util/find-first #(= (:id %)
                                                           (:id old-task))))
                overwriting-task (->> application :tasks
                                      (util/find-first #(and (not= (:state %)
                                                                   "faulty_review_task")
                                                             (= (-> % :data :katselmus :pitoPvm :value)
                                                                "29.09.2014"))))]

            (fact "one of the application reviews is updated"
              (->> overwriting-task :data :katselmus :huomautukset :kuvaus :value)
              => (contains "aloituskoKKouksessa"))  ;; <- NOTE!

            (fact "the overwritten review is marked faulty"
                  (:state overwritten-task) => "faulty_review_task")

            (fact "the associated attachments are removed"
              (let [faulty-attachments (->> application :attachments
                                            (filter #(= (-> % :target :id)
                                                        (:id overwritten-task))))]
                (count faulty-attachments) => 0)))
          => truthy

          (provided (krysp-reader/rakval-application-xml anything anything [application-id] :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id)
                   (ss/replace #"aloituskokouksessa" "aloituskoKKouksessa") ;; <- NOTE!
                   (sxml/parse-string "utf-8"))))))))

)
(facts "Automatic checking for reviews trigger automatic assignments"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (mongo/remove-many :assignments {})

    (let [trigger (-> (mongo/by-id :organizations "753-R")
                      :assignment-triggers
                      ((fn [map-coll] (filter #(= "review-test-trigger" (:description %)) map-coll)))
                      (first))
          get-assignments (fn [] (mongo/select :assignments {}))
          application-id-submitted (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "Hakemusjätettie 15"))]

      (fact "trigger ok"
        (:description trigger) => "review-test-trigger")

      (fact "initially zero assignments"
            (count (get-assignments)) => 0)

      (fact "verdict to application"
            (give-local-verdict sonja application-id-submitted :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)

      (fact "first batchrun creates assignments"
        (let [poll-result (batchrun/poll-verdicts-for-reviews)
              assignments (get-assignments)]

          (fact "attachments trigger assignments"
                (count (get-assignments)) => 1)

          (fact "assignment is not user-created"
                (-> (get-assignments) (first) :trigger) => (:id trigger))) => truthy

        (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                  => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                         (ss/replace #"LP-186-2014-90009" application-id-submitted)
                         (sxml/parse-string "utf-8"))))

      (fact "batchrun does not change assignments if there is no changed attachments"
        (let [old-assignments (get-assignments)
              poll-result (batchrun/poll-verdicts-for-reviews)
              assignments (get-assignments)]

          (fact "no new assignments"
                (count (get-assignments)) => 1)

          (fact "assignments do not change"
                (first old-assignments) => (first assignments))) => truthy

        (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                  => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                         (ss/replace #"LP-186-2014-90009" application-id-submitted)
                         (sxml/parse-string "utf-8"))))

      (fact "batchrun updates assignment if application status changes"
        (let [old-assignments (get-assignments)
              poll-result (batchrun/poll-verdicts-for-reviews)
              assignments (get-assignments)]

          (fact "only one assignment"
            (count assignments) => 1)

          (fact "only one state"
            (count (-> assignments (first) :states)) => 1)
          ;; tee testeistä järkevämpiä tee testeistä järkevämpiätee testeistä järkevämpiätee testeistä järkevämpiätee testeistä järkevämpiä
          (fact "assignment in created status"
            (-> assignments (first) :states :type) => "created")

          (fact "updated assignment is updated"
            (> (-> assignments (first) :states :timestamp)
               (-> old-assignments (first) :states :timestamp)) => true)) => truthy

        (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                  => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                         (ss/replace #"LP-186-2014-90009" application-id-submitted)
                         (ss/replace #"<yht:muokkausHetki>2014-09-17T09:37:06Z</yht:muokkausHetki>"
                                      "<yht:muokkausHetki>2014-09-20T09:37:06Z</yht:muokkausHetki>")
                         (sxml/parse-string "utf-8"))))

      (fact "batchrun does nothing to completed assignments that have not changed"
        (let [old-assignments (get-assignments)
              _ (complete-assignment sonja (:id application-id-submitted))
              poll-result (batchrun/poll-verdicts-for-reviews)
              assignments (get-assignments)]

          (fact "no new assignments"
            (count assignments) => 1)

          (fact "batchrun does not create new assignments if attachment has completed assignments"
            (= (-> old-assignments :targets (first) :id) (-> assignments :targets (first) :id)) => true)) => truthy

        (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                   => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                          (ss/replace #"LP-186-2014-90009" application-id-submitted)
                          (ss/replace #"<yht:muokkausHetki>2014-09-17T09:37:06Z</yht:muokkausHetki>"
                                       "<yht:muokkausHetki>2014-09-20T09:37:06Z</yht:muokkausHetki>")
                          (sxml/parse-string "utf-8")))))))
