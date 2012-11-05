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
  
  return {
    setPageReady:     setPageReady,
    setPageNotReady:  setPageNotReady,
    isPageReady:      isPageReady
  };
  
}();
