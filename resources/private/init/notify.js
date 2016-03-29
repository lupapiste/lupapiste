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
      displayMessage("error.dialog.title", loc(resp.text));
    },
    success: _.partial(displayMessage, "success.dialog.title")
  };

})();
