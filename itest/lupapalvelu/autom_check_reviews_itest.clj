(ns lupapalvelu.autom-check-reviews-itest
  "TODO: The current tests do not make sense in the Pate
  era. `give-local-legacy-verdict` creates legacy Pate verdicts and
  those are excluded from review batchrun. More feasible approach
  could be using the backing system verdicts instead. However, it is
  not self-evident how those could be managed locally. Overall, this
  test suite should be thought again."
  (:require [clojure.java.io :as io]
            [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.krysp-test-util :refer [build-multi-app-xml]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.review :as review]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.tasks :refer [task-is-review?]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-api]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer :all]
            [pandect.core :as pandect]
            [sade.coordinate :as coordinate]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [slingshot.support]
            [swiss.arrows :refer :all])
  (:import [java.io Closeable]
           [org.xml.sax SAXParseException]))

(set! *warn-on-reflection* true)

(testable-privates lupapalvelu.review)

(defn- slingshot-exception [ex-map]
  (-> (slingshot.support/make-context ex-map (str "throw+: " ex-map) nil (slingshot.support/stack-trace))
      (slingshot.support/wrap)))

(defn query-tasks [user application-id]
  (:tasks (query-application local-query user application-id)))

(defn count-reviews [user app-id] (count (filter task-is-review? (query-tasks user app-id))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {})

  (let [krysp-url     (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) (minimal/organizations {:http-port (-> (test-system/get-common-components)
                                                                                                                                        components/system->http-port)}))]
    (dorun (map (partial mongo/insert :organizations) organizations)))

  (facts "Automatic checking for reviews"
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil
                         ]
      (let [application-submitted          (create-and-submit-local-application sonja :propertyId sipoo-property-id
                                                                                :address "Katselmuskuja 17")
            application-id-submitted       (:id application-submitted)
            application-verdict-given-1    (create-and-submit-local-application sonja :propertyId sipoo-property-id
                                                                                :address "Katselmuskuja 18")
            application-id-verdict-given-1 (:id application-verdict-given-1)
            application-verdict-given-2    (create-and-submit-local-application sonja :propertyId sipoo-property-id
                                                                                :address "Katselmuskuja 19")
            application-id-verdict-given-2 (:id application-verdict-given-2)]

        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-1
                       :description "otsikko julkipanoon") => ok?
        (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given-2
                       :description "otsikko julkipanoon") => ok?

        (facts "Initial state of reviews before krysp reading is sane"
          (local-command sonja :approve-application :id application-id-verdict-given-1 :lang "fi") => ok?
          (local-command sonja :approve-application :id application-id-verdict-given-2 :lang "fi") => ok?
          (count (:tasks (query-application local-query sonja application-id-verdict-given-1))) => 0
          (fetch-verdicts) => nil?
          (count (:tasks (query-application local-query sonja application-id-verdict-given-1))) =not=> 0

          (give-local-legacy-verdict sonja application-id-verdict-given-1 :R true)
          (give-local-legacy-verdict sonja application-id-verdict-given-2 :R true)

          (let [application-submitted       (query-application local-query sonja application-id-submitted)
                application-verdict-given-1 (query-application local-query sonja application-id-verdict-given-1)
                application-verdict-given-2 (query-application local-query sonja application-id-verdict-given-2)]

            (fact "queries ok"
              application-submitted => truthy
              application-verdict-given-1 => truthy
              application-verdict-given-2 => truthy)
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

          (let [reviews-count (count-reviews sonja application-id-verdict-given-1)
                _             (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                app-1         (query-application local-query sonja application-id-verdict-given-1)
                app-2         (query-application local-query sonja application-id-verdict-given-2)
                last-review-1 (last (filter task-is-review? (:tasks app-1)))
                last-review-2 (last (filter task-is-review? (:tasks app-2)))]

            reviews-count => 3

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
          (provided
            (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given-1 application-id-verdict-given-2] :in-any-order) :application-id anything)
            =throws=> (SAXParseException. "msg" "id" "sid" 0 0))
          ;; Fallback - no xml found for application 1 by application id or backend-id
          (provided
            (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-1] :application-id anything)
            => nil)
          (provided
            (krysp-reader/rakval-application-xml anything anything ["2013-01"] :kuntalupatunnus anything)
            => nil)
          ;; Xml found for application 2
          (provided
            (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-2] :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id-verdict-given-2)
                   (sxml/parse-string "utf-8")
                   test-system/fix-urls)
            (#'lupapalvelu.review/validate-changed-tasks anything anything anything) => nil))

        (fact "checking for reviews in correct states"

          (let [reviews-count (count-reviews sonja application-id-verdict-given-1)
                _             (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                last-review   (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
                app           (query-application local-query sonja application-id-verdict-given-1)]

            reviews-count => 3

            (fact "reviews for submitted application"
              (count-reviews sonja application-id-submitted) => 0)
            (fact "last review state"
              (:state last-review) => "sent")
            (fact "reviews for verdict given application"
              (count-reviews sonja application-id-verdict-given-1) => 6)
            (fact "application state is updated"
              (:state app) => "constructionStarted")) => truthy

          ;; Application xml found for application 1 when fetching multiple applications
          (provided
            (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given-1 application-id-verdict-given-2] :in-any-order) :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id-verdict-given-1)
                   (sxml/parse-string "utf-8")
                   test-system/fix-urls)
            (#'lupapalvelu.review/validate-changed-tasks anything anything anything) => nil)
          ;; Fetching xml for application 2 by kuntalupatunnus returns nil
          (provided (krysp-reader/rakval-application-xml anything anything ["2013-01"] :kuntalupatunnus anything) => nil))

        (fact "existing tasks are preserved"
          (count-reviews sonja application-id-verdict-given-1) => 6
          (let [tasks         (map tools/unwrapped (query-tasks sonja application-id-verdict-given-1))
                reviews       (filter task-is-review? tasks)
                review-types  (map #(-> % :data :katselmuksenLaji) reviews)
                final-review? (fn [review]
                                (= (get-in review [:data :katselmus :tila]) "lopullinen"))]
            (fact "no validation errors"
              (not-any? :validationErrors reviews))
            (count (filter (partial = "aloituskokous") review-types)) => 2
            (get-in (first (filter final-review? reviews)) [:data :rakennus :0 :tila :tila]) => "lopullinen"))))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (fact "Automatic checking for reviews - 404 in fetching multiple applications causes fallback into fetching consecutively"
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
        (count (:tasks (query-application local-query sonja application-id-verdict-given-1))) => 0
        (fetch-verdicts) => nil?
        (count (:tasks (query-application local-query sonja application-id-verdict-given-1))) =not=> 0

        (give-local-legacy-verdict sonja application-id-verdict-given-1 :R true)
        (give-local-legacy-verdict sonja application-id-verdict-given-2 :R true)

        ;; Trying to fetch with multiple ids throws xml parser exception -> causes fallback into consecutive fetching
        (let [reviews-count (count-reviews sonja application-id-verdict-given-1)
              _             (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
              last-review-1 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-1)))
              last-review-2 (last (filter task-is-review? (query-tasks sonja application-id-verdict-given-2)))
              app-1         (query-application local-query sonja application-id-verdict-given-1)
              app-2         (query-application local-query sonja application-id-verdict-given-2)]
          reviews-count => 3
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
        (provided
          (krysp-reader/rakval-application-xml anything anything [application-id-verdict-given-2] :application-id anything)
          => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                 (ss/replace #"LP-186-2014-90009" application-id-verdict-given-2)
                 (sxml/parse-string "utf-8")
                 test-system/fix-urls)
          (#'lupapalvelu.review/validate-changed-tasks anything anything anything) => nil)))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (facts "Automatic checking for reviews - application state and operation"
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [application-id-submitted     (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "submitted 16"))
            application-id-canceled      (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "canceled 17"))
            application-id-verdict-given (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "verdict-given 18"))
            application-id-construction  (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "construction-started 19"))
            application-id-tj            (:id (create-local-app pena :propertyId sipoo-property-id :address "foreman 20" :operation "tyonjohtajan-nimeaminen-v2"))
            _                            (local-command pena :submit-application :id application-id-tj)
            application-id-suunnittelija (:id (create-local-app sonja :propertyId sipoo-property-id :address "suunnittelija 21" :operation "suunnittelijan-nimeaminen"))
            _                            (local-command pena :submit-application :id application-id-suunnittelija)]

        (doseq [id [application-id-verdict-given application-id-construction]]
          (local-command sonja :update-app-bulletin-op-description :id id :description "otsikko julkipanoon") => ok?)


        (facts "Initial state of applications before krysp reading is sane"
          (fact "cancel application"
            (local-command pena :cancel-application :id application-id-canceled :lang "fi" :text "cancelation") => ok?)

          (fact "approve verdictGiven app" (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?)
          (fact "approve construction app" (local-command sonja :approve-application :id application-id-construction :lang "fi") => ok?)

          (fact "add link permit for tj app" (local-command pena :add-link-permit :id application-id-tj :linkPermitId application-id-verdict-given) => ok?)
          (fact "tj app as tj-ilmoitus" (local-command pena :change-permit-sub-type :id application-id-tj :permitSubtype "tyonjohtaja-hakemus") => ok?)
          (fact "submit tj app" (local-command pena :submit-application :id application-id-tj) => ok?)
          (fact "approve tj app" (local-command sonja :approve-application :id application-id-tj :lang "fi") => ok?)

          (fact "add link permit for suunnittelija app" (local-command sonja :add-link-permit :id application-id-suunnittelija :linkPermitId application-id-verdict-given) => ok?)
          (fact "submit suunnittelija app" (local-command sonja :submit-application :id application-id-suunnittelija) => ok?)

          (fact "Give verdict for verdictGiven app"
            (give-local-legacy-verdict sonja application-id-verdict-given :R true
                                       :kuntalupatunnus "verdict-vg"))
          (fact "give verdict for construction app" (give-local-legacy-verdict sonja application-id-construction :R true
                                                                               :kuntalupatunnus "verdict-cs"))
          ;; TJ app does not follow kuulutusmenettely or julkipano for that matter
          (fact "give verdict for tj app" (give-local-legacy-verdict sonja application-id-tj :TJ false
                                                                     :kuntalupatunnus "verdict-tj"
                                                                     :julkipano nil))

          (fact "approve suunnittelija app" (local-command sonja :approve-application :id application-id-suunnittelija :lang "fi") => ok?)
          (fact "give verdict for suunnittelija app" (give-local-legacy-verdict sonja application-id-suunnittelija :R false
                                                                                :kuntalupatunnus "verdict-suun"))

          (fact "give verdict for suunnittelja app"
            ;; Suunnittelijan nimeäminen is similar to TJ applications: they do not
            ;; participate in bulletins => use julkipano dates
            (give-local-legacy-verdict sonja application-id-suunnittelija :R false))

          (fact "add application comment for verdictGiven app"
            (local-command sonja :add-comment :id application-id-verdict-given
                           :text "See you in pdf!"
                           :target {:type "application"}
                           :openApplication false
                           :roles []) => ok?)

          (fact "change construction application state to constructionStarted"
            (local-command sonja :change-application-state :id application-id-construction :state "constructionStarted") => ok?)

          (let [application-submitted     (query-application local-query sonja application-id-submitted)
                application-canceled      (query-application local-query sonja application-id-canceled)
                application-verdict-given (query-application local-query sonja application-id-verdict-given)
                application-construction  (query-application local-query sonja application-id-construction)
                application-tj            (query-application local-query sonja application-id-tj)
                application-suunnittelija (query-application local-query sonja application-id-suunnittelija)]

            (fact "queries ok"
              application-submitted => truthy
              application-canceled => truthy
              application-verdict-given => truthy
              application-construction => truthy
              application-tj => truthy
              application-suunnittelija => truthy)
            (fact "submitted"
              (:state application-submitted) => "submitted")
            (fact "canceled"
              (:state application-canceled) => "canceled")
            (fact "verdictGiven"
              (:state application-verdict-given) => "verdictGiven")
            (fact "constructionStarted"
              (:state application-construction) => "constructionStarted")
            (fact "tyonjohtaja"
              (:state application-tj) => "foremanVerdictGiven")
            (fact "suunnittelija"
              (:state application-suunnittelija) => "verdictGiven")) => truthy)

        (fact "checking review updates states"

          (let [_                         (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                application-submitted     (query-application local-query sonja application-id-submitted)
                application-canceled      (query-application local-query sonja application-id-canceled)
                application-verdict-given (query-application local-query sonja application-id-verdict-given)
                application-construction  (query-application local-query sonja application-id-construction)
                application-tj            (query-application local-query sonja application-id-tj)
                application-suunnittelija (query-application local-query sonja application-id-suunnittelija)]

            (fact "queries ok"
              application-submitted => truthy
              application-canceled => truthy
              application-verdict-given => truthy
              application-construction => truthy
              application-tj => truthy
              application-suunnittelija => truthy)
            (fact "submitted"
              (:state application-submitted) => "submitted")
            (fact "canceled"
              (:state application-canceled) => "canceled")
            (fact "application state is updated from verdictGive to constructionStarted"
              (:state application-verdict-given) => "constructionStarted")
            (fact "constructionStarted state is updated"
              (:state application-construction) => "inUse")
            (fact "tj application state is not updated"
              (:state application-tj) => "foremanVerdictGiven")
            (fact "suunnittelija application state is not updated"
              (:state application-suunnittelija) => "verdictGiven")
            (fact "comments attachment is not created for verdictGiven app"
              (->> (:attachments application-verdict-given)
                   (util/find-first #(= (:type %) {:type-group "muut"
                                                   :type-id    "keskustelu"})))
              => nil)) => truthy

          ;; Review query is made only for applications in eligible state -> xml found for verdict given application
          (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given application-id-construction] :in-any-order) :application-id anything)
                    => (build-multi-app-xml [[{:lp-tunnus application-id-verdict-given} {:pvm "2016-06-05Z" :tila "rakennustyöt aloitettu"}]
                                             [{:lp-tunnus application-id-construction} {:pvm "2016-06-06Z" :tila "osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on käyttöönotettu"}]])))

        (fact "checking review updates states again"
          (let [_                         (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                application-verdict-given (query-application local-query sonja application-id-verdict-given)
                application-construction  (query-application local-query sonja application-id-construction)]
            (fact "queries ok"
              application-verdict-given => truthy
              application-construction => truthy)
            (fact "verdict given application state is now closed"
              (:state application-verdict-given) => "closed")

            (fact "comments attachment is created for verdictGiven app"
              (let [comments-attachment (->> (:attachments application-verdict-given)
                                             (util/find-first #(= (:type %) {:type-group "muut"
                                                                             :type-id    "keskustelu"})))]
                comments-attachment => truthy
                (:applicationState comments-attachment) => "closed"
                (get-in comments-attachment [:latestVersion :user :id]) => (:id usr/batchrun-user-data)))

            (fact "constructionStarted state is not updated, since fetched state is before current state"
              (:state application-construction) => "inUse")) => truthy

          ;; Review query is made only for applications in eligible state -> xml found for verdict given application
          (provided (krysp-reader/rakval-application-xml anything anything (contains [application-id-verdict-given application-id-construction] :in-any-order) :application-id anything)
                    => (build-multi-app-xml [[{:lp-tunnus application-id-verdict-given} {:pvm "2016-06-08Z" :tila "luvalla ei loppukatselmusehtoa, lupa valmis"}]
                                             [{:lp-tunnus application-id-construction} {:pvm "2016-06-07Z" :tila "rakennustyöt aloitettu"}]])))))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (facts "ETL export notices review updates"
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [app-id (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "etl 1"))]
        (against-background [(krysp-reader/rakval-application-xml anything anything [app-id] :application-id anything)
                             => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                                    (ss/replace #"LP-186-2014-90009" app-id)
                                    (sxml/parse-string "utf-8")
                                    test-system/fix-urls)
                             (#'lupapalvelu.review/validate-changed-tasks anything anything anything) => nil]
          (facts "initialize app for review fetching"
            (local-command sonja :update-app-bulletin-op-description :id app-id :description "otsikko") => ok?
            (local-command sonja :approve-application :id app-id :lang "fi") => ok?
            (give-local-legacy-verdict sonja app-id :R true :kuntalupatunnus "verdict") => some?)
          ;; Access db directly since defexport has no local fn and the timestamp is not included in queries
          (let [->reporting-ts   #(:reportingDbOverrideTs (mongo/by-id :applications % [:reportingDbOverrideTs]))
                old-ts           (now)
                old-review-count (count-reviews sonja app-id)]
            (fact "application has no ETL timestamp"
              (->reporting-ts app-id) => nil?)
            (fact "fetch verdict reviews"
              (batchrun/poll-verdicts-for-reviews (test-system/get-common-components)) => nil?)
            (fact "app has new reviews"
              (> (count-reviews sonja app-id) old-review-count) => true)
            (let [reporting-ts (some-> app-id ->reporting-ts)]
              (fact "batchrun has added ETL timestamp"
                (some-> reporting-ts (> old-ts)) => true)
              (fact "fetch verdict reviews again"
                (batchrun/poll-verdicts-for-reviews (test-system/get-common-components)) => nil?)
              (fact "batchrun ETL timestamp not updated because there were no changes"
                (->reporting-ts app-id) => reporting-ts))))))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (facts "Imported review PDF generation"
    (with-redefs [lupapalvelu.review/validate-changed-tasks (constantly nil)]
      (let [parsed-xml     (-> "resources/krysp/dev/r-verdict-review.xml"
                               slurp
                               (sxml/parse-string "utf-8")
                               test-system/fix-urls)
            application    (create-and-submit-local-application sonja :propertyId sipoo-property-id
                                                                :address "Katselmuskuja 18")
            application-id (:id application)
            batchrun-user  (usr/batchrun-user (map :id (batchrun/orgs-for-review-fetch)))
            read-result    (review/read-reviews-from-xml batchrun-user (now) application parsed-xml)]
        (fact "give verdict"
          (give-local-legacy-verdict sonja (:id application) :R true) => string?)
        (fact "read verdict"
          read-result => ok?)
        (fact "tasks have created timestamp"
          (:added-tasks-with-updated-buildings read-result) => (has every? :created))
        (let [application                       (domain/get-application-no-access-checking application-id)
              only-use-inspection-from-backend? (-> application :organization org/get-organization :only-use-inspection-from-backend)]
          (fact "do side effecting save"
            (review/save-review-updates (assoc (action/application->command application)
                                               :user batchrun-user
                                               :system (test-system/get-common-components))
                                        (:updates read-result)
                                        (:added-tasks-with-updated-buildings read-result)
                                        (:attachments-by-ids read-result)
                                        only-use-inspection-from-backend?)
            => {:ok true}
            (provided
              (sade.http/get "http://localhost:8001/dev/sample-attachment.txt" :as :stream :throw-exceptions false :conn-timeout 10000)
              => {:status 200
                  :body   (io/input-stream (io/resource "public/dev/sample-attachment.txt"))}))
          (let [updated-application     (domain/get-application-no-access-checking application-id)
                task-attachments        (->> (:attachments updated-application)
                                             (filter (fn [{:keys [target]}]
                                                       (= "task" (:type target)))))
                task-attachment         (->> task-attachments
                                             (util/find-first #(-> % :type :type-id (= "aloituskokouksen_poytakirja"))))
                task-attachment-file-id (att/attachment-latest-file-id updated-application (:id task-attachment))
                target                  (:target task-attachment)]
            (fact "count of attachment equal to"
              (count task-attachments) => (count (:added-tasks-with-updated-buildings read-result)))
            (fact "type"
              (:type task-attachment) => {:type-group "katselmukset_ja_tarkastukset"
                                          :type-id    "aloituskokouksen_poytakirja"})
            (fact "target"
              target => (just {:id      (-> task-attachment :target :id)
                               :type    "task"
                               :urlHash #"[a-f0-9]+"})
              (:id target) =not=> (:urlHash target)
              (:id task-attachment) => truthy
              (:id task-attachment) =not=> (:urlHash target))
            (facts "modified but not sent"
              (:modified task-attachment) => pos?
              (:sent task-attachment) => nil)
            (with-open [^Closeable content-fios ((:content (storage/download updated-application task-attachment-file-id)))]
              (fact "File exists"
                content-fios => truthy))))

        (facts "LPK-4857 New review attachment appears later"
          (let [application            (domain/get-application-no-access-checking application-id)
                task-attachments       (->> (:attachments application)
                                            (filter (fn [{:keys [target]}]
                                                      (= "task" (:type target)))))
                attachments-and-hashes (->> task-attachments
                                            (mapv (juxt (comp :type-id :type)
                                                        (comp :urlHash :target))))]
            (fact "starting situation"
              attachments-and-hashes
              => '(["aloituskokouksen_poytakirja" "0efa56e908e36a42f71a30c2017d09eb9445abb8"]
                   ["katselmuksen_tai_tarkastuksen_poytakirja" nil]
                   ["katselmuksen_tai_tarkastuksen_poytakirja" nil]
                   ["aloituskokouksen_poytakirja" nil]))

            (review/save-review-updates
              (assoc (action/application->command application)
                     :user batchrun-user
                     :system (test-system/get-common-components)
                     :created (now))
              {$set {:modified (now)}} ; putting only {} will naturally clear whole application :)
              []
              (-> (select-keys (:attachments-by-ids read-result) ["fefefefefefefefefefefefefefefa"])
                  (update "fefefefefefefefefefefefefefefa"
                          (fn [atts]
                            (->> atts
                                 (map
                                   #(assoc-in % [:liite :linkkiliitteeseen]
                                              (str (server-address) "/dev/sample-verdict.pdf")))))))
              true)


            (fact "after review-updates, new attachment is added"
              (let [app                (domain/get-application-no-access-checking application-id)
                    url-hash           (pandect/sha1 (str (server-address) "/dev/sample-verdict.pdf"))
                    attachments-after  (->> (:attachments app)
                                            (filter (fn [{:keys [target]}]
                                                      (= "task" (:type target))))
                                            (mapv (juxt (comp :type-id :type)
                                                        (comp :urlHash :target))))
                    url-hash-task-type (-<>> (:attachments app)
                                             (map :target)
                                             (util/find-by-key :urlHash url-hash)
                                             :id
                                             (util/find-by-id <> (:tasks app))
                                             :data :katselmuksenLaji :value)]
                url-hash-task-type => "aloituskokous"
                attachments-after => (conj attachments-and-hashes
                                           ["aloituskokouksen_poytakirja" url-hash])

                (facts "try again with original data, no changes should exist"
                  (review/save-review-updates
                    (assoc (action/application->command application)
                           :user batchrun-user
                           :system (test-system/get-common-components)
                           :created (now))
                    {$set {:modified (now)}} ; putting only {} will naturally clear whole application :)
                    []
                    (:attachments-by-ids read-result)
                    true)

                  (let [attachments-after2 (->> (domain/get-application-no-access-checking application-id)
                                                :attachments
                                                (filter (fn [{:keys [target]}]
                                                          (= "task" (:type target))))
                                                (mapv (juxt (comp :type-id :type)
                                                            (comp :urlHash :target))))]
                    (fact "attachments same as in previous step"
                      attachments-after2 => attachments-after)))))))))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (facts "Automatic checking for reviews - overwriting existing reviews"
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [application-id (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "submitted 16"))
            _              (mongo/update-by-id :applications application-id {$set {:created 1292112000000}})] ;;hack to test validate-changed-tasks
        (local-command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
        (facts "Initial state of applications before krysp reading is sane"
          (fact "approve app" (local-command sonja :approve-application :id application-id :lang "fi") => ok?)
          (fact "give verdict for app" (give-local-legacy-verdict sonja application-id :R true
                                                                  :kuntalupatunnus "verdict-vg"))

          (let [application (query-application local-query sonja application-id)]
            application => truthy

            (fact "verdictGiven"
              (:state application) => "verdictGiven"))
          => truthy)

        (fact "first batchrun"

          (let [_           (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                application (query-application local-query sonja application-id)]
            application => truthy
            (fact "application reviews contain the one that will be changed"
              (->> application :tasks (util/find-first #(= (-> % :data :katselmus :pitoPvm :value)
                                                           "29.09.2014"))
                   :data :katselmus :huomautukset :kuvaus :value) => (contains "aloituskokouksessa")))
          => truthy

          (provided (krysp-reader/rakval-application-xml anything anything [application-id] :application-id anything)
                    => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                           (ss/replace #"LP-186-2014-90009" application-id)
                           (sxml/parse-string "utf-8")
                           test-system/fix-urls)))

        (fact "second batchrun, no overwrite"

          (let [_           (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                application (query-application local-query sonja application-id)]
            application => truthy

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
                           (sxml/parse-string "utf-8")
                           test-system/fix-urls)))

        (fact "second batchrun, overwrite with the same data"
          (let [_           (batchrun/poll-verdicts-for-reviews (test-system/get-common-components) :overwrite-background-reviews? true)
                application (query-application local-query sonja application-id)]
            application => truthy

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
                           (sxml/parse-string "utf-8")
                           test-system/fix-urls)))

        (fact "third batchrun, overwrite with differing data"

          (let [application-before-rewrite (query-application local-query sonja application-id)
                old-task                   (->> application-before-rewrite :tasks
                                                (util/find-first #(= (-> % :data :katselmus :pitoPvm :value)
                                                                     "29.09.2014")))
                _                          (batchrun/poll-verdicts-for-reviews (test-system/get-common-components) :overwrite-background-reviews? true)
                application                (query-application local-query sonja application-id)
                overwritten-task           (->> application :tasks
                                                (util/find-first #(= (:id %)
                                                                     (:id old-task))))
                overwriting-task           (->> application :tasks
                                                (util/find-first #(and (not= (:state %)
                                                                             "faulty_review_task")
                                                                       (= (-> % :data :katselmus :pitoPvm :value)
                                                                          "29.09.2014"))))]

            application => ok?
            (fact "query ok"
              application-before-rewrite => truthy)
            (fact "one of the application reviews is updated"
              (->> overwriting-task :data :katselmus :huomautukset :kuvaus :value)
              => (contains "aloituskoKKouksessa"))          ;; <- NOTE!

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
                           (sxml/parse-string "utf-8")
                           test-system/fix-urls)))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (facts "pht for apartments get updated when fetching reviews"
    (mongo/remove-many :applications {})
    (against-background [(coordinate/convert anything anything anything anything) => nil]
      (let [application-id          (:id (create-local-app sonja :propertyId sipoo-property-id :address "submitted 16"))
            _                       (mongo/update-by-id :applications application-id {$set {:created 1292112000000}}) ;;so validation validate-changed-tasks
            _                       (local-command sonja :add-operation :id application-id :operation "pientalo")
            application             (query-application local-query sonja application-id)
            kerrostalo-doc          (domain/get-document-by-name application "uusiRakennus")
            kerrostalo-doc-id       (:id kerrostalo-doc)
            kerrostalo-operation-id (get-in kerrostalo-doc [:schema-info :op :id])
            pientalo-doc            (-> (domain/get-documents-by-name application "uusiRakennus") last)
            pientalo-doc-id         (:id pientalo-doc)
            pientalo-operation-id   (get-in pientalo-doc [:schema-info :op :id])]

        (facts "Update apartments for both operations"
          (local-command sonja :update-doc :id application-id :doc kerrostalo-doc-id :collection "documents"
                         :updates [["huoneistot.0.jakokirjain", "a"]
                                   ["huoneistot.0.huoneistonumero", "153"]
                                   ["huoneistot.0.porras", "b"]
                                   ["huoneistot.1.jakokirjain", "c"]
                                   ["huoneistot.1.huoneistonumero", "634"]
                                   ["huoneistot.1.porras", "d"]
                                   ["huoneistot.2.jakokirjain", "c"]
                                   ["huoneistot.2.huoneistonumero", "644"]
                                   ["huoneistot.2.porras", "d"]]) => ok?

          (local-command sonja :update-doc :id application-id :doc pientalo-doc-id :collection "documents"
                         :updates [["huoneistot.0.jakokirjain", "a"]
                                   ["huoneistot.0.huoneistonumero", "111"]
                                   ["huoneistot.0.porras", "b"]
                                   ["huoneistot.1.huoneistonumero", "222"]
                                   ["huoneistot.2.jakokirjain", "c"]
                                   ["huoneistot.2.huoneistonumero", "333"]
                                   ["huoneistot.2.porras", "d"]]) => ok?)

        (local-command sonja :submit-application :id application-id)
        (local-command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
        (facts "Initial state of applications before krysp reading is sane"
          (fact "approve app" (local-command sonja :approve-application :id application-id :lang "fi") => ok?)
          (fact "give verdict for app" (give-local-legacy-verdict sonja application-id :R true
                                                                  :kuntalupatunnus "verdict-vg"))
          (let [application (query-application local-query sonja application-id)]
            application => truthy
            (fact "verdictGiven"
              (:state application) => "verdictGiven"))
          => truthy)

        (fact "batchrun updates phts (pysyvat huoneistotunnukset) for apartments"
          (let [_                     (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
                application           (query-application local-query sonja application-id)
                kerrostalo-apartments (->> application
                                           :documents
                                           (util/find-by-id kerrostalo-doc-id)
                                           :data
                                           :huoneistot)
                pientalo-apartments   (->> application
                                           :documents
                                           (util/find-by-id pientalo-doc-id)
                                           :data
                                           :huoneistot)]

            (facts "pht updates for kerrostalo apartments"
              (fact ":0 apartment"
                (-> kerrostalo-apartments :0 :pysyvaHuoneistotunnus :value) => "1234567890")
              (fact ":1 apartment"
                (-> kerrostalo-apartments :1 :pysyvaHuoneistotunnus :value) => "3141")
              (fact ":2 apartment is not updated because building data from background (xml) does not have matching apartment"
                (-> kerrostalo-apartments :2 :pysyvaHuoneistotunnus :value) => ""))

            (facts "pht updates for pientalo apartments"
              (fact ":0 apartment"
                (-> pientalo-apartments :0 :pysyvaHuoneistotunnus :value) => "555")
              (fact ":1 apartment"
                (-> pientalo-apartments :1 :pysyvaHuoneistotunnus :value) => "777")
              (fact ":2 apartment is not updated because building data from background (xml) does not have matching apartment"
                (-> pientalo-apartments :2 :pysyvaHuoneistotunnus :value) => ""))

            (facts "pht updates for first building"
              (->> application :buildings first :apartments
                   (mapv #(select-keys % [:huoneistonumero :porras :jakokirjain :pysyvaHuoneistotunnus])))
              => [{:huoneistonumero       "153"
                   :jakokirjain           "a"
                   :porras                "b"
                   :pysyvaHuoneistotunnus "1234567890"}

                  {:huoneistonumero       "634"
                   :jakokirjain           "c"
                   :porras                "d"
                   :pysyvaHuoneistotunnus "3141"}])

            (facts "pht updates for second building"
              (->> application :buildings second :apartments
                   (mapv #(select-keys % [:huoneistonumero :porras :jakokirjain :pysyvaHuoneistotunnus])))
              => [{:huoneistonumero       "222"
                   :jakokirjain           nil
                   :porras                nil
                   :pysyvaHuoneistotunnus "777"}

                  {:huoneistonumero       "111"
                   :jakokirjain           "a"
                   :porras                "b"
                   :pysyvaHuoneistotunnus "555"}]))
          => truthy
          (provided (krysp-reader/rakval-application-xml anything anything [application-id] :application-id anything)
                    => (-> (slurp "resources/krysp/dev/verdict-2.2.4.xml")
                           (ss/replace #"LP-186-2013-00002" application-id)
                           (ss/replace #"589012" kerrostalo-operation-id)
                           (ss/replace #"3141592" pientalo-operation-id)
                           (sxml/parse-string "utf-8")
                           test-system/fix-urls)))))))
