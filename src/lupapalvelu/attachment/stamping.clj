(ns lupapalvelu.attachment.stamping
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.stamper :as stamper]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.tiedonohjaus :as tos]
            [lupapalvelu.job :as job]
            [lupapalvelu.i18n :as i18n]
            [sade.files :as files]
            [sade.util :refer [future* fn-> fn->>] :as util]
            [sade.strings :as ss]
            [lupapalvelu.attachment.stamps :as stamps]))

(defn status [job-id version timeout]
  (job/status job-id (util/->long version) (util/->long timeout)))

(defn- ->file-info [attachment]
  (let [versions (-> attachment :versions reverse)
        re-stamp? (:stamped (first versions))
        source (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size])
      :signature (util/find-by-key :fileId (:fileId (first versions)) (:signatures attachment))
      :stamped-original-file-id (when re-stamp? (:originalFileId (first versions)))
      :operation-ids (set (att/get-operation-ids attachment))
      :attachment-id (:id attachment)
      :attachment-type (:type attachment))))

(defn- update-stamp-to-attachment! [stamp file-info {:keys [application user created] :as context}]
  (let [{:keys [attachment-id fileId filename stamped-original-file-id signature]} file-info
        options (select-keys context [:x-margin :y-margin :transparency :page])]
    (files/with-temp-file file
      (with-open [out (io/output-stream file)]
        (stamper/stamp stamp fileId out options))
      (debug "uploading stamped file: " (.getAbsolutePath file))
      (let [result (att/upload-and-attach!
                     {:application application :user user}
                     {:attachment-id                attachment-id
                      :replaceable-original-file-id stamped-original-file-id
                      :comment-text                 nil :created created
                      :stamped                      true :comment? false
                      :state                        :ok
                      :signature                    signature}
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

(defn update-buildings [lang info-fields]
  (if (:buildings info-fields)
    (stamps/assoc-tag-by-type (:fields info-fields) :building-id (first (sort (map (partial building->str lang) (:buildings info-fields)))))
    info-fields))

(defn- info-fields->stamp [{:keys [stamp-created transparency qr-code lang]} info-fields]
  {:pre [(pos? stamp-created)]}
  (->> (update-buildings lang info-fields)
       (stamps/row-values-as-string)
       (map
         (fn [field]
           (ss/join " " (mapv (fn [text] (if (seq text) (ss/limit (str text) 100))) field))))
       (stamper/make-stamp transparency qr-code)))

(defn- make-stamp-without-buildings [context info-fields]
  (->> (stamps/dissoc-tag-by-type (:fields info-fields) :building-id)
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
      (-> (if (seq op-ids)
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
