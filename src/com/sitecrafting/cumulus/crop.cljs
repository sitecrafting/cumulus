;; Core logic for cropping and resizing
(ns com.sitecrafting.cumulus.crop
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]
   ["cropperjs" :as Cropper]
   ["react-dom"]
   [re-frame.core :as rf]
   [reagent.core :as r]))


(comment
  ;; Evaluate any of these forms in your editor

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



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;    Database config    ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


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



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                       ;;
  ;;     Core functions    ;;
 ;;                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Purely functional logic governing crops, sizes, params, etc.
;;

(defn- size->name [size]
  (keyword (:size_name size)))

(defn update-current-size [{:keys [db]} [_ new-size]]
  (let [saved-size (get-in db [:img-config :params_by_size (size->name new-size)])
        ;; Default to scale mode
        saved-edit-mode (keyword (:edit_mode saved-size "scale"))
        saved-crop (:crop saved-size)]
    {:db (assoc db
                :current-size new-size
                :edit-mode saved-edit-mode
                :crop-params saved-crop)
     :ui/update-cropper-params (when (= :crop saved-edit-mode)
                                 [saved-crop])}))

(defmulti params-to-save #(:edit-mode % :scale))

(defmethod params-to-save :scale [_]
  {:edit_mode "scale"})

(defmethod params-to-save :crop [{:keys [crop-params]}]
  {:edit_mode "crop"
   :crop crop-params})

(defn unsaved-changes? [{:keys [img-config current-size] :as db}]
  (let [size (keyword (:size_name current-size))]
    (not= (get-in img-config [:params_by_size size])
          (params-to-save db))))

(defn db->update-sizes-config
  [{:keys [img-config current-size] :as db}]
  (assoc-in img-config
            [:params_by_size (size->name current-size)]
            (params-to-save db)))

(defn save-current-size [{:keys [db]}]
  (let [config (db->update-sizes-config db)]
    {:db (assoc db :img-config config)
     ::save-current-size! config}))

(defn reset-current-size [{:keys [db]}]
  {:dispatch [::update-current-size (:current-size db)]})



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                              ;;
  ;;     Subscriptions/Effects    ;;
 ;;                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-event-fx ::update-current-size update-current-size)
(rf/reg-event-fx ::reset-current-size reset-current-size)
(rf/reg-event-fx ::save-current-size save-current-size)

(rf/reg-fx
 ::save-current-size!
 (fn [{:keys [attachment_id params_by_size]}]
   (-> (js/fetch (str "/wp-json/cumulus/v1/attachment/" attachment_id)
                 #js {:method "POST"
                      :headers #js {"content-type" "application/json"}
                      :body (js/JSON.stringify (clj->js params_by_size))})
       (.then (fn [response]
                (js/console.log response))))))

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

(rf/reg-sub ::img-config :img-config)
(rf/reg-sub ::current-size :current-size)