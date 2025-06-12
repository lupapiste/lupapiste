(ns lupapalvelu.html-email.css
  "Styles for the default base template."
  (:require [garden.core :as garden]
            [sade.util :as util]))

(def background    "#ededed")
(def header-bg     "#afccef")
(def footer-bg     "#191147")
(def black         "#011627")
(def white         "#ffffff")
(def violet        "#4f0891")
(def violet-130    "#370666")
(def note-bg       "#e7ecfb") ; blue 10
(def h1            "#08206a") ; blue 150
(def h2            "#09267e") ; blue 140
(def h3            "#0b2c93") ; blue 130
(def night-sky-150 "#0d0924")

(def msg-width "700px")

(def font-family "Arial,Helvetica Neue,Helvetica,sans-serif")

(def style-definition
  (garden/css {:pretty-print? false}
              [[:table.body {:font-family      font-family
                             :font-size        "14px"
                             :background-color background
                             :width            "100%"
                             :height           "100%"}]
               [:table.main {:background-color white}]
               [:table.header {:background-color header-bg
                               :width            msg-width}
                [:a {:font-size       "24px"
                     :font-weight     :bold
                     :color           night-sky-150
                     :text-decoration :none}]]
               [:table.message {:background-color white
                                :width            msg-width}
                [:td.do-not-reply {:font-weight :bold
                                   :color       violet-130}]]
               [:table.footer {:background-color footer-bg
                               :width            msg-width}
                [:a {:color           white
                     :text-decoration :none}]
                [:a.cloudpermit {:font-size       "22px"
                                 :font-weight     :bold}]
                [:a.social {:font-size "14px"}]]
               [:pre {:font-family font-family}]
               [:div.user-note {:white-space      :pre
                                :padding          "4px"
                                :background-color note-bg}]
               [:h1 {:font-size "20px" :color h1 :font-weight :bold}]
               [:h2 {:font-size "18px" :color h2 :font-weight :bold}]
               [:h3 {:font-size "16px" :color h3 :font-weight :bold}]
               ]))

(def STYLES
  "Styles is tree, where eventually each leaf styles are composition of the branch styles
  on-route."
  {:font {:font-family   font-family
          :font-size     "14px"
          :table.body    {:background-color background
                          :width            "100%"
                          :height           "100%"}
          :table.main    {:background-color white}
          :table.message {:background-color white
                          :width            msg-width
                          :table.header     {:background-color header-bg}
                          :table.footer     {:background-color footer-bg}}
          :a.header      {:font-size       "24px"
                          :font-weight     :bold
                          :color           night-sky-150
                          :text-decoration :none}
          :do-not-reply  {:font-weight :bold
                          :color       violet-130}
          :a.footer      {:color           white
                          :text-decoration :none
                          :a.cloudpermit   {:font-size   "22px"
                                            :font-weight :bold}
                          :a.social        {:font-size "14px"}}
          :dt            {:font-weight :bold
                          :margin-top  "0.5em"}
          :dd            {:margin-top    "0.5em"
                          :margin-bottom "0.5em"
                          :dd.ul {:padding-left "1em"}}
          :pre           {:margin    "0.5em 0"
                          :user-note {:padding          "4px"
                                      :background-color note-bg}}
          :bold          {:font-weight :bold
                          :h1          {:font-size "20px" :color h1}
                          :h2          {:font-size "18px" :color h2}
                          :h3          {:font-size "16px" :color h3}}
          :divider       {:border-bottom (str "8px solid " background)}
          :td.button     {:border        :none
                          :border-radius "6px"
                          :cursor        :pointer
                          :padding       "11px 20px"
                          :primary.td    {:background-color violet}}
          :a.button      {:font-size       "18px"
                          :font-weight     :bold
                          :line-height     "120%"
                          :margin          0
                          :text-decoration :none
                          :text-transform  :none
                          :primary.a       {:background-color violet
                                            :color            white}}}
   :code {:font-family "monospace,monospace"}})



(defn- style-fields [& forms]
  (->> forms
       (map (partial util/filter-map-by-val (complement map?)))
       (apply merge)))

(defn- find-and-gather [id form]
  (when (map? form)
    (some (fn [[k v]]
            (if (and (= k id) (map? v))
              (style-fields form v)
              (some->> (find-and-gather id v)
                       (merge (style-fields form)))))
          form)))

(defn find-style
  "Style map for the given `id`. The id is searched from `styles` and default `STYLES`, as
  fallback."
  ([styles id fallback?]
   (some (partial find-and-gather (keyword id))
         [styles (when fallback? STYLES)]))
  ([styles id]
   (find-style styles id true))
  ([id]
   (find-style STYLES id false)))
