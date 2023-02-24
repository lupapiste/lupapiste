(ns lupapalvelu.ui.attachment.state-tags
  (:require [lupapalvelu.ui.attachment.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [sade.shared-util :as util]))

;; Adapted from state-icons-model.js

(defn- stamped [attachment]
  (when (some-> attachment :latestVersion :stamped)
    :attachment.stamped))

(defn- signed [{:keys [latestVersion signatures]}]
  (when-let [file-id (:fileId latestVersion)]
    (when (util/find-by-key :fileId file-id signatures)
      :attachment.signed)))

(defn- sent [attachment]
  (let [transfers (js->clj js/lupapisteApp.models.application._js.transfers
                           :keywordize-keys true)]
    (when (and (:sent attachment)
               (not (some #(-> % :type (= "attachments-to-asianhallinta")) transfers)))
      :attachment.sent)))

(defn- for-printing [attachment]
  (when (:forPrinting attachment) :verdict.status))

(defn- ram [{:keys [ramLink]}]
  (when ramLink :a11y.ram))

(defn- archived [{:keys [metadata]}]
  (when (= (:tila metadata) "arkistoitu")
    :archived))

(defn- states [attachment]
  (->> [stamped signed sent for-printing ram archived]
       (map #(% attachment))
       (remove nil?)))

(defn state-tags [attachment]
  [:div.flex--wrap.flex--gap1
   (for [state (states attachment)]
     [:span.attachment-state-tag.bg--gray.pad--v1.pad--h2 {:key [state]}
      (common/loc state)])])

(def state-filters
  "State filter defnitions for `lupapalvelu.ui.attachment.filters`."
  [{:text-loc  :ram.type.ram
    :value     :ram
    :filter-fn ram}
   {:text-loc  :attachment.signed
    :value     :signed
    :filter-fn signed}
   {:text-loc  :attachment.stamped
    :value     :stamped
    :filter-fn stamped}
   {:text-loc  :attachment.sent
    :value     :sent
    :filter-fn sent}
   {:text-loc  :archived
    :value     :archived
    :filter-fn archived}
   {:text-loc  :attachment.not-archivable
    :value     :not-archivable
    :filter-fn shared/archival-error}])
