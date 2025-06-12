(ns lupapalvelu.foreman-api-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal]
            [lupapalvelu.foreman-itest-util :refer [create-and-invite-foreman-command]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(def verdict-date (now))
(def kuulutus-date (-> verdict-date (date/plus :day) date/timestamp))
(def lainvoimainen-date (-> kuulutus-date (date/plus :days 16) date/timestamp))

(use-fixtures :each (local-fixture-factory (-> {}
                                               test-system/with-mock-laundry)))

(defn- emails-have-subjects
  "Check that we have the correct number of outgoing emails with expected subjects. Clears the outbox."
  [& subject-strings]
  (let [emails (sent-emails)]
    (is (= (count emails) (count subject-strings)))
    (is (= []
           (->> (map (fn [email subject-str]
                       (when-not (ss/includes-i? (:subject email) subject-str)
                         [subject-str email]))
                     emails subject-strings)
                (filter identity)
                (into []))))))

(defn- get-docs
  [app-id]
  (let [app (query-application raktark-jarvenpaa app-id)]
    (into [] (domain/get-documents-by-name app :tyonjohtaja-v3))))

(defn- docs->last-address
  "Helper for checking if the latest document is filled with user info"
  [docs]
  (-> docs
      peek
      :data :osoite :katu :value))

(deftest foreman-v3-flow
  (env/with-feature-value :new-foreman-invite true
    (let [{app-id :id}         (create-and-submit-application pena :propertyId jarvenpaa-property-id)
          {:keys [verdict-id]} (command raktark-jarvenpaa :new-legacy-verdict-draft :id app-id)
          _                    (fill-verdict raktark-jarvenpaa app-id verdict-id
                                             :handler "Decider"
                                             :giver "lautakunta"
                                             :giver-name "Board"
                                             :kuntalupatunnus "123-456"
                                             :verdict-code "1"
                                             :verdict-text "Verdict given"
                                             :k-verdict-date verdict-date
                                             :k-lainvoimainen lainvoimainen-date
                                             :k-kuulutus kuulutus-date
                                             :bulletin-op-description "This is pate legacy bulletin description")
          edit                 (partial edit-verdict raktark-jarvenpaa app-id verdict-id)
          foreman-id           (-> (edit :add-foreman true) :changes flatten second)
          _                    (edit [:foremen foreman-id :role] "KVV-työnjohtaja")
          _                    (last-email)]

      (testing "Foreman document added successfully when verdict is published"
        (is (ok? (command raktark-jarvenpaa :publish-legacy-verdict :id app-id :verdict-id verdict-id)))
        (let [docs (get-docs app-id)]
          (is (= 1 (count docs)))
          (is (= "" (docs->last-address docs)))
          (is (emails-have-subjects "hankkeen tila on nyt Päätös annettu"))))

      (testing "Foreman invite works and a new document is created"
        (is (ok? (create-and-invite-foreman-command pena app-id "KVV-työnjohtaja" "mikko@example.com" "")))
        (let [docs (get-docs app-id)]
          (is (= 2 (count docs)))
          (is (= "" (docs->last-address docs)))
          (is (emails-have-subjects "kutsu työnjohtajaksi")))
        (is (ok? (create-and-invite-foreman-command pena app-id "IV-työnjohtaja" "" pena-id)))
        (let [docs (get-docs app-id)]
          (is (= 3 (count docs)))
          (is (= "Paapankuja 12" (docs->last-address docs)))
          (is (emails-have-subjects "kutsu työnjohtajaksi")))
        (testing "email is canonized"
          (is (ok? (create-and-invite-foreman-command pena app-id "erityisalojen työnjohtaja" "PeRttI@Example.COM" "")))
          (let [docs (get-docs app-id)]
            (is (= 4 (count docs)))
            (is (= "" (docs->last-address docs)))
            (is (emails-have-subjects "kutsu työnjohtajaksi"))))
        (testing "sad paths"
          (testing "inviting a non-existing user"
            (is (fail? (create-and-invite-foreman-command pena app-id "KVV-työnjohtaja" "" (str (random-uuid)))))
            (let [docs (get-docs app-id)]
              (is (= 4 (count docs))))
            (is (empty? (sent-emails))))
          (testing "inviting an existing user with no auth to app"
            (is (fail? (create-and-invite-foreman-command pena app-id "KVV-työnjohtaja" "" teppo-id)))
            (let [docs (get-docs app-id)]
              (is (= 4 (count docs))))
            (is (empty? (sent-emails))))
          (testing "inviting a user with a pending invite"
            (is (fail? (create-and-invite-foreman-command pena app-id "IV-työnjohtaja" "" mikko-id)))
            (let [docs (get-docs app-id)]
              (is (= 4 (count docs))))
            (is (empty? (sent-emails)))
            (testing ".. but can invite after accepting invite"
              (is (ok? (command mikko :approve-invite :id app-id)))
              (is (ok? (create-and-invite-foreman-command pena app-id "IV-työnjohtaja" "" mikko-id)))
              (let [docs (get-docs app-id)]
                (is (= 5 (count docs)))
                (is (= "Rambokuja 6" (docs->last-address docs))))
              (is (emails-have-subjects "kutsu työnjohtajaksi"))))))

      (testing "Foreman invite doesn't work without a valid email or user"
        (is (= "error.email" (:text (create-and-invite-foreman-command pena app-id "IV-työnjohtaja" "huonomaili" ""))))
        (is (empty? (sent-emails))))

      (testing "Foreman can not be declared before the consent has been given"
        (let [app  (query-application raktark-jarvenpaa app-id)
              docs (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]
          (is (not (nil? doc)))
          (is (fail? (command pena :declare-foreman :id app-id :foreman-doc-id (:id doc) :with-lang "fi")))))

      (testing "Selected foreman user can give consent"
        (let [app  (query-application raktark-jarvenpaa app-id)
              docs (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]
          (is (fail? (apply command sonja :foreman-consent
                            [:id app-id :foreman-doc-id (:id doc) :consent true])))
          (is (ok? (apply command pena :foreman-consent
                          [:id app-id :foreman-doc-id (:id doc) :consent true])))))

      (testing "Foreman can be declared after the consent has been given"
        (let [app  (query-application raktark-jarvenpaa app-id)
              docs (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]
          (is (not (nil? doc)))
          (is (ok? (command pena :update-doc :id app-id :doc (:id doc) :updates (default-foreman-updates))))
          (is (ok? (command pena :foreman-consent :id app-id :foreman-doc-id (:id doc) :consent true)))
          (is (ok? (command pena :declare-foreman :id app-id :foreman-doc-id (:id doc) :with-lang "fi")))))

      (testing "Foreman can be acknowledged only by authority once it is declared"
        (let [app  (query-application raktark-jarvenpaa app-id)
              docs (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]
          (is (fail? (apply command pena :acknowledge-foreman
                            [:id app-id :foreman-doc-id (:id doc) :with-lang "fi"])))
          (is (ok? (apply command raktark-jarvenpaa :acknowledge-foreman
                          [:id app-id :foreman-doc-id (:id doc) :with-lang "fi"])))))

      (testing "Foreman can be terminated by authority if ended is after start"
        (let [app  (query-application raktark-jarvenpaa app-id)
              docs (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]
          (is (fail? (apply command pena :terminate-foreman-v3
                            [:id app-id :foreman-doc-id (:id doc) :reason "test" :ended (now)])))
          (is (fail? (apply command raktark-jarvenpaa :terminate-foreman-v3
                            [:id app-id :foreman-doc-id (:id doc) :reason "test" :ended 631227839000])))
          (is (ok? (apply command raktark-jarvenpaa :terminate-foreman-v3
                          [:id app-id :foreman-doc-id (:id doc) :reason "test" :ended (now)])))))

      (testing "Foreman verdict can be given"
        (let [app                  (query-application raktark-jarvenpaa app-id)
              docs                 (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc                  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)
              {:keys [verdict-id]} (command raktark-jarvenpaa :new-legacy-foreman-verdict-draft :id app-id :foreman-doc-id (:id doc))
              _                    (fill-verdict raktark-jarvenpaa app-id verdict-id
                                                 :handler "Decider"
                                                 :kuntalupatunnus "123-456"
                                                 :verdict-code "21"
                                                 :verdict-text "Verdict given")]

          (is (= "acknowledged" (:state doc)))
          (is (ok? (command raktark-jarvenpaa :publish-legacy-verdict :id app-id :verdict-id verdict-id)))))

      (testing "Foreman verdict updates the foreman document"
        (let [app  (query-application raktark-jarvenpaa app-id)
              docs (domain/get-documents-by-name app :tyonjohtaja-v3)
              doc  (util/find-first #(-> % :data :kuntaRoolikoodi :value (= "IV-työnjohtaja")) docs)]
          (is (= "rejected" (:state doc))))))))
