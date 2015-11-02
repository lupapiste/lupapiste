(ns lupapalvelu.child-to-attachment-test
  (:require [clojure.string :as str]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.itest-util :as util]
            [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
            [midje.sweet :refer :all]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [pdfboxing.text :as pdfbox]
            [clojure.java.io :as io]
            [lupapalvelu.organization :as organization])
  (:import (java.io File)))

(def build-attachment #'lupapalvelu.child-to-attachment/build-attachment)

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

(defn- dummy-application [id type val]
  (merge domain/application-skeleton
         {:id id
          :address " Korpikuusen kannon alla 1 "
          :documents (map util/dummy-doc (remove ignored-schemas (keys (schemas/get-schemas 1))))
          :municipality "444"
          :organization "753-R"
          :tosFunction "10.3.0.1"
          :state "open"}
         {type val}))

(defn- dummy-statement [id name status text]
  {:id id
   :requested nil
   :given nil
   :status status
   :text text
   :person {:name name :text "Paloviranomainen"}
   :attachments [{:target {:type "statement"}} {:target {:type "something else"}}]})


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


(background (lupapalvelu.pdf.pdf-conversion/pdf-a-required? anything) => false)

(facts " Generate attachment from dummy application statements "
       (let [dummy-statements [(dummy-statement "2" "Matti Malli" nil "Lorelei ipsum")
                               (dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero.")]
             application (dummy-application "LP-1" :statements dummy-statements)]
         (doseq [lang i18n/languages]
           (let [file (File/createTempFile (str "child-test-statement-" (name lang)) ".pdf")
                 att (build-attachment nil application :statements "2" lang file)]
             (fact " :contents"
                   (:contents att) => "Paloviranomainen")
             (fact " :attachment-type"
                   (:attachment-type att) => {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"})
             (fact " :archivable"
                   (:archivable att) => false)))))

(facts " Generate attachment from dummy application neightbors "
       (let [dummy-neighbours [(dummy-neighbour "2" "Matti Malli" "response-given" "SigloXX")
                               (dummy-neighbour "1" "Minna Malli" "open" "nada")]
             application (dummy-application "LP-1" :neighbors dummy-neighbours)]
         (doseq [lang i18n/languages]
           (let [file (File/createTempFile (str "child-test-statement-" (name lang)) ".pdf")
                 att (build-attachment nil application :neighbors "2" lang file)]
             (fact " :contents"
                   (:contents att) => "Matti Malli")
             (fact " :attachment-type"
                   (:attachment-type att) => {:type-group "ennakkoluvat_ja_lausunnot" :type-id "selvitys_naapurien_kuulemisesta"})
             (fact " :archivable"
                   (:archivable att) => false)))))