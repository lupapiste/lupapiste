var pageutil = (function() {
  "use strict";
  
  var ajaxImg;
  var ajaxLoaderContainer;

  /**
   * Returns HTTP GET parameter value or null if the parameter is not set.
   */
  function getURLParameter(name) {
    if (location.search) {
      var value = (location.search.match(new RegExp("[?|&]" + name + "=([^&]*)(&|$)")) || [null])[1];
      if (value !== null) {
        return decodeURIComponent(value).replace(/\+/g, " ");
      }
    }
    return null;
  }
  
  function showAjaxWait() {
    ajaxImg.hide();
    ajaxLoaderContainer.show();
    setTimeout(function() {
      ajaxImg.show();
    }, 500);
  }

  function hideAjaxWait() {
    ajaxLoaderContainer.hide();
  }
  
  $(function() {
    ajaxImg = $('<img src="/img/ajax-loader.gif" class="ajax-loader">');
    ajaxLoaderContainer = $('<div class="ajax-loader-container">').append(ajaxImg);
    $('body').append(ajaxLoaderContainer);
  });

  return {
    getURLParameter:  getURLParameter,
    showAjaxWait:     showAjaxWait,
    hideAjaxWait:     hideAjaxWait,
  };

})();
