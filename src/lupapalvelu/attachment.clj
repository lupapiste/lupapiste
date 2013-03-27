(ns lupapalvelu.attachment
  (:use [monger.operators]
        [lupapalvelu.core]
        [clojure.tools.logging]
        [lupapalvelu.domain :only [get-application-as application-query-for]]
        [clojure.string :only [split join trim]])
  (:require [clojure.java.io :as io]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [sade.strings :as strings]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.i18n :as i18n])
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

(def attachment-types [:hakija [:valtakirja
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
                                                   :ymparistolupa]
                       :muut [:selvitys_rakennuspaikan_terveellisyydesta
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
                              :muu]])

(defn municipality-attachments [municipality]
  attachment-types)

(defn make-attachment [now attachement-type]
  {:id (mongo/create-id)
   :type attachement-type
   :modified now
   :state :requires_user_action
   :versions []})

(defn make-attachments [now attachement-types]
  (map (partial make-attachment now) attachement-types))

(defn create-attachment [application-id attachement-type now]
  (let [attachment (make-attachment now attachement-type)]
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
  ([application-id attachment-id file-id filename content-type size now user]
    (set-attachment-version application-id attachment-id file-id filename content-type size now user 5))
  ([application-id attachment-id file-id filename content-type size now user retry-limit]
    (if (pos? retry-limit)
      (when-let [application (mongo/by-id :applications application-id)]
        (let [latest-version (attachment-latest-version (application :attachments) attachment-id)
              next-version (next-attachment-version latest-version user)
              version-model {:version  next-version
                             :fileId   file-id
                             :created  now
                             :accepted nil
                             :user    (security/summary user)
                             ; File name will be presented in ASCII when the file is downloaded.
                             ; Conversion could be done here as well, but we don't want to lose information.
                             :filename filename
                             :contentType content-type
                             :size size}
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
              (set-attachment-version application-id attachment-id file-id filename content-type size now user (dec retry-limit))))))
      (do
        (error "Concurrancy issue: Could not save attachment version meta data.")
        nil))))

(defn update-or-create-attachment [id attachment-id attachement-type file-id filename content-type size created user]
  (let [attachment-id (if (empty? attachment-id)
                        (create-attachment id attachement-type created)
                        attachment-id)]
    (set-attachment-version id attachment-id file-id filename content-type size created user)))

(defn parse-attachment-type [attachment-type]
  (if-let [match (re-find #"(.+)\.(.+)" (or attachment-type ""))]
    (let [[type-group type-id] (->> match (drop 1) (map keyword))]
      {:type-group type-group :type-id type-id})))

(defn- allowed-attachment-type-for? [allowed-types {:keys [type-group type-id]}]
  (if-let [types (some (fn [[group-name group-types]] (if (= group-name (name type-group)) group-types)) allowed-types)]
    (some (partial = (name type-id)) types)))

(defn attachment-file-ids
  "Gets all file-ids from attachment."
  [{:keys [attachments]} attachmentId]
  (let [attachment (first (filter #(= (:id %) attachmentId) attachments))
        versions   (:versions attachment)
        file-ids   (map :fileId versions)]
    file-ids))

(defn file-id-in-application?
  "tests that file-id is referenced from application"
  [application attachmentId file-id]
  (let [file-ids (attachment-file-ids application attachmentId)]
    (if (some #{file-id} file-ids) true false)))

(defn delete-attachment
  "Delete attachement with all it's versions. does not delete comments. Non-atomic operation: first deletes files, then updates document."
  [{:keys [id attachments] :as application} attachmentId]
  (info "1/3 deleting files of attachment" attachmentId)
  (dorun (map mongo/delete-file (attachment-file-ids application attachmentId)))
  (info "2/3 deleted files of attachment" attachmentId)
  (mongo/update-by-id :applications id {$pull {:attachments {:id attachmentId}}})
  (info "3/3 deleted meta-data of attachment" attachmentId))

(defn delete-attachment-version
  "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
  [{:keys [id attachments] :as application} attachmentId fileId]
  (let [latest-version (latest-version-after-removing-file attachments attachmentId fileId)]
    (infof "1/3 deleting file %s of attachment %s" fileId attachmentId)
    (mongo/delete-file fileId)
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

(defquery "attachment-types"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [command]
  (with-application command (comp (partial ok :attachmentTypes) :allowedAttachmentTypes)))

(defcommand "set-attachment-type"
  {:parameters [:id :attachmentId :attachmentType]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed]}
  [{{:keys [id attachmentId attachmentType]} :data :as command}]
  (with-application command
    (fn [application]
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
            (fail :error.attachmentTypeNotAllowed)))))))

(defcommand "approve-attachment"
  {:description "Authority can approve attachement, moves to ok"
   :parameters  [:id :attachmentId]
   :roles       [:authority]
   :states      [:draft :open :complement-needed :submitted]}
  [{{:keys [attachmentId]} :data created :created :as command}]
  (with-application command
    (fn [{id :id}]
      (mongo/update
        :applications
        {:_id id, :attachments {$elemMatch {:id attachmentId}}}
        {$set {:modified (:created command)
               :attachments.$.state :ok}}))))

(defcommand "reject-attachment"
  {:description "Authority can reject attachement, requires user action."
   :parameters  [:id :attachmentId]
   :roles       [:authority]
   :states      [:draft :open :complement-needed :submitted]}
  [{{:keys [attachmentId]} :data created :created :as command}]
  (with-application command
    (fn [{id :id}]
      (mongo/update
        :applications
        {:_id id, :attachments {$elemMatch {:id attachmentId}}}
        {$set {:modified (:created command)
               :attachments.$.state :requires_user_action}}))))

(defcommand "create-attachments"
  {:description "Authority can set a placeholder for an attachment"
   :parameters  [:id :attachmentTypes]
   :roles       [:authority]
   :states      [:draft :open :complement-needed]}
  [{{application-id :id attachment-types :attachmentTypes} :data created :created}]
  (if-let [attachment-ids (create-attachments application-id attachment-types created)]
    (ok :applicationId application-id :attachmentIds attachment-ids)
    (fail :error.attachment-placeholder)))

(defcommand "delete-attachment"
  {:description "Delete attachement with all it's versions. does not delete comments. Non-atomic operation: first deletes files, then updates document."
   :parameters  [:id :attachmentId]
   :states      [:draft :open :complement-needed]}
  [{{:keys [id attachmentId]} :data :as command}]
  (with-application command
    (fn [application]
      (delete-attachment application attachmentId)
      (ok))))

(defcommand "delete-attachment-version"
  {:description   "Delete attachment version. Is not atomic: first deletes file, then removes application reference."
   :parameters  [:id :attachmentId :fileId]
   :states      [:draft :open :complement-needed]}
  [{{:keys [id attachmentId fileId]} :data :as command}]
  (with-application command
    (fn [application]
      (if (file-id-in-application? application attachmentId fileId)
        (delete-attachment-version application attachmentId fileId)
        (fail :file_not_linked_to_the_document)))))

(defcommand "upload-attachment"
  {:parameters [:id :attachmentId :attachmentType :filename :tempfile :size]
   :roles      [:applicant :authority]
   :states     [:draft :open :complement-needed :answered]}
  [{:keys [created user] {:keys [id attachmentId attachmentType filename tempfile size text]} :data :as command}]
  (debugf "Create GridFS file: id=%s attachmentId=%s attachmentType=%s filename=%s temp=%s size=%d text=\"%s\"" id attachmentId attachmentType filename tempfile size text)
  (let [file-id (mongo/create-id)
        sanitazed-filename (strings/suffix (strings/suffix filename "\\") "/")]
    (if (mime/allowed-file? sanitazed-filename)
      (if-let [application (mongo/by-id :applications id)]
        (if (allowed-attachment-type-for? (:allowedAttachmentTypes application) attachmentType)
          (let [content-type (mime/mime-type sanitazed-filename)]
            (mongo/upload id file-id sanitazed-filename content-type tempfile created)
            (.delete (io/file tempfile))
            (if-let [attachment-version (update-or-create-attachment id attachmentId attachmentType file-id sanitazed-filename content-type size created user)]
              (executed "add-comment"
                (-> command
                  (assoc :data {:id id
                                :text text,
                                :target {:type :attachment
                                         :id (:id attachment-version)
                                         :version (:version attachment-version)
                                         :filename (:filename attachment-version)
                                         :fileId (:fileId attachment-version)}})))
              (fail :error.unknown)))
          (fail :error.illegal-attachment-type))
        (fail :error.no-such-application))
      (fail :error.illegal-file-type))))

;;
;; Download
;;

(defn- get-attachment
  "Returns the attachment if user has access to application, otherwise nil."
  [file-id user]
  (when-let [attachment (mongo/download file-id)]
    (when-let [application (get-application-as (:application attachment) user)]
      (when (seq application) attachment))))

(def windows-filename-max-length 255)

(defn encode-filename
  "Replaces all non-ascii chars and other that the allowed punctuation with dash.
   UTF-8 support would have to be browser specific, see http://greenbytes.de/tech/tc2231/"
  [unencoded-filename]
  (when-let [de-accented (strings/de-accent unencoded-filename)]
      (clojure.string/replace
        (strings/last-n windows-filename-max-length de-accented)
        #"[^a-zA-Z0-9\.\-_ ]" "-")))

(defn output-attachment [attachment-id user download?]
  (debugf "file download: attachment-id=%s" attachment-id)
  (if-let [attachment (get-attachment attachment-id user)]
    (let [response {:status 200
                    :body ((:content attachment))
                    :headers {"Content-Type" (:content-type attachment)
                              "Content-Length" (str (:content-length attachment))}}]
      (if download?
        (assoc-in response
                  [:headers "Content-Disposition"]
                  (format "attachment;filename=\"%s\"" (encode-filename (:file-name attachment))) )
        response))
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))

(defn- append-gridfs-file [zip file-name file-id]
  (when file-id
    (.putNextEntry zip (ZipEntry. (encode-filename file-name)))
    (with-open [in ((:content (mongo/download file-id)))]
      (io/copy in zip))))

(defn- append-stream [zip file-name in]
  (when in
    (.putNextEntry zip (ZipEntry. (encode-filename file-name)))
    (io/copy in zip)))

(defn- append-attachment [zip {:keys [filename fileId]}]
  (append-gridfs-file zip filename fileId))

(defn- get-all-attachments [application loc lang]
  (let [temp-file (File/createTempFile "lupapiste.attachments." ".zip.tmp")]
    (debugf "Created temporary zip file for attachments: %s" (.getAbsolutePath temp-file))
    (with-open [out (io/output-stream temp-file)]
      (let [zip (ZipOutputStream. out)]
        ; Add all attachments:
        (doseq [attachment (:attachments application)]
          (append-attachment zip (-> attachment :versions last)))
        ; Add submitted PDF, if exists:
        (when-let [submitted-application (mongo/by-id :submitted-applications (:id application))]
          (append-stream zip (loc "attachment.zip.pdf.filename.current") (ke6666/generate submitted-application lang)))
        ; Add current PDF:
        (append-stream zip (loc "attachment.zip.pdf.filename.submitted") (ke6666/generate application lang))
        (.finish zip)))
    temp-file))

(defn- temp-file-input-stream [^File file]
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defn output-all-attachments [application-id user lang]
  (let [loc (i18n/localizer lang)]
    (if-let [application (mongo/select-one :applications {$and [{:_id application-id} (application-query-for user)]})]
      {:body (temp-file-input-stream (get-all-attachments application loc lang))
       :status 200
       :headers {"Content-Type" "application/octet-stream"
                 "Content-Disposition" (str "attachment;filename=\"" (loc "attachment.zip.filename") "\"")}}
      {:body "404"
       :status 404
       :headers {"Content-Type" "text/plain"}})))
