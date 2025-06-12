(ns lupapalvelu.ui.filebank.keyword-container
  (:require [rum.core :as rum]
            [lupapalvelu.ui.components :as components]))


(rum/defc keyword-container
  "Arguments Initial value, options
   Options [all optional]
    callback   callback fn to save file keywords."
  [default-keywords {:keys [callback test-id new?] :or {new? false}}]
  (let [[keywords set-keywords!] (rum/use-state (or default-keywords []))
        [active set-active!] (rum/use-state nil)]
    [:div.attachment-operations
     (conj
       (into [:div]
             (for [[i kw] (map-indexed vector keywords)]
               (if-not (= active i)
                 [:span.keyword-box
                  [:i.lupicon-circle-remove
                   {:data-test-id (str test-id "-" i "-remove")
                    :on-click     #(let [keywords-left (into (subvec keywords 0 i) (subvec keywords (inc i)))]
                                     (set-keywords! keywords-left)
                                     (callback keywords-left))}]
                  [:label
                   {:data-test-id (str test-id "-" i "-edit")
                    :on-click     #(set-active! i)}
                   kw]
                  [:i.lupicon-pen {:on-click #(set-active! i)}]]
                 [:span.keyword-box
                  (components/text-edit kw
                                        {;;:default-value kw
                                     :test-id       (if (empty? kw)
                                                      (str test-id "-new")
                                                      (str test-id "-" i "-input"))
                                     :autoFocus     true
                                     :on-blur       #(let [kws (assoc keywords i (-> % .-target .-value))]
                                                       (set-keywords! kws)
                                                       (callback kws)
                                                       (set-active! nil))
                                     :on-key-up     #(case (.-keyCode %)
                                                       27 (set-active! nil)
                                                       13 (let [kws (assoc keywords i (-> % .-target .-value))]
                                                            (set-keywords! kws)
                                                            (callback kws)
                                                            (set-active! nil))
                                                       nil)})
                  [:i.lupicon-pen]])))
       (components/icon-button
         {:test-id   (str test-id "-add")
          :icon      :lupicon-circle-plus
          :class     [:positive :add-keyword]
          :text-loc  "auth-admin.tags.add"
          :on-click  #(do
                        (set-keywords! (conj keywords ""))
                        (set-active! (count keywords)))}))]))
