(ns lupapalvelu.ui.auth-admin.imports.data.view
  "Model for import configurations main view. Keys under datapath:

  `:visible?` Whether the section is currently active/visible.

  `:configs` List of configs (both drafts and imported).

  `:include-deleted?` Value for showing also deleted configurations toggle."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.misc]
            [re-frame.core :as rf]
            [sade.util :as util]))


(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))

;; -----------------------------
;; Events
;; -----------------------------

(rf/reg-event-db ::organization-import-configs.success
  (fn [db [_ {:keys [configs]}]]
    (if (seq configs)
      (set-d db :configs (map im-sc/->config configs))
      (unset-d db :configs))))

(rf/reg-event-fx ::toggle-view
  [(rf/inject-cofx ::org-subs/url-org-id)]
  (fn [{:keys [db] :as cofx} [_ visible?]]
    (let [org-id (::org-subs/url-org-id cofx)]
      (if (and org-id visible?)
        {:db (set-d db {:visible? true
                        :org-id   org-id})
         :fx [[:dispatch [:auth/refresh]]]
         :action/query {:name    :organization-import-configs
                        :success ::organization-import-configs.success
                        :params  {:organizationId org-id}}}
        {:db (unset-d db)}))))

(rf/reg-event-db ::toggle-deleted
  (fn [db [_ include-deleted?]]
    (set-d db :include-deleted? include-deleted?)))

;; -----------------------------
;; Subs
;; -----------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::visible?
  :<- [::data]
  :-> :visible?)

(rf/reg-sub ::include-deleted?
  :<- [::data]
  :-> :include-deleted?)

(rf/reg-sub ::any-deleted?
  :<- [::data]
  :-> (util/fn->> :configs (some (comp boolean :deleted))))

(rf/reg-sub ::configs?
  :<- [::data]
  :-> (comp boolean seq :configs))

(rf/reg-sub ::config-ids
  :<- [::data]
  (fn [{:keys [configs include-deleted?]}]
    (some->> (cond->> configs
               (not include-deleted?) (remove :deleted))
             seq
             (mapv :id))))


(rf/reg-sub ::config-info
  :<- [::data]
  (fn [{:keys [configs]} [_ cfg-id]]
    (let [{:keys [id title rowdata modified deleted
                  imported]} (util/find-by-id cfg-id configs)]
      (util/assoc-when {:id       id
                        :title    title
                        :filename (:filename rowdata)
                        :modified (:ts modified)}
                       :imported (:ts imported)
                       :deleted (:ts deleted)))))
