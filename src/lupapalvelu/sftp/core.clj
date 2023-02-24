(ns lupapalvelu.sftp.core
  (:require [babashka.fs :as fs]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.schemas :refer [FileStream IntegrationMessages
                                              WriteApplicationOptions
                                              SftpOrganizationConfiguration]]
            [lupapalvelu.sftp.util :as sftp-util]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.files :as files]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [errorf info]]))

(def IntegrationMessageFileType (sc/enum "ok" "waiting" "error"))

(sc/defn ^:private ^:always-validate file-type-dir
  [file-type :- IntegrationMessageFileType]
  (case (keyword file-type)
    :ok    sftp-ctx/ARCHIVE
    :error sftp-ctx/ERROR
    nil))

(sc/defn ^:always-validate integration-messages :- IntegrationMessages
  [command]
  (let [id          (-> command :application :id)
        ctx         (sftp-ctx/files-context command)
        xml-pattern (re-pattern (str "^" id "_.*\\.xml"))
        id-pattern  (re-pattern (str "^" id "_.*"))]
    {:waiting (sftp-ctx/list-files ctx (file-type-dir "waiting") xml-pattern)
     :ok      (sftp-ctx/list-files ctx (file-type-dir "ok") xml-pattern)
     :error   (sftp-ctx/list-files ctx (file-type-dir "error") id-pattern)}))

(sc/defn ^:always-validate integration-message-stream :- FileStream
  [{:keys [data] :as command}]
  (let [{:keys [fileType filename]} data]
    (sftp-ctx/read-file (sftp-ctx/files-context command)
                        [(file-type-dir fileType) filename])))

(defn- mkdirs-in-dev
  "Try to make sure the directories exist. This is for local testing purposes. In
  production, the folders MUST exist. Fails silently."
  [ctx]
  (env/in-dev
    (try
      (sftp-ctx/make-directories ctx)
      (catch Exception e
        (info "Make SFTP directories failed:" (ex-message e))))))


(sc/defn ^:always-validate write-application :- [ssc/NonBlankStr]
  "Writes application KuntaGML, generated PDF exports and attachments into correct SFTP
    folder. XML should be validated before calling this function. `:attachments` must be
    list of maps where each item contains `:fileId` and `:filename` and the filename has
    been processed to the final format as referred to in the KuntaGML XML.  Returns a
    sequence of attachment fileIds that were provided / written."
  ([organization application options :- WriteApplicationOptions]
   (write-application (sftp-ctx/->ApplicationSftp organization application) options))
  ([ctx-param {:keys [xml ts file-suffix lang sftp-links?
                      attachments submitted-application]} :- WriteApplicationOptions]
   (let [{:keys [application organization]
          :as   ctx}   (if (sftp-ctx/context? ctx-param)
                         ctx-param
                         (sftp-ctx/->ApplicationSftp ctx-param))
         app-id        (:id application)
         xml-file-name (str (ss/join-non-blanks "_" [(:id application)
                                                     (str (or ts (now)))
                                                     file-suffix])
                            ".xml")]

     (mkdirs-in-dev ctx)

     ;; Write KuntaGML message
     (sftp-ctx/write-file ctx xml-file-name xml)

     ;; sftp-links? is a bit confusingly named. If true, it forces writing the attachments
     ;; to SFTP folder.
     (when (or sftp-links? (not (:use-attachment-links-integration organization)))
       (sftp-ctx/write-attachments ctx attachments)

       (when (and submitted-application lang)
         (files/with-temp-file submitted-file
           (pdf-export/generate submitted-application lang submitted-file)
           (sftp-ctx/write-file ctx
                                (sftp-util/get-submitted-filename app-id)
                                submitted-file))
         (files/with-temp-file current-file
           (pdf-export/generate application lang current-file)
           (sftp-ctx/write-file ctx
                                (sftp-util/get-current-filename app-id)
                                current-file))))

     (keep :fileId attachments))))

(defn cleanup-directory
  "Removes the old KuntaGML message files from the output folder. Only the messages with the
  removable kayttotapaus value will be removed. `param` can be either context, application
  or command."
  [param]
  (when-let [ctx (cond-> param
                   (not (sftp-ctx/context? param))
                   sftp-ctx/->ApplicationSftp)]
    (sftp-ctx/cleanup-directory ctx)))

(defn process-case-management-responses
  "Processes case management (asianhallinta) responses for each applicable organization and ELY. "
  []
  (doseq [cpu (sftp-ctx/case-management-processors)
          zip (sftp-ctx/list-zips cpu)]
    (logging/with-logging-context {:userId  (:ftp-user cpu)
                                   :zipfile (:name zip)}
      (let [result    (try
                        (sftp-ctx/process-message cpu (:name zip))
                        (catch Throwable e
                          (logging/log-event :error
                                             {:run-by            "Asianhallinta reader"
                                              :event             "Unable to process ah zip file"
                                              :exception-message (ex-message e)})
                          (fail :error.unknown)))
            [move-target log-type
             log-msg] (if (ok? result)
                        [:archive :info "Succesfully processed message"]
                        [:error :error "Failed to process message"])]
        (logging/log-event log-type {:run-by "Asianhallinta reader"
                                     :event  log-msg})
        (try
          (sftp-ctx/move cpu (:name zip) move-target)
          (catch Exception e (errorf e "Failed to move %s to %s: %s"
                                     (:name zip) (name move-target) (ex-message e))))))))

(sc/defn ^:always-validate write-invoicing-file
  "Writes the file `filename` to local organization invoicing SFTP folder. `input` can be
  string, input stream or file. Currently, the only use case is writing a transfer batch
  XML file. Only filenames without directory parts are supported. In other words, subdirs
  are not created. Throws if the local SFTP is not properly configured."
  [organization-or-id filename :- ssc/NonBlankStr xml]
  (let [ctx (sftp-ctx/invoicing-context organization-or-id)]
    (when-not ctx
      (throw (ex-info (str "No local invoicing SFTP configuration for "
                           (:id organization-or-id organization-or-id))
                      {})))

    (mkdirs-in-dev ctx)

    (sftp-ctx/write-file ctx (ss/trim (fs/file-name filename)) xml)))

(defn- make-user-config [ups utype]
  (->> ups
       (filter (comp ss/not-blank? :user))
       (group-by :user)
       (map (fn [[user xs]]
              {:type        utype
               :username    user
               :permitTypes (distinct (map :permit-type xs))}))))

(defn- process-directories
  "Resolves the target directories and processes them according to `sftp-type`.
  For legacy sftp, the (top-level) folders must exist. For gcs the directories are created.
  `permit-type-users` is a permit-type - user -map. If `cm?` is true, case management
  folders are targeted instead.

  Returns missing directories or nil, if OK.

  Two-arg version checks/creates invoicing directory."
  ([sftp-type permit-type-users cm?]
   (let [field {:sftpType sftp-type}
         dirs  (->> permit-type-users
                    (map (fn [[permit-type user]]
                           (if cm?
                             (sftp-ctx/case-management-directory field user :out)
                             (sftp-ctx/permit-type-directory field user permit-type))))
                    distinct)]
     (when (seq dirs)
       (if (sftp-ctx/legacy-sftp? field)
         (seq (remove fs/directory? dirs))
         (doseq [dir dirs]
           (sftp-ctx/gcs-make-dirs dir :subdirs? true :cm? cm?))))))
  ([sftp-type invoicing-user]
   (when-not (ss/blank? invoicing-user)
     (let [field {:sftpType sftp-type}
           dir   (sftp-ctx/invoicing-directory field invoicing-user)]
       (if (sftp-ctx/legacy-sftp? field)
         (when-not (fs/directory? dir)
           [dir])
         (sftp-ctx/gcs-make-dirs dir))))))

(sc/defn ^:always-validate get-organization-configuration :- SftpOrganizationConfiguration
  [organization-id :- ssc/NonBlankStr]
  (let [{:keys [sftpType krysp invoicing-config
                scope]} (mongo/by-id :organizations organization-id
                                     [:sftpType :krysp :scope :invoicing-config])
        kryspers        (make-user-config (map #(hash-map :user (some-> % second :ftpUser)
                                                          :permit-type (-> % first name) )
                                               krysp)
                                          "backing-system")
        scopers         (make-user-config  (map (fn [{:keys [caseManagement permitType]}]
                                                  {:user        (:ftpUser caseManagement)
                                                   :permit-type permitType})
                                                scope)
                                           "case-management")
        invoicer        (some->> invoicing-config
                                 :local-sftp-user
                                 (hash-map :type "invoicing" :username)
                                 list)]
    {:sftpType    (or sftpType "legacy")
     :users       (concat kryspers scopers invoicer)
     :permitTypes (->> scope (map :permitType) distinct)}))

(sc/defn ^:always-validate valid-configuration? :- sc/Bool
  "Semantic sanity checks: no ambiguity among users."
  [config :- SftpOrganizationConfiguration]
  (let [{:strs [invoicing backing-system
                case-management]} (group-by :type (:users config))]
    (and (-> (map :username invoicing) distinct count (< 2))
         (->> (concat backing-system case-management)
              (mapcat (fn [{:keys [type username permitTypes]}]
                        (map #(vector type username %) permitTypes)))
              distinct
              (group-by last)
              vals
              ;; Each permit type can have a maximum of one sftp user context.
              (every? #(= (count %) 1))))))


(sc/defn ^:always-validate update-organization-configuration :- (sc/maybe {:ok        sc/Bool
                                                                           sc/Keyword sc/Any})
  [organization-id :- ssc/NonBlankStr
   {:keys [users sftpType]} :- SftpOrganizationConfiguration]
  (let [{:keys [scope]}     (mongo/by-id :organizations organization-id [:scope])
        org-permit-types    (->> scope (map :permitType) set)
        pt-vs-user          (fn [users]
                              (->> users
                                   (mapcat (fn [{:keys [username permitTypes]}]
                                             (->> permitTypes
                                                  (filter org-permit-types)
                                                  (map #(vector % username)))))
                                   (into {})))
        {:strs [backing-system case-management
                invoicing]} (group-by :type users)
        ;; We do not give error on conflicts. The last applied wins. This should not
        ;; matter in practise.
        krysp-set           (pt-vs-user backing-system)
        krysp-unset         (util/difference-as-kw org-permit-types (keys krysp-set))
        scopers             (pt-vs-user case-management)
        scope-set           (some->> scope
                                     (map (fn [{:keys [permitType caseManagement] :as m}]
                                            (let [user (get scopers permitType)]
                                              (cond
                                                user
                                                ;; In addition to setting the ftpUser we enable case management.
                                                (assoc m :caseManagement {:enabled true
                                                                          :version (or (:version caseManagement) "1.1")
                                                                          :ftpUser user})
                                                caseManagement
                                                ;; Likewise when removing the ftpUser we disable case management.
                                                (update m :caseManagement #(-> %
                                                                               (dissoc :ftpUser)
                                                                               (assoc :enabled false)))
                                                :else m))))
                                     seq
                                     (hash-map :scope))
        invoicer            (some->> invoicing first :username ss/blank-as-nil)
        unset-map           (some->> krysp-unset
                                     (map #(vector (util/kw-path :krysp % :ftpUser) true))
                                     (into {})
                                     (merge (when-not invoicer
                                              {:invoicing-config.local-sftp-user true}))
                                     not-empty
                                     (hash-map $unset))
        set-map             (some->> krysp-set
                                     (map (fn [[permit-type user]]
                                            [(util/kw-path :krysp permit-type :ftpUser) user]))
                                     (into {:sftpType sftpType})
                                     (merge scope-set
                                            (when invoicer
                                              {:invoicing-config.local-sftp-user invoicer}))
                                     not-empty
                                     (hash-map $set))

        missing-dirs        (seq (concat (process-directories sftpType krysp-set false)
                                         (process-directories sftpType scopers true)
                                         (process-directories sftpType invoicer)))]

    (if missing-dirs
      (fail :error.sftp.directories-not-found
            :directories missing-dirs)
      (mongo/update-by-id :organizations organization-id (merge unset-map set-map)))))
