(ns com.sitecrafting.cumulus.core
  (:require
   [com.sitecrafting.cumulus.crop :as c]
   [com.sitecrafting.cumulus.ui :as ui]
   [reagent.dom :as dom]
   [re-frame.core :as rf]))


;; start is called by init and after code reloading finishes
(defn ^:dev/after-load mount-root
  "Mount the root UI component, crop/crop-ui"
  []
  (rf/clear-subscription-cache!)
  (dom/render [ui/crop-ui]
              (js/document.getElementById "cumulus-crop-ui")))

(defn ^:export init
  "Entry point for the Cumulus Crop UI. Called once when the page loads,
  and once for each time a new attachment is selected from the WP modal.
  In dev, this is called in the index.html and must be exported
  so it is available even in :advanced release builds."
  [cumulus-config]
  ;; Clear the currently mounted component, if any.
  (dom/unmount-component-at-node (js/document.getElementById "cumulus-crop-ui"))
  (rf/dispatch-sync [::c/init-db (js->clj cumulus-config)])
  (mount-root))
