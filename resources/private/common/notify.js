var notify = (function() {
  "use strict";

  function displayMessage(defautlTitle, title, data) {
    LUPAPISTE.ModalDialog.close();
    if (data !== undefined) {
      LUPAPISTE.ModalDialog.showDynamicOk(title, data);
    } else {
      LUPAPISTE.ModalDialog.showDynamicOk(defaultTitle, data);
    }
  }

  return {
    error: _.partial(displayMessage, loc("error.dialog.title")),
    success: _.partial(displayMessage, loc("success.dialog.title"))
  };

})();
