<script>
jQuery(function($){
  function _template(selector) {
    return $(selector).html();
  }

  function _mountReactApp() {
    if (
      com && com.sitecrafting && com.sitecrafting.cumulus && com.sitecrafting.cumulus.core
      && typeof (com.sitecrafting.cumulus.core.init) === 'function'
    ) {
      com.sitecrafting.cumulus.core.init();
    }
  }

  /**
   * Do a bunch of hack jQuery stuff to render the Edit Image Crops UI
   */
  function _initCumulusCropsUi() {
    var $editCropsBtn = $('.cumulus-edit-crops-btn');

    if ($editCropsBtn.length) {
      // We're already done.
      return;
    }

    $editCropsBtn = $('<button type="button"></button>')
      .addClass('button cumulus-edit-crops-btn')
      .text('<?= $data['customize_crops'] ?>');

    $('.attachment-actions').append($editCropsBtn);

    $editCropsBtn.click(function() {
      var $modal = $(this).closest('.media-modal-content');
      $modal.addClass('media-modal-content--original').hide();

      var $cumulusUi = $('.media-modal-content--cumulus');

      if ($cumulusUi.length) {
        $cumulusUi.show();
      } else {
        // Build and inject modal container markup.
        $cumulusUi = $(_template('#cumulus-template-edit-media'))
        $modal.after($cumulusUi);

        // Mount our CLJS/React app into the container.
        _mountReactApp();

        $cumulusUi.find('.media-modal-close--cumulus').click(function(e) {
          $cumulusUi.hide();
          $modal.show();
          return false;
        });
      }
    });
  }

  var observer = new MutationObserver(function(mutationList, observer) {
    if ($('.attachment-actions').length) {
      // The DOM is now ready for use to inject our UI!
      _initCumulusCropsUi();
    }
  });

  // TODO fall back on DOMNodeInserted?

  // Watch for elements being inserted in the DOM, or attributes changing.
  observer.observe(document.body, { attributes: true, childList: true });
});
</script>
<script type="text/html" id="cumulus-template-edit-media">
  <div class="media-modal-content media-modal-content--cumulus">
    <div class="edit-attachment-frame hide-menu hide-router">
      <div class="edit-media-header">
        <button type="button" class="media-modal-close media-modal-close--cumulus">
          <span class="media-modal-icon">
            <span class="screen-reader-text"><?= $data['back_to_details'] ?></span>
          </span>
        </button>
      </div>
      <div class="media-frame-title"><h1><?= $data['customize_crops'] ?></h1></div>
      <div class="media-frame-content">
        <div id="cumulus-crop-ui"></div>
      </div>
    </div>
  </div>
</script>