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
;; Helpers
;;

(defn equal-versions? [ver1 ver2]
  (and ver1 ver2 (= (:major ver1) (:major ver2)) (= (:minor ver1) (:minor ver2))))

;;
;; Metadata
;;

(def attachment-types-for-permit-type
  {:buildingPermit
   [{:ordinal 0, :key :jotkuliitteet, :s "Liitetyyppej\u00e4 voi ryhmitell\u00e4 n\u00e4in" ; optgroup
     :types [{:ordinal 0,   :key :asemapiirrosluonnos, :s "Asemapiirrosluonnos"} ; options
             {:ordinal 10,  :key :pohjapiirustusluonnos, :s "Pohjapiirustusluonnos"}
             {:ordinal 20,  :key :leikkauspiirustusluonnos, :s "Leikkauspiirustusluonnos"}
             {:ordinal 30,  :key :julkisivupiirustusluonnos,  :s "Julkisivupiirustusluonnos"}
             {:ordinal 40,  :key :vesikattopiirustusluonnos,  :s "Vesikattopiirustusluonnos"}
             {:ordinal 50,  :key :muut_paapiirustusluonnokset,  :s "Muut p\u00e4\u00e4piirustusluonnokset"}
             {:ordinal 60,  :key :ulkovaritysselvitys, :s "Ulkov\u00e4ritysselvitys"}
             {:ordinal 70,  :key :tonttikartta, :s "Tonttikartta"}
             {:ordinal 80,  :key :tonttikartan_liite, :s "Tonttikartan liite"}
             {:ordinal 90,  :key :pintavaaituskartta, :s "Pintavaaituskartta"}
             {:ordinal 100, :key :kerrosalalaskelma, :s "Kerrosalalaskelma"}
             {:ordinal 110, :key :valtakirja, :s "Valtakirja"}
             {:ordinal 120, :key :poytakirjaote, :s "P\u00f6yt\u00e4kirjaote"}
             {:ordinal 130, :key :kaupparekisteriote, :s "Kaupparekisteriote"}
             {:ordinal 140, :key :osakeyhtion_perustamiskirja,  :s "Osakeyhti\u00f6n perustamiskirja"}
             {:ordinal 150, :key :jaljennos_yhtiojarjestyksesta,  :s "J\u00e4ljenn\u00f6s yhti\u00f6j\u00e4rjestyksest\u00e4"}
             {:ordinal 160, :key :ositus__ja_perinnonjakokirja,  :s "Ositus- ja perinn\u00f6njakokirja"}
             {:ordinal 170, :key :sopimusjaljennos, :s "Sopimusj\u00e4ljenn\u00f6s"}
             {:ordinal 180, :key :rakennesuunnittelusta_vastaavan_lomake,  :s "Rakennesuunnittelusta vastaavan lomake"}
             {:ordinal 190, :key :suunnittelijan_lomake, :s "Suunnittelijan lomake"}
             {:ordinal 200, :key :naapurin_kuuleminen, :s "Naapurin kuuleminen"}
             {:ordinal 210, :key :lehtikuulutus, :s "Lehtikuulutus"}
             {:ordinal 220, :key :ilmoitus_vaestonsuojasta,  :s "Ilmoitus v\u00e4est\u00f6nsuojasta"}
             {:ordinal 230, :key :esteettomyysselvitys, :s "Esteett\u00f6myysselvitys"}
             {:ordinal 240, :key :poistumistieselvitys, :s "Poistumistieselvitys"}
             {:ordinal 250, :key :lampohavioiden_tasauslaskelma,  :s "L\u00e4mp\u00f6h\u00e4vi\u00f6iden tasauslaskelma"}
             {:ordinal 260, :key :energiatodistus, :s "Energiatodistus"}
             {:ordinal 270, :key :rakennusjateselvitys, :s "Rakennusj\u00e4teselvitys"}
             {:ordinal 280, :key :purkujateselvitys, :s "Purkuj\u00e4teselvitys"}
             {:ordinal 290, :key :palotekninen_selvitys, :s "Palotekninen selvitys"}
             {:ordinal 300, :key :pohjatutkimus, :s "Pohjatutkimus"}
             {:ordinal 310, :key :riskianalyysi, :s "Riskianalyysi"}
             {:ordinal 320, :key :poikkeamispaatos, :s "Poikkeamisp\u00e4\u00e4t\u00f6s"}
             {:ordinal 330, :key :suunnittelutarveratkaisu, :s "Suunnittelutarveratkaisu"}
             {:ordinal 460, :key :rh_ilmoitus, :s "RH-ilmoitus"}]}
    {:ordinal 0, :key :lausunnot, :s "Lausunnot" ; optgroup
     :types [{:ordinal 340, :key :asemakaavoituksen_lausunto, :s "Asemakaavoituksen lausunto"}
             {:ordinal 350, :key :yleiskaavoituksen_lausunto, :s "Yleiskaavoituksen lausunto"}
             {:ordinal 360, :key :kunnallistekniikan_lausunto, :s "Kunnallistekniikan lausunto"}
             {:ordinal 370, :key :mittauksen_lausunto, :s "Mittauksen lausunto"}
             {:ordinal 380, :key :viherpalveluiden_lausunto,  :s "Viherpalveluiden lausunto"}
             {:ordinal 390, :key :vesilaitoksen_lausunto,  :s "Vesilaitoksen lausunto"}
             {:ordinal 400, :key :paloviranomaisen_lausunto,  :s "Paloviranomaisen lausunto"}
             {:ordinal 410, :key :vaestonsuojeluviranomaisen_lausunto, :s "V\u00e4est\u00f6nsuojeluviranomaisen lausunto"}
             {:ordinal 420, :key :terveydenhoitoviranomaisen_lausunto, :s "Terveydenhoitoviranomaisen lausunto"}]}
    {:ordinal 0, :key :muut, :s "Muut liitteet" ; optgroup
     :types [{:ordinal 430, :key :muu_lausunto, :s "Muu lausunto"}
             {:ordinal 440, :key :valokuvia, :s "Valokuvia"}
             {:ordinal 450, :key :neuvottelumuistio, :s "Neuvottelumuistio"}
             {:ordinal 999, :key :muu, :s "Muu liite"}]
     }]})

(defquery "attachment-types"
  {:parameters [:id]
   :roles      [:applicant :authority]}
  [command]
  (with-application command
    (fn [{permit-type :permitType}]
      (ok :typeGroups (attachment-types-for-permit-type (keyword permit-type))))))

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
  (let [attachment-id (create-attachment application-id type created)]
    (ok :applicationId application-id :attachmentId attachment-id)))

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

(defcommand "upload-attachment"
  {:parameters [:id :attachmentId :type :filename :tempfile :size]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{created :created
    user    :user
    {:keys [id attachmentId type filename tempfile size]} :data}]
  (debug "Create GridFS file: %s %s %s %s %s %d" id attachmentId type filename tempfile size)
  (let [file-id (mongo/create-id)
        sanitazed-filename (strings/suffix (strings/suffix filename "\\") "/")]
    (if (allowed-file? sanitazed-filename)
      (let [content-type (mime-type sanitazed-filename)]
        (mongo/upload id file-id sanitazed-filename content-type tempfile created)
        (if (empty? attachmentId)
          (let [attachment-id (create-attachment id type created)]
            (set-attachment-version id attachment-id file-id sanitazed-filename content-type size created user))
          (set-attachment-version id attachmentId file-id sanitazed-filename content-type size created user))
        (.delete (file tempfile))
        (ok))
      (fail "Illegal file type"))))

;;
;; Delete
;;

(defcommand "delete-empty-attachment"
  {:parameters [:id :attachmentId]
   :roles      [:applicant :authority]
   :states     [:draft :open]}
  [{{:keys [id attachmentId]} :data}]
  (mongo/update-by-query
          mongo/applications
          {:_id id
           (str "attachments." attachmentId ".latestVersion.version.major") (:major default-version)
           (str "attachments." attachmentId ".latestVersion.version.minor") (:minor default-version)}
          {$unset {(str "attachments." attachmentId) 1}})

  (ok))

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
