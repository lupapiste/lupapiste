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


(defn- dummy-neighbour [id name status]
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
   :status [{:state status
             :user {:firstName "Sonja" :lastName "Sibbo"}
             :created 1444902294666}]})

(defn- file-exists [data]
  (.exists (:content data)))

(defn- filesize-exists [data]
  (> (:size data) 0))




(facts " Generate PDF from dummy application statements "
       (let [dummy-statements [(dummy-statement "2" " Matti Malli " nil " Lorelei ipsum ")
                               (dummy-statement "1" " Minna Malli " " joku status " " Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero. ")]
             application (dummy-application "LP-1" :statements dummy-statements)]
         (doseq [lang i18n/languages]
           (debug " org ok : " (not-empty (:organization application)))
           (fact {:midje/description (name lang)}
                 (let [pdf-content (child-to-attachment/generate-attachment-from-children nil application lang :statements "2")]
;                   (debug " Exported statement : " (with-out-str (clojure.pprint/pprint pdf-content)))
                   pdf-content
                   ) => (every-checker file-exists filesize-exists)
             (provided (lupapalvelu.pdf.pdf-conversion/pdf-a-required? anything) => false)))))

(facts " Generate PDF from dummy application neighbours "
       (let [dummy-neighbours [(dummy-neighbour "2" " Matti Malli " "mark-done")
                               (dummy-neighbour "1" " Minna Malli " "open")]
             application (dummy-application "LP-1" :neighbors dummy-neighbours)]
         (doseq [lang i18n/languages]
           (fact {:midje/description (name lang)}
                 (let [pdf-content (child-to-attachment/generate-attachment-from-children nil application lang :neighbors "2")]
                   ;                   (debug " Exported neighbours file: " (:content pdf-content))
                   ;                   (debug " Exported neighbours: " pdf-content)
                   pdf-content
                   ) => (every-checker file-exists filesize-exists)
                 (provided (lupapalvelu.pdf.pdf-conversion/pdf-a-required? anything) => false)))))
