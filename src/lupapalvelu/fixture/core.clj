(ns lupapalvelu.fixture.core
  (:require [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.elasticsearch.util :as es-util]
            [lupapalvelu.logging :as log :refer [error]]
            [lupapalvelu.mongo-watcher.client :as mw-client]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [sade.env :as env]))

(set! *warn-on-reflection* true)

(env/in-dev
  (defn clear-and-reindex-all-elastic-indices! [system]
    (when-let [es-client (components/system->elastic system)]
      (log/info "Reindexing elastic indexes!")
      (->> (components/system->elastic-config system)
           :indices
           (map :name)
           (filter (fn [index] (es-util/index-exists? (:rest-client es-client) (-> es-client :index-alias-map index :write))))
           (run! (fn [index]
                   (es-api/delete-by-query! es-client index {:match_all {}})
                   (es-api/full-index-update! es-client index))))))

  (defmulti fixture (fn [fname _system] (keyword fname)))

  (defmethod fixture :default
    [fname _system]
    (error "Fixture" fname "not defined."))

  (defn fixtures []
    (-> (methods fixture)
        (dissoc :default)
        keys set))

  (defn fixture? [fname]
    (contains? (fixtures) (keyword fname)))

  (defn apply-fixture
    "Runs fixture method for `fname` and returns true. False if the fixture is not defined."
    [fname system]
    {:pre [(map? system)]}
    (fixture fname system)
    (some-> (components/system->mongo-watcher system)
            mw-client/restart-watchers!)
    (clear-and-reindex-all-elastic-indices! system)
    (fixture? fname))

  (def ^:dynamic *fixture*
    "Side-channel for passing current fname outside regular params"
    nil)

  (defmacro with-fixture
    [fname & body]
    `(binding [*fixture* ~fname]
       ~@body))

  ;; Helpers

  (defmulti users
    "Retrieve fixture users"
    keyword)

  (defmethod users :default
    [fname]
    (error "Fixture" fname "defines no user accessor"))

  (defn find-user
    [fname username]
    (->> (users fname)
         (some #(when (= (:username %) username) %))))

  (defn find-user-by-apikey
    [fname apikey]
    (->> (users fname)
         (some #(when (= (get-in % [:private :apikey]) apikey) %))
         (usr/with-org-auth)))

  (defn id-for
    [fname username]
    (:id (find-user fname username)))

  (defn id-for-key
    [fname apikey]
    (:id (find-user-by-apikey fname apikey)))

  (defn apikey-for
    [fname username]
    (get-in (find-user fname username) [:private :apikey]))

  (defn email-for
    [fname username]
    (:email (find-user fname username)))

  (defn email-for-key
    [fname apikey]
    (:email (find-user-by-apikey fname apikey))))
