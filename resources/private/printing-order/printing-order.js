var printingOrder = (function() {

  hub.onPageLoad("printing-order", function() {
    pageutil.showAjaxWait();
  });

  hub.onPageUnload("printing-order", function() {
    pageutil.hideAjaxWait();
  });

})();