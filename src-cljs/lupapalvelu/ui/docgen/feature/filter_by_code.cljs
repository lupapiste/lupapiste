(ns lupapalvelu.ui.docgen.feature.filter-by-code
  "Replicates the old `filterByCode` functionality but without `emit`.

  Instead, we walk the schema and figure out which paths emit and which paths
  listen for the code changes. The listener(s) can then subscribe to the emitter
  and see the currently active code."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]))

(defn- emitter?
  "The emitter is expected to be `select` components"
  [{:keys [type emit]}]
  (and (= "select" type)
       emit
       (contains? (set emit) "filterByCode")))

(defn listener?
  "Listener(s) are expected to be any components with children (ie. with `body`s)"
  [{:keys [listen body]}]
  (and listen
       (seq body)
       (contains? (set listen) "filterByCode")))

(defn- collect-codes
  "Given an emitter, build a map of 'value'->'code'."
  [{:keys [body]}]
  (->> body
       (keep (fn [{:keys [name code]}]
               (when (and name code)
                 [name code])))
       (into {})))

(defn- collect-emitter-and-listeners
  [body path]
  (loop [results          {:listeners #{}}
         [entry & others] (when (seq body)
                            [{:path path
                              :body body}])]
    (if (nil? entry)
      results
      (let [{:keys [path body]} entry
            {:keys [emitter
                    listeners
                    entries]}   (reduce (fn [acc {:keys [body] :as element}]
                                          ;; loop through the children and decide if they are
                                          ;; emitters, listeners or elements with children
                                          (let [new-path (conj path (keyword (:name element)))]
                                            (cond-> acc
                                              (emitter? element)  (assoc :emitter {:path  new-path
                                                                                   :codes (collect-codes element)})
                                              (listener? element) (update :listeners conj new-path)
                                              (seq body)          (update :entries conj {:path new-path
                                                                                         :body body}))))
                                        {:listeners #{}
                                         :entries   []}
                                        body)
            results             (cond-> results
                                  emitter         (assoc :emitter emitter)
                                  (seq listeners) (update :listeners into listeners))
            others              (into others entries)]
        (if (seq others)
          (recur results others)
          results)))))

(defn- doc->emitter-and-listeners
  [document]
  (let [{:keys [emitter listeners]
         :as   results} (collect-emitter-and-listeners
                          (-> document :schema :body) [])]
    (when (and emitter (seq listeners))
      results)))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  {:db (assoc db ::filter-by-code (->> (:documents app)
                                       (keep (fn [{:keys [id] :as doc}]
                                               (when-let [results (doc->emitter-and-listeners doc)]
                                                 [id results])))
                                       (into {})))})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::filter-by-code)})

(rf/reg-sub ::filter-by-code :-> ::filter-by-code)

(rf/reg-sub ::doc-emitter-and-listeners
  :<- [::filter-by-code]
  (fn doc-listeners-inputfn [data [_ doc-id]]
    {:pre [doc-id]}
    (get data doc-id)))

(rf/reg-sub ::listen-for-code
  (fn listen-value-inputfn [[_ doc-id path]]
    {:pre [doc-id (vector? path)]}
    (rf/subscribe [::doc-emitter-and-listeners doc-id]))
  (fn [{:keys [emitter listeners]} [_ doc-id path]]
    ;; Are we a listener?
    (when (contains? listeners path)
      ;; Check the current value of the emitter and translate it into a code
      (let [{:keys [path codes]} emitter
            emitter-val          (<sub [::committed/backend-submitted-value doc-id path])]
        (get codes emitter-val)))))

(defn filter-body
  "Given a schema, return visible `body` elements"
  [{:keys [schema path doc-id]}]
  (let [body (:body schema)]
    (if (listener? schema)
      ;; If no code is available, show nothing. This slightly differs from the legacy docgen,
      ;; as it would display the previous choices if the user chose an empty value.
      (let [code (<sub [::listen-for-code doc-id (path/keywordify-path path)])]
        (filterv (fn [{:keys [codes]}]
                   ;; Some child fields might not participate in this
                   ;; feature (no `:codes` field), don't discard them.
                   (or (nil? codes)
                       (some #{code} codes))) body))
      body)))
