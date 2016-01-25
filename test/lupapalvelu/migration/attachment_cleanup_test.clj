(ns lupapalvelu.migration.attachment-cleanup-test
  (:require [lupapalvelu.migration.migrations :refer [remove-unwanted-fields-from-attachment-auth]]
            [lupapalvelu.attachment :refer [Attachment AttachmentUser]]
            [schema.core :as sc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]
            [sade.schema-generators :as ssg]))

;; Define flawed schema for auth array.
(def ExtendedUserSummary (assoc AttachmentUser :email sc/Str :orgAuthz [{sc/Str sc/Str}]))
(def ExtendedAuth {:auth [ExtendedUserSummary]})

;; Use static attachment for performance reasons
(def attachment (ssg/generate Attachment))

(defspec update-auth-array 20 ;; num-tests has to be small enough. Too big value result in out of memory error!
  (prop/for-all [extended-auth (ssg/generator ExtendedAuth)]
                (let [extended-attachment (merge attachment extended-auth)
                      cleaned-attachment  (remove-unwanted-fields-from-attachment-auth extended-attachment)]
                  (and (nil? (sc/check Attachment cleaned-attachment))
                       (= (dissoc cleaned-attachment :auth) (dissoc attachment :auth))
                       (= (count (:auth extended-auth)) (count (:auth cleaned-attachment)))))))

