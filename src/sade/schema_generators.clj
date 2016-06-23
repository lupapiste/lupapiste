(ns sade.schema-generators
  (:require [sade.schemas :as ssc]
            [sade.validators :as sv]
            [sade.util :refer [fn-> fn->>] :as util]
            [schema.core :as sc]
            [schema-generators.generators :as sg]
            [clj-time.format :as ctf]
            [clj-time.coerce :as ctc]
            [clojure.string :as s]
            [clojure.test.check.generators :as gen]))

(defonce static-schema-generators (atom {}))
(defonce dynamic-schema-generator-constructors (atom {}))

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

(defn generators
  ([] (generators {}))
  ([custom-generators] (merge (create-dynamic-schema-generators)
                              @static-schema-generators
                              custom-generators)))

(defn generate
  ([schema]                          (generate schema {}))
  ([schema leaf-generators]          (generate schema leaf-generators {}))
  ([schema leaf-generators wrappers] (sg/generate schema (generators leaf-generators) wrappers)))

(defn generator
  ([schema]                          (generator schema {}))
  ([schema leaf-generators]          (generator schema leaf-generators {}))
  ([schema leaf-generators wrappers] (sg/generator schema (generators leaf-generators) wrappers)))

;; Overwrite default prismatic schema generators

(register-generator sc/Str gen/string-ascii)

;; Custom static schema generators

(register-generator ssc/Nat gen/nat)

(def int-string (gen/fmap str gen/int))

(register-generator ssc/IntString int-string)

(def nat-string (gen/fmap str gen/nat))

(register-generator ssc/NatString nat-string)

(def decimal-string  (gen/fmap (partial s/join ".") (gen/tuple gen/large-integer gen/pos-int)))

(register-generator ssc/DecimalString decimal-string)

(def single-hex (gen/elements (concat (map str (range 10)) (map (comp str char) (range (int \a) (inc (int \f)))))))

(def single-number-int (gen/elements (range 10)))

(def blank-string (gen/elements ["" nil]))

(register-generator ssc/BlankStr blank-string)

(def not-blank-string (gen/such-that (comp not s/blank?) gen/string))

(def digit (gen/fmap str single-number-int))

(register-generator ssc/Digit digit)

(def letter (gen/fmap str gen/char-alpha))

(register-generator ssc/Letter letter)

(def upper-case-letter (gen/fmap s/upper-case letter))

(register-generator ssc/UpperCaseLetter upper-case-letter)

(def lower-case-letter (gen/fmap s/lower-case letter))

(register-generator ssc/LowerCaseLetter lower-case-letter)

(def email (gen/such-that (ssc/max-length-constraint 255)
                          (gen/fmap (fn [[name domain]] (s/lower-case (str name "@" domain ".com")))
                                    (gen/tuple (gen/not-empty gen/string-alphanumeric)
                                               (gen/not-empty gen/string-alphanumeric)))))

(register-generator ssc/Email email)
(register-generator ssc/Username email)

(def timestamp (gen/fmap (partial + 1450000000000)
                         gen/large-integer))

(register-generator ssc/Timestamp timestamp)

(def rakennusnumero (gen/fmap (partial apply str) (gen/vector single-number-int 3)))

(register-generator ssc/Rakennusnumero rakennusnumero)

(def kiinteistotunnus (gen/fmap (partial apply str) (gen/vector single-number-int 9)))

(register-generator ssc/Kiinteistotunnus kiinteistotunnus)

(def rakennustunnus (gen/fmap (fn->> (cons 1) (apply str) (#(str % (sv/vrk-checksum (read-string %)))))
                              (gen/vector single-number-int 8)))

(register-generator ssc/Rakennustunnus rakennustunnus)

(def maaraalatunnus (gen/fmap (partial format "%04d") ; (partial format "M%04d")
                              (gen/fmap #(+ 1 (rem % 9999)) gen/pos-int)))

(register-generator ssc/Maaraalatunnus maaraalatunnus)

(def finnish-zipcode (gen/fmap s/join
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

(def ddMMyy-formatter (ctf/formatter "ddMMyy"))
(def hetu (gen/fmap (fn [[day n]] (let [date (->> (* 86400000 day) ; day in milliseconds
                                                  (ctc/from-long)
                                                  (ctf/unparse ddMMyy-formatter))
                                        end  (format "9%02d" n)
                                        checksum  (sv/hetu-checksum (str date \- end))]
                                    (str date \- end checksum)))
                    (gen/tuple (gen/fmap #(mod % 36524) gen/large-integer)
                               (gen/fmap #(mod % 100) gen/int))))

(register-generator ssc/Hetu hetu)

(def object-id (gen/fmap s/join
                         (gen/vector single-hex 24)))

(register-generator ssc/ObjectIdStr object-id)

(def ipv4-address (gen/fmap (partial s/join ".")
                            (gen/vector (gen/elements (range 256)) 4)))

(def ipv6-address (gen/fmap (partial s/join ":")
                            (gen/vector (gen/fmap s/join (gen/vector single-hex 1 4)) 8)))

(def ip-address (gen/one-of [ipv4-address ipv6-address]))

(register-generator ssc/IpAddress ip-address)

(def http-protocol (gen/elements ["http://" "https://"]))
(def http-url
  (gen/fmap
    (fn [[proto ip path]] (s/lower-case (str proto ip "/" (ring.util.codec/url-encode path))))
    (gen/tuple http-protocol ipv4-address gen/string-ascii)))

(register-generator ssc/HttpUrl http-url)

;; Dynamic schema generator constructors

(defn date-string
  "Creates a generator that generates a date string of any a date 1.1.1970 +/- 50 years.
  Date string formatting is compatible with Joda Time date format (eg. dd.MM.yyyy)"
  [format]
  (let [formatter (ctf/formatter format)]
    (gen/fmap (fn->> (#(rem % 18262))
                     (* 86400000)
                     (ctc/from-long)
                     (ctf/unparse formatter))
              gen/large-integer)))

(register-generator ssc/date-string date-string)

(defn fixed-length-string [len]
  (gen/fmap s/join
            (gen/vector gen/char-ascii len)))

(register-generator ssc/fixed-length-string fixed-length-string)

(defn min-length-string [min-len]
  (gen/bind (gen/fmap #(+ min-len %) gen/pos-int)
            fixed-length-string))

(register-generator ssc/min-length-string min-length-string)

(defn max-length-string [max-len]
  (gen/fmap s/join
            (gen/vector gen/char-ascii 0 max-len)))

(register-generator ssc/max-length-string max-length-string)

(defn min-max-length-string [min-len max-len]
  (gen/fmap s/join
            (gen/vector gen/char-ascii min-len max-len)))

(register-generator ssc/min-max-length-string min-max-length-string)

(defn min-length-hex-string [min-len]
  (gen/bind (gen/fmap #(+ min-len %) gen/pos-int)
            #(gen/fmap s/join
                       (gen/vector single-hex %))))

(register-generator ssc/min-length-hex-string min-length-hex-string)

(defn- min-max-value
  "Wraps a numeric generator with min and/or max bounds.
  This function provides a way to bypass a problem with numeric
  generator and gen/such-that function. If such-that does not
  allow small values as possible outcome of a generator, it easily
  ends up producing an error:
  'Couldn't satisfy such-that predicate after 10 tries.'.
  Values are still shrinking towards zero."
  [numeric-gen min-val max-val]
  {:pre [(gen/generator? numeric-gen)
         (or (nil? min-val) (number? min-val))
         (or (nil? max-val) (number? max-val))
         (or (nil? min-val) (nil? max-val) (< min-val max-val))]}
  (let [less?    (if min-val (fn [v] (> min-val v)) (constantly false))
        greater? (if max-val (fn [v] (< max-val v)) (constantly false))
        bias     (cond (less? 0)    min-val
                       (greater? 0) max-val
                       :else        0)
        maxgen   (when (and min-val max-val)
                   (-> (- (max (Math/abs min-val) (Math/abs max-val))
                          (Math/abs bias))
                       (#(* (Math/ceil (/ 15 %)) %))))] ;; Magical 15 seems to work with gen/int + such-that
    ;; Initial value is generated so that 0 is in the range of possible values
    ;; since numeric generator values are shrinking towards zero by default.
    ;; Limiting maximum value ensures fast convergence in the loop function.
    (gen/fmap (fn->> (+ bias)
                     (#(loop [v %] (cond (less? v)    (recur (- (* 2 min-val) v))
                                         (greater? v) (recur (- (* 2 max-val) v))
                                         :else        v))))
              (if maxgen
                (gen/such-that #(>= maxgen (Math/abs %)) numeric-gen)
                numeric-gen))))

(defn min-max-valued-integer-string [min max]
  (gen/fmap str (min-max-value gen/int min max)))

(register-generator ssc/min-max-valued-integer-string min-max-valued-integer-string)

(defn min-max-valued-decimal-string [min max]
  (gen/fmap (partial format "%f") (min-max-value gen/double min max)))

(register-generator ssc/min-max-valued-decimal-string min-max-valued-decimal-string)
