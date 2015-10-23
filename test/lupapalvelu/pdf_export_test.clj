(ns lupapalvelu.pdf-export-test
  (:require [clojure.string :as str]
            [lupapalvelu.pdf-export :as pdf-export]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.itest-util :as util]
            [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
            [midje.sweet :refer :all]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [pdfboxing.text :as pdfbox]
            [clojure.java.io :as io])
  (:import (java.io File)))

(def ignored-schemas #{"hankkeen-kuvaus-jatkoaika"
                       "poikkeusasian-rakennuspaikka"
                       "hulevedet"
                       "talousvedet"
                       "ottamismaara"
                       "ottamis-suunnitelman-laatija"
                       "kaupunkikuvatoimenpide"
                       "task-katselmus"
                       "approval-model-with-approvals"
                       "approval-model-without-approvals"})

(defn- localized-doc-headings [schema-names]
  (map #(loc (str % "._group_label")) schema-names))

(defn- dummy-statement [id name status text]
  {:id id
   :requested nil
   :given nil
   :status status
   :text text
   :person {:name name}
   :attachments [{:target {:type "statement"}} {:target {:type "something else"}}]})

(facts "Generate PDF from dummy application with all documents"
       (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
             dummy-docs (map util/dummy-doc schema-names)
             application (merge domain/application-skeleton {:documents dummy-docs
                                                             :municipality "444"
                                                             :state "draft"})
             file (File/createTempFile "test" ".pdf")]

         (doseq [lang i18n/languages]
           (facts {:midje/description (name lang)}
                  (pdf-export/generate application lang file)
                  (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                        rows (remove str/blank? (str/split pdf-content #"\n"))]

                    (fact "All localized document headers are present in the PDF"
                          (with-lang lang
                                     (doseq [heading (localized-doc-headings schema-names)]
                                       pdf-content => (contains heading :gaps-ok))))

                    #_(fact "PDF does not contain unlocalized strings"
                            (doseq [row rows]
                              row =not=> (contains "???"))))))

         (.delete file)))

(facts "Generate PDF from dummy application statements"
       (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
             dummy-docs (map util/dummy-doc schema-names)
             dummy-statements [(dummy-statement "2" "Matti Malli" nil "Lorelei ipsum" )
                               (dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero.")]
             application (merge domain/application-skeleton {:id "LP-1"
                                                             :address "Korpikuusen kannon alla 1 "
                                                             :documents dummy-docs
                                                             :statements dummy-statements
                                                             :municipality "444"
                                                             :state "draft"})
             ]
         (doseq [lang i18n/languages]
           (facts {:midje/description (name lang)}
                  (let [file (File/createTempFile (str "export-test-" (name lang)) ".pdf")
                        pdf-content (pdf-export/generate-pdf-from-child application lang :statements )
                        ]
                    (io/copy pdf-content file)
                    (debug " Exported statement to: " (.getAbsolutePath file))
                    (.delete file)
                    )))))
