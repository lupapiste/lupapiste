(ns lupapalvelu.attachment
  (:use [monger.operators]
        [lupapalvelu.core]
        [clojure.tools.logging]
        [lupapalvelu.domain :only [get-application-as application-query-for]]
        [clojure.string :only [split join trim]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.strings :as strings]
            [lupapalvelu.mime :as mime]
            [clojure.java.io :as io])
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

;;
;; Upload
;;

(defn create-attachment [application-id attachement-type now]
  (let [attachment-id (mongo/create-id)
        attachment-model {:id attachment-id
                          :type (or attachement-type default-type)
                          :state :requires_user_action
                          :modified now
                          :versions []}]
    (mongo/update-by-id :applications application-id
      {$set {:modified now}
       $push {:attachments attachment-model}})
    attachment-id))

(defn- next-attachment-version [{major :major minor :minor} user]
  (let [major (or major 0)
        minor (or minor 0)]
    (if (= (keyword (:role user)) :authority)
      {:major major, :minor (inc minor)}
      {:major (inc major), :minor 0})))

(defn attachment-latest-version [attachments attachment-id]
  (:version (:latestVersion (some #(when (= attachment-id (:id %)) %) attachments))))

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
   :states     [:draft :open]}
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
   :states      [:draft :open :submitted]}
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
   :states      [:draft :open :submitted]}
  [{{:keys [attachmentId]} :data created :created :as command}]
  (with-application command
    (fn [{id :id}]
      (mongo/update
        :applications
        {:_id id, :attachments {$elemMatch {:id attachmentId}}}
        {$set {:modified (:created command)
               :attachments.$.state :requires_user_action}}))))

(defcommand "create-attachment"
  {:description "Authority can set a placeholder for an attachment"
   :parameters  [:id :attachmentType]
   :roles       [:authority]
   :states      [:draft :open]}
  [{{application-id :id attachmentType :attachmentType} :data created :created}]
  (if-let [attachment-id (create-attachment application-id attachmentType created)]
    (ok :applicationId application-id :attachmentId attachment-id)
    (fail :error.attachment-placeholder)))

(defcommand "upload-attachment"
  {:parameters [:id :attachmentId :attachmentType :filename :tempfile :size]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{created :created
    user    :user
    {:keys [id attachmentId attachmentType filename tempfile size text]} :data}]
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
              (executed (assoc (command "add-comment"
                                        {:id id
                                         :text text,
                                         :target {:type :attachment
                                                  :id (:id attachment-version)
                                                  :version (:version attachment-version)
                                                  :filename (:filename attachment-version)
                                                  :fileId (:fileId attachment-version)}})
                               :user user))
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

(defn- append-attachment [zip latest]
  (when latest
    (.putNextEntry zip (ZipEntry. (encode-filename (:filename latest))))
    (with-open [in ((-> latest :fileId mongo/download :content))]
      (io/copy in zip))))

(defn- get-all-attachments [attachments]
  (let [temp-file (File/createTempFile "lupapiste.attachments." ".zip.tmp")]
    (debugf "Created temporary zip file for attachments: %s" (.getAbsolutePath temp-file))
    (with-open [out (io/output-stream temp-file)]
      (let [zip (ZipOutputStream. out)]
        (doseq [attachment attachments]
          (append-attachment zip (-> attachment :versions last)))
        (.finish zip)))
    temp-file))

(defn- temp-file-input-stream [^File file]
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defn output-all-attachments [application-id user]
  (if-let [application (mongo/select-one :applications {$and [{:_id application-id} (application-query-for user)]} {:attachments 1})]
    {:body (temp-file-input-stream (get-all-attachments (:attachments application)))
     :status 200
     :headers {"Content-Type" "application/octet-stream"
               "Content-Disposition" "attachment;filename=\"liitteet.zip\""}}
    {:body "404"
     :status 404
     :headers {"Content-Type" "text/plain"}}))
