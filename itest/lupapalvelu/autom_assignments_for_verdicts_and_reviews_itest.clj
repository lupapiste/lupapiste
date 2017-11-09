(ns lupapalvelu.autom-assignments-for-verdicts-and-reviews-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.batchrun :as batchrun]
            [sade.xml :as sxml]
            [sade.strings :as ss]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]))

(def db-name (str "autom-assignments-for-verdicts-and-reviews-itest_" (now)))3

(defn check-verdict-attachments []
  (-> (http-get (str (server-address) "/dev/batchrun-invoke?batchrun=check-verdict-attachments") {})
      decode-response
      :body))

(defn- slingshot-exception [ex-map]
  (-> (slingshot.support/make-context ex-map (str "throw+: " ex-map) nil (slingshot.support/stack-trace))
      (slingshot.support/wrap)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(defn get-assignments []
  (mongo/select :assignments {}))

(def review-assignment-trigger
  (organization/create-trigger
    nil
    ["katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja"]
    {:id   "abba1111111111111111acdc"
     :name {:fi "Käsittelijä" :sv "Handläggare" :en "Handler"}} "review-test-trigger"))

(def verdict-assignment-trigger
  (organization/create-trigger
    nil
    ["paatoksenteko.paatos"]
    {:id "abba1111111111111111acdc"
     :name {:fi "Käsittelijä" :sv "Handläggare" :en "Handler"}} "verdict-test-trigger"))

(mongo/with-db db-name
  (mongo/remove-many :assignments {})
  (mongo/remove-many :applications {})
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)
        assignment-triggers ()
        organizations (map
                       (fn [org] (if (= "753-R" (:id org))
                                   (update-in org [:assignment-triggers] conj review-assignment-trigger verdict-assignment-trigger)
                                   org))
                       organizations)]
   (dorun (map (partial mongo/insert :organizations) organizations))))

(facts "Verdict attachments trigger automatic assignments"
  (mongo/with-db test-db-name
    (mongo/remove-many :assignments {})
    (mongo/remove-many :applications {})
    (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 18")
          app-id (:id application)
          assignments (get-assignments)]

      (fact "verdict trigger ok"
            (->> (mongo/by-id :organizations "753-R")
                 :assignment-triggers
                 ((fn [c] (println c) c))
                 (filter #(= "verdict-test-trigger" (:description %)))
                 ((fn [c] (println c) c))
                 (count))
            => 1)

      (fact "no assignments"
            (count (map :id assignments)) => 0)

      (fact "verdict without attachments is fetched"
        (override-krysp-xml sipoo "753-R" :R [{:selector [:yht:liite] :value ""}]) => ok?
        (command sonja :check-for-verdict :id app-id) => ok?
        (remove-krysp-xml-overrides sipoo "753-R" :R) => ok?)

      (let [batchrun-result (check-verdict-attachments)
            _ (println batchrun-result)
            assignments (get-assignments)]
        (fact "one attachment creates one assignment"
              (count (map :id assignments)) => 1)

        (fact "new attachment created new assignment"
              (-> assignments (first) :application :id) => app-id)

        (fact "assignment came from the correct trigger"
              (:trigger (first assignments)) => (:id verdict-assignment-trigger))))))
(comment
(facts "Automatic checking for reviews trigger automatic assignments"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (mongo/remove-many :assignments {})

      (let [application-id-submitted (:id (create-and-submit-local-application pena :propertyId sipoo-property-id :address "Hakemusjätettie 15"))]

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
              (let [poll-result (batchrun/poll-verdicts-for-reviews)
                    assignments (get-assignments)]

                (fact "attachments trigger assignments"
                      (count (map :id (get-assignments))) => 1)

                (fact "assignment is not user-created"
                      (-> (get-assignments) (first) :trigger) => (:id review-assignment-trigger))) => truthy

              (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                        => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                               (ss/replace #"LP-186-2014-90009" application-id-submitted)
                               (sxml/parse-string "utf-8"))))

        (fact "batchrun does not change assignments if there is no changed attachments"
              (let [old-assignments (get-assignments)
                    poll-result (batchrun/poll-verdicts-for-reviews)
                    assignments (get-assignments)]

                (fact "no new assignments"
                      (count (map :id (get-assignments))) => 1)

                (fact "assignments do not change"
                      (first old-assignments) => (first assignments))) => truthy

              (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                        => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                               (ss/replace #"LP-186-2014-90009" application-id-submitted)
                               (sxml/parse-string "utf-8"))))

        (fact "new assignment with updated attachment when old assignment is completed"
              (let [old-assignments (get-assignments)
                    _ (complete-assignment sonja (:id application-id-submitted))
                    poll-result (batchrun/poll-verdicts-for-reviews)
                    assignments (get-assignments)]

                (fact "no new assignments"
                      (count (map :id assignments)) => 1)

                (fact "batchrun does not create new assignments if attachment has completed assignments"
                      (= (-> old-assignments :targets (first) :id) (-> assignments :targets (first) :id)) => true)) => truthy

              (provided (krysp-reader/rakval-application-xml anything anything [application-id-submitted] :application-id anything)
                        => (-> (slurp "resources/krysp/dev/r-verdict-review.xml")
                               (ss/replace #"LP-186-2014-90009" application-id-submitted)
                               (ss/replace #"<rakval:pitoPvm>2011-11-11Z</rakval:pitoPvm>"
                                           " <rakval:pitoPvm>2011-11-12Z</rakval:pitoPvm>")
                               (sxml/parse-string "utf-8"))))))))