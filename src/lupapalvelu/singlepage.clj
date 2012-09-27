(ns lupapalvelu.singlepage
  (:require [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.env :as env]
            [lupapalvelu.components.core :as c])
  (:use [lupapalvelu.log]
        [lupapalvelu.components.ui-components :only [ui-components]])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPOutputStream]
           [org.apache.commons.io IOUtils]))

; js from component
; css from component
; html:
;  - comp deps -> heading, nav, footer, section.page
;  - inject into template.html

(defn write-header [out name]
  (.write out (.getBytes (format "\n/*\n * %s\n */\n\n" name)))
  out)

(def content-type {:js "application/javascript"
                   :css "text/css"
                   :html "text/html; charset=utf-8"})

(defn compose [kind component]
  (let [buffer (ByteArrayOutputStream.)
        out (GZIPOutputStream. buffer)]
    (doseq [src (c/get-resources ui-components kind component)]
      (with-open [resource (clojure.lang.RT/resourceAsStream nil (str "components/" src))]
        (IOUtils/copy resource (write-header out src))))
    (.close out)
    (let [content (.toByteArray buffer)]
      {:status 200
       :body (ByteArrayInputStream. content)
       :headers {"Content-Type" (content-type kind)
                 "Content-Encoding" "gzip"
                 "Content-Length" (str (alength content))}})))

(def template (enlive/html-resource "components/template.html"))

(defn parse-html-resource
  "Parse header, nav, footer and section.page elements from html resource and
   append them to context. The context is a map containing lists of elements
   under keys :header, :nav, :footer and :page."
  [c resource]
  (let [h (enlive/html-resource resource)]
    (debug "LOAD: %s" resource )
    (assoc c
           :header (concat (:header c) (enlive/select h [:header]))
           :nav    (concat (:nav c)    (enlive/select h [:nav]))
           :footer (concat (:footer c) (enlive/select h [:footer]))
           :page   (concat (:page  c)  (enlive/select h [:section.page])))))

(defn compose-html [component]
  (let [c (reduce parse-html-resource {} (map (partial str "components/") (c/get-resources ui-components :html component)))]
    (spply str (enlive/emit* (-> template
                               (enlive/transform [:header] (constantly (:header c)))
                               (enlive/transform [:nav] (constantly (:nav c)))
                               (enlive/transform [:section] (:page c))
                               (enlive/transform [:footer] (constantly (:footer c)))
                               (enlive/transform [:link] (fn [e] (if (= "inject")))))))))
  
(compose-html :welcome)


(defn- strip-script-tags [r]
  (enlive/transform r [:script] (fn [e] (if (-> e :attrs :src) nil e))))

(defn- strip-css-tags [r]
  (enlive/transform r [:link] (fn [e] (if (not= (-> e :attrs :rel) "stylesheet") e))))




(defn write-html-header [out name]
  (.write out (.getBytes (format "<!--\n  ** %s\n-->\n\n" name)))
  out)



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

(defn compose-singlepage-html [c]
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

(defn gzip ^bytes [^bytes a]
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
        (if-let [r (.getResourceAsStream loader src)]
          (do
            (IOUtils/copy r buffer)
            (IOUtils/closeQuietly r)))))
    (let [content (gzip (.toByteArray buffer))]
      {:status 200
       :body (ByteArrayInputStream. content)
       :headers {"Content-Type" content-type
                 "Content-Encoding" "gzip"
                 "Content-Length" (str (alength content))}})))

(defn js-path [src]
  (if (or (.startsWith src "js/") (.startsWith src "lib/"))
    (str "public/" src)
    src))

(defn css-path [href]
  (if (.startsWith href "css/" )
    (str "public/" href)
    href))

(defn compose-singlepage-js [name]
  (compose-singlepage-resources 
    name 
    (fn [r] (map #(js-path (-> % :attrs :src)) (script-tags r))) "application/javascript"))

(defn compose-singlepage-css [name]
  (compose-singlepage-resources 
    name 
    (fn [r] (map #(css-path (-> % :attrs :href)) (css-tags r))) "text/css"))
