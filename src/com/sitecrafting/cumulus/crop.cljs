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
         {:keys [width height]} current-size]
     {:img-config config
      :current-size current-size
      :edit-mode :scale
      :aspect-ratio (/ width height)
      :crop-params {:x 0
                    :y 0
                    :w 0
                    :h 0}
      :sizes sizes})))

;; Image info

(rf/reg-sub ::img-config :img-config)
(rf/reg-sub ::current-size :current-size)

(defn update-current-size [{:keys [img-config] :as db} [_ new-size]]
  (let [saved-size (get-in img-config [:params_by_size (keyword (:size_name new-size))])
        saved-edit-mode (keyword (:edit_mode saved-size))
        saved-crop (:crop saved-size)]
    (assoc db
           :current-size new-size
           :edit-mode saved-edit-mode
           :crop-params saved-crop)))

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
  (let [size (keyword (:size_name current-size))]
    (assoc-in img-config [:params_by_size size] (params-to-save db))))

(defn save-current-size [{:keys [db]}]
  (let [config (db->update-sizes-config db)]
    {:db (assoc db :img-config config)
     ::save-current-size! config}))

(defn reset-current-size [{:keys [db]}]
  {:dispatch [::update-current-size (:current-size db)]})

(rf/reg-event-db ::update-current-size update-current-size)
(rf/reg-event-fx ::reset-current-size reset-current-size)
(rf/reg-event-fx ::save-current-size! save-current-size)

(rf/reg-fx ::save-current-size! (fn [{:keys [attachment_id params_by_size]}]
                                  (-> (js/fetch (str "/wp-json/v1/attachment/" attachment_id)
                                                #js {:method "POST"
                                                     :body (js/JSON.stringify (clj->js params_by_size))})
                                      (.then (fn [response]
                                               (js/console.log response))))))

;; CropperJS params

;; Init the CropperJS instance.
;; https://github.com/fengyuanchen/cropperjs/blob/master/README.md
(rf/reg-sub
 ::cropper-js
 (fn [db [_ img-elem]]
   (let [{:keys [width height]} (:current-size db)]
     (js/console.log img-elem)
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
           ;; TODO get from params_by_size
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
  (let [!ref (atom nil)
        !cropper (atom nil)]
    (fn []
      (let [{:keys [full_url]} @(rf/subscribe [::img-config])]
        [:div#cumulus-cropperjs-container
         {:ref (fn [elem]
                 (reset! !ref elem)
                 (when-let [img (js/document.getElementById "cumulus-img")]
                   (when @!cropper (.destroy @!cropper))
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
