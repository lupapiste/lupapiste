(ns lupapalvelu.migration.neighbor-attachment-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.migration.neighbor-attachment :as neat]
            [monger.operators :refer :all]))

(defn make-neighbor [id & states]
  {:id id
   :status (map #(hash-map :state %) states)})

(defn make-attachment
  [type-id & [source-type source-id]]
  (cond-> {:type {:type-group "foo"
                  :type-id    type-id}}
    source-type
    (assoc :source {:type source-type
                    :id   source-id})))

(defn make-neighbor-attachment [neighbor-id comments?]
  (make-attachment (if comments?
                     "naapurin_huomautus"
                     "naapurin_kuuleminen")
                   "neighbors"
                   neighbor-id))

(defn make-app [neighbors attachments]
  {:neighbors   neighbors
   :attachments attachments})

(deftest neighbor-attachment-fix
  (let [n1        (make-neighbor "one" "open")
        n2        (make-neighbor "two" "open" "email-sent")
        n3        (make-neighbor "three" "open" "response-given-ok"
                                 "rewound")
        n-silent1 (make-neighbor "silent1" "response-given-ok")
        n-silent2 (make-neighbor "silent2" "open" "email-sent"
                                 "response-given-ok")
        a1        (make-attachment "foobar")
        a2        (make-attachment "naapurin_huomautus")
        a3        (make-neighbor-attachment "bar" true)
        a4        (make-neighbor-attachment "one" true)
        a5        (make-neighbor-attachment "two" true)
        a6        (make-neighbor-attachment "silent1" false)
        a7        (make-attachment "naapurin_huomautus" "foobar" "silent1")
        a8        (make-neighbor-attachment "silent2" true)
        a9        (make-neighbor-attachment "silent1" true)]
    (testing "fix-maps"
      (is (nil? (neat/no-comments-fix (make-app [n1 n2 n3 n-silent1 n-silent2]
                                                [a1 a2 a3 a4 a5 a6 a7]))))
      (is (= (neat/no-comments-fix (make-app [n1 n2 n3 n-silent1 n-silent2]
                                             [a1 a2 a3 a4 a5 a6 a7 a8]))
             {$set {:attachments.7.type.type-id :naapurin_kuuleminen}}))
      (is (nil? (neat/no-comments-fix (make-app [n1 n2 n3 n-silent1]
                                             [a1 a2 a3 a4 a5 a6 a7 a8]))))
      (is (= (neat/no-comments-fix (make-app [n1 n2 n3 n-silent2 n-silent1]
                                             [a1 a8 a2 a9 a3 a8 a9 a4]))
             {$set {:attachments.1.type.type-id :naapurin_kuuleminen
                    :attachments.3.type.type-id :naapurin_kuuleminen
                    :attachments.5.type.type-id :naapurin_kuuleminen
                    :attachments.6.type.type-id :naapurin_kuuleminen}})))))
