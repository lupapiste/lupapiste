(ns lupapalvelu.autom-check-verdict-attachments-itest
  (:require [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.util :as util]))

(defn check-verdict-attachments []
  (-> (http-get (str (server-address) "/dev/batchrun-invoke?batchrun=check-verdict-attachments") {})
      decode-response
      :body))

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

(apply-remote-minimal)
(mongo/with-db test-db-name
  (mount/start #'mongo/connection)
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db test-db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(facts "Updating application's verdict attachments"
  ;; Create new application
  (let [application (create-and-submit-application mikko :propertyId sipoo-property-id :address "Paatoskuja 17")
        app-id      (:id application)
        timestamp   (now)]

    (fact "new application doesn't have verdicts"
      (:verdicts application) => empty?)

    ;; Fetch a verdict without verdict attachment
    (fact "verdict without attachments is fetched"
      (override-krysp-xml sipoo "753-R" :R [{:selector [:yht:liite] :value ""}]) => ok?
      (command sonja :check-for-verdict :id app-id) => ok?
      (remove-krysp-xml-overrides sipoo "753-R" :R) => ok?)

    (let [application (query-application sonja app-id)]

      (fact "verdicts do not have urlHashes"
        (application-verdict-url-hashes application) => (has every? nil?))

      (let [batchrun-result     (check-verdict-attachments)
            updated-application (query-application sonja app-id)
            url-hashes          (->> (application-verdict-url-hashes updated-application)
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
              => (has every? true?))

            (fact "Fetched timestamps are current, modified timestampls are taken from KuntaGML"
              (let [[{m1 :modified f1 :fetched}
                     {m2 :modified f2 :fetched}] verdict-attachments]
                f1 => f2
                (>= f1 timestamp) => true
                (map date/finnish-datetime [m1 m2])
                => (just "1.9.2013 12.00" "2.9.2013 12.00"
                         :in-any-order)))))

        (fact ":modified timestamp has not changed"
          (:modified application) => (:modified updated-application))

        (fact "comments are added for the attachments"
          (count (:comments updated-application)) => 2
          (map (comp :id :target) (:comments updated-application)) => (has every? (set url-hashes)))

        (fact "besides comments, verdicts and attachments, the application has not been updated"
          (dissoc application :attachments :comments :verdicts)
          => (dissoc updated-application :attachments :comments :verdicts))

        (fact "running fetch-verdict-attachments again does not alter the application"
          (let [new-batchrun-result     (check-verdict-attachments)
                non-updated-application (query-application sonja app-id)]
            (:updated-applications new-batchrun-result) => empty?
            updated-application => non-updated-application))))))
