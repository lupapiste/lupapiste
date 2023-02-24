(ns lupapalvelu.singlepage
  (:require [clj-time.coerce :as tc]
            [clojure.java.io :as io]
            [lupapalvelu.chatbot :as chatbot]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.components.ui-components :refer [ui-components] :as uic]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.shared-util :as util]
            [sade.strings :as ss]
            [taoensso.timbre :refer [warn error tracef]])
  (:import [com.googlecode.htmlcompressor.compressor HtmlCompressor]
           [com.yahoo.platform.yui.compressor JavaScriptCompressor CssCompressor]
           [java.io ByteArrayOutputStream Writer Reader]
           [org.apache.commons.io IOUtils]
           [org.mozilla.javascript ErrorReporter EvaluatorException]))

(defn- ^Writer write-header [kind ^Writer out n]
  (when (env/feature? :no-minification)
    (.write out (format "\n\n/*\n * %s\n */\n" n)))
  (when (= kind :js)
    (.write out "\n"))
  out)

(def error-reporter
  (reify ErrorReporter
    (warning [_ message _ _ _ _] (warn message))
    (error [_ message _ _ _ _] (error message))
    (runtimeError [_ message _ _ _ _]
      (error message)
      (EvaluatorException. message))))

(defn- minified [kind ^Reader in ^Writer out]
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
          (.write (write-header kind out (str "fn: " (fn-name src))) ^String (src))
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
  (str (kind (env/value :cdn)) env/build-number "/" (name component) "." (name kind)))

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

(def ie-script-str
  ; trick to obfuscate script end tag stolen from https://stackoverflow.com/a/236106
  "if(!/MSIE \\d|Trident.*rv:/.test(navigator.userAgent)) document.write('<script type=\"text/javascript\" src=\"%s\">\\x3C/script>');")

(defn ie-conditional
  "Put script source in script tag, that writes script-src with document.write if user agent not IE."
  [elem script-src]
  (-> ((enlive/content (format ie-script-str script-src)) elem)
      (util/dissoc-in [:attrs :src])))

(defn inject-script [component lang elem]
  (case (get-in elem [:attrs :src])
    "inject-common" (assoc-in elem [:attrs :src] (str (resource-url :common :js) "?lang=" (name lang)))
    "inject-app" (assoc-in elem [:attrs :src] (resource-url component :js))
    ; ClojureScript bundle (includes React)
    "inject-cljs" (assoc-in elem [:attrs :src] (str uic/cljs-app-url "?b=" env/build-number))
    ; React.js map component TODO: replace with bundle hash to support better caching compared to env build number
    "inject-map-component" (ie-conditional elem (str uic/map-component-url "?b=" env/build-number))
    "inject-chatbot" (chatbot/chatbot-script-tag)
    elem))

(defn inject-content [t {:keys [nav info page footer templates]} component lang theme]
  (-> t
      (set-body-class component theme)
      (enlive/transform [:html] #(assoc-in % [:attrs :lang] (name lang)))
      (enlive/transform [:nav] (enlive/content (map :content nav)))
      (enlive/transform [:div.notification] (enlive/content (map :content info)))
      (enlive/transform [:section] (enlive/content page))
      (enlive/transform [:footer] (enlive/content (map :content footer)))
      (enlive/transform [:script] (partial inject-script component lang))
      (enlive/transform [:link] (fn [e] (if (= (-> e :attrs :href) "inject") (assoc-in e [:attrs :href] (resource-url component :css)) e)))
      (enlive/transform [:#buildinfo] (enlive/content buildinfo-summary))
      (enlive/transform [:link.release-css] (fn [e] (update-in e [:attrs :href] #(str % "?b=" env/build-number))))
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
      (.write out ^bytes (ss/utf8-bytes element)))
    (-> out (.toString (.name ss/utf8)) (compress-html) (ss/utf8-bytes))))

(defn compose [kind component lang theme]
  (tracef "Compose %s%s" component kind)
  (if (= :html kind)
    (compose-html component lang theme)
    (compose-resource kind component)))
