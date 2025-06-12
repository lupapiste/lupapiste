(ns lupapalvelu.application-bulletins-itest
  "Bulletin-related tests are sensitive to:
   - bulletin process (kuulutus vs julkipano)
   - organization filtering
   - category filtering (local-application-bulletins excludes ymp)
   - date-driven visibility
   - remote vs local execution shape differences

   Before rewriting assertions, inspect the actual returned values.
   Do not assume the same shape as the old Midje test.

   For :local-application-bulletins specifically:
   - it is anonymous
   - it filters by organization
   - it filters by bulletinProcess
   - it excludes ymp via :category-not [\"ymp\"]
   - it uses bulletin visibility rules based on current timestamps

   Good tests for it usually cover:
   - success response
   - organization filtering
   - bulletin process filtering
   - ymp exclusion"
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.application-bulletin-utils :refer [day-range-ts]]
            [lupapalvelu.application-bulletins :refer [version-elemMatch versions-elemMatch]]
            [lupapalvelu.application-bulletins-test-util :refer [make-version randstamp]]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [midje.sweet :refer :all]
            [monger.operators :refer [$set]]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(deftest change-process-api-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (with-local-actions
      (testing "R app"
        (let [{:keys [id]} (create-local-app mikko :operation "kerrostalo-rivitalo"
                                             :propertyId sipoo-property-id
                                             :x 406898.625 :y 6684125.375
                                             :address "Hitantine 108")]
          (is (ss/not-blank? id))
          (testing "processes listed for R"
            (let [resp (query sonja :application :id id)]
              (is (ok? resp))
              (is (= #{:julkipano :kuulutus} (-> resp :bulletinProcesses)))))
          (testing "before submitting"
            (testing "applicant cannot change process"
              (is (fails-with? :error.command-illegal-state
                               (command mikko :change-bulletin-process :id id :process "julkipano"))))
            (testing "authority cannot change process"
              (is (fails-with? :error.command-illegal-state
                               (command sonja :change-bulletin-process :id id :process "julkipano")))
              (is (fails-with? :error.command-illegal-state
                               (command sonja :change-bulletin-process :id id :process "kuulutus")))))
          (testing "after submitting"
            (is (ok? (command mikko :submit-application :id id)))
            (testing "current process"
              (let [resp (query sonja :application :id id)]
                (is (ok? resp))
                (is (= :kuulutus (-> resp :application :bulletinProcess)))))
            (testing "applicant cannot change process"
              (is (unauthorized? (command mikko :change-bulletin-process :id id :process "julkipano"))))
            (testing "authority can change process"
              (is (ok? (command sonja :change-bulletin-process :id id :process "julkipano")))
              (let [resp (query sonja :application :id id)]
                (is (ok? resp))
                (is (= :julkipano (-> resp :application :bulletinProcess))))
              (is (ok? (command sonja :change-bulletin-process :id id :process "kuulutus")))
              (let [resp (query sonja :application :id id)]
                (is (ok? resp))
                (is (= :kuulutus (-> resp :application :bulletinProcess))))))
          (testing "foreman app"
            ;; Create a verdict so that we can create a TJ app
            (is (ok? (command sonja :check-for-verdict :id id)))
            (let [foreman-app-id (create-foreman-application id mikko pena-id "vastaava työnjohtaja" "A")
                  _              (finalize-foreman-app mikko sonja foreman-app-id true)
                  resp           (query sonja :application :id foreman-app-id)]
              (testing "no choices for non TJ"
                (is (ok? resp))
                (is (= #{} (-> resp :bulletinProcesses))))
              (testing "can't change process"
                (is (fails-with? :error.unsupported-bulletin-process
                                 (command sonja :change-bulletin-process :id foreman-app-id :process "julkipano"))))))))
      (testing "YMP app"
        (let [{:keys [id]} (create-and-submit-local-application olli :operation "lannan-varastointi"
                                                                :propertyId oulu-property-id
                                                                :x 430109.3125 :y 7210461.375
                                                                :address "Oulu 10")
              resp         (query olli :application :id id)]
          (testing "no choices for non R/P"
            (is (ok? resp))
            (is (= #{} (-> resp :bulletinProcesses))))
          (testing "can't change process"
            (is (fails-with? :error.unsupported-bulletin-process
                             (command olli :change-bulletin-process :id id :process "julkipano")))))))))

(deftest bulletin-pseudo-queries-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (let [r-app      (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                    :propertyId sipoo-property-id
                                                    :x 406898.625 :y 6684125.375
                                                    :address "Hitantine 108")
          ymp-app    (create-and-submit-application olli :operation "lannan-varastointi"
                                                    :propertyId oulu-property-id
                                                    :x 430109.3125 :y 7210461.375
                                                    :address "Oulu 10")
          r-app-id   (:id r-app)
          ymp-app-id (:id ymp-app)]
      (testing "bulletin-for-application-verdict-enabled"
        (is (unauthorized? (query sonja :bulletin-for-application-verdict-enabled :id r-app-id)))
        (is (unauthorized? (query olli :bulletin-for-application-verdict-enabled :id ymp-app-id))))
      (testing "bulletin-will-be-published-for-verdict"
        (is (ok? (query sonja :bulletin-will-be-published-for-verdict :id r-app-id)))
        (is (ok? (command sonja :check-for-verdict :id r-app-id)))
        (is (unauthorized? (query sonja :bulletin-will-be-published-for-verdict :id r-app-id))))
      (testing "pseudo-queries fail when bulletins disabled"
        (is (ok? (command admin :update-organization
                          :permitType "R"
                          :municipality "753"
                          :bulletinsEnabled false)))
        (is (expected-failure? "error.bulletins-not-enabled-for-scope"
                               (query sonja :bulletin-for-application-verdict-enabled :id r-app-id)))
        (is (unauthorized? (query sonja :bulletin-will-be-published-for-verdict :id r-app-id)))
        (is (ok? (command admin :update-organization
                          :permitType "R"
                          :municipality "753"
                          :bulletinsEnabled true)))))))

(deftest local-bulletin-page-settings-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (let [organization-id         "753-R"
          old-bulletin-heading1   (-> (query pena :local-bulletins-page-settings :organization organization-id)
                                      :texts :fi :heading1)
          old-kuulutus-heading1   (-> (query pena :local-kuulutus-page-settings :organization organization-id)
                                      :texts :fi :heading1)
          bulletin-heading1-value "Bulletin heading override"
          kuulutus-heading1-value "Kuulutus heading override"]
      (testing "local-bulletins-page-settings"
        (let [resp (query pena :local-bulletins-page-settings :organization organization-id)]
          (is (ok? resp))
          (is (= true (:enabled resp)))
          (is (string? (-> resp :texts :fi :heading1)))
          (is (seq (-> resp :texts :fi :caption)))))
      (testing "local-kuulutus-page-settings"
        (let [resp (query pena :local-kuulutus-page-settings :organization organization-id)]
          (is (ok? resp))
          (is (= true (:enabled resp)))
          (is (string? (-> resp :texts :fi :heading1)))
          (is (seq (-> resp :texts :fi :caption)))))
      (testing "blank organization fails validation"
        (is (fail? (query pena :local-bulletins-page-settings :organization "")))
        (is (fail? (query pena :local-kuulutus-page-settings :organization ""))))
      (testing "configured texts are returned"
        (is (= {:ok true :valid true}
               (command sipoo :upsert-organization-local-bulletins-text
                        :organizationId organization-id
                        :lang "fi"
                        :key "heading1"
                        :value bulletin-heading1-value)))
        (is (= {:ok true :valid true}
               (command sipoo :upsert-organization-local-bulletins-text
                        :organizationId organization-id
                        :lang "fi"
                        :key "heading1"
                        :value kuulutus-heading1-value
                        :isKuulutus true)))
        (is (= bulletin-heading1-value
               (-> (query pena :local-bulletins-page-settings :organization organization-id)
                   :texts :fi :heading1)))
        (is (= kuulutus-heading1-value
               (-> (query pena :local-kuulutus-page-settings :organization organization-id)
                   :texts :fi :heading1)))
        (is (= {:ok true :valid true}
               (command sipoo :upsert-organization-local-bulletins-text
                        :organizationId organization-id
                        :lang "fi"
                        :key "heading1"
                        :value old-bulletin-heading1)))
        (is (= {:ok true :valid true}
               (command sipoo :upsert-organization-local-bulletins-text
                        :organizationId organization-id
                        :lang "fi"
                        :key "heading1"
                        :value old-kuulutus-heading1
                        :isKuulutus true)))))))

(deftest local-application-bulletins-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (let [r-app      (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                     :propertyId sipoo-property-id
                                                     :x 406898.625 :y 6684125.375
                                                     :address "Hitantine 108")
          ymp-app    (create-and-submit-application olli :operation "lannan-varastointi"
                                                     :propertyId oulu-property-id
                                                     :x 430109.3125 :y 7210461.375
                                                     :address "Oulu 10")
          r-app-id   (:id r-app)
          ymp-app-id (:id ymp-app)
          _          (is (ok? (command sonja :check-for-verdict :id r-app-id)))
          _          (is (ok? (command sonja :change-bulletin-process :id r-app-id :process "julkipano")))
          _          (is (ok? (command sonja :check-for-verdict :id r-app-id)))
          julkipano  (query pena :local-application-bulletins
                             :organization "753-R"
                             :bulletinProcess "julkipano")
          kuulutus   (query pena :local-application-bulletins
                             :organization "753-R"
                             :bulletinProcess "kuulutus")
          wrong-org  (query pena :local-application-bulletins
                             :organization "753-YA"
                             :bulletinProcess "julkipano")]
      (testing "query succeeds"
        (is (ok? julkipano))
        (is (ok? kuulutus))
        (is (ok? wrong-org)))
      (testing "page parameter regression check"
        (is (= julkipano
               (query pena :local-application-bulletins
                      :organization "753-R"
                      :page 0
                      :bulletinProcess "julkipano"))))
      (testing "filters by organization"
        (is (= [] (:data wrong-org))))
      (testing "filters by bulletin process"
        (is (match? (m/embeds [{:application-id  r-app-id
                                :bulletinProcess "julkipano"
                                :category        "r"}])
                    (:data julkipano)))
        (is (not-any? #(= r-app-id (:application-id %)) (:data kuulutus))))
      (testing "excludes ymp bulletins from local listing"
        (is (not-any? #(= ymp-app-id (:application-id %)) (:data julkipano)))
        (is (not-any? #(= ymp-app-id (:application-id %)) (:data kuulutus)))))))

(deftest bulletins-julkipano-test
  (with-test-system (-> {:fixture "pate-verdict"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (let [r-app          (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                        :propertyId sipoo-property-id
                                                        :x 406898.625 :y 6684125.375
                                                        :address "Hitantine 108")
          op-description "Kuvausteksti123"
          app-id         (:id r-app)
          template-id    (->> pate-fixture/verdict-templates-r
                              (filter #(= "r" (:category %)))
                              first
                              :id)]
      (testing "Enable pate sftp"
        (is (ok? (command admin :update-organization
                          :permitType "R"
                          :municipality "753"
                          :pateEnabled true
                          :pateSftp true))))
      (testing "can change bulletin process"
        (is (ok? (command sonja :change-bulletin-process :id (:id r-app) :process "julkipano"))))
      (testing "bulletin-op-description"
        (is (ok? (command sonja :update-app-bulletin-op-description :id (:id r-app) :description op-description)))
        (is (= op-description
               (:bulletinOpDescription (query-application mikko app-id)))))
      (testing "Create pate-verdict to ensure bulletin creation for backend verdict works later even with pate-verdicts on application"
        (let [{:keys [verdict-id]} (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)]
          (fill-verdict sonja app-id verdict-id
                        :verdict-code "hyvaksytty"
                        :verdict-text "Verdict given"
                        :verdict-date (date/timestamp "13.3.2024")
                        :giver "päättäjä"
                        :julkipano (date/timestamp "14.3.2024"))))
      (testing "check for backend verdict"
        (is (ok? (command sonja :check-for-verdict :id app-id)))
        (is (pos? (-> (query-application mikko app-id) :verdicts count))))
      (testing "Bulletin is created automatically"
        (let [[bulletin :as bulletins] (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :page 1))]
          (testing "single bulletin created"
            (is (= 1 (count bulletins))))
          (testing "fields are set"
            (is (match? (m/embeds {:address                "Hitantine 108"
                                   :appealPeriodEndsAt     (date/timestamp "6.9.2100")
                                   :appealPeriodStartsAt   (date/timestamp "7.9.2013")
                                   :application-id         app-id
                                   :bulletinOpDescription  op-description
                                   :bulletinProcess        "julkipano"
                                   :bulletinState          "verdictGiven"
                                   :bulletinVersionCreated int?
                                   :category               "r"
                                   :location               [406898.625 6684125.375]
                                   :propertyId             sipoo-property-id
                                   :verdictData            {:contact "johtava viranomainen" :status 1 :text "Päätös 2"}
                                   :verdictDate            (date/timestamp "1.8.2013")
                                   :verdictGivenAt         (date/timestamp "5.9.2013")})
                        bulletin)))
          (testing "No documents, attachments, tasks nor applicant fields"
            (let [b-keys (->> bulletin :id (query-bulletin :public-bulletin mikko) keys set)]
              (is (empty? (set/intersection b-keys #{:documents :attachments :tasks
                                                     :applicant :_applicantIndex})))))
          (testing "cannot export R bulletin as PDF"
            (let [bulletin-id [(-> bulletin :id)]]
              (testing "has id"
                (is bulletin-id))
              (testing "cannot export using bulletin id"
                (is (http404? (raw pena :bulletin-pdf-export :bulletinId bulletin-id))))
              (testing "cannot export using app id"
                (is (http404? (raw pena :bulletin-pdf-export :bulletinId app-id))))))))
      (testing "foreman verdict"
        (let [foreman-app-id (create-foreman-application app-id mikko pena-id "vastaava työnjohtaja" "A")
              _              (finalize-foreman-app mikko sonja foreman-app-id true)
              bulletins      (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1))]
          (testing "bulletin was not generated"
            (is (empty? (util/find-by-key :application-id foreman-app-id bulletins)))
            (is (empty? (:bulletins (query mikko :verdict-bulletins :id foreman-app-id)))))))
      (testing "Bulletin reports for application"
        (let [[{bull-id :id} & _ :as bulls] (:bulletins (query mikko :verdict-bulletins :id app-id))]
          (is (= [{:id   bull-id
                   :text "07.09.2013 - 06.09.2100"}]
                 bulls))
          (testing "Bulletin information pdf"
            (is (pdf-response?
                  (raw mikko :bulletin-report-pdf :id app-id
                       :bulletinId bull-id
                       :lang "fi")
                  {"Content-Disposition" (format "filename=\"Julkipanon tiedot %s %s.pdf\""
                                                 app-id (date/finnish-date (now) :zero-pad))})))
          (testing "Disable bulletins for Sipoo"
            (is (ok? (command admin :update-organization
                              :permitType "R"
                              :municipality "753"
                              :bulletinsEnabled false)))
            (is (expected-failure? "error.bulletins-not-enabled-for-scope"
                                   (query mikko :verdict-bulletins :id app-id)))
            (is (match? (m/embeds {:status 404
                                   :body   "{\"ok\":false,\"text\":\"error.bulletins-not-enabled-for-scope\"}"})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId bull-id
                             :lang "fi"))))
          (testing "Enable bulletins for Sipoo"
            (is (ok? (command admin :update-organization
                              :permitType "R"
                              :municipality "753"
                              :bulletinsEnabled true)))
            (is (ok? (query mikko :verdict-bulletins :id app-id)))
            (is (match? (m/embeds {:status 200})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId bull-id
                             :lang "fi"))))
          (testing "Bad bulletin id"
            (is (match? (m/embeds {:status 404
                                   :body   "{\"ok\":false,\"text\":\"error.bulletin.not-found\"}"})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId "bad-id"
                             :lang "fi"))))
          (testing "Bad language"
            (is (match? (m/embeds {:status 404
                                   :body   "{\"ok\":false,\"text\":\"error.illegal-value:schema-validation\"}"})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId bull-id
                             :lang "bad"))))))
      (testing "Fetch bulletin app-description from backend"
        (is (ok? (command sipoo :update-organization-bulletin-scope :organizationId "753-R"
                          :permitType "R"
                          :municipality "753"
                          :descriptionsFromBackendSystem true)))
        (is (ok? (command sonja :check-for-verdict :id app-id))))
      (let [bulletins (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :page 1))]
        (testing "description is set according to the backend verdict"
          (is (= "Uudisrakennuksen ja talousrakennuksen 15 m2 rakentaminen. Yhden huoneiston erillistalo."
                 (-> bulletins first :bulletinOpDescription))))))))

(deftest bulletins-kuulutus-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (let [r-app          (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                        :propertyId sipoo-property-id
                                                        :x 406898.625 :y 6684125.375
                                                        :address "Hitantine 108")
          op-description "Kuvausteksti123"
          app-id         (:id r-app)]
      (testing "bulletin-op-description"
        (is (ok? (command sonja :update-app-bulletin-op-description :id (:id r-app) :description op-description)))
        (is (= op-description
               (:bulletinOpDescription (query-application mikko app-id)))))
      (testing "check for backend verdict"
        (is (ok? (command sonja :check-for-verdict :id app-id)))
        (is (pos? (-> (query-application mikko app-id) :verdicts count))))
      (testing "Bulletin is created automatically"
        (let [[bulletin :as bulletins] (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1))]
          (testing "single bulletin created"
            (is (= 1 (count bulletins))))
          (testing "fields are set"
            (is (match? (m/embeds {:address                "Hitantine 108"
                                   :appealPeriodEndsAt     (date/timestamp "6.9.2100")
                                   :appealPeriodStartsAt   (date/timestamp "7.9.2013")
                                   :application-id         app-id
                                   :bulletinOpDescription  op-description
                                   :bulletinProcess        "kuulutus"
                                   :bulletinState          "verdictGiven"
                                   :bulletinVersionCreated int?
                                   :category               "r"
                                   :location               [406898.625 6684125.375]
                                   :propertyId             sipoo-property-id
                                   :verdictDate            (date/timestamp "1.8.2013")})
                        bulletin)))
          (testing "No documents, attachments, tasks nor applicant fields"
            (let [b-keys (->> bulletin :id (query-bulletin :public-bulletin mikko) keys set)]
              (is (empty? (set/intersection b-keys #{:documents :attachments :tasks
                                                     :applicant :_applicantIndex})))))
          (testing "cannot export R bulletin as PDF"
            (let [bulletin-id [(-> bulletin :id)]]
              (testing "has id"
                (is bulletin-id))
              (testing "cannot export using bulletin id"
                (is (http404? (raw pena :bulletin-pdf-export :bulletinId bulletin-id))))
              (testing "cannot export using app id"
                (is (http404? (raw pena :bulletin-pdf-export :bulletinId app-id))))))))
      (testing "foreman verdict"
        (let [foreman-app-id (create-foreman-application app-id mikko pena-id "vastaava työnjohtaja" "A")
              _              (finalize-foreman-app mikko sonja foreman-app-id true)
              bulletins      (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1))]
          (testing "bulletin was not generated"
            (is (empty? (util/find-by-key :application-id foreman-app-id bulletins)))
            (is (empty? (:bulletins (query mikko :verdict-bulletins :id foreman-app-id)))))))
      (testing "Bulletin reports for application"
        (let [[{bull-id :id} & _ :as bulls] (:bulletins (query mikko :verdict-bulletins :id app-id))]
          (is (= [{:id   bull-id
                   :text "07.09.2013 - 06.09.2100"}]
                 bulls))
          (testing "Bulletin information pdf"
            (is (pdf-response?
                  (raw mikko :bulletin-report-pdf :id app-id
                       :bulletinId bull-id
                       :lang "fi")
                  {"Content-Disposition" (format "filename=\"Kuulutuksen tiedot %s %s.pdf\""
                                                 app-id (date/finnish-date (now) :zero-pad))})))
          (testing "Disable bulletins for Sipoo"
            (is (ok? (command admin :update-organization
                              :permitType "R"
                              :municipality "753"
                              :bulletinsEnabled false)))
            (is (expected-failure? "error.bulletins-not-enabled-for-scope"
                                   (query mikko :verdict-bulletins :id app-id)))
            (is (match? (m/embeds {:status 404
                                   :body   "{\"ok\":false,\"text\":\"error.bulletins-not-enabled-for-scope\"}"})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId bull-id
                             :lang "fi"))))
          (testing "Enable bulletins for Sipoo"
            (is (ok? (command admin :update-organization
                              :permitType "R"
                              :municipality "753"
                              :bulletinsEnabled true)))
            (is (ok? (query mikko :verdict-bulletins :id app-id)))
            (is (match? (m/embeds {:status 200})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId bull-id
                             :lang "fi"))))
          (testing "Bad bulletin id"
            (is (match? (m/embeds {:status 404
                                   :body   "{\"ok\":false,\"text\":\"error.bulletin.not-found\"}"})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId "bad-id"
                             :lang "fi"))))
          (testing "Bad language"
            (is (match? (m/embeds {:status 404
                                   :body   "{\"ok\":false,\"text\":\"error.illegal-value:schema-validation\"}"})
                        (raw mikko :bulletin-report-pdf :id app-id
                             :bulletinId bull-id
                             :lang "bad"))))))
      (testing "Fetch bulletin app-description from backend"
        (is (ok? (command sipoo :update-organization-bulletin-scope :organizationId "753-R"
                          :permitType "R"
                          :municipality "753"
                          :descriptionsFromBackendSystem true)))
        (is (ok? (command sonja :check-for-verdict :id app-id))))
      (let [bulletins (:data (datatables mikko :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1))]
        (testing "description is set according to the backend verdict"
          (is (= "Uudisrakennuksen ja talousrakennuksen 15 m2 rakentaminen. Yhden huoneiston erillistalo."
                 (-> bulletins first :bulletinOpDescription))))))))

(deftest bulletins-ends-at-correct-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (let [app          (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                      :propertyId sipoo-property-id
                                                      :x 406898.625 :y 6684125.375
                                                      :address "Hitantine 109")
          app-id       (:id app)
          other-app-id (:id (create-and-submit-application mikko :operation "pientalo"
                                                           :propertyId sipoo-property-id
                                                           :address "Elsewhere"))
          check-resp   (command sonja :check-for-verdict :id app-id)
          verdict-app  (query-application sonja app-id)]
      (is other-app-id)
      (testing "verdict ok"
        (is (ok? check-resp))
        (is (= "verdictGiven" (:state verdict-app))))
      (testing "Bulletin is shown on its final day"
        (let [bulletins         (datatables pena :application-bulletins
                                            :municipality sonja-muni :searchText ""
                                            :state "" :page 1)
              {bulletin-id :id} (first (:data bulletins))
              versions          (query sonja :bulletin-versions :id app-id
                                       :bulletinId bulletin-id)
              first-version-id  (-> versions :bulletin :versions first :id)]
          (is (= 1 (count (:data bulletins))))
          (testing "Bulletin versions contain :bulletinVersionCreated field"
            (is (= true
                   (->> versions
                        :bulletin
                        :versions
                        (map :bulletinVersionCreated)
                        (every? some?)))))
          (testing "edit ends at"
            (testing "Application does not match"
              (is (fails-with? :error.not-found
                               (command sonja :save-verdict-given-bulletin
                                        :id other-app-id
                                        :bulletinId bulletin-id
                                        :bulletinVersionId first-version-id
                                        :verdictGivenAt (date/timestamp (date/minus (date/now) :day))
                                        :appealPeriodStartsAt (date/timestamp (date/now))
                                        :appealPeriodEndsAt (date/timestamp (date/plus (date/now) :day))
                                        :verdictGivenText "test time stamps logic"))))
            (testing "Invalid bulletin id"
              (is (fails-with? :error.not-found
                               (command sonja :save-verdict-given-bulletin
                                        :id app-id
                                        :bulletinId "not-found"
                                        :bulletinVersionId first-version-id
                                        :verdictGivenAt (date/timestamp (date/minus (date/now) :day))
                                        :appealPeriodStartsAt (date/timestamp (date/now))
                                        :appealPeriodEndsAt (date/timestamp (date/plus (date/now) :day))
                                        :verdictGivenText "test timestamps logic"))))
            (testing "Appeal period starts after ends"
              (is (expected-failure? :error.startdate-before-enddate
                                     (command sonja :save-verdict-given-bulletin
                                              :id app-id
                                              :bulletinId bulletin-id
                                              :bulletinVersionId first-version-id
                                              :verdictGivenAt (date/timestamp (date/minus (date/now) :day))
                                              :appealPeriodStartsAt (date/timestamp (date/plus (date/now) :day))
                                              :appealPeriodEndsAt (date/timestamp (date/now))
                                              :verdictGivenText "test timestamps logic"))))
            (testing "Verdict given date after appeal period starts"
              (is (expected-failure? :error.startdate-before-enddate
                                     (command sonja :save-verdict-given-bulletin
                                              :id app-id
                                              :bulletinId bulletin-id
                                              :bulletinVersionId first-version-id
                                              :verdictGivenAt (date/timestamp (date/plus (date/now) :day))
                                              :appealPeriodStartsAt (date/timestamp (date/now))
                                              :appealPeriodEndsAt (date/timestamp (date/plus (date/now) :days 2))
                                              :verdictGivenText "test timestamps logic"))))
            (is (ok? (command sonja :save-verdict-given-bulletin
                              :id app-id
                              :bulletinId bulletin-id
                              :bulletinVersionId first-version-id
                              :verdictGivenAt (date/timestamp (date/minus (date/now) :day))
                              :appealPeriodStartsAt (date/timestamp (date/now))
                              :appealPeriodEndsAt (date/timestamp (date/plus (date/now) :day))
                              :verdictGivenText "test timestamps logic"))))
          (testing "Mikka can access bulletin versions"
            (is (ok? (query mikko :bulletin-versions :id app-id
                            :bulletinId bulletin-id))))
          (testing "Veikko cannot since not authed to the application"
            (is (fails-with? :error.application-not-accessible
                             (query veikko :bulletin-versions :id app-id
                                    :bulletinId bulletin-id))))
          (testing "Bulletin must match application"
            (is (fails-with? :error.not-found
                             (query mikko :bulletin-versions :id other-app-id
                                    :bulletinId bulletin-id))))
          (let [bulletins (:data (datatables pena :application-bulletins :municipality sonja-muni :searchText "" :state "" :page 1))]
            (testing "data is still visible"
              (is (pos? (count bulletins)))))
          (testing "authority can still manually add comment to bulletin after end date"
            (is (ok? (command sonja :save-verdict-given-bulletin
                              :id app-id
                              :bulletinId bulletin-id
                              :bulletinVersionId first-version-id
                              :verdictGivenAt (date/timestamp (date/minus (date/now) :days 2))
                              :appealPeriodStartsAt (date/timestamp (date/minus (date/now) :days 2))
                              :appealPeriodEndsAt (date/timestamp (date/minus (date/now) :day))
                              :verdictGivenText "test timestamps logic")))
            (is (ok? (command sonja :add-manual-bulletin-comment
                              :id app-id
                              :bulletinId bulletin-id
                              :comment "test comment"
                              :bulletinVersionId first-version-id
                              :contact {:firstName "Teemu"
                                        :lastName  "Testaaja"
                                        :street    "Striit"
                                        :zip       "12345"
                                        :city      "Siti"
                                        :email     "maili@foo.com"})))
            (testing "Pena can not manually add bulletin comment"
              (is (fails-with? :error.application-not-accessible
                               (command pena :add-manual-bulletin-comment
                                        :id app-id
                                        :bulletinId bulletin-id
                                        :comment "test comment"
                                        :bulletinVersionId first-version-id
                                        :contact {:firstName "Teemu"
                                                  :lastName  "Testaaja"
                                                  :street    "Striit"
                                                  :zip       "12345"
                                                  :city      "Siti"
                                                  :email     "maili@foo.com"})))))
          (testing "Mikko can read comments"
            (is (ok? (query mikko :bulletin-comments :id app-id :bulletinId bulletin-id
                            :versionId first-version-id))))
          (testing "Veikko cannot read comments"
            (is (fails-with? :error.application-not-accessible
                             (query veikko :bulletin-comments :id app-id :bulletinId bulletin-id
                                    :versionId first-version-id)))))))))

(defn make-bulletin [state start-ts & [end-ts]]
  {:bulletinState state
   :versions      [(make-version state start-ts end-ts)]})

(defn query-check [ts limit-ts & states]
  (let [check (m/in-any-order (map name states))]
    (testing (str (date/finnish-datetime ts) ": " states)
      (testing "version-elemMatch"
        (is (match? check
                    (map :bulletinState (mongo/select :application-bulletins (version-elemMatch ts limit-ts))))))
      (testing "versions-elemMatch"
        (is (match? check
                    (map :bulletinState (mongo/select :application-bulletins (versions-elemMatch ts limit-ts)))))))))

(def hour 3600000)

(deftest mongo-queries-and-dates-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (mongo/insert :application-bulletins (make-bulletin :proclaimed
                                                        (randstamp "1.2.2019")
                                                        (randstamp "1.3.2019")))
    (mongo/insert :application-bulletins (make-bulletin :verdictGiven
                                                        (randstamp "20.2.2019")
                                                        (randstamp "20.3.2019")))
    (mongo/insert :application-bulletins (make-bulletin :final
                                                        (randstamp "10.3.2019")))
    (testing "Three bulletins"
      (is (= 3 (mongo/count :application-bulletins))))
    (query-check (randstamp "10.2.2019") 12345 :proclaimed)
    (query-check (first (day-range-ts (randstamp "1.2.2019"))) 12345 :proclaimed)
    (query-check (last (day-range-ts (randstamp "1.3.2019"))) 12345 :proclaimed :verdictGiven)
    (query-check (randstamp "10.3.2019") (randstamp "20.3.2019") :verdictGiven)
    (query-check (first (day-range-ts (randstamp "20.2.2019")))
                 (randstamp "25.2.2019") :proclaimed :verdictGiven)
    (query-check (last (day-range-ts (randstamp "20.3.2019")))
                 (randstamp "30.3.2019") ::verdictGiven)
    (query-check (randstamp "20.3.2019")
                 (randstamp "5.3.2019") :verdictGiven :final)
    (query-check (randstamp "10.3.2019")
                 (randstamp "9.3.2019") :verdictGiven :final)
    (testing "Sorting (id as a tie-breaker)"
      (letfn [(insert [id modified address]
                (mongo/insert :application-bulletins
                              {:id       id
                               :versions [{:modified             modified
                                           :address              address
                                           :municipality         "123"
                                           :bulletinState        :verdictGiven
                                           :verdictGivenAt       (- (now) (* 2 hour))
                                           :appealPeriodStartsAt (- (now) hour)
                                           :appealPeriodEndsAt   (+ (now) hour)}]}))]
        (insert "b" 100 "Road")
        (insert "a" 100 "Avenue")
        (insert "c" 100 "Road")
        (insert "d" 90 "Street")
        (insert "e" 80 "Way")
        (insert "f" 110 "Road")
        (testing "Default is modified descending"
          (is (= [[110 "f"] [100 "c"] [100 "b"] [100 "a"] [90 "d"] [80 "e"]]
                 (->> (local-query pena :application-bulletins :page 0
                                   :municipality "" :state "" :searchText "")
                      :data
                      (map (juxt :modified :id))))))
        (testing "Address ascending"
          (is (= [["Avenue" "a"]
                  ["Road" "b"]
                  ["Road" "c"]
                  ["Road" "f"]
                  ["Street" "d"]
                  ["Way" "e"]]
                 (->> (local-query pena :application-bulletins :page 0
                                   :municipality "" :state "" :searchText ""
                                   :sort {:field "address" :asc true})
                      :data
                      (map (juxt :address :id))))))
        (testing "Municipality (tie-breakers only)"
          (is (= "fedcba"
                 (->> (local-query pena :application-bulletins :page 0
                                   :municipality "" :state "" :searchText ""
                                   :sort {:field "municipality"})
                      :data
                      (map :id)
                      (apply str))))
          (is (= "abcdef"
                 (->> (local-query pena :application-bulletins :page 0
                                   :municipality "" :state "" :searchText ""
                                   :sort {:field "municipality" :asc true})
                      :data
                      (map :id)
                      (apply str)))))))))

(deftest kuulutus-from-backend-verdict-test
  (with-test-system (-> {:fixture "minimal"}
                        test-system/with-remote-components
                        test-system/with-mock-laundry)
    (with-local-actions
      (let [r-app          (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                          :propertyId sipoo-property-id
                                                          :x 406898.625 :y 6684125.375
                                                          :address "Hitantine 108")
            op-description "Kuvausteksti123"
            app-id         (:id r-app)
            submit-date    (inc (date/timestamp "1.1.2025"))]
        ;; Fake submission in 2025
        (mongo/update-by-id :applications app-id {$set {:submitted submit-date}})
        (testing "bulletin-op-description"
          (is (ok? (command sonja :update-app-bulletin-op-description :id (:id r-app) :description op-description)))
          (is (match? (m/embeds {:bulletinOpDescription op-description})
                      (mongo/select-one :applications {:_id app-id}))))
        (testing "check for backend verdict"
          (is (ok? (command sonja :check-for-verdict :id app-id)))
          (is (pos? (-> (query-application mikko app-id) :verdicts count))))
        (let [[bulletin :as bulletins] (:data (query mikko :application-bulletins
                                                     :municipality sonja-muni
                                                     :searchText ""
                                                     :page 1))]
          (testing "single bulletin created"
            (is (= 1 (count bulletins))))
          (testing "fields are set"
            (is (match? (m/embeds {:address                "Hitantine 108"
                                   :appealPeriodEndsAt     (date/timestamp "6.9.2100")
                                   :appealPeriodStartsAt   (date/timestamp "7.9.2013")
                                   :application-id         app-id
                                   :bulletinOpDescription  op-description
                                   :bulletinProcess        "kuulutus"
                                   :bulletinState          "verdictGiven"
                                   :bulletinVersionCreated int?
                                   :category               "r"
                                   :location               [406898.625 6684125.375]
                                   :propertyId             sipoo-property-id
                                   :verdictDate            (date/timestamp "1.8.2013")})
                        bulletin))))))))
