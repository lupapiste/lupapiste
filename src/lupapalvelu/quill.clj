(ns lupapalvelu.quill
  "Quill is a JavaScript richtext editor with its own output format (operations). The
  various aspects of Quill support are dispersed within the repository.

  Namespace lupapalvelu.quill (this namespace):

  Schemas for the supported Quill operations. Since the operation format is very
  verbose, a simple deflate/inflate mechanism is used for persisting in mongo.

  Namespace lupapalvelu.shared-quill:

  Mechanism for transforming Quill operations into ´hiccup´ tags. Also some other shared
  functionality between front- and backend.

  Namespace lupapalvelu.ui.components.quill:

  Low level ClojureScript wrapper for the React Quill component. Typically not used
  directly but via `lupapalvelu.ui.components/quill-edit` wrapper component.

  Sass file _quill.scss:

  Styles for the transformed Quill tags. Also some other stylistic tweaks. The same file
  is used in Docstore as well.

  More resources
  - https://quilljs.com/
  - https://github.com/zenoamaro/react-quill"
  (:require [lupapalvelu.shared-quill :refer [Image] :as quill]
            [sade.core :refer [fail]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.coerce :as coerce]
            [schema.core :as sc]
            [schema.utils :as scu]))

(def ^:private Color
  "When manually edited Quill uses #rrggbb format, but rgb/rgba definitions can be
  introduced via copy-paste."
  #"#[a-f0-9]{6}|rgba?\([\d\., ]+\)")
(def ^:private True  (sc/eq true))
(def ^:private Link
  "Only external http links are allowed."
  #"https?://.+")

(sc/defschema QuillAttributes
  {(sc/optional-key :header)     ssc/PosInt
   (sc/optional-key :list)       (sc/enum "bullet" "ordered")
   (sc/optional-key :bold)       True
   (sc/optional-key :italic)     True
   (sc/optional-key :strike)     True
   (sc/optional-key :underline)  True
   (sc/optional-key :code)       True
   (sc/optional-key :blockquote) True
   (sc/optional-key :script)     (sc/enum "super" "sub")
   (sc/optional-key :indent)     ssc/PosInt
   (sc/optional-key :align)      (sc/enum "center" "right" "justify")
   (sc/optional-key :color)      Color
   (sc/optional-key :background) Color
   (sc/optional-key :link)       Link})

(sc/defschema QuillInsert
  (sc/conditional
    :image {:image Image}
    :else sc/Str))

(sc/defschema QuillOp
  {:insert                       QuillInsert
   (sc/optional-key :attributes) QuillAttributes})

(def ^:private color-checker (sc/checker Color))
(def ^:private link-checker (sc/checker Link))

(defn- coerce-link [link]
  (some-> link ss/trim ss/lower-case
          (util/pcond->>
              link-checker (str "http://"))))

(def ^:private quill-coersion-matcher
  {QuillAttributes (fn [{:keys [color] :as m}]
                     (cond-> m
                       (some-> color color-checker)
                       (dissoc :color)))
   Link coerce-link})

(def coerce-operations  (coerce/coercer  [QuillOp] quill-coersion-matcher))
(def coerce-operations! (coerce/coercer! [QuillOp] quill-coersion-matcher))

(defn- deflate-attributes [attributes]
  (some->> (seq attributes)
           (map (fn [[k v]]
                  (case k
                    :list       (if (= v "bullet") :ul :ol)
                    :header     {:h v}
                    :bold       :b
                    :italic     :i
                    :strike     :s
                    :underline  :u
                    :code       :c
                    :blockquote :q
                    :script     (if (= v "super") :sup :sub)
                    :indent     {:i v}
                    :align      (case v
                                  "center"  :ac
                                  "right"   :ar
                                  "justify" :aj)
                    :color      {:c v}
                    :background {:b v}
                    :link       {:l v})))
           (group-by keyword?)
           ((fn [{ks true ms false}]
              (apply merge {:k ks} ms)))))

(defn- deflate-op [{:keys [insert attributes]}]
  (util/strip-nils {:i insert
                    :a (deflate-attributes attributes)}))

(defn deflate
  "Poor man's lossless compression for given Quill `operations`. The idea is very simple:
  since the format is well defined, a simple space-saving mapping is good enough. This is
  better than nothing, but savings are insignficant if the data has an image."
  [operations]
  (map deflate-op operations))

(defn- inflate-attributes [deflated-attributes]
  (let [flags  (:k deflated-attributes)
        others (dissoc deflated-attributes :k)]
    (->> (map (fn [flag]
                (case (keyword flag)
                  :ul  [:list "bullet"]
                  :ol  [:list "ordered"]
                  :b   [:bold true]
                  :i   [:italic true]
                  :s   [:strike true]
                  :u   [:underline true]
                  :c   [:code true]
                  :q   [:blockquote true]
                  :sup [:script "super"]
                  :sub [:script "sub"]
                  :ac  [:align "center"]
                  :ar  [:align "right"]
                  :aj  [:align "justify"]))
              flags)
         (concat (map (fn [[k v]]
                        [(case k
                           :h :header
                           :i :indent
                           :c :color
                           :b :background
                           :l :link) v])
                      others))
         (into {})
         not-empty)))

(defn- inflate-op [{:keys [i a]}]
  (util/strip-nils
    {:insert     i
     :attributes (inflate-attributes a)}))

(defn inflate
  "Reverts deflated Quill operations into their original format."
  [deflated-ops]
  (mapv inflate-op deflated-ops))

(defn check-operations
  "Returns nil for valid operations or error code."
  [operations]
  (let [operations (coerce-operations operations)]
    (if (scu/error? operations)
      :error.quill.data
      (quill/check-images operations))))

(defn blank-operations?
  "True if every text insert operation is blank. In other words, the contents/operations are
  considered blank if it consists only of images."
  [operations]
  (every? (fn [{:keys [insert]}]
            (if (string? insert)
              (ss/blank? insert)
              true))
          operations))

(defn input-validator
  "Returns input validator function for `param-key` parameter. If `allow-blank?` is true,
  blank contents are allowed (default false). Can `fail` with two errors:

  `:error.quill.data` Operations are not valid Quill operations
  `:error.missing-parameters` Every string insert operation is blank (and not allowed)"
  ([param-key allow-blank?]
   (fn [command]
     (let [ops (get-in command [:data (keyword param-key)])]
       (some-> (or (check-operations ops)
                   (when (and (not allow-blank?)
                              (blank-operations? ops))
                     :error.missing-parameters))
               fail))))
  ([param-key]
   (input-validator param-key false)))
