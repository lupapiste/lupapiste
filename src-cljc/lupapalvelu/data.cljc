(ns lupapalvelu.data
  "Convenience mechanisms for app-db handling."
  (:require [sade.util :as util]))

(defn pathify
  "Transforms `xs` into keyword vector. `xs` is flattened and items that cannot be casted to
  keywords are ignored. Returns nil for emptyish `xs`."
  [& xs]
  (some->> xs flatten (keep util/make-kw) seq vec))

(defn root?
  "True, if `m` has `::root` key. This is useful when determining, whether `m` is app-db."
  [m]
  (contains? m ::root))

(defn datapath
  "Value path (keyword vector). The path always starts with `[::root datakey]`. For
  example, `(datapath \"hello\")` -> `[::root datakey :hello]`."
  [datakey & xs]
  {:pre [(keyword? datakey)]}
  (if-let [xs (pathify xs)]
    (concat [::root datakey] xs)
    [::root datakey]))

(defn set-d
  "Sets value into app-db `datapath`. The last `xs` item is the value."
  [datakey db & xs]
  (assoc-in db (datapath datakey (butlast xs)) (last xs)))

(defn get-d
  "Gets value from `db` with `datapath`."
  [datakey db & xs]
  (get-in db (datapath datakey xs)))

(defn unset-d
  "Dissocs `xs` from `db` via `datapath`."
  [datakey db & xs]
  (util/dissoc-in db (datapath datakey xs)))

(defn ctx-d
  "Gets value for path `xs` from context `ctx` (typically either app-db or its
  submap). Calls `get-d` if `ctx` is app-db (determined by `root?`), otherwise `xs` is
  interpereted as a (keyword vector) path to `ctx`."
  [datakey ctx & xs]
  (if (root? ctx)
    (apply get-d datakey ctx xs)
    (get-in ctx (pathify xs))))

(defn clear
  "Dissocs `::root` key from `db`."
  [db]
  (dissoc db ::root))
