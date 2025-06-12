(ns lupapalvelu.conversion-test-util
  (:require [sade.env :as env]))

(defn ->xml
  "Transforms map `m` into xml. Unlike `sade.xml/map->xml` interprets sequential values as
  repeating elements. Tests for this are found in
  `lupapalvelu.conversion.kuntagml-converter-test`."
  [m]
  (mapcat (fn [[k v]]
            (cond
              (map? v)        [{:tag k :content (->xml v)}]
              (sequential? v) (map #(hash-map :tag k :content (->xml %)) v)
              :else           [{:tag k :content [(str v)]}]))
          m))

(defmacro with-proxy
  "Substitutes Sade environment value `:conversion.proxy` with `data` for `body`."
  [data & body]
  `(let [old-fn# env/value]
     (with-redefs
       [env/value (fn [& args#]
                    (if (= args# [:conversion :proxy])
                      ~data
                      (apply old-fn# args#)))]
      ~@body)))
