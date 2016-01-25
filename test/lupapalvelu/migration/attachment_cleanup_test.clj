(ns lupapalvelu.migration.attachment-cleanup-test
  (:require [lupapalvelu.migration.migrations :refer [remove-unwanted-fields-from-attachment-auth
                                                      merge-required-fields-into-attachment
                                                      applicationState-as-camelCase]]
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

(defspec complete-missing-attachment-fields 20 ;; num-tests has to be small enough. Too big value result in out of memory error!
  (prop/for-all [attachment     (ssg/generator Attachment)
                 missing-fields (ssg/generator [(apply sc/enum [:locked :applicationState :target :requestedByAuthority
                                                                 :notNeeded :op :signatures :auth])])]
                (let [failing-attachment   (apply dissoc attachment missing-fields)
                      completed-attachment (merge-required-fields-into-attachment failing-attachment)]
                  (and (nil? (sc/check Attachment completed-attachment))))))

(defspec rename-applicationState-as-camelCase 20 ;; num-tests has to be small enough. Too big value result in out of memory error!
  (prop/for-all [application-state (ssg/generator (sc/enum "draft" "complement-needed" "complementNeeded"))]
                (let [failing-attachment   (assoc attachment :applicationState application-state)
                      completed-attachment (applicationState-as-camelCase failing-attachment)]
                  (and (nil? (sc/check Attachment completed-attachment))
                       (= (dissoc completed-attachment :applicationState) (dissoc attachment :applicationState))))))

