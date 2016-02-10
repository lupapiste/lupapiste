(ns lupapalvelu.migration.attachment-signature-fileid-test
  (:require [lupapalvelu.migration.migrations :refer [set-signature-fileId
                                                      add-fileId-for-signatures]]
            [lupapalvelu.attachment :refer [Attachment Signature Version VersionNumber]]
            [schema.core :as sc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test :refer [is]]
            [sade.schemas :refer [Timestamp]]
            [sade.schema-generators :as ssg]))

(def SignatureWithOutFileId (dissoc Signature :fileId))

(def static-version-generator (gen/elements [{:major 2 :minor 0}]))
(def not-static-version-generator (gen/such-that #(not= {:major 2 :minor 0} %) (ssg/generator VersionNumber)))

(def timestamp-before-generator (gen/elements [1449999999999]))
(def timestamp-after-generator (gen/elements  [1500000000000]))

(defspec set-signature 20
  (prop/for-all [versions1 (ssg/generator [Version] {VersionNumber not-static-version-generator})
                 matching-version (ssg/generator Version {VersionNumber static-version-generator 
                                                          Timestamp timestamp-before-generator})
                 versions2 (ssg/generator [Version] {VersionNumber not-static-version-generator})
                 signature (ssg/generator SignatureWithOutFileId {VersionNumber static-version-generator 
                                                                  Timestamp timestamp-after-generator})
                 file-id   ssg/object-id]
                (let [versions (concat versions1 [(assoc matching-version :fileId file-id)] versions2)
                      updated (set-signature-fileId versions signature)]
                  (is (= (:fileId updated) file-id) "File id not updated properly")
                  (is (nil? (sc/check Signature updated)) "Signature validation error"))))

(defspec add-fileId-no-signature 20
  (prop/for-all [attachment (ssg/generator Attachment)]
                (let [updated (-> attachment (assoc :signatures []) add-fileId-for-signatures)]
                  (is (->> updated :signatures empty?) "Signatures empty")
                  (is (->> updated (sc/check Attachment) nil?)) "Valid attachemnt")))

(defspec add-fileId-attachment-with-signature 20
  (prop/for-all [attachment (ssg/generator Attachment)
                 versions+signatures 
                 (gen/bind (gen/set (ssg/generator VersionNumber))
                           (fn [vns]
                             (gen/fmap
                              (fn [[v s fid]] [(map #(assoc %1 :version %2 :created 0 :fileId %3) v vns fid)
                                               (map #(assoc %1 :version %2 :created 1) s vns)])
                              (gen/tuple (gen/vector (ssg/generator Version) (count vns))
                                         (gen/vector (ssg/generator SignatureWithOutFileId) 0 (count vns))
                                         (gen/vector ssg/object-id (count vns))))))]
                (let [versions   (first versions+signatures)
                      signatures (second versions+signatures)
                      updated (-> attachment (assoc :versions versions :signatures signatures) add-fileId-for-signatures)]
                  (is (= (count (second versions+signatures)) (count (:signatures updated))) "Some signature missing from updated attachment")
                  (is (every? (comp (set (map :fileId versions)) :fileId) (:signatures updated)) "No mathing fileId for all signatures")
                  (is (->> updated (sc/check Attachment) nil?) "Invalid attachement"))))

