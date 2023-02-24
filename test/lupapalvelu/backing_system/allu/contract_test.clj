(ns lupapalvelu.backing-system.allu.contract-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.backing-system.allu.schemas :refer [ContractMetadata DecisionMetadata
                                                             ValidPlacementApplication ValidShortTermRental
                                                             ValidPromotion]]
            [lupapalvelu.backing-system.allu.contract :as allu-contract]
            [lupapalvelu.pate.metadata :as pate-metadata]
            [lupapalvelu.pate.schemas :refer [PateLegacyVerdict]]
            [lupapalvelu.pate.schema-util :refer [application->category]]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.user :refer [User]]
            [sade.schema-generators :as ssg]
            [sade.core :refer [now]]
            [sade.env :as env]
            [schema.core :as sc]))

(use-fixtures :once
              (fn [run-tests] (sc/with-fn-validation (run-tests)))
              (fn [run-tests] (env/with-feature-value :allu true (run-tests))))

(deftest contract-metadata?-test
  (is (#'allu-contract/contract-metadata? (ssg/generate ContractMetadata)))
  (is (not (#'allu-contract/contract-metadata? (ssg/generate DecisionMetadata)))))

(deftest allu-metadata-status-test
  (testing "contract metadata"
    (let [contract-metadata (ssg/generate ContractMetadata)]
      (is (= (#'allu-contract/allu-metadata-status contract-metadata)
             (:status contract-metadata)))))

  (testing "decision metadata"
    (is (= (#'allu-contract/allu-metadata-status (ssg/generate DecisionMetadata))
           "FINAL"))))

(deftest allu-metadata-signer-test
  (testing "contract metadata"
    (let [{:keys [handler decisionMaker]
           :as   contract-metadata} (ssg/generate ContractMetadata)]
      (is (= (#'allu-contract/allu-metadata-signer contract-metadata)
             (case (:status contract-metadata)
               "FINAL" (or decisionMaker handler)
               handler)))))

  (testing "decision metadata"
    (let [decision-metadata (ssg/generate DecisionMetadata)]
      (is (= (#'allu-contract/allu-metadata-signer decision-metadata)
             (or (:decisionMaker decision-metadata)
                 (:handler decision-metadata)))))))

(deftest allu-user->string-test
  (is (= (#'allu-contract/allu-user->string {:title "Viranomainen", :name "Allu Lupanen"})
         "Viranomainen Allu Lupanen")))

(deftest next-state-test
  (testing "placement contract"
    (let [application (ssg/generate ValidPlacementApplication)]
      (is (= (#'allu-contract/next-state application []) (sm/next-state application)))

      (let [metadatas [(assoc (ssg/generate ContractMetadata) :status "FINAL")]]
        (is (= (#'allu-contract/next-state application metadatas) :agreementSigned)))))

  (testing "placement contract no state change"
    (let [application (assoc (ssg/generate ValidPlacementApplication)
                             :state "agreementPrepared")
          metadatas   [(assoc (ssg/generate ContractMetadata) :status "PROPOSAL")]]
      (is (nil? (#'allu-contract/next-state application metadatas)))
      (is (nil? (#'allu-contract/next-state application
                                            (conj metadatas
                                                  (assoc (ssg/generate ContractMetadata)
                                                         :status "FINAL")))))))

  (testing "short term rental"
    (let [application (ssg/generate ValidShortTermRental)
          metadatas   [(ssg/generate DecisionMetadata)]]
      (is (= (#'allu-contract/next-state application metadatas) :verdictGiven))))

  (testing "promotion"
    (let [application (ssg/generate ValidPromotion)
          metadatas   [(ssg/generate DecisionMetadata)]]
      (is (= (#'allu-contract/next-state application metadatas) :verdictGiven)))))

;; NOTE: Also implicitly tests entire `new-allu-contract` return value since Schema fn validation is on:
(deftest new-allu-verdict-test
  (let [user (ssg/generate (select-keys User [:id :username :firstName :lastName]))
        created (now)
        signer {:title "Viranomainen", :name "Allu Lupanen"}]
    (testing "placement contract"
      (let [kuntalupatunnus "SL2317"
            application (-> (ssg/generate ValidPlacementApplication)
                            (assoc-in [:integrationKeys :ALLU :kuntalupatunnus] kuntalupatunnus)
                            (assoc :handlers [{:general true, :firstName "Allu", :lastName "Lupanen"}]))
            command {:application application, :user user, :created created}]
        (testing "decision"
          (let [metadata (ssg/generate DecisionMetadata)
                decision (#'allu-contract/new-allu-verdict command metadata signer)]
            (is (= (:signatures decision) [{:name    "Viranomainen Allu Lupanen"
                                            :user-id (:id user)
                                            :date    created}]))
            (is (= (-> decision :data :agreement-state :_value) :final))))

        (testing "proposal"
          (let [metadata (assoc (ssg/generate ContractMetadata) :status "PROPOSAL")
                proposal (#'allu-contract/new-allu-verdict command metadata #_"PROPOSAL" signer)]
            (is (not (contains? proposal :signatures)))

            (is (= (-> proposal :data :handler :_value) "Allu Lupanen"))
            (is (= (-> proposal :data :agreement-state :_value) :proposal))
            (is (= (-> proposal :data :kuntalupatunnus :_value) kuntalupatunnus))))

        (testing "final placement contract"
          (let [metadata (assoc (ssg/generate ContractMetadata) :status "FINAL")
                contract (#'allu-contract/new-allu-verdict command metadata #_"FINAL" signer)]
            (is (= (:signatures contract) [{:name    "Viranomainen Allu Lupanen"
                                            :user-id (:id user)
                                            :date    created}]))
            (is (= (-> contract :data :agreement-state :_value) :final))))))

    (testing "short term rental"
      (let [application (ssg/generate ValidShortTermRental)
            command {:application application, :user user, :created created}
            metadata (ssg/generate DecisionMetadata)
            decision (#'allu-contract/new-allu-verdict command metadata signer)]
        (is (= (:signatures decision) [{:name    "Viranomainen Allu Lupanen"
                                        :user-id (:id user)
                                        :date    created}]))
        (is (= (-> decision :data :agreement-state :_value) :final))))

    (testing "promotion"
      (let [application (ssg/generate ValidPromotion)
            command {:application application, :user user, :created created}
            metadata (ssg/generate DecisionMetadata)
            decision (#'allu-contract/new-allu-verdict command metadata signer)]
        (is (= (:signatures decision) [{:name    "Viranomainen Allu Lupanen"
                                        :user-id (:id user)
                                        :date    created}]))
        (is (= (-> decision :data :agreement-state :_value) :final))))))

(deftest allu-verdict-type-test
  (testing "contract proposal"
    (let [verdict (ssg/generate (assoc PateLegacyVerdict :category (sc/eq "allu-contract")
                                                         :data {:agreement-state (sc/eq :proposal)}))]
      (is (= (#'allu-contract/allu-verdict-type verdict) :contract-proposal))))

  (testing "final contract"
    (let [verdict (ssg/generate (assoc PateLegacyVerdict :category (sc/eq "allu-contract")
                                                         :data {:agreement-state (sc/eq "final")}))]
      (is (= (#'allu-contract/allu-verdict-type verdict) :final-contract))))

  (testing "decision"
    (let [verdict (ssg/generate (assoc PateLegacyVerdict :category (sc/eq "allu-verdict")
                                                         :data {:agreement-state (sc/eq :final)}))]
      (is (= (#'allu-contract/allu-verdict-type verdict) :decision)))))

(def ^:private contract-proposal
  (assoc (ssg/generate PateLegacyVerdict)
    :category "allu-contract"
    :data {:agreement-state (pate-metadata/wrap "rakennustarkastaja@hel.fi" (now) :proposal)}))

(def ^:private final-contract
  (assoc (ssg/generate PateLegacyVerdict)
    :category "allu-contract"
    :data {:agreement-state (pate-metadata/wrap "rakennustarkastaja@hel.fi" (now) :final)}))

(deftest verdict-types-to-load-test
  (testing "contract proposal"
    (let [contract-metadata (assoc (ssg/generate ContractMetadata) :status "PROPOSAL")]
      (is (= (#'allu-contract/verdict-types-to-load contract-metadata "sent" [])
             #{:contract-proposal}))))

  (testing "final contract + decision"
    (let [contract-metadata (assoc (ssg/generate ContractMetadata) :status "FINAL")
          verdicts [contract-proposal]]
      (is (= (#'allu-contract/verdict-types-to-load contract-metadata "agreementPrepared" verdicts)
             #{:final-contract :decision}))))

  (testing "does not use contracts"
    (is (= (#'allu-contract/verdict-types-to-load nil "sent" []) #{:decision})))

  (testing "missing decision"
    (let [contract-metadata (assoc (ssg/generate ContractMetadata) :status "FINAL")
          verdicts [final-contract contract-proposal]]
      (is (= (#'allu-contract/verdict-types-to-load contract-metadata "agreementSigned" verdicts)
             #{:decision})))))
