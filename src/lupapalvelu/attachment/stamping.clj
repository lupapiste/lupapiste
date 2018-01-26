(ns lupapalvelu.attachment.stamping
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.stamps :as stamps]
            [lupapalvelu.attachment.util :as att-util]
            [lupapalvelu.stamper :as stamper]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.job :as job]
            [lupapalvelu.i18n :as i18n]
            [sade.files :as files]
            [sade.util :refer [future* fn-> fn->>] :as util]
            [sade.strings :as ss]))

(defn status [job-id version timeout]
  (job/status job-id (util/->long version) (util/->long timeout)))

(defn- ->file-info [attachment]
  (let [versions (-> attachment :versions reverse)
        re-stamp? (:stamped (first versions))
        source (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size])
      :signature (filter #(= (:fileId (first versions)) (:fileId %)) (:signatures attachment))
      :stamped-original-file-id (when re-stamp? (:originalFileId (first versions)))
      :operation-ids (set (att-util/get-operation-ids attachment))
      :attachment-id (:id attachment)
      :attachment-type (:type attachment))))

(defn get-attachment-approval-stamping [application attachment-id]
  (let [attachment     (att/get-attachment-info application attachment-id)
        originalFileId (-> attachment :latestVersion :originalFileId keyword)
        approval       (-> attachment :approvals originalFileId)]
    (when (-> approval :state #{"ok"}) approval)))

(defn- update-stamp-to-attachment! [stamp file-info {:keys [application user created] :as context}]
  (let [{:keys [attachment-id fileId filename stamped-original-file-id signature]} file-info
        options (select-keys context [:x-margin :y-margin :transparency :page])]
    (files/with-temp-file file
      (with-open [out (io/output-stream file)]
        (stamper/stamp stamp fileId out options))
      (debug "uploading stamped file: " (.getAbsolutePath file))
      (let [approval (get-attachment-approval-stamping application attachment-id)
            result  (att/upload-and-attach!
                     {:application application :user user}
                     {:attachment-id                attachment-id
                      :replaceable-original-file-id stamped-original-file-id
                      :comment-text                 nil :created created
                      :stamped                      true :comment? false
                      :state                        :ok
                      :signature                    signature
                      :approval                     approval}
                     {:filename filename :content file
                      :size     (.length file)})]
        (tos/mark-attachment-final! application created attachment-id)
        (:fileId result)))))

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

(defn- info-fields->stamp [{:keys [stamp-created transparency qr-code lang]} info-fields]
  {:pre [(pos? stamp-created)]}
  (let [limit-to-100 (fn [text] (when (seq text)
                                  (ss/limit (str text) 100)))
        values->row-text  (fn [row-of-values]
                            (ss/join " " (mapv limit-to-100 row-of-values)))]
    (->> (update-buildings lang info-fields)
         stamps/non-empty-stamp-rows->vec-of-string-value-vecs
         (map values->row-text)
         (stamper/make-stamp transparency qr-code))))

(defn- make-stamp-without-buildings [context {:keys [fields]}]
  (->> {:fields (stamps/dissoc-tag-by-type fields :building-id)}
       (info-fields->stamp context)))

(defn- make-operation-specific-stamps [context info-fields operation-id-sets]
  (->> (map (partial update info-fields :buildings select-buildings-by-operations) operation-id-sets)
       (map (partial info-fields->stamp context))
       (zipmap operation-id-sets)))

(defn- stamp-attachment! [stamp file-info context job-id application-id]
  (try
    (debug "Stamping" (select-keys file-info [:attachment-id :contentType :fileId :filename :stamped-original-file-id]))
    (job/update job-id assoc (:attachment-id file-info) {:status :working :fileId (:fileId file-info)})
    (->> (update-stamp-to-attachment! stamp file-info context)
         (hash-map :status :done :fileId)
         (job/update job-id assoc (:attachment-id file-info)))
    (catch Throwable t
      (errorf t "failed to stamp attachment: application=%s, file=%s" application-id (:fileId file-info))
      (job/update job-id assoc (:attachment-id file-info) {:status :error :fileId (:fileId file-info)}))))

(defn- stamp-attachments!
  [file-infos {:keys [job-id application info-fields] :as context}]
  (let [stamp-without-buildings (make-stamp-without-buildings context info-fields)
        operation-specific-stamps (->> (map :operation-ids file-infos)
                                       (remove empty?)
                                       distinct
                                       (make-operation-specific-stamps context info-fields))]
    (doseq [{op-ids :operation-ids :as file-info} file-infos]
      (-> (if (and (seq op-ids) (seq (:buildings info-fields)))
            (operation-specific-stamps op-ids)
            stamp-without-buildings)
          (stamp-attachment! file-info context job-id (:id application))))))

(defn- stamp-job-status [data]
  (if (every? #{:done :error} (map #(get-in % [:status]) (vals data))) :done :running))

(defn make-stamp-job [attachment-infos context]
  (let [file-infos (map ->file-info attachment-infos)
        job (-> (zipmap (map :attachment-id file-infos) (map #(assoc % :status :pending) file-infos))
                (job/start stamp-job-status))]
    (future* (stamp-attachments! file-infos (assoc context :job-id (:id job))))
    job))
