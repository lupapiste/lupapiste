(ns lupapalvelu.sftp.context
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.backing-system.asianhallinta.reader :as ah-reader]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit :refer [PermitType]]
            [lupapalvelu.sftp.schemas :refer [SftpTypeField FileEntry FileStream SftpUser]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xml.disk-writer :as disk-writer]
            [lupapalvelu.xml.gcs-writer :as gcs-writer]
            [monger.operators :refer :all]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.files :as files]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema.core :as sc])
  (:import [java.io InputStream  File FileInputStream]))

(def CASE-MANAGEMENT     "asianhallinta")
(def CASE-MANAGEMENT-OUT "from_lupapiste")
(def CASE-MANAGEMENT-IN  "to_lupapiste")
(def REMOVABLES "Kayttotapaus values for removable KuntaGML messages."
  #{"Rakentamisen aikainen muutos"
    "Uuden työnjohtajan nimeäminen"
    "Uuden suunnittelijan nimeäminen"
    "Jatkoaikahakemus"
    "Uusi aloitusoikeus"
    "Uusi maisematyöhakemus"
    "Uusi hakemus"
    "Uusi poikkeamisasia"
    "Uusi suunnittelutarveasia"})

(def ELY-CONFIG {:version  "1.3"
                 :sftpType (if (env/value :ely :gcs-sftp?) "gcs" "legacy")
                 :ftpUser  (env/value :ely :sftp-user)})

(def ARCHIVE   "archive")
(def ERROR     "error")
(def INVOICING "laskutus")

(defprotocol FileSftp
  (write-file [this filepath input]
    "Writes the given `input` (string, stream or file) into `filepath` (string or list). The
    path must be relative and is resolved against the context directory. Subfolders are
    automatically created.")
  (make-directories [this]
    "Creates all the needed directories for this context. Depending on the context type
    this could mean anything from a single directory to a whole directory tree. Note that
    in production, the directories should exist. They are created during the organisation
    configuration at the latest. However, automatic creation does not hurt and helps
    testing."))

(defprotocol ApplicationSftp
  (list-files [this regex] [this subdir-or-dirs regex]
    "Returns a list of `FileEntry` items for files that locate in `subdir-or-dirs` and
    match `regex` pattern. `subdir-or-dirs` can be either string or list of subdirs. Empty
    lis / blank string is allowed. Note only regular files (not directories) are
    considered. The arity without subdirs filters the filenames only in the context
    directory.")
  (read-file [this filepath-or-paths]
    "Returns `FileStream` for `filepath-or-paths` that is resolved against context
    path. Note: the `:stream` must be closed by the caller.")
  (cleanup-directory [this]
    "Removes old application KuntaGML files from the SFTP folder. A file is removed only
    if its kayttotapaus is deemed removable.")
  (write-attachments [this attachments]
    "Writes `attachments` to context directory."))

(sc/defn ^:always-validate gcs-sftp? :- sc/Bool
  [organization :- SftpTypeField]
  (boolean (some-> (force organization) :sftpType (= "gcs"))))

(def legacy-sftp? (complement gcs-sftp?))

(defn- directory-exists? [organization path]
  (if (legacy-sftp? organization)
    (fs/directory? path)
    (gcs-writer/file-exists? (str path "/"))))

(defn- resolve-directory-path
  [organization & parts]
  (ss/join-file-path (when (legacy-sftp? organization)
                       (env/value :outgoing-directory))
                     parts))

(sc/defn ^:always-validate permit-type-directory :- ssc/NonBlankStr
  [m :- SftpTypeField user :- SftpUser permit-type :- PermitType]
  (resolve-directory-path m user (permit/get-sftp-directory permit-type)))

(sc/defn ^:always-validate case-management-directory :- ssc/NonBlankStr
  [m :- SftpTypeField user :- SftpUser direction :- (sc/enum :in :out)]
  (resolve-directory-path m user CASE-MANAGEMENT
                          (if (= direction :in)
                            CASE-MANAGEMENT-IN
                            CASE-MANAGEMENT-OUT)))

(sc/defn ^:always-validate invoicing-directory :- ssc/NonBlankStr
  [m :- SftpTypeField user :- SftpUser]
  (resolve-directory-path m user INVOICING))

(defn- ftp-user [{:keys [ftpUser version]}]
  (when (every? ss/not-blank? [ftpUser version])
    (ss/trim ftpUser)))

(sc/defn ^:private ^:always-validate context-properties
  :- (sc/maybe {:user      ssc/NonBlankStr
                :directory ssc/NonBlankStr
                :cm?       sc/Bool})
  [{:keys [krysp scope] :as organization} permit-type :- PermitType]
  (let [{:keys [enabled]
         :as   cm}  (:caseManagement (util/find-by-key :permitType
                                                       permit-type
                                                       scope))
        cm-ftp-user (ftp-user cm)]
    (cond
      (and enabled (not cm-ftp-user))
      nil

      enabled
      {:user      cm-ftp-user
       :directory (case-management-directory organization cm-ftp-user :out)
       :cm?       true}

      :else
      (when-let [user (ftp-user (get krysp (keyword permit-type)))]
        {:user      user
         :directory (permit-type-directory organization user permit-type)
         :cm?       false}))))

(defn- directory-factory
  "Overly generic directory maker. `mkdirs-fn` is a function that receives a path and
  creates the whole directory tree. `directory` is the _output_ directory. If `subdirs?`
  option is given the defaulta archive and error directories are created as well. If `cm?`
  flag is given, the sibling case management `to_lupapiste` directory (and its subdirs) is
  created."
  [mkdirs-fn directory & {:keys [cm? subdirs?]}]
  (let [cm-in-dir (when cm?
                    (ss/join-file-path (str (fs/parent directory)) CASE-MANAGEMENT-IN))
        subdirs   (cond-> [nil]
                    subdirs? (concat [ARCHIVE ERROR]))]
    (doseq [subdir subdirs]
      (mkdirs-fn (ss/join-file-path directory subdir))
      (when cm-in-dir
        (mkdirs-fn (ss/join-file-path cm-in-dir subdir))))))

(def fs-make-dirs (partial directory-factory fs/create-dirs))
(def gcs-make-dirs (partial directory-factory #(gcs-writer/create-directory % true)))

(defn- should-remove? [is-or-path]
  (try
    (some-> is-or-path
            slurp
            (xml/parse-string "utf-8")
            strip-xml-namespaces
            (xml/get-text [:RakennusvalvontaAsia :kayttotapaus])
            REMOVABLES)
    (catch Exception _)))

(defn- safe-filepath?
  "Relative filepath with no .. traversal."
  [s]
  (not (or (ss/blank? s)
           (fs/absolute? s)
           (some #(= % "..") (ss/split (ss/trim s) #"/+")))))

(defn- make-safe! [& parts]
  (let [f (ss/join-file-path parts)]
    (if (safe-filepath? f)
      f
      (throw (ex-info (str "Unsafe filename: " f)
                      {:params parts})))))

(sc/defn ^:always-validate file-entry :- FileEntry
  [source]
  (cond
    (:contentType source)
    {:name         (:fileId source)
     :content-type (:contentType source)
     :size         (:size source)
     :modified     (:modified source)}

    (or (string? source) (instance? java.io.File source))
    (let [f (fs/file source)]
      (when (fs/regular-file? f)
        (let [filename (fs/file-name f)]
          {:name         filename
           :size         (fs/size f)
           :content-type (mime/mime-type filename)
           :modified     (-> f fs/last-modified-time fs/file-time->millis)})))))

(defn- safe-subdirs-join [directory subdirs]
  (if (ss/blank? (ss/join-file-path subdirs))
    directory
    (ss/join-file-path directory (make-safe! subdirs))))

(sc/defn ^:always-validate fs-list-files :- [FileEntry]
  ([directory :- ssc/NonBlankStr subdirs regex :- sc/Regex]
   (let [directory (safe-subdirs-join directory subdirs)]
     (for [f     (fs/list-dir directory #(re-matches regex (fs/file-name %)))
           :when (fs/regular-file? f)]
       (file-entry (fs/file f)))))
  ([directory :- ssc/NonBlankStr regex :- sc/Regex]
   (fs-list-files directory nil regex)))

(sc/defn ^:always-validate fs-read-file :- (sc/maybe FileStream)
  [filepath :- ssc/NonBlankStr]
  (when-let [f (fs/file filepath)]
    (assoc (file-entry f) :stream (FileInputStream. f))))

(sc/defn ^:always-validate gcs-list-files :- [FileEntry]
  ([directory :- ssc/NonBlankStr subdirs regex :- sc/Regex]
   (let [directory (safe-subdirs-join directory subdirs)]
     (for [fd (gcs-writer/list-files directory regex)]
       (file-entry fd))))
  ([directory :- ssc/NonBlankStr regex :- sc/Regex]
   (gcs-list-files directory nil regex)))

(sc/defn ^:always-validate gcs-read-file :- FileStream
  [filepath :- ssc/NonBlankStr]
  (let [{:keys [content] :as fd} (gcs-writer/get-file filepath)]
    (assoc (file-entry fd)
           :stream (content))))

(defn- good-input?
  "An 'writable input' is either an input stream, relative file or non-blank string."
  [input]
  (or (instance? InputStream input)
      (and (instance? File input)
           (fs/regular-file? input))
      (and (string? input) (ss/not-blank? input))))

(defrecord LegacyApplicationSftp [organization application user directory cm?]
  FileSftp
  (write-file [_ filepath input]
    {:pre [(fs/relative? filepath)
           (good-input? input)]}
    (disk-writer/write-file (ss/join-file-path directory filepath)
                            input))
  (make-directories [_]
    (fs-make-dirs directory :subdirs? true :cm? cm?))

  ApplicationSftp
  (list-files [_ subdir-or-dirs regex]
    (fs-list-files directory (flatten [subdir-or-dirs]) regex))
  (list-files [_ regex]
    (fs-list-files directory [] regex))
  (read-file [_ filepath-or-paths]
    (fs-read-file (ss/join-file-path directory (make-safe! filepath-or-paths))))
  (cleanup-directory [_]
    (disk-writer/cleanup-output-dir (:id application) directory should-remove?))
  (write-attachments [_ attachments]
    (disk-writer/write-attachments application attachments directory)))

(defrecord GcsApplicationSftp [organization application user directory cm?]
  FileSftp
  (write-file [_ filepath input]
    {:pre [(fs/relative? filepath)
           (good-input? input)]}
    (gcs-writer/write-file (ss/join-file-path directory filepath)
                           input))
  (make-directories [_]
    (gcs-make-dirs directory :subdirs? true :cm? cm?))

  ApplicationSftp
  (list-files [_ subdir-or-dirs regex]
    (gcs-list-files directory (flatten [subdir-or-dirs]) regex))
  (list-files [_ regex]
    (gcs-list-files directory [] regex))
  (read-file [_ filepath-or-paths]
    (gcs-read-file (ss/join-file-path directory (make-safe! filepath-or-paths))))
  (cleanup-directory [_]
    (gcs-writer/cleanup-output-dir (:id application) directory should-remove?))
  (write-attachments [_ attachments]
    (gcs-writer/write-attachments (:id application) attachments directory)))

(defn ->ApplicationSftp
  ([organization application]
   (when-let [{:keys [user directory cm?]} (context-properties organization (:permitType application))]
     ((if (legacy-sftp? organization)
        ->LegacyApplicationSftp
        ->GcsApplicationSftp) organization application user directory cm?)))
  ([{:keys [organization application] :as command-or-application}]
   (let [organization (if application
                        (force organization)
                        (mongo/by-id :organizations organization))
         application  (or application command-or-application)]
     (->ApplicationSftp organization application))))


(defn context? [v]
  (some #(satisfies? % v) [FileSftp]))

(def Context (sc/pred context? "Valid SFTP context"))

(sc/defn ^:private ^:always-validate context :- Context
  [context-type :- (sc/enum :default :case-management :files) & args]
  (let [cm-check (fn [flag ctx]
                   (when (= (:cm? ctx) flag)
                     ctx))]
    (case context-type
      :default         (cm-check false (apply ->ApplicationSftp args))
      :case-management (cm-check true (apply ->ApplicationSftp args))
      :files           (apply ->ApplicationSftp args))))

(defn default-context
  ([command-or-application]
   (context :default command-or-application))
  ([organization application]
   (context :default organization application)))

(defn files-context
  ([command-or-application]
   (context :files command-or-application))
  ([organization application]
   (context :files organization application)))

(defn ely-context [organization application]
  (let [user         (ftp-user ELY-CONFIG)
        organization (force organization)
        directory    (resolve-directory-path ELY-CONFIG
                                             user
                                             CASE-MANAGEMENT
                                             CASE-MANAGEMENT-OUT)]
    ((if (legacy-sftp? ELY-CONFIG)
       ->LegacyApplicationSftp
       ->GcsApplicationSftp) organization application user directory true)))

(sc/defn ^:always-validate target-directory
  [directory :- ssc/NonBlankStr target :- (sc/enum :archive :error)]
  (ss/join-file-path directory (if (= target :archive) ARCHIVE ERROR)))

(defprotocol CaseManagementProcessor
  (list-zips [this]
    "Returns zip-file `FileEntry` maps.")
  (process-message [this zip-name]
    "Reads in case management response from `zip-name` file in the processor directory.")
  (move [this zip-name target]
    "Move file `zip-name` from the processor directory to subdirectory defined by
    `target` (`:archive` or `:error`)."))

(defn processor? [v]
  (satisfies? CaseManagementProcessor v))

(def zip-regex #"(?i)^.*\.zip$")

(defrecord LegacyProcessor [user ftp-user directory]
  CaseManagementProcessor
  (list-zips [_]
    (fs-list-files directory zip-regex))
  (process-message [_ zip-name]
    {:pre [(fs/relative? zip-name)]}
    (ah-reader/process-message (ss/join-file-path directory zip-name) ftp-user user))
  (move [_ zip-name target]
    {:pre [(fs/relative? zip-name)]}
    (fs/move (ss/join-file-path directory zip-name)
             (target-directory directory target)
             {:replace-existing true})))

(defrecord GcsProcessor [user ftp-user directory]
  CaseManagementProcessor
  (list-zips [_]
    (gcs-list-files directory nil zip-regex))
  (process-message [_ zip-name]
    {:pre [(fs/relative? zip-name)]}
    (files/with-temp-file tmp
      (with-open [stream (-> (ss/join-file-path directory zip-name)
                             gcs-read-file
                             :stream)]
        (io/copy stream tmp))
      (ah-reader/process-message tmp ftp-user user)))
  (move [_ zip-name target]
    {:pre [(fs/relative? zip-name)]}
    (gcs-writer/move-file (ss/join-file-path directory zip-name)
                          (target-directory directory target))))

(sc/defn case-management-processors :- (sc/pred processor? "Case management processor")
  []
  (let [orgs         (mongo/select :organizations
                                   {:deactivated {$ne true}
                                    :scope       {$elemMatch {:caseManagement.enabled true
                                                              :caseManagement.ftpUser #"\S+"}}}
                                   [:sftpType :scope])
        user         (usr/batchrun-user (map :id orgs))
        ps-datas     (->> orgs
                          (mapcat (fn [{:keys [scope] :as org}]
                                    (for [{cm :caseManagement
                                           }    scope
                                          :let  [ftp-user (some-> cm :ftpUser ss/blank-as-nil)
                                                 dir      (when ftp-user
                                                            (resolve-directory-path org ftp-user
                                                                                    CASE-MANAGEMENT
                                                                                    CASE-MANAGEMENT-IN))]
                                          :when (and (:enabled cm)
                                                     ftp-user
                                                     (directory-exists? org dir))]
                                      [ftp-user (gcs-sftp? org) dir])))
                          distinct)
        ely-ftp-user (some-> ELY-CONFIG :ftpUser ss/blank-as-nil)
        ely-dir      (when ely-ftp-user
                       (resolve-directory-path ELY-CONFIG
                                               ely-ftp-user
                                               CASE-MANAGEMENT
                                               CASE-MANAGEMENT-IN))]
    (for [[ftp-user gcs? dir] (cond->> ps-datas
                                (and ely-ftp-user (directory-exists? ELY-CONFIG ely-dir))
                                (cons [ely-ftp-user (gcs-sftp? ELY-CONFIG) ely-dir]))]
      ((if gcs?
         ->GcsProcessor
         ->LegacyProcessor) user ftp-user dir))))


(defrecord LegacyInvoicingSftp [user directory]
  FileSftp
  (write-file [_ filepath input]
    {:pre [(fs/relative? filepath)
           (good-input? input)]}
    (disk-writer/write-file (ss/join-file-path directory filepath)
                            input))
  (make-directories [_]
    (fs-make-dirs directory)))

(defrecord GcsInvoicingSftp [user directory]
  FileSftp
  (write-file [_ filepath input]
    {:pre [(fs/relative? filepath)
           (good-input? input)]}
    (gcs-writer/write-file (ss/join-file-path directory filepath)
                           input))
  (make-directories [_]
    (gcs-make-dirs directory)))

(defn- invoicing-ftp-user
  "The SFTP user is firstly taken from invoicing config (`:local-sftp-user`) and if not
  defined falls back to the first active `ftpUser` for any invoicing scope."
  [{:keys [krysp scope invoicing-config]}]
  (let [{:keys [local-sftp? local-sftp-user]} invoicing-config]
    (when local-sftp?
      (or (some-> local-sftp-user ss/blank-as-nil ss/trim)
          (some->> scope
                   (filter :invoicing-enabled)
                   ;; Unlike in `ftp-user` function, we do not care about the version.
                   (some (fn [{pt :permitType cm :caseManagement}]
                           (if (:enabled cm)
                             (:ftpUser cm)
                             (get-in krysp [(keyword pt) :ftpUser]))))
                   ss/blank-as-nil
                   ss/trim)))))

(sc/defn ^:always-validate invoicing-context :- (sc/maybe Context)
  "Invoicing SFTP context for the given `organization`. Returns nil if the context is not
  fully configured (enabled and user)."
  [organization-or-id]
  (let [organization (if (string? organization-or-id)
                       (mongo/by-id :organizations organization-or-id)
                       (force organization-or-id))]
    (when-let [user (invoicing-ftp-user organization)]
      ((if (gcs-sftp? organization)
         ->GcsInvoicingSftp
         ->LegacyInvoicingSftp) user (invoicing-directory organization user)))))
