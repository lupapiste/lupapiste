(ns lupapalvelu.printing-order.printing-order-api
  (:require [lupapalvelu.action :as action :refer [defquery defcommand]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.printing-order.domain :refer :all]
            [lupapalvelu.printing-order.processor :as processor]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [error]]))

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

(defn orderable-attachment? [{:keys [type forPrinting] :as attachment}]
  (and (pdf-attachment? attachment)
       (not (and (util/includes-as-kw? omitted-attachment-type-groups
                                       (:type-group type))
                 (not forPrinting)))))

(defn has-orderable-attachments
  "Pre-check that fails if the application does not have any orderable attachments."
  [{:keys [application]}]
  (when-not (some orderable-attachment? (:attachments application))
    (fail :error.no-orderable-attachments)))

(defquery attachments-for-printing-order
  {:feature    :printing-order
   :parameters [id]
   :states     states/all-application-states
   :user-roles #{:applicant}
   :pre-checks [pricing-available?
                (partial permit/valid-permit-types {:R ["muutoslupa" :empty] :P :all})
                has-orderable-attachments]}
  [command]
  (ok :attachments (->> (att/sorted-attachments command)
                        (filter orderable-attachment?)
                        (map att/enrich-attachment))
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

(defquery my-printing-orders
  {:feature :printing-order
   :user-roles  #{:applicant :authority}}
  [{user :user}]
  (ok :orders (map (fn [m] {:order-number (:external-reference m)
                            :application (-> m :application :id)
                            :created (:created m)
                            :acknowledged? (not (nil? (:acknowledged m)))})
                   (mongo/select :integration-messages {:messageType :printing-order
                                                        :initiator.id (:id user)}))))

(defcommand submit-printing-order
  {:feature          :printing-order
   :parameters       [:id order contacts]
   :input-validators [(partial action/map-parameters [:order :contacts])]
   :states           states/all-application-states
   :user-roles       #{:applicant}
   :user-authz-roles roles/default-authz-reader-roles
   :pre-checks       [pricing-available?]}
  [{:keys [application] :as command}]
  (let [prepared-order (processor/prepare-order application order contacts)
        total-size (reduce + (map :size (:files prepared-order)))]
    (when (> total-size max-total-file-size)
      (fail! :error.printing-order.too-large))
    (processor/do-submit-order command application prepared-order)
    (ok)))
