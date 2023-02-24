(ns lupapalvelu.backing-system.allu.contract
  "ALLU meets Pate. They meet here in order to avoid verdict/allu/pate-verdict dependency cycles."
  (:require [clojure.set :as set]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as attachment :refer [AttachmentOptions]]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [AlluUser ContractStatus ContractMetadata AlluMetadata]]
            [lupapalvelu.file-upload :refer [SavedFileData]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.metadata :as pate-metadata]
            [lupapalvelu.pate.schema-util :refer [application->category]]
            [lupapalvelu.pate.schemas :refer [PateCategoryTag PateVerdict PateSignature]]
            [lupapalvelu.pate.verdict :as pate-verdict]
            [lupapalvelu.pate.verdict-common :refer [allu-contract-proposal?]]
            [lupapalvelu.state-machine :as sm]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]
            [taoensso.timbre :refer [error warn]]))

(defschema AlluVerdictType (sc/enum :decision :contract-proposal :final-contract :contract-rejected))

;;;; # Document Metadata from ALLU

(defn- contract-metadata? [allu-metadata]
  (contains? allu-metadata :status))

(sc/defn ^:private allu-metadata-status :- ContractStatus
  "ALLU contract/decision status. If not a placement contract, always returns 'FINAL'."
  [allu-metadata :- AlluMetadata]
  (or (:status allu-metadata) "FINAL"))                     ; Only PlacementContracts have non-final states.

(sc/defn ^:private allu-metadata-signer :- AlluUser
  "Get the signing [[AlluUser]] from [[AlluMetadata]]."
  [{:keys [handler decisionMaker] :as allu-metadata} :- AlluMetadata]
  (case (allu-metadata-status allu-metadata)
    "FINAL" (or decisionMaker handler)
    handler))

(sc/defn ^:private allu-user->string :- sc/Str [{:keys [title name]} :- AlluUser]
  (str title " " name))

;;;; # ALLU Pate Contracts

(sc/defn ^:private allu-category-tag :- PateCategoryTag [application, allu-metadata :- AlluMetadata]
  (name (if (contract-metadata? allu-metadata)
          (application->category application)
          :allu-verdict)))

(sc/defn ^:private allu-contract-data
  ":data for ALLU contract [[PateVerdict]]:s."
  [{:keys [application] :as command} allu-status :- ContractStatus]
  (pate-metadata/wrap-all (pate-metadata/wrapper command)
                          {:handler         (pate-verdict/general-handler application)
                           :agreement-state (-> allu-status ss/lower-case keyword)
                           :kuntalupatunnus (-> application :integrationKeys :ALLU :kuntalupatunnus)}))

(sc/defn ^:private final-allu-contract-signature :- PateSignature
  "ALLU signature for contracts in 'FINAL' state."
  [{:keys [user created] :as _command} signer :- AlluUser]
  {:name    (allu-user->string signer)
   ;; TODO: `signer` might not be the same as `user`, do we need to do something about this?
   ;;       Indeed, `signer` might not even exist in Lupapiste and `user` might be `batchrun-user`!
   :user-id (:id user)
   :date    created})

(sc/defn ^:private allu-contract-initial-signatures :- (sc/maybe [PateSignature])
  "nil for contract proposals. For final contracts returns proposal signatures and signature of `signer`.
  For decisions returns singleton seq of `signer` signature."
  [{:keys [application] :as command} category :- PateCategoryTag, allu-status :- ContractStatus, signer :- AlluUser]
  (when (= allu-status "FINAL")
    (let [contract-proposal (when (= category "allu-contract")
                              (util/find-first allu-contract-proposal? (:pate-verdicts application)))]
      (->> (:signatures contract-proposal)
           (cons (final-allu-contract-signature command signer))
           (sort-by :date)))))

(sc/defn ^:always-validate ^:private new-allu-verdict :- PateVerdict
  "Create Pate verdict for ALLU placement contract (proposal) or other decision."
  [{:keys [application created] :as command} metadata :- AlluMetadata, signer :- AlluUser]
  (let [category (allu-category-tag application metadata)
        allu-status (allu-metadata-status metadata)
        verdict {:id        (mongo/create-id)
                 :modified  created
                 :state     (pate-verdict/wrapped-state command :published)
                 :category  category
                 :data      (allu-contract-data command allu-status)
                 :template  {:inclusions []}
                 :legacy?   true
                 :published {:published created
                             :tags      (ss/serialize {:body []})}
                 :archive   {:verdict-giver (allu-user->string signer)}}]
    (util/assoc-when verdict :signatures (allu-contract-initial-signatures command category allu-status signer))))

(sc/defn ^:private allu-verdict-type :- AlluVerdictType [{:keys [category data] :as _verdict} :- PateVerdict]
  (case category
    "allu-verdict" :decision
    "allu-contract" (case (-> data :agreement-state pate-metadata/unwrap name)
                      "proposal" :contract-proposal
                      "final" :final-contract
                      "rejected" :contract-rejected)))

;;;; # Assorted Helpers

(defn- next-state
  "Returns the next state or nil if no state change."
  [application allu-metadatas]
  (let [md-status-set (set (map allu-metadata-status allu-metadatas))]
    (cond
      (and (md-status-set "PROPOSAL")
           (util/=as-kw (:state application) :agreementPrepared))
      nil

      (md-status-set "FINAL")
      (case (allu/application-type application)
        "sijoitussopimus"                          :agreementSigned
        ("lyhytaikainen-maanvuokraus" "promootio") :verdictGiven)

      :else
      (sm/next-state application))))

(sc/defn ^:private allu-contract-attachment-options :- AttachmentOptions
  [verdict-id :- ssc/ObjectIdStr, created :- ssc/Timestamp]
  {:created         created
   :attachment-type {:type-group :muut
                     :type-id    :sopimus}
   :target          {:id   verdict-id
                     :type :verdict}
   :modified        created
   :locked          true
   :read-only       true})

(sc/defn ^:private required-verdict-types :- #{AlluVerdictType}
  [application-state :- sc/Str, contract-metadata :- (sc/maybe ContractMetadata)]
  (if (some? contract-metadata)
    (if (= (:status contract-metadata) "FINAL")
      (if (= application-state "agreementPrepared")
        #{:contract-proposal :final-contract :decision}
        #{:final-contract :decision})
      #{:contract-proposal})
    #{:decision}))

(sc/defn ^:private existing-verdict-types :- #{AlluVerdictType}
  [verdicts :- [PateVerdict]]
  (into #{} (map allu-verdict-type) verdicts))

(sc/defn ^:private verdict-types-to-load :- #{AlluVerdictType}
  [contract-metadata :- (sc/maybe ContractMetadata), application-state :- sc/Str, verdicts :- [PateVerdict]]
  (set/difference (required-verdict-types application-state contract-metadata)
                  (existing-verdict-types verdicts)))

(sc/defn ^:private load-allu-verdict :- (sc/maybe {:metadata AlluMetadata, :filedata SavedFileData})
  [command, contract-metadata :- (sc/maybe ContractMetadata), verdict-type :- AlluVerdictType]
  (case verdict-type
    :contract-proposal (when-some [filedata (allu/load-contract-document! command :proposal)]
                         {:metadata contract-metadata, :filedata filedata})
    :final-contract (when-some [filedata (allu/load-contract-document! command :final)]
                      {:metadata contract-metadata, :filedata filedata})
    :contract-rejected (warn "Don't know how to load rejected contract, skipping loading ALLU verdict...") ; FIXME ???
    :decision (when-some [metadata (allu/load-decision-metadata command)]
                (when-some [filedata (allu/load-decision-document! command)]
                  {:metadata metadata, :filedata filedata}))))

(sc/defn ^:private load-allu-verdicts :- (sc/maybe [{:metadata AlluMetadata, :filedata SavedFileData}])
  "Load application decisions and/or contracts from ALLU."
  [{{:keys [pate-verdicts] :as application} :application :as command}]
  (if (allu/uses-contract-documents? application)
    (when-some [contract-metadata (allu/load-contract-metadata command)]
      (util/mapv-some (partial load-allu-verdict command contract-metadata)
                      (verdict-types-to-load contract-metadata (:state application) pate-verdicts)))
    (util/mapv-some (partial load-allu-verdict command nil)
                    (verdict-types-to-load nil (:state application) pate-verdicts))))

(defn- allu-verdict->verdict-params [command {:keys [metadata] :as allu-verdict}]
  (let [signer (allu-metadata-signer metadata)]
    (assoc allu-verdict :signer signer
                        :verdict (new-allu-verdict command metadata signer))))

(sc/defn ^:private save-verdicts!
  "Save verdict attachments and `:pate-verdicts`."
  [{:keys [application user created] :as command}
   verdict-params :- [{:verdict PateVerdict, :metadata AlluMetadata, :filedata SavedFileData, :signer AlluUser}]
   update-state?]
  (doseq [{:keys [verdict filedata signer]} verdict-params]
    (attachment/convert-and-attach! (update command :user assoc
                                            :firstName (:title signer)
                                            :lastName (:name signer))
                                    (allu-contract-attachment-options (:id verdict) created)
                                    filedata))
  (action/update-application command
                             (util/deep-merge
                               (when update-state?
                                 (some-> (next-state application (map :metadata verdict-params))
                                         (app-state/state-transition-update created application user)))
                               {$push {:pate-verdicts {$each (map :verdict verdict-params)}}})))

;;;; # Public API

(sc/defn fetch-allu-contract :- (sc/maybe [PateVerdict])
  "Fetch contract from ALLU, create matching verdict and verdict attachment on application and transition application
  to next state. If application has been fast-tracked in ALLU this might speed it directly to :agreementSigned.
  Returns [[PateVerdict]] for fetched ALLU contract or nil if fetching contract file or metadata failed."
  [command]
  (when-some [allu-verdicts (seq (load-allu-verdicts command))]
    (let [verdict-params (map (partial allu-verdict->verdict-params command) allu-verdicts)]
      (save-verdicts! command verdict-params true)
      (map :verdict verdict-params))))

(defn sign-allu-contract
  "Sign ALLU contract and inform ALLU of the proposal approval.
  Unlike [[pate-verdict/sign-contract]] will only modify Lupapiste application to add signature to contract."
  [command]
  (pate-verdict/verdict-update command {$push {:pate-verdicts.$.signatures (pate-verdict/create-signature command)}})
  (allu/approve-placementcontract! command))
