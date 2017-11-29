(ns lupapalvelu.ui.pate.path
  "Various utilities for component state, path, schema and _meta
  handling."
  (:require [clojure.string :as s]
            [goog.events :as googe]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn state
  "Cursor for substate or rather state-within-state."
  [path state]
  (rum/cursor-in state (map keyword path)))

(defn extend
  "New extended path. Extension args can be either items or lists. The
  result is flattened and nils removed."
  [path & extra]
  (->> (concat [path] extra)
       flatten
       (remove nil?)
       (map keyword)))

(defn loc-extend
  "Extends loc-path based on the options. The options can include
  loc-prefix, locPrefix (via docgen body) or id."
  [loc-path {:keys [id loc-prefix body]}]
  (let [locPrefix (some-> body first :locPrefix)]
    (cond
      loc-prefix [loc-prefix]
      locPrefix  [locPrefix]
      id (extend loc-path id)
      :else loc-path)))

(defn id-extend
  "Extends id-path with the options id."
  [id-path {id :id}]
  (extend id-path id))

(defn schema-options
  "Options for a child schema. Options are parent options (and are
  stored into _parent property)."
  [options schema]
  (let [{:keys [id-path loc-path]} options]
    (assoc options
           :schema schema
           :_parent options
           :loc-path (loc-extend loc-path schema)
           :id-path (id-extend id-path schema))))

(defn dict-options
  "Options corresponding to the the dictionary schema referenced in the
  given options. Creates path onto top-level. Extends existing
  path, [dict] otherwise."
  [{{dict :dict} :schema dictionary :dictionary path :path :as options}]
  (let [{dict-schema :schema
         dict-path   :path :as res} (shared/dict-resolve (concat path [dict])
                                                         dictionary)]
    (assoc (schema-options options dict-schema )
           :path (extend path dict dict-path))))

(defn value
  "Value of the substate denoted by path (and extras)."
  [path state* & extra]
  @(state (extend path extra) state*))

(defn react
  "Like value but uses rum/reac for deref."
  [path state* & extra]
  (rum/react (state (extend path extra) state*)))

(defn id
  "Transforms path (list) to id (string).

  ['foo.bar' :hii nil 'hoo'] -> 'foo-bar-hii-hoo'"
  [path]
  (s/replace (->> (filter identity path)
                  (map name)
                  (s/join "-"))
             "." "-"))

(defmulti loc
  "Localization resolution for different scenarios."
  (fn [a & _]
    (when (map? a)
      :map)))

;; Default localization: args are combined into loc-key and localized.
(defmethod loc :default
  [& path]
  (->> (flatten path)
       (filter identity)
       (map name)
       (s/join ".")
       common/loc))

;; Component-aware localization: i18nkey(s), loc-prefix and dict
;; override the loc-path.
(defmethod loc :map
  [{:keys [i18nkey loc-path schema] :as arg} & extra]
  (loc (concat [(or i18nkey
                    (some-> schema :body first :i18nkey)
                    (:i18nkey schema)
                    (:loc-prefix schema)
                    (:dict schema)
                    loc-path)]
               extra)))

(defn- access-meta
  ([{:keys [id-path _meta]} key deref-fn]
   (loop [path id-path]
     (let [v (deref-fn (rum/cursor-in _meta [(util/kw-path path key)]))]
       (if (or (not (nil? v)) (empty? path))
         v
         (recur (butlast path))))))
  ([options key]
   (access-meta options key deref)))

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
    (swap! (rum/cursor-in _meta [kw]) not)))

(defn css
  "List of CSS classes based on current :css value and status
  classes (pate--edit, pate--view) from the latest _meta."
  [options & other-classes]
  (->> [(if (meta-value options :editing?) "pate--edit" "pate--view")
        (some-> options :schema :css)
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


;; Callthrough for goog.events.getUniqueId.
;; Must be in the global scope.
(def unique-id googe/getUniqueId)

(defn pathify
  "Splits kw-path if necessary."
  [path]
  (if (keyword? path)
    (util/split-kw-path path)
    path))

(defn- has-path?
  "True if the path value in the state is not nil. No existing path
  should resolve into nil value. Note: this is not reactive, since the
  idea is that the state 'structure' is not dynamic."
  [path state]
  (-> (value path state) nil? not))

(defn- truthy? [v]
  (boolean (cond
             (sequential? v) (seq v)
             :else v)))

(defn- resolve-path
  "Resolve relative path. Relative path is denoted with + or -. The
  former refers to child path and the latter to sibling: Meta paths
  cannot have +/-.

  Examples (current path is [:foo :bar])

  :?+.hii.hoo -> [:? :foo :bar :hii :hoo]
  :-.hii.hoo  -> [:foo :hii :hoo]
  :-?.hii.hoo -> [? :foo :hii :hoo]"
  [{path :path :as options} kw-path]
  (let [path                   (pathify path)
        [x & xs :as unchanged] (pathify kw-path)
        result (->> (condp #(%2 %1) x
                     #{:?+ :+?} [:? path xs]
                     #{:?- :-?} [:? (butlast path) xs]

                     #{:+}      [path xs]
                     #{:-}      [(butlast path) xs]
                     unchanged)
                    flatten
                    (map keyword))]
    result))

(defn- path-truthy? [{state :state :as options} kw-path]
  (let [[x & k :as path] (resolve-path options kw-path)]
    (truthy? (case x
               :_meta (react-meta options k)
               :*ref  (react k (:references options))
              :?      (has-path? k state)
              (react path state)))))

(defn- eval-state-condition [options condition]
  (cond
    (nil? condition) nil

    (keyword? condition)
    (path-truthy? options condition)

    (sequential? condition)
    (loop [op       (first condition)
           [x & xs] (rest condition)]
      (if (nil? x)
        ;; Every item must have been true for :AND
        (= op :AND)
        (let [flag (eval-state-condition options x)]
          (cond
            (and flag (= op :OR))        true
            (and (not flag) (= op :AND)) false
            :else (recur op xs)))))))

(defn- good? [options good-condition bad-condition]
  (let [good (eval-state-condition options good-condition)
        bad  (eval-state-condition options bad-condition)]
    (cond
      (and (nil? good)
           (nil? bad))   true
      (or bad
          (false? good)) false
      :else              true)))

(defn- climb-to-condition [options schema-key]
            (loop [{:keys [schema _parent]} options]
              (if-let [condition (schema-key schema)]
                condition
                (when _parent
                  (recur _parent)))))
(defn enabled?
  "Enable disable climbs schema tree until conditions are found. Thus,
  if parent is disabled, the children are also automatically
  disabled."
  [options]
  (and (react-meta options :enabled?)
       (good? options
              (climb-to-condition options :enabled?)
              (climb-to-condition options :disabled?))))

(defn disabled? [options] (not (enabled? options)))

(defn visible?
  "Similar to enabled? but for show? and hide? properties."
  [{schema :schema :as options}]
  (good? options
         (climb-to-condition options :show?)
         (climb-to-condition options :hide?)))

(defn not-visible? [options] (not (visible? options)))
