(ns lupapalvelu.ui.matti.path
  (:require [clojure.string :as s]
            [goog.events :as googe]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn state [path state]
  (rum/cursor-in state (map keyword path)))

(defn extend [path & extra]
  (->> (concat [path] extra)
       flatten
       (remove nil?)
       (map keyword)))

(defn loc-extend
  [loc-path {:keys [id loc-prefix body]}]
  (let [locPrefix (some-> body first :locPrefix)]
    (cond
      loc-prefix [loc-prefix]
      locPrefix  [locPrefix]
      id (extend loc-path id)
      :else loc-path)))

(defn id-extend [id-path {id :id}]
  (extend id-path id))

(defn schema-options
  "Options for a child schema. Options are parent options."
  [options schema]
  (let [{:keys [id-path loc-path]} options]
    (assoc options
           :schema schema
           :loc-path (loc-extend loc-path schema)
           :id-path (id-extend id-path schema))))

(defn dict-options
  "Options corresponding to the the dictionary schema referenced in the
  given options. Creates path ([dict]) onto top-level."
  [{{dict :dict} :schema dictionary :dictionary :as options}]
  (assoc (schema-options options (dict dictionary))
         :path [dict]))

(defn value [path state* & extra]
  @(state (extend path extra) state*))

(defn react [path state* & extra]
  (rum/react (state (extend path extra) state*)))

(defn id [path]
  (s/replace (->> (filter identity path)
                  (map name)
                  (s/join "-"))
             "." "-"))

(defmulti loc (fn [a & _]
                (when (map? a)
                  :map)))

(defmethod loc :default
  [& path]
  (->> (flatten path)
       (filter identity)
       (map name)
       (s/join ".")
       common/loc))

(defmethod loc :map
  [{:keys [i18nkey loc-path schema] :as arg} & extra]
  (loc (concat [(if (sequential? arg)
                  arg
                  (or i18nkey
                      (some-> schema :body first :i18nkey)
                      (:i18nkey schema)
                      (:loc-prefix schema)
                      (:dict schema)
                      loc-path))]
               extra)))

(defn- access-meta [{:keys [id-path _meta]} key & [deref-fn]]
  (loop [m ((or deref-fn deref) _meta)
         path id-path]
    (if (empty? path)
      (get m key)
      (let [v (get m (util/kw-path path key))]
        (if (nil? v)
          (recur m (butlast path))
          v)))))

(defn meta-value
  "_meta is a flat map with kw-mapped keys. meta-value returns value for the
  key that is closest to the id-path of the options."
  [options key]
  (access-meta options key))

(defn react-meta
  "Like meta-value but uses rum/react to access the _meta atom."
  [options key]
  (access-meta options key rum/react))

(defn flip-meta
  "Flips (toggles boolean) on the id-path _meta."
  [{:keys [_meta id-path]} key]
  (let [kw (util/kw-path id-path key)]
    (swap! _meta (fn [m]
                   (assoc m kw (not (kw m)))))))

(defn css
  "List of CSS classes based on current :css value and status
  classes (matti--edit, matti--view) from the latest _meta."
  [options & other-classes]
  (->> [(if (meta-value options :editing?) "matti--edit" "matti--view")
        (:css options)
        other-classes]
       flatten
       (remove nil?)
       (map name)))

(defn meta-updated
  "Calls _meta :updated if defined."
  [options]
  (when-let [fun (access-meta options :updated)]
    (fun options)))

(defn key-fn
  "Rum key-fn, where key is the path id."
  [{id-path :id-path}]
  (id id-path))

(defn unique-id
  "Callthrough for goog.events.getUniqueId."
  [prefix]
  (googe/getUniqueId prefix))

(defn pathify
  "Splits kw-path if necessary."
  [path]
  (if (keyword? path)
    (util/split-kw-path path)
    path))

(defn- truthy [v]
  (cond
    (sequential? v) (seq v)
    :else v))

(defn- path-truthy [{state :state :as options} kw-path]
  (let [[x & [k] :as path] (pathify kw-path)]
    (truthy (if (= x :_meta )
              (react-meta options k )
              (react path state)))))

(defn- paths-result
  "Result is calculated be calling op-fn (e.g., some) to path-truthy
  results of every path. Result is nil for empty/nil paths."
  [options op-fn paths]
  (let [paths (->> [paths] flatten (remove nil?))]
    (when (seq paths)
      (->> paths
           (map (partial path-truthy options))
           (op-fn identity )))))

(defn- flag?
  "not-any-flag: schema key for NOT-ANY (or empty/nil) condition.
   or-flag:  schema key for ANY (or empty/nil) condition.
  True if either conditions met."
  [{:keys [state schema] :as options} not-any-flag or-flag]
  (let [results (->> [(->> schema not-any-flag (paths-result options not-any?))
                      (->> schema or-flag (paths-result options some))]
                     (remove nil?))]
    (when (seq results)
      (some identity results))))

(defn disabled?
  "Component disabled status as defined in MattiEnabled schema."
  [options]
  (flag? options :enabled? :disabled?))

(defn enabled? [options] (not (disabled? options)))

(defn not-visible?
  "Component not-visible status as defined in MattiVisible schema."
  [options]
  (flag? options :show? :hide?))

(defn visible? [options] (not (not-visible? options)))
