(ns lupapalvelu.document.tools
  (require
    [clojure.walk :as walk]
    [lupapalvelu.document.schemas :as schemas]
    [lupapalvelu.document.model :as model]))

(def osoite (schemas/schemas "osoite"))

(defn nil-values [x] nil)

(defn dummy-values [{:keys [type name]}]
  (if (sequential? type)
    "!!!"
    (condp = type
      :checkbox         true
      :number           "42"
      :email            "example@example.com"
      :tel              "012 123 4567"
      :letter           "\u00c5"
      :kiinteistotunnus "09100200990013"
      name)))

(defn create [{body :body} f]
  (walk/postwalk
    (fn [x]
      (if (map? x)
        (let [k (:name x)
              v (if (= :group (:type x)) (:body x) (f x))]
          {k v})
        x))
    body))

(comment
  {:info {:name "osoite"},
   :body
   [{:name "osoite",
     :type :group,
     :body
     [{:name "kunta", :type :string}
      {:name "lahiosoite", :type :string}
      {:name "osoitenumero", :type :string}
      {:name "osoitenumero2", :type :string}
      {:name "jakokirjain", :size "s", :type :string}
      {:name "jakokirjain2", :size "s", :type :string}
      {:name "porras", :size "s", :type :string}
      {:name "huoneisto", :size "s", :type :string}
      {:name "postinumero", :size "s", :type :string}
      {:name "postitoimipaikannimi", :size "m", :type :string}
      {:name "pistesijanti", :type :string}]}]})
