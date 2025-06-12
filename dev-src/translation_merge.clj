(ns translation-merge
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [sade.util :refer [fn->>]])
  (:import [java.io BufferedReader]))

(defn merge-translations
  "Merges Swedish translations from file 'translations-file-name' into file
  'orig-localization-file-name' and outputs result in file 'output-file-name'.
  If Swedish translation for key is provided it is merged right after Finnish
  translation with same key. Duplicate keys are also removed."
  [orig-localization-file-name translations-file-name output-file-name]
  {:pre [(not= orig-localization-file-name translations-file-name)
         (not= orig-localization-file-name output-file-name)
         (not= translations-file-name output-file-name)]}
  (with-open [^BufferedReader orig (io/reader orig-localization-file-name)
              ^BufferedReader tran (io/reader translations-file-name)
              out  (io/writer output-file-name)]
    (let [translations (loop [tr {}]
                         (if-let [l (.readLine tran)]
                           (->> (s/split l #"\s")
                                (first)
                                (#(assoc tr % l))
                                (recur))
                           tr))
          writed-keys (atom #{})]
      (loop []
        (when-let [l (.readLine orig)]
          (let [[k lang] (s/split l #"\s")]
            (when-not (@writed-keys [k lang])
              (swap! writed-keys conj [k lang])
              (.write out (str l \newline))
              (when (and (translations k) (= lang "\"fi\""))
                (swap! writed-keys conj [k "\"sv\""])
                (.write out (str (translations k) \newline)))))
          (recur)))
      (doto (->> (map first @writed-keys) (distinct) (filter translations))
        (#(println "Number of translations writed: " (count %)))
        (#(println "Writed keys: " %))))))

#_(merge-translations "resources/i18n/errors.txt"
                    "../../sv.txt"
                    "resources/i18n/errors_new.txt")
