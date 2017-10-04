(ns lupapalvelu.autom-check-verdict-attachments-itest
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [midje.sweet :refer :all]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [now]]
            [sade.util :as util]))

(defonce db-name (str "test_autom-check-verdict-attachments-itest_" (now)))

(defn- application-verdict-url-hashes [application]
  (->> application
       :verdicts
       (mapcat :paatokset)
       (mapcat :poytakirjat)
       (map :urlHash)))

(defn- verdict-has-url-hash? [application verdict-id url-hash]
  (boolean ((->> (util/find-first #(= (:id %) verdict-id)
                                  (:verdicts application))
                 :paatokset
                 (mapcat :poytakirjat)
                 (map :urlHash)
                 set)
            url-hash)))

(defn- attachment-targets-correct-verdict? [application attachment]
  (verdict-has-url-hash? application
                         (-> attachment :target :id)
                         (-> attachment :target :urlHash)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(facts "Updating application's verdict attachments"
       (mongo/with-db db-name
         (mongo/remove-many :applications {})

         ;; Create new application
         (let [application (create-and-submit-local-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
               app-id (:id application)]

           (fact "new application doesn't have verdicts"
             (:verdicts application) => empty?)

           ;; Fetch a verdict without verdict attachment
           (fact "verdict without attachments is fetched"
             (override-krysp-xml sipoo "753-R" :R [{:selector [:yht:liite] :value ""}] :test-db-name db-name) => ok?
             (local-command sonja :check-for-verdict :id app-id) => ok?
             (remove-krysp-xml-overrides sipoo "753-R" :R :test-db-name db-name) => ok?)

           (let [application (query-application local-query sonja app-id)]

             (fact "verdicts do not have urlHashes"
               (application-verdict-url-hashes application)=> (has every? nil?))

             (let [batchrun-result (batchrun/fetch-verdict-attachments (-> 3 t/months t/ago c/to-long)
                                                                       (now)
                                                                       [])
                   updated-application (query-application local-query sonja app-id)
                   url-hashes (->> (application-verdict-url-hashes updated-application)
                                   (filter string?))]


               (fact "verdict attachment update is run successfully"
                 (:updated-applications batchrun-result) => (has every? ok?))

               (fact "bachrun created two verdict attachments"
                 (->> batchrun-result
                      :updated-applications
                      (map :updated-verdicts)
                      (mapcat vals)
                      (mapcat :paatokset)
                      (mapcat :poytakirjat)
                      (map :urlHash)
                      count)
                 => 2)

               (fact "the verdicts of the application contain two url hashes"
                 (count url-hashes) => 2)

               (facts "verdict attachments"
                 (let [verdict-attachments (->> (:attachments updated-application)
                                               (filter #((set url-hashes) (:id %))))]

                   (fact "attachments are read-only"
                     (map :readOnly verdict-attachments) => (has every? true?))

                   (fact "the url hashes correspond to attachments in the application, and attachments target correct verdict"
                     (count verdict-attachments) => 2
                     (map (partial attachment-targets-correct-verdict? updated-application)
                          verdict-attachments)
                     => (has every? true?))))

               (fact ":modified timestamp has not changed"
                 (:modified application) => (:modified updated-application))

               (fact "comments are added for the attachments"
                 (count (:comments updated-application)) => 2
                 (map (comp :id :target) (:comments updated-application)) => (has every? (set url-hashes)))

               (fact "besides comments, verdicts and attachments, the application has not been updated"
                 (dissoc application :attachments :comments :verdicts)
                 => (dissoc updated-application :attachments :comments :verdicts))

               (fact "running fetch-verdict-attachments again does not alter the application"
                 (let [new-batchrun-result (batchrun/fetch-verdict-attachments (-> 3 t/months t/ago c/to-long)
                                                                               (now)
                                                                               [])
                       non-updated-application (query-application local-query sonja app-id)]
                   (:updated-applications new-batchrun-result) => empty?
                   updated-application => non-updated-application)))))))
