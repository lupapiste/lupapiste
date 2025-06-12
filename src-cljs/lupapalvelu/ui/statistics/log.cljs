(ns lupapalvelu.ui.statistics.log
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.statistics.tags :as stat-tags]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.statistics.data.log :as data]
            [reagent.core :as r]))

(defn log []
  (r/with-let [_ (>evt [::data/fetch-logdata])]
    (if-let [logdata (<sub [::data/logdata])]
      [stat-tags/processing-log-tags logdata
       {:date-fn date/finnish-date
        :loc-fn  loc}]
      [:div.pad--10.h--10
       [:i.fg--gray.icon-spin.lupicon-refresh]])
    (finally (>evt [::data/clear]))))

(defn pdf-button []
  (when (<sub [:auth/application? :processing-log-pdf])
    [:a.btn.primary
     {:href   (common/make-url "/api/raw/processing-log-pdf"
                               {:id   (common/application-id)
                                :lang (common/get-current-language)})
      :target :_blank}
     [:span "PDF"] [:i.txt--small.lupicon-external-link]]))
