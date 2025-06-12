(ns lupapalvelu.notices.pdf-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.notices.pdf :as pdf]
            [lupapalvelu.pate-test-util :as ptu]
            [sade.date :as date]))

(def bold {:style {:font-weight :bold}})
(def ws {:style {:white-space :pre-wrap}})

(def applicants #'pdf/applicants)

(deftest applicants-test
  (let [person1  (ptu/party-person "Demi" "Demolisher" "Boomway" "12345"
                                   "Tampere" "FIN")
        person2  (ptu/party-person " Wacky " " Whacker " " Avenue " " 00056 "
                                   " Shire " " TUN ")
        person3  (ptu/party-person "" "" "" "" "" "FIN")
        company1 (ptu/party-company "Corporation" "Loop" "00123" "Town" "USA"
                                    "Bob" "Bossman")
        company2 (ptu/party-company " Business " " Cul-de-sac " " 00789 "
                                    " Turku " " FIN ")
        company3 (ptu/party-company "" "" "" "" "FIN")
        make-cmd (fn [& docs]
                   {:lang        "fi"
                    :application {:documents (into [{:foo "bar"}] docs)}})]
    (is (= [[:div bold "Hakija"]
            [:div ws "Demi Demolisher, Boomway, 12345 Tampere"] ]
           (applicants (make-cmd person1)))
        "Person")
    (is (= [[:div bold "Hakija"]
            [:div ws "Demi Demolisher, Boomway, 12345 Tampere"] ]
           (applicants (make-cmd company3 person1 person3)))
        "Person and blanks")
    (is (= [[:div bold "Hakija"]
            [:div ws "Corporation, Loop, 00123 Town, Yhdysvallat (USA)"]]
           (applicants (make-cmd company1)))
        "Company")
    (is (= [[:div bold "Hakijat"]
            [:div ws "Wacky Whacker, Avenue, 00056 Shire, Tunisia\nBusiness, Cul-de-sac, 00789 Turku"]]
           (applicants (make-cmd person2 company2)))
        "Two applicants")
    (is (nil? (applicants (make-cmd))) "No applicants")
    (is (nil? (applicants (make-cmd person3))) "Blank person")
    (is (nil? (applicants (make-cmd company3))) "Blank company")
    (is (nil? (applicants (make-cmd person3 company3))) "Blank both")))

(def demolition-target #'pdf/demolition-target)

(deftest demolition-target-test
  (let [doc      {:schema-info {:name "purkamisilmoitus"}
                  :data        {:buildingDemolition      true
                                :kohde                   " Parking lot. "
                                :valtakunnallinenNumero  " VTJ-PRT "
                                :manuaalinen_rakennusnro " 987 "}}
        make-cmd (fn [& {:as m}]
                   {:lang        "fi"
                    :application {:documents [(update doc :data merge m)]}})
        d-target (fn [& args]
                   (demolition-target (apply make-cmd args)))
        title    [:div bold "Purkamisen kohde"]]
    (is (= [title [:div ws "Rakennus VTJ-PRT"]] (d-target))
        "Bulding with vtj-prt")
    (is (= [title [:div ws "Rakennus"]]
           (d-target :valtakunnallinenNumero "  "))
        "Building without vtj-prt")
    (is (= [title [:div ws "Rakennus 987"]]
           (d-target :buildingId "other"))
        "Building with manual id")
    (is (= [title [:div ws "Rakennus"]]
           (d-target :buildingId "other" :manuaalinen_rakennusnro "  "))
        "Building with blank manual id")
    (is (= [title [:div ws "Parking lot."]]
           (d-target :buildingDemolition false))
        "Non-building target")))

(def operation-name #'pdf/operation-name)

(deftest operation-name-test
  (letfn [(make-cmd [lang op-name]
            {:lang lang :application {:primaryOperation {:name op-name}}})
          (op-name [lang s] (operation-name (make-cmd lang s)))]
    (is (= "Purkamisilmoitus (Rakentamislaki 55§)"
           (op-name "fi" "purkamisilmoitus")))
    (is (= "Rivningslov (rivning av byggnad)" (op-name "sv" "purkaminen")))))

(def body-tags #'pdf/body-tags)

(deftest body-tags-test
  (let [resolution {:state "approved" :published (date/timestamp "8.7.2025")
                    :user  {:firstName " Annie " :lastName " Authority "}
                    :text  [{:insert "Hello"}]}
        applicant  (ptu/party-person " Abe " " Applicant " "Happy Path"
                                     "33100" "Tampere" "FIN")
        op-doc     {:schema-info {:name "purkamisilmoitus"}
                    :data        {:buildingDemolition      true
                                  :kohde                   " Parking lot. "
                                  :valtakunnallinenNumero  " VTJ-PRT "
                                  :manuaalinen_rakennusnro " 987 "}}
        make-cmd   (fn [& docs]
                     {:lang        "fi"
                      :application {:id               "LP-123-2025-00012"
                                    :primaryOperation {:name "purkamisilmoitus"}
                                    :documents        (into [{:foo "bar"}] docs)}})
        b-tags     (fn [& docs]
                     (body-tags (apply make-cmd docs) resolution))
        grid       {:style
                    {:display :grid, :gap "1em 1em", :grid-template-columns "auto 1fr"}}
        title      [:div
                    {:style {:grid-column "1 / -1"}}
                    [:h2 "Purkamisilmoitus (Rakentamislaki 55§)"]]
        app-id1    [:div bold "Asiointitunnus"]
        app-id2    [:div "LP-123-2025-00012"]]
    (is (= [:div grid title
            app-id1 app-id2
            [:div bold "Hakija"]
            [:div ws "Abe Applicant, Happy Path, 33100 Tampere"]
            [:div bold "Purkamisen kohde"]
            [:div ws "Rakennus VTJ-PRT"]
            [:div bold "Ilmoituksen tila"]
            [:div "Hyväksytty"]
            [:div bold "Huomiot"]
            [:div.quill [:span.quill [:p.quill [:span.quill "Hello"]]]]
            [:div]
            [:div "8.7.2025"]
            [:div bold "Käsittelijä"]
           [:div "Annie Authority"]]
           (b-tags applicant op-doc))
        "All the information")
    (is (= [:div grid title
            app-id1 app-id2
            [:div bold "Ilmoituksen tila"]
            [:div "Hyväksytty"]
            [:div bold "Huomiot"]
            [:div.quill [:span.quill [:p.quill [:span.quill "Hello"]]]]
            [:div]
            [:div "8.7.2025"]
            [:div bold "Käsittelijä"]
           [:div "Annie Authority"]]
           (b-tags))
        "Applicant and target missing")))

(def header-tags #'pdf/header-tags)

(deftest header-tags-test
  (let [cmd {:organization (delay {:name {:fi " Tampere "
                                          :sv " Tammerfors"}})
             :lang         "fi"
             :created      (date/timestamp "8.7.2025")}]
    (is (= [:div.header
            [:div.section.header
             [:div.row.pad-after
              [:div.cell.cell--50 "Tampere"]
              [:div.cell.cell--50.right
               "8.7.2025"]]]]
           (header-tags cmd))
        "Finnish")
    (is (= [:div.header
            [:div.section.header
             [:div.row.pad-after
              [:div.cell.cell--50 "Tammerfors"]
              [:div.cell.cell--50.right
               "8.7.2025"]]]]
           (header-tags (assoc cmd :lang "sv")))
        "Swedish")
    (is (= [:div.header
            [:div.section.header
             [:div.row.pad-after
              [:div.cell.cell--50 nil]
              [:div.cell.cell--50.right
               "8.7.2025"]]]]
           (header-tags (assoc cmd :lang "en")))
        "English (not supported)")))
