(ns com.sitecrafting.cumulus.crop
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.string :refer [join split]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]
   ["cropperjs" :as Cropper]
   ["react-dom"]
   [re-frame.core :as rf]
   [reagent.core :as r]))

;; On mount, we'll store the instance of our Cropper in here,
;; so we can refer to it later
;; (def !cropper (atom nil))

(comment
  ;; Evaluate any of these forms in your editor

  ;; (.setAspectRatio @!cropper (/ 16 9))
  ;; (.setAspectRatio @!cropper (/ 4 3))

  ;; (.reset @!cropper)

  @(rf/subscribe [::crop-params])

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
         sizes (:sizes config)
         current-size (first sizes)
         {:keys [width height size_name]} current-size
         params (get-in config [:params_by_size (keyword size_name)])
         mode (keyword (:edit_mode params))
         crop (:crop params)]
     {:img-config config
      :current-size current-size
      :edit-mode mode
      :aspect-ratio (/ width height)
      :crop-params crop
      :sizes sizes})))

;; Image info

;; CropperJS instance
(defonce !cropper (r/atom nil))

(rf/reg-sub ::img-config :img-config)
(rf/reg-sub ::current-size :current-size)

(defn- size->name [size]
  (keyword (:size_name size)))

(defn update-current-size [{:keys [db]} [_ new-size]]
  (let [saved-size (get-in db [:img-config :params_by_size (size->name new-size)])
        saved-edit-mode (keyword (:edit_mode saved-size))
        saved-crop (:crop saved-size)]
    {:db (assoc db
                :current-size new-size
                :edit-mode saved-edit-mode
                :crop-params saved-crop)
     ::update-crop-params (when (= :crop saved-edit-mode)
                            [saved-crop])}))

(defmulti params-to-save :edit-mode)

(defmethod params-to-save :scale [_]
  {:edit_mode "scale"})

(defmethod params-to-save :crop [{:keys [crop-params]}]
  {:edit_mode "crop"
   :crop crop-params})

(defn unsaved-changes? [{:keys [img-config current-size] :as db}]
  (let [size (keyword (:size_name current-size))]
    ;; TODO fix params-to-save ??
    (js/console.log (clj->js (get-in img-config [:params_by_size size])) (clj->js (params-to-save db)))
    (not= (get-in img-config [:params_by_size size])
          (params-to-save db))))

(defn db->update-sizes-config [{:keys [img-config current-size] :as db}]
  (assoc-in img-config [:params_by_size (size->name current-size)] (params-to-save db)))

(defn save-current-size [{:keys [db]}]
  (let [config (db->update-sizes-config db)]
    {:db (assoc db :img-config config)
     ::save-current-size! config}))

(defn reset-current-size [{:keys [db]}]
  {:dispatch [::update-current-size (:current-size db)]})

(rf/reg-event-fx ::update-current-size update-current-size)
(rf/reg-event-fx ::reset-current-size reset-current-size)
(rf/reg-event-fx ::save-current-size! save-current-size)

(rf/reg-fx ::save-current-size! (fn [{:keys [attachment_id params_by_size]}]
                                  (-> (js/fetch (str "/wp-json/v1/attachment/" attachment_id)
                                                #js {:method "POST"
                                                     :body (js/JSON.stringify (clj->js params_by_size))})
                                      (.then (fn [response]
                                               (js/console.log response))))))

(defn update-crop-params [[params]]
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
  (update-crop-params [{:w 200 :h 200 :x 10 :y 10}])
  (update-crop-params [{:w 350 :h 350 :x 5 :y 5}])
  (update-crop-params [{:w 1412 :h 1412 :x 1069 :y 568}])

  ;;
  )

(rf/reg-fx ::update-crop-params update-crop-params)


;; CropperJS params

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
                     (rf/dispatch [::set-crop-params
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

;; Edit mode (whether we're scaling vs. manually cropping)

(rf/reg-sub ::edit-mode :edit-mode)
(rf/reg-event-db ::update-edit-mode (fn [db [_ mode]]
                                      (assoc db :edit-mode mode)))

;; Dimensions

(rf/reg-sub ::crop-params :crop-params)
(rf/reg-sub ::unsaved-changes? unsaved-changes?)
(rf/reg-event-db ::set-crop-params (fn [db [_ params]]
                                     (assoc db :crop-params params)))

;; Compute the end result: the Cloudinary URL for our custom crop.

(rf/reg-sub
 ::cloudinary-url
 (fn [{:keys [crop-params img-config current-size]}]
   (cloud/crop->url (merge img-config
                           crop-params
                           {:target-size [(:width current-size)
                                          (:height current-size)]}))))

(comment
  @(rf/subscribe [::unsaved-changes?])

  ;;
  )

;; Helpers

(defn size-name->label [s]
  (join " " (split s #"[-_]")))


;; Components



(defn cropperjs []
  (r/create-class
   {:reagent-render
    (fn []
      (let [{:keys [full_url]} @(rf/subscribe [::img-config])
            ;; We don't strictly need this, but it's a simple way
            ;; to get this component to update when we switch between sizes,
            ;; so we can dispatch ::update-current-size
            current-size (:size_name @(rf/subscribe [::current-size]))]
        [:div#cumulus-cropperjs-container {:data-size (:size_name current-size)}
         ;; By putting the image in here, we tell CropperJS to inject its UI here.
         [:img#cumulus-img {:src full_url}]]))

    :component-did-update
    (fn []
      (rf/dispatch [::update-current-size @(rf/subscribe [::current-size])]))

    :component-did-mount
    (fn []
      (when-let [img (js/document.getElementById "cumulus-img")]
        (reset! !cropper @(rf/subscribe [::cropper-js img]))))}))

(defn scaled-img []
  (let [img-url @(rf/subscribe [::cloudinary-url])]
    [:div.cumulus-scaled-img-container
     [:img#cumulus-img {:src img-url}]]))

(defn crop-ui []
  (let [img-url @(rf/subscribe [::cloudinary-url])
        edit-mode @(rf/subscribe [::edit-mode])
        cropping? (= :crop edit-mode)
        {:keys [width height] :as current-size} @(rf/subscribe [::current-size])
        {:keys [sizes full_url full_width full_height]} @(rf/subscribe [::img-config])
        confirm!? #(or (not @(rf/subscribe [::unsaved-changes?]))
                       (js/confirm "Do you want to save your changes?"))]
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
                                           (rf/dispatch [::save-current-size!])
                                           (rf/dispatch [::update-current-size size]))))}
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
                             (rf/dispatch [::update-edit-mode (if cropping? :scale :crop)]))}
             (if cropping? "Scale" "Crop")]]
           [:li "Flip horizontal"]
           [:li "Flip vertical"]]]

         [:footer
          [:span.cumulus-control
           [:button {:on-click #(rf/dispatch [::save-current-size!])} "Save"]
           [:button {:on-click #(rf/dispatch [::reset-current-size])} "Reset"]]
          [:pre
           (js/JSON.stringify (clj->js @(rf/subscribe [::crop-params])) nil 2)]]]]]]]))
