(ns lupapalvelu.ui.docgen.field.huoneistot.muutostapa
  "Muutotapa business logic"
  (:require [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.source-value :as source-value]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [re-frame.core :as rf]
            [sade.strings :as ss]
            [sade.util :as util]))

;; Rows might be missing a `muutostapa` value. When we modify a row,
;; we may need to also set the `muutostapa` at the same time.

(def ^:const added "lisäys")
(def ^:const removed "poisto")
(def ^:const edited "muutos")

;; Are we adding, modifying or removing a row?
(rf/reg-sub ::current-muutostapa
  (fn current-muutostapa-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::committed/backend-submitted-value doc-id (conj row-path :muutostapa)]))
  :-> identity)

(rf/reg-sub ::original-muutostapa
  (fn original-muutostapa-inputfn [[_ doc-id row-path]]
    {:pre [(string? doc-id) (vector? row-path)]}
    (rf/subscribe [::source-value/source-value doc-id (conj row-path :muutostapa)]))
  :-> identity)

(defn- resolve-regular-muutostapa
  "Mimics `resolveRegularMuutostapa`"
  [{:keys [current-value source-value new-row? change-type]}]
  (let [edited?   (= :edited change-type)
        reverted? (= :reverted change-type)
        changed?  (not= (ss/blank-as-nil current-value)
                        (ss/blank-as-nil source-value))]
    (cond
      new-row?              added
      edited?               edited
      (and (not edited?)
           (not reverted?)) (when (not (contains? #{removed added} source-value))
                              source-value)
      (and reverted?
           changed?)        (when (not= added source-value)
                              source-value)
      reverted?             nil
      :else                 current-value)))

(defn- resolve-change-permit-muutostapa
  "Mimics `resolveChangePermitMuutostapa`"
  [{:keys [source-value new-row? change-type]}]
  (let [edited?   (= :edited change-type)
        reverted? (= :reverted change-type)]
    (cond
      new-row?  added
      edited?   (if (= added source-value)
                  added
                  edited)
      reverted? (if (= removed source-value)
                  edited
                  source-value)
      :else     source-value)))

(defn resolve-muutostapa
  [{:keys [change-permit? change-type current-value new-row? source-value]}]
  (if (and change-permit? (not new-row?))
    (resolve-change-permit-muutostapa (util/mmap source-value new-row? change-type))
    (resolve-regular-muutostapa (util/mmap source-value new-row? change-type current-value))))

(defn db->current-muutostapa
  [db doc-id row-path]
  {:pre [(string? doc-id) (vector? row-path)]}
  (let [muutostapa-path (conj row-path :muutostapa)]
    (or (committed/db->backend-submitted-value db doc-id muutostapa-path)
        "")))

(defn db->new-muutostapa
  "Calculate the expected `muutostapa` given the current `db`.

  * `cell-paths` -- Paths to all non-synthetic cells in the row"
  [db {:keys [cell-paths change-permit? doc-id row-path]}]
  {:pre [(vector? cell-paths) (boolean? change-permit?) (string? doc-id) (vector? row-path)]}
  ;; Cannot use subscriptions as we (might) be in the middle of
  ;; altering a cell and need to know the `muutostapa` given the
  ;; changes we've are making using the `db` in hand. For example, the user
  ;; might have previously edited the row (=> muutostapa becomes EDITED)
  ;; but then decides to manually revert the change (=> muutostapa might
  ;; become ADDED or stay EDITED depending on the other cells).
  (let [new-row?        (every?
                          (fn [cell-path] (nil? (source-value/db->source-value db doc-id cell-path)))
                          cell-paths)
        edited-row?     (some
                          (fn [cell-path]
                            (not= (ss/blank-as-nil (source-value/db->source-value db doc-id cell-path))
                                  (ss/blank-as-nil (value/db->current-field-value db doc-id cell-path))))
                          cell-paths)
        muutostapa-path (conj row-path :muutostapa)
        source-value    (source-value/db->source-value db doc-id muutostapa-path)
        current-value   (value/db->current-field-value db doc-id muutostapa-path)
        change-type     (when edited-row? :edited)
        new-muutostapa  (resolve-muutostapa (util/mmap change-permit? change-type new-row? current-value source-value))]
    (or new-muutostapa "")))

(defn make-set-muutostapa-callback
  [app-id doc-id row-path]
  (docgen-common/make-on-change-immediate
    (util/mmap {:path (conj row-path :muutostapa)} app-id doc-id)))

(defn make-mark-as-removed-callback
  [{:keys [app-id doc-id row-path]}]
  (let [set-muutostapa (make-set-muutostapa-callback app-id doc-id row-path)]
    (fn [_]
      (set-muutostapa removed))))
