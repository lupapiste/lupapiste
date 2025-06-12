(ns lupapalvelu.asiointi.notifications.outgoing-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.asiointi.notifications.common :as common]
            [lupapalvelu.asiointi.notifications.notification :as notification]
            [lupapalvelu.asiointi.notifications.outgoing :as outgoing]
            [lupapalvelu.asiointi.notifications.registration :as registration]
            [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt])
  (:import [org.bson.types ObjectId]))

(deftest all-types-supported-test
  (testing "`outgoing/Notification` supports all notification types"
    (let [supported (->> (m/children outgoing/Notification)
                         (map first)
                         (into #{}))]
      (is (= common/notification-types supported)))))

(deftest notification-body-example-test
  (let [registration {:created-at      #inst "2026-04-30T05:40:11.784-00:00"
                      :created-by      "user"
                      :id              (ObjectId. "69f2eb73ac8abc1108c57360")
                      :modified-by     "user-another"
                      :organization-id "123-R"
                      :target          {:url "http://localhost/foo"}}
        notification {:application-id      "LP-568-2026-00095"
                      :attempting-at       #inst "2026-04-30T05:40:11.784-00:00"
                      :control-case        :building
                      :created-at          #inst "2026-04-30T05:40:11.784-00:00"
                      :delivered-at        #inst "2026-04-30T05:40:11.784-00:00"
                      :extra-data          {:old-state :agreementSigned
                                            :new-state :info}
                      :id                  (ObjectId. "69f2eba0ac8abc1108c57361")
                      :notification-type   :application-state-change
                      :organization-id     "123-R"
                      :permanent-permit-id "foobar"
                      :registration-id     (ObjectId. "69f2eb73ac8abc1108c57360")}]
    (is (= {:application-id      "LP-568-2026-00095"
            :control-case        :building
            :created-at          "2026-04-30T08:40:11+03:00"
            :extra-data          {:old-state :agreementSigned
                                  :new-state :info}
            :notification-id     "69f2eba0ac8abc1108c57361"
            :notification-type   :application-state-change
            :organization-id     "123-R"
            :permanent-permit-id "foobar"
            :registration-id     "69f2eb73ac8abc1108c57360"}
           (outgoing/notification-body registration notification)))))

(def ^:private coercer! (m/coercer outgoing/Notification mt/json-transformer))

(defspec notification-body-property
  ;; Any given registration and notification produces a valid outgoing notification
  (prop/for-all [registration (mg/generator registration/Registration)
                 notification (mg/generator notification/Notification)]
    ;; The output contains a couple of fields (e.g. ObjectIds) converted to strings
    ;; Use a coercer to get back the original values
    (-> (outgoing/notification-body registration notification)
        coercer!)))
