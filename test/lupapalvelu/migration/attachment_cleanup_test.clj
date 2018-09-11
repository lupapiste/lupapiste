(ns lupapalvelu.migration.attachment-cleanup-test
  (:require [lupapalvelu.migration.migrations :refer [remove-unwanted-fields-from-attachment-auth
                                                      merge-required-fields-into-attachment
                                                      applicationState-as-camelCase
                                                      set-target-timestamps-as-nil
                                                      set-target-with-nil-valued-map-as-nil
                                                      set-verdict-id-for-nil-valued-verdict-targets]]
            [lupapalvelu.attachment :refer [Attachment Target]]
            [lupapalvelu.user :refer [SummaryUser]]
            [schema.core :as sc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

;; Define flawed schema for auth array.
(def ExtendedSummaryUser (sc/conditional
                          :orgAuthz     (assoc SummaryUser
                                               :role (sc/enum "uploader" "stamper")
                                               :orgAuthz [{sc/Str sc/Str}])
                          :username     (assoc SummaryUser
                                               :role (sc/enum "uploader" "stamper"))
                          :else         (sc/enum {:id        "-"
                                                  :firstName "Lupapiste"
                                                  :lastName  "Eraajo"
                                                  :role      "uploader"})))

;; Use static attachment for performance reasons
(def attachment (ssg/generate Attachment))
(def attachment-coercer (ssc/json-coercer Attachment))

(defspec update-auth-array 20 ;; num-tests has to be small enough. Too big value result in out of memory error!
  (prop/for-all [extended-auth (ssg/generator [ExtendedSummaryUser])]
                (let [extended-attachment (assoc attachment :auth extended-auth)
                      cleaned-attachment  (remove-unwanted-fields-from-attachment-auth extended-attachment)]
                  (and (nil? (sc/check Attachment (attachment-coercer cleaned-attachment)))
                       (= (dissoc cleaned-attachment :auth) (dissoc attachment :auth))
                       (= (count  extended-auth) (count (:auth cleaned-attachment)))))))


(defspec complete-missing-attachment-fields 20 ;; num-tests has to be small enough. Too big value result in out of memory error!
  (prop/for-all [attachment     (ssg/generator Attachment)
                 missing-fields (ssg/generator [(apply sc/enum [:locked :applicationState :target :requestedByAuthority
                                                                :notNeeded :op :signatures :auth])])]
                (let [failing-attachment   (apply dissoc attachment missing-fields)
                      completed-attachment (merge-required-fields-into-attachment failing-attachment)]
                  (and (nil? (sc/check Attachment (attachment-coercer completed-attachment)))))))


(defspec rename-applicationState-as-camelCase 20
  (prop/for-all [application-state (ssg/generator (sc/enum "draft" "complement-needed" "complementNeeded"))]
                (let [failing-attachment   (assoc attachment :applicationState application-state)
                      completed-attachment (applicationState-as-camelCase failing-attachment)]
                  (and (nil? (sc/check Attachment (attachment-coercer completed-attachment)))
                       (= (dissoc completed-attachment :applicationState) (dissoc attachment :applicationState))))))

(def TimestampTarget (sc/if number? sc/Int Target))

(defspec cleanup-attachment-target-timestamps 20
  (prop/for-all [target (ssg/generator TimestampTarget)]
                (let [failing-attachment   (assoc attachment :target target)
                      completed-attachment (set-target-timestamps-as-nil failing-attachment)]
                  (and
                   (if (number? target) 
                     (nil? (:target completed-attachment))
                     (= target (:target completed-attachment)))
                   (nil? (sc/check Attachment completed-attachment))))))

(def Nil (sc/enum nil))
(def NilValuedMapTarget (sc/if :type Target {:type Nil :id Nil}))

(defspec cleanup-attachment-target-with-nil-valued-map 20
  (prop/for-all [target (ssg/generator NilValuedMapTarget)]
                (let [failing-attachment   (assoc attachment :target target)
                      completed-attachment (set-target-with-nil-valued-map-as-nil failing-attachment)]
                  (and
                   (if (:type target)
                     (= target (:target completed-attachment))
                     (nil? (:target completed-attachment)))
                   (nil? (sc/check Attachment completed-attachment))))))

(def NilVerdictIdTarget (sc/if :id Target {:type (sc/enum "verdict") :id Nil}))

(defspec update-attachment-target-verdict-id 20
  (prop/for-all [target (ssg/generator NilVerdictIdTarget)
                 verdict-id ssg/object-id]
                (let [failing-attachment   (assoc attachment :target target)
                      completed-attachment (set-verdict-id-for-nil-valued-verdict-targets verdict-id failing-attachment)]
                  (and
                   (if (and (= "verdict" (:type target)) (nil? (:id target)))
                     (= (get-in completed-attachment [:target :id]) verdict-id)
                     (= target (:target completed-attachment)))
                   (nil? (sc/check Attachment (attachment-coercer completed-attachment)))))))

