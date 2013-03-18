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
  
  function showWaitForLoading() {
    console.log("wait for loading");
    $('.wait-for-loading').css("display", "inline-block");
  }

  function hideWaitForLoading() {
    console.log("hide wait for loading");
    $('.wait-for-loading').css("display", "none");
  }
  
  return {
    getURLParameter:  getURLParameter,
    hideWaitForLoading: hideWaitForLoading,
    showWaitForLoading: showWaitForLoading,
  };

})();
