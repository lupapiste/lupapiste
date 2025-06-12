(ns lupapalvelu.document.model
  (:require [clojure.set :refer [difference union]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.rakennusoikeus-validator]
            [lupapalvelu.document.ryhti-validator]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.subtype :as subtype]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.validator :as validator]
            [lupapalvelu.document.vrk :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.logging :refer [errorf warn warnf]]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v])
  (:import [java.net MalformedURLException URISyntaxException URL]))

(set! *warn-on-reflection* true)

;;
;; Validation:
;;

(def default-max-len 255)
;; Finnish date format d.m.yyyy, where the year must be four digits.
#_(def dd-mm-yyyy (-> (DateTimeFormatterBuilder.)
                    (.appendDayOfMonth 1)
                    (.appendLiteral ".")
                    (.appendMonthOfYear 1)
                    (.appendLiteral ".")
                    (.appendFixedDecimal (DateTimeFieldType/year) 4)
                    (.toFormatter)))

(def other-value "other")

;;
;; Field validation
;;

(defmulti validate-field (fn [application elem value] (keyword (:type elem))))

(defmethod validate-field :group [_ _ v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate-field :table [_ _ v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate-field :string [_ {:keys [max-len min-len] :as elem} v]
  (cond
    (nil? v) nil
    (not (string? v)) [:err "illegal-value:not-a-string"]
    :else (let [^String v v]
            (cond
              (> (.length ^String v) (or max-len default-max-len)) [:err "illegal-value:too-long"]
              (and
                (pos? (.length ^String v))
                (< (.length v) (or min-len 0))) [:warn "illegal-value:too-short"]
              :else (subtype/subtype-validation elem v)))))

(defmethod validate-field :url [_ _ v]
  (when (ss/not-blank? v)
    (try
      ;; NOTE: URL part makes protocol/scheme required
      (.toURI (URL. v))
      nil
      (catch MalformedURLException _
        [:err "illegal-value:malformed-url"])
      (catch URISyntaxException _
        [:err "illegal-value:malformed-url"]))))

(defmethod validate-field :text [_ elem v]
  (if (not (string? v))
    [:err "illegal-value:not-a-string"]
    (let [^String v v]
      (cond
        (> (.length v) (or (:max-len elem) default-max-len)) [:err "illegal-value:too-long"]
        (and
          (pos? (.length v))
          (< (.length v)
             (or (:min-len elem) 0))) [:warn "illegal-value:too-short"]))))

(defmethod validate-field :hetu [_ _ v]
  (cond
    (ss/blank? v) nil
    (re-matches v/finnish-hetu-regex v) (when-not (v/valid-hetu? v) [:err "illegal-hetu"])
    :else [:err "illegal-hetu"]))

(defmethod validate-field :checkbox [_ _ v]
  (if (not= (type v) Boolean) [:err "illegal-value:not-a-boolean"]))

(defmethod validate-field :date [_ _ v]
  (when-not (or (ss/blank? v) (date/format? :fi-date v))
    [:err "illegal-value:date"]))

(defmethod validate-field :msDate [_ _ v]
  (when (and v (not (util/->long v)))
    [:err "illegal-value:msDate-NotValidDate"]))

(defmethod validate-field :time [_ _ v]
  (when-not (ss/blank? v)
    (if-let [matches (seq (rest (re-matches util/time-pattern v)))]
      (let [h (util/->int (first matches))
            m (util/->int (second matches))]
        (when-not (and (<= 0 h 23) (<= 0 m 59)) [:warn "illegal-value:time"]))
      [:warn "illegal-value:time"])))

(defmethod validate-field :select [_ {:keys [body other-key]} v]
  (let [accepted-values (set (map :name body))
        accepted-values (if other-key (conj accepted-values other-value) accepted-values)]
    (when-not (or (ss/blank? v) (accepted-values v))
      [:warn "illegal-value:select"])))

(defmethod validate-field :autocomplete [_ {:keys [body]} v]
  (let [accepted-values (set (map :name body))]
    (when-not (or (ss/blank? v) (accepted-values v))
      [:warn "illegal-value:autocomplete"])))

(defmethod validate-field :allu-application-kind [_ _ v]
  (when-not (or (ss/blank? v) (string? v))
    [:warn "illegal-value:allu-application-kind"]))

(defmethod validate-field :radioGroup [_ {body :body} v]
  (let [accepted-values (set (map :name body))]
    (when-not (or (ss/blank? v) (accepted-values v))
      [:warn "illegal-value:select"])))

(defmethod validate-field :review-officer-dropdown [_ _ _] nil)

(defmethod validate-field :buildingSelector [_ _ v]
  (cond
    (ss/blank? v) nil
    (= other-value v) nil
    (v/rakennusnumero? v) nil
    (v/rakennustunnus? v) nil
    :else [:warn "illegal-rakennusnumero"]))

(defmethod validate-field :personSelector [application _ v]
  (let [approved-auths (remove :invite (:auth application))]
    (when-not (or (ss/blank? v)
                  (auth/has-auth? {:auth approved-auths} v)
                  (auth/has-auth-via-company? {:auth approved-auths} v))
      [:warn "application-does-not-have-given-auth"])))

(defmethod validate-field :companySelector [_ _ v]
  (when-not (or (string? v) (nil? v))
    [:err "unknown-type"]))

(defmethod validate-field :linkPermitSelector [_ _ v]
  ;; This validation is for custom value in linkPermitSelector field, which should not be restricted into any format.
  (when-not (or (string? v) (nil? v))
    [:err "unknown-type"]))

(defmethod validate-field :fillMyInfoButton [_ _ _] nil)
(defmethod validate-field :foremanHistory [_ _ _] nil)
(defmethod validate-field :foremanActions [_ _ _] nil)
(defmethod validate-field :foremanOtherApplications [_ _ _] nil)
(defmethod validate-field :fundingSelector [_ _ _] nil)
(defmethod validate-field :consent [_ _ _] nil)
(defmethod validate-field :partyAttachment [_ _ _] nil)

(defmethod validate-field :maaraalaTunnus [_ _ v]
  (cond
    (ss/blank? v) nil
    (re-matches v/maara-alatunnus-pattern v) nil
    :else [:warn "illegal-maaraala-tunnus"]))

(defmethod validate-field :calculation [_ _ _] nil)

(defmethod validate-field :ryhti-identifier [_ _ _] nil)

(defmethod validate-field :rakennuskohdeSelector [_ _ _] nil)

(def illegal-key [:err "illegal-key"])

(defmethod validate-field nil [_ _ _]
  illegal-key)

(defmethod validate-field :default [_ elem _]
  (warn "Unknown schema type: elem=[%s]" elem)
  [:err "unknown-type"])

;;
;; Element validation (:validator key in schema)
;;

(defn resolve-element-loc-key [info element path]
  (or (:i18nkey element)
      (-> (str (ss/join "." (cons (-> info :document :locKey) (map name path)))
               (when (= :select (:type element)) "._group_label"))
          (ss/replace #"\.+\d+\." ".") ;; removes numbers in the middle:  "a.1.b" => "a.b"
          (ss/replace #"\.+" ".")))) ;; removes multiple dots: "a..b" => "a.b"

(declare find-by-name)

(defn- use-old-zip-validator?
  "From the 9.12.2025 there will be a new postal code validator that checks against postal codes
  from koodisto.suomi.fi. But there are a lot of older postal codes in the db that are
  invalid with this new validator."
  [modified-ts]
  (let [new-postal-code-validator-in-prod 1765231200000]
    (and modified-ts
         (< modified-ts new-postal-code-validator-in-prod))))

(defn good-postal-code?
  "Empty postal code is always valid. The idea here is to avoid
false negatives and this should be a safe assumption since the
required fields are enforced on the schema level."
  [postal-code modified-ts country]
  (cond
    (not= country "FIN")                 true
    (ss/blank? postal-code)              true
    (use-old-zip-validator? modified-ts) (v/five-digit-zip? postal-code)
    :else                                (v/finnish-zip? postal-code)))

(defmulti validate-element
  "Validates a single document element (a group of fields, e.g. a table)
  Check validate-field above for validation of single inputs
  The arguments are:
  info      a map with the schema and body of the whole document
  data      a map containing the values of the this element
  path      the full path to the element inside the document
  element   the schema for the element itself (or the table if element is inside a row"
  (fn [_ _ _ element _application]
    (:validator element)))

(defmethod validate-element :default [& _] nil)

(defn validate-postal-code-element
  "Shared logic for address and building-address postal code validation.

  The result warning includes :sanitize, indicating it will be removed during document sanitization.
  See also:
    - [[lupapalvelu.prev-permit/sanitize-document]]
    - [[lupapalvelu.prev-permit/sanitize-updates]]"
  [info data path element maa]
  (let [postinumero-modified                (some-> data :postinumero :modified)
        {:keys [postinumero] :as unwrapped} (tools/unwrapped data)
        country                             (or maa (:maa unwrapped))]
    (when-not (good-postal-code? postinumero postinumero-modified country)
      {:path     (-> (map keyword path) (concat [:postinumero]))
       :element  (assoc (find-by-name (:body element) [:postinumero]) :locKey "postinumero")
       :document (:document info)
       :result   [:warn "bad-postal-code" :sanitize]})))

(defmethod validate-element :address
  [info data path element _]
  (validate-postal-code-element info data path element nil))

(defmethod validate-element :building-address
  [info data path element _]
  (validate-postal-code-element info data path element "FIN"))

(defmethod validate-element :some-checked
  [info data path element _]
  (let [checkboxes (->> (:body element)
                        (filter (comp #{:checkbox} :type))
                        (map :name)
                        (map keyword))]
    (when-not (some (tools/unwrapped data) checkboxes)
      {:path     (mapv keyword path)
       :element  (assoc element :locKey (resolve-element-loc-key info element path))
       :document (:document info)
       :result   [:tip "illegal-value:required"]})))

(defn repeating-group?
  "Returns true if the given data contains a repeating group
  (in MongoDB an object where each 'row' is an entry with an integer key) like
  {:0 {...}
   :1 {...}
   ...}"
  [data]
  (every? (comp number? parse-long name key) data))

(defn- zero-trim-huoneistonumero [data]
  (update data :huoneistonumero ss/remove-leading-zeros))

(defn- inspect-repeating-for-duplicate-rows [data inspected-fields]
  (when (repeating-group? data)
    (let [dummy-keyset      (zipmap inspected-fields (repeat ""))
          select-keyset     (fn [row] (some->> (select-keys (val row) inspected-fields)
                                               zero-trim-huoneistonumero
                                               (util/filter-map-by-val ss/not-blank?)
                                               not-empty
                                               (merge dummy-keyset)))
          duplicate-keysets (->> (keep select-keyset data)
                                 (frequencies)
                                 (filter (comp (partial < 1) val))
                                 (keys))]
      (when-not (empty? duplicate-keysets)
        (->> (filter (comp (set duplicate-keysets) select-keyset) data)
             (keys))))))

(defmethod validate-element :huoneistot
  [info data path element _]
  (let [data               (tools/unwrapped data)
        ;; Validation can target either all the apartments or an individual apartment
        ;; field.
        data               (if (util/=as-kw :huoneistot (last path))
                             ;; Allow duplicate values with deleted apartments that have a PHT (pysyvaHuoneistotunnus).
                             (util/filter-map-by-val (fn [{:keys [muutostapa pysyvaHuoneistotunnus]}]
                                                       (or (not= muutostapa "poisto")
                                                           (ss/blank? pysyvaHuoneistotunnus)))
                                                     data)
                             data)
        fields-to-validate [:porras :huoneistonumero :jakokirjain]
        build-row-result   (fn [ind]
                             (map #(hash-map
                                     :path (-> (map keyword path) (concat [ind %]))
                                     :element (assoc (find-by-name (:body element) [%]) :locKey (ss/join "." ["huoneistot" (name %)]))
                                     :document (:document info)
                                     :result [:warn "duplicate-apartment-data"])
                                  fields-to-validate))]
    (some->> (inspect-repeating-for-duplicate-rows data fields-to-validate)
             (mapcat build-row-result))))

(defmethod validate-element :property-list
  [info data path element _]
  ;; Validate that the property-id has the desired format and has no duplicates. Only
  ;; valid property-ids are considered as the duplicate candidates.
  (let [data        (filter (fn [[_ {:keys [kiinteistotunnus]}]]
                              (some-> (ss/blank-as-nil kiinteistotunnus)
                                      v/kiinteistotunnus?))
                            (tools/unwrapped data))
        make-result (fn [ind]
                      {:path     (-> (map keyword path) (concat [ind :kiinteistotunnus]))
                       :element  (assoc (find-by-name (:body element) [:kiinteistotunnus])
                                        :locKey "pate.property-id")
                       :document (:document info)
                       :result   [:warn "duplicate-properties"]})]
    (some->> (inspect-repeating-for-duplicate-rows data [:kiinteistotunnus])
             (map make-result))))

(defmethod validate-element :poikkeus-olemassa-olevat-rakennukset
  [info data path element _]
  (let [fields (-> data
                   tools/unwrapped
                   (select-keys [:pintaAla :kayttotarkoitusKoodi]))
        fields (filter #(ss/blank? (last %)) fields)]
    (when (= (count fields) 1)
      (let [field-key (ffirst fields)
            path      (concat (map keyword path) [field-key])
            element   (find-by-name (:body element) [field-key])]
        {:path     path
         :element  (assoc element :locKey (resolve-element-loc-key info element path))
         :document (:document info)
         :result   [:tip "illegal-value:required"]}))))

(defmethod validate-element :patevyys
  [info data path element application]
  (let [{:keys [patevyystodistusurl patevyysvoimassapvm patevyydentoteaja]} (-> data tools/unwrapped)
        doc-id (:id (:document info))

        patevyystodistusliite-found (domain-att/get-attachment-by-target application "suunnittelija-patevyystodistus" doc-id)

        patevyystodistus-defined (and (or (ss/not-blank? patevyystodistusurl)
                                          patevyystodistusliite-found)
                                      (not= schemas/patevyydentoteaja-siirtymasaannos patevyydentoteaja))

        data {:patevyydentoteaja (ss/blank? patevyydentoteaja)
              :patevyysvoimassapvm (ss/blank? patevyysvoimassapvm)}]
    (keep (fn [[field-key field-is-missing]]
            (when (and patevyystodistus-defined
                       field-is-missing)
              (let [path      (concat (map keyword path) [field-key])
                    element   (find-by-name (:body element) [field-key])]
                {:path     path
                 :element  (assoc element :locKey (resolve-element-loc-key info element path))
                 :document (:document info)
                 :result   [:warn (str "patevyys-vaaditut-kentat." (name field-key))]})))
          data)))

(defmethod validate-element :is-primary
  [info data path element _]
  ;; The validator will trigger once for the whole table and then also separately for each row.
  ;; We want the whole table, so if it is trying to validate a row (the path ends with an index number) we do nothing.
  (when-not (parse-long (peek path))
    (let [rows                  (tools/unwrapped data)
          toggled-primary-boxes (filter (fn [[_k v]] (:is-primary v)) rows)]
      (cond
        ;; If there is only 1 element we assume that it is primary.
        (and (> (count rows) 1)
             (= (count toggled-primary-boxes) 0))
        {:path     path
         :element  (assoc element :locKey (resolve-element-loc-key info element path))
         :document (:document info)
         :result   [:tip "illegal-value:required"]}

        (> (count toggled-primary-boxes) 1)
        {:path     path
         :element  (assoc element :locKey (resolve-element-loc-key info element path))
         :document (:document info)
         :result   [:warn "select-only-one-primary"]}))))

;;
;; Neue api:
;;

(defn find-by-name [schema-body [k & ks]]
  (when-let [elem (some #(when (= (:name %) (name k)) %) schema-body)]
    (if (nil? ks)
      elem
      (if (:repeating elem)
        (when (ss/numeric? (name (first ks)))
          (if (seq (rest ks))
            (find-by-name (:body elem) (rest ks))
            elem))
        (find-by-name (:body elem) ks)))))

(defn ->validation-result [info data path element result]
  (when result
    ; Return results without :data (user input).
    ; Data is handy when hacking in REPL, though.
    (let [validation-result {:path     (vec (map keyword path))
                             ;:data        data
                             :element  (merge element {:locKey (resolve-element-loc-key info element path)})
                             :document (:document info)
                             :result   result}]

      (if (= illegal-key result)
        (assoc validation-result :path []) ; Invalid path from user input should not be echoed
        validation-result))))

(defn- data-match?
  "True if show/hide-when definition resolves true in this context."
  [application doc-data doc-path {:keys [path values document]}]
  (boolean
    (and path
         values
         (contains? values
                    (if document
                      (tools/get-value-by-path (:data (domain/get-document-by-name application
                                                                                   document))
                                               nil
                                               path)
                      (tools/get-value-by-path doc-data doc-path path))))))

(defn field-visible?
  [application data path {:keys [hide-when show-when hidden]}]
  (let [hide             (and hide-when (data-match? application data path hide-when))
        show             (and show-when (data-match? application data path show-when))
        hidden-by-value? (when (= hidden :by-value)
                           (let [v (get-in data (util/conjv (map keyword path) :value))]
                             (or (false? v) (util/emptyish? v))))]
    (when-not hidden-by-value?
      (or
        (and (nil? hide) (nil? show))
        (and (not hide) (or (nil? show) show))))))

(defn- huoneisto-path
  "If `path` refers to an apartment field, returns path to apartment. Otherwise nil. For
  legacy applications the root `:huoneistot` is in the data root, but in Ryhti under
  `:rakennuskohteen-osa`."
  [path]
  (let [[a b c] (some->> path (take 3) (mapv keyword))]
    (case a
      :huoneistot          (when b
                             [a b])
      :rakennuskohteen-osa (when (and (= b :huoneistot) c)
                             [a b c])
      nil)))

(defmulti skip-validation? (fn [_application _info path _data _element]
                             (when (huoneisto-path path)
                               :huoneisto)))

(defmethod skip-validation? :huoneisto [_application _info path data _element]
  (let [muutostapa (get-in data (conj (huoneisto-path path) :muutostapa :value))]
    (or (ss/blank? muutostapa) (= muutostapa "poisto"))))

(defmethod skip-validation? :default [& _]
  false)

(defn- do-validate-fields [application doc-data info k data path]
  (let [current-path (if k (conj path (name k)) path)
        element      (if (not-empty current-path)
                       (find-by-name (:schema-body info) current-path)
                       {})
        selected     (get-in data [(keyword schemas/select-one-of-key) :value])
        ignore-path  (some->> (case (keyword selected)
                                :henkilo :yritys
                                :yritys :henkilo
                                nil)
                              (conj current-path)
                              (map keyword)
                              not-empty)
        invisible?   (and (:name element)
                          (not (field-visible? application doc-data current-path element)))
        skip?        (skip-validation? application info current-path doc-data element)
        data         (cond
                       (nil? data) nil ; E.g., conversion can have sparse data.
                       (map? data) data
                       :else ; Picks the first option in the rare case there are multiple ones (see TT-18938)
                       (do (warnf "Multiple document values found in application %s: %s %s" (:id application) current-path data)
                           (util/find-first :value data)))
        results      (cond
                       skip?
                       (some->> [(validate-element info data current-path element application)]
                                flatten
                                (filter seq))

                       (contains? data :value)
                       (let [result (validate-field application element (:value data))]
                         (->validation-result info data current-path element result))

                       :else
                       (->> data
                            (map (fn [[k2 v2]]
                                   (do-validate-fields application doc-data info k2 v2 current-path)))
                            (concat (flatten [(validate-element info data current-path element application)]))
                            (filter seq)))]

    (cond
      ignore-path
      (->> (flatten [results])
           (remove nil?)
           (map (fn [{:keys [path] :as result}]
                  (cond-> result
                    (some->> path (take (count ignore-path)) (= ignore-path))
                    (assoc :ignore true)))))

      invisible?
      (remove nil?
              (map #(if (not-empty %)
                      (assoc % :ignore true)
                      %)
                   (flatten [results])))

      :else
      results)))

(defn validate-fields [application info k data path]
  (do-validate-fields application data info k data path))

(defn- sub-schema->dict
  "Construct a simple dictionary of `schema name` -> `schema`."
  [subschemas]
  (->> subschemas
       (map (juxt :name identity))
       (into {})))

(defn- sub-schema-by-name [schema-dict name]
  (get schema-dict name))

(defn- one-of-many-options [schema-dict]
  (map :name (:body (sub-schema-by-name schema-dict schemas/select-one-of-key))))

(defn- one-of-many-selection [schema-dict path data]
  (when-let [one-of (seq (one-of-many-options schema-dict))]
    (or (get-in data (conj path :_selected :value)) (first one-of))))

(defn- with-required-other-fields [sub-schemas path data]
  (let [other-selected?     #(->> (conj path (keyword %) :value)
                                  (get-in data)
                                  (= other-value))
        required-other-keys (reduce (fn [acc {:keys [name other-key]}]
                                      (cond-> acc
                                        (and (some? other-key)
                                             (other-selected? name))
                                        (conj other-key)))
                                    #{}
                                    sub-schemas)]
    (map #(cond-> %
            (contains? required-other-keys (:name %))
            (assoc :required true))
         sub-schemas)))

(defn validate-required-fields [application info path data validation-errors]
  (let [check                   (fn [{:keys [name required body repeating dynamic] :as element}]
                                  (let [kw               (keyword name)
                                        current-path     (conj path kw)
                                        value            (get-in data (conj current-path :value))
                                        skip?            (skip-validation? application info current-path data element)
                                        visible?         (field-visible? application data current-path element)
                                        not-dynamic?     (not dynamic)
                                        required-error   (->validation-result info nil current-path element [:tip "illegal-value:required"])
                                        validation-error (when (and required
                                                                    (not skip?)
                                                                    visible?
                                                                    not-dynamic?)
                                                           (if (instance? Long value)
                                                             (when-not (some? value)
                                                               required-error)
                                                             (when (and (not (map? value)) (ss/blank? value))
                                                               required-error)))]
                                    (concat (if validation-error
                                              (conj validation-errors validation-error)
                                              validation-errors)
                                            (if (and body visible? not-dynamic?)
                                              (let [newInfo (assoc info :schema-body body)]
                                                (if repeating
                                                  (map (fn [k] (validate-required-fields application newInfo (conj current-path k) data [])) (keys (get-in data current-path)))
                                                  (validate-required-fields application newInfo current-path data [])))
                                              []))))

        schema-body             (with-required-other-fields (:schema-body info) path data)
        schema-dict             (sub-schema->dict schema-body)
        selected                (one-of-many-selection schema-dict path data)
        sub-schemas-to-validate (-> (set (map :name schema-body))
                                    (difference (set (one-of-many-options schema-dict)) #{schemas/select-one-of-key})
                                    (union (when selected #{selected})))]
    (map #(check (sub-schema-by-name schema-dict %)) sub-schemas-to-validate)))

(defn get-document-schema
  "Returns document's schema map that contains :info and :body."
  [{:keys [schema-info id]}]
  {:pre [schema-info], :post [%]}
  (if-let [schema (schemas/get-schema schema-info)]
    schema
    (errorf "Schema '%s' (version %s) not found for document %s!" (:name schema-info) (:version schema-info) id)))

(defn- validate-document [{data :data :as document} info]
  (let [doc-validation-results (validator/validate document)]
    (map
      #(let [element (find-by-name (:schema-body info) (:path %))]
         (->validation-result info data (:path %) element (:result %)))
      doc-validation-results)))

(defn document-info
  "Creates an info map for validators"
  [document schema]
  {:document    {:id     (:id document)
                 :name   (-> schema :info :name)
                 :locKey (or (-> schema :info :i18name) (-> schema :info :name))
                 :type   (-> schema :info :type)}
   :schema-body (:body schema)})

(defn validate
  "Validates document against schema and document level rules. Returns list of validation errors.
   If schema is not given, uses schema defined in document."
  ([application document]
   (validate application document nil))
  ([application document schema]
   {:pre [(map? application) (map? document)]}
   (let [data   (:data document)
         schema (or schema (get-document-schema document))
         info   (document-info document schema)]
     (when data
       (flatten
         (concat
           (validate-fields application info nil data [])
           (validate-required-fields application info [] data [])
           (validate-document document info)))))))

(defn validate-pertinent
  "Like validate but weeds out the results with `:ignore` flag and illegal-key results. The
  latter can be caused by schema flags, for example. Also, disabled documents and sent
  reviews are always valid."
  [command document]
  (when-not (or (:disabled document)
                (and (some-> document :schema-info :subtype (util/=as-kw :review))
                     (util/=as-kw (:state document) :sent)))
    (->> (get-document-schema document)
         (tools/strip-exclusions command)
         (validate (:application command) document)
         (remove (fn [{:keys [ignore result]}]
                   (or ignore (= result illegal-key))))
         (mapv (fn [result]
                 (update result :path (partial mapv keyword))))
         (into []))))

(defn has-errors?
  [results]
  (->>
    results
    (map :result)
    (map first)
    (some (partial = :err))
    true?))

(defn is-relevant-value [value]
  (not (contains? #{nil false ""} value)))

;;
;; Updates
;;

(def ^:dynamic *timestamp* nil)
(defn current-timestamp
  "Returns the current timestamp to be used in document modifications."
  [] *timestamp*)

(defmacro with-timestamp [timestamp & body]
  `(binding [*timestamp* ~timestamp]
     ~@body))

(declare apply-updates)

(defn map2updates
  "Creates model-updates from map into path."
  [path m]
  (map (fn [[p v]] [(into path p) v]) (tools/path-vals m)))

(defn apply-update
  "Updates a document returning the modified document.
   Value defaults to \"\", e.g. unsetting the value.
   To be used within with-timestamp.
   Example: (apply-update document [:mitat :koko] 12)

   If you want to update a map as a value instead of routing apply-updates through this function,
   add {:_atomic-map? true} to your map so it won't get taken apart."
  ([document path]
   (apply-update document path ""))
  ([document path value]
   (if (and (map? value) (not (:_atomic-map? value)))
     (apply-updates document (map2updates path value))
     (let [data-path (vec (flatten [:data path]))]
       (-> document
           (assoc-in (conj data-path :value) value)
           (assoc-in (conj data-path :modified) (current-timestamp)))))))

(defn apply-updates
  "Updates a document returning the modified document.
   To be used within with-timestamp.
   Example: (apply-updates document [[:mitat :koko] 12])"
  [document updates]
  (reduce (fn [document [path value]] (apply-update document path value)) document updates))

;;
;; Approvals
;;

(defn modifications-since-approvals
  ([{:keys [schema-info data meta]}]
   (let [schema    (and schema-info (schemas/get-schema (:version schema-info) (:name schema-info)))
         timestamp (max (get-in meta [:_approved :timestamp] 0) (get-in meta [:_indicator_reset :timestamp] 0))]
     (modifications-since-approvals (:body schema) [] data meta (get-in schema [:info :approvable]) timestamp)))
  ([schema-body path data meta approvable-parent timestamp]
   (letfn [(max-timestamp [p] (max timestamp (get-in meta (concat p [:_approved :timestamp]) 0)))
           (count-mods
             [{:keys [name approvable repeating body type]}]
             (let [current-path       (conj path (keyword name))
                   current-approvable (or approvable-parent approvable)]
               (if (or (= :group type) (= :table type))
                 (if repeating
                   (reduce + 0 (map (fn [k]
                                      (modifications-since-approvals body (conj current-path k) data meta current-approvable (max-timestamp (conj current-path k))))
                                    (keys (get-in data current-path))))
                   (modifications-since-approvals body current-path data meta current-approvable (max-timestamp current-path)))
                 (if (and
                       current-approvable
                       (>
                         (or
                           (get-in data (conj current-path :modified))
                           0)
                         (max-timestamp current-path)))
                   1
                   0))))]
     (reduce + 0 (map count-mods schema-body)))))

(defn mark-approval-indicators-seen-update
  "Generates update map for marking document approval indicators seen. Merge into $set statement."
  [{documents :documents} timestamp]
  (mongo/generate-array-updates :documents documents (constantly true) "meta._indicator_reset.timestamp" timestamp))

;;
;; Create
;;

(def db-schema-info-keys [:name :version :type :subtype :op])

(defn ->schema-info
  "Returns given `schema-info` map with only `db-schema-info-keys` selected. Nil values are
  stripped from the result. Nil if the result would be an empty map."
  [schema-info]
  (some-> schema-info
          (select-keys db-schema-info-keys)
          util/strip-nils
          not-empty))

(def schema->schema-info
  "Returns `->schema-info` version of the given schema."
  (comp ->schema-info :info))


(defn new-document
  "Creates an empty document out of schema.
  Uses tools/default-values to create default data.
  Returns minimal keys needed in the db for :schema-info.
  Use `schemas/with-current-schema-info` to add all schema-info attributes."
  ([schema schema-flags created]
   {:id          (mongo/create-id)
    :created     created
    :schema-info (schema->schema-info schema)
    :data        (tools/create-document-data schema (partial tools/default-values schema-flags))})
  ([schema created]
   (new-document schema nil created)))

;;
;; Convert data
;;
(defn convert-document-data
  "Walks document data starting from initial-path.
   If predicate matches, value is outputted using emitter function.
   Both predicate and emitter take two parameters: element schema definition and the value map."
  [pred emitter {data :data :as document} initial-path]
  (if-not data
    document
    (letfn [(doc-walk [schema-body path]
              (into {}
                    (map
                      (fn [{:keys [name type body repeating] :as element}]
                        (let [k            (keyword name)
                              current-path (conj path k)
                              v            (get-in data current-path)]
                          (if (pred element v)
                            [k (emitter element v)]
                            (when v
                              (if (or (= (keyword type) :group) (= (keyword type) :table))
                                [k (if repeating
                                     (into {} (map (fn [i] [i (doc-walk body (conj current-path i))]) (keys v)))
                                     (doc-walk (conj body {:name :validationResult}) current-path))]
                                [k v])))))
                      schema-body)))]
      (let [path        (vec initial-path)
            schema      (get-document-schema document)
            schema-body (:body (if (seq path) (find-by-name (:body schema) path) schema))]
        (assoc-in document (concat [:data] path) (doc-walk schema-body path))))))

(defn strip-blacklisted-data
  "Strips values from document data if blacklist in schema includes given blacklist-item."
  [document blacklist-item & [initial-path]]
  (let [bl-kw    (keyword blacklist-item)
        strip-if (fn [{bl :blacklist} _] ((set (map keyword bl)) bl-kw))]
    (convert-document-data strip-if (constantly nil) document initial-path)))

(defn strip-turvakielto-data [{data :data :as document}]
  (reduce
    (fn [doc [path v]]
      (let [turvakielto-value (:value v)
            ; Strip data starting from one level up.
            ; Fragile, but currently schemas are modeled this way!
            strip-from        (butlast path)]
        (if turvakielto-value
          (strip-blacklisted-data doc schemas/turvakielto strip-from)
          doc)))
    document
    (tools/deep-find data (keyword schemas/turvakielto))))

(defn mask-person-id-ending
  "Replaces last characters of person IDs with asterisks (e.g., 010188-123A -> 010188-****)"
  [document & [initial-path]]
  (let [mask-if (fn [{type :type} {hetu :value}] (and (= (keyword type) :hetu) hetu (> (count hetu) 7)))
        do-mask (fn [_ {hetu :value :as v}] (assoc v :value (str (subs hetu 0 7) "****")))]
    (convert-document-data mask-if do-mask document initial-path)))

(defn mask-person-id-birthday
  "Replaces first characters of person IDs with asterisks (e.g., 010188-123A -> ******-123A)"
  [document & [initial-path]]
  (let [mask-if (fn [{type :type} {hetu :value}] (and (= (keyword type) :hetu) hetu (pos? (count hetu))))
        do-mask (fn [_ {hetu :value :as v}] (assoc v :value (str "******" (ss/substring hetu 6 11))))]
    (convert-document-data mask-if do-mask document initial-path)))

(defn mask-non-finnish-person-id
  "Replaces all characters of person IDs (non finnish ID) with 6 asterisks (e,g, 1234567 -> ******)"
  [document & [initial-path]]
  (let [mask-if (fn [{name :name} _]
                  (= (keyword name) :ulkomainenHenkilotunnus))
        do-mask (fn [_ v] (if (ss/blank? (:value v))
                            v
                            (assoc v :value "******")))]
    (convert-document-data mask-if do-mask document initial-path)))

(defn without-user-id
  "Removes userIds from the document."
  [doc]
  (util/postwalk-map (fn [m] (dissoc m :userId)) doc))

(defn has-hetu?
  ([schema]
   (has-hetu? schema [:henkilo]))
  ([schema-body base-path]
   (let [full-path (apply conj base-path [:henkilotiedot :hetu])]
     (boolean (find-by-name schema-body full-path)))))

(defn good-flag? [flag]
  (or (nil? flag) (boolean? flag)))

(defn ->henkilo [{:keys [id firstName lastName email phone street zip city personId non-finnish-personId turvakieltokytkin
                         companyName companyId allowDirectMarketing not-finnish-hetu
                         fise fiseKelpoisuus degree graduatingYear]} & {:keys [with-hetu with-empty-defaults?]}]
  {:pre [(good-flag? turvakieltokytkin) (good-flag? allowDirectMarketing)]}
  (letfn [(wrap [v] (if (and with-empty-defaults? (nil? v)) "" v))]
    (->
      {:userId               (wrap id)
       :henkilotiedot        {:etunimi                 (wrap firstName)
                              :sukunimi                (wrap lastName)
                              :hetu                    (wrap (when with-hetu personId))
                              :ulkomainenHenkilotunnus (when non-finnish-personId
                                                         (wrap (when with-hetu non-finnish-personId)))
                              :not-finnish-hetu        (when (some? not-finnish-hetu)
                                                         (if with-hetu
                                                           not-finnish-hetu
                                                           false))
                              :turvakieltoKytkin       (when (or turvakieltokytkin with-empty-defaults?) (boolean turvakieltokytkin))}
       :yhteystiedot         {:email   (wrap email)
                              :puhelin (wrap phone)}
       :kytkimet             {:suoramarkkinointilupa (when (or allowDirectMarketing with-empty-defaults?) (boolean allowDirectMarketing))}
       :osoite               {:katu                 (wrap street)
                              :postinumero          (wrap zip)
                              :postitoimipaikannimi (wrap city)}
       :yritys               {:yritysnimi           (wrap companyName)
                              :liikeJaYhteisoTunnus (wrap companyId)}
       :patevyys             {:koulutusvalinta  (wrap degree)
                              :koulutus         nil
                              :valmistumisvuosi (wrap graduatingYear)
                              :fise             (wrap fise)
                              :fiseKelpoisuus   (wrap fiseKelpoisuus)}
       :patevyys-tyonjohtaja {:koulutusvalinta  (wrap degree)
                              :koulutus         nil
                              :valmistumisvuosi (wrap graduatingYear)}}
      util/strip-nils
      util/strip-empty-maps
      tools/wrapped)))

(defn ->yritys [{:keys [firstName lastName email phone address1 zip po
                        turvakieltokytkin name y netbill ovt pop allowDirectMarketing
                        contactAddress contactZip contactPo]}
                & {:keys [with-empty-defaults? contact-address?]}]
  {:pre [(good-flag? turvakieltokytkin) (good-flag? allowDirectMarketing)]}
  (letfn [(wrap [v] (if (and with-empty-defaults? (nil? v)) "" v))]
    (->
      {:yritysnimi           (wrap name)
       :liikeJaYhteisoTunnus (wrap y)
       :osoite               {:katu                 (wrap (if (and contact-address? (not (empty? contactAddress))) contactAddress address1))
                              :postinumero          (wrap (if (and contact-address? (not (empty? contactZip))) contactZip zip))
                              :postitoimipaikannimi (wrap (if (and contact-address? (not (empty? contactPo))) contactPo po))}
       :yhteyshenkilo        {:henkilotiedot {:etunimi           (wrap firstName)
                                              :sukunimi          (wrap lastName)
                                              :turvakieltoKytkin (when (or turvakieltokytkin with-empty-defaults?) (boolean turvakieltokytkin))}
                              :yhteystiedot  {:email   (wrap email)
                                              :puhelin (wrap phone)}
                              :kytkimet      {:suoramarkkinointilupa (when (or allowDirectMarketing with-empty-defaults?) (boolean allowDirectMarketing))}}
       :verkkolaskutustieto  {:ovtTunnus         (wrap ovt)
                              :verkkolaskuTunnus (wrap netbill)
                              :valittajaTunnus   (wrap pop)}}
      util/strip-nils
      util/strip-empty-maps
      tools/wrapped)))
