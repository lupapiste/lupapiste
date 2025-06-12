(ns lupapalvelu.statistics.tags
  (:require [lupapalvelu.statistics.schemas :as stats-sc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn complexity->text [loc-fn complexity]
  (when complexity
    (let [months (stats-sc/complexity->months complexity)
          text   (loc-fn (util/kw-path :pate.complexity complexity))]
      (str text " (" months " " (loc-fn :unit.kk) ")" ))))

(defn period->text [loc-fn period]
  (letfn [(part [k]
            (let [n (get period k)]
              (when (and (int? n) (not (zero? n)))
                (loc-fn (util/kw-path :statistics.period k) n))))]
    (or (->> [:y :m :d]
             (keep part)
             (ss/trim-join " "))
        (loc-fn :statistics.period.d 0))))

(sc/defn processing-log-tags
  [{:keys [submitted-ts verdict-ts delays
           processing]} :- stats-sc/ProcessingLogData
   {:keys [loc-fn date-fn]}]
  (let [pad-top      {:padding-top "8px"}
        pad-left     {:padding-left "12px"}
        indent       {:padding-left "20px"}
        first-indent {:padding-left "10px"}
        cell         (fn [{:keys [text text-loc ts]}
                          attributes & styles]
                       [:td (merge attributes
                                   {:style (apply merge
                                                  {:vertical-align :top}
                                                  styles)})
                        (cond
                          text     text
                          text-loc (loc-fn text-loc)
                          ts       (date-fn ts))])
        border       "1px solid black"
        bold         {:white-space :nowrap
                      :font-weight :bold}
        nowrap       {:white-space :nowrap}
        late         (when (some-> processing :remaining neg?)
                       {:font-weight :bold :color :red})
        comp-text    (complexity->text loc-fn (:complexity processing))
        status       (when comp-text
                       [[:tr
                         (cell {:text-loc (if verdict-ts
                                            :statistics.processing.verdict-ts
                                            :statistics.processing.deadline-ts)}
                               nil pad-top bold)
                         (cell {:text-loc :statistics.processing.remaining}
                               nil pad-top pad-left bold)
                         (when delays
                           (cell {:text-loc :statistics.tags.delays}
                                 nil pad-top pad-left bold))]
                        [:tr
                         (cell {:ts (or verdict-ts (:deadline processing))}
                               nil first-indent)
                         (cell {:text (period->text loc-fn (:remaining-period processing))}
                               nil indent late)
                         (when delays
                           (cell {:text (loc-fn :statistics.period.d
                                                (:delays processing))}
                                 nil indent))]])
        body         [:tbody
                      [:tr
                       (cell {:text-loc :submitted} nil bold pad-top)
                       (when comp-text
                         (cell  {:text-loc :statistics.processing.complexity} nil bold pad-top pad-left))
                       (cell {:text-loc :statistics.processing.status}
                             nil pad-left pad-top bold)]
                      [:tr
                       (cell {:ts submitted-ts} nil first-indent)
                       (when comp-text (cell {:text comp-text} nil indent))
                       (cell {:text-loc (util/kw-path :statistics.processing.status
                                                      (:status processing))}
                             nil indent nowrap)]]
        delays       (when delays
                       (-> []
                           (into [[:tr {:style {:border-bottom border}}
                                   (cell {:text (loc-fn :statistics.entries.delay.created)}
                                         nil bold pad-top)
                                   (cell {:text (loc-fn :statistics.entries.explanation)}
                                         nil bold pad-top pad-left)
                                   (cell {:text (loc-fn :statistics.entries.delay.start)}
                                         nil bold pad-top pad-left)
                                   (cell {:text (loc-fn :statistics.entries.delay.end)}
                                         nil bold pad-top pad-left)]])
                           (into (for [{:keys [start-ts end-ts explanation text]} delays]
                                   [:tr
                                    (cell {:text text} nil {:white-space :pre-wrap})
                                    (cell {:text explanation} nil
                                          {:white-space :pre-wrap} pad-left)
                                    (cell {:ts start-ts} nil pad-left)
                                    (cell {:ts end-ts} nil pad-left)]))))]
    (cond-> [:div
             {:style {:display :flex :flex-direction :column
                      :gap     "16px"}}
             [:table.reset ;; Reset is for the frontend
              {:style {:border-collapse :collapse
                       :width :fit-content}}
              (into body status)]]
      delays    (into [[:table.reset ;; Reset is for the frontend
                        {:style {:border-collapse :collapse
                                 :width           :fit-content}}
                        (into [:tbody] delays)]])
      comp-text (into [[:div
                        [:h3 (loc-fn :statistics.summary.title)]
                        [:div {:style {:display :flex :gap "20px"}}
                         [:div (loc-fn :statistics.summary.label)]
                         [:div {:style {:font-weight :bold}}
                          (period->text loc-fn (:used-period processing))]]]]))))
