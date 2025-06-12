(ns lupapalvelu.ui.topbar-connected
  "Connects the pure `lupapalvelu.ui2.topbar/topbar` to live Lupapiste app state."
  (:require ["@carbon/icons-react/es/Archive$default" :as IconArchive]
            ["@carbon/icons-react/es/DocumentMultiple_01$default" :as IconDocumentMultiple]
            ["@carbon/icons-react/es/Home$default" :as IconHome]
            ["@carbon/icons-react/es/Settings$default" :as IconSettings]
            ["@carbon/icons-react/es/ShoppingCart$default" :as IconShoppingCart]
            ["@carbon/icons-react/es/TreeView$default" :as IconTreeView]
            ["@carbon/icons-react/es/User$default" :as IconUser]
            [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.next.session :as sess]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui2.topbar :refer [topbar]]
            [reagent.core :refer [defc]]))

(defn- purpose->user-item
  "Converts a usage-purpose map to a topbar user-item with the correct label, icon and href.
  Mirrors the KO UserMenu purposeName/purposeIcon/purposeLink functions."
  [{:keys [type orgId]}]
  (let [lang      (<sub [::sess/language])
        org-names (<sub [::sess/org-names])]
    (if (= type "authority-admin")
      (let [org-name (when (and org-names orgId)
                       (get-in org-names [(keyword orgId) (keyword lang)]))]
        {:key   (str "authority-admin-" orgId)
         :label (str (loc "authorityAdmin.settings") (when org-name (str " – " org-name)))
         :icon  IconSettings
         :href  (str "/app/" lang "/authority-admin/" orgId)})
      {:key   (str "purpose-" type)
       :label (loc "permit.service")
       :icon  IconHome
       :href  (str "/app/" lang "/" type)})))

(defc connected-topbar []
  (let [lang             (<sub [::sess/language])
        alts             (<sub [::sess/alt-usage-purposes])
        authority-admin? (<sub [::sess/page-authority-admin?])]
    [topbar
     {:logo-href     "#"
      :on-logo-click (fn [e] (.preventDefault e) (.send js/hub "open-start-page" #js {}))
      :user          {:name     (<sub [::sess/display-name])
                      :org-name (or (<sub [::sess/current-org-name])
                                    (when-let [role (<sub [::sess/role])]
                                      (loc (case role "authority-admin" "authorityAdmin" role))))}
      :apps-label    (loc "topbar.apps.title")
      :apps-subtitle (loc "topbar.apps.subtitle")
      :apps          [{:key       "document-search"
                       :label     (loc "topbar.document-search")
                       :icon      IconArchive
                       :href      (not-empty (loc "document.search.url"))
                       :disabled? (not (<sub [:auth/global? "document-search-access-allowed"]))}
                      {:key       "rami"
                       :label     (loc "reporting")
                       :icon      IconDocumentMultiple
                       :href      "https://rami.lupapiste.fi/"
                       :disabled? (not (<sub [:auth/global? "rami-access"]))}
                      {:key       "billing"
                       :label     (loc "billing")
                       :icon      IconShoppingCart
                       :href      "#!/biller"
                       :disabled? (not (<sub [:auth/global? "biller-organizations"]))}
                      {:key       "toj"
                       :label     (loc "topbar.toj")
                       :icon      IconTreeView
                       :href      (not-empty (loc "toj.url"))
                       :disabled? (not (<sub [:auth/global? "toj-access-allowed"]))}
                      {:key       "luparekisteri"
                       :label     (loc "topbar.luparekisteri")
                       :icon      IconArchive
                       :disabled? true}]
      :user-items    (cond-> [{:key   "mypage"
                               :label (loc "topbar.mypage")
                               :icon  IconUser
                               :href  "#!/mypage"}]
                       (seq alts)       (into (map purpose->user-item alts))
                       authority-admin? (conj {:key   "admin-settings"
                                               :label (loc "authorityAdmin.settings")
                                               :icon  IconSettings
                                               :href  (str "/app/" lang "/authority-admin")}))
      :on-logout     (fn [_] (set! (.-location js/window) (str "/app/" lang "/logout")))}]))
