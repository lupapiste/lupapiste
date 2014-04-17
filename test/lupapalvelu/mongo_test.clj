(ns lupapalvelu.mongo-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [monger.collection :as mc]
            [monger.gridfs :as gfs]
            [lupapalvelu.mongo :as mongo]))

(def valid-id "502770568de2282ae6fbb0be")
(def invalid-id "123")

(facts "Facts about create-id"
  (fact (mongo/create-id) => string?))

(facts "Facts about with-_id"
  (fact (mongo/with-_id nil) => nil)
  (fact (mongo/with-_id {:data "data"}) => {:data "data"})
  (fact (mongo/with-_id {:id "foo" :data "data"}) => {:_id "foo" :data "data"}))

(facts "Facts about with-id"
  (fact (mongo/with-id nil) => nil)
  (fact (mongo/with-id {:data "data"}) => {:data "data"})
  (fact (mongo/with-id {:_id "foo" :data "data"}) => {:id "foo" :data "data"}))

(facts "Facts about insert"
  (fact (mongo/insert "c" {:id "foo" :data "data"}) => nil
        (provided
          (mc/insert "c" {:_id "foo" :data "data"}) => nil))
  (fact (mongo/insert "c" {:data "data"}) => nil
        (provided
          (mc/insert "c" {:data "data"}) => nil)))

(facts "delete-file"
  (mongo/delete-file ...query...) => nil
    (provided (gfs/remove ...query...) => nil)
  (mongo/delete-file-by-id ...id...) => nil
    (provided (gfs/remove {:_id ...id...}) => nil))
