(ns lupapalvelu.rss.bulletin
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.rss.api :as api]
            [lupapalvelu.rss.common :as rss-common]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.xml.util :as xml-util]
            [monger.operators :refer [$exists $group $in $last $lt $match $or $project $sort $unwind]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [java.time Duration]
           [lupapalvelu.rss.lupapiste ObjectFactory]))

(set! *warn-on-reflection* true)

;;; Organization configs wrt. bulletins

(defn- set-title
  [m bulletin-process lang settings]
  (assoc-in m [bulletin-process lang :title] (ss/join-non-blanks " - "
                                                                 [(:heading1 settings)
                                                                  (:heading2 settings)])))

(defn build-org-configs
  [organizations]
  (->> organizations
       (map (fn [{:keys [id local-bulletins-page-settings local-kuulutus-page-settings]}]
              (reduce
                (fn [m lang]
                  (let [j-settings (get-in local-bulletins-page-settings [:texts lang])
                        k-settings (get-in local-kuulutus-page-settings [:texts lang])
                        j-defined? (-> j-settings :heading1 ss/not-blank?)
                        k-defined? (-> k-settings :heading1 ss/not-blank?)]
                    (cond-> m
                      j-defined?                        (set-title :julkipano lang j-settings)
                      k-defined?                        (set-title :kuulutus lang k-settings)
                      (and j-defined? (not k-defined?)) (set-title :kuulutus lang j-settings))))
                {:id        id
                 :julkipano {:fi {:title "Julkipanot"}
                             :sv {:title "Anslag"}}
                 :kuulutus  {:fi {:title "Kuulutukset"}
                             :sv {:title "Kungörelser"}}}
                [:fi :sv])))
       (map (fn [{:keys [id] :as entry}]
              [id (dissoc entry :id)]))
       (into {})))

(defn- query-enabled-organizations!
  "Find organizations with bulletins enabled and construct a map of language labels to use in the feed"
  []
  (let [now          (date/now)
        configs      (->> (mongo/select :organizations
                                        {:scope.bulletins.enabled true
                                         $or                      [{:scope.bulletins.enabled-at {$exists false}}
                                                                   {:scope.bulletins.enabled-at {$lt (date/timestamp (date/now))}}]}
                                        [:local-bulletins-page-settings
                                         :local-kuulutus-page-settings])
                          build-org-configs)
        elapsed-time (- (date/timestamp (date/now)) (date/timestamp now))]
    (log/info (format "Organization config built for RSS feed(s). Elapsed time: %s ms"
                      elapsed-time))
    configs))

;; Similar to `lupapalvelu.web/name->bulletin-redirect-conf`, we don't expect the
;; organization bulletin configs to change very often and we don't want to query the db
;; every time someone accesses the feed(s)
(def ^:private org-ttl-ms (.toMillis (Duration/ofSeconds 10)))

(def organization-configs-cached (memo/ttl query-enabled-organizations! :ttl/threshold org-ttl-ms))

(defn- get-organization-config
  [org-id]
  (get (organization-configs-cached) org-id))

(defn- supported-organization?
  "Use the cache to check if the target organization exists and has bulletins enabled"
  [org-id]
  (boolean (get-organization-config org-id)))

;;; Query bulletins for feed

(defn- bulletin->version
  [{:keys [versions] :as bulletin}]
  (assoc versions :id (:_id bulletin)))

(defn post-process-bulletin
  [bulletin]
  (-> bulletin
      bulletin->version))

(def projected-fields #{:versions.address
                        :versions.appealPeriodEndsAt
                        :versions.appealPeriodStartsAt
                        :versions.application-id
                        :versions.bulletinOpDescription
                        :versions.modified
                        :versions.primaryOperation
                        :versions.propertyId
                        :versions.id
                        :versions.attachment-id
                        :versions.verdictData
                        :versions.verdictDate
                        :versions.municipality})

(defn- make-pipeline
  [{:keys [now
           org-id
           bulletin-process
           municipality]}]
  {:pre [now org-id bulletin-process]}
  (let [query (merge {:versions.organization org-id
                      :versions.category     {$in ["p" "r"]}}
                     (if (util/=as-kw :julkipano bulletin-process)
                       {:versions.bulletinProcess {$in [bulletin-process nil]}}
                       {:versions.bulletinProcess bulletin-process})
                     (when municipality
                       {:versions.municipality municipality}))]
    [{$match (bulletins/versions-elemMatch now)}
     {$unwind {:path "$versions"}}
     {$match (bulletins/version-elemMatch now)}
     {$group {:_id             "$_id"
              :versions        {$last "$$ROOT.versions"},
              :modified        {$last "$$ROOT.modified"},
              :address         {$last "$$ROOT.address"},
              :_applicantIndex {$last "$$ROOT._applicantIndex"}}}
     {$match query}
     {$project (zipmap projected-fields (repeat 1))}
     {$sort (array-map :versions.modified -1)}]))

(defn query-bulletins!
  [options]
  (->> (mongo/aggregate "application-bulletins" (make-pipeline options) true)
       (mapv post-process-bulletin)))

(comment
  (query-bulletins! {:now              (date/now)
                     :org-id           "753-R"
                     :bulletin-process :julkipano}))

;;; Construct bulletin feed

(defn- channel-link
  [system org-id bulletin-process lang municipality]
  (format "%s/rss/%s?organization=%s&lang=%s%s"
          (components/system->host system)
          (name bulletin-process)
          org-id
          (name lang)
          (if municipality
            (str "&municipality=" municipality)
            "")))

(defn- bulletin-link
  [system org-id bulletin-id lang]
  (format "%s/app/%s/local-bulletins?organization=%s#!/r-bulletin/%s"
          (components/system->host system)
          (name lang)
          org-id
          bulletin-id))

(defn- attachment-link
  [system bulletin-id]
  (format "%s/api/raw/download-bulletin-doc?bulletinId=%s"
          (components/system->host system)
          bulletin-id))

(def ^ObjectFactory factory (ObjectFactory.))

(defn- make-items
  [{:keys [lang org-id system]}
   {:keys [address
           appealPeriodEndsAt
           appealPeriodStartsAt
           application-id
           attachment-id
           bulletinOpDescription
           id
           modified
           primaryOperation
           propertyId
           verdictData
           verdictDate
           municipality]}]
  (let [section       (:section verdictData)
        verdict-giver (:contact verdictData)
        operation     (i18n/localize lang :operations (:name primaryOperation))
        extra         (cond-> [(.createToimenpideteksti factory operation)]
                        application-id       (conj (.createLupatunnus factory application-id))
                        address              (conj (.createRakennuspaikka factory address))
                        ;; Move the start and end timestamps to the start and end of the day so that they match
                        ;; how we already decide if a bulletin is visible or not.
                        appealPeriodStartsAt (conj (.createJulkaisuAlkaa factory (-> appealPeriodStartsAt
                                                                                     date/start-of-day
                                                                                     (date/zoned-format :rfc :local))))
                        appealPeriodEndsAt   (conj (.createJulkaisuPaattyy factory (-> appealPeriodEndsAt
                                                                                       date/end-of-day
                                                                                       (date/zoned-format :rfc :local))))
                        propertyId           (conj (.createKiinteistotunnus factory propertyId))
                        verdictDate          (conj (.createPaatosPvm factory (date/zoned-format verdictDate :rfc :local)))
                        section              (conj (.createPaatoksenPykala factory section))
                        verdict-giver        (conj (.createPaattaja factory verdict-giver))
                        attachment-id        (conj (.createAsiakirjaLink factory (attachment-link system id)))
                        municipality         (conj (.createKuntanumero factory municipality)))]
    {:link           (bulletin-link system org-id id lang)
     :guid           id
     :title          operation
     :description    bulletinOpDescription
     :pub-date       modified
     :extra-elements extra}))

(defn- latest-published
  [bulletins]
  (some->> (map :modified bulletins)
           (apply util/max-key identity)
           date/zoned-date-time))

(defn- empty-items
  "Although the RSS spec allows zero items,
  the schema we use requires at least one item to avoid non-deterministic XML
  To fix this we add an \"empty\" item to items"
  [{:keys [bulletin-process lang]}]
  (let [title (case bulletin-process
                :kuulutus  (i18n/localize lang :bulletin.rss.no-kuulutus)
                :julkipano (i18n/localize lang :bulletin.rss.no-julkipano))]
    [{:title title
      :guid  "no-data"}]))

(defn build-feed-content
  [{:keys [system bulletins lang now org-id bulletin-process org-config municipality]}]
  {:channel (util/assoc-when
              {:copyright       "Cloudpermit Oy"
               :description     (get-in org-config [bulletin-process lang :title])
               :items           (if (seq bulletins)
                                  (mapv (partial make-items (util/mmap lang org-id system)) bulletins)
                                  (empty-items (util/mmap lang bulletin-process)))
               :last-build-date now
               :lang            lang
               :link            (channel-link system org-id bulletin-process lang municipality)
               :title           (get-in org-config [bulletin-process lang :title])}
              :pub-date (latest-published bulletins))})

(def extension-schema-path "Lupapiste extensions to RSS 2.0 schema'" "rss/lupapiste_extensions.xsd")

(def schemas (xml-util/merge-schema-paths [rss-common/root-schema-path
                                           extension-schema-path]))

(defn produce-content!
  "Produces the XML/RSS response."
  ;; Instruct `core.memoize` to only care about certain keys
  {::memo/args-fn (fn [args]
                    (-> (first args)
                        (select-keys #{:bulletin-process :org-id :lang :municipality})))}
  [{:keys [system bulletin-process org-id lang municipality]}]
  {:pre [(map? system)]}
  (try
    (let [now          (date/now)
          args         (util/mmap {:bulletins  (query-bulletins! (util/mmap org-id bulletin-process now municipality))
                                   :org-config (get-organization-config org-id)}
                                  org-id now lang bulletin-process municipality system)
          xml          (-> args
                           build-feed-content
                           rss-common/make-rss
                           (rss-common/->xml {:schema-prefixes {"https://www.lupapiste.fi/rss/extensions" "lupapiste"}
                                              :schema          schemas}))
          elapsed-time (- (date/timestamp (date/now)) (date/timestamp now))]
      (log/info (format "RSS-feed created for organization: %s, %s Bulletin(s) created, Elapsed time: %s ms"
                        org-id
                        (count (:bulletins args))
                        elapsed-time))
      {;; TODO: compress with gzip
       :xml     xml
       ;; TODO: Add these to the HTTP headers (etag etc.) for caching
       :created (date/timestamp now)
       :hash    (str (hash xml))})
    (catch Exception e
      (log/error e "Failed to construct RSS feed")
      {:status :error})))

(comment
  (produce-content! {:bulletin-process :julkipano
                     :org-id           "753-R"
                     :lang             :fi}))

(def ^:private rss-ttl-ms (.toMillis (Duration/ofSeconds 10)))

(def produce-cached (memo/ttl #'produce-content! :ttl/threshold rss-ttl-ms))

(defn valid-municipalities [org-id]
  (->> (mongo/by-id :organizations org-id [:scope.municipality])
       :scope
       (map :municipality)
       set))

(def cached-valid-municipalities (memo/ttl valid-municipalities :ttl/threshold rss-ttl-ms))

(defn get-data
  "Query the cache(s) for the latest feed data"
  [{{:keys [organization lang municipality]} :params system :system}
   bulletin-process]
  {:pre [(map? system)]}
  (log/with-logging-context
    {:organizationId  organization
     :lang            lang
     :bulletinProcess bulletin-process}
    (cond
      (nil? organization)                          {:status :not-found}
      (not-any? #{(keyword lang)} [:fi :en :sv])   {:status :not-found}
      (not (supported-organization? organization)) {:status :not-found}

      ;; If municipality filter is used, it should be a municipality configured for the chosen org
      (and (some? municipality)
           (not (contains? (cached-valid-municipalities organization) municipality)))
      {:status :not-found}

      :else                                        (merge {:status :ok}
                                                          (produce-cached
                                                            {:system           system
                                                             :bulletin-process bulletin-process
                                                             :org-id           organization
                                                             :municipality     municipality
                                                             :lang             (keyword lang)})))))

(defmethod api/rss-content "julkipano"
  [options]
  (get-data options :julkipano))

(defmethod api/rss-content "kuulutus"
  [options]
  (get-data options :kuulutus))


(comment
  (-> (make-rss {:channel {:title     "c title"
                           :link      "c link"
                           :copyright "Cloudpermit Oy"
                           :items     [{:title          "i title"
                                        :link           "i link"
                                        :description    "i descr"
                                        :extra-elements [(.createLupatunnus factory "LP-123-2025-00001")
                                                         (.createRakennuspaikka factory "Testikatu 1")]}]}})
      (rss-common/->xml {:schema-prefixes {"http://www.lupapiste.fi/rss/extensions" "lupapiste"}})
      println))


(when (env/dev-mode?)
  (defmethod api/rss-content "bulletin-clear-cache"
    [_options]
    (memo/memo-clear! organization-configs-cached)
    (memo/memo-clear! produce-cached)
    {:status :bulletin-cache-cleared}))
