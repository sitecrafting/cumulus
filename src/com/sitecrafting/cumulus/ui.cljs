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


; (defonce !cropper (r/atom nil))

; (defn crop-data [_ _]
;   (let [params (.getData @!cropper true)]
;     {:x (.-x params)
;      :y (.-y params)
;      :w (.-width params)
;      :h (.-height params)}))

; (defn update-cropper-params [[params]]
;   ;; CropperJS understands the Crop Box size in terms of on-screen pixels,
;   ;; whereas Cloudinary crops are in terms of the pixel width/height/offsets
;   ;; of the entire full-size image. CropperJS gives us getImageData()
;   ;; (https://github.com/fengyuanchen/cropperjs/blob/master/README.md#getimagedata),
;   ;; which we can use to compute the ratio to size the saved crop dimensions down to
;   ;; their final rendered counterparts.
;   (let [img-data (.getImageData @!cropper)
;         ratio (/ (.-width img-data) (.-naturalWidth img-data))
;         ratio* #(js/Math.round (* ratio %))]
;     (.setCropBoxData @!cropper
;                      #js {:width  (ratio* (:w params))
;                           :height (ratio* (:h params))
;                           :top    (ratio* (:y params))
;                           :left   (ratio* (:x params))})))

; (defn cropper-instance
;   "Initialize a CropperJS instance.
;    See: https://github.com/fengyuanchen/cropperjs/blob/master/README.md"
;   [db [_ img-elem]]
;   (let [{:keys [width height]} (:current-size db)]
;     (Cropper.
;      img-elem
;      #js {:cropend #(let [new-params @(rf/subscribe [::crop-data])]
;                       (rf/dispatch-sync [::c/set-crop-params new-params]))
;           :aspectRatio (when (> height 0) (/ width height))
;           :minCropBoxWidth width
;           :minCropBoxHeight height
;           :background false
;           :scalable false
;           :movable false
;           :rotatable false
;           :zoomable false})))

; (rf/reg-sub ::crop-data crop-data)
; (rf/reg-sub ::cropper-js cropper-instance)

; (rf/reg-fx :ui/update-cropper-params update-cropper-params)




; (rf/reg-sub ::crop-data crop-data)
; (rf/reg-sub ::cropper-js cropper-instance)

; (rf/reg-fx :ui/update-cropper-params update-cropper-params)

(comment
  (update-cropper-params [{:w 200 :h 200 :x 10 :y 10}])
  (update-cropper-params [{:w 350 :h 350 :x 5 :y 5}])
  (update-cropper-params [{:w 1412 :h 1412 :x 1069 :y 568}])

  @(rf/subscribe [::crop-data])

  ;;
  )



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


; (defn cropperjs
;   "Render the main cropping UI."
;   []
;   (r/create-class
;    {:reagent-render
;     (fn []
;       (let [{:keys [full_url]} @(rf/subscribe [::c/img-config])
;             ;; We don't strictly need this, but it's a simple way
;             ;; to get this component to update when we switch between sizes,
;             ;; so we can dispatch ::c/update-current-size
;             current-size (:size_name @(rf/subscribe [::c/current-size]))]
;         [:div#cumulus-cropperjs-container {:data-size (:size_name current-size)}
;          ;; By putting the image in here, we tell CropperJS to inject its UI here.
;          [:img#cumulus-img {:src full_url}]]))

;     :component-did-update
;     (fn []
;       (rf/dispatch [::c/update-current-size @(rf/subscribe [::c/current-size])]))

;     :component-did-mount
;     (fn []
;       (when-let [img (js/document.getElementById "cumulus-img")]
;         (reset! !cropper @(rf/subscribe [::cropper-js img]))))}))

(defn react-image-crop []
  (let [url (:full_url @(rf/subscribe [::c/img-config]))]
    [:img {:src url}]))

(defn crop-size-nav-item
  "Given a size to display and the current size being edited,
   renders a nav item for the given size. Prompts to save any
   unsaved changes."
  [{:keys [size_name] :as size} current-size]
  (let [unsaved-changes? @(rf/subscribe [::c/unsaved-changes?])
        confirm!? #(or (not unsaved-changes?)
                       (js/confirm "Do you want to save your changes?"))
        current? (= (:size_name current-size) size_name)]
    [:li {:class (when current? "cumulus-current-size")}
     [:a {:name size_name
          :href "#"
          :on-click (fn [e]
                      (.preventDefault e)
                      ;; Clicking on the currently selected size should have no effect
                      (when (not current?)
                        ;; Prompt to save any unsaved changes, or switch immediately
                        ;; if there aren't any.
                        (if unsaved-changes?
                          (when (confirm!?)
                            (rf/dispatch [::c/save-current-size])
                            (rf/dispatch [::c/update-current-size size]))
                          (rf/dispatch [::c/update-current-size size]))))}
      (size-name->label size_name)]]))

(defn scaled-img []
  (let [img-url @(rf/subscribe [::c/cloudinary-url])]
    [:div.cumulus-scaled-img-container
     [:img#cumulus-img {:src img-url}]]))

(defn debugger
  "Displays debug info."
  []
  (let [info {:current-size @(rf/subscribe [::c/current-size])
              :new-params   @(rf/subscribe [::c/params-to-save])
              :saved-params  @(rf/subscribe [::c/saved-params])}]
    [:pre
     (js/JSON.stringify (clj->js info) nil 2)]))

(defn crop-ui []
  (let [debug? @(rf/subscribe [::c/debug?])
        img-url @(rf/subscribe [::c/cloudinary-url])
        edit-mode @(rf/subscribe [::c/edit-mode])
        cropping? (= :crop edit-mode)
        {:keys [width height] :as current-size} @(rf/subscribe [::c/current-size])
        {:keys [sizes full_url full_width full_height]} @(rf/subscribe [::c/img-config])
        unsaved-changes? @(rf/subscribe [::c/unsaved-changes?])
        save-crop! #(when unsaved-changes?
                      (rf/dispatch [::c/save-current-size]))
        reset-crop! #(when unsaved-changes?
                       (rf/dispatch [::c/reset-current-size]))]
    [:div.cumulus-crop-ui
     [:nav
      [:ul.cumulus-crop-sizes
       (map (fn [size]
              ^{:key (:size_name size)}
              [crop-size-nav-item size current-size])
            sizes)]]

     [:div.stack
      [:div.columns
       [:div.col-60
        (if cropping?
          ;[cropperjs]
          [react-image-crop]
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
                             (rf/dispatch [::c/update-edit-mode
                                           (if cropping? :scale :crop)]))}
             (if cropping? "Scale" "Crop")]]
           ;; TODO
           #_[:li "Flip horizontal"]
           #_[:li "Flip vertical"]]]

         [:footer
          [:span.cumulus-control
           [:button {:disabled (not unsaved-changes?)
                     :on-click save-crop!}
            "Save"]
           [:button {:disabled (not unsaved-changes?)
                     :on-click reset-crop!}
            "Reset"]]
          (when debug?
            [debugger])]]]]]]))