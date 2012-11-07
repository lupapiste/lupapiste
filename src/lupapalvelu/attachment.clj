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
  {:buildingPermit
   [{:ordinal 0, :key :hakija, :s "Hakija", :types ; <optgroup>
     [{:ordinal 0, :key :valtakirja, :s "Valtakirja"} ; <option>
      {:ordinal 10, :key :ote_kauppa_ja_yhdistysrekisterista, :s "Ote kauppa- ja yhdistysrekisterist\u00e4"}
      {:ordinal 20, :key :ote_asunto_osakeyhtion_hallituksen_kokouksen_poytakirjasta, :s "Ote asunto-osakeyhti\u00f6n hallituksen kokouksen p\u00f6yt\u00e4kirjasta"}]}
    {:ordinal 10, :key :rakennuspaikan_hallinta, :s "Rakennuspaikan hallinta", :types
     [{:ordinal 0, :key :jaljennos_myonnetyista_lainhuudoista, :s "J\u00e4ljenn\u00f6s my\u00f6nnetyist\u00e4 lainhuudoista"}
      {:ordinal 10, :key :jaljennos_kauppakirjasta_tai_muusta_luovutuskirjasta, :s "J\u00e4ljenn\u00f6s kauppakirjasta tai muusta luovutuskirjasta"}
      {:ordinal 20, :key :rasitustodistus, :s "Rasitustodistus"}
      {:ordinal 30, :key :todistus_erityisoikeuden_kirjaamisesta, :s "Todistus erityisoikeuden kirjaamisesta"}
      {:ordinal 40, :key :jaljennos_vuokrasopimuksesta, :s "J\u00e4ljenn\u00f6s vuokrasopimuksesta"}
      {:ordinal 50, :key :jaljennos_perunkirjasta, :s "J\u00e4ljenn\u00f6s perunkirjasta"}]}
    {:ordinal 20, :key :rakennuspaikka, :s "Rakennuspaikka", :types
     [{:ordinal 0, :key :ote_alueen_peruskartasta, :s "Ote alueen peruskartasta"}
      {:ordinal 10, :key :ote_asemakaavasta_jos_asemakaava_alueella, :s "Ote asemakaavasta (jos asemakaava-alueella)"}
      {:ordinal 20, :key :ote_kiinteistorekisteristerista, :s "Ote kiinteist\u00f6rekisteristerist\u00e4"}
      {:ordinal 30, :key :tonttikartta_tarvittaessa, :s "Tonttikartta (tarvittaessa)"}
      {:ordinal 40, :key :selvitys_rakennuspaikan_perustamis_ja_pohjaolosuhteista, :s "Selvitys rakennuspaikan perustamis-  ja pohjaolosuhteista"}
      {:ordinal 50, :key :kiinteiston_vesi_ja_viemarilaitteiston_suunnitelma, :s "Kiinteist\u00f6n vesi- ja viem\u00e4rilaitteiston suunnitelma"}]}
    {:ordinal 30, :key :paapiirustus, :s "P\u00e4\u00e4piirustus", :types
     [{:ordinal 0, :key :asemapiirros, :s "Asemapiirros"}
      {:ordinal 10, :key :pohjapiirros, :s "Pohjapiirros"}
      {:ordinal 20, :key :leikkauspiirros, :s "Leikkauspiirros"}
      {:ordinal 30, :key :julkisivupiirros, :s "Julkisivupiirros"}]}
    {:ordinal 40, :key :ennakkoluvat_ja_piirustukset, :s "Ennakkoluvat ja piirustukset", :types
     [{:ordinal 0, :key :naapurien_suostumukset, :s "Naapurien suostumukset"}
      {:ordinal 10, :key :selvitys_naapurien_kuulemisesta, :s "Selvitys naapurien kuulemisesta"}
      {:ordinal 20, :key :elyn_tai_kunnan_poikkeamapaatos, :s "ELYn tai kunnan poikkeamap\u00e4\u00e4t\u00f6s"}
      {:ordinal 30, :key :suunnittelutarveratkaisu, :s "Suunnittelutarveratkaisu"}
      {:ordinal 40, :key :ymparistolupa, :s "Ymp\u00e4rist\u00f6lupa"}]}
    {:ordinal 50, :key :muut, :s "Muut", :types
     [{:ordinal 0, :key :selvitys_rakennuspaikan_terveellisyydesta, :s "Selvitys rakennuspaikan terveellisyydest\u00e4"}
      {:ordinal 10, :key :selvitys_rakennuspaikan_korkeusasemasta, :s "Selvitys rakennuspaikan korkeusasemasta"}
      {:ordinal 20, :key :selvitys_liittymisesta_ymparoivaan_rakennuskantaan, :s "Selvitys liittymisest\u00e4 ymp\u00e4r\u00f6iv\u00e4\u00e4n rakennuskantaan"}
      {:ordinal 30, :key :julkisivujen_varityssuunnitelma, :s "Julkisivujen v\u00e4rityssuunnitelma"}
      {:ordinal 40, :key :selvitys_tontin_tai_rakennuspaikan_pintavesien_kasittelysta, :s "Selvitys tontin tai rakennuspaikan pintavesien k\u00e4sittelyst\u00e4"}
      {:ordinal 50, :key :piha_tai_istutussuunnitelma, :s "Piha-  tai istutussuunnitelma"}
      {:ordinal 60, :key :selvitys_rakenteiden_kokonaisvakavuudesta_ja_lujuudesta, :s "Selvitys rakenteiden kokonaisvakavuudesta ja lujuudesta"}
      {:ordinal 70, :key :selvitys_rakennuksen_kosteusteknisesta_toimivuudesta, :s "Selvitys rakennuksen kosteusteknisest\u00e4 toimivuudesta"}
      {:ordinal 80, :key :selvitys_rakennuksen_aaniteknisesta_toimivuudesta, :s "Selvitys rakennuksen \u00e4\u00e4niteknisest\u00e4 toimivuudesta"}
      {:ordinal 90, :key :selvitys_sisailmastotavoitteista_ja_niihin_vaikuttavista_tekijoista, :s "Selvitys sis\u00e4ilmastotavoitteista ja niihin vaikuttavista tekij\u00f6ist\u00e4"}
      {:ordinal 100, :key :energiataloudellinen_selvitys, :s "Energiataloudellinen selvitys"}
      {:ordinal 110, :key :paloturvallisuussuunnitelma, :s "Paloturvallisuussuunnitelma"}
      {:ordinal 120, :key :liikkumis_ja_esteettomyysselvitys, :s "Liikkumis-  ja esteett\u00f6myysselvitys"}
      {:ordinal 130, :key :kerrosalaselvitys, :s "Kerrosalaselvitys"}
      {:ordinal 140, :key :vaestonsuojasuunnitelma, :s "V\u00e4est\u00f6nsuojasuunnitelma"}
      {:ordinal 150, :key :rakennukseen_tai_sen_osaan_kohdistuva_kuntotutkimus_jos_korjaus_tai_muutostyo, :s "Rakennukseen tai sen osaan kohdistuva kuntotutkimus (jos korjaus-  tai muutosty\u00f6)"}
      {:ordinal 160, :key :selvitys_rakennuksen_rakennustaiteellisesta_ja_kulttuurihistoriallisesta_arvosta_jos_korjaus_tai_muutostyo, :s "Selvitys rakennuksen rakennustaiteellisesta ja kulttuurihistoriallisesta arvosta (jos korjaus-  tai muutosty\u00f6)"}
      {:ordinal 170, :key :selvitys_kiinteiston_jatehuollon_jarjestamisesta, :s "Selvitys kiinteist\u00f6n j\u00e4tehuollon j\u00e4rjest\u00e4misest\u00e4"}
      {:ordinal 180, :key :rakennesuunnitelma, :s "Rakennesuunnitelma"}
      {:ordinal 190, :key :ilmanvaihtosuunnitelma, :s "Ilmanvaihtosuunnitelma"}
      {:ordinal 200, :key :lammityslaitesuunnitelma, :s "L\u00e4mmityslaitesuunnitelma"}
      {:ordinal 210, :key :radontekninen_suunnitelma, :s "Radontekninen suunnitelma"}
      {:ordinal 220, :key :kalliorakentamistekninen_suunnitelma, :s "Kalliorakentamistekninen suunnitelma"}
      {:ordinal 230, :key :paloturvallisuusselvitys, :s "Paloturvallisuusselvitys"}
      {:ordinal 240, :key :suunnitelma_paloilmoitinjarjestelmista_ja_koneellisesta_savunpoistosta, :s "Suunnitelma paloilmoitinj\u00e4rjestelmist\u00e4 ja koneellisesta savunpoistosta"}
      {:ordinal 250, :key :merkki_ja_turvavalaistussuunnitelma, :s "Merkki- ja turvavalaistussuunnitelma"}
      {:ordinal 260, :key :sammutusautomatiikkasuunnitelma, :s "Sammutusautomatiikkasuunnitelma"}
      {:ordinal 270, :key :rakennusautomaatiosuunnitelma, :s "Rakennusautomaatiosuunnitelma"}
      {:ordinal 280, :key :valaistussuunnitelma, :s "Valaistussuunnitelma"}
      {:ordinal 290, :key :selvitys_rakennusjatteen_maarasta_laadusta_ja_lajittelusta, :s "Selvitys rakennusj\u00e4tteen m\u00e4\u00e4r\u00e4st\u00e4, laadusta ja lajittelusta"}
      {:ordinal 300, :key :selvitys_purettavasta_rakennusmateriaalista_ja_hyvaksikaytosta, :s "Selvitys purettavasta rakennusmateriaalista ja hyv\u00e4ksik\u00e4yt\u00f6st\u00e4"}
      {:ordinal 999, :key :muu, :s "Muu liite"}]}]
   })

(defn- attachment-types-for [application-id]
  (if-let [permit-type (:permitType (mongo/select-one mongo/applications {:_id application-id} [:permitType]))]
    (attachment-types-for-permit-type (keyword permit-type))
    []))

(defquery "attachment-types"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [{{application-id :id} :data}]
  (ok :typeGroups (attachment-types-for application-id)))

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
;; Upload commands
;;

(defn- create-attachment [application-id attachement-type now]
  (let [attachment-id (mongo/create-id)
        attachment-model {:id attachment-id
                          :type attachement-type
                          :state :none
                          :latestVersion   {:version default-version}
                          :versions []
                          :comments []}]
    (mongo/update-by-id mongo/applications application-id
      {$set {:modified now, (str "attachments." attachment-id) attachment-model}})
    attachment-id))

;; Authority can set a placeholder for an attachment
(defcommand "create-attachment"
  {:parameters [:id :type]
   :roles      [:authority]
   :states     [:draft :open]}
  [{{application-id :id type :type} :data created :created}]
  (if-let [attachment-id (create-attachment application-id type created)]
    (ok :applicationId application-id :attachmentId attachment-id)
    (fail "Failed to create attachment placeholder")))

(defn- next-attachment-version [current-version user]
  (if (= (keyword (:role user)) :authority)
    {:major (:major current-version), :minor (inc (:minor current-version))}
    {:major (inc (:major current-version)), :minor 0}))

(defn- set-attachment-version
  [application-id attachment-id file-id filename content-type size now user]
  (when-let [application (mongo/by-id mongo/applications application-id)]
    (let [latest-version (-> application :attachments (get (keyword attachment-id)) :latestVersion :version)
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
                 (str "attachments." attachment-id ".modified") now
                 (str "attachments." attachment-id ".state")  :added
                 (str "attachments." attachment-id ".latestVersion") version-model}]

        ; TODO check return value and try again with new version number
        (mongo/update-by-query
          mongo/applications
          {:_id application-id
           (str "attachments." attachment-id ".latestVersion.version.major") (:major latest-version)
           (str "attachments." attachment-id ".latestVersion.version.minor") (:minor latest-version)}
          {$set attachment-model
           $push {(str "attachments." attachment-id ".versions") version-model}}))))

(defn- allowed-attachment-type-for? [application-id type]
  (some (fn [{types :types}]
          (some (fn [{key :key}] (= key type)) types))
        (attachment-types-for application-id))
  )

(defcommand "upload-attachment"
  {:parameters [:id :attachmentId :type :filename :tempfile :size]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{created :created
    user    :user
    {:keys [id attachmentId type filename tempfile size comment]} :data}]
  (debug "Create GridFS file: %s %s %s %s %s %d" id attachmentId type filename tempfile size)
  (let [file-id (mongo/create-id)
        sanitazed-filename (strings/suffix (strings/suffix filename "\\") "/")]
    (if (allowed-file? sanitazed-filename)
      (if (allowed-attachment-type-for? id (keyword type))
        (let [content-type (mime-type sanitazed-filename)]
          (mongo/upload id file-id sanitazed-filename content-type tempfile created)
          (if (empty? attachmentId)
            (let [attachment-id (create-attachment id type created)]
              (set-attachment-version id attachment-id file-id sanitazed-filename content-type size created user))
            (set-attachment-version id attachmentId file-id sanitazed-filename content-type size created user))
          (.delete (file tempfile))
          (ok))
        (fail "Illegal attachment type"))
      (fail "Illegal file type"))))

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
