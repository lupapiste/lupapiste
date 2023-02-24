(ns lupapalvelu.invoices.util
  (:require [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
            [sade.util :as util]
            [swiss.arrows :refer [-<>>]]))

(defn make-history-entry [user action now-timestamp updated-doc]
  {:user user
   :time now-timestamp
   :action action
   :state (:state updated-doc)})

(defn add-history-entry
  "Adds history entry

  updated-doc - doc that should have the new state
  user        - user who makes the action
  action      - String representation of the action (create or update basically)"
  ([user action now-timestamp updated-doc]
   (update updated-doc :history (fn [history-from-doc]
                                  (conj
                                    (or history-from-doc [])
                                    (make-history-entry user action now-timestamp updated-doc)))))
  ([user action updated-doc]
   (add-history-entry user action (now) updated-doc)))

(defn enrich-with-backend-id
  "Adds `:backend-id` to `invoice` if the invoice does not have backend-id AND the
  backend-id is enabled AND properly configured for the invoice organization. However, if
  `:backend-code` is no longer supported (unknown code or backend-id not enabled), it is
  removed without generating `:backend-id`."
  [{:keys [organization-id backend-id backend-code]
    :as   invoice}]
  (if (or backend-id (not backend-code))
    invoice
    (let [{:keys [invoicing-backend-id-config
                  invoicing-config]} (mongo/by-id :organizations organization-id
                                                  [:invoicing-backend-id-config
                                                   :invoicing-config.backend-id?])
          invoice                    (dissoc invoice :backend-code)]
      (if-not (and (:backend-id? invoicing-config)
                   (util/find-by-key :code backend-code (:codes invoicing-backend-id-config)))
        invoice
        (-<>> (:numbers invoicing-backend-id-config 1)
              (str "%s%0" <> "d") ; XY0000123
              (format <> backend-code (mongo/get-next-sequence-value (str "invoices-" organization-id)))
              (assoc invoice :backend-id))))))
