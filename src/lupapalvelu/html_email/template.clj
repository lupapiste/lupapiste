(ns lupapalvelu.html-email.template
  (:require [clojure.java.io :as io]
            [lupapalvelu.html-email.css :as css]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.user :as usr]
            [rum.core :as rum]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [selmer.filter-parser :refer [escape-html]]
            [selmer.parser :as parser]
            [taoensso.timbre :as timbre]))

(def TEMPLATE-RESOURCE-PATH "html-email-templates")
(def SUBJECT
  "Tag makes sure that a user input cannot be misinterpreted as subject"
  "<SUBJECT> ")
(def BASE-TEMPLATE {:id      :base-template
                    :context {:lupapiste (env/value :host)
                              :css       css/style-definition}})
(def PLACEHOLDER "placeholder.djhtml")

(env/in-dev (parser/cache-off!))

(defn localize!
  "Returns localized string or throws."
  [lang & terms]
  {:pre [(i18n/supported-lang? lang)]}
  (assert (i18n/has-term? lang terms) (str "Lang " lang " has no term: " terms))
  (i18n/localize lang terms))

(defn bad-tag! [tag]
  (timbre/error "Bad tag:" tag)
  (throw (ex-info  "Bad tag" tag)))

(defn- process-args
  "Make :args into keywords. Returns :args, :escape-fn map. :escape-fn is either
  `escape-html` (default) or `identity` (if the last arg is |safe). The |safe arg is
  omitted from returned :args."
  [args]
  (let [safe? (= "|safe" (last args))]
    {:escape-fn (if safe? identity escape-html)
     :args      (map (fn [a]
                       (cond-> a
                         (ss/starts-with a ":") (-> (subs 1) keyword)))
                     (cond-> args
                       safe? butlast))}))


(defn- get-in-ctx
  [context & path]
  (some->> path util/kw-path util/split-kw-path seq (get-in context)))

(defn loc-tag
  "Returns a localized string. Each argument can be either static (:arg) or context
  paths. If there is only one argument and it is context path, some shortcuts are
  supported. If the resolved term is function, then it is called with the current lang as
  argument. Result escaped as in Selmer (supports :safe). In addition, if the last arg is
  |safe, the result is not escaped (useful in subject)."
  [full-args {:keys [lang] :as context}]
  (let [{:keys [args escape-fn]
         }       (process-args full-args)
        [x & xs] args
        term     (cond
                   ;; Static l10n key
                   (every? keyword? args) args
                   ;; One context key, can be one of the shortcuts.
                   (nil? xs)              (let [v (get-in-ctx context x)]
                                            (case (keyword x)
                                              :municipality    [:municipality v]
                                              :operation       [:operations v]
                                              :attachment-type [:attachmentType v]
                                              v))
                   ;; Mixed path.
                   :else                  (map (fn [a]
                                                 (util/pcond->> a
                                                   string? (get-in-ctx context)))
                                               args))]
    (escape-fn (if (fn? term)
                 (term lang)
                 (localize! lang term)))))

(parser/add-tag! :loc loc-tag)

(defn app-tag
  "Gets information from application in `context`. The following (lonely) keyword arguments
  return localized string: :municipality, :operation and :state. So, for example:
   municipality -> 186
   :municipality -> Järvenpää

  Every non-special arguments keywords and strings are both both treated as paths. Thus,
  all of the following return (get-in context [:application :foo :bar]):
  :foo :bar
  foo :bar
  foo bar
  :foo.bar
  foo.bar

  Result escaped as in Selmer (supports :safe). In addition |safe arg works as in
  `loc-tag`."
  [full-args {:keys [lang application]}]
  (let [{:keys [args escape-fn]} (process-args full-args)
        [x & xs]                 args]
    (escape-fn (if (and (nil? xs) (keyword? x))
                 (case x
                   :municipality (localize! lang :municipality
                                            (:municipality application))
                   :operation    (localize! lang :operations
                                            (get-in application
                                                    [:primaryOperation :name]))
                   :state        (localize! lang (:state application))
                   (x application))
                 (get-in-ctx application args)))))

(parser/add-tag! :app app-tag)

(defn usr-tag
  "The argument can be either full, first or last. The result is the corresponding name
  information (full is first last) from the `user` in the context.  Result escaped as in
  `loc-tag`."
  [all-args {:keys [user]}]
  (let [{:keys [args escape-fn]}   (process-args all-args)
        {a :firstName b :lastName} user]
    (or (some-> (case (-> args first keyword)
                  :full  (ss/join-non-blanks " " [a b])
                  :first a
                  :last  b
                  nil)
                ss/blank-as-nil
                escape-fn)
        (bad-tag! {:tag  :usr
                   :args all-args}))))

(parser/add-tag! :usr usr-tag)

(defn greet-tag
  "The main greeting title. Format includes user's firstname, if available. Arguments are
  the greeting (e.g., Howdy). Thus, the two different formats could be:

  Howdy Randy,
  Howdy.

  Always escaped."
  [greeting {user :user}]
  (let [first-name (:firstName user)
        greeting   (ss/join-non-blanks " " greeting)]
    (->> (if first-name (str " " first-name ",") ".")
         (str greeting)
         escape-html)))

(parser/add-tag! :greet greet-tag)

(defn app-link-tag
  "Link to an application. The target application and the user are taken from the
  context. Parameter tab is the target tab (e.g., info, tasks, attachments, ...). If tab
  is :keyword it is used as it is, otherwise as key for the context.

  :tasks -> target tab is tasks
  tasks -> target tab is (:tasks context)

  Tab can be omitted (or resolve to nil). Result not escaped."
  [[tab & _] context]
  (let [tab          (cond
                       (nil? tab)               nil
                       (ss/starts-with tab ":") (subs tab 1)
                       :else                    (->> (keyword tab)
                                                     (util/split-kw-path)
                                                     (get-in context)))
        {:keys [infoRequest
                id]} (or (:application context)
                         (bad-tag! {:app-link "No application"}))
        page         (-> (:user context)
                         usr/applicationpage-for
                         (str "#!"))]
                     (->> [(env/value :host) "app" (:lang context "fi") page
                           (if infoRequest "inforequest" "application") id tab]
                          (map ss/->plain-string)
                          (ss/join-non-blanks "/" ))))

(parser/add-tag! :app-link app-link-tag)

(defn subject-block
  "Evaluates block content into one SUBJECT: line."
  [_ _ content]
  (->> (get-in content [:subject :content])
       ss/split-lines
       (map ss/trim)
       (ss/join-non-blanks " ")
       (str SUBJECT )))

(parser/add-tag! :subject subject-block :endsubject)

(defn- canonize-args
  "For some tags, :arg and arg are interchangeable, so we remove superflous :."
  [args]
  (map (fn [a]
         (cond-> a (ss/starts-with a ":") (subs 1)))
       args))

(defn- resolve-styles [styles style-ids]
  (some->> style-ids
           (map (fn [id]
                  (css/find-style styles id)))
           (apply merge)))

(defn style-attr-tag
  "Creates style attribute by merging the styles correspoding to the given ids. The style
  definitions are taken from context `styles` and the default styles (see
  `css/find-style`). NOTE: Do not confuse with the style tag."
  [style-ids {:keys [styles]}]
  (some->> style-ids
           canonize-args
           (resolve-styles styles)
           (map (fn [[k v]]
                  (str (name k) ":" (ss/->plain-string v))))
           (ss/join ";")
           (format "style=\"%s\"")))

(parser/add-tag! :style-attr style-attr-tag)

(defn button-block
  "Bulletproof button for emails (link in table cell). Parameters:

  `style-ids`: Style-ids, similar usage as in `style-attr-tag`. However, for each given
  style-id, two other styles (:style-id.td and :style-id.a) are resolved.

  `content`: Button link and text separated by whitespace.

  {% button primary}
  http://www.lupapiste.fi
  Lupapiste
  {% endbutton}"
  [style-ids {:keys [styles]} content]
  (let [style-ids   (canonize-args style-ids)
        td-style    (resolve-styles styles (map #(util/kw-path % :td) style-ids))
        a-style     (resolve-styles styles (map #(util/kw-path % :a) style-ids))
        [link text] (map ss/blank-as-nil
                         (some-> content :button :content
                                 ss/trim
                                 (ss/split #"\s+" 2)))]
    (assert (and link text) "Link or text cannot be nil.")
    (rum/render-static-markup [:table {:border      0 :cellpadding 0
                                       :cellspacing 0 :role        :presentation
                                       :style       {:border-collapse :separate
                                                     :line-height     "100%"}}
                               [:tr
                                [:td {:align  :center :role  :presentation
                                      :valign :middle :style td-style}
                                 [:a {:href link :target :_blank :style a-style}
                                  text]]]])))

(parser/add-tag! :button button-block :endbutton)

(defn- template-id->name
  "Template filename without directory part. The format is `template-id.lang.djhtml` where
  the lang part can be missing."
  ([template-id lang]
   (name (util/kw-path template-id lang :djhtml)))
  ([template-id]
   (template-id->name template-id nil)))

(defn render-template
  "Renders template. If `lang` is given it is expected to be part of the template name and
  it is also assoced to `context`. If the template source file does not exist, either
  throws or renders placeholder template. The latter happens only when
  `template-placeholder` feature is enabled. This is useful for local development and QA,
  since the localisations typically include some translation lag."
  ([template-id context lang]
   (let [filename (template-id->name template-id lang)]
     (cond
       (io/resource (str TEMPLATE-RESOURCE-PATH "/" filename))
       (parser/render-file filename
                           (util/assoc-when context :lang lang)
                           {:custom-resource-path TEMPLATE-RESOURCE-PATH})

       (env/feature? :template-placeholder)
       (parser/render-file PLACEHOLDER
                           (assoc context :filename filename)
                           {:custom-resource-path TEMPLATE-RESOURCE-PATH})

       :else (let [msg (format "Template file %s not found" filename)]
               (timbre/errorf msg)
               (throw (ex-info msg {}))))))
  ([template-id context]
   (render-template template-id context nil)))

(defn render-email-template
  "Renders HTML email template '`template-id`.`lang`.djhtml'. The used context is `context`
  with `lang`. Returns :subject, :body map. Fails if the template does not have EXACTLY
  one `subject` block."
  [template-id lang context]
  (let [subtract       (util/fn-> first (ss/split (re-pattern SUBJECT)) second)
        {:keys [subject
                body]} (->> (render-template template-id context lang)
                            ss/split-lines
                            (group-by #(if (ss/starts-with % SUBJECT) :subject :body)))]
    (assert (and (= 1 (count subject)) body)
            "Template must have one subject and some body.")
    {:subject (subtract subject)
     :body    (ss/trim (ss/join "\n" body))}))

(defn render-email
  "Renders the whole email and returns combined :subject, :body map, where body contains
  every required language version. The final body is rendered according to
  BASE-TEMPLATE or the :base-template given in the options."
  [{:keys [template-id lang] :as options} context]
  (let [{base-ctx :context
         base-id  :id} (:base-template options BASE-TEMPLATE)
        lang          (map keyword (flatten [lang]))
        versions       (zipmap lang
                               (map #(render-email-template template-id % context)
                                    lang))]
    {:subject (some #(get-in versions [% :subject]) i18n/supported-langs)
     :body    (render-template base-id (merge base-ctx versions))}))

(defn missing-templates
  "Returns a list of missing (l10n) template filenames (without folder part). Called from
  test. Also useful for double checking that every template has been translated before
  even attempting the build."
  []
  (let [id-parts (->> (io/resource TEMPLATE-RESOURCE-PATH)
                      io/as-file
                      file-seq
                      (map #(.getName (io/as-file %)))
                      (filter (partial re-matches #".*\.(fi|sv|en)\.djhtml"))
                      (map (util/fn->> (re-find #"^(.+)\.(fi|sv|en)\.djhtml") second)))]
    (for [lang i18n/supported-langs
          id   id-parts
          :let [filename (template-id->name id lang)]
          :when (not (io/resource (str TEMPLATE-RESOURCE-PATH "/" filename)))]
      filename)))
