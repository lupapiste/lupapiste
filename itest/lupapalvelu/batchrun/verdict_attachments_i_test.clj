(ns lupapalvelu.batchrun.verdict-attachments-i-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.batchrun.fetch-verdict :refer [fetch-verdict-attachments]]
            [lupapalvelu.conversion.itest-util :as conv-test]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn test-xml []
  (conv-test/read-xml "dev-resources/krysp/verdict-r.xml"))

(defn verdict-attachment-fetched
  "Returns the fetched timestamp or nil."
  [app-id]
  (let [{:keys [attachments verdicts]} (mongo/by-id :applications app-id)]
    (when-let [url-hash (-> verdicts first :paatokset first
                            :poytakirjat first :urlHash)]
      (->> attachments
           (util/find-first #(= (:target %)
                                {:type    "verdict"
                                 :urlHash url-hash
                                 :id      (-> verdicts first :id)}))
           :fetched))))

(use-fixtures :once
  (local-fixture-factory (-> {:fixture "minimal"}
                             test-system/with-remote-components))
  (fn [f]
    (mongo/update-by-id :organizations "837-R" {$unset {:dmcity true}})
    (with-redefs [permit/fetch-xml-from-krysp (fn [& _args]
                                                (test-xml))]
      (f))))

(deftest fetch-verdict-attachments-test
  (let [{app-id :id} (create-and-submit-local-application pena)
        _            (local-command sonja :check-for-verdict :id app-id)
        paatospvm    (-> (mongo/by-id :applications app-id [:verdicts])
                         :verdicts first :paatokset first
                         :poytakirjat first :paatospvm)
        unfetch      #(mongo/update-by-id :applications app-id
                                          {$set   {:attachments []}
                                           $unset {:verdicts.0.paatokset.0.poytakirjat.0.urlHash true}})
        system       {}]

    (testing "Initial verdict is OK"
      (is (and paatospvm (pos? paatospvm)))
      (let [ts (verdict-attachment-fetched app-id)]
        (is (and ts (pos? ts)))))

    (testing "Remove the verdict attachment"
      (unfetch)
      (is (nil? (verdict-attachment-fetched app-id))))

    (testing "Fetch by id: no match"
      (fetch-verdict-attachments system {:application-ids ["LP-123-2024-00001"]})
      (is (nil? (verdict-attachment-fetched app-id))))

    (testing "Fetch by id: match"
      (fetch-verdict-attachments system {:application-ids [app-id]})
      (let [ts (verdict-attachment-fetched app-id)]
        (is (and ts (pos? ts)))
        (testing "Refetch does nothing"
          (fetch-verdict-attachments system {:application-ids [app-id]})
          (is (= ts (verdict-attachment-fetched app-id))))))

    (testing "Fetch by time range: no match"
      (unfetch)
      (fetch-verdict-attachments system {:start (date/timestamp "1.9.2024")
                                         :end   (date/timestamp "5.9.2024")})
      (fetch-verdict-attachments system {:start         (date/timestamp (date/minus (date/now) :day))
                                         :end           (date/timestamp (date/plus (date/now) :day))
                                         :organizations ["123-R"]})
      (is (nil? (verdict-attachment-fetched app-id))))

    (testing "Fetch by time range: match"
      (fetch-verdict-attachments system {:start (date/timestamp (date/minus (date/now) :day))
                                         :end   (date/timestamp (date/plus (date/now) :day))})
      (let [ts (verdict-attachment-fetched app-id)]
        (is (and ts (pos? ts)))
        (testing "Refetch does nothing"
          (fetch-verdict-attachments system {:start (date/timestamp (date/minus (date/now) :day))
                                             :end   (date/timestamp (date/plus (date/now) :day))})
          (is (= ts (verdict-attachment-fetched app-id))))))

    (testing "paatospvm equality granularity is the same day"
      (unfetch)
      (is (and paatospvm
               (zero? (.getHour (date/zoned-date-time paatospvm)))))
      (when paatospvm
        (mongo/update-by-id :applications app-id
                            {$set {:verdicts.0.paatokset.0.poytakirjat.0.paatospvm
                                   (-> paatospvm date/end-of-day date/timestamp)}}))
      (fetch-verdict-attachments system {:application-ids [app-id]})
      (let [ts (verdict-attachment-fetched app-id)]
        (is (and ts (pos? ts)))))))
