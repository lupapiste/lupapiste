(ns lupapalvelu.migration.applications-owner-to-writer-itest
  (:require [midje.sweet :refer :all]
            [clojure.test.check.generators :as gen]
            [schema.core :as sc]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]
            [sade.util :refer [fn-> fn->>]]
            [lupapalvelu.migration.migrations :as mig]
            [lupapalvelu.mongo :as mongo]))

(def id-sequence (atom 0))
(defn get-id [] (swap! id-sequence inc))

(def application-id-generator (gen/fmap (partial apply format "LP-%d%d%d-%d%d%d%d-%05d")
                                        (gen/fmap (fn-> (conj (get-id)))
                                                  (gen/vector ssg/single-number-int 7))))

(def Auth {:id   ssc/ObjectIdStr
           :role (sc/enum "writer" "reader")
           :firstName (sc/enum "name")
           :lastName (sc/enum "foo")
           :unsubscribe sc/Bool
           (sc/optional-key :type) (sc/enum "company")})

(defn generate-auth-array [owner-ind len]
  (-> (gen/sample (ssg/generator Auth) len)
      (vec)
      (assoc-in [owner-ind :role] "owner")
      (assoc-in [owner-ind :type] "owner")))

(defn generate-application [auth-owner-ind auth-len]
  {:_id  (-> (ssg/generate ssc/ApplicationId {ssc/ApplicationId application-id-generator}))
   :auth (-> (generate-auth-array auth-owner-ind auth-len))})

(def ApplicationWithoutOwner {:_id ssc/ApplicationId
                              :creator {:id        ssc/ObjectIdStr
                                        :firstName (sc/enum "name")
                                        :lastName (sc/enum "foo")}
                              :auth [{:id ssc/ObjectIdStr
                                      :role (sc/enum "writer" "reader")
                                      :firstName (sc/enum "name")
                                      :lastName (sc/enum "foo")
                                      :unsubscribe sc/Bool
                                      (sc/optional-key :type) (sc/enum "company")}]})

(mongo/connect!)

(def db-name (str "test_applications-owner-to-writer-itest_" (now)))

(def apps (mapv (partial generate-application 0) (range 1 200)))

(fact "owner as first auth"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (mongo/insert-batch :applications apps)
    (->> (mongo/select :applications {} [:auth])
         (run! (partial mig/update-application-owner-to-writer :applications)))

    (let [migrated-apps (mongo/find-maps :applications {})]
      (fact "all applications are valid after migration"
        (sc/check [ApplicationWithoutOwner] migrated-apps) => nil)

      (fact "owner is changed properly"
        (let [owner-entries (map (fn-> :auth first) migrated-apps)]

          (fact "only role type is changed"
            (map :role owner-entries) => (partial every? #{"writer"})
            (map :type owner-entries) => (partial every? nil?)
            (map (fn-> (dissoc :role :type)) owner-entries) =>  (map (fn-> :auth first (dissoc :role :type)) apps))))

      (fact "all the other auth entries are unchanged"
        (map (fn-> :auth rest) migrated-apps) => (map (fn-> :auth rest) apps))

      (fact "creator is added"
        (map :creator migrated-apps) => (map (fn-> :auth first (select-keys [:username :firstName :lastName :id])) apps)))))

(def apps2 (mapv (partial generate-application 5) (range 10 20)))

(fact "owner not first"
  (mongo/with-db db-name
    (mongo/remove-many :applications {})
    (mongo/insert-batch :applications apps2)
    (->> (mongo/select :applications {} [:auth])
         (run! (partial mig/update-application-owner-to-writer :applications) ))

    (let [migrated-apps (mongo/find-maps :applications {})]
      (fact "all applications are valid after migration"
        (sc/check [ApplicationWithoutOwner] migrated-apps) => nil)

      (fact "owner is changed properly"
        (let [owner-entries (map (fn-> :auth (nth 5)) migrated-apps)]

          (fact "only role type is changed"
            (map :role owner-entries) => (partial every? #{"writer"})
            (map :type owner-entries) => (partial every? nil?)
            (map (fn-> (dissoc :role :type)) owner-entries) =>  (map (fn-> :auth (nth 5) (dissoc :role :type)) apps2))))

      (fact "all the other auth entries before owner are unchanged"
        (map (fn->> :auth (take 5)) migrated-apps) => (map (fn->> :auth (take 5)) apps2))

      (fact "all the other auth entries after owner are unchanged"
        (map (fn->> :auth (drop 6)) migrated-apps) => (map (fn->> :auth (drop 6)) apps2))

      (fact "creator is added"
        (map :creator migrated-apps) => (map (fn-> :auth (nth 5) (select-keys [:username :firstName :lastName :id])) apps2)))))
