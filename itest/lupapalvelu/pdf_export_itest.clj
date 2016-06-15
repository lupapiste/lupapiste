(ns lupapalvelu.pdf-export-itest
    (:require [clojure.java.io :as io]
              [midje.sweet :refer :all]
              [midje.util :refer [testable-privates]]
              [pdfboxing.text :as pdfbox]
              [sade.util :as util]
              [lupapalvelu.test-util :refer [dummy-doc]]
              [lupapalvelu.itest-util :refer [apply-remote-minimal pena query-application] :as itu]
              [lupapalvelu.pdf.pdf-export :as pdf-export]
              [lupapalvelu.pdf.pdfa-conversion :as pdfa-conversion]
              [lupapalvelu.i18n :refer [with-lang loc]]
              [lupapalvelu.mongo :as mongo]
              [lupapalvelu.i18n :as i18n]
              [lupapalvelu.domain :as domain]
              [lupapalvelu.document.schemas :as schemas])
  (:import (java.io File FileOutputStream)))

(apply-remote-minimal)

(def pdfa #'lupapalvelu.pdf.pdf-export/generate-pdf-with-child)

(defn- localize-value [value]
  (if (instance? Boolean value)
    (loc (if value "yes" "no"))
    value))

(defn- walk-function [pdf-content node]
  ; What to do when this is a value node
  (when (and (map? node) (contains? node :value))
    (let [loc-value (localize-value (:value node))]
      pdf-content => (contains loc-value)))

  ; When do we need to stop going deeper in the tree
  (if (and (vector? node) (#{:_selected :userId} (first node)))
    nil
    node))

(def ignored-schemas #{"hankkeen-kuvaus-jatkoaika"
                       "poikkeusasian-rakennuspaikka"
                       "hulevedet"
                       "talousvedet"
                       "ottamismaara"
                       "ottamis-suunnitelman-laatija"
                       "kaupunkikuvatoimenpide"
                       "task-katselmus"
                       "task-katselmus-backend"
                       "approval-model-with-approvals"
                       "approval-model-without-approvals"})

(defn- localized-doc-headings [schema-names]
  (map #(loc (str % "._group_label")) schema-names))

(def yesterday (- (System/currentTimeMillis) (* 1000 60 60 24)))
(def today (System/currentTimeMillis))

(defn- dummy-statement [id name status text saateText]
  {:id id
   :requested 1444802294666
   :given 1444902294666
   :status status
   :text text
   :dueDate 1449439200000
   :saateText saateText
   :person {:name name}
;   :attachments [{:target {:type "statement"}} {:target {:type "something else"}}]
   })

(defn- dummy-neighbour [id name status message]
  {:propertyId id
   :owner {:type "luonnollinen"
           :name name
           :email nil
           :businessID nil
           :nameOfDeceased nil
           :address
           {:street "Valli & kuja I/X:s Gaatta"
            :city "Helsinki"
            :zip "00100"}}
   :id id
   :status [{:state nil
             :user {:firstName nil :lastName nil}
             :created nil}
            {:state status
             :message message
             :user {:firstName "Sonja" :lastName "Sibbo"}
             :vetuma {:firstName "TESTAA" :lastName "PORTAALIA"}
             :created 1444902294666}]})

(fact "Generate PDF from an R application with dummy document values"
  (let [test-municipality        444
        test-address             "Testikuja 1234"
        test-property-id         "44400100100100"
        test-submitted           (clj-time.coerce/to-long "2014-01-01")
        test-authority           {:id "foo" :firstName "Erkki" :lastName "Testihenkilo"}
        test-primaryOperation    {:name "kerrostalo-rivitalo"}
        test-secondaryoperations [{:name "aita"}]

        application (itu/create-and-submit-application pena)
        _           (itu/generate-documents application pena)
        application (query-application pena (:id application))

        ; Add some manual test data to application common fields
        application (merge application
                           {:municipality        test-municipality
                            :address             test-address
                            :submitted           test-submitted
                            :propertyId          test-property-id
                            :authority           test-authority
                            :primaryOperation    test-primaryOperation
                            :secondaryOperations test-secondaryoperations})

        lang        "fi"
        file        (File/createTempFile "pdf-export-itest-" ".pdf")]


    (with-lang lang
      (fact "Test data assertions (just in case)"
        (loc (str "municipality." test-municipality)) => "Lohja"
        (loc (str "operations.kerrostalo-rivitalo")) => "Asuinkerrostalon tai rivitalon rakentaminen"
        (loc (str "operations.aita")) => "Aidan rakentaminen")

      (pdf-export/generate application lang file)

      (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
            documents-data (map :data (:documents application))]

        ; common fields
        pdf-content => (contains test-address)
        pdf-content => (contains "Lohja")
        pdf-content => (contains (loc (:state application)))
        pdf-content => (contains "444-1-10-100")
        pdf-content => (contains "01.01.2014")
        pdf-content => (contains (:id application))
        pdf-content => (contains "Testihenkilo Erkki")
        pdf-content => (contains "Asuinkerrostalon tai rivitalon rakentaminen, Aidan rakentaminen")
        ; documents
        (doseq [doc-data documents-data]
          (clojure.walk/prewalk (partial walk-function pdf-content) doc-data)))
        (.delete file))))

(facts "Generated statement PDF is valid PDF/A"
  (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
        dummy-docs (map dummy-doc schema-names)
        dummy-statements [(dummy-statement "2" "Matti Malli" "puollettu" "Lorelei ipsum" "Saatteen sisalto")
                          (dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero." "Saatteen sisalto")]
        application (merge domain/application-skeleton {:id           "LP-1"
                                                        :address      "Korpikuusen kannon alla 1 "
                                                        :documents    dummy-docs
                                                        :statements   dummy-statements
                                                        :municipality "444"
                                                        :state        "draft"})]
    (doseq [lang i18n/languages]
      (fact {:midje/description (name lang)}
        (against-background
          [(mongo/update "statistics" {:type "pdfa-conversion"} anything :upsert true) => nil]
          (let [file (File/createTempFile (str "export-test-statement-pdfa-" (name lang)) ".pdf")
                fis (FileOutputStream. file)]
            (pdfa application :statements "2" lang fis)
            (fact "File exists " (.exists file))
            (fact "File not empty " (> (.length file) 1))
            (fact "File is valid PDF/A " (pdfa-conversion/convert-file-to-pdf-in-place file))
            (.delete file)))))))
