(ns lupapalvelu.backing-system.allu.contract
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.legacy-schemas :as legacy]
            [lupapalvelu.pate.metadata :as metadata]
            [lupapalvelu.pate.schema-util :as schema-util]
            [lupapalvelu.pate.schemas :as schemas]
            [lupapalvelu.pate.verdict :as pate-verdict]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.state-machine :as sm]
            [monger.operators :refer :all]
            [sade.core :refer [ok]]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn ^:always-validate new-allu-contract :- schemas/PateVerdict [{:keys [application created] :as command}]
  (let [category (schema-util/application->category application)
        kuntalupatunnus (-> application :integrationKeys :ALLU :kuntalupatunnus)]
    {:id       (mongo/create-id)
     :modified created
     :state    (pate-verdict/wrapped-state command :published)
     :category (name category)
     :data     (metadata/wrap-all (metadata/wrapper command)
                                  {:handler         (pate-verdict/general-handler application)
                                   :agreement-state (allu/agreement-state application)
                                   :kuntalupatunnus kuntalupatunnus})
     :template {:inclusions []}
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
  ;; This is here instead of e.g. do-check-for-verdict to avoid
  ;; verdict/allu/pate-verdict dependency cycles:
  (when-let [filedata (allu/load-contract-document! command)]
    (when-let [allu-metadata (allu/load-contract-metadata command)]
      (let [signer-name (-> allu-metadata :handler :name)
            signer-title (-> allu-metadata :handler :title)
            verdict (merge (new-allu-contract command)
                           {:published {:published created
                                        :tags (ss/serialize {:body []})}
                            ;; FIXME: Should be general-handler fullname instead of current username:
                            :archive {:verdict-giver (:username user)}}
                           (when (= :final (allu/agreement-state application))
                             {:signatures (->> application
                                               :pate-verdicts
                                               (util/find-first #(and (vc/has-category? % :allu-contract)
                                                                      (util/not=as-kw (vc/allu-agreement-state %)
                                                                                      :final)))
                                               :signatures
                                               (cons {:name (str signer-title " " signer-name)
                                                      :user-id (:id user)
                                                      :date created})
                                               (sort-by :date))}))
            transition-update (app-state/state-transition-update (sm/next-state application)
                                                                 created application user)]
        (attachment/convert-and-attach! (update-in command [:user] assoc
                                                   :lastName signer-name
                                                   :firstName signer-title)
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
        verdict))))
