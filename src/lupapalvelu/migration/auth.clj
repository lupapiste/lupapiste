(ns lupapalvelu.migration.auth
  (:require [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [schema.core :as sc]))

(defn- transfer-cfg [old-email new-email]
  (let [{old-username :username
         old-id       :id } (usr/get-user-by-email old-email)
        {new-username :username
         new-id       :id}  (usr/get-user-by-email new-email)
        q                   {:auth {$elemMatch {:id old-id}}}]
    (when (and old-id old-username new-id new-username)
      (util/mmap old-username old-id new-username new-id q))))

(sc/defn ^:always-validate transfer-auths :- {:apply-when ssc/Fn
                                              :execute    ssc/Fn}
  "Transfers every application auth for `old-email` user to `new-email`. Only id and
  username details are updated. `prefix` starts the migration sheriff note."
  [prefix :- ssc/NonBlankStr old-email :- ssc/Email new-email :- ssc/Email]
  {:pre [(not= old-email new-email)]}
  (let [cfg (delay (transfer-cfg old-email new-email))]
    {:apply-when (fn []
                   (if-let [{q :q} @cfg]
                     (mongo/any? :applications q)
                     false))
     :execute    (fn []
                   (when-let [{:keys [old-username old-id new-username new-id
                                      q]} @cfg]
                     (let [note (format "%s Migration: Auth change %s (%s) -> %s (%s)."
                                        prefix old-username old-id new-username new-id)]
                       (mongo/update-by-query :applications q
                                              (conv-util/with-sheriff-note note
                                                {$set {:auth.$.id       new-id
                                                       :auth.$.username new-username}})))))}))


(comment
  (let [{:keys [apply-when execute]} (transfer-auths "TT-1234" "bob@example.net" "dot@example.org")]
    (defmigration TT-1234-bob-dot
      {:apply-when (apply-when)}
      (execute))))

(def Auth [{sc/Keyword sc/Any}])

(sc/defn ^:always-validate purge-auth :- (sc/maybe Auth)
  "Takes an application and returns a new `:auth` vector with items matching `bad-ids`
  removed and the first item matching `good-id` retained. Returns nil if no changes are
  needed."
  [{:keys [auth]} :- {:auth Auth sc/Keyword sc/Any}
   good-id        :- ssc/NonBlankStr
   bad-ids        :- #{ssc/NonBlankStr}]
  {:pre [(util/find-by-id good-id auth)]}
  (let [all-ids (conj bad-ids good-id)]
    (when (-> (filter (comp all-ids :id) auth) count (> 1))
      (loop [[x & xs]       auth
             good-id-found? false
             result         []]
        (cond
          (nil? x)            result
          (= (:id x) good-id) (recur xs true (cond-> result
                                               (not good-id-found?) (conj x)))
          (bad-ids (:id x))   (recur xs good-id-found? result)
          :else               (recur xs good-id-found? (conj result x)))))))
