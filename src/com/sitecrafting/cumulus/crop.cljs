(ns com.sitecrafting.cumulus.crop
  (:require
   [clojure.walk :refer [keywordize-keys]]
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

  ;;
  )

(rf/reg-event-db
 ::init-db
 (fn [_ [_ config]]
   {:img-config (keywordize-keys config)
    :aspect-ratio (/ 16 9)
    :crop-params {:x 0
                  :y 0
                  :width 0
                  :height 0}}))

;; Image info

(rf/reg-sub ::img-config :img-config)

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

(rf/reg-sub ::cloudinary-url (fn [{:keys [crop-params img-config]}]
                               (cloud/crop->url (merge img-config crop-params))))


(defn cropperjs []
  (let [!ref (atom nil)]
    (r/create-class
     {:reagent-render
      (fn []
        (let [aspect-ratio @(rf/subscribe [::aspect-ratio])]
          [:div.cropper-container
           {:ref (fn [elem]
                   (reset! !ref elem)
                   (let [img (js/document.getElementById "cropper-img")]
                     (reset!
                      !cropper
                      (Cropper.
                       img
                       #js {:crop (fn [event]
                                    (let [params (.-detail event)]
                                      (rf/dispatch [::set-crop-params
                                                    {:x (js/Math.round (.-x params))
                                                     :y (js/Math.round (.-y params))
                                                     :width (js/Math.round (.-width params))
                                                     :height (js/Math.round (.-height params))}])))
                            :aspectRatio aspect-ratio}))))}]))
      
      :component-did-update
      (fn []
        (.setAspectRatio @!cropper @(rf/subscribe [::aspect-ratio])))})))

(defn crop-ui []
  (let [img-url @(rf/subscribe [::cloudinary-url])]
    [:div.cumulus-crop-ui
     [cropperjs]
     [:a {:target "_blank" :href img-url} img-url]]))
