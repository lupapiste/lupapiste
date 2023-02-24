var notify = (function() {
  "use strict";

  function displayMessage(defaultTitleKey, titleOrMessage, data) {
    LUPAPISTE.ModalDialog.close();
    hub.send( "close-dialog" );
    if (data !== undefined) {
      hub.send( "show-dialog", {title: titleOrMessage,
                                component: "ok-dialog",
                                size: "small",
                                componentParams: {text: data,
                                                 html: false}});
    } else {
      hub.send( "show-dialog", {ltitle: defaultTitleKey,
                                component: "ok-dialog",
                                size: "small",
                                componentParams: {text: titleOrMessage,
                                                  html: false}});
    }
  }

  return {
    error: _.partial(displayMessage, "error.dialog.title"),
    ajaxError: function(resp) {
      displayMessage("error.dialog.title", loc(resp.text, resp.errorParams));
    },
    success: _.partial(displayMessage, "success.dialog.title")
  };

})();
