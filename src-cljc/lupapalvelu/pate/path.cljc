(ns lupapalvelu.pate.path
  "Various utilities for component state, path, schema and _meta
  handling."
  (:refer-clojure :exclude [extend])
  (:require [clojure.string :as s]
            [lupapalvelu.pate.schema-util :as schema-util]
            #?(:cljs [lupapalvelu.ui.common :as l10n]
               :clj  [lupapalvelu.i18n :as l10n])
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
  loc-prefix or id"
  [loc-path {:keys [id loc-prefix]}]
  (cond
    loc-prefix [loc-prefix]
    id         (extend loc-path id)
    :else      loc-path))

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
  (let [{dict-schema :schema, dict-path :path} (schema-util/dict-resolve (concat path [dict]) dictionary)]
    (assoc (schema-options options dict-schema )
           :path (extend path dict dict-path))))

(defn value
  "Value of the substate denoted by path (and extras)."
  [path state* & extra]
  (get-in @state* (extend path extra)))

(defn react
  "Like value but uses rum/react for deref."
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
       l10n/loc))

;; Component-aware localization: i18nkey, loc-prefix and dict override
;; the loc-path.
(defmethod loc :map
  [{:keys [i18nkey loc-path schema]} & extra]
  (loc (concat [(or i18nkey
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

(defn schema-css
  "List of CSS class based on :css property."
  [schema & other-classes]
  (->> [(:css schema)
        other-classes]
       flatten
       (mapcat util/split-kw-path)
       (remove nil?)
       (map name)))

(defn css
  "List of CSS classes based on current :css value and status
  classes (pate--edit, pate--view) from the latest _meta."
  [options & other-classes]
  (schema-css (:schema options)
              (if (meta-value options :editing?) "pate--edit" "pate--view")
              other-classes))

(defn meta-updated
  "Calls _meta :updated if defined."
  [options & extra-args]
  (when-let [fun (access-meta options :updated)]
    (apply fun (cons options extra-args))))

(defn key-fn
  "Rum key-fn, where key is the path id."
  [{id-path :id-path}]
  (id id-path))

(defn pathify
  "Splits kw-path if necessary."
  [path]
  (if (keyword? path)
    (util/split-kw-path path)
    path))

(defn- inclusion-status
  "Tri-state inclusion status: true, false or nil.
  Every other path part is considered repeating id and ignored."
  [path inclusions]
  (when (seq inclusions)
    (let [kws (loop [[x & xs] path
                       result   []]
                  ;; Every other part is id
                  (if x
                    (recur (drop 1 xs) (conj result x))
                    result))]
      (boolean (some (fn [kw]
                       (= kws (take (count kws)
                                    (util/split-kw-path kw))))
                     inclusions)))))

(defn- has-path?
  "If inclusions are available the result is determined by
  inclusion-status: true, if the path is valid for the current schema
  with inclusions. If inclusions is empty/nil the result is true if
  the path value in the state is not nil. Note: this is not reactive,
  since the idea is that the state 'structure' is not dynamic."
  [path state inclusions]
  (let [inc-status (inclusion-status path inclusions)]
    (if (boolean? inc-status)
      inc-status
      (-> (value path state) nil? not))))

(defn- truthy? [v]
  (boolean (cond
             (map? v)        (not-empty v)
             (sequential? v) (seq v)
             :else           v)))

(defn- resolve-path
  "Resolve relative path. Relative path is denoted with + or -. The
  former refers to child path and the latter to sibling: Meta paths
  cannot have +/-.

  Examples (current path is [:foo :bar])

  :?+.hii.hoo -> [:? :foo :bar :hii :hoo]
  :-.hii.hoo  -> [:foo :hii :hoo]
  :-?.hii.hoo -> [? :foo :hii :hoo]"
  [{:keys [path]} kw-path]
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

(defn- parse-path-condition
  "The condition kw-path has the following format [optional]:

  <path>      -> <path-part>[<op><expected>]
  <path-part> -> string
  <op>        -> != | =
  <expected>  -> string

  Examples:

  :?.hii.hoo
  :hii.hoo.foo=9
  :_meta.foo.bar!=hello

  Returns map with two keys:
   :path path-part as kw-path
   :fun? Resolution function that takes path value and compares it to
         the expected."
  [kw-path]
  (let [[_ path op expected] (re-find #"([^!=]+)(?:(!?=)([^\s]+))?"
                                      (name kw-path))]
    {:path (keyword path)
     :fun? (if op
             (fn [value]
               ((case op "=" = "!=" not=) expected (str value)))
             truthy?)}))

(defn- path-truthy? [{:keys [state info] :as options} kw-path]
  (let [{:keys [path fun?]} (parse-path-condition kw-path)
        [x & k :as path] (resolve-path options path)]
    (fun? (case x
            :_meta (react-meta options k)
            :*ref  (let [r (react k (:references options))]
                     (cond->> r
                       (sequential? r) (remove :deleted)))
            :?      (has-path? k state (when info
                                         (:inclusions @info)))
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
            (and (not flag) (= op :NOT)) true
            :else (recur op xs)))))))

(defn- good? [options good-condition bad-condition]
  (let [good (eval-state-condition options good-condition)
        bad  (eval-state-condition options bad-condition)]
    (not (or bad (false? good)))))

(defn- climb-to-condition [options schema-key]
            (loop [{:keys [schema _parent]} options]
              (if-let [condition (schema-key schema)]
                condition
                (when _parent
                  (recur _parent)))))
(defn enabled?
  "Enable disable climbs schema tree until conditions are found. Thus,
  if parent is disabled, the children are also automatically
  disabled. Note that the conditions are are still resolved according
  to the 'current' path. In other words, the relative paths for
  ancestors do not resolve correctly."
  [options]
  (and (react-meta options :enabled?)
       (good? options
              (climb-to-condition options :enabled?)
              (climb-to-condition options :disabled?))))

(defn disabled? [options] (not (enabled? options)))

(defn visible?
  "Similar to enabled? but for show? and hide? properties."
  [options]
  (good? options
         (climb-to-condition options :show?)
         (climb-to-condition options :hide?)))

(defn not-visible? [options] (not (visible? options)))

(defn item-visible?
  "Layout item (grid cell or list item) visibility is determined both
  by explicit :show?/:hide? definitions and whether the item is
  included in inclusions."
  [{:keys [schema path info] :as options}]
  (let [part       (or (:dict schema) (:repeating schema))
        inc-status (when part
                     (inclusion-status (extend path part)
                                       (when info
                                         (:inclusions @info))))]
    (if (false? inc-status)
      false
      (visible? options))))

(defn required?
  "True if the parent schema has :required? flag. Why parent and not the
  schema itself? The :required? key is on the same level as the schema
  reference (in a dictionary value). If meta property
  highlight-required? is false (default true), then the result is
  always false"
  [options]
  (boolean (and (some-> options :_parent :schema :required?)
                (not (false? (meta-value options :highlight-required?))))))

(defn error? [{:keys [state path]}]
  (boolean (react (extend :_errors path) state)))
