(ns lupapalvelu.printing-order.printing-order-api
  (:require [taoensso.timbre :as timbre :refer [error]]
            [lupapalvelu.action :refer [defquery defcommand]]
            [sade.core :refer :all]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]
            [lupapalvelu.attachment.tag-groups :as att-tag-groups]
            [lupapalvelu.printing-order.domain :refer :all]
            [sade.util :as util]
            [clojure.java.io :as io]
            [schema.core :as sc]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.tags :as att-tags]))

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
   :states           states/post-verdict-states
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
      :tagGroups (map vector (concat att-tags/application-group-types att-type/type-groups))))

(defquery printing-order-pricing
  {:feature          :printing-order
   :states           states/post-verdict-states
   :user-roles       #{:applicant}
   :pre-checks       [pricing-available?]}
  [_]
  (ok :pricing pricing))

(def max-total-file-size ; 1 Gb
  (* 1024 1024 1024))

(defcommand submit-printing-order
  {:feature      :printing-order
   :parameters  [:id order contacts]
   :states      states/post-verdict-states
   :user-roles  #{:applicant}
   :pre-checks  [pricing-available?]}
  [{application :application}]
  (let [printing-order (prepare-order application order contacts)
        total-size (reduce + (map :size (:files printing-order)))]
    (when (> total-size max-total-file-size)
      (fail! :error.printing-order.too-large))
    (ok :prepared-order printing-order :size total-size)))