(ns lupapalvelu.child-to-attachment
  (:require
    [lupapalvelu.attachment :as attachment]
    [lupapalvelu.pdf.pdf-export :as pdf-export]
    [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
    [sade.core :refer [def- now]]
    [sade.env :as env]
    [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
    [clojure.pprint :refer [pprint]]
    [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
    [lupapalvelu.pdf.libreoffice-conversion-client :as libre-client]
    [clojure.java.io :as io])
  (:import (java.io File FileOutputStream)))

(defn- get-child [application type id]
  (first (filter #(or (nil? id) (= id (:id %))) (type application))))

(defn build-attachment [user application type id lang file attachment-id]
  {:pre [(map? user) (map? application) (keyword? type) (string? id) (#{:statements :neighbors :verdicts :tasks} type)]}
  (let [is-pdf-a? (pdf-conversion/ensure-pdf-a-by-organization file (:organization application))
        child (get-child application type id)
        type-name (case type
                    :statements (i18n/localize (name lang) "statement.lausunto")
                    :neighbors (i18n/localize (name lang) "application.MM.neighbors")
                    :verdicts (i18n/localize (name lang) (if (:sopimus child) "userInfo.company.contract" "application.verdict.title"))
                    :tasks (i18n/localize (name lang) "task-katselmus.rakennus.tila._group_label"))]
    {:application        application
     :filename           (str type-name ".pdf")
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
                           :tasks (i18n/localize (name lang) (str (get-in child [:schema-info :i18nprefix]) "." (get-in child [:data :katselmuksenLaji :value])))
                           type-name)
     :locked             true
     :read-only          (contains? #{:neighbors :statements :verdicts} type)
     :user               user
     :created            (now)
     :required           false
     :archivable         is-pdf-a?
     :archivabilityError (when-not is-pdf-a? :invalid-pdfa)
     :missing-fonts      []
     :source             {:type type :id id}}))

(defn- get-child-attachment-id [app child-type id]
  (let [attachment (filter #(= {:type (name child-type) :id id} (:source %)) (:attachments app))
        attachment-id (:id (first attachment))]
    attachment-id))

(defn- generate-attachment-from-children!
  "Builds attachment and return attachment data as map"
  [user app child-type id lang]
  {:pre [lang child-type]}
  (tracef "   generate-attachment-from-children lang=%s, type=%s, id=%s,org: %s, child: %s" lang child-type id (:organization app) (get-child app child-type id))
  (let [pdf-file (File/createTempFile (str "pdf-generation-" (name lang) "-" (name child-type) "-") ".pdf")
        fis (FileOutputStream. pdf-file)
        attachment-id (get-child-attachment-id app child-type id)]
    (case child-type
      :statements (libre-client/generate-statment-pdfa-to-file! app id lang pdf-file)
      :verdicts   (libre-client/generate-verdict-pdfa app id 0 lang pdf-file)
      (pdf-export/generate-pdf-with-child app child-type id lang fis))

    (build-attachment user app child-type id lang pdf-file attachment-id)))

(defn create-attachment-from-children
  "Generates attachment from child and saves it. Returns created attachment version."
  [user app child-type id lang]
  (let [child (generate-attachment-from-children! user app child-type id lang)
        file (:content child)]
    (try
      (attachment/attach-file! app child)
      (finally
        (io/delete-file file :silently)))))

(defn delete-child-attachment [app child-type id]
  (attachment/delete-attachment! app (get-child-attachment-id app child-type id)))
