(ns lupapalvelu.archive.foreman-list-archiving-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.archive.archiving :as archiving]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer [no-errors?]]
            [lupapalvelu.pdf.html-templates.foreman-info-template :as foreman-info-template]
            [lupapiste-commons.archive-metadata-schema :as ams]
            [lupapiste-commons.schema-utils :as schema-utils]
            [sade.core :as core]
            [sade.env :as env]
            [schema.core :as schema])
  (:import [java.util Date]))

(defn- add-missing-coordinates [{:keys [location] :as metadata}]
  (assoc metadata :location-etrs-tm35fin location))

(use-fixtures :once (local-fixture-factory (-> {:fixture "pate-verdict"}
                                               test-system/with-mock-laundry)))

(deftest foreman-list-archiving-test
  (env/with-feature-value :new-foreman-invite true

    (testing "Enable permanent archive"
      (is (ok? (command admin :set-organization-boolean-attribute
                        :attribute "permanent-archive-enabled"
                        :enabled true :organizationId "753-R"))))

    (let [app-id (create-app-id pena
                                :propertyId sipoo-property-id
                                :operation  "kerrostalo-rivitalo")]

      (testing "Submit, approve and publish verdict"
        (is (ok? (command pena :submit-application :id app-id)))
        ;; NOTE: This used to be "10 03 00 01", but QA TOS has no such TOS function..
        (is (ok? (command sonja :set-tos-function-for-application :id app-id :functionCode "10 03 00 02")))
        (is (ok? (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description")))
        (is (ok? (command sonja :approve-application :id app-id :lang "fi")))

        (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                :id app-id
                                                :template-id (-> pate-fixture/verdict-templates-r
                                                                 first
                                                                 :id))]
          (is (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:automatic-verdict-dates] :value true)))
          (is (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:verdict-date] :value (core/now))))
          (is (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:verdict-code] :value "hyvaksytty")))
          (is (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:giver-first-name] :value "Gi")))
          (is (no-errors? (command sonja :edit-pate-verdict :id app-id :verdict-id verdict-id :path [:giver-last-name] :value "Ver")))
          (is (no-errors? (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id)))))

      (testing "Application has foreman query should not pass when application has no 'initiated' (submitted/declared/etc.) foremen"
        (is (fail? (query sonja :application-has-foreman :id app-id))))

      (testing "Create and set up v3 foreman (on main application)"
        ;; publish-pate-verdict already creates a blank "vastaava työjohtaja" so we don't have to create a new one
        (let [app         (query-application sonja app-id)
              foreman-doc (domain/get-document-by-name app "tyonjohtaja-v3")]
          (is (some? foreman-doc))
          (is (ok? (command sonja :set-user-to-document :id app-id :documentId (:id foreman-doc) :userId pena-id :path "")))
          (is (ok? (command sonja :update-doc :id app-id :doc (:id foreman-doc) :updates (default-foreman-updates))))
          (is (ok? (command pena :foreman-consent :id app-id :foreman-doc-id (:id foreman-doc) :consent true)))
          (is (ok? (command sonja :declare-foreman :id app-id :foreman-doc-id (:id foreman-doc))))))

      (testing "Application has foreman query passes"
        (is (ok? (query sonja :application-has-foreman :id app-id))))

      (testing "Create and set up v2 foreman (linked foreman application)"
        (let [{foreman-app-id :id} (command sonja :create-foreman-application
                                            :id app-id
                                            :taskId ""
                                            :foremanRole "KVV-työnjohtaja"
                                            :foremanEmail "")]
          (is (string? foreman-app-id))
          (let [foreman-app (query-application sonja foreman-app-id)
                foreman-doc (domain/get-document-by-name foreman-app "tyonjohtaja-v2")]
            (is (some? foreman-doc))
            (is (ok? (command sonja :update-doc :id foreman-app-id :doc (:id foreman-doc)
                              :updates [["henkilotiedot.etunimi" "Kaisa"]
                                        ["henkilotiedot.sukunimi" "Putkinen"]
                                        ["kuntaRoolikoodi" "KVV-työnjohtaja"]
                                        ["patevyysvaatimusluokka" "B"]
                                        ["patevyys-tyonjohtaja.koulutusvalinta" "insinööri"]
                                        ["patevyys-tyonjohtaja.valmistumisvuosi" "2005"]
                                        ["patevyys-tyonjohtaja.kokemusvuodet" "10"]
                                        ["patevyys-tyonjohtaja.valvottavienKohteidenMaara" "5"]])))
            (is (ok? (command sonja :change-permit-sub-type :id foreman-app-id :permitSubtype "tyonjohtaja-hakemus")))
            (is (ok? (command sonja :submit-application :id foreman-app-id)))
            (is (ok? (command sonja :approve-application :id foreman-app-id :lang "fi")))
            (let [tj-template-id                    (->> pate-fixture/verdict-templates-r
                                                         (filter #(= (:category %) "tj"))
                                                         first
                                                         :id)
                  {verdict-id :verdict-id :as resp} (command sonja :new-pate-verdict-draft
                                                             :id foreman-app-id
                                                             :template-id tj-template-id)]
              (is (ok? resp))
              (is (some? verdict-id))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:verdict-date] :value (core/now))))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:verdict-code] :value "hyvaksytty")))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:giver] :value "päättäjä")))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:muutoksenhaku] :value (core/now))))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:anto] :value (core/now))))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:lainvoimainen] :value (core/now))))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:automatic-verdict-dates] :value true)))
              (is (no-errors? (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id :path [:responsibilities-start-date] :value (core/now))))
              (is (no-errors? (command sonja :publish-pate-verdict :id foreman-app-id :verdict-id verdict-id)))))))

      (testing "Foreman list HTML includes both v3 and v2 foremen"
        (let [application    (query-application sonja app-id)
              foremen        (foreman/get-all-initiated-foreman-documents application)
              {:keys [body header]} (foreman-info-template/foreman-list-html foremen application :fi)]

          (testing "HTML structure"
            (is (string? body))
            (is (seq body))
            (is (string? header))
            (is (seq header))

            (is (re-find #"Työnjohtajaluettelo" header))
            (is (re-find #"LP-" header)))

          (testing "Contains both v3 and v2 foremen"
            (is (= 2 (count foremen)))
            (is (re-find #"Henkilötiedot" body)))

          (testing "Contains the v3 foreman (from main application)"
            (is (re-find #"Pena" body)))

          (testing "Contains the v2 foreman (from linked foreman application)"
            (is (re-find #"Kaisa Putkinen" body))
            (is (re-find #"KVV-työnjohtaja" body)))))

      (testing "foreman-list-tos-metadata"
        (testing "Foreman-list-tos-metadata can be updated"
          (let [foreman-list-metadata {:henkilotiedot   "sisaltaa"
                                       :julkisuusluokka "julkinen"
                                       :kieli           "fi"
                                       :myyntipalvelu   false
                                       :nakyvyys        "julkinen"
                                       :sailytysaika    {:arkistointi "ikuisesti"
                                                         :perustelu   "AL/11665/07.01.01.03.01/2008"}}]
            (is (ok? (command sonja :store-tos-metadata-for-foreman-list :id app-id :metadata foreman-list-metadata)))))

        (let [application (query-application sonja app-id)]
          (testing "foreman-list-metadata should have been updated"
            (is (= (:foreman-list-metadata application) {:tila            "luonnos"
                                                         :nakyvyys        "julkinen"
                                                         :sailytysaika    {:arkistointi "ikuisesti"
                                                                           :perustelu   "AL/11665/07.01.01.03.01/2008"}
                                                         :myyntipalvelu   false
                                                         :kieli           "fi"
                                                         :henkilotiedot   "sisaltaa"
                                                         :julkisuusluokka "julkinen"})))

          (testing "Foreman list archive metadata validates against schema"
            (let [user             {:username "sonja" :firstName "Sonja" :lastName "Sipoo"}
                  archive-metadata (archiving/generate-archive-metadata application user :foreman-list-metadata {})
                  metadata         (->> (assoc archive-metadata
                                               :arkistointipvm (Date.)
                                               :tila           :arkistoitu
                                               :type           :osapuolet.tyonjohtajaluettelo
                                               :tiedostonimi   (str (:id application) "-foreman-list.pdf"))
                                        (add-missing-coordinates)
                                        schema-utils/remove-blank-keys
                                        (schema-utils/coerce-metadata-to-schema ams/full-document-metadata))]
              (is (nil? (schema/check ams/full-document-metadata metadata)))))

          (testing "Document states include foreman list"
            (let [{:keys [state]} (query sonja :document-states :id app-id)
                  foreman-list-id (str app-id "-foreman-list")]
              (is (contains? state foreman-list-id)))))))))
