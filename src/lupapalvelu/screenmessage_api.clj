(ns lupapalvelu.screenmessage-api
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defquery screenmessages
  {:description "Returns the Lupapiste screenmessages "
   :user-roles  #{:anonymous}}
  [_]
  (->> (mongo/select :screenmessages
                     {:products :lupapiste}
                     [:fi :sv])
       (map (fn [{:keys [fi sv] :as m}]
              (cond-> (select-keys m [:fi :sv])
                (nil? sv) (assoc :sv fi))))
       (ok :screenmessages)))

(defquery admin-screenmessages
  {:description "All screenmessage data"
   :user-roles  #{:admin}}
  [_]
  (ok :screenmessages (mongo/select :screenmessages {})))

(def Product (sc/enum "lupapiste" "store" "terminal" "departmental"))

(defcommand add-screenmessage
  {:parameters          [fi products]
   :optional-parameters [sv]
   :input-validators    [{:fi                   ssc/NonBlankStr
                          (sc/optional-key :sv) ssc/NonBlankStr
                          :products             [(sc/one Product "product") Product]}]
   :user-roles          #{:admin}}
  [{created :created}]
  (let [msg-id (mongo/create-id)]
    (->> {:id       msg-id
          :added    created
          :products (set products)
          :fi       (ss/trim fi)
          :sv       (ss/trim sv)}
         util/strip-blanks
         (mongo/insert :screenmessages))
    (ok :message-id msg-id)))

(defcommand remove-screenmessage
  {:description      "Remove screenmesssage for all of its products."
   :parameters       [message-id]
   :input-validators [(partial action/non-blank-parameters [:message-id])]
   :user-roles       #{:admin}}
  [_]
  (mongo/remove-by-id :screenmessages message-id)
  (ok))
