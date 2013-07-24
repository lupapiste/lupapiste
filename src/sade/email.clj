(ns sade.email
  (:use [sade.core]
        [clojure.tools.logging])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [postal.core :as postal]
            [sade.env :as env]
            [net.cgrand.enlive-html :as enlive]
            [endophile.core :as endophile]
            [clostache.parser :as clostache]))

(def ^:private from (get-in (env/get-config) [:email :from] "\"Lupapiste\" <lupapiste@lupapiste.fi>"))

(defn send-mail [to subject & {:keys [plain html]}]
  (assert to "must provide 'to'")
  (assert subject "must provide 'subject'")
  (assert (or plain html) "must provide some content")
  (let [plain-body (when plain {:content plain :type "text/plain; charset=utf-8"})
        html-body  (when html {:content html :type "text/html; charset=utf-8"})
        body       (if (and plain-body html-body)
                     [:alternative plain-body html-body]
                     [(or plain-body html-body)])
        error      (postal/send-message
                     (env/value :email)
                     {:from     from
                      :to       to
                      :subject  subject
                      :body     body})]
    (when-not (= (:error error) :SUCCESS)
      error)))

;;
;; email with templating:
;; ======================
;;

(defn find-resource [resource-name]
  (or (io/resource (str "email-templates/" resource-name)) (throw (IllegalArgumentException. (str "Can't find mail resource: " resource-name)))))

(defn fetch-template [template-name]
  (with-open [in (io/input-stream (find-resource template-name))]
    (slurp in)))

(when-not (env/dev-mode?)
  (def fetch-template (memoize fetch-template)))

;; Plain text support:
;; ===================

(defmulti ->str (fn [element] (if (map? element) (:tag element) :str)))

(defn- ->str* [elements] (s/join (map ->str elements)))

(defmethod ->str :default [element] (->str* (:content element)))
(defmethod ->str :str     [element] (s/join element))
(defmethod ->str :h1      [element] (str \newline (->str* (:content element)) \newline))
(defmethod ->str :h2      [element] (str \newline (->str* (:content element)) \newline))
(defmethod ->str :p       [element] (str \newline (->str* (:content element)) \newline))
(defmethod ->str :ul      [element] (->str* (:content element)))
(defmethod ->str :li      [element] (str \* \space (->str* (:content element)) \newline))
(defmethod ->str :a       [element] (str (->str* (:content element)) \: \space (get-in element [:attrs :href])))
(defmethod ->str :img     [element] "")
(defmethod ->str :br      [element] "")
(defmethod ->str :hr      [element] "\n---------\n")
(defmethod ->str :blockquote [element] (-> element :content ->str* (s/replace #"\n{2,}" "\n  ") (s/replace #"^\n" "  ")))

;; HTML support:
;; =============

(defn wrap-html [html-body]
  (let [html-wrap (enlive/html-resource (find-resource "html-wrap.html"))]
    (enlive/transform html-wrap [:body] (enlive/content html-body))))

;; Sending emails with templates:
;; ==============================

(defn apply-template [template context]
  (let [master    (fetch-template "master.md")
        header    (fetch-template "header.md")
        body      (fetch-template template)
        footer    (fetch-template "footer.md")
        rendered  (clostache/render master context {:header header :body body :footer footer})
        content   (endophile/to-clj (endophile/mp rendered))]
    [(->str* content) (endophile/html-string (wrap-html content))]))

(defn send-email-message [to subject template context]
  (assert (and to subject template context) "missing argument")
  (let [[plain html] (apply-template template context)]
    (send-mail to subject :plain plain :html html)))
