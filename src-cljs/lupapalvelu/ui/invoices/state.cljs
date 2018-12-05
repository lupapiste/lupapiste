(ns lupapalvelu.ui.invoices.state
  (:refer-clojure :exclude [select-keys])
  (:require [clojure.string :as s]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]))

(defonce state* (atom {}))

(defn- state-cursor [key]
  (rum/cursor-in state* [key]))

(def invoices                  (state-cursor :invoices))
(def new-invoice               (state-cursor :new-invoice))
(def valid-units               (state-cursor :valid-units))
(def application-id            (state-cursor :application-id))
(def operations                (state-cursor :operations))
(def price-catalogue           (state-cursor :price-catalogue))
