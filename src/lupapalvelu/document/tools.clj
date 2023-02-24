(ns lupapalvelu.document.tools
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [clojure.zip :as zip]
            [lupapalvelu.backing-system.krysp.kuntagml-yht-version :as yht]
            [sade.strings :as ss]
            [sade.util :as util :refer [fn->>]]
            [schema.core :as sc]))

(defn by-id
  "Return item from application collection by id.
  Nil if not found."
  [application collection id]
  (let [docs ((keyword collection) application)]
    (some #(when (= (:id %) id) %) docs)))

(defn body
  "Shallow merges stuff into vector"
  [& rest]
  (reduce
    (fn [a x]
      (let [v (if (sequential? x)
                x
                (vector x))]
        (concat a v)))
    [] rest))

(defn build-body
  "build-body extend body lupapalvelu.document.tools/body function. It allows you to pass a vector of modifiction
  functions that are applied recursively to the body.

  Note: body is always converted to a vector so that modification functions do not need to handle lazy sequence, and
  hence, modification functions can be simpler."
  [with-modification-fns & body-content]
  (reduce (fn [acc mod-fn] (into [] (mod-fn acc)))
          (into [] (apply body body-content))
          with-modification-fns))

(defn ->select-options
  "Generates options for select component from list of strings"
  [values]
  (mapv (partial hash-map :name) values))

(defn no-nodes-has-visibility-condition-in-body?
  "Returns false if there is a docgen node that have :show-when or :hide-when attribute in the
   body vector, otherwise returns true"
  [body]
  (not-any? #(or (:show-when %) (:hide-when %)) body))

(defn with-tiedot-esitetty-liitteessa
  "Adds tiedot-esitetty-liitteessä and tiedot-esitetty-liitteessä-referenssi into body vector as the first fields.
  If user chooses all other field in the body are hidden.

  Note: If there are non-nil show-when or hide-when attributes, function will throw.  User of this function must
  organize docgen nodes so that are no conflicting visibility settings, because currently docgen does not support
  multiple fields in hide-when or show-when options."
  [body-content]
  {:pre [(no-nodes-has-visibility-condition-in-body? body-content)]}
  (->> body-content
       (map #(assoc % :show-when {:path   "tiedot-esitetty-liitteessa"
                                  :values #{false}}))
       (concat [{:name "tiedot-esitetty-liitteessa"
                 :type :checkbox
                 :css [:full-width-component]
                 :size :l}
                {:name "tiedot-esitetty-liitteessa-referenssi"
                 :type :string
                 :css [:full-width-component]
                 :size :l
                 :show-when {:path "tiedot-esitetty-liitteessa" :values #{true}}}])))

(defn nil-values [_] nil)

(defn type-verifier [{:keys [type] :as element}]
  (when-not (keyword? type) (throw (RuntimeException. (str "Invalid type: " element)))))

(defn missing [element]
  (throw (UnsupportedOperationException. (str element))))

(defn default-values [{:keys [type default]}]
  (case (keyword type)
    :radioGroup       default
    :select           default
    :checkbox         (or default false)
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

(defn dummy-values [user-id {:keys [type subtype case body dummy-test max-len] :as element}]
  (condp = (keyword dummy-test)
    :postal-code "12345"
    (condp = (keyword type)
      :text                    "text"
      :checkbox                true
      :date                    "2.5.1974"
      :msDate                  136700000000
      :time                    "16:10"
      :select                  (-> body first :name)
      :radioGroup              (-> body first :name)
      :personSelector          user-id
      :companySelector         nil
      :buildingSelector        "001"
      :linkPermitSelector      "tunnus123"
      :hetu                    "210281-9988"
      :fillMyInfoButton        nil
      :foremanHistory          nil
      :maaraalaTunnus          nil
      :calculation             nil
      :string                  (condp = (keyword subtype)
                                 :maaraala-tunnus  "0003"
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
      :fundingSelector         true
      :review-officer-dropdown "Rake Reviewer"
      :allu-application-kind   "bridge-banner"
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
       (if (:pseudo? %)
         {}
         (let [t (keyword (:type %))
               v (if (#{:group :table :foremanOtherApplications} t) (group % t) (f %))]
           {(keyword (:name %)) v}))
       %)
    body)))

;;
;; Public api
;;

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

(defn wrapped
  "Wraps leaf values in a map and under k key, key defaults to :value.
   Assumes that every key in the original map is a keyword.
   If a node is a map with the keyword :_atomic-map? the node is treated as a leaf."
  ([m] (wrapped m :value))
  ([m k]
    (walk/postwalk
      (fn [x] (if-not (or (keyword? x) (coll? x))
                {k x}
                (if (and (map? x) (contains? x :_atomic-map?))
                  {k (unwrapped x k)}
                  x)))
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
      (not= :select (:type node))
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

(defn doc-type [doc]
  (keyword (get-in doc [:schema-info :type])))

(defn party-document? [doc]
  (= :party (doc-type doc)))

(defn doc-subtype [doc]
  (keyword (get-in doc [:schema-info :subtype])))

(defn doc-name [doc]
  (get-in doc [:schema-info :name]))

(defn- party-doc-id [paths doc]
  (some (fn->> (cons :data) (get-in doc) :value) paths))

(def party-doc-user-id-paths
  [[:henkilo :userId]
   [:userId]])

(def party-doc-user-id
  (partial party-doc-id party-doc-user-id-paths))

(def party-doc-company-id-paths
  [[:yritys :companyId]])

(def party-doc-company-id
  (partial party-doc-id party-doc-company-id-paths))

(defn party-doc-selected-id
  "Returns either the user or company id, depending on which one is selected"
  [doc]
  (when-let [id-extractor (-> doc :data :_selected :value
                              {"henkilo" party-doc-user-id
                               "yritys"  party-doc-company-id})]
    (id-extractor doc)))

(defn party-doc->user-role [doc]
  (cond
    (#{:hakija :hakijan-asiamies :maksaja} (doc-subtype doc)) (doc-subtype doc)
    (#{"tyonjohtaja" "tyonjohtaja-v2"}     (doc-name doc))    :tyonjohtaja
    (= :party (doc-type doc))                                 (keyword (doc-name doc))))


(def document-ordering-fn (fn [document] (get-in document [:schema-info :order] 7)))

(defn- path-string->absolute-path [elem-path path-string]
  (->> (ss/split path-string #"/")
       (#(if (ss/blank? (first %)) (rest %) (concat elem-path %)))
       (mapv keyword)))

(defn get-value-by-path
  "Get element value by target-path string. path is a list of
  keywords. target-path can be absolute (/path/to/element) or
  relative (sibling/element). If target path is given as relative,
  path is used to resolve absolute _sibling_ path. Path resolution
  examples:

  path           target-path         result-path
  -----------------------------------------------------------
  [:hello]       'world'             [:world :value]
  [:foo :bar]    'one/two'           [:foo :one :two :value]
  [:foo :bar]    '/one/two'          [:one :two :value]
  nil            '/one/two'          [:one :two :value]
  [:foo :bar]    nil                 [:value]
  [:foo :bar]    ''                  [:value]
  "
  [doc path target-path]
  (get-in doc (conj (path-string->absolute-path (butlast path) target-path) :value)))

(def SchemaFlag (sc/enum :rakennusluokka ; Rakennusluokat enabled and KuntaGML version high enough
                         :rakval-223     ; R KuntaGML 2.2.3 or higher
                         :rakval-224     ; R KuntaGML 2.2.4 or higher
                         :yht-219        ; Yhteiset KuntaGML 2.1.9 or higher
                         ))

(defn- match-version?
  "True if `candidate` version string either matches or is higher than `target`."
  [target candidate]
  (<= (compare target candidate) 0))

(sc/defn ^:always-validate resolve-schema-flags :- #{SchemaFlag}
  "Flags that are active in the current context. Since the flags can be referenced in the docgen schemas with
  `:schema-include` and `:schema-exclude` keys, this facilitates conditional handling (rendering, validation,
  processing, ...)."
  [{:keys [application organization]}]
  (let [{:keys [rakennusluokat-enabled
                krysp]} (force organization)
        permit-type     (some-> application :permitType util/make-kw
                                ;; ARK -> R, due to digitized applications
                                (util/pcond-> #{:ARK} ((constantly :R))))
        version         (get-in krysp [permit-type :version])
        rakval          (when (= permit-type :R) version)
        rakval-224      (match-version? "2.2.4" rakval)]
    (-> (util/assoc-when {}
                         :rakennusluokka (and rakval-224 rakennusluokat-enabled)
                         :rakval-223   (match-version? "2.2.3" rakval)
                         :rakval-224   rakval-224
                         :yht-219 (when (and permit-type version)
                                    (match-version? "2.1.9" (yht/get-yht-version permit-type
                                                                                 version))))
        keys
        set)))

(defn include-schema?
  "True if the given `schema` should be 'included' (not ignored) according to its schema
  conditionals. See `resolve-schema-flags` for details."
  [flags schema]
  (let [flags   (set flags) ; Just in case
        exclude (:schema-exclude schema)
        include (:schema-include schema)]
    (boolean (and (or (nil? exclude)
                      (not (flags exclude)))
                  (or (nil? include)
                      (flags include))))))

(def exclude-schema? (complement include-schema?))

(defn strip-exclusions
  "Removes from `schema` all the elements that should not be included according their
  `:schema-include` and `:schema-exclude` definitions."
  [command schema]
  (let [flags (resolve-schema-flags command)]
    (walk/postwalk (fn [form]
                     (let [form (when (include-schema? flags form)
                                     form)]
                       (util/pcond-> form
                         :body (update :body (partial remove nil?)))))
                   schema)))

(defn alter-schema
  "Returns a new schema with the given alterations

  Each alteration is in the form `[\"name of field\" :key(s) val(s)]` and
  handled as if by repeated use of assoc."
  [schema & alterations]
  (if-not (seq alterations)
    schema
    (let [alter-field  (fn [field [field-name k v & kvs]]
                         (if (= (:name field) field-name)
                           (apply assoc field k v kvs)
                           field))
          alter-fields (fn [field]
                         (if (map? field)
                           (reduce alter-field field alterations)
                           field))]
      (walk/postwalk alter-fields schema))))
