(ns lupapalvelu.conversion.import.note-i-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.import.itest-util :refer :all]
            [lupapalvelu.conversion.import.note]
            [lupapalvelu.conversion.import.shared :refer [import--items]]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(use-fixtures :once (local-fixture-factory :db-name "test_conversion-import-note-itest"))

(defn make-cfg [& kvs]
  (->> (partition 2 kvs)
       (map #(hash-map :backend-id (first %) :text (second %)))
       (assoc {:organization-id "753-R" :op :notes} :items)
       (sc/validate conv-sc/ImportNotesConfig)))

(defn noted?
  ([app-id text]
   (let [{notice :authorityNotice ts :authorityNoticeEdited
          :as a} (mongo/by-id :applications app-id)]
     (if (ss/blank? text)
       (and (ss/blank? notice) (nil? ts))
       (and (= text notice) (pos? ts)
            (util/find-by-key :note "Import note" (:_sheriff-notes a)))))))

(defn add-backend-id [app-id bid]
  (mongo/update-by-id :applications app-id
                      {$set {:verdicts [{:kuntalupatunnus bid}]}}))

(defn untouched? [app-id]
  (not (mongo/any? :applications {:_id            app-id
                                  :_sheriff-notes {$exists true}})))

(deftest import-notes-test
  (let [[app-id1 app-id2 app-id3
         app-id4] (for [i (range 4)]
                    (->> (str "Notice " (inc i))
                         (create-and-open-application pena :address)
                         :id))]
    (testing "No updates"
      (import--items (make-cfg "hello" "world"))
      (is (false? (mongo/any? :applications {:_sheriff-notes {$exists true}}))))
    (testing "Update one"
      (add-backend-id app-id1 "hello")
      (import--items (make-cfg "hello" "world"))
      (is (noted? app-id1 "world")))
    (testing "Reupdate does nothing"
      (let [a (mongo/by-id :applications app-id1)]
        (import--items (make-cfg "hello" "world"))
        (is (= a (mongo/by-id :applications app-id1)))))
    (testing "No update, text already noted"
      (is (:ok (command sonja :add-authority-notice :id app-id2
                        :authorityNotice "ÄläMöLÖ and friends")))
      (add-backend-id app-id2 "foobar")
      (import--items (make-cfg "foobar" " Älämölö "))
      (is (untouched? app-id2)))
    (testing "Update multiple"
      (add-backend-id app-id3 "yeah")
      (add-backend-id app-id4 "yeah")
      (is (:ok (command sonja :add-authority-notice :id app-id4
                        :authorityNotice "  Lorem ipsum  ")))
      (import--items (make-cfg "yeah" " One " "hello" "Two"))
      (is (noted? app-id3 "One"))
      (is (noted? app-id4 "Lorem ipsum\n\nOne"))
      (is (noted? app-id1 "world\n\nTwo")))))
