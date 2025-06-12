(ns lupapalvelu.conversion.location-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.conversion.location :as conv-xy]
            [lupapalvelu.conversion.util :refer [->double]]
            [lupapalvelu.mml.property.api :refer [property-infos-by-point!]]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]
            [sade.util :as util]))

(deftest tidy-location-test
  (doseq [[from to] [[nil {}] [{} {}]
                     [{:x nil :y "" :propertyId "  " :address ""} {}]
                     [{:x 1 :y 2 :propertyId " foo " :address " bar "}
                      {:x 1.0 :y 2.0 :propertyId "foo" :address "bar"}]
                     [{:x 12.34 :y 56.78} {:x 12.34 :y 56.78}]]]
    (is (= to (conv-xy/tidy-location from)))))

(deftest ->double-test
  (is (= 12.34 (->double "12.34")))
  (is (= 12.34 (->double " 12,34 ")))
  (is (nil? (->double " 12,34,56 ")))
  (is (nil? (->double :foo)))
  (is (= 12.0 (->double 12)))
  (is (= 12.0 (->double "12")))
  (is (= 12.345 (->double 12.345)))
  (is (nil? (->double nil)))
  (is (nil? (->double "  ")))
  (is (nil? (->double "not a number"))))

(def sanlitun-4326 {:x 116.448006 :y 39.933582})
(def sanlitun-3067 {:x 6956732.982663 :y 9924699.459311})

(def humppila-4326 {:x 23.366668 :y 60.933810})
(def humppila-3067 {:x 303149.131982 :y 6760872.990480})

(def humppila-scaled {:x 303149.13 :y 6760872.99})

(def property-id-or-nil #'conv-xy/property-id-or-nil)

(def pid "30542300650013")

(deftest property-id-or-nil-test
  (is (nil? (property-id-or-nil nil)))
  (is (nil? (property-id-or-nil " ")))
  (is (nil? (property-id-or-nil "foobar")))
  (doseq [arg [pid " 305-423-65-13 " (str " " pid " ")]]
    (is (= pid (property-id-or-nil arg)))))

(def mml-pid "09206600040005")

(defn make-lox [propertyId address]
  (->> (util/mmap propertyId address)
       (merge humppila-scaled)
       util/strip-nils))

(deftest process-location-test
  (testing "bad coordinates"
    (doseq [m [nil {} {:x 1 :y 2}]]
      (is (nil? (conv-xy/process-location m)))))

  (testing "bad property-id from mml (unlikely)"
    (with-redefs [property-infos-by-point! (constantly [{:property-id "yikes!"}])]
      (is (= (make-lox nil "hello")
             (conv-xy/process-location (make-lox "bad" " hello "))))
      (is (= (make-lox nil "hello")
             (conv-xy/process-location (make-lox nil " hello "))))))
  (testing "good coordinates"
    (is (= (make-lox pid "Happy Path 10")
           (->> (make-lox pid "Happy Path 10")
                (util/map-values #(str "  " % "  ") )
                conv-xy/process-location)))
    (is (= (make-lox pid nil)
           (conv-xy/process-location (make-lox pid " ")))))
  (testing "property id from mml"
    (with-redefs [property-infos-by-point! (constantly [{:propertyId mml-pid}])]
      (is (= (make-lox mml-pid "MML Mansion")
             (conv-xy/process-location (make-lox nil "MML Mansion")))))))

(def bid1 "00-22357-R")
(def bid2 "00-22358-R")
(def x (:x humppila-scaled))
(def y (:y humppila-scaled))

(deftest make-location-override-map-test
  (is (= {bid1 (make-lox pid "First Way")
          bid2 (-> (make-lox pid "Second Street")
                   (update :x inc)
                   (update :y inc))}
         (conv-xy/make-location-override-map [{:lupatunnus bid1 :kiinteisto pid
                                               :east       x    :north      y
                                               :osoite     " First Way  "
                                               :foo        "bar"}
                                              {:lupatunnus bid2 :kiinteisto pid
                                               :east       0    :north      y
                                               :osoite     " Cul-de-sac "}
                                              {:lupatunnus bid2    :kiinteisto pid
                                               :east       (inc x) :north      (inc y)
                                               :osoite     " Second Street "}]
                                             {:backend-id :lupatunnus
                                              :x          :east   :y          :north
                                              :address    :osoite :propertyId :kiinteisto})))
  (with-redefs [property-infos-by-point! (constantly [{:propertyId mml-pid}])]
    (is (= {bid1 (make-lox mml-pid "Conversion Circuit")}
           (conv-xy/make-location-override-map [{:lupatunnus bid1 :kiinteisto ""
                                                 :x          (:x humppila-4326)
                                                 :y          (:y humppila-4326)
                                                 :address    " Conversion Circuit "
                                                 :foo        "bar"}]
                                               {:backend-id :lupatunnus
                                                :propertyId :kiinteisto}
                                               "epsg:4326")))))

(def =location #'conv-xy/=location)
(def not=location #'conv-xy/not=location)
(def lox (make-lox pid "Hello"))

(deftest =location-test
  (testing "new location must have data"
    (doseq [m [nil {} {:address " " :x nil}]]
      (is (thrown-with-msg? Exception #"No new location given"
                            (=location lox m)))))
  (doseq [[m =?] [[lox true] [(update lox :x inc) false]
                  [(select-keys lox [:x :y]) true]
                  [(assoc lox :propertyId mml-pid) false]
                  [(ss/stringwalk #(str "  " % "  ") lox) true]]]
    (is (= =? (=location lox m)))
    (is (= (not =?) (not=location lox m)))))



(def sysi {:address    "Sysikuja"
           :x          394271
           :y          6707830
           :propertyId "18640100100118"})

(def sysi-xy (conv-xy/valid-xy-or-nil sysi))

(def viili {:address    "Viilarinkatu"
            :x          393371
            :y          6707943
            :propertyId "18600999030006"})

(def viili-xy (conv-xy/valid-xy-or-nil viili))

(def riffi {:address    "Riffikatu"
            :x          396523
            :y          6703471
            :propertyId "18602121310001"})

(def riffi-xy (conv-xy/valid-xy-or-nil riffi))

(defn double-xy [m]
  (util/map-values util/->double (select-keys m [:x :y])))

(defn double-lox [m]
  (merge m (double-xy m)))

(deftest valid-xy-or-nil-test
  (testing "Projection"
    (is (nil? (conv-xy/valid-xy-or-nil sanlitun-4326)))
    (is (nil? (conv-xy/valid-xy-or-nil (assoc sanlitun-4326 :source-projection "EPSG:4326"))))
    (is (thrown-with-msg?
          Exception #"No code"
          (conv-xy/valid-xy-or-nil (assoc sanlitun-4326 :source-projection "EPSG:what"))))
    (is (nil? (conv-xy/valid-xy-or-nil sanlitun-3067)))
    (is (nil? (conv-xy/valid-xy-or-nil humppila-4326)))
    (is (= humppila-3067 (conv-xy/valid-xy-or-nil humppila-3067)))
    (is (= humppila-scaled (conv-xy/valid-xy-or-nil (assoc humppila-4326
                                                           :source-projection "EPSG:4326")))))
  (testing "Sanity checks"
    (is (= sysi-xy (double-xy sysi)))
    (is (= viili-xy (double-xy viili)))
    (is (= riffi-xy (double-xy riffi))))
  (testing "No coordinates"
    (doseq [m [nil {} {:hello "world"} {:x "" :y ""}
               {:x 396523} {:y 6707943.02}]]
      (is (nil? (conv-xy/valid-xy-or-nil m)))))
  (testing "Coordinates"
    (is (= {:x 396523.0 :y 6703471.02}
           (conv-xy/valid-xy-or-nil {:x " 396523.0 " :y " 6703471.02 "})))
    (is (= {:x 396523.0 :y 6703471.02}
           (conv-xy/valid-xy-or-nil {:x 396523 :y " 6703471,02 "})))
    (doseq [m [sysi viili riffi (dissoc sysi [:x])
               (dissoc viili [:y])]]
      (is (= (double-xy m) (conv-xy/valid-xy-or-nil m))))))

(def rnl #'conv-xy/resolve-new-location)

(deftest resolve-new-location-test
  (with-redefs [mongo/select-one (fn [_ m _]
                                   (when-let [{:keys [address propertyId
                                                      x y]} (case (:_id m)
                                                              "LP-001" sysi
                                                              "LP-002" viili
                                                              "LP-003" riffi
                                                              nil)]
                                     {:location   [x y]
                                      :address    address
                                      :propertyId propertyId}))]
    (testing "No new location"
      (is (nil? (rnl {} {} {})))
      (is (nil? (rnl {} {:LP-id "LP-001"} nil)))
      (is (nil? (rnl {} {:LP-id    "LP-001" :reset-location sysi-xy
                         :location sysi} sysi)))
      (is (nil? (rnl {} {:LP-id     "LP-001" :reset-location sysi-xy
                         :converted true} sysi)))
      (is (nil? (rnl {} {:LP-id "LP-001" :location sysi} sysi))))

    (testing "User has changed the location"
      (is (nil? (rnl {} {:LP-id "LP-002" :converted true} riffi)))
      (is (nil? (rnl {} {:LP-id     "LP-002" :location sysi
                         :converted true} riffi)))
      (is (nil? (rnl {} {:LP-id     "LP-002" :reset-location sysi-xy
                         :converted true} riffi)))
      (is (nil? (rnl {:location-fallback sysi}
                     {:LP-id     "LP-002" :backend-id "002"
                      :converted true}
                     riffi))))

    (testing "No reset reference, no relaxing"
      (is (nil? (rnl {} {:LP-id "LP-001" :converted true} riffi))))

    (testing "No reset reference, relax"
      (is (= {:application (double-lox riffi)}
             (rnl {:location-relax true} {:LP-id "LP-001" :converted true}
                  riffi)))
      (is (= {:application (merge sysi (double-xy riffi))}
             (rnl {:location-relax true} {:LP-id "LP-001" :converted true}
                  riffi-xy))))

    (testing "Application location change"
      (is (= {:application (double-lox riffi)}
             (rnl {} {:LP-id          "LP-001" :converted true
                      :reset-location sysi-xy}
                  riffi)))
      (is (= {:application (double-lox riffi)}
             (rnl {:location-fallback sysi}
                  {:LP-id "LP-001" :converted true}
                  riffi))))

    (testing "Document location change only"
      (is (= {:document (double-lox viili)}
             (rnl {} {:LP-id "LP-004"} viili)))
      (is (= {:document (double-lox viili)}
             (rnl {} {:LP-id "LP-004" :converted true} viili)))
      (is (= {:document (double-lox viili)}
             (rnl {} {:LP-id "LP-004" :location riffi} viili))))))
