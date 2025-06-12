(ns lupapalvelu.printing-order.printing-order-api
  (:require [lupapalvelu.action :as action :refer [defquery defcommand]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.printing-order.domain :refer :all]
            [lupapalvelu.printing-order.processor :as processor]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :as util]))

(def omitted-attachment-type-groups
  [:hakija :osapuolet :rakennuspaikan_hallinta :paatoksenteko :muutoksenhaku
   :katselmukset_ja_tarkastukset :tietomallit])

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
  {:feature          :printing-order
   :parameters       [id]
   :permissions      [{:required [:application/printing-order]}]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :pre-checks       [pricing-available?
                      (partial permit/valid-permit-types {:R ["muutoslupa" :empty] :P :all})
                      has-orderable-attachments]}
  [command]
  (ok :attachments (->> (att/sorted-attachments command)
                        (filter orderable-attachment?)
                        (mapv att/enrich-attachment))
      :tagGroups (mapv vector att-type/type-groups)))

(defquery printing-order-pricing
  {:feature          :printing-order
   :permissions      [{:required [:users/printing-order]}]
   :pre-checks       [pricing-available?]}
  [{:keys [created]}]
  (ok :pricing pricing :vat (vat-for-timestamp created)))

(def max-total-file-size ; 1 Gb
  (* 1024 1024 1024))

(env/in-dev
  (defquery my-printing-orders
    {:feature    :printing-order
     ;; No permissions, since `:users/printing-order` would require changes to
     ;; common-actions-test as well.
     :user-roles #{:applicant}}
    [{user :user}]
    (ok :orders (mapv (fn [m] {:order-number  (:external-reference m)
                               :application   (-> m :application :id)
                               :created       (:created m)
                               :acknowledged? (not (nil? (:acknowledged m)))})
                      (mongo/select :integration-messages {:messageType  :printing-order
                                                           :initiator.id (:id user)})))))

(defcommand submit-printing-order
  {:feature          :printing-order
   :parameters       [:id order contacts]
   :input-validators [(partial action/map-parameters [:order :contacts])]
   :permissions      [{:required [:application/printing-order]}]
   :pre-checks       [pricing-available?]}
  [{:keys [application] :as command}]
  (let [prepared-order (processor/prepare-order application order contacts)
        total-size     (reduce + (map :size (:files prepared-order)))]
    (when (> total-size max-total-file-size)
      (fail! :error.printing-order.too-large))
    (processor/do-submit-order command application prepared-order)
    (ok)))
