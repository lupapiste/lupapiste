(ns lupapalvelu.singlepage
  (:require [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.env :as env])
  (:use [lupapalvelu.log])
  (:import [org.springframework.core.io Resource]
           [org.springframework.core.io.support PathMatchingResourcePatternResolver]
           [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPOutputStream]
           [org.apache.commons.io IOUtils]))

(defmethod enlive/get-resource Resource [^Resource r loader]
  (enlive/get-resource (.getInputStream r) loader))

(defn- get-content [r]
  (-> r (enlive/html-resource) (enlive/select [:.page])))

(defn- append-class-hidden [p]
  (assoc-in p [:attrs :class] (str (-> p :attrs :class) " hidden")))

(defn- make-invisible* [p]
  (map append-class-hidden p))

(defn inject-pages [main pages]
  (enlive/transform
    main
    [:#body]
    (enlive/content (map (comp make-invisible* get-content) pages))))

(defn- pages []
  (.getResources (PathMatchingResourcePatternResolver.) "html/pages/*.html"))

(defn- main-page []
  (enlive/html-resource "html/main.html"))

(defn- script-tags [r]
  (enlive/select r [:script]))

(defn- css-tags [r]
  (filter #(= (-> % :attrs :rel) "stylesheet") (enlive/select r [:link])))

(defn- strip-script-tags [r]
  (enlive/transform r [:script] nil))

(defn- strip-css-tags [r]
  (enlive/transform r [:link] (fn [e] (if (not= (-> e :attrs :rel) "stylesheet") e))))

(defn- add-combined-tags [r]
  (enlive/transform r [:title] (enlive/after
                                 [{:tag :script :attrs {:src "js/lupapalvelu.js" :type "text/javascript"}}
                                  {:tag :link :attrs {:href "css/lupapalvelu.css" :rel "stylesheet"}}])))

#_(if (= :dev env/mode)
  (do
    (def strip-script-tags identity)
    (def strip-css-tags identity)
    (def strip-combined-tags identity)))

(defn compose-singlepage-html []
  (apply str
         (enlive/emit*
           (inject-pages (->
                           (main-page)
                           (strip-script-tags)
                           (strip-css-tags)
                           (add-combined-tags))
                         (pages)))))

(defn gzip [a]
  (let [i (ByteArrayInputStream. a)
        o (ByteArrayOutputStream.)
        g (GZIPOutputStream. o)]
    (IOUtils/copy i g)
    (.close g)
    (.toByteArray o)))

(defn compose-singlepage-resources [selector content-type]
  (let [loader (clojure.lang.RT/baseLoader)
        buffer (ByteArrayOutputStream.)]
    (dorun
      (for [src (selector (main-page))]
        (if-let [r (.getResourceAsStream loader (str "public/" src))]
          (do
            (IOUtils/copy r buffer)
            (IOUtils/closeQuietly r)))))
    (let [content (gzip (.toByteArray buffer))]
      {:status 200
       :body (ByteArrayInputStream. content)
       :headers {"Content-Type" content-type
                 "Content-Encoding" "gzip"
                 "Content-Length" (str (alength content))}})))

(defn compose-singlepage-js []
  (compose-singlepage-resources (fn [r] (map #(-> % :attrs :src) (script-tags r))) "application/javascript"))

(defn compose-singlepage-css []
  (compose-singlepage-resources (fn [r] (map #(-> % :attrs :href) (css-tags r))) "text/css"))
