(ns lupapalvelu.ui.application.foreman-list
  (:require [lupapalvelu.application.foreman :as foreman]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.application.foreman-util :as foreman-util]
            [lupapalvelu.ui.common :as common :refer [loc]]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.misc]
            [sade.util :as util]))

(defn view
  "Lists old foreman (tyonjohtaja-v2) applications in the parties view under (Työnjohtaja) on application"
  []
  (let [foreman-items (<sub [::data/foreman-v2-applications])]
    (when foreman-items
      [:ul.invited-foremans.mt-2
       (doall
         (for [item foreman-items]
           (let [tj-document (foreman-util/foreman-item->tj-document item)
                 fullname    (foreman/tj-document->fullname tj-document)
                 email       (foreman/tj-document->email tj-document)
                 id          (:id item)]
             [:li.foreman
              {:key id}
              [:span.foreman-role (->> item foreman-util/foreman-item->tj-role (util/kw-path :osapuoli.tyonjohtaja.kuntaRoolikoodi) loc)]
              [:span.foreman-name fullname]
              (when email [:span.foreman-email email])
              [:a
               {:on-click #(common/open-page :application id :parties)}
               [:span.foreman-application id]]])))])))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (>evt [::data/fetch-foreman-applications])
  (ui-dom/mount! mounter dom-id))
