(ns lupapalvelu.money
  "API for money calculations. Made for invoices feature, but could contain other money
  handling functions as well. Heavily utilizes clojurewerkz money library"
  (:require [clojurewerkz.money.amounts :as ma]
            [clojurewerkz.money.currencies :as mc :refer [EUR]]
            [clojurewerkz.money.format :as mf]
            [lupapalvelu.money-schema :refer [MoneyResponse]]
            [clojurewerkz.money.conversion :refer [to-rounding-mode]]
            [schema.core :as sc]
            [taoensso.timbre :refer [trace tracef debug debugf info infof
                                     warn warnf error errorf fatal fatalf]])
  (:import [org.joda.money Money CurrencyUnit]
           [java.util Locale]))

(def default-currency EUR)

(def locale (new Locale "fi_FI"))

(sc/defschema SumByOptions
  {:currency Money})

(defn ->currency
  ([currency amount]
   (ma/amount-of currency amount))
  ([amount]
   (->currency default-currency amount)))

(defn minor->currency
  ([currency amount-in-minor]
   (ma/of-minor currency amount-in-minor))
  ([amount-in-minor]
   (minor->currency default-currency amount-in-minor)))

(defn ->currency-code [currency-string]
  (mc/for-code currency-string))

(sc/defn ->MoneyResponse [money-value]
  {:major (ma/major-of money-value)
   :minor (ma/minor-of money-value)
   :text (mf/format money-value locale)
   :currency (.toString (ma/currency-of money-value))})

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
                           (->currency currency value)))) data)

         total (if (empty? values) (->currency currency 0) (ma/total values))]
     (assert (instance? CurrencyUnit currency) "currency is not of type CurrencyUnit.")
     (->MoneyResponse total)))
  ([value-key :- sc/Keyword
    data :- sc/Any]
   (sum-by value-key data {:currency default-currency})))

(defn multiply-amount
  ([multiplier amount currency]
   (let [multiplied-amount (ma/multiply (->currency currency amount) multiplier :half-up)]
     multiplied-amount))
  ([multiplier amount]
   (multiply-amount multiplier amount default-currency)))

(defn discounted-value
  "Calculates discounted value for object, treats discount-percent as integer value.
   With :is-decimal? true in `opts` treats discount-percent as a decimal value.
   Without `opts` uses default currency and treats percentage as integer value.

  returns Money object"
  ([value discount-percent {:keys [is-decimal? currency] :as opts}]
   (let [currency (or currency default-currency)
         discount-percent-decimal (if discount-percent
                                    (if is-decimal? discount-percent (- 1 (/ discount-percent 100)))
                                    1)
         money-value (if (instance? Money value) value(->currency currency value)) ]
     (assert (and (<= discount-percent-decimal 1) (>= discount-percent-decimal 0)) "Invalid discount-percent-decimal: discount-percent-decimal > 1")
     (ma/multiply money-value discount-percent-decimal :half-up)))
  ([value discount-percent]
   (discounted-value value discount-percent {})))

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
