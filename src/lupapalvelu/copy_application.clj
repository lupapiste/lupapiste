(ns lupapalvelu.copy-application
  (:require [clojure.set :as set]
            [lupapalvelu.application :as app]))

(defn- assoc-x-y [source]
  (assoc source
         :x (-> source :location first)
         :y (-> source :location second)))

(defn- pre-create-keys
  ([source] (pre-create-keys source (keys source)))
  ([source copied-fields]
   (let [copied-fields-set (set copied-fields)]
     (->> (if (copied-fields-set :location)
            (conj (disj copied-fields-set :location) :x :y)
            copied-fields-set)
          (set/intersection  app/do-create-application-data-fields)
          vec
          (select-keys (assoc-x-y source))))))

(defn- post-create-keys
  ([source] (post-create-keys source (keys source)))
  ([source copied-fields]
   (->> (set/difference (disj (set copied-fields) :location)
                        (disj app/do-create-application-data-fields :x :y))
        vec
        (select-keys source))))

(defn new-application-copy [source-application user created copied-fields overrides & [manual-schema-datas]]
  (-> (app/do-create-application
       {:data    (merge (pre-create-keys source-application copied-fields)
                        (pre-create-keys overrides))
        :user    user
        :created created}
       manual-schema-datas)
      (merge (post-create-keys source-application copied-fields)
             (post-create-keys overrides))))
