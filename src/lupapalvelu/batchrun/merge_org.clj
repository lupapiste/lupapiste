(ns lupapalvelu.batchrun.merge-org
  "Provides a batchrun for merging organizations.

  Can be used when e.g. a municipal union is formed where an existing
  organization (`destination`) absorbs another organization (`source`).
  Data is moved from the source organization to the destination and,
  finally, the source organization is disabled.

  Operations:
  - Moves authority users, drops authorizations to the source organization
  - Moves authority admins, but will retain their admin authorization to the source organization
  - Moves scopes
  - Moves applications
  - Moves application bulletins
  - Disables source organization

  Known limitations/restrictions:
  - Source may not have invoices, transfer batches, price catalogs, nor assignments
  - Source may not have AD login configured
  - Source scopes are sanitized and any integrations et al. are disabled
  - Integration messages are left as-is
  "
  (:require [babashka.cli :as cli]
            [clojure.set :as set]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$each $exists $push $set $unset]]
            [sade.core :as sade]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- restrict
  "Forbid unknown options in the spec"
  [spec]
  (assoc spec :restrict (vec (keys (:spec spec)))))

(def ^:private cli-spec
  (-> {:spec {:dest-org {:desc    "Destination organization"
                         :alias   :d
                         :require true}
              :src-org  {:desc    "Source organization (will be disabled)"
                         :alias   :s
                         :require true}
              :ticket   {:desc    "Jira ticket number to include in sheriff notes"
                         :alias   :t
                         :require true}
              :dry-run  {:desc   "Dry run, do no changes"
                         :coerce :boolean}}}
      restrict))

(defn- print-help
  []
  (log/info "-------------------------")
  (log/info "Merges two organizations:")
  (doseq [line (ss/split-lines (cli/format-opts cli-spec))]
    (log/info line))
  (log/info "-------------------------"))

(defn- parse-args
  [args]
  (try
    (cli/parse-opts args cli-spec)
    (catch Exception e
      (log/error "Invalid arguments for batchrun:" args)
      (print-help)
      (throw e))))

(defn- args->options
  [{:keys [dest-org src-org now ticket dry-run]}]
  (let [dest (mongo/by-id :organizations (ss/upper-case dest-org))
        src  (mongo/by-id :organizations (ss/upper-case src-org))]
    (and (or dest (throw (ex-info "Could not find organization" {:org dest-org})))
         (or src (throw (ex-info "Could not find organization" {:org src-org})))
         (or (not= (:id dest) (:id src))
             (throw (ex-info "Same target and source organization" {:id (:id dest)})))
         {::now      now
          ::dest     dest
          ::src      src
          ::ticket   ticket
          ::dry-run? dry-run})))

(defn- join-list
  [xs]
  (ss/join ", " (sort xs)))

(defn- pre-checks
  "Immediately fail if source organization has data we don't support (yet)."
  [{::keys [src dry-run?]}]
  (when-let [problems (cond-> #{}
                        (mongo/any? :ad-logins {:organizations.id (:id src)})               (conj :ad-logins)
                        (mongo/any? :assignments {:application.organization (:id src)})     (conj :assignments)
                        (mongo/any? :invoices {:organization-id (:id src)})                 (conj :invoices)
                        (mongo/any? :invoice-transfer-batches {:organization-id (:id src)}) (conj :transfer-batches)
                        (mongo/any? :price-catalogues {:organization-id (:id src)})         (conj :price-catalogues)
                        true                                                                seq)]
    (if dry-run?
      (log/errorf "Cannot merge: %s has documents we do not yet support: %s!"
                  (:id src) (join-list problems))
      (throw (ex-info "Pre-checks failed" {:problems problems
                                           :src      (:id src)})))))

(def ^:private scope-keys #{:municipality
                            :permitType
                            ;; Note that these two flags retain their value
                            :new-application-enabled
                            :inforequest-enabled})

(defn- clean-scope
  "Drops scope fields which might cause problems under the new org (e.g. integrations)"
  [{:keys [municipality permitType] :as scope}]
  (let [cleaned (select-keys scope scope-keys)
        dropped (set/difference (set (keys scope)) scope-keys)]
    (when (seq dropped)
      ;; Some of these logging statements are prefixed with spaces to make the logging output
      ;; a bit easier to read
      (log/warnf "  Dropping fields from scope %s-%s: %s"
                 municipality permitType (join-list dropped)))
    [cleaned (when (seq dropped)
               (select-keys scope (conj dropped :municipality :permitType)))]))

(defn- move-scopes!
  [{::keys [dest src dry-run? now ticket]}]
  (let [scope-labels (->> src
                          :scope
                          (map (fn [{:keys [municipality permitType]}]
                                 (str municipality "-" permitType)))
                          sort
                          (into []))]
    (log/infof "Moving %d scopes from %s to %s: %s"
               (count scope-labels)
               (:id src) (:id dest)
               (join-list scope-labels))
    (let [{:keys [cleaned-scopes dropped]} (reduce (fn [m scope]
                                                     (let [[cleaned dropped] (clean-scope scope)]
                                                       (cond-> (update m :cleaned-scopes conj cleaned)
                                                         (seq dropped) (update :dropped conj dropped))))
                                                   {:cleaned-scopes []
                                                    :dropped        []}
                                                   (:scope src))]
      (when-not dry-run?
        (mongo/update-by-id :organizations
                            (:id dest)
                            {$push {:scope          {$each cleaned-scopes}
                                    :_sheriff-notes {:note   (format "Merged scopes from %s" (:id src))
                                                     :ticket ticket
                                                     :now    now}}}))
      (log/infof "Deactivating and clearing scopes for: %s" (:id src))
      (when-not dry-run?
        (mongo/update-by-id :organizations
                            (:id src)
                            {$set  {:scope       []
                                    :deactivated true}
                             $push {:_sheriff-notes {$each (concat
                                                             [{:note   (format "Deactivated due to merge with %s"
                                                                               (:id dest))
                                                               :ticket ticket
                                                               :now    now}]
                                                             ;; Include the dropped data (not the whole scope) in note,
                                                             ;; just in case we need them later
                                                             (when (seq dropped)
                                                               [{:note    "Dropped scope fields due to merge"
                                                                 :ticket  ticket
                                                                 :dropped dropped
                                                                 :now     now}]))}}})))))

(defn- org->authz
  "Construct a path like `:orgAuthz.123-R`"
  [org]
  (util/kw-path :orgAuthz (:id org)))

(defn- assign-new-authz!
  "Grant authorization as needed"
  [{::keys [dest src dry-run? ticket now]} user]
  (let [src-auths  (set (get-in user [:orgAuthz (keyword (:id src))]))
        dest-auths (set (get-in user [:orgAuthz (keyword (:id dest))] #{}))
        to-add     (set/difference src-auths dest-auths)]
    (when (seq to-add)
      (log/infof "  Authorizing %s in %s as %s"
                 (:email user)
                 (:id dest)
                 (join-list to-add))
      (when-not dry-run?
        (mongo/update-by-id :users
                            (:id user)
                            {$push {(org->authz dest) {$each (vec to-add)}
                                    :_sheriff-notes   {:note   (format "Granted access to %s" (:id dest))
                                                       :ticket ticket
                                                       :now    now}}})))))

(defn- drop-old-authz!
  "Drop non-authorityAdmin authorizations"
  [{::keys [src dry-run? ticket now]} user]
  (let [src-auths  (set (get-in user [:orgAuthz (keyword (:id src))]))
        not-admin? (not (util/includes-as-kw? src-auths :authorityAdmin))]
    (cond
      not-admin?
      (do
        (log/infof "  Deauthorizing all authz for %s from %s: %s"
                   (:email user)
                   (:id src)
                   (join-list src-auths))
        (when-not dry-run?
          (mongo/update-by-id :users
                              (:id user)
                              {$unset {(org->authz src) true}
                               $push  {:_sheriff-notes {:note   (format "Removed access from %s" (:id src))
                                                        :ticket ticket
                                                        :now    now}}})))

      (< 1 (count src-auths))
      (do
        (log/infof "  Deauthorizing non-admin authz for %s from %s: %s"
                   (:email user)
                   (:id src)
                   (join-list (disj src-auths "authorityAdmin")))
        (when-not dry-run?
          (mongo/update-by-id :users
                              (:id user)
                              {$set  {(org->authz src) [:authorityAdmin]}
                               $push {:_sheriff-notes {:note   (format "Removed other authorizations from %s" (:id src))
                                                       :ticket ticket
                                                       :now    now}}}))))))

(defn- move-authorities!
  "Move authority users under the destination organization"
  [{::keys [src] :as options}]
  (let [all-user-q {(org->authz src) {$exists true}}
        ;; Non-authority users with orgAuthz (e.g. rest-api users) are ignored
        user-q     {(org->authz src) {$exists true}
                    :role            :authority}
        users      (mongo/select :users user-q [:orgAuthz :email])]
    (log/infof "Found %d applicable authorities (out of %s) in %s"
               (count users) (mongo/count :users all-user-q) (:id src))
    (doseq [user users]
      (assign-new-authz! options user)
      (drop-old-authz! options user))))

(defn- move-applications!
  "Move all applications under the destination organization"
  [{::keys [src dest dry-run? now ticket]}]
  (let [collection :applications
        org-key    :organization
        q          {org-key (:id src)}
        matches    (if dry-run?
                     (mongo/count collection q)
                     (mongo/update-by-query collection q
                                            {$set  {org-key (:id dest)}
                                             $push {:_sheriff-notes {:note   (format "Moved from %s" (:id src))
                                                                     :ticket ticket
                                                                     :now    now}}}))]
    (log/infof "Found %d %s in %s" matches (name collection) (:id src))))

(defn- move-application-bulletins!
  "Move all application bulletin versions under the destination organization"
  [{::keys [src dest dry-run?]}]
  (let [collection :application-bulletins
        q          {:versions.organization (:id src)}
        matches    (if dry-run?
                     (mongo/count collection q)
                     (mongo/update-by-query collection q {$set {"versions.$[].organization" (:id dest)}}))]
    (log/infof "Found %d %s in %s" matches (name collection) (:id src))))

(defn do-merge!
  [args]
  (let [{::keys [dest src] :as options} (args->options args)]
    (log/infof "Merging from %s (%s) into %s (%s).."
               (:id src) (-> src :name :fi) (:id dest) (-> dest :name :fi))
    (pre-checks options)
    (move-applications! options)
    (move-application-bulletins! options)
    (move-authorities! options)
    (move-scopes! options)
    (log/info "Done!")))

(defn batchrun
  "Merge source organization into the destination organization"
  [args]
  (let [parsed (parse-args args)]
    (try
      (do-merge! (assoc parsed :now (sade/now)))
      (catch Exception e
        (log/error e "Failed to merge organizations")
        (throw e))))
  nil)

(comment
  (apply batchrun "-s" "753-R" "-d" "186-R" "-t" "LPK-1234" "--dry-run"))
