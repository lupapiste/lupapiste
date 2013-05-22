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

  function getPage() {
    var pageMatch = window.location.hash.match(/\/([\-\w]*)/);
    return pageMatch ? pageMatch[1] : null;
  }

  var ajaxLoaderContainer;
  var ajaxLoaderTask;
  
  function showAjaxWaitNow(message) { ajaxLoaderContainer.find("p").html(message || "").end().show(); }
  
  function showAjaxWait(message) {
    if (ajaxLoaderTask) clearTimeout(ajaxLoaderTask);
    ajaxLoaderTask = _.delay(showAjaxWaitNow, 300, message);
  }

  function hideAjaxWait() {
    if (ajaxLoaderTask) clearTimeout(ajaxLoaderTask);
    ajaxLoaderTask = undefined;
    ajaxLoaderContainer.hide();
  }

  $(function() {
    ajaxLoaderContainer = $("<div>").attr("id", "ajax-loader-container")
      .append($("<div>"))
      .append($("<p>").addClass("message"))
      .appendTo($("body"));
  });

  return {
    getURLParameter:  getURLParameter,
    getPage:          getPage,
    showAjaxWait:     showAjaxWait,
    hideAjaxWait:     hideAjaxWait
  };

})();
