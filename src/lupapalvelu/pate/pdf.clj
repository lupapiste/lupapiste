(ns lupapalvelu.pate.pdf
  "PDF generation via HTML for Pate verdicts."
  (:require [clojure.java.io :as io]
            [garden.core :as garden]
            [garden.selectors :as sel]
            [hiccup.core :as hiccup]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.pdf.html-template-common :as common]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- page-number-script []
  [:script
   (-> common/wkhtmltopdf-page-numbering-script-path
       io/resource
       slurp)])


(defn html [body & [script?]]
  (str "<!DOCTYPE html>"
       (hiccup/html
        [:html
         [:head
          [:meta {:http-equiv "content-type"
                  :content    "text/html; charset=UTF-8"}]
          [:style {:type "text/css"}
           (garden/css [[:* {:font-family "'Carlito', sans-serif"}]
                        [:.permit {:text-transform :uppercase
                                   :font-weight    :bold}]
                        [:div.header {:padding-bottom "1em"}]
                        [:.page-break {:page-break-before :always}]
                        [:.section {:display :table
                                    :width "100%"
                                    :padding-bottom "1em"}
                         [:&.border {:border-top  "1px solid black"
                                     :padding-top  "1em"}]
                         [:&.header {:padding 0
                                     :border-bottom  "1px solid black"
                                     }]
                         [:.row {:display :table-row}
                          [:&.pad [:.cell {:padding-bottom "0.5em"}]]
                          [:.cell {:display     :table-cell
                                   :white-space :pre-wrap
                                   :padding-right "1em"}
                           [:&:last-child {:padding-right 0}]
                           [:&.right {:text-align :right}]
                           [:&.center {:text-align :center}]
                           [:&.bold {:font-weight :bold}]]
                          (map (fn [n]
                                 [(keyword (str ".cell.cell--" n))
                                  {:display     :table-cell
                                   :white-space :pre-wrap
                                   :width       (str n "%")}])
                               (range 10 101 10))
                          ]]])]]
         [:body
          body
          (when script?
            (page-number-script))]])))

(defn verdict-body [lang application {:keys [published data]}]
  (let [loc (partial i18n/localize-and-fill lang)]
    [:div
     [:div.section
      [:div.row
       [:div.cell.cell--30 "Morbi tempus, leo in aliquet placerat, eros nisl congue ligula, in sagittis leo diam sed velit. Aliquam erat volutpat. Vestibulum tristique consectetur diam vitae elementum. Etiam vulputate libero nec sapien scelerisque, at tincidunt mauris convallis. Vivamus id facilisis lectus. Nullam mattis nisi in est fringilla, nec mollis turpis finibus. Nam risus nunc, eleifend quis nunc et, blandit molestie ligula. Ut volutpat facilisis ornare. Vivamus volutpat rhoncus posuere. Maecenas non venenatis velit, eu aliquam eros. Curabitur tincidunt massa sit amet tempor aliquam. Vivamus eu mollis lectus."]
       [:div.cell.cell--70.right "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin vitae finibus nisi. Cras venenatis rhoncus eleifend. Vestibulum non nisl et purus rhoncus vulputate. Curabitur laoreet nisi in arcu lacinia porta. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Nunc ac nunc quis eros condimentum blandit ut id ligula. Aenean ut metus vitae eros fringilla tristique. Integer lacus libero, scelerisque vitae ex tincidunt, aliquet dictum dolor. Morbi vehicula commodo nisi eu tempor. Curabitur sit amet laoreet mi. Lorem ipsum dolor sit amet, consectetur adipiscing elit."]]]
     [:div.section.border
      [:div.row
       [:div.cell.cell--30 "Morbi tempus, leo in aliquet placerat, eros nisl congue ligula, in sagittis leo diam sed velit. Aliquam erat volutpat. Vestibulum tristique consectetur diam vitae elementum. Etiam vulputate libero nec sapien scelerisque, at tincidunt mauris convallis. Vivamus id facilisis lectus. Nullam mattis nisi in est fringilla, nec mollis turpis finibus. Nam risus nunc, eleifend quis nunc et, blandit molestie ligula. Ut volutpat facilisis ornare. Vivamus volutpat rhoncus posuere. Maecenas non venenatis velit, eu aliquam eros. Curabitur tincidunt massa sit amet tempor aliquam. Vivamus eu mollis lectus."]
       [:div.cell.cell--70.right "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin vitae finibus nisi. Cras venenatis rhoncus eleifend. Vestibulum non nisl et purus rhoncus vulputate. Curabitur laoreet nisi in arcu lacinia porta. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Nunc ac nunc quis eros condimentum blandit ut id ligula. Aenean ut metus vitae eros fringilla tristique. Integer lacus libero, scelerisque vitae ex tincidunt, aliquet dictum dolor. Morbi vehicula commodo nisi eu tempor. Curabitur sit amet laoreet mi. Lorem ipsum dolor sit amet, consectetur adipiscing elit."]]]
     (repeat 10 [:div
                 [:p "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin vitae finibus nisi. Cras venenatis rhoncus eleifend. Vestibulum non nisl et purus rhoncus vulputate. Curabitur laoreet nisi in arcu lacinia porta. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Nunc ac nunc quis eros condimentum blandit ut id ligula. Aenean ut metus vitae eros fringilla tristique. Integer lacus libero, scelerisque vitae ex tincidunt, aliquet dictum dolor. Morbi vehicula commodo nisi eu tempor. Curabitur sit amet laoreet mi. Lorem ipsum dolor sit amet, consectetur adipiscing elit."]
                 [:p "Morbi tempus, leo in aliquet placerat, eros nisl congue ligula, in sagittis leo diam sed velit. Aliquam erat volutpat. Vestibulum tristique consectetur diam vitae elementum. Etiam vulputate libero nec sapien scelerisque, at tincidunt mauris convallis. Vivamus id facilisis lectus. Nullam mattis nisi in est fringilla, nec mollis turpis finibus. Nam risus nunc, eleifend quis nunc et, blandit molestie ligula. Ut volutpat facilisis ornare. Vivamus volutpat rhoncus posuere. Maecenas non venenatis velit, eu aliquam eros. Curabitur tincidunt massa sit amet tempor aliquam. Vivamus eu mollis lectus."]
                 [:p "Phasellus placerat magna tortor, ut auctor nulla sodales in. Nullam tellus mauris, accumsan ut ornare sit amet, euismod sit amet urna. Donec aliquet aliquam ullamcorper. In hac habitasse platea dictumst. Aenean rhoncus, diam id varius eleifend, tellus lectus laoreet urna, id euismod elit ex ut libero. Sed tempus nisl erat, id aliquet ante porttitor vel. Phasellus laoreet massa urna, non elementum neque rhoncus eu. Sed fringilla velit elit, eget sagittis nunc malesuada in. Sed convallis egestas euismod. Sed at libero congue, maximus turpis ac, aliquam massa. Pellentesque vitae magna ex. Donec nec est tortor."]
                 [:p "Ut pulvinar dolor est, ac semper mauris maximus ac. Vivamus tristique tellus eget pharetra efficitur. Sed at ultricies risus. Integer vel massa maximus, egestas lacus vitae, maximus ex. Suspendisse viverra dignissim odio, quis rutrum tortor. Fusce mattis felis quis nisl maximus efficitur. Nullam libero risus, accumsan ut ligula nec, consectetur semper nunc. Vivamus finibus, est vel feugiat congue, leo risus vulputate ipsum, at placerat nulla mi ac tellus. In hac habitasse platea dictumst. Integer nec tristique velit, ut volutpat lorem. Vestibulum ante odio, iaculis eu odio in, sollicitudin molestie nisl. Nunc egestas urna ut placerat tempor. In sit amet magna eget libero ullamcorper iaculis. Etiam vitae libero posuere, gravida massa efficitur, hendrerit augue."]
                 [:p "Mauris at lorem eleifend, semper est id, efficitur diam. Pellentesque dignissim massa ligula. Nullam malesuada justo vitae tortor pulvinar, sed egestas orci rhoncus. Suspendisse sollicitudin velit quis ipsum facilisis fringilla. Proin at diam a nisi lobortis ultricies sed ut odio. Phasellus accumsan est eget viverra sagittis. Donec posuere rhoncus massa. Donec magna diam, efficitur in ultricies a, sollicitudin fermentum nibh. Cras at ullamcorper quam. Etiam laoreet quam sed felis laoreet, non lacinia lacus dapibus. Donec at tellus felis. In mattis quis urna in imperdiet."]])
     [:div.page-break "Tämän pitäisi olla uudella sivulla."]]))

(defn verdict-header
  [lang {:keys [organization]} {:keys [published category data]}]
  [:div.header
   [:div.section.header
    [:div.row.pad
     [:div.cell.cell--30 (org/get-organization-name organization lang)]
     [:div.cell.cell--40.center
      [:div (i18n/localize lang :attachmentType.paatoksenteko.paatos)]]
     [:div.cell.cell--30.right
      [:div.permit (i18n/localize lang (format "pdf.%s.permit" category))]]]
    [:div.row
     [:div.cell.cell--30 (:section data)]
     [:div.cell.cell--40.center [:div (util/to-local-date published)]]
     [:div.cell.cell--30.right "Sivu " [:span#page-number "sivu"]]]]])


(defn verdict-pdf [lang application {:keys [published] :as verdict}]
  (html-pdf/create-and-upload-pdf
   application
   "pate-verdict"
   (html (verdict-body lang application verdict))
   {:filename (i18n/localize-and-fill lang
                                      :pate.pdf-filename
                                      (:id application)
                                      (util/to-local-datetime published))
    :header (html (verdict-header lang application verdict) true)
    :footer (html nil)}))


(defn create-verdict-attachment
  "1. Create PDF file for the verdict.
   2. Create verdict attachment.
   3. Bind 1 into 2."
  [{:keys [lang application user created] :as command} verdict]
  (let [{:keys [file-id mongo-file]} (verdict-pdf lang application verdict)
        _                            (when-not file-id
                                       (fail! :pate.pdf-verdict-error))
        {attachment-id :id
         :as           attachment}   (att/create-attachment!
                                      application
                                      {:created           created
                                       :set-app-modified? false
                                       :attachment-type   {:type-group "paatoksenteko"
                                                           :type-id    "paatos"}
                                       :target            {:type "verdict"
                                                           :id   (:id verdict)}
                                       :locked            true
                                       :read-only         true
                                       :contents          (i18n/localize lang
                                                                         :pate-verdict)})]
    (bind/bind-single-attachment! (update-in command
                                             [:application :attachments]
                                             #(conj % attachment))
                                  (mongo/download file-id)
                                  {:fileId       file-id
                                   :attachmentId attachment-id}
                                  nil)))
