(ns lupapalvelu.autom-assignments-for-verdicts-and-reviews-itest
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set]]
            [ring.util.response :as resp]
            [schema.core :as sc]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.batchrun.fetch-verdict]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

(def db-name (str "test_autom-assignments-for-verd-and-rev-itest_" (now)))

(defn check-verdict-attachments []
  (-> (http-get (str (server-address) "/dev/batchrun-invoke?batchrun=check-verdict-attachments") {})
      decode-response
      :body))

(defn get-assignments []
  (mongo/select :assignments {}))

(def verdict-assignment-trigger
  (organization/create-trigger
    nil
    ["paatoksenteko.paatosote"]
    {:id "abba1111111111111111acdc"
     :name {:fi "K\u00e4sittelij\u00e4" :sv "Handl\u00e4ggare" :en "Handler"}} "verdict-test-trigger"))

(def review-assignment-trigger
  (organization/create-trigger
    nil
    ["katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja"]
    {:id "abba1111111111111111acdc"
     :name {:fi "K\u00e4sittelij\u00e4" :sv "Handl\u00e4ggare" :en "Handler"}} "review-test-trigger"))

(defn sample-response []
  (-> (io/resource "public/dev/sample-attachment.txt")
      (io/input-stream)
      (resp/response)
      (resp/header "content-length" 7)
      (resp/header "content-disposition" "filename=foo.txt")))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :assignments {})
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications  {})
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)
        organizations (map (fn [org] (if (= "753-R" (:id org))
                                       (update-in org [:assignment-triggers] conj verdict-assignment-trigger review-assignment-trigger)
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

        (fact "verdict trigger ok"
          (let [sipoo                     (mongo/by-id :organizations "753-R")
                sipoo-triggers            (:assignment-triggers sipoo)
                verdict-filtered-triggers (filter #(= "verdict-test-trigger" (:description %)) sipoo-triggers)]
           (count verdict-filtered-triggers))
          => 1)

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

            (fact "assignment came from the correct trigger"
                 (:trigger (first assignments)) => (:id verdict-assignment-trigger))) => truthy

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

        (fact "review trigger ok"
          (->> (mongo/by-id :organizations "753-R")
               :assignment-triggers
               (filter #(= "review-test-trigger" (:description %)))
               (count))
          => 1)

        (fact "initially zero assignments"
          (count (get-assignments)) => 0)

        (fact "verdict to application"
          (give-local-verdict sonja application-id-submitted :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?)

        (fact "first batchrun creates assignments"
          (against-background
            (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id-submitted)
                   (sxml/parse-string "utf-8"))
            (sade.http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
            => (sample-response))
          (let [poll-result (batchrun/poll-verdicts-for-reviews)
                assignments (get-assignments)]

            (fact "attachments trigger assignments"
              (count assignments) => 1)

            (fact "assignment is not user-created"
              (-> (get-assignments) (first) :trigger) => (:id review-assignment-trigger))))

        (fact "batchrun does not change assignments if there are no changed attachments"
          (against-background
            (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
            => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                   (ss/replace #"LP-186-2014-90009" application-id-submitted)
                   (sxml/parse-string "utf-8"))
            (sade.http/get anything :as :stream :throw-exceptions false :conn-timeout 10000)
            => (sample-response))
          (let [old-assignments (get-assignments)
                poll-result (batchrun/poll-verdicts-for-reviews)
                assignments (get-assignments)]

            (fact "no new assignments"
              (count old-assignments) => (count assignments))

            (fact "all assignments are valid"
              assignments => (has every? (partial sc/validate lupapalvelu.assignment/Assignment)))

            (fact "assignments do not change"
              (first old-assignments) => (first assignments)))))))
