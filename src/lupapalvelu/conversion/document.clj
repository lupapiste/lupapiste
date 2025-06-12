(ns lupapalvelu.conversion.document
  (:require [clojure.set :as set]
            [lupapalvelu.conversion.schemas
             :refer [ConversionDocument]
             :as conv-sc]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]
            [schema-tools.coerce :as stc]
            [schema.core :as sc]))

(def ^:private coerse-document (stc/coercer ConversionDocument
                                            (some-fn stc/json-coercion-matcher
                                                     {conv-sc/LowerKeyword conv-sc/->lowerkeyword})))

(sc/defn ^:private ^:always-validate ->document :- (sc/maybe ConversionDocument)
  [m]
  (some-> m
          (dissoc :_sheriff-notes)
          (set/rename-keys {:_id :id})
          coerse-document))

(defn- args-map [[x & _ :as args]]
  (cond
    (map? x)    x
    (string? x) {:id x}
    :else       (apply hash-map args)))

(sc/defn ^:private new-document :- ConversionDocument
  "Creates and inserts new document to mongo."
  [organization-id :- ssc/NonBlankStr
   backend-id      :- ssc/NonBlankStr
   & args]
  (let [m        (conv-sc/destructure-permit-id backend-id)
        document (-> {:id           (mongo/create-id)
                      :organization organization-id
                      :backend-id   backend-id
                      :suffix       (conv-sc/->lowerkeyword (:tyyppi m))
                      :year         (:vuosi m)
                      :permit-type  (:permit-type m)
                      :tags         []}
                     (merge (args-map args))
                     util/strip-nils)]
    (mongo/insert :conversion document)
    document))

(sc/defn document :- (sc/maybe ConversionDocument)
  ([query-or-id]
   (->document (mongo/select-one :conversion
                                 (if (string? query-or-id)
                                   {:_id query-or-id}
                                   query-or-id))))
  ([organization-id backend-id]
   (document  {:organization organization-id
               :backend-id   backend-id})))

(def FindOption
  "Options for `find-documents`.

   `:exclude-errors`: Omit documents that have either conversion or KuntaGML error.

   `:exclude-foremen`: Omit documents that correspond to foreman applications. This is
  determined by `:suffix` value: `:tjo` suffix denotes foreman.

   `:target-order`: Make sure that the result documents are returned in the same order as
  the targets are listed in the configuration. Typically this is needed only in those
  cases, where the `backend-ids` are provided in the configuration EDN file. The option
  does not do anything, if the configuration does not have explicit targets.

  `:lp-id-order`: Sort the results in the ascending order by the `LP-id` field. The option
  does not do anything with an active `:target-order`."
  (sc/enum :exclude-errors :exclude-foremen :target-order :lp-id-order))

(sc/defn ^:private ^:always-validate find-options->map
  [opts :- [FindOption]]
  (zipmap opts (repeat true)))

(sc/defn find-documents :- [ConversionDocument]
  "Returns documents that match the given configuration `cfg` and an optional
  `extra-query` parameters. See `FindOption` for a list of additional options."
  ([{:keys [organization-id permit-type tags targets years limit]
     :as   cfg} :- {:organization-id ssc/NonBlankStr
                    sc/Keyword       sc/Any}
    extra-query
    & opts :- [FindOption]]
   (let [{:keys [exclude-errors exclude-foremen target-order
                 lp-id-order]} (find-options->map opts)
         ids                   (some->> targets
                                        (keep (fn [{:keys [id filename]}]
                                                (or id
                                                    (:backend-id (conv-util/find-xml-ids permit-type
                                                                                         filename)))))
                                        seq)
         target-order          (and target-order ids)
         lp-id-order           (and lp-id-order (not target-order))
         id-query              (case (count ids)
                                 0 nil
                                 1 {:backend-id (first ids)}
                                 {:backend-id {$in ids}})

         suffix-query          (when exclude-foremen
                                 {:suffix {$ne :tjo}})

         tag-maps              (some->> (seq tags)
                                        (map #(hash-map :tags %)))
         tags-query            (case (count tag-maps)
                                 0 nil
                                 1 (first tag-maps)
                                 {$and tag-maps})
         range-query           (when-let [[from to] (:range cfg)]
                                 {:n {$gte from $lte to}})
         years-query           (case (count years)
                                 0 nil
                                 1 {:year (first years)}
                                 {:year {$in years}})
         query                 (merge (util/assoc-when {:organization organization-id}
                                                       :permit-type permit-type)
                                      id-query
                                      suffix-query
                                      tags-query
                                      range-query
                                      years-query
                                      extra-query
                                      (when exclude-errors
                                        {:conversion-error {$exists false}
                                         :kuntagml.error   {$exists false}}))
         order-fn              (cond
                                 target-order
                                 (let [order-map (zipmap ids (range))]
                                   (util/fn->> :backend-id order-map))

                                 lp-id-order :LP-id)
         ;; Withe `order-fn`, the limit option cannot be part of the
         ;; mongo options, since mongo results are not ordered.
         take-limit?           (and order-fn limit)
         mongopts              (cond-> {}
                                 (and limit (not order-fn)) (assoc :limit limit)
                                 target-order               (assoc :projection [:backend-id])
                                 lp-id-order                (assoc :projection [:LP-id]))]
     ;; With `order-fn` the results are are gathered in two passes, since we want to do the sorting in
     ;; Clojure while avoiding memory overhead.
     (cond->> (mongo/select-options :conversion query mongopts)
       order-fn       (sort-by order-fn)
       take-limit?    (take limit)
       order-fn       (map (comp document :id))
       (not order-fn) (map ->document))))
  ([configuration]
   (find-documents configuration nil)))

(defn find-converted-documents
  "Convenience function that limits the query to documents that have been successfully
  converted without errors."
  ([cfg extra-query & opts]
   (apply find-documents
          cfg
          (assoc extra-query :converted true)
          (distinct (cons :exclude-errors opts))))
  ([cfg] (find-converted-documents cfg nil)))

(sc/defn ^:private ^:always-validate history-entry :- conv-sc/HistoryEntry
  [op]
  {:ts (now) :op op})

(defn- history-update
  ([{:keys [history]} op mongo?]
   (let [entry (history-entry op)]
     (if mongo?
       {$push {:history entry}}
       {:history (util/conjv history entry)})))
  ([m op] (history-update m op true)))

(defn tags-update
  ([{:keys [tags]} mongo?]
   (when (seq tags)
     (if mongo?
       {$addToSet {:tags {$each tags}}}
       {:tags tags})))
  ([m] (tags-update m true)))

(def ^:private id-arg (sc/pred #(ss/not-blank? (:id % %))
                               "Conversion document id"))

(sc/defn ^:always-validate update-document :- ConversionDocument
  [document-or-id :- id-arg updates]
  (let [doc-id (:id document-or-id document-or-id)]
    (->document (mongo/update-one-and-return :conversion
                                             {:_id doc-id}
                                             updates))))

(sc/defn ^:always-validate set-conversion-error
  [document-or-id :- id-arg {:keys [code text exception xml-error]}]
  (let [doc-id    (:id document-or-id document-or-id)
        code      (or code
                      (some-> exception ex-data :conversion-error)
                      :unknown)
        xml-error (ss/blank-as-nil xml-error)
        text      (or (ss/blank-as-nil text)
                      (some->> exception ex-message ss/blank-as-nil)
                      xml-error)]
    (some->> text
             (log/errorf "Conversion error for doc %s: %s" doc-id ))
    (mongo/update-by-id :conversion
                        doc-id
                        {$set (util/assoc-when
                                {:conversion-error (util/strip-nils {:code code
                                                                     :text text})}
                                :kuntagml.error xml-error)})))

(sc/defn ^:always-validate clear-conversion-error
  [document-or-id :- id-arg]
  (mongo/update-by-id :conversion
                      (:id document-or-id document-or-id)
                      {$unset {:conversion-error true}}))

(defn- store-upsert [{:keys [document organization-id
                             backend-id]
                      :as   m}]
  {:pre [(:kuntagml m)]}
  (let [fields (select-keys m (cond-> [:conversion-error :kuntagml]
                                (not document) (conj :n)))]
    (if document
      (update-document document
                       (merge {$set fields}
                              (history-update document :store)
                              (tags-update m)))
      (new-document organization-id
                    backend-id
                    (merge fields
                           (history-update m :store false)
                           (tags-update m false))))))

(defn- location-upsert
  "Stores location information into the document. `op` is either `:location` (default) or
  `:fix-locations`)."
  ([{:keys [document location]} op]
   (let [fix?       (= op :fix-locations)
         converted? (:converted document)]
     (update-document document
                      (merge {$set (cond-> {:location location}
                                     (and fix? converted?)
                                     (assoc :reset-location (select-keys location [:x :y])))}
                             (history-update document op)))))
  ([m] (location-upsert m :location)))

(defn- convert-upsert
  "This is called only for an at least somewhat succesful conversion. Otherwise, the
  failures are handled by `set-conversion-error`."
  [m]
  (update-document m (merge {$set   (merge (select-keys m [:reset-state
                                                           :reset-location])
                                           {:converted true
                                            :app-links []})
                             $unset {:conversion-error true}}
                            (history-update m :convert))))

(defn- app-links-upsert
  "Initializes `app-links` (and optionally `reset-state`). Called at the end of
  conversion. The actual linking steps are elsewhere. Since this can be seen as a part of
  `convert`, the history is not updated"
  [{:keys [app-links reset-state] :as m}]
  ;; `distinct` works with nil as well
  (update-document m {$set (util/assoc-when {:app-links (distinct app-links)}
                                            :reset-state reset-state)}))

(defn- reset-state-upsert
  "(Re)initializes the conversion reset state. Separate upsert function is needed, since the
  state changes during the conversion depending whether the application has verdicts or
  not. Does not update history."
  [m]
  (update-document m {$set {:reset-state (:reset-state m)}}))

(defn- fix-history-upsert [m]
  (update-document m (merge {$set {:reset-state (:reset-state m)}}
                            (history-update m :fix-history))))

(defn- link-upsert [m]
  (update-document m (merge {$set {:linked true}}
                            (history-update m :link))))

(defn- link-repair-upsert [m]
  (update-document m (history-update m :link-repair)))

(defn- send-upsert [m]
  (update-document m (history-update m :send)))

(defn- tagset [tags]
  (->> (ss/trimwalk tags)
       (filter ss/not-blank?)
       set))

(defn- tag-upsert [{:keys [add remove n document]}]
  (let [old-tags (tagset (:tags document))
        new-tags (cond-> old-tags
                   add    (set/union (tagset add))
                   remove (set/difference (tagset remove)))
        m        (cond-> {}
                   (not= old-tags new-tags) (assoc :tags (sort new-tags))
                   n                        (assoc :n n))]
    (cond-> document
      (not-empty m)
      (update-document {$set m}))))

(defn- clean-upsert [m]
  (update-document m (merge {$unset {:linked           true
                                     :conversion-error true
                                     :converted        true
                                     :reset-state      true
                                     :reset-location   true
                                     :app-links        true}}
                            (history-update m :clean))))

(defn- history-upsert [m op]
  (update-document m (history-update m op)))

(sc/defn ^:always-validate upsert-document :- ConversionDocument
  [op & args]
  (let [m (args-map args)]
    (case op
      :store         (store-upsert m)
      :location      (location-upsert m)
      :fix-locations (location-upsert m :fix-locations)
      :convert       (convert-upsert m)
      :reset-state   (reset-state-upsert m)
      :app-links     (app-links-upsert m)
      :link          (link-upsert m)
      :link-repair   (link-repair-upsert m)
      :send          (send-upsert m)
      :tag           (tag-upsert m)
      :clean         (clean-upsert m)
      :fix-history   (fix-history-upsert m)
      (history-upsert m op))))

(defn read-xml
  "Returns parsed KuntaGML message for document. Throws if xml is not available or
  `kuntagml.error` exists (unless `force?` option is given). Keyword options:

   `:strip?` Strip namespaces from parsed xml (default true)

   `:force?` Ignore `kuntagml.error` (default false)"
  [{{:keys [xml error]} :kuntagml} & {:keys [strip? force?]
                                      :or   {strip? true force? false}}]
  (cond
    (and error (not force?)) (throw (ex-info "Document KuntaGML error" {:error error}))
    (ss/blank? xml)          (throw (ex-info "KuntaGML not found" {}))
    :else                    (cond-> (xml/parse-string xml "utf8")
                               strip? strip-xml-namespaces)))

(defn- enrich-with-LupaTunnus [application-id node]
  (update node :content
          (partial mapcat (fn [element]
                            (cond-> [element]
                              (= (:tag element) :yht:kuntalupatunnus)
                              (conj {:tag     :yht:muuTunnustieto
                                     :content [{:tag     :yht:MuuTunnus
                                                :content [{:tag     :yht:tunnus
                                                           :content [application-id]}
                                                          {:tag     :yht:sovellus
                                                           :content ["Lupapiste"]}]}]}))))))

(defn xml-with-application-id
  "Adds `conversion-doc` application id to the document XML. Returns the augmented XML as
  string or throws. The (optional) `application-id` parameter can override the conversion
  document LP-id."
  ([{:keys [permit-type] :as conversion-doc} application-id]
   (-> (read-xml conversion-doc :strip? false)
       (enlive/at [(case permit-type
                     "R" :rakval:luvanTunnisteTiedot
                     "P" :ppst:luvanTunnistetiedot)
                   :yht:LupaTunnus]
                  (partial enrich-with-LupaTunnus application-id))
       xml/element-to-string))
  ([{app-id :LP-id :as conversion-doc}]
   (xml-with-application-id conversion-doc app-id)))

(defn read-application
  "Returns the successfully converted application that corresponds to the `conversion-doc` or
  nil. Note that the function returns nil for those rare older applications that are
  deemed erroneous after the initial conversion."
  ([{:keys [converted conversion-error LP-id]} projection]
   (when (and converted LP-id (not conversion-error))
     (mongo/select-one :applications
                       {:_id LP-id :facta-imported true}
                       (or projection {}))))
  ([conversion-doc]
   (read-application conversion-doc nil)))

(defn- xml-year
  "The earliest KuntaGML Tilamuutos pvm date that matches vireillä state and is in the
  past. Nil, if none found."
  [conversion-doc]
  (try
    (some->> (read-xml conversion-doc)
             conv-util/kuntagml-states
             (util/filter-by-key :tila "vireillä")
             (keep (comp date/timestamp :pvm))
             (filter #(date/before? % (date/now)))
             seq
             (apply min)
             date/year)
    (catch Exception _ nil)))

(defn resolve-application-id
  "Resolves (and stores) `LP-id` field. The `year` field is double-checked against
  `vireillä` Tilamuutos in the document KuntaGML. Returns updated document."
  [{doc-year :year :as conversion-doc} municipality]
  {:pre [(not (:LP-id conversion-doc))]}
  (let [year (or (some->> (xml-year conversion-doc) (max doc-year))
                 doc-year)
        m    (cond-> {:LP-id (conv-util/make-converted-application-id municipality year)}
               (not= year doc-year) (assoc :year year))]
    (update-document conversion-doc {$set m})))
