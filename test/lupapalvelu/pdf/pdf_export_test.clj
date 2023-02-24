(ns lupapalvelu.pdf.pdf-export-test
  (:require [clojure.string :as str]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :refer [get-value-by-path]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :refer [with-lang loc] :as i18n]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.test-util :as test-util]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [pdfboxing.text :as pdfbox]
            [sade.files :as files]
            [sade.util :as util]
            [taoensso.timbre :refer [warn]]
            [clojure.string :as s])
  (:import (java.io FileOutputStream)))

(testable-privates lupapalvelu.pdf.pdf-export get-subschemas removable-groups)

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
                       "approval-model-without-approvals"
                       "rahoitus"
                       "visibility-schema" ;; model-test
                       "crossref-schema" ;; model-test
                       })

;; TODO: Remove after Allu applications have been localized
(warn "-------------------------------------------------------")
(warn "----------- Allu schemas not yet localized! -----------")
(warn "-------------------------------------------------------")
(def ignored-schemas (->> lupapalvelu.document.allu-schemas/schema-definitions
                          (map (comp :name :info))
                          set
                          (clojure.set/union ignored-schemas)))

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

(defn- dummy-attachment
  [type signatures contents filename notNeeded]
  {:type          type
   :contents      contents
   :signatures    signatures
   :latestVersion {:filename filename}
   :notNeeded      notNeeded})

(defn- dummy-author
  [first-name last-name email role & {:keys [invite invite-accepted inviter id]}]
  (util/assoc-when
    {:firstName first-name
     :lastName last-name
     :username email
     :role role}
    :id id
    :invite invite
    :inviteAccepted invite-accepted
    :inviter inviter))

(defn make-app [doc-name doc-data]
  {:documents [{:schema-info {:name doc-name}
                :data doc-data}]})

(facts get-value-by-path
  (fact "simple structure"
    (get-value-by-path {:value ..value..} [] "") => ..value..)

  (fact "simple strcuture - with absolute path"
    (get-value-by-path {:value ..value..} [:foo :bar] "/") => ..value..)

  (fact "absolute path"
    (get-value-by-path {:foo {:bar {:value ..value..}}} [:hii :hoo] "/foo/bar") => ..value..)

  (fact "relative path"
    (get-value-by-path {:foo {:quu {:bar {:value ..value..}}}} [:foo :extra] "quu/bar") => ..value..)

  (fact "not found"
    (get-value-by-path {:foo {:quu {:bar {:value ..value..}}}} [:foo] "buz") => nil)

  (fact "no data"
    (get-value-by-path nil [:foo] "buz") => nil))

(facts removable-groups
  (fact "simple structure"
    (removable-groups {:_selected {:value "foo"}}
                      {:body [{:name "_selected" :body [{:name "foo"} {:name "bar"}]}]}
                      []) => #{"bar"})

  (fact "simple structure - multiple omitted groups"
    (removable-groups {:_selected {:value "foo"}}
                      {:body [{:name "_selected" :body [{:name "foo"} {:name "bar"} {:name "quu"}  {:name "quz"}]}]}
                      []) => #{"bar" "quu" "quz"})

  (fact "deeper structure"
    (removable-groups {:quu {:quz {:_selected {:value "foo"}}}}
                      {:body [{:name "_selected" :body [{:name "foo"} {:name "bar"}]}]}
                      [:quu :quz]) => #{"bar"})

  (fact "no value selected -> remove all"
    (removable-groups {:_selected {:value nil}}
                      {:body [{:name "_selected" :body [{:name "foo"} {:name "bar"}]}]}
                      []) => #{"foo" "bar"})

  (fact "no _selected"
    (removable-groups {:quu {:quz {:justGroup {:value "foo"}}}}
                      {:body [{:name "justGroup" :body [{:name "foo"} {:name "bar"}]}]}
                      [:quu :quz]) => #{}))

(facts get-subschemas
  (fact "exclude fields by schema"
    (get-subschemas nil
                    {:_selected {:value "foo"}}
                    {:name "doc"
                     :type :group
                     :body [{:name "goo"
                             :type :group
                             :body [{:name "sub"}]}
                            {:name "foo" :type :text :exclude-from-pdf true}
                            {:name "fiu" :type :text :hidden true}
                            {:name "bar" :type :text}
                            {:name             "gone"
                             :type             :group
                             :exclude-from-pdf true
                             :body             [{:name "also" :type :text}]}]}
                    [])
    => {[] {:fields [{:name "bar", :type :text}],
            :groups [{:name "goo", :type :group, :body [{:name "sub"}]}]}})

  (facts "exclude fields by schema flags"
    (let [foo {:name "foo" :type :text :schema-exclude :one}
          fiu {:name "fiu" :type :text :schema-include :two}
          bar {:name "bar" :type :text}
          gone {:name           "gone"
                :type           :group
                :schema-exclude :one
                :body           [{:name "also" :type :text}]}
          goo {:name "goo"
               :type :group
               :body [{:name "sub"}]}
          schema {:name "doc"
                  :type :group
                  :body [goo foo fiu bar gone]}]
      (get-subschemas nil
                      {:_selected {:value "foo"}}
                      schema
                      [])
      => {[] {:fields [foo bar]
              :groups [goo gone]}}

      (get-subschemas {::pdf-export/schema-flags #{:one :two}}
                      {}
                      schema
                      [])
      => {[] {:fields [fiu bar]
              :groups [goo]}}))

  (fact "excluded by _selected - two options"
    (get-subschemas nil
                    {:_selected {:value "foo"}}
                    {:name "doc"
                     :type :group
                     :body [{:name "_selected"
                             :type :radioGroup
                             :body [{:name "foo" :type :text}
                                    {:name "bar" :type :text}]}
                            {:name "foo" :type :text}
                            {:name "bar" :type :text}]}
                    [])
    => {[] {:fields [{:name "foo", :type :text}],
            :groups []}})

  (fact "excluded by _selected - many options"
    (get-subschemas nil
                    {:_selected {:value "foo"}}
                    {:name "doc"
                     :type :group
                     :body [{:name "_selected"
                             :type :radioGroup
                             :body [{:name "foo" :type :text}
                                    {:name "bar" :type :text}
                                    {:name "fuz" :type :text}]}
                            {:name "foo" :type :text}
                            {:name "bar" :type :text}
                            {:name "fuz" :type :text}
                            {:name "waz" :type :text}]}
                    [])
    => {[] {:fields [{:name "foo", :type :text}
                     {:name "waz", :type :text}],
            :groups []}})

  (fact "excluded by hide-when / show-when - root doc - non-repeating"
    (get-subschemas nil
                    {:foo {:value ..foo..} :bar {:value ..bar..} :quu {:fuz {:value ..fuz..}}}
                    {:name "doc"
                     :type :group
                     :body [{:name "quu" :type :group :body [{:name "buz"} {:name "fuz"}]}
                            {:name "fiu" :type :text :show-when {:path "quu/buz" :values #{..fuz..}}}
                            {:name "fau" :type :text :show-when {:path "quu/fuz" :values #{..waz..}}}
                            {:name "foo" :type :text :show-when {:path "quu/fuz" :values #{..fuz..}}}
                            {:name "bar" :type :text :hide-when {:path "foo" :values #{..goo..}}}
                            {:name "boo" :type :text :hide-when {:path "foo" :values #{..foo..}}}]}
                    [])
    => {[] {:fields [{:name "foo", :type :text, :show-when {:path "quu/fuz", :values #{..fuz..}}},
                     {:name "bar", :type :text, :hide-when {:path "foo", :values #{..goo..}}}]
            :groups [{:name "quu", :type :group, :body [{:name "buz"} {:name "fuz"}]}]}})

  (fact "excluded by cross-document hide-when / show-when - root doc - non-repeating"
    (get-subschemas (make-app "xref" {:foo {:value ..foo..} :bar {:value ..bar..} :quu {:fuz {:value ..fuz..}}})
                    {}
                    {:name "doc"
                     :type :group
                     :body [{:name "quu" :type :group :body [{:name "buz"} {:name "fuz"}]}
                            {:name "fiu" :type :text :show-when {:document "xref" :path "quu/buz" :values #{..fuz..}}}
                            {:name "fau" :type :text :show-when {:document "xref" :path "quu/fuz" :values #{..waz..}}}
                            {:name "foo" :type :text :show-when {:document "xref" :path "quu/fuz" :values #{..fuz..}}}
                            {:name "bar" :type :text :hide-when {:document "xref" :path "foo" :values #{..goo..}}}
                            {:name "boo" :type :text :hide-when {:document "xref" :path "foo" :values #{..foo..}}}]}
                    [])
    => {[] {:fields [{:name "foo", :type :text, :show-when {:document "xref" :path "quu/fuz", :values #{..fuz..}}},
                     {:name "bar", :type :text, :hide-when {:document "xref" :path "foo", :values #{..goo..}}}]
            :groups [{:name "quu", :type :group, :body [{:name "buz"} {:name "fuz"}]}]}})

  (fact "excluded by hide-when / show-when - inner group - non-repeating"
    (get-subschemas nil
                    {:foo {:value ..foo..} :bar {:value ..bar..} :quu {:fuz {:value ..fuz..}}}
                    {:name "quu"
                     :type :group
                     :body [{:name "buz" :type :text :hide-when {:path "fuz" :values #{..fuz..}}}
                            {:name "biz" :type :text :hide-when {:path "fuz" :values #{..guz..}}}
                            {:name "fiz" :type :text :show-when {:path "/foo" :values #{..foo..}}}
                            {:name "fuz" :type :text :show-when {:path "/foo" :values #{..goo..}}}]}
                    [:quu])
    => {[:quu] {:fields [{:name "biz", :type :text, :hide-when {:path "fuz", :values #{..guz..}}},
                         {:name "fiz", :type :text, :show-when {:path "/foo", :values #{..foo..}}}]
                :groups []}})

  (fact "excluded by cross-document hide-when / show-when - inner group - non-repeating"
    (get-subschemas (make-app "xref" {:foo {:value ..foo..} :bar {:value ..bar..} :quu {:fuz {:value ..fuz..}}})
                    {}
                    {:name "quu"
                     :type :group
                     :body [{:name "buz" :type :text :hide-when {:document "xref" :path "quu/fuz" :values #{..fuz..}}}
                            {:name "biz" :type :text :hide-when {:document "xref" :path "quu/fuz" :values #{..guz..}}}
                            {:name "baz" :type :text :show-when {:document "xref" :path "/quu/fuz" :values #{..fuz..}}}
                            {:name "fiz" :type :text :show-when {:document "xref" :path "/foo" :values #{..foo..}}}
                            {:name "fuz" :type :text :show-when {:document "xref" :path "/foo" :values #{..goo..}}}]}
                    [:quu])
    => {[:quu] {:fields [{:name "biz", :type :text, :hide-when {:document "xref" :path "quu/fuz", :values #{..guz..}}}
                         {:name "baz" :type :text :show-when {:document "xref" :path "/quu/fuz" :values #{..fuz..}}}
                         {:name "fiz", :type :text, :show-when {:document "xref" :path "/foo", :values #{..foo..}}}]
                :groups []}})

  (fact "excluded by hide-when / show-when - inner group - repeating"
    (get-subschemas nil
                    {:foo {:value ..foo..}
                     :quu {:0 {:fuz {:value ..0-fuz..}
                               :bar {:value ..0-bar..}}
                           :2 {:fuz {:value ..2-fuz..}
                               :bar {:value ..2-bar..}}}}
                    {:name      "quu"
                     :type      :group
                     :repeating true
                     :body      [{:name "buz" :type :text :hide-when {:path "fuz" :values #{..2-fuz..}}}
                                 {:name "bar" :type :text :hide-when {:path "fuz" :values #{..2-guz..}}}
                                 {:name "fiz" :type :text :hide-when {:path "/foo" :values #{..foo..}}}
                                 {:name "fuz" :type :text :hide-when {:path "/foo" :values #{..goo..}}}]}
                    [:quu])
    => {[:quu :0] {:fields [{:name "buz", :type :text, :hide-when {:path "fuz", :values #{..2-fuz..}}}
                            {:name "bar", :type :text, :hide-when {:path "fuz", :values #{..2-guz..}}}
                            {:name "fuz", :type :text, :hide-when {:path "/foo", :values #{..goo..}}}],
                   :groups []},
        [:quu :2] {:fields [{:name "bar", :type :text, :hide-when {:path "fuz", :values #{..2-guz..}}}
                            {:name "fuz", :type :text, :hide-when {:path "/foo", :values #{..goo..}}}],
                   :groups []}}))

(background (lupapalvelu.organization/get-application-organization anything) => {})

(facts "Generate PDF file from application with all documents"
       (files/with-temp-file
         file

         (let [schema-names (remove ignored-schemas (keys (schemas/get-schemas 1)))
               dummy-docs (map test-util/dummy-doc schema-names)
               application (merge domain/application-skeleton {:documents    dummy-docs
                                                               :municipality "444"
                                                               :state        "draft"})]
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
                              row =not=> (contains "???")))))))))

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
        (files/with-temp-file file
          (let [fis (FileOutputStream. file)]
            (pdf-export/generate-pdf-with-child application :statements "2" lang fis)
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
                (nth rows 34) => "dolor sit amet"))))))))

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
        (files/with-temp-file file
          (let [fis (FileOutputStream. file)]
            (pdf-export/generate-pdf-with-child application :neighbors "2" lang fis)
            (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                  expected-state (if (= lang :fi) "Vastattu" "Besvarad")
                  rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
              (fact "PDF data rows " (count rows) => 32)
              (fact "Pdf data id" (nth rows 22) => "2")
              (fact "Pdf data owner" (nth rows 24) => "Matti Malli")
              (fact "Pdf data state" (nth rows 26) => expected-state)
              (fact "Pdf data message" (nth rows 28) => "SigloXX")
              (fact "Pdf data signature" (nth rows 30) => "TESTAA PORTAALIA, 15.10.2015"))))))))

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
        (files/with-temp-file file
          (let [fis (FileOutputStream. file)]
            (pdf-export/generate-pdf-with-child application :tasks "2" lang fis)
            (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                  rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
              (fact "PDF data rows " (count rows) => 93)
              (fact "Pdf data type " (nth rows 22) => (i18n/localize (name lang) "task-katselmus.katselmuksenLaji.muu katselmus")))))))))

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
    (files/with-temp-file file
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
                                                               :state "draft"})]
        (pdf-export/generate application "fi" file)
        (let [pdf-content (pdfbox/extract (.getAbsolutePath file))]
          (fact {:midje/description loc-role} (re-find (re-pattern (str "(?m)" loc-role)) pdf-content) => truthy)
          (facts {:midje/description (str loc-role " tasks")}
            (doseq [good loc-good-tasks]
              (fact {:midje/description good} (re-find (re-pattern (str "(?m)" good "\\s+Kyll\u00e4")) pdf-content) => truthy))
            (doseq [bad loc-bad-tasks]
              (fact {:midje/description bad} (re-find (re-pattern (str "(?m)" bad "\\s+Kyll\u00e4")) pdf-content) => falsey))))))))

(facts "Foreman roles and tasks"
  (doall (map foreman-pdf-content-check (keys foreman-roles-and-tasks))))

(facts "Attachments table"
  (files/with-temp-file
    file
    (let [attachment1 (dummy-attachment {:type-group "ennakkoluvat_ja_lausunnot" :type-id "naapurin_kuuleminen"}
                                        [{:created 1567596825654 :user {:firstName "Sonja" :lastName "Sibbo"}}]
                                        "Neighbour is mad" "example1.pdf" true)
          attachment2 (dummy-attachment {:type-group "hakija" :type-id "valtakirja"}
                                        []
                                        "Valtakirja" "example2.pdf" false)
          attachment3 (dummy-attachment {:type-group "ennakkoluvat_ja_lausunnot" :type-id "naapurin_huomautus"}
                                        [{:created 1449439200000 :user {:firstName "Sonja" :lastName "Sibbo"}}]
                                        "This is fine" "example3.pdf" false)
          attachments [attachment1 attachment2 attachment3]
          application (merge domain/application-skeleton {:attachments attachments
                                                          :municipality "444"
                                                          :state "draft"})]
      (doseq [lang test-util/test-languages]
        (facts "Table rows contain correct data in right order"
          (pdf-export/generate application lang file)
          (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
                rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
            (when (= lang :fi)
              (fact "Table header"
                (nth rows 22) =>
                "Liitteen nimi Sisält\u00f6 Liiteryhmä Tyyppi Allekirjoitukset")

              (fact "First row of the attachments table"
                (nth rows 23) =>
                "example3.pdf This is fine Ennakkoluvat ja lausunnot Naapurin huomautus Sonja Sibbo07.12.2015")

              (fact "Second row of the attachments table"
                (nth rows 24) =>
                "example1.pdf Neighbour is mad Ennakkoluvat ja lausunnot Naapurin kuuleminen Sonja Sibbo04.09.2019")

              (fact "Third row of the attachments table"
                (nth rows 25) =>
                "example2.pdf Valtakirja Hakija Valtakirja -"))

            (fact "PDF does not contain unlocalized strings"
              (doseq [row rows]
                row =not=> (contains "???")))))))))

(facts "Authors table"
  (files/with-temp-file
    file
    (let [author1 (dummy-author "Sonja" "Sibbo" "sonja@example.com"  "writer" :id "123")
          author2 (dummy-author "Ronja" "Ribbo" "ronja@example.com" "writer" :inviter {:blah "blah"} :invite-accepted 1567596825654)
          author3 (dummy-author "Mikko" "Mikkonen" "mikko@example.com" "reader" :invite {:foo "foo"})
          author4 (dummy-author "Pena" "Penanen" "pena@example.com" "guestAuthority" :inviter "123")
          author5 (dummy-author nil "somebody@example.com" "somebody@example.com" "reader" :invite {:foo "foo"})
          authors [author1 author2 author3 author4 author5]
          application (merge domain/application-skeleton {:auth authors
                                                          :municipality "444"
                                                          :state "draft"
                                                          :creator {:id "123"}})]
      (doseq [lang test-util/test-languages]
        (pdf-export/generate application lang file)
        (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
              rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
          (facts "Table rows contain correct data in right order"
            (when (= lang :fi)
              (fact "Table header"
                (nth rows 22) =>
                "Nimi S\u00e4hk\u00f6posti Rooli Kutsu hyv\u00e4ksytty")

              (fact "First row of the table"
                (nth rows 23) =>
                "Sonja Sibbo sonja@example.com Kirjoitusoikeus Hakemuksen tekij\u00e4")

              (fact "Second row of the table"
                (nth rows 24) =>
                "Ronja Ribbo ronja@example.com Kirjoitusoikeus 04.09.2019")

              (fact "Third row of the table"
                (nth rows 25) =>
                "Pena Penanen pena@example.com Hankekohtainen lukuoikeus -")

              (fact "Third row of the table"
                (nth rows 26) =>
                "Mikko Mikkonen mikko@example.com Lukuoikeus -")

              (fact "Third row of the table"
                (nth rows 27) =>
                "- somebody@example.com Lukuoikeus -"))

            (fact "PDF does not contain unlocalized strings"
              (doseq [row rows]
                row =not=> (contains "???")))))))))
