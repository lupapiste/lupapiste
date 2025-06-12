(ns lupapalvelu.ui.conversion.layout
  "While the UI utilises Pate definitions, it only implements a subset of Pate functionality."
  (:require [goog.functions :as goof]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.conversion.data :as data]
            [sade.util :as util]))

(defn- make-id [section-id & path]
  (name (util/kw-path section-id path)))

(defn- subset
  "Convenience function for common subs et al."
  [section-id path]
  {:editing? (<sub [::data/dict-editing? section-id path])
   :id       (make-id section-id path)
   :value    (<sub [::data/dict-value section-id path])
   :schema   (<sub [::data/dict-inner-schema section-id path])
   :error    (<sub [::data/dict-error section-id path])})

(defn pate-unit [unit]
  (when-let [c (case (keyword unit)
                 :days    (common/loc :pate-date-delta.days)
                 :years   (common/loc :pate-date-delta.years)
                 :ha      (common/loc :unit.hehtaaria)
                 :cm      [:span "cm"]
                 :m       [:span "m"]
                 :m2      [:span "m" [:sup 2]]
                 :m3      [:span "m" [:sup 3]]
                 :kpl     (common/loc :unit.kpl)
                 :section "§"
                 :eur     "€"
                 nil)]
    (cond->> c
      (string? c) (into [:span]))))

(defn text--item [section-id {:keys [dict css]}]
  (let [{:keys [editing? id value schema
                error]}        (subset section-id dict)
        component              (if editing?
                                 [components/text-edit
                                  value
                                  {:id       id
                                   :invalid? error
                                   :class    (or (util/kw-path (:css schema) css)
                                                 :w--min-20em)
                                   :lines    (:lines schema)
                                   :callback (goof/debounce #(>evt [::data/edit-section section-id dict %])
                                                            500)}]
                                 [:div.ws--pre-wrap.pad--h05 {:id id} value])
        {:keys [before after]} schema]
    (if (or before after)
      [:div.flex--align-center [pate-unit before] component [pate-unit after]]
      component)))

(defn with-label [section-id {:keys [dict box-css]} content]
  [(util/kw-path :div box-css)
   [:label.lux.txt--bold
    {:for (make-id section-id dict)}
    (<sub [::data/dict-loc section-id dict])]
   content])

(defn toggle--item [section-id {:keys [dict box-css css] :as item}]
  (let [{:keys [editing? value schema
                id]} (subset section-id dict)
        text         (<sub [::data/dict-loc section-id dict])]
    (if editing?
      [components/toggle
       value
       {:id       id
        :text     text
        :class    (util/kw-path (or box-css :flex--self-center)
                                css
                                (:css schema))
        :callback #(>evt [::data/edit-section section-id dict %])}]
      [with-label section-id item
       [:div.pad--l05 {:id id} (common/loc (if value :yes :no))]])))

(defn select--item [section-id {:keys [dict css]}]
  (let [{:keys [editing? id value schema
                error]} (subset section-id dict)
        items           (<sub [::data/dict-items section-id dict])]
    (if editing?
      [components/dropdown
       value
       {:id       id
        :items    items
        :invalid  error
        :class    (util/kw-path (:css schema) css)
        :callback #(>evt [::data/edit-section section-id dict %])}]
      [:div.pad--l05 {:id id} (some->> items
                                       (util/find-by-key :value value)
                                       :text)])))

(defn date--item [section-id {:keys [dict css]}]
  (let [{:keys [editing? id value schema
                error]} (subset section-id dict)
        items           (<sub [::data/dict-items section-id dict])]
    (if editing?
      [day-edit/day-edit
       value
       {:id       id
        :invalid? error
        :format   :string
        :class    (util/kw-path (:css schema) css)
        :callback #(>evt [::data/edit-section section-id dict %])}]
      [:div.pad--l05 {:id id} (js/util.finnishDate value)])))

(defn labelled--item [section-id {:keys [dict] :as item}]
  (when-let [component (case (<sub [::data/dict-schema-type section-id dict])
                         :text   text--item
                         :select select--item
                         :date date--item
                         nil)]
    [with-label section-id item [component section-id item]]))

(defn list-item [section-id {:keys [dict] :as item}]
  (when (<sub [::data/dict-visible? section-id dict])
    (when-let [component (case (<sub [::data/dict-schema-type section-id dict])
                           (:text :select :date) labelled--item
                           :toggle               toggle--item
                           nil)]
      [component section-id item])))

(defn box-item [section-id {:keys [dict box] :as item}]
  (if dict
    (list-item section-id item)
    (some->> (:items box)
             (keep #(box-item section-id %))
             seq
             (into [(util/kw-path :div.flex--wrap.flex--column-gap12.flex--row-gap2
                                  (:css box))]))))

(defn row [section-id row-index {:keys [title items] :as r}]
  (when-let [div (some->> (or items r)
                          (keep #(box-item section-id %))
                          seq
                          (into [:div.flex--wrap.flex--column-gap12.flex--row-gap2]))]
    [:<>
     {:key (make-id section-id :row row-index)}
     (when title
       [:h3.gap--t2 (<sub [::data/section-loc section-id title])])
     div]))

(defn section [section-id]
  (let [{:keys [title]} (<sub [::data/section section-id])
        {:keys [rows]}  (<sub [::data/schema section-id])]
    (some->> (keep-indexed #(row section-id %1 %2) rows)
             seq
             (apply conj [:div.flex--column.flex--gap1.gap--b4
                          (when title
                            [:h2 (<sub [::data/section-loc section-id title])])]))))

(defn sections []
  (into [:div.flex--column.flex--row-gap2]
        (map section (<sub [::data/section-ids]))))
