var printingOrder = (function() {

  hub.onPageLoad("printing-order", function() {
    pageutil.showAjaxWait();
    pageutil.hideAjaxWait();
  });

  hub.onPageUnload("printing-order", function() {
  });

  $(function() {
    $('#printing-order').applyBindings({
      backToApplication: function() {
        var id = pageutil.subPage();
        pageutil.openPage("application/" + id, "attachments");
      }
    });
  });

})();