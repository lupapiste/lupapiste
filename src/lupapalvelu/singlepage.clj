(ns lupapalvelu.singlepage
  (:require [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.env :as env]
            [lupapalvelu.components.core :as c])
  (:use [lupapalvelu.log]
        [lupapalvelu.components.ui-components :only [ui-components]])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPOutputStream]
           [org.apache.commons.io IOUtils]))

(defn write-header [out name]
  (.write out (.getBytes (format "\n/*\n * %s\n */\n\n" name)))
  out)

(defn compose-resource [kind component]
  (let [out (ByteArrayOutputStream.)]
    (doseq [src (c/get-resources ui-components kind component)]
      (if (fn? src)
        (.write (write-header out (str "fn: " 'src)) (.getBytes (src)))
        (with-open [resource (clojure.lang.RT/resourceAsStream nil (c/path src))]
          (IOUtils/copy resource (write-header out src)))))
    (.toByteArray out)))

(def utf8 (java.nio.charset.Charset/forName "UTF-8"))

(defn parse-html-resource [c resource]
  (let [h (enlive/html-resource resource)]
    (assoc c
      :header (concat (:header c) (enlive/select h [:header]))
      :nav    (concat (:nav c)    (enlive/select h [:nav]))
      :footer (concat (:footer c) (enlive/select h [:footer]))
      :page   (concat (:page  c)  (enlive/select h [:section.page])))))

(defn inject-content [t {:keys [header nav page footer]} component]
  (enlive/emit* (-> t
                  (enlive/transform [:body] (fn [e] (assoc-in e [:attrs :class] (name component))))
                  (enlive/transform [:header] (constantly (first header)))
                  (enlive/transform [:nav] (constantly (first nav)))
                  (enlive/transform [:section] (enlive/content page))
                  (enlive/transform [:footer] (constantly (first footer)))
                  (enlive/transform [:script] (fn [e] (if (= (-> e :attrs :src) "inject") (assoc-in e [:attrs :src] (str "/" (name component) ".js")) e)))
                  (enlive/transform [:link] (fn [e] (if (= (-> e :attrs :href) "inject") (assoc-in e [:attrs :href] (str "/" (name component) ".css")) e))))))

(defn compose-html [component]
  (let [out (ByteArrayOutputStream.)]
    (doseq [element (inject-content
                      (enlive/html-resource (c/path "template.html"))
                      (reduce parse-html-resource {} (map (partial str (c/path)) (c/get-resources ui-components :html component)))
                      component)]
      (.write out (.getBytes element utf8)))
    (.toByteArray out)))

(defn compose [kind component]
  (ByteArrayInputStream.
    (if (= :html kind)
      (compose-html component)
      (compose-resource kind component))))
