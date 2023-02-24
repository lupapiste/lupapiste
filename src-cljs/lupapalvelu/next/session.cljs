(ns lupapalvelu.next.session
  (:require [lupapalvelu.next.event :refer [>evt]]
            [re-frame.core :as rf]))

(defn user [db]
  (get-in db [:session :user]))

(defn user-id [db]
  (:id (user db)))

(rf/reg-event-db ::invalidate
  (fn [db [_]]
    (dissoc db :session)))

(rf/reg-event-db ::initialize
  (fn [db [_ session-data]]
    (assoc db :session session-data)))

(rf/reg-sub ::user
  (fn [db]
    (user db)))

(defn user-success
  "Success handler from calling 'ajax.query(\"user\")' "
  [router-callback js-result]
  (let [data (js->clj js-result :keywordize-keys true)]
    (if (:user data)
      (do
        (>evt [::initialize data])
        (js/hub.send "login" js-result)
        (router-callback))
      (do
        (js/console.error "User query did not return user, response" js-result)
        (>evt [::invalidate])
        (js/hub.send "logout" js-result)))))

(defn error-response [js-result]
  (>evt [::invalidate])
  (js/hub.send "logout" js-result))
