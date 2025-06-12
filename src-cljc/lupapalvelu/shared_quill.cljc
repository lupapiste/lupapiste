(ns lupapalvelu.shared-quill
  "Tags support for Quill operations and some common code for front- and backend. See
  `lupapalvelu.quill` for more information."
  (:require [sade.util :refer [consv conjv] :as util]
            [sade.strings :as ss]))

(def ^:const Image #"data:image/.+;base64,.+")
(def ^:const max-image-size
  "Maximum size of the image string (including preamble)."
  100000)

(defn- resolve-attributes [{:keys [header bold color list italic indent
                                   blockquote strike underline align
                                   code background link script]}]
  (letfn [(add-tag [elem tag]
            (update elem :tags conjv (keyword tag)))
          (fallback-tag [{:keys [tags] :as elem} tag]
            (cond-> elem
              (not tags) (add-tag tag)))
          (add-class [elem & cls]
            (update-in elem [:attr :class] conjv (apply str cls)))
          (close-scope [elem]
            (assoc elem :scope-tag ::close))]
    (cond-> {}
      list       (merge (let [scope-tag (if (= list "bullet") :ul.quill :ol.quill)]
                          {:scope-tag scope-tag
                           :tags      [(util/kw-path :li scope-tag)]}))
      link       (-> (add-tag :a)
                     (update :attr
                             assoc
                             :rel "noopener noreferrer"
                             :href link
                             :target :_blank))
      header     (-> (add-tag (str "h" header)) close-scope)
      blockquote (-> (add-tag :blockquote) close-scope)
      bold       (add-tag :strong)
      italic     (add-tag :em)
      strike     (add-tag :s)
      underline  (add-tag :u)
      code       (add-tag :code)
      script     (add-tag (if (= script "super") :sup :sub))
      color      (-> (assoc-in [:attr :style :color] color)
                     (fallback-tag :span))
      background (-> (assoc-in [:attr :style :background-color] background)
                     (fallback-tag :span))
      indent     (add-class "ql-indent-" indent)
      align      (-> (add-class "ql-align-" align)
                     close-scope
                     (fallback-tag :p))
      true       (-> (fallback-tag :span)
                     (update :tags (fn [tags]
                                     (mapv #(util/kw-path % :quill) tags)))))))

(defn- make-tag
  ([tags attr content]
   (if (seq tags)
     (reduce (fn [acc tag]
               [tag acc])
             (cond-> [(first tags)]
               attr (conjv attr)
               true (conjv content))
             (rest tags))
     content))
  ([tags content]
   (make-tag tags nil content)))

(defn- tag-line
  ([tags attr line]
   (make-tag (rest tags)
             (cond->> line
               attr (consv attr)
               true (consv (first tags)))))
  ([tags line]
   (tag-line tags nil line)))

(defn- close-line [attributes line scopes]
  (let [scope                        (last scopes)
        old-scopes                   (butlast scopes)
        {:keys [scope-tag tags attr]} (resolve-attributes attributes)]
    (cond
      (= scope-tag ::close)
      (conjv scopes (tag-line tags attr line))

      (and scope-tag (not= scope-tag (first scope)))
      (conjv scopes (make-tag [scope-tag] (tag-line tags attr line)))

      (and scope-tag (= scope-tag (first scope)))
      (conjv old-scopes (conjv scope (tag-line tags attr line)))

      (seq line)
      (conjv scopes (tag-line  (consv :p.quill tags) attr line))

      :else scopes)))

(defn- newline?
  ([s pred-fn]
   (pred-fn #(= % \newline) s))
  ([s]
   (newline? s some)))

(defn- add-paragraphs [insert line scopes]
  (loop [scopes     scopes
         line       line
         ;; ss/split or ss/split-lines would lose the trailing newlines.
         [px & pxs] (re-seq  #"\n|[^\n]+" insert)]
    (cond
      (nil? px) {:scopes scopes
                 :line   line}

      (= px "\n")
      (recur (conjv scopes (into [:p.quill]
                                 (if (seq line) line [[:br]])))
             [] pxs)

      :else (recur scopes (conjv line px) pxs))))

(defn- add-to-line [{:keys [image] :as insert} attributes line]
  (conjv line (if image
                [:img {:src image}]
                (let [{:keys [tags attr]} (resolve-attributes attributes)]
                  (make-tag tags attr insert)))))

(defn- operations->tags [operations]
  (loop [[{:keys [insert attributes]}
          & ops] operations
         line    []
         scopes  []]
    (cond
      (nil? insert)
      (close-line nil line scopes)

      (newline? insert every?)
      (recur ops nil (close-line attributes line scopes))

      (newline? insert)
      (let [{:keys [line scopes]} (add-paragraphs insert line scopes)]
        (recur ops line scopes))

      :else
      (recur ops (add-to-line insert attributes line) scopes))))


(defn ->tags
  "Transforms given `data` into div. If the data is Quill operations it is converted into
  tags. The default div can be overridden with `div` parameter that is a div vector
  without contents."
  ([data div]
   (cond
     (string? data)     (conj (or div [:div.quill.string]) data)
     (sequential? data) (into (or div [:div.quill]) (operations->tags data))))
  ([data] (->tags data nil)))

(defn check-images
  "Checks that Quill operations images are valid. Since the image data is stored as a base64
  string, we enforce the following restrictions due to data size:

    - Only one image is allowed
    - Only data:image - base64 strings are allowed
    - The maximum string size is 100 000. This corresponds roughly to relatively small
      images (less than 100kb)."
  [operations]
  (when-let [[image & images] (seq (keep #(some-> % :insert :image)
                                         operations))]
    (cond
      (seq images)                     :error.quill.image.count
      (not (re-matches Image image))   :error.quill.image.format
      (> (count image) max-image-size) :error.quill.image.size)))

(defn- trim-until
  "Trims inserts until non-blank encountered."
  [operations trim-fn]
  (loop [[{:keys [insert] :as op} & rest-ops :as ops] operations
         result                                       []]
    (if-not (string? insert)
      (into result ops)
      (let [op (update op :insert trim-fn)]
        (cond
          (ss/not-blank? (:insert op)) (-> result (conj op) (into rest-ops))
          :else                        (recur rest-ops (conj result op)))))))

(defn trim-operations
  "Trims 'left' and 'right' insert operations."
  [operations]
  (-> (reverse operations)
      (trim-until ss/trimr)
      reverse
      (trim-until ss/triml)))
