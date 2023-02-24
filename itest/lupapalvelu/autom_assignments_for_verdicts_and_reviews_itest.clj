(ns lupapalvelu.autom-assignments-for-verdicts-and-reviews-itest
  (:require [artemis-server :as artemis-server]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [lupapalvelu.automatic-assignment.schemas :refer [Filter]]
            [lupapalvelu.backing-system.krysp.reader :as krysp-reader]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.batchrun.fetch-verdict]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.permit :as permit]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [ring.util.response :as resp]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [schema.core :as sc]))

(def db-name (str "test_autom-assignments-for-verd-and-rev-itest_" (now)))

(defn get-assignments []
  (mongo/select :assignments {}))

(def verdict-assignment-trigger
  (sc/validate Filter {:id (mongo/create-id)
                       :name "verdict-test-trigger"
                       :rank 0
                       :modified 12345
                       :criteria {:attachment-types ["paatoksenteko.paatosote"]}
                       :target {:handler-role-id sipoo-general-handler-id}}))

(def review-assignment-trigger
  (sc/validate Filter {:id       (mongo/create-id)
                       :name     "review-test-trigger"
                       :rank     0
                       :modified 12345
                       :criteria {:attachment-types ["katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja"]}
                       :target   {:handler-role-id sipoo-general-handler-id}}))

(defn sample-response []
  (-> (io/resource "public/dev/sample-attachment.txt")
      (io/input-stream)
      (resp/response)
      (resp/header "content-length" 7)
      (resp/header "content-disposition" "filename=foo.txt")))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :assignments {})
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications  {})
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)
        organizations (map (fn [org] (if (= "753-R" (:id org))
                                       (update-in org [:automatic-assignment-filters] conj verdict-assignment-trigger review-assignment-trigger)
                                       org))
                           organizations)]
   (dorun (map (partial mongo/insert :organizations) organizations))))

(facts "Verdict attachments trigger automatic assignments"
  (mongo/with-db db-name
    (mongo/remove-many :assignments {})
    (mongo/remove-many :applications {})
      (let [application (create-and-submit-local-application mikko :propertyId sipoo-property-id :address "Paatoskuja 18")
           app-id (:id application)
           assignments (get-assignments)]

        (fact "verdict filter ok"
          (let [sipoo                     (mongo/by-id :organizations "753-R")
                sipoo-triggers            (:automatic-assignment-filters sipoo)
                verdict-filters (filter #(= "verdict-test-trigger" (:name %)) sipoo-triggers)]
            (count verdict-filters)) => 1)

        (fact "no assignments"
          (count assignments) => 0)

        (fact "application sent"
          (mongo/update-by-id :applications app-id {"$set" {:state "sent"}})
          (:state (query-application local-query sonja app-id)) => "sent")

        (fact "batchrun creates assignments"
          (let [_ (fetch-verdicts)
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
                (sade.http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
                => (sample-response))))))

(facts "Automatic checking for reviews trigger automatic assignments"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (mongo/remove-many :assignments {})

      (let [application-id-submitted (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "Hakemusj\u00e4tettie 15"))]

        (fact "review filter ok"
          (->> (mongo/by-id :organizations "753-R")
               :automatic-assignment-filters
               (filter #(= "review-test-trigger" (:name %)))
               (count))
          => 1)

        (fact "initially zero assignments"
          (count (get-assignments)) => 0)

        (fact "verdict to application"
          (give-local-legacy-verdict sonja application-id-submitted))

        (fact "first batchrun creates assignments"
          (against-background
            (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id-submitted)
                   (sxml/parse-string "utf-8"))
            (#'lupapalvelu.review/validate-changed-tasks anything anything) => nil
            (sade.http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
            => (sample-response))
          (let [_ (batchrun/poll-verdicts-for-reviews)
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
            (sade.http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
            => (sample-response))
          (let [old-assignments (get-assignments)
                _ (batchrun/poll-verdicts-for-reviews)
                assignments (get-assignments)]

            (fact "no new assignments"
              (count old-assignments) => (count assignments))

            (fact "all assignments are valid"
              assignments => (has every? (partial sc/validate lupapalvelu.assignment/Assignment)))

            (fact "assignments do not change"
              (first old-assignments) => (first assignments)))))))
