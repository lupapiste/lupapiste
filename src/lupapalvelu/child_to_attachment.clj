(ns lupapalvelu.child-to-attachment
  (:require
    [lupapalvelu.attachment :as attachment]
    [lupapalvelu.pdf.pdf-export :as pdf-export]
    [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
    [sade.core :refer [def- now]]
    [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
    [clojure.pprint :refer [pprint]]
    [lupapalvelu.pdf.pdf-conversion :as pdf-conversion])
  (:import (java.io File FileOutputStream)))

(defn- get-child [application type id]
  (first (filter #(or (nil? id) (= id (:id %))) (type application))))

(defn- build-attachment [user application type id lang file]
  (let [is-pdf-a? (pdf-conversion/ensure-pdf-a-by-organization file (:organization application))
        type-name (case type
                    :statements (i18n/localize (name lang) "statement.lausunto")
                    :neighbors (i18n/localize (name lang) "application.MM.neighbors")
                    :verdicts (i18n/localize (name lang) "application.verdict.title"))
        child (get-child application type id)]
    (debug "building attachemnt form child: " child )
    {:application application
     :filename (str type-name ".pdf")
     :size (.length file)
     :content file
     :attachment-id nil
     :attachment-type (case type
                        :neighbors {:type-group "ennakkoluvat_ja_lausunnot" :type-id "selvitys_naapurien_kuulemisesta"}
                        :statements {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"}
                        {:type-group "muut" :type-id "muu"})
     :op nil
     :contents (case type
                 :statements (get-in child [:person :text])
                 :neighbors (get-in child [:owner :name])
                 type-name)
     :locked true
     :user user
     :created (now)
     :required false
     :archivable is-pdf-a?
     :archivabilityError (when-not is-pdf-a? :invalid-pdfa)
     :missing-fonts []}))

(defn generate-attachment-from-children [user app child-type id lang]
  "Builds attachment and return attachment data as map"
  (trace "   generate-attachment-from-children lang=" (name lang) ", type=" (name child-type) ", id=" id ",org: " (:organization app) ", child: " (get-child app child-type id))
  (let [pdf-file (File/createTempFile (str "pdf-export-" (name lang) "-" (name child-type) "-") ".pdf")
        fis (FileOutputStream. pdf-file)]
    (pdf-export/generate-pdf-with-child app child-type id lang fis)
    (build-attachment user app child-type id lang pdf-file)))

(defn create-attachment-from-children [user app child-type id lang]
  "Generates attachment from child and saves it"
  (let [child (generate-attachment-from-children user app child-type id lang)]
    (attachment/attach-file! child)))
