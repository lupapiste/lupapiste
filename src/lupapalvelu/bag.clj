(ns lupapalvelu.bag
  "A simple metadata cache mechanism. Value can be stored into an object metadata. This approach
  should make the data storage both transparent and safe.")

(defn put
  [obj k v]
  (vary-meta obj assoc-in [::bag k] v))

(defn clear [obj k]
  (vary-meta obj (fn [m]
                   (let [bag (not-empty (dissoc (::bag m) k))]
                     (not-empty (if bag
                                  (assoc m ::bag bag)
                                  (dissoc m ::bag)))))))

(defn out [obj k]
  (get-in (meta obj) [::bag k]))

(defmacro bag
  "Gets a value from bag. If missing (or nil), then the body is evaluated."
  [obj k & body]
  `(let [v# (out ~obj ~k)]
     (if (nil? v#)
       (do ~@body)
       v#)))
