(ns lupapalvelu.email
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]
            [clostache.parser :as clostache]
            [endophile.core :as endophile]
            [net.cgrand.enlive-html :as enlive]
            [sade.email :as email]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [map-values deep-merge]]
            [lupapalvelu.i18n :as i18n] ))

(def send-email-message email/send-email-message)

;;
;; templating:
;; -----------
;; Template naming follows the format [lang-]name.ext,
;; where the language part is optional. Also, if the prefixed template
;; is not found, the fallback is a plain template.

(defn- template-path [template-name lang]
  (str "email-templates/"
       (if lang
         (str lang "-" template-name)
         template-name)))

(defn find-resource
  [resource-name & [lang]]
  (if-let [resource (io/resource (template-path resource-name lang))]
    resource
    (if lang
      (find-resource resource-name)
      (throw (IllegalArgumentException. (str "Can't find mail resource: " resource-name))))))

(defn- slurp-resource [resource]
  (with-open [in (io/input-stream resource)]
    (slurp in)))

(defn fetch-template
  [template-name & [lang]]
  (slurp-resource (find-resource template-name lang)))

(defn- fetch-html-template
  [template-name & [lang]]
  (enlive/html-resource (find-resource template-name lang)))

(when-not (env/feature? :no-cache)
  (alter-var-root #'fetch-template memoize)
  (alter-var-root #'fetch-html-template memoize))

;;
;; Plain text support:
;; -------------------

(defmulti ->str (fn [element] (if (map? element) (:tag element) :str)))

(defn- ->str* [elements] (s/join (map ->str elements)))

(defmethod ->str :default [element] (->str* (:content element)))
(defmethod ->str :str     [element] (s/join element))
(defmethod ->str :h1      [element] (str \newline (->str* (:content element)) \newline))
(defmethod ->str :h2      [element] (str \newline (->str* (:content element)) \newline))
(defmethod ->str :p       [element] (str \newline (->str* (:content element)) \newline))
(defmethod ->str :ul      [element] (->str* (:content element)))
(defmethod ->str :li      [element] (str \* \space (->str* (:content element)) \newline))
(defmethod ->str :a       [element] (let [linktext (->str* (:content element))
                                          href (get-in element [:attrs :href])
                                          separator (if (> (count href) 50) \newline \space)]
                                      (if-not (= linktext href)
                                        (str linktext \: separator href \space)
                                        (str separator href \space))))
(defmethod ->str :img     [element] "")
(defmethod ->str :br      [element] "")
(defmethod ->str :hr      [element] "\n---------\n")
(defmethod ->str :blockquote [element] (-> element :content ->str* (s/replace #"\n{2,}" "\n  ") (s/replace #"^\n" "  ")))
(defmethod ->str :table      [element] (str \newline (->str* (filter map? (:content element))) \newline))
(defmethod ->str :tr         [element] (let [contents (filter #(map? %) (:content element))]
                                         (s/join \tab (map #(s/join (:content %)) contents))))

;;
;; HTML support:
;; -------------

(defn- wrap-html [html-body]
  (let [html-wrap (fetch-html-template "html-wrap.html")]
    (enlive/transform html-wrap [:body] (enlive/content html-body))))

(defn- html->plain [html]
  (-> html
    (ss/utf8-bytes)
    (io/reader :encoding "UTF-8")
    enlive/html-resource
    (enlive/select [:body])
    ->str*
    ss/unescape-html))

;;
;; Context preprocessing:
;; ----------------------

(let [open-braces         "\\{+&?"
      open-section-braces "\\{\\{(?:#|\\^)"
      open-comment-braces "\\{\\{!"
      close-braces        "\\}+"
      whitespace          "\\s*"
      captured-key        "((?:[^#.}\\s]+\\.?)*)"
      key                 "(?:(?:[^#.}\\s]+\\.?)*)"
      anything            "(?:.|\n|\r)*"
      variable            (str "(?:" open-braces whitespace captured-key whitespace close-braces ")")
      open-section        (str open-section-braces whitespace captured-key whitespace close-braces)
      close-section       (str open-braces whitespace "/" "\\1" whitespace close-braces)
      section             (str "(?:(?:" open-section anything close-section "))")
      comment             (str open-comment-braces anything close-braces)]
  (defn localization-keys-from-template
    "Find localization keys from template. Ignore Clostache sections and comments."
    [template]
    (->> (re-seq (re-pattern (str section "|" comment "|" variable)) template)
         (map last)
         (remove nil?)
         (map #(ss/split % #"\.")))))

(defn throw-localization-not-found! [lang localization]
  (throw (Exception. (str "No localization for language "
                          (or lang "(nil)")
                          " in "
                          localization))))

(defn template->localization-model [template]
  (reduce (fn [locs loc-key]
            (assoc-in locs
                      (map keyword loc-key)
                      #(i18n/try-localize throw-localization-not-found!
                                          %
                                          loc-key)))
          {}
          (localization-keys-from-template template)))

(defn- localization-for-language [lang localization]
  (if (fn? localization)
    (localization lang)
    localization))

(defn- preprocess-context
  "1. Build a default context by finding all template variables and searching for respective
      translations from i18n files.
   2. Override with the provided context (deep-merge).
   3. Walk through the resulting map, calling all encountered functions with lang as argument."
  [template {:keys [lang] :as context}]
  (postwalk (partial localization-for-language lang)
            (deep-merge (template->localization-model (fetch-template template lang))
                        context)))

;;
;; Apply template:
;; ---------------

(defn- unescape-at [s]
  (ss/replace s #"[\\]+@" "@"))

(defn- apply-md-template [template context]
  (let [lang        (:lang context)
        master      (fetch-template "master.md" lang)
        header      (fetch-template "header.md" lang)
        body        (fetch-template template lang)
        footer      (fetch-template "footer.md" lang)
        html-wrap   (fetch-html-template "html-wrap.html" lang)
        rendered    (clostache/render master context {:header header :body body :footer footer})
        ; Avoid email links to be generated, see also https://github.com/theJohnnyBrown/endophile/issues/6
        escaped     (ss/replace rendered "@" "\\@")
        content     (endophile/to-clj (endophile/mp escaped))
        ]
    [(-> content ->str* ss/unescape-html unescape-at)
     (->> content enlive/content (enlive/transform html-wrap [:body]) endophile/html-string unescape-at)]))

(defn- apply-html-template [template-name context]
  (let [lang     (:lang context)
        master   (fetch-html-template "master.html" lang)
        template (fetch-html-template template-name lang)
        html     (-> master
                     (enlive/transform [:style] (enlive/append (->> (enlive/select template [:style]) (map :content) flatten s/join)))
                     (enlive/transform [:.body] (enlive/append (map :content (enlive/select template [:body]))))
                     (endophile/html-string)
                     (clostache/render context))
        plain    (html->plain html)]
    [plain html]))

(defn apply-template
  "Build an email from the given template and context. Localizations
   from i18n files are used for template variables that do not have a
   localization in the provided context."
  [template context]
  (cond
    (ss/ends-with template ".md")    (apply-md-template template (preprocess-context template context))
    (ss/ends-with template ".html")  (apply-html-template template (preprocess-context template context))
    :else                            (throw (Exception. (str "unsupported template: " template)))))
