(ns sade.email
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [postal.core :as postal]
            [sade.env :as env]
            [sade.strings :as ss]
            [net.cgrand.enlive-html :as enlive]
            [endophile.core :as endophile]
            [clostache.parser :as clostache]
            [net.cgrand.enlive-html :as html]))

;;
;; Configuration:
;; ----------------

(def defaults
  (merge
    {:from     "\"Lupapiste\" <no-reply@lupapiste.fi>"
     :reply-to "\"Lupapiste\" <no-reply@lupapiste.fi>"}
    (select-keys (env/value :email) [:from :reply-to :bcc :user-agent])))

(def config (select-keys (env/value :email) [:host :port :user :pass :sender :ssl :tls]))

;;
;; Delivery:
;; ---------
;;

(declare deliver-email)

(when-not (env/value :email :dummy-server)
  (def deliver-email (fn [to subject body]
                      (assert (string? to) "must provide 'to'")
                      (assert (string? subject) "must provide 'subject'")
                      (assert body "must provide 'body'")
                      (let [message (merge defaults {:to to :subject subject :body body})
                            error (postal/send-message config message)]
                        (when-not (= (:error error) :SUCCESS)
                          error)))))

;;
;; Sending 'raw' email messages:
;; =============================
;;

(defn send-mail
  "Send raw email message. Consider using send-email-message instead."
  [to subject & {:keys [plain html attachments]}]
  (assert (or plain html) "must provide some content")
  (let [plain-body (when plain {:content plain :type "text/plain; charset=utf-8"})
        html-body  (when html {:content html :type "text/html; charset=utf-8"})
        body       (if (and plain-body html-body)
                     [:alternative plain-body html-body]
                     [(or plain-body html-body)])
        attachments (when attachments
                      (for [attachment attachments]
                        (assoc (select-keys attachment [:content :file-name]) :type :attachment)))
        body       (if attachments
                     (into body attachments)
                     body)]
    (deliver-email to subject body)))

;;
;; Sending emails with templates:
;; ==============================
;;

(declare apply-template)

(defn send-email-message
  "Sends email message using a template. Returns true if there is an error, false otherwise.
   Attachments as sequence of maps with keys :file-name and :content. :content as File object."
  [to subject msg & [attachments]]
  {:pre [subject msg (or (nil? attachments) (and (sequential? attachments) (every? map? attachments)))]}
  (if-not (ss/blank? to)
    (let [[plain html] msg]
      (try
        (send-mail to subject :plain plain :html html :attachments attachments)
        false
        (catch Exception e
          (error "Email failure:" e)
          (.printStackTrace e)
          true)))
    (do
      (error "Email could not be sent because of missing To field. Subject being: " subject)
      true)))

;;
;; templating:
;; -----------
;;

(defn find-resource [resource-name]
  (or (io/resource (str "email-templates/" resource-name)) (throw (IllegalArgumentException. (str "Can't find mail resource: " resource-name)))))

(defn fetch-template [template-name]
  (with-open [in (io/input-stream (find-resource template-name))]
    (slurp in)))

(defn fetch-html-template [template-name]
  (enlive/html-resource (find-resource template-name)))

(when-not (env/feature? :no-cache)
  (alter-var-root #'fetch-template memoize)
  (alter-var-root #'fetch-html-template memoize))

;;
;; Plain text support:
;; -------------------

(defmulti ->str (fn [element] (if (map? element) (:tag element) :str)))

(defn- ->str* [elements] (s/join (map ->str elements)))

(defmethod ->str :default [element] (->str* (:content element)))
(defmethod ->str :str     [element] (s/join element))
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
(defmethod ->str :hr      [element] "\n---------\n")
(defmethod ->str :blockquote [element] (-> element :content ->str* (s/replace #"\n{2,}" "\n  ") (s/replace #"^\n" "  ")))
(defmethod ->str :table      [element] (str \newline (->str* (filter map? (:content element))) \newline))
(defmethod ->str :tr         [element] (let [contents (filter #(map? %) (:content element))]
                                         (s/join \tab (map #(s/join (:content %)) contents))))


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
;; Apply template:
;; ---------------

(defn apply-md-template [template context]
  (let [master      (fetch-template "master.md")
        header      (fetch-template "header.md")
        body        (fetch-template template)
        footer      (fetch-template "footer.md")
        html-wrap   (fetch-html-template "html-wrap.html")
        rendered    (clostache/render master context {:header header :body body :footer footer})
        content     (endophile/to-clj (endophile/mp rendered))]
    [(-> content ->str* ss/unescape-html)
     (->> content enlive/content (enlive/transform html-wrap [:body]) endophile/html-string)]))

(defn apply-html-template [template-name context]
  (let [master    (fetch-html-template "master.html")
        template  (fetch-html-template template-name)
        html      (-> master
                    (enlive/transform [:style] (enlive/append (->> (enlive/select template [:style]) (map :content) flatten s/join)))
                    (enlive/transform [:.body] (enlive/append (map :content (enlive/select template [:body]))))
                    (endophile/html-string)
                    (clostache/render context))
        plain     (html->plain html)]
    [plain html]))

(defn apply-template [template context]
  (cond
    (ss/ends-with template ".md")    (apply-md-template template context)
    (ss/ends-with template ".html")  (apply-html-template template context)
    :else                            (throw (Exception. (str "unsupported template: " template)))))

