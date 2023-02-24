(ns lupapalvelu.chatbot
  "GetJenny chatbot support for Lupapiste"
  (:require [sade.env :as env]))

(def icon-url (str (env/server-address) "/lp-static/img/chatbot.png"))

(def chatbot
  {:id                                  "bundle"
   :src                                 "https://widget-telwin.getjenny.com/static/js/main.js"
   :data-accent-color                   "#2b1d44"
   :data-auto-open-time                 "0"
   :data-background-color               "#f5f5f5"
   :data-background-image-url           ""
   :data-background-position            "center"
   :data-background-repeat              "no-repeat"
   :data-background-size                "cover"
   :data-background-transparency        "1"
   :data-bot-icon-border-radius         "0"
   :data-bot-icon-height                "40"
   :data-bot-icon-url                   icon-url
   :data-bot-icon-width                 "40"
   :data-bot-reply-delay                "1"
   :data-bubble-corners                 "10 10 0 10"
   :data-button-font-size               "14"
   :data-button-font                    "arial"
   :data-button-label                   "Lähetä"
   :data-chat-corners                   "25"
   :data-chat-icon-position             "bottom: 23px; right: 20px;"
   :data-chat-icon-shape                "10 10 10 10"
   :data-chat-icon-url                  icon-url
   :data-chat-location-horizontal       "right"
   :data-chat-location-vertical         "bottom"
   :data-chat-location                  "right"
   :data-chat-margin-horizontal         "20px"
   :data-chat-margin-vertical           "0px"
   :data-connector-id                   "33594488-6617-11eb-b1a5-b5aa2c66dbed"
   :data-customer-logo-side-margins     "0"
   :data-customer-logo-url              icon-url
   :data-customer-logo-width-percentage "25"
   :data-disable-chat-on-buttons        "false"
   :data-disclaimer-background-color    "#2b1d44"
   :data-disclaimer-text                ""
   :data-enable-feedback                "true"
   :data-enable-font-size-option        "true"
   :data-enable-high-contrast-option    "true"
   :data-fixed-size                     "true"
   :data-footer-color                   "#B7B7B7"
   :data-footer-text                    ""
   :data-footer-url                     ""
   :data-header-exists                  "true"
   :data-height                         "550px"
   :data-identifier                     ""
   :data-input-font-size                "14"
   :data-input-font                     "arial"
   :data-like-off-color                 "#000000"
   :data-like-on-color                  "#FFFFFF"
   :data-open-chat-on-load              "false"
   :data-placeholder                    "Kirjoita viestisi tähän"
   :data-send-icon-url                  ""
   :data-shadows                        "0 5px 20px -3px rgba(40,40,40,.1)"
   :data-show-getjenny-reference        "false"
   :data-show-widget-borders            "true"
   :data-target-element                 ""
   :data-theme-color                    "#140544"
   :data-timeout                        "60"
   :data-title-alignment                "flex-start"
   :data-title-text-color               "#fff"
   :data-title                          "Pilvi"
   :data-use-chat-icon                  "true"
   :data-widget-font-size               "18"
   :data-widget-font                    "arial"
   :data-widget-shape                   "10 10 0 10"
   :data-width                          "450px"
   :async                               "true"})

(defn chatbot-script-tag
  "Enlive script element if `chatbot` feature is enabled."
  []
  (when (env/feature? :chatbot)
    {:tag   :script
     :attrs chatbot}))

(defn chatbot-hiccup
  "Hiccup chatbot element if `chatbot` feature is enabled."
  []
  (when (env/feature? :chatbot)
    [:script chatbot]))
