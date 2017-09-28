(ns lupapalvelu.printing-order.printing-order-api
  (:require [taoensso.timbre :as timbre :refer [error]]
            [lupapalvelu.action :refer [defquery defcommand]]
            [sade.core :refer :all]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]
            [lupapalvelu.printing-order.domain :refer :all]
            [lupapalvelu.printing-order.processor :as processor]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.tags :as att-tags]
            [lupapalvelu.printing-order.mylly-client :as mylly]))

(def omitted-attachment-type-groups
  [:hakija :osapuolet :rakennuspaikan_hallinta :paatoksenteko :muutoksenhaku
   :katselmukset_ja_tarkastukset :tietomallit])

(defn read-pricing []
  (try
    (sc/validate PricingConfiguration
                 (util/read-edn-resource "printing-order/pricing.edn"))
    (catch Exception _
      (try
        (sc/validate PricingConfiguration
                     (util/read-edn-file "./printing-order/pricing.edn"))
        (catch Exception e
          (error "Reading pricing configuration failed" e)
          nil)))))

(def pricing (read-pricing))

(defn pricing-available? [_]
  (when-not pricing
    (fail :error.feature-not-enabled)))


(defquery attachments-for-printing-order
  {:feature          :printing-order
   :parameters       [id]
   :states           states/all-application-states
   :user-roles       #{:applicant}
   :pre-checks       [pricing-available?]}
  [{application :application :as command}]
  (ok :attachments (->> (att/sorted-attachments command)
                        (map att/enrich-attachment)
                        (remove #(and
                                  (util/contains-value? omitted-attachment-type-groups (keyword (-> % :type :type-group)))
                                  (not (:forPrinting %))))
                        (filter (fn [att] (util/contains-value? (:tags att) :hasFile)))
                        (filter pdf-attachment?))
      :tagGroups (map vector att-type/type-groups)))

(defquery printing-order-pricing
  {:feature          :printing-order
   :states           states/all-application-states
   :user-roles       #{:applicant}
   :pre-checks       [pricing-available?]}
  [_]
  (ok :pricing pricing))

(def max-total-file-size ; 1 Gb
  (* 1024 1024 1024))

(defcommand submit-printing-order
  {:feature      :printing-order
   :parameters  [:id order contacts]
   :states      states/all-application-states
   :user-roles  #{:applicant}
   :pre-checks  [pricing-available?]}
  [{application :application user :user created-ts :created}]
  (let [prepared-order (processor/prepare-order application order contacts)
        total-size (reduce + (map :size (:files prepared-order)))]
    (when (> total-size max-total-file-size)
      (fail! :error.printing-order.too-large))
    (let [result (mylly/login-and-send-order! (processor/enrich-with-file-content user prepared-order))]
      (if (:ok result)
        (do
          (processor/save-integration-message user created-ts application prepared-order (:orderNumber result))
          (ok :order-number (:orderNumber result) :size total-size))
        (fail! :error.printing-order.submit-failed)))))