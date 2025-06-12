(ns lupapalvelu.singlepage
  (:require [clojure.java.io :as io]
            [lupapalvelu.chatbot :as chatbot]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.components.ui-components :refer [ui-components] :as uic]
            [lupapalvelu.logging :refer [error tracef warn]]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [com.googlecode.htmlcompressor.compressor HtmlCompressor]
           [com.yahoo.platform.yui.compressor CssCompressor JavaScriptCompressor]
           [java.io ByteArrayOutputStream Reader Writer]
           [org.apache.commons.io IOUtils]
           [org.mozilla.javascript ErrorReporter EvaluatorException]))

(set! *warn-on-reflection* true)

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
            (if (or (ss/includes? src "debug") (ss/includes? src ".min."))
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
          (date/zoned-date-time (:time env/buildinfo))
          (:build-number env/buildinfo)))

(defn- set-body-class [t component theme]
  (let [css-classes (->> [(name component)
                          (when (uic/themes theme)
                            theme)
                          (env/in-dev
                            "debug-screens")]
                         (remove nil?)
                         (ss/join " "))]
    (enlive/transform t [:body] (fn [e] (assoc-in e [:attrs :class] css-classes)))))

(def ie-script-str
  ; trick to obfuscate script end tag stolen from https://stackoverflow.com/a/236106
  "if(!/MSIE \\d|Trident.*rv:/.test(navigator.userAgent)) document.write('<script type=\"text/javascript\" src=\"%s\">\\x3C/script>');")

(defn ie-conditional
  "Put script source in script tag, that writes script-src with document.write if user agent not IE."
  [elem script-src]
  (-> ((enlive/content (format ie-script-str script-src)) elem)
      (util/dissoc-in [:attrs :src])))

(def manifest-path "public/lp-static/js/manifest.edn")

(defn read-manifest
  "Parse the shadow-cljs output manifest.
  Produces a map of module id -> vector of .js scripts to load"
  []
  (when-not (io/resource manifest-path)
    (throw (ex-info "manifest.edn not found! Frontend not built?" {:path manifest-path})))
  (let [manifest (util/read-edn-resource manifest-path)]
    ;; The manifest contains the modules a) in dependency order,
    ;; b) each module knows which other modules it depends on, and
    ;; c) the name of the actual .js file
    ;; -> Produce a list of .js dependencies for each module in proper order
    (reduce (fn [m {:keys [module-id depends-on output-name]}]
              (if (nil? depends-on)
                (assoc m module-id [output-name])
                (assoc m module-id (conj (->> depends-on
                                              (mapcat (fn [dependency] (get m dependency)))
                                              distinct
                                              (into []))
                                         output-name))))
            {}
            manifest)))

(defn- inject-modules [component]
  (let [module->js-scripts (read-manifest)]
    (->> (c/get-component ui-components component)
         :module
         module->js-scripts
         (map (fn [output-name] (enlive/html [:script {:src (format "/lp-static/js/%s" output-name)}])))
         enlive/flatten-nodes-coll)))

(defn inject-script [component lang elem]
  (case (get-in elem [:attrs :src])
    "inject-common"        (assoc-in elem [:attrs :src] (str (resource-url :common :js) "?lang=" (name lang)))
    "inject-app"           (assoc-in elem [:attrs :src] (resource-url component :js))
    ;; ClojureScript bundle(s) (includes React)
    "inject-cljs"          (inject-modules component)
    "inject-chatbot"       (chatbot/chatbot-script-tag)
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
