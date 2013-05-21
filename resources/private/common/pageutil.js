var pageutil = (function() {
  "use strict";

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

  var ajaxLoaderContainer;
  var ajaxLoaderTask;
  
  function showAjaxWait(message) {
    if (ajaxLoaderTask) clearTimeout(ajaxLoaderTask);
    ajaxLoaderTask = setTimeout(ajaxLoaderContainer.show, 300);
  }

  function hideAjaxWait() {
    if (ajaxLoaderTask) clearTimeout(ajaxLoaderTask);
    ajaxLoaderTask = undefined;
    ajaxLoaderContainer.hide();
  }

  function getPage() {
    var pageMatch = window.location.hash.match(/\/([\-\w]*)/);
    return pageMatch ? pageMatch[1] : null;
  }

  $(function() {
    ajaxLoaderContainer = $("<div>").attr("id", "ajax-loader-container")
      .append($("<img>").attr("src", "/img/ajax-loader.gif"))
      .append($("<p>").addClass("message"));
    $("body").append(ajaxLoaderContainer);
  });

  return {
    getURLParameter:  getURLParameter,
    showAjaxWait:     showAjaxWait,
    hideAjaxWait:     hideAjaxWait,
    getPage:          getPage
  };

})();
