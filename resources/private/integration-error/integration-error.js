var LUPAPISTE = LUPAPISTE || {};

(function() {
  "use strict";
  var errorModel = {
    title: ko.observable(""),
    text: ko.observable(""),
    details: ko.observable("")
  };

  LUPAPISTE.showIntegrationError = function(titleKey, helpKey, detailsText) {
    errorModel.title(titleKey);
    errorModel.text(helpKey);
    errorModel.details(detailsText);
    LUPAPISTE.ModalDialog.open("#integration-error-dialog");
  };

  $(function(){
    $("#integration-error-page").applyBindings(errorModel);
  });

})();
