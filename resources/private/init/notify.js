var notify = (function() {
  "use strict";

  function displayMessage(defaultTitleKey, titleOrMessage, data) {
    LUPAPISTE.ModalDialog.close();
    if (data !== undefined) {
      LUPAPISTE.ModalDialog.showDynamicOk(titleOrMessage, data);
    } else {
      LUPAPISTE.ModalDialog.showDynamicOk(loc(defaultTitleKey), titleOrMessage);
    }
  }

  return {
    error: _.partial(displayMessage, "error.dialog.title"),
    ajaxError: function(resp) {
      displayMessage("error.dialog.title", _.apply(loc, null, _.concat(resp.text, resp.errorParams)));
    },
    success: _.partial(displayMessage, "success.dialog.title")
  };

})();
