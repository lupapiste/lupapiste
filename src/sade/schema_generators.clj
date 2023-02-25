(ns sade.schema-generators
  (:require [ring.util.codec :as codec]
            [sade.schemas :as ssc]
            [sade.validators :as sv]
            [sade.util :refer [fn-> fn->>]]
            [schema.core :as sc]
            [schema-generators.generators :as sg]
            [clj-time.core :as ct]
            [clj-time.format :as ctf]
            [clj-time.coerce :as ctc]
            [clojure.string :as s]
            [clojure.test.check.generators :as gen]
            [sade.shared-schemas :as sssc]
            [com.gfredericks.test.chuck.generators :refer [string-from-regex]]))

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

(defn get-generator
  [schema]
  (get (generators) schema))

;; Overwrite default prismatic schema generators

(register-generator sc/Str gen/string-ascii)
(register-generator sc/Any (gen/recursive-gen #(gen/frequency [[1 (gen/map % %)]
                                                               [1 (gen/vector %)]
                                                               [5 %]])
                                              (gen/one-of [gen/boolean
                                                           gen/string-ascii
                                                           gen/double
                                                           gen/int])))

;; Custom static schema generators

(defn maybe [generator] (gen/one-of [(gen/elements [nil]) generator]))

(defn format-map [fmt generator] (gen/fmap (partial format fmt) generator))

(register-generator ssc/Nat gen/nat)

(register-generator ssc/PosInt gen/s-pos-int)

(register-generator ssc/NatKeyword (gen/fmap (comp keyword str) gen/nat))

(def int-string (gen/fmap str gen/int))

(register-generator ssc/IntString int-string)

(def nat-string (gen/fmap str gen/nat))

(register-generator ssc/NatString nat-string)

(def decimal-string  (gen/fmap (partial s/join ".") (gen/tuple gen/large-integer gen/pos-int)))

(register-generator ssc/DecimalString decimal-string)

(def time-string (gen/fmap (fn->> (take-while identity) (interleave [""  ":" ":" "."]) (apply str))
                           (gen/tuple (gen/large-integer* {:min 0 :max 23})                              ; h / hh
                                      (format-map "%02d" (gen/large-integer* {:min 0 :max 59}))          ; mm
                                      (maybe (format-map "%02d" (gen/large-integer* {:min 0 :max 59})))  ; ss / nil
                                      (maybe (gen/large-integer* {:min 0 :max 9})))))                    ; d / nil

(register-generator ssc/TimeString time-string)

(def single-hex (gen/elements (concat (map str (range 10)) (map (comp str char) (range (int \a) (inc (int \f)))))))

(def single-number-int (gen/elements (range 10)))

(def blank-string (gen/elements ["" nil]))

(register-generator ssc/BlankStr blank-string)

(def not-blank-string (gen/fmap (partial apply str) (gen/tuple gen/char-alphanumeric gen/string-ascii)))

(register-generator ssc/NonBlankStr not-blank-string)

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

(def timestamp (gen/fmap (partial + 1450000000000)          ; ~ 2015 (offset)
                         (gen/large-integer* {:min (- -2208988800000 ; 1.1.1900
                                                      1450000000000)
                                              :max (- 253402214400000 ; 31.12.9999
                                                      1450000000000)})))

(register-generator ssc/Timestamp timestamp)

(def rakennusnumero (gen/fmap (partial apply str) (gen/vector single-number-int 3)))

(register-generator ssc/Rakennusnumero rakennusnumero)

(def kiinteistotunnus (gen/fmap (partial apply str) (gen/vector single-number-int 14)))

(register-generator ssc/Kiinteistotunnus kiinteistotunnus)

(def rakennustunnus (gen/fmap (fn->> (cons 1) (apply str) (#(str % (sv/vrk-checksum (read-string %)))))
                              (gen/vector single-number-int 8)))

(register-generator ssc/Rakennustunnus rakennustunnus)

(def maaraalatunnus (format-map "%04d"   ; "M%04d"
                                (gen/fmap #(+ 1 (rem % 9999)) gen/pos-int)))

(register-generator ssc/Maaraalatunnus maaraalatunnus)

(def location-x-generator (gen/double* {:min 10001 :max 800000 :infinite? false :NaN? false}))
(register-generator ssc/LocationX location-x-generator)
(def location-y-generator (gen/double* {:min 6610001 :max 7779999 :infinite? false :NaN? false}))
(register-generator ssc/LocationY location-y-generator)

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
(def hetu (gen/fmap (fn [[day n]] (let [day-ts (* 86400000 day) ; day in milliseconds
                                        date (->> day-ts
                                                  (ctc/from-long)
                                                  (ctf/unparse ddMMyy-formatter))
                                        end  (format "9%02d" n)
                                        delim     (if (>= day-ts 946684800000) \A \-)
                                        checksum  (sv/hetu-checksum (str date delim end))]
                                    (str date delim end checksum)))
                    (gen/tuple (gen/fmap #(mod % 36524) gen/large-integer)
                               (gen/fmap #(mod % 100) gen/int))))

(register-generator ssc/Hetu hetu)

(def object-id (gen/fmap s/join
                         (gen/vector single-hex 24)))

(register-generator ssc/ObjectIdStr object-id)

(def uuid (gen/fmap str gen/uuid))

(register-generator sssc/UUIDStr uuid)

(def ipv4-address (gen/fmap (partial s/join ".")
                            (gen/vector (gen/elements (range 256)) 4)))

(def ipv6-address (gen/fmap (partial s/join ":")
                            (gen/vector (gen/fmap s/join (gen/vector single-hex 1 4)) 8)))

(def ip-address (gen/one-of [ipv4-address ipv6-address]))

(register-generator ssc/IpAddress ip-address)


(def application-id (gen/fmap (fn [v] (str "LP" \- (s/join (subvec v 0 3)) \- (s/join (subvec v 3 7)) \- (s/join (subvec v 7 12))))
                              (gen/vector single-number-int 12)))

(register-generator ssc/ApplicationId application-id)

(register-generator ssc/Tel (string-from-regex #"\+?[\d -]+"))

(def http-protocol (gen/elements ["http://" "https://"]))
(def http-url
  (gen/fmap
    (fn [[proto ip path]] (s/lower-case (str proto ip "/" (codec/url-encode path))))
    (gen/tuple http-protocol ipv4-address gen/string-ascii)))

(register-generator ssc/HttpUrl http-url)

;; Dynamic schema generator constructors

(defn date-string
  "Creates a generator that generates a date string of any a date 1.1.1970 +/- 50 years.
  Date string formatting is compatible with Joda Time date format (eg. dd.MM.yyyy)"
  [& formats]
  (let [formatter (if (< (count formats) 2) ; HACK: Workaround for the ctf/formatter arities SNAFU
                    (apply ctf/formatter formats)
                    (apply ctf/formatter ct/utc formats))]
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

(defn min-max-valued-integer-string [min max]
  (gen/fmap str (gen/large-integer* {:min min :max max})))

(register-generator ssc/min-max-valued-integer-string min-max-valued-integer-string)

(defn min-max-valued-decimal-string [min max]
  (gen/fmap
    (comp #(s/replace % \, \.) (partial format "%f"))
    (gen/double* {:infinite? false :NaN? false :min min :max max})))

(register-generator ssc/min-max-valued-decimal-string min-max-valued-decimal-string)
