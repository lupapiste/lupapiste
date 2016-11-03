(ns lupapalvelu.child-to-attachment-test
  (:require [clojure.java.io :as io]
            [sade.files :as files]
            [lupapalvelu.child-to-attachment :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [taoensso.timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]])
  (:import (java.io File)))

(testable-privates lupapalvelu.child-to-attachment build-attachment-options)

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
         {:id           id
          :address      " Korpikuusen kannon alla 1 "
          :documents    (map test-util/dummy-doc (remove ignored-schemas (keys (schemas/get-schemas 1))))
          :municipality "444"
          :organization "753-R"
          :tosFunction  "10.3.0.1"
          :state        "open"}
         {type val}))

(defn- dummy-statement [id name status text]
  {:id          id
   :requested   nil
   :given       nil
   :status      status
   :text        text
   :person      {:name name :text "Paloviranomainen"}
   :attachments [{:target {:type "statement"}} {:target {:type "something else"}}]})


(defn- dummy-neighbour [id name status message]
  {:propertyId id
   :owner      {:type           "luonnollinen"
                :name           name
                :email          nil
                :businessID     nil
                :nameOfDeceased nil
                :address
                                {:street "Valli & kuja I/X:s Gaatta"
                                 :city   "Helsinki"
                                 :zip    "00100"}}
   :id         id
   :status     [{:state   nil
                 :user    {:firstName nil :lastName nil}
                 :created nil}
                {:state   status
                 :message message
                 :user    {:firstName "Sonja" :lastName "Sibbo"}
                 :vetuma  {:firstName "TESTAA" :lastName "PORTAALIA"}
                 :created 1444902294666}]})

(defn- dummy-task [id name & [taskname]]
  {:id          id
   :taskname    taskname
   :schema-info {:name       "task-katselmus",
                 :type       "task"
                 :order      1
                 :i18nprefix "task-katselmus.katselmuksenLaji"
                 :version    1}
   :data        {:katselmuksenLaji {:value name}}})


(background (lupapalvelu.pdf.pdfa-conversion/pdf-a-required? anything) => false)

(facts "Generate attachment from dummy application statements"
  (let [dummy-statements [(dummy-statement "2" "Matti Malli" nil "Lorelei ipsum")
                          (dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero.")]
        application (dummy-application "LP-1" :statements dummy-statements)]
    (doseq [lang i18n/languages]
      (files/with-temp-file file
        (let [att (build-attachment-options {} application :statements "2" lang file nil)]
          (fact ":contents"
            (:contents att) => "Paloviranomainen")
          (fact " :attachment-type"
            (:attachment-type att) => {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"})
          (fact ":archivable"
            (:archivable att) => false)
          (fact ":read-only"
            (:read-only att) => true))))))

(facts "Generate attachment from dummy application neighbors"
  (let [dummy-neighbours [(dummy-neighbour "2" "Matti Malli" "response-given" "SigloXX")
                          (dummy-neighbour "1" "Minna Malli" "open" "nada")]
        application (dummy-application "LP-1" :neighbors dummy-neighbours)]
    (doseq [lang i18n/languages]
      (files/with-temp-file file
        (let [att (build-attachment-options {} application :neighbors "2" lang file nil)
              att-other (build-attachment-options {} application :tasks "2" lang file nil)]
          (fact ":contents"
            (:contents att) => "Matti Malli")
          (fact ":attachment-type"
            (:attachment-type att) => {:type-group "ennakkoluvat_ja_lausunnot" :type-id "naapurin_kuuleminen"})
          (fact ":archivable"
            (:archivable att) => false)
          (fact ":read-only"
            (:read-only att) => true)
          (fact ":read-only of attachment with other type than :neighbors or :statements"
            (:read-only att-other) => false))))))

(facts "Generate attachment from dummy application tasks"
  (let [dummy-tasks [(dummy-task "2" "muu katselmus" "katselmointi ftw")
                     (dummy-task "1" "muu katselmus")]
        application (dummy-application "LP-1" :tasks dummy-tasks)]
    (doseq [lang i18n/languages]
      (files/with-temp-file file
        (let [att (build-attachment-options {} application :tasks "2" lang file nil)
              att1 (build-attachment-options {} application :tasks "1" lang file "one")
              att-other (build-attachment-options {} application :tasks "2" lang file nil)]
          (fact ":contents"
            (:contents att) => (str (i18n/localize lang "task-katselmus.katselmuksenLaji.muu katselmus") " - katselmointi ftw"))
          (fact ":attachment-type"
            (:attachment-type att) => {:type-group "katselmukset_ja_tarkastukset" :type-id "katselmuksen_tai_tarkastuksen_poytakirja"})
          (fact ":archivable"
            (:archivable att) => false)
          (fact ":read-only"
            (:read-only att) => false)
          (fact ":attachment-id is nil"
            (:attachment-id att) => nil)
          (fact ":attachment-id exists"
            (:attachment-id att1) => "one")
          (fact ":read-only of attachment with other type than :neighbors or :statements"
            (:read-only att-other) => false)
          (fact "attachment targets the original task"
            (:target att) => {:type :task :id "2"}))))))
