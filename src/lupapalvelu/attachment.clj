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
  ;  NOTE: :text is here as documentation only. Actual UI label for each :key
  ;        is defined in loc.js.
  {:buildingPermit
   [{:key :hakija, :text "Hakija", :types ; <optgroup>
     [{:key :valtakirja, :text "Valtakirja"} ; <option>
      {:key :ote_kauppa_ja_yhdistysrekisterista, :text "Ote kauppa- ja yhdistysrekisterist\u00e4"}
      {:key :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta, :text "Ote asunto-osakeyhti\u00f6n hallituksen kokouksen p\u00f6yt\u00e4kirjasta"}]}
    {:key :rakennuspaikan_hallinta, :text "Rakennuspaikan hallinta", :types
     [{:key :jaljennos_myonnetyista_lainhuudoista, :text "J\u00e4ljenn\u00f6s my\u00f6nnetyist\u00e4 lainhuudoista"}
      {:key :jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta, :text "J\u00e4ljenn\u00f6s kauppakirjasta tai muusta luovutuskirjasta"}
      {:key :rasitustodistus, :text "Rasitustodistus"}
      {:key :todistus_erityisoikeuden_kirjaamisesta, :text "Todistus erityisoikeuden kirjaamisesta"}
      {:key :jaljennos_vuokrasopimuksesta, :text "J\u00e4ljenn\u00f6s vuokrasopimuksesta"}
      {:key :jaljennos_perunkirjasta, :text "J\u00e4ljenn\u00f6s perunkirjasta"}]}
    {:key :rakennuspaikka, :text "Rakennuspaikka", :types
     [{:key :ote_alueen_peruskartasta, :text "Ote alueen peruskartasta"}
      {:key :ote_asemakaavasta_jos_asemakaava_alueella, :text "Ote asemakaavasta (jos asemakaava-alueella)"}
      {:key :ote_kiinteistorekisteristerista, :text "Ote kiinteist\u00f6rekisteristerist\u00e4"}
      {:key :tonttikartta_tarvittaessa, :text "Tonttikartta (tarvittaessa)"}
      {:key :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista, :text "Selvitys rakennuspaikan perustamis-  ja pohjaolosuhteista"}
      {:key :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma, :text "Kiinteist\u00f6n vesi- ja viem\u00e4rilaitteiston suunnitelma"}]}
    {:key :paapiirustus, :text "P\u00e4\u00e4piirustus", :types
     [{:key :asemapiirros, :text "Asemapiirros"}
      {:key :pohjapiirros, :text "Pohjapiirros"}
      {:key :leikkauspiirros, :text "Leikkauspiirros"}
      {:key :julkisivupiirros, :text "Julkisivupiirros"}]}
    {:key :ennakkoluvat_ja_lausunnot, :text "Ennakkoluvat ja lausunnot", :types
     [{:key :naapurien_suostumukset, :text "Naapurien suostumukset"}
      {:key :selvitys_naapurien_kuulemisesta, :text "Selvitys naapurien kuulemisesta"}
      {:key :elyn_tai_kunnan_poikkeamapaatos, :text "ELYn tai kunnan poikkeamap\u00e4\u00e4t\u00f6s"}
      {:key :suunnittelutarveratkaisu, :text "Suunnittelutarveratkaisu"}
      {:key :ymparistolupa, :text "Ymp\u00e4rist\u00f6lupa"}]}
    {:key :muut, :text "Muut", :types
     [{:key :selvitys_rakennuspaikan_terveellisyydesta, :text "Selvitys rakennuspaikan terveellisyydest\u00e4"}
      {:key :selvitys_rakennuspaikan_korkeusasemasta, :text "Selvitys rakennuspaikan korkeusasemasta"}
      {:key :selvitys_liittymisesta_ymparoivaan_rakennuskantaan, :text "Selvitys liittymisest\u00e4 ymp\u00e4r\u00f6iv\u00e4\u00e4n rakennuskantaan"}
      {:key :julkisivujen_varityssuunnitelma, :text "Julkisivujen v\u00e4rityssuunnitelma"}
      {:key :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta, :text "Selvitys tontin tai rakennuspaikan pintavesien k\u00e4sittelyst\u00e4"}
      {:key :piha_tai_istutussuunnitelma, :text "Piha-  tai istutussuunnitelma"}
      {:key :selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta, :text "Selvitys rakenteiden kokonaisvakavuudesta ja lujuudesta"}
      {:key :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta, :text "Selvitys rakennuksen kosteusteknisest\u00e4 toimivuudesta"}
      {:key :selvitys_rakennuksen_aaniteknisesta_toimivuudesta, :text "Selvitys rakennuksen \u00e4\u00e4niteknisest\u00e4 toimivuudesta"}
      {:key :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista, :text "Selvitys sis\u00e4ilmastotavoitteista ja niihin vaikuttavista tekij\u00f6ist\u00e4"}
      {:key :energiataloudellinen_selvitys, :text "Energiataloudellinen selvitys"}
      {:key :paloturvallisuussuunnitelma, :text "Paloturvallisuussuunnitelma"}
      {:key :liikkumis_ja_esteettomyysselvitys, :text "Liikkumis-  ja esteett\u00f6myysselvitys"}
      {:key :kerrosalaselvitys, :text "Kerrosalaselvitys"}
      {:key :vaestonsuojasuunnitelma, :text "V\u00e4est\u00f6nsuojasuunnitelma"}
      {:key :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo, :text "Rakennukseen tai sen osaan kohdistuva kuntotutkimus (jos korjaus-  tai muutosty\u00f6)"}
      {:key :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo, :text "Selvitys rakennuksen rakennustaiteellisesta ja kulttuurihistoriallisesta arvosta (jos korjaus-  tai muutosty\u00f6)"}
      {:key :selvitys_kiinteiston_jatehuollon_jarjestamisesta, :text "Selvitys kiinteist\u00f6n j\u00e4tehuollon j\u00e4rjest\u00e4misest\u00e4"}
      {:key :rakennesuunnitelma, :text "Rakennesuunnitelma"}
      {:key :ilmanvaihtosuunnitelma, :text "Ilmanvaihtosuunnitelma"}
      {:key :lammityslaitesuunnitelma, :text "L\u00e4mmityslaitesuunnitelma"}
      {:key :radontekninen_suunnitelma, :text "Radontekninen suunnitelma"}
      {:key :kalliorakentamistekninen_suunnitelma, :text "Kalliorakentamistekninen suunnitelma"}
      {:key :paloturvallisuusselvitys, :text "Paloturvallisuusselvitys"}
      {:key :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta, :text "Suunnitelma paloilmoitinj\u00e4rjestelmist\u00e4 ja koneellisesta savunpoistosta"}
      {:key :merkki_ja_turvavalaistussuunnitelma, :text "Merkki- ja turvavalaistussuunnitelma"}
      {:key :sammutusautomatiikkasuunnitelma, :text "Sammutusautomatiikkasuunnitelma"}
      {:key :rakennusautomaatiosuunnitelma, :text "Rakennusautomaatiosuunnitelma"}
      {:key :valaistussuunnitelma, :text "Valaistussuunnitelma"}
      {:key :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta, :text "Selvitys rakennusj\u00e4tteen m\u00e4\u00e4r\u00e4st\u00e4, laadusta ja lajittelusta"}
      {:key :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta, :text "Selvitys purettavasta rakennusmateriaalista ja hyv\u00e4ksik\u00e4yt\u00f6st\u00e4"}
      {:key :muu, :text "Muu liite"}]}]
   })

(defn- attachment-types-for [application-id]
  (if-let [permit-type (:permitType (mongo/select-one mongo/applications {:_id application-id} [:permitType]))]
    (attachment-types-for-permit-type (keyword permit-type))
    []))

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

(defn- next-attachment-version [current-version user]
  (if (= (keyword (:role user)) :authority)
    {:major (:major current-version), :minor (inc (:minor current-version))}
    {:major (inc (:major current-version)), :minor 0}))

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
            (assoc version-model :id attachment-id)
            (do
              (warn
                "Latest version of attachment %s changed before new version could be saved, retry %d time(s)."
                attachment-id retry-limit)
              (set-attachment-version application-id attachment-id file-id filename content-type size now user (dec retry-limit)))))))
      (do
        (error "Concurrancy issue: Could not save attachment version meta data.")
        nil))))

(defn update-or-create-attachment [id attachment-id attachement-type file-id filename content-type size created user]
  (let [attachment-id (if (empty? attachment-id)
                        (create-attachment id attachement-type created)
                        attachment-id)]
    (set-attachment-version id attachment-id file-id filename content-type size created user)))

(defn- allowed-attachment-type-for? [application-id type]
  (some (fn [{types :types}] (some (fn [{key :key}] (= key type)) types))
        (attachment-types-for application-id)))

;;
;; Actions
;;

(defquery "attachment-types"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [{{application-id :id} :data}]
  (ok :typeGroups (attachment-types-for application-id)))

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
          (if-let [attachment-version (update-or-create-attachment id attachmentId type file-id sanitazed-filename content-type size created user)]
            (executed (assoc (command "add-comment"
                                      {:id id, :text text, 
                                       :target {:type :attachment, :id (:id attachment-version) :version (:version attachment-version) :filename (:filename attachment-version) :fileId (:fileId attachment-version) }})
                             :user user ))
            (fail :error.unknown)))
        (fail :error.illegal-attachment-type))
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
