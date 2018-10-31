(ns lupapalvelu.backing-system.allu.contract
  (:require [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.state-machine :as sm]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.action :as action]
            [lupapalvelu.pate.schemas :as schemas]
            [monger.operators :refer :all]
            [sade.core :refer [ok]]
            [sade.util :as util]
            [schema.core :as sc]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.verdict :as pate-verdict]))

(sc/defn ^:always-validate new-allu-contract :- schemas/PateVerdict [{:keys [application created] :as command}]
         (let [category (schema-util/application->category application)]
           {:id       (mongo/create-id)
            :modified created
            :state    (pate-verdict/wrapped-state command :published)
            :category (name category)
            :data     {:handler (pate-verdict/general-handler application)}
            :template {:inclusions (-> category
                                       legacy/legacy-verdict-schema
                                       :dictionary
                                       pate-verdict/dicts->kw-paths)}
            :legacy?  true}))

(defn sign-allu-contract
  "Sign the contract
   - Update verdict signatures
   - Change application state to agreementSigned if needed
   - Don't move to the :agreementSigned state yet (still requires a verdict from ALLU)"
  [{:keys [user created application] :as command}]
  (let [signature (pate-verdict/create-signature command)
        verdict (pate-verdict/command->verdict command)]
    (pate-verdict/verdict-update command
                    (util/deep-merge
                      {$push {(util/kw-path :pate-verdicts.$.signatures) signature}}))
    (allu/approve-placementcontract! command)))

(defn fetch-allu-contract
  [{:keys [application created user] :as command}]
  ;; HACK: This is here instead of e.g. do-check-for-verdict to avoid verdict/allu/pate-verdict
  ;;       dependency cycles:
  (when-let [filedata (allu/load-contract-document! command)]
    ;; FIXME: Some times should be dates, not timestamps:
    (let [creator (:creator application)
          signatures [{:name (:username user)
                       :user-id (:id user)
                       :date created}]
          signatures (if (= :final (allu/agreement-state application))
                       (conj signatures {:name (str (:firstName creator) " " (:lastName creator))
                                         :user-id (:id (:creator application))
                                         :date created})
                       signatures)
          verdict (new-allu-contract command)
          verdict (assoc verdict
                    :published {:published created
                                :tags (pr-str {:body (pate-verdict/backing-system--tags
                                                       application verdict)})}
                    ;; FIXME: Should be general-handler fullname instead of current username:
                    :archive {:verdict-giver (:username user)}
                    ;; FIXME: Should be general-handler fullname instead of current username:
                    :signatures signatures) ; HACK
          transition-update (app-state/state-transition-update (sm/next-state application)
                                                               created application user)]
      (attachment/convert-and-attach! command
                                      {:created created
                                       :attachment-type {:type-group :muut
                                                         :type-id :sopimus}
                                       :target {:id (:id verdict)
                                                :type :verdict}
                                       :modified created
                                       :locked true
                                       :read-only true}
                                      filedata)
      (action/update-application command (util/deep-merge transition-update
                                                          {$push {:pate-verdicts verdict}}))
      (ok :verdicts [verdict]))))

