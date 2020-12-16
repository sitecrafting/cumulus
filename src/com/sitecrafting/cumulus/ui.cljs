(ns com.sitecrafting.cumulus.ui
  (:require
   [clojure.string :refer [join split]]
  ;  ["cropperjs" :as Cropper]
   [com.sitecrafting.cumulus.crop :as c]
   ["react-dom"]
   ["react-image-crop" :default ReactCrop]
   [re-frame.core :as rf]))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                          ;;
  ;;   Helpers & Co-Effects   ;;
 ;;                          ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def !img (atom nil))

(defn dimensions-cofx
  "Inject the natural and rendered image dimensions into the cofx map."
  [cofx _]
  (let [img @!img]
    (assoc cofx :dimensions {:natural-width (.-naturalWidth img)
                             :natural-height (.-naturalHeight img)
                             :rendered-width (.-width img)
                             :rendered-height (.-height img)})))

(rf/reg-cofx :dimensions dimensions-cofx)

;; TODO readjust crop params
(.addEventListener js/window "resize" #(rf/dispatch [::c/image-loaded]))


(defn size-name->label [s]
  (join " " (split s #"[-_]")))




    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;      Components       ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn react-image-crop []
  (let [url @(rf/subscribe [::c/full-url])
        aspect-ratio @(rf/subscribe [::c/aspect-ratio])
        current-size @(rf/subscribe [::c/current-size])
        scaling-factor @(rf/subscribe [::c/scaling-factor])
        natural->rendered #(js/Math.round (/ % scaling-factor))
        rendered->natural #(js/Math.round (* % scaling-factor))
        {:keys [w h x y]} @(rf/subscribe [::c/crop-params])]
    [:> ReactCrop {:src url
                   :on-image-loaded #(do
                                       (reset! !img %)
                                       (rf/dispatch [::c/image-loaded]))
                   :crop #js {:unit   "px"
                              :width  (natural->rendered w)
                              :height (natural->rendered h)
                              :x      (natural->rendered x)
                              :y      (natural->rendered y)
                              :aspect aspect-ratio}
                   :minWidth (/ (:width current-size) scaling-factor)
                   :on-change #(rf/dispatch-sync
                                [::c/set-crop-params
                                 {:w (rendered->natural (.-width %))
                                  :h (rendered->natural (.-height %))
                                  :x (rendered->natural (.-x %))
                                  :y (rendered->natural (.-y %))}])}]))

(defn crop-size-nav-item
  "Given a size to display and the current size being edited,
   renders a nav item for the given size. Prompts to save any
   unsaved changes."
  [{:keys [size_name] :as size} current-size]
  (let [unsaved-changes? @(rf/subscribe [::c/unsaved-changes?])
        confirm!? #(or (not unsaved-changes?)
                       (js/confirm "Do you want to save your changes? If you don't, reset before switching."))
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
              :saved-params  @(rf/subscribe [::c/saved-params])
              :dimensions @(rf/subscribe [::c/dimensions])
              :scaling-factor @(rf/subscribe [::c/scaling-factor])
              :cloudinary-params @(rf/subscribe [::c/cloudinary-params])}]
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
                       (rf/dispatch [::c/update-current-size current-size]))]
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
           [:button {:class "button button-primary"
                     :disabled (not unsaved-changes?)
                     :on-click save-crop!}
            "Save"]
           [:button {:class "button"
                     :disabled (not unsaved-changes?)
                     :on-click reset-crop!}
            "Reset"]]
          (when debug?
            [debugger])]]]]]]))