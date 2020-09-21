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
        saved-crop (:crop saved-size)
        crop-fx (if (= :crop saved-edit-mode)
                  {::update-crop-params [saved-crop]}
                  {::destroy-cropper []})]
    (js/console.log "crop for" (:size_name new-size) (clj->js saved-crop))
    (merge crop-fx {:db (assoc db
                               :current-size new-size
                               :edit-mode saved-edit-mode
                               :crop-params saved-crop)})))

(defmulti params-to-save :edit-mode)

(defmethod params-to-save :scale [_]
  {:edit_mode "scale"})

(defmethod params-to-save :crop [{:keys [crop-params]}]
  {:edit_mode "crop"
   :crop crop-params})

(defn unsaved-changes? [{:keys [img-config current-size] :as db}]
  (let [size (keyword (:size_name current-size))]
    (not= (get-in img-config [:params_by_size size])
          (params-to-save db))))

(defn db->update-sizes-config [{:keys [img-config current-size] :as db}]
  (assoc-in img-config [:params_by_size (size->name current-size)] (params-to-save db)))

(defn save-current-size [{:keys [db]}]
  (let [config (db->update-sizes-config db)]
    (js/console.log (clj->js (get-in config [:params_by_size (size->name (:current-size db)) :crop])))
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
  (js/console.log ".setCropBoxData" (clj->js params))
  (.setCropBoxData @!cropper
                   #js {:width (:w params)
                        :height (:h params)
                        :top (:y params)
                        :left (:x params)}))

(comment
  (update-crop-params [{:w 980 :h 980 :x 1200 :y 700}])
  (update-crop-params [{:w 2300 :h 2300 :x 450 :y 175}])
  (r/flush)

  ;;
  )

(rf/reg-fx ::update-crop-params update-crop-params)

(rf/reg-fx ::destroy-cropper (fn [_]
                               (.destroy @!cropper)))

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
           ;; TODO enforce minimums based on actual current-size in proportion to rendered viewport
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


;; Helpers

(defn size-name->label [s]
  (join " " (split s #"[-_]")))


;; Components



(defn cropperjs []
  (let [!ref (atom nil)]
    (fn []
      (let [{:keys [full_url]} @(rf/subscribe [::img-config])]
        [:div#cumulus-cropperjs-container
         {:ref (fn [elem]
                 (reset! !ref elem)
                 (when-let [img (js/document.getElementById "cumulus-img")]
                   (reset! !cropper @(rf/subscribe [::cropper-js img]))))}
         ;; By putting the image in here, we tell CropperJS to inject its UI here.
         [:img#cumulus-img {:src full_url}]]))))

(defn scaled-img []
  (let [img-url @(rf/subscribe [::cloudinary-url])]
    [:div.cumulus-scaled-img-container
     [:img#cumulus-img {:src img-url}]]))

(defn crop-ui []
  (let [img-url @(rf/subscribe [::cloudinary-url])
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
                             (rf/dispatch [::update-edit-mode (if cropping? :scale :crop)]))}
             (if cropping? "Scale" "Crop")]]
           [:li "Flip horizontal"]
           [:li "Flip vertical"]]]

         [:footer
          [:span.cumulus-control
           [:button {:on-click #(rf/dispatch [::save-current-size!])} "Save"]
           [:button {:on-click #(rf/dispatch [::reset-current-size])} "Reset"]]]]]]]]))
