(ns lupapalvelu.markdown
  (:import [com.vladsch.flexmark.parser Parser]
           [com.vladsch.flexmark.ext.tables TablesExtension]
           [com.vladsch.flexmark.html HtmlRenderer]
           [com.vladsch.flexmark.util.data MutableDataSet]
           [com.vladsch.flexmark.ext.autolink AutolinkExtension]))

(set! *warn-on-reflection* true)

(def parser-options (doto (MutableDataSet.)
                      (.set Parser/EXTENSIONS [(TablesExtension/create)
                                               (AutolinkExtension/create)])
                      (.set AutolinkExtension/IGNORE_LINKS ".+@.+\\..+")))

(def *parser (delay (.build (Parser/builder parser-options))))

(def *renderer (delay (.build (HtmlRenderer/builder parser-options))))

(defn render-html ^String [^String markdown-string]
  (->> markdown-string
       (.parse ^Parser @*parser)
       (.render ^HtmlRenderer @*renderer)))
