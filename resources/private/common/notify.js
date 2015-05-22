var notify = (function() {
  "use strict";

  function displayMessage(defaultTitle, title, data) {
    LUPAPISTE.ModalDialog.close();
    if (data !== undefined) {
      LUPAPISTE.ModalDialog.showDynamicOk(title, data);
    } else {
      LUPAPISTE.ModalDialog.showDynamicOk(defaultTitle, title);
    }
  }

  return {
    error: _.partial(displayMessage, loc("error.dialog.title")),
    success: _.partial(displayMessage, loc("success.dialog.title"))
  };

})();
