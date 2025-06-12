(ns ryhti-openapi-parser
  "Parse Ryhti OpenAPI specification and write out Clojure namespace
  with Malli schemas from the JSON-Schemas used in the spec."
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [clojure.java.io :as io]
            [fipp.clojure :as fclj]
            [fipp.edn :as fedn]
            [fipp.engine :as fengine]
            [jsonista.core :as json]
            [lupapalvelu.ryhti.api.malli-schema :as malli-schema]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [malli.core :as m]
            [sade.date :as date]
            [sade.strings :as str]))

(set! *warn-on-reflection* true)

(def om (json/object-mapper {:decode-key-fn true}))

(defn read-openapi-spec
  "Load OpenAPI spec and parse the JSON to Clojure data"
  []
  (json/read-value (java.net.URL. mappings/CURRENT-API) om))

(defn parse-ref [ref-name]
  (nth (str/split ref-name #"/") 3))

(defn get-codeset-uri [description]
  (when description
    ;; NOTE: Clojure doesn't support the named groups currently
    ;; Groups: href 4, uri 6
    (let [[_ _1 _2 _3 href _5 uri] (re-matches #".*Käytetään koodisto[an]( (URI|code) arvoa)? (<a href=\"(?<href>.*)\">(.*)</a>|(?<uri>.*))" description)]
      (or href uri))))

(defn resolve-handler [json-schema]
  (or (when (str/starts-with? (:description json-schema) "Geometriatyyppi.")
        "geom")
      (when (get-codeset-uri (:description json-schema))
        "codeset")
      (:type json-schema)
      (when (:allOf json-schema)
        "allOf")
      (when (:oneOf json-schema)
        "oneOf")
      (when (:$ref json-schema)
        "$ref")))

(defn update-properties
  "Like malli.util/update-properties, but works on Clojure data,
  and doesn't validate schema to be a real schema value.

  E.g. works for cases where a schema is a symbol/code."
  [schema f & args]
  (if (list? schema)
    ;; Presume a list is a (into schema ...) call
    (let [updated-schema (apply update-properties (nth schema 1) f args)]
      (apply list (nth schema 0) updated-schema (drop 2 schema)))
    (let [;; Wrap schema into vector
          schema (if (vector? schema)
                   schema
                   [schema])
          ;; Ensure second item of the vector is the properties map
          schema (if (map? (nth schema 1 nil))
                   schema
                   (into [(first schema) {}] (rest schema)))]
      ;; Update properties map
      (apply update schema 1 f args))))

(defmulti ->malli-schema resolve-handler)

(defn ->malli
  "Handle generic properties"
  [json-schema]
  (let [desc (:description json-schema)
        ;; Add description, unless already exists
        schema (cond-> (->malli-schema json-schema)
                 (seq desc) (update-properties (fn [props]
                                                 ;; Add description if the properties map doesn't have any value for the property.
                                                 ;; This way :description nil can be added to skip the default.
                                                 (if (contains? props :description)
                                                   props
                                                   (assoc props :description desc)))))]
    (if (:nullable json-schema)
      [:maybe schema]
      schema)))

(defmethod ->malli-schema "object" [json-schema]
  (let [required-keys (set (map keyword (:required json-schema)))
        open? (boolean (:additionalProperties json-schema))]
    (into [:map
           {:closed (not open?)}]
          (map (fn [[k json-schema]]
                 (let [key-props (if (required-keys k)
                                   nil
                                   {:optional true})
                       key-schema (->malli json-schema)]
                   (if key-props
                     [k key-props key-schema]
                     [k key-schema])))
               (:properties json-schema)))))

(defmethod ->malli-schema "integer" [json-schema]
  (case (:format json-schema)
    "int32" :int))

(defmethod ->malli-schema "number" [json-schema]
  (case (:format json-schema)
    "double" :double))

(defmethod ->malli-schema "string" [json-schema]
  ;; TODO: format: uuid, date, uri
  (if (:enum json-schema)
    (into [:enum] (:enum json-schema))
    :string))

(defmethod ->malli-schema "boolean" [_] :boolean)

(defmethod ->malli-schema "array" [json-schema]
  [:vector (->malli (:items json-schema))])

(defn missing [json-schema extra]
  (println "Missing:" (resolve-handler json-schema) extra)
  [:any
   {:missing__json-schema json-schema}])

(defmethod ->malli-schema "allOf" [json-schema]
  (if (seq (rest (:allOf json-schema)))
    (missing json-schema "many allOf children")
    (->malli (first (:allOf json-schema)))))

(defmethod ->malli-schema "oneOf" [json-schema]
  (into [:or] (map ->malli (:oneOf json-schema))))

(defmethod ->malli-schema "$ref" [json-schema]
  [:ref (keyword "ryhti" (parse-ref (:$ref json-schema)))])

(defmethod ->malli-schema "geom" [json-schema]
  (let [[_ required-value] (re-matches #"Geometriatyyppi. Pakollinen arvo: \"(.*)\"" (:description json-schema))]
    [:enum required-value]))

(defmethod ->malli-schema "codeset" [json-schema]
  ;; Codeset field can also be array type, wrap into :vector and recurse into
  ;; children type, which will call this same method again (the second if branch).
  (if (= "array" (:type json-schema))
    [:vector
     ;; The sub-schema (enum) will have the description, add empty description here.
     {:description nil}
     (->malli (merge (dissoc json-schema :type) (:items json-schema)))]
    (let [desc (:description json-schema)
          uri (get-codeset-uri desc)
          sym (get mappings/uri->mapping-name uri)]
      (if sym
        (list 'into [:enum] (list 'sort (list 'vals (symbol "mappings" (name sym)))))
        ;; If no ryhti-mappings defined yet, use JSON schema string enum property if available
        (case uri
          "http://uri.suomi.fi/codelist/interoperabilityplatform/languagecodes" (list 'into [:enum] 'mappings/main-language-codes)
          (if (:enum json-schema)
            (into [:enum] (:enum json-schema))
            [:any
             {:description (str "Missing codeset: " desc)}]))))))

(defmethod ->malli-schema :default [json-schema]
  (missing json-schema "default handler"))

(defn create-schemas
  "Parse the OpenAPI spec into data which can be used to write schema namespace."
  []
  (let [openapi (read-openapi-spec)
        schemas (-> openapi :components :schemas)]
    ;; Consider also adding note about which routes use the schema
    (->> (for [[schema-name json-schema] schemas]
           {:schema-name (name schema-name)
            :json-schema json-schema
            :malli-schema (->malli json-schema)})
         (sort-by :name))))

(defn pretty [x]
  (fedn/pretty x {:symbols fclj/default-symbols}))

(defn create-document [schemas]
  [:group
   (pretty (list 'ns 'lupapalvelu.ryhti.api.malli-schema.generated
                 (format "Automatically generated on %s, do not modify manually"
                         (date/iso-datetime (date/now)))
                 (list :require '[lupapalvelu.ryhti.api.mappings :as mappings])))
   [:break]
   [:break]

   (into [:group]
         (map (fn [{:keys [schema-name malli-schema]}]
                (let [schema-sym (symbol schema-name)]
                  [:group
                   (pretty (list 'def schema-sym malli-schema))
                   [:break]
                   [:break]]))
              schemas))

   (pretty (list 'def 'registry (reduce (fn [acc {:keys [schema-name]}]
                                          (assoc acc (keyword "ryhti" schema-name) (symbol schema-name)))
                                        {}
                                        schemas)))])

(defn write-schemas
  "Write schema data into a namespace."
  []
  (let [out-file (io/file "src" "lupapalvelu" "ryhti" "api" "malli_schema" "generated.clj")
        schemas (create-schemas)
        doc (create-document schemas)]
    (io/make-parents out-file)
    (spit out-file (with-out-str (fengine/pprint-document doc)))))

;; NOTE: Do we want to filter away some unused schemas?
;; For example:
;; ConstructionAction vs GeneralizedConstructionAction
;; Apartment vs ChangeInformationApartment

(comment
  (create-schemas)
  (write-schemas)
  )

(defn find-description
  "Search for first sub-schema with description.

  E.g. if the schema with :description is wrapped inside [:maybe]"
  [s]
  (or (:description (m/properties s))
      (let [c (first (m/children s))]
        (when (m/schema? c)
          (find-description c)))))

(defn datamodel-stub
  "Take Malli schema from the generated schemas, and use the schema to create
  datamodel mapping value with placeholder fields for the schema.

  Prints the value to *out*, copy it the the correct file."
  [schema-name]
  (let [map-schema (-> [:schema {:registry malli-schema/registry}
                        [:ref (keyword "ryhti" schema-name)]]
                       ;; :schema is used to declare the used registry, need to get the RefSchema
                       m/children
                       first
                       ;; resolve RefSchema to the MapSchema
                       m/deref)]
    (fengine/pprint-document
      [:group
       "("
       (list "def "
             (str (->kebab-case schema-name)
                  "-mapping")
             :line
             [:nest 2
              (when-let [d (:description (m/properties map-schema))]
                [:text ";; " d])
              :line

              [:group
               "["
               [:align
                (for [[k _properties key-schema]
                      (->> (m/children map-schema)
                           (sort-by first))
                      :let [desc (find-description key-schema)]]
                  [:span
                   [:group
                    "{"
                    [:align
                     [:span ":ryhti        " (pretty [k])] :line
                     [:span ":lupapiste    nil"] :line
                     [:span ":->ryhti      (fn [_v _context] nil)"] :line
                     [:span ":description  " (pretty (or desc ""))]
                     ]
                    "}"]
                   [:line]
                   [:line]])]]
              "]"])
       ")"])))


(comment
  (datamodel-stub "Elevator"))
