(ns lupapalvelu.ui.docgen.field.huoneistot.common
  (:require [lupapalvelu.ui.docgen.feature.application :as app]
            [re-frame.core :as rf]
            [sade.util :as util]))

;;; Change permit is a special case

;; A "muutoslupa" application is based on an existing application and
;; we are applying changes to the premises data from the original app.

(rf/reg-sub ::change-permit?
  :<- [::app/permit-subtype]
  (fn [subtype]
    (util/=as-kw subtype :muutoslupa)))

(defn schema->body-map
  [{:keys [body]}]
  (->> body
       (remove :hidden)
       (map (juxt (comp keyword :name) identity))
       (into {})))
