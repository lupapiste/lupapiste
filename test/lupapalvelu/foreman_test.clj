(ns lupapalvelu.foreman-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.company :as company]
            [lupapalvelu.document.data-schema :as dds]
            [lupapalvelu.foreman :as f]
            [lupapalvelu.foreman-application-util :refer [select-latest-verdict-status] :as foreman-util]
            [lupapalvelu.test-util :refer [expected-failure? unauthorized?]]
            [lupapalvelu.user :as usr]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.schema-generators :as ssg]))

;; Using #' to access private functions
(def validate-notice-or-application #'lupapalvelu.foreman/validate-notice-or-application)
(def validate-notice-submittable #'lupapalvelu.foreman/validate-notice-submittable)
(def henkilo-invite #'lupapalvelu.foreman/henkilo-invite)
(def yritys-invite #'lupapalvelu.foreman/yritys-invite)
(def filter-foreman-doc-responsibilities #'lupapalvelu.foreman-application-util/filter-foreman-doc-responsibilities)

(def foreman-app {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                  :permitSubtype    "tyonjohtaja-ilmoitus"
                  :created          1444444444444
                  :documents        [{:schema-info {:name "tyonjohtaja-v2"}
                                      :data        {}}]
                  :linkPermitData   [{:type "lupapistetunnus"
                                      :id   "LP-123"}]})
(def project-app {:tasks [{:data  {:katselmuksenLaji {:value "loppukatselmus"}
                                   :katselmus        {:tila    {:value "lopullinen"}
                                                      :pitoPvm {:value "12.10.2019"}}}
                           :state "sent"}]})

(deftest foreman-application-validation-test
  (testing "Foreman application validation"
    (is (nil? (validate-notice-or-application foreman-app)))
    (is (expected-failure? :error.foreman.type-not-selected
                           (validate-notice-or-application
                             (assoc foreman-app :permitSubtype ""))))
    (is (expected-failure? :error.foreman.type-not-selected
                           (validate-notice-or-application
                             (assoc foreman-app :permitSubtype nil))))
    (is (nil? (validate-notice-or-application
                (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus"))))))

(deftest notice-tests
  (testing "Notice? tests"
    (is (false? (f/notice? nil)))
    (is (false? (f/notice? {})))
    (is (true? (f/notice? foreman-app)))
    (is (false? (f/notice? (assoc-in foreman-app [:primaryOperation :name] "other-op"))))
    (is (false? (f/notice? (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus"))))))

(deftest validate-notice-submittable-test
  (testing "validate if notice is submittable"
    (is (nil? (validate-notice-submittable nil :irrelevant)))
    (is (nil? (validate-notice-submittable {} :irrelevant)))
    (testing "Only foreman notice apps are validated"
      (is (nil? (validate-notice-submittable (assoc foreman-app :permitSubtype "hakemus") :irrelevant))))
    (testing "Link permit must be provided for validate to run"
      (is (nil? (validate-notice-submittable foreman-app nil))))
    (testing "Validator returns nil if state is post-verdict state"
      (is (nil? (validate-notice-submittable foreman-app {:state "verdictGiven"}))))
    (testing "Validator returns fail! when state is post-verdict state"
      (is (expected-failure? :error.foreman.notice-not-submittable
                             (validate-notice-submittable foreman-app {:state "sent"}))))))

(deftest foreman-submittable-test
  (testing "Foreman submittable"
    (is (nil? (f/validate-foreman-submittable {:state :draft} nil)))
    (is (expected-failure? :error.not-submittable.foreman-link
                           (f/validate-foreman-submittable {:state :draft} {:state :open})))
    (is (nil? (f/validate-foreman-submittable {:state :draft} {:state :submitted})))))

(def tj-data-schema (dds/doc-data-schema "tyonjohtaja-v2"))
(def hankkeen-kuv-min-data-schema (dds/doc-data-schema "hankkeen-kuvaus-minimum"))
(def hankkeen-kuvaus-data-schema (dds/doc-data-schema "hankkeen-kuvaus"))
(def hakija-tj-data-schema (dds/doc-data-schema "hakija-tj"))
(def hakija-r-data-schema (dds/doc-data-schema "hakija-r"))
(def rj-data-schema (dds/doc-data-schema "rakennusjatesuunnitelma"))

(deftest update-foreman-docs-test
  (testing "f/update-foreman-docs"
    (let [tj-doc               (ssg/generate tj-data-schema)
          hankkeen-kuvaus      (ssg/generate hankkeen-kuv-min-data-schema)
          hankkeen-kuvaus-orig (assoc-in (ssg/generate hankkeen-kuvaus-data-schema) [:data :kuvaus :value] "Test description")
          hakija-tj            (ssg/generate hakija-tj-data-schema)
          hakija-r1            (ssg/generate hakija-r-data-schema)
          hakija-r2            (ssg/generate hakija-r-data-schema)
          rj-doc               (ssg/generate rj-data-schema)
          foreman-app          (assoc foreman-app :documents [hankkeen-kuvaus rj-doc tj-doc hakija-tj])
          orig-app             {:documents [hankkeen-kuvaus-orig hakija-r1 hakija-r2]}
          result-doc           (f/update-foreman-docs foreman-app orig-app "KVV-tyonjohtaja")]

      (testing "hakija docs are copied from orig application"
        (let [hakija-docs (filter (comp #{:hakija} keyword :subtype :schema-info) (:documents result-doc))]
          (is (= 2 (count hakija-docs)))
          (is (= #{"hakija-tj"} (set (map #(get-in % [:schema-info :name]) hakija-docs))))
          (is (= (-> hakija-r1 :data :henkilo (dissoc :userId))
                 (-> hakija-docs first :data :henkilo (dissoc :userId))))
          (is (= (-> hakija-r2 :data :henkilo (dissoc :userId))
                 (-> hakija-docs second :data :henkilo (dissoc :userId))))))

      (testing "hankkeen kuvaus value is copied from orig app"
        (let [hankkeen-kuvaus-docs (filter (comp #{:hankkeen-kuvaus} keyword :subtype :schema-info) (:documents result-doc))]
          (is (= 1 (count hankkeen-kuvaus-docs)))
          (is (= "hankkeen-kuvaus-minimum" (-> hankkeen-kuvaus-docs first :schema-info :name)))
          (is (= "Test description" (-> hankkeen-kuvaus-docs first :data :kuvaus :value)))))

      (testing "foreman role is set"
        (let [foreman-docs (filter (comp #{"tyonjohtaja-v2"} :name :schema-info) (:documents result-doc))]
          (is (= 1 (count foreman-docs)))
          (is (= "KVV-tyonjohtaja" (-> foreman-docs first :data :kuntaRoolikoodi :value)))))

      (testing "other docs are not changed"
        (let [other-docs (remove (comp #{"tyonjohtaja-v2" "hakija-tj" "hankkeen-kuvaus-minimum"} :name :schema-info) (:documents result-doc))]
          (is (= 1 (count other-docs)))
          (is (= rj-doc (-> other-docs first))))))))

(deftest allow-foreman-only-in-foreman-app-test
  (testing "allow-foreman-only-in-foreman-app"
    (let [foreman-app-with-auth (assoc foreman-app :auth [{:id "1" :role "foreman"}])
          non-foreman-app       (assoc-in foreman-app-with-auth [:primaryOperation :name] "kerrostalo-rivitalo")
          fm-command            {:application foreman-app-with-auth, :user {:id "1"}}
          non-fm-command        {:application non-foreman-app, :user {:id "1"}}]

      (testing "meta"
        (is (true? (foreman-util/foreman-app? foreman-app-with-auth)))
        (is (false? (foreman-util/foreman-app? non-foreman-app))))

      (testing "has auth"
        (is (nil? (f/allow-foreman-only-in-foreman-app fm-command))))
      (testing "other user ok"
        (is (nil? (f/allow-foreman-only-in-foreman-app (assoc fm-command :user {:id "2"})))))
      (testing "no access to other kind of application"
        (is (unauthorized? (f/allow-foreman-only-in-foreman-app non-fm-command)))))))

(deftest select-latest-verdict-status-test
  (testing "select-latest-verdict-status"
    (testing "backing system verdict with status"
      (is (= {:status    "ok"
              :statusLoc :verdict.status.10}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :status "10"}]}]}]}))))
    (testing "one verdict with paatoskoodi"
      (is (= {:status    "rejected"
              :statusLoc :verdict.status.29}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "29"}]}]}]}))))

    (testing "one verdict with status and paatoskoodi - prefer status"
      (is (= {:status    "ok"
              :statusLoc :verdict.status.1}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :status "1" :paatoskoodi "29"}]}]}]}))))

    (testing "one verdict without poytakirja"
      (is (= {:status "new"}
             (select-latest-verdict-status {:verdicts [{:paatokset []}]}))))

    (testing "one verdict with empty poytakirja"
      (is (= {:status "new"}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{}]}]}]}))))

    (testing "one verdict with multiple paatoskoodi"
      (is (= {:status    "ok"
              :statusLoc :verdict.status.4}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}
                                                                                   {:paatospvm 3 :paatoskoodi "3"}
                                                                                   {:paatospvm 4 :paatoskoodi "4"}
                                                                                   {:paatospvm 2 :paatoskoodi "2"}]}]}]}))))

    (testing "one verdict with multiple paatos"
      (is (= {:status    "ok"
              :statusLoc :verdict.status.4}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}
                                                                    {:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                   {:paatospvm 4 :paatoskoodi "4"}]}
                                                                    {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}]}))))

    (testing "application with multiple verdicts"
      (is (= {:status    "ok"
              :statusLoc :verdict.status.4}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}
                                                       {:paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                   {:paatospvm 4 :paatoskoodi "4"}]}
                                                                    {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}]}))))

    (testing "one verdict with future paatoskoodi"
      (is (= {:status    "ok"
              :statusLoc :verdict.status.3}
             (select-latest-verdict-status {:verdicts [{:paatokset [{:poytakirjat [{:paatospvm (- (now) 9999) :paatoskoodi "1"}
                                                                                   {:paatospvm (- (now) 999) :paatoskoodi "2"}
                                                                                   {:paatospvm (+ (now) 9999) :paatoskoodi "3"}]}]}]}))))

    (testing "Pate verdict with verdict-code"
      (is (= {:status    "ok"
              :statusLoc :pate.verdict-code.ehdollinen}
             (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                             :published {:published 123}
                                                             :data      {:verdict-code "ehdollinen"}}]}))))

    (testing "Pate verdict with numeric, rejected verdict-code"
      (is (= {:status    "rejected"
              :statusLoc :verdict.status.21}
             (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                             :published {:published 123}
                                                             :data      {:verdict-code "21"}}]}))))

    (testing "Pate verdict with verdict-code: wrapped"
      (is (= {:status    "ok"
              :statusLoc :pate.verdict-code.ehdollinen}
             (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                             :published {:published 123}
                                                             :data      {:verdict-code {:_value    "ehdollinen"
                                                                                        :_modified 123
                                                                                        :_user     "hello"}}}]}))))

    (testing "Draft Pate verdict"
      (is (= {:status "new"}
             (select-latest-verdict-status {:pate-verdicts [{:category "tj"
                                                             :data     {:verdict-code "ehdollinen"}}]}))))

    (testing "Multiple published verdicts: latest is  a Pate verdict"
      (is (= {:status "ok" :statusLoc :pate.verdict-code.myonnetty}
             (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                             :published {:published 100}
                                                             :data      {:verdict-code "hyvaksytty"}}
                                                            {:category  "tj"
                                                             :published {:published 110}
                                                             :data      {:verdict-code "ehdollinen"}}
                                                            {:category  "tj"
                                                             :published {:published 120}
                                                             :data      {:verdict-code "myonnetty"}}]
                                            :verdicts      [{:modified  105
                                                             :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}
                                                            {:modified  115
                                                             :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                        {:paatospvm 4 :paatoskoodi "4"}]}
                                                                         {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}]}))))

    (testing "Multiple published verdicts: latest is  a backing system verdict"
      (is (= {:status "rejected" :statusLoc :verdict.status.37}
             (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                             :published {:published 100}
                                                             :data      {:verdict-code "hyvaksytty"}}
                                                            {:category  "tj"
                                                             :published {:published 110}
                                                             :data      {:verdict-code "ehdollinen"}}
                                                            {:category  "tj"
                                                             :published {:published 120}
                                                             :data      {:verdict-code "myonnetty"}}]
                                            :verdicts      [{:timestamp 125
                                                             :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                        {:paatospvm 4 :paatoskoodi "37"}]}
                                                                         {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}
                                                            {:timestamp 105
                                                             :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}]}))))

    (testing "Multiple published verdicts: drafts are ignored"
      (is (= {:status "ok" :statusLoc :pate.verdict-code.ehdollinen}
             (select-latest-verdict-status {:pate-verdicts [{:category  "tj"
                                                             :published {:published 100}
                                                             :data      {:verdict-code "hyvaksytty"}}
                                                            {:category  "tj"
                                                             :published {:published 110}
                                                             :data      {:verdict-code "ehdollinen"}}
                                                            {:category "tj"
                                                             :data     {:verdict-code "myonnetty"}}]
                                            :verdicts      [{:timestamp 125
                                                             :draft     true
                                                             :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                        {:paatospvm 4 :paatoskoodi "37"}]}
                                                                         {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}
                                                            {:timestamp 105
                                                             :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}]}))))

    (testing "Multiple published verdicts: state is acknowledged"
      (is (= {:status "ok"}
             (select-latest-verdict-status {:state         "acknowledged"
                                            :pate-verdicts [{:category  "tj"
                                                             :published {:published 100}
                                                             :data      {:verdict-code "hyvaksytty"}}
                                                            {:category  "tj"
                                                             :published {:published 110}
                                                             :data      {:verdict-code "ehdollinen"}}
                                                            {:category "tj"
                                                             :data     {:verdict-code "myonnetty"}}]
                                            :verdicts      [{:timestamp 125
                                                             :draft     true
                                                             :paatokset [{:poytakirjat [{:paatospvm 3 :paatoskoodi "3"}
                                                                                        {:paatospvm 4 :paatoskoodi "37"}]}
                                                                         {:poytakirjat [{:paatospvm 2 :paatoskoodi "2"}]}]}
                                                            {:timestamp 105
                                                             :paatokset [{:poytakirjat [{:paatospvm 1 :paatoskoodi "1"}]}]}]}))))))

(deftest foreman-app-context-test
  (testing "foreman-app-context"
    (testing "foreman in foreman application"
      (with-redefs [lupapalvelu.permissions/get-permissions-by-role
                    (fn [app-type role]
                      (when (and (= app-type :foreman-app) (= role "foreman"))
                        #{:foreman-app/test}))]
        (is (= #{:foreman-app/test}
               (-> (f/foreman-app-context {:user        {:id "1"}
                                           :application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                                         :auth             [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
                   :permissions)))))

    (testing "non-foreman in foreman application"
      (with-redefs [lupapalvelu.permissions/get-permissions-by-role
                    (fn [app-type role]
                      (when (and (= app-type :foreman-app) (= role "writer"))
                        #{}))]
        (is (empty? (-> (f/foreman-app-context {:user        {:id "2"}
                                                :application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                                              :auth             [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
                        :permissions)))))

    (testing "foreman in non-foreman application"
      (is (empty? (-> (f/foreman-app-context {:user        {:id "1"}
                                              :application {:primaryOperation {:name "kerrostalo-rivitalo"}
                                                            :auth             [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
                      :permissions))))

    (testing "user not in application auths"
      (is (empty? (-> (f/foreman-app-context {:user        {:id "3"}
                                              :application {:primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                                            :auth             [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
                      :permissions))))

    (testing "authority user not in application auths"
      (is (empty? (-> (f/foreman-app-context {:user        {:id "3" :orgAuthz {:123-T ["authority"]}}
                                              :application {:organization     "123-T"
                                                            :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}
                                                            :auth             [{:id "1" :role "foreman"} {:id "2" :role "writer"}]}})
                      :permissions))))

    (testing "no application in command"
      (is (empty? (-> (f/foreman-app-context {:user {:id "1"}})
                      :permissions))))))

(defn add-substitute-dates [foreman-app start-ts end-ts]
  (update foreman-app :documents
          #(map (fn [{:keys [schema-info] :as doc}]
                  (cond-> doc
                    (= "tyonjohtaja-v2" (:name schema-info))
                    (-> (assoc-in [:data :sijaistus :alkamisPvm :value] (date/finnish-date start-ts))
                        (assoc-in [:data :sijaistus :paattymisPvm :value] (date/finnish-date end-ts))))) %)))

(deftest foreman-responsibility-dates-test
  (testing "foreman responsibility dates"
    (let [mon           (date/timestamp "12.8.2019")
          tue           (date/timestamp "13.8.2019")
          wed           (date/timestamp "14.8.2019")
          thu           (date/timestamp "15.8.2019")
          fri           (date/timestamp "16.8.2019")
          app-new       (assoc foreman-app :permitSubtype "tyonjohtaja-hakemus")
          app-notice    (assoc foreman-app :permitSubtype "tyonjohtaja-ilmoitus"
                               :state "acknowledged"
                               :submitted tue)
          app-verdict   (assoc app-new :state "foremanVerdictGiven"
                               :pate-verdicts [{:published {:published wed}
                                                :category  "tj"}])
          app-ok        (assoc-in app-verdict [:pate-verdicts 0 :data :verdict-code] "1")
          app-rejected  (assoc-in app-verdict [:pate-verdicts 0 :data :verdict-code] "evatty")
          app-requested (assoc app-ok :foremanTermination {:started tue :ended nil})
          app-confirmed (assoc-in app-requested [:foremanTermination :ended] thu)
          app-conv1     (assoc app-notice
                               :facta-imported true ;; Does not affect date handling
                               :foremanTermination {:ended fri})
          app-conv2     (assoc-in app-conv1 [:foremanTermination :started] thu)
          app-conv3     (assoc app-conv1 :foremanTermination {:started fri})
          taskdate      (date/timestamp "12.10.2019")]
      (testing "new app has no start or end date"
        (is (= {:started nil :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps app-new {}))))
      (testing "tyonjohtaja-ilmoitus is considered started"
        (is (= {:started tue :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps app-notice {}))))
      (testing "tyonjohtaja-ilmoitus started date set by authority"
        (is (= {:started wed :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps (assoc app-notice :started wed)
                                                                   {}))))
      (testing "approved app has start date"
        (is (= {:started wed :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps app-ok {}))))
      (testing "Verdict date overrides submitted date for notice"
        (is (= {:started 4321 :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps
                 (assoc app-notice :pate-verdicts [{:published {:published 4321}
                                                    :data      {:verdict-code "hyvaksytty"}
                                                    :category  "tj"}])
                 {}))))
      (testing "rejected app has no start or end date"
        (is (= {:started nil :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps app-rejected {}))))
      (testing "app with termination requested has no end date (yet)"
        (is (= {:started tue :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps app-requested {}))))
      (testing "terminated app has start and end date"
        (is (= {:started tue :ended thu}
               (foreman-util/get-foreman-responsibility-timestamps app-confirmed {}))))
      (testing "approved app whose project app has a final review has ended"
        (is (= {:started wed :ended taskdate}
               (foreman-util/get-foreman-responsibility-timestamps app-ok project-app))))

      (testing "Converted applications"
        (is (= {:started tue :ended fri}
               (foreman-util/get-foreman-responsibility-timestamps app-conv1 {})))
        (is (= {:started thu :ended fri}
               (foreman-util/get-foreman-responsibility-timestamps app-conv2 {})))
        (is (= {:started tue :ended fri}
               (foreman-util/get-foreman-responsibility-timestamps app-conv1 project-app)))
        (is (= {:started thu :ended fri}
               (foreman-util/get-foreman-responsibility-timestamps app-conv2 project-app)))
        (is (= {:started fri :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps app-conv3 {})))
        (is (= {:started fri :ended taskdate}
               (foreman-util/get-foreman-responsibility-timestamps app-conv3 project-app))))

      (testing "Substitute foreman vs. responsibility dates"
        (is (= {:started mon :ended tue}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-new mon tue) {})))
        (is (= {:started nil :ended tue}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-new nil tue) {})))
        (is (= {:started nil :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-new tue mon) {})))
        (is (= {:started wed :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-ok tue nil) {})))
        (is (= {:started wed :ended thu}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-ok tue thu) {})))
        (is (= {:started thu :ended fri}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-ok thu fri) {})))
        (is (= {:started tue :ended thu}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-confirmed mon fri) {})))
        (is (= {:started wed :ended wed}
               (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-confirmed wed wed) {}))))

      (testing "Bad ranges are not returned"
        (is (nil? (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-ok nil tue) {})))
        (is (nil? (foreman-util/get-foreman-responsibility-timestamps (add-substitute-dates app-ok (date/timestamp (date/now)) nil)
                                                                      project-app)))))))

(deftest foreman-responsibility-dates-v3-test
  (testing "foreman responsibility dates for V3 documents"
    (let [mon           (date/timestamp "12.8.2019")
          tue           (date/timestamp "13.8.2019")
          wed           (date/timestamp "14.8.2019")
          thu           (date/timestamp "15.8.2019")
          fri           (date/timestamp "16.8.2019")
          taskdate      (date/timestamp "12.10.2019")
          ;; Base V3 document with tj-document flag
          base-v3-doc   {:tj-document true
                         :id          "doc-123"
                         :schema-info {:name "tyonjohtaja-v3"}
                         :data        {:sijaistus {}}}
          ;; Project app with verdicts (note: document-id is used for V3 foreman verdict matching)
          project-with-verdict {:tasks        []
                                :pate-verdicts [{:published {:published wed}
                                                 :category  "tj"
                                                 :data      {:verdict-code "hyvaksytty"}
                                                 :document-id "doc-123"}]}]

      (testing "V3 document - new (no state) has no start or end date"
        (is (= {:started nil :ended nil}
               (foreman-util/get-foreman-responsibility-timestamps base-v3-doc {}))))

      (testing "V3 document - declared state with submitted timestamp"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue)]
          (is (= {:started tue :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc {})))))

      (testing "V3 document - acknowledged state with submitted timestamp"
        (let [doc (assoc base-v3-doc
                         :state "acknowledged"
                         :submitted tue)]
          (is (= {:started tue :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc {})))))

      (testing "V3 document - with started timestamp (authority set)"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :started mon
                         :submitted tue)]
          (is (= {:started mon :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc {}))
              "Explicitly set start date takes precedence over submitted")))

      (testing "V3 document - verdict date takes precedence"
        (let [doc (assoc base-v3-doc
                         :state "acknowledged"
                         :submitted tue)]
          (is (= {:started wed :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc project-with-verdict))
              "Verdict date overrides submitted date")))

      (testing "V3 document - authority-set start takes precedence over verdict date"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :started mon  ; Authority set this
                         :submitted tue)
              proj project-with-verdict] ; Has verdict with date wed
          (is (= {:started mon :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc proj))
              "Authority-set start date should take precedence over verdict date")))

      (testing "V3 document - with termination dates"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue
                         :foremanTermination {:started wed :ended fri})]
          (is (= {:started wed :ended fri}
                 (foreman-util/get-foreman-responsibility-timestamps doc {})))))

      (testing "V3 document - ended by final review"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue)
              proj (assoc project-with-verdict :tasks [{:data  {:katselmuksenLaji {:value "loppukatselmus"}
                                                                :katselmus        {:tila    {:value "lopullinen"}
                                                                                   :pitoPvm {:value "12.10.2019"}}}
                                                        :state "sent"}])]
          (is (= {:started wed :ended taskdate}
                 (foreman-util/get-foreman-responsibility-timestamps doc proj)))))

      (testing "V3 document - with substitute dates"
        (let [doc (-> base-v3-doc
                      (assoc :state "declared" :submitted tue)
                      (assoc-in [:data :sijaistus :alkamisPvm :value] (date/finnish-date mon))
                      (assoc-in [:data :sijaistus :paattymisPvm :value] (date/finnish-date thu)))]
          (is (= {:started tue :ended thu}
                 (foreman-util/get-foreman-responsibility-timestamps doc {}))
              "Substitute start (mon) is earlier than submitted (tue), so tue is used as MAX")))

      (testing "V3 document - substitute start is MAX of substitute and responsibility start"
        (let [doc (-> base-v3-doc
                      (assoc :state "declared" :submitted wed)
                      (assoc-in [:data :sijaistus :alkamisPvm :value] (date/finnish-date tue)))]
          (is (= {:started wed :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc {}))
              "Responsibility start (wed) is later than substitute start (tue), so wed is used")))

      (testing "V3 document - substitute end is MIN of substitute and responsibility end"
        (let [doc (-> base-v3-doc
                      (assoc :state "declared" :submitted tue :foremanTermination {:ended fri})
                      (assoc-in [:data :sijaistus :paattymisPvm :value] (date/finnish-date thu)))]
          (is (= {:started tue :ended thu}
                 (foreman-util/get-foreman-responsibility-timestamps doc {}))
              "Substitute end (thu) is earlier than termination end (fri), so thu is used")))

      (testing "V3 document - invalid substitute range ignored"
        (let [doc (-> base-v3-doc
                      (assoc :state "declared" :submitted wed)
                      (assoc-in [:data :sijaistus :alkamisPvm :value] (date/finnish-date fri))
                      (assoc-in [:data :sijaistus :paattymisPvm :value] (date/finnish-date tue)))]
          (is (= {:started wed :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc {}))
              "Invalid substitute range (fri > tue) should be ignored")))

      (testing "V3 document - bad range not returned (only substitute dates, invalid)"
        (let [doc (-> base-v3-doc
                      (assoc-in [:data :sijaistus :alkamisPvm :value] (date/finnish-date fri))
                      (assoc-in [:data :sijaistus :paattymisPvm :value] (date/finnish-date tue)))]
          (is (= {:started nil :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc {}))
              "When only substitute dates exist and are invalid (start > end), they are ignored")))

      (testing "V3 document - responsibilities-start-date from verdict"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue)
              proj {:pate-verdicts [{:published {:published wed}
                                     :category  "tj"
                                     :data      {:verdict-code "hyvaksytty"
                                                 :responsibilities-start-date thu}
                                     :document-id "doc-123"}]}]
          (is (= {:started thu :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc proj))
              "responsibilities-start-date should take precedence over verdict published date")))

      (testing "V3 document - negative verdict doesn't provide verdict date but submitted still counts"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue)
              proj {:pate-verdicts [{:published {:published wed}
                                     :category  "tj"
                                     :data      {:verdict-code "evatty"}
                                     :document-id "doc-123"}]}]
          (is (= {:started tue :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc proj))
              "Negative verdict doesn't provide tj-verdict-ts, but declared document with submitted timestamp is still valid")))

      (testing "V3 document - verdict without document-id doesn't match"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue)
              proj {:pate-verdicts [{:published {:published wed}
                                     :category  "tj"
                                     :data      {:verdict-code "hyvaksytty"}
                                     ;; No document-id, so won't match this foreman doc
                                     }]}]
          (is (= {:started tue :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc proj))
              "Verdict without matching document-id should not be used")))

      (testing "V3 document - multiple verdicts, latest is used"
        (let [doc (assoc base-v3-doc
                         :state "declared"
                         :submitted tue)
              proj {:pate-verdicts [{:published {:published mon}
                                     :category  "tj"
                                     :data      {:verdict-code "hyvaksytty"}
                                     :document-id "doc-123"}
                                    {:published {:published thu}
                                     :category  "tj"
                                     :data      {:verdict-code "hyvaksytty"}
                                     :document-id "doc-123"}]}]
          (is (= {:started thu :ended nil}
                 (foreman-util/get-foreman-responsibility-timestamps doc proj))
              "Latest verdict (by published date) should be used"))))))

(deftest required-roles-test
  (testing "required-roles"
    (let [application  {:tasks [{:schema-info {:name "task-vaadittu-tyonjohtaja"} ; Regular task
                                 :data        {:kuntaRoolikoodi {:value "vastaava työnjohtaja"}}}
                                {:schema-info           {:name "task-vaadittu-tyonjohtaja"} ; Migrated task
                                 :data                  {:kuntaRoolikoodi {:value "työnjohtaja"}}
                                 :migrated-foreman-task true}
                                {:schema-info {:name "task-vaadittu-tyonjohtaja"} ; KVV-foreman (special case)
                                 :data        {:kuntaRoolikoodi {:value "kvv-työnjohtaja"}}}]
                        :documents [{:schema-info {:name "tyonjohtaja-v3"}
                                     :data        {:kuntaRoolikoodi {:value "erityisalojen työnjohtaja"}}}]}
          foreman-apps [{:id        "LP-VTJ" ; Required
                         :documents [{:data {:kuntaRoolikoodi {:value "vastaava työnjohtaja"}}}]}
                        {:id        "LP-IVTJ" ; Not required
                         :documents [{:data {:kuntaRoolikoodi {:value "iv-työnjohtaja"}}}]}]]
      (is (= [{:documents [{:data {:kuntaRoolikoodi {:value "erityisalojen työnjohtaja"}
                                   :vastattavatTyotehtavat {}}
                            :schema-info {:name "tyonjohtaja-v3"}}]
               :ended nil
               :latest-started-edit nil
               :latest-verdict-status {:status "new"}
               :required false
               :role-name ""
               :started nil
               :state nil
               :termination-reason nil
               :termination-request-ts nil
               :termination-requested false
               :termination-requester nil}
              {:lupapiste-role "KVV-työnjohtaja"
               :required       true
               :role-name      "KVV-työnjohtaja"
               :migrated       false
               :unmet-role     true}
              {:lupapiste-role "työnjohtaja"
               :required       true
               :role-name      "työnjohtaja"
               :migrated       true
               :unmet-role     true}
              {:documents [{:data {:kuntaRoolikoodi {:value "iv-työnjohtaja"}}}]
               :id        "LP-IVTJ"
               :required  false
               :role-name "iv-työnjohtaja"}
              {:documents [{:data {:kuntaRoolikoodi {:value "vastaava työnjohtaja"}}}]
               :id        "LP-VTJ"
               :required  true
               :role-name "vastaava työnjohtaja"}]
             (f/add-required-roles-to-foreman-info application foreman-apps))))))

(deftest foreman-info-responsible-tasks-filtering-test
  (testing "foreman-info-responsible-tasks-filtering"
    (is (= [{:data {:kuntaRoolikoodi        {:value "IV-työnjohtaja"}
                    :vastattavatTyotehtavat {:ivLaitoksenAsennustyo {:value true}
                                             :sisapuolinenKvvTyo    {:value false}
                                             :ulkopuolinenKvvTyo    {:value false}
                                             :muuMika               {:value true}
                                             :muuMikaValue          {:value "Janitor work"}}}}
            {:data {:kuntaRoolikoodi        {:value "työnjohtaja"}
                    :vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value false}
                                             :ulkopuolinenKvvTyo {:value false}
                                             :linjasaneeraus     {:value true}}}}]
           (->> [{:data {:kuntaRoolikoodi        {:value "IV-työnjohtaja"}
                         :vastattavatTyotehtavat {:ivLaitoksenAsennustyo {:value true}
                                                  :sisapuolinenKvvTyo    {:value true}
                                                  :ulkopuolinenKvvTyo    {:value false}
                                                  :muuMika               {:value true}
                                                  :muuMikaValue          {:value "Janitor work"}}}}
                 {:data {:kuntaRoolikoodi        {:value "työnjohtaja"}
                         :vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}
                                                  :ulkopuolinenKvvTyo {:value true}
                                                  :linjasaneeraus     {:value true}}}}]
                (mapv filter-foreman-doc-responsibilities))))))

(deftest vastattavat-tyotehtavat-test
  (testing "vastattavat-tyotehtavat"
    (is (= [[]
            ["Internal water supply and drainage work"]
            ["Internal water supply and drainage work" "Janitor work"]
            []
            ["Internal water supply and drainage work"]]
           (->> [{:data {:vastattavatTyotehtavat {}}}
                 {:data {:kuntaRoolikoodi        {:value "KVV-työnjohtaja"}
                         :vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}}}}
                 {:data {:kuntaRoolikoodi        {:value "KVV-työnjohtaja"}
                         :vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}
                                                  :ulkopuolinenKvvTyo {:value false}
                                                  :muuMika            {:value true}
                                                  :muuMikaValue       {:value "Janitor work"}}}}
                 {:data {:kuntaRoolikoodi        {:value "KVV-työnjohtaja"}
                         :vastattavatTyotehtavat {:muuMika      {:value false}
                                                  :muuMikaValue {:value "Janitor work"}}}}
                 {:data {:kuntaRoolikoodi        {:value "KVV-työnjohtaja"}
                         :vastattavatTyotehtavat {:sisapuolinenKvvTyo {:value true}
                                                  :muuMika            {:value true}
                                                  :muuMikaValue       {:value ""}}}}]
                (mapv #(f/vastattavat-tyotehtavat % "en")))))))

(deftest preprocess-foreman-role-test
  (testing "preprocess-foreman-role"
    (doseq [[from to] [[404 nil] ["  hello  " "hello"]
                       [" EI Tiedossa " "ei tiedossa"]
                       [" kvv-työnjohtaja " "KVV-työnjohtaja"]
                       [" Kvv-Työnjohtaja " "KVV-työnjohtaja"]
                       [" iv-työnjohtaja " "IV-työnjohtaja"]
                       [" Ivv-työnjohtaja " "ivv-työnjohtaja"]
                       [" TYÖNJOHTAJA " "työnjohtaja"]
                       [" Vastaava työnjohtaja " "vastaava työnjohtaja"]
                       [" Vatsaava työnjohtaja " "vatsaava työnjohtaja"]]]
      (testing (str from " -> " to)
        (is (= to (f/preprocess-foreman-role from)))))))

(deftest foreman-document-schema?-test
  (testing "foreman-document-schema?"
    (testing "returns nil for nil input"
      (is (nil? (f/foreman-document-schema? nil))))

    (testing "returns nil for document without foreman-document flag"
      (is (nil? (f/foreman-document-schema? {:schema-info {:name "hakija-r"}}))))

    (testing "returns truthy for document with foreman-document flag"
      (is (true? (f/foreman-document-schema? {:schema-info {:name            "tyonjohtaja-v2"
                                                            :foreman-document true}}))))

    (testing "returns falsy for document with foreman-document set to false"
      (is (false? (f/foreman-document-schema? {:schema-info {:name            "some-doc"
                                                             :foreman-document false}}))))))

(deftest foreman-doc-id-is-foreman-document-test
  (testing "foreman-doc-id-is-foreman-document"
    (let [foreman-doc     {:id          "foreman-doc-1"
                           :schema-info {:name             "tyonjohtaja-v2"
                                         :foreman-document true}}
          non-foreman-doc {:id          "other-doc-1"
                           :schema-info {:name "hakija-r"}}
          application     {:documents [foreman-doc non-foreman-doc]}]

      (testing "returns nil when foreman-doc-id is not provided"
        (is (nil? (f/foreman-doc-id-is-foreman-document {:application application
                                                         :data        {}}))))

      (testing "returns nil for valid foreman document"
        (is (nil? (f/foreman-doc-id-is-foreman-document {:application application
                                                         :data        {:foreman-doc-id "foreman-doc-1"}}))))

      (testing "returns error for non-foreman document"
        (is (expected-failure? :error.document-is-not-a-foreman-document
                               (f/foreman-doc-id-is-foreman-document {:application application
                                                                      :data        {:foreman-doc-id "other-doc-1"}}))))

      (testing "returns error for non-existent document"
        (is (expected-failure? :error.document-is-not-a-foreman-document
                               (f/foreman-doc-id-is-foreman-document {:application application
                                                                      :data        {:foreman-doc-id "non-existent"}})))))))

(deftest foreman-document-is-initiated-test
  (testing "foreman-document-is-initiated"
    (testing "v3 documents"
      (let [v3-doc-declared     {:id          "v3-doc-1"
                                 :tj-document true
                                 :schema-info {:name "tyonjohtaja-v3"}
                                 :state       "declared"}
            v3-doc-submitted    {:id          "v3-doc-2"
                                 :tj-document true
                                 :schema-info {:name "tyonjohtaja-v3"}
                                 :state       "submitted"}
            v3-doc-acknowledged {:id          "v3-doc-3"
                                 :tj-document true
                                 :schema-info {:name "tyonjohtaja-v3"}
                                 :state       "acknowledged"}
            v3-doc-no-state     {:id          "v3-doc-4"
                                 :tj-document true
                                 :schema-info {:name "tyonjohtaja-v3"}}
            v3-application      {:id        "LP-V3-123"
                                 :documents [v3-doc-declared
                                             v3-doc-submitted
                                             v3-doc-acknowledged
                                             v3-doc-no-state]}]

        (testing "returns error when document is not found"
          (is (expected-failure? :error.foreman-document-not-found
                                 (f/foreman-document-is-initiated {:application v3-application
                                                                   :data        {:foreman-doc-id "non-existent"}}))))

        (testing "v3 document with 'declared' state passes"
          (is (nil? (f/foreman-document-is-initiated {:application v3-application
                                                      :data        {:foreman-doc-id "v3-doc-1"}}))))

        (testing "v3 document with 'submitted' state passes"
          (is (nil? (f/foreman-document-is-initiated {:application v3-application
                                                      :data        {:foreman-doc-id "v3-doc-2"}}))))

        (testing "v3 document with 'acknowledged' state passes"
          (is (nil? (f/foreman-document-is-initiated {:application v3-application
                                                      :data        {:foreman-doc-id "v3-doc-3"}}))))

        (testing "v3 document without state fails"
          (is (expected-failure? :error.foreman.wrong-state
                                 (f/foreman-document-is-initiated {:application v3-application
                                                                   :data        {:foreman-doc-id "v3-doc-4"}}))))))

    (testing "v2 documents"
      (let [v2-doc                  {:id          "v2-doc-1"
                                     :schema-info {:name "tyonjohtaja-v2"}}
            v2-application          {:id        "LP-V2-123"
                                     :documents [v2-doc]}
            v2-app-approved-verdict (assoc v2-application
                                           :state         "foremanVerdictGiven"
                                           :pate-verdicts [{:published {:published 123}
                                                            :category  "tj"
                                                            :data      {:verdict-code "hyvaksytty"}}])
            v2-app-rejected-verdict (assoc v2-application
                                           :state         "foremanVerdictGiven"
                                           :pate-verdicts [{:published {:published 123}
                                                            :category  "tj"
                                                            :data      {:verdict-code "evatty"}}])]

        (testing "v2 document with approved verdict passes"
          (is (nil? (f/foreman-document-is-initiated {:application v2-app-approved-verdict
                                                      :data        {:foreman-doc-id "v2-doc-1"
                                                                    :id             "LP-V2-123"}}))))

        (testing "v2 document with rejected verdict passes"
          (is (nil? (f/foreman-document-is-initiated {:application v2-app-rejected-verdict
                                                      :data        {:foreman-doc-id "v2-doc-1"
                                                                    :id             "LP-V2-123"}}))))

        (testing "v2 document without verdict fails"
          (is (expected-failure? :error.foreman.wrong-state
                                 (f/foreman-document-is-initiated {:application v2-application
                                                                   :data        {:foreman-doc-id "v2-doc-1"
                                                                                 :id             "LP-V2-123"}}))))))))

(deftest validate-foreman-user-and-email-params-test
  (let [valid-user-id         (str (random-uuid))
        another-valid-user-id (str (random-uuid))
        no-auth-user-id       (str (random-uuid))
        invited-user-id       (str (random-uuid))
        denied-user-id        (str (random-uuid))
        reader-user-id        (str (random-uuid))
        company-id            (str (random-uuid))
        users                 [{:id    valid-user-id
                                :email "valid@example.com"}
                               {:id    another-valid-user-id
                                :email "another-valid@example.com"}
                               {:id    reader-user-id
                                :email "just-a-reader@example.com"}
                               {:id    no-auth-user-id
                                :email "no-auth@example.com"}
                               {:id    invited-user-id
                                :email "invited@example.com"}
                               {:id      denied-user-id
                                :email   "valid-but-company-denies@example.com"
                                :company {:id company-id}}
                               {:id      (str (random-uuid))
                                :email   "new-but-company-denies@example.com"
                                :company {:id company-id}}]
        users-by-email        (->> users
                                   (map (juxt :email identity))
                                   (into {}))
        users-by-id           (->> users
                                   (map (juxt :id identity))
                                   (into {}))
        denied-company        #{company-id}
        application           {:auth [{:id   valid-user-id
                                       :role "writer"}
                                      {:id   denied-user-id
                                       :role "writer"}
                                      {:id     invited-user-id
                                       :role   "writer"
                                       :invite {:user {:id invited-user-id}}}
                                      {:id   reader-user-id
                                       :role "reader"}]}
        cmd                   {:application application
                               :data        {}}]
    (with-redefs [usr/get-user-by-email               (fn [email] (get users-by-email email))
                  usr/get-user-by-id                  (fn [id]
                                                        (get users-by-id id))
                  company/company-denies-invitations? (fn [_app company-id]
                                                        (contains? denied-company company-id))]
      (testing "validate user-id"
        (testing "happy path"
          (testing "valid user-id"
            (let [cmd (assoc-in cmd [:data :foremanUser] valid-user-id)]
              (is (nil? (f/validate-foreman-user-params cmd))))))

        (testing "unknown user id"
          (let [cmd (assoc-in cmd [:data :foremanUser] "-1")]
            (is (= {:ok   false
                    :text "invite.user-not-found"}
                   (f/validate-foreman-user-params cmd)))))

        (testing "user has only read authz"
          (let [cmd (assoc-in cmd [:data :foremanUser] reader-user-id)]
            (is (= {:ok   false
                    :text "invite.user-not-found"}
                   (f/validate-foreman-user-params cmd)))))

        (testing "company denies auth"
          (testing "by id"
            (let [cmd (assoc-in cmd [:data :foremanUser] denied-user-id)]
              (is (= {:ok   false
                      :text "invite.company-denies-invitation"}
                     (f/validate-foreman-user-params cmd))))))

        (testing "user with no auth"
          (testing "by id"
            (let [cmd (assoc-in cmd [:data :foremanUser] no-auth-user-id)]
              (is (= {:ok   false
                      :text "invite.user-not-found"}
                     (f/validate-foreman-user-params cmd))))))

        (testing "pending invite"
          (testing "by id"
            (let [cmd (assoc-in cmd [:data :foremanUser] invited-user-id)]
              (is (= {:ok   false
                      :text "invite.already-has-auth"}
                     (f/validate-foreman-user-params cmd)))))))
      (testing "validate email"
        (testing "happy path"
          (testing "valid email"
            (let [cmd (assoc-in cmd [:data :foremanEmail] "good-new-email@example.com")]
              (is (nil? (f/validate-foreman-email-params cmd))))))

        (testing "invalid email"
          (let [cmd (assoc-in cmd [:data :foremanEmail] "foo")]
            (is (= {:ok   false
                    :text "error.email"}
                   (f/validate-foreman-email-params cmd)))))

        (testing "already has auth"
          (let [cmd (assoc-in cmd [:data :foremanEmail] "valid@example.com")]
            (is (= {:ok   false
                    :text "invite.already-has-auth"}
                   (f/validate-foreman-email-params cmd)))))

        (testing "company denies auth"
          (testing "by email"
            (let [cmd (assoc-in cmd [:data :foremanEmail] "new-but-company-denies@example.com")]
              (is (= {:ok   false
                      :text "invite.company-denies-invitation"}
                     (f/validate-foreman-email-params cmd))))))

        (testing "pending invite"
          (testing "by email"
            (let [cmd (assoc-in cmd [:data :foremanEmail] "invited@example.com")]
              (is (= {:ok   false
                      :text "invite.already-has-auth"}
                     (f/validate-foreman-email-params cmd))))))))))
