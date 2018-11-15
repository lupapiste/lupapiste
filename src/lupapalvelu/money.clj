(ns lupapalvelu.money
  "API for money calculations. Made for invoices feature, but could contain other money
  handling functions as well. Heavily utilizes clojurewerkz money library"
  (:require [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :refer [EUR]]
            [clojurewerkz.money.format :as mf]
            [lupapalvelu.money-schema :refer [MoneyResponse]]
            [schema.core :as sc])
  (:import [org.joda.money Money CurrencyUnit]))

(def default-currency EUR)

(sc/defschema SumByOptions
  {:currency Money})

(sc/defn sum-by :- MoneyResponse
  "Sums values by given key

   `key` is the key that's used for calculation. `data` is sequence of entities
  with key `key`.
  last argument is map of options, that supports key `currency` for choosing other than
  default currency. Please use currency defined by clojurewerks.money.currencies

  Returns
  `{:major <amount in major units>
   :minor <amount in minor units>
   :text <textual representation, never trust this anywhere>}
   :currency <used currency, in ISO-4217}`"
  ([value-key :- sc/Keyword
    data :- sc/Any
    opts :- SumByOptions]
   (let [currency (:currency opts)
         values (map (fn [map-entry-that-has-value]
                       (let [value (value-key map-entry-that-has-value)]
                         (if (instance? Money value)
                           value
                           (ma/amount-of currency value)))) data)
         total (ma/total values)]
     (assert (instance? CurrencyUnit currency) "currency is not of type CurrencyUnit.")
     {:major (ma/major-of total)
      :minor (ma/minor-of total)
      :text (mf/format total)
      :currency (.toString (ma/currency-of total))}))
  ([value-key :- sc/Keyword
    data :- sc/Any]
   (sum-by value-key data {:currency default-currency})))

(defn discounted-value
  "Calculates discounted value for object, treats discount as percentage.
   Without `opts` uses default currency and percentage value.

  returns Money object"
  ([value discount opts]
   (let [currency (or (:currency opts) (:currency opts) default-currency)
         is-decimal? (:is-decimal? opts)
         discount (if is-decimal? discount (- 1 (/ discount 100)))
         money-value (ma/amount-of currency value)]
     (assert (and (<= discount 1) (>= discount 0)) "Invalid discount: discount > 1")
     (ma/multiply money-value discount :half-up)))
  ([value discount]
   (discounted-value value discount {})))

(defn sum-with-discounts
  "Calculates sum of money for sequences with keys for value and discount

  `value-key` is key for value in data sequence object
  `discount-key` is key for discount percent in data sequence object
  `data` is data that consists of handled objects
  Optionally you can provide options with
    `:currency` if you want to use other currency than EUR
    `:discount-is-decimal` Set this to true if you want to indicate that discount
                           decimal, like 0.20 instead of percentage like 20

  Returns
  `{:major <amount in major units>
   :minor <amount in minor units>
   :text <textual representation, never trust this anywhere>}
   :currency <used currency, in ISO-4217`"
  [value-key discount-key data & {:keys [currency discount-is-decimal]}]
  (let [currency (or currency default-currency)
        opts {:currency currency
              :is-decimal? discount-is-decimal}
        summable-data (map (fn [entry]
                             {:value (discounted-value
                                      (value-key entry)
                                      (discount-key entry)
                                      opts)})
                           data)]
    (sum-by :value summable-data {:currency currency})))
