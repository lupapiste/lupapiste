(ns lupapalvelu.ui.auth-admin.foreman-settings.submit
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.auth-admin.org-subs :as org-subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.dom :as ui-dom]
            [lupapalvelu.ui.components :as components]
            [sade.util :as util]))

(defn settings-for-type [title prefix org-id settings]
  (let [foreman-settings (get (<sub [::org-subs/foreman-settings-submit]) title)
        one-active       (->> foreman-settings vals (filter identity) count (= 1))]
    [:div.p-2
     [:h3.help-adjacent-title (common/loc (util/kw-path prefix title :title))]
     (for [setting settings
           :let [checked?  (setting foreman-settings)
                 path      (util/kw-path title setting)
                 on-change #(>evt [::org-subs/set-foreman-setting org-id title setting (not checked?)])]]
       [:div {:class (util/kw-path :submit path :checkbox-wrapper)
              :key   (util/kw-path prefix path)}
        [ui-input/checkbox {:id        (name path)
                            :class     "blockbox-toggle-6"
                            :on-change on-change
                            :disabled? (and checked? one-active)
                            :checked?  checked?}]
        [:label.blockbox-label {:for path}
         (common/loc (if (= path :notification.give-verdict)
                       (util/kw-path :foreman.navigate-to-verdict)
                       (util/kw-path :foreman setting)))]])]))

(defn submit-view []
  (let [org-id (<sub [::org-subs/org-id])
        prefix (util/kw-path :auth-admin :foreman-settings :submit)]
    [:<>
     [:h2.mb-4.help-adjacent-title (common/loc (util/kw-path prefix "title"))]
     [components/help-toggle {:text-loc (util/kw-path prefix "help")}]
     [:div.blockbox-wrapper
      [settings-for-type :notification prefix org-id [:acknowledge :approve :give-verdict]]
      [settings-for-type :application  prefix org-id [:approve :give-verdict]]]]))

(defonce mounter (ui-dom/init :reagent (fn [] submit-view)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
