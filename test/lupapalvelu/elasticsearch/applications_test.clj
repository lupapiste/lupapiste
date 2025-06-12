(ns lupapalvelu.elasticsearch.applications-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.elasticsearch.applications :as es-applications]))

(deftest parse-address-test
  (testing "Returns nil for blank input"
    (is (nil? (es-applications/parse-address nil)))
    (is (nil? (es-applications/parse-address "")))
    (is (nil? (es-applications/parse-address "   "))))

  (testing "Splits a simple street address into codes and words"
    (is (= {:codes "5" :words "Testikatu"}
           (es-applications/parse-address "Testikatu 5"))))

  (testing "Short letter suffixes (1-2 chars) go to :codes, not :words"
    (is (= {:codes "12 B" :words "Mannerheimintie"}
           (es-applications/parse-address "Mannerheimintie 12 B"))))

  (testing "Number and letter codes are separateed"
    (is (= {:codes "12 B" :words "Mannerheimintie"}
           (es-applications/parse-address "Mannerheimintie 12B"))))

  (testing "Multiple numbers and a letter suffix are all placed in :codes"
    (is (= {:codes "15 23 A" :words "Aleksanterinkatu"}
           (es-applications/parse-address "Aleksanterinkatu 15 A 23"))))

  (testing "Multiple long words all appear in :words"
    (is (= {:codes "14" :words "Iso Roobertinkatu"}
           (es-applications/parse-address "Iso Roobertinkatu 14"))))

  (testing "Non-ASCII letters (e.g. Finnish ä/ö) are captured in :words but not in :codes"
    (is (= {:codes "5" :words "Hämeentie"}
           (es-applications/parse-address "Hämeentie 5 Ä"))))

  (testing "Address with only a number produces empty :words"
    (is (= {:codes "42" :words ""}
           (es-applications/parse-address "42"))))

  (testing "Address with only a long word produces empty :codes"
    (is (= {:codes "" :words "Mannerheimintie"}
           (es-applications/parse-address "Mannerheimintie")))))

(deftest followed-fields-changed-test
  (testing "Returns false for empty changed-fields"
    (is (false? (es-applications/followed-fields-changed? #{}))))

  (testing "Returns false for fields that are not followed"
    (is (false? (es-applications/followed-fields-changed? #{:some-unrelated-field})))
    (is (false? (es-applications/followed-fields-changed? #{:not-a-thing :also-not-a-thing}))))

  (testing "Returns true for a direct shared field"
    (is (true? (es-applications/followed-fields-changed? #{:address})))
    (is (true? (es-applications/followed-fields-changed? #{:state})))
    (is (true? (es-applications/followed-fields-changed? #{:municipality})))
    (is (true? (es-applications/followed-fields-changed? #{:organization}))))

  (testing "Returns true for followed top-level fields (not in source-fields)"
    (is (true? (es-applications/followed-fields-changed? #{:_stats})))
    (is (true? (es-applications/followed-fields-changed? #{:attachments})))
    (is (true? (es-applications/followed-fields-changed? #{:auth})))
    (is (true? (es-applications/followed-fields-changed? #{:comments})))
    (is (true? (es-applications/followed-fields-changed? #{:handlers})))
    (is (true? (es-applications/followed-fields-changed? #{:pate-verdicts})))
    (is (true? (es-applications/followed-fields-changed? #{:statements})))
    (is (true? (es-applications/followed-fields-changed? #{:verdicts}))))

  (testing "Returns true for nested path fields (deep paths from shared-fields)"
    (is (true? (es-applications/followed-fields-changed? #{:attachments.id})))
    (is (true? (es-applications/followed-fields-changed? #{:handlers.userId})))
    (is (true? (es-applications/followed-fields-changed? #{:verdicts.id})))
    (is (true? (es-applications/followed-fields-changed? #{:pate-verdicts.id})))
    (is (true? (es-applications/followed-fields-changed? #{:comments.created}))))

  (testing "Returns true for dynamic-key-map top-level fields via prefix matching"
    ;; Dynamic key maps like :_comments-seen-by.<some-id> should trigger update
    ;; The function extracts the first segment of the kw-path
    (is (true? (es-applications/followed-fields-changed? #{:_comments-seen-by})))
    (is (true? (es-applications/followed-fields-changed? #{:_statements-seen-by})))
    (is (true? (es-applications/followed-fields-changed? #{:_verdicts-seen-by}))))

  (testing "Returns true for dynamic-key-maps when changed as subpath"
    ;; e.g. :_comments-seen-by.some-user-id
    (is (true? (es-applications/followed-fields-changed? #{:_comments-seen-by.some-user-id})))
    (is (true? (es-applications/followed-fields-changed? #{:_statements-seen-by.some-user-id})))
    (is (true? (es-applications/followed-fields-changed? #{:_verdicts-seen-by.some-user-id}))))

  (testing "Returns true when at least one followed field is in the changed set"
    (is (true? (es-applications/followed-fields-changed? #{:not-followed :state})))
    (is (true? (es-applications/followed-fields-changed? #{:not-followed :attachments}))))

  (testing "Returns false when only unrelated fields are changed"
    (is (false? (es-applications/followed-fields-changed? #{:not-followed :also-not-followed})))))
