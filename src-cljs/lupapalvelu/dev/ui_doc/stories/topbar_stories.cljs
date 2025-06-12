(ns lupapalvelu.dev.ui-doc.stories.topbar-stories
  (:require ["@carbon/icons-react/es/Archive$default" :as IconArchive]
            ["@carbon/icons-react/es/DocumentMultiple_01$default" :as IconDocumentMultiple]
            ["@carbon/icons-react/es/Settings$default" :as IconSettings]
            ["@carbon/icons-react/es/ShoppingCart$default" :as IconShoppingCart]
            ["@carbon/icons-react/es/TreeView$default" :as IconTreeView]
            ["@carbon/icons-react/es/User$default" :as IconUser]
            [lupapalvelu.dev.ui-doc.util :refer [action-fn defmeta defstory]]
            [lupapalvelu.ui2.topbar :as topbar]))

(def ^:private example-apps
  [{:key "docs" :label "Dokumentit" :icon IconArchive :href "https://example.fi/docs"}
   {:key "rami" :label "Raportointi" :icon IconDocumentMultiple :href "https://rami.lupapiste.fi/" :disabled? true}
   {:key "billing" :label "Laskutus" :icon IconShoppingCart :href "#!/biller"}
   {:key "toj" :label "Tiedonohjaus" :icon IconTreeView :disabled? true}
   {:key "luparekisteri" :label "Luparekisteri" :icon IconArchive :disabled? true}])

(def ^:private example-user-items
  [{:key "mypage" :label "Omat tiedot ja asetukset" :icon IconUser :href "#!/mypage"}
   {:key "admin" :label "Pääkäyttäjän asetukset" :icon IconSettings :href "#"}])

(defmeta default
  {:title     "Component / Topbar"
   :component topbar/topbar
   :args      {:logo-href     "#!/dashboard"
               :logo-src      "/lp-static/img/lupapiste-logo.svg"
               :on-logout     (action-fn "on-logout")
               :user          {:name "Pena Panaani" :org-name "Jyväskylän kaupunki"}
               :apps-label    "Tuotevalikko"
               :apps-subtitle "Vaihda tuotteiden välillä"}
   :argTypes  {}})

(defstory default-story
  "Full topbar with user, role subtitle, app switcher and user menu."
  [{:keys [logo-href logo-src on-logout user apps-label apps-subtitle]}]
  [topbar/topbar {:logo-href     logo-href
                  :logo-src      logo-src
                  :on-logout     on-logout
                  :user          user
                  :apps          example-apps
                  :apps-label    apps-label
                  :apps-subtitle apps-subtitle
                  :logout-label  "Kirjaudu ulos"
                  :user-items    example-user-items}])

(defstory no-org-name
  "Citizen user — no role subtitle."
  [_]
  [topbar/topbar {:logo-src      "/lp-static/img/lupapiste-logo.svg"
                  :user          {:name "Pena Panaani"}
                  :apps          example-apps
                  :apps-label    "Tuotevalikko"
                  :apps-subtitle "Vaihda tuotteiden välillä"
                  :on-logout     (action-fn "on-logout")
                  :logout-label  "Kirjaudu ulos"
                  :user-items    [{:key "mypage" :label "Omat tiedot ja asetukset" :icon IconUser :href "#!/mypage"}]}])

(defstory all-apps-enabled
  "All apps enabled — no greyed-out items."
  [_]
  [topbar/topbar {:logo-src      "/lp-static/img/lupapiste-logo.svg"
                  :user          {:name "Viranomainen Virtanen" :org-name "Jyväskylän kaupunki"}
                  :apps          (map #(dissoc % :disabled?) example-apps)
                  :apps-label    "Tuotevalikko"
                  :apps-subtitle "Vaihda tuotteiden välillä"
                  :on-logout     (action-fn "on-logout")
                  :logout-label  "Kirjaudu ulos"
                  :user-items    example-user-items}])

(defstory no-user
  "No logged-in user — user menu is not rendered."
  [_]
  [topbar/topbar {:logo-src      "/img/lupapiste-logo.svg"
                  :apps          example-apps
                  :apps-label    "Tuotevalikko"
                  :apps-subtitle "Vaihda tuotteiden välillä"
                  :on-logout     (action-fn "on-logout")}])
