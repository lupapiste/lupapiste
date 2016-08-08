(ns lupapalvelu.child-to-attachment
  (:require
    [lupapalvelu.attachment :as attachment]
    [lupapalvelu.pdf.pdf-export :as pdf-export]
    [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
    [sade.core :refer [def- now]]
    [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
    [clojure.pprint :refer [pprint]]
    [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
    [lupapalvelu.pdf.libreoffice-conversion-client :as libre-client]
    [clojure.java.io :as io]
    [clojure.string :as s]
    [sade.util :as util])
  (:import (java.io File FileOutputStream)))

(defn- get-child [application type id]
  (first (filter #(or (nil? id) (= id (:id %))) (type application))))

(defn- build-attachment-options [user application type id lang file attachment-id]
  {:pre [(map? user) (map? application) (keyword? type) (string? id) (#{:statements :neighbors :verdicts :tasks} type)]}
  (let [is-pdf-a? (pdf-conversion/ensure-pdf-a-by-organization file (:organization application))
        {:keys [taskname] :as child} (get-child application type id)
        type-name (case type
                    :statements (i18n/localize lang "statement.lausunto")
                    :neighbors (i18n/localize lang "application.MM.neighbors")
                    :verdicts (i18n/localize lang (if (:sopimus child) "userInfo.company.contract" "application.verdict.title"))
                    :tasks (i18n/localize lang "task-katselmus.rakennus.tila._group_label"))
        base-attachment-opts {:application        application
                              :filename           (-> type-name (s/replace " " "_") (str ".pdf"))
                              :size               (.length file)
                              :content            file
                              :attachment-id      attachment-id
                              :attachment-type    (case type
                                                    :neighbors {:type-group "ennakkoluvat_ja_lausunnot" :type-id "naapurin_kuuleminen"}
                                                    :statements {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"}
                                                    :tasks {:type-group "katselmukset_ja_tarkastukset" :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}
                                                    :verdicts {:type-group "paatoksenteko" :type-id "paatos"}
                                                    {:type-group "muut" :type-id "muu"})
                              :contents           (case type
                                                    :statements (get-in child [:person :text])
                                                    :neighbors (get-in child [:owner :name])
                                                    :tasks (str (i18n/localize lang (str (get-in child [:schema-info :i18nprefix]) "." (get-in child [:data :katselmuksenLaji :value])))
                                                                (when taskname (str " - " taskname)))
                                                    type-name)
                              :locked             true
                              :read-only          (contains? #{:neighbors :statements :verdicts} type)
                              :user               user
                              :created            (now)
                              :required           false
                              :archivable         is-pdf-a?
                              :archivabilityError (when-not is-pdf-a? :invalid-pdfa)
                              :missing-fonts      []
                              :source             {:type (name type) :id id}}]
    (cond-> base-attachment-opts
            (= :tasks type) (assoc :target {:type :task :id id}))))

(defn- get-child-attachment-id [app child-type id]
  (:id (util/find-first
         #(= {:type (name child-type) :id id} (:source %))
         (:attachments app))))

(defn- generate-attachment-from-child!
  "Builds attachment and return attachment data as map"
  [user app child-type child-id lang]
  {:pre [lang child-type]}
  (tracef "   generate-attachment-from-children lang=%s, type=%s, child-id=%s,org: %s, child: %s" lang child-type child-id (:organization app) (get-child app child-type child-id))
  (let [pdf-file (File/createTempFile (str "pdf-generation-" (name lang) "-" (name child-type) "-") ".pdf")
        fis (FileOutputStream. pdf-file)
        attachment-id (get-child-attachment-id app child-type child-id)]
    (case child-type
      :statements (libre-client/generate-statment-pdfa-to-file! app child-id lang pdf-file)
      :verdicts   (libre-client/generate-verdict-pdfa app child-id 0 lang pdf-file)
      (pdf-export/generate-pdf-with-child app child-type child-id lang fis))

    (build-attachment-options user app child-type child-id lang pdf-file attachment-id)))

(defn create-attachment-from-children
  "Generates attachment from child and saves it. Returns created attachment version."
  [user application child-type child-id lang]
  (let [attachment-options (generate-attachment-from-child! user application child-type child-id lang)
        file-options       (select-keys attachment-options [:filename :size :content])
        file (:content attachment-options)]
    (try
      (attachment/upload-and-attach-new! {:application application :user user}
                                         attachment-options
                                         file-options)
      (finally
        (io/delete-file file :silently)))))

(defn delete-child-attachment [app child-type id]
  (attachment/delete-attachment! app (get-child-attachment-id app child-type id)))
