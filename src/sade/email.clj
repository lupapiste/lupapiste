(ns sade.email
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
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

;; Timeout info need to be of Integer type or javax.mail api will not follow them.
;; ks. com.sun.mail.util.PropUtil.getIntProperty
(def config (-> (env/value :email)
              (select-keys [:host :port :user :pass :sender :ssl :tls :connectiontimeout :timeout])
              (update-in [:connectiontimeout] int)
              (update-in [:timeout] int)))

;; Dynamic for testing purposes only, thus no earmuffs
(def ^:dynamic blacklist (when-let [p (env/value :email :blacklist)] (re-pattern p)))

(defn blacklisted? [email]
  (boolean  (when (and blacklist email) (re-matches blacklist email))))

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
  [to subject & {:keys [plain html calendar attachments] :as args}]
  (assert (or plain html calendar) "must provide some content")
  (let [plain-body (when plain {:content plain :type "text/plain; charset=utf-8"})
        html-body  (when html {:content html :type "text/html; charset=utf-8"})
        calendar-body (when calendar {:content (:content calendar) :type (str "text/calendar; charset=utf-8; method=" (:method calendar))})
        body       (remove nil? [:alternative plain-body html-body calendar-body])
        body       (if (= (count body) 2)
                     [(second body)]
                     (vec body))
        attachments (when attachments
                      (for [attachment attachments]
                        (assoc (select-keys attachment [:content :filename]) :type :attachment)))
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

  (cond
    (ss/blank? to) (do
                     (error "Email could not be sent because of missing To field. Subject being: " subject)
                     true)

    (blacklisted? to) (do
                        (warnf "Ignoring message to %s bacause address matches blacklist %s" to blacklist)
                        false) ; false = pretend that the message was send

    :else (let [[plain html calendar] msg]
            (try
              (send-mail to subject :plain plain :html html :calendar calendar :attachments attachments)
              false
              (catch Exception e
                (error "Email failure:" e)
                (.printStackTrace e)
                true)))))

;;
;; templating:
;; -----------
;; Template naming follows the format [lang-]name.ext,
;; where the language part is optional. Also, if the prefixed template
;; is not found, the fallback is a plain template.

(defn- template-path [template-name lang]
  (str "email-templates/"
       (if lang
         (str lang "-" template-name)
         template-name)))

(defn find-resource
  [resource-name & [lang]]
  (if-let [resource (io/resource (template-path resource-name lang))]
    resource
    (if lang
      (find-resource resource-name)
      (throw (IllegalArgumentException. (str "Can't find mail resource: " resource-name))))))

(defn- slurp-resource [resource]
  (with-open [in (io/input-stream resource)]
    (slurp in)))

(defn fetch-template
  [template-name & [lang]]
  (slurp-resource (find-resource template-name lang)))

(defn fetch-html-template
  [template-name & [lang]]
  (enlive/html-resource (find-resource template-name lang)))

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

(defn unescape-at [s]
  (ss/replace s #"[\\]+@" "@"))

(defn apply-md-template [template context]
  (let [lang        (:lang context)
        master      (fetch-template "master.md" lang)
        header      (fetch-template "header.md" lang)
        body        (fetch-template template lang)
        footer      (fetch-template "footer.md" lang)
        html-wrap   (fetch-html-template "html-wrap.html" lang)
        rendered    (clostache/render master context {:header header :body body :footer footer})
        ; Avoid email links to be generated, see also https://github.com/theJohnnyBrown/endophile/issues/6
        escaped     (ss/replace rendered "@" "\\@")
        content     (endophile/to-clj (endophile/mp escaped))
        ]
    [(-> content ->str* ss/unescape-html unescape-at)
     (->> content enlive/content (enlive/transform html-wrap [:body]) endophile/html-string unescape-at)]))

(defn apply-html-template [template-name context]
  (let [lang     (:lang context)
        master   (fetch-html-template "master.html" lang)
        template (fetch-html-template template-name lang)
        html     (-> master
                     (enlive/transform [:style] (enlive/append (->> (enlive/select template [:style]) (map :content) flatten s/join)))
                     (enlive/transform [:.body] (enlive/append (map :content (enlive/select template [:body]))))
                     (endophile/html-string)
                     (clostache/render context))
        plain    (html->plain html)]
    [plain html]))

(defn apply-template [template context]
  (cond
    (ss/ends-with template ".md")    (apply-md-template template context)
    (ss/ends-with template ".html")  (apply-html-template template context)
    :else                            (throw (Exception. (str "unsupported template: " template)))))

