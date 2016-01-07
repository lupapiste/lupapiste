var notify = (function() {
  "use strict";

  function displayMessage(defaultTitleKey, title, data) {
    LUPAPISTE.ModalDialog.close();
    if (data !== undefined) {
      LUPAPISTE.ModalDialog.showDynamicOk(title, data);
    } else {
      LUPAPISTE.ModalDialog.showDynamicOk(loc(defaultTitleKey), title);
    }
  }

  return {
    error: _.partial(displayMessage, "error.dialog.title"),
    success: _.partial(displayMessage, "success.dialog.title")
  };

})();
