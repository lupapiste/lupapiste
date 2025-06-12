(ns lupapalvelu.ui.auth-admin.imports.data.core
  (:require [lupapalvelu.ui.misc]
            [re-frame.core :as rf]))

;; -----------------------------
;; Events
;; -----------------------------

(rf/reg-event-fx ::goto-config
  (fn [_ [_ config-id]]
    {:page/open ["auth-admin-import-config" config-id]}))

(rf/reg-event-fx ::goto-imports
  (fn [_]
    {:page/open "auth-admin-imports"}))
