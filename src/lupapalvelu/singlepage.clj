(ns lupapalvelu.singlepage
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal tracef debugf infof warnf errorf fatalf]]
            [clojure.java.io :as io]
            [lupapalvelu.components.ui-components :refer [ui-components] :as uic]
            [lupapalvelu.i18n :as i18n]
            [net.cgrand.enlive-html :as enlive]
            [clj-time.coerce :as tc]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [lupapalvelu.components.core :as c])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [org.apache.commons.io IOUtils]
           [com.googlecode.htmlcompressor.compressor HtmlCompressor]
           [com.yahoo.platform.yui.compressor JavaScriptCompressor CssCompressor]
           [org.mozilla.javascript ErrorReporter EvaluatorException]))

(defn write-header [kind out n]
  (when (env/feature? :no-minification)
    (.write out (format "\n\n/*\n * %s\n */\n" n)))
  (when (= kind :js)
    (.write out "\n"))
  out)

(def error-reporter
  (reify ErrorReporter
    (^void warning [this ^String message, ^String sourceName,
                    ^int line, ^String lineSource, ^int lineOffset]
      (warn message))
    (^void error [this ^String message, ^String sourceName,
                    ^int line, ^String lineSource, ^int lineOffset]
      (error message))
    (^EvaluatorException runtimeError [this ^String message, ^String sourceName,
                    ^int line, ^String lineSource, ^int lineOffset]
      (error message) (EvaluatorException. message))))

(defn- minified [kind ^java.io.Reader in ^java.io.Writer out]
  (cond
    (env/feature? :no-minification) (IOUtils/copy in out)
    (= kind :js) (let [c (JavaScriptCompressor. in error-reporter)]
                   ; linebreaks at 32K, obfuscate locals, no verbose,
                   (.compress c out 32000 true false
                     ; preserve semicolons, disable optimizations
                     true true))
    (= kind :css) (let [c (CssCompressor. in)]
                    ; no linebreaks
                    (.compress c out -1))))

(defn- fn-name [f]
  (-> f str (.replace \$ \/) (.split "@") first))

(defn compose-resource [kind component]
  (let [stream (ByteArrayOutputStream.)]
    (with-open [out (io/writer stream)]
      (doseq [src (c/get-resources ui-components kind component)]
        (if (fn? src)
          (.write (write-header kind out (str "fn: " (fn-name src))) (src))
          (with-open [in (-> src c/path io/resource io/input-stream io/reader)]
            (if (or (ss/contains? src "debug") (ss/contains? src ".min."))
              (IOUtils/copy in (write-header kind out src))
              (minified kind in (write-header kind out src)))))))
    (.toByteArray stream)))

(defn parse-html-resource [c resource]
  (let [h (enlive/html-resource resource)]
    (assoc c
      :nav    (concat (:nav c)    (enlive/select h [:nav]))
      :info   (concat (:info c)   (enlive/select h [:div.notification]))
      :footer (concat (:footer c) (enlive/select h [:footer]))
      :page   (concat (:page  c)  (enlive/select h [:section.page]))
      :templates (concat (:templates c) (enlive/select h [:script.ko-template])))))

(defn- resource-url [component kind]
  (str (kind (env/value :cdn)) (:build-number env/buildinfo) "/" (name component) "." (name kind)))

(def- buildinfo-summary
  (format "%s %s [%s] %4$tF %4$tT (%5$s)"
          env/target-env
          (:git-branch env/buildinfo)
          (name env/mode)
          (tc/to-date (tc/from-long (:time env/buildinfo)))
          (:build-number env/buildinfo)))

(defn- set-body-class [t component theme]
  (let [css-classes (if (uic/themes theme)
                      (str (name component) \space theme)
                      (name component))]
    (enlive/transform t [:body] (fn [e] (assoc-in e [:attrs :class] css-classes)))))

(defn inject-content [t {:keys [nav info page footer templates]} component lang theme]
  (-> t
      (set-body-class component theme)
      (enlive/transform [:nav] (enlive/content (map :content nav)))
      (enlive/transform [:div.notification] (enlive/content (map :content info)))
      (enlive/transform [:section] (enlive/content page))
      (enlive/transform [:footer] (enlive/content (map :content footer)))
      (enlive/transform [:script] (fn [e] (if (= (-> e :attrs :src) "inject-common") (assoc-in e [:attrs :src] (str (resource-url :common :js) "?lang=" (name lang))) e)))
      (enlive/transform [:script] (fn [e] (if (= (-> e :attrs :src) "inject-app") (assoc-in e [:attrs :src] (resource-url component :js)) e)))
      (enlive/transform [:script] (fn [e] (if (= (-> e :attrs :src) "inject-cljs") (assoc-in e [:attrs :src] uic/cljs-base-url) e)))
      (enlive/transform [:link] (fn [e] (if (= (-> e :attrs :href) "inject") (assoc-in e [:attrs :href] (resource-url component :css)) e)))
      (enlive/transform [:#buildinfo] (enlive/content buildinfo-summary))
      (enlive/transform [:link#main-css] (fn [e] (update-in e [:attrs :href] #(str % "?b=" (:build-number env/buildinfo)))))
      (enlive/transform [:div.ko-templates] (enlive/content templates))
      enlive/emit*))

(defn- compress-html [^String html]
  (let [c (doto (HtmlCompressor.)
            (.setRemoveScriptAttributes true)    ; remove optional attributes from script tags
            (.setRemoveStyleAttributes true)     ; remove optional attributes from style tags
            (.setRemoveLinkAttributes true)      ; remove optional attributes from link tags
            (.setRemoveFormAttributes true)      ; remove optional attributes from form tags
            (.setSimpleBooleanAttributes true)   ; remove values from boolean tag attributes
            (.setRemoveJavaScriptProtocol true)  ; remove "javascript:" from inline event handlers
            (.setRemoveHttpProtocol false)       ; do not replace "http://" with "//" inside tag attributes
            (.setRemoveHttpsProtocol false)      ; do not replace "https://" with "//" inside tag attributes
            (.setRemoveSurroundingSpaces HtmlCompressor/BLOCK_TAGS_MAX)  ; remove spaces around provided tags
            (.setPreservePatterns [(re-pattern "<!--\\s*/?ko.*-->")]))] ; preserve KnockoutJS comments
    (.compress c html)))

(defn compose-html [component lang theme]
  (let [out (ByteArrayOutputStream.)]
    (doseq [element (inject-content
                      (enlive/html-resource (c/path "template.html"))
                      (reduce parse-html-resource {} (map (partial str (c/path)) (c/get-resources ui-components :html component)))
                      component
                      lang
                      theme)]
      (.write out (ss/utf8-bytes element)))
    (-> out (.toString (.name ss/utf8)) (compress-html) (ss/utf8-bytes))))

(defn compose [kind component lang theme]
  (tracef "Compose %s%s" component kind)
  (if (= :html kind)
    (compose-html component lang theme)
    (compose-resource kind component)))
