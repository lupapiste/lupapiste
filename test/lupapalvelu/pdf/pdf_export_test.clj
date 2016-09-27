(ns lupapalvelu.pdf.pdf-export-test
  (:require [clojure.string :as str]
            [sade.util :as util]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
            [midje.sweet :refer :all]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [pdfboxing.text :as pdfbox]
            [clojure.java.io :as io])
  (:import (java.io File FileOutputStream)))

(def ignored-schemas #{"hankkeen-kuvaus-jatkoaika"
                       "poikkeusasian-rakennuspaikka"
                       "hulevedet"
                       "talousvedet"
                       "ottamismaara"
                       "ottamis-suunnitelman-laatija"
                       "kaupunkikuvatoimenpide"
                       "task-katselmus"
                       "tyonjohtaja"
                       "approval-model-with-approvals"
                       "approval-model-without-approvals"})

(defn- localized-doc-headings [schema-names]
  (map #(loc (str % "._group_label")) schema-names))

(def yesterday (- (System/currentTimeMillis) (* 1000 60 60 24)))
(def today (System/currentTimeMillis))

(defn- dummy-statement [id name status text saateText nothing-to-add reply-text]
  (cond-> {:id id
           :requested 1444802294666
           :given 1444902294666
           :status status
           :text text
           :dueDate 1449439200000
           :saateText saateText
           :person {:name name}
       ;   :attachments [{:target {:type "statement"}} {:target {:type "something else"}}]
           }
    (not (nil? nothing-to-add)) (assoc-in [:reply :nothing-to-add] nothing-to-add)
    (not (nil? reply-text)) (assoc-in [:reply :text] reply-text)))

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

(defn- dummy-task [id name]
  {:id          id
   :schema-info {:name       "task-katselmus",
                 :type       "task"
                 :order      1
                 :i18nprefix "task-katselmus.katselmuksenLaji"
                 :version    1}
   :data        {:katselmuksenLaji {:value name}
                 :rakennus {"0" {:kayttoonottava {:value false} :rakennus {:rakennusnro {:value "rak0"}}}
                            "1" {:kayttoonottava {:value false} :rakennus {:rakennusnro {:value "rak1"}}}
                            "2" {:kayttoonottava {:value true} :rakennus {:rakennusnro {:value "rak2"}}}
                                    }}})

(facts "Generate PDF file from application with all documents"
       (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
             dummy-docs (map test-util/dummy-doc schema-names)
             application (merge domain/application-skeleton {:documents dummy-docs
                                                             :municipality "444"
                                                             :state "draft"})
             file (File/createTempFile "test" ".pdf")]

         (doseq [lang test-util/test-languages]
           (facts {:midje/description (name lang)}
                  (pdf-export/generate application lang file)
                  (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                        rows (remove str/blank? (str/split pdf-content #"\r?\n"))]

                    (fact "All localized document headers are present in the PDF"
                          (with-lang lang
                                     (doseq [heading (localized-doc-headings schema-names)]
                                       pdf-content => (contains heading :gaps-ok))))

                    (fact "PDF does not contain unlocalized strings"
                          (doseq [row rows]
                            row =not=> (contains "???"))))))

         (.delete file)))

(facts "Generate PDF from application statements"
       (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
             dummy-docs (map test-util/dummy-doc schema-names)
             dummy-statements [(dummy-statement "2" "Matti Malli" "puollettu" "Lorelei ipsum" "Saatteen sisalto" false "dolor sit amet")
                               (dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero." "Saatteen sisalto" nil nil)]
             application (merge domain/application-skeleton {:id "LP-1"
                                                             :address "Korpikuusen kannon alla 1 "
                                                             :documents dummy-docs
                                                             :statements dummy-statements
                                                             :municipality "444"
                                                             :state "draft"})]
         (doseq [lang test-util/test-languages]
           (facts {:midje/description (name lang)}
                  (let [file (File/createTempFile (str "export-test-statement-" (name lang) "-") ".pdf")
                        fis (FileOutputStream. file)]
                    (pdf-export/generate-pdf-with-child application :statements "2" lang fis)
                    (fact "File exists " (.exists file))
                    (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                          rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
                      (fact "PDF data rows "
                        (count rows) => 36
                        (nth rows 22) => "14.10.2015"
                        (nth rows 24) => "Matti Malli"
                        (nth rows 26) => "15.10.2015"
                        (nth rows 28) => "puollettu"
                        (nth rows 30) => "Lorelei ipsum"
                        (nth rows 32) => "07.12.2015"
                        (nth rows 34) => "dolor sit amet"))
                    (.delete file))))))

(facts "Generate PDF from application neigbors - signed"
       (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
             dummy-docs (map test-util/dummy-doc schema-names)
             dummy-neighbours [(dummy-neighbour "2" "Matti Malli" "response-given" "SigloXX")
                               (dummy-neighbour "1" "Minna Malli" "open" "nada")]
             application (merge domain/application-skeleton {:id "LP-1"
                                                             :address "Korpikuusen kannon alla 1 "
                                                             :documents dummy-docs
                                                             :neighbors dummy-neighbours
                                                             :municipality "444"
                                                             :state "draft"})]
         (doseq [lang test-util/test-languages]
           (facts {:midje/description (name lang)}
                  (let [file (File/createTempFile (str "export-test-neighbor-" (name lang) "-") ".pdf")
                        fis (FileOutputStream. file)]
                    (pdf-export/generate-pdf-with-child application :neighbors "2" lang fis)
                    (fact "File exists " (.exists file))
                    (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                          expected-state (if (= lang :fi) "Vastattu" "Besvarad")
                          rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
                      (fact "PDF data rows " (count rows) => 32)
                      (fact "Pdf data id" (nth rows 22) => "2")
                      (fact "Pdf data owner" (nth rows 24) => "Matti Malli")
                      (fact "Pdf data state" (nth rows 26) => expected-state)
                      (fact "Pdf data message" (nth rows 28) => "SigloXX")
                      (fact "Pdf data signature" (nth rows 30) => "TESTAA PORTAALIA, 15.10.2015"))
                    (.delete file))))))

(facts "Generate PDF from application stasks - signed"
       (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
             dummy-docs (map test-util/dummy-doc schema-names)
             dummy-tasks [(dummy-task "2" "muu katselmus")
                          (dummy-task "1" "muu katselmus")]
             application (merge domain/application-skeleton {:id "LP-1"
                                                             :address "Korpikuusen kannon alla 1 "
                                                             :documents dummy-docs
                                                             :tasks dummy-tasks
                                                             :municipality "444"
                                                             :state "draft"})]
         (doseq [lang test-util/test-languages]
           (facts {:midje/description (name lang)}
                  (let [file (File/createTempFile (str "export-test-tasks-" (name lang) "-") ".pdf")
                        fis (FileOutputStream. file)]
                    (pdf-export/generate-pdf-with-child application :tasks "2" lang fis)
                    (fact "File exists " (.exists file))
                    (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                          rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
                      (fact "PDF data rows " (count rows) => 93)
                      (fact "Pdf data type " (nth rows 22) => (i18n/localize (name lang) "task-katselmus.katselmuksenLaji.muu katselmus"))
                       )
                    (.delete file))))))

(def foreman-roles-and-tasks {"vastaava ty\u00f6njohtaja"      ["rakennuksenMuutosJaKorjaustyo"
                                                           "uudisrakennustyoMaanrakennustoineen"
                                                           "uudisrakennustyoIlmanMaanrakennustoita"
                                                           "linjasaneeraus"
                                                           "maanrakennustyot"
                                                           "rakennuksenPurkaminen"]
                              "KVV-ty\u00f6njohtaja"           ["sisapuolinenKvvTyo"
                                                           "ulkopuolinenKvvTyo"]
                              "IV-ty\u00f6njohtaja"            ["ivLaitoksenAsennustyo"
                                                           "ivLaitoksenKorjausJaMuutostyo"]
                              "erityisalojen ty\u00f6njohtaja" []})

(defn loc-tasks [role]
  (with-lang :fi (doall (map #(loc (str "osapuoli.tyonjohtaja.vastattavatTyotehtavat." %))
                       (get foreman-roles-and-tasks role)))))

(defn foreman-pdf-content-check [role]
  (facts {:midje/description (str "Foreman role " role " should have these tasks: " (get foreman-roles-and-tasks role))}
         (let [loc-role       (with-lang :fi (loc (str "osapuoli.tyonjohtaja.kuntaRoolikoodi." role)))
               loc-good-tasks (loc-tasks role)
               loc-bad-tasks  (->> (keys foreman-roles-and-tasks)
                                   (remove #(= role %))
                                   (map loc-tasks)
                                   (reduce concat))
               schema-names   (remove ignored-schemas (keys (schemas/get-schemas 1)))
               dummy-docs     (map test-util/dummy-doc schema-names)
               foreman-pred   #(= (-> % :schema-info :name) "tyonjohtaja-v2")
               foreman-doc    (assoc-in (util/find-first foreman-pred dummy-docs) [:data :kuntaRoolikoodi :value] role)
               dummy-docs     (cons foreman-doc (remove foreman-pred dummy-docs))
               application    (merge domain/application-skeleton {:id "LP-88"
                                                                  :address "Dongzhimen"
                                                                  :documents dummy-docs
                                                                  :municipality "888"
                                                                  :state "draft"})
               file           (File/createTempFile "export-test-foreman" ".pdf")]
           (pdf-export/generate application "fi" file)
           (fact "File exists " (.exists file))
           (let [pdf-content (pdfbox/extract (.getAbsolutePath file))]
             (.delete file)
             (fact {:midje/description loc-role} (re-find (re-pattern (str "(?m)" loc-role)) pdf-content) => truthy)
             (facts {:midje/description (str loc-role " tasks")}
                    (doseq [good loc-good-tasks]
                      (fact {:midje/description good} (re-find (re-pattern (str "(?m)" good "\\s+Kyll\u00e4")) pdf-content) => truthy))
                    (doseq [bad loc-bad-tasks]
                      (fact {:midje/description bad} (re-find (re-pattern (str "(?m)" bad "\\s+Kyll\u00e4")) pdf-content) => falsey)))))))

(facts "Foreman roles and tasks"
       (doall (map foreman-pdf-content-check (keys foreman-roles-and-tasks))))
