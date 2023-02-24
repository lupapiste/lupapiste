(ns lupapalvelu.migration.product-constants-test
  (:require [lupapalvelu.invoices.schemas :refer [InvoiceRow InvoiceOperation]]
            [lupapalvelu.invoices.shared.schemas :refer [ProductConstants]]
            [lupapalvelu.migration.migrations :refer [TT-20085-fix-operations TT-20085-fix-constants]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [sade.util :as util]
            [schema.core :as sc]))

(sc/defn ^:always-validate make-constants :- ProductConstants
  [calc other]
  (util/strip-nils {:kustannuspaikka  "kp"
                    :alv              "a"
                    :projekti         "p"
                    :kohde            "k"
                    :toiminto         "t"
                    :laskentatunniste calc
                    :muu-tunniste     other}))

(defn check-constants [before after]
  (fact "Product constants"
    (TT-20085-fix-constants before) => after))

(facts "Fix constants"
  (fact "No calc, no other"
    (let [m1 (make-constants nil nil)
          m2 (make-constants "" "")
          m3 (make-constants " " "  ")]
      (check-constants m1 m1)
      (check-constants m2 m2)
      (check-constants m3 m3)))
  (fact "Both calc and other"
    (let [m (make-constants "Foo" "Bar")]
      (check-constants m m)))
  (fact "Calc, no other"
    (let [before1 (make-constants "Foo" "")
          before2 (make-constants "Foo" "  ")
          after (make-constants "" "Foo")]
      (check-constants before1 after)
      (check-constants before2 after)))
  (fact "No calc, but other"
    (let [m (make-constants "" "Bar")]
      (check-constants m m))))

(defn rnd-str []
  (->> (range (+ 4 (rand-int 20)))
       (map (fn [_] (rand-nth "abcdefghijklmnopqrstuwzyzåäö1234567890")))
       (apply str)))

(sc/defn ^:always-validate make-row :- InvoiceRow
  [calc other]
  {:code              (rnd-str)
   :text              (rnd-str)
   :type              "custom"
   :unit              "kpl"
   :price-per-unit    (rand-int 100)
   :units             (rand-int 100)
   :discount-percent  (rand-int 100)
   :product-constants (make-constants calc other)
   :order-number      (rand-int 100)
   :comment           (rnd-str)
   :min-unit-price    (rand-int 100)
   :max-unit-price    (rand-int 100)})

(defn update-row [row calc other]
  (update row :product-constants
          #(assoc %
                  :laskentatunniste calc
                  :muu-tunniste other)))

(fact "Fix operations"
  (let [a   (make-row "" "")
        b   (make-row "one" "one")
        c   (make-row "two" "three")
        d   (make-row "four" "")
        e   (make-row "" "five")
        op1 {:operation-id (mongo/create-id)
             :name         "pientalo"
             :invoice-rows [a b c d e]}
        f   (make-row "six" "")
        g   (make-row "seven" "")
        h   (make-row "eight" "")
        i   (make-row "" "nine")
        j   (make-row "ten" "ten")
        op2 {:operation-id (mongo/create-id)
             :name         "aita"
             :invoice-rows [f g h i j]}
        op3 {:operation-id (mongo/create-id)
             :name         "varasto-tms"
             :invoice-rows [(make-row "eleven" "eleven")]}
        op4 {:operation-id (mongo/create-id)
             :name         "puun-kaataminen"
             :invoice-rows []}]
    (TT-20085-fix-operations [op1 op2 op3 op4])
    => [(assoc op1 :invoice-rows [a b c (update-row d "" "four") e])
        (assoc op2 :invoice-rows [(update-row f "" "six")
                                  (update-row g "" "seven")
                                  (update-row h "" "eight")
                                  i j])
        op3
        op4]))
