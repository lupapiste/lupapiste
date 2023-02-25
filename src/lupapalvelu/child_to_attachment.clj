(ns lupapalvelu.child-to-attachment
  (:require [clojure.java.io :as io]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.pdf.libreoffice-template-statement :as statement]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [sade.core :refer [now]]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [tracef debugf]])
  (:import [java.io FileOutputStream File]))

(defn- get-child [application child-type id]
  (first (filter #(or (nil? id) (= id (:id %))) (child-type application))))

(defn- child-attachment-type [permit-type type source-document]
  (->> (case type
         :neighbors  {:type-group "ennakkoluvat_ja_lausunnot"
                      :type-id    (if (-> source-document :status last :state (= "response-given-comments"))
                                    "naapurin_huomautus"
                                    "naapurin_kuuleminen")}
         :statements {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"}
         :tasks      (if (.equalsIgnoreCase "aloituskokous" (get-in source-document [:data :katselmuksenLaji :value]))
                       {:type-group "katselmukset_ja_tarkastukset" :type-id "aloituskokouksen_poytakirja"}
                       {:type-group "katselmukset_ja_tarkastukset" :type-id "katselmuksen_tai_tarkastuksen_poytakirja"})
         :verdicts   {:type-group (if (#{"R" "ARK"} permit-type) "paatoksenteko" "muut")
                      :type-id    "paatos"}
         {:type-group "muut" :type-id "muu"})
       att-type/attachment-type))

(defn- review-attachment-contents [document lang taskname]
  (let [loc-key (str (get-in document [:schema-info :i18nprefix]) "." (get-in document [:data :katselmuksenLaji :value]))
        text (i18n/localize lang loc-key)]
    (str
      text
      ; Add taskname to contents only if it's not equal to the localized review type
      (when (and taskname (not (.equalsIgnoreCase text taskname))) (str " - " taskname)))))

(defn- build-attachment-options [user application type id lang ^File file attachment-id]
  {:pre [(map? user) (map? application) (keyword? type) (string? id) (#{:statements :neighbors :verdicts :tasks} type)]}
  (let [{:keys [taskname] :as child} (get-child application type id)
        type-name (case type
                    :statements (i18n/localize lang "statement.lausunto")
                    :neighbors (i18n/localize lang "application.MM.neighbors")
                    :verdicts (i18n/localize lang (if (:sopimus child) "userInfo.company.contract" "application.verdict.title"))
                    :tasks (i18n/localize lang "task-katselmus.rakennus.tila._group_label"))
        attachment-type      (child-attachment-type (:permitType application) type child)
        base-attachment-opts {:application        application
                              :filename           (-> type-name (ss/replace " " "_") (str ".pdf"))
                              :size               (.length file)
                              :content            file
                              :attachment-id      attachment-id
                              :attachment-type    (select-keys attachment-type [:type-group :type-id])
                              :group              {:groupType (get-in attachment-type [:metadata :grouping])}
                              :contents           (case type
                                                    :statements (get-in child [:person :text])
                                                    :neighbors (get-in child [:owner :name])
                                                    :tasks (review-attachment-contents child lang taskname)
                                                    type-name)
                              :locked             true
                              :read-only          (contains? #{:neighbors :statements :verdicts} type)
                              :user               user
                              :created            (now)
                              :required           false
                              ;; The file can be generated either by vaahtera-laundry or by the local
                              ;; pdfa-generator.core which both should always generate PDF/A
                              :archivable         true
                              :archivabilityError nil
                              :missing-fonts      []
                              :conversionLog      []
                              :source             {:type (name type) :id id}}]
    (cond-> base-attachment-opts
            (= :tasks type) (assoc :target {:type :task :id id}))))

(defn- get-child-attachment-id [app child-type id]
  (:id (util/find-first
         #(= {:type (name child-type) :id id} (:source %))
         (:attachments app))))

(defn- generate-statement-pdfa-to-file! [application id lang dst-file]
  (debugf "Generating PDF/A statement %s for application %s in %s" id (:id application) lang)
  (files/with-temp-file tmp-file
    (statement/write-statement-libre-doc application id lang tmp-file)
    (with-open [is (laundry-client/convert-libre-template-to-pdfa-stream tmp-file)]
      (io/copy is dst-file))))

(defn- generate-attachment-from-child!
  "Builds attachment and return attachment data as map"
  [user app child-type child-id lang ^File pdf-file]
  {:pre [lang child-type]}
  (tracef "   generate-attachment-from-children lang=%s, type=%s, child-id=%s,org: %s, child: %s" lang child-type child-id (:organization app) (get-child app child-type child-id))
  (let [attachment-id (get-child-attachment-id app child-type child-id)]
    (case child-type
      :statements (generate-statement-pdfa-to-file! app child-id lang pdf-file)
      (pdf-export/generate-pdf-with-child app child-type child-id lang (FileOutputStream. pdf-file)))

    (build-attachment-options user app child-type child-id lang pdf-file attachment-id)))

(defn create-attachment-from-children
  "Generates attachment from child and saves it. Returns created attachment version."
  [user application child-type child-id lang]
  (files/with-temp-file pdf-file
    (let [attachment-options (generate-attachment-from-child! user application child-type child-id lang pdf-file)
          file-options       (select-keys attachment-options [:filename :size :content])]
      (attachment/upload-and-attach! {:application application :user user} attachment-options file-options))))

(defn delete-child-attachment [app child-type id]
  (when-let [id (get-child-attachment-id app child-type id)]
    (attachment/delete-attachments! app [id])))
