(ns lupapalvelu.ui.invoices.state
  (:refer-clojure :exclude [select-keys])
  (:require [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def invoices          (state-cursor :invoices))
(def new-invoice       (state-cursor :new-invoice))
(def valid-units       (state-cursor :valid-units))
(def application-id    (state-cursor :application-id))
(def operations        (state-cursor :operations))
(def price-catalogues  (state-cursor :price-catalogues))
(def backend-id-codes  (state-cursor :backend-id-codes))


(defn auth? [action]
  (js/lupapisteApp.models.applicationAuthModel.ok (name action)))
