(ns lupapalvelu.admin.import-export.operations-attachment-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.admin.import-export.operations-attachment :as oa]
            [sade.schema-generators :as ssg]
            [schema.core :as sc :refer [defschema]]))

(deftest merge-test
  (is (= {:a [["a" "a"]
              ["a2" "a2"]]
          :b [["b" "b"]]
          :c [["c" "c"]]} (oa/deep-merge
                            {:a [["a" "a"]]
                             :b [["b" "b"]]}
                            {:a [["a2" "a2"]]
                             :c [["c" "c"]]}))))

;; Use sc/enum instead of sc/Keyword et al. to limit the search space and get collisions
(defschema MockOperation (sc/enum :kerrostalo-rivitalo
                                  :vapaa-ajan-asuinrakennus
                                  :poikkeamis
                                  :meluilmoitus))

(defschema MockTypeGroup (sc/enum "paapiirustus"
                                  "hakija"
                                  "muut"))

(defschema MockTypeId (sc/enum "asemapiirros"
                               "pohjapiirustus"
                               "muu"))

(defschema MockAttachment
  [(sc/one MockTypeGroup 'type-group)
   (sc/one MockTypeId 'type-id)])

(defschema MockAttachments [MockAttachment])

(defschema OperationsAttachment
  {MockOperation MockAttachments})

(defn- dedupe-attachments
  "`MockAttachments` shouldn't contain duplicates"
  [attachments-gen]
  (gen/fmap
    (fn [attachments] (->> attachments distinct (into [])))
    attachments-gen))

(def oa-gen (ssg/generator OperationsAttachment
                           {}
                           {MockAttachments dedupe-attachments}))

(defspec merge-with-empty-is-identity 50
  (prop/for-all [oa oa-gen]
    (and (= oa (oa/deep-merge nil oa))
         (= oa (oa/deep-merge oa nil))
         (= oa (oa/deep-merge {} oa))
         (= oa (oa/deep-merge oa {})))))

(defspec merge-with-self-is-identity 50
  (prop/for-all [oa oa-gen]
    (and (= oa (oa/deep-merge oa oa))
         ;; Drop one operation -> should not affect result
         (= oa (oa/deep-merge oa (->> oa
                                      (drop 1)
                                      (into {}))))
         ;; Drop one attachment -> should not affect result
         (= oa (oa/deep-merge oa (->> oa
                                      (map (fn [[op attachments]]
                                             [op (vec (drop 1 attachments))]))
                                      (into {})))))))

(defspec all-operations-are-retained 50
  (prop/for-all [oa-1 oa-gen
                 oa-2 oa-gen]
    (let [m (oa/deep-merge oa-1 oa-2)]
      ;; Result contains all operations from both inputs
      (= (set (keys m))
         (set/union
           (set (keys oa-1))
           (set (keys oa-2)))))))

(defspec all-attachments-are-retained 50
  (prop/for-all [oa-1 oa-gen
                 oa-2 oa-gen]
    (let [m (oa/deep-merge oa-1 oa-2)]
      (every?
        (fn [op]
          ;; Result contains all attachments from both inputs
          (= (set (get m op))
             (set/union (set (get oa-1 op))
                        (set (get oa-2 op)))))
        (keys m)))))
