(ns com.sitecrafting.cumulus.crop
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.string :refer [join split]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]
   ["cropperjs" :as Cropper]
   ["react-dom"]
   [re-frame.core :as rf]))

;; On mount, we'll store the instance of our Cropper in here,
;; so we can refer to it later
;; (def !cropper (atom nil))

(comment
  ;; Evaluate any of these forms in your editor

  ;; (.setAspectRatio @!cropper (/ 16 9))
  ;; (.setAspectRatio @!cropper (/ 4 3))

  ;; (.reset @!cropper)

  js/CUMULUS_CONFIG
  @(rf/subscribe [::img-config])
  ;; => {:bucket "sean-dean",
  ;;     :version "v1596590505",
  ;;     :folder "sc-test",
  ;;     :filename "heron.jpg",
  ;;     :sizes
  ;;     [{:size_name "thumbnail", :width 150, :height 150}
  ;;      {:size_name "medium", :width 300, :height 300}
  ;;      {:size_name "medium_large", :width 768, :height 0}
  ;;      {:size_name "large", :width 1024, :height 1024}
  ;;      {:size_name "post-thumbnail", :width 1200, :height 9999}
  ;;      {:size_name "twentytwenty-fullscreen", :width 1980, :height 9999}]}

  ;;
  )

;; Database config

(rf/reg-event-db
 ::init-db
 (fn [_ [_ config]]
   (let [config (keywordize-keys config)
         ;; TODO remove?
         sizes (:sizes config)
         current-size (first sizes)
         {:keys [width height size_name]} current-size]
     (js/console.log (get-in config [:params_by_size (keyword size_name) :edit_mode]))
     {:img-config config
      :current-size current-size
      ;; TODO make this dynamic and remove this literal index
      :edit-mode (keyword (or
                           (get-in config [:params_by_size (keyword size_name) :edit_mode])
                           :scale))
      :aspect-ratio (/ width height)
      ;; TODO remove?
      :sizes sizes})))

;; Image info

(rf/reg-sub ::img-config :img-config)
(rf/reg-sub ::current-size :current-size)

(rf/reg-event-db
 ::update-current-size
 (fn [db [_ size]]
   (assoc db :current-size size)))

;; CropperJS params

;; Init the CropperJS instance.
;; https://github.com/fengyuanchen/cropperjs/blob/master/README.md
(rf/reg-sub
 ::cropper-js-params
 (fn [db]
   (let [{:keys [width height]} (:current-size db)]
     #js {:crop (fn [event]
                  (let [params (.-detail event)]
                    (rf/dispatch [::update-transform-params
                                  :crop
                                  {:x (js/Math.round (.-x params))
                                   :y (js/Math.round (.-y params))
                                   :w (js/Math.round (.-width params))
                                   :h (js/Math.round (.-height params))}])))
          :aspectRatio (when (> height 0) (/ width height))
          ;; TODO enforce minimums based on actual current-size in proportion to rendered viewport
          :minCropBoxWidth width
          :minCropBoxHeight height
          :background false
          :scalable false
          :movable false
          :rotatable false
          :zoomable false})))

;; Edit mode (whether we're scaling vs. manually cropping)

(defn- db->size-name [db]
  (keyword (get-in db [:current-size :size_name])))

(defn db->edit-mode [db]
  (keyword (get-in db [:img-config :params_by_size (db->size-name db) :edit_mode])))

(rf/reg-sub ::edit-mode db->edit-mode)

;; Dimensions

(defn- db->target-size [{:keys [current-size]}]
  [(:width current-size) (:height current-size)])

(defn- db->cropper-params [db]
  (get-in db [:img-config :params_by_size (db->size-name db) :crop]))

(defn db->transform-params [{:keys [img-config] :as db}]
  (let [params (assoc (db->cropper-params db)
                      :edit-mode (db->edit-mode db)
                      :target-size (db->target-size db))]
    {:cloud (:cloud img-config)
     :filename (:filename img-config)
     :transforms (cloud/params->transforms params)}))

(defmulti update-transform-params (fn [_ [_ mode]]
                                    (keyword mode)))

(defmethod update-transform-params :crop [db [_ _ crop]]
  (assoc-in db [:img-config :params_by_size (db->size-name db)] {:edit_mode "crop"
                                                                 :crop crop}))

(defmethod update-transform-params :scale [db _]
  (assoc-in db [:img-config :params_by_size (db->size-name db)] {:edit_mode "scale"}))

(rf/reg-event-db ::update-transform-params update-transform-params)
(rf/reg-event-db
 ::update-edit-mode
 (fn [db [_ mode]]
   (update-transform-params db [::update-transform-params mode {}])))

;; Compute the end result: the Cloudinary URL for our custom crop.

(rf/reg-sub ::cloudinary-url (fn [db]
                               (cloud/crop->url (db->transform-params db))))


;; Helpers

(defn size-name->label [s]
  (join " " (split s #"[-_]")))


;; Components



(defn cropperjs []
  (let [!ref (atom nil)
        !cropper (atom nil)]
    (fn []
      (let [{:keys [full_url]} @(rf/subscribe [::img-config])
            current-size @(rf/subscribe [::current-size])]
        [:div#cumulus-cropperjs-container
         {:ref (fn [elem]
                 (reset! !ref elem)
                 (when-let [img (js/document.getElementById "cumulus-img")]
                   (when @!cropper (.destroy @!cropper))
                   (reset! !cropper (Cropper. img @(rf/subscribe [::cropper-js-params current-size])))))}
         ;; By putting the image in here, we tell CropperJS to inject its UI here.
         [:img#cumulus-img {:src full_url}]]))))

(defn scaled-img []
  (let [img-url @(rf/subscribe [::cloudinary-url])]
    [:div.cumulus-scaled-img-container
     [:img#cumulus-img {:src img-url}]]))

(defn crop-ui []
  (let [img-url @(rf/subscribe [::cloudinary-url])
        params-by-size (:params_by_size @(rf/subscribe [::img-config]))
        edit-mode @(rf/subscribe [::edit-mode])
        cropping? (= :crop edit-mode)
        {:keys [width height] :as current-size} @(rf/subscribe [::current-size])
        {:keys [sizes full_url full_width full_height]} @(rf/subscribe [::img-config])]
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
                                         (rf/dispatch [::update-current-size size])))}
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
                             ;; TODO update-transform-params
                             (rf/dispatch [::update-edit-mode (if cropping? :scale :crop)]))}
             (if cropping? "Scale" "Crop")]]
           [:li "Flip horizontal"]
           [:li "Flip vertical"]]]

         [:footer
          [:span.cumulus-control
           [:button {:on-click #(rf/dispatch [::save!])} "Save"]
           [:button {:on-click #(rf/dispatch [::cancel])} "Cancel"]]]
         
         [:aside.debugger
          [:pre (js/JSON.stringify (clj->js params-by-size) nil 2)]]]]]]]))
