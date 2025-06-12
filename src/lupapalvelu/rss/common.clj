(ns lupapalvelu.rss.common
  (:require [lupapalvelu.xml.util :as xml-util]
            [sade.date :as date])
  (:import [jakarta.xml.bind JAXBContext]
           [lupapalvelu.rss.base Category
                                 Guid
                                 ObjectFactory
                                 Rss
                                 RssChannel
                                 RssItem]))

(set! *warn-on-reflection* true)

(def ^ObjectFactory factory (ObjectFactory.))

(def rss-jaxb-context (JAXBContext/newInstance ^Class/1 (into-array [Rss])))

(defn as-iterable
  "Helper function for producing type-hinted collections to satisfy e.g. `Iterable<? extends JAXBElement<Something>>`."
  (^Iterable [e1] [e1])
  (^Iterable [e1 e2] [e1 e2])
  (^Iterable [e1 e2 & elements] (into [e1 e2] elements)))

(defn- ->category
  [category]
  (-> (Category/builder)
      (.withValue category)
      .build))

(defn- ->guid
  [guid]
  (-> (Guid/builder)
      (.withValue guid)
      (.withIsPermaLink false)
      .build))

(defn- make-item
  "Create a JAXB document describing a RSS item"
  [{:keys [author
           category
           comments
           description
           ^Iterable extra-elements
           link
           pub-date
           title
           guid]}]
  (cond-> (RssItem/builder)
    (seq extra-elements) (.withTitleOrDescriptionOrLink extra-elements)
    author               (.addAuthor (as-iterable (.createRssItemAuthor factory author)))
    category             (.addCategory (as-iterable (.createRssItemCategory factory (->category category))))
    comments             (.addComments (as-iterable (.createRssItemComments factory comments)))
    ;; description or title are mandatory for e.g. W3C validator
    description          (.addDescription (as-iterable (.createRssItemDescription factory description)))
    ;; GUID is mandatory for e.g. W3C validator
    guid                 (.addGuid (as-iterable (.createRssItemGuid factory (->guid guid))))
    link                 (.addLink (as-iterable (.createRssItemLink factory link)))
    pub-date             (.addPubDate (as-iterable (.createRssItemPubDate factory (date/zoned-format pub-date :rfc :local))))
    title                (.addTitle (as-iterable (.createRssItemTitle factory title)))
    true                 .build))

(defn- make-channel
  "Create a JAXB document describing a RSS channel"
  [{:keys [copyright
           items
           lang
           last-build-date
           link
           pub-date
           title
           description
           ttl-minutes]}]
  (cond-> (RssChannel/builder)
    copyright       (.addCopyright (as-iterable (.createRssChannelCopyright factory copyright)))
    ;; items is mandatory to pass our XML schema validation
    true            (.withItem ^Iterable (mapv make-item items))
    lang            (.addLanguage (as-iterable (.createRssChannelLanguage factory (name lang))))
    last-build-date (.addLastBuildDate (as-iterable (.createRssChannelLastBuildDate factory (date/zoned-format last-build-date :rfc :local))))
    link            (.addLink (as-iterable (.createRssChannelLink factory link)))
    pub-date        (.addPubDate (as-iterable (.createRssChannelPubDate factory (date/zoned-format pub-date :rfc :local))))
    title           (.addTitle (as-iterable (.createRssChannelTitle factory title)))
    description     (.addDescription (as-iterable (.createRssChannelDescription factory description)))
    ttl-minutes     (.addTtl (as-iterable (.createRssChannelTtl factory (biginteger ttl-minutes))))
    true            .build))

(defn make-rss
  "Create a JAXB document describing a RSS feed"
  [{:keys [channel
           ^Iterable extra-elements]}]
  (cond-> (Rss/builder)
    true                 (.withVersion 2.0M)
    channel              (.withChannel (make-channel channel))
    (seq extra-elements) (.withAny extra-elements)
    true                 .build))

;; JAXB classes ignore any regex restrictions in the schema,
;; so we must also perform schema validation during marshalling to be sure
;; our output is valid
(def root-schema-path "RSS 2.0 schema path'" "rss/rss-2_0.xsd")
(def root-schema "RSS 2.0 schema instance'" (xml-util/load-schema root-schema-path))

(defn ->xml
  ([document]
   (->xml document {}))
  ([document options]
   (xml-util/->xml (merge
                     {:document      document
                      :jaxb-context  rss-jaxb-context
                      :pretty-print? false
                      ;; By default we just use the root RSS schema
                      :schema        root-schema}
                     options))))

(comment
  (-> (make-rss {:channel {:copyright       "Acme RSS Feeds Ltd. 2025 All rights reserved."
                           :items           [{:author      "user@example.com"
                                              :category    "examples"
                                              :comments    "http://localhost/comments"
                                              :description "i description"
                                              :link        "i link"
                                              :pub-date    (date/now)
                                              :title       "i title"}]
                           :lang            "fi"
                           :last-build-date (date/now)
                           :link            "channel link"
                           :pub-date        (date/now)
                           :title           "channel title"
                           :ttl-minutes     5}})
      (->xml {:pretty-print? true})
      println))
