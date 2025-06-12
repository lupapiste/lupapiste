(ns lupapalvelu.mongo-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [monger.collection :as mc]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]))

(def test-db-name (str "test_" (now)))

(facts "Facts about create-id"
  (fact (mongo/create-id) => string?))

(facts "Facts about with-_id"
  (fact (mongo/with-_id nil) => nil)
  (fact (mongo/with-_id {}) => {})
  (fact (mongo/with-_id {:data "data"}) => {:data "data"})
  (fact (mongo/with-_id {:id nil :data "data"})
    => {:id nil :data "data"})
  (fact (mongo/with-_id {:id "foo" :data "data"}) => {:_id "foo" :data "data"})
  (fact (mongo/with-_id {:_id "foo" :data "data"}) => {:_id "foo" :data "data"})
  (fact (mongo/with-_id {:_id "foo" :id "bar" :data "data"})
    => {:_id "bar" :data "data"}))

(facts "Facts about with-id"
  (fact (mongo/with-id nil) => nil)
  (fact (mongo/with-id {}) => {})
  (fact (mongo/with-id {:data "data"}) => {:data "data"})
  (fact (mongo/with-id {:_id nil :data "data"})
    => {:_id nil :data "data"})
  (fact (mongo/with-id {:_id "foo" :data "data"}) => {:id "foo" :data "data"})
  (fact (mongo/with-id {:id "foo" :data "data"}) => {:id "foo" :data "data"})
  (fact (mongo/with-id {:id "foo" :_id "bar" :data "data"})
    => {:id "bar" :data "data"}))

(facts "Facts about insert"
  (fact (mongo/insert "c" {:id "foo" :data "data"}) => nil
        (provided
         (mongo/get-db) => anything
         (mc/insert anything "c" {:_id "foo" :data "data"}) => nil))
  (fact (mongo/insert "c" {:data "data"}) => nil
        (provided
         (mongo/get-db) => anything
         (mc/insert anything "c" {:data "data"}) => nil)))

(facts "valid key"
  (mongo/valid-key? (mongo/create-id)) => true
  (mongo/valid-key? (org.bson.types.ObjectId.)) => true
  (mongo/valid-key? nil) => false
  (mongo/valid-key? "") => false
  (mongo/valid-key? "\u0000") => false
  (mongo/valid-key? "$var") => false
  (mongo/valid-key? "path.path") => false)

(let [attachments [{:id 1 :versions [{:fileId "11"} {:fileId "21"}]}
                   {:id 2 :versions [{:fileId "12"} {:fileId "22"}]}
                   {:id 3 :versions [{:fileId "13"} {:fileId "23"}]}]]

  (facts "generate-array-updates"
    (mongo/generate-array-updates :attachments attachments #(= (:id %) 1) "foo" "bar") => {"attachments.0.foo" "bar"}
    (mongo/generate-array-updates :attachments attachments #(odd? (:id %)) "foo" "bar") => {"attachments.0.foo" "bar", "attachments.2.foo" "bar"}
    (mongo/generate-array-updates :attachments attachments #(= (:id %) 1) "foo" "bar" "quu" "quz") => {"attachments.0.foo" "bar" "attachments.0.quu" "quz"}))

(facts "remove-null-chars"
  (mongo/remove-null-chars nil) => nil
  (mongo/remove-null-chars "") => ""
  (mongo/remove-null-chars "\u0000") => ""
  (mongo/remove-null-chars [nil]) => [nil]
  (mongo/remove-null-chars [""]) => [""]
  (mongo/remove-null-chars {"" nil}) => {"" nil}
  (mongo/remove-null-chars {"$set" {:k "v\0"}}) => {"$set" {:k "v"}}
  (mongo/remove-null-chars {"$push" {:a {"$each" [nil "" "\0" "a\u0000"]}}}) => {"$push" {:a {"$each" [nil "" "" "a"]}}})

(against-background
  [(mongo/remove anything anything) => nil :times 0]
  (let [failed (throws #"Assert failed")
        bad-cast (throws ClassCastException)]
    (facts "remove-by-id"
      (mongo/remove-by-id :foo nil) => failed
      (mongo/remove-by-id :foo "  ") => failed
      (mongo/remove-by-id :foo 1) => bad-cast
      (mongo/remove-by-id :foo #".*") => bad-cast)))
