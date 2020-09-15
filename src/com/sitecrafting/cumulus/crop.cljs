(ns com.sitecrafting.cumulus.crop
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.string :refer [join split]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]
   ["cropperjs" :as Cropper]
   ["react-dom"]
   [reagent.core :as r]
   [re-frame.core :as rf]))

;; On mount, we'll store the instance of our Cropper in here,
;; so we can refer to it later
(def !cropper (atom nil))

(comment
  ;; Evaluate any of these forms in your editor

  (.setAspectRatio @!cropper (/ 16 9))
  (.setAspectRatio @!cropper (/ 4 3))

  (.reset @!cropper)

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
         {:keys [size_name width height]} current-size]
     (js/console.log size_name width height)
     {:img-config config
      :current-size current-size
      :aspect-ratio (/ width height)
      :crop-params {:x 0
                    :y 0
                    :w 0
                    :h 0}
      :sizes sizes})))

;; Image info

(rf/reg-sub ::img-config :img-config)
(rf/reg-sub ::current-size :current-size)

(rf/reg-event-fx
 ::update-current-size
 (fn [{:keys [db]} [_ {:keys [width height] :as size}]]
   {:db (assoc db :current-size size)
    ::set-aspect-ratio! (when (> height 0) (/ width height))}))

;; CropperJS params

;; Init the CropperJS instance.
;; https://github.com/fengyuanchen/cropperjs/blob/master/README.md
(rf/reg-sub
 ::cropper-js-params
 (fn [db]
   (let [{:keys [width height]} (:current-size db)]
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
          :scalable false
          :movable false
          :rotatable false})))

;; Aspect Ratio

 (rf/reg-sub ::aspect-ratio :aspect-ratio)
 (rf/reg-event-fx ::set-aspect-ratio (fn [{:keys [db]} [_ ar]]
                                       {:db (assoc db :aspect-ratio ar)
                                        ::set-aspect-ratio! ar}))
(rf/reg-fx ::set-aspect-ratio! (fn [ar]
                                 (.setAspectRatio @!cropper ar)))

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
    (r/create-class
     {:reagent-render
      (fn []
        [:div.cropper-container
         {:ref (fn [elem]
                 (reset! !ref elem)
                 (when-let [img (js/document.getElementById "cropper-img")]
                   (reset! !cropper (Cropper. img @(rf/subscribe [::cropper-js-params])))))}])

      :component-did-update
      (fn []
        (.setAspectRatio @!cropper @(rf/subscribe [::aspect-ratio])))})))

(defn crop-ui []
  (let [img-url @(rf/subscribe [::cloudinary-url])
        current-size @(rf/subscribe [::current-size])
        config @(rf/subscribe [::img-config])]
    [:div.cumulus-crop-ui
     [:nav [:ul.crop-sizes
            (map (fn [{:keys [size_name] :as size}]
                   ^{:key size_name}
                   [:li [:a {:href "#"
                             :on-click (fn [e]
                                         (.preventDefault e)
                                         (rf/dispatch [::update-current-size size]))}
                         (size-name->label size_name)]])
                 (:sizes config))]]
     [:pre (js/JSON.stringify (clj->js current-size))]
     [:img#cropper-img {:src (:full_url config)}]
     [cropperjs]
     [:a {:target "_blank" :href img-url} img-url]]))
