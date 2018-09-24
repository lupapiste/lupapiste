(ns lupapalvelu.autom-check-reviews-itest
  "TODO: The current tests do not make sense in the Pate
  era. `give-local-legacy-verdict` creates legacy Pate verdicts and
  those are excluded from review batchrun. More feasible approach
  could be using the backing system verdicts instead. However, it is
  not self-evident how those could be managed locally. Overall, this
  test suite should be thought again."
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.pdftk :as pdftk]
            [lupapalvelu.review :as review]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.tasks :refer [task-is-review?]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-api]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.coordinate :as coordinate]
            [sade.core :refer [now fail]]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [slingshot.support])
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
            application-id-verdict-given-2 (:id application-verdict-given-2)]

        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-1 :description "otsikko julkipanoon") => ok?
        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-2 :description "otsikko julkipanoon") => ok?

        (facts "Initial state of reviews before krysp reading is sane"
          (local-command sonja :approve-application :id application-id-verdict-given-1 :lang "fi") => ok?
          (local-command sonja :approve-application :id application-id-verdict-given-2 :lang "fi") => ok?
          (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) => 0
          (fetch-verdicts) => nil?
          (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) =not=> 0

          (give-local-legacy-verdict sonja application-id-verdict-given-1)
          (give-local-legacy-verdict sonja application-id-verdict-given-2)

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

          (let [_ (count-reviews sonja application-id-verdict-given-1) => 3
                _ (batchrun/poll-verdicts-for-reviews)
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
          (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given-1 application-id-verdict-given-2] :in-any-order) :application-id anything) =throws=> (SAXParseException. "msg" "id" "sid" 0 0))
          ;; Fallback - no xml found for application 1 by application id or backend-id
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1] :application-id anything) => nil)
          (provided (krysp-reader/rakval-application-xml anything anything ["2013-01"] :kuntalupatunnus anything) => nil)
          ;; Xml found for application 2
          (provided (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-2] :application-id anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id-verdict-given-2)
                           (sxml/parse-string "utf-8"))))

        (fact "checking for reviews in correct states"

          (let [_ (count-reviews sonja application-id-verdict-given-1) => 3
                _ (batchrun/poll-verdicts-for-reviews)
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
          (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given-1 application-id-verdict-given-2] :in-any-order) :application-id anything)
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
        (fetch-verdicts) => nil?
        (count  (:tasks (query-application local-query sonja application-id-verdict-given-1))) =not=> 0

        (give-local-legacy-verdict sonja application-id-verdict-given-1)
        (give-local-legacy-verdict sonja application-id-verdict-given-2)

        ;; Trying to fetch with multiple ids throws xml parser exception -> causes fallback into consecutive fetching
        (let [_ (count-reviews sonja application-id-verdict-given-1) => 3
              _   (batchrun/poll-verdicts-for-reviews)
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

            (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given-1 application-id-verdict-given-2] :in-any-order) :application-id anything) =throws=> (slingshot-exception {:sade.core/type :sade.core/fail, :status 404}))
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

          (fact "Give verdict for verdictGiven app"
            (give-local-legacy-verdict sonja application-id-verdict-given
                                       :kuntalupatunnus "verdict-vg"))
          (fact "give verdict for construction app" (give-local-legacy-verdict sonja application-id-construction
                                                                               :kuntalupatunnus "verdict-cs"))
          (fact "give verdict for tj app"    (give-local-legacy-verdict sonja application-id-tj
                                                                        :kuntalupatunnus "verdict-tj"))

          (fact "approve suunnittelija app"  (local-command sonja :approve-application :id application-id-suunnittelija :lang "fi") => ok?)
          (fact "give verdict for suunnittelija app"   (give-local-legacy-verdict sonja application-id-suunnittelija
                                                                                  :kuntalupatunnus "verdict-suun"))


          (fact "give verdict for suunnittelja app"
            (give-local-legacy-verdict sonja application-id-suunnittelija))

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

          (let [_                         (batchrun/poll-verdicts-for-reviews)
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
          (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given application-id-construction] :in-any-order) :application-id anything)
                    => (build-multi-app-xml [[{:lp-tunnus application-id-verdict-given} {:pvm "2016-06-05Z" :tila "rakennusty\u00f6t aloitettu"}]
                                             [{:lp-tunnus application-id-construction}  {:pvm "2016-06-06Z" :tila "osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on k\u00e4ytt\u00f6\u00f6notettu"}]])))

        (fact "checking review updates states again"
          (let [_                         (batchrun/poll-verdicts-for-reviews)
                application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy
                application-construction  (query-application local-query sonja application-id-construction)  => truthy]

            (fact "verdict given application state is not updated"
              (:state application-verdict-given) => "constructionStarted")
            (fact "constructionStarted state is not updated, since fetched state is before current state"
              (:state application-construction)  => "inUse")) => truthy

          ;; Review query is made only for applications in eligible state -> xml found for verdict given application
          (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given application-id-construction] :in-any-order) :application-id anything)
                    => (build-multi-app-xml [[{:lp-tunnus application-id-construction}  {:pvm "2016-06-07Z" :tila "rakennusty\u00f6t aloitettu"}]]))
          ;; No result for verdict given app when retried with kuntalupatunnus
          #_(provided (krysp-reader/rakval-application-xml anything anything ["verdict-vg"] :kuntalupatunnus anything) => nil))))))

(facts "Imported review PDF generation"
  (mongo/with-db db-name
    (let [parsed-xml     (sxml/parse-string (slurp "resources/krysp/dev/r-verdict-review.xml") "utf-8")
          application    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
          application-id (:id application)
          batchrun-user  (usr/batchrun-user (map :id (batchrun/orgs-for-review-fetch)))
          read-result    (review/read-reviews-from-xml batchrun-user (now) application parsed-xml)]
      (fact "give verdict"
        (give-local-legacy-verdict sonja (:id application)))
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
            (with-open [content-fios ((:content (storage/download updated-application last-attachment-file-id)))]
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
          (fact "give verdict for app" (give-local-legacy-verdict sonja application-id
                                                                  :kuntalupatunnus "verdict-vg"))

          (let [application (query-application local-query sonja application-id) => truthy]

            (fact "verdictGiven"
                  (:state application) => "verdictGiven"))
          => truthy)

        (fact "first batchrun"

          (let [_ (batchrun/poll-verdicts-for-reviews)
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

          (let [_ (batchrun/poll-verdicts-for-reviews)
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
          (let [_ (batchrun/poll-verdicts-for-reviews :overwrite-background-reviews? true)
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
                _ (batchrun/poll-verdicts-for-reviews :overwrite-background-reviews? true)
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
