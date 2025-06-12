(ns lupapalvelu.fixture.invoicing-enabled
  "Enable invoicing in every minimal organization."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]))

(defmethod fixture/fixture  :invoicing-enabled
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (mapv #(assoc % :scope (mapv (fn [sco] (assoc sco :invoicing-enabled true)) (:scope %)))
                                           (minimal/organizations {:http-port (components/system->http-port system)})))
  (mongo/insert-batch :verdict-templates minimal/vantaa-ya-verdict-templates))

(defmethod fixture/users :invoicing-enabled
  [_]
  minimal/users)
