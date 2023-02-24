(ns lupapalvelu.attachment.stamping
  (:require [clj-uuid :as uuid]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.stamps :as stamps]
            [lupapalvelu.attachment.util :as att-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.job :as job]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [lupapalvelu.storage.object-storage :as object-storage]
            [lupapalvelu.tiedonohjaus :as tos]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [future*] :as util]
            [taoensso.timbre :refer [debug errorf]]))

(defn status [job-id version timeout]
  (job/status job-id (util/->long version) (util/->long timeout)))

(defn- ->file-info [attachment]
  (let [versions (-> attachment :versions reverse)
        re-stamp? (:stamped (first versions))
        source (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size :storageSystem])
      :signatures (when-not re-stamp?
                    (seq (filter #(= (-> versions first :version) (:version %))
                                 (:signatures attachment))))
      :stamped-original-file-id (when re-stamp? (:originalFileId (first versions)))
      :operation-ids (set (att-util/get-operation-ids attachment))
      :attachment-id (:id attachment)
      :attachment-type (:type attachment))))

(defn get-attachment-approval-stamping [application attachment-id]
  (let [attachment     (att/get-attachment-info application attachment-id)
        originalFileId (-> attachment :latestVersion :originalFileId keyword)
        approval       (-> attachment :approvals originalFileId)]
    (when (-> approval :state #{"ok"}) approval)))

(def qr-code-unscaled-pixel-size 70)

(defn- update-stamp-to-attachment! [info-fields file-info {:keys [application user created] :as context}]
  (let [{:keys [attachment-id fileId filename stamped-original-file-id signatures]} file-info
        new-file-id (str (uuid/v1))
        pdfa?       (conversion/pdf-a-required? (:organization application))
        params      {:bucket        (gcs/actual-bucket-name object-storage/application-bucket)
                     :object-key    (storage/actual-object-id {:application (:id application)} fileId)
                     :target-bucket (gcs/actual-bucket-name object-storage/unlinked-bucket)
                     :target-key    (storage/actual-object-id {:user-id (:id user)} new-file-id)
                     :pdfa?         pdfa?
                     :stamp         (merge (select-keys context [:page
                                                                 :x-margin
                                                                 :y-margin
                                                                 :scale
                                                                 :transparency])
                                           {:info-fields info-fields}
                                           (when (:qr-code context)
                                             {:qr-code {:data (env/value :host)
                                                        :size qr-code-unscaled-pixel-size}}))}
        {:keys [size content-type]} (laundry-client/stamp-attachment params)
        approval    (get-attachment-approval-stamping application attachment-id)]
    (att/attach! (select-keys context [:application :user])
                 nil
                 {:attachment-id                attachment-id
                  :replaceable-original-file-id stamped-original-file-id
                  :comment-text                 nil
                  :created                      created
                  :stamped                      true
                  :comment?                     false
                  :state                        :ok
                  :signatures                   signatures
                  :approval                     approval}
                 {:fileId        new-file-id
                  :filename      filename
                  :size          size
                  :contentType   content-type
                  :storageSystem :gcs}
                 {:result {:archivable pdfa?}})
    (tos/mark-attachment-final! application created attachment-id)))

(defn- asemapiirros? [{{type :type-id} :attachment-type}]
  (= :asemapiirros (keyword type)))

(defn- select-buildings-by-operations [buildings operation-ids]
  (filter (comp operation-ids :operation-id) buildings))

(defn- building->str [lang {:keys [short-id national-id]}]
  (when (ss/not-blank? national-id)
    (i18n/with-lang lang
      (ss/join " " (cons (i18n/loc "stamp.building")
                         (if (ss/not-blank? short-id)
                           [short-id ":" national-id]
                           [national-id]))))))

(defn update-buildings [lang {:keys [buildings fields]}]
  (let [building-strs (->> buildings (map (partial building->str lang)) sort)]
    (reduce (fn [result row]
              (if (some #(= :building-id (keyword (:type %))) row)
                (concat
                  result
                  ; First building id is set normally in the building-id tag
                  [(mapv (fn [field]
                           (if (= :building-id (keyword (:type field)))
                             (assoc field :value (first building-strs))
                             field))
                         row)]
                  ; Possible additional ids are added as new stamp rows
                  (when (seq (rest building-strs))
                    (mapv #(identity [{:type :building-id :value %}]) (rest building-strs))))
                (concat result [row])))
            []
            fields)))

(defn- process-info-fields-for-stamper [{:keys [stamp-created lang]} info-fields]
  {:pre [(pos? stamp-created)]}
  (let [limit-to-100 (fn [text] (when (seq text)
                                  (ss/limit (str text) 100)))
        values->row-text  (fn [row-of-values]
                            (ss/join " " (mapv limit-to-100 row-of-values)))]
    (concat (->> (update-buildings lang info-fields)
                 stamps/non-empty-stamp-rows->vec-of-string-value-vecs
                 (map values->row-text))
            ["www.lupapiste.fi"])))

(defn- make-stamp-without-buildings [context {:keys [fields]}]
  (->> {:fields (stamps/dissoc-tag-by-type fields :building-id)}
       (process-info-fields-for-stamper context)))

(defn- make-operation-specific-stamps [context info-fields operation-id-sets]
  (->> (map (partial update info-fields :buildings select-buildings-by-operations) operation-id-sets)
       (map (partial process-info-fields-for-stamper context))
       (zipmap operation-id-sets)))

(defn- stamp-attachment! [info-fields file-info context job-id application-id]
  (try
    (debug "Stamping" (select-keys file-info [:attachment-id :contentType :fileId :filename :stamped-original-file-id :job-id]))
    (job/update-by-id job-id (:attachment-id file-info) {:status :working :fileId (:fileId file-info)})
    (->> (update-stamp-to-attachment! info-fields file-info context)
         (hash-map :status :done :fileId)
         (job/update-by-id job-id (:attachment-id file-info)))
    (debug "Stamping complete" (select-keys file-info [:attachment-id :contentType :fileId :filename :stamped-original-file-id]))
    (catch Throwable t
      (errorf t "failed to stamp attachment: application=%s, file=%s" application-id (:fileId file-info))
      (job/update-by-id job-id (:attachment-id file-info) {:status :error :fileId (:fileId file-info)}))))

(defn- stamp-attachments!
  [file-infos {:keys [job-id application info-fields] :as context}]
  (debug "stamp-attachments! invoked")
  (let [info-fields-without-buildings (make-stamp-without-buildings context info-fields)
        ;; A map from set of operation ids related to each attachment to the stamp data required for that attachment
        op-ids-info-fields-map (->> (map :operation-ids file-infos)
                                    (remove empty?)
                                    distinct
                                    (make-operation-specific-stamps context info-fields))]
    (doseq [{op-ids :operation-ids :as file-info} file-infos]
      (debug "stamp-attachments! - file-infos seq, processing attachment:" (:attachment-id file-info))
      (-> (if (and (seq op-ids) (seq (:buildings info-fields)))
            (op-ids-info-fields-map op-ids)
            info-fields-without-buildings)
          (stamp-attachment! file-info context job-id (:id application))))))

(defn make-stamp-job [attachment-infos context]
  (let [file-infos (map ->file-info attachment-infos)
        job (-> (zipmap (map :attachment-id file-infos) (map #(assoc % :status :pending) file-infos))
                (job/start))]
    (future* (stamp-attachments! file-infos (assoc context :job-id (:id job))))
    (debug "Returning stamp job:" job)
    job))
