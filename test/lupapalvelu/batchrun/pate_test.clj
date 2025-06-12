(ns lupapalvelu.batchrun.pate-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.batchrun.pate :as pate]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$in]]
            [sade.core :refer [fail!]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.validators :as v])
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(def runeberg (date/zoned-date-time "5.2.2026"))

(defn duration [h m s]
  (->> (date/with-time runeberg h m s)
       (Duration/between runeberg)
       pate/duration->str))

(deftest duration-test
  (testing "duration->str"
    (is (= "12h 34m 56s" (duration 12 34 56)))
    (is (nil? (duration 0 0 0)))
    (is (= "12h" (duration 12 0 0)))
    (is (= "34m" (duration 0 34 0)))
    (is (= "56s" (duration 0 0 56)))
    (is (= "12h 34m" (duration 12 34 0)))
    (is (= "12h 56s" (duration 12 0 56)))
    (is (= "34m 56s" (duration 0 34 56))))
  (testing "runtime-string"
    (is (= "0s" (pate/runtime-string (date/now))))
    (is (= "1m" (pate/runtime-string (date/minus (date/now) :minute))))))

(def field-query #'pate/field-query)

(deftest field-query-test
  (testing "field-query"
    (is (nil? (field-query :foo nil)))
    (is (nil? (field-query :foo [])))
    (is (= {:foo "bar"} (field-query :foo ["bar"])))
    (is (= {:foo {$in ["bar" "baz"]}}
           (field-query :foo ["bar" "baz"])))))


(def application-id?  #'v/lp-application-id?)
(def organization-id? #'pate/organization-id?)
(def parse-args!      #'pate/parse-args!)

(deftest parse-args-test
  (testing "application-id?"
    (is (true? (application-id? "LP-123-2024-98765")))
    (is (false? (application-id? "LX-123-2024-98765")))
    (is (false? (application-id? nil)))
    (is (false? (application-id? "  ")))
    (is (false? (application-id? "753-R"))))
  (testing "organization-id?"
    (is (false? (organization-id? "LP-123-2024-98765")))
    (is (false? (organization-id? "LX-123-2024-98765")))
    (is (false? (organization-id? nil)))
    (is (false? (organization-id? "  ")))
    (is (true? (organization-id? "753-R")))
    (is (true? (organization-id? "Weird enough, right?"))))
  (with-redefs [mongo/select (fn [coll q proj]
                               {:pre [(= [:applications [:organization]]
                                         [coll proj])]}
                               (let [v   (:_id q)
                                     ids (if (string? v)
                                           [v]
                                           (get v $in))]
                                 (keep #(let [muni (second (ss/split % #"-"))]
                                          (when-not (= "000" muni)
                                            {:organization (str muni "-ORG")
                                             :id           %}))
                                       ids)))]
    (testing "parse-args!"
      (is (empty? (parse-args! [])))
      (is (empty? (parse-args! [nil "" " "])))
      (is (= [{:organization-id "123-R"}]
             (parse-args! ["123-R"])))
      (is (= [{:organization-id "123-R"}
              {:organization-id "345-YA"}
              {:organization-id "678-Q"}]
             (parse-args! [" " " 123-R " nil "345-ya" "  678-q  "])))
      (is (thrown-with-msg? Exception #"Bad arguments."
                            (parse-args! ["LX-123-2024-98765"])))
      (is (thrown-with-msg? Exception #"Bad arguments."
                            (parse-args! ["LP-123-2024-98765" "753-R"])))
      (is (= [{:application-ids ["LP-123-2024-98765" "LP-123-2023-11223"]
               :organization-id "123-ORG"}
              {:application-ids ["LP-313-2000-00008"]
               :organization-id "313-ORG"}]
             (parse-args! ["LP-123-2024-98765" "lp-313-2000-00008" "lp-123-2023-11223"])))
      (is (thrown-with-msg? Exception #"Applications not found"
                            (parse-args! ["lp-000-2000-00001"])))
      (is (= [{:application-ids ["LP-123-2024-98765"]
               :organization-id "123-ORG"}
              {:application-ids ["LP-313-2000-00008"]
               :organization-id "313-ORG"}]
             (parse-args! ["LP-123-2024-98765" "lp-313-2000-00008"
                           "LP-000-2023-11223"]))))))

(def integration-error? #'lupapalvelu.batchrun.pate/integration-error?)

(defn make-exception [boom-fn]
  (try
    (boom-fn)
    (catch Exception e
      e)))

(deftest integration-error-test
  (testing "no"
    (is (false? (integration-error? nil)))
    (is (false? (integration-error? (make-exception #(/ 1 0)))))
    (is (false? (integration-error? (make-exception #(fail! "error.hiihoo")))))
    (is (false? (integration-error? (make-exception #(fail! :error.integration))))))
  (testing "yes"
    (is (true? (integration-error? (make-exception #(throw (ex-info "hello" {:text "error.integration.boom"}))))))
    (is (true? (integration-error? (make-exception #(fail! "error.integration.http")))))
    (is (true? (integration-error? (make-exception #(fail! :error.integration.connection)))))))
