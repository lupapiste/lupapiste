(ns lupapalvelu.conversion.import.description-i-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.import.description]
            [lupapalvelu.conversion.import.shared :refer [import--items]]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.conversion.import.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

(use-fixtures :once (local-fixture-factory :db-name "test_conversion-import-description-itest"))

(defn make-cfg [& kvs]
  (->> (partition 2 kvs)
       (map #(hash-map :backend-id (first %) :text (second %)))
       (assoc {:organization-id "753-R" :op :descriptions} :items)
       (sc/validate conv-sc/ImportDescriptionsConfig)))

(defn find-doc [app-or-id]
  (->> (util/pcond->> app-or-id
         string? (mongo/by-id :applications))
       :documents
       (util/find-first #(= "hankkeen-kuvaus"
                        (get-in % [:schema-info :subtype])))))

(defn described?
  ([app-id text]
   (let [a               (mongo/by-id :applications app-id)
         {:keys [modified
                 value]} (->> (find-doc a) :data :kuvaus)]
     (if (ss/blank? text)
       (and (ss/blank? value) (nil? modified))
       (and (= text value) (pos? modified)
            (util/find-by-key :note "Import description" (:_sheriff-notes a)))))))

(defn untouched? [app-id]
  (not (mongo/any? :applications {:_id            app-id
                                  :_sheriff-notes {$exists true}})))

(defn mark-converted [app-id]
  (mongo/update-by-query :conversion {:LP-id app-id}
                         {$set {:converted true}}))

(deftest import-descriptions-test
  (let [[app-id1 app-id2 app-id3
         app-id4] (for [i (range 4)]
                    (->> (str "Description " (inc i))
                         (create-and-open-application pena :address)
                         :id))]
    (testing "No updates"
      (import--items (make-cfg "hello" "world"))
      (make-conversion-document app-id1 "hello" false)
      (is (false? (mongo/any? :applications {:_sheriff-notes {$exists true}}))))
    (testing "Update one"
      (mark-converted app-id1)
      (import--items (make-cfg "hello" "world"))
      (is (described? app-id1 "world")))
    (testing "Reupdate does nothing"
      (let [a (mongo/by-id :applications app-id1)]
        (import--items (make-cfg "hello" "world"))
        (is (= a (mongo/by-id :applications app-id1)))))
    (testing "No update, text already in the description"
      (is (:ok (command pena :update-doc :id app-id2
                        :doc (:id (find-doc app-id2))
                        :updates [["kuvaus" "ÄläMöLÖ and friends"]])))
      (make-conversion-document app-id2 "foobar" true)
      (import--items (make-cfg "foobar" " Älämölö "))
      (is (untouched? app-id2)))
    (testing "Update multiple"
      (make-conversion-document app-id3 "yeah" true)
      (make-conversion-document app-id4 "howdy" true)
      (is (:ok (command pena :update-doc :id app-id4
                        :doc (:id (find-doc app-id4))
                        :updates [["kuvaus" "  Lorem ipsum  "]])))
      (import--items (make-cfg "yeah" " One " "hello" "Two" "howdy" " Three "))
      (is (described? app-id3 "One"))
      (is (described? app-id4 "Lorem ipsum\n\nThree"))
      (is (described? app-id1 "world\n\nTwo")))))
