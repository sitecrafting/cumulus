(ns com.sitecrafting.cumulus.ui
  (:require
   [clojure.string :refer [join split]]
   ["cropperjs" :as Cropper]
   [com.sitecrafting.cumulus.crop :as c]
   ["react-dom"]
   [re-frame.core :as rf]
   [reagent.core :as r]))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;   CropperJS Controls  ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce !cropper (r/atom nil))

(defn update-cropper-params [[params]]
  ;; CropperJS understands the Crop Box size in terms of on-screen pixels,
  ;; whereas Cloudinary crops are in terms of the pixel width/height/offsets
  ;; of the entire full-size image. CropperJS gives us getImageData()
  ;; (https://github.com/fengyuanchen/cropperjs/blob/master/README.md#getimagedata),
  ;; which we can use to compute the ratio to size the saved crop dimensions down to
  ;; their final rendered counterparts.
  (let [img-data (.getImageData @!cropper)
        ratio (/ (.-width img-data) (.-naturalWidth img-data))
        ratio* #(js/Math.round (* ratio %))]
    (.setCropBoxData @!cropper
                     #js {:width  (ratio* (:w params))
                          :height (ratio* (:h params))
                          :top    (ratio* (:y params))
                          :left   (ratio* (:x params))})))

(comment
  (update-cropper-params [{:w 200 :h 200 :x 10 :y 10}])
  (update-cropper-params [{:w 350 :h 350 :x 5 :y 5}])
  (update-cropper-params [{:w 1412 :h 1412 :x 1069 :y 568}])

  ;;
  )

(rf/reg-fx :ui/update-cropper-params update-cropper-params)

;; Init the CropperJS instance.
;; https://github.com/fengyuanchen/cropperjs/blob/master/README.md
(rf/reg-sub
 ::cropper-js
 (fn [db [_ img-elem]]
   (let [{:keys [width height]} (:current-size db)]
     (Cropper.
      img-elem
      #js {:crop (fn [event]
                   (let [params (.-detail event)]
                     (rf/dispatch [::c/set-crop-params
                                   {:x (js/Math.round (.-x params))
                                    :y (js/Math.round (.-y params))
                                    :w (js/Math.round (.-width params))
                                    :h (js/Math.round (.-height params))}])))
           :aspectRatio (when (> height 0) (/ width height))
           :minCropBoxWidth width
           :minCropBoxHeight height
           :background false
           :scalable false
           :movable false
           :rotatable false
           :zoomable false}))))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;        Helpers        ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn size-name->label [s]
  (join " " (split s #"[-_]")))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;      Components       ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn cropperjs []
  (r/create-class
   {:reagent-render
    (fn []
      (let [{:keys [full_url]} @(rf/subscribe [::c/img-config])
            ;; We don't strictly need this, but it's a simple way
            ;; to get this component to update when we switch between sizes,
            ;; so we can dispatch ::c/update-current-size
            current-size (:size_name @(rf/subscribe [::c/current-size]))]
        [:div#cumulus-cropperjs-container {:data-size (:size_name current-size)}
         ;; By putting the image in here, we tell CropperJS to inject its UI here.
         [:img#cumulus-img {:src full_url}]]))

    :component-did-update
    (fn []
      (rf/dispatch [::c/update-current-size @(rf/subscribe [::c/current-size])]))

    :component-did-mount
    (fn []
      (when-let [img (js/document.getElementById "cumulus-img")]
        (reset! !cropper @(rf/subscribe [::cropper-js img]))))}))

(defn scaled-img []
  (let [img-url @(rf/subscribe [::c/cloudinary-url])]
    [:div.cumulus-scaled-img-container
     [:img#cumulus-img {:src img-url}]]))

(defn crop-ui []
  (let [img-url @(rf/subscribe [::c/cloudinary-url])
        edit-mode @(rf/subscribe [::c/edit-mode])
        cropping? (= :crop edit-mode)
        {:keys [width height] :as current-size} @(rf/subscribe [::c/current-size])
        {:keys [sizes full_url full_width full_height]} @(rf/subscribe [::c/img-config])
        unsaved-changes? @(rf/subscribe [::c/unsaved-changes?])
        confirm!? #(or (not unsaved-changes?)
                       (js/confirm "Do you want to save your changes?"))
        save-crop! #(when unsaved-changes?
                      (rf/dispatch [::c/save-current-size]))
        reset-crop! #(when unsaved-changes?
                       (rf/dispatch [::c/reset-current-size]))]
    [:div.cumulus-crop-ui
     [:nav [:ul.cumulus-crop-sizes
            (map (fn [{:keys [size_name] :as size}]
                   (let [current? (= (:size_name current-size) size_name)]
                     ^{:key size_name}
                     [:li {:class (when current? "cumulus-current-size")}
                      [:a {:name size_name
                           :href "#"
                           :on-click (fn [e]
                                       (.preventDefault e)
                                       ;; Clicking on the currently selected size should have no effect
                                       (when-not current?
                                         (when (confirm!?)
                                           (rf/dispatch [::c/save-current-size!])
                                           (rf/dispatch [::c/update-current-size size]))))}
                       (size-name->label size_name)]]))
                 sizes)]]

     [:div.stack
      [:div.columns
       [:div.col-60
        (if cropping?
          [cropperjs]
          [scaled-img])]

       [:div.col-40
        [:aside.cumulus-controls.stack
         [:h3 "Resize Image"]

         [:section.cumulus-dimensions
          [:label "Original dimensions: " full_width " x " full_height]
          [:a {:href full_url
               :target "_blank"}
           "View full size image"]]

         [:section.cumulus-dimensions
          [:label "New dimensions:"]
          [:a {:href img-url
               :target "_blank"}
           "View resized image"]]

         [:section.stack-exception
          [:p.description "Image can only scale down from the original dimensions."]
          [:div
           [:span.cumulus-dimension {:data-label "w"} width]
           ;; TODO svg lock
           [:span " 🔒 "]
           [:span.cumulus-dimension {:data-label "h"} height]]]

         [:section.cumulus-resize-options
          [:h3 "Other Image Options"]
          [:ul
           [:li
            [:a {:href "#"
                 :on-click (fn [e]
                             (.preventDefault e)
                             (rf/dispatch [::c/update-edit-mode (if cropping? :scale :crop)]))}
             (if cropping? "Scale" "Crop")]]
           [:li "Flip horizontal"]
           [:li "Flip vertical"]]]

         [:footer
          [:span.cumulus-control
           [:button {:disabled (not unsaved-changes?)
                     :on-click save-crop!}
            "Save"]
           [:button {:disabled (not unsaved-changes?)
                     :on-click reset-crop!}
            "Reset"]]
          [:pre
           (js/JSON.stringify (clj->js @(rf/subscribe [::c/crop-params])) nil 2)]]]]]]]))