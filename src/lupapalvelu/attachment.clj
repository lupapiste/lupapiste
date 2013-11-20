(ns lupapalvelu.attachment
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [swiss-arrows.core :refer [-<> -<>>]]
            [monger.operators :refer :all]
            [sade.util :refer [fn-> fn->> future*]]
            [sade.strings :as ss]
            [sade.env :as env]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand defraw with-application executed]]
            [lupapalvelu.domain :refer [get-application-as get-application-no-access-checking]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.job :as job]
            [lupapalvelu.stamper :as stamper]
            [lupapalvelu.statement :as statement]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp])
  (:import [java.util.zip ZipOutputStream ZipEntry]
           [java.io File OutputStream FilterInputStream]))

;;
;; Constants
;;

(def default-version {:major 0, :minor 0})
(def default-type {:type-group :muut, :type-id :muu})

;;
;; Metadata
;;

(def attachment-types-osapuoli
  [:cv :tutkintotodistus :patevyystodistus])

(defn- attachment-types-R []
  (let [attachment-tree [:hakija [:valtakirja
                                  :ote_kauppa_ja_yhdistysrekisterista
                                  :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta]
                         :rakennuspaikan_hallinta [:jaljennos_myonnetyista_lainhuudoista
                                                   :jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta
                                                   :rasitustodistus
                                                   :todistus_erityisoikeuden_kirjaamisesta
                                                   :jaljennos_vuokrasopimuksesta
                                                   :jaljennos_perunkirjasta]
                         :rakennuspaikka [:ote_alueen_peruskartasta
                                          :ote_asemakaavasta_jos_asemakaava_alueella
                                          :ote_kiinteistorekisteristerista
                                          :tonttikartta_tarvittaessa
                                          :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista
                                          :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma]
                         :paapiirustus [:asemapiirros
                                        :pohjapiirros
                                        :leikkauspiirros
                                        :julkisivupiirros]
                         :ennakkoluvat_ja_lausunnot [:naapurien_suostumukset
                                                     :selvitys_naapurien_kuulemisesta
                                                     :elyn_tai_kunnan_poikkeamapaatos
                                                     :suunnittelutarveratkaisu
                                                     :ymparistolupa]]

        attachment-tree
        (if (env/feature? :rakentamisen-aikaiset-erityissuunnitelmat)
          (conj attachment-tree :rakentamisen_aikaiset [:erityissuunnitelma])
          attachment-tree)

        attachment-tree
        (if (env/feature? :architect-info)
          (conj attachment-tree :osapuolet attachment-types-osapuoli)
          attachment-tree)

        attachment-tree
        (conj attachment-tree :muut [:selvitys_rakennuspaikan_terveellisyydesta
                                     :selvitys_rakennuspaikan_korkeusasemasta
                                     :selvitys_liittymisesta_ymparoivaan_rakennuskantaan
                                     :julkisivujen_varityssuunnitelma
                                     :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta
                                     :piha_tai_istutussuunnitelma
                                     :selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta
                                     :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta
                                     :selvitys_rakennuksen_aaniteknisesta_toimivuudesta
                                     :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista
                                     :energiataloudellinen_selvitys
                                     :paloturvallisuussuunnitelma
                                     :liikkumis_ja_esteettomyysselvitys
                                     :kerrosalaselvitys
                                     :vaestonsuojasuunnitelma
                                     :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo
                                     :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo
                                     :selvitys_kiinteiston_jatehuollon_jarjestamisesta
                                     :rakennesuunnitelma
                                     :ilmanvaihtosuunnitelma
                                     :lammityslaitesuunnitelma
                                     :radontekninen_suunnitelma
                                     :kalliorakentamistekninen_suunnitelma
                                     :paloturvallisuusselvitys
                                     :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta
                                     :merkki_ja_turvavalaistussuunnitelma
                                     :sammutusautomatiikkasuunnitelma
                                     :rakennusautomaatiosuunnitelma
                                     :valaistussuunnitelma
                                     :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta
                                     :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta
                                     :muu])]
    attachment-tree))

(def attachment-types-YA
  [:yleiset-alueet [:aiemmin-hankittu-sijoituspaatos
                    :tilapainen-liikennejarjestelysuunnitelma
                    :tyyppiratkaisu
                    :tieto-kaivupaikkaan-liittyvista-johtotiedoista
                    :liitoslausunto
                    :asemapiirros
                    :rakennuspiirros
                    :suunnitelmakartta]
   :osapuolet attachment-types-osapuoli
   ;; This is needed for statement attachments to work.
   :muut [:muu]])

;;
;; Api
;;

(defn get-attachment-types-by-permit-type
  "Returns partitioned list of allowed attachment types or throws exception"
  [permit-type]
  (partition 2
    (condp = (keyword permit-type)
      :R  (attachment-types-R)
      :YA attachment-types-YA
      :P (attachment-types-R)
      (fail! "unsupported permit-type"))))

;; TODO: return attachment type based on what types of operations the given organization is having.
(defn organization-attachments [organization]
  (attachment-types-R))

(defn make-attachment [now target locked op attachement-type & [attachment-id]]
  {:id (or attachment-id (mongo/create-id))
   :type attachement-type
   :modified now
   :locked locked
   :state :requires_user_action
   :target target
   :op op
   :versions []})

(defn make-attachments
  "creates attachments with nil target"
  [now attachement-types]
  (map (partial make-attachment now nil false nil) attachement-types))

(defn create-attachment [application-id attachement-type now target locked & [attachment-id]]
  (let [attachment (make-attachment now target locked nil attachement-type attachment-id)]
    (mongo/update-by-id
      :applications application-id
      {$set {:modified now}
       $push {:attachments attachment}})
    (:id attachment)))

(defn create-attachments [application-id attachement-types now]
  (let [attachments (make-attachments now attachement-types)]
    (mongo/update-by-id
      :applications application-id
      {$set {:modified now}
       $pushAll {:attachments attachments}})
    (map :id attachments)))

(defn- next-attachment-version [{major :major minor :minor} user]
  (let [major (or major 0)
        minor (or minor 0)]
    (if (= (keyword (:role user)) :authority)
      {:major major, :minor (inc minor)}
      {:major (inc major), :minor 0})))

(defn attachment-latest-version [attachments attachment-id]
  (:version (:latestVersion (some #(when (= attachment-id (:id %)) %) attachments))))

(defn version-number
  [{{:keys [major minor]} :version}]
  (+ (* 1000 major) minor))

(defn latest-version-after-removing-file [attachments attachment-id fileId]
  (let [attachment (some #(when (= attachment-id (:id %)) %) attachments)
        versions   (:versions attachment)
        stripped   (filter #(not= (:fileId %) fileId) versions)
        sorted     (sort-by version-number stripped)
        latest     (last sorted)]
    latest))

(defn- set-attachment-version
  ([application-id attachment-id file-id filename content-type size now user stamped]
    (set-attachment-version application-id attachment-id file-id filename content-type size now user stamped 5))
  ([application-id attachment-id file-id filename content-type size now user stamped retry-limit]
    (if (pos? retry-limit)
      (when-let [application (mongo/by-id :applications application-id)]
        (let [latest-version (attachment-latest-version (application :attachments) attachment-id)
              next-version (next-attachment-version latest-version user)
              version-model {:version  next-version
                             :fileId   file-id
                             :created  now
                             :accepted nil
                             :user    (user/summary user)
                             ; File name will be presented in ASCII when the file is downloaded.
                             ; Conversion could be done here as well, but we don't want to lose information.
                             :filename filename
                             :contentType content-type
                             :size size
                             :stamped stamped}
              result-count (mongo/update-by-query
                             :applications
                             {:_id application-id
                              :attachments {$elemMatch {:id attachment-id
                                                        :latestVersion.version.major (:major latest-version)
                                                        :latestVersion.version.minor (:minor latest-version)}}}
                             {$set {:modified now
                                    :attachments.$.modified now
                                    :attachments.$.state  :requires_authority_action
                                    :attachments.$.latestVersion version-model}
                              $push {:attachments.$.versions version-model}})]
          ; Check return value and try again with new version number
          (if (pos? result-count)
            (assoc version-model :id attachment-id)
            (do
              (warn
                "Latest version of attachment %s changed before new version could be saved, retry %d time(s)."
                attachment-id retry-limit)
              (set-attachment-version application-id attachment-id file-id filename content-type size now user stamped (dec retry-limit))))))
      (do
        (error "Concurrancy issue: Could not save attachment version meta data.")
        nil))))

(defn- update-version-content [application-id attachment-id file-id size now]
  (mongo/update-by-query :applications
    {:_id application-id
     :attachments {$elemMatch {:id attachment-id}}}
    {$set {:modified now
           :attachments.$.modified now
           :attachments.$.latestVersion.fileId file-id
           :attachments.$.latestVersion.size size
           :attachments.$.latestVersion.created now}}))

(defn update-or-create-attachment
  "If the attachment-id matches any old attachment, a new version will be added.
   Otherwise a new attachment is created."
  [{:keys [application-id attachment-id attachment-type file-id filename content-type size created user target locked]}]
  (let [attachment-id (cond
                        (s/blank? attachment-id) (create-attachment application-id attachment-type created target locked)
                        (pos? (mongo/count :applications {:_id application-id :attachments.id attachment-id})) attachment-id
                        :else (create-attachment application-id attachment-type created target locked attachment-id))]
    (set-attachment-version application-id attachment-id file-id filename content-type size created user false)))

(defn parse-attachment-type [attachment-type]
  (if-let [match (re-find #"(.+)\.(.+)" (or attachment-type ""))]
    (let [[type-group type-id] (->> match (drop 1) (map keyword))]
      {:type-group type-group :type-id type-id})))

(defn- allowed-attachment-type-for? [allowed-types {:keys [type-group type-id]}]
  (if-let [types (some (fn [[group-name group-types]] (if (= group-name (name type-group)) group-types)) allowed-types)]
    (some (partial = (name type-id)) types)))

(defn get-attachment-info
  "gets an attachment from application or nil"
  [{:keys [attachments]} attachmentId]
  (first (filter #(= (:id %) attachmentId) attachments)))

(defn get-attachment-info-by-file-id
  "gets an attachment from application or nil"
  [{:keys [attachments]} file-id]
  (first
    (filter
      (fn->> :versions (some (fn-> :fileId (= file-id))))
      attachments)))

(defn attachment-file-ids
  "Gets all file-ids from attachment."
  [application attachmentId]
  (->> (get-attachment-info application attachmentId) :versions (map :fileId)))

(defn attachment-latest-file-id
  "Gets latest file-id from attachment."
  [application attachmentId]
  (->> (attachment-file-ids application attachmentId) last))

(defn file-id-in-application?
  "tests that file-id is referenced from application"
  [application attachmentId file-id]
  (let [file-ids (attachment-file-ids application attachmentId)]
    (boolean (some #{file-id} file-ids))))

(defn delete-attachment
  "Delete attachement with all it's versions. does not delete comments. Non-atomic operation: first deletes files, then updates document."
  [{:keys [id attachments] :as application} attachmentId]
  (info "1/3 deleting files of attachment" attachmentId)
  (dorun (map mongo/delete-file-by-id (attachment-file-ids application attachmentId)))
  (info "2/3 deleted files of attachment" attachmentId)
  (mongo/update-by-id :applications id {$pull {:attachments {:id attachmentId}}})
  (info "3/3 deleted meta-data of attachment" attachmentId))

(defn delete-attachment-version
  "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
  [{:keys [id attachments] :as application} attachmentId fileId]
  (let [latest-version (latest-version-after-removing-file attachments attachmentId fileId)]
    (infof "1/3 deleting file %s of attachment %s" fileId attachmentId)
    (mongo/delete-file-by-id fileId)
    (infof "2/3 deleted file %s of attachment %s" fileId attachmentId)
    (mongo/update
      :applications
      {:_id id :attachments {$elemMatch {:id attachmentId}}}
      {$pull {:attachments.$.versions {:fileId fileId}}
       $set  {:attachments.$.latestVersion latest-version}})
    (infof "3/3 deleted meta-data of file %s of attachment" fileId attachmentId)))

;;
;; Actions
;;

(defn- to-key-types-vec [r [k v]]
  (conj r {:group k :types (map (fn [v] {:name v}) v)}))

(defquery attachment-types
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [command]
  (with-application command (comp (partial ok :attachmentTypes) :allowedAttachmentTypes)))

(defcommand set-attachment-type
  {:parameters [id attachmentId attachmentType]
   :roles      [:applicant :authority]
   :states     [:draft :info :open :submitted :complement-needed]}
  [{:keys [application]}]
  (let [attachment-type (parse-attachment-type attachmentType)]
    (if (allowed-attachment-type-for? (:allowedAttachmentTypes application) attachment-type)
      (do
        (mongo/update
          :applications
          {:_id (:id application)
           :attachments {$elemMatch {:id attachmentId}}}
          {$set {:attachments.$.type attachment-type}})
        (ok))
      (do
        (errorf "attempt to set new attachment-type: [%s] [%s]: %s" id attachmentId attachment-type)
        (fail :error.attachmentTypeNotAllowed)))))

(defcommand approve-attachment
  {:description "Authority can approve attachement, moves to ok"
   :parameters  [id attachmentId]
   :roles       [:authority]
   :states      [:draft :info :open :complement-needed :submitted]}
  [{:keys [created]}]
  (mongo/update
    :applications
    {:_id id, :attachments {$elemMatch {:id attachmentId}}}
    {$set {:modified created
           :attachments.$.state :ok}}))

(defcommand reject-attachment
  {:description "Authority can reject attachement, requires user action."
   :parameters  [id attachmentId]
   :roles       [:authority]
   :states      [:draft :info :open :complement-needed :submitted]}
  [{:keys [created]}]
  (mongo/update
    :applications
    {:_id id, :attachments {$elemMatch {:id attachmentId}}}
    {$set {:modified created
           :attachments.$.state :requires_user_action}}))

(defcommand create-attachments
  {:description "Authority can set a placeholder for an attachment"
   :parameters  [:id :attachmentTypes]
   :roles       [:authority]
   :states      [:draft :info :open :complement-needed :submitted]}
  [{{application-id :id attachment-types :attachmentTypes} :data created :created}]
  (if-let [attachment-ids (create-attachments application-id attachment-types created)]
    (ok :applicationId application-id :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

(defcommand delete-attachment
  {:description "Delete attachement with all it's versions. does not delete comments. Non-atomic operation: first deletes files, then updates document."
   :parameters  [id attachmentId]
   :states      [:draft :info :open :submitted :complement-needed]}
  [{:keys [application]}]
  (delete-attachment application attachmentId)
  (ok))

(defcommand delete-attachment-version
  {:description   "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
   :parameters  [:id attachmentId fileId]
   :states      [:draft :info :open :submitted :complement-needed]}
  [{:keys [application]}]
  (if (file-id-in-application? application attachmentId fileId)
    (delete-attachment-version application attachmentId fileId)
    (fail :file_not_linked_to_the_document)))

(defn attachment-is-not-locked [{{:keys [attachmentId]} :data :as command} application]
  (when (-> (get-attachment-info application attachmentId) :locked (= true))
    (fail :error.attachment-is-locked)))

(defn if-not-authority-states-must-match [state-set]
  (fn [{user :user} {state :state}]
    (when (and
            (not= (:role user) "authority")
            (state-set (keyword state)))
      (fail :error.non-authority-viewing-application-in-verdictgiven-state))))

(defn attach-file!
  "Uploads a file to MongoDB and creates a corresponding attachment structure to application.
   Content can be a file or input-stream.
   Returns attachment version."
  [options]
  (let [file-id (mongo/create-id)
        application-id (:application-id options)
        filename (:filename options)
        content (:content options)
        user (:user options)
        sanitazed-filename (mime/sanitize-filename filename)
        content-type (mime/mime-type sanitazed-filename)
        options (merge options {:file-id file-id
                                :sanitazed-filename sanitazed-filename
                                :content-type content-type})]
    (mongo/upload file-id sanitazed-filename content-type content :application application-id)
    (update-or-create-attachment options)))

(defcommand upload-attachment
  {:parameters [id attachmentId attachmentType filename tempfile size]
   :roles      [:applicant :authority]
   :validators [attachment-is-not-locked (if-not-authority-states-must-match #{:sent :verdictGiven})]
   :input-validators [(fn [{{size :size} :data}] (when-not (pos? size) (fail :error.select-file)))
                      (fn [{{filename :filename} :data}] (when-not (mime/allowed-file? filename) (fail :error.illegal-file-type)))]
   :states     [:draft :info :open :submitted :complement-needed :answered :sent :verdictGiven]
   :description "Reads :tempfile parameter, which is a java.io.File set by ring"}
  [{:keys [created user application] {:keys [text target locked]} :data :as command}]

  (when-not (allowed-attachment-type-for? (:allowedAttachmentTypes application) attachmentType) (fail! :error.illegal-attachment-type))

  (when (= (:type target) "statement")
    (when-let [validation-error (statement/statement-owner (assoc-in command [:data :statementId] (:id target)) application)]
      (fail! (:text validation-error))))

  (if-let [attachment-version (attach-file! {:application-id id 
                                             :filename filename
                                             :size size
                                             :content tempfile
                                             :attachment-id attachmentId
                                             :attachment-type attachmentType
                                             :target target
                                             :locked locked 
                                             :user user 
                                             :created created})]
    ; FIXME try to combine mongo writes
    (executed "add-comment"
      (-> command
        (assoc :data {:id id
                      :text text,
                      :type :system
                      :target {:type :attachment
                               :id (:id attachment-version)
                               :version (:version attachment-version)
                               :filename (:filename attachment-version)
                               :fileId (:fileId attachment-version)}})))
    (fail :error.unknown)))


(defn- get-data-argument-for-attachments-mongo-update [timestamp attachments]
  (reduce
    (fn [data-map attachment]
      (conj data-map {(keyword (str "attachments." (count data-map) ".sent")) timestamp}))
    {}
    attachments))

(defcommand move-attachments-to-backing-system
  {:parameters [id lang]
   :roles      [:authority]
   :validators [(if-not-authority-states-must-match #{:verdictGiven})]
   :states     [:verdictGiven]
   :description "Sends such attachments to backing system that are not yet sent."}
  [{:keys [created user application] :as command}]

  (let [attachments-wo-sent-timestamp (filter
                                        #(and
                                           (pos? (-> % :versions count))
                                           (or
                                             (not (:sent %))
                                             (> (-> % :versions last :created) (:sent %)))
                                           (not (= "statement" (-> % :target :type)))
                                           (not (= "verdict" (-> % :target :type))))
                                        (:attachments application))]
    (if (pos? (count attachments-wo-sent-timestamp))

      (let [organization (mongo/by-id :organizations (:organization application))]
        (try
          (mapping-to-krysp/save-unsent-attachments-as-krysp
            (-> application
              (dissoc :attachments)
              (assoc :attachments attachments-wo-sent-timestamp))
            lang
            organization
            user)

          (ok :updateCount (mongo/update-by-query
                             :applications
                             {:_id id}
                             {$set (get-data-argument-for-attachments-mongo-update
                                     created
                                     (:attachments application))}))

          (catch Exception e
            (errorf e "failed to save unsent attachments as krysp: application=%s" id)
            (fail :error.sending-unsent-attachments-failed))))

      (ok :updateCount 0))))


;;
;; Download
;;

(defn get-attachment-as
  "Returns the attachment if user has access to application, otherwise nil."
  [user file-id]
  (when-let [attachment (mongo/download file-id)]
    (when-let [application (get-application-as (:application attachment) user)]
      (when (seq application) attachment))))

(defn get-attachment
  "Returns the attachment without access checking, otherwise nil."
  [file-id]
  (when-let [attachment (mongo/download file-id)]
    (when-let [application (get-application-no-access-checking (:application attachment))]
      (when (seq application) attachment))))

(defn output-attachment
  [attachment-id download? attachment-fn]
  (debugf "file download: attachment-id=%s" attachment-id)
  (if-let [attachment (attachment-fn attachment-id)]
    (let [response {:status 200
                    :body ((:content attachment))
                    :headers {"Content-Type" (:content-type attachment)
                              "Content-Length" (str (:content-length attachment))}}]
      (if download?
        (assoc-in response
          [:headers "Content-Disposition"]
          (format "attachment;filename=\"%s\"" (ss/encode-filename (:file-name attachment))))
        response))
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

(defn- output-attachment-if-logged-in [attachment-id download? user]
  (if user
    (output-attachment attachment-id download? (partial get-attachment-as user))
    {:status 401
     :headers {"Content-Type" "text/plain"}
     :body "401 Unauthorized"}))

(defraw "view-attachment"
  {:parameters [:attachment-id]}
  [{{:keys [attachment-id]} :data user :user}]
  (output-attachment-if-logged-in attachment-id false user))

(defraw "download-attachment"
  {:parameters [:attachment-id]}
  [{{:keys [attachment-id]} :data user :user}]
  (output-attachment-if-logged-in attachment-id true user))

(defn- append-gridfs-file [zip filename file-id]
  (when file-id
    (.putNextEntry zip (ZipEntry. (ss/encode-filename (str file-id "_" filename))))
    (with-open [in ((:content (mongo/download file-id)))]
      (io/copy in zip))))

(defn- append-stream [zip filename in]
  (when in
    (.putNextEntry zip (ZipEntry. (ss/encode-filename filename)))
    (io/copy in zip)))

(defn- append-attachment [zip {:keys [filename fileId]}]
  (append-gridfs-file zip filename fileId))

(defn- get-all-attachments [application lang]
  (let [temp-file (File/createTempFile "lupapiste.attachments." ".zip.tmp")]
    (debugf "Created temporary zip file for attachments: %s" (.getAbsolutePath temp-file))
    (with-open [out (io/output-stream temp-file)]
      (let [zip (ZipOutputStream. out)]
        ; Add all attachments:
        (doseq [attachment (:attachments application)]
          (append-attachment zip (-> attachment :versions last)))
        ; Add submitted PDF, if exists:
        (when-let [submitted-application (mongo/by-id :submitted-applications (:id application))]
          (append-stream zip (i18n/loc "attachment.zip.pdf.filename.submitted") (ke6666/generate submitted-application lang)))
        ; Add current PDF:
        (append-stream zip (i18n/loc "attachment.zip.pdf.filename.current") (ke6666/generate application lang))
        (.finish zip)))
    temp-file))

(defn- temp-file-input-stream [^File file]
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defraw "download-all-attachments"
  {:parameters [:id]}
  [{:keys [application lang]}]
  (if application
    {:status 200
       :headers {"Content-Type" "application/octet-stream"
                 "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
       :body (temp-file-input-stream (get-all-attachments application lang))}
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

;;
;; Stamping:
;;

(defn- stampable? [attachment]
  (let [latest       (-> attachment :versions last)
        content-type (:contentType latest)
        stamped      (:stamped latest)]
    (and (not stamped) (or (= "application/pdf" content-type) (ss/starts-with content-type "image/")))))

(defn- loc-organization-name [organization]
  (get-in organization [:name i18n/*lang*] (str "???ORG:" (:id organization) "???")))

(defn- get-organization-name [application-id]
  (-<> application-id
       (mongo/by-id :applications <> [:organization])
       (:organization)
       (mongo/by-id :organizations <> [:name])
       (loc-organization-name <>)))

(defn- key-by [f coll]
  (into {} (for [e coll] [(f e) e])))

(defn ->long [v]
  (if (string? v) (Long/parseLong v) v))

(defn- ->file-info [attachment]
  (let [versions   (-> attachment :versions reverse)
        re-stamp?  (:stamped (first versions))
        source     (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size])
           :re-stamp? re-stamp?
           :attachment-id (:id attachment))))

(defn- add-stamp-comment [new-version new-file-id file-info context]
  ; mea culpa, but what the fuck was I supposed to do
  (mongo/update-by-id :applications (:application-id context)
    {$set {:modified (:created context)}
     $push {:comments {:text    (i18n/loc (if (:re-stamp? file-info) "stamp.comment.restamp" "stamp.comment"))
                       :created (:created context)
                       :user    (:user context)
                       :target  {:type "attachment"
                                 :id (:attachment-id file-info)
                                 :version (:version new-version)
                                 :filename (:filename file-info)
                                 :fileId new-file-id}}}}))

(defn- stamp-attachment! [stamp file-info context]
  (let [{:keys [application-id user created]} context
        {:keys [attachment-id contentType fileId filename re-stamp?]} file-info
        temp-file (File/createTempFile "lupapiste.stamp." ".tmp")
        new-file-id (mongo/create-id)]
    (debug "created temp file for stamp job:" (.getAbsolutePath temp-file))
    (with-open [in ((:content (mongo/download fileId)))
                out (io/output-stream temp-file)]
      (stamper/stamp stamp contentType in out (:x-margin context) (:y-margin context) (:transparency context)))
    (mongo/upload new-file-id filename contentType temp-file :application application-id)
    (let [new-version (if re-stamp?
                        (update-version-content application-id attachment-id new-file-id (.length temp-file) created)
                        (set-attachment-version application-id attachment-id new-file-id filename contentType (.length temp-file) created user true))]
      (add-stamp-comment new-version new-file-id file-info context))
    (try (.delete temp-file) (catch Exception _))))

(defn- stamp-attachments! [file-infos {:keys [user created job-id application-id] :as context}]
  (let [stamp (stamper/make-stamp
                (i18n/loc "stamp.verdict")
                created
                (str (:firstName user) \space (:lastName user))
                (get-organization-name application-id)
                (:transparency context))]
    (doseq [file-info (vals file-infos)]
      (try
        (job/update job-id assoc (:attachment-id file-info) :working)
        (stamp-attachment! stamp file-info context)
        (job/update job-id assoc (:attachment-id file-info) :done)
        (catch Exception e
          (errorf e "failed to stamp attachment: application=%s, file=%s" application-id (:fileId file-info))
          (job/update job-id assoc (:attachment-id file-info) :error))))))

(defn- stamp-job-status [data]
  (if (every? #{:done :error} (vals data)) :done :runnig))

(defn- make-stamp-job [file-infos context]
  (let [job (job/start (zipmap (keys file-infos) (repeat :pending)) stamp-job-status)]
    (future* (stamp-attachments! file-infos (assoc context :job-id (:id job))))
    job))

(defcommand stamp-attachments
  {:parameters [:id :files :xMargin :yMargin]
   :roles      [:authority]
   :states     [:verdictGiven]
   :description "Stamps all attachments of given application"}
  [{data :data :as command}]
  (with-application command
    (fn [application]
      (ok :job (make-stamp-job
                 (key-by :attachment-id (map ->file-info (filter (comp (set (:files data)) :id) (:attachments application))))
                 {:application-id (:id application)
                  :user (:user command)
                  :created (:created command)
                  :x-margin (->long (:xMargin data))
                  :y-margin (->long (:yMargin data))
                  :transparency (->long (or (:transparency data) 0))})))))

(defquery stamp-attachments-job
  {:parameters [:job-id :version]
   :roles      [:authority]
   :description "Returns state of stamping job"}
  [{{job-id :job-id version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (assoc (job/status job-id (->long version) (->long timeout)) :ok true))
