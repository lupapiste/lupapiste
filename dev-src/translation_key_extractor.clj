(ns translation-key-extractor
  (:require [clojure.string :as s]))

(defn ^:private extract-translation-keys
  "Extracts keys from Docgen schema. Intent of this function is to reduce work amount, need for copy-paste and
  amount or errors when adding new Docgen schema translation into i18n txt file."
  [prefix schema-body]
  (let [group-like? (fn [s] (not (nil? (:body s))))
        body->prefixed-docgen-nodes (fn [prefix body] (map (fn [docgen-node] [prefix docgen-node]) body))
        flatten-prefixed-group-node (fn [[current-prefix group]]
                                      (let [new-prefix (str current-prefix "." (:name group))]
                                        (body->prefixed-docgen-nodes new-prefix (:body group))))
        prefixed-docgen-node->resource-key (fn [[parent-prefix docgen-node]]
                                             (let [prefix (str parent-prefix "." (:name docgen-node))]
                                               (keep identity
                                                     [(or
                                                        (:i18nkey docgen-node)
                                                        (cond-> prefix
                                                                (group-like? docgen-node) (str "._group_label")))
                                                      (when (:repeating docgen-node) (str prefix "._append_label"))
                                                      (:group-help docgen-node)])))

        initial-prefixed-schemas (body->prefixed-docgen-nodes prefix schema-body)]
    (distinct
      (loop [{:keys [keys remaining]} {:keys [(str prefix "._group_label")] :remaining initial-prefixed-schemas}]
        (let [new-keys (concat keys (mapcat prefixed-docgen-node->resource-key remaining))
              new-remaining (->>
                              remaining
                              (filter (comp group-like? second))
                              (map flatten-prefixed-group-node)
                              (mapcat identity))]
          (if (seq new-remaining)
            (recur {:keys new-keys :remaining new-remaining})
            new-keys))))))

(defn ^:private translation-keys->i18n-csv
  "Creates nicely formated cvs templates for the translation key list for each locale"
  [locales keys]
  (s/join "\n"
          (for [key keys
                locale locales]
            (format "\"%s\" \"%s\" \"\"" key locale))))

(defn docgen-body->i18n-csv
  "Generates resource csv for a docgen schema and list of locales"
  [schema-name schema-body locales]
  (->> (extract-translation-keys schema-name schema-body)
       (sort)
       (translation-keys->i18n-csv locales)))

(comment
  ; This is how to use it
  (do
    (require '[lupapalvelu.document.ymparisto-schemas :as yt])
    (println
      (docgen-body->i18n-csv "kirjallinen-vireillepano"
                             yt/kirjallinen-vireillepano
                             ["en" "fi"] ))))

