(ns lupapalvelu.autom-assignments-for-verdicts-and-reviews-itest
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is use-fixtures]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.automatic-assignment.schemas :as schemas]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.batchrun.fetch-verdict]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer [fill-verdict]]
            [lupapalvelu.pate-legacy-itest-util :refer [give-local-legacy-verdict]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.system.util-components :as components]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set]]
            [ring.util.response :as resp]
            [sade.core :refer [now]]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [schema.core :as sc]))

(def valid-assignment! (sc/validator assignment/Assignment))

(defn- get-assignments []
  (mongo/select :assignments {}))

(def verdict-assignment-trigger
  (schemas/valid-filter! {:id       (mongo/create-id)
                          :name     "verdict-test-trigger"
                          :rank     0
                          :modified 12345
                          :criteria {:attachment-types ["paatoksenteko.paatosote"]}
                          :target   {:handler-role-id sipoo-general-handler-id}}))

(def review-assignment-trigger
  (schemas/valid-filter! {:id       (mongo/create-id)
                          :name     "review-test-trigger"
                          :rank     0
                          :modified 12345
                          :criteria {:attachment-types ["katselmukset_ja_tarkastukset.aloituskokouksen_poytakirja"]}
                          :target   {:handler-role-id sipoo-general-handler-id}}))

(defn- sample-response []
  (-> (io/resource "public/dev/sample-attachment.txt")
      (io/input-stream)
      (resp/response)
      (resp/header "content-length" 7)
      (resp/header "content-disposition" "filename=foo.txt")))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (mongo/remove-many :assignments {})
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications  {})
  (let [krysp-url     (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) (minimal/organizations {:http-port (-> (test-system/get-common-components)
                                                                                                                                        components/system->http-port)}))
        organizations (map (fn [org] (if (= "753-R" (:id org))
                                       (update-in org [:automatic-assignment-filters] conj verdict-assignment-trigger review-assignment-trigger)
                                       org))
                           organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations)))

  (facts "Verdict attachments trigger automatic assignments"
    (mongo/remove-many :assignments {})
    (mongo/remove-many :applications {})
    (let [application (create-and-submit-local-application mikko :propertyId sipoo-property-id :address "Paatoskuja 18")
          app-id      (:id application)
          assignments (get-assignments)]

      (fact "verdict filter ok"
        (let [sipoo           (mongo/by-id :organizations "753-R")
              sipoo-triggers  (:automatic-assignment-filters sipoo)
              verdict-filters (filter #(= "verdict-test-trigger" (:name %)) sipoo-triggers)]
          (count verdict-filters)) => 1)

      (fact "no assignments"
        (count assignments) => 0)

      (fact "application sent"
        (mongo/update-by-id :applications app-id {"$set" {:state "sent"}})
        (:state (query-application local-query sonja app-id)) => "sent")

      (fact "batchrun creates assignments"
        (let [_           (fetch-verdicts)
              assignments (get-assignments)]
          (fact "one attachment creates one assignment"
            (count assignments) => 1)

          (fact "new attachment created new assignment"
            (-> assignments (first) :application :id) => app-id)

          (fact "assignment came from the correct filter"
            (:filter-id (first assignments)) => (:id verdict-assignment-trigger))) => truthy

        (provided (permit/fetch-xml-from-krysp "R" anything anything anything anything anything)
                  => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                         (ss/replace #"LP-186-2014-90009" app-id)
                         (sxml/parse-string "utf-8"))
                  (http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
                  => (sample-response)))))

  (facts "Automatic checking for reviews trigger automatic assignments"
    (mongo/remove-many :applications {})
    (mongo/remove-many :assignments {})

    (let [application-id-submitted (:id (create-and-submit-local-application pena
                                                                             :propertyId sipoo-property-id
                                                                             :address "Testitie 15"))]

      (fact "review filter ok"
        (->> (mongo/by-id :organizations "753-R")
             :automatic-assignment-filters
             (filter #(= "review-test-trigger" (:name %)))
             (count))
        => 1)

      (fact "initially zero assignments"
        (count (get-assignments)) => 0)

      (fact "verdict to application"
        (give-local-legacy-verdict sonja application-id-submitted :R true))

      (fact "first batchrun creates assignments"
        (against-background
            (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
          => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                 (ss/replace #"LP-186-2014-90009" application-id-submitted)
                 (sxml/parse-string "utf-8"))
          (#'review/validate-changed-tasks anything anything anything) => nil
          (http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
          => (sample-response))
        (let [_           (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
              assignments (get-assignments)]

          (fact "attachments trigger assignments"
            (count assignments) => 1)

          (fact "assignment is not user-created"
            (-> (get-assignments) (first) :filter-id) => (:id review-assignment-trigger))))

      (fact "batchrun does not change assignments if there are no changed attachments"
        (against-background
            (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
          => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                 (ss/replace #"LP-186-2014-90009" application-id-submitted)
                 (sxml/parse-string "utf-8"))
          (http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
          => (sample-response))
        (let [old-assignments (get-assignments)
              _               (batchrun/poll-verdicts-for-reviews (test-system/get-common-components))
              assignments     (get-assignments)]

          (fact "no new assignments"
            (count old-assignments) => (count assignments))

          (fact "all assignments are valid"
            assignments => (has every? valid-assignment!))

          (fact "assignments do not change"
            (first old-assignments) => (first assignments)))))))


(use-fixtures :each (itest/local-fixture-factory (-> {:fixture "pate-verdict"}
                                                     test-system/with-mock-laundry)))

(def pate-verdict-assignment-trigger
  (schemas/valid-filter! {:id       (mongo/create-id)
                          :name     "verdict-test-trigger"
                          :rank     0
                          :modified 12345
                          :criteria {:attachment-types ["paatoksenteko.paatos"]}
                          :target   {:handler-role-id sipoo-general-handler-id}}))

(def pate-app-at-time-of-verdict-assignment-trigger
  (schemas/valid-filter! {:id       (mongo/create-id)
                          :name     "app-at-time-of-verdict-test-trigger"
                          :rank     1
                          :modified 12345
                          :criteria {:attachment-types ["muut.paatoksen_aikaiset_hankkeen_tiedot"]}
                          :target   {:handler-role-id sipoo-general-handler-id}}))

(deftest automatic-assignment-pate-test
  ;; Install both filters — both are triggered by a single verdict publish
  (is (= 1
         (mongo/update-by-query :organizations
                                {:_id "753-R"}
                                {$set {:automatic-assignment-filters [pate-verdict-assignment-trigger
                                                                      pate-app-at-time-of-verdict-assignment-trigger]}})))
  ;; Create & submit application
  (let [{app-id :id} (itest/create-and-submit-application pena
                                                          :propertyId sipoo-property-id
                                                          :address "Testitie 15")]
    (is app-id)
    ;; Assign sonja as general handler
    (is (true?
          (:ok (command sonja :upsert-application-handler {:id     app-id
                                                           :userId sonja-id
                                                           :roleId sipoo-general-handler-id}))))
    ;; Create a verdict template
    (let [template-id             "ba7aff3e5266a1d9c1581666"
          {:keys [ok verdict-id]} (command sonja :new-pate-verdict-draft
                                           :id app-id
                                           :template-id template-id)]
      (is ok)
      (is (string? verdict-id))
      (fill-verdict sonja app-id verdict-id
                    :verdict-code "hyvaksytty"
                    :verdict-text "Verdict given"
                    :verdict-date (now)
                    :automatic-verdict-dates true
                    :giver "päättäjä"
                    :bulletin-op-description "Changed pate bulletin description"
                    :giver-first-name "Gi"
                    :giver-last-name "Ver")

      ;; Publish verdict -> creates verdict PDF and app-at-time-of-verdict PDF -> triggers both filters
      (is (= {:ok true}
             (command sonja :publish-pate-verdict {:id         app-id
                                                   :verdict-id verdict-id})))

      (let [expected-assignment {:application {:address      "Testitie 15"
                                               :id           "LP-753-2026-90001"
                                               :municipality "753"
                                               :organization "753-R"}
                                 :id          string?
                                 :modified    int?
                                 :recipient   {:firstName "Sonja"
                                               :handlerId string?
                                               :id        sonja-id
                                               :lastName  "Sibbo"
                                               :role      "authority"
                                               :roleId    sipoo-general-handler-id
                                               :username  "sonja"}
                                 :states      [{:timestamp int?
                                                :type      "created"
                                                :user      {:firstName "Sonja"
                                                            :id        sonja-id
                                                            :lastName  "Sibbo"
                                                            :role      "authority"
                                                            :username  "sonja"}}]
                                 :status      "active"
                                 :targets     [{:group     "attachments"
                                                :id        string?
                                                :timestamp int?}]
                                 :trigger     string?}]
        (is (match? (m/in-any-order
                      [(assoc expected-assignment
                              :description "verdict-test-trigger"
                              :filter-id   (:id pate-verdict-assignment-trigger))
                       (assoc expected-assignment
                              :description "app-at-time-of-verdict-test-trigger"
                              :filter-id   (:id pate-app-at-time-of-verdict-assignment-trigger))])
                    (vec (get-assignments))))))))
