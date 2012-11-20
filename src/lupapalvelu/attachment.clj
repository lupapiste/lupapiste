(ns lupapalvelu.attachment
  (:use [monger.operators]
        [lupapalvelu.core]
        [lupapalvelu.log]
        [lupapalvelu.action :only [get-application-as]]
        [clojure.java.io :only [reader file]]
        [clojure.string :only [split join trim]])
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.strings :as strings]))

;;
;; Constants
;;
(def default-version {:major 0, :minor 0})

;;
;; Metadata
;;

(def attachment-types-for-permit-type
  {:buildingPermit [{:key :hakija
                     :types [{:key :valtakirja}
                             {:key :ote_kauppa_ja_yhdistysrekisterista}
                             {:key :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta}]}
                    {:key :rakennuspaikan_hallinta
                     :types [{:key :jaljennos_myonnetyista_lainhuudoista}
                             {:key :jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta}
                             {:key :rasitustodistus}
                             {:key :todistus_erityisoikeuden_kirjaamisesta}
                             {:key :jaljennos_vuokrasopimuksesta}
                             {:key :jaljennos_perunkirjasta}]}
                    {:key :rakennuspaikka
                     :types [{:key :ote_alueen_peruskartasta}
                             {:key :ote_asemakaavasta_jos_asemakaava_alueella}
                             {:key :ote_kiinteistorekisteristerista}
                             {:key :tonttikartta_tarvittaessa}
                             {:key :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista}
                             {:key :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma}]}
                    {:key :paapiirustus
                     :types [{:key :asemapiirros}
                             {:key :pohjapiirros}
                             {:key :leikkauspiirros}
                             {:key :julkisivupiirros}]}
                    {:key :ennakkoluvat_ja_lausunnot
                     :types [{:key :naapurien_suostumukset}
                             {:key :selvitys_naapurien_kuulemisesta}
                             {:key :elyn_tai_kunnan_poikkeamapaatos}
                             {:key :suunnittelutarveratkaisu}
                             {:key :ymparistolupa}]}
                    {:key :muut
                     :types [{:key :selvitys_rakennuspaikan_terveellisyydesta}
                             {:key :selvitys_rakennuspaikan_korkeusasemasta}
                             {:key :selvitys_liittymisesta_ymparoivaan_rakennuskantaan}
                             {:key :julkisivujen_varityssuunnitelma}
                             {:key :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta}
                             {:key :piha_tai_istutussuunnitelma}
                             {:key :selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta}
                             {:key :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta}
                             {:key :selvitys_rakennuksen_aaniteknisesta_toimivuudesta}
                             {:key :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista}
                             {:key :energiataloudellinen_selvitys}
                             {:key :paloturvallisuussuunnitelma}
                             {:key :liikkumis_ja_esteettomyysselvitys}
                             {:key :kerrosalaselvitys}
                             {:key :vaestonsuojasuunnitelma}
                             {:key :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo}
                             {:key :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo}
                             {:key :selvitys_kiinteiston_jatehuollon_jarjestamisesta}
                             {:key :rakennesuunnitelma}
                             {:key :ilmanvaihtosuunnitelma}
                             {:key :lammityslaitesuunnitelma}
                             {:key :radontekninen_suunnitelma}
                             {:key :kalliorakentamistekninen_suunnitelma}
                             {:key :paloturvallisuusselvitys}
                             {:key :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta}
                             {:key :merkki_ja_turvavalaistussuunnitelma}
                             {:key :sammutusautomatiikkasuunnitelma}
                             {:key :rakennusautomaatiosuunnitelma}
                             {:key :valaistussuunnitelma}
                             {:key :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta}
                             {:key :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta}
                             {:key :muu}]}]})

(defn- get-permit-type [application-id]
  (keyword (:permitType (mongo/select-one mongo/applications {:_id application-id} [:permitType]))))

(defn- attachment-types-for [permit-type]
  (attachment-types-for-permit-type permit-type))

;; Reads mime.types file provided by Apache project.
;; Ring has also some of the most common file extensions mapped, but is missing
;; docx and other MS Office formats.
(def mime-types
  (with-open [resource (clojure.lang.RT/resourceAsStream nil "private/mime.types")]
    (into {} (for [line (line-seq (reader resource))
                   :let [l (trim line)
                         type-and-exts (split l #"\s+")
                         mime-type (first type-and-exts)]
                   :when (and (not (.isEmpty l)) (not (.startsWith l "#")))]
               (into {} (for [ext (rest type-and-exts)] [ext mime-type]))))))

(def mime-type-pattern
  (re-pattern
    (join "|" [
          "(image/.+)"
          "(text/(plain|rtf))"
          (str "(application/("
               (join "|" [
                     "pdf" "postscript"
                     "zip" "x-7z-compressed"
                     "rtf" "msword" "vnd\\.ms-excel" "vnd\\.ms-powerpoint"
                     "vnd\\.oasis\\.opendocument\\..+"
                     "vnd\\.openxmlformats-officedocument\\..+"]) "))")])))


(defn mime-type [filename]
  (when filename
    (get mime-types (.toLowerCase (strings/suffix filename ".")))))

(defn allowed-file? [filename]
  (when-let [type (mime-type filename)]
      (re-matches mime-type-pattern type)))

;;
;; Upload
;;

(defn- create-attachment [application-id attachement-type now]
  (let [attachment-id (mongo/create-id)
        attachment-model {:id attachment-id
                          :type attachement-type
                          :state :requires_user_action
                          :latestVersion   {:version default-version}
                          :versions []}]
    (mongo/update-by-id mongo/applications application-id
      {$set {:modified now} $push {:attachments attachment-model}})
    attachment-id))

(defn- next-attachment-version [{major :major minor :minor} user]
  (if (= (keyword (:role user)) :authority)
    {:major major, :minor (inc minor)}
    {:major (inc major), :minor 0}))

(defn attachment-latest-version [attachments attachment-id]
  (:version (:latestVersion (some #(when (= attachment-id (:id %)) %) attachments))))

(defn- set-attachment-version
  ([application-id attachment-id file-id filename content-type size now user]
    (set-attachment-version application-id attachment-id file-id filename content-type size now user 5))
  ([application-id attachment-id file-id filename content-type size now user retry-limit]
    (if (> retry-limit 0)
      (when-let [application (mongo/by-id mongo/applications application-id)]
        (let [latest-version (attachment-latest-version (application :attachments) attachment-id)
              next-version (next-attachment-version latest-version user)
              version-model {
                  :version  next-version
                  :fileId   file-id
                  :created  now
                  :accepted nil
                  :user    (security/summary user)
                  ; File name will be presented in ASCII when the file is downloaded.
                  ; Conversion could be done here as well, but we don't want to lose information.
                  :filename filename
                  :contentType content-type
                  :size size}
              attachment-model {:modified now
                 :attachments.$.modified now
                 :attachments.$.state  :requires_authority_action
                 :attachments.$.latestVersion version-model}]

        ; Check return value and try again with new version number
        (let [result-count (mongo/update-by-query mongo/applications
            {:_id application-id
             :attachments {$elemMatch {:id attachment-id
                                       :latestVersion.version.major (:major latest-version)
                                       :latestVersion.version.minor (:minor latest-version)}}}
            {$set attachment-model
             $push {:attachments.$.versions version-model}})]
          (if (> result-count 0)
            true
            (do
              (warn
                "Latest version of attachment %s changed before new version could be saved, retry %d time(s)."
                attachment-id retry-limit)
              (set-attachment-version application-id attachment-id file-id filename content-type size now user (dec retry-limit)))))))
      (do
        (error "Concurrancy issue: Could not save attachment version meta data.")
        false))))

(defn update-or-create-attachment [id attachment-id attachement-type file-id filename content-type size created user]
  (if (empty? attachment-id)
    (let [attachment-id (create-attachment id attachement-type created)]
      (set-attachment-version id attachment-id file-id filename content-type size created user)
      attachment-id)
    (do
      (set-attachment-version id attachment-id file-id filename content-type size created user)
      attachment-id)))

(defn- allowed-attachment-type-for? [application-id type]
  (some (fn [{types :types}] (some (fn [{key :key}] (= key type)) types))
        (attachment-types-for (get-permit-type application-id))))

;;
;; Actions
;;

(defquery "attachment-types"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [{{application-id :id} :data}]
  (ok :typeGroups (attachment-types-for (get-permit-type application-id))))

(defcommand "approve-attachment"
  {:description "Authority can approve attachement, moves to ok"
   :parameters  [:id :attachmentId]
   :roles       [:authority]
   :states      [:draft :open]}
  [{{:keys [attachmentId]} :data created :created :as command}]
  (with-application command
    (fn [{id :id}]
      (mongo/update
        mongo/applications
        {:_id id, :attachments {$elemMatch {:id attachmentId}}}
        {$set {:modified (:created command)
               :attachments.$.state :ok}}))))

(defcommand "reject-attachment"
  {:description "Authority can reject attachement, requires user action."
   :parameters  [:id :attachmentId]
   :roles       [:authority]
   :states      [:draft :open]}
  [{{:keys [attachmentId]} :data created :created :as command}]
  (with-application command
    (fn [{id :id}]
      (mongo/update
        mongo/applications
        {:_id id, :attachments {$elemMatch {:id attachmentId}}}
        {$set {:modified (:created command)
               :attachments.$.state :requires_user_action}}))))


(defcommand "create-attachment"
  {:description "Authority can set a placeholder for an attachment"
   :parameters  [:id :type]
   :roles       [:authority]
   :states      [:draft :open]}
  [{{application-id :id type :type} :data created :created}]
  (if-let [attachment-id (create-attachment application-id type created)]
    (ok :applicationId application-id :attachmentId attachment-id)
    (fail :error.attachment-placeholder)))

(defcommand "upload-attachment"
  {:parameters [:id :attachmentId :type :filename :tempfile :size]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{created :created
    user    :user
    {:keys [id attachmentId type filename tempfile size text]} :data}]
  (debug "Create GridFS file: %s %s %s %s %s %d (%s)" id attachmentId type filename tempfile size text)
  (let [file-id (mongo/create-id)
        sanitazed-filename (strings/suffix (strings/suffix filename "\\") "/")]
    (if (allowed-file? sanitazed-filename)
      (if (allowed-attachment-type-for? id (keyword type))
        (let [content-type (mime-type sanitazed-filename)]
          (mongo/upload id file-id sanitazed-filename content-type tempfile created)
          (.delete (file tempfile))
          (if-let [attachment-id (update-or-create-attachment id attachmentId type file-id sanitazed-filename content-type size created user)]
            (if (seq text)
              (executed (assoc (command "add-comment"
                                        {:id id, :text text, :target {:type :attachment, :id attachment-id}})
                               :user user ))
              (ok))
            (fail :error.unknown)))
        (fail :error.illegal-attachment-type))
      (fail :error.illegal-file-type))))

;;
;; Download
;;

(defn- get-attachment
  "Returns the attachment if user has access to application, otherwise nil."
  [attachment-id user]
  (when-let [attachment (mongo/download attachment-id)]
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
  (debug "file download: attachment-id=%s" attachment-id)
  (if-let [attachment (get-attachment attachment-id user)]
    (let [response
          {:status 200
           :body ((:content attachment))
           :headers {"Content-Type" (:content-type attachment)
                     "Content-Length" (str (:content-length attachment))}}]
        (if download?
          (assoc-in response [:headers "Content-Disposition"]
            (format "attachment;filename=\"%s\"" (encode-filename (:file-name attachment))) )
          response))
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))
