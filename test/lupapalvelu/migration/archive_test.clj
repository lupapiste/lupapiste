(ns lupapalvelu.migration.archive-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.migration.archive :as archive]
            [monger.operators :refer [$set]]))

(defn att [tila]
  {:metadata {:tila tila}})

(deftest arkistoidaan->valmis-attachment-updates-test
  (testing "returns nil when there are no attachments"
    (is (nil? (archive/arkistoidaan->valmis-attachment-updates {})))
    (is (nil? (archive/arkistoidaan->valmis-attachment-updates {:attachments []}))))

  (testing "returns nil when no attachment has tila arkistoidaan"
    (is (nil? (archive/arkistoidaan->valmis-attachment-updates
                {:attachments [(att :valmis) (att :luonnos)]}))))

  (testing "returns nil when attachments have no metadata"
    (is (nil? (archive/arkistoidaan->valmis-attachment-updates
                {:attachments [{} {:metadata {}}]}))))

  (testing "single arkistoidaan attachment produces update at correct index"
    (is (= {$set {:attachments.0.metadata.tila :valmis}}
           (archive/arkistoidaan->valmis-attachment-updates
             {:attachments [(att :arkistoidaan)]}))))

  (testing "arkistoidaan as string is also matched"
    (is (= {$set {:attachments.0.metadata.tila :valmis}}
           (archive/arkistoidaan->valmis-attachment-updates
             {:attachments [(att "arkistoidaan")]}))))

  (testing "only arkistoidaan attachments are updated, index is preserved"
    (is (= {$set {:attachments.1.metadata.tila :valmis}}
           (archive/arkistoidaan->valmis-attachment-updates
             {:attachments [(att :valmis) (att :arkistoidaan)]}))))

  (testing "multiple arkistoidaan attachments all appear in the update"
    (is (= {$set {:attachments.0.metadata.tila :valmis
                  :attachments.2.metadata.tila :valmis}}
           (archive/arkistoidaan->valmis-attachment-updates
             {:attachments [(att :arkistoidaan) (att :luonnos) (att :arkistoidaan)]})))))
