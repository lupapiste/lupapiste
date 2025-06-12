(ns lupapalvelu.transfer-application-itest
  (:require [clojure.test :as t]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :as itest :refer [server-address]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [monger.operators :refer [$set]]))

(t/use-fixtures :each
  (itest/local-fixture-factory (-> {}
                                   test-system/with-remote-components
                                   ;; These could also be omitted; test would invoke HTTP
                                   ;; endpoint directly without going through pubsub
                                   (test-system/with-pubsub-publisher :backing-system/http)
                                   (test-system/with-pubsub-subscriber :backing-system/http))))

;; Test data
(def admin itest/admin)
(def applicant-apikey itest/pena)

;; YMP-test data
(def ymp-org-id "753-R") ;; This has ymp configurations enabled
(def ymp-property-id itest/sipoo-property-id)
(def ymp-authority-apikey itest/sonja)

;; Non-YMP test data
(def non-ymp-org-id "837-R")
(def non-ymp-property-id itest/tampere-property-id)
(def non-ymp-authority-apikey itest/veikko)

(def applicant-updates
  (let [{:keys [firstName
                lastName
                email
                phone
                street
                city
                zip
                id]} (fixture/find-user-by-apikey :minimal applicant-apikey)]
    [["_selected" "henkilo"]
     ["henkilo.userId" id]
     ["henkilo.henkilotiedot.etunimi" firstName]
     ["henkilo.henkilotiedot.sukunimi" lastName]
     ["henkilo.osoite.katu" street]
     ["henkilo.osoite.postinumero" zip]
     ["henkilo.osoite.postitoimipaikannimi" city]
     ["henkilo.yhteystiedot.puhelin" phone]
     ["henkilo.yhteystiedot.email" email]]))

;; Fill in applicant info, needed eventually for approve application at least in some cases.
(defn fill-applicant-info [app]
  (itest/command applicant-apikey :update-doc
                 :id (:id app)
                 :doc (:id (domain/get-applicant-document (:documents app)))
                 :updates applicant-updates))

(defn approve-succeeds [app-id auth-apikey]
  (t/testing "Approve application succeeds"
    (t/is (= {:ok                   true
              :integrationAvailable true}
             (itest/command auth-apikey :approve-application
                            :id app-id
                            :lang "fi")))))

(defn dmcity-config [permit-types]
  ;; Use the mock DmCity endpoint in `lupapalvelu.dummy-krysp-service`
  {:username "test-user"
   :password "test-secret"
   :vault    "test-vault"
   :url      (str (server-address) "/dev/dummy-dmcity")
   :targets  (->> permit-types
                  (reduce (fn [m pt]
                            (assoc m
                                   (keyword pt) true))
                          {}))})

(defn enable-dmcity [org-id permit-types & [config]]
  (itest/command admin :upsert-dmcity-configuration
                 :organizationId org-id
                 :configuration (or config
                                    (dmcity-config permit-types))))

(defn disable-dmcity [org-id]
  (itest/command admin :delete-dmcity-configuration
                 :organizationId org-id))

(defn transfer-application [app-id auth-apikey]
  (itest/command auth-apikey :transfer-application
                 :id app-id
                 :lang "fi"))

(t/deftest feature-for-YMP-permit-type
  (let [app         (itest/create-and-submit-application applicant-apikey
                                                         :operation "lannan-varastointi-v3"
                                                         :propertyId ymp-property-id)
        permit-type (:permitType app)
        app-id      (:id app)]
    (t/testing "Ensure application is of YMP permit type"
      (t/is (contains? permit/ymp-permit-types
                       permit-type)))

    (enable-dmcity ymp-org-id #{permit-type})
    (fill-applicant-info app)

    (t/testing "Transfer application fails when not approved"
      (t/is (= {:ok    false
                :state "submitted"
                :text  "error.command-illegal-state"}
               (transfer-application app-id ymp-authority-apikey))))

    (approve-succeeds app-id ymp-authority-apikey)

    (t/testing "Transfer application succeeds when dmcity is enabled"
      (t/is (= {:ok                   true
                :integrationAvailable true}
               (transfer-application app-id ymp-authority-apikey))))

    (disable-dmcity ymp-org-id)

    (t/testing "Transfer application fails when dmcity is disabled"
      (t/is (= {:ok   false
                :text "error.dmcity.enabled"}
               (transfer-application app-id ymp-authority-apikey))))))

(t/deftest transfer-application-fails-for-non-ymp-permit
  (let [app    (itest/create-and-submit-application applicant-apikey
                                                    :operation "kerrostalo-rivitalo"
                                                    :propertyId non-ymp-property-id)
        app-id (:id app)]
    (t/testing "Ensure application is of _not_ a YMP permit type"
      (t/is (not (contains? permit/ymp-permit-types
                            (-> app :permitType)))))

    (enable-dmcity non-ymp-org-id #{"R"})
    (fill-applicant-info app)

    (approve-succeeds app-id non-ymp-authority-apikey)

    (t/testing "Transfer application fails for non-YMP permit type"
      (t/is (= {:ok   false
                :text "error.invalid-permit-type"}
               (transfer-application app-id non-ymp-authority-apikey))))))

(t/deftest transfer-application-requires-exported-to-backend-system-transfers
  (let [app    (itest/create-and-submit-application applicant-apikey
                                                    :operation "lannan-varastointi-v3"
                                                    :propertyId ymp-property-id)
        app-id (:id app)]
    (enable-dmcity ymp-org-id #{"YM"})
    (fill-applicant-info app)
    (approve-succeeds app-id ymp-authority-apikey)

    (t/testing "Transfer application succeeds when exported transfers exist"
      (t/is (= {:ok                   true
                :integrationAvailable true}
               (transfer-application app-id ymp-authority-apikey))))

    (t/testing "Remove transfers and verify no transfers found"
      (mongo/update-by-id :applications app-id {$set {:transfers []}})
      (t/is (= []
               (->> app-id
                    (mongo/by-id :applications)
                    :transfers))))

    (t/testing "Transfer application fails when no exported transfers exist"
      (t/is (= {:ok   false
                :text "error.cannot-transfer-application-not-exported-yet"}
               (transfer-application app-id ymp-authority-apikey))))))
