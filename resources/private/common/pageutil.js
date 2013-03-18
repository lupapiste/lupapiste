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
  
  function showAjaxWait() {
    console.log("show");
    $('.ajax-wait').css("display", "inline-block");
  }

  function hideAjaxWait() {
    console.log("hide");
    $('.ajax-wait').css("display", "none");
  }
  
  return {
    getURLParameter:  getURLParameter,
    showAjaxWait:     showAjaxWait,
    hideAjaxWait:     hideAjaxWait,
  };

})();
