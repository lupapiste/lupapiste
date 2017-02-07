(ns lupapalvelu.email
  (:require [clojure.java.io :as io]
            [clojure.walk :refer [postwalk]]
            [clostache.parser :as clostache]
            [endophile.core :as endophile]
            [net.cgrand.enlive-html :as enlive]
            [taoensso.timbre :as timbre :refer [warn]]
            [sade.core :refer :all]
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

(defn- template-path [template-name]
  (str "email-templates/" template-name))

(defn find-resource
  [resource-name]
  (if-let [resource (io/resource (template-path resource-name))]
    resource
    (throw (IllegalArgumentException. (str "Can't find mail resource: " resource-name)))))

(defn- slurp-resource [resource]
  (with-open [in (io/input-stream resource)]
    (slurp in)))

(defn- fetch-template-uncached
  [template-name]
  (slurp-resource (find-resource template-name)))

(defn- fetch-html-template-uncached
  [template-name & [lang]]
  (enlive/html-resource (find-resource (if lang (str lang "-" template-name) template-name))))

(if (env/feature? :no-cache)
  (do
    (def fetch-template fetch-template-uncached)
    (def fetch-html-template fetch-html-template-uncached))
  (do
    (def fetch-template (memoize fetch-template-uncached))
    (def fetch-html-template (memoize fetch-html-template-uncached))))

(defn fetch-template-by-lang [template-name lang]
  (fetch-template (str lang "-" template-name)))
;;
;; Plain text support:
;; -------------------

(def ^:private hr-as-text "\n---------\n")

(defmulti ->str (fn [element] (if (map? element) (:tag element) :str)))

(defn- ->str* [elements] (ss/join (map ->str elements)))

(defmethod ->str :default [element] (->str* (:content element)))
(defmethod ->str :str     [element] (ss/join element))
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
(defmethod ->str :hr      [element] hr-as-text)
(defmethod ->str :blockquote [element] (-> element :content ->str* (ss/replace #"\n{2,}" "\n  ") (ss/replace #"^\n" "  ")))
(defmethod ->str :table      [element] (str \newline (->str* (filter map? (:content element))) \newline))
(defmethod ->str :tr         [element] (let [contents (filter #(map? %) (:content element))]
                                         (ss/join \tab (map #(ss/join (:content %)) contents))))

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
  (let [message (str "No localization for language "
                     (or lang "(nil)")
                     " in "
                     localization)]
    (if (env/dev-mode?)
      (throw (Exception. message))
      (do (warn message)
          ""))))

(defn template->localization-model [template]
  (reduce (fn [locs loc-key]
            (assoc-in locs
                      (map keyword loc-key)
                      #(ss/unescape-html-scandinavian-characters
                        (i18n/try-localize throw-localization-not-found!
                                           %
                                           loc-key))))
          {}
          (localization-keys-from-template template)))

(defn- localization-for-language [lang localization]
  (if (fn? localization)
    (localization lang)
    localization))

(defn prepare-context-for-language [lang context]
  (postwalk (partial localization-for-language lang) context))

(defn- preprocess-context
  "1. Build a default context by finding all template variables and searching for respective
      translations from i18n files.
   2. Override with the provided context (deep-merge).
   3. Walk through the resulting map, calling all encountered functions with lang as argument."
  [template {:keys [lang] :as context}]
  (prepare-context-for-language lang
                                (deep-merge (template->localization-model template)
                                            context)))

;;
;; Apply template:
;; ---------------

(defn- render-body-with-lang [template-name context lang]
  (let [master (fetch-template "master.md")
        footer (fetch-template-by-lang "footer.md" lang)
        body   (fetch-template-by-lang template-name lang)]
    (clostache/render master
                      (preprocess-context body
                                          (assoc context :lang lang))
                      {:body body :footer footer})))

(defn try-render-with-lang
  "Try render with language. If resource is not found and IllegalArgumentException is thrown, return empty string."
  [template context lang]
  (try
    (render-body-with-lang template context lang)
    (catch IllegalArgumentException e
      (warn (.getMessage e))
      "")))

(defn- render-body
  "Render the template body. If no language is given in context, build
  the body by catenating the templates of all supported languages."
  [template context]
  (let [res (->> (if (:lang context)
                   [(:lang context)]
                   i18n/supported-langs)
                 (map name)
                 (map (partial try-render-with-lang template context))
                 (remove ss/blank?))]
    (if (not-empty res)
      (ss/join hr-as-text res)
      (fail! :error.empty-email :template template))))

(defn- unescape-at [s]
  (ss/replace s #"[\\]+@" "@"))

(defn- apply-md-template [template context]
  (let [html-wrap   (fetch-html-template "html-wrap.html")
        content     (-> (render-body template context)
                        ; Avoid email links to be generated, see also https://github.com/theJohnnyBrown/endophile/issues/6
                        (ss/replace "@" "\\@")
                        (endophile/mp)
                        (endophile/to-clj))]
    [(-> content ->str* ss/unescape-html unescape-at)
     (->> content enlive/content (enlive/transform html-wrap [:body]) endophile/html-string unescape-at)]))

(defn- apply-html-template [template-name context]
  (let [lang     (:lang context)
        master   (fetch-html-template "master.html")
        template (fetch-html-template template-name lang)
        html     (-> master
                     (enlive/transform [:style] (enlive/append (->> (enlive/select template [:style]) (map :content) flatten ss/join)))
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
    (ss/ends-with template ".md")    (apply-md-template template context)
    (ss/ends-with template ".html")  (apply-html-template template (preprocess-context template context))
    :else                            (throw (Exception. (str "unsupported template: " template)))))
