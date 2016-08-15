(ns lupapalvelu.document.tools
  (:require [clojure.walk :as walk]
            [clojure.zip :as zip]
            [clojure.edn :as edn]
            [sade.strings :as ss]))

(defn nil-values [_] nil)

(defn type-verifier [{:keys [type] :as element}]
  (when-not (keyword? type) (throw (RuntimeException. (str "Invalid type: " element)))))

(defn missing [element]
  (throw (UnsupportedOperationException. (str element))))

(defn default-values [{:keys [type default]}]
  (case (keyword type)
    :radioGroup       default
    :select           default
    :checkbox         false
    :string           (or default "")
    :text             (or default "")
    :fillMyInfoButton nil
    :foremanHistory   nil
    nil))

(defn dummy-vrk-address
  "For some (historical?) reason the dummy value for vrk-address is
  not a valid address. This function can be overridden in midje
  tests."
  []
  "Ranta\"tie\" 66:*")

(defn dummy-values [user-id {:keys [type subtype case name body dummy-test max-len] :as element}]
  (condp = (keyword dummy-test)
    :postal-code "12345"
    (condp = (keyword type)
      :text             "text"
      :checkbox         true
      :date             "2.5.1974"
      :time             "16:10"
      :select           (-> body first :name)
      :radioGroup       (-> body first :name)
      :personSelector   user-id
      :companySelector  nil
      :buildingSelector "001"
      :newBuildingSelector "1"
      :hetu             "210281-9988"
      :fillMyInfoButton nil
      :foremanHistory   nil
      :maaraalaTunnus   nil
      :string           (condp = (keyword subtype)
                          :maaraala-tunnus   "0003"
                          :email            "example@example.com"
                          :tel              "012 123 4567"
                          :number           "4"
                          :decimal          "6,9"
                          :digit            "1"
                          :kiinteistotunnus "09100200990013"
                          :zip              "33800"
                          :vrk-address      (dummy-vrk-address)
                          :vrk-name         "Ilkka"
                          :y-tunnus         "2341528-4"
                          :rakennusnumero   "001"
                          :rakennustunnus   "1234567892"
                          :ovt              "003712345678"
                          nil               (if max-len (ss/substring "string" 0 max-len) "string")
                          :recent-year      "2010"
                          :letter           (condp = (keyword case)
                                              :lower "a"
                                              :upper "A"
                                              nil    "Z"
                                              (missing element))
                          (missing element))
      (missing element))))

;;
;; Internal
;;

(defn- ^{:testable true} flattened [col]
  (walk/postwalk
    #(if (and (sequential? %) (-> % first map?))
       (into {} %)
       %)
    col))

(defn- ^{:testable true} group [x t]
  (if (:repeating x)
    (if-not (:repeating-init-empty x)
      {:name :0
       :type t
       :body (:body x)}
      {})
    (:body x)))

(defn create-unwrapped-data [{body :body} f]
  (flattened
    (walk/prewalk
      #(if (and (map? %) (not-empty %))
         (let [t (keyword (:type %))
               v (if (#{:group :table :foremanOtherApplications} t) (group % t) (f %))]
           {(keyword (:name %)) v})
         %)
      body)))

;;
;; Public api
;;

(defn wrapped
  "Wraps leaf values in a map and under k key, key defaults to :value.
   Assumes that every key in the original map is a keyword."
  ([m] (wrapped m :value))
  ([m k]
    (walk/postwalk
      (fn [x] (if (or (keyword? x) (coll? x)) x {k x}))
      m)))

(defn unwrapped
  "(unwrapped (wrapped original)) => original"
  ([m] (unwrapped m :value))
  ([m k]
    (assert (keyword? k))
    (walk/postwalk
      (fn [x] (if (and (map? x) (contains? x k))
                (k x)
                x))
      m)))

(defn timestamped
  "Assocs timestamp besides every value-key"
  ([m timestamp] (timestamped m timestamp :value :modified))
  ([m timestamp value-key timestamp-key]
  (walk/postwalk
    (fn [x] (if (and (map? x) (contains? x value-key)) (assoc x timestamp-key timestamp) x))
    m)))

(defn create-document-data
  "Creates document data from schema using function f as input-creator. f defaults to 'nil-values'"
  ([schema]
    (create-document-data schema nil-values))
  ([schema f]
    (-> schema
      (create-unwrapped-data f)
      wrapped)))

(defn path-vals
  "Returns vector of tuples containing path vector to the value and the value."
  [m]
  (letfn
    [(pvals [l p m]
       (reduce
         (fn [l [k v]]
           (if (map? v)
             (pvals l (conj p k) v)
             (cons [(conj p k) v] l)))
         l m))]
    (pvals [] [] m)))

(defn assoc-in-path-vals
  "Re-created a map from it's path-vals extracted with (path-vals)."
  [c] (reduce (partial apply assoc-in) {} c))

(defn schema-body-without-element-by-name
  "returns a schema body with all elements with name of element-name stripped of."
  [schema-body & element-names]
  (let [names (set element-names)]
    (walk/postwalk
      (fn [form]
        (cond
          (and (map? form) (names (:name form))) nil
          (sequential? form) (->> form (filter identity) vec)
          :else form))
      schema-body)))

(defn schema-without-element-by-name
  "returns a copy of a schema with all elements with name of element-name stripped of."
  [schema element-name]
  (update-in schema [:body] schema-body-without-element-by-name element-name))

(defn deep-find
  "Finds 0..n locations in the m structured where target is found.
   Target can be a single key or any deep vector of keys.
   Returns list of vectors that first value contains key to location and second val is value found in."
  ([m target]
    (deep-find m target [] []))
  ([m target current-location result]
    (let [target (if (sequential? target) target [target])]
      (if (get-in m target)
        (conj result [current-location (get-in m target)])
        (reduce concat (for [[k v] m]
                         (when (map? v) (if-not (contains? v :value)
                                          (concat result (deep-find v target (conj current-location k) result))
                                          result))))))))

(defn get-update-item-value [updates item-name]
  {:pre [(vector? updates) (every? vector? updates) (string? item-name)]}
  (some
    (fn [[k v]]
      (when (= item-name k) v))
    updates))

(defn update-in-repeating
  ([m [k & ks] f & args]
    (if (every? (comp ss/numeric? name) (keys m))
      (apply hash-map (mapcat (fn [[repeat-k v]] [repeat-k (apply update-in-repeating v (conj ks k) f args)] ) m))
      (if ks
        (assoc m k (apply update-in-repeating (get m k) ks f args))
        (assoc m k (apply f (get m k) args))))))

(defn- schema-branch? [node]
  (or
    (seq? node)
    (and
      (map? node)
      (not (= :select (:type node)))
      (contains? node :body))))

(def schema-leaf? (complement schema-branch?))

(defn schema-zipper [doc-schema]
  (let [branch? (fn [node]
                  (and (map? node)
                       (contains? node :body)))
        children (fn [{body :body :as branch-node}]
                   (assert (map? branch-node) (str "Assertion failed in schema-zipper/children, expected node to be a map:" branch-node))
                   (assert (not (empty? body)) (str "Assertion failed in schema-zipper/children, branch node to have children:" branch-node))
                   body)
        make-node (fn [node, children]
                    (assert (map? node) (str "Assertion failed in schema-zipper/make-node, expected node to be a map:" node))
                    (assoc node :body children))]
    (zip/zipper branch? children make-node doc-schema)))

(defn- iterate-siblings-to-right [loc f]
  (if (nil? (zip/right loc))
    (-> (f loc)
        zip/up)
    (-> (f loc)
        zip/right
        (recur f))))

(defn- get-root-path [loc]
  (let [keyword-name (comp keyword :name)
        root-path (->> (zip/path loc)
                       (mapv keyword-name)
                       (filterv identity))
        node-name (-> (zip/node loc)
                      keyword-name)]
    (seq (conj root-path node-name))))

(defn- add-whitelist-property [node new-whitelist]
  (if-not (and (seq? node) (:whitelist node))
    (assoc node :whitelist new-whitelist)
    node))

(defn whitelistify-schema
  ([loc] (whitelistify-schema loc nil))
  ([loc disabled-paths]
   (if (zip/end? loc)
     disabled-paths
     (let [current-node (zip/node loc)
           current-whitelist (:whitelist current-node)
           propagate-wl? (and (schema-branch? current-node) current-whitelist)
           loc (if propagate-wl?
                 (iterate-siblings-to-right
                   (zip/down loc)                           ;leftmost-child, starting point
                   #(zip/edit % add-whitelist-property current-whitelist))
                 loc)
           whitelisted-leaf? (and
                               (schema-leaf? current-node)
                               current-whitelist)
           disabled-paths (if whitelisted-leaf?
                            (conj disabled-paths [(get-root-path loc) current-whitelist])
                            disabled-paths)]
       (recur (zip/next loc) disabled-paths)))))

(defn rows-to-list
  "Some schema types (e.g., table) model sequences as maps where keys
  are sparse indexes. This function transforms the map into list.
  {:0 {:foo 4} :2 {:foo :dii}} -> [{:foo 4} {:foo :dii}]"
  [row-map]
  (->> row-map
       keys
       (map #(-> % name edn/read-string))
       sort
       (map #(-> % str keyword row-map))))
