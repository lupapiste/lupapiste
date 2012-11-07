var pageutil = function() {

  function setPageReady(id) {
    $("#pageStatus").append('<div id="'+id+'-page-is-ready">' + id + '-page-is-ready</div>');
  }

  function isPageReady(id) {
    return  $('#'+id+'-page-is-ready').length > 0;
  }

  function setPageNotReady() {
    $("#pageStatus").children().remove();
  }

  /**
   * Returns HTTP GET parameter value or null if the parameter is not set.
   */
  function getURLParameter(name) {
    if (location.search) {
      var value = (location.search.match(RegExp("[?|&]"+name+'=([^&]*)(&|$)'))||[,null])[1];
      if (value !== null) {
        return decodeURIComponent(value).replace(/\+/g, " ");
      }
    }
    return null;
  }

  return {
    setPageReady:     setPageReady,
    setPageNotReady:  setPageNotReady,
    isPageReady:      isPageReady,
    getURLParameter:  getURLParameter
  };

}();
