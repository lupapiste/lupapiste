(ns lupapalvelu.backing-system.dmcity.client
  "Support for manually sending messages to dmcity. Used by Admin admin UI and batchruns."
  (:require [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.backing-system.dmcity.schemas :as dm-sc]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as bs]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.user :as usr]
            [sade.core :refer [now]]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn ^:always-validate client-config :- dm-sc/ClientConfig
  ([system organization-id user options]
   {:system       system
    :organization (mongo/by-id :organizations organization-id)
    :user         (or user (usr/batchrun-user [organization-id]))
    :options      (merge {:report? true :force-enabled? true}
                         options)})
  ([system organization-id options]
   (client-config system organization-id nil options))
  ([system organization-id]
   (client-config system organization-id nil nil)))

(defn- cfg->cmd [cfg application]
  (assoc (select-keys cfg [:organization :user :system])
         :application application
         :created (now)))

(sc/defn ^:private ^:always-validate
  client--state-changes :- [dm-sc/ClientResult]
  [cfg :- dm-sc/ClientConfig application state-or-states]
  (let [cmd (cfg->cmd cfg application)]
    (for [state (flatten [state-or-states])]
      (util/strip-nils
        {:application-id (:id application)
         :state          (name state)
         :ts             (:created cmd)
         :error          (dmcity/send-state-change cmd state (:options cfg))}))))

(sc/defn ^:private ^:always-validate
  client--xml :- dm-sc/ClientResult
  [cfg :- dm-sc/ClientConfig application ctx-key xml]
  (let [cmd (cfg->cmd cfg application)]
    (assert (map? (:system cmd)))
    (util/strip-nils
      {:application-id (:id application)
       :ts             (:created cmd)
       :error          (dmcity/send-xml cmd ctx-key xml (:options cfg))})))

(defmulti ^:private client--send :op)

(defn- history-states
  "Skipped states are ignored only from the beginning of the state history."
  [{:keys [history]}]
  (->> history
       (keep (comp keyword :state))
       (drop-while dm-sc/skipped-state?)
       seq))

(defmethod client--send :last-state
  [{:keys [cfg application]}]
  (some->> (history-states application)
           last
           (client--state-changes cfg application)))

(defmethod client--send :all-states
  [{:keys [cfg application]}]
  (some->> (history-states application)
           (mapcat #(client--state-changes cfg application %))))

(defmethod client--send :application
  [{:keys [cfg application xml]}]
  [(client--xml cfg application :application
                (or xml (bs/batchrun-kuntagml application (:organization cfg) (:system cfg))))])

(defmethod client--send :verdicts
  [{:keys [cfg application]}]
  (let [cmd (cfg->cmd cfg application)
        cfg (assoc-in cfg [:options :type-suffix] :verdict)]
    (for [verdict (filter vc/kuntagml-verdict? (vif/published-verdicts cmd))]
      (assoc (client--xml cfg application :application
                          (bs/full-batchrun-kuntagml (:organization cfg)
                                                     (:system cfg)
                                                     application
                                                     {:verdict-id (:id verdict)}))
             :verdict-id (:id verdict)))))

(defn sent-review? [{:keys [state schema-info]}]
  (and (util/=as-kw :sent state)
       (util/includes-as-kw? #{:review :review-backend}
                             (:subtype schema-info))))

(defmethod client--send :reviews
  [{:keys [cfg application]}]
  (for [review (filter sent-review? (:tasks application))]
    (assoc (client--xml cfg application :review
                        (bs/batchrun-kuntagml application
                                              (:organization cfg)
                                              (:system cfg)
                                              {:review review
                                               :user   (:user cfg)}))
           :review {:id   (:id review)
                    :name (:taskname review)})))

(def ClientOps [dm-sc/ClientOp])

(defn- skipped [{:keys [application]}]
  (let [{:keys [state primaryOperation id permitType
                infoRequest]} application
        op-name               (:name primaryOperation)]
    (when-let [m (cond
                   (dm-sc/skipped-state? state)
                   {:state state}

                   (contains? #{"tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen-v3"} op-name)
                   {:operation op-name}

                   (= permitType "ARK")
                   {:permitType permitType}

                   infoRequest {:infoRequest true})]
      [{:application-id id
        :ts             (now)
        :skipped        m}])))

(sc/defn ^:always-validate send-messages :- [dm-sc/ClientResult]
  ([options :- {:cfg         dm-sc/ClientConfig
                :application sc/Any
                sc/Keyword   sc/Any}
    ops :- ClientOps]
   (let [{:keys [cfg application]} options
         ops                       (set ops)]
     (log/with-logging-context {:applicationId (:id application)
                                :userId        (-> cfg :user :id)}
       (or (skipped options)
           (doall
             (mapcat (fn [op]
                       (when-let [op (cond
                                       (nil? (ops op)) nil

                                       (= op :application/verdicts)
                                       (if (vif/latest-published-verdict-date application)
                                         :verdicts :application)

                                       :else           op)]
                         (try
                           ;; Doall not strictly needed due to schema validation, but just in
                           ;; case.
                           (doall (client--send (assoc options :op op)))
                           (catch Exception e
                             (log/errorf "Send messages %s failed: %s" op (ex-message e))
                             [{:application-id (:id application)
                               :ts             (now)
                               :error          (ex-message e)}]))))
                     [:last-state :all-states :application :verdicts
                      :application/verdicts :reviews]))))))
  ([cfg :- dm-sc/ClientConfig application ops :- ClientOps]
   (send-messages {:cfg cfg :application application} ops)))
