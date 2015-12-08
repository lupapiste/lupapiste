(ns sade.schema-generators
  (:require [sade.schemas :as ssc]
            [schema.experimental.generators :as sg]
            [clojure.string]
            [clojure.test.check.generators :as gen]))

(def static-schema-generators (atom {}))
(def dynamic-schema-generator-constructors (atom {}))

(defn register-generator [schema generator]
  (if (fn? generator)
    (swap! dynamic-schema-generator-constructors assoc schema generator)
    (swap! static-schema-generators assoc schema generator)))

(defn create-dynamic-schema-generators []
  (let [constructors @dynamic-schema-generator-constructors
        schemas     (filter (comp constructors first key) @ssc/dynamically-created-schemas)]
    (->>
     (keys schemas)
     (map #(apply (constructors (first %)) (rest %)))
     (zipmap (vals schemas)))))

(defn generators []
  (merge (create-dynamic-schema-generators) @static-schema-generators))

(defn generate
  ([schema]          (generate schema {}))
  ([schema wrappers] (sg/generate schema (generators) wrappers)))

(defn generator
  ([schema]          (generator schema {}))
  ([schema wrappers] (sg/generator schema (generators) wrappers)))

;; Custom static schema generators

(def single-number-int (gen/elements (range 10)))

(def blank-string (gen/elements ["" nil]))

(register-generator ssc/BlankStr blank-string)

(def email (gen/such-that (ssc/max-length-constraint 255)
                          (gen/fmap (fn [[name domain]] (str name "@" domain ".com"))
                                    (gen/tuple (gen/not-empty gen/string-alphanumeric)
                                               (gen/not-empty gen/string-alphanumeric)))))

(register-generator ssc/Email email)

(def timestamp (gen/fmap (partial + 1450000000000)
                         gen/large-integer))

(register-generator ssc/Timestamp timestamp)

(def finnish-zipcode (gen/fmap clojure.string/join 
                               (gen/vector single-number-int 5)))

(register-generator ssc/Zipcode finnish-zipcode)

(def finnish-y-parts (gen/such-that (fn [[_ cn]] (not= 10 cn)) 
                                     (gen/fmap (fn [v] 
                                                 (let [cn (mod (apply + (map * [7 9 10 5 8 4 2] v)) 11)
                                                       cn (if (zero? cn) 0 (- 11 cn))]
                                                   [(apply str v) cn]))
                                                   (gen/vector single-number-int 7))))

(def finnish-y (gen/fmap (fn [[id cn]] (str id "-" cn))
                         finnish-y-parts))

(register-generator ssc/FinnishY finnish-y)

(def finnish-ovt (gen/fmap (fn [y-and-org] (apply str "0037" (flatten y-and-org)))
                           (gen/tuple finnish-y-parts
                                      (gen/vector gen/char-alphanumeric 0 5))))

(register-generator ssc/FinnishOVTid finnish-ovt)

;; Dynamic schema generator constructors

(defn fixed-length-string [len]
  (gen/fmap clojure.string/join 
            (gen/vector gen/char len)))

(register-generator ssc/fixed-length-string fixed-length-string)

(defn min-length-string [min-len]
  (gen/bind (gen/fmap #(+ min-len %) gen/pos-int)
            fixed-length-string))

(register-generator ssc/min-length-string min-length-string)

(defn max-length-string [max-len]
  (gen/fmap clojure.string/join
            (gen/vector gen/char 0 max-len)))

(register-generator ssc/max-length-string max-length-string)

(defn min-max-length-string [min-len max-len]
  (gen/fmap clojure.string/join
            (gen/vector gen/char min-len max-len)))

(register-generator ssc/min-max-length-string min-max-length-string)

