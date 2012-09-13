(ns lupapalvelu.singlepage
  (:require [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.env :as env])
  (:use [lupapalvelu.log])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPOutputStream]
           [org.apache.commons.io IOUtils]))

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

(defn- load-page [name]
  (enlive/html-resource (str "public/html/" name ".html")))

(defn- script-tags [r]
  (filter #(-> % :attrs :src) (enlive/select r [:script])))

(defn- css-tags [r]
  (filter #(= (-> % :attrs :rel) "stylesheet") (enlive/select r [:link])))

(defn- page-tags [r]
  (filter #(= (-> % :attrs :rel) "page") (enlive/select r [:link])))

(defn- strip-script-tags [r]
  (enlive/transform r [:script] (fn [e] (if (-> e :attrs :src) nil e))))

(defn- strip-css-tags [r]
  (enlive/transform r [:link] (fn [e] (if (not= (-> e :attrs :rel) "stylesheet") e))))

(defn- strip-page-tags [r]
  (enlive/transform r [:link] (fn [e] (if (not= (-> e :attrs :rel) "page") e))))

(defn- add-combined-tags [r name]
  (enlive/transform r [:title] (enlive/after
                                 [{:tag :script :attrs {:src (str name ".js") :type "text/javascript"}}
                                  {:tag :link :attrs {:href (str name ".css") :rel "stylesheet"}}])))

(defn- pages [r]
  (let [loader (clojure.lang.RT/baseLoader)]
    (map
      #(->> % :attrs :href (.getResourceAsStream loader))
      (page-tags r))))

#_(if (= :dev env/mode)
  (do
    (def strip-script-tags identity)
    (def strip-css-tags identity)
    (def strip-combined-tags identity)))

(defn compose-singlepage-html [name]
  (let [main (load-page name)]
    (apply str
           (enlive/emit*
             (inject-pages (->
                             main
                             (strip-script-tags)
                             (strip-css-tags)
                             (strip-page-tags)
                             (add-combined-tags name))
                           (pages main))))))

(defn gzip [a]
  (let [i (ByteArrayInputStream. a)
        o (ByteArrayOutputStream.)
        g (GZIPOutputStream. o)]
    (IOUtils/copy i g)
    (.close g)
    (.toByteArray o)))

(defn compose-singlepage-resources [name selector content-type]
  (let [loader (clojure.lang.RT/baseLoader)
        buffer (ByteArrayOutputStream.)]
    (dorun
      (for [src (selector (load-page name))]
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

(defn compose-singlepage-js [name]
  (compose-singlepage-resources name (fn [r] (map #(-> % :attrs :src) (script-tags r))) "application/javascript"))

(defn compose-singlepage-css [name]
  (compose-singlepage-resources name (fn [r] (map #(-> % :attrs :href) (css-tags r))) "text/css"))
